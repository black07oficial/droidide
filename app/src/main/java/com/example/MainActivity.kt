package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.IdeViewModel
import com.example.ui.screens.HomeProjectsScreen
import com.example.ui.screens.IdeWorkbench
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(dynamicColor = false) {
        val viewModel: IdeViewModel = viewModel()
        val projects by viewModel.projectsFlow.collectAsState(initial = emptyList())
        val selectedProject = viewModel.currentProject

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          if (selectedProject == null) {
            HomeProjectsScreen(
                viewModel = viewModel,
                projects = projects,
                onSelectProject = { project ->
                    viewModel.selectProject(project)
                }
            )
          } else {
            IdeWorkbench(
                viewModel = viewModel,
                onExit = {
                    viewModel.exitProject()
                }
            )
          }
        }
      }
    }
  }
}

