/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.resources

import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.js.asJsException
import kotlin.js.js

private external interface AbortSignal
private external class AbortController {
    val signal: AbortSignal
    fun abort()
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(url, signal) => window.fetch(url, { signal })")
private external fun jsFetchWithSignal(url: String, signal: AbortSignal): Promise<Response>

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun cancellableFetch(url: String): Response = suspendCancellableCoroutine { cont ->
    val ac = AbortController()
    jsFetchWithSignal(url, ac.signal).then(
        onFulfilled = { cont.resume(it); null },
        onRejected = { cont.resumeWithException(it.asJsException()); null }
    )
    cont.invokeOnCancellation { ac.abort() }
}

// The only platform-specific part of the web resource reader
internal expect suspend fun Blob.asByteArray(): ByteArray

@ExperimentalResourceApi
internal actual fun getPlatformResourceReader(): ResourceReader = DefaultWebResourceReader

@ExperimentalResourceApi
@OptIn(ExperimentalWasmJsInterop::class)
internal object DefaultWebResourceReader : ResourceReader {
    override suspend fun read(path: String): ByteArray = loadInBrowser(path) {
        readAsBlob(path).asByteArray()
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
        loadInBrowser(path) {
            readAsBlob(path).slice(offset.toInt(), (offset + size).toInt()).asByteArray()
        }

    override fun getUri(path: String): String {
        return getResourceUrl(path)
    }

    private suspend fun readAsBlob(path: String): Blob {
        return fetchBlob(WebResourcesConfiguration.getResourcePath(path))
    }

    suspend fun fetchBlob(url: String): Blob {
        val response = ResourceWebCache.load(url) { cancellableFetch(url) }
        if (!response.ok) {
            throw MissingResourceException(url)
        }
        return response.blob().await()
    }
}

/** The resource file as the browser holds it, fetched from [ResourceReader.getUri]. */
internal suspend fun ResourceReader.readBlob(path: String): Blob =
    DefaultWebResourceReader.fetchBlob(getUri(path))

/**
 * Runs [load], which calls into the browser and the [ResourceReader]. What fails there is reported
 * in the console, where it's the only trace of it, and ends as a [MissingResourceException]: the
 * errors of the browser aren't the [Exception]s that [rememberResourceState] takes for a failed
 * resource.
 */
internal suspend fun <T> loadInBrowser(path: String, load: suspend () -> T): T =
    try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: MissingResourceException) {
        throw e
    } catch (e: Throwable) {
        warn("Failed to load the resource $path: ${e.message}")
        throw MissingResourceException(path, e.message.toString())
    }

// language=js
internal fun warn(message: String): Unit = js("console.warn(message)")
