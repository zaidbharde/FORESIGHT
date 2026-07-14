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

    def test_trusted_contact_and_known_device_reduces_score(self) -> None:
        decision = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=True, known_device=True),
        )
        self.assertLess(decision.adjusted_score, decision.ai_score)
        self.assertIn("trusted_contact", [r.rule_id for r in decision.applied_rules])
        self.assertIn("known_device", [r.rule_id for r in decision.applied_rules])

    def test_unknown_contact_new_device_is_higher_than_trusted(self) -> None:
        safe_context = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=True, known_device=True),
        )
        unknown_context = self.engine.evaluate(
            62.0,
            self._context(amount=500.0, trusted_contact=False, known_device=False),
        )
        self.assertGreater(unknown_context.adjusted_score, safe_context.adjusted_score)

    def test_large_anomalous_late_night_payment_is_critical(self) -> None:
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
        self.assertEqual(decision.final_risk, "CRITICAL")
        self.assertIn("location_anomaly", [r.rule_id for r in decision.applied_rules])
        self.assertIn("large_amount", [r.rule_id for r in decision.applied_rules])
        self.assertEqual(
            round(sum(rule.adjustment for rule in decision.applied_rules), 2),
            round(decision.adjusted_score - decision.ai_score, 2),
        )

    def test_late_night_config_adjustment_value(self) -> None:
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
        late_night_rule = next(
            (r for r in decision.applied_rules if r.rule_id == "late_night"), None
        )
        self.assertIsNotNone(late_night_rule)
        self.assertEqual(late_night_rule.configured_adjustment, 10.0)

    def test_small_trusted_known_payment_is_low(self) -> None:
        decision = self.engine.evaluate(
            20.0,
            self._context(amount=50.0, trusted_contact=True, known_device=True),
        )
        self.assertLess(decision.adjusted_score, decision.ai_score)
        self.assertLess(decision.adjusted_score, 30.0)

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
        self.assertIn("many_transactions", [r.rule_id for r in first.applied_rules])
        self.assertTrue(first.applied_rules[0].explanation)

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
        location_rule = next(
            (r for r in decision.applied_rules if r.rule_id == "location_anomaly"), None
        )
        self.assertIsNotNone(location_rule)
        self.assertEqual(location_rule.configured_adjustment, 30.0)

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
        self.assertEqual(response.rule_config_version, "1.3.0")
        self.assertLess(response.adjusted_score, response.ai_score)
        self.assertGreater(len(response.applied_rules), 0)
        self.assertEqual(response.final_recommendation, "Proceed normally.")

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
        sim_recently_changed: bool = False,
        active_call: bool = False,
        device_anomaly: bool = False,
        account_history_days: int = 0,
        first_time_beneficiary: bool = False,
    ) -> RiskRuleContext:
        return RiskRuleContext(
            amount=amount,
            trusted_contact=trusted_contact,
            known_device=known_device,
            location_anomaly=location_anomaly,
            hour=hour,
            transactions_last_hour=transactions_last_hour,
            transactions_last_24h=transactions_last_24h,
            sim_recently_changed=sim_recently_changed,
            active_call=active_call,
            device_anomaly=device_anomaly,
            account_history_days=account_history_days,
            first_time_beneficiary=first_time_beneficiary,
        )


if __name__ == "__main__":
    unittest.main()
