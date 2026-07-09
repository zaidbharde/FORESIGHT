from pathlib import Path

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import shap
from sklearn.compose import ColumnTransformer
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from xgboost import XGBClassifier


BASE_DIR = Path(__file__).resolve().parent

X_TRAIN_PATH = BASE_DIR / "X_train.csv"
X_TEST_PATH = BASE_DIR / "X_test.csv"
Y_TRAIN_PATH = BASE_DIR / "y_train.csv"
Y_TEST_PATH = BASE_DIR / "y_test.csv"
BASELINE_RESULTS_PATH = BASE_DIR / "baseline_results.csv"

MODEL_OUTPUT_PATH = BASE_DIR / "explainability_xgboost.pkl"
RESULTS_OUTPUT_PATH = BASE_DIR / "explainability_results.csv"
CONFUSION_MATRIX_OUTPUT_PATH = BASE_DIR / "explainability_confusion_matrix.png"
SHAP_SUMMARY_OUTPUT_PATH = BASE_DIR / "explainability_shap_summary.png"
SHAP_BAR_OUTPUT_PATH = BASE_DIR / "explainability_shap_bar.png"

RANDOM_STATE = 42
SHAP_SAMPLE_SIZE = 2000

REMOVED_FEATURES = [
    "DeviceInfo",
    "P_emaildomain",
    "R_emaildomain",
    "id_30",
    "id_31",
]

KEPT_FEATURES = [
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

BUSINESS_FRIENDLY_BASE_FEATURES = {
    "TransactionAmt",
    "hour_of_day",
    "is_late_night",
    "is_large_amount",
    "is_round_amount",
    "amount_to_average_ratio",
    "transactions_last_hour",
    "transactions_last_day",
    "days_since_last_transaction",
    "device_change_flag",
}

BASELINE_RECALL = 0.7324
BASELINE_ROC_AUC = 0.8548


def load_data():
    X_train = pd.read_csv(X_TRAIN_PATH)
    X_test = pd.read_csv(X_TEST_PATH)
    y_train = pd.read_csv(Y_TRAIN_PATH).squeeze("columns")
    y_test = pd.read_csv(Y_TEST_PATH).squeeze("columns")
    return X_train, X_test, y_train, y_test


def create_reduced_feature_set(X_train, X_test):
    missing = [feature for feature in KEPT_FEATURES if feature not in X_train.columns]
    if missing:
        raise ValueError(f"Missing expected features: {', '.join(missing)}")

    return X_train[KEPT_FEATURES].copy(), X_test[KEPT_FEATURES].copy()


def build_preprocessor(X_train):
    categorical_features = X_train.select_dtypes(include=["object", "string"]).columns.tolist()
    numerical_features = X_train.select_dtypes(exclude=["object", "string"]).columns.tolist()

    preprocessor = ColumnTransformer(
        transformers=[
            (
                "categorical",
                OneHotEncoder(handle_unknown="ignore", sparse_output=True),
                categorical_features,
            ),
            (
                "numerical",
                StandardScaler(with_mean=False),
                numerical_features,
            ),
        ]
    )
    return preprocessor, categorical_features, numerical_features


def train_xgboost_model(X_train, y_train):
    negative_count = int((y_train == 0).sum())
    positive_count = int((y_train == 1).sum())
    scale_pos_weight = negative_count / positive_count

    preprocessor, categorical_features, numerical_features = build_preprocessor(X_train)
    model = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "model",
                XGBClassifier(
                    n_estimators=200,
                    max_depth=6,
                    learning_rate=0.1,
                    subsample=0.8,
                    colsample_bytree=0.8,
                    scale_pos_weight=scale_pos_weight,
                    random_state=RANDOM_STATE,
                    eval_metric="logloss",
                    tree_method="hist",
                    n_jobs=-1,
                ),
            ),
        ]
    )

    print("Reduced feature preprocessing:")
    print(f"  Categorical features encoded: {len(categorical_features)}")
    print(f"  Numerical features scaled: {len(numerical_features)}")
    print(f"  scale_pos_weight: {scale_pos_weight:.4f}")

    model.fit(X_train, y_train)
    return model


