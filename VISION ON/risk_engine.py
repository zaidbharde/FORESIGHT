from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple

import joblib
import numpy as np
import pandas as pd
import shap


BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "explainability_xgboost.pkl"
X_TEST_PATH = BASE_DIR / "X_test.csv"
DEMO_RESULTS_PATH = BASE_DIR / "risk_engine_demo_results.json"

RANDOM_STATE = 42
SHAP_SAMPLE_SIZE = 2000

LOW_MAX = 29
MEDIUM_MAX = 59
HIGH_MAX = 79

REQUIRED_FEATURES = [
    "TransactionAmt",
    "card1",
    "card2",
    "card3",
    "card4",
    "card5",
    "card6",
    "addr1",
    "addr2",
    "ProductCD",
    "DeviceType",
    "hour_of_day",
    "is_late_night",
    "is_large_amount",
    "is_round_amount",
    "amount_to_average_ratio",
    "transactions_last_hour",
    "transactions_last_day",
    "days_since_last_transaction",
    "device_change_flag",
]

RULE_KEYWORDS = ("urgent", "prize", "lottery", "kyc", "verify")


MODEL = None
PREPROCESSOR = None
XGB_MODEL = None
FEATURE_NAMES = None
SHAP_EXPLAINER = None
SHAP_BACKGROUND = None


def load_model():
    if not MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Production model not found: {MODEL_PATH}. "
            "Run step_4_5_explainability_optimization.py first."
        )
    return joblib.load(MODEL_PATH)


def get_model():
    global MODEL, PREPROCESSOR, XGB_MODEL, FEATURE_NAMES
    if MODEL is None:
        MODEL = load_model()
        if hasattr(MODEL, "named_steps") and "preprocessor" in MODEL.named_steps:
            PREPROCESSOR = MODEL.named_steps["preprocessor"]
            XGB_MODEL = MODEL.named_steps["model"]
            FEATURE_NAMES = list(PREPROCESSOR.get_feature_names_out())
        else:
            PREPROCESSOR = None
            XGB_MODEL = MODEL
            FEATURE_NAMES = None
    return MODEL


def load_test_features():
    X_test = pd.read_csv(X_TEST_PATH)
    missing = [feature for feature in REQUIRED_FEATURES if feature not in X_test.columns]
    if missing:
        raise ValueError(f"Missing required features from X_test.csv: {', '.join(missing)}")
    return X_test[REQUIRED_FEATURES].copy()


def ensure_dataframe(transaction_features: Any) -> pd.DataFrame:
    if isinstance(transaction_features, pd.DataFrame):
        df = transaction_features.copy()
    elif isinstance(transaction_features, pd.Series):
        df = transaction_features.to_frame().T
    elif isinstance(transaction_features, dict):
        df = pd.DataFrame([transaction_features])
    else:
        raise TypeError("transaction_features must be a dict, pandas Series, or DataFrame")

    missing = [feature for feature in REQUIRED_FEATURES if feature not in df.columns]
    if missing:
        raise ValueError(f"Missing required transaction features: {', '.join(missing)}")

    return df[REQUIRED_FEATURES].copy()


def calculate_risk_score(probability: float) -> int:
    return int(round(float(probability) * 100))


def classify_risk(risk_score: int) -> str:
    if risk_score <= LOW_MAX:
        return "LOW"
    if risk_score <= MEDIUM_MAX:
        return "MEDIUM"
    if risk_score <= HIGH_MAX:
        return "HIGH"
    return "CRITICAL"


def calibrate_display_probability(risk_score: int, model_probability: float) -> float:
    if risk_score >= 100:
        return 0.999

    score_probability = risk_score / 100
    calibrated = max(float(model_probability), score_probability)
    return min(calibrated, 0.999)


def get_recommended_action(risk_category: str) -> str:
    actions = {
        "LOW": "Proceed Normally",
        "MEDIUM": "Show Warning and Review Screen",
        "HIGH": "Require Additional Verification and User Confirmation",
        "CRITICAL": "Display Strong Scam Warning and Require Explicit Confirmation",
    }
    return actions[risk_category]


def get_behavioral_risk_adjustment(transaction_features: Any, model_risk_score: int) -> Tuple[int, List[str]]:
    row = _as_first_row(transaction_features)
    amount = float(row.get("TransactionAmt", 0) or 0)
    hour = int(row.get("hour_of_day", 0) or 0)
    transactions_last_hour = int(row.get("transactions_last_hour", 0) or 0)
    is_large_amount = bool(int(row.get("is_large_amount", 0) or 0))
    device_changed = bool(int(row.get("device_change_flag", 0) or 0))
    late_night = bool(int(row.get("is_late_night", 0) or 0)) or hour >= 22 or hour <= 5

    behavioral_score = 0
    reasons = []

    if amount >= 100000:
        behavioral_score += 40
        reasons.append("Very Large Amount")
    elif amount >= 50000:
        behavioral_score += 35
        reasons.append("Large Amount")
    elif amount >= 5000:
        behavioral_score += 15
        reasons.append("Elevated Amount")

    if is_large_amount:
        behavioral_score += 10
        if "Large Amount" not in reasons and "Very Large Amount" not in reasons:
            reasons.append("Large Amount")

    if device_changed:
        behavioral_score += 15
        reasons.append("New Device")

    if late_night:
        behavioral_score += 10
        reasons.append("Late Night Transaction")

    if transactions_last_hour >= 20:
        behavioral_score += 30
        reasons.append("Burst of Payments")
    elif transactions_last_hour >= 3:
        behavioral_score += 15
        reasons.append("Multiple Payments in Last Hour")

    final_score = min(100, max(model_risk_score, model_risk_score + behavioral_score))
    return final_score, reasons if final_score > model_risk_score else []


def _as_first_row(transaction_features: Any) -> Dict[str, Any]:
    if isinstance(transaction_features, pd.DataFrame):
        return transaction_features.iloc[0].to_dict()
    if isinstance(transaction_features, pd.Series):
        return transaction_features.to_dict()
    if isinstance(transaction_features, dict):
        return dict(transaction_features)
    raise TypeError("transaction_features must be a dict, pandas Series, or DataFrame")


def _rule_reason(transaction_features: Any) -> str | None:
    row = _as_first_row(transaction_features)

    if bool(row.get("active_call_detected", False)):
        return "active_call_detected"
    if bool(row.get("sim_changed_within_7_days", False)):
        return "sim_changed_within_7_days"
    if bool(row.get("otp_request_detected", False)):
        return "otp_request_detected"
    if bool(row.get("otp_receive_money_scam", False)):
        return "otp_receive_money_scam"

    purpose = str(
        row.get("payment_purpose")
        or row.get("purpose")
        or row.get("transaction_purpose")
        or ""
    ).lower()
    for keyword in RULE_KEYWORDS:
        if keyword in purpose:
            return f"payment_purpose_contains_{keyword}"

    return None


def otp_scam_prescreen(transaction_features: Any) -> Dict[str, Any] | None:
    reason = _rule_reason(transaction_features)
    if reason is None:
        return None

    return {
        "blocked_by_rule_engine": True,
        "risk_score": 100,
        "risk_category": "CRITICAL",
        "recommended_action": "Immediate Scam Warning",
        "reason": reason,
    }


def _get_feature_display_name(feature_name: str) -> str:
    base = feature_name.replace("categorical__", "").replace("numerical__", "")
    if base.startswith("ProductCD_"):
        return "Product category"
    if base.startswith("card4_"):
        return "Card network"
    if base.startswith("card6_"):
        return "Payment method"
    if base.startswith("DeviceType_"):
        return "Device type"

    mappings = {
        "TransactionAmt": "Transaction Amount",
        "hour_of_day": "Late Night Transaction",
        "is_late_night": "Late Night Transaction",
        "is_large_amount": "Large Amount",
        "is_round_amount": "Round Amount Transaction",
        "amount_to_average_ratio": "Unusual Spending Pattern",
        "transactions_last_hour": "High Transaction Velocity",
        "transactions_last_day": "High Transaction Velocity",
        "days_since_last_transaction": "Time Since Last Transaction",
        "device_change_flag": "Device Change",
        "card1": "Card Pattern",
        "card2": "Card Pattern",
        "card3": "Card Pattern",
        "card5": "Card Pattern",
        "addr1": "Billing Address Pattern",
        "addr2": "Billing Address Pattern",
    }
    return mappings.get(base, base)


def _normalize_reason_name(feature_name: str) -> str:
    display = _get_feature_display_name(feature_name)
    if display == "Late Night Transaction":
        return "Late Night Transaction"
    if display == "Large Amount":
        return "Large Amount"
    if display == "Device Change":
        return "Device Change"
    if display == "High Transaction Velocity":
        return "High Transaction Velocity"
    if display == "Unusual Spending Pattern":
        return "Unusual Spending Pattern"
    return display


def _to_dense(matrix: Any) -> np.ndarray:
    if hasattr(matrix, "toarray"):
        return matrix.toarray()
    return np.asarray(matrix)


def _build_shap_explainer():
    global SHAP_EXPLAINER, SHAP_BACKGROUND
    if SHAP_EXPLAINER is not None:
        return SHAP_EXPLAINER

    if PREPROCESSOR is None or XGB_MODEL is None:
        raise RuntimeError("Model pipeline has not been initialized correctly")

    X_test = load_test_features()
    sample_size = min(SHAP_SAMPLE_SIZE, len(X_test))
    background = X_test.sample(n=sample_size, random_state=RANDOM_STATE)
    background_transformed = PREPROCESSOR.transform(background)
    background_dense = _to_dense(background_transformed)
    feature_names = [name.replace("categorical__", "").replace("numerical__", "") for name in PREPROCESSOR.get_feature_names_out()]
    SHAP_BACKGROUND = pd.DataFrame(background_dense, columns=feature_names)
    SHAP_EXPLAINER = shap.TreeExplainer(XGB_MODEL)
    return SHAP_EXPLAINER


