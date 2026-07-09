from __future__ import annotations

import unittest

from backend.risk_engine import HybridRiskEngine, RiskRuleContext
from backend.schemas.request import PredictionRequest
from backend.services.prediction_service import PredictionService


class FixedProbabilityModel:
    """Deterministic test double; production tests still exercise the real service path."""

    def predict_proba(self, features: object) -> list[list[float]]:
        return [[0.38, 0.62]]


class HybridRiskEngineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.engine = HybridRiskEngine.from_file()

    def test_case_1_trusted_contact_known_device_lowers_score(self) -> None:
        decision = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=True, known_device=True),
        )
        self.assertEqual(decision.adjusted_score, 47.0)
        self.assertLess(decision.adjusted_score, decision.ai_score)
        self.assertEqual(
            [rule.rule_id for rule in decision.applied_rules],
            ["trusted_contact", "known_device"],
        )

    def test_case_2_unknown_contact_new_device_is_higher_than_case_1(self) -> None:
        safe_context = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=True, known_device=True),
        )
        unknown_context = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=False, known_device=False),
        )
        self.assertGreater(unknown_context.adjusted_score, safe_context.adjusted_score)
        self.assertEqual(unknown_context.adjusted_score, 62.0)
        self.assertEqual(unknown_context.applied_rules, ())

    def test_case_3_large_anomalous_late_night_payment_is_critical(self) -> None:
        decision = self.engine.evaluate(
            40.0,
            self._context(
                amount=20000.0,
                trusted_contact=False,
                known_device=False,
                location_anomaly=True,
                hour=23,
            ),
        )
        self.assertEqual(decision.adjusted_score, 83.0)
        self.assertEqual(decision.final_risk, "CRITICAL")
        self.assertEqual(
            [rule.rule_id for rule in decision.applied_rules],
            ["location_anomaly", "large_amount", "late_night"],
        )
        self.assertEqual(
            round(sum(rule.adjustment for rule in decision.applied_rules), 2),
            round(decision.adjusted_score - decision.ai_score, 2),
        )
        self.assertEqual(decision.applied_rules[-1].configured_adjustment, 8.0)
        self.assertEqual(decision.applied_rules[-1].adjustment, 8.0)

    def test_case_4_small_trusted_known_payment_is_low(self) -> None:
        decision = self.engine.evaluate(
            20.0,
            self._context(amount=50.0, trusted_contact=True, known_device=True),
        )
        self.assertEqual(decision.adjusted_score, 5.0)
        self.assertEqual(decision.final_risk, "LOW")

    def test_many_transactions_rule_is_explainable_and_deterministic(self) -> None:
        context = self._context(
            amount=500.0,
            trusted_contact=False,
            known_device=False,
            transactions_last_hour=3,
        )
        first = self.engine.evaluate(50.0, context)
        second = self.engine.evaluate(50.0, context)
        self.assertEqual(first, second)
        self.assertEqual(first.adjusted_score, 62.0)
        self.assertEqual(len(first.applied_rules), 1)
        self.assertEqual(first.applied_rules[0].rule_id, "many_transactions")
        self.assertTrue(first.applied_rules[0].explanation)
        self.assertEqual(first.applied_rules[0].score_before, 50.0)
        self.assertEqual(first.applied_rules[0].score_after, 62.0)

    def test_score_clamp_reports_effective_and_configured_adjustment(self) -> None:
        decision = self.engine.evaluate(
            95.0,
            self._context(
                amount=500.0,
                trusted_contact=False,
                known_device=False,
                location_anomaly=True,
            ),
        )
        self.assertEqual(decision.adjusted_score, 100.0)
        self.assertEqual(decision.applied_rules[0].configured_adjustment, 20.0)
        self.assertEqual(decision.applied_rules[0].adjustment, 5.0)

    def test_prediction_service_preserves_ai_fields_and_adds_final_fields(self) -> None:
        service = PredictionService(rule_engine=self.engine)
        service.model = FixedProbabilityModel()
        response = service.predict(
            PredictionRequest(
                amount=500.0,
                trusted_contact=True,
                new_device=False,
                location_anomaly=False,
                hour=14,
                transactions_last_hour=0,
                transactions_last_24h=0,
            )
        )
        self.assertEqual(response.risk_score, 62.0)
        self.assertEqual(response.ai_score, 62.0)
        self.assertEqual(response.risk_level, "HIGH")
        self.assertEqual(response.adjusted_score, 47.0)
        self.assertEqual(response.final_risk, "MEDIUM")
        self.assertEqual(len(response.applied_rules), 2)
        self.assertEqual(response.rule_config_version, "1.0.0")

    @staticmethod
    def _context(
        *,
        amount: float,
        trusted_contact: bool,
        known_device: bool,
        location_anomaly: bool = False,
        hour: int = 14,
        transactions_last_hour: int = 0,
        transactions_last_24h: int = 0,
    ) -> RiskRuleContext:
        return RiskRuleContext(
            amount=amount,
            trusted_contact=trusted_contact,
            known_device=known_device,
            location_anomaly=location_anomaly,
            hour=hour,
            transactions_last_hour=transactions_last_hour,
            transactions_last_24h=transactions_last_24h,
        )


if __name__ == "__main__":
    unittest.main()
