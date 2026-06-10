package com.studyapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var storage: Storage
    private lateinit var musicManager: MusicManager

    // Views
    private lateinit var characterView: CharacterView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var container: FrameLayout
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

    // Quiz
    private lateinit var quizSetup: View
    private lateinit var quizActive: View
    private lateinit var quizCount: NumberPicker
    private lateinit var quizDifficulty: Spinner
    private lateinit var quizProgress: TextView
    private lateinit var quizScore: TextView
    private lateinit var quizQuestion: TextView
    private lateinit var quizOptions: LinearLayout
    private lateinit var quizResult: TextView
    private lateinit var btnQuizNext: Button
    private lateinit var btnQuizRestart: Button
    private var quizState = QuizState()

    // Stats
    private lateinit var statToday: TextView
    private lateinit var statTotal: TextView
    private lateinit var statSessions: TextView
    private lateinit var statDone: TextView
    private lateinit var statBest: TextView
    private lateinit var statAvg: TextView

    // Music
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var songAdapter: SongAdapter
    private var isSeeking = false

    private val persianFormatter = NumberFormat.getInstance(Locale("fa"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = 0xFF0f0f1a.toInt()
        window.navigationBarColor = 0xFF0f0f1a.toInt()

        storage = Storage(this)
        musicManager = MusicManager(this)
        initViews()
        setupTimer()
        setupTasks()
        setupQuiz()
        setupStats()
        setupMusic()
        switchPage(R.id.nav_timer)
        bottomNav.selectedItemId = R.id.nav_timer
        bottomNav.setOnItemSelectedListener { item ->
            switchPage(item.itemId)
            true
        }
    }

    private fun initViews() {
        container = findViewById(R.id.container)
        characterView = findViewById(R.id.characterView)
        bottomNav = findViewById(R.id.bottomNav)
        timerDisplay = findViewById(R.id.timerDisplay)
        timerSubject = findViewById(R.id.timerSubject)
        taskInput = findViewById(R.id.taskInput)
        taskPriority = findViewById(R.id.taskPriority)
        quizSetup = findViewById(R.id.quizSetup)
        quizActive = findViewById(R.id.quizActive)
        quizCount = findViewById(R.id.quizCount)
        quizDifficulty = findViewById(R.id.quizDifficulty)
        quizProgress = findViewById(R.id.quizProgress)
        quizScore = findViewById(R.id.quizScore)
        quizQuestion = findViewById(R.id.quizQuestion)
        quizOptions = findViewById(R.id.quizOptions)
        quizResult = findViewById(R.id.quizResult)
        btnQuizNext = findViewById(R.id.btnQuizNext)
        btnQuizRestart = findViewById(R.id.btnQuizRestart)
        statToday = findViewById(R.id.statToday)
        statTotal = findViewById(R.id.statTotal)
        statSessions = findViewById(R.id.statSessions)
        statDone = findViewById(R.id.statDone)
        statBest = findViewById(R.id.statBest)
        statAvg = findViewById(R.id.statAvg)
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle)
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        bottomNav.layoutDirection = View.LAYOUT_DIRECTION_RTL

        pages = mapOf(
            R.id.nav_timer to findViewById(R.id.pageTimer),
            R.id.nav_tasks to findViewById(R.id.pageTasks),
            R.id.nav_quiz to findViewById(R.id.pageQuiz),
            R.id.nav_stats to findViewById(R.id.pageStats),
            R.id.nav_music to findViewById(R.id.pageMusic)
        )

        ArrayAdapter.createFromResource(
            this, R.array.priority_levels, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            taskPriority.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this, R.array.quiz_difficulty, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            quizDifficulty.adapter = adapter
        }

        quizCount.minValue = 1
        quizCount.maxValue = 20
        quizCount.value = 5
        quizCount.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        try { quizCount.javaClass.getMethod("setTextColor", Int::class.java).invoke(quizCount, 0xFFf1f5f9.toInt()) }
        catch (_: Exception) {}
    }

    private fun switchPage(id: Int) {
        pages.forEach { (key, view) -> view.visibility = if (key == id) View.VISIBLE else View.GONE }
        characterView.visibility = if (id == R.id.nav_timer) View.VISIBLE else View.GONE
        if (id == R.id.nav_stats) updateStats()
        if (id == R.id.nav_music) loadMusic()
    }

    // ==================== TIMER ====================

    private fun setupTimer() {
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!timerRunning) {
                timerRunning = true
                characterView.isStudying = true
                timerHandler.post(timerRunnable)
                showToast("⏱ زمان مطالعه شروع شد")
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
            if (timerSeconds == 0) { showToast("⚠️ زمانی ثبت نشده"); return@setOnClickListener }
            timerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
            characterView.isStudying = false
            val subj = timerSubject.text.toString().trim().ifEmpty { "بدون عنوان" }
            val session = StudySession(
                id = System.currentTimeMillis(),
                subject = subj,
                durationSeconds = timerSeconds,
                date = persianDate()
            )
            val list = storage.loadSessions().toMutableList()
            list.add(session)
            storage.saveSessions(list)
            showToast("✅ جلسه \"$subj\" ثبت شد")
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
        val h = timerSeconds / 3600
        val m = (timerSeconds % 3600) / 60
        val s = timerSeconds % 60
        timerDisplay.text = String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun loadSessions() {
        val sessions = storage.loadSessions()
        val today = persianDate()
        val todaySessions = sessions.filter { it.date == today }.reversed()
        val rv = findViewById<RecyclerView>(R.id.sessionList)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SessionAdapter(todaySessions) { id ->
            val list = storage.loadSessions().toMutableList()
            list.removeAll { it.id == id }
            storage.saveSessions(list)
            loadSessions()
            updateStats()
        }
    }

    // ==================== TASKS ====================

    private fun setupTasks() {
        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val text = taskInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val prio = when (taskPriority.selectedItemPosition) {
                0 -> "high"; 1 -> "medium"; else -> "low"
            }
            val list = storage.loadTasks().toMutableList()
            list.add(0, StudyTask(System.currentTimeMillis(), text, prio, false))
            storage.saveTasks(list)
            taskInput.text.clear()
            loadTasks()
        }
        taskInput.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                findViewById<Button>(R.id.btnAddTask).performClick(); true
            } else false
        }
        loadTasks()
    }

    private fun loadTasks() {
        val tasks = storage.loadTasks()
        val rv = findViewById<RecyclerView>(R.id.taskList)
        rv.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(tasks.toMutableList(), { id, done ->
            val list = storage.loadTasks().toMutableList()
            list.replaceAll { if (it.id == id) it.copy(done = done) else it }
            storage.saveTasks(list)
            loadTasks()
            updateStats()
        }, { id ->
            val list = storage.loadTasks().toMutableList()
            list.removeAll { it.id == id }
            storage.saveTasks(list)
            loadTasks()
            updateStats()
        })
        rv.adapter = taskAdapter
    }

    // ==================== QUIZ ====================

    private val questions = listOf(
        QuizQuestion("سرعت نور در خلأ چقدر است؟", listOf("۳۰۰,۰۰۰ km/s", "۱۵۰,۰۰۰ km/s", "۵۰۰,۰۰۰ km/s", "۱,۰۰۰,۰۰۰ km/s"), 0),
        QuizQuestion("فرمول شیمیایی آب چیست؟", listOf("CO₂", "H₂O", "NaCl", "O₂"), 1),
        QuizQuestion("کدام سیاره به \"سیاره سرخ\" معروف است؟", listOf("زهره", "مشتری", "مریخ", "زحل"), 2),
        QuizQuestion("کوچکترین واحد ماده چیست؟", listOf("مولکول", "اتم", "الکترون", "پروتون"), 1),
        QuizQuestion("پایتخت ایران کدام شهر است؟", listOf("اصفهان", "مشهد", "تهران", "شیراز"), 2),
        QuizQuestion("نیروی جاذبه توسط چه کسی کشف شد؟", listOf("انیشتین", "نیوتن", "گالیله", "ادیسون"), 1),
        QuizQuestion("طولانی‌ترین رود جهان کدام است؟", listOf("آمازون", "نیل", "میسیسیپی", "یانگ‌تسه"), 1),
        QuizQuestion("عدد پی (π) تقریباً برابر با چیست؟", listOf("۲.۱۴", "۳.۱۴", "۴.۱۴", "۱.۱۴"), 1),
        QuizQuestion("کدام عنصر در طلا وجود دارد؟", listOf("Fe", "Ag", "Au", "Cu"), 2),
        QuizQuestion("کدام حیوان سریع‌ترین است؟", listOf("یوزپلنگ", "شیر", "آهو", "عقاب"), 0),
        QuizQuestion("استخوان‌های بدن انسان بالغ؟", listOf("۱۰۶", "۲۰۶", "۳۰۶", "۴۰۶"), 1),
        QuizQuestion("اولین انسان روی ماه؟", listOf("نیل آرمسترانگ", "باز آلدرین", "یوری گاگارین", "جان گلن"), 0),
        QuizQuestion("بزرگترین سیاره منظومه شمسی؟", listOf("زحل", "مشتری", "نپتون", "اورانوس"), 1),
        QuizQuestion("واحد نیرو چیست؟", listOf("وات", "نیوتن", "ژول", "آمپر"), 1),
        QuizQuestion("عمیق‌ترین نقطه اقیانوس؟", listOf("درازگودال ماریانا", "اقیانوس آرام", "دریای خزر", "خلیج فارس"), 0),
        QuizQuestion("فرمول شیمیایی نمک طعام؟", listOf("KCl", "NaCl", "CaCO₃", "NaHCO₃"), 1),
        QuizQuestion("قلب انسان چند حفره دارد؟", listOf("۲", "۳", "۴", "۵"), 2),
        QuizQuestion("گاز غالب در جو زمین؟", listOf("اکسیژن", "نیتروژن", "CO₂", "هیدروژن"), 1),
        QuizQuestion("نخستین زبان برنامه‌نویسی؟", listOf("Python", "Fortran", "C", "Java"), 1),
        QuizQuestion("بلندترین قله جهان؟", listOf("کی۲", "دماوند", "اورست", "آلپ"), 2),
        QuizQuestion("تعداد رنگین‌کمان؟", listOf("۵", "۶", "۷", "۸"), 2),
        QuizQuestion("واحد جریان الکتریکی؟", listOf("ولت", "آمپر", "اهم", "وات"), 1),
        QuizQuestion("کدام اندام خون را تصفیه می‌کند؟", listOf("قلب", "ریه", "کلیه", "کبد"), 2),
        QuizQuestion("نزدیک‌ترین سیاره به زمین؟", listOf("مریخ", "زهره", "عطارد", "مشتری"), 1),
        QuizQuestion("مخترع تلفن؟", listOf("ادیسون", "تسلا", "بل", "گراهام بل"), 2),
        QuizQuestion("تعداد استان‌های ایران؟", listOf("۲۸", "۳۰", "۳۱", "۳۳"), 2),
        QuizQuestion("عنصر اصلی الماس؟", listOf("اکسیژن", "کربن", "سیلیسیم", "گرافیت"), 1),
        QuizQuestion("سرعت صوت در هوا؟", listOf("۳۴۰ m/s", "۵۰۰ m/s", "۷۰۰ m/s", "۱۰۰۰ m/s"), 0),
        QuizQuestion("واحد انرژی؟", listOf("نیوتن", "پاسکال", "ژول", "وات"), 2),
        QuizQuestion("پر جمعیت‌ترین کشور جهان؟", listOf("آمریکا", "هند", "چین", "اندونزی"), 1),
        QuizQuestion("نزدیک‌ترین ستاره به زمین؟", listOf("قطبی", "خورشید", "سیریوس", "آلفا قنطورس"), 1),
        QuizQuestion("مغز چند٪ اکسیژن مصرف می‌کند؟", listOf("۱۰٪", "۲۰٪", "۳۰٪", "۴۰٪"), 1),
        QuizQuestion("کدام ویتامین از نور خورشید ساخته می‌شود؟", listOf("A", "B", "C", "D"), 3),
        QuizQuestion("طول خط استوا؟", listOf("۲۰,۰۰۰", "۳۰,۰۰۰", "۴۰,۰۰۰", "۵۰,۰۰۰"), 2),
        QuizQuestion("کدام حیوان بیشتر عمر می‌کند؟", listOf("فیل", "لاک‌پشت", "نهنگ", "کوسه"), 1),
        QuizQuestion("تعداد رنگ‌های اصلی نور؟", listOf("۲", "۳", "۴", "۵"), 1),
        QuizQuestion("کدام سیاره حلقه دارد؟", listOf("مشتری", "زحل", "مریخ", "زهره"), 1),
        QuizQuestion("مخترع برق؟", listOf("ادیسون", "تسلا", "فرانکلین", "فارادی"), 2),
        QuizQuestion("واحد فشار؟", listOf("نیوتن", "پاسکال", "بار", "ژول"), 1),
        QuizQuestion("کدام عضو بدن بیشترین آب را دارد؟", listOf("استخوان", "عضله", "مغز", "خون"), 2),
    )

    data class QuizState(
        var questionList: MutableList<QuizQuestion> = mutableListOf(),
        var current: Int = 0,
        var score: Int = 0,
        var answered: Boolean = false
    )

    private fun setupQuiz() {
        findViewById<Button>(R.id.btnQuizStart).setOnClickListener { startQuiz() }
        btnQuizNext.setOnClickListener {
            quizState.current++
            renderQuestion()
        }
        btnQuizRestart.setOnClickListener {
            quizSetup.visibility = View.VISIBLE
            quizActive.visibility = View.GONE
            btnQuizRestart.visibility = View.GONE
            quizResult.visibility = View.GONE
        }
    }

    private fun startQuiz() {
        val count = quizCount.value.coerceIn(1, 20)
        val diff = quizDifficulty.selectedItemPosition
        var pool = questions.toMutableList()
        when (diff) {
            0 -> pool = pool.filterIndexed { i, _ -> i % 3 == 0 }.toMutableList()
            2 -> pool = pool.filterIndexed { i, _ -> i % 3 != 0 }.toMutableList()
        }
        if (pool.size < count) pool = questions.toMutableList()
        pool.shuffle()
        quizState = QuizState(questionList = pool.take(count).toMutableList())
        quizSetup.visibility = View.GONE
        quizActive.visibility = View.VISIBLE
        renderQuestion()
    }

    private fun renderQuestion() {
        val st = quizState
        if (st.current >= st.questionList.size) { finishQuiz(); return }
        val q = st.questionList[st.current]
        quizProgress.text = "سوال ${st.current + 1} از ${st.questionList.size}"
        quizScore.text = "امتیاز: ${faNum(st.score)}"
        quizQuestion.text = q.question
        quizOptions.removeAllViews()
        btnQuizNext.visibility = View.GONE
        quizResult.visibility = View.GONE
        st.answered = false
        for ((i, opt) in q.options.withIndex()) {
            val btn = Button(this).apply {
                text = opt
                setOnClickListener { selectAnswer(i) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                setTextColor(0xFFf1f5f9.toInt())
                minimumHeight = 56
                textSize = 14f
                isAllCaps = false
                typeface = android.graphics.Typeface.DEFAULT
                gravity = Gravity.CENTER
                setPadding(20, 14, 20, 14)
            }
            quizOptions.addView(btn)
        }
        // Apply custom background to options
        for (i in 0 until quizOptions.childCount) {
            val v = quizOptions.getChildAt(i)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF252542.toInt())
                cornerRadius = 12f
                setStroke(2, 0xFF252542.toInt())
            }
            v.background = bg
        }
    }

    private fun selectAnswer(idx: Int) {
        if (quizState.answered) return
        quizState.answered = true
        val q = quizState.questionList[quizState.current]
        for (i in 0 until quizOptions.childCount) {
            val btn = quizOptions.getChildAt(i) as Button
            btn.isEnabled = false
            val bg = btn.background as? android.graphics.drawable.GradientDrawable
            when {
                i == q.answerIndex -> {
                    bg?.setStroke(3, 0xFF10b981.toInt())
                    bg?.setColor(0x2210b981.toInt())
                    btn.setTextColor(0xFF10b981.toInt())
                }
                i == idx -> {
                    bg?.setStroke(3, 0xFFef4444.toInt())
                    bg?.setColor(0x22ef4444.toInt())
                    btn.setTextColor(0xFFef4444.toInt())
                }
            }
        }
        quizResult.visibility = View.VISIBLE
        if (idx == q.answerIndex) {
            quizState.score++
            quizResult.text = "✅ پاسخ صحیح!"
            quizResult.setTextColor(0xFF10b981.toInt())
        } else {
            quizResult.text = "❌ پاسخ نادرست!\nپاسخ صحیح: ${q.options[q.answerIndex]}"
            quizResult.setTextColor(0xFFef4444.toInt())
        }
        quizScore.text = "امتیاز: ${faNum(quizState.score)}"
        if (quizState.current < quizState.questionList.size - 1) {
            btnQuizNext.visibility = View.VISIBLE
        } else {
            btnQuizNext.postDelayed({ finishQuiz() }, 600)
        }
    }

    private fun finishQuiz() {
        val st = quizState
        val pct = if (st.questionList.isEmpty()) 0 else (st.score * 100) / st.questionList.size
        val grade = when {
            pct >= 90 -> "🌟 عالی"
            pct >= 70 -> "👍 خوب"
            pct >= 50 -> "👌 قابل قبول"
            else -> "💪 تلاش بیشتر"
        }
        quizQuestion.text = ""
        quizOptions.removeAllViews()
        btnQuizNext.visibility = View.GONE
        quizResult.visibility = View.VISIBLE
        quizResult.text = "🏁 مسابقه تمام شد!\nامتیاز: ${faNum(st.score)} از ${faNum(st.questionList.size)} (${faNum(pct)}%)\n$grade"
        quizResult.setTextColor(0xFFa78bfa.toInt())
        btnQuizRestart.visibility = View.VISIBLE
        quizProgress.text = "پایان مسابقه"
        val result = QuizResult(st.score, st.questionList.size, System.currentTimeMillis())
        val results = storage.loadQuizResults().toMutableList()
        results.add(result)
        storage.saveQuizResults(results)
        updateStats()
    }

    // ==================== STATS ====================

    private fun setupStats() {
        updateStats()
    }

    private fun updateStats() {
        val sessions = storage.loadSessions()
        val tasks = storage.loadTasks()
        val quizResults = storage.loadQuizResults()
        val today = persianDate()
        val todaySecs = sessions.filter { it.date == today }.sumOf { it.durationSeconds }
        val totalSecs = sessions.sumOf { it.durationSeconds }
        val doneTasks = tasks.count { it.done }

        statToday.text = "${faNum(todaySecs / 60)} دقیقه"
        statTotal.text = "${faNum(totalSecs / 60)} دقیقه"
        statSessions.text = faNum(sessions.count { it.date == today })
        statDone.text = faNum(doneTasks)

        if (quizResults.isNotEmpty()) {
            statBest.text = faNum(quizResults.maxOf { it.score })
            statAvg.text = "${faNum(quizResults.sumOf { it.score * 100 / it.total } / quizResults.size)}%"
        } else {
            statBest.text = "۰"
            statAvg.text = "۰%"
        }
    }

    // ==================== MUSIC ====================

    private fun setupMusic() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) isSeeking = true
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val song = musicManager.playlist.getOrNull(musicManager.currentIndex) ?: return
                val target = sb!!.progress * song.duration / 100
                musicManager.seekTo(target)
                isSeeking = false
            }
        })

        findViewById<View>(R.id.btnPrev).setOnClickListener { musicManager.prev() }
        btnPlayPause.setOnClickListener { musicManager.togglePlayPause() }
        findViewById<View>(R.id.btnNext).setOnClickListener { musicManager.next() }

        musicManager.onSongChange = { song ->
            nowPlayingTitle.text = song.title
            nowPlayingArtist.text = song.artist
            seekBar.progress = 0
            currentTime.text = "00:00"
            totalTime.text = formatTime(song.duration)
            songAdapter?.updateCurrent(musicManager.currentIndex)
        }
        musicManager.onProgress = { pos, dur ->
            if (!isSeeking) {
                val p = if (dur > 0) pos * 100 / dur else 0
                seekBar.progress = p
                currentTime.text = formatTime(pos)
                totalTime.text = formatTime(dur)
            }
        }
        musicManager.onPlayStateChange = { playing ->
            btnPlayPause.setImageResource(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
        musicManager.onCompletion = {
            musicManager.next()
        }
        loadMusic()
    }

    private fun loadMusic() {
        if (musicManager.playlist.isEmpty()) {
            musicManager.scanAssets()
        }
        val rv = findViewById<RecyclerView>(R.id.songList)
        rv.layoutManager = LinearLayoutManager(this)
        songAdapter = SongAdapter(musicManager.playlist, musicManager.currentIndex) { idx ->
            musicManager.play(idx)
        }
        rv.adapter = songAdapter
    }

    // ==================== HELPERS ====================

    private fun persianDate(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "$year/${faNum(month)}/${faNum(day)}"
    }

    private fun faNum(n: Int): String = persianFormatter.format(n.toLong())
    private fun faNum(n: Long): String = persianFormatter.format(n)

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ==================== ADAPTERS ====================

    inner class SessionAdapter(
        private val data: List<StudySession>,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val EMPTY = 0; private val ITEM = 1
        override fun getItemViewType(p: Int) = if (data.isEmpty()) EMPTY else ITEM
        override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
            return if (t == EMPTY) {
                val tv = TextView(this@MainActivity).apply {
                    text = "📭 هنوز جلسه‌ای ثبت نشده"
                    setTextColor(0xFF94a3b8.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = RecyclerView.LayoutParams(-1, -2).apply { topMargin = 16 }
                }
                object : RecyclerView.ViewHolder(tv) {}
            } else {
                val v = layoutInflater.inflate(R.layout.item_session, p, false)
                object : RecyclerView.ViewHolder(v) {
                    val subject: TextView = v.findViewById(android.R.id.text1)
                    val duration: TextView = v.findViewById(android.R.id.text2)
                    val del: ImageButton = v.findViewById(android.R.id.button1)
                }
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
        override fun onCreateViewHolder(p: ViewGroup, i: Int): TaskVH {
            val v = layoutInflater.inflate(R.layout.item_task, p, false)
            return TaskVH(v)
        }
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: TaskVH, i: Int) {
            val t = data[i]
            h.text.text = t.text
            h.text.paintFlags = if (t.done) android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else 0
            h.toggle.isChecked = t.done
            h.toggle.setOnCheckedChangeListener { _, checked -> onToggle(t.id, checked) }
            h.badge.text = when (t.priority) { "high" -> "بالا"; "medium" -> "متوسط"; else -> "پایین" }
            h.badge.setTextColor(
                when (t.priority) { "high" -> 0xFFef4444.toInt(); "medium" -> 0xFFf59e0b.toInt(); else -> 0xFF10b981.toInt() }
            )
            h.del.setOnClickListener { onDelete(t.id) }
        }
    }

    inner class SongVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val artist: TextView = v.findViewById(android.R.id.text2)
        val playing: View = v.findViewById(android.R.id.icon)
    }

    inner class SongAdapter(
        private val data: List<Song>,
        private var currentIdx: Int,
        private val onPlay: (Int) -> Unit
    ) : RecyclerView.Adapter<SongVH>() {
        override fun onCreateViewHolder(p: ViewGroup, i: Int): SongVH {
            val v = layoutInflater.inflate(R.layout.item_song, p, false)
            return SongVH(v)
        }
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: SongVH, i: Int) {
            val s = data[i]
            h.title.text = s.title
            h.artist.text = s.artist
            h.playing.visibility = if (i == musicManager.currentIndex) View.VISIBLE else View.GONE
            h.itemView.setOnClickListener { onPlay(i) }
        }
        fun updateCurrent(idx: Int) { currentIdx = idx; notifyDataSetChanged() }
    }
}
