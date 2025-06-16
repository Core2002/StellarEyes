package `fun`.fifu.stellareyes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel
) {
    val continuousInferenceActive by viewModel.isContinuousInferenceEnabled.collectAsState()
    val useDarkTheme by viewModel.isDarkThemeEnabled.collectAsState()
    val autoRecordActive by viewModel.isAutoRecordEnabled.collectAsState()
    val processAllFacesActive by viewModel.isProcessAllFacesEnabled.collectAsState() // <--- 获取状态

    val settingsItems = listOf(
        SettingToggleItem(
            title = "持续推理",
            description = "启用后，应用将持续进行图像分析和推理。",
            isChecked = continuousInferenceActive,
            onCheckedChange = { newValue ->
                viewModel.setContinuousInference(newValue)
            }
        ),
        SettingToggleItem(
            title = "推理每帧所有的人脸",
            description = "启用后，将处理检测到的所有人脸；禁用则只处理最大的人脸。",
            isChecked = processAllFacesActive,
            onCheckedChange = { newValue ->
                viewModel.setProcessAllFaces(newValue)
            }
        ),
        SettingToggleItem(
            title = "自动录入",
            description = "启用后，符合条件的结果将自动保存或记录。",
            isChecked = autoRecordActive,
            onCheckedChange = { newValue ->
                viewModel.setAutoRecord(newValue)
            }
        ),
        SettingToggleItem(
            title = "深色主题",
            description = "启用或禁用应用内的深色主题",
            isChecked = useDarkTheme,
            onCheckedChange = { newValue ->
                viewModel.toggleDarkTheme()
            }
        ),
        SettingActionItem(
            title = "关于",
            description = "查看应用版本和信息",
            onClick = {
                onNavigateToAbout()
                println("关于被点击了，准备导航")
            }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            items(settingsItems) { item ->
                when (item) {
                    is SettingToggleItem -> ToggleableSettingRow(item = item)
                    is SettingActionItem -> ActionSettingRow(item = item)
                }
                HorizontalDivider()
            }
        }
    }
}

// 用于表示不同类型的设置项的 Sealed Class
sealed class SettingListItem {
    abstract val title: String
    abstract val description: String?
}

data class SettingToggleItem(
    override val title: String,
    override val description: String? = null,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
) : SettingListItem()

data class SettingActionItem(
    override val title: String,
    override val description: String? = null,
    val onClick: () -> Unit
) : SettingListItem()


@Composable
fun ToggleableSettingRow(item: SettingToggleItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onCheckedChange(!item.isChecked) } // 点击整行也能切换
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            item.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = item.isChecked,
            onCheckedChange = item.onCheckedChange
        )
    }
}

@Composable
fun ActionSettingRow(item: SettingActionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            item.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
        // 你可以根据需要在这里添加一个箭头或其他指示图标
    }
}
