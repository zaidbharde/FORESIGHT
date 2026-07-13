"""Strict inputs and explainable outputs for the hybrid risk engine."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class RiskEngineModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True, allow_inf_nan=False)


class RiskRuleContext(RiskEngineModel):
    """Business context evaluated after the statistical model has scored payment."""

    amount: float = Field(gt=0.0)
    trusted_contact: bool
    known_device: bool
    location_anomaly: bool
    hour: int = Field(ge=0, le=23)
    transactions_last_hour: int = Field(ge=0)
    transactions_last_24h: int = Field(ge=0)
    sim_recently_changed: bool
    active_call: bool
    device_anomaly: bool
    account_history_days: int = Field(ge=0)
    first_time_beneficiary: bool


class RuleEvaluation(RiskEngineModel):
    """A matched rule before score bounds are applied by the engine."""

    rule_id: str
    rule_name: str
    adjustment: float
    explanation: str


class AppliedRule(RiskEngineModel):
    """Complete audit record for one deterministic score adjustment."""

    rule_id: str
    rule_name: str
    configured_adjustment: float
    adjustment: float
    explanation: str
    score_before: float = Field(ge=0.0, le=100.0)
    score_after: float = Field(ge=0.0, le=100.0)


class HybridRiskDecision(RiskEngineModel):
    """Final bounded decision while retaining the original statistical AI score."""

    config_version: str
    ai_score: float = Field(ge=0.0, le=100.0)
    adjusted_score: float = Field(ge=0.0, le=100.0)
    applied_rules: tuple[AppliedRule, ...]
    final_risk: str
    final_recommendation: str
