package com.example.memorecite

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load

class BulkImportActivity : AppCompatActivity() {
    private lateinit var groups: MutableList<MemoGroup>
    private lateinit var group: MemoGroup
    private lateinit var etText: EditText
    private lateinit var tvPreview: TextView
    private lateinit var imagePreviewContainer: LinearLayout

    private val selectedImages = mutableListOf<String>()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val name = ImageStore.copyFromUri(this, uri)
            if (name != null) selectedImages.add(name)
        }
        renderPreviews()
        updatePreviewText()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bulk_import)

        val groupId = intent.getStringExtra("group_id") ?: run { finish(); return }
        groups = MemoStore.load(this)
        group = groups.firstOrNull { it.id == groupId } ?: run { finish(); return }

        etText = findViewById(R.id.etText)
        tvPreview = findViewById(R.id.tvPreview)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)

        findViewById<TextView>(R.id.tvTargetGroup).text =
            getString(R.string.bi_target, "${group.icon} ${group.name}")

        findViewById<Button>(R.id.btnPickImages).setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener { doImport() }

        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreviewText() }
        })

        renderPreviews()
        updatePreviewText()
    }

    private fun renderPreviews() {
        imagePreviewContainer.removeAllViews()
        selectedImages.forEachIndexed { index, name ->
            val item = LayoutInflater.from(this)
                .inflate(R.layout.item_image_preview, imagePreviewContainer, false)
            val iv = item.findViewById<ImageView>(R.id.ivPreview)
            val tvIndex = item.findViewById<TextView>(R.id.tvIndex)
            val btnDelete = item.findViewById<TextView>(R.id.btnDeleteImage)

            ImageStore.file(this, name)?.let { iv.load(it) }
            tvIndex.text = "${index + 1}"
            btnDelete.setOnClickListener {
                ImageStore.delete(this, name)
                selectedImages.removeAt(index)
                renderPreviews()
                updatePreviewText()
            }
            imagePreviewContainer.addView(item)
        }
    }

    private fun updatePreviewText() {
        val lines = etText.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            tvPreview.text = getString(R.string.bi_preview_empty)
            return
        }
        val sb = StringBuilder()
        lines.take(10).forEachIndexed { i, line ->
            val img = if (i < selectedImages.size) " 🖼️" else ""
            sb.append("${i + 1}. $line$img\n")
        }
        if (lines.size > 10) sb.append("... 共 ${lines.size} 张")
        else sb.append("共 ${lines.size} 张")
        tvPreview.text = sb.toString()
    }

    private fun doImport() {
        val lines = etText.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            Toast.makeText(this, getString(R.string.bi_no_content), Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveDelay = group.effectiveFirstDelay(groups)

        var count = 0
        lines.forEachIndexed { index, line ->
            val imageFile = if (index < selectedImages.size) selectedImages[index] else null
            val card = if (line.contains("|")) {
                val parts = line.split("|", limit = 2)
                MemoCard(
                    front = parts[0].trim(),
                    back = parts[1].trim(),
                    imageFile = imageFile,
                    createdAt = System.currentTimeMillis()
                )
            } else {
                MemoCard(
                    back = line,
                    imageFile = imageFile,
                    createdAt = System.currentTimeMillis()
                )
            }
            EbbinghausScheduler.initNewCard(card, effectiveDelay)
            group.cards.add(card)
            count++
        }
        MemoStore.save(this, groups)
        AlarmScheduler.scheduleNext(this)
        Toast.makeText(this, getString(R.string.bi_imported, count), Toast.LENGTH_SHORT).show()
        finish()
    }
}