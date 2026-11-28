from fastapi import APIRouter

from app.api.deps import CurrentUser, DbSession
from app.api.v1.buses import StoreDep
from app.core.envelope import ok
from app.providers.registry import ProvidersDep
from app.schemas.common import Envelope
from app.schemas.user import UserOut, UserUpdateIn
from app.services.account import delete_account

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
async def delete_me(user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    """In-app account deletion (Google Play requirement). Personal data is removed; bookings are
    kept for accounting without anything that identifies the person (see services/account.py)."""
    await delete_account(db, providers, store, user)
    return ok(None)
