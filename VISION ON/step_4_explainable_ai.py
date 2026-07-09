from pathlib import Path

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import shap


BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "xgboost_baseline.pkl"
X_TEST_PATH = BASE_DIR / "X_test.csv"

SHAP_SAMPLE_SIZE = 2000
RANDOM_STATE = 42

RISK_BANDS = {
    "LOW": (0, 25, 10),
    "MEDIUM": (25, 60, 45),
    "HIGH": (60, 80, 70),
    "CRITICAL": (80, 101, 90),
}


selected_model = None
preprocessor = None
xgb_model = None
explainer = None
feature_names = None


def load_selected_model():
    if not MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Selected model not found: {MODEL_PATH}. "
            "Run step_2_baseline_models.py first to create xgboost_baseline.pkl."
        )

    model = joblib.load(MODEL_PATH)
    print(f"Loaded selected model: {MODEL_PATH.name}")
    return model


def load_x_test():
    if not X_TEST_PATH.exists():
        raise FileNotFoundError(f"X_test.csv not found: {X_TEST_PATH}")

    X_test = pd.read_csv(X_TEST_PATH)
    print(f"Loaded X_test.csv with shape: {X_test.shape}")
    return X_test


def get_pipeline_parts(model):
    if "preprocessor" not in model.named_steps or "model" not in model.named_steps:
        raise ValueError("Expected a sklearn Pipeline with preprocessor and model steps.")

    return model.named_steps["preprocessor"], model.named_steps["model"]


def clean_feature_name(name):
    name = name.replace("categorical__", "").replace("numerical__", "")

    if "_" not in name:
        return name

    raw_feature, raw_value = name.split("_", 1)
    known_raw_features = {
        "ProductCD",
        "card4",
        "card6",
        "P_emaildomain",
        "R_emaildomain",
        "DeviceType",
        "DeviceInfo",
        "id_30",
        "id_31",
    }
    if raw_feature in known_raw_features:
        return f"{raw_feature} = {raw_value}"

    return name


def get_transformed_feature_names(fitted_preprocessor):
    names = fitted_preprocessor.get_feature_names_out()
    return [clean_feature_name(name) for name in names]


def to_dense_array(matrix):
    if hasattr(matrix, "toarray"):
        return matrix.toarray()
    return np.asarray(matrix)


def transform_features(X):
    transformed = preprocessor.transform(X)
    dense = to_dense_array(transformed)
    return pd.DataFrame(dense, columns=feature_names, index=X.index)


def create_shap_explainer(model, X_test):
    global selected_model, preprocessor, xgb_model, explainer, feature_names

    selected_model = model
    preprocessor, xgb_model = get_pipeline_parts(selected_model)

    sample_size = min(SHAP_SAMPLE_SIZE, len(X_test))
    background_raw = X_test.sample(
        n=sample_size,
        random_state=RANDOM_STATE,
    )
    background_transformed = preprocessor.transform(background_raw)
    feature_names = get_transformed_feature_names(preprocessor)
    background_dense = to_dense_array(background_transformed)

    explainer = shap.TreeExplainer(xgb_model)
    print(f"Created SHAP TreeExplainer using {sample_size} sampled rows.")

    return pd.DataFrame(
        background_dense,
        columns=feature_names,
        index=background_raw.index,
    )


def compute_shap_values(X_transformed):
    shap_values = explainer.shap_values(X_transformed)
    if isinstance(shap_values, list):
        shap_values = shap_values[1]
    return np.asarray(shap_values)


def save_global_feature_importance(shap_values):
    importance = (
        pd.DataFrame(
            {
                "Feature": feature_names,
                "Mean Absolute SHAP": np.abs(shap_values).mean(axis=0),
            }
        )
        .sort_values("Mean Absolute SHAP", ascending=False)
        .reset_index(drop=True)
    )
    importance.to_csv(BASE_DIR / "global_feature_importance.csv", index=False)

    print("\nGlobal Feature Importance:")
    print(importance.head(15).to_string(index=False))
    return importance


def save_shap_summary_plot(shap_values, X_transformed):
    plt.figure()
    shap.summary_plot(
        shap_values,
        X_transformed,
        max_display=20,
        show=False,
    )
    plt.tight_layout()
    plt.savefig(BASE_DIR / "shap_summary.png", dpi=150, bbox_inches="tight")
    plt.close()


