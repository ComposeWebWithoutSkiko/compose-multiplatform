@file:JvmName("FontResources_skikoKt")

package org.jetbrains.compose.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.Density
import kotlin.jvm.JvmName

private const val defaultFontIdentity = "org.jetbrains.compose.resources.defaultFont"

// It's mainly necessary for the web.
// A meaningful default font is used while a requested font is being loaded asynchronously.
// No font has this name, so the text uses the default font of the platform: of the browser on the web.
@OptIn(ExperimentalTextApi::class)
private val defaultFont: Font = SystemFont(defaultFontIdentity)

private val fontCache = AsyncCache<String, Font>()

internal val Font.isDefault: Boolean
    @OptIn(ExperimentalTextApi::class)
    get() = (this as? SystemFont)?.identity == defaultFontIdentity

internal expect suspend fun ResourceReader.readFont(
    path: String,
    identity: String,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings
): Font

@Deprecated(
    message = "Use the new Font function with variationSettings instead.",
    level = DeprecationLevel.HIDDEN
)
@Composable
actual fun Font(resource: FontResource, weight: FontWeight, style: FontStyle): Font {
    val resourceReader = LocalResourceReader.currentOrPreview
    val fontFile by rememberResourceState(resource, weight, style, { defaultFont }) { env ->
        val path = resource.getResourceItemByEnvironment(env).path
        val key = "$path:$weight:$style"
        fontCache.getOrLoad(key) {
            resourceReader.readFont(path, path, weight, style, FontVariation.Settings())
        }
    }
    return fontFile
}
@Composable
actual fun Font(
    resource: FontResource,
    weight: FontWeight,
    style: FontStyle,
    variationSettings: FontVariation.Settings,
): Font {
    val resourceReader = LocalResourceReader.currentOrPreview
    val fontFile by rememberResourceState(resource, weight, style, variationSettings, { defaultFont }) { env ->
        val path = resource.getResourceItemByEnvironment(env).path
        val key = "$path:$weight:$style:${variationSettings.getCacheKey()}"
        fontCache.getOrLoad(key) {
            resourceReader.readFont(path, key, weight, style, variationSettings)
        }
    }
    return fontFile
}

internal fun FontVariation.Settings.getCacheKey(): String {
    val defaultDensity = Density(1f)
    return settings
        .map { "${it::class.simpleName}(${it.axisName},${it.toVariationValue(defaultDensity)})" }
        .sorted()
        .joinToString(",")
}