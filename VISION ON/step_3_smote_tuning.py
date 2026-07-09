from pathlib import Path

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline
from sklearn.base import clone
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    PrecisionRecallDisplay,
    RocCurveDisplay,
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import ParameterSampler, StratifiedKFold, cross_val_score
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from xgboost import XGBClassifier


BASE_DIR = Path(__file__).resolve().parent
CHECKPOINT_DIR = BASE_DIR / "checkpoints"
BEST_TUNED_MODEL_PATH = BASE_DIR / "best_tuned_model_checkpoint.pkl"
BEST_TUNED_METADATA_PATH = BASE_DIR / "best_tuned_model_metadata.csv"
RANDOM_STATE = 42
CV_FOLDS = 5
TUNING_ITERATIONS = 5
SMOTE_SAMPLING_STRATEGY = 0.20


def load_data():
    X_train = pd.read_csv(BASE_DIR / "X_train.csv")
    X_test = pd.read_csv(BASE_DIR / "X_test.csv")
    y_train = pd.read_csv(BASE_DIR / "y_train.csv").squeeze("columns")
    y_test = pd.read_csv(BASE_DIR / "y_test.csv").squeeze("columns")
    return X_train, X_test, y_train, y_test


def analyze_class_imbalance(y_train):
    class_counts = y_train.value_counts().sort_index()
    non_fraud_count = int(class_counts.get(0, 0))
    fraud_count = int(class_counts.get(1, 0))
    fraud_percentage = fraud_count / len(y_train) * 100

    print("Original class distribution:")
    print(f"Non-fraud transactions: {non_fraud_count}")
    print(f"Fraud transactions: {fraud_count}")
    print(f"Fraud percentage: {fraud_percentage:.4f}%")


def get_feature_groups(X_train):
    categorical_features = X_train.select_dtypes(include=["object"]).columns.tolist()
    numerical_features = X_train.select_dtypes(exclude=["object"]).columns.tolist()
    return categorical_features, numerical_features


def build_preprocessor(categorical_features, numerical_features):
    return ColumnTransformer(
        transformers=[
            (
                "categorical",
                OneHotEncoder(
                    handle_unknown="infrequent_if_exist",
                    min_frequency=100,
                    sparse_output=True,
                    dtype=np.float32,
                ),
                categorical_features,
            ),
            (
                "numerical",
                StandardScaler(with_mean=False),
                numerical_features,
            ),
        ],
        sparse_threshold=1.0,
    )


def print_smote_distribution(X_train, y_train, preprocessor):
    X_train_encoded = preprocessor.fit_transform(X_train)
    smote = SMOTE(
        sampling_strategy=SMOTE_SAMPLING_STRATEGY,
        k_neighbors=3,
        random_state=RANDOM_STATE,
    )
    _, y_resampled = smote.fit_resample(X_train_encoded, y_train)

    print("\nClass distribution after SMOTE:")
    print(y_resampled.value_counts().sort_index())


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

    return metrics, y_pred, y_pred_proba


def save_confusion_matrix_plot(model_name, y_test, y_pred, output_file):
    fig, ax = plt.subplots(figsize=(6, 5))
    ConfusionMatrixDisplay.from_predictions(
        y_test,
        y_pred,
        display_labels=["Not Fraud", "Fraud"],
        cmap="Blues",
        values_format="d",
        ax=ax,
    )
    ax.set_title(f"{model_name} Confusion Matrix")
    fig.tight_layout()
    fig.savefig(BASE_DIR / output_file, dpi=150)
    plt.close(fig)


def save_roc_curve(model_name, y_test, y_pred_proba, output_file):
    fig, ax = plt.subplots(figsize=(6, 5))
    RocCurveDisplay.from_predictions(y_test, y_pred_proba, ax=ax)
    ax.set_title(f"{model_name} ROC Curve")
    ax.grid(linestyle="--", alpha=0.4)
    fig.tight_layout()
    fig.savefig(BASE_DIR / output_file, dpi=150)
    plt.close(fig)


