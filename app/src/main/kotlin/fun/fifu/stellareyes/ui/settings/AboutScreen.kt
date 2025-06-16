package `fun`.fifu.stellareyes.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `fun`.fifu.stellareyes.R

// 定义一个简单的数据类来表示每个库
data class LibraryItem(val name: String, val description: String? = null, val url: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    // 定义开源库列表
    val libraries = remember {
        listOf(
            // Kotlin (作为基础语言，通常会提及)
            LibraryItem(
                name = "Kotlin",
                description = "The programming language used for development.",
                url = "https://kotlinlang.org/"
            ),

            // Jetpack Compose (核心UI库)
            LibraryItem(
                name = "Jetpack Compose",
                description = "Android's modern toolkit for building native UI.",
                url = "https://developer.android.com/jetpack/compose"
            ),
            LibraryItem(
                name = "Material Design 3 Components",
                description = "Material 3 components for Jetpack Compose.",
                url = "https://developer.android.com/jetpack/compose/designsystems/material3"
            ),
            LibraryItem(
                name = "Compose UI Tooling & Preview",
                description = "Tools for previewing and developing Compose UIs.",
                url = "https://developer.android.com/jetpack/compose/tooling"
            ),
            LibraryItem(
                name = "Material Icons for Compose",
                description = "Pre-built Material Design icons for Compose.",
                url = "https://developer.android.com/jetpack/compose/resources#icons"
            ),

            // AndroidX Core & AppCompat (基础支持库)
            LibraryItem(
                name = "AndroidX Core KTX",
                description = "Kotlin extensions for AndroidX Core libraries.",
                url = "https://developer.android.com/kotlin/ktx#core"
            ),
            LibraryItem(
                name = "AndroidX AppCompat",
                description = "Provides backward-compatible versions of Android framework APIs.",
                url = "https://developer.android.com/jetpack/androidx/releases/appcompat"
            ),
            LibraryItem( // libs.material - 通常指旧的 com.google.android.material:material
                name = "Material Components for Android (XML)",
                description = "Material Design components for XML-based layouts.",
                url = "https://github.com/material-components/material-components-android"
            ),


            // Data and Serialization
            LibraryItem(
                name = "DataStore Preferences",
                description = "Jetpack library for storing key-value pairs.",
                url = "https://developer.android.com/topic/libraries/architecture/datastore"
            ),
            LibraryItem(
                name = "Kotlinx Serialization (JSON)",
                description = "Kotlin multiplatform / multi-format serialization library.",
                url = "https://github.com/Kotlin/kotlinx.serialization"
            ),

            // Image Loading
            LibraryItem(
                name = "Coil (Coil-Compose)",
                description = "Image loading library for Android backed by Kotlin Coroutines.",
                url = "https://github.com/coil-kt/coil"
            ),

            // ViewModel and LiveData (Lifecycle Components)
            LibraryItem(
                name = "Lifecycle ViewModel Compose",
                description = "Integration for ViewModel with Jetpack Compose.",
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
            ),
            LibraryItem(
                name = "Lifecycle LiveData Runtime",
                description = "Integration for LiveData with Jetpack Compose.",
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
            ),

            // Navigation
            LibraryItem(
                name = "Navigation Compose",
                description = "Jetpack Navigation library for Compose.",
                url = "https://developer.android.com/jetpack/compose/navigation"
            ),

            // CameraX
            LibraryItem(
                name = "CameraX (Core, Camera2, Lifecycle, View)",
                description = "Jetpack library for camera app development.",
                url = "https://developer.android.com/training/camerax"
            ),

            // ML Kit
            LibraryItem(
                name = "ML Kit Face Detection",
                description = "Google's on-device ML SDK for face detection.",
                url = "https://developers.google.com/ml-kit/vision/face-detection"
            ),
            LibraryItem( // play-services-mlkit-face-detection
                name = "Google Play Services for ML Kit Face Detection",
                description = "Google Play Services dependency for ML Kit Face Detection.",
                url = "https://developers.google.com/ml-kit/terms" // General ML Kit terms
            ),

            // NCNN
            LibraryItem(
                name = "NCNN",
                description = "ncnn is a high-performance neural network inference computing framework optimized for mobile platforms.",
                url = "https://github.com/Tencent/ncnn"
            ),

            // Firebase
            LibraryItem(
                name = "Firebase Firestore KTX",
                description = "Kotlin extensions for Cloud Firestore.",
                url = "https://firebase.google.com/docs/firestore"
            ),

            // Other Utilities
            LibraryItem(
                name = "Accompanist Permissions",
                description = "Jetpack Compose utilities for runtime permissions.",
                url = "https://google.github.io/accompanist/permissions/"
            ),
            LibraryItem( // libs.androidx.glance.appwidget
                name = "Glance AppWidget",
                description = "A framework for building app widgets with Jetpack Compose-like APIs.",
                url = "https://developer.android.com/jetpack/androidx/releases/glance"
            ),
            LibraryItem( // libs.capturable
                name = "Capturable (dev.shreyaspatil:capturable)",
                description = "A Jetpack Compose utility to capture Composable content as Bitmap.",
                url = "https://github.com/PatilShreyas/Capturable" // Assuming this is the lib
            ),
            LibraryItem( // libs.litert
                name = "Google AI Edge Lite Runtime",
                description = "Runtime for executing TensorFlow Lite models on edge devices.",
                url = "https://developers.google.com/ml-kit/tflite-support" // Related, actual lib might be harder to link directly
            ),
            LibraryItem( // libs.guava
                name = "Google Guava",
                description = "Google core libraries for Java/Android.",
                url = "https://github.com/google/guava"
            )

            // Test libraries are usually not included in "About" sections,
            // but if you wish, you can add JUnit and Espresso.
        )
    }

    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.about_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back_content_description)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // 应用 Scaffold 的内边距
                .padding(horizontal = 16.dp), // 左右两侧的额外边距
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 应用图标
            Image(
                painter = painterResource(id = R.drawable.app_display_icon),
                contentDescription = stringResource(id = R.string.app_icon_content_description),
                modifier = Modifier
                    .size(100.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Text(
                text = stringResource(id = R.string.app_name_full),
                style = MaterialTheme.typography.headlineSmall, // 调整标题大小
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${stringResource(id = R.string.version_prefix)} 1.0.0",
                style = MaterialTheme.typography.bodyMedium, // 调整版本号字体
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    id = R.string.copyright_notice,
                    "2024",
                    "Core2002"
                ),
                style = MaterialTheme.typography.labelSmall, // 使用更小的字体
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.app_description_long),
                style = MaterialTheme.typography.bodySmall, // 调整描述字体
                textAlign = TextAlign.Justify,
                modifier = Modifier.padding(horizontal = 8.dp) // 给描述文本一些左右内边距
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 鸣谢/开源库 标题
            Text(
                text = stringResource(id = R.string.acknowledgements_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 使用 LazyColumn 展示库列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 让列表占据剩余空间
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp) // 给列表内容一些垂直内边距
            ) {
                items(libraries) { library ->
                    LibraryRow(library = library, onClick = {
                        library.url?.let { uriHandler.openUri(it) }
                    })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp)) // 在列表项之间添加分隔线
                }
            }

            Button(
                onClick = {
                    val githubUrl = "https://github.com/Core2002/StellarEyes"
                    uriHandler.openUri(githubUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp) // 给按钮一些垂直外边距
            ) {
                Text(stringResource(id = R.string.view_source_code_button))
            }
        }
    }
}

@Composable
fun LibraryRow(library: LibraryItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(library.name, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            library.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = library.url != null, onClick = onClick)
    )
}

