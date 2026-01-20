package com.example.todolist.view.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import androidx.core.view.GravityCompat
import com.example.todolist.databinding.ActivityBaseBinding
import com.example.todolist.model.Task
import org.apache.poi.ss.usermodel.*
import java.io.InputStream
import android.net.Uri
import org.apache.poi.ss.usermodel.*

class BaseActivity : AppCompatActivity() {
    lateinit var binding: ActivityBaseBinding

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1001
        private const val MAX_FILE_SIZE_MB = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= 35) {
            handleEdgeToEdge()
        }

        TaskDatabase.getDatabase(applicationContext)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TaskListFragment())
                .commit()
        }

        setupNavigationDrawer()
        setupBackPressHandler()
    }

    private fun setupNavigationDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_import -> {
                    handleImportFromGoogleDrive()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_export -> {
                    handleExportToGoogleDrive()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
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
                val excelFile = createExcelFile(tasks, timestamp)

                Log.d("BaseActivity", "File created: ${excelFile.name}")

                progressDialog.dismiss()
                shareFiles(listOf(excelFile), tasks.size)

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

        // Data rows (starting from row 1, after header)
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

    fun handleImportFromGoogleDrive() {
        openFilePicker()
    }

    private fun openFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
                ))
                putExtra("android.content.extra.SHOW_ADVANCED", true)
                putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            }

            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        } catch (e: Exception) {
            Log.e("BaseActivity", "Failed to open file picker", e)
            Toast.makeText(this, "Failed to open file picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Take persistable URI permission for Android 16+
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.e("BaseActivity", "Permission grant failed", e)
                }

                importExcelFile(uri)
            }
        }
    }
    private fun importExcelFile(uri: android.net.Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Importing data...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                // STEP 1: Validate file size BEFORE parsing
                val fileSize = getFileSize(uri)
                if (fileSize > MAX_FILE_SIZE_MB * 1024 * 1024) {
                    progressDialog.dismiss()
                    showErrorDialog(
                        "File Too Large",
                        "The selected file is too large (${fileSize / 1024 / 1024}MB). Please select a file smaller than ${MAX_FILE_SIZE_MB}MB.\n\nEnsure your Excel file only contains Title and Description columns."
                    )
                    return@launch
                }

                // STEP 2: Parse Excel file
                val tasks = withContext(Dispatchers.IO) {
                    parseExcelFile(uri)
                }

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    showErrorDialog(
                        "No Valid Data",
                        "No valid data found in the Excel file.\n\nPlease ensure:\n• Column 1: Title (required)\n• Column 2: Description (optional)\n• File format: .xlsx"
                    )
                    return@launch
                }

                // STEP 3: Import to database
                val db = TaskDatabase.getDatabase(this@BaseActivity)
                withContext(Dispatchers.IO) {
                    db.taskDao().deleteAllTasks()
                    db.taskDao().insertAll(tasks)
                }

                progressDialog.dismiss()

                AlertDialog.Builder(this@BaseActivity)
                    .setTitle("Import Successful")
                    .setMessage("Successfully imported ${tasks.size} records. All previous data has been replaced.")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: InvalidExcelFormatException) {
                Log.e("BaseActivity", "Invalid Excel format", e)
                progressDialog.dismiss()
                showErrorDialog(
                    "Invalid Excel Format",
                    e.message ?: "Invalid Excel file format.\n\nRequired format:\n• Column 1: Title\n• Column 2: Description\n• Only 2 columns allowed"
                )
            } catch (e: OutOfMemoryError) {
                Log.e("BaseActivity", "Out of memory", e)
                progressDialog.dismiss()
                showErrorDialog(
                    "File Too Large",
                    "The Excel file is too large to import. Please reduce the number of rows or split into smaller files.\n\nRequired format:\n• Column 1: Title\n• Column 2: Description"
                )
            } catch (e: Exception) {
                Log.e("BaseActivity", "Import failed", e)
                progressDialog.dismiss()
                showErrorDialog(
                    "Import Failed",
                    "Could not import the Excel file.\n\nPlease ensure:\n• File format is .xlsx\n• Column 1: Title (required)\n• Column 2: Description (optional)\n• No extra columns\n\nError: ${e.message}"
                )
            }
        }
    }
    private fun getFileSize(uri: android.net.Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    class InvalidExcelFormatException(message: String) : Exception(message)
    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""

        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue ?: ""
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    // Remove .0 from whole numbers
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
    private fun parseExcelFile(uri: Uri): List<Task> {
        val tasks = mutableListOf<Task>()

        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw InvalidExcelFormatException("Unable to open file")

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Start from row 0 (first row)
            for (rowIndex in 0 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(rowIndex) ?: continue

                // Skip header row if detected
                if (isHeaderRow(row)) {
                    Log.d("BaseActivity", "Skipping header row at index $rowIndex")
                    continue
                }

                // Get all cell values, filtering out empty ones
                val cellValues = mutableListOf<String>()

                // Iterate through all cells in the row
                for (cellIndex in 0 until row.lastCellNum) {
                    val cell = row.getCell(cellIndex)
                    val cellValue = getCellValueAsString(cell)

                    // Only add non-empty values
                    if (cellValue.isNotBlank()) {
                        cellValues.add(cellValue.trim())
                    }
                }

                // Skip completely empty rows
                if (cellValues.isEmpty()) {
                    continue
                }

                // Get title (first column)
                val title = cellValues.getOrNull(0)?.trim() ?: ""

                // Skip if title is empty
                if (title.isBlank()) {
                    continue
                }

                // Merge all remaining columns into description
                val description = if (cellValues.size > 1) {
                    cellValues.subList(1, cellValues.size).joinToString(" | ")
                } else {
                    null
                }

                // Create task matching your data class structure
                tasks.add(
                    Task(
                        title = title,
                        description = description
                    )
                )
            }

            workbook.close()
            inputStream.close()

        } catch (e: InvalidExcelFormatException) {
            throw e
        } catch (e: Exception) {
            throw InvalidExcelFormatException("Error parsing Excel file: ${e.message}")
        }

        return tasks
    }
    private fun isHeaderRow(row: org.apache.poi.ss.usermodel.Row): Boolean {
        val firstCell = row.getCell(0)
        val firstCellValue = getCellValueAsString(firstCell).trim().lowercase()

        // Check if first cell contains common header keywords
        val isHeader = firstCellValue in listOf("title", "name", "task", "item", "subject", "description", "desc")

        if (isHeader) {
            Log.d("BaseActivity", "Detected header row with value: $firstCellValue")
        }

        return isHeader
    }
}