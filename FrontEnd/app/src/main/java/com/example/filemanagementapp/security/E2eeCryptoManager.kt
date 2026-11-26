package com.example.filemanagementapp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * End-to-End File Encryption (E2EE) using AES-256-GCM and SHA-256 Checksum.
 * Binary format of `.enc` payload:
 * [4 bytes magic "E2EE"] [32 bytes SHA-256 of plaintext] [12 bytes GCM IV] [ciphertext + 16-byte GCM tag]
 */
object E2eeCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val E2EE_KEY_ALIAS = "e2ee_file_master_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val SHA256_LENGTH = 32
    private val MAGIC = byteArrayOf('E'.code.toByte(), '2'.code.toByte(), 'E'.code.toByte(), 'E'.code.toByte())

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(E2EE_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = javax.crypto.KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            E2EE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encryptBytes(plaintext: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(plaintext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(MAGIC.size + SHA256_LENGTH + iv.size + ciphertext.size).apply {
            var offset = 0
            System.arraycopy(MAGIC, 0, this, offset, MAGIC.size)
            offset += MAGIC.size
            System.arraycopy(sha256, 0, this, offset, SHA256_LENGTH)
            offset += SHA256_LENGTH
            System.arraycopy(iv, 0, this, offset, iv.size)
            offset += iv.size
            System.arraycopy(ciphertext, 0, this, offset, ciphertext.size)
        }
    }

    fun isE2eePayload(bytes: ByteArray): Boolean {
        if (bytes.size <= MAGIC.size + SHA256_LENGTH + IV_LENGTH) return false
        return bytes[0] == MAGIC[0] &&
            bytes[1] == MAGIC[1] &&
            bytes[2] == MAGIC[2] &&
            bytes[3] == MAGIC[3]
    }

    fun decryptBytes(payload: ByteArray): ByteArray {
        if (!isE2eePayload(payload)) {
            throw SecurityException("Invalid E2EE .enc header")
        }
        var offset = MAGIC.size
        val expectedSha256 = payload.copyOfRange(offset, offset + SHA256_LENGTH)
        offset += SHA256_LENGTH
        val iv = payload.copyOfRange(offset, offset + IV_LENGTH)
        offset += IV_LENGTH
        val ciphertext = payload.copyOfRange(offset, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)

        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(plaintext)
        if (!MessageDigest.isEqual(expectedSha256, actualSha256)) {
            throw SecurityException("SHA-256 checksum mismatch! File may be tampered.")
        }
        return plaintext
    }

    fun encryptFile(sourceFile: File, destFile: File) {
        val plaintext = sourceFile.readBytes()
        destFile.writeBytes(encryptBytes(plaintext))
    }
}
