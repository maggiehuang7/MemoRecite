package com.example.memorecite

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load

class CardEditActivity : AppCompatActivity() {

    private var imageFile: String? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = ImageStore.copyFromUri(this, uri)
            if (name != null) {
                val iv = findViewById<ImageView>(R.id.ivImage)
                val btnRemove = findViewById<Button>(R.id.btnRemoveImage)

                ImageStore.delete(this, imageFile)
                imageFile = name
                ImageStore.file(this, name)?.let { f -> iv.load(f) }
                iv.visibility = View.VISIBLE
                btnRemove.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, getString(R.string.ce_img_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_edit)

        val groups = MemoStore.load(this)
        val groupId = intent.getStringExtra("group_id") ?: run { finish(); return }
        val group = groups.firstOrNull { it.id == groupId } ?: run { finish(); return }
        val cardId = intent.getStringExtra("card_id")

        val etFront = findViewById<EditText>(R.id.etFront)
        val etBack = findViewById<EditText>(R.id.etBack)
        val ivImage = findViewById<ImageView>(R.id.ivImage)
        val btnRemoveImage = findViewById<Button>(R.id.btnRemoveImage)
        val btnPickImage = findViewById<Button>(R.id.btnPickImage)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val existing = cardId?.let { id -> group.cards.firstOrNull { it.id == id } }
        existing?.let {
            etFront.setText(it.front)
            etBack.setText(it.back)
            imageFile = it.imageFile
            if (it.hasImage) {
                ImageStore.file(this, it.imageFile)?.let { f ->
                    ivImage.load(f)
                    ivImage.visibility = View.VISIBLE
                    btnRemoveImage.visibility = View.VISIBLE
                }
            }
        }

        btnPickImage.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnRemoveImage.setOnClickListener {
            ImageStore.delete(this, imageFile)
            imageFile = null
            ivImage.visibility = View.GONE
            btnRemoveImage.visibility = View.GONE
        }

        btnSave.setOnClickListener {
            val front = etFront.text.toString().trim()
            val back = etBack.text.toString().trim()
            if (back.isEmpty()) {
                Toast.makeText(this, getString(R.string.ce_empty_back), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (existing != null) {
                val idx = group.cards.indexOfFirst { it.id == existing.id }
                if (idx >= 0) {
                    group.cards[idx] = existing.copy(
                        front = front,
                        back = back,
                        imageFile = imageFile
                    )
                }
            } else {
                val card = MemoCard(
                    front = front,
                    back = back,
                    imageFile = imageFile,
                    createdAt = System.currentTimeMillis()
                )
                EbbinghausScheduler.initNewCard(card, group.effectiveFirstDelay(groups))
                group.cards.add(card)
            }

            MemoStore.save(this, groups)
            AlarmScheduler.scheduleNext(this)
            finish()
        }
    }
}