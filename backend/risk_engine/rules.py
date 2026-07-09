"""Isolated deterministic business rules used by the hybrid risk engine."""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, Mapping

from backend.risk_engine.models import RiskRuleContext, RuleEvaluation


class BusinessRule(ABC):
    """One independently testable contextual risk adjustment."""

    rule_id: str
    rule_name: str

    def __init__(self, adjustment: float, explanation: str, parameters: Mapping[str, Any]):
        self.adjustment = adjustment
        self.explanation = explanation
        self.parameters = dict(parameters)
        if adjustment == 0.0:
            raise ValueError(f"{self.rule_id} adjustment cannot be zero.")
        self.validate_configuration()

    def evaluate(self, context: RiskRuleContext) -> RuleEvaluation | None:
        if not self.matches(context):
            return None
        return RuleEvaluation(
            rule_id=self.rule_id,
            rule_name=self.rule_name,
            adjustment=float(self.adjustment),
            explanation=self.explanation,
        )

    @abstractmethod
    def matches(self, context: RiskRuleContext) -> bool:
        """Return whether this rule applies to the supplied business context."""

    @abstractmethod
    def validate_configuration(self) -> None:
        """Reject invalid adjustments or parameters during engine startup."""

    def require_adjustment_direction(self, *, positive: bool) -> None:
        if positive and self.adjustment <= 0.0:
            raise ValueError(f"{self.rule_id} adjustment must be positive.")
        if not positive and self.adjustment >= 0.0:
            raise ValueError(f"{self.rule_id} adjustment must be negative.")

    def require_no_parameters(self) -> None:
        if self.parameters:
            raise ValueError(f"{self.rule_id} does not accept parameters.")


class TrustedContactRule(BusinessRule):
    """Reduce risk when the receiver is an explicitly trusted contact."""

    rule_id = "trusted_contact"
    rule_name = "Trusted Contact"

    def matches(self, context: RiskRuleContext) -> bool:
        return context.trusted_contact

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=False)
        self.require_no_parameters()


class KnownDeviceRule(BusinessRule):
    """Reduce risk when the current device is known for this user."""

    rule_id = "known_device"
    rule_name = "Known Device"

    def matches(self, context: RiskRuleContext) -> bool:
        return context.known_device

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=False)
        self.require_no_parameters()


class LocationAnomalyRule(BusinessRule):
    """Increase risk when the feature layer reports a location anomaly."""

    rule_id = "location_anomaly"
    rule_name = "Location Anomaly"

    def matches(self, context: RiskRuleContext) -> bool:
        return context.location_anomaly

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=True)
        self.require_no_parameters()


class LargeAmountRule(BusinessRule):
    """Increase risk at or above the configured high-value threshold."""

    rule_id = "large_amount"
    rule_name = "Large Amount"

    def matches(self, context: RiskRuleContext) -> bool:
        threshold = float(self.parameters["minimum_amount"])
        return context.amount >= threshold

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=True)
        if set(self.parameters) != {"minimum_amount"}:
            raise ValueError("large_amount requires only minimum_amount.")
        threshold = self.parameters["minimum_amount"]
        if type(threshold) not in {int, float} or threshold <= 0:
            raise ValueError("large_amount minimum_amount must be a positive number.")


class LateNightRule(BusinessRule):
    """Increase risk during the configured overnight interval, inclusive."""

    rule_id = "late_night"
    rule_name = "Late Night"

    def matches(self, context: RiskRuleContext) -> bool:
        start = int(self.parameters["start_hour"])
        end = int(self.parameters["end_hour"])
        if start <= end:
            return start <= context.hour <= end
        return context.hour >= start or context.hour <= end

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=True)
        if set(self.parameters) != {"start_hour", "end_hour"}:
            raise ValueError("late_night requires start_hour and end_hour.")
        for name in ("start_hour", "end_hour"):
            value = self.parameters[name]
            if type(value) is not int or not 0 <= value <= 23:
                raise ValueError(f"late_night {name} must be an integer from 0 to 23.")


class ManyTransactionsRule(BusinessRule):
    """Increase risk when either short- or daily-window velocity is high."""

    rule_id = "many_transactions"
    rule_name = "Many Transactions"

    def matches(self, context: RiskRuleContext) -> bool:
        hourly = int(self.parameters["last_hour_threshold"])
        daily = int(self.parameters["last_24h_threshold"])
        return (
            context.transactions_last_hour >= hourly
            or context.transactions_last_24h >= daily
        )

    def validate_configuration(self) -> None:
        self.require_adjustment_direction(positive=True)
        required = {"last_hour_threshold", "last_24h_threshold"}
        if set(self.parameters) != required:
            raise ValueError("many_transactions requires hourly and daily thresholds.")
        for name in required:
            value = self.parameters[name]
            if type(value) is not int or value <= 0:
                raise ValueError(f"many_transactions {name} must be a positive integer.")


RULE_TYPES: dict[str, type[BusinessRule]] = {
    rule.rule_id: rule
    for rule in (
        TrustedContactRule,
        KnownDeviceRule,
        LocationAnomalyRule,
        LargeAmountRule,
        LateNightRule,
        ManyTransactionsRule,
    )
}
