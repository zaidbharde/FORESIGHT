# FORESIGHT Backend

Production-ready FastAPI foundation for FORESIGHT.

## Project Structure

```text
backend/
├── app/
├── api/
│   └── predict.py
├── core/
│   └── config.py
├── models/
├── schemas/
│   ├── request.py
│   └── response.py
├── services/
│   └── prediction_service.py
├── utils/
├── main.py
├── requirements.txt
└── README.md
```

## Install

Use Python 3.11 or newer.

```bash
cd backend
pip install -r requirements.txt
```

## Run

Start the API from the workspace root:

```bash
uvicorn backend.main:app --reload
```

## Access

- API root: http://localhost:8000/
- Health check: http://localhost:8000/health
- Swagger UI: http://localhost:8000/docs
- OpenAPI schema: http://localhost:8000/openapi.json

## Notes

- `/predict` only validates the request body for now.
- XGBoost integration is intentionally deferred to a later sprint.
