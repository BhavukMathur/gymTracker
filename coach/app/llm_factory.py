"""Factory for LangChain chat models (OpenAI vs Gemini)."""

from __future__ import annotations

from typing import Literal

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI

from app.config import Settings

ProviderId = Literal["gemini", "openai"]


class LlmConfigurationError(ValueError):
    """Raised when no usable LLM provider is configured."""


def resolved_provider(settings: Settings) -> ProviderId | None:
    """Pick provider from LLM_PROVIDER and available keys (auto: OpenAI first, then Gemini)."""
    choice = settings.llm_provider
    if choice == "openai":
        return "openai" if settings.openai_api_key else None
    if choice == "gemini":
        return "gemini" if settings.gemini_api_key else None
    # auto
    if settings.openai_api_key:
        return "openai"
    if settings.gemini_api_key:
        return "gemini"
    return None


def create_chat_model(settings: Settings) -> tuple[BaseChatModel, ProviderId]:
    """Build the LangChain chat model for the active provider."""
    provider = resolved_provider(settings)
    if provider is None:
        raise LlmConfigurationError(
            "No LLM configured: set OPENAI_API_KEY or GEMINI_API_KEY (or GOOGLE_API_KEY), "
            "and LLM_PROVIDER=auto|openai|gemini as needed."
        )
    if provider == "openai":
        assert settings.openai_api_key is not None
        llm = ChatOpenAI(
            model=settings.openai_model,
            api_key=settings.openai_api_key,
            temperature=0.35,
        )
        return llm, "openai"
    assert settings.gemini_api_key is not None
    llm = ChatGoogleGenerativeAI(
        model=settings.gemini_model,
        google_api_key=settings.gemini_api_key,
        temperature=0.35,
    )
    return llm, "gemini"
