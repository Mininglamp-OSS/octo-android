package com.chat.uikit.chat.provider

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chat.base.R
import java.io.File

class FileSaveActivity : AppCompatActivity() {

    private var sourceFilePath: String? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && sourceFilePath != null) {
            try {
                val sourceFile = File(sourceFilePath!!)
                contentResolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, getString(R.string.str_file_saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.str_file_save_fail), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceFilePath = intent.getStringExtra("sourceFilePath")
        val fileName = intent.getStringExtra("fileName") ?: "file"

        if (sourceFilePath == null || !File(sourceFilePath!!).exists()) {
            Toast.makeText(this, getString(R.string.str_file_not_exist), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (savedInstanceState == null) {
            createDocumentLauncher.launch(fileName)
        }
    }
}
