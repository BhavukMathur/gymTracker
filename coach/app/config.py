from typing import Literal

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

LlmProviderChoice = Literal["auto", "gemini", "openai"]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Which backend to use: auto prefers OpenAI when OPENAI_API_KEY is set, else Gemini.
    llm_provider: LlmProviderChoice = Field(default="auto", validation_alias="LLM_PROVIDER")

    openai_api_key: str | None = Field(default=None, validation_alias="OPENAI_API_KEY")
    openai_model: str = Field(default="gpt-4o-mini", validation_alias="OPENAI_MODEL")

    gemini_api_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices("GEMINI_API_KEY", "GOOGLE_API_KEY"),
    )
    gemini_model: str = Field(default="gemini-2.0-flash", validation_alias="GEMINI_MODEL")

    backend_url: str = Field(
        default="http://127.0.0.1:8080",
        validation_alias="BACKEND_URL",
    )
    monthly_goal_days: int = Field(default=20, validation_alias="MONTHLY_GOAL_DAYS")
    coach_host: str = Field(default="127.0.0.1", validation_alias="COACH_HOST")
    coach_port: int = Field(default=8090, validation_alias="COACH_PORT")
