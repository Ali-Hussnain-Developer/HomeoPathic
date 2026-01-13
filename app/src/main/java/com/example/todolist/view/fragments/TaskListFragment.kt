package com.example.todolist.view.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.todolist.R
import android.text.Editable
import android.text.TextWatcher
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

class TaskListFragment : Fragment() {
    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskDao: TaskDao
    private var fullTaskList = listOf<Task>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialization()
        setUpObserver()
        setUpListener()
    }

    private fun setUpObserver() {
        taskDao.getAllTasks().observe(viewLifecycleOwner) { tasks ->
            fullTaskList = tasks
            val searchText = binding.edtSearch.text.toString().trim()
            if (searchText.isNotEmpty()) {
                val filteredList = tasks.filter {
                    it.title.contains(searchText, ignoreCase = true)
                }.sortedBy { it.title.lowercase() }
                taskAdapter.submitList(filteredList)
            } else {
                val sortedTasks = tasks.sortedBy { it.title.lowercase() }
                taskAdapter.submitList(sortedTasks)
            }
            if (tasks.isEmpty()) {
                binding.tvNoData.visibility = View.VISIBLE
            } else {
                binding.tvNoData.visibility = View.GONE
            }
        }
    }

    private fun setUpListener() {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s.toString().trim()
                if (searchText.isNotEmpty()) {
                    val filteredList = fullTaskList.filter {
                        it.title.contains(searchText, ignoreCase = true)
                    }.sortedBy { it.title.lowercase() }

                    taskAdapter.submitList(filteredList)
                } else {
                    val filteredListFullTaskList = fullTaskList.filter {
                        it.title.contains(searchText, ignoreCase = true)
                    }.sortedBy { it.title.lowercase() }

                    taskAdapter.submitList(filteredListFullTaskList)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAdd.setOnClickListener {
            showAddTaskDialog()
        }

        binding.btnGoogleDrive.setOnClickListener {
            (activity as? BaseActivity)?.handleExportToGoogleDrive()
        }
        binding.btnImport.setOnClickListener {
            (activity as? BaseActivity)?.handleImportFromGoogleDrive()
        }
    }

    private fun initialization() {
        taskDao = TaskDatabase.getDatabase(requireContext()).taskDao()

        taskAdapter = TaskAdapter { task ->
            val detailFragment = TaskDetailFragment.newInstance(task.id)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = taskAdapter
    }

    private fun showAddTaskDialog() {
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
            val taskText = etTask.text.toString().trim()
            if (taskText.isNotEmpty()) {
                lifecycleScope.launch {
                    val newTask = Task(title = taskText)
                    taskDao.insertTask(newTask)
                    dialog.dismiss()
                }
            } else {
                etTask.error = "Please enter a task"
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}