def evaluate_model(model, X_test, y_test):
    y_pred = model.predict(X_test)
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    cm = confusion_matrix(y_test, y_pred)

    metrics = {
        "Model": "Explainability XGBoost",
        "Accuracy": accuracy_score(y_test, y_pred),
        "Precision": precision_score(y_test, y_pred, zero_division=0),
        "Recall": recall_score(y_test, y_pred, zero_division=0),
        "F1 Score": f1_score(y_test, y_pred, zero_division=0),
        "ROC-AUC": roc_auc_score(y_test, y_pred_proba),
        "TN": int(cm[0, 0]),
        "FP": int(cm[0, 1]),
        "FN": int(cm[1, 0]),
        "TP": int(cm[1, 1]),
    }
    return metrics, cm


def save_confusion_matrix_plot(cm):
    fig, ax = plt.subplots(figsize=(6, 5))
    im = ax.imshow(cm, interpolation="nearest", cmap=plt.cm.Blues)
    ax.figure.colorbar(im, ax=ax)
    ax.set(
        xticks=[0, 1],
        yticks=[0, 1],
        xticklabels=["Predicted 0", "Predicted 1"],
        yticklabels=["Actual 0", "Actual 1"],
        ylabel="Actual label",
        xlabel="Predicted label",
        title="Explainability XGBoost Confusion Matrix",
    )

    threshold = cm.max() / 2.0
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            ax.text(
                j,
                i,
                format(cm[i, j], "d"),
                ha="center",
                va="center",
                color="white" if cm[i, j] > threshold else "black",
            )

    fig.tight_layout()
    fig.savefig(CONFUSION_MATRIX_OUTPUT_PATH, dpi=300, bbox_inches="tight")
    plt.close(fig)


def clean_feature_name(name):
    name = name.replace("categorical__", "").replace("numerical__", "")

    for raw_feature in ("ProductCD", "card4", "card6", "DeviceType"):
        prefix = f"{raw_feature}_"
        if name.startswith(prefix):
            return f"{raw_feature} = {name[len(prefix):]}"
    return name


def get_transformed_feature_names(preprocessor):
    return [clean_feature_name(name) for name in preprocessor.get_feature_names_out()]


def to_dense_array(matrix):
    if hasattr(matrix, "toarray"):
        return matrix.toarray()
    return np.asarray(matrix)


def compute_shap_outputs(model, X_test):
    preprocessor = model.named_steps["preprocessor"]
    xgb_model = model.named_steps["model"]

    sample_size = min(SHAP_SAMPLE_SIZE, len(X_test))
    X_sample = X_test.sample(n=sample_size, random_state=RANDOM_STATE)
    X_sample_transformed = preprocessor.transform(X_sample)
    X_sample_dense = to_dense_array(X_sample_transformed)
    feature_names = get_transformed_feature_names(preprocessor)
    X_shap = pd.DataFrame(X_sample_dense, columns=feature_names, index=X_sample.index)

    explainer = shap.TreeExplainer(xgb_model)
    shap_values = explainer.shap_values(X_shap)
    if isinstance(shap_values, list):
        shap_values = shap_values[1]
    shap_values = np.asarray(shap_values)

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

    plt.figure(figsize=(10, 8))
    shap.summary_plot(shap_values, X_shap, max_display=20, show=False)
    plt.tight_layout()
    plt.savefig(SHAP_SUMMARY_OUTPUT_PATH, dpi=300, bbox_inches="tight")
    plt.close()

    plt.figure(figsize=(10, 8))
    shap.summary_plot(shap_values, X_shap, plot_type="bar", max_display=20, show=False)
    plt.tight_layout()
    plt.savefig(SHAP_BAR_OUTPUT_PATH, dpi=300, bbox_inches="tight")
    plt.close()

    return importance


def base_feature_name(feature_name):
    if " = " in feature_name:
        return feature_name.split(" = ", 1)[0]
    return feature_name


def load_baseline_metrics():
    if not BASELINE_RESULTS_PATH.exists():
        return None

    baseline_results = pd.read_csv(BASELINE_RESULTS_PATH)
    if "XGBoost" in baseline_results["Model"].values:
        return baseline_results.loc[baseline_results["Model"] == "XGBoost"].iloc[0]
    return None


def assess_business_friendliness(importance):
    top_20 = importance.head(20).copy()
    top_20["Base Feature"] = top_20["Feature"].map(base_feature_name)

    desired_hits = [
        feature for feature in top_20["Base Feature"] if feature in BUSINESS_FRIENDLY_BASE_FEATURES
    ]
    removed_hits = [feature for feature in top_20["Base Feature"] if feature in REMOVED_FEATURES]
    behavioral_dominant = len(desired_hits) >= 5 and len(removed_hits) == 0
    return top_20, desired_hits, removed_hits, behavioral_dominant


