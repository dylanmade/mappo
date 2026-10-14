package com.themestudio.core

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * **A Google Font fetched straight from the Google Fonts web API** (2026-10-12) — the fallback
 * behind the GMS provider in [googleFontFamily], for the families GMS doesn't serve (newer ones,
 * and width cuts such as "Barlow Condensed" that GMS only knows as widths of their base family).
 *
 * The web API's CSS (`fonts.googleapis.com/css`) names the family exactly as fonts.google.com
 * does and links a TrueType file per weight; the face nearest [weight] is downloaded once into
 * the app's cache (`cacheDir/webfonts`) and loaded from there on every later use. Asking for
 * every weight, not just [weight], is what makes single-weight families resolve — the API
 * answers 400 when the one weight asked for doesn't exist (Buda is 300 only).
 *
 * Async, like the GMS font: Compose shows the fallback typeface until it lands, and a failure
 * (offline, unknown family) leaves the fallback in place.
 */
class WebGoogleFont(
    val family: String,
    override val weight: FontWeight = FontWeight.Normal,
) : AndroidFont(FontLoadingStrategy.Async, WebGoogleFontLoader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal

    override fun equals(other: Any?): Boolean =
        other is WebGoogleFont && other.family == family && other.weight == weight

    override fun hashCode(): Int = 31 * family.hashCode() + weight.hashCode()

    override fun toString(): String = "WebGoogleFont(family=$family, weight=$weight)"
}

private object WebGoogleFontLoader : AndroidFont.TypefaceLoader {
    // Async only — there is nothing to load without the network or the cache read below.
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = null

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        val web = font as WebGoogleFont
        return withContext(Dispatchers.IO) {
            runCatching { WebGoogleFonts.load(context, web.family, web.weight.weight) }
                .onFailure { Log.w(TAG, "web font '${web.family}' failed: $it") }
                .getOrNull()
        }
    }
}

/** Why a web font didn't load, in the picker's words — [message] is shown to the user as is.
 *  [transient] failures (the network) are worth retrying; the rest won't change. */
class WebFontException(message: String, val transient: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

/** The download itself — shared by [WebGoogleFont]'s loader and [FontAvailability]'s probe, so
 *  a probe that succeeds has already filled the cache the real load reads. Blocking: call it
 *  off the main thread. */
internal object WebGoogleFonts {

    /** [family]'s face nearest [weight], downloading it once; throws [WebFontException]. */
    fun load(context: Context, family: String, weight: Int): Typeface {
        val dir = File(context.cacheDir, "webfonts").apply { mkdirs() }
        val slug = family.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val file = File(dir, "$slug-$weight.ttf")
        if (!file.exists()) {
            val url = faceUrl(family, weight)
                ?: throw WebFontException("No Android-compatible file on Google Fonts", transient = false)
            val part = File(dir, "${file.name}.part")
            network { open(url).inputStream.use { input -> part.outputStream().use { input.copyTo(it) } } }
            if (!part.renameTo(file)) throw WebFontException("Couldn't save the font file", transient = true)
            Log.d(TAG, "web font '$family' $weight downloaded (${file.length()} bytes)")
        }
        return runCatching { Typeface.createFromFile(file) }.getOrElse {
            file.delete()
            throw WebFontException("The font file couldn't be read", transient = false, it)
        }
    }

    /** The TrueType URL of [family]'s upright face nearest [weight] (ties go lighter). */
    private fun faceUrl(family: String, weight: Int): String? =
        faces(family).entries
            .minWithOrNull(compareBy({ abs(it.key - weight) }, { it.key }))
            ?.value

    /** The weights [family] comes in, upright, ascending — what the web API serves. Throws
     *  [WebFontException] when it can't be asked. */
    fun weights(family: String): List<Int> = faces(family).keys.sorted()

    /** [family]'s upright faces, weight → TrueType URL — fetched once per family per process
     *  (every weight's download and the weight picker share it). */
    private fun faces(family: String): Map<Int, String> {
        faceCache[family]?.let { return it }
        val query = URLEncoder.encode(family, "UTF-8") + ":" + AllWeights
        val css = network {
            open("https://fonts.googleapis.com/css?family=$query").inputStream
                .bufferedReader().use { it.readText() }
        }
        val faces = FaceRule.findAll(css)
            .filter { it.groupValues[1] == "normal" }
            .associate { it.groupValues[2].toInt() to it.groupValues[3] }
        faceCache[family] = faces
        return faces
    }

    private val faceCache = ConcurrentHashMap<String, Map<Int, String>>()

    /** Runs a network step, turning its failures into the picker's messages. */
    private fun <T> network(block: () -> T): T = try {
        block()
    } catch (e: WebFontException) {
        throw e
    } catch (e: SocketTimeoutException) {
        throw WebFontException("Download timed out", transient = true, e)
    } catch (e: UnknownHostException) {
        throw WebFontException("Offline — can't download it", transient = true, e)
    } catch (e: IOException) {
        throw WebFontException("Download failed — check the connection", transient = true, e)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            val code = responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                disconnect()
                throw when (code) {
                    // The CSS API answers 400 for a family it doesn't have.
                    400, 404 -> WebFontException("Not found on Google Fonts", transient = false)
                    else -> WebFontException("Google Fonts error (HTTP $code)", transient = true)
                }
            }
        }

    /** One `@font-face` rule: style, weight, TrueType url. */
    private val FaceRule = Regex(
        """font-style:\s*(\w+);\s*font-weight:\s*(\d+);[^}]*?src:\s*url\(([^)]+)\)\s*format\('truetype'\)""",
    )

    private const val AllWeights = "100,200,300,400,500,600,700,800,900"
    private const val TimeoutMillis = 10_000
}

private const val TAG = "WebGoogleFont"
