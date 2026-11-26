package com.example.filemanagementapp.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

data class VaultItemMetadata(
    val id: String,
    val originalName: String,
    val mimeType: String?,
    val originalSize: Long,
    val sha256Checksum: String,
    val encryptedFileName: String,
    val createdAt: Long
)

class VaultRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val vaultDir: File
        get() = File(context.filesDir, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    private val previewCacheDir: File
        get() = File(context.cacheDir, PREVIEW_DIR_NAME).apply { if (!exists()) mkdirs() }
    private val indexFile: File
        get() = File(vaultDir, INDEX_FILE_NAME)

    fun isVaultInitialized(): Boolean {
        return !prefs.getString(KEY_WRAPPED_DEK, null).isNullOrBlank() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
    }

    suspend fun setupVault(pin: String): Result<SecretKey> = withContext(Dispatchers.IO) {
        runCatching {
            require(pin.length >= 4) { "PIN must be at least 4 digits" }
            val salt = VaultCryptoManager.generateRandomSalt()
            val dek = VaultCryptoManager.generateDataEncryptionKey()
            val wrappedDek = VaultCryptoManager.wrapDataKey(dek, pin, salt)

            prefs.edit()
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK, wrappedDek)
                .apply()

            saveIndex(emptyList())
            dek
        }
    }

    suspend fun unlockVault(pin: String): Result<SecretKey> = withContext(Dispatchers.IO) {
        runCatching {
            val saltB64 = prefs.getString(KEY_PIN_SALT, null)
                ?: error("Vault is not initialized")
            val wrappedDek = prefs.getString(KEY_WRAPPED_DEK, null)
                ?: error("Vault is not initialized")
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            VaultCryptoManager.unwrapDataKey(wrappedDek, pin, salt)
        }
    }

    suspend fun listItems(): Result<List<VaultItemMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            loadIndex().sortedByDescending { it.createdAt }
        }
    }

    suspend fun addFileFromUri(uri: Uri, dek: SecretKey): Result<VaultItemMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val originalName = resolveFileName(uri) ?: "vault_file_${System.currentTimeMillis()}"
                val mimeType = resolver.getType(uri)
                val plaintext = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot read selected file")

                encryptAndStore(originalName, mimeType, plaintext, dek)
            }
        }

    suspend fun addLocalFile(
        file: File,
        originalName: String,
        mimeType: String?,
        dek: SecretKey
    ): Result<VaultItemMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val plaintext = file.readBytes()
            encryptAndStore(originalName, mimeType, plaintext, dek)
        }
    }

    suspend fun decryptToTempFile(item: VaultItemMetadata, dek: SecretKey): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encFile = File(vaultDir, item.encryptedFileName)
                if (!encFile.exists()) error("Encrypted file not found")

                val encryptedBytes = encFile.readBytes()
                val plaintext = VaultCryptoManager.decryptAesGcm(encryptedBytes, dek)
                val actualSha256 = VaultCryptoManager.sha256Hex(plaintext)
                if (!actualSha256.equals(item.sha256Checksum, ignoreCase = true)) {
                    throw SecurityException("SHA-256 checksum mismatch! File may be corrupted or tampered.")
                }

                val safeName = item.originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val outFile = File(previewCacheDir, "${item.id}_$safeName")
                outFile.writeBytes(plaintext)
                outFile
            }
        }

    suspend fun deleteItem(item: VaultItemMetadata): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(vaultDir, item.encryptedFileName).delete()
            val updated = loadIndex().filterNot { it.id == item.id }
            saveIndex(updated)
        }
    }

    fun clearPreviewCache() {
        previewCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun encryptAndStore(
        originalName: String,
        mimeType: String?,
        plaintext: ByteArray,
        dek: SecretKey
    ): VaultItemMetadata {
        val id = UUID.randomUUID().toString()
        val checksum = VaultCryptoManager.sha256Hex(plaintext)
        val encryptedBytes = VaultCryptoManager.encryptAesGcm(plaintext, dek)
        val encryptedFileName = "$id.enc"

        File(vaultDir, encryptedFileName).writeBytes(encryptedBytes)

        val metadata = VaultItemMetadata(
            id = id,
            originalName = originalName,
            mimeType = mimeType,
            originalSize = plaintext.size.toLong(),
            sha256Checksum = checksum,
            encryptedFileName = encryptedFileName,
            createdAt = System.currentTimeMillis()
        )
        val updated = loadIndex() + metadata
        saveIndex(updated)
        return metadata
    }

    private fun loadIndex(): List<VaultItemMetadata> {
        if (!indexFile.exists()) return emptyList()
        val masterKey = VaultCryptoManager.getOrCreateKeystoreMasterKey()
        val decrypted = VaultCryptoManager.decryptAesGcm(indexFile.readBytes(), masterKey)
        val json = String(decrypted, Charsets.UTF_8)
        val type = object : TypeToken<List<VaultItemMetadata>>() {}.type
        return gson.fromJson<List<VaultItemMetadata>>(json, type) ?: emptyList()
    }

    private fun saveIndex(items: List<VaultItemMetadata>) {
        val masterKey = VaultCryptoManager.getOrCreateKeystoreMasterKey()
        val jsonBytes = gson.toJson(items).toByteArray(Charsets.UTF_8)
        val encrypted = VaultCryptoManager.encryptAesGcm(jsonBytes, masterKey)
        indexFile.writeBytes(encrypted)
    }

    private fun resolveFileName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        private const val PREFS_NAME = "secure_vault_prefs"
        private const val KEY_PIN_SALT = "vault_pin_salt"
        private const val KEY_WRAPPED_DEK = "vault_wrapped_dek"
        private const val VAULT_DIR_NAME = "secure_vault"
        private const val PREVIEW_DIR_NAME = "vault_preview"
        private const val INDEX_FILE_NAME = "vault_index.enc"
    }
}