def save_precision_recall_curve(model_name, y_test, y_pred_proba, output_file):
    fig, ax = plt.subplots(figsize=(6, 5))
    PrecisionRecallDisplay.from_predictions(y_test, y_pred_proba, ax=ax)
    ax.set_title(f"{model_name} Precision-Recall Curve")
    ax.grid(linestyle="--", alpha=0.4)
    fig.tight_layout()
    fig.savefig(BASE_DIR / output_file, dpi=150)
    plt.close(fig)


def checkpoint_path(model_key):
    return CHECKPOINT_DIR / f"{model_key}_tuning_checkpoint.pkl"


def load_tuning_checkpoint(model_key):
    path = checkpoint_path(model_key)
    if not path.exists():
        return {
            "completed_results": [],
            "best_score": None,
            "best_params": None,
            "best_estimator": None,
        }

    checkpoint = joblib.load(path)
    print(f"\nResuming {model_key} tuning from checkpoint:")
    print(f"Completed candidates: {len(checkpoint['completed_results'])}")
    if checkpoint["best_score"] is not None:
        print(f"Current best CV recall: {checkpoint['best_score']:.4f}")
    return checkpoint


def save_tuning_checkpoint(model_key, checkpoint):
    CHECKPOINT_DIR.mkdir(exist_ok=True)
    path = checkpoint_path(model_key)
    temp_path = path.with_suffix(".tmp")
    joblib.dump(checkpoint, temp_path)
    temp_path.replace(path)


def save_best_tuned_checkpoint(model_name, metrics, model):
    checkpoint = {
        "model_name": model_name,
        "metrics": metrics,
        "model": model,
    }
    joblib.dump(checkpoint, BEST_TUNED_MODEL_PATH)
    pd.DataFrame([metrics]).to_csv(BEST_TUNED_METADATA_PATH, index=False)


def build_tuning_configs(preprocessor):
    cv = StratifiedKFold(
        n_splits=CV_FOLDS,
        shuffle=True,
        random_state=RANDOM_STATE,
    )

    smote = SMOTE(
        sampling_strategy=SMOTE_SAMPLING_STRATEGY,
        k_neighbors=3,
        random_state=RANDOM_STATE,
    )

    rf_pipeline = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("smote", smote),
            (
                "model",
                RandomForestClassifier(
                    class_weight="balanced",
                    random_state=RANDOM_STATE,
                    n_jobs=-1,
                ),
            ),
        ]
    )

    xgb_pipeline = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("smote", smote),
            (
                "model",
                XGBClassifier(
                    objective="binary:logistic",
                    eval_metric="logloss",
                    tree_method="hist",
                    device="cpu",
                    random_state=RANDOM_STATE,
                    n_jobs=-1,
                ),
            ),
        ]
    )

    rf_params = {
        "model__n_estimators": [100, 200, 300],
        "model__max_depth": [10, 15, 20, None],
        "model__min_samples_split": [2, 5, 10],
        "model__min_samples_leaf": [1, 2, 4],
    }

    xgb_params = {
        "model__n_estimators": [100, 200, 300],
        "model__learning_rate": [0.01, 0.05, 0.1],
        "model__max_depth": [4, 6, 8],
        "model__subsample": [0.8, 1.0],
        "model__colsample_bytree": [0.8, 1.0],
    }

    return {
        "random_forest": {
            "display_name": "Random Forest",
            "pipeline": rf_pipeline,
            "params": rf_params,
            "cv": cv,
        },
        "xgboost": {
            "display_name": "XGBoost",
            "pipeline": xgb_pipeline,
            "params": xgb_params,
            "cv": cv,
        },
    }


