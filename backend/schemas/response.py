from pydantic import BaseModel, Field

from backend.risk_engine.models import AppliedRule


class PredictionResponse(BaseModel):
    # Legacy fields retain their original XGBoost semantics for existing clients.
    risk_score: float = Field(..., ge=0, le=100)
    risk_level: str
    confidence: float = Field(..., ge=0, le=100)
    reasons: list[str]
    recommendation: str
    prediction_time_ms: int = Field(..., ge=0)

    # Hybrid V2 fields expose the contextual decision without overwriting AI output.
    ai_score: float = Field(..., ge=0, le=100)
    adjusted_score: float = Field(..., ge=0, le=100)
    applied_rules: list[AppliedRule]
    final_risk: str
    final_recommendation: str
    rule_config_version: str
