package com.jhacode.chitrini.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    private const val TAG = "EncryptionManager"
    private const val KEY_ALIAS = "chitrini_rsa_key"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // 🔥 CACHE KEYS FOR SPEED
    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    fun getOrGenerateKeyPair(): KeyPair {
        if (cachedKeyPair != null) return cachedKeyPair!!

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Log.d(TAG, "Generating new RSA key pair...")
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()
            )
            cachedKeyPair = kpg.generateKeyPair()
        } else {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            cachedKeyPair = KeyPair(publicKey, privateKey)
        }
        
        return cachedKeyPair!!
    }

    fun getPublicKeyBase64(): String {
        val pair = getOrGenerateKeyPair()
        return Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    private fun getPublicKeyFromBase64(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }

    // ================= ENCRYPTION =================

    fun encrypt(message: String, targetPublicKeyBase64: String?): String? {
        if (targetPublicKeyBase64 == null) return null
        try {
            // 1. Generate random AES key
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val aesKey = keyGen.generateKey()
            
            // 2. Encrypt message with AES
            val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val iv = aesCipher.iv
            val encryptedMessageBytes = aesCipher.doFinal(message.toByteArray())

            // 3. Encrypt AES key with Target RSA Public Key
            val targetPublicKey = getPublicKeyFromBase64(targetPublicKeyBase64)
            val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
            rsaCipher.init(Cipher.ENCRYPT_MODE, targetPublicKey)
            val encryptedAESKey = rsaCipher.doFinal(aesKey.encoded)

            // 4. Bundle: EncryptedKey | IV | EncryptedMessage
            return Base64.encodeToString(encryptedAESKey, Base64.NO_WRAP) + "|" +
                   Base64.encodeToString(iv, Base64.NO_WRAP) + "|" +
                   Base64.encodeToString(encryptedMessageBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            return null
        }
    }

    // ================= DECRYPTION =================

    fun decrypt(bundle: String?): String? {
        if (bundle == null || !bundle.contains("|")) return null
        try {
            val parts = bundle.split("|")
            if (parts.size != 3) return null

            val encryptedAESKey = Base64.decode(parts[0], Base64.DEFAULT)
            val iv = Base64.decode(parts[1], Base64.DEFAULT)
            val encryptedMessageBytes = Base64.decode(parts[2], Base64.DEFAULT)

            // 1. Decrypt AES key with My RSA Private Key
            val pair = getOrGenerateKeyPair()
            val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
            rsaCipher.init(Cipher.DECRYPT_MODE, pair.private)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAESKey)
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            // 2. Decrypt message with AES
            val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val decryptedBytes = aesCipher.doFinal(encryptedMessageBytes)

            return String(decryptedBytes)
        } catch (e: Exception) {
            // Log.e(TAG, "Decryption failed (might be plain text)")
            return null
        }
    }

    fun deleteKeys() {
        try {
            cachedKeyPair = null
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "✅ RSA Keys deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete keys", e)
        }
    }
}
