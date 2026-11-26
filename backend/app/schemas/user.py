import uuid

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    phone: str
    name: str | None
    email: str | None
    language: str


class UserUpdateIn(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=80)
    email: EmailStr | None = None
    language: str | None = Field(default=None, pattern="^(bn|en)$")
