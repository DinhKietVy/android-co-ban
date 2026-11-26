package com.example.filemanagementapp.vault

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanagementapp.R
import com.example.filemanagementapp.explorer.ExplorerItem
import com.example.filemanagementapp.preview.FilePreviewActivity
import com.example.filemanagementapp.security.DeviceSecurityChecker
import com.example.filemanagementapp.security.UploadSecurityValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

class SecureVaultActivity : AppCompatActivity() {

    private enum class VaultSort { DATE_DESC, NAME_ASC, SIZE_DESC }

    private val vaultRepository by lazy { VaultRepository(applicationContext) }
    private var unlockedDek: SecretKey? = null
    private var allItems: List<VaultItemMetadata> = emptyList()
    private var searchQuery: String = ""
    private var currentSort: VaultSort = VaultSort.DATE_DESC

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSummary: TextView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: VaultAdapter

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val dek = unlockedDek ?: return@registerForActivityResult
        uri?.let {
            val displayName = UploadSecurityValidator.resolveDisplayName(this, it)
            if (UploadSecurityValidator.isBlockedFileName(displayName)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.upload_blocked_executable_title)
                    .setMessage(getString(R.string.upload_blocked_executable_message, displayName ?: ""))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@let
            }

            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                vaultRepository.addFileFromUri(it, dek)
                    .onSuccess { added ->
                        Toast.makeText(
                            this@SecureVaultActivity,
                            getString(R.string.vault_file_encrypted, added.originalName),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadVaultItems()
                    }
                    .onFailure { error ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this@SecureVaultActivity,
                            error.message ?: getString(R.string.vault_error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_secure_vault)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        recyclerView = findViewById(R.id.rvVaultItems)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSummary = findViewById(R.id.tvVaultSummary)
        etSearch = findViewById(R.id.etVaultSearch)
        progressBar = findViewById(R.id.progressBar)
        fabAdd = findViewById(R.id.fabAddVaultFile)

        findViewById<ImageButton>(R.id.btnVaultSearch).setOnClickListener {
            if (etSearch.visibility == View.VISIBLE) {
                etSearch.setText("")
                etSearch.visibility = View.GONE
            } else {
                etSearch.visibility = View.VISIBLE
                etSearch.requestFocus()
            }
        }
        etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString().orEmpty().trim()
            renderFilteredItems()
        }
        findViewById<ImageButton>(R.id.btnVaultSort).setOnClickListener { showSortDialog() }

        adapter = VaultAdapter(
            onItemClick = ::openVaultItem,
            onDeleteClick = ::confirmDeleteItem
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            if (unlockedDek != null) {
                pickFileLauncher.launch(arrayOf("*/*"))
            }
        }

        if (!DeviceSecurityChecker.isEnvironmentTrustedForVault(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vault_blocked_title)
                .setMessage(R.string.vault_blocked_rooted_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .show()
            return
        }

        if (vaultRepository.isVaultInitialized()) {
            showUnlockDialog()
        } else {
            showSetupPinDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockedDek = null
        vaultRepository.clearPreviewCache()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.vault_sort_date),
            getString(R.string.vault_sort_name),
            getString(R.string.vault_sort_size)
        )
        val checked = when (currentSort) {
            VaultSort.DATE_DESC -> 0
            VaultSort.NAME_ASC -> 1
            VaultSort.SIZE_DESC -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_sort)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                currentSort = when (which) {
                    1 -> VaultSort.NAME_ASC
                    2 -> VaultSort.SIZE_DESC
                    else -> VaultSort.DATE_DESC
                }
                renderFilteredItems()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSetupPinDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vault_pin, null)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvPinDescription)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)
        val confirmLayout = dialogView.findViewById<TextInputLayout>(R.id.confirmPinInputLayout)
        val confirmEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPinEditText)

        tvDesc.setText(R.string.vault_setup_pin_desc)
        confirmLayout.visibility = View.VISIBLE

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_setup_pin_title)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.vault_setup_confirm) { _, _ ->
                val pin = pinEditText.text?.toString().orEmpty()
                val confirm = confirmEditText.text?.toString().orEmpty()
                if (pin.length < 4) {
                    Toast.makeText(this, R.string.vault_pin_too_short, Toast.LENGTH_SHORT).show()
                    showSetupPinDialog()
                    return@setPositiveButton
                }
                if (pin != confirm) {
                    Toast.makeText(this, R.string.vault_pin_mismatch, Toast.LENGTH_SHORT).show()
                    showSetupPinDialog()
                    return@setPositiveButton
                }
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    vaultRepository.setupVault(pin)
                        .onSuccess { dek ->
                            unlockedDek = dek
                            handlePendingImportAndLoad()
                        }
                        .onFailure { error ->
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@SecureVaultActivity,
                                error.message ?: getString(R.string.vault_error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun showUnlockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vault_pin, null)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvPinDescription)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)
        tvDesc.setText(R.string.vault_unlock_pin_desc)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_unlock_pin_title)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.vault_unlock_button) { _, _ ->
                val pin = pinEditText.text?.toString().orEmpty()
                if (pin.length < 4) {
                    Toast.makeText(this, R.string.vault_pin_too_short, Toast.LENGTH_SHORT).show()
                    showUnlockDialog()
                    return@setPositiveButton
                }
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    vaultRepository.unlockVault(pin)
                        .onSuccess { dek ->
                            unlockedDek = dek
                            handlePendingImportAndLoad()
                        }
                        .onFailure {
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@SecureVaultActivity,
                                R.string.vault_pin_incorrect,
                                Toast.LENGTH_SHORT
                            ).show()
                            showUnlockDialog()
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun handlePendingImportAndLoad() {
        val dek = unlockedDek ?: return
        val localPath = intent.getStringExtra(EXTRA_IMPORT_LOCAL_PATH)
        val fileName = intent.getStringExtra(EXTRA_IMPORT_FILE_NAME)
        val mimeType = intent.getStringExtra(EXTRA_IMPORT_MIME_TYPE)

        if (localPath.isNullOrBlank() || fileName.isNullOrBlank()) {
            loadVaultItems()
            return
        }

        intent.removeExtra(EXTRA_IMPORT_LOCAL_PATH)
        val sourceFile = File(localPath)
        if (!sourceFile.exists()) {
            loadVaultItems()
            return
        }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            vaultRepository.addLocalFile(sourceFile, fileName, mimeType, dek)
                .onSuccess { added ->
                    sourceFile.delete()
                    Toast.makeText(
                        this@SecureVaultActivity,
                        getString(R.string.vault_file_encrypted, added.originalName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@SecureVaultActivity,
                        error.message ?: getString(R.string.vault_error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            loadVaultItems()
        }
    }

    private fun loadVaultItems() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            vaultRepository.listItems()
                .onSuccess { items ->
                    progressBar.visibility = View.GONE
                    allItems = items
                    val totalSize = items.sumOf { it.originalSize }
                    tvSummary.text = getString(
                        R.string.vault_summary_format,
                        items.size,
                        Formatter.formatShortFileSize(this@SecureVaultActivity, totalSize)
                    )
                    renderFilteredItems()
                }
                .onFailure { error ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@SecureVaultActivity,
                        error.message ?: getString(R.string.vault_error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun renderFilteredItems() {
        val filtered = if (searchQuery.isBlank()) {
            allItems
        } else {
            allItems.filter { it.originalName.contains(searchQuery, ignoreCase = true) }
        }
        val sorted = when (currentSort) {
            VaultSort.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            VaultSort.NAME_ASC -> filtered.sortedBy { it.originalName.lowercase() }
            VaultSort.SIZE_DESC -> filtered.sortedByDescending { it.originalSize }
        }
        adapter.submitList(sorted)
        tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openVaultItem(item: VaultItemMetadata) {
        val dek = unlockedDek ?: return
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            vaultRepository.decryptToTempFile(item, dek)
                .onSuccess { tempFile ->
                    progressBar.visibility = View.GONE
                    val contentUri = FileProvider.getUriForFile(
                        this@SecureVaultActivity,
                        "${packageName}.fileprovider",
                        tempFile
                    )
                    val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(item.createdAt))
                    val explorerItem = ExplorerItem(
                        id = item.id,
                        name = item.originalName,
                        path = item.encryptedFileName,
                        type = ExplorerItem.Type.FILE,
                        modified = formattedDate,
                        modifiedEpochMillis = item.createdAt,
                        size = Formatter.formatShortFileSize(this@SecureVaultActivity, item.originalSize),
                        sizeBytes = item.originalSize,
                        previewUrl = contentUri.toString(),
                        isImagePreviewable = item.mimeType?.startsWith("image/") == true,
                        aiAnalyzed = false,
                        tags = listOf("AES-256-GCM")
                    )
                    startActivity(
                        FilePreviewActivity.newIntent(
                            context = this@SecureVaultActivity,
                            item = explorerItem,
                            username = "vault",
                            analyzedImagePath = null,
                            ocrText = "SHA-256: ${item.sha256Checksum}",
                            aiTags = listOf("AES-256-GCM"),
                            showAiPanel = false
                        )
                    )
                }
                .onFailure { error ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@SecureVaultActivity,
                        error.message ?: getString(R.string.vault_error_generic),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun confirmDeleteItem(item: VaultItemMetadata) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_delete_title)
            .setMessage(getString(R.string.vault_delete_message, item.originalName))
            .setPositiveButton(R.string.trusted_device_remove) { _, _ ->
                lifecycleScope.launch {
                    vaultRepository.deleteItem(item)
                        .onSuccess { loadVaultItems() }
                        .onFailure { error ->
                            Toast.makeText(
                                this@SecureVaultActivity,
                                error.message ?: getString(R.string.vault_error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_IMPORT_LOCAL_PATH = "extra_import_local_path"
        const val EXTRA_IMPORT_FILE_NAME = "extra_import_file_name"
        const val EXTRA_IMPORT_MIME_TYPE = "extra_import_mime_type"
    }
}

private class VaultAdapter(
    private val onItemClick: (VaultItemMetadata) -> Unit,
    private val onDeleteClick: (VaultItemMetadata) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    private val items = mutableListOf<VaultItemMetadata>()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun submitList(newItems: List<VaultItemMetadata>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vault_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.tvName.text = item.originalName
        val sizeText = Formatter.formatShortFileSize(context, item.originalSize)
        val dateText = dateFormatter.format(Date(item.createdAt))
        holder.tvMeta.text = "$sizeText • $dateText • AES-256-GCM"
        holder.tvChecksum.text = "SHA-256: ${item.sha256Checksum.take(16)}…"
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVaultFileName)
        val tvMeta: TextView = view.findViewById(R.id.tvVaultFileMeta)
        val tvChecksum: TextView = view.findViewById(R.id.tvVaultChecksum)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDeleteVaultItem)
    }
}
