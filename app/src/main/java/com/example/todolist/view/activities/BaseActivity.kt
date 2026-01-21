package com.example.todolist.view.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import com.example.todolist.databinding.ActivityBaseBinding
import com.example.todolist.model.Task
import org.apache.poi.ss.usermodel.*
import android.net.Uri
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BaseActivity : AppCompatActivity() {
    private var _binding: ActivityBaseBinding? = null
    private val binding get() = _binding!!
    private lateinit var crashlytics: FirebaseCrashlytics
    private var isImporting = false

    companion object {
        private const val MAX_FILE_SIZE_MB = 10
        private const val TAG = "BaseActivity"
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        Log.d(TAG, "File picker result: $uri")
        crashlytics.log("File picker result: $uri")

        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Permission granted")
                crashlytics.log("File picked: $uri")
                importExcelFile(uri)
            } catch (e: SecurityException) {
                isImporting = false
                crashlytics.recordException(e)
                Log.e(TAG, "Security exception", e)
                Toast.makeText(this, "Cannot access file. Please try again.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                isImporting = false
                crashlytics.recordException(e)
                Log.e(TAG, "File picker error", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            isImporting = false
            Log.d(TAG, "File picker cancelled")
            crashlytics.log("File picker cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        try {
            crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.log("BaseActivity onCreate - Android ${Build.VERSION.SDK_INT}")

            _binding = ActivityBaseBinding.inflate(layoutInflater)
            setContentView(binding.root)

            Log.d(TAG, "Layout inflated")

            if (Build.VERSION.SDK_INT >= 35) {
                handleEdgeToEdge()
            }

            TaskDatabase.getDatabase(applicationContext)
            Log.d(TAG, "Database initialized")

            if (savedInstanceState == null) {
                Log.d(TAG, "Loading fragment")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TaskListFragment())
                    .commit()
            }

            Log.d(TAG, "onCreate completed")
            crashlytics.log("BaseActivity ready")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "onCreate failed", e)
            Toast.makeText(this, "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun handleEdgeToEdge() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Edge-to-edge setup failed", e)
        }
    }

    fun handleExportToGoogleDrive() {
        crashlytics.log("Export started")
        Log.d(TAG, "Export requested")

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

                Log.d(TAG, "Tasks count: ${tasks.size}")
                crashlytics.log("Exporting ${tasks.size} tasks")

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    Toast.makeText(this@BaseActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val excelFile = createExcelFile(tasks, timestamp)

                Log.d(TAG, "File created: ${excelFile.name}")
                crashlytics.log("Excel created: ${excelFile.name}")

                progressDialog.dismiss()
                shareFiles(listOf(excelFile), tasks.size)

            } catch (e: Exception) {
                crashlytics.recordException(e)
                Log.e(TAG, "Export failed", e)
                progressDialog.dismiss()

                AlertDialog.Builder(this@BaseActivity)
                    .setTitle("Export Failed")
                    .setMessage("Error: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private suspend fun createExcelFile(
        tasks: List<Task>,
        timestamp: Long
    ): File = withContext(Dispatchers.IO) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Medical Data")

        val headerStyle = workbook.createCellStyle()
        val headerFont = workbook.createFont()
        headerFont.bold = true
        headerStyle.setFont(headerFont)

        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).apply {
            setCellValue("Title")
            cellStyle = headerStyle
        }
        headerRow.createCell(1).apply {
            setCellValue("Description")
            cellStyle = headerStyle
        }

        tasks.forEachIndexed { index, task ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(task.title)
            row.createCell(1).setCellValue(task.description ?: "")
        }

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
                putExtra(Intent.EXTRA_TEXT, "Backup files (Excel) with $taskCount medical records")
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
            crashlytics.recordException(e)
            Log.e(TAG, "Share failed", e)
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOpenDriveOption() {
        android.os.Handler(mainLooper).postDelayed({
            try {
                AlertDialog.Builder(this)
                    .setTitle("Files Shared")
                    .setMessage("Your backup files have been shared to Google Drive.\n\nWould you like to open Google Drive app?")
                    .setPositiveButton("Open Google Drive") { _, _ ->
                        openGoogleDrive()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Dialog failed", e)
            }
        }, 1000)
    }

    private fun openGoogleDrive() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
            if (intent != null) {
                startActivity(intent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com"))
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "Drive open failed", e)
            Toast.makeText(this, "Could not open Google Drive", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleImportFromGoogleDrive() {
        Log.d(TAG, "Import called")
        crashlytics.log("Import called")

        try {
            if (isImporting) {
                Log.w(TAG, "Already importing")
                Toast.makeText(this, "Import in progress", Toast.LENGTH_SHORT).show()
                return
            }

            isImporting = true
            crashlytics.log("Opening file picker")
            openFilePicker()

        } catch (e: Exception) {
            isImporting = false
            crashlytics.recordException(e)
            Log.e(TAG, "Import failed", e)
            Toast.makeText(this, "Failed to open file picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFilePicker() {
        try {
            Log.d(TAG, "Launching picker")
            crashlytics.log("Launching file picker")

            filePickerLauncher.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel"
            ))

            Log.d(TAG, "Picker launched")

        } catch (e: Exception) {
            isImporting = false
            crashlytics.recordException(e)
            Log.e(TAG, "Picker launch failed", e)
            Toast.makeText(this, "Failed to open picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importExcelFile(uri: Uri) {
        Log.d(TAG, "Import Excel: $uri")
        crashlytics.log("Import Excel: $uri")

        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Importing data...")
            .setCancelable(false)
            .create()

        try {
            progressDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Dialog failed", e)
            crashlytics.recordException(e)
        }

        lifecycleScope.launch {
            try {
                val fileSize = getFileSize(uri)
                Log.d(TAG, "File size: $fileSize")
                crashlytics.log("File size: $fileSize")

                if (fileSize > MAX_FILE_SIZE_MB * 1024 * 1024) {
                    progressDialog.dismiss()
                    isImporting = false
                    showErrorDialog(
                        "File Too Large",
                        "File is ${fileSize / 1024 / 1024}MB. Max is ${MAX_FILE_SIZE_MB}MB."
                    )
                    return@launch
                }

                Log.d(TAG, "Parsing Excel")
                val tasks = withContext(Dispatchers.IO) {
                    parseExcelFile(uri)
                }

                Log.d(TAG, "Parsed ${tasks.size} tasks")
                crashlytics.log("Parsed ${tasks.size} tasks")

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    isImporting = false
                    showErrorDialog(
                        "No Valid Data",
                        "No valid data found in Excel file."
                    )
                    return@launch
                }

                Log.d(TAG, "Saving to database")
                val db = TaskDatabase.getDatabase(this@BaseActivity)
                withContext(Dispatchers.IO) {
                    db.taskDao().deleteAllTasks()
                    db.taskDao().insertAll(tasks)
                }

                Log.d(TAG, "Import complete")
                crashlytics.log("Import successful: ${tasks.size} tasks")
                progressDialog.dismiss()
                isImporting = false

                AlertDialog.Builder(this@BaseActivity)
                    .setTitle("Import Successful")
                    .setMessage("Imported ${tasks.size} records. Previous data replaced.")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: InvalidExcelFormatException) {
                crashlytics.recordException(e)
                Log.e(TAG, "Invalid format", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("Invalid Excel Format", e.message ?: "Invalid format")
            } catch (e: OutOfMemoryError) {
                crashlytics.recordException(e)
                Log.e(TAG, "Out of memory", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("File Too Large", "File is too large to import.")
            } catch (e: Exception) {
                crashlytics.recordException(e)
                Log.e(TAG, "Import error", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("Import Failed", "Error: ${e.message}")
            }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            crashlytics.recordException(e)
            0L
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error dialog failed", e)
            Toast.makeText(this, "$title: $message", Toast.LENGTH_LONG).show()
        }
    }

    class InvalidExcelFormatException(message: String) : Exception(message)

    private fun parseExcelFile(uri: Uri): List<Task> {
        val tasks = mutableListOf<Task>()

        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw InvalidExcelFormatException("Unable to open file")

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            for (rowIndex in 0 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(rowIndex) ?: continue

                if (isHeaderRow(row)) {
                    Log.d(TAG, "Skipping header at row $rowIndex")
                    continue
                }

                val cellValues = mutableListOf<String>()

                for (cellIndex in 0 until row.lastCellNum) {
                    val cell = row.getCell(cellIndex)
                    val cellValue = getCellValueAsString(cell)

                    if (cellValue.isNotBlank()) {
                        cellValues.add(cellValue.trim())
                    }
                }

                if (cellValues.isEmpty()) continue

                val title = cellValues.getOrNull(0)?.trim() ?: ""
                if (title.isBlank()) continue

                val description = if (cellValues.size > 1) {
                    cellValues.subList(1, cellValues.size).joinToString(" | ")
                } else null

                tasks.add(Task(title = title, description = description))
            }

            workbook.close()
            inputStream.close()

        } catch (e: InvalidExcelFormatException) {
            throw e
        } catch (e: Exception) {
            throw InvalidExcelFormatException("Parse error: ${e.message}")
        }

        return tasks
    }

    private fun isHeaderRow(row: Row): Boolean {
        val firstCell = row.getCell(0)
        val value = getCellValueAsString(firstCell).trim().lowercase()
        return value in listOf("title", "name", "task", "item", "subject", "description", "desc")
    }

    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""

        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue ?: ""
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    val numValue = cell.numericCellValue
                    if (numValue == numValue.toLong().toDouble()) {
                        numValue.toLong().toString()
                    } else {
                        numValue.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue ?: ""
                } catch (e: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (e: Exception) {
                        ""
                    }
                }
            }
            CellType.BLANK -> ""
            CellType._NONE -> ""
            else -> ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        Log.d(TAG, "Destroyed")
    }
}