def tune_with_checkpoints(model_key, config, X_train, y_train):
    checkpoint = load_tuning_checkpoint(model_key)
    completed_results = checkpoint["completed_results"]
    completed_indices = {result["candidate_index"] for result in completed_results}
    candidates = list(
        ParameterSampler(
            config["params"],
            n_iter=TUNING_ITERATIONS,
            random_state=RANDOM_STATE,
        )
    )

    for candidate_index, params in enumerate(candidates, start=1):
        if candidate_index in completed_indices:
            print(
                f"\nSkipping {config['display_name']} candidate "
                f"{candidate_index}/{len(candidates)} from checkpoint."
            )
            continue

        print(
            f"\nTuning {config['display_name']} candidate "
            f"{candidate_index}/{len(candidates)}..."
        )
        print(params)

        estimator = clone(config["pipeline"])
        estimator.set_params(**params)
        scores = cross_val_score(
            estimator,
            X_train,
            y_train,
            scoring="recall",
            cv=config["cv"],
            n_jobs=1,
        )
        mean_score = float(scores.mean())
        std_score = float(scores.std())

        print(
            f"{config['display_name']} candidate {candidate_index} "
            f"CV recall: {mean_score:.4f} (+/- {std_score:.4f})"
        )

        result = {
            "candidate_index": candidate_index,
            "params": params,
            "mean_cv_recall": mean_score,
            "std_cv_recall": std_score,
        }
        completed_results.append(result)

        if checkpoint["best_score"] is None or mean_score > checkpoint["best_score"]:
            print(f"New best {config['display_name']} candidate found. Refitting...")
            best_estimator = clone(config["pipeline"])
            best_estimator.set_params(**params)
            best_estimator.fit(X_train, y_train)
            checkpoint["best_score"] = mean_score
            checkpoint["best_params"] = params
            checkpoint["best_estimator"] = best_estimator

        checkpoint["completed_results"] = completed_results
        save_tuning_checkpoint(model_key, checkpoint)
        print(f"Checkpoint saved: {checkpoint_path(model_key).name}")

    if checkpoint["best_estimator"] is None:
        raise RuntimeError(f"No completed candidates found for {config['display_name']}.")

    return checkpoint


def build_comparison_table(tuned_results):
    baseline_results_path = BASE_DIR / "baseline_results.csv"
    if baseline_results_path.exists():
        baseline_results = pd.read_csv(baseline_results_path)
        baseline_results["Stage"] = "Baseline"
    else:
        baseline_results = pd.DataFrame()

    tuned_results_df = pd.DataFrame(tuned_results)
    tuned_results_df["Stage"] = "Tuned + SMOTE"

    comparison_df = pd.concat(
        [baseline_results, tuned_results_df],
        ignore_index=True,
        sort=False,
    )

    ordered_columns = [
        "Stage",
        "Model",
        "Accuracy",
        "Precision",
        "Recall",
        "F1 Score",
        "ROC-AUC",
    ]
    comparison_df = comparison_df[ordered_columns]
    metric_columns = ["Accuracy", "Precision", "Recall", "F1 Score", "ROC-AUC"]
    comparison_df[metric_columns] = comparison_df[metric_columns].round(4)

    return comparison_df


