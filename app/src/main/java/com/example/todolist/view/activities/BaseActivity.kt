package com.example.todolist.view.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.todolist.R
import com.example.todolist.data.TaskDatabase
import com.example.todolist.view.fragments.TaskListFragment

class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)
          if (Build.VERSION.SDK_INT >= 35) {
            handleEdgeToEdge();
        }
        TaskDatabase.getDatabase(applicationContext)
        // Load TaskListFragment
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
            view.setPadding(0, systemBars.top, 0,systemBars.bottom )
            WindowInsetsCompat.CONSUMED
        }
    }

}