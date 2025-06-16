package `fun`.fifu.stellareyes.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// 为 DataStore 定义一个 Context 扩展属性
val Context.dataStore by preferencesDataStore(name = "settings")

// 定义 DataStore 中的键
val CONTINUOUS_INFERENCE_KEY = booleanPreferencesKey("continuous_inference_enabled")
val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
val AUTO_RECORD_KEY = booleanPreferencesKey("auto_record_enabled")
val PROCESS_ALL_FACES_KEY = booleanPreferencesKey("process_all_faces_enabled")
val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

