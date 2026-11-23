package com.catatan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catatan.app.data.Repo
import com.catatan.app.ui.AppViewModel
import com.catatan.app.ui.MainScaffold
import com.catatan.app.ui.theme.CatatanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = (application as CatatanApp).repo

        setContent {
            val store by repo.store.collectAsState()
            val settings = store.settings
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CatatanTheme(accentHex = settings.accent, darkTheme = dark) {
                AppRoot(repo = repo, onRootExit = { finish() })
            }
        }
    }
}

@Composable
private fun AppRoot(repo: Repo, onRootExit: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(repo, context))
    val state = viewModel.state

    // Izin pemasangan aplikasi (update dari GitHub Releases)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        state.onInstallPermissionResult()
    }

    LaunchedEffect(Unit) {
        state.permissionLauncher = permissionLauncher
        state.autoCheckUpdates()
    }

    MainScaffold(state = state, onRootExit = onRootExit)
}
