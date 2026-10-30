@file:OptIn(InternalComposeUiApi::class)

package org.jetbrains.compose.resources

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.webcanvas.text.loadFont
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal actual suspend fun ResourceReader.readFont(
    path: String,
    identity: String,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings
): Font = loadInBrowser(path) {
    val blob = readBlob(path)
    suspendCancellableCoroutine { continuation ->
        loadFont("$identity[${blob.size}]", blob, weight, style, variationSettings) {
            continuation.resume(it)
        }
    }
}
