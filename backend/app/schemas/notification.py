import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class NotificationOut(BaseModel):
    id: uuid.UUID
    type: str
    title_en: str
    title_bn: str
    body_en: str
    body_bn: str
    data: dict
    is_read: bool
    created_at: datetime


class NotificationPage(BaseModel):
    items: list[NotificationOut]
    unread: int


class DeviceIn(BaseModel):
    token: str = Field(min_length=20, max_length=255, description="FCM registration token")
    platform: Literal["android"] = "android"
