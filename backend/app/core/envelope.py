"""One response envelope for every endpoint:

{ "success": bool, "data": ..., "error": { "code", "message_en", "message_bn" } }
"""

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException


def ok(data: Any = None) -> dict[str, Any]:
    return {"success": True, "data": data, "error": None}


class AppError(Exception):
    """A user-facing error with an English and a Bangla message."""

    def __init__(self, status: int, code: str, message_en: str, message_bn: str):
        self.status = status
        self.code = code
        self.message_en = message_en
        self.message_bn = message_bn


def _error(status: int, code: str, en: str, bn: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"success": False, "data": None, "error": {"code": code, "message_en": en, "message_bn": bn}},
    )


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _app_error(_: Request, exc: AppError) -> JSONResponse:
        return _error(exc.status, exc.code, exc.message_en, exc.message_bn)

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        first = exc.errors()[0] if exc.errors() else {}
        field = ".".join(str(p) for p in first.get("loc", [])[1:]) or "request"
        return _error(
            422,
            "validation_error",
            f"Please check {field}.",
            "অনুগ্রহ করে দেওয়া তথ্যগুলো আবার দেখুন।",
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        if exc.status_code == 404:
            return _error(404, "not_found", "Not found.", "খুঁজে পাওয়া যায়নি।")
        if exc.status_code == 405:
            return _error(405, "method_not_allowed", "Method not allowed.", "এই অনুরোধ গ্রহণযোগ্য নয়।")
        return _error(exc.status_code, "http_error", str(exc.detail), "কিছু একটা ভুল হয়েছে। আবার চেষ্টা করুন।")

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, __: Exception) -> JSONResponse:
        # Never leak internals (or personal data) to the client.
        return _error(500, "server_error", "Something went wrong. Please try again.", "কিছু একটা ভুল হয়েছে। আবার চেষ্টা করুন।")


class Errors:
    """Catalogue of the errors the API returns, so codes stay consistent."""

    @staticmethod
    def invalid_phone() -> AppError:
        return AppError(422, "invalid_phone", "Enter a valid Bangladeshi mobile number.", "সঠিক বাংলাদেশি মোবাইল নম্বর দিন।")

    @staticmethod
    def otp_cooldown(seconds: int) -> AppError:
        return AppError(
            429,
            "otp_cooldown",
            f"Please wait {seconds} seconds before asking for a new code.",
            f"নতুন কোড চাইতে {seconds} সেকেন্ড অপেক্ষা করুন।",
        )

    @staticmethod
    def otp_rate_limited() -> AppError:
        return AppError(
            429,
            "otp_rate_limited",
            "Too many codes requested. Please try again in a few minutes.",
            "অনেকবার কোড চাওয়া হয়েছে। কয়েক মিনিট পরে আবার চেষ্টা করুন।",
        )

    @staticmethod
    def otp_invalid() -> AppError:
        return AppError(400, "otp_invalid", "The code is not correct.", "কোডটি সঠিক নয়।")

    @staticmethod
    def otp_expired() -> AppError:
        return AppError(400, "otp_expired", "The code has expired. Ask for a new one.", "কোডের মেয়াদ শেষ। নতুন কোড চান।")

    @staticmethod
    def otp_too_many_attempts() -> AppError:
        return AppError(
            429,
            "otp_too_many_attempts",
            "Too many wrong tries. Ask for a new code.",
            "অনেকবার ভুল কোড দেওয়া হয়েছে। নতুন কোড চান।",
        )

    @staticmethod
    def unauthorized() -> AppError:
        return AppError(401, "unauthorized", "Please log in again.", "অনুগ্রহ করে আবার লগইন করুন।")

    @staticmethod
    def invalid_refresh() -> AppError:
        return AppError(
            401,
            "invalid_refresh_token",
            "Your session has ended. Please log in again.",
            "আপনার সেশন শেষ হয়েছে। আবার লগইন করুন।",
        )
