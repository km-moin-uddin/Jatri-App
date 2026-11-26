from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class ApiError(BaseModel):
    code: str
    message_en: str
    message_bn: str


class Envelope(BaseModel, Generic[T]):
    """Documents the response envelope in OpenAPI."""

    success: bool
    data: T | None = None
    error: ApiError | None = None
