package com.example.solarsoilingadvisor.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solarsoilingadvisor.classifier.SoilingResult
import com.example.solarsoilingadvisor.decision.CleaningAction
import com.example.solarsoilingadvisor.decision.CleaningAdvisor
import com.example.solarsoilingadvisor.ui.widget.DirtinessMeter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { SoilingScreen() } } }
    }
}

@Composable
fun SoilingScreen(vm: SoilingViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val state by vm.uiState.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? -> bitmap?.let { scope.launch { vm.classify(it) } } }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Solar Soiling Advisor", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Arid-region cleaning recommender", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Capture solar panel photo")
        }
        Spacer(Modifier.height(16.dp))

        state.bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "captured panel",
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        when {
            state.loading -> CircularProgressIndicator()
            state.result != null -> ResultCard(state.result!!, vm)
        }

        Spacer(Modifier.height(24.dp))
        SettingsCard(vm)
    }
}

@Composable
private fun ResultCard(result: SoilingResult, vm: SoilingViewModel) {
    val state by vm.uiState.collectAsState()
    val inputs = vm.currentInputs(result)
    val decision = CleaningAdvisor.decide(inputs)
    val clean = decision.action == CleaningAction.CLEAN_NOW

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Meter is the visual centerpiece: green/amber/red zones + threshold + needle.
            DirtinessMeter(
                dirtinessFraction = result.dirtinessFraction,
                cleaningThreshold = state.cleaningThresholdScore,
            )
            Spacer(Modifier.height(16.dp))

            Text("Prediction: ${result.label}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Model confidence: ${"%.1f".format(result.confidence * 100)}%",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                if (clean) "RECOMMENDATION: CLEAN NOW" else "RECOMMENDATION: WAIT",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (clean) Color(0xFFA32D2D) else Color(0xFF173404),
            )
            Spacer(Modifier.height(8.dp))
            Text(decision.rationale, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            EconRow("Estimated soiling loss", "%.1f%%".format(inputs.soilingFraction * 100))
            EconRow("Daily revenue loss", "%.3f".format(decision.dailyRevenueLoss))
            EconRow("Projected loss over horizon", "%.2f".format(decision.projectedLoss))
            EconRow("Break-even",
                if (decision.breakevenDays.isFinite()) "%.1f days".format(decision.breakevenDays) else "n/a")
        }
    }
}

@Composable
private fun EconRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(vm: SoilingViewModel) {
    val s by vm.uiState.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Settings", fontWeight = FontWeight.SemiBold)
            NumberField("Rated power (W)", s.ratedPowerW) { vm.setRatedPower(it) }
            NumberField("Peak sun hours/day", s.peakSunHours) { vm.setPeakSunHours(it) }
            NumberField("Tariff per kWh", s.tariffPerKwh) { vm.setTariff(it) }
            NumberField("Cleaning cost", s.cleaningCost) { vm.setCleaningCost(it) }
            NumberField("Days until next check", s.daysUntilNextCheck.toDouble()) { vm.setDays(it.toInt()) }
            NumberField("Max assumed dusty loss (0-1)", s.maxAssumedLoss) { vm.setMaxAssumedLoss(it) }
            NumberField("Cleaning threshold score (0-1)", s.cleaningThresholdScore.toDouble()) {
                vm.setCleaningThresholdScore(it.toFloat())
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.toDoubleOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}