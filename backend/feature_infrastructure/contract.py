"""Loader and strict validator for the shared Model V2 feature contract."""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any, Mapping

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from backend.feature_infrastructure.exceptions import (
    FeatureContractError,
    FeatureMissingError,
    FeatureSchemaMismatchError,
    FeatureTypeError,
    FeatureValueError,
)


DEFAULT_CONTRACT_PATH = (
    Path(__file__).resolve().parents[2] / "shared" / "contracts" / "feature_schema_v2.json"
)


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)


class ValidationRules(ContractModel):
    allowed_values: list[int] | None = None
    minimum: float | None = None
    finite: bool = False


class FeatureDefinition(ContractModel):
    name: str = Field(min_length=1)
    version: str = Field(min_length=1)
    type: str
    storage_type: str
    required: bool
    encoding: dict[str, Any]
    default: int | float
    validation: ValidationRules

    @model_validator(mode="after")
    def validate_supported_type(self) -> "FeatureDefinition":
        if self.type not in {"integer", "number"}:
            raise ValueError(f"Unsupported feature type: {self.type}")
        return self


class LocationPolicy(ContractModel):
    version: str
    lookback_days: int = Field(ge=1)
    minimum_observations: int = Field(ge=1)
    maximum_accuracy_m: float = Field(gt=0.0)
    anomaly_threshold_km: float = Field(gt=0.0)
    earth_radius_km: float = Field(gt=0.0)


class CrossFieldRule(ContractModel):
    if_feature: str
    if_equals: int | float
    then_feature: str
    then_equals: int | float
    message: str = Field(min_length=1)


class FeatureContractDocument(ContractModel):
    contract_id: str
    schema_version: str
    schema_hash: str
    transformer_version: str
    device_fingerprint_version: str
    aliases: dict[str, str]
    location_policy: LocationPolicy
    features: list[FeatureDefinition]
    cross_field_rules: list[CrossFieldRule]

    @model_validator(mode="after")
    def validate_unique_features(self) -> "FeatureContractDocument":
        names = [feature.name for feature in self.features]
        if len(names) != len(set(names)):
            raise ValueError("Feature names must be unique.")
        if not self.features:
            raise ValueError("At least one feature is required.")
        name_set = set(names)
        for feature in self.features:
            if feature.version != self.schema_version:
                raise ValueError(
                    f"Feature {feature.name} version must equal schema_version."
                )
        for rule in self.cross_field_rules:
            if rule.if_feature not in name_set or rule.then_feature not in name_set:
                raise ValueError("Cross-field rules must reference declared features.")
        if any(target not in name_set for target in self.aliases.values()):
            raise ValueError("Aliases must resolve to declared canonical features.")
        if any(alias in name_set for alias in self.aliases):
            raise ValueError("Aliases cannot duplicate canonical feature names.")
        return self


class FeatureContract:
    """Immutable shared contract with exact schema and value validation."""

    def __init__(self, document: FeatureContractDocument, raw_document: Mapping[str, Any]):
        self.document = document
        self._raw_document = dict(raw_document)
        self.verify_integrity()

    @classmethod
    def load(cls, path: Path = DEFAULT_CONTRACT_PATH) -> "FeatureContract":
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except FileNotFoundError as exc:
            raise FeatureContractError(f"Feature contract not found: {path}") from exc
        except json.JSONDecodeError as exc:
            raise FeatureContractError(f"Feature contract is invalid JSON: {exc}") from exc

        if not isinstance(raw, dict):
            raise FeatureContractError("Feature contract root must be an object.")
        try:
            document = FeatureContractDocument.model_validate(raw)
        except ValidationError as exc:
            raise FeatureContractError(f"Feature contract structure is invalid: {exc}") from exc
        return cls(document, raw)

    @property
    def feature_names(self) -> tuple[str, ...]:
        return tuple(feature.name for feature in self.document.features)

    @property
    def schema_version(self) -> str:
        return self.document.schema_version

    @property
    def schema_hash(self) -> str:
        return self.document.schema_hash

    def calculated_schema_hash(self) -> str:
        hashable = dict(self._raw_document)
        hashable.pop("schema_hash", None)
        canonical = json.dumps(
            hashable, ensure_ascii=False, allow_nan=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        return hashlib.sha256(canonical).hexdigest()

    def verify_integrity(self) -> None:
        actual_hash = self.calculated_schema_hash()
        if self.document.schema_hash != actual_hash:
            raise FeatureContractError(
                "Feature contract hash mismatch: "
                f"declared={self.document.schema_hash}, calculated={actual_hash}."
            )
        defaults = {
            definition.name: definition.default for definition in self.document.features
        }
        self.validate_feature_mapping(defaults)

    def validate_feature_mapping(self, values: Mapping[str, Any]) -> None:
        expected = self.feature_names
        actual = tuple(values.keys())
        missing = [name for name in expected if name not in values]
        if missing:
            raise FeatureMissingError(f"Missing required features: {', '.join(missing)}.")
        extras = [name for name in actual if name not in expected]
        if extras or actual != expected:
            raise FeatureSchemaMismatchError(
                f"Feature schema mismatch: expected ordered fields {expected}, received {actual}."
            )

        for definition in self.document.features:
            self._validate_value(definition, values[definition.name])
        self._validate_cross_field_rules(values)

    @staticmethod
    def _validate_value(definition: FeatureDefinition, value: Any) -> None:
        if definition.type == "integer":
            if type(value) is not int:
                raise FeatureTypeError(
                    f"{definition.name} must be an integer, got {type(value).__name__}."
                )
        elif type(value) is not float:
            raise FeatureTypeError(
                f"{definition.name} must be a float, got {type(value).__name__}."
            )

        rules = definition.validation
        if rules.finite and not math.isfinite(value):
            raise FeatureValueError(f"{definition.name} must be finite.")
        if rules.minimum is not None and value < rules.minimum:
            raise FeatureValueError(
                f"{definition.name} must be >= {rules.minimum}, got {value}."
            )
        if rules.allowed_values is not None and value not in rules.allowed_values:
            raise FeatureValueError(
                f"{definition.name} must be one of {rules.allowed_values}, got {value}."
            )

    def _validate_cross_field_rules(self, values: Mapping[str, Any]) -> None:
        for rule in self.document.cross_field_rules:
            if (
                values[rule.if_feature] == rule.if_equals
                and values[rule.then_feature] != rule.then_equals
            ):
                raise FeatureValueError(rule.message)
