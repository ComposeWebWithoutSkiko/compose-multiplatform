package org.jetbrains.compose.resources

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.js.JsAny
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Resources that the browser reads and decodes itself, without their bytes reaching Kotlin. */
class BrowserResourcesTest {
    /** Serves every resource from [url], as a reader whose resources aren't files may do. */
    private class UrlResourceReader(private val url: String) : ResourceReader by DefaultResourceReader {
        override fun getUri(path: String): String = url
    }

    @Test
    fun aBitmapOfAHigherDensityIsDecodedAtTheDensityOfTheScreen() = runTest {
        val bitmap = TestResourceReader().readBitmap("1.png", resourceDensity = 480, targetDensity = 160)
        // 128 x 128 scaled by a third, rounded down.
        assertEquals(42, bitmap.width)
        assertEquals(42, bitmap.height)
    }

    @Test
    fun aBitmapOfALowerDensityIsNotScaled() = runTest {
        val bitmap = TestResourceReader().readBitmap("1.png", resourceDensity = 160, targetDensity = 480)
        assertEquals(128, bitmap.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun anSvgServedWithAnotherTypeIsShown() = runTest {
        val reader = UrlResourceReader(dataUrl("application/octet-stream", SvgWithASize))
        val painter = reader.readSvgPainter("image.svg", Density(3f))
        assertEquals(Size(6f, 6f), painter.intrinsicSize)
        val pixels = painter.drawnAt(64, 64)
        // Scaled up from 2 pixels, the middle would be a blend of both colors.
        assertEquals(Color.Red, pixels[31, 32])
        assertEquals(Color.Blue, pixels[32, 32])
    }

    @Test
    fun anSvgWithoutASizeHasTheAspectRatioOfItsViewBox() = runTest {
        val reader = UrlResourceReader(dataUrl("image/svg+xml", SvgOfAViewBox))
        val painter = reader.readSvgPainter("image.svg", Density(3f))
        assertEquals(2f, painter.intrinsicSize.width / painter.intrinsicSize.height)
        val pixels = painter.drawnAt(64, 64)
        assertEquals(Color.Red, pixels[0, 32])
        assertEquals(Color.Blue, pixels[63, 32])
    }

    @Test
    fun anSvgIsDrawnWithAlpha() = runTest {
        val reader = UrlResourceReader(dataUrl("image/svg+xml", SvgWithASize))
        val pixels = reader.readSvgPainter("image.svg", Density(1f)).drawnAt(64, 64, alpha = 0.5f)
        assertEquals(0.5f, pixels[10, 32].alpha, 0.01f)
    }

    @Test
    fun aColorFilterKeepsAnSvgSharp() = runTest {
        val reader = UrlResourceReader(dataUrl("image/svg+xml", SvgOfAShape))
        val pixels = reader.readSvgPainter("image.svg", Density(1f))
            .drawnAt(64, 64, ColorFilter.tint(Color.Green))
        assertEquals(Color.Green, pixels[31, 32])
        assertEquals(Color.Transparent, pixels[32, 32])
    }

    @Test
    fun anSvgThatTaintsCanvasesIsAFailedResource() = runTest {
        val reader = UrlResourceReader(dataUrl("image/svg+xml", SvgOfForeignContent))
        assertFailsWith<MissingResourceException> {
            reader.readSvgPainter("image.svg", Density(1f))
        }
    }

    @Test
    fun anSvgDecodedFromBytesAppearsOnceLoaded() = runTest {
        val painter = SvgWithASize.encodeToByteArray().decodeToSvgPainter(Density(3f))
        assertEquals(Size.Unspecified, painter.intrinsicSize)
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (painter.intrinsicSize == Size.Unspecified) delay(10)
            }
        }
        assertEquals(Size(6f, 6f), painter.intrinsicSize)
        assertEquals(Color.Red, painter.drawnAt(64, 64)[31, 32])
    }

    @Test
    fun anSvgFromBytesThatCannotBeLoadedStaysEmptyAndIsReported() = runTest {
        withWarnings { warnings ->
            val painter = "not an SVG".encodeToByteArray().decodeToSvgPainter(Density(1f))
            awaitWarning(warnings)
            assertEquals(Size.Unspecified, painter.intrinsicSize)
        }
    }

