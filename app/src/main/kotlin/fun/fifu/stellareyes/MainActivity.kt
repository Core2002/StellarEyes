package `fun`.fifu.stellareyes

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `fun`.fifu.stellareyes.data.VectorSearchEngine
import `fun`.fifu.stellareyes.ui.camera.StellarEyesAppScreen
import `fun`.fifu.stellareyes.ui.managefaces.ManageFacesScreen
import `fun`.fifu.stellareyes.ui.permissions.PermissionDeniedScreen
import `fun`.fifu.stellareyes.ui.settings.AboutScreen
import `fun`.fifu.stellareyes.ui.settings.SettingsScreen
import `fun`.fifu.stellareyes.ui.settings.SettingsViewModel
import `fun`.fifu.stellareyes.ui.theme.StellarEyesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import `fun`.fifu.stellareyes.NcnnController as ncnnController

sealed class Screen(val route: String) {
    object Main : Screen("main_screen")
    object Settings : Screen("settings_screen")
    object ABOUT : Screen("about")
    object ManageFaces : Screen("manage_faces_screen_route")
}

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var showPermissionDeniedMessage by mutableStateOf(false)

    // 整个 Activity 共用的 SettingsViewModel
    private val settingsViewModel by lazy {
        SettingsViewModel(application)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            showPermissionDeniedMessage = !isGranted
            Log.e("MainActivity", if (isGranted) "Camera permission granted." else "Camera permission denied.")
            setContent { AppNavigation() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // NCNN 初始化
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = ncnnController.setupNcnn(assets)
            Log.d("MainActivity", "NCNN init ${if (ok) "success" else "failed"}")
        }

        checkCameraPermissionAndSetup()
        VectorSearchEngine.loadFromFile(this)
    }

    private fun checkCameraPermissionAndSetup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showPermissionDeniedMessage = false
            setContent { AppNavigation() }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @SuppressLint("ViewModelConstructorInComposable")
    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()
        StellarEyesTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    composable(Screen.Main.route) {
                        when {
                            showPermissionDeniedMessage -> PermissionDeniedScreen { requestPermissionLauncher.launch(Manifest.permission.CAMERA) }
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                                StellarEyesAppScreen(navController = navController, viewModel = settingsViewModel)
                            else ->
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("正在检查相机权限…")
                                }
                        }
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToAbout = { navController.navigate(Screen.ABOUT.route) },
                            viewModel = settingsViewModel
                        )
                    }
                    composable(Screen.ManageFaces.route) {
                        ManageFacesScreen(onNavigateBack = { navController.popBackStack() })
                    }
                    composable(Screen.ABOUT.route) {
                        AboutScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}