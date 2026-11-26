from typing import Annotated

from fastapi import Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.envelope import Errors
from app.core.security import decode_access_token
from app.db.session import get_session
from app.models import User
from app.services.sms import SmsSender, get_sms_sender

DbSession = Annotated[AsyncSession, Depends(get_session)]
AppSettings = Annotated[Settings, Depends(get_settings)]


def sms_sender(settings: AppSettings) -> SmsSender:
    return get_sms_sender(settings)


Sms = Annotated[SmsSender, Depends(sms_sender)]


async def current_user(
    db: DbSession,
    settings: AppSettings,
    authorization: Annotated[str | None, Header()] = None,
) -> User:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise Errors.unauthorized()
    user_id = decode_access_token(authorization[7:].strip(), settings)
    if user_id is None:
        raise Errors.unauthorized()
    user = await db.get(User, user_id)
    if user is None or user.status != "active":
        raise Errors.unauthorized()
    return user


CurrentUser = Annotated[User, Depends(current_user)]
