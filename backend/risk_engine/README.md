# FORESIGHT Hybrid Risk Engine

The hybrid engine is a post-model decision layer. It always begins with the
XGBoost score, evaluates enabled rules in configuration order, bounds the result
to `0..100`, and returns both scores plus an auditable rule trail. It never
changes model inputs or model artifacts.

Configuration lives in `backend/config/risk_rules_v1.json`. Changes require a
new `config_version`, review, and scenario-test updates.

| Rule | Trigger | Configured effect | Rationale |
|---|---|---:|---|
| Trusted Contact | Receiver is trusted | `-10` | Established receiver context slightly lowers risk. |
| Known Device | Current device is known | `-5` | Established device context slightly lowers risk. |
| Location Anomaly | Feature layer reports anomaly | `+20` | Unusual location raises contextual risk. |
| Large Amount | Amount is at least `10000` | `+15` | High-value payment raises potential loss exposure. |
| Late Night | Hour is `22..23` or `0..5` | `+8` | Overnight payment slightly raises contextual risk. |
| Many Transactions | At least 3/hour or 10/24h | `+12` | Elevated payment velocity raises risk. |

`AppliedRule.adjustment` is the effective bounded change. The original configured
value is retained as `configured_adjustment`, so clamped decisions still reconcile
exactly: `ai_score + sum(adjustment) == adjusted_score`.

Risk bands are `LOW < 30`, `MEDIUM < 60`, `HIGH < 80`, and `CRITICAL >= 80`.
