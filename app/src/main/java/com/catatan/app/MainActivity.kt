package com.catatan.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
                AppRoot(repo = repo, activity = this, onRootExit = { finish() })
            }
        }
    }
}

@Composable
private fun AppRoot(repo: Repo, activity: Context, onRootExit: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(repo, context))
    val state = viewModel.state

    // Activity dibutuhkan Credential Manager untuk pemilih akun Google.
    DisposableEffect(activity) {
        state.activity = activity
        onDispose { state.activity = null }
    }

    // Sinkronisasi otomatis tiap kali aplikasi kembali ke foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.onAppResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
