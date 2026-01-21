package com.example.todolist.view.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.todolist.R
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todolist.adapter.TaskAdapter
import com.example.todolist.data.TaskDao
import com.example.todolist.data.TaskDatabase
import com.example.todolist.databinding.FragmentTaskListBinding
import com.example.todolist.model.Task
import com.example.todolist.view.activities.BaseActivity
import kotlinx.coroutines.launch
import com.google.firebase.crashlytics.FirebaseCrashlytics

class TaskListFragment : Fragment() {
    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskDao: TaskDao
    private var fullTaskList = listOf<Task>()
    private lateinit var crashlytics: FirebaseCrashlytics

    companion object {
        private const val TAG = "TaskListFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")

        return try {
            crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("TaskListFragment onCreateView")

            _binding = FragmentTaskListBinding.inflate(inflater, container, false)
            Log.d(TAG, "View binding inflated successfully")
            binding.root
        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: onCreateView failed", e)
            // Return empty view to prevent crash
            View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            crashlytics.log("TaskListFragment onViewCreated")
            initialization()
            setUpObserver()
            setUpListener()
            Log.d(TAG, "onViewCreated completed successfully")
        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: onViewCreated failed", e)
        }
    }

    private fun initialization() {
        try {
            Log.d(TAG, "Initializing TaskListFragment")

            taskDao = TaskDatabase.getDatabase(requireContext()).taskDao()
            Log.d(TAG, "TaskDao initialized")

            taskAdapter = TaskAdapter { task ->
                try {
                    Log.d(TAG, "Task clicked: ${task.id}")
                    val detailFragment = TaskDetailFragment.newInstance(task.id)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, detailFragment)
                        .addToBackStack(null)
                        .commit()
                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e(TAG, "Failed to open task detail", e)
                }
            }

            Log.d(TAG, "TaskAdapter created")

            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerView.adapter = taskAdapter

            Log.d(TAG, "RecyclerView configured")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: initialization failed", e)
            throw e
        }
    }

    private fun setUpObserver() {
        try {
            Log.d(TAG, "Setting up observer")

            taskDao.getAllTasks().observe(viewLifecycleOwner) { tasks ->
                try {
                    Log.d(TAG, "Tasks updated: ${tasks?.size ?: 0} items")

                    fullTaskList = tasks ?: emptyList()
                    val searchText = binding.edtSearch.text.toString().trim()

                    if (searchText.isNotEmpty()) {
                        val filteredList = fullTaskList.filter {
                            it.title.contains(searchText, ignoreCase = true)
                        }.sortedBy { it.title.lowercase() }
                        taskAdapter.submitList(filteredList)
                    } else {
                        val sortedTasks = fullTaskList.sortedBy { it.title.lowercase() }
                        taskAdapter.submitList(sortedTasks)
                    }

                    if (fullTaskList.isEmpty()) {
                        binding.tvNoData.visibility = View.VISIBLE
                    } else {
                        binding.tvNoData.visibility = View.GONE
                    }

                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e(TAG, "Error in observer callback", e)
                }
            }

            Log.d(TAG, "Observer setup complete")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: setUpObserver failed", e)
            throw e
        }
    }

    private fun setUpListener() {
        try {
            Log.d(TAG, "Setting up listeners")

            binding.edtSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        val searchText = s.toString().trim()
                        if (searchText.isNotEmpty()) {
                            val filteredList = fullTaskList.filter {
                                it.title.contains(searchText, ignoreCase = true)
                            }.sortedBy { it.title.lowercase() }
                            taskAdapter.submitList(filteredList)
                        } else {
                            val sortedList = fullTaskList.sortedBy { it.title.lowercase() }
                            taskAdapter.submitList(sortedList)
                        }
                    } catch (e: Exception) {
                        crashlytics.recordException(e)
                        Log.e(TAG, "Error in text watcher", e)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            binding.btnAdd.setOnClickListener {
                try {
                    Log.d(TAG, "Add button clicked")
                    crashlytics.log("Add button clicked")
                    showAddTaskDialog()
                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e(TAG, "Failed to show add dialog", e)
                }
            }

            binding.layoutImport.setOnClickListener {
                (activity as? BaseActivity)?.handleImportFromGoogleDrive()
            }

            binding.layoutExport.setOnClickListener {
                (activity as? BaseActivity)?.handleExportToGoogleDrive()
            }

            Log.d(TAG, "Listeners setup complete")

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "CRITICAL: setUpListener failed", e)
            throw e
        }
    }

    private fun showAddTaskDialog() {
        try {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
            val etTask = dialogView.findViewById<EditText>(R.id.etTask)

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
                try {
                    val taskText = etTask.text.toString().trim()
                    if (taskText.isNotEmpty()) {
                        lifecycleScope.launch {
                            try {
                                val newTask = Task(title = taskText)
                                taskDao.insertTask(newTask)
                                dialog.dismiss()
                                Log.d(TAG, "Task added successfully")
                            } catch (e: Exception) {
                                crashlytics.recordException(e)
                                Log.e(TAG, "Failed to insert task", e)
                            }
                        }
                    } else {
                        etTask.error = "Please enter a task"
                    }
                } catch (e: Exception) {
                    crashlytics.recordException(e)
                    Log.e(TAG, "Failed to save task", e)
                }
            }

            dialog.show()

        } catch (e: Exception) {
            crashlytics.recordException(e)
            Log.e(TAG, "Failed to show add task dialog", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView called")
    }
}