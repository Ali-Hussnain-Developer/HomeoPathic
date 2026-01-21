package com.example.todolist.view.fragments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Html
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.example.todolist.R
import com.example.todolist.data.TaskDatabase
import com.example.todolist.databinding.FragmentTaskDetailBinding
import com.example.todolist.model.Task
import jp.wasabeef.richeditor.RichEditor
import kotlinx.coroutines.launch

class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: TaskDatabase
    private var taskId: Int = -1
    private var task: Task? = null
    private var dialog: Dialog? = null

    companion object {
        private const val ARG_TASK_ID = "task_id"

        fun newInstance(taskId: Int): TaskDetailFragment {
            val fragment = TaskDetailFragment()
            val args = Bundle()
            args.putInt(ARG_TASK_ID, taskId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            taskId = it.getInt(ARG_TASK_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialization()
        setUpListener()
    }

    private fun setUpListener() {
        binding.ivAdd.setOnClickListener {
            // Only show dialog if it's not already showing and task is loaded
            if (dialog?.isShowing != true && task != null) {
                showAddDescriptionDialog()
            }
        }
        binding.ivBack.setOnClickListener {
            // Dismiss dialog if showing before going back
            dialog?.dismiss()
            parentFragmentManager.popBackStack()
        }
    }

    private fun initialization() {
        db = TaskDatabase.getDatabase(requireContext())
        if (taskId != -1) {
            lifecycleScope.launch {
                try {
                    task = db.taskDao().getTaskById(taskId)
                    task?.let { loadedTask ->
                        binding.tvTitle.text = loadedTask.title

                        if (!loadedTask.description.isNullOrEmpty()) {
                            binding.tvDescription.text = Html.fromHtml(loadedTask.description, Html.FROM_HTML_MODE_LEGACY)
                            binding.tvDescription.visibility = View.VISIBLE
                        } else {
                            binding.tvDescription.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Show error or go back
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    private fun showAddDescriptionDialog() {
        val currentTask = task ?: return

        // Create the dialog
        dialog = Dialog(requireContext(), R.style.TransparentDialogTheme).apply {
            setContentView(R.layout.dialog_add_description)
            setCancelable(false)

            window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                val layoutParams = WindowManager.LayoutParams()
                layoutParams.copyFrom(window.attributes)
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

                val horizontalMargin = (10 * resources.displayMetrics.density).toInt()
                val verticalMargin = (10 * resources.displayMetrics.density).toInt()
                window.decorView.setPadding(
                    horizontalMargin,
                    verticalMargin,
                    horizontalMargin,
                    verticalMargin
                )

                window.attributes = layoutParams
                window.setDimAmount(1f)
            }
        }

        val etTitle = dialog?.findViewById<EditText>(R.id.edtTitle)
        val etDescription = dialog?.findViewById<RichEditor>(R.id.edtDescription)
        val btnCancel = dialog?.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialog?.findViewById<Button>(R.id.btnSave)

        if (etTitle == null || etDescription == null || btnCancel == null || btnSave == null) {
            dialog?.dismiss()
            return
        }

        // Hide back and add buttons while dialog is showing
        binding.ivBack.visibility = View.INVISIBLE
        binding.ivAdd.visibility = View.INVISIBLE

        // Initialize the RichEditor
        etDescription.setPadding(10, 10, 10, 10)
        etDescription.setPlaceholder("Insert text here...")

        // Handle null description properly with empty string
        val safeDescription = currentTask.description ?: ""

        // Set text values
        etTitle.setText(currentTask.title)
        etDescription.setHtml(safeDescription)

        // Configure rich editor buttons
        setRichEditorLayout(dialog!!, etDescription)

        btnCancel.setOnClickListener {
            dialog?.dismiss()
            binding.ivBack.visibility = View.VISIBLE
            binding.ivAdd.visibility = View.VISIBLE
        }

        btnSave.setOnClickListener {
            try {
                val newTitle = etTitle.text.toString().trim()
                val newDesc = etDescription.html

                if (newTitle.isEmpty()) {
                    etTitle.error = "Please enter title"
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    try {
                        val updatedTask = currentTask.copy(title = newTitle, description = newDesc)
                        db.taskDao().updateTask(updatedTask)

                        // Update UI
                        binding.tvTitle.text = newTitle
                        if (newDesc.isNullOrEmpty().not()) {
                            binding.tvDescription.text = Html.fromHtml(newDesc, Html.FROM_HTML_MODE_LEGACY)
                            binding.tvDescription.visibility = View.VISIBLE
                        } else {
                            binding.tvDescription.visibility = View.GONE
                        }

                        task = updatedTask
                        dialog?.dismiss()
                        binding.ivBack.visibility = View.VISIBLE
                        binding.ivAdd.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Show error message
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Handle dialog dismissal
        dialog?.setOnDismissListener {
            binding.ivBack.visibility = View.VISIBLE
            binding.ivAdd.visibility = View.VISIBLE
        }

        // Show the dialog
        try {
            dialog?.show()
            // Place cursor at the end of text after dialog is shown
            etTitle.setSelection(etTitle.text?.length ?: 0)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.ivBack.visibility = View.VISIBLE
            binding.ivAdd.visibility = View.VISIBLE
        }
    }

    private fun setRichEditorLayout(dialog: Dialog, etDescription: RichEditor) {
        dialog.findViewById<ImageView>(R.id.btnBold)?.setOnClickListener {
            etDescription.setBold()
        }

        dialog.findViewById<ImageView>(R.id.btnItalic)?.setOnClickListener {
            etDescription.setItalic()
        }

        dialog.findViewById<ImageView>(R.id.btnUnderline)?.setOnClickListener {
            etDescription.setUnderline()
        }

        dialog.findViewById<ImageView>(R.id.btnAlignCenter)?.setOnClickListener {
            etDescription.setAlignCenter()
        }
        dialog.findViewById<ImageView>(R.id.btnAlignRight)?.setOnClickListener {
            etDescription.setAlignRight()
        }
        dialog.findViewById<ImageView>(R.id.btnAlignLeft)?.setOnClickListener {
            etDescription.setAlignLeft()
        }
        dialog.findViewById<ImageView>(R.id.btnRedo)?.setOnClickListener {
            etDescription.redo()
        }
        dialog.findViewById<ImageView>(R.id.btnUndo)?.setOnClickListener {
            etDescription.undo()
        }
        dialog.findViewById<ImageView>(R.id.btnStrikeThrough)?.setOnClickListener {
            etDescription.setStrikeThrough()
        }
        dialog.findViewById<ImageView>(R.id.btnBullets)?.setOnClickListener {
            etDescription.setBullets()
        }
        dialog.findViewById<ImageView>(R.id.btnNumbers)?.setOnClickListener {
            etDescription.setNumbers()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Dismiss dialog if showing
        dialog?.dismiss()
        dialog = null
        _binding = null
    }
}