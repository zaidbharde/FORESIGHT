from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    amount: float = Field(...)
    trusted_contact: bool
    new_device: bool
    location_anomaly: bool
    hour: int = Field(...)
    transactions_last_hour: int = Field(...)
    transactions_last_24h: int = Field(...)
