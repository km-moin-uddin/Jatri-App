package com.jatri.core.model

/** The logged-in traveller. Phone is always the canonical 01XXXXXXXXX form. */
data class User(
    val id: String,
    val phone: String,
    val name: String?,
    val email: String?,
    val language: AppLanguage,
)

sealed interface SessionState {
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

/**
 * A failure the UI can show. [Api] carries the backend's own English and Bangla message.
 */
sealed interface AppError {
    data class Api(val code: String, val messageEn: String, val messageBn: String) : AppError {
        fun message(language: AppLanguage) = if (language == AppLanguage.BANGLA) messageBn else messageEn
    }
    data object Network : AppError
    data object Unknown : AppError
}

class AppException(val error: AppError) : Exception(error.toString())
