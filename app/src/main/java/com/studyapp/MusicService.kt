package com.studyapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import java.io.ByteArrayOutputStream

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
                        try {
                            context.assets.open("music/$name").use { input ->
                                File(musicDir, name).outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            val files = musicDir.listFiles()
                ?.filter { it.extension in setOf("mp3", "wav", "ogg", "m4a") }
                ?.sortedBy { it.name } ?: return emptyList()

            val arts = mutableMapOf<String, Bitmap>()

            return files.map { file ->
                var title = file.nameWithoutExtension
                    .replace("_", " ").replace("-", " ").trim()
                var artist = "نامشخص"

                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val a = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val artBytes = retriever.embeddedPicture
                    if (!t.isNullOrBlank()) title = t
                    if (!a.isNullOrBlank()) artist = a
                    if (artBytes != null) {
                        arts[file.name] = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    }
                    retriever.release()
                } catch (_: Exception) {}

                val art = arts[file.name]
                MediaItem.Builder()
                    .setMediaId(file.name)
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .apply {
                                art?.let { b ->
                                    val bs = ByteArrayOutputStream()
                                    b.compress(Bitmap.CompressFormat.JPEG, 80, bs)
                                    setArtworkData(bs.toByteArray())
                                }
                            }
                            .build()
                    )
                    .build()
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()

        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "موزیک پلیر", NotificationManager.IMPORTANCE_LOW)
            )
        }

        player = ExoPlayer.Builder(this).build()

        session = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }, PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForeground: Boolean): Unit {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(session.player.currentMediaItem?.mediaMetadata?.title ?: "در حال پخش")
            .setContentText(session.player.currentMediaItem?.mediaMetadata?.artist ?: "")
            .setOngoing(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionCompatToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "قبلی", null)
            .addAction(
                if (session.player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (session.player.isPlaying) "مکث" else "پخش", null
            )
            .addAction(android.R.drawable.ic_media_next, "بعدی", null)

        if (startInForeground) {
            startForeground(NOTIFICATION_ID, builder.build())
        } else {
            stopForeground(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, builder.build())
        }
    }

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }
}

class MusicPlayer(context: Context) {

    private var controller: MediaController? = null
    private var _mediaItems: List<MediaItem> = emptyList()
    private val albumArts = mutableMapOf<Int, Bitmap?>()
    private var progressRunning = true

    var onMediaItemsLoaded: ((List<MediaItem>) -> Unit)? = null
    var onSongChange: ((title: String, artist: String, art: Bitmap?) -> Unit)? = null
    var onProgress: ((currentSec: Int, totalSec: Int) -> Unit)? = null
    var onPlayStateChange: ((isPlaying: Boolean) -> Unit)? = null
    var onShuffleChange: ((enabled: Boolean) -> Unit)? = null
    var onRepeatChange: ((mode: Int) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayStateChange?.invoke(isPlaying)
            updateCurrentSong()
        }
        override fun onMediaItemTransition(index: Int, reason: Int) {
            updateCurrentSong()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            onShuffleChange?.invoke(shuffleModeEnabled)
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            onRepeatChange?.invoke(repeatMode)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) updateCurrentSong()
        }
    }

    private fun updateCurrentSong() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        if (idx !in _mediaItems.indices) return
        val meta = _mediaItems[idx].mediaMetadata
        onSongChange?.invoke(
            meta.title?.toString() ?: "بدون عنوان",
            meta.artist?.toString() ?: "نامشخص",
            albumArts[idx]
        )
    }

    fun load(ctx: Context) {
        _mediaItems = MusicService.buildMediaItems(ctx)
        _mediaItems.forEachIndexed { i, item ->
            item.mediaMetadata.artworkData?.let { data ->
                albumArts[i] = BitmapFactory.decodeByteArray(data, 0, data.size)
            }
        }
        onMediaItemsLoaded?.invoke(_mediaItems)
        connect(ctx)
    }

    private fun connect(ctx: Context) {
        val future = MediaController.Builder(ctx).buildAsync()
        future.addListener({
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
                        val pos = (c.currentPosition / 1000).toInt()
                        val dur = (c.duration / 1000).toInt()
                        if (dur > 0) onProgress?.invoke(pos, dur)
                        try { Thread.sleep(500) } catch (_: Exception) { break }
                    }
                }
            }.start()
        }, {})
    }

    fun getMediaItems(): List<MediaItem> = _mediaItems
    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun play(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun prev() { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
    fun seekTo(sec: Int) { controller?.seekTo(sec * 1000L) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun isPlaying(): Boolean = controller?.isPlaying ?: false
    fun getShuffle(): Boolean = controller?.shuffleModeEnabled ?: false
    fun getRepeat(): Int = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
    fun getCurrentIndex(): Int = controller?.currentMediaItemIndex ?: 0

    fun release() {
        progressRunning = false
        controller?.removeListener(listener)
        controller?.release()
    }
}
