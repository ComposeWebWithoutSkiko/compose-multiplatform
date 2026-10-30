@file:OptIn(ExperimentalWasmJsInterop::class, InternalComposeUiApi::class)

package org.jetbrains.compose.resources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.webcanvas.dom.toBlob
import androidx.compose.ui.webcanvas.svgImageBitmapOf
import org.w3c.files.Blob
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.asJsException
import kotlin.js.js
import kotlin.js.unsafeCast
import kotlin.math.roundToInt

private const val SvgType = "image/svg+xml"

internal actual class SvgElement(val blob: Blob)

internal actual fun ByteArray.toSvgElement(): SvgElement =
    SvgElement(toBlob(SvgType).unsafeCast<Blob>())

internal actual fun SvgElement.toSvgPainter(density: Density): Painter {
    val painter = SvgPainter(density)
    svgImageElementOf(blob)
        .then { painter.show(it); null }
        .catch { warn("Failed to load an SVG image: ${it.asJsException().message}"); null }
    return painter
}

internal actual suspend fun ResourceReader.readSvgPainter(path: String, density: Density): Painter =
    loadInBrowser(path) {
        SvgPainter(density).apply { show(svgImageElementOf(readBlob(path)).await()) }
    }

/**
 * Draws an SVG document through an `<img>`, which the browser renders at the size it's drawn at.
 *
 * Color filters are left to [Painter.draw], which applies them to a layer of the size of the
 * drawing: the color filter of a draw applies to an image flattened to its natural size.
 */
private class SvgPainter(private val density: Density) : Painter() {
    private var image: ImageBitmap? by mutableStateOf(null)
    private var alpha = 1f

    override var intrinsicSize: Size by mutableStateOf(Size.Unspecified)
        private set

    /** Shows the document of [element], an `<img>` that has loaded it. */
    fun show(element: JsAny) {
        val image = svgImageBitmapOf(element)
        this.image = image
        intrinsicSize = Size(image.width * density.density, image.height * density.density)
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun DrawScope.onDraw() {
        val image = image ?: return
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = alpha,
        )
    }
}

/** An `<img>` shows a document only of the SVG type, whatever type the file was served with. */
private fun svgImageElementOf(blob: Blob): Promise<JsAny> = imageElementOf(typed(blob, SvgType))

/** A `Blob` of the bytes of [blob], which it shares, of the given [type]. */
// language=js
private fun typed(blob: Blob, type: String): Blob = js("blob.slice(0, blob.size, type)")
