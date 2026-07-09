from __future__ import annotations

import json
import unittest
from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

from pydantic import ValidationError

from backend.feature_infrastructure.builder import (
    CurrentLocation,
    FeatureSnapshotBuildRequest,
    FeatureSnapshotBuilder,
)
from backend.feature_infrastructure.contract import FeatureContract
from backend.feature_infrastructure.exceptions import (
    FeatureMissingError,
    FeatureSchemaMismatchError,
    FeatureTypeError,
    FeatureValueError,
    FeatureSourceStateError,
)
from backend.feature_infrastructure.models import (
    ActorType,
    DeviceHistory,
    LocationEligibilityStatus,
    LocationHistory,
    LocationSource,
    FeatureSnapshot,
    TrustedContact,
    TrustedContactEvent,
    TrustedContactEventType,
    TrustedContactStatus,
)
from backend.feature_infrastructure.verification import FeatureInfrastructureVerifier


NOW = datetime(2026, 7, 6, 12, 0, tzinfo=timezone.utc)
FIXED_SNAPSHOT_ID = UUID("00000000-0000-0000-0000-000000000001")


class FeatureInfrastructureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = FeatureContract.load()
        cls.builder = FeatureSnapshotBuilder(
            cls.contract, clock=lambda: NOW, id_factory=lambda: FIXED_SNAPSHOT_ID
        )

    def test_contract_integrity_and_order(self) -> None:
        result = FeatureInfrastructureVerifier(self.contract).verify_contract("2.0.0")
        self.assertEqual(result.feature_count, 6)
        self.assertEqual(
            self.contract.feature_names,
            (
                "trusted_contact",
                "trusted_contact_age",
                "device_change_flag",
                "location_anomaly",
                "location_distance",
                "known_device",
            ),
        )

    def test_domain_models_reject_naive_timestamps(self) -> None:
        with self.assertRaises(ValidationError):
            TrustedContactEvent(
                event_id=uuid4(),
                user_id=uuid4(),
                receiver_id=uuid4(),
                event_type=TrustedContactEventType.ADDED,
                event_ts=datetime(2026, 7, 1),
                event_sequence=1,
                actor_type=ActorType.USER,
                source="android-v1",
                ingested_at=NOW,
            )

    def test_builds_complete_snapshot_and_stable_json(self) -> None:
        request = self._complete_request(known_device=False)
        snapshot = self.builder.build(request)

        self.assertEqual(snapshot.features.trusted_contact, 1)
        self.assertEqual(snapshot.features.trusted_contact_age, 10.0)
        self.assertEqual(snapshot.features.device_change_flag, 1)
        self.assertEqual(snapshot.features.known_device, 0)
        self.assertGreater(snapshot.features.location_distance, 100.0)
        self.assertEqual(snapshot.features.location_anomaly, 1)

        payload = json.loads(snapshot.to_json())
        self.assertEqual(payload["schema_version"], "2.0.0")
        self.assertIsNone(payload["model_version"])
        self.assertEqual(list(payload["features"]), list(self.contract.feature_names))
        self.assertEqual(FeatureSnapshot.model_validate_json(snapshot.to_json()), snapshot)
        FeatureInfrastructureVerifier(self.contract).verify_snapshot(snapshot)

    def test_known_device_is_not_marked_as_changed(self) -> None:
        snapshot = self.builder.build(self._complete_request(known_device=True))
        self.assertEqual(snapshot.features.known_device, 1)
        self.assertEqual(snapshot.features.device_change_flag, 0)

    def test_cold_start_and_missing_location_use_documented_defaults(self) -> None:
        request = FeatureSnapshotBuildRequest(
            transaction_id=uuid4(),
            user_id=uuid4(),
            receiver_id=uuid4(),
            cutoff_ts=NOW - timedelta(minutes=1),
        )
        snapshot = self.builder.build(request)
        self.assertEqual(snapshot.features.known_device, 0)
        self.assertEqual(snapshot.features.device_change_flag, 0)
        self.assertEqual(snapshot.features.location_distance, -1.0)
        self.assertEqual(snapshot.features.location_anomaly, 0)

    def test_contract_rejects_missing_wrong_type_and_invalid_value(self) -> None:
        valid = {
            "trusted_contact": 0,
            "trusted_contact_age": 0.0,
            "device_change_flag": 0,
            "location_anomaly": 0,
            "location_distance": -1.0,
            "known_device": 0,
        }
        missing = dict(valid)
        missing.pop("known_device")
        with self.assertRaises(FeatureMissingError):
            self.contract.validate_feature_mapping(missing)

        wrong_type = dict(valid)
        wrong_type["known_device"] = False
        with self.assertRaises(FeatureTypeError):
            self.contract.validate_feature_mapping(wrong_type)

        invalid = dict(valid)
        invalid["trusted_contact"] = 2
        with self.assertRaises(FeatureValueError):
            self.contract.validate_feature_mapping(invalid)

    def test_contract_rejects_order_and_extra_fields(self) -> None:
        reordered = {
            "known_device": 0,
            "trusted_contact": 0,
            "trusted_contact_age": 0.0,
            "device_change_flag": 0,
            "location_anomaly": 0,
            "location_distance": -1.0,
        }
        with self.assertRaises(FeatureSchemaMismatchError):
            self.contract.validate_feature_mapping(reordered)

        ordered_with_extra = {
            "trusted_contact": 0,
            "trusted_contact_age": 0.0,
            "device_change_flag": 0,
            "location_anomaly": 0,
            "location_distance": -1.0,
            "known_device": 0,
            "unexpected": 0,
        }
        with self.assertRaises(FeatureSchemaMismatchError):
            self.contract.validate_feature_mapping(ordered_with_extra)

    def test_builder_rejects_state_unavailable_at_cutoff(self) -> None:
        request = self._complete_request(known_device=True)
        device = request.prior_devices[0].model_copy(
            update={"last_seen_ts": request.cutoff_ts}
        )
        invalid_request = request.model_copy(update={"prior_devices": (device,)})
        with self.assertRaises(FeatureSourceStateError):
            self.builder.build(invalid_request)

    def _complete_request(self, *, known_device: bool) -> FeatureSnapshotBuildRequest:
        user_id = uuid4()
        receiver_id = uuid4()
        transaction_id = uuid4()
        cutoff = NOW - timedelta(minutes=1)
        fingerprint = "b" * 64 if known_device else "c" * 64
        prior_fingerprint = "b" * 64

        contact = TrustedContact(
            id=uuid4(),
            user_id=user_id,
            receiver_id=receiver_id,
            status=TrustedContactStatus.ACTIVE,
            active_since_ts=cutoff - timedelta(days=10),
            last_verified_ts=cutoff - timedelta(days=2),
            last_event_id=uuid4(),
            version=2,
            created_at=cutoff - timedelta(days=20),
            updated_at=cutoff - timedelta(days=10),
        )
        device = DeviceHistory(
            id=uuid4(),
            user_id=user_id,
            fingerprint_hash=prior_fingerprint,
            fingerprint_version="v1",
            first_seen_ts=cutoff - timedelta(days=30),
            last_seen_ts=cutoff - timedelta(days=2),
            first_seen_transaction_id=uuid4(),
            last_seen_transaction_id=uuid4(),
            successful_payment_count=3,
            created_at=cutoff - timedelta(days=30),
            updated_at=cutoff - timedelta(days=2),
        )
        locations = tuple(
            LocationHistory(
                id=uuid4(),
                user_id=user_id,
                transaction_id=uuid4(),
                event_ts=cutoff - timedelta(days=index + 1),
                event_sequence=index + 1,
                latitude=12.9716 + index * 0.001,
                longitude=77.5946 + index * 0.001,
                accuracy_m=20.0,
                source=LocationSource.GPS,
                consent_version="v1",
                eligibility_status=LocationEligibilityStatus.ELIGIBLE,
                ingested_at=cutoff - timedelta(days=index + 1) + timedelta(seconds=1),
                created_at=cutoff - timedelta(days=index + 1) + timedelta(seconds=1),
            )
            for index in range(3)
        )
        return FeatureSnapshotBuildRequest(
            transaction_id=transaction_id,
            user_id=user_id,
            receiver_id=receiver_id,
            cutoff_ts=cutoff,
            trusted_contact=contact,
            current_device_fingerprint=fingerprint,
            current_device_fingerprint_version="v1",
            prior_devices=(device,),
            current_location=CurrentLocation(
                latitude=28.6139,
                longitude=77.2090,
                accuracy_m=15.0,
            ),
            location_history=locations,
        )


if __name__ == "__main__":
    unittest.main()
