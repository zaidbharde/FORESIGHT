from fastapi import APIRouter, Depends

from backend.schemas.request import PredictionRequest
from backend.schemas.response import PredictionResponse
from backend.services.prediction_service import PredictionService, get_prediction_service


router = APIRouter(tags=["Prediction"])


@router.post("/predict")
async def predict(
    payload: PredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
) -> PredictionResponse:
    service.validate_request(payload)
    return service.predict(payload)
