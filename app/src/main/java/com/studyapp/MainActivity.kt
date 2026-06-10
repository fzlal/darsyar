package com.studyapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var storage: Storage
    private lateinit var musicPlayer: MusicPlayer

    private lateinit var characterView: CharacterView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pages: Map<Int, View>

    // Timer
    private lateinit var timerDisplay: TextView
    private lateinit var timerSubject: EditText
    private var timerSeconds = 0
    private var timerRunning = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    // Tasks
    private lateinit var taskInput: EditText
    private lateinit var taskPriority: Spinner
    private lateinit var taskAdapter: TaskAdapter

    // Stats
    private lateinit var statToday: TextView
    private lateinit var statTotal: TextView
    private lateinit var statSessions: TextView
    private lateinit var statDone: TextView

    // Music
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var albumArtView: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var songAdapter: SongAdapter
    private var isSeeking = false
    private var lastDuration = 1

    private val persianFormatter = NumberFormat.getInstance(Locale("fa"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = 0xFF0f0f1a.toInt()
        window.navigationBarColor = 0xFF0f0f1a.toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        storage = Storage(this)
        musicPlayer = MusicPlayer(this)
        initViews()
        setupTimer()
        setupTasks()
        setupStats()
        setupMusic()
        switchPage(R.id.nav_timer)
        bottomNav.selectedItemId = R.id.nav_timer
        bottomNav.setOnItemSelectedListener { item ->
            switchPage(item.itemId)
            true
        }
    }

    override fun onDestroy() {
        musicPlayer.release()
        super.onDestroy()
    }

    private fun initViews() {
        characterView = findViewById(R.id.characterView)
        bottomNav = findViewById(R.id.bottomNav)
        timerDisplay = findViewById(R.id.timerDisplay)
        timerSubject = findViewById(R.id.timerSubject)
        taskInput = findViewById(R.id.taskInput)
        taskPriority = findViewById(R.id.taskPriority)
        statToday = findViewById(R.id.statToday)
        statTotal = findViewById(R.id.statTotal)
        statSessions = findViewById(R.id.statSessions)
        statDone = findViewById(R.id.statDone)
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle)
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist)
        albumArtView = findViewById(R.id.albumArt)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        pages = mapOf(
            R.id.nav_timer to findViewById(R.id.pageTimer),
            R.id.nav_tasks to findViewById(R.id.pageTasks),
            R.id.nav_stats to findViewById(R.id.pageStats),
            R.id.nav_music to findViewById(R.id.pageMusic)
        )

        ArrayAdapter.createFromResource(this, R.array.priority_levels, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            taskPriority.adapter = it
        }

        bottomNav.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun switchPage(id: Int) {
        pages.forEach { (key, view) -> view.visibility = if (key == id) View.VISIBLE else View.GONE }
        characterView.visibility = if (id == R.id.nav_timer) View.VISIBLE else View.GONE
        if (id == R.id.nav_stats) updateStats()
    }

    // ==================== TIMER ====================

    private fun setupTimer() {
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!timerRunning) {
                timerRunning = true
                characterView.isStudying = true
                timerHandler.post(timerRunnable)
                toast("⏱ زمان مطالعه شروع شد")
            }
        }
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            if (timerRunning) {
                timerRunning = false
                characterView.isStudying = false
                timerHandler.removeCallbacks(timerRunnable)
            }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            if (timerSeconds == 0) { toast("⚠️ زمانی ثبت نشده"); return@setOnClickListener }
            timerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
            characterView.isStudying = false
            val subj = timerSubject.text.toString().trim().ifEmpty { "بدون عنوان" }
            val session = StudySession(System.currentTimeMillis(), subj, timerSeconds, persianDate())
            val list = storage.loadSessions().toMutableList()
            list.add(session)
            storage.saveSessions(list)
            toast("✅ جلسه \"$subj\" ثبت شد")
            timerSeconds = 0
            updateTimerDisplay()
            timerSubject.text.clear()
            loadSessions()
            updateStats()
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            timerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
            timerSeconds = 0
            updateTimerDisplay()
            characterView.isStudying = false
            toast("🔄 ریست شد")
        }
        timerRunnable = object : Runnable {
            override fun run() {
                if (timerRunning) {
                    timerSeconds++
                    updateTimerDisplay()
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        loadSessions()
    }

    private fun updateTimerDisplay() {
        timerDisplay.text = String.format("%02d:%02d:%02d", timerSeconds / 3600, (timerSeconds % 3600) / 60, timerSeconds % 60)
    }

    private fun loadSessions() {
        val today = persianDate()
        val sessions = storage.loadSessions().filter { it.date == today }.reversed()
        findViewById<RecyclerView>(R.id.sessionList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = SessionAdapter(sessions) { id ->
                val list = storage.loadSessions().toMutableList()
                list.removeAll { it.id == id }; storage.saveSessions(list)
                loadSessions(); updateStats()
            }
        }
    }

    // ==================== TASKS ====================

    private fun setupTasks() {
        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val text = taskInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val prio = when (taskPriority.selectedItemPosition) { 0 -> "high"; 1 -> "medium"; else -> "low" }
            val list = storage.loadTasks().toMutableList()
            list.add(0, StudyTask(System.currentTimeMillis(), text, prio, false))
            storage.saveTasks(list)
            taskInput.text.clear(); loadTasks()
        }
        taskInput.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) { findViewById<Button>(R.id.btnAddTask).performClick(); true } else false
        }
        loadTasks()
    }

    private fun loadTasks() {
        val tasks = storage.loadTasks()
        findViewById<RecyclerView>(R.id.taskList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TaskAdapter(tasks.toMutableList(),
                { id, done -> val l = storage.loadTasks().toMutableList(); l.replaceAll { if (it.id == id) it.copy(done = done) else it }; storage.saveTasks(l); loadTasks(); updateStats() },
                { id -> val l = storage.loadTasks().toMutableList(); l.removeAll { it.id == id }; storage.saveTasks(l); loadTasks(); updateStats() }
            )
        }
    }

    // ==================== STATS ====================

    private fun setupStats() { updateStats() }

    private fun updateStats() {
        val sessions = storage.loadSessions()
        val tasks = storage.loadTasks()
        val today = persianDate()
        statToday.text = "${faNum(sessions.filter { it.date == today }.sumOf { it.durationSeconds } / 60)} دقیقه"
        statTotal.text = "${faNum(sessions.sumOf { it.durationSeconds } / 60)} دقیقه"
        statSessions.text = faNum(sessions.count { it.date == today })
        statDone.text = faNum(tasks.count { it.done })
    }

    // ==================== MUSIC ====================

    private fun setupMusic() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) isSeeking = true }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                musicPlayer.seekTo(sb!!.progress * lastDuration / 100)
                isSeeking = false
            }
        })
        findViewById<View>(R.id.btnPrev).setOnClickListener { musicPlayer.prev() }
        btnPlayPause.setOnClickListener { musicPlayer.togglePlayPause() }
        findViewById<View>(R.id.btnNext).setOnClickListener { musicPlayer.next() }
        btnShuffle.setOnClickListener { musicPlayer.toggleShuffle(); updateShuffleIcon() }
        btnRepeat.setOnClickListener { musicPlayer.cycleRepeat(); updateRepeatIcon() }

        musicPlayer.onSongChange = { title, artist, art ->
            nowPlayingTitle.text = title
            nowPlayingArtist.text = artist
            albumArtView.setImageBitmap(art)
            if (art == null) albumArtView.setImageResource(android.R.drawable.ic_media_play)
            seekBar.progress = 0
            currentTime.text = "00:00"
            songAdapter?.notifyDataSetChanged()
        }
        musicPlayer.onProgress = { pos, dur ->
            lastDuration = dur.coerceAtLeast(1)
            if (!isSeeking) { seekBar.progress = if (dur > 0) pos * 100 / dur else 0 }
            currentTime.text = formatTime(pos)
            totalTime.text = formatTime(dur)
        }
        musicPlayer.onPlayStateChange = { playing ->
            btnPlayPause.setImageResource(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            songAdapter?.notifyDataSetChanged()
        }
        musicPlayer.onShuffleChange = { updateShuffleIcon() }
        musicPlayer.onRepeatChange = { updateRepeatIcon() }
        musicPlayer.onMediaItemsLoaded = { items ->
            songAdapter = SongAdapter(items, musicPlayer) { idx -> musicPlayer.play(idx) }
            findViewById<RecyclerView>(R.id.songList).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = songAdapter
            }
        }
        musicPlayer.load(this)
    }

    private fun updateShuffleIcon() {
        btnShuffle.setColorFilter(if (musicPlayer.getShuffle()) 0xFFa78bfa.toInt() else 0xFF6b7280.toInt())
    }

    private fun updateRepeatIcon() {
        val color = when (musicPlayer.getRepeat()) {
            Player.REPEAT_MODE_ONE -> 0xFF10b981.toInt()
            Player.REPEAT_MODE_ALL -> 0xFFa78bfa.toInt()
            else -> 0xFF6b7280.toInt()
        }
        btnRepeat.setColorFilter(color)
    }

    // ==================== HELPERS ====================

    private fun persianDate(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}/${faNum(cal.get(Calendar.MONTH) + 1)}/${faNum(cal.get(Calendar.DAY_OF_MONTH))}"
    }

    private fun faNum(n: Int): String = persianFormatter.format(n.toLong())
    private fun formatTime(sec: Int): String = String.format("%02d:%02d", sec / 60, sec % 60)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ==================== ADAPTERS ====================

    inner class SessionAdapter(
        private val data: List<StudySession>,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(p: Int) = if (data.isEmpty()) 0 else 1
        override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
            if (t == 0) {
                val tv = TextView(this@MainActivity).apply {
                    text = "📭 هنوز جلسه‌ای ثبت نشده"; setTextColor(0xFF94a3b8.toInt())
                    gravity = Gravity.CENTER; layoutParams = RecyclerView.LayoutParams(-1, -2).apply { topMargin = 16 }
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            val v = layoutInflater.inflate(R.layout.item_session, p, false)
            return object : RecyclerView.ViewHolder(v) {
                val subject: TextView = v.findViewById(android.R.id.text1)
                val duration: TextView = v.findViewById(android.R.id.text2)
                val del: ImageButton = v.findViewById(android.R.id.button1)
            }
        }
        override fun getItemCount() = if (data.isEmpty()) 1 else data.size
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
            if (data.isEmpty()) return
            val s = data[i]
            val subj = h.itemView.findViewById<TextView>(android.R.id.text1) ?: return
            val dur = h.itemView.findViewById<TextView>(android.R.id.text2)
            val del = h.itemView.findViewById<ImageButton>(android.R.id.button1)
            subj.text = s.subject
            dur?.text = "${s.durationSeconds / 3600}:${String.format("%02d", (s.durationSeconds % 3600) / 60)}:${String.format("%02d", s.durationSeconds % 60)}"
            del?.setOnClickListener { onDelete(s.id) }
        }
    }

    inner class TaskVH(v: View) : RecyclerView.ViewHolder(v) {
        val toggle: CheckBox = v.findViewById(android.R.id.checkbox)
        val text: TextView = v.findViewById(android.R.id.text1)
        val badge: TextView = v.findViewById(android.R.id.text2)
        val del: ImageButton = v.findViewById(android.R.id.button1)
    }

    inner class TaskAdapter(
        private val data: MutableList<StudyTask>,
        private val onToggle: (Long, Boolean) -> Unit,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<TaskVH>() {
        override fun onCreateViewHolder(p: ViewGroup, i: Int): TaskVH = TaskVH(layoutInflater.inflate(R.layout.item_task, p, false))
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: TaskVH, i: Int) {
            val t = data[i]
            h.text.text = t.text
            h.text.paintFlags = if (t.done) android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else 0
            h.toggle.isChecked = t.done
            h.toggle.setOnCheckedChangeListener { _, checked -> onToggle(t.id, checked) }
            h.badge.text = when (t.priority) { "high" -> "بالا"; "medium" -> "متوسط"; else -> "پایین" }
            h.badge.setTextColor(when (t.priority) { "high" -> 0xFFef4444.toInt(); "medium" -> 0xFFf59e0b.toInt(); else -> 0xFF10b981.toInt() })
            h.del.setOnClickListener { onDelete(t.id) }
        }
    }

    inner class SongVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val artist: TextView = v.findViewById(android.R.id.text2)
        val playing: View = v.findViewById(android.R.id.icon)
    }

    inner class SongAdapter(
        private val data: List<androidx.media3.common.MediaItem>,
        private val player: MusicPlayer,
        private val onPlay: (Int) -> Unit
    ) : RecyclerView.Adapter<SongVH>() {
        override fun onCreateViewHolder(p: ViewGroup, i: Int): SongVH = SongVH(layoutInflater.inflate(R.layout.item_song, p, false))
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: SongVH, i: Int) {
            val meta = data[i].mediaMetadata
            h.title.text = meta.title?.toString() ?: "بدون عنوان"
            h.artist.text = meta.artist?.toString() ?: "نامشخص"
            h.playing.visibility = if (i == player.getCurrentIndex()) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener { onPlay(i) }
        }
    }
}
