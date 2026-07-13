# FORESIGHT IEEE-CIS Processed Dataset

This dataset package contains the processed tabular artifacts used by the FORESIGHT fraud-risk project.

## Files

- `processed_data.csv` - cleaned and feature-engineered dataset.
- `X_train.csv` - training feature matrix.
- `X_test.csv` - test feature matrix.
- `y_train.csv` - training labels.
- `y_test.csv` - test labels.
- `FeatureSchema.md` - semantic feature contract for the FORESIGHT model inputs.
- `DatasetEngineeringPlan.md` - dataset preparation and engineering plan.
- `LICENSE` - dataset redistribution and attribution notice.

## Intended Use

Use these files for reproducible model training, evaluation, and project review. The backend application does not read files from this folder at runtime; this package is prepared only for dataset upload and sharing.

## Project Runtime Note

The FORESIGHT backend model file remains separate from this dataset package at:

```text
backend/models/xgboost_baseline.pkl
```

Do not move backend source files, Android files, or runtime model files into this dataset folder.
