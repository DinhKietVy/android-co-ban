package com.example.filemanagementapp.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed cryptography for Secure Vault:
 * - Non-exportable Master Key in Android Keystore System
 * - PBKDF2WithHmacSHA256 key derivation from user's Vault PIN
 * - AES-256-GCM authenticated encryption for files (.enc)
 * - SHA-256 checksum verification for data integrity
 */
object VaultCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "secure_vault_master_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    fun getOrCreateKeystoreMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = javax.crypto.KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_LENGTH_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun generateRandomSalt(): ByteArray = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }

    fun generateDataEncryptionKey(): SecretKey {
        val raw = ByteArray(32).also { secureRandom.nextBytes(it) }
        return SecretKeySpec(raw, "AES")
    }

    fun deriveKeyFromPin(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Wraps DEK first with PBKDF2(PIN), then with the hardware AndroidKeyStore Master Key.
     */
    fun wrapDataKey(dek: SecretKey, pin: String, salt: ByteArray): String {
        val pinKey = deriveKeyFromPin(pin, salt)
        val pinWrapped = encryptAesGcm(dek.encoded, pinKey)
        val masterKey = getOrCreateKeystoreMasterKey()
        val hwWrapped = encryptAesGcm(pinWrapped, masterKey)
        return Base64.encodeToString(hwWrapped, Base64.NO_WRAP)
    }

    fun unwrapDataKey(wrappedBase64: String, pin: String, salt: ByteArray): SecretKey {
        val hwWrapped = Base64.decode(wrappedBase64, Base64.NO_WRAP)
        val masterKey = getOrCreateKeystoreMasterKey()
        val pinWrapped = decryptAesGcm(hwWrapped, masterKey)
        val pinKey = deriveKeyFromPin(pin, salt)
        val rawDek = decryptAesGcm(pinWrapped, pinKey)
        return SecretKeySpec(rawDek, "AES")
    }

    fun encryptAesGcm(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
        }
    }

    fun decryptAesGcm(ivAndCiphertext: ByteArray, key: SecretKey): ByteArray {
        require(ivAndCiphertext.size > GCM_IV_LENGTH) { "Invalid encrypted payload" }
        val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH, ivAndCiphertext.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
