package com.example.todolist.view.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.todolist.R
import com.example.todolist.data.TaskDatabase
import com.example.todolist.view.fragments.TaskListFragment
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)
        if (Build.VERSION.SDK_INT >= 35) {
            handleEdgeToEdge()
        }
        TaskDatabase.getDatabase(applicationContext)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TaskListFragment())
                .commit()
        }
    }

    private fun handleEdgeToEdge() {
        val window = window
        val statusBarColor = ContextCompat.getColor(this, R.color.orange)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val decorView = window.decorView
        decorView.setBackgroundColor(statusBarColor)

        ViewCompat.setOnApplyWindowInsetsListener(decorView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setBackgroundColor(statusBarColor)
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun handleExportToGoogleDrive() {
        Log.d("BaseActivity", "Export requested")

        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Preparing export...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val db = TaskDatabase.getDatabase(this@BaseActivity)
                val tasks = withContext(Dispatchers.IO) {
                    db.taskDao().getAllTasksSync()
                }

                Log.d("BaseActivity", "Total tasks: ${tasks.size}")

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    Toast.makeText(this@BaseActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val timestamp = System.currentTimeMillis()

                // Create both files
                val jsonFile = createJsonFile(tasks, timestamp)
                val excelFile = createExcelFile(tasks, timestamp)

                Log.d("BaseActivity", "Files created: ${jsonFile.name}, ${excelFile.name}")

                progressDialog.dismiss()

                // Share both files
                shareFiles(listOf(jsonFile, excelFile), tasks.size)

            } catch (e: Exception) {
                Log.e("BaseActivity", "Export failed", e)
                progressDialog.dismiss()

                AlertDialog.Builder(this@BaseActivity)
                    .setTitle("Export Failed")
                    .setMessage("Error: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private suspend fun createJsonFile(
        tasks: List<com.example.todolist.model.Task>,
        timestamp: Long
    ): File = withContext(Dispatchers.IO) {
        data class TaskExport(val title: String, val description: String?)

        val tasksWithoutId = tasks.map { TaskExport(it.title, it.description) }
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonData = gson.toJson(tasksWithoutId)

        val fileName = "medical_data_backup_$timestamp.json"
        File(cacheDir, fileName).apply {
            writeText(jsonData)
        }
    }

    private suspend fun createExcelFile(
        tasks: List<com.example.todolist.model.Task>,
        timestamp: Long
    ): File = withContext(Dispatchers.IO) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Medical Data")

        // Header style
        val headerStyle = workbook.createCellStyle()
        val headerFont = workbook.createFont()
        headerFont.bold = true
        headerStyle.setFont(headerFont)

        // Header row
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).apply {
            setCellValue("Title")
            cellStyle = headerStyle
        }
        headerRow.createCell(1).apply {
            setCellValue("Description")
            cellStyle = headerStyle
        }

        // Data rows
        tasks.forEachIndexed { index, task ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(task.title)
            row.createCell(1).setCellValue(task.description ?: "")
        }

        // Set column widths
        sheet.setColumnWidth(0, 6000)
        sheet.setColumnWidth(1, 15000)

        val fileName = "medical_data_backup_$timestamp.xlsx"
        File(cacheDir, fileName).apply {
            FileOutputStream(this).use { output ->
                workbook.write(output)
            }
            workbook.close()
        }
    }

    private fun shareFiles(files: List<File>, taskCount: Int) {
        try {
            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_SUBJECT, "Medical Data Backup")
                putExtra(Intent.EXTRA_TEXT, "Backup files (JSON + Excel) with $taskCount medical records")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.apps.docs")
            }

            try {
                startActivity(shareIntent)
                showOpenDriveOption()
            } catch (e: Exception) {
                shareIntent.setPackage(null)
                startActivity(Intent.createChooser(shareIntent, "Upload backup to Google Drive"))
            }
        } catch (e: Exception) {
            Log.e("BaseActivity", "Failed to share files", e)
            Toast.makeText(this, "Failed to share files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOpenDriveOption() {
        android.os.Handler(mainLooper).postDelayed({
            AlertDialog.Builder(this)
                .setTitle("Files Shared")
                .setMessage("Your backup files have been shared to Google Drive.\n\nWould you like to open Google Drive app?")
                .setPositiveButton("Open Google Drive") { _, _ ->
                    openGoogleDrive()
                }
                .setNegativeButton("Close", null)
                .show()
        }, 1000)
    }

    private fun openGoogleDrive() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
            if (intent != null) {
                startActivity(intent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://drive.google.com"))
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.e("BaseActivity", "Failed to open Google Drive", e)
            Toast.makeText(this, "Could not open Google Drive", Toast.LENGTH_SHORT).show()
        }
    }
}