def explain_prediction(transaction_features: Any, probability: float | None = None) -> List[str]:
    transaction = ensure_dataframe(transaction_features)
    _build_shap_explainer()

    transformed = PREPROCESSOR.transform(transaction)
    dense = _to_dense(transformed)
    feature_names = [name.replace("categorical__", "").replace("numerical__", "") for name in PREPROCESSOR.get_feature_names_out()]
    shaped = pd.DataFrame(dense, columns=feature_names)

    shap_values = SHAP_EXPLAINER.shap_values(shaped)
    if isinstance(shap_values, list):
        shap_values = shap_values[1]
    shap_values = np.asarray(shap_values)[0]

    contributions = []
    total_abs = float(np.abs(shap_values).sum()) or 1.0
    for name, value in zip(feature_names, shap_values):
        if value <= 0:
            continue
        contributions.append((name, float(value), abs(float(value)) / total_abs * 100))

    if not contributions:
        ranked = sorted(zip(feature_names, shap_values), key=lambda item: abs(float(item[1])), reverse=True)
        contributions = [(name, float(value), abs(float(value)) / total_abs * 100) for name, value in ranked[:3]]
    else:
        contributions.sort(key=lambda item: item[1], reverse=True)
        contributions = contributions[:3]

    reasons = []
    seen = set()
    for name, value, pct in contributions:
        reason = _normalize_reason_name(name)
        if reason in seen:
            continue
        seen.add(reason)
        reasons.append(f"{reason} (+{pct:.0f}%)")
    return reasons


def predict_transaction(transaction_features: Any) -> Dict[str, Any]:
    rule_result = otp_scam_prescreen(transaction_features)
    if rule_result is not None:
        return rule_result

    transaction = ensure_dataframe(transaction_features)
    model = get_model()
    probability = float(model.predict_proba(transaction)[:, 1][0])
    model_risk_score = calculate_risk_score(probability)
    risk_score, behavioral_reasons = get_behavioral_risk_adjustment(transaction, model_risk_score)
    risk_category = classify_risk(risk_score)
    recommended_action = get_recommended_action(risk_category)
    display_probability = calibrate_display_probability(risk_score, probability)
    top_reasons = explain_prediction(transaction, probability)
    if risk_score > model_risk_score:
        top_reasons = [f"{reason} (behavioral rule)" for reason in behavioral_reasons] + top_reasons

    return {
        "blocked_by_rule_engine": False,
        "risk_score": risk_score,
        "risk_category": risk_category,
        "recommended_action": recommended_action,
        "fraud_probability": display_probability,
        "model_fraud_probability": probability,
        "model_risk_score": model_risk_score,
        "top_reasons": top_reasons,
    }


def _pick_demo_row(X_test: pd.DataFrame, probabilities: np.ndarray, low: float, high: float, target: float) -> pd.Series:
    mask = (probabilities >= low) & (probabilities <= high)
    if mask.any():
        idx = np.where(mask)[0][np.argmin(np.abs(probabilities[mask] - target))]
        return X_test.iloc[int(idx)]

    idx = int(np.argmin(np.abs(probabilities - target)))
    return X_test.iloc[idx]


def generate_demo_transactions() -> Dict[str, Dict[str, Any]]:
    X_test = load_test_features()
    model = get_model()
    probabilities = model.predict_proba(X_test)[:, 1]

    demos = {
        "LOW": _pick_demo_row(X_test, probabilities, 0.0, 0.29, 0.10),
        "MEDIUM": _pick_demo_row(X_test, probabilities, 0.30, 0.59, 0.45),
        "HIGH": _pick_demo_row(X_test, probabilities, 0.60, 0.79, 0.70),
        "CRITICAL": _pick_demo_row(X_test, probabilities, 0.80, 1.0, 0.90),
    }

    outputs = {}
    for label, row in demos.items():
        outputs[label] = predict_transaction(row)

    scam_demo = {
        **demos["LOW"].to_dict(),
        "active_call_detected": True,
        "sim_changed_within_7_days": True,
        "otp_receive_money_scam": True,
        "payment_purpose": "urgent prize verify kyc",
    }
    outputs["OTP_SCAM_BLOCKED"] = predict_transaction(scam_demo)
    return outputs


def _to_jsonable(value: Any) -> Any:
    if isinstance(value, (np.integer,)):
        return int(value)
    if isinstance(value, (np.floating,)):
        return float(value)
    if isinstance(value, (np.bool_,)):
        return bool(value)
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, pd.Series):
        return value.to_dict()
    if isinstance(value, dict):
        return {k: _to_jsonable(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_to_jsonable(v) for v in value]
    return value


def main():
    get_model()

    demo_results = generate_demo_transactions()

    print("Step 5: Risk Scoring Engine")
    print("=" * 60)
    for scenario, result in demo_results.items():
        print(f"\n{scenario} SCENARIO")
        print(json.dumps(_to_jsonable(result), indent=2))

    with open(DEMO_RESULTS_PATH, "w", encoding="utf-8") as f:
        json.dump(_to_jsonable(demo_results), f, indent=2)

    print(f"\nSaved demo results to: {DEMO_RESULTS_PATH.name}")


if __name__ == "__main__":
    main()
