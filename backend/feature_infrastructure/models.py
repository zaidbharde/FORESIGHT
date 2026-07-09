"""Validated domain and snapshot models for FORESIGHT Model V2.

The models are immutable value objects suitable for mapping to a relational
persistence layer.  They intentionally contain no ORM or API dependencies.
"""

from __future__ import annotations

import json
import math
import re
from datetime import datetime
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def require_utc_datetime(value: datetime, field_name: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must be timezone-aware UTC.")
    if value.utcoffset().total_seconds() != 0:
        raise ValueError(f"{field_name} must use UTC.")
    return value


class DomainModel(BaseModel):
    """Strict immutable base for domain entities and value objects."""

    model_config = ConfigDict(
        extra="forbid", frozen=True, strict=True, allow_inf_nan=False
    )


class TrustedContactStatus(StrEnum):
    ACTIVE = "ACTIVE"
    REMOVED = "REMOVED"
    BLOCKED = "BLOCKED"


class TrustedContactEventType(StrEnum):
    ADDED = "ADDED"
    RESTORED = "RESTORED"
    VERIFIED = "VERIFIED"
    REMOVED = "REMOVED"
    BLOCKED = "BLOCKED"


class ActorType(StrEnum):
    USER = "USER"
    SYSTEM = "SYSTEM"
    ADMIN = "ADMIN"


class LocationSource(StrEnum):
    GPS = "GPS"
    NETWORK = "NETWORK"


class LocationEligibilityStatus(StrEnum):
    ELIGIBLE = "ELIGIBLE"
    INACCURATE = "INACCURATE"
    REVOKED = "REVOKED"
    TEST = "TEST"
    REVERSED = "REVERSED"


class FeatureAvailability(StrEnum):
    AVAILABLE = "AVAILABLE"
    CONFIRMED_ABSENT = "CONFIRMED_ABSENT"
    CURRENT_DEVICE_UNAVAILABLE = "CURRENT_DEVICE_UNAVAILABLE"
    CURRENT_LOCATION_UNAVAILABLE = "CURRENT_LOCATION_UNAVAILABLE"
    CURRENT_LOCATION_INACCURATE = "CURRENT_LOCATION_INACCURATE"
    INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY"


class TrustedContact(DomainModel):
    """Current trusted-contact projection; immutable events remain authoritative."""

    id: UUID
    user_id: UUID
    receiver_id: UUID
    status: TrustedContactStatus
    active_since_ts: datetime | None = None
    last_verified_ts: datetime | None = None
    last_event_id: UUID
    version: int = Field(ge=1)
    created_at: datetime
    updated_at: datetime

    @field_validator("active_since_ts", "last_verified_ts", "created_at", "updated_at")
    @classmethod
    def validate_timestamps(cls, value: datetime | None, info: Any) -> datetime | None:
        return None if value is None else require_utc_datetime(value, info.field_name)

    @model_validator(mode="after")
    def validate_state(self) -> "TrustedContact":
        if (self.status is TrustedContactStatus.ACTIVE) != (self.active_since_ts is not None):
            raise ValueError("active_since_ts must be present if and only if status is ACTIVE.")
        if self.updated_at < self.created_at:
            raise ValueError("updated_at cannot precede created_at.")
        if self.active_since_ts is not None and self.active_since_ts > self.updated_at:
            raise ValueError("active_since_ts cannot follow updated_at.")
        return self


class TrustedContactEvent(DomainModel):
    """Append-only trusted-contact state transition."""

    event_id: UUID
    user_id: UUID
    receiver_id: UUID
    event_type: TrustedContactEventType
    event_ts: datetime
    event_sequence: int = Field(ge=1)
    actor_type: ActorType
    source: str = Field(min_length=1, max_length=32)
    reason_code: str | None = Field(default=None, max_length=64)
    ingested_at: datetime

    @field_validator("event_ts", "ingested_at")
    @classmethod
    def validate_timestamps(cls, value: datetime, info: Any) -> datetime:
        return require_utc_datetime(value, info.field_name)

    @model_validator(mode="after")
    def validate_ingestion_order(self) -> "TrustedContactEvent":
        if self.ingested_at < self.event_ts:
            raise ValueError("ingested_at cannot precede event_ts.")
        return self


class DeviceHistory(DomainModel):
    """Per-user projection of an eligible pseudonymous device fingerprint."""

    id: UUID
    user_id: UUID
    fingerprint_hash: str
    fingerprint_version: str = Field(min_length=1, max_length=32)
    first_seen_ts: datetime
    last_seen_ts: datetime
    first_seen_transaction_id: UUID
    last_seen_transaction_id: UUID
    successful_payment_count: int = Field(ge=1)
    revoked_at: datetime | None = None
    created_at: datetime
    updated_at: datetime

    @field_validator("fingerprint_hash")
    @classmethod
    def validate_fingerprint_hash(cls, value: str) -> str:
        if not HASH_PATTERN.fullmatch(value):
            raise ValueError("fingerprint_hash must be 64 lowercase hexadecimal characters.")
        return value

    @field_validator(
        "first_seen_ts", "last_seen_ts", "revoked_at", "created_at", "updated_at"
    )
    @classmethod
    def validate_timestamps(cls, value: datetime | None, info: Any) -> datetime | None:
        return None if value is None else require_utc_datetime(value, info.field_name)

    @model_validator(mode="after")
    def validate_chronology(self) -> "DeviceHistory":
        if self.last_seen_ts < self.first_seen_ts:
            raise ValueError("last_seen_ts cannot precede first_seen_ts.")
        if self.updated_at < self.created_at:
            raise ValueError("updated_at cannot precede created_at.")
        if self.revoked_at is not None and self.revoked_at < self.first_seen_ts:
            raise ValueError("revoked_at cannot precede first_seen_ts.")
        return self


class LocationHistory(DomainModel):
    """Append-only consented location observation linked to a transaction."""

    id: UUID
    user_id: UUID
    transaction_id: UUID
    event_ts: datetime
    event_sequence: int = Field(ge=1)
    latitude: float = Field(ge=-90.0, le=90.0)
    longitude: float = Field(ge=-180.0, le=180.0)
    accuracy_m: float = Field(ge=0.0)
    source: LocationSource
    consent_version: str = Field(min_length=1, max_length=32)
    eligibility_status: LocationEligibilityStatus
    ingested_at: datetime
    created_at: datetime

    @field_validator("event_ts", "ingested_at", "created_at")
    @classmethod
    def validate_timestamps(cls, value: datetime, info: Any) -> datetime:
        return require_utc_datetime(value, info.field_name)

    @field_validator("latitude", "longitude", "accuracy_m")
    @classmethod
    def validate_finite_values(cls, value: float, info: Any) -> float:
        if not math.isfinite(value):
            raise ValueError(f"{info.field_name} must be finite.")
        return value

    @model_validator(mode="after")
    def validate_ingestion_order(self) -> "LocationHistory":
        if self.ingested_at < self.event_ts:
            raise ValueError("ingested_at cannot precede event_ts.")
        return self


class BusinessFeatures(DomainModel):
    """Canonical ordered Model V2 business feature vector."""

    trusted_contact: int = Field(ge=0, le=1)
    trusted_contact_age: float = Field(ge=0.0)
    device_change_flag: int = Field(ge=0, le=1)
    location_anomaly: int = Field(ge=0, le=1)
    location_distance: float = Field(ge=-1.0)
    known_device: int = Field(ge=0, le=1)

    @model_validator(mode="after")
    def validate_invariants(self) -> "BusinessFeatures":
        if self.trusted_contact == 0 and self.trusted_contact_age != 0.0:
            raise ValueError(
                "trusted_contact_age must be 0.0 when trusted_contact is 0."
            )
        if self.known_device == 1 and self.device_change_flag != 0:
            raise ValueError("device_change_flag must be 0 when known_device is 1.")
        if self.location_distance == -1.0 and self.location_anomaly != 0:
            raise ValueError(
                "location_anomaly must be 0 when location_distance is unavailable."
            )
        return self


class FeatureProvenance(DomainModel):
    """Non-sensitive lineage required to audit or replay a snapshot."""

    trusted_contact_availability: FeatureAvailability
    device_availability: FeatureAvailability
    location_availability: FeatureAvailability
    trusted_contact_event_id: UUID | None = None
    device_history_ids: tuple[UUID, ...] = ()
    location_history_ids: tuple[UUID, ...] = ()
    prior_device_count: int = Field(ge=0)
    location_baseline_count: int = Field(ge=0)


class FeatureSnapshot(DomainModel):
    """Immutable, versioned record of the exact business vector built for payment."""

    snapshot_id: UUID
    transaction_id: UUID
    user_id: UUID
    cutoff_ts: datetime
    computed_at: datetime
    schema_version: str
    schema_hash: str
    transformer_version: str
    device_fingerprint_version: str
    location_policy_version: str
    features: BusinessFeatures
    provenance: FeatureProvenance
    vector_hash: str
    model_version: str | None = None
    created_at: datetime

    @field_validator("cutoff_ts", "computed_at", "created_at")
    @classmethod
    def validate_timestamps(cls, value: datetime, info: Any) -> datetime:
        return require_utc_datetime(value, info.field_name)

    @field_validator("schema_hash", "vector_hash")
    @classmethod
    def validate_hash(cls, value: str, info: Any) -> str:
        if not HASH_PATTERN.fullmatch(value):
            raise ValueError(f"{info.field_name} must be a lowercase SHA-256 hex digest.")
        return value

    @model_validator(mode="after")
    def validate_chronology(self) -> "FeatureSnapshot":
        if self.computed_at < self.cutoff_ts:
            raise ValueError("computed_at cannot precede cutoff_ts.")
        if self.created_at < self.computed_at:
            raise ValueError("created_at cannot precede computed_at.")
        return self

    def to_json(self) -> str:
        """Return stable compact JSON in declared field order, including null fields."""

        return json.dumps(
            self.model_dump(mode="json"),
            ensure_ascii=False,
            allow_nan=False,
            separators=(",", ":"),
        )
