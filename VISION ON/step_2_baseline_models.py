from pathlib import Path

import joblib
import matplotlib.pyplot as plt
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    accuracy_score,
    classification_report,
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
BEST_BASELINE_MODEL_PATH = BASE_DIR / "best_baseline_model_checkpoint.pkl"
BEST_BASELINE_METADATA_PATH = BASE_DIR / "best_baseline_model_metadata.csv"


def load_step_1_outputs():
    X_train = pd.read_csv(BASE_DIR / "X_train.csv")
    X_test = pd.read_csv(BASE_DIR / "X_test.csv")
    y_train = pd.read_csv(BASE_DIR / "y_train.csv").squeeze("columns")
    y_test = pd.read_csv(BASE_DIR / "y_test.csv").squeeze("columns")

    return X_train, X_test, y_train, y_test 


def build_preprocessor(X_train):
    categorical_features = X_train.select_dtypes(include=["object"]).columns.tolist()
    numerical_features = X_train.select_dtypes(exclude=["object"]).columns.tolist()

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


def evaluate_model(model_name, model, X_test, y_test):
    y_pred = model.predict(X_test)
    y_pred_proba = model.predict_proba(X_test)[:, 1]

    metrics = {
        "Model": model_name,
        "Accuracy": accuracy_score(y_test, y_pred),
        "Precision": precision_score(y_test, y_pred, zero_division=0),
        "Recall": recall_score(y_test, y_pred, zero_division=0),
        "F1 Score": f1_score(y_test, y_pred, zero_division=0),
        "ROC-AUC": roc_auc_score(y_test, y_pred_proba),
    }

    print(f"\n{model_name} Confusion Matrix:")
    print(confusion_matrix(y_test, y_pred))

    print(f"\n{model_name} Classification Report:")
    print(classification_report(y_test, y_pred, zero_division=0))

    return metrics, y_pred


def save_confusion_matrix_plot(model_name, y_test, y_pred, output_file):
    fig, ax = plt.subplots(figsize=(6, 5))
    ConfusionMatrixDisplay.from_predictions(
        y_test,
        y_pred,
        display_labels=["Not Fraud", "Fraud"],
        cmap="Blues",
        ax=ax,
        values_format="d",
    )
    ax.set_title(f"{model_name} Confusion Matrix")
    fig.tight_layout()
    fig.savefig(BASE_DIR / output_file, dpi=150)
    plt.close(fig)


def save_model_comparison_plot(results_df):
    metrics_to_plot = ["Accuracy", "Precision", "Recall", "F1 Score", "ROC-AUC"]
    plot_df = results_df.set_index("Model")[metrics_to_plot]

    fig, ax = plt.subplots(figsize=(10, 6))
    plot_df.plot(kind="bar", ax=ax)
    ax.set_title("Baseline Model Comparison")
    ax.set_xlabel("Model")
    ax.set_ylabel("Score")
    ax.set_ylim(0, 1)
    ax.legend(loc="lower right")
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    plt.xticks(rotation=0)
    fig.tight_layout()
    fig.savefig(BASE_DIR / "model_comparison_bar_chart.png", dpi=150)
    plt.close(fig)


def save_best_model_checkpoint(best_model_row, trained_models):
    best_model_name = best_model_row["Model"]
    best_model = trained_models[best_model_name]

    joblib.dump(best_model, BEST_BASELINE_MODEL_PATH)
    pd.DataFrame([best_model_row]).to_csv(BEST_BASELINE_METADATA_PATH, index=False)

    print("\nBest baseline checkpoint saved:")
    print(BEST_BASELINE_MODEL_PATH.name)
    print(BEST_BASELINE_METADATA_PATH.name)


def main():
    X_train, X_test, y_train, y_test = load_step_1_outputs()

    print("Loaded Step 1 files successfully.")
    print(f"X_train shape: {X_train.shape}")
    print(f"X_test shape: {X_test.shape}")
    print(f"y_train shape: {y_train.shape}")
    print(f"y_test shape: {y_test.shape}")

    preprocessor, categorical_features, numerical_features = build_preprocessor(X_train)

    print("\nFeature preprocessing:")
    print(f"Categorical features encoded: {len(categorical_features)}")
    print(f"Numerical features scaled: {len(numerical_features)}")

    negative_count = int((y_train == 0).sum())
    positive_count = int((y_train == 1).sum())
    scale_pos_weight = negative_count / positive_count

    print("\nFraud ratio for XGBoost:")
    print(f"Non-fraud count: {negative_count}")
    print(f"Fraud count: {positive_count}")
    print(f"scale_pos_weight: {scale_pos_weight:.4f}")

    random_forest_model = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "model",
                RandomForestClassifier(
                    n_estimators=100,
                    class_weight="balanced",
                    random_state=42,
                    n_jobs=-1,
                ),
            ),
        ]
    )

    xgboost_model = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "model",
                XGBClassifier(
                    n_estimators=100,
                    learning_rate=0.1,
                    max_depth=6,
                    scale_pos_weight=scale_pos_weight,
                    random_state=42,
                    eval_metric="logloss",
                    tree_method="hist",
                    n_jobs=-1,
                ),
            ),
        ]
    )

    print("\nTraining Random Forest baseline model...")
    random_forest_model.fit(X_train, y_train)

    print("Training XGBoost baseline model...")
    xgboost_model.fit(X_train, y_train)

    rf_metrics, rf_predictions = evaluate_model(
        "Random Forest", random_forest_model, X_test, y_test
    )
    xgb_metrics, xgb_predictions = evaluate_model(
        "XGBoost", xgboost_model, X_test, y_test
    )

    results_df = pd.DataFrame([rf_metrics, xgb_metrics])
    metric_columns = ["Accuracy", "Precision", "Recall", "F1 Score", "ROC-AUC"]
    results_df[metric_columns] = results_df[metric_columns].round(4)

    print("\nBaseline model comparison:")
    print(results_df.to_string(index=False))

    best_model_row = results_df.sort_values(
        by=["Recall", "ROC-AUC"], ascending=False
    ).iloc[0]

    joblib.dump(random_forest_model, BASE_DIR / "random_forest_baseline.pkl")
    joblib.dump(xgboost_model, BASE_DIR / "xgboost_baseline.pkl")
    results_df.to_csv(BASE_DIR / "baseline_results.csv", index=False)
    save_best_model_checkpoint(
        best_model_row,
        {
            "Random Forest": random_forest_model,
            "XGBoost": xgboost_model,
        },
    )

    save_confusion_matrix_plot(
        "Random Forest",
        y_test,
        rf_predictions,
        "random_forest_confusion_matrix.png",
    )
    save_confusion_matrix_plot(
        "XGBoost",
        y_test,
        xgb_predictions,
        "xgboost_confusion_matrix.png",
    )
    save_model_comparison_plot(results_df)

    print("\nFiles saved successfully:")
    print("random_forest_baseline.pkl")
    print("xgboost_baseline.pkl")
    print("baseline_results.csv")
    print("best_baseline_model_checkpoint.pkl")
    print("best_baseline_model_metadata.csv")
    print("random_forest_confusion_matrix.png")
    print("xgboost_confusion_matrix.png")
    print("model_comparison_bar_chart.png")

    print("\nBest baseline model:")
    print(f"Best Model: {best_model_row['Model']}")
    print(f"Best Recall: {best_model_row['Recall']:.4f}")
    print(f"Best ROC-AUC: {best_model_row['ROC-AUC']:.4f}")

    print("\nStep 2 completed successfully.")


if __name__ == "__main__":
    main()
