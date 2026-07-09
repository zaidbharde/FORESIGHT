from pathlib import Path

import numpy as np
import pandas as pd


BASE_DIR = Path(__file__).resolve().parent
RANDOM_STATE = 42

TRACKING_COLUMNS = ["TransactionID"]
TARGET_COLUMN = "isFraud"

RAW_MODEL_FEATURES = [
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
    "DeviceInfo",
    "id_30",
    "id_31",
    "P_emaildomain",
    "R_emaildomain",
]

ENGINEERED_FEATURES = [
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

MODEL_FEATURES = RAW_MODEL_FEATURES + ENGINEERED_FEATURES

SOURCE_COLUMNS = [
    "TransactionID",
    "isFraud",
    "TransactionDT",
    *RAW_MODEL_FEATURES,
]


def find_input_file(file_name):
    candidate_paths = [
        BASE_DIR / file_name,
        BASE_DIR / "ml-training" / "data" / file_name,
    ]

    for path in candidate_paths:
        if path.exists():
            return path

    searched_paths = "\n".join(str(path) for path in candidate_paths)
    raise FileNotFoundError(
        f"Could not find {file_name}. Searched these paths:\n{searched_paths}"
    )


def load_and_merge_data():
    train_transaction_path = find_input_file("train_transaction.csv")
    train_identity_path = find_input_file("train_identity.csv")

    print("Input files:")
    print(f"train_transaction: {train_transaction_path}")
    print(f"train_identity: {train_identity_path}")

    train_transaction = pd.read_csv(train_transaction_path)
    train_identity = pd.read_csv(train_identity_path)

    print("\nDataset shape before merge:")
    print(f"train_transaction shape: {train_transaction.shape}")
    print(f"train_identity shape: {train_identity.shape}")

    df = train_transaction.merge(
        train_identity,
        on="TransactionID",
        how="left",
    )

    print("\nDataset shape after merge:")
    print(f"merged dataset shape: {df.shape}")

    missing_source_columns = [col for col in SOURCE_COLUMNS if col not in df.columns]
    if missing_source_columns:
        raise KeyError(f"Missing expected source columns: {missing_source_columns}")

    return df[SOURCE_COLUMNS].copy()


def impute_missing_values(df):
    numerical_columns = df.select_dtypes(include=["int64", "float64"]).columns.tolist()
    categorical_columns = df.select_dtypes(include=["object", "string"]).columns.tolist()

    for excluded_column in [TARGET_COLUMN, *TRACKING_COLUMNS]:
        if excluded_column in numerical_columns:
            numerical_columns.remove(excluded_column)

    for column in numerical_columns:
        df[column] = df[column].fillna(df[column].median())

    for column in categorical_columns:
        df[column] = df[column].fillna("Unknown")

    return df


def add_time_and_amount_features(df):
    df["hour_of_day"] = ((df["TransactionDT"] // 3600) % 24).astype(int)
    df["is_late_night"] = df["hour_of_day"].between(0, 5).astype(int)

    large_amount_threshold = df["TransactionAmt"].quantile(0.95)
    df["is_large_amount"] = (df["TransactionAmt"] > large_amount_threshold).astype(int)
    df["is_round_amount"] = (df["TransactionAmt"] % 1 == 0).astype(int)

    return df


def count_prior_transactions_within_window(times, window_seconds):
    values = times.to_numpy()
    left_positions = np.searchsorted(values, values - window_seconds, side="left")
    current_positions = np.arange(len(values))
    return current_positions - left_positions


def add_customer_behavior_features(df):
    df = df.sort_values(["card1", "TransactionDT", "TransactionID"]).copy()

    prior_average_amount = (
        df.groupby("card1", sort=False)["TransactionAmt"]
        .transform(lambda series: series.expanding().mean().shift(1))
    )
    df["amount_to_average_ratio"] = (
        df["TransactionAmt"] / prior_average_amount
    ).replace([np.inf, -np.inf], np.nan)
    df["amount_to_average_ratio"] = df["amount_to_average_ratio"].fillna(1.0)

    seconds_since_last_transaction = df.groupby("card1", sort=False)[
        "TransactionDT"
    ].diff()
    df["days_since_last_transaction"] = (
        seconds_since_last_transaction / 86400
    ).fillna(999.0)

    previous_device = df.groupby("card1", sort=False)["DeviceInfo"].shift(1)
    df["device_change_flag"] = (
        previous_device.notna() & df["DeviceInfo"].ne(previous_device)
    ).astype(int)

    df["transactions_last_hour"] = (
        df.groupby("card1", sort=False)["TransactionDT"]
        .transform(lambda times: count_prior_transactions_within_window(times, 3600))
        .astype(int)
    )
    df["transactions_last_day"] = (
        df.groupby("card1", sort=False)["TransactionDT"]
        .transform(lambda times: count_prior_transactions_within_window(times, 86400))
        .astype(int)
    )

    return df.sort_index()


def build_processed_dataset():
    df = load_and_merge_data()

    print("\nSelected source columns:")
    for column in SOURCE_COLUMNS:
        print(column)

    print("\nMissing value summary before imputation:")
    missing_summary = df.isnull().sum()
    missing_summary = missing_summary[missing_summary > 0].sort_values(ascending=False)
    print(missing_summary if not missing_summary.empty else "No missing values.")

    df = impute_missing_values(df)
    df = add_time_and_amount_features(df)
    df = add_customer_behavior_features(df)

    return df


def stratified_train_test_split(df):
    test_index_parts = []

    for fraud_label, group in df.groupby(TARGET_COLUMN):
        shuffled_group = group.sample(frac=1, random_state=RANDOM_STATE)
        test_size = int(round(len(shuffled_group) * 0.20))
        test_index_parts.append(shuffled_group.head(test_size).index)

    test_indices = test_index_parts[0].append(test_index_parts[1:])
    test_df = df.loc[test_indices].sort_index()
    train_df = df.drop(index=test_indices).sort_index()

    return train_df, test_df


def validate_outputs(X_train, X_test):
    missing_engineered_features = [
        feature for feature in ENGINEERED_FEATURES if feature not in X_train.columns
    ]
    transaction_id_included = (
        "TransactionID" in X_train.columns or "TransactionID" in X_test.columns
    )

    print("\nFinal feature list:")
    for index, feature in enumerate(X_train.columns, start=1):
        print(f"{index:02d}. {feature}")

    print(f"\nNumber of features: {X_train.shape[1]}")

    print("\nMissing engineered features check:")
    if missing_engineered_features:
        for feature in missing_engineered_features:
            print(f"{feature}: MISSING")
    else:
        print("All required engineered features are present.")

    print("\nTransactionID training feature check:")
    print(f"TransactionID included in X_train/X_test: {transaction_id_included}")

    if missing_engineered_features:
        raise RuntimeError(
            f"Missing engineered features: {missing_engineered_features}"
        )
    if transaction_id_included:
        raise RuntimeError("TransactionID must not be included in training features.")


def save_outputs(df, train_df, test_df):
    X_train = train_df[MODEL_FEATURES].copy()
    X_test = test_df[MODEL_FEATURES].copy()
    y_train = train_df[TARGET_COLUMN].copy()
    y_test = test_df[TARGET_COLUMN].copy()

    validate_outputs(X_train, X_test)

    df.to_csv(BASE_DIR / "processed_data.csv", index=False)
    X_train.to_csv(BASE_DIR / "X_train.csv", index=False)
    X_test.to_csv(BASE_DIR / "X_test.csv", index=False)
    y_train.to_csv(BASE_DIR / "y_train.csv", index=False)
    y_test.to_csv(BASE_DIR / "y_test.csv", index=False)

    print("\nFiles saved successfully:")
    print("processed_data.csv")
    print("X_train.csv")
    print("X_test.csv")
    print("y_train.csv")
    print("y_test.csv")

    print("\nOutput shapes:")
    print(f"processed_data shape: {df.shape}")
    print(f"X_train shape: {X_train.shape}")
    print(f"X_test shape: {X_test.shape}")
    print(f"y_train shape: {y_train.shape}")
    print(f"y_test shape: {y_test.shape}")


def print_fraud_distribution(df):
    fraud_distribution = df[TARGET_COLUMN].value_counts().sort_index()
    fraud_distribution_percent = (
        df[TARGET_COLUMN].value_counts(normalize=True).sort_index() * 100
    )

    print("\nFraud distribution summary:")
    print(
        pd.DataFrame(
            {
                "count": fraud_distribution,
                "percentage": fraud_distribution_percent.round(2),
            }
        )
    )


def main():
    df = build_processed_dataset()
    print_fraud_distribution(df)

    train_df, test_df = stratified_train_test_split(df)
    save_outputs(df, train_df, test_df)

    print("\nStep 1 corrected preprocessing completed successfully.")


if __name__ == "__main__":
    main()
