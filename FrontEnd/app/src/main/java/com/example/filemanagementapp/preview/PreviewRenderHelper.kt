package com.example.filemanagementapp.preview

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.explorer.ExplorerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PreviewRenderHelper(
    private val activity: AppCompatActivity,
    private val item: ExplorerItem?,
    private val previewUrl: String?,
    private val analyzedImagePath: String?,
    private val fileName: String,
    private var ocrText: String,
    private var aiTags: List<String>
) {
    private val titleText: TextView = activity.findViewById(R.id.titleText)
    private val previewInfoNameValue: TextView = activity.findViewById(R.id.previewInfoNameValue)
    private val previewInfoTypeValue: TextView = activity.findViewById(R.id.previewInfoTypeValue)
    private val previewInfoSizeValue: TextView = activity.findViewById(R.id.previewInfoSizeValue)
    private val previewInfoUploadedValue: TextView = activity.findViewById(R.id.previewInfoUploadedValue)
    private val previewInfoModifiedValue: TextView = activity.findViewById(R.id.previewInfoModifiedValue)
    
    private val previewImage: ImageView = activity.findViewById(R.id.previewImage)
    val previewVideo: VideoView = activity.findViewById(R.id.previewVideo)
    private val previewTextScroll: ScrollView = activity.findViewById(R.id.previewTextScroll)
    private val previewText: TextView = activity.findViewById(R.id.previewText)
    private val previewPdfRecycler: androidx.recyclerview.widget.RecyclerView = activity.findViewById(R.id.previewPdfRecycler)
    val previewDocxWebView: android.webkit.WebView = activity.findViewById(R.id.previewDocxWebView)
    private val previewUnsupported: View = activity.findViewById(R.id.previewUnsupported)
    val previewLoadingIndicator: ProgressBar = activity.findViewById(R.id.previewLoadingIndicator)
    
    private val openExternallyButton: com.google.android.material.button.MaterialButton = activity.findViewById(R.id.openExternallyButton)
    private val showProcessedImageSwitch: androidx.appcompat.widget.SwitchCompat = activity.findViewById(R.id.showProcessedImageSwitch)
    private val objectTagsTitle: TextView = activity.findViewById(R.id.objectTagsTitle)
    private val objectTagsContainerLayout: LinearLayout = activity.findViewById(R.id.objectTagsContainerLayout)
    private val objectTagsChipGroup: com.google.android.material.chip.ChipGroup = activity.findViewById(R.id.objectTagsChipGroup)
    private val addTagButton: com.google.android.material.button.MaterialButton = activity.findViewById(R.id.addTagButton)
    private val ocrTextView: TextView = activity.findViewById(R.id.ocrTextView)
    private val editOcrButton: com.google.android.material.button.MaterialButton = activity.findViewById(R.id.editOcrButton)

    var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
    var pdfFileDescriptor: android.os.ParcelFileDescriptor? = null

    fun setup(onOpenExternally: (View) -> Unit, onAiDataChanged: (String, List<String>) -> Unit = { _, _ -> }) {
        titleText.text = fileName.ifBlank { activity.getString(R.string.preview_file_name) }
        previewInfoNameValue.text = fileName.ifBlank { activity.getString(R.string.preview_file_name) }
        
        val fileExtension = fileName.substringAfterLast('.', "").uppercase()
        val type = if (fileExtension.isNotBlank()) {
            when (fileExtension) {
                "JPG", "JPEG", "PNG", "GIF", "WEBP", "BMP" -> "$fileExtension Image"
                "MP4", "MKV", "WEBM", "AVI" -> "$fileExtension Video"
                "MP3", "WAV", "OGG", "M4A" -> "$fileExtension Audio"
                "PDF", "DOC", "DOCX", "TXT", "MD", "CSV" -> "$fileExtension Document"
                "ZIP", "RAR", "7Z", "TAR", "GZ" -> "$fileExtension Archive"
                else -> "$fileExtension File"
            }
        } else {
            "Unknown File"
        }
        previewInfoTypeValue.text = type
        
        previewInfoSizeValue.text = item?.size ?: activity.getString(R.string.preview_file_size)
        val modified = item?.modified ?: activity.getString(R.string.preview_modified_date)
        previewInfoUploadedValue.text = modified
        previewInfoModifiedValue.text = modified

        val originalPreviewSource = previewUrl?.let { Uri.parse(it) }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val isVideo = extension in listOf("mp4", "mkv", "webm", "avi")
        val isAudio = extension in listOf("mp3", "wav", "ogg", "m4a")
        val isText = extension in listOf("txt", "json", "xml", "md", "csv", "kt", "java", "py", "html", "css", "js", "sh")
        val isPdf = extension == "pdf"
        val isDocx = extension in listOf("doc", "docx")

        previewImage.visibility = View.GONE
        previewVideo.visibility = View.GONE
        previewTextScroll.visibility = View.GONE
        previewPdfRecycler.visibility = View.GONE
        previewDocxWebView.visibility = View.GONE
        previewUnsupported.visibility = View.GONE
        previewLoadingIndicator.visibility = View.GONE

        showProcessedImageSwitch.isEnabled = (isImage || (extension.isBlank() && originalPreviewSource != null)) && !analyzedImagePath.isNullOrEmpty()

        if (isImage || (extension.isBlank() && originalPreviewSource != null)) {
            previewImage.visibility = View.VISIBLE
            
            fun loadImage(sourceUri: Uri?) {
                previewLoadingIndicator.visibility = View.VISIBLE
                if (sourceUri == null) {
                    previewImage.setImageResource(R.drawable.explorer_file_preview_placeholder)
                    previewLoadingIndicator.visibility = View.GONE
                } else {
                    previewImage.setImageDrawable(null)
                    previewImage.load(sourceUri) {
                        size(coil.size.Size.ORIGINAL)
                        setHeader("ngrok-skip-browser-warning", "69420")
                        error(R.drawable.explorer_file_preview_placeholder)
                        listener(
                            onSuccess = { _, _ -> previewLoadingIndicator.visibility = View.GONE },
                            onError = { _, _ -> previewLoadingIndicator.visibility = View.GONE }
                        )
                    }
                }
            }
            
            loadImage(originalPreviewSource)
            
            showProcessedImageSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !analyzedImagePath.isNullOrEmpty()) {
                    loadImage(Uri.fromFile(java.io.File(analyzedImagePath)))
                } else {
                    loadImage(originalPreviewSource)
                }
            }
        } else if (isVideo || isAudio) {
            previewVideo.visibility = View.VISIBLE
            previewLoadingIndicator.visibility = View.VISIBLE
            val mediaController = android.widget.MediaController(activity)
            mediaController.setAnchorView(previewVideo)
            previewVideo.setMediaController(mediaController)
            
            if (previewUrl == null) {
                previewLoadingIndicator.visibility = View.GONE
                return
            }

            activity.lifecycleScope.launch {
                try {
                    val fileBytes = withContext(Dispatchers.IO) {
                        val request = okhttp3.Request.Builder()
                            .url(previewUrl)
                            .addHeader("ngrok-skip-browser-warning", "69420")
                            .build()
                        val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        response.body?.bytes() ?: throw Exception("Empty response")
                    }
                    
                    val tempFile = File(activity.cacheDir, "temp_preview_media.${if(isVideo) "mp4" else "mp3"}")
                    withContext(Dispatchers.IO) {
                        tempFile.writeBytes(fileBytes)
                    }

                    withContext(Dispatchers.Main) {
                        previewVideo.setVideoURI(Uri.fromFile(tempFile))
                        previewVideo.setOnPreparedListener { 
                            it.start() 
                            previewLoadingIndicator.visibility = View.GONE
                        }
                        previewVideo.setOnErrorListener { _, _, _ ->
                            previewLoadingIndicator.visibility = View.GONE
                            true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(activity, "Error loading media: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        previewLoadingIndicator.visibility = View.GONE
                        previewUnsupported.visibility = View.VISIBLE
                        previewVideo.visibility = View.GONE
                    }
                }
            }
        } else if (isText) {
            previewTextScroll.visibility = View.VISIBLE
            previewLoadingIndicator.visibility = View.VISIBLE
            previewText.text = activity.getString(R.string.preview_loading_text)
            activity.lifecycleScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        if (previewUrl == null) throw Exception("Preview URL is missing")
                        val request = okhttp3.Request.Builder()
                            .url(previewUrl)
                            .addHeader("ngrok-skip-browser-warning", "69420")
                            .build()
                        val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                        response.body?.string() ?: throw Exception("Empty response body")
                    }
                    previewText.text = content
                } catch (e: Exception) {
                    previewText.text = activity.getString(R.string.preview_error_loading_text) + "\n" + e.message
                } finally {
                    previewLoadingIndicator.visibility = View.GONE
                }
            }
        } else if (isPdf || isDocx) {
            val isPdfType = isPdf
            if (isPdfType) {
                previewPdfRecycler.visibility = View.VISIBLE
                previewPdfRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
            } else {
                previewDocxWebView.visibility = View.VISIBLE
                previewDocxWebView.settings.javaScriptEnabled = true
                previewDocxWebView.settings.allowFileAccess = true
                previewDocxWebView.settings.allowContentAccess = true
                previewDocxWebView.settings.useWideViewPort = true
                previewDocxWebView.settings.loadWithOverviewMode = true
                previewDocxWebView.settings.builtInZoomControls = true
                previewDocxWebView.settings.displayZoomControls = false
                previewDocxWebView.settings.setSupportZoom(true)
                
                previewDocxWebView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        previewLoadingIndicator.visibility = View.GONE
                    }
                }
            }

            if (previewUrl == null) {
                android.widget.Toast.makeText(activity, "Preview URL is missing", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            previewLoadingIndicator.visibility = View.VISIBLE
            activity.lifecycleScope.launch {
                try {
                    val fileBytes = withContext(Dispatchers.IO) {
                        val request = okhttp3.Request.Builder()
                            .url(previewUrl)
                            .addHeader("ngrok-skip-browser-warning", "69420")
                            .build()
                        val response = ExplorerNetworkModule.okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
                        response.body?.bytes() ?: throw Exception("Empty response body")
                    }
                    
                    val tempFile = File(activity.cacheDir, "temp_preview.${if(isPdfType) "pdf" else "docx"}")
                    withContext(Dispatchers.IO) {
                        tempFile.writeBytes(fileBytes)
                    }

                    if (isPdfType) {
                        pdfFileDescriptor = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = android.graphics.pdf.PdfRenderer(pdfFileDescriptor!!)
                        withContext(Dispatchers.Main) {
                            val adapter = PdfRendererAdapter(pdfRenderer!!)
                            previewPdfRecycler.adapter = adapter
                            previewLoadingIndicator.visibility = View.GONE
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            val base64Data = withContext(Dispatchers.IO) {
                                val bytes = tempFile.readBytes()
                                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            }
                            
                            class DocxInterface(private val data: String) {
                                @android.webkit.JavascriptInterface
                                fun getBase64Data(): String = data
                            }
                            
                            previewDocxWebView.addJavascriptInterface(DocxInterface(base64Data), "Android")
                            previewDocxWebView.loadUrl("file:///android_asset/docx_viewer/preview.html")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(activity, "Error loading document: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        previewUnsupported.visibility = View.VISIBLE
                        previewPdfRecycler.visibility = View.GONE
                        previewDocxWebView.visibility = View.GONE
                        previewLoadingIndicator.visibility = View.GONE
                    }
                }
            }
        } else {
            previewUnsupported.visibility = View.VISIBLE
            openExternallyButton.setOnClickListener(onOpenExternally)
        }

        ocrTextView.text = ocrText.ifBlank {
            activity.getString(R.string.preview_ai_empty)
        }
        
        editOcrButton.setOnClickListener {
            val input = android.widget.EditText(activity)
            input.setText(ocrText)
            
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle("Edit Extracted Text")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    ocrText = input.text.toString()
                    ocrTextView.text = ocrText.ifBlank { activity.getString(R.string.preview_ai_empty) }
                    onAiDataChanged(ocrText, aiTags)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun renderTags() {
            objectTagsChipGroup.removeAllViews()
            objectTagsTitle.visibility = View.VISIBLE
            objectTagsContainerLayout.visibility = View.VISIBLE
            aiTags.forEach { tag ->
                val chip = com.google.android.material.chip.Chip(activity)
                chip.text = tag
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener {
                    val mutableTags = aiTags.toMutableList()
                    mutableTags.remove(tag)
                    aiTags = mutableTags
                    renderTags()
                    onAiDataChanged(ocrText, aiTags)
                }
                objectTagsChipGroup.addView(chip)
            }
        }

        renderTags()

        addTagButton.setOnClickListener {
            val input = android.widget.EditText(activity)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle("Add Tag")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val newTag = input.text.toString().trim()
                    if (newTag.isNotBlank() && !aiTags.contains(newTag)) {
                        val mutableTags = aiTags.toMutableList()
                        mutableTags.add(newTag)
                        aiTags = mutableTags
                        renderTags()
                        onAiDataChanged(ocrText, aiTags)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun cleanUp() {
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            pdfFileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            previewDocxWebView.clearHistory()
            previewDocxWebView.clearCache(true)
            previewDocxWebView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            previewVideo.stopPlayback()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val tempPdf = java.io.File(activity.cacheDir, "temp_preview.pdf")
            if (tempPdf.exists()) tempPdf.delete()
            val tempDocx = java.io.File(activity.cacheDir, "temp_preview.docx")
            if (tempDocx.exists()) tempDocx.delete()
            val tempMediaVideo = java.io.File(activity.cacheDir, "temp_preview_media.mp4")
            if (tempMediaVideo.exists()) tempMediaVideo.delete()
            val tempMediaAudio = java.io.File(activity.cacheDir, "temp_preview_media.mp3")
            if (tempMediaAudio.exists()) tempMediaAudio.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
