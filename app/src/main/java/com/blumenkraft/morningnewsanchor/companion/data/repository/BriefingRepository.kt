package com.blumenkraft.morningnewsanchor.companion.data.repository

import com.blumenkraft.morningnewsanchor.companion.data.models.Briefing
import com.blumenkraft.morningnewsanchor.companion.data.models.BriefingDetail
import com.blumenkraft.morningnewsanchor.companion.data.models.BriefingStory
import com.blumenkraft.morningnewsanchor.companion.data.models.PlayableTrack
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.StorageUrlResolver
import com.blumenkraft.morningnewsanchor.companion.data.supabase.Tables
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
// FilterOperator lives in postgrest, not realtime, even though the realtime DSL takes it.
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads briefings and their stories, and resolves their audio into a playable queue.
 *
 * Every query filters on `user_id` even though RLS already does. That is deliberate: the
 * filter is what makes the intent legible at the call site, and it keeps a policy change
 * from silently widening these reads.
 */
@Singleton
class BriefingRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val realtime: Realtime,
    private val auth: AuthRepository,
    private val urls: StorageUrlResolver,
    private val json: Json,
) {

    /** Most recent first. Mirrors the web app's 30-row cap. */
    suspend fun listBriefings(limit: Long = 30): List<Briefing> =
        postgrest.from(Tables.BRIEFINGS)
            .select {
                filter { eq("user_id", auth.requireUserId()) }
                order("briefing_date", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun getBriefing(id: String): Briefing? =
        postgrest.from(Tables.BRIEFINGS)
            .select {
                filter {
                    eq("id", id)
                    eq("user_id", auth.requireUserId())
                }
            }
            .decodeSingleOrNull()

    /** Today's briefing in the user's timezone, or null if the cron hasn't run yet. */
    suspend fun getBriefingForDate(isoDate: String): Briefing? =
        postgrest.from(Tables.BRIEFINGS)
            .select {
                filter {
                    eq("user_id", auth.requireUserId())
                    eq("briefing_date", isoDate)
                }
            }
            .decodeSingleOrNull()

    suspend fun getStories(briefingId: String): List<BriefingStory> =
        postgrest.from(Tables.BRIEFING_STORIES)
            .select {
                filter { eq("briefing_id", briefingId) }
                order("position", Order.ASCENDING)
            }
            .decodeList()

    suspend fun getBriefingDetail(id: String): BriefingDetail? {
        val briefing = getBriefing(id) ?: return null
        return BriefingDetail(briefing, getStories(id))
    }

    /**
     * Builds the playback queue: the intro first, then each story in `position` order.
     *
     * Tracks whose audio hasn't been synthesized yet are skipped rather than surfaced as
     * broken entries — a briefing mid-generation legitimately has stories without audio,
     * and a partially-playable briefing is more useful than none.
     */
    suspend fun buildQueue(detail: BriefingDetail): List<PlayableTrack> = buildList {
        val (briefing, stories) = detail

        urls.signedUrl(briefing.introAudioUrl)?.let { url ->
            add(
                PlayableTrack(
                    id = "intro-${briefing.id}",
                    title = "Intro",
                    subtitle = briefing.briefingDate,
                    signedUrl = url,
                )
            )
        }

        for (story in stories) {
            val url = urls.signedUrl(story.audioUrl) ?: continue
            add(
                PlayableTrack(
                    id = story.id,
                    title = story.headline,
                    subtitle = story.sourceName,
                    signedUrl = url,
                    artworkUrl = story.imageUrl,
                )
            )
        }
    }

    /**
     * Emits whenever the current user's `briefings` rows change.
     *
     * This is the app's primary "new briefing is ready" signal — it needs no FCM project and
     * no server push, at the cost of only firing while the app holds a live socket. See
     * ARCHITECTURE.md "Notifications" for the tradeoff against FCM.
     *
     * The channel is unsubscribed when collection stops, so callers should scope this to a
     * ViewModel rather than a singleton.
     */
    fun observeBriefings(): Flow<PostgresAction> {
        val userId = auth.requireUserId()
        val channel = realtime.channel("briefings:$userId")

        return channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = Tables.BRIEFINGS
                filter("user_id", FilterOperator.EQ, userId)
            }
            .onCompletion { channel.unsubscribe() }
    }

    /**
     * Realtime inserts only, decoded into [Briefing].
     *
     * An insert fires when the pipeline *starts* a briefing, so the row usually arrives with
     * `status = pending` and no audio. Callers that want "ready to listen" should watch for
     * updates reaching [Briefing.isReady] instead of treating an insert as done.
     */
    fun observeNewBriefings(): Flow<Briefing> =
        observeBriefings().mapNotNull { action ->
            val record = (action as? PostgresAction.Insert)?.record ?: return@mapNotNull null
            runCatching { json.decodeFromJsonElement(Briefing.serializer(), record) }.getOrNull()
        }
}
