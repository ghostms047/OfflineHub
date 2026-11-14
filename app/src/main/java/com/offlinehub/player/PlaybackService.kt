package com.offlinehub.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.offlinehub.MainActivity
import com.offlinehub.app
import com.offlinehub.storage.SmartCache
import kotlinx.coroutines.*

const val CMD_EQ = "offlinehub.apply_eq"

/** Background audio with lock-screen / notification media controls (Media3 MediaSession). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saver: Job? = null
    private var eq: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_EQ, Bundle.EMPTY)).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(cmds).build()
                }

                override fun onCustomCommand(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == CMD_EQ) applyEq(player.audioSessionId)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startSaving() else { saver?.cancel(); saveNow() }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                scope.launch(Dispatchers.IO) {
                    app.dao.touch(id, System.currentTimeMillis())
                    app.dao.byId(id)?.let { SmartCache.record(it.type, it.category) }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                val id = oldPosition.mediaItem?.mediaId?.toLongOrNull() ?: return
                val pos = oldPosition.positionMs
                scope.launch(Dispatchers.IO) { app.dao.setPosition(id, pos, 0) }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) = applyEq(audioSessionId)
        })
    }

    private fun applyEq(sessionId: Int) {
        try {
            eq?.release(); eq = null
            if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
            val prefs = EqPrefs(this)
            val e = Equalizer(0, sessionId)
            val levels = prefs.levels()
            for (b in 0 until e.numberOfBands) e.setBandLevel(b.toShort(), (levels.getOrNull(b) ?: 0).toShort())
            e.enabled = prefs.enabled
            eq = e
        } catch (_: Exception) {
            // Equalizer is optional; some devices don't provide the effect.
        }
    }

    private fun startSaving() {
        saver?.cancel()
        saver = scope.launch {
            while (isActive) {
                delay(10_000)
                saveNow()
            }
        }
    }

    private fun saveNow() {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition
        val dur = if (player.duration == C.TIME_UNSET) 0L else player.duration
        scope.launch(Dispatchers.IO) { app.dao.setPosition(id, pos, dur) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveNow()
        scope.cancel()
        eq?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
