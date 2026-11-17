package com.lucafilippi.panelpath

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

object PanelPalette {
    val background = Color(0xFF0D0F14)
    val surface = Color(0xFF1C1F27)
    val raised = Color(0xFF252832)
    val accent = Color(0xFFF05258)
    val muted = Color(0xFFB1B4BE)
    val track = Color(0xFF424550)
    val border = Color(0xFF343844)
}

@Composable fun PanelHeader(title: String, subtitle: String, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = PanelPalette.muted)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(PanelPalette.raised)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable fun CoverArt(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF493044), PanelPalette.raised, Color(0xFF1D2736)))), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = "$title cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = PanelPalette.accent, fontWeight = FontWeight.Black)
    }
}

@Composable fun PanelProgress(value: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { value.coerceIn(0f, 1f) }, modifier = modifier.height(7.dp).clip(RoundedCornerShape(20.dp)),
        color = PanelPalette.accent, trackColor = PanelPalette.track)
}

@Composable fun SectionHeading(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PanelPalette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
