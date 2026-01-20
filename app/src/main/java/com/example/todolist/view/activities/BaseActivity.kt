package com.example.todolist.view.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import androidx.core.view.GravityCompat
import com.example.todolist.databinding.ActivityBaseBinding
import com.example.todolist.model.Task
import org.apache.poi.ss.usermodel.*
import android.net.Uri
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BaseActivity : AppCompatActivity() {
    private var _binding: ActivityBaseBinding? = null
    private val binding get() = _binding!!
    private lateinit var crashlytics: FirebaseCrashlytics
    private var isImporting = false // Prevent multiple imports

    companion object {
        private const val MAX_FILE_SIZE_MB = 10
        private const val TAG = "BaseActivity"
    }

    // Modern ActivityResultLauncher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        Log.d(TAG, "File picker result received: $uri")
        crashlytics.log("File picker result: $uri")

        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "URI permission granted successfully")
                crashlytics.log("File picked and permission granted: $uri")
                importExcelFile(uri)
            } catch (e: SecurityException) {
                crashlytics.recordException(e)
                Log.e(TAG, "Security exception during permission grant", e)
                Toast.makeText(this, "Cannot access file. Please try again.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                crashlytics.recordException(e)
                Log.e(TAG, "Exception during file picker result", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d(TAG, "File picker cancelled by user")
            crashlytics.log("File picker cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        try {
            // Initialize Crashlytics first
            crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.log("BaseActivity onCreate - Android ${Build.VERSION.SDK_INT}")

            _binding = ActivityBaseBinding.inflate(layoutInflater)
            setContentView(binding.root)

            Log.d(TAG, "Layout inflated successfully")

            if (Build.VERSION.SDK_INT >= 35) {
                handleEdgeToEdge()
                crashlytics.log("Android 16 detected")
            }

            // Initialize database
            TaskDatabase.getDatabase(applicationContext)
            Log.d(TAG, "Database initialized")

            // Load fragment only if not already loaded
            if (savedInstanceState == null) {
                Log.d(TAG, "Loading TaskListFragment")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TaskListFragment())
                    .commit()
            }

            setupNavigationDrawer()
            setupBackPressHandler()

            Log.d(TAG, "onCreate completed successfully")
            crashlytics.log("BaseActivity onCreate completed")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: onCreate failed", e)
            // Show error and finish activity
            Toast.makeText(this, "App initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupNavigationDrawer() {
        try {
            Log.d(TAG, "Setting up navigation drawer")

            binding.navigationView.setNavigationItemSelectedListener { menuItem ->
                Log.d(TAG, "Navigation item clicked: ${menuItem.itemId}")
                crashlytics.log("Nav item: ${menuItem.itemId}")

                try {
                    val result = when (menuItem.itemId) {
                        R.id.nav_import -> {
                            Log.d(TAG, "Import menu item selected")
                            crashlytics.log("Import button clicked")

                            // Prevent multiple simultaneous imports
                            if (isImporting) {
                                Log.w(TAG, "Import already in progress, ignoring click")
                                Toast.makeText(this, "Import already in progress", Toast.LENGTH_SHORT).show()
                                binding.drawerLayout.closeDrawer(GravityCompat.START)
                                return@setNavigationItemSelectedListener true
                            }

                            handleImportFromGoogleDrive()
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                            true
                        }
                        R.id.nav_export -> {
                            Log.d(TAG, "Export menu item selected")
                            crashlytics.log("Export button clicked")
                            handleExportToGoogleDrive()
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                            true
                        }
                        else -> {
                            Log.w(TAG, "Unknown menu item: ${menuItem.itemId}")
                            false
                        }
                    }

                    Log.d(TAG, "Menu item handled, result: $result")
                    result

                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e(TAG, "Error handling navigation item", e)
                    Toast.makeText(this, "Action failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    false
                }
            }

            Log.d(TAG, "Navigation drawer setup complete")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: Failed to setup navigation drawer", e)
            Toast.makeText(this, "Navigation setup failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        try {
            Log.d(TAG, "Opening drawer")
            binding.drawerLayout.openDrawer(GravityCompat.START)
        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "Failed to open drawer", e)
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
        crashlytics.log("handleExportToGoogleDrive started")
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

                Log.d(TAG, "Total tasks: ${tasks.size}")
                crashlytics.log("Exporting ${tasks.size} tasks")

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    Toast.makeText(this@BaseActivity, "No data to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val excelFile = createExcelFile(tasks, timestamp)

                Log.d(TAG, "File created: ${excelFile.name}")
                crashlytics.log("Excel file created: ${excelFile.name}")

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
            Log.e(TAG, "Failed to share files", e)
            Toast.makeText(this, "Failed to share files: ${e.message}", Toast.LENGTH_LONG).show()
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
                Log.e(TAG, "Failed to show drive option dialog", e)
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
            Log.e(TAG, "Failed to open Google Drive", e)
            Toast.makeText(this, "Could not open Google Drive", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleImportFromGoogleDrive() {
        Log.d(TAG, "handleImportFromGoogleDrive called")
        crashlytics.log("handleImportFromGoogleDrive called")

        try {
            // Check if already importing
            if (isImporting) {
                Log.w(TAG, "Import already in progress")
                Toast.makeText(this, "Import already in progress", Toast.LENGTH_SHORT).show()
                return
            }

            isImporting = true
            crashlytics.log("Opening file picker")
            openFilePicker()

        } catch (e: Exception) {
            isImporting = false
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: handleImportFromGoogleDrive failed", e)
            Toast.makeText(this, "Failed to open file picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFilePicker() {
        try {
            Log.d(TAG, "Launching file picker")
            crashlytics.log("Launching ActivityResultLauncher")

            // Launch the modern file picker
            filePickerLauncher.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel"
            ))

            Log.d(TAG, "File picker launched successfully")

        } catch (e: Exception) {
            isImporting = false
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: Failed to launch file picker", e)
            Toast.makeText(this, "Failed to open file picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importExcelFile(uri: Uri) {
        Log.d(TAG, "importExcelFile called with URI: $uri")
        crashlytics.log("importExcelFile started for URI: $uri")

        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Importing data...")
            .setCancelable(false)
            .create()

        try {
            progressDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show progress dialog", e)
            crashlytics.recordException(e)
        }

        lifecycleScope.launch {
            try {
                val fileSize = getFileSize(uri)
                Log.d(TAG, "File size: $fileSize bytes")
                crashlytics.log("File size: $fileSize bytes")

                if (fileSize > MAX_FILE_SIZE_MB * 1024 * 1024) {
                    progressDialog.dismiss()
                    isImporting = false
                    showErrorDialog(
                        "File Too Large",
                        "The selected file is too large (${fileSize / 1024 / 1024}MB). Please select a file smaller than ${MAX_FILE_SIZE_MB}MB."
                    )
                    return@launch
                }

                Log.d(TAG, "Starting Excel parsing")
                val tasks = withContext(Dispatchers.IO) {
                    parseExcelFile(uri)
                }

                Log.d(TAG, "Parsed ${tasks.size} tasks")
                crashlytics.log("Parsed ${tasks.size} tasks from Excel")

                if (tasks.isEmpty()) {
                    progressDialog.dismiss()
                    isImporting = false
                    showErrorDialog(
                        "No Valid Data",
                        "No valid data found in the Excel file."
                    )
                    return@launch
                }

                Log.d(TAG, "Inserting tasks into database")
                val db = TaskDatabase.getDatabase(this@BaseActivity)
                withContext(Dispatchers.IO) {
                    db.taskDao().deleteAllTasks()
                    db.taskDao().insertAll(tasks)
                }

                Log.d(TAG, "Import completed successfully")
                crashlytics.log("Successfully imported ${tasks.size} tasks")
                progressDialog.dismiss()
                isImporting = false

                AlertDialog.Builder(this@BaseActivity)
                    .setTitle("Import Successful")
                    .setMessage("Successfully imported ${tasks.size} records. All previous data has been replaced.")
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: InvalidExcelFormatException) {
                crashlytics.recordException(e)
                Log.e(TAG, "Invalid Excel format", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("Invalid Excel Format", e.message ?: "Invalid file format")
            } catch (e: OutOfMemoryError) {
                crashlytics.recordException(e)
                Log.e(TAG, "Out of memory", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("File Too Large", "The Excel file is too large to import.")
            } catch (e: Exception) {
                crashlytics.recordException(e)
                Log.e(TAG, "Import failed", e)
                progressDialog.dismiss()
                isImporting = false
                showErrorDialog("Import Failed", "Error: ${e.message}")
            }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0L
        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "Failed to get file size", e)
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
            Log.e(TAG, "Failed to show error dialog", e)
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
                    Log.d(TAG, "Skipping header row at index $rowIndex")
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

                if (cellValues.isEmpty()) {
                    continue
                }

                val title = cellValues.getOrNull(0)?.trim() ?: ""

                if (title.isBlank()) {
                    continue
                }

                val description = if (cellValues.size > 1) {
                    cellValues.subList(1, cellValues.size).joinToString(" | ")
                } else {
                    null
                }

                tasks.add(Task(title = title, description = description))
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

    private fun isHeaderRow(row: Row): Boolean {
        val firstCell = row.getCell(0)
        val firstCellValue = getCellValueAsString(firstCell).trim().lowercase()

        val isHeader = firstCellValue in listOf("title", "name", "task", "item", "subject", "description", "desc")

        if (isHeader) {
            Log.d(TAG, "Detected header row with value: $firstCellValue")
        }

        return isHeader
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
        Log.d(TAG, "BaseActivity destroyed")
    }
}