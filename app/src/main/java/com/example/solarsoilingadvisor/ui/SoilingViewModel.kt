package com.example.solarsoilingadvisor.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.example.solarsoilingadvisor.classifier.SoilingClassifier
import com.example.solarsoilingadvisor.classifier.SoilingResult
import com.example.solarsoilingadvisor.decision.CleaningInputs
import com.example.solarsoilingadvisor.decision.SoilingEstimator
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
    // Gulf-region defaults
    val ratedPowerW: Double = 5000.0,
    val peakSunHours: Double = 6.0,
    val tariffPerKwh: Double = 0.18,
    val cleaningCost: Double = 10.0,
    val daysUntilNextCheck: Int = 7,
    val maxAssumedLoss: Double = SoilingEstimator.DEFAULT_DUSTY_FRACTION,
    val cleaningThresholdScore: Float = 0.5f,
)

class SoilingViewModel(app: Application) : AndroidViewModel(app) {

    private val classifier: SoilingClassifier by lazy { SoilingClassifier(getApplication()) }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    suspend fun classify(bitmap: Bitmap) {
        _uiState.update { it.copy(loading = true, bitmap = bitmap, result = null) }
        val result = withContext(Dispatchers.Default) { classifier.classify(bitmap) }
        _uiState.update { it.copy(loading = false, result = result) }
    }

    /**
     * Inputs for the decision layer.
     * We scale the assumed max loss by the continuous dirtiness score so the
     * recommendation tracks the meter smoothly instead of jumping at a hard
     * Clean/Dusty boundary.
     */
    fun currentInputs(result: SoilingResult): CleaningInputs {
        val s = _uiState.value
        val soilingFraction = (result.dirtinessFraction * s.maxAssumedLoss).coerceIn(0.0, 1.0)
        return CleaningInputs(
            ratedPowerW = s.ratedPowerW,
            peakSunHours = s.peakSunHours,
            tariffPerKwh = s.tariffPerKwh,
            soilingFraction = soilingFraction,
            daysUntilNextCheck = s.daysUntilNextCheck,
            cleaningCost = s.cleaningCost,
        )
    }

    fun setRatedPower(v: Double) = _uiState.update { it.copy(ratedPowerW = v) }
    fun setPeakSunHours(v: Double) = _uiState.update { it.copy(peakSunHours = v) }
    fun setTariff(v: Double) = _uiState.update { it.copy(tariffPerKwh = v) }
    fun setCleaningCost(v: Double) = _uiState.update { it.copy(cleaningCost = v) }
    fun setDays(v: Int) = _uiState.update { it.copy(daysUntilNextCheck = v) }
    fun setMaxAssumedLoss(v: Double) = _uiState.update { it.copy(maxAssumedLoss = v) }
    fun setCleaningThresholdScore(v: Float) = _uiState.update {
        it.copy(cleaningThresholdScore = v.coerceIn(0f, 1f))
    }

    override fun onCleared() {
        classifier.close()
        super.onCleared()
    }
}

// `Double.coerceIn` for the `Double..Double` range we use above.
private fun Double.coerceIn(min: Double, max: Double): Double =
    if (this < min) min else if (this > max) max else this