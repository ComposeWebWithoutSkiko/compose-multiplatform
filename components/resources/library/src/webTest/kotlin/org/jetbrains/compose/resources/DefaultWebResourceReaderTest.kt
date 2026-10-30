package org.jetbrains.compose.resources

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTestApi::class)
class DefaultWebResourceReaderTest {

    private val reader = DefaultWebResourceReader

    private val appNameStringRes = TestStringResource("app_name")

    @Test
    fun stringResource() = runComposeUiTest {

        var appName: String by mutableStateOf("")

        setContent {
            CompositionLocalProvider(
                LocalResourceReader provides reader,
                LocalComposeEnvironment provides TestComposeEnvironment
            ) {
                appName = stringResource(appNameStringRes)
                Text(appName)
            }
        }

        awaitUntil {
            appName == "Compose Resources App"
        }

        assertEquals("Compose Resources App", appName)
    }

    @Test
    fun uriOnAPageWithAFileNameIsNextToThePage() {
        // Karma runs the tests in /context.html.
        assertEquals("${window.location.origin}/1.png", reader.getUri("1.png"))
    }

    @Test
    fun uriFollowsTheBaseOfThePage() {
        val base = document.createElement("base")
        base.setAttribute("href", "/some/dir/")
        document.head!!.appendChild(base)
        try {
            assertEquals("${window.location.origin}/some/dir/1.png", reader.getUri("1.png"))
        } finally {
            base.remove()
        }
    }

    @Test
    fun uriFollowsThePathMapping() {
        val mapping = WebResourcesConfiguration.getResourcePath
        try {
            WebResourcesConfiguration.resourcePathMapping { "/absolute/$it" }
            assertEquals("${window.location.origin}/absolute/1.png", reader.getUri("1.png"))
            WebResourcesConfiguration.resourcePathMapping { "https://cdn.example.com/res/$it" }
            assertEquals("https://cdn.example.com/res/1.png", reader.getUri("1.png"))
        } finally {
            WebResourcesConfiguration.resourcePathMapping(mapping)
        }
    }

    private suspend fun ComposeUiTest.awaitUntil(
        timeout: Duration = 100.milliseconds,
        block: suspend () -> Boolean
    ) {
        withContext(Dispatchers.Default) {
            withTimeout(timeout) {
                while (!block()) {
                    delay(10)
                    awaitIdle()
                }
            }
        }
    }
}