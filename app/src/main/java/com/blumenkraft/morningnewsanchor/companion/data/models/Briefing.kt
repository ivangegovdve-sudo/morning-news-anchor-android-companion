package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of `public.briefings`.
 *
 * A briefing is a container: the narration lives in [narrativeScript] and the audio is split
 * across [introAudioUrl] plus one [BriefingStory.audioUrl] per story. There is no single
 * "the briefing mp3" — the player builds a playlist. See [BriefingDetail].
 *
 * Audio columns hold *storage object paths*, not URLs, despite the `_url` naming inherited
 * from the web app. Resolve them through StorageUrlResolver before handing them to a player.
 */
@Serializable
data class Briefing(
    val id: String,
    @SerialName("user_id") val userId: String,
    /** ISO date (`YYYY-MM-DD`) in the user's local timezone, not an instant. */
    @SerialName("briefing_date") val briefingDate: String,
    val status: String,
    @SerialName("intro_text") val introText: String? = null,
    @SerialName("outro_text") val outroText: String? = null,
    @SerialName("narrative_script") val narrativeScript: String? = null,
    @SerialName("intro_audio_url") val introAudioUrl: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    val error: String? = null,
    @SerialName("drive_folder_id") val driveFolderId: String? = null,
    @SerialName("drive_backup_at") val driveBackupAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    val isReady: Boolean get() = status in READY_STATUSES
    val hasFailed: Boolean get() = status == STATUS_ERROR

    companion object {
        // Written by the web app's pipeline.server.ts as it advances through the run.
        const val STATUS_PENDING = "pending"
        const val STATUS_INGESTED = "ingested"
        const val STATUS_NARRATED = "narrated"
        const val STATUS_READY = "ready"
        const val STATUS_SENT = "sent"
        const val STATUS_ERROR = "error"

        private val READY_STATUSES = setOf(STATUS_READY, STATUS_SENT)
    }
}

/** Row of `public.briefing_stories`, ordered by [position] within a briefing. */
@Serializable
data class BriefingStory(
    val id: String,
    @SerialName("briefing_id") val briefingId: String,
    @SerialName("user_id") val userId: String,
    val position: Int,
    val headline: String,
    val summary: String,
    @SerialName("narration_text") val narrationText: String? = null,
    /** Storage object path under the `audio` bucket, or null if TTS hasn't run yet. */
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val category: String? = null,
    val edited: Boolean = false,
    @SerialName("drive_file_id") val driveFileId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * A briefing joined with its stories. Assembled client-side by BriefingRepository —
 * there is no such table.
 */
data class BriefingDetail(
    val briefing: Briefing,
    val stories: List<BriefingStory>,
)

/** One entry in the playback queue: the intro, then each story in order. */
data class PlayableTrack(
    val id: String,
    val title: String,
    val subtitle: String?,
    /** A time-limited signed URL — see StorageUrlResolver. */
    val signedUrl: String,
    val artworkUrl: String? = null,
)
