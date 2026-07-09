from fastapi import FastAPI, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from backend.api.predict import router as predict_router
from backend.core.config import settings
from backend.services.prediction_service import prediction_service


app = FastAPI(title=settings.project_name, version=settings.version)


@app.on_event("startup")
async def startup_event() -> None:
    prediction_service.load_model()


@app.get("/")
async def root() -> dict[str, str]:
    return {"project": "FORESIGHT", "status": "Backend Running"}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "healthy"}


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=jsonable_encoder({"detail": exc.detail}),
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content=jsonable_encoder({"detail": exc.errors()}),
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"},
    )


app.include_router(predict_router)
