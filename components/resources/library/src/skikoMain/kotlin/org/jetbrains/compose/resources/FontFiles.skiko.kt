package org.jetbrains.compose.resources

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private fun ByteArray.footprint() = "[$size:${lastOrNull()?.toInt()}]"

internal actual suspend fun ResourceReader.readFont(
    path: String,
    identity: String,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings
): Font {
    val fontBytes = read(path)
    return Font("$identity${fontBytes.footprint()}", fontBytes, weight, style, variationSettings)
}
