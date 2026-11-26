package com.jatri.core.data

import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.SessionState
import com.jatri.core.network.mock.MockJatriApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class InMemoryStore : SessionStore {
    val state = MutableStateFlow<StoredSession?>(null)
    override val session = state
    override suspend fun current() = state.value
    override suspend fun save(session: StoredSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

/** Runs the repository against the mock backend, which follows the real API's rules. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val store = InMemoryStore()
    private val api = MockJatriApi()

    private fun TestScope.repo() = DefaultAuthRepository(api, store, backgroundScope)

    @Test
    fun `new user logs in, names themselves, and is recognised next time`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        assertEquals(SessionState.LoggedOut, repo.session.first { it != SessionState.Unknown })

        assertEquals("01712345678", repo.sendOtp("+880 1712-345678").phone)
        assertTrue(repo.verifyOtp("01712345678", MockJatriApi.MOCK_OTP))
        val loggedIn = repo.session.first { it is SessionState.LoggedIn } as SessionState.LoggedIn
        assertEquals("01712345678", loggedIn.user.phone)

        repo.updateProfile(name = "  Rahim  Uddin ")
        assertEquals("Rahim Uddin", store.current()?.name)

        repo.logout()
        assertEquals(SessionState.LoggedOut, repo.session.first { it == SessionState.LoggedOut })
        assertFalse(repo.verifyOtp("01712345678", MockJatriApi.MOCK_OTP)) // has a name now
    }

    @Test
    fun `wrong code gives a bilingual error and no session`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        try {
            repo.verifyOtp("01712345678", "000000")
            fail("expected AppException")
        } catch (e: AppException) {
            val error = e.error as AppError.Api
            assertEquals("otp_invalid", error.code)
            assertEquals("কোডটি সঠিক নয়।", error.messageBn)
        }
        assertEquals(null, store.current())
    }

    @Test
    fun `invalid phone is rejected`() = runTest(UnconfinedTestDispatcher()) {
        try {
            repo().sendOtp("01212345678")
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals("invalid_phone", (e.error as AppError.Api).code)
        }
    }

    @Test
    fun `delete account clears the session`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo()
        repo.verifyOtp("01812345678", MockJatriApi.MOCK_OTP)
        repo.deleteAccount()
        assertEquals(null, store.current())
        assertTrue(repo.verifyOtp("01812345678", MockJatriApi.MOCK_OTP)) // signs up as new again
    }
}
