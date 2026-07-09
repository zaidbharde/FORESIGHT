"""Point-in-time builder for immutable Model V2 feature snapshots."""

from __future__ import annotations

import hashlib
import json
import math
from collections.abc import Callable
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

from pydantic import Field, field_validator, model_validator

from backend.feature_infrastructure.contract import FeatureContract
from backend.feature_infrastructure.exceptions import FeatureSourceStateError
from backend.feature_infrastructure.models import (
    BusinessFeatures,
    DeviceHistory,
    DomainModel,
    FeatureAvailability,
    FeatureProvenance,
    FeatureSnapshot,
    LocationEligibilityStatus,
    LocationHistory,
    TrustedContact,
    TrustedContactStatus,
    require_utc_datetime,
)


class CurrentLocation(DomainModel):
    """Current consented location signal attached to a payment decision."""

    latitude: float = Field(ge=-90.0, le=90.0)
    longitude: float = Field(ge=-180.0, le=180.0)
    accuracy_m: float = Field(ge=0.0)

    @field_validator("latitude", "longitude", "accuracy_m")
    @classmethod
    def validate_finite(cls, value: float, info: object) -> float:
        if not math.isfinite(value):
            field_name = getattr(info, "field_name", "value")
            raise ValueError(f"{field_name} must be finite.")
        return value


class FeatureSnapshotBuildRequest(DomainModel):
    """Payment identity plus successfully resolved source state at its cutoff.

    An empty optional projection means a confirmed authoritative absence. Source
    lookup failures must be raised by the adapter and must never construct this
    request.
    """

    transaction_id: UUID
    user_id: UUID
    receiver_id: UUID
    cutoff_ts: datetime
    trusted_contact: TrustedContact | None = None
    current_device_fingerprint: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    current_device_fingerprint_version: str | None = Field(default=None, min_length=1)
    prior_devices: tuple[DeviceHistory, ...] = ()
    current_location: CurrentLocation | None = None
    location_history: tuple[LocationHistory, ...] = ()

    @field_validator("cutoff_ts")
    @classmethod
    def validate_cutoff(cls, value: datetime) -> datetime:
        return require_utc_datetime(value, "cutoff_ts")

    @model_validator(mode="after")
    def validate_device_pair(self) -> "FeatureSnapshotBuildRequest":
        has_fingerprint = self.current_device_fingerprint is not None
        has_version = self.current_device_fingerprint_version is not None
        if has_fingerprint != has_version:
            raise ValueError(
                "current_device_fingerprint and current_device_fingerprint_version "
                "must be supplied together."
            )
        return self