def build_results_row(metrics, baseline_metrics, desired_hits, removed_hits, behavioral_dominant):
    results_row = metrics.copy()
    results_row["Desired Behavioral Features In Top 20"] = len(desired_hits)
    results_row["Removed Features In Top 20"] = len(removed_hits)
    results_row["Behavioral Features Dominant"] = behavioral_dominant
    results_row["Recall Difference"] = metrics["Recall"] - BASELINE_RECALL
    results_row["ROC-AUC Difference"] = metrics["ROC-AUC"] - BASELINE_ROC_AUC

    if baseline_metrics is not None:
        results_row["Baseline Model"] = baseline_metrics["Model"]
        results_row["Baseline Recall"] = float(baseline_metrics["Recall"])
        results_row["Baseline ROC-AUC"] = float(baseline_metrics["ROC-AUC"])
    else:
        results_row["Baseline Model"] = "XGBoost"
        results_row["Baseline Recall"] = BASELINE_RECALL
        results_row["Baseline ROC-AUC"] = BASELINE_ROC_AUC

    return results_row


def print_report(metrics, top_20, desired_hits, removed_hits, behavioral_dominant):
    recall_diff = metrics["Recall"] - BASELINE_RECALL
    roc_auc_diff = metrics["ROC-AUC"] - BASELINE_ROC_AUC

    print("\nTop 20 SHAP features:")
    print(top_20[["Feature", "Mean Absolute SHAP"]].to_string(index=False))

    print("\nBaseline comparison:")
    print(f"  Recall difference: {recall_diff:+.4f}")
    print(f"  ROC-AUC difference: {roc_auc_diff:+.4f}")
    print(f"  Behavioral features became dominant: {behavioral_dominant}")

    print("\nBusiness-friendly feature check:")
    if desired_hits:
        print(f"  Desired behavioral features in top 20: {', '.join(desired_hits)}")
    else:
        print("  Desired behavioral features in top 20: none")

    if removed_hits:
        print(f"  Removed technical features still present: {', '.join(removed_hits)}")
    else:
        print("  Removed technical features still present: none")


def main():
    X_train, X_test, y_train, y_test = load_data()
    X_train_reduced, X_test_reduced = create_reduced_feature_set(X_train, X_test)

    print("Explainability optimization experiment")
    print(f"  X_train shape: {X_train.shape} -> {X_train_reduced.shape}")
    print(f"  X_test shape:  {X_test.shape} -> {X_test_reduced.shape}")
    print(f"  Removed features: {', '.join(REMOVED_FEATURES)}")

    print("\nTraining XGBoost model...")
    model = train_xgboost_model(X_train_reduced, y_train)

    metrics, cm = evaluate_model(model, X_test_reduced, y_test)
    save_confusion_matrix_plot(cm)

    print("\nEvaluation metrics:")
    print(
        pd.DataFrame([metrics])[
            ["Model", "Accuracy", "Precision", "Recall", "F1 Score", "ROC-AUC", "TN", "FP", "FN", "TP"]
        ]
        .round(4)
        .to_string(index=False)
    )

    print("\nGenerating SHAP outputs...")
    importance = compute_shap_outputs(model, X_test_reduced)
    top_20, desired_hits, removed_hits, behavioral_dominant = assess_business_friendliness(importance)

    baseline_metrics = load_baseline_metrics()
    results_row = build_results_row(
        metrics,
        baseline_metrics,
        desired_hits,
        removed_hits,
        behavioral_dominant,
    )
    pd.DataFrame([results_row]).to_csv(RESULTS_OUTPUT_PATH, index=False)

    joblib.dump(model, MODEL_OUTPUT_PATH)

    print_report(metrics, top_20, desired_hits, removed_hits, behavioral_dominant)

    print("\nSaved files:")
    print(f"  {MODEL_OUTPUT_PATH.name}")
    print(f"  {RESULTS_OUTPUT_PATH.name}")
    print(f"  {CONFUSION_MATRIX_OUTPUT_PATH.name}")
    print(f"  {SHAP_SUMMARY_OUTPUT_PATH.name}")
    print(f"  {SHAP_BAR_OUTPUT_PATH.name}")

    print("\nExperiment complete.")


if __name__ == "__main__":
    main()
