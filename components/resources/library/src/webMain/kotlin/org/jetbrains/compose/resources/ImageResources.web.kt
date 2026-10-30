@file:OptIn(ExperimentalWasmJsInterop::class, InternalComposeUiApi::class)

package org.jetbrains.compose.resources

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap as decodeToComposeImageBitmap
import androidx.compose.ui.webcanvas.composeImageBitmapOf
import androidx.compose.ui.webcanvas.dom.decodeToJsString
import org.jetbrains.compose.resources.vector.xmldom.Element
import org.jetbrains.compose.resources.vector.xmldom.ElementImpl
import org.jetbrains.compose.resources.vector.xmldom.MalformedXMLException
import org.w3c.dom.Document
import org.w3c.files.Blob
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.js

// Only the public decoding of bytes gets here, with equal densities: see readBitmap for resources.
internal actual fun ByteArray.toImageBitmap(resourceDensity: Int, targetDensity: Int): ImageBitmap =
    decodeToComposeImageBitmap()

internal actual fun ByteArray.toXmlElement(): Element = xmlElementOf(decodeToJsString())

internal actual suspend fun ResourceReader.readBitmap(
    path: String,
    resourceDensity: Int,
    targetDensity: Int
): ImageBitmap = loadInBrowser(path) {
    val blob = readBlob(path)
    //https://youtrack.jetbrains.com/issue/CMP-5657
    //android only downscales drawables. If there is only low dpi resource then use it as is (not upscale)
    //we need a consistent behavior on all platforms
    val decoded = if (resourceDensity > targetDensity) {
        val image = imageElementOf(blob).await()
        decodeResizedImage(blob, image, targetDensity, resourceDensity).await()
    } else {
        decodeImage(blob).await()
    }
    composeImageBitmapOf(decoded)
}

internal actual suspend fun ResourceReader.readXmlElement(path: String): Element =
    loadInBrowser(path) { xmlElementOf(textOf(readBlob(path)).await()) }

private fun xmlElementOf(text: JsString): Element {
    val domElement = parseXml(text).documentElement
        ?: throw MalformedXMLException("missing documentElement")
    return ElementImpl(domElement)
}

// language=js
private fun textOf(blob: Blob): Promise<JsString> = js("blob.text()")

// language=js
private fun parseXml(text: JsString): Document =
    js("new DOMParser().parseFromString(text, 'application/xml')")

// language=js
private fun decodeImage(blob: Blob): Promise<JsAny> = js("createImageBitmap(blob)")

/** Decodes [blob] right into the size of [image] scaled by [targetDensity] / [resourceDensity]. */
// language=js
private fun decodeResizedImage(
    blob: Blob,
    image: JsAny,
    targetDensity: Int,
    resourceDensity: Int,
): Promise<JsAny> = js(
    """createImageBitmap(blob, {
        resizeWidth: Math.trunc(image.width * targetDensity / resourceDensity),
        resizeHeight: Math.trunc(image.height * targetDensity / resourceDensity),
        resizeQuality: 'high'
    })"""
)

/** An `<img>` that has loaded [blob]. Its contents stay with it after its URL is revoked. */
// language=js
internal fun imageElementOf(blob: Blob): Promise<JsAny> = js(
    """new Promise((resolve, reject) => {
        const url = URL.createObjectURL(blob);
        const image = new Image();
        image.onload = () => {
            URL.revokeObjectURL(url);
            resolve(image);
        };
        image.onerror = () => {
            URL.revokeObjectURL(url);
            reject(new Error('The browser cannot show the image'));
        };
        image.src = url;
    })"""
)
