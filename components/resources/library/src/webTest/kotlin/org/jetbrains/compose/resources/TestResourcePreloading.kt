package org.jetbrains.compose.resources

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.text.font.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.JsAny
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class, ExperimentalEncodingApi::class)
class TestResourcePreloading {

    @Test
    fun testPreloadFont() = clearResourceCachesAndRunUiTest {
        val fontFile = "font_awesome.otf"
        ResourceWebCache.resetCache() // A file in the cache isn't fetched.
        val fetches = holdFetches(fontFile)
        try {
            var font: Font? = null
            var font2: Font? = null
            var condition by mutableStateOf(false)

            setContent {
                CompositionLocalProvider(LocalComposeEnvironment provides TestComposeEnvironment) {
                    font = preloadFont(TestFontResource(fontFile)).value

                    if (condition) {
                        font2 = Font(TestFontResource(fontFile))
                    }
                }
            }
            waitForIdle()
            assertEquals(null, font)
            assertEquals(null, font2)

            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    while (heldFetches(fetches) == 0) delay(10)
                }
            }
            assertEquals(null, font)
            releaseFetches(fetches)

            waitResources()
            assertNotEquals(null, font)
            assertEquals(null, font2) // condition was false for now, so font2 should be not initialized

            condition = true
            waitForIdle()
            assertNotEquals(null, font)
            assertEquals(font, font2, "font2 is expected to be loaded from cache")
            assertEquals(1, heldFetches(fetches), "expected no more fetches of the font")
        } finally {
            releaseFetches(fetches)
        }
    }

    @Test
    fun testIsDefaultCheck() = clearResourceCachesAndRunUiTest {
        val fontFile = "Workbench-Regular.ttf"
        ResourceWebCache.resetCache() // A file in the cache isn't fetched.
        val fetches = holdFetches(fontFile)
        try {
            var font: Font? = null

            setContent {
                CompositionLocalProvider(LocalComposeEnvironment provides TestComposeEnvironment) {
                    font = Font(TestFontResource(fontFile))
                }
            }

            waitForIdle()
            assertNotNull(font)
            assertTrue(font.isDefault)
        } finally {
            releaseFetches(fetches)
        }
    }
}

/**
 * Holds the calls of `fetch` for the URLs ending with [suffix] back until [releaseFetches], which
 * also brings the real `fetch` back. Returns the object to pass to the functions below.
 */
// language=js
private fun holdFetches(suffix: String): JsAny = js(
    """{
        const holder = { fetch: window.fetch, count: 0 };
        const released = new Promise((resolve) => { holder.release = resolve; });
        window.fetch = (url, options) => {
            if (!String(url).endsWith(suffix)) return holder.fetch.call(window, url, options);
            holder.count++;
            return released.then(() => holder.fetch.call(window, url, options));
        };
        return holder;
    }"""
)

// language=js
private fun heldFetches(holder: JsAny): Int = js("holder.count")

// language=js
private fun releaseFetches(holder: JsAny): Unit = js(
    """{
        window.fetch = holder.fetch;
        holder.release();
    }"""
)
