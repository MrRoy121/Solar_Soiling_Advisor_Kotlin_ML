package com.example.solarsoilingadvisor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solarsoilingadvisor.classifier.SoilingResult
import com.example.solarsoilingadvisor.data.SetupData
import com.example.solarsoilingadvisor.ui.theme.*
import com.example.solarsoilingadvisor.ui.widget.DirtinessMeter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SolarSoilingTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: SoilingViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    // Show the setup form on first launch, or when the user chooses to edit it.
    var editingSetup by rememberSaveable { mutableStateOf(false) }
    val showSetup = !state.onboarded || editingSetup

    if (showSetup) {
        SetupScreen(
            initial = state.setup,
            isOnboarding = !state.onboarded,
            onSave = { vm.saveSetup(it); editingSetup = false },
            onCancel = if (state.onboarded) { { editingSetup = false } } else null,
        )
    } else {
        MainScreen(vm = vm, onEditSetup = { editingSetup = true })
    }
}

/** Plain-language "how dirty" buckets, derived from the model's dirtiness score. */
private enum class DirtLevel(
    val statusLabel: String,
    val verdictTitle: String,
    val verdictBody: String,
    val color: Color,
    val container: Color,
) {
    CLEAN(
        "Looks clean", "No cleaning needed",
        "Your panels look clean — nothing to do right now.",
        CleanGreen, CleanGreenContainer,
    ),
    DUSTY(
        "Getting dusty", "Worth a clean soon",
        "Dust is starting to build up. A clean will help your panels work their best.",
        DustyAmber, DustyAmberContainer,
    ),
    DIRTY(
        "Very dirty", "Time to clean",
        "Your panels are dirty enough to lose noticeable output. Give them a clean to restore performance.",
        DirtyRed, DirtyRedContainer,
    ),
}

private fun levelFor(f: Float) = when {
    f < 0.30f -> DirtLevel.CLEAN
    f < 0.70f -> DirtLevel.DUSTY
    else -> DirtLevel.DIRTY
}

// --- Main screen -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: SoilingViewModel, onEditSetup: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? -> bitmap?.let { scope.launch { vm.classify(it) } } }

    // --- Camera permission handling ---
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            val activity = context as? Activity
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            permanentlyDenied = !canAskAgain
            showPermissionDialog = true
        }
    }

    fun startCapture() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) cameraLauncher.launch(null)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Solar Soiling Advisor", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onEditSetup) {
                        Text("Setup", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SystemHeader(state.setup)
            Spacer(Modifier.height(16.dp))

            state.bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Photo of your solar panel",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(16.dp))
            } ?: run {
                EmptyState()
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { startCapture() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (state.bitmap == null) "Take a photo" else "Take another photo",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(20.dp))

            when {
                state.loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Checking the panel…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.result != null -> ResultSection(state.result!!)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPermissionDialog) {
        CameraPermissionDialog(
            permanentlyDenied = permanentlyDenied,
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                if (permanentlyDenied) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
        )
    }
}

@Composable
private fun SystemHeader(setup: SetupData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(setup.systemName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val detail = buildString {
            append(if (setup.panelCount == 1) "1 panel" else "${setup.panelCount} panels")
            if (setup.location.isNotBlank()) append(" · ${setup.location}")
        }
        Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("☀", fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Point your camera at a solar panel in daylight and take a clear, head-on photo.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ResultSection(result: SoilingResult) {
    val level = levelFor(result.dirtinessFraction)

    // How dirty does it look?
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp)) {
            StatusPill(level)
            Spacer(Modifier.height(16.dp))
            DirtinessMeter(dirtinessFraction = result.dirtinessFraction)
        }
    }
    Spacer(Modifier.height(14.dp))

    // The verdict.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = level.container),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(level.verdictTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = level.color)
            Spacer(Modifier.height(8.dp))
            Text(level.verdictBody, fontSize = 14.sp, color = Color(0xFF1A1C1E))
        }
    }
    Spacer(Modifier.height(10.dp))

    // Technical readout for the curious.
    var showDetails by remember { mutableStateOf(false) }
    TextButton(onClick = { showDetails = !showDetails }) {
        Text(if (showDetails) "Hide technical details" else "Show technical details")
    }
    AnimatedVisibility(visible = showDetails) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            DetailRow("Model verdict", result.label)
            DetailRow("Model confidence", "%.0f%%".format(result.confidence * 100))
            DetailRow("Dirtiness score", "%.0f%%".format(result.dirtinessFraction * 100))
        }
    }
}

@Composable
private fun StatusPill(level: DirtLevel) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(level.container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(level.statusLabel, color = level.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun CameraPermissionDialog(
    permanentlyDenied: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera access needed") },
        text = {
            Text(
                if (permanentlyDenied)
                    "This app needs the camera to photograph your panel. You've turned it off, " +
                        "so please enable Camera in Settings to continue."
                else
                    "We use the camera only to take a photo of your solar panel — nothing is uploaded. " +
                        "Please allow camera access to continue."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (permanentlyDenied) "Open settings" else "Allow")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

// --- Setup / onboarding screen -----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    initial: SetupData,
    isOnboarding: Boolean,
    onSave: (SetupData) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial.systemName) }
    var panels by rememberSaveable { mutableStateOf(initial.panelCount.toString()) }
    var location by rememberSaveable { mutableStateOf(initial.location) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (isOnboarding) "Welcome" else "Edit setup", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (onCancel != null) TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            if (isOnboarding) {
                Text("☀", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Let's set up your solar system",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tell us a little about your panels. Then just snap a photo whenever you " +
                        "want to know if they need cleaning.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Give your system a name") },
                placeholder = { Text("e.g. Rooftop array") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = panels,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) panels = it },
                label = { Text("How many panels?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (optional)") },
                placeholder = { Text("e.g. Riyadh rooftop") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    onSave(
                        SetupData(
                            systemName = name.ifBlank { "My Solar System" }.trim(),
                            panelCount = panels.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            location = location.trim(),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (isOnboarding) "Get started" else "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
