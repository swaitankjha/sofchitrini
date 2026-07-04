package com.jhacode.chitrini.crypto

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MediaCryptoManager {
    private const val TAG = "MediaCryptoManager"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    data class EncryptedBundle(
        val encryptedBytes: ByteArray,
        val keyBase64: String,
        val ivBase64: String
    )

    fun encrypt(data: ByteArray): EncryptedBundle? {
        return try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE)
            val secretKey = keyGen.generateKey()

            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val encryptedData = cipher.doFinal(data)

            EncryptedBundle(
                encryptedBytes = encryptedData,
                keyBase64 = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Media encryption failed", e)
            null
        }
    }

    fun decrypt(data: ByteArray, keyBase64: String, ivBase64: String): ByteArray? {
        return try {
            val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Media decryption failed", e)
            null
        }
    }
}
