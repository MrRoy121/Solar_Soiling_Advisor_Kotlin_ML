package com.example.solarsoilingadvisor.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solarsoilingadvisor.ui.theme.CleanGreen
import com.example.solarsoilingadvisor.ui.theme.DirtyRed
import com.example.solarsoilingadvisor.ui.theme.DustyAmber

@Composable
fun DirtinessMeter(
    dirtinessFraction: Float,
    modifier: Modifier = Modifier,
) {
    val f = dirtinessFraction.coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val track = size.height

            // Rounded track split into three zones.
            drawLine(CleanGreen, Offset(0f, cy), Offset(w * 0.34f, cy), track, StrokeCap.Round)
            drawLine(DustyAmber, Offset(w * 0.34f, cy), Offset(w * 0.70f, cy), track)
            drawLine(DirtyRed, Offset(w * 0.70f, cy), Offset(w, cy), track, StrokeCap.Round)

            // Marker dot at the current value, with a white ring so it reads on any zone.
            val x = (w * f).coerceIn(track / 2f, w - track / 2f)
            drawCircle(Color.White, radius = track * 0.85f, center = Offset(x, cy))
            drawCircle(Color(0xFF1A1C1E), radius = track * 0.55f, center = Offset(x, cy))
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ZoneLabel("Clean", Alignment.Start)
            ZoneLabel("Dusty", Alignment.CenterHorizontally)
            ZoneLabel("Dirty", Alignment.End)
        }
    }
}

@Composable
private fun RowScope.ZoneLabel(text: String, align: Alignment.Horizontal) {
    Column(Modifier.weight(1f), horizontalAlignment = align) {
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
