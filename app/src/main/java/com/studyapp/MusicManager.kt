package com.studyapp

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

class MusicManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    var playlist: MutableList<Song> = mutableListOf()
    var currentIndex: Int = -1
    var isPlaying: Boolean = false
        private set
    var onSongChange: ((Song) -> Unit)? = null
    var onProgress: ((Int, Int) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onPlayStateChange: ((Boolean) -> Unit)? = null

    fun scanAssets(): List<Song> {
        playlist.clear()
        try {
            val assets = context.assets
            val files = assets.list("music") ?: return playlist
            for (f in files.sorted()) {
                val lower = f.lowercase()
                if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a")) {
                    try {
                        val stream = assets.open("music/$f")
                        val tags = ID3Parser.parse(f, stream)
                        stream.close()
                        val title = tags.title?.takeIf { it.isNotBlank() }
                            ?: f.removeSuffix(".mp3").removeSuffix(".wav")
                                .removeSuffix(".ogg").removeSuffix(".m4a")
                                .replace("_", " ").replace("-", " ")
                        val artist = tags.artist?.takeIf { it.isNotBlank() } ?: "نامشخص"
                        playlist.add(Song(title.trim(), artist.trim(), f))
                    } catch (_: Exception) {
                        val name = f.removeSuffix(".mp3").removeSuffix(".wav")
                            .removeSuffix(".ogg").removeSuffix(".m4a")
                            .replace("_", " ").replace("-", " ")
                        playlist.add(Song(name, "نامشخص", f))
                    }
                }
            }
        } catch (_: Exception) {}
        return playlist
    }

    fun play(index: Int) {
        if (index !in playlist.indices) return
        stop()
        currentIndex = index
        try {
            val fd = context.assets.openFd("music/${playlist[index].fileName}")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                prepare()
                fd.close()
                start()
                isPlaying = true
                playlist[index].duration = duration / 1000
                onSongChange?.invoke(playlist[index])
                onPlayStateChange?.invoke(true)

                setOnCompletionListener {
                    isPlaying = false
                    onPlayStateChange?.invoke(false)
                    onCompletion?.invoke()
                }

                object : Thread() {
                    override fun run() {
                        while (isPlaying && mediaPlayer != null) {
                            try {
                                val pos = mediaPlayer?.currentPosition ?: return
                                val dur = mediaPlayer?.duration ?: return
                                if (dur > 0) {
                                    onProgress?.invoke(pos / 1000, dur / 1000)
                                }
                                Thread.sleep(500)
                            } catch (_: Exception) { break }
                        }
                    }
                }.start()
            }
        } catch (_: Exception) {
            mediaPlayer = null
            isPlaying = false
            onPlayStateChange?.invoke(false)
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
        } else {
            mp.start()
            isPlaying = true
        }
        onPlayStateChange?.invoke(isPlaying)
    }

    fun seekTo(sec: Int) {
        mediaPlayer?.seekTo(sec * 1000)
    }

    fun next() {
        if (playlist.isEmpty()) return
        val next = (currentIndex + 1) % playlist.size
        play(next)
    }

    fun prev() {
        if (playlist.isEmpty()) return
        val prev = if (currentIndex <= 0) playlist.size - 1 else currentIndex - 1
        play(prev)
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        onPlayStateChange?.invoke(false)
    }
}
