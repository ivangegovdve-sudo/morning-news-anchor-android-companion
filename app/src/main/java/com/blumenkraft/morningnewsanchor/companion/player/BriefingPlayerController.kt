package com.blumenkraft.morningnewsanchor.companion.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blumenkraft.morningnewsanchor.companion.data.models.PlayableTrack
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the player UI renders. */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTrackId: String? = null,
    val currentTrackTitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
)

/**
 * App-side handle on [BriefingPlaybackService].
 *
 * Connecting is async and the controller must be released, so this is a singleton rather
 * than something a ViewModel builds per screen — otherwise rotating the device would tear
 * down and rebuild the connection mid-playback.
 *
 * TODO: the position in [state] is only refreshed on player events, so the scrubber won't
 *  advance smoothly on its own. Drive a ~500ms ticker from the UI while playing, or move to
 *  media3-ui-compose's state helpers once they're stable.
 */
@Singleton
class BriefingPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = PlayerUiState(
                isPlaying = player.isPlaying,
                currentTrackId = player.currentMediaItem?.mediaId,
                currentTrackTitle = player.currentMediaItem?.mediaMetadata?.title?.toString(),
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                queueSize = player.mediaItemCount,
                currentIndex = player.currentMediaItemIndex,
            )
        }
    }

    /** Idempotent — safe to call from every screen that touches playback. */
    suspend fun connect() {
        if (controller != null) return

        val token = SessionToken(context, ComponentName(context, BriefingPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        controller = suspendCancellableCoroutine { cont ->
            future.addListener(
                { cont.resume(runCatching { future.get() }.getOrNull()) },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }?.also { it.addListener(listener) }
    }

    /**
     * Replaces the queue and starts playing.
     *
     * @param startIndex which track to begin on — used when the user taps a specific story
     *   rather than the play button.
     */
    fun play(tracks: List<PlayableTrack>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return

        c.setMediaItems(tracks.map(::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun stop() { controller?.stop() }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    private fun toMediaItem(track: PlayableTrack): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            // The signed URL expires (see StorageUrlResolver). ExoPlayer buffers rather than
            // holding the URL open, so a long queue must be built from freshly-signed URLs;
            // re-signing mid-playback is not handled here.
            .setUri(track.signedUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.subtitle)
                    .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
                    .build()
            )
            .build()
}
