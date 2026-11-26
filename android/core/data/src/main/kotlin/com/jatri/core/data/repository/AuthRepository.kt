package com.jatri.core.data.repository

import com.jatri.core.common.dispatch.ApplicationScope
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.SessionState
import com.jatri.core.model.User
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.mock.MockJatriApi
import com.jatri.core.network.model.OtpSendRequest
import com.jatri.core.network.model.OtpVerifyRequest
import com.jatri.core.network.model.RefreshRequest
import com.jatri.core.network.model.UserDto
import com.jatri.core.network.model.UserUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class OtpSent(val phone: String, val resendAfterSeconds: Int)

/**
 * Phone + OTP login and the logged-in session. If a token refresh fails, the session store is
 * cleared and [session] flips to LoggedOut on its own. All calls throw
 * [com.jatri.core.model.AppException] with a UI-ready error on failure.
 */
interface AuthRepository {
    val session: StateFlow<SessionState>
    suspend fun sendOtp(phone: String): OtpSent

    /** @return true when this phone number has just signed up (ask for a name next). */
    suspend fun verifyOtp(phone: String, code: String): Boolean
    suspend fun updateProfile(name: String? = null, email: String? = null, language: AppLanguage? = null): User
    suspend fun refreshProfile()
    suspend fun logout()
    suspend fun deleteAccount()
}

@Singleton
internal class DefaultAuthRepository @Inject constructor(
    private val api: JatriApi,
    private val store: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {

    override val session: StateFlow<SessionState> = store.session
        .map { s -> if (s == null) SessionState.LoggedOut else SessionState.LoggedIn(s.toUser()) }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

    init {
        // The mock backend keeps nothing across app restarts: hand it the stored session.
        if (api is MockJatriApi) {
            scope.launch {
                store.current()?.let { api.restore(it.phone, it.toDto(), it.accessToken, it.refreshToken) }
            }
        }
    }

    override suspend fun sendOtp(phone: String): OtpSent {
        val res = apiCall { api.sendOtp(OtpSendRequest(phone)) }
        return OtpSent(res.phone, res.resendAfter)
    }

    override suspend fun verifyOtp(phone: String, code: String): Boolean {
        val res = apiCall { api.verifyOtp(OtpVerifyRequest(phone, code, deviceInfo = deviceInfo())) }
        store.save(
            StoredSession(
                accessToken = res.tokens.accessToken,
                refreshToken = res.tokens.refreshToken,
                userId = res.user.id,
                phone = res.user.phone,
                name = res.user.name,
                email = res.user.email,
                language = res.user.language,
            ),
        )
        return res.isNewUser || res.user.name.isNullOrBlank()
    }

    override suspend fun updateProfile(name: String?, email: String?, language: AppLanguage?): User {
        val dto = apiCall { api.updateMe(UserUpdateRequest(name = name, email = email, language = language?.tag)) }
        saveUser(dto)
        return dto.toUser()
    }

    override suspend fun refreshProfile() {
        saveUser(apiCall { api.me() })
    }

    override suspend fun logout() {
        val current = store.current()
        // Clear locally first: logging out must work even with no internet.
        store.clear()
        if (current != null) runCatching { apiCall { api.logout(RefreshRequest(current.refreshToken)) } }
    }

    override suspend fun deleteAccount() {
        apiCall { api.deleteMe() }
        store.clear()
    }

    private suspend fun saveUser(dto: UserDto) {
        val current = store.current() ?: return
        store.save(current.copy(name = dto.name, email = dto.email, language = dto.language, phone = dto.phone))
    }

    private fun deviceInfo(): String = "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}".take(200)
}

private fun StoredSession.toUser() = User(userId, phone, name, email, AppLanguage.fromTag(language))
private fun StoredSession.toDto() = UserDto(userId, phone, name, email, language)
private fun UserDto.toUser() = User(id, phone, name, email, AppLanguage.fromTag(language))
