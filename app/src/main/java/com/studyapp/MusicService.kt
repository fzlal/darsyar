package com.studyapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File

class MusicService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "music_channel"
        const val NOTIFICATION_ID = 1

        fun buildMediaItems(context: Context): List<MediaItem> {
            val musicDir = File(context.filesDir, "music")
            if (!musicDir.exists()) {
                musicDir.mkdirs()
                try {
                    context.assets.list("music")?.forEach { name ->
                        try { context.assets.open("music/$name").use { i -> File(musicDir, name).outputStream().use { o -> i.copyTo(o) } } }
                        catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            val files = musicDir.listFiles()
                ?.filter { it.extension in setOf("mp3", "wav", "ogg", "m4a") }
                ?.sortedBy { it.name } ?: return emptyList()
            return files.map { file ->
                var title = file.nameWithoutExtension.replace("_", " ").replace("-", " ").trim()
                var artist = "نامشخص"
                var artBytes: ByteArray? = null
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(file.absolutePath)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                    artBytes = r.embeddedPicture
                    r.release()
                } catch (_: Exception) {}
                MediaItem.Builder()
                    .setMediaId(file.name)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(title).setArtist(artist).apply {
                            artBytes?.let { b -> val bs = ByteArrayOutputStream(); BitmapFactory.decodeByteArray(b, 0, b.size)?.compress(Bitmap.CompressFormat.JPEG, 80, bs); setArtworkData(bs.toByteArray()) }
                        }.build()
                    ).build()
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NotificationManager::class.java)!!).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "موزیک پلیر", NotificationManager.IMPORTANCE_LOW)
            )
        }
        player = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForeground: Boolean) {
        val meta = session.player.currentMediaItem?.mediaMetadata
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(meta?.title ?: "در حال پخش")
            .setContentText(meta?.artist ?: "")
            .setOngoing(true)
            .setStyle(MediaStyle().setMediaSession(session.sessionCompatToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "قبلی", null)
            .addAction(if (session.player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (session.player.isPlaying) "مکث" else "پخش", null)
            .addAction(android.R.drawable.ic_media_next, "بعدی", null)
        if (startInForeground) startForeground(NOTIFICATION_ID, builder.build())
        else { stopForeground(false); (getSystemService(NotificationManager::class.java)!!).notify(NOTIFICATION_ID, builder.build()) }
    }

    override fun onDestroy() { session.release(); player.release(); super.onDestroy() }
}

class MusicPlayer(private val context: Context) {

    private var controller: MediaController? = null
    private var _mediaItems: List<MediaItem> = emptyList()
    private val albumArts = mutableMapOf<Int, Bitmap?>()
    @Volatile private var progressRunning = true

    var onMediaItemsLoaded: ((List<MediaItem>) -> Unit)? = null
    var onSongChange: ((title: String, artist: String, art: Bitmap?) -> Unit)? = null
    var onProgress: ((currentSec: Int, totalSec: Int) -> Unit)? = null
    var onPlayStateChange: ((isPlaying: Boolean) -> Unit)? = null
    var onShuffleChange: ((enabled: Boolean) -> Unit)? = null
    var onRepeatChange: ((mode: Int) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { onPlayStateChange?.invoke(isPlaying); postUpdate() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { postUpdate() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { onShuffleChange?.invoke(enabled) }
        override fun onRepeatModeChanged(repeatMode: Int) { onRepeatChange?.invoke(repeatMode) }
        override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) postUpdate() }
    }

    private fun postUpdate() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val meta = c.getMediaItemAt(idx).mediaMetadata
        onSongChange?.invoke(meta.title?.toString() ?: "", meta.artist?.toString() ?: "", albumArts[idx])
    }

    fun load(ctx: Context) {
        _mediaItems = MusicService.buildMediaItems(ctx)
        _mediaItems.forEachIndexed { i, item ->
            item.mediaMetadata.artworkData?.let { albumArts[i] = BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        onMediaItemsLoaded?.invoke(_mediaItems)
        val token = SessionToken(ctx, ComponentName(ctx, MusicService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener(Runnable {
            controller = future.get()
            controller?.addListener(listener)
            controller?.let { c ->
                c.setMediaItems(_mediaItems, 0, C.TIME_UNSET)
                c.prepare()
            }
            object : Thread() {
                override fun run() {
                    while (progressRunning) {
                        val c = controller ?: break
                        val p = (c.currentPosition / 1000).toInt()
                        val d = (c.duration / 1000).toInt()
                        if (d > 0) onProgress?.invoke(p, d)
                        try { Thread.sleep(500) } catch (_: Exception) { break }
                    }
                }
            }.start()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun getMediaItems() = _mediaItems
    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun play(idx: Int) { controller?.let { it.seekToDefaultPosition(idx); it.play() } }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun prev() { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
    fun seekTo(sec: Int) { controller?.seekTo(sec * 1000L) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } } }
    fun getCurrentIndex() = controller?.currentMediaItemIndex ?: 0
    fun isPlaying() = controller?.isPlaying ?: false
    fun getShuffle() = controller?.shuffleModeEnabled ?: false
    fun getRepeat() = controller?.repeatMode ?: Player.REPEAT_MODE_OFF

    fun release() { progressRunning = false; controller?.removeListener(listener); controller?.release() }
}
