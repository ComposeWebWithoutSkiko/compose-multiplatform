package org.jetbrains.compose.resources

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.vector.xmldom.Element

internal actual suspend fun ResourceReader.readBitmap(
    path: String,
    resourceDensity: Int,
    targetDensity: Int
): ImageBitmap = read(path).toImageBitmap(resourceDensity, targetDensity)

internal actual suspend fun ResourceReader.readXmlElement(path: String): Element =
    read(path).toXmlElement()

internal actual suspend fun ResourceReader.readSvgPainter(path: String, density: Density): Painter =
    read(path).toSvgElement().toSvgPainter(density)
