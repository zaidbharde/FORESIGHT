from pydantic import BaseModel


class Settings(BaseModel):
    project_name: str = "FORESIGHT"
    version: str = "0.1.0"
    api_name: str = "foresight-backend"


settings = Settings()
