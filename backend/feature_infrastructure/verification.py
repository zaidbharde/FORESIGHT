"""Reusable contract and snapshot verification utilities."""

from __future__ import annotations

from dataclasses import dataclass

from backend.feature_infrastructure.builder import FeatureSnapshotBuilder
from backend.feature_infrastructure.contract import FeatureContract
from backend.feature_infrastructure.exceptions import FeatureSchemaMismatchError
from backend.feature_infrastructure.models import FeatureSnapshot


@dataclass(frozen=True, slots=True)
class VerificationResult:
    contract_version: str
    schema_hash: str
    feature_count: int
    vector_hash: str | None = None


class FeatureInfrastructureVerifier:
    """Fails fast on contract drift, missing fields, types, values, and hashes."""

    def __init__(self, contract: FeatureContract) -> None:
        self.contract = contract

    def verify_contract(self, expected_version: str | None = None) -> VerificationResult:
        self.contract.verify_integrity()
        if expected_version is not None and self.contract.schema_version != expected_version:
            raise FeatureSchemaMismatchError(
                "Feature contract version mismatch: "
                f"expected={expected_version}, actual={self.contract.schema_version}."
            )
        return VerificationResult(
            contract_version=self.contract.schema_version,
            schema_hash=self.contract.schema_hash,
            feature_count=len(self.contract.feature_names),
        )

    def verify_snapshot(self, snapshot: FeatureSnapshot) -> VerificationResult:
        if snapshot.schema_version != self.contract.schema_version:
            raise FeatureSchemaMismatchError(
                "Feature snapshot contract version does not match the loaded contract."
            )
        if snapshot.schema_hash != self.contract.schema_hash:
            raise FeatureSchemaMismatchError(
                "Feature snapshot schema hash does not match the loaded contract."
            )
        if snapshot.transformer_version != self.contract.document.transformer_version:
            raise FeatureSchemaMismatchError("Feature snapshot transformer version mismatch.")
        if (
            snapshot.device_fingerprint_version
            != self.contract.document.device_fingerprint_version
        ):
            raise FeatureSchemaMismatchError("Feature snapshot fingerprint version mismatch.")
        if snapshot.location_policy_version != self.contract.document.location_policy.version:
            raise FeatureSchemaMismatchError("Feature snapshot location policy version mismatch.")

        values = snapshot.features.model_dump()
        self.contract.validate_feature_mapping(values)
        calculated_vector_hash = FeatureSnapshotBuilder._hash_vector(values)
        if snapshot.vector_hash != calculated_vector_hash:
            raise FeatureSchemaMismatchError("Feature snapshot vector hash is invalid.")
        return VerificationResult(
            contract_version=self.contract.schema_version,
            schema_hash=self.contract.schema_hash,
            feature_count=len(values),
            vector_hash=snapshot.vector_hash,
        )