    @Test
    fun anSvgFromBytesThatTaintsCanvasesIsReportedWithTheReason() = runTest {
        withWarnings { warnings ->
            val painter = SvgOfForeignContent.encodeToByteArray().decodeToSvgPainter(Density(1f))
            awaitWarning(warnings)
            assertEquals(Size.Unspecified, painter.intrinsicSize)
            assertTrue("foreignObject" in firstWarning(warnings), firstWarning(warnings))
        }
    }

    @Test
    fun aReaderWithoutAUrlIsReported() = runTest {
        val reader = object : ResourceReader by DefaultResourceReader {
            override fun getUri(path: String): String = TODO("No URL")
        }
        withWarnings { warnings ->
            assertFailsWith<MissingResourceException> { reader.readBitmap("1.png", 160, 160) }
            assertEquals(1, warningCount(warnings))
            assertTrue("1.png" in firstWarning(warnings), firstWarning(warnings))
        }
    }

    @Test
    fun aMalformedPathIsAMissingResource() = runTest {
        val mapping = WebResourcesConfiguration.getResourcePath
        try {
            WebResourcesConfiguration.resourcePathMapping { "http://" }
            assertFailsWith<MissingResourceException> { DefaultResourceReader.read("1.png") }
        } finally {
            WebResourcesConfiguration.resourcePathMapping(mapping)
        }
    }

    @Test
    fun anXmlFileIsParsedFromAnyUrl() = runTest {
        val reader = UrlResourceReader(dataUrl("text/plain", """<vector width="24"/>"""))
        val element = reader.readXmlElement("image.xml")
        assertEquals("vector", element.localName)
        assertEquals("24", element.getAttribute("width"))
    }

    @Test
    fun aFontIsLoadedByTheTimeItIsReturned() = runTest {
        val loadedBefore = loadedFontFaces()
        TestResourceReader().readFont(
            "font_awesome.otf",
            "A font loaded for a test",
            FontWeight.Normal,
            FontStyle.Normal,
            FontVariation.Settings(),
        )
        assertEquals(loadedBefore + 1, loadedFontFaces())
    }
}

private fun Painter.drawnAt(
    width: Int,
    height: Int,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
): PixelMap {
    val bitmap = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        draw(size, alpha, colorFilter)
    }
    return bitmap.toPixelMap()
}

/** Runs [block] with what's passed to `console.warn` collected instead of printed. */
private inline fun withWarnings(block: (warnings: JsAny) -> Unit) {
    val warnings = collectWarnings()
    try {
        block(warnings)
    } finally {
        stopCollectingWarnings(warnings)
    }
}

private suspend fun awaitWarning(warnings: JsAny) = withContext(Dispatchers.Default) {
    withTimeout(5.seconds) {
        while (warningCount(warnings) == 0) delay(10)
    }
}

// language=js
private fun collectWarnings(): JsAny = js(
    """{
        const warnings = { warn: console.warn, messages: [] };
        console.warn = (message) => { warnings.messages.push(String(message)); };
        return warnings;
    }"""
)

// language=js
private fun stopCollectingWarnings(warnings: JsAny): Unit = js("{ console.warn = warnings.warn; }")

// language=js
private fun warningCount(warnings: JsAny): Int = js("warnings.messages.length")

// language=js
private fun firstWarning(warnings: JsAny): String = js("warnings.messages[0]")

// language=js
private fun dataUrl(type: String, content: String): String =
    js("'data:' + type + ',' + encodeURIComponent(content)")

// language=js
private fun loadedFontFaces(): Int =
    js("[...document.fonts].filter((face) => face.status === 'loaded').length")

private const val SvgNamespace = """xmlns="http://www.w3.org/2000/svg""""

// Red on the left, blue on the right.
private const val TwoColors =
    """<rect width="1" height="2" fill="#f00"/><rect x="1" width="1" height="2" fill="#00f"/>"""

private const val SvgWithASize =
    """<svg $SvgNamespace width="2" height="2" viewBox="0 0 2 2">$TwoColors</svg>"""

private const val SvgOfAViewBox = """<svg $SvgNamespace viewBox="0 0 2 1">$TwoColors</svg>"""

// Opaque on the left, transparent on the right.
private const val SvgOfAShape =
    """<svg $SvgNamespace width="2" height="2"><rect width="1" height="2"/></svg>"""

private const val SvgOfForeignContent =
    """<svg $SvgNamespace width="2" height="2"><foreignObject width="2" height="2">""" +
        """<div xmlns="http://www.w3.org/1999/xhtml"></div></foreignObject></svg>"""
