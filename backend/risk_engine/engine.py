"""Configuration loader and deterministic hybrid score orchestration."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from backend.risk_engine.models import AppliedRule, HybridRiskDecision, RiskRuleContext
from backend.risk_engine.rules import BusinessRule, RULE_TYPES


DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "risk_rules_v1.json"


class ConfigurationError(ValueError):
    """The risk rule configuration is missing, invalid, or incomplete."""


class ConfigModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True, allow_inf_nan=False)


class RiskThresholds(ConfigModel):
    medium: float = Field(ge=0.0, le=100.0)
    high: float = Field(ge=0.0, le=100.0)
    critical: float = Field(ge=0.0, le=100.0)

    @model_validator(mode="after")
    def validate_order(self) -> "RiskThresholds":
        if not self.medium < self.high < self.critical:
            raise ValueError("Risk thresholds must be strictly increasing.")
        return self


class RuleConfiguration(ConfigModel):
    rule_id: str
    enabled: bool
    adjustment: float
    parameters: dict[str, Any]
    explanation: str = Field(min_length=1)


class RiskEngineConfiguration(ConfigModel):
    config_version: str
    minimum_score: float
    maximum_score: float
    risk_thresholds: RiskThresholds
    recommendations: dict[str, str]
    rules: list[RuleConfiguration]

    @model_validator(mode="after")
    def validate_configuration(self) -> "RiskEngineConfiguration":
        if self.minimum_score != 0.0 or self.maximum_score != 100.0:
            raise ValueError("FORESIGHT score bounds must be 0.0 and 100.0.")
        rule_ids = [rule.rule_id for rule in self.rules]
        if len(rule_ids) != len(set(rule_ids)):
            raise ValueError("Rule IDs must be unique.")
        unknown = set(rule_ids) - set(RULE_TYPES)
        missing = set(RULE_TYPES) - set(rule_ids)
        if unknown or missing:
            raise ValueError(f"Rule registry mismatch; unknown={unknown}, missing={missing}.")
        expected_risks = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
        if set(self.recommendations) != expected_risks:
            raise ValueError("Recommendations must define every risk level exactly once.")
        return self


class HybridRiskEngine:
    """Sequentially adjusts an AI score with configured, explainable rules."""

    def __init__(self, configuration: RiskEngineConfiguration):
        self.configuration = configuration
        try:
            self._rules = self._build_rules(configuration)
        except (KeyError, TypeError, ValueError) as exc:
            raise ConfigurationError(f"Risk rule parameters are invalid: {exc}") from exc

    @classmethod
    def from_file(cls, path: Path = DEFAULT_CONFIG_PATH) -> "HybridRiskEngine":
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except FileNotFoundError as exc:
            raise ConfigurationError(f"Risk rule configuration not found: {path}") from exc
        except json.JSONDecodeError as exc:
            raise ConfigurationError(f"Risk rule configuration is invalid JSON: {exc}") from exc
        try:
            configuration = RiskEngineConfiguration.model_validate(raw)
        except ValidationError as exc:
            raise ConfigurationError(f"Risk rule configuration is invalid: {exc}") from exc
        return cls(configuration)

    @staticmethod
    def _build_rules(configuration: RiskEngineConfiguration) -> tuple[BusinessRule, ...]:
        return tuple(
            RULE_TYPES[item.rule_id](item.adjustment, item.explanation, item.parameters)
            for item in configuration.rules
            if item.enabled
        )

    def evaluate(self, ai_score: float, context: RiskRuleContext) -> HybridRiskDecision:
        if type(ai_score) is not float:
            raise TypeError(f"ai_score must be a float, got {type(ai_score).__name__}.")
        if not 0.0 <= ai_score <= 100.0:
            raise ValueError("ai_score must be between 0.0 and 100.0.")

        current_score = round(ai_score, 2)
        applied: list[AppliedRule] = []
        for rule in self._rules:
            evaluation = rule.evaluate(context)
            if evaluation is None:
                continue
            score_before = current_score
            current_score = round(
                min(
                    self.configuration.maximum_score,
                    max(
                        self.configuration.minimum_score,
                        current_score + evaluation.adjustment,
                    ),
                ),
                2,
            )
            applied.append(
                AppliedRule(
                    rule_id=evaluation.rule_id,
                    rule_name=evaluation.rule_name,
                    configured_adjustment=evaluation.adjustment,
                    adjustment=round(current_score - score_before, 2),
                    explanation=evaluation.explanation,
                    score_before=score_before,
                    score_after=current_score,
                )
            )

        risk = self.risk_level_for(current_score)
        return HybridRiskDecision(
            config_version=self.configuration.config_version,
            ai_score=round(ai_score, 2),
            adjusted_score=current_score,
            applied_rules=tuple(applied),
            final_risk=risk,
            final_recommendation=self.configuration.recommendations[risk],
        )

    def risk_level_for(self, score: float) -> str:
        thresholds = self.configuration.risk_thresholds
        if score < thresholds.medium:
            return "LOW"
        if score < thresholds.high:
            return "MEDIUM"
        if score < thresholds.critical:
            return "HIGH"
        return "CRITICAL"
