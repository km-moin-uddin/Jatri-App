package com.jatri.core.network

import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(initial: StoredSession? = null) : SessionStore {
    val state = MutableStateFlow(initial)
    override val session = state
    override suspend fun current() = state.value
    override suspend fun save(session: StoredSession) {
        state.value = session
    }
    override suspend fun clear() {
        state.value = null
    }
}
