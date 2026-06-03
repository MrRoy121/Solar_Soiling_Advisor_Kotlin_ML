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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Horizontal "dirtiness" meter: green -> amber -> red, with a needle marking
 * the current value and a vertical dashed line marking the cleaning threshold.
 *
 * @param dirtinessFraction current score, 0.0..1.0  (P(Dusty) from the sigmoid model)
 * @param cleaningThreshold the score above which we recommend cleaning (e.g. 0.5)
 */
@Composable
fun DirtinessMeter(
    dirtinessFraction: Float,
    cleaningThreshold: Float = 0.5f,
    modifier: Modifier = Modifier,
) {
    val pct = (dirtinessFraction.coerceIn(0f, 1f) * 100).toInt()
    val thresholdPct = (cleaningThreshold.coerceIn(0f, 1f) * 100).toInt()

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Dirtiness score: $pct%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val w = size.width
            val h = size.height
            val barTop = h * 0.30f
            val barHeight = h * 0.40f

            // 3 zones: 0-30% green, 30-70% amber, 70-100% red
            drawRect(Color(0xFF63991F), Offset(0f, barTop),                Size(w * 0.30f, barHeight))
            drawRect(Color(0xFFBA7517), Offset(w * 0.30f, barTop),         Size(w * 0.40f, barHeight))
            drawRect(Color(0xFFA32D2D), Offset(w * 0.70f, barTop),         Size(w * 0.30f, barHeight))

            // Threshold marker (dashed vertical line)
            val thresholdX = w * cleaningThreshold
            val dashLen = 4f
            val gapLen = 4f
            var y = 0f
            while (y < h) {
                drawLine(
                    color = Color(0xFF2C2C2A),
                    start = Offset(thresholdX, y),
                    end = Offset(thresholdX, (y + dashLen).coerceAtMost(h)),
                    strokeWidth = 2f,
                )
                y += dashLen + gapLen
            }

            // Needle: triangle indicating current position
            val needleX = w * dirtinessFraction.coerceIn(0f, 1f)
            val needleColor = when {
                dirtinessFraction < 0.30f -> Color(0xFF173404)
                dirtinessFraction < 0.70f -> Color(0xFF633806)
                else                       -> Color(0xFF501313)
            }
            // a small downward triangle pointing at the bar
            drawLine(needleColor, Offset(needleX, 0f), Offset(needleX, h), strokeWidth = 3f)
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Threshold: $thresholdPct%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("100%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}