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

// 定义屏幕路由 (可以放在单独的文件中)
sealed class Screen(val route: String) {
    object Main : Screen("main_screen")
    object Settings : Screen("settings_screen")
    object ABOUT : Screen("about")
    object ManageFaces : Screen("manage_faces_screen_route")
}

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var showPermissionDeniedMessage by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showPermissionDeniedMessage = false
                // setupUi() // setupUi 现在由 NavHost 控制
            } else {
                showPermissionDeniedMessage = true
                Log.e("MainActivity", "Camera permission denied.")
                // setupUi() // setupUi 现在由 NavHost 控制
            }
            // 触发 recomposition 来更新 NavHost 的内容
            setContent { AppNavigation() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "NCNN initialization coroutine started.")
            val isInitialized = ncnnController.setupNcnn(assets)
            if (isInitialized) {
                Log.d("MainActivity", "NCNN initialized successfully!")
            } else {
                Log.e("MainActivity", "Failed to initialize NCNN.")
            }
            Log.d("MainActivity", "NCNN initialization coroutine finished.")
        }

        checkCameraPermissionAndSetup()
        VectorSearchEngine.loadFromFile(this)
    }

    private fun checkCameraPermissionAndSetup() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                showPermissionDeniedMessage = false
                setContent { AppNavigation() }
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // 将 UI 设置移到一个单独的 Composable 函数中，用于导航
    @SuppressLint("ViewModelConstructorInComposable")
    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        StellarEyesTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val settingsViewModel = SettingsViewModel(application)
                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    composable(Screen.Main.route) {
                        if (showPermissionDeniedMessage) {
                            PermissionDeniedScreen(
                                onRequestPermission = {
                                    // 再次请求权限或引导用户到设置
                                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            )
                        } else if (ContextCompat.checkSelfPermission(
                                this@MainActivity, // 注意这里的 Context
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            StellarEyesAppScreen(navController = navController,viewModel = settingsViewModel)
                        } else {
                            // 初始请求权限时的占位符，或者在权限被拒绝后，
                            // 但 showPermissionDeniedMessage 还未更新时显示
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("正在检查相机权限...")
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
                        ManageFacesScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(Screen.ABOUT.route) {
                        AboutScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        // ncnnController.release()
    }
}
