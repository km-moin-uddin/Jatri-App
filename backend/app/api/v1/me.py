from fastapi import APIRouter
from sqlalchemy import delete

from app.api.deps import CurrentUser, DbSession
from app.core.envelope import ok
from app.models import RefreshToken
from app.schemas.common import Envelope
from app.schemas.user import UserOut, UserUpdateIn

router = APIRouter(prefix="/me", tags=["profile"])


@router.get("", response_model=Envelope[UserOut])
async def get_me(user: CurrentUser):
    return ok(UserOut.model_validate(user))


@router.patch("", response_model=Envelope[UserOut])
async def update_me(body: UserUpdateIn, user: CurrentUser, db: DbSession):
    for field, value in body.model_dump(exclude_unset=True).items():
        if field == "name" and value is not None:
            value = " ".join(value.split())
        setattr(user, field, value)
    await db.commit()
    await db.refresh(user)
    return ok(UserOut.model_validate(user))


@router.delete("", response_model=Envelope[None])
async def delete_me(user: CurrentUser, db: DbSession):
    """In-app account deletion (Google Play requirement). Personal data is removed; bookings that
    must be kept for accounting will be anonymised here once they exist (Phase 3+)."""
    await db.execute(delete(RefreshToken).where(RefreshToken.user_id == user.id))
    await db.delete(user)
    await db.commit()
    return ok(None)