def save_shap_bar_plot(importance):
    top_features = importance.head(20).sort_values("Mean Absolute SHAP")

    fig, ax = plt.subplots(figsize=(10, 7))
    ax.barh(top_features["Feature"], top_features["Mean Absolute SHAP"])
    ax.set_title("SHAP Global Feature Importance")
    ax.set_xlabel("Mean Absolute SHAP Value")
    ax.grid(axis="x", linestyle="--", alpha=0.35)
    fig.tight_layout()
    fig.savefig(BASE_DIR / "shap_bar.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def risk_category_from_score(risk_score):
    if risk_score < 25:
        return "LOW"
    if risk_score < 60:
        return "MEDIUM"
    if risk_score < 80:
        return "HIGH"
    return "CRITICAL"


def describe_feature_for_transaction(feature, transformed_value):
    if " = " in feature and transformed_value == 0:
        return f"Not {feature}"
    return feature


def format_reason(feature, transformed_value, contribution, denominator):
    feature_description = describe_feature_for_transaction(feature, transformed_value)
    percentage = int(round(abs(contribution) / denominator * 100))
    sign = "+" if contribution >= 0 else "-"
    return f"{feature_description} ({sign}{percentage}%)"


def explain_transaction(transaction_features):
    if isinstance(transaction_features, pd.Series):
        transaction_df = transaction_features.to_frame().T
    elif isinstance(transaction_features, dict):
        transaction_df = pd.DataFrame([transaction_features])
    elif isinstance(transaction_features, pd.DataFrame):
        transaction_df = transaction_features.iloc[[0]].copy()
    else:
        raise TypeError("transaction_features must be a pandas Series, dict, or DataFrame.")

    risk_probability = selected_model.predict_proba(transaction_df)[0, 1]
    risk_score = int(round(risk_probability * 100))
    risk_category = risk_category_from_score(risk_score)

    transaction_transformed = transform_features(transaction_df)
    shap_values = compute_shap_values(transaction_transformed)[0]

    positive_indices = np.where(shap_values > 0)[0]
    if len(positive_indices) >= 3:
        candidate_indices = positive_indices[
            np.argsort(np.abs(shap_values[positive_indices]))[::-1]
        ][:3]
    else:
        candidate_indices = np.argsort(np.abs(shap_values))[::-1][:3]

    denominator = float(np.abs(shap_values).sum())
    if denominator == 0:
        denominator = 1.0

    top_reasons = [
        format_reason(
            feature_names[index],
            transaction_transformed.iloc[0, index],
            shap_values[index],
            denominator,
        )
        for index in candidate_indices
    ]

    explanation = (
        f"This transaction is classified as {risk_category} risk with a "
        f"{risk_score}/100 fraud score. The strongest drivers are "
        f"{', '.join(top_reasons)}."
    )

    return {
        "risk_score": risk_score,
        "risk_category": risk_category,
        "top_reasons": top_reasons,
        "human_readable_explanation": explanation,
    }


def select_transaction_for_risk_band(X_test, risk_scores, category):
    lower, upper, target = RISK_BANDS[category]
    in_band = np.where((risk_scores >= lower) & (risk_scores < upper))[0]

    if len(in_band) > 0:
        selected_position = in_band[np.argmin(np.abs(risk_scores[in_band] - target))]
    else:
        selected_position = int(np.argmin(np.abs(risk_scores - target)))

    return selected_position, X_test.iloc[selected_position]


def save_transaction_explanation_plot(category, explanation, output_file):
    fig, ax = plt.subplots(figsize=(8, 4.5))
    ax.axis("off")

    lines = [
        f"{category.title()} Risk Transaction",
        f"Risk Score: {explanation['risk_score']}",
        f"Risk Category: {explanation['risk_category']}",
        "",
        "Top Reasons:",
    ]
    lines.extend(
        f"{index}. {reason}"
        for index, reason in enumerate(explanation["top_reasons"], start=1)
    )
    lines.extend(["", explanation["human_readable_explanation"]])

    ax.text(
        0.04,
        0.96,
        "\n".join(lines),
        va="top",
        ha="left",
        fontsize=12,
        wrap=True,
    )
    fig.tight_layout()
    fig.savefig(BASE_DIR / output_file, dpi=150, bbox_inches="tight")
    plt.close(fig)


def generate_sample_explanations(X_test):
    risk_scores = selected_model.predict_proba(X_test)[:, 1] * 100
    outputs = {
        "LOW": "low_risk_explanation.png",
        "MEDIUM": "medium_risk_explanation.png",
        "HIGH": "high_risk_explanation.png",
        "CRITICAL": "critical_risk_explanation.png",
    }

    print("\nSample Transaction Explanations:")
    for category, output_file in outputs.items():
        row_position, transaction = select_transaction_for_risk_band(
            X_test,
            risk_scores,
            category,
        )
        explanation = explain_transaction(transaction)
        save_transaction_explanation_plot(category, explanation, output_file)

        print(f"\n{category} risk sample")
        print(f"X_test row: {row_position}")
        print(
            {
                "risk_score": explanation["risk_score"],
                "risk_category": explanation["risk_category"],
                "top_reasons": explanation["top_reasons"],
            }
        )
        print(explanation["human_readable_explanation"])


def main():
    model = load_selected_model()
    X_test = load_x_test()

    shap_background = create_shap_explainer(model, X_test)
    shap_values = compute_shap_values(shap_background)

    importance = save_global_feature_importance(shap_values)
    save_shap_summary_plot(shap_values, shap_background)
    save_shap_bar_plot(importance)
    generate_sample_explanations(X_test)

    print("\nFiles saved successfully:")
    print("shap_summary.png")
    print("shap_bar.png")
    print("global_feature_importance.csv")
    print("low_risk_explanation.png")
    print("medium_risk_explanation.png")
    print("high_risk_explanation.png")
    print("critical_risk_explanation.png")

    print("\nStep 4 completed successfully.")


if __name__ == "__main__":
    main()
