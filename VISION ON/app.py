from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict

import risk_engine
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field


BASE_DIR = Path(__file__).resolve().parent
FRAUD_REPORTS_PATH = BASE_DIR / "fraud_reports.json"


class TransactionRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    TransactionAmt: float
    card1: int
    card2: float
    card3: float
    card4: str
    card5: float
    card6: str
    addr1: float
    addr2: float
    ProductCD: str
    DeviceType: str
    hour_of_day: int = Field(ge=0, le=23)
    is_late_night: int = Field(ge=0, le=1)
    is_large_amount: int = Field(ge=0, le=1)
    is_round_amount: int = Field(ge=0, le=1)
    amount_to_average_ratio: float = Field(ge=0)
    transactions_last_hour: int = Field(ge=0)
    transactions_last_day: int = Field(ge=0)
    days_since_last_transaction: float = Field(ge=0)
    device_change_flag: int = Field(ge=0, le=1)

    active_call_detected: bool | None = None
    sim_changed_within_7_days: bool | None = None
    otp_request_detected: bool | None = None
    otp_receive_money_scam: bool | None = None
    payment_purpose: str | None = None


class FraudReportRequest(BaseModel):
    transaction_id: str = Field(min_length=1)
    risk_score: int = Field(ge=0, le=100)
    reported: bool = True


app = FastAPI(title="Fraud Risk Scoring API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup_event() -> None:
    risk_engine.get_model()
    app.state.model_loaded = True


@app.exception_handler(ValueError)
def value_error_handler(_: Any, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"detail": str(exc)})


@app.exception_handler(Exception)
def unhandled_exception_handler(_: Any, exc: Exception) -> JSONResponse:
    return JSONResponse(status_code=500, content={"detail": f"Internal server error: {exc}"})


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "status": "healthy",
        "model_loaded": bool(getattr(app.state, "model_loaded", False)),
    }


@app.post("/predict")
def predict(transaction: TransactionRequest) -> Dict[str, Any]:
    if not bool(getattr(app.state, "model_loaded", False)):
        raise HTTPException(status_code=503, detail="Model is not loaded")

    payload = transaction.model_dump(exclude_none=True)
    result = risk_engine.predict_transaction(payload)
    return result


def _load_reports() -> list[dict[str, Any]]:
    if not FRAUD_REPORTS_PATH.exists():
        return []

    with open(FRAUD_REPORTS_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, list):
        raise ValueError("fraud_reports.json must contain a JSON array")

    return data


def _save_reports(reports: list[dict[str, Any]]) -> None:
    with open(FRAUD_REPORTS_PATH, "w", encoding="utf-8") as f:
        json.dump(reports, f, indent=2)


@app.post("/report-fraud")
def report_fraud(report: FraudReportRequest) -> Dict[str, Any]:
    existing = _load_reports()
    payload = report.model_dump()
    payload["timestamp"] = datetime.now(timezone.utc).isoformat()
    existing.append(payload)
    _save_reports(existing)

    return {
        "status": "success",
        "message": "Fraud report saved",
        "transaction_id": report.transaction_id,
    }


@app.get("/demo-scenarios")
def demo_scenarios() -> Dict[str, Any]:
    return risk_engine.generate_demo_transactions()
