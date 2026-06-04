package com.example.solarsoilingadvisor.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.example.solarsoilingadvisor.classifier.SoilingClassifier
import com.example.solarsoilingadvisor.classifier.SoilingResult
import com.example.solarsoilingadvisor.data.SetupData
import com.example.solarsoilingadvisor.data.SetupStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = false,
    val bitmap: Bitmap? = null,
    val result: SoilingResult? = null,
    val onboarded: Boolean = false,
    val setup: SetupData = SetupData(),
)

class SoilingViewModel(app: Application) : AndroidViewModel(app) {

    private val classifier: SoilingClassifier by lazy { SoilingClassifier(getApplication()) }
    private val store = SetupStore(app)

    private val _uiState = MutableStateFlow(
        UiState(onboarded = store.isOnboarded, setup = store.load())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    suspend fun classify(bitmap: Bitmap) {
        _uiState.update { it.copy(loading = true, bitmap = bitmap, result = null) }
        val result = withContext(Dispatchers.Default) { classifier.classify(bitmap) }
        _uiState.update { it.copy(loading = false, result = result) }
    }

    /** Persist the user's setup and mark onboarding complete. */
    fun saveSetup(data: SetupData) {
        store.save(data)
        _uiState.update { it.copy(setup = data, onboarded = true) }
    }

    override fun onCleared() {
        classifier.close()
        super.onCleared()
    }
}
