# FORESIGHT

FORESIGHT contains an Android app, a FastAPI backend, shared feature contracts, and legacy ML experimentation assets.

## Structure

- `Foresight/` - Android Studio project. Keep Gradle files, wrapper files, `app/`, and Kotlin packages in place.
- `backend/` - FastAPI backend. Startup command: `uvicorn backend.main:app`.
- `shared/` - Cross-platform contracts used by backend and future Android bindings.
- `docs/` - Architecture and dataset/feature documentation.
- `assets/` - Repository-level images and non-Android assets.
- `screenshots/` - App or project screenshots.
- `VISION ON/` - Legacy ML/API/frontend experiment workspace.

## Verification

- Android build: `.\gradlew.bat :Foresight:app:assembleDebug`
- Backend startup: `python -m uvicorn backend.main:app`
