package com.example.filemanagementapp.preview

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.filemanagementapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var photoEditor: PhotoEditor
    private lateinit var colorPickerScroll: View
    private lateinit var colorPickerLayout: LinearLayout
    private lateinit var btnBrush: View
    private lateinit var btnText: View
    private lateinit var btnEraser: View

    private var currentMode = Mode.NONE
    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private val colorList = listOf(
        Color.BLACK,
        Color.WHITE,
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#9C27B0")  // Purple
    )

    enum class Mode {
        NONE, BRUSH, ERASER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_photo_editor)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topControlLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + v.paddingTop, v.paddingRight, v.paddingBottom)
            insets
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomControlLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + v.paddingBottom)
            insets
        }

        sourceUri = intent.getParcelableExtra(EXTRA_SOURCE_URI)
        destUri = intent.getParcelableExtra(EXTRA_DEST_URI)

        if (sourceUri == null || destUri == null) {
            Toast.makeText(this, "Thiếu đường dẫn ảnh", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        photoEditorView = findViewById(R.id.photoEditorView)
        photoEditorView.source.setImageURI(sourceUri)

        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .setClipSourceImage(true)
            .build()

        bindViews()
        setupColorPicker()
    }

    private fun bindViews() {
        colorPickerScroll = findViewById(R.id.colorPickerScroll)
        colorPickerLayout = findViewById(R.id.colorPickerLayout)
        btnBrush = findViewById(R.id.btnBrush)
        btnText = findViewById(R.id.btnText)
        btnEraser = findViewById(R.id.btnEraser)

        findViewById<ImageButton>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { photoEditor.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { photoEditor.redo() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveImage() }

        btnBrush.setOnClickListener {
            toggleMode(Mode.BRUSH)
        }

        btnEraser.setOnClickListener {
            toggleMode(Mode.ERASER)
        }

        btnText.setOnClickListener {
            showAddTextDialog()
        }
    }

    private fun toggleMode(mode: Mode) {
        if (currentMode == mode) {
            currentMode = Mode.NONE
            photoEditor.setBrushDrawingMode(false)
            colorPickerScroll.visibility = View.GONE
            updateButtonStates()
            return
        }

        currentMode = mode
        when (mode) {
            Mode.BRUSH -> {
                photoEditor.setBrushDrawingMode(true)
                photoEditor.setShape(ShapeBuilder().withShapeSize(15f))
                colorPickerScroll.visibility = View.VISIBLE
            }
            Mode.ERASER -> {
                photoEditor.brushEraser()
                colorPickerScroll.visibility = View.GONE
            }
            else -> {
                photoEditor.setBrushDrawingMode(false)
                colorPickerScroll.visibility = View.GONE
            }
        }
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val selectedColor = ContextCompat.getColor(this, R.color.preview_primary)
        val defaultColor = Color.WHITE

        findViewById<TextView>(R.id.textBrush).setTextColor(if (currentMode == Mode.BRUSH) selectedColor else defaultColor)
        findViewById<TextView>(R.id.textEraser).setTextColor(if (currentMode == Mode.ERASER) selectedColor else defaultColor)
    }

    private fun setupColorPicker() {
        for (color in colorList) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    setMargins(16, 16, 16, 16)
                }
                background = ContextCompat.getDrawable(this@PhotoEditorActivity, R.drawable.circle_shape)?.apply {
                    setTint(color)
                }
                setOnClickListener {
                    if (currentMode == Mode.BRUSH) {
                        photoEditor.setShape(ShapeBuilder().withShapeColor(color))
                    }
                }
            }
            colorPickerLayout.addView(colorView)
        }
    }

    private fun showAddTextDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_text, null)
        val editTextInput = dialogView.findViewById<TextInputEditText>(R.id.editTextInput)
        val textColorPickerLayout = dialogView.findViewById<LinearLayout>(R.id.textColorPickerLayout)

        var selectedTextColor = Color.WHITE

        for (color in colorList) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                    setMargins(12, 12, 12, 12)
                }
                background = ContextCompat.getDrawable(this@PhotoEditorActivity, R.drawable.circle_shape)?.apply {
                    setTint(color)
                }
                setOnClickListener {
                    selectedTextColor = color
                    editTextInput.setTextColor(color)
                }
            }
            textColorPickerLayout.addView(colorView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Thêm Chữ")
            .setView(dialogView)
            .setPositiveButton("Thêm") { _, _ ->
                val text = editTextInput.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    photoEditor.addText(text, selectedTextColor)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun saveImage() {
        val saveSettings = SaveSettings.Builder()
            .setClearViewsEnabled(true)
            .setTransparencyEnabled(true)
            .build()

        destUri?.let { uri ->
            try {
                // PhotoEditor requires absolute file path, not content URI for some Android versions, but let's use the file path if possible
                val file = File(uri.path!!)
                
                // Permission check for storage is not required since we save to cache dir
                photoEditor.saveAsFile(file.absolutePath, saveSettings, object : PhotoEditor.OnSaveListener {
                    override fun onSuccess(imagePath: String) {
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra(EXTRA_OUTPUT_URI, Uri.fromFile(File(imagePath)))
                        })
                        finish()
                    }

                    override fun onFailure(exception: Exception) {
                        Toast.makeText(this@PhotoEditorActivity, "Lỗi khi lưu: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: SecurityException) {
                Toast.makeText(this, "Lỗi quyền: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_DEST_URI = "extra_dest_uri"
        const val EXTRA_OUTPUT_URI = "extra_output_uri"
    }
}
