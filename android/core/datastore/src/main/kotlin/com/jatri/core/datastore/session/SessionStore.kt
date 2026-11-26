package com.jatri.core.datastore.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Everything about the logged-in session. Stored encrypted as one blob. */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val phone: String,
    val name: String? = null,
    val email: String? = null,
    val language: String = "bn",
)

/** Holds the session. Tokens never leave this store unencrypted on disk. */
interface SessionStore {
    val session: Flow<StoredSession?>
    suspend fun current(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}

/** Encrypts small strings. Production: AES-256-GCM with a non-exportable Android Keystore key. */
interface SessionCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
}

internal class KeystoreSessionCipher @Inject constructor() : SessionCipher {
    private val alias = "jatri_session_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull() // e.g. key lost after a restore: treat as logged out

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionPrefs

private val Context.sessionPreferences: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class EncryptedSessionStore @Inject constructor(
    @SessionPrefs private val dataStore: DataStore<Preferences>,
    private val cipher: SessionCipher,
) : SessionStore {
    private val key = stringPreferencesKey("session_v1")
    private val json = Json { ignoreUnknownKeys = true }

    override val session: Flow<StoredSession?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[key]?.let(cipher::decrypt)?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() } }

    override suspend fun current(): StoredSession? = session.first()

    override suspend fun save(session: StoredSession) {
        val blob = cipher.encrypt(json.encodeToString(StoredSession.serializer(), session))
        dataStore.edit { it[key] = blob }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SessionModule {
    @Binds abstract fun bindsSessionStore(impl: EncryptedSessionStore): SessionStore
    @Binds abstract fun bindsSessionCipher(impl: KeystoreSessionCipher): SessionCipher

    companion object {
        @Provides
        @Singleton
        @SessionPrefs
        fun providesSessionPrefs(@ApplicationContext context: Context): DataStore<Preferences> = context.sessionPreferences
    }
}
