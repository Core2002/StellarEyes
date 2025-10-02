package `fun`.fifu.stellareyes.ui.settings

import android.annotation.SuppressLint
import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application){
    @SuppressLint("StaticFieldLeak")
    val appContext = getApplication<Application>().applicationContext

    val isContinuousInferenceEnabled: StateFlow<Boolean> = appContext.dataStore.data
        .map { preferences ->
            preferences[CONTINUOUS_INFERENCE_KEY] ?: false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val isDarkThemeEnabled: StateFlow<Boolean> = appContext.dataStore.data
        .map { preferences ->
            preferences[DARK_THEME_KEY] ?: false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val isAutoRecordEnabled: StateFlow<Boolean> = appContext.dataStore.data
        .map { preferences ->
            preferences[AUTO_RECORD_KEY] ?: false // 假设默认值为 false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false // 与 map 中的默认值一致
        )

    fun setAutoRecord(enabled: Boolean) {
        viewModelScope.launch {
            appContext.dataStore.edit { settings ->
                settings[AUTO_RECORD_KEY] = enabled
            }
        }
    }

    val isProcessAllFacesEnabled: StateFlow<Boolean> = appContext.dataStore.data
        .map { preferences ->
            preferences[PROCESS_ALL_FACES_KEY] ?: true
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setProcessAllFaces(enabled: Boolean) {
        viewModelScope.launch {
            appContext.dataStore.edit { settings ->
                settings[PROCESS_ALL_FACES_KEY] = enabled
            }
        }
    }

    fun setContinuousInference(enabled: Boolean) {
        viewModelScope.launch {
            appContext.dataStore.edit { settings ->
                settings[CONTINUOUS_INFERENCE_KEY] = enabled
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            appContext.dataStore.edit { settings ->
                val currentSetting = settings[DARK_THEME_KEY] ?: false
                settings[DARK_THEME_KEY] = !currentSetting
            }
        }
    }
}