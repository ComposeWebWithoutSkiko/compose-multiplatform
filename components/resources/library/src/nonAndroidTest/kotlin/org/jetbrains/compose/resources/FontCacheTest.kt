package org.jetbrains.compose.resources

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class FontCacheTest {

    @Test
    fun testFontResourceCache() = clearResourceCachesAndRunUiTest {
        val font1 = "font_awesome.otf"
        val font2 = "Workbench-Regular.ttf"

        val testResourceReader = TestResourceReader()
        var res by mutableStateOf(TestFontResource(font1))
        setContent {
            CompositionLocalProvider(
                LocalResourceReader provides testResourceReader,
                LocalComposeEnvironment provides TestComposeEnvironment
            ) {
                Text(
                    fontFamily = FontFamily(Font(res)),
                    text = "Hello"
                )
            }
        }
        waitResources()
        res = TestFontResource(font2)
        waitResources()
        res = TestFontResource(font1)
        waitResources()

        assertEquals(
            expected = listOf(font1, font2), //no second read of font_awesome.otf
            actual = testResourceReader.readPaths
        )

        ResourceCaches.clear()

        res = TestFontResource(font2)
        waitResources()
        res = TestFontResource(font1)
        waitResources()

        assertEquals(
            expected = listOf(font1, font2, font2, font1), //read fonts again
            actual = testResourceReader.readPaths
        )
    }

    @Test
    fun testFontReplacement() = clearResourceCachesAndRunUiTest {
        val font1 = "font_awesome.otf"
        val font2 = "Workbench-Regular.ttf"

        val testResourceReader = TestFontResourceReplacementReader()
        var res by mutableStateOf(TestFontResource(font1))
        var currentFont: Font? = null
        setContent {
            CompositionLocalProvider(
                LocalResourceReader provides testResourceReader,
                LocalComposeEnvironment provides TestComposeEnvironment
            ) {
                val font = Font(res)
                currentFont = font
                Text(
                    fontFamily = FontFamily(font),
                    text = "Hello"
                )
            }
        }
        waitResources()
        val loaded1 = currentFont

        res = TestFontResource(font2)
        waitResources()

        val loaded2 = currentFont

        assertNotEquals(loaded1, loaded2)

        ResourceCaches.clear()

        testResourceReader.replaceNextReadWith(font2)
        res = TestFontResource(font1)
        waitResources()

        val loaded3 = currentFont
        assertNotEquals(loaded1, loaded3)
    }

    internal class TestFontResourceReplacementReader : ResourceReader {

        private var nextPath: String? = null

        fun replaceNextReadWith(path: String) {
            nextPath = path
        }

        override suspend fun read(path: String): ByteArray {
            return DefaultResourceReader.read(nextPath ?: path)
        }

        override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
            return DefaultResourceReader.readPart(path, offset, size)
        }

        // On the web, the browser loads fonts from here.
        override fun getUri(path: String): String {
            return DefaultResourceReader.getUri(nextPath ?: path)
        }
    }
}