def main():
    X_train, X_test, y_train, y_test = load_data()

    print("Loaded Step 1 train/test files successfully.")
    print(f"X_train shape: {X_train.shape}")
    print(f"X_test shape: {X_test.shape}")

    analyze_class_imbalance(y_train)

    categorical_features, numerical_features = get_feature_groups(X_train)
    preprocessor = build_preprocessor(categorical_features, numerical_features)

    print("\nFeature preprocessing:")
    print(f"Categorical features encoded: {len(categorical_features)}")
    print(f"Numerical features scaled: {len(numerical_features)}")

    print_smote_distribution(X_train, y_train, preprocessor)

    tuning_configs = build_tuning_configs(preprocessor)

    print("\nTuning Random Forest with resumable checkpoints...")
    rf_checkpoint = tune_with_checkpoints(
        "random_forest",
        tuning_configs["random_forest"],
        X_train,
        y_train,
    )

    print("\nTuning XGBoost with resumable checkpoints...")
    xgb_checkpoint = tune_with_checkpoints(
        "xgboost",
        tuning_configs["xgboost"],
        X_train,
        y_train,
    )

    print("\nBest Random Forest parameters:")
    print(rf_checkpoint["best_params"])
    print(f"Best Random Forest CV recall: {rf_checkpoint['best_score']:.4f}")

    print("\nBest XGBoost parameters:")
    print(xgb_checkpoint["best_params"])
    print(f"Best XGBoost CV recall: {xgb_checkpoint['best_score']:.4f}")

    rf_metrics, rf_pred, rf_proba = evaluate_model(
        "Random Forest", rf_checkpoint["best_estimator"], X_test, y_test
    )
    xgb_metrics, xgb_pred, xgb_proba = evaluate_model(
        "XGBoost", xgb_checkpoint["best_estimator"], X_test, y_test
    )

    comparison_df = build_comparison_table([rf_metrics, xgb_metrics])
    comparison_df.to_csv(BASE_DIR / "tuning_results.csv", index=False)

    print("\nTuning comparison table:")
    print(comparison_df.to_string(index=False))

    tuned_only = comparison_df[comparison_df["Stage"] == "Tuned + SMOTE"]
    best_model_row = tuned_only.sort_values(
        by=["Recall", "ROC-AUC"], ascending=False
    ).iloc[0]

    joblib.dump(rf_checkpoint["best_estimator"], BASE_DIR / "tuned_random_forest.pkl")
    joblib.dump(xgb_checkpoint["best_estimator"], BASE_DIR / "tuned_xgboost.pkl")
    best_tuned_models = {
        "Random Forest": rf_checkpoint["best_estimator"],
        "XGBoost": xgb_checkpoint["best_estimator"],
    }
    save_best_tuned_checkpoint(
        best_model_row["Model"],
        best_model_row.to_dict(),
        best_tuned_models[best_model_row["Model"]],
    )

    save_confusion_matrix_plot(
        "Tuned Random Forest",
        y_test,
        rf_pred,
        "tuned_random_forest_confusion_matrix.png",
    )
    save_confusion_matrix_plot(
        "Tuned XGBoost",
        y_test,
        xgb_pred,
        "tuned_xgboost_confusion_matrix.png",
    )
    save_roc_curve(
        "Tuned Random Forest",
        y_test,
        rf_proba,
        "tuned_random_forest_roc_curve.png",
    )
    save_roc_curve(
        "Tuned XGBoost",
        y_test,
        xgb_proba,
        "tuned_xgboost_roc_curve.png",
    )
    save_precision_recall_curve(
        "Tuned Random Forest",
        y_test,
        rf_proba,
        "tuned_random_forest_precision_recall_curve.png",
    )
    save_precision_recall_curve(
        "Tuned XGBoost",
        y_test,
        xgb_proba,
        "tuned_xgboost_precision_recall_curve.png",
    )

    print("\nFiles saved successfully:")
    print("tuned_random_forest.pkl")
    print("tuned_xgboost.pkl")
    print("best_tuned_model_checkpoint.pkl")
    print("best_tuned_model_metadata.csv")
    print("checkpoints/random_forest_tuning_checkpoint.pkl")
    print("checkpoints/xgboost_tuning_checkpoint.pkl")
    print("tuning_results.csv")
    print("tuned_random_forest_confusion_matrix.png")
    print("tuned_xgboost_confusion_matrix.png")
    print("tuned_random_forest_roc_curve.png")
    print("tuned_xgboost_roc_curve.png")
    print("tuned_random_forest_precision_recall_curve.png")
    print("tuned_xgboost_precision_recall_curve.png")

    print("\nBest tuned model:")
    print(f"Best Model: {best_model_row['Model']}")
    print(f"Best Recall: {best_model_row['Recall']:.4f}")
    print(f"Best ROC-AUC: {best_model_row['ROC-AUC']:.4f}")

    print("\nStep 3 completed successfully.")


if __name__ == "__main__":
    main()
