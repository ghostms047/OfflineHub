package com.offlinehub.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.offlinehub.app
import com.offlinehub.data.Item
import com.offlinehub.data.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class QueueEntry(val id: Long, val title: String, val artist: String)

data class PlayerState(
    val currentId: Long = -1,
    val title: String = "",
    val artist: String = "",
    val artworkPath: String = "",
    val type: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val radio: Boolean = false
) {
    val active: Boolean get() = currentId > 0
}

/** UI-side handle to the background PlaybackService. Must be used from the main thread. */
class PlayerController(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var radioActive = false
    private var refilling = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val poll = object : Runnable {
        override fun run() {
            controller?.let { c ->
                if (c.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = if (c.duration == C.TIME_UNSET) _state.value.durationMs else c.duration
                    )
                }
            }
            main.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) maybeRefillRadio(player)
        }
    }

    private fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh(c)
                pending.toList().forEach { it(c) }
                pending.clear()
                main.removeCallbacks(poll)
                main.post(poll)
            } catch (_: Exception) {
                future = null
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending += block; connect() }
    }

    private fun refresh(p: Player) {
        val item = p.currentMediaItem
        val q = ArrayList<QueueEntry>()
        for (i in 0 until minOf(p.mediaItemCount, 300)) {
            val m = p.getMediaItemAt(i)
            q += QueueEntry(m.mediaId.toLongOrNull() ?: -1, m.mediaMetadata.title?.toString().orEmpty(), m.mediaMetadata.artist?.toString().orEmpty())
        }
        _state.value = PlayerState(
            currentId = item?.mediaId?.toLongOrNull() ?: -1,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            artworkPath = item?.mediaMetadata?.artworkUri?.path.orEmpty(),
            type = item?.mediaMetadata?.extras?.getString("type").orEmpty(),
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration,
            hasNext = p.hasNextMediaItem(),
            hasPrev = p.hasPreviousMediaItem() || p.currentPosition > 3000,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            speed = p.playbackParameters.speed,
            queue = q,
            currentIndex = p.currentMediaItemIndex,
            radio = radioActive
        )
    }

    private fun toMediaItem(i: Item): MediaItem {
        val extras = Bundle().apply { putString("type", i.type) }
        val meta = MediaMetadata.Builder()
            .setTitle(i.title)
            .setArtist(i.subtitle)
            .setExtras(extras)
            .apply { if (i.thumbPath.isNotEmpty() && File(i.thumbPath).exists()) setArtworkUri(Uri.fromFile(File(i.thumbPath))) }
            .build()
        return MediaItem.Builder()
            .setMediaId(i.id.toString())
            .setUri(Uri.fromFile(File(i.localPath)))
            .setMediaMetadata(meta)
            .build()
    }

    private fun resumePos(i: Item): Long {
        val resumable = i.type == Type.PODCAST || i.type == Type.AUDIOBOOK
        if (!resumable || i.positionMs < 5000) return 0
        if (i.durationMs > 0 && i.positionMs > 0.95 * i.durationMs) return 0
        return i.positionMs
    }

    // -------------------------------------------------------------- commands

    fun play(items: List<Item>, startIndex: Int = 0, radio: Boolean = false, shuffle: Boolean = false) {
        val playable = items.filter { it.localPath.isNotEmpty() && File(it.localPath).exists() }
        if (playable.isEmpty()) return
        val startItem = items.getOrNull(startIndex)
        val idx = playable.indexOfFirst { it.id == startItem?.id }.coerceAtLeast(0)
        radioActive = radio
        withController { c ->
            c.setMediaItems(playable.map(::toMediaItem), idx, resumePos(playable[idx]))
            c.shuffleModeEnabled = shuffle
            c.repeatMode = if (radio) Player.REPEAT_MODE_OFF else c.repeatMode
            c.prepare()
            c.play()
        }
    }

    fun startRadio(mode: RadioMode, playlistId: Long = -1, onEmpty: () -> Unit = {}) {
        scope.launch {
            val items = kotlinx.coroutines.withContext(Dispatchers.IO) { RadioEngine.start(mode, playlistId) }
            if (items.isEmpty()) onEmpty() else play(items, 0, radio = true)
        }
    }

    private fun maybeRefillRadio(p: Player) {
        if (!radioActive || refilling) return
        if (p.mediaItemCount - p.currentMediaItemIndex > 3) return
        refilling = true
        scope.launch {
            val more = kotlinx.coroutines.withContext(Dispatchers.IO) { RadioEngine.next(6) }
            withController { c -> c.addMediaItems(more.filter { it.localPath.isNotEmpty() }.map(::toMediaItem)) }
            refilling = false
        }
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() } }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { c -> if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipBy(ms: Long) = withController { c -> c.seekTo((c.currentPosition + ms).coerceAtLeast(0)) }
    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun setSpeed(s: Float) = withController { it.setPlaybackSpeed(s) }
    fun jumpTo(index: Int) = withController { c -> c.seekToDefaultPosition(index); c.play() }
    fun removeFromQueue(index: Int) = withController { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }

    fun enqueue(item: Item, playNext: Boolean = false) {
        if (item.localPath.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(toMediaItem(item)); c.prepare(); c.play()
            } else if (playNext) c.addMediaItem(c.currentMediaItemIndex + 1, toMediaItem(item))
            else c.addMediaItem(toMediaItem(item))
        }
    }

    fun stop() {
        radioActive = false
        withController { c -> c.stop(); c.clearMediaItems() }
    }

    fun applyEqualizer() = withController { it.sendCustomCommand(SessionCommand(CMD_EQ, Bundle.EMPTY), Bundle.EMPTY) }
}
