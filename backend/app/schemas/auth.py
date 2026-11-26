from pydantic import BaseModel, Field

from app.schemas.user import UserOut


class OtpSendIn(BaseModel):
    phone: str = Field(min_length=10, max_length=20, examples=["01712345678"])


class OtpSendOut(BaseModel):
    phone: str
    expires_in: int
    resend_after: int


class OtpVerifyIn(BaseModel):
    phone: str = Field(min_length=10, max_length=20)
    code: str = Field(pattern=r"^\d{4,8}$")
    device_info: str | None = Field(default=None, max_length=200)


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"  # noqa: S105 - not a secret
    expires_in: int


class AuthOut(BaseModel):
    tokens: TokenPair
    user: UserOut
    is_new_user: bool


class RefreshIn(BaseModel):
    refresh_token: str = Field(min_length=20, max_length=200)


class LogoutIn(BaseModel):
    refresh_token: str = Field(min_length=20, max_length=200)