class FeatureSnapshotBuilder:
    """Builds and contract-validates a snapshot without invoking an ML model."""

    def __init__(
        self,
        contract: FeatureContract,
        *,
        clock: Callable[[], datetime] | None = None,
        id_factory: Callable[[], UUID] | None = None,
    ) -> None:
        self.contract = contract
        self._clock = clock or (lambda: datetime.now(timezone.utc))
        self._id_factory = id_factory or uuid4

    def build(self, request: FeatureSnapshotBuildRequest) -> FeatureSnapshot:
        """Produce a validated immutable snapshot from resolved point-in-time state."""

        self._validate_source_state(request)
        trusted_flag, trusted_age, trusted_availability = self._trusted_features(request)
        known_device, device_changed, device_availability, eligible_devices = (
            self._device_features(request)
        )
        distance, anomaly, location_availability, eligible_locations = (
            self._location_features(request)
        )

        features = BusinessFeatures(
            trusted_contact=trusted_flag,
            trusted_contact_age=trusted_age,
            device_change_flag=device_changed,
            location_anomaly=anomaly,
            location_distance=distance,
            known_device=known_device,
        )
        feature_mapping = features.model_dump()
        self.contract.validate_feature_mapping(feature_mapping)

        now = self._clock()
        require_utc_datetime(now, "clock result")
        if now < request.cutoff_ts:
            raise FeatureSourceStateError("Builder clock cannot precede the payment cutoff.")

        vector_hash = self._hash_vector(feature_mapping)
        trusted_event_id = (
            request.trusted_contact.last_event_id if request.trusted_contact is not None else None
        )
        provenance = FeatureProvenance(
            trusted_contact_availability=trusted_availability,
            device_availability=device_availability,
            location_availability=location_availability,
            trusted_contact_event_id=trusted_event_id,
            device_history_ids=tuple(item.id for item in eligible_devices),
            location_history_ids=tuple(item.id for item in eligible_locations),
            prior_device_count=len(eligible_devices),
            location_baseline_count=len(eligible_locations),
        )
        document = self.contract.document
        return FeatureSnapshot(
            snapshot_id=self._id_factory(),
            transaction_id=request.transaction_id,
            user_id=request.user_id,
            cutoff_ts=request.cutoff_ts,
            computed_at=now,
            schema_version=document.schema_version,
            schema_hash=document.schema_hash,
            transformer_version=document.transformer_version,
            device_fingerprint_version=document.device_fingerprint_version,
            location_policy_version=document.location_policy.version,
            features=features,
            provenance=provenance,
            vector_hash=vector_hash,
            model_version=None,
            created_at=now,
        )

    def _validate_source_state(self, request: FeatureSnapshotBuildRequest) -> None:
        contact = request.trusted_contact
        if contact is not None:
            if contact.user_id != request.user_id or contact.receiver_id != request.receiver_id:
                raise FeatureSourceStateError(
                    "TrustedContact identity does not match the payment identity."
                )
            if contact.updated_at >= request.cutoff_ts:
                raise FeatureSourceStateError(
                    "TrustedContact projection is not strictly before the payment cutoff."
                )

        for device in request.prior_devices:
            if device.user_id != request.user_id:
                raise FeatureSourceStateError("DeviceHistory user does not match payment user.")
            if device.first_seen_ts >= request.cutoff_ts:
                raise FeatureSourceStateError(
                    "DeviceHistory contains an observation at or after the payment cutoff."
                )
            if device.last_seen_ts >= request.cutoff_ts or device.created_at >= request.cutoff_ts:
                raise FeatureSourceStateError(
                    "DeviceHistory contains state unavailable at the payment cutoff."
                )

        for location in request.location_history:
            if location.user_id != request.user_id:
                raise FeatureSourceStateError("LocationHistory user does not match payment user.")
            if location.transaction_id == request.transaction_id:
                raise FeatureSourceStateError("Current transaction cannot appear in location history.")
            if location.event_ts >= request.cutoff_ts:
                raise FeatureSourceStateError(
                    "LocationHistory contains an observation at or after the payment cutoff."
                )
            if location.ingested_at >= request.cutoff_ts:
                raise FeatureSourceStateError(
                    "LocationHistory contains an observation unavailable at the payment cutoff."
                )

    @staticmethod
    def _trusted_features(
        request: FeatureSnapshotBuildRequest,
    ) -> tuple[int, float, FeatureAvailability]:
        contact = request.trusted_contact
        if contact is None or contact.status is not TrustedContactStatus.ACTIVE:
            return 0, 0.0, FeatureAvailability.CONFIRMED_ABSENT
        if contact.active_since_ts is None or contact.active_since_ts >= request.cutoff_ts:
            raise FeatureSourceStateError(
                "Active trusted contact must start strictly before the payment cutoff."
            )
        age_days = (request.cutoff_ts - contact.active_since_ts).total_seconds() / 86400.0
        return 1, float(age_days), FeatureAvailability.AVAILABLE

    def _device_features(
        self, request: FeatureSnapshotBuildRequest
    ) -> tuple[int, int, FeatureAvailability, tuple[DeviceHistory, ...]]:
        expected_version = self.contract.document.device_fingerprint_version
        eligible = tuple(
            device
            for device in request.prior_devices
            if device.fingerprint_version == expected_version
            and (device.revoked_at is None or device.revoked_at >= request.cutoff_ts)
            and device.first_seen_ts < request.cutoff_ts
        )
        if request.current_device_fingerprint is None:
            return 0, 0, FeatureAvailability.CURRENT_DEVICE_UNAVAILABLE, eligible
        if request.current_device_fingerprint_version != expected_version:
            raise FeatureSourceStateError(
                "Current device fingerprint version does not match the feature contract."
            )
        known = any(
            device.fingerprint_hash == request.current_device_fingerprint for device in eligible
        )
        changed = bool(eligible) and not known
        return int(known), int(changed), FeatureAvailability.AVAILABLE, eligible

    def _location_features(
        self, request: FeatureSnapshotBuildRequest
    ) -> tuple[float, int, FeatureAvailability, tuple[LocationHistory, ...]]:
        current = request.current_location
        policy = self.contract.document.location_policy
        if current is None:
            return -1.0, 0, FeatureAvailability.CURRENT_LOCATION_UNAVAILABLE, ()
        if current.accuracy_m > policy.maximum_accuracy_m:
            return -1.0, 0, FeatureAvailability.CURRENT_LOCATION_INACCURATE, ()

        start_ts = request.cutoff_ts - timedelta(days=policy.lookback_days)
        eligible = tuple(
            location
            for location in request.location_history
            if location.eligibility_status is LocationEligibilityStatus.ELIGIBLE
            and location.accuracy_m <= policy.maximum_accuracy_m
            and location.ingested_at < request.cutoff_ts
            and start_ts <= location.event_ts < request.cutoff_ts
        )
        if len(eligible) < policy.minimum_observations:
            return -1.0, 0, FeatureAvailability.INSUFFICIENT_HISTORY, eligible

        distance = min(
            self._haversine_km(
                current.latitude,
                current.longitude,
                location.latitude,
                location.longitude,
                policy.earth_radius_km,
            )
            for location in eligible
        )
        distance = float(distance)
        anomaly = int(distance > policy.anomaly_threshold_km)
        return distance, anomaly, FeatureAvailability.AVAILABLE, eligible

    @staticmethod
    def _haversine_km(
        latitude_1: float,
        longitude_1: float,
        latitude_2: float,
        longitude_2: float,
        earth_radius_km: float,
    ) -> float:
        lat1, lon1, lat2, lon2 = map(
            math.radians, (latitude_1, longitude_1, latitude_2, longitude_2)
        )
        delta_latitude = lat2 - lat1
        delta_longitude = lon2 - lon1
        haversine = (
            math.sin(delta_latitude / 2.0) ** 2
            + math.cos(lat1) * math.cos(lat2) * math.sin(delta_longitude / 2.0) ** 2
        )
        central_angle = 2.0 * math.asin(min(1.0, math.sqrt(haversine)))
        return earth_radius_km * central_angle

    @staticmethod
    def _hash_vector(values: dict[str, int | float]) -> str:
        encoded = json.dumps(
            values, ensure_ascii=False, allow_nan=False, separators=(",", ":")
        ).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()
