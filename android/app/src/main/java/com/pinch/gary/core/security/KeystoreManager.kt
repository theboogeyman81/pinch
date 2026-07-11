package com.pinch.gary.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "com.pinch.gary.keystore.master_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val PREFS_NAME = "gary_secure_prefs"

/**
 * Wraps Android Keystore for the two secrets this app must never send to the
 * cloud: the Home Assistant long-lived token (smarthome/, not yet built) and
 * the JWT refresh token (garyclient/, not yet built). The AES key itself
 * never leaves the secure hardware — only ciphertext is persisted in
 * SharedPreferences.
 *
 * Built now (ahead of the features that need it) because both future
 * consumers need identical semantics and it's cheap to get right once.
 */
@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun putSecret(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(key + "_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(key + "_value", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun getSecret(key: String): String? {
        val ivB64 = prefs.getString(key + "_iv", null) ?: return null
        val valueB64 = prefs.getString(key + "_value", null) ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
            )
        }
        val plaintext = cipher.doFinal(Base64.decode(valueB64, Base64.NO_WRAP))
        return String(plaintext, Charsets.UTF_8)
    }

    fun clearSecret(key: String) {
        prefs.edit().remove(key + "_iv").remove(key + "_value").apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object Keys {
        const val HA_LONG_LIVED_TOKEN = "ha_long_lived_token"
        const val JWT_REFRESH_TOKEN = "jwt_refresh_token"
    }
}
