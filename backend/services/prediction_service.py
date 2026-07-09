from __future__ import annotations

import logging
from pathlib import Path
from time import perf_counter
from typing import Any

import joblib
import pandas as pd
from fastapi import HTTPException

from backend.schemas.request import PredictionRequest
from backend.schemas.response import PredictionResponse
from backend.risk_engine import HybridRiskEngine, RiskRuleContext


logger = logging.getLogger(__name__)


class PredictionService:
    def __init__(self, rule_engine: HybridRiskEngine | None = None) -> None:
        self.model: Any | None = None
        self.model_path: Path | None = None
        self.rule_engine = rule_engine or HybridRiskEngine.from_file()

    def load_model(self) -> None:
        if self.model is not None:
            return

        model_path = self._resolve_model_path()
        if model_path is None:
            self.model = None
            self.model_path = None
            logger.error("xgboost_baseline.pkl was not found.")
            return

        try:
            self.model = joblib.load(model_path)
            self.model_path = model_path
            logger.info("Loaded model from %s", model_path)
        except Exception:
            self.model = None
            self.model_path = None
            logger.exception("Failed to load model from %s", model_path)

    def validate_request(self, payload: PredictionRequest) -> None:
        if payload.amount <= 0:
            raise HTTPException(
                status_code=400,
                detail="amount must be greater than 0.",
            )
        if payload.hour < 0 or payload.hour > 23:
            raise HTTPException(
                status_code=400,
                detail="hour must be between 0 and 23.",
            )
        if payload.transactions_last_hour < 0:
            raise HTTPException(
                status_code=400,
                detail="transactions_last_hour must be greater than or equal to 0.",
            )
        if payload.transactions_last_24h < 0:
            raise HTTPException(
                status_code=400,
                detail="transactions_last_24h must be greater than or equal to 0.",
            )

    def predict(self, payload: PredictionRequest) -> PredictionResponse:
        if self.model is None:
            raise HTTPException(status_code=500, detail="Model not loaded.")

        start_time = perf_counter()

        try:
            features = self._build_feature_frame(payload)
            probability = self._predict_probability(features)
            risk_score = round(probability * 100.0, 2)
            confidence = round(probability * 100.0, 2)
            risk_level = self.calculate_risk_level(risk_score)
            recommendation = self.generate_recommendation(risk_level)
            reasons = self._build_reasons(payload, risk_score, confidence)
            hybrid_decision = self.rule_engine.evaluate(
                float(risk_score),
                RiskRuleContext(
                    amount=float(payload.amount),
                    trusted_contact=payload.trusted_contact,
                    known_device=not payload.new_device,
                    location_anomaly=payload.location_anomaly,
                    hour=int(payload.hour),
                    transactions_last_hour=int(payload.transactions_last_hour),
                    transactions_last_24h=int(payload.transactions_last_24h),
                ),
            )
            prediction_time_ms = max(0, int((perf_counter() - start_time) * 1000))

            self._log_prediction(
                payload,
                risk_level,
                hybrid_decision.final_risk,
                prediction_time_ms,
            )

            return PredictionResponse(
                risk_score=risk_score,
                risk_level=risk_level,
                confidence=confidence,
                reasons=reasons,
                recommendation=recommendation,
                prediction_time_ms=prediction_time_ms,
                ai_score=hybrid_decision.ai_score,
                adjusted_score=hybrid_decision.adjusted_score,
                applied_rules=list(hybrid_decision.applied_rules),
                final_risk=hybrid_decision.final_risk,
                final_recommendation=hybrid_decision.final_recommendation,
                rule_config_version=hybrid_decision.config_version,
            )
        except HTTPException:
            raise
        except Exception:
            logger.exception("Prediction failed")
            raise HTTPException(status_code=500, detail="Prediction failed.")

    def calculate_risk_level(self, risk_score: float) -> str:
        if risk_score < 30:
            return "LOW"
        if risk_score < 60:
            return "MEDIUM"
        if risk_score < 80:
            return "HIGH"
        return "CRITICAL"

    def generate_recommendation(self, risk_level: str) -> str:
        recommendations = {
            "LOW": "Proceed normally.",
            "MEDIUM": "Review before payment.",
            "HIGH": "Require additional verification.",
            "CRITICAL": "Recommend blocking payment.",
        }
        return recommendations.get(risk_level, "Review before payment.")

    def _resolve_model_path(self) -> Path | None:
        filename = "xgboost_baseline.pkl"
        project_root = Path(__file__).resolve().parents[2]
        candidates = [
            project_root / filename,
            project_root / "VISION ON" / filename,
            project_root / "backend" / filename,
        ]
        for candidate in candidates:
            if candidate.exists():
                return candidate
        return None

    def _predict_probability(self, features: pd.DataFrame) -> float:
        if hasattr(self.model, "predict_proba"):
            probability = float(self.model.predict_proba(features)[0][1])
        else:
            raw_prediction = self.model.predict(features)[0]
            probability = float(raw_prediction)

        return max(0.0, min(1.0, probability))

    def _build_feature_frame(self, payload: PredictionRequest) -> pd.DataFrame:
        amount = float(payload.amount)
        hour = int(payload.hour)
        last_hour = int(payload.transactions_last_hour)
        last_day = int(payload.transactions_last_24h)

        feature_values = {
            "TransactionAmt": amount,
            "card1": 0.0,
            "card2": 0.0,
            "card3": 0.0,
            "card4": "unknown",
            "card5": 0.0,
            "card6": "unknown",
            "addr1": 0.0,
            "addr2": 0.0,
            "ProductCD": "unknown",
            "DeviceType": "unknown",
            "DeviceInfo": "unknown",
            "id_30": "unknown",
            "id_31": "unknown",
            "P_emaildomain": "unknown",
            "R_emaildomain": "unknown",
            "hour_of_day": hour,
            "is_late_night": int(hour <= 5),
            "is_large_amount": int(amount >= 1000.0),
            "is_round_amount": int(abs(amount - round(amount)) < 1e-9 and int(round(amount)) % 10 == 0),
            "is_trusted_contact": int(payload.trusted_contact),
            "amount_to_average_ratio": amount / max(last_day, 1),
            "transactions_last_hour": last_hour,
            "transactions_last_day": last_day,
            "days_since_last_transaction": 1.0 / max(last_day, 1),
            "device_change_flag": int(payload.new_device),
        }

        feature_names = list(getattr(self.model, "feature_names_in_", feature_values.keys()))
        return pd.DataFrame([feature_values], columns=feature_names)

    def _build_reasons(
        self, payload: PredictionRequest, risk_score: float, confidence: float
    ) -> list[str]:
        reasons: list[str] = []

        if payload.amount >= 1000:
            reasons.append("Large transaction amount.")
        if payload.new_device:
            reasons.append("New device detected.")
        if payload.location_anomaly:
            reasons.append("Location anomaly detected.")
        if not payload.trusted_contact:
            reasons.append("Trusted contact not confirmed.")
        else:
            reasons.append("Trusted contact present.")
        if payload.hour >= 22 or payload.hour <= 5:
            reasons.append("Late-night transaction.")
        if payload.transactions_last_hour >= 3:
            reasons.append("Frequent recent payments.")
        if payload.transactions_last_24h >= 10:
            reasons.append("High transaction volume in the last 24 hours.")

        if confidence >= 80:
            reasons.append("Model confidence is high.")
        elif confidence >= 60:
            reasons.append("Model confidence is moderate.")
        elif confidence >= 30:
            reasons.append("Model confidence is low-moderate.")

        if not reasons:
            reasons.append("No major anomalies detected.")

        return reasons

    def _log_prediction(
        self,
        payload: PredictionRequest,
        ai_risk_level: str,
        final_risk_level: str,
        prediction_time_ms: int,
    ) -> None:
        timestamp = pd.Timestamp.now(tz="UTC").isoformat()
        sanitized_request = {
            "amount": payload.amount,
            "trusted_contact": payload.trusted_contact,
            "new_device": payload.new_device,
            "location_anomaly": payload.location_anomaly,
            "hour": payload.hour,
            "transactions_last_hour": payload.transactions_last_hour,
            "transactions_last_24h": payload.transactions_last_24h,
        }
        logger.info(
            "timestamp=%s request=%s ai_risk_level=%s final_risk_level=%s execution_time_ms=%s",
            timestamp,
            sanitized_request,
            ai_risk_level,
            final_risk_level,
            prediction_time_ms,
        )


prediction_service = PredictionService()


def get_prediction_service() -> PredictionService:
    return prediction_service
