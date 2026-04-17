from __future__ import annotations

import logging
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, model_validator

from app.agent_service import run_coach_turn
from app.config import Settings
from app.llm_factory import LlmConfigurationError, resolved_provider

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

settings = Settings()

app = FastAPI(title="Gym Tracker Coach", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def get_bearer_token(authorization: Annotated[str | None, Header()] = None) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization[7:].strip()
    if not token:
        raise HTTPException(status_code=401, detail="Empty bearer token")
    return token


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=12000)


class CoachChatRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=4000)
    history: list[ChatTurn] = Field(default_factory=list, max_length=24)
    # Matches the calendar month open in the UI so the agent does not guess year/month wrong.
    context_year: int | None = Field(default=None, ge=2000, le=2100)
    context_month: int | None = Field(default=None, ge=1, le=12)

    @model_validator(mode="after")
    def calendar_context_both_or_neither(self):
        y, m = self.context_year, self.context_month
        if y is not None and m is None:
            raise ValueError("context_month is required when context_year is set")
        if m is not None and y is None:
            raise ValueError("context_year is required when context_month is set")
        return self


class CoachChatResponse(BaseModel):
    reply: str


def _active_model_label() -> str | None:
    p = resolved_provider(settings)
    if p == "openai":
        return settings.openai_model
    if p == "gemini":
        return settings.gemini_model
    return None


@app.get("/coach/health")
def coach_health() -> dict:
    p = resolved_provider(settings)
    return {
        "status": "ok",
        "llm_provider": p,
        "llm_provider_choice": settings.llm_provider,
        "openai_configured": bool(settings.openai_api_key),
        "gemini_configured": bool(settings.gemini_api_key),
        "active_model": _active_model_label(),
    }


@app.post("/coach/chat", response_model=CoachChatResponse)
def coach_chat(
    body: CoachChatRequest,
    bearer: Annotated[str, Depends(get_bearer_token)],
) -> CoachChatResponse:
    if resolved_provider(settings) is None:
        raise HTTPException(
            status_code=503,
            detail=(
                "Coach is not configured: set OPENAI_API_KEY or GEMINI_API_KEY (or GOOGLE_API_KEY). "
                "Use LLM_PROVIDER=auto|openai|gemini to choose."
            ),
        )
    history_pairs: list[tuple[str, str]] = [(t.role, t.content) for t in body.history]
    try:
        reply = run_coach_turn(
            settings=settings,
            jwt_token=bearer,
            user_message=body.message.strip(),
            history=history_pairs,
            calendar_year=body.context_year,
            calendar_month=body.context_month,
        )
    except LlmConfigurationError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    except Exception as e:
        logger.exception("Coach invocation failed")
        raise HTTPException(status_code=502, detail=f"Coach error: {e!s}") from e
    if not reply:
        reply = "I could not produce a reply. Please try again with a shorter question."
    return CoachChatResponse(reply=reply)
