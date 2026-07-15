package com.blumenkraft.morningnewsanchor.companion.data.supabase

import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Turns the storage object paths held in `briefings.intro_audio_url` and
 * `briefing_stories.audio_url` into playable signed URLs.
 *
 * Two things worth knowing:
 *
 * 1. Those columns hold **paths**, not URLs, despite their names — the web pipeline writes
 *    `{userId}/{briefingId}/{slug}.mp3`. Handing the raw column value to ExoPlayer fails.
 *
 * 2. The web app signs these with the service-role key server-side, but it doesn't have to.
 *    The `audio` bucket's RLS policy grants `authenticated` users SELECT on objects under
 *    their own uid prefix, which is sufficient to sign. So this app signs client-side and
 *    needs no backend endpoint for playback.
 *
 * URLs are cached until shortly before expiry, because a long briefing signs one URL per
 * story and re-signing on every recomposition would be a burst of pointless requests.
 */
@Singleton
class StorageUrlResolver @Inject constructor(
    private val storage: Storage,
) {
    private data class CachedUrl(val url: String, val expiresAtMillis: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedUrl>()

    /**
     * @param path a storage object path such as `2f9c.../8b1e.../story-03.mp3`.
     * @return a signed URL valid for [TTL], or null if [path] is null/blank.
     */
    suspend fun signedUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null

        mutex.withLock {
            val now = System.currentTimeMillis()
            cache[path]?.let { if (it.expiresAtMillis > now + REFRESH_MARGIN_MILLIS) return it.url }

            val url = storage.from(Buckets.AUDIO).createSignedUrl(path, expiresIn = TTL)
            cache[path] = CachedUrl(url, now + TTL.inWholeMilliseconds)
            return url
        }
    }

    /** Drops every cached URL. Call on sign-out so a new user can't reuse the old ones. */
    suspend fun clear() = mutex.withLock { cache.clear() }

    private companion object {
        val TTL = 1.hours

        /**
         * Re-sign this long before expiry. A briefing can be ~10 minutes of audio, so a URL
         * handed to the player must outlive the whole track, not merely be valid at the
         * moment we hand it over.
         */
        val REFRESH_MARGIN_MILLIS = 15.minutes.inWholeMilliseconds
    }
}
