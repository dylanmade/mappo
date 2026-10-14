package com.themestudio.core

import android.content.Context
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.themestudio.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Whether a family named in a font picker can actually be used. */
sealed interface FontLoadStatus {
    data object Loading : FontLoadStatus
    data object Loaded : FontLoadStatus

    /** [message] is short and user-facing ("Offline — can't download it"). */
    data class Failed(val message: String, val transient: Boolean) : FontLoadStatus
}

/**
 * **Which font families load** (2026-10-12) — so a picker can offer only the fonts that work.
 * Applying a family that fails just falls back to the default typeface without a word, which is
 * how the condensed families came to look identical; the picker disables those rows and says
 * why instead.
 *
 * A probe walks the same chain [resolveThemeFontFamily] builds: an asset-bundled family loads;
 * otherwise GMS ([GoogleFontsProvider]), then the web download ([WebGoogleFonts]) — the web
 * failure's reason is the one reported, as it's the last thing tried. A probe that succeeds has
 * warmed the cache the real load reads (GMS's, or `cacheDir/webfonts`).
 *
 * Results live for the process. Permanent failures stick; TRANSIENT ones (the network) are
 * probed again the next time a row asks, so coming back online recovers them.
 *
 * Probes run in this object's OWN scope, not the asking row's: the picker composes its first
 * rows for one frame before jumping to the applied font, and a probe tied to the row was
 * cancelled with it — leaving the family on Loading forever (nothing re-probes a Loading
 * family) and its probe permit held. A probe that dies anyway clears its Loading mark.
 */
object FontAvailability {

    private val statuses = MutableStateFlow<Map<String, FontLoadStatus>>(emptyMap())

    /** A few probes at a time: a fast scroll composes many rows at once. */
    private val probes = Semaphore(4)

    /** Every family probed so far; a missing key hasn't been asked about. */
    val all: StateFlow<Map<String, FontLoadStatus>> = statuses.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Probe [name] unless it's loaded, already being probed, or failed for good. Returns at
     *  once; the result lands in [all]. */
    fun request(context: Context, name: String) {
        val key = name.trim()
        var claimed = false
        statuses.update { map ->
            val current = map[key]
            val stale = current == null || (current is FontLoadStatus.Failed && current.transient)
            claimed = stale
            if (stale) map + (key to FontLoadStatus.Loading) else map
        }
        if (!claimed) return
        val appContext = context.applicationContext
        scope.launch {
            var result: FontLoadStatus? = null
            try {
                result = probes.withPermit { probe(appContext, key) }
            } finally {
                // No result (cancelled, or something threw past probe's own catch): drop the
                // Loading mark so the next ask probes again.
                statuses.update { if (result != null) it + (key to result) else it - key }
            }
        }
    }

    private fun probe(context: Context, name: String): FontLoadStatus {
        if (LocalFontRegistry.findByDisplayName(name) != null) return FontLoadStatus.Loaded
        if (gmsServes(context, name)) return FontLoadStatus.Loaded
        return try {
            WebGoogleFonts.load(context, name, weight = 400)
            FontLoadStatus.Loaded
        } catch (e: WebFontException) {
            FontLoadStatus.Failed(e.message ?: "Couldn't load this font", e.transient)
        } catch (e: Exception) {
            FontLoadStatus.Failed("Couldn't load this font", transient = true)
        }
    }

    /** The GMS provider has [name] — the same query Compose's GoogleFont sends. */
    private fun gmsServes(context: Context, name: String): Boolean = runCatching {
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "name=$name&weight=400&italic=0&besteffort=true",
            R.array.com_google_android_gms_fonts_certs,
        )
        // A provider query can block on GMS's own download; give up on it after a while and let
        // the web download decide instead.
        val cancel = CancellationSignal()
        val timer = Handler(Looper.getMainLooper())
        timer.postDelayed({ cancel.cancel() }, GmsTimeoutMillis)
        val result = try {
            FontsContractCompat.fetchFonts(context, cancel, request)
        } finally {
            timer.removeCallbacksAndMessages(null)
        }
        result.statusCode == FontsContractCompat.FontFamilyResult.STATUS_OK &&
            result.fonts.any { it.resultCode == FontsContractCompat.Columns.RESULT_CODE_OK }
    }.getOrDefault(false)

    private const val GmsTimeoutMillis = 10_000L
}

/**
 * **The weights a family comes in** (2026-10-12) — the font picker's weight slider puts a pip on
 * each. An asset-bundled family answers from its variants; a Google Font from the web API's CSS
 * (GMS can't list a family's weights). Null when they can't be learned (offline).
 */
object FontWeightCatalog {
    private val cache = MutableStateFlow<Map<String, List<Int>>>(emptyMap())

    suspend fun weights(context: Context, name: String): List<Int>? {
        val key = name.trim()
        cache.value[key]?.let { return it }
        val local = LocalFontRegistry.findByDisplayName(key)
        val weights = if (local != null) {
            local.variants.filter { it.style == androidx.compose.ui.text.font.FontStyle.Normal }
                .map { it.weight.weight }.distinct().sorted()
        } else {
            withContext(Dispatchers.IO) { runCatching { WebGoogleFonts.weights(key) }.getOrNull() }
        }?.takeIf { it.isNotEmpty() } ?: return null
        cache.update { it + (key to weights) }
        return weights
    }
}

/** [name]'s load status, probing it the first time a row shows it. */
@Composable
fun rememberFontLoadStatus(name: String): FontLoadStatus {
    val context = LocalContext.current
    val key = name.trim()
    val flow = remember(key) { FontAvailability.all.map { it[key] ?: FontLoadStatus.Loading } }
    // Seeded with what's known, so a row scrolled back into view doesn't flash "loading".
    val status by flow.collectAsState(initial = FontAvailability.all.value[key] ?: FontLoadStatus.Loading)
    LaunchedEffect(key) { FontAvailability.request(context, key) }
    return status
}
