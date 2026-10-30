@file:JvmName("ImageDecoders_skikoKt")

package org.jetbrains.compose.resources

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import kotlin.jvm.JvmName

/**
 * Decodes a byte array of an SVG file to a compose Painter.
 *
 * On the web the browser loads the file asynchronously: until then the painter has no intrinsic
 * size and draws nothing, and if the file can't be loaded it stays that way, with a warning in the
 * console.
 *
 * @param density density to apply during converting the source units to the [Painter] units.
 *
 * @return The converted Painter.
 */
fun ByteArray.decodeToSvgPainter(density: Density): Painter {
    return this.toSvgElement().toSvgPainter(density)
}