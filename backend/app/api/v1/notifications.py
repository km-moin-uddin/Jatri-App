import uuid
from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Query
from sqlalchemy import delete, func, select, update

from app.api.deps import CurrentUser, DbSession
from app.core.envelope import AppError, ok
from app.core.security import now
from app.models import DeviceToken, Notification
from app.schemas.common import Envelope
from app.schemas.notification import DeviceIn, NotificationOut, NotificationPage

router = APIRouter(tags=["notifications"])


def _out(n: Notification) -> NotificationOut:
    return NotificationOut(
        id=n.id,
        type=n.type,
        title_en=n.title_en,
        title_bn=n.title_bn,
        body_en=n.body_en,
        body_bn=n.body_bn,
        data=n.data or {},
        is_read=n.is_read,
        created_at=n.created_at,
    )


@router.get("/notifications", response_model=Envelope[NotificationPage])
async def inbox(
    user: CurrentUser,
    db: DbSession,
    limit: Annotated[int, Query(ge=1, le=100)] = 30,
    before: Annotated[datetime | None, Query(description="created_at of the last item seen, for the next page")] = None,
):
    stmt = select(Notification).where(Notification.user_id == user.id)
    if before:
        stmt = stmt.where(Notification.created_at < before)
    rows = (await db.scalars(stmt.order_by(Notification.created_at.desc()).limit(limit))).all()
    unread = await db.scalar(select(func.count()).where(Notification.user_id == user.id, Notification.is_read.is_(False)))
    return ok(NotificationPage(items=[_out(n) for n in rows], unread=unread or 0))


@router.post("/notifications/{notification_id}/read", response_model=Envelope[NotificationOut])
async def mark_read(notification_id: uuid.UUID, user: CurrentUser, db: DbSession):
    n = await db.get(Notification, notification_id)
    if n is None or n.user_id != user.id:
        raise AppError(404, "notification_not_found", "Notification not found.", "নোটিফিকেশন পাওয়া যায়নি।")
    if not n.is_read:
        n.is_read, n.read_at = True, now()
        await db.commit()
        await db.refresh(n)
    return ok(_out(n))


@router.post("/notifications/read-all", response_model=Envelope[None])
async def mark_all_read(user: CurrentUser, db: DbSession):
    await db.execute(
        update(Notification)
        .where(Notification.user_id == user.id, Notification.is_read.is_(False))
        .values(is_read=True, read_at=now())
    )
    await db.commit()
    return ok()


@router.post("/devices", response_model=Envelope[None])
async def register_device(body: DeviceIn, user: CurrentUser, db: DbSession):
    """Registers this phone for push. A token moves to the account that registered it last."""
    existing = await db.scalar(select(DeviceToken).where(DeviceToken.token == body.token))
    if existing:
        existing.user_id, existing.platform = user.id, body.platform
    else:
        db.add(DeviceToken(user_id=user.id, token=body.token, platform=body.platform))
    await db.commit()
    return ok()


@router.delete("/devices/{token}", response_model=Envelope[None])
async def unregister_device(token: str, user: CurrentUser, db: DbSession):
    """On logout: stop pushing to this phone."""
    await db.execute(delete(DeviceToken).where(DeviceToken.token == token, DeviceToken.user_id == user.id))
    await db.commit()
    return ok()
