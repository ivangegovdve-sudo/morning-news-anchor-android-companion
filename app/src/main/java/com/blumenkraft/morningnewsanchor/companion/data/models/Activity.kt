package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of `public.ingested_items` — raw material the pipeline collected, before narration.
 *
 * There is no `summary` or `importance_score` column; ranking happens inside the pipeline
 * and its output lands in [BriefingStory]. [usedInBriefingId] is the link between the two.
 */
@Serializable
data class IngestedItem(
    val id: String,
    @SerialName("user_id") val userId: String,
    /** Stable id from the upstream source; unique per user+source. */
    @SerialName("external_id") val externalId: String,
    /** Where it came from, e.g. `rss`, `gmail`. */
    val source: String,
    val title: String? = null,
    val url: String? = null,
    val sender: String? = null,
    @SerialName("raw_text") val rawText: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val classification: String? = null,
    @SerialName("catalog_id") val catalogId: String? = null,
    @SerialName("used_in_briefing_id") val usedInBriefingId: String? = null,
    val archived: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Row of `public.cron_runs`.
 *
 * Column names differ from the usual guess: the end timestamp is `finished_at` (not
 * `completed_at`), there is no `items_ingested` counter, and per-run detail is a JSON blob
 * in [details]. [userId] is nullable because the nightly sweep logs a fleet-wide row.
 */
@Serializable
data class CronRun(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    /** What kind of run this was, e.g. `morning-briefing`. */
    val kind: String,
    val status: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("finished_at") val finishedAt: String? = null,
    val error: String? = null,
    val details: kotlinx.serialization.json.JsonElement? = null,
) {
    val isRunning: Boolean get() = finishedAt == null && error == null
}

/** Row of `public.weekly_highlights`. [payload] shape is owned by the web pipeline. */
@Serializable
data class WeeklyHighlights(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("week_start") val weekStart: String,
    val payload: kotlinx.serialization.json.JsonElement,
    @SerialName("computed_at") val computedAt: String,
)
