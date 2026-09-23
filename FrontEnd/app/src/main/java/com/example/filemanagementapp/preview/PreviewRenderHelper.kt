package com.example.filemanagementapp.preview

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.example.filemanagementapp.R
import com.example.filemanagementapp.data.explorer.network.ExplorerNetworkModule
import com.example.filemanagementapp.explorer.ExplorerItem
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
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
    val previewVideo: androidx.media3.ui.PlayerView = activity.findViewById(R.id.previewVideo)
    private val previewAudioContainer: LinearLayout = activity.findViewById(R.id.previewAudioContainer)
    private val audioArt: ImageView = activity.findViewById(R.id.audioArt)
    private var audioArtAnimator: android.animation.ObjectAnimator? = null
    
    // Rich Editor specific fields
    private var isRichEditing = false
    private val previewRichTextContainer: android.widget.LinearLayout = activity.findViewById(R.id.previewRichTextContainer)
    private val richEditorToolbar: android.widget.HorizontalScrollView = activity.findViewById(R.id.richEditorToolbar)
    private val richEditor: jp.wasabeef.richeditor.RichEditor = activity.findViewById(R.id.richEditor)
    var exoPlayer: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val editFab: com.google.android.material.floatingactionbutton.FloatingActionButton = activity.findViewById(R.id.editFab)
    private val previewPdfRecycler: androidx.recyclerview.widget.RecyclerView = activity.findViewById(R.id.previewPdfRecycler)
    val previewDocxWebView: android.webkit.WebView = activity.findViewById(R.id.previewDocxWebView)
    private val previewUnsupported: View = activity.findViewById(R.id.previewUnsupported)
    private val rotateVideoFab: com.google.android.material.floatingactionbutton.FloatingActionButton = activity.findViewById(R.id.rotateVideoFab)
    private val extractButton: com.google.android.material.button.MaterialButton = activity.findViewById(R.id.extractButton)
    private val previewConvertPdfButton: android.widget.Button = activity.findViewById(R.id.previewConvertPdfButton)
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

    fun setup(
        onOpenExternally: (View) -> Unit,
        onExtract: () -> Unit,
        onAiDataChanged: (String, List<String>) -> Unit = { _, _ -> },
        onEditImage: (String) -> Unit = {},
        onSaveTextContent: (String) -> Unit = {},
        onConvertPdfRequested: () -> Unit = {}
    ) {
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
        val isArchive = extension in listOf("zip", "rar", "7z", "tar", "gz")

        previewImage.visibility = View.GONE
        previewVideo.visibility = View.GONE
        previewAudioContainer.visibility = View.GONE
        previewRichTextContainer.visibility = View.GONE
        previewPdfRecycler.visibility = View.GONE
        previewDocxWebView.visibility = View.GONE
        previewUnsupported.visibility = View.GONE
        previewLoadingIndicator.visibility = View.GONE
        rotateVideoFab.visibility = View.GONE

        showProcessedImageSwitch.isEnabled = (isImage || (extension.isBlank() && originalPreviewSource != null)) && !analyzedImagePath.isNullOrEmpty()

        if (isImage || (extension.isBlank() && originalPreviewSource != null)) {
            previewImage.visibility = View.VISIBLE
            editFab.visibility = View.VISIBLE
            editFab.setOnClickListener { anchor ->
                val popup = android.widget.PopupMenu(activity, anchor)
                popup.menu.add(0, 1, 0, "Cắt & Xoay")
                popup.menu.add(0, 2, 1, "Vẽ & Ghi chú")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onEditImage("crop")
                        2 -> onEditImage("draw")
                    }
                    true
                }
                popup.show()
            }
            
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
            
            if (isAudio) {
                previewAudioContainer.visibility = View.VISIBLE
                if (audioArtAnimator == null) {
                    audioArtAnimator = android.animation.ObjectAnimator.ofFloat(audioArt, View.ROTATION, 0f, 360f).apply {
                        duration = 10000
                        repeatCount = android.animation.ValueAnimator.INFINITE
                        interpolator = android.view.animation.LinearInterpolator()
                    }
                }
            }
            
            if (previewUrl == null) {
                previewLoadingIndicator.visibility = View.GONE
                return
            }

            try {
                val sessionToken = SessionToken(activity, ComponentName(activity, AudioPlaybackService::class.java))
                controllerFuture = MediaController.Builder(activity, sessionToken).buildAsync()
                controllerFuture?.addListener({
                    try {
                        val controller = controllerFuture?.get()
                        exoPlayer = controller
                        if (controller != null) {
                            previewVideo.player = controller
                            if (isVideo) {
                                rotateVideoFab.visibility = View.VISIBLE
                                rotateVideoFab.setOnClickListener {
                                    val currentOrientation = activity.resources.configuration.orientation
                                    if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    } else {
                                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }
                            }
                            val currentUri = controller.currentMediaItem?.localConfiguration?.uri?.toString()
                            if (currentUri != previewUrl) {
                                val mediaItem = MediaItem.Builder()
                                    .setUri(previewUrl)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(fileName)
                                            .build()
                                    )
                                    .build()
                                controller.setMediaItem(mediaItem)
                                controller.prepare()
                                controller.playWhenReady = true
                            }
                            
                            controller.addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_READY) {
                                        previewLoadingIndicator.visibility = View.GONE
                                        if (isAudio) {
                                            previewVideo.visibility = View.VISIBLE
                                            val params = previewVideo.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                                            params.width = 1
                                            params.height = 1
                                            previewVideo.layoutParams = params
                                        }
                                    }
                                }
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    if (activity is FilePreviewActivity) {
                                        activity.updatePictureInPictureActions(isPlaying)
                                    }
                                    if (isAudio) {
                                        if (isPlaying) {
                                            if (audioArtAnimator?.isPaused == true) {
                                                audioArtAnimator?.resume()
                                            } else {
                                                audioArtAnimator?.start()
                                            }
                                        } else {
                                            audioArtAnimator?.pause()
                                        }
                                    }
                                }
                            })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        previewLoadingIndicator.visibility = View.GONE
                    }
                }, ContextCompat.getMainExecutor(activity))
            } catch (e: Exception) {
                android.widget.Toast.makeText(activity, "Error initializing player: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                previewLoadingIndicator.visibility = View.GONE
                previewUnsupported.visibility = View.VISIBLE
                previewVideo.visibility = View.GONE
                previewAudioContainer.visibility = View.GONE
            }
        } else if (isText) {
            previewRichTextContainer.visibility = View.VISIBLE
            previewLoadingIndicator.visibility = View.VISIBLE
            richEditor.setHtml(activity.getString(R.string.preview_loading_text))
            
            // Setup rich editor properties
            richEditor.setEditorHeight(200)
            richEditor.setEditorFontSize(16)
            richEditor.setPadding(10, 10, 10, 10)
            richEditor.setPlaceholder("Insert text here...")
            richEditor.setInputEnabled(false) // Initially read-only
            
            // Setup toolbar actions
            activity.findViewById<View>(R.id.action_undo).setOnClickListener { richEditor.undo() }
            activity.findViewById<View>(R.id.action_redo).setOnClickListener { richEditor.redo() }
            activity.findViewById<View>(R.id.action_bold).setOnClickListener { richEditor.setBold() }
            activity.findViewById<View>(R.id.action_italic).setOnClickListener { richEditor.setItalic() }
            activity.findViewById<View>(R.id.action_underline).setOnClickListener { richEditor.setUnderline() }
            activity.findViewById<View>(R.id.action_heading1).setOnClickListener { richEditor.setHeading(1) }
            activity.findViewById<View>(R.id.action_heading2).setOnClickListener { richEditor.setHeading(2) }
            activity.findViewById<View>(R.id.action_bullets).setOnClickListener { richEditor.setBullets() }
            activity.findViewById<View>(R.id.action_numbers).setOnClickListener { richEditor.setNumbers() }
            
            editFab.visibility = View.VISIBLE
            isRichEditing = false
            editFab.setImageResource(R.drawable.edit_3)
            
            editFab.setOnClickListener {
                if (!isRichEditing) {
                    isRichEditing = true
                    editFab.setImageResource(R.drawable.check)
                    richEditor.setInputEnabled(true)
                    richEditor.focusEditor()
                    richEditorToolbar.visibility = View.VISIBLE
                } else {
                    isRichEditing = false
                    editFab.setImageResource(R.drawable.edit_3)
                    richEditor.setInputEnabled(false)
                    richEditorToolbar.visibility = View.GONE
                    
                    val newHtmlContent = richEditor.html ?: ""
                    onSaveTextContent(newHtmlContent)
                }
            }
            
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
                    if (extension == "html" || content.contains("<html") || content.contains("<body") || content.contains("<p>")) {
                         richEditor.setHtml(content)
                    } else {
                         val escapedText = content.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                         richEditor.setHtml(escapedText)
                    }
                } catch (e: Exception) {
                    richEditor.setHtml(activity.getString(R.string.preview_error_loading_text) + "<br>" + e.message)
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
            if (isArchive) {
                extractButton.visibility = View.VISIBLE
                extractButton.setOnClickListener { onExtract() }
            } else {
                extractButton.visibility = View.GONE
            }
            val isConvertible = extension in listOf("xlsx", "xls", "pptx", "ppt", "doc", "docx")
            if (isConvertible) {
                previewConvertPdfButton.visibility = View.VISIBLE
                previewConvertPdfButton.setOnClickListener {
                    onConvertPdfRequested()
                }
            } else {
                previewConvertPdfButton.visibility = View.GONE
            }
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
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            audioArtAnimator?.cancel()
            audioArtAnimator = null
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
