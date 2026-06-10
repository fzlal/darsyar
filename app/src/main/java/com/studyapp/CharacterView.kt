package com.studyapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class CharacterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isStudying: Boolean = false
        set(v) { field = v; resetAnimations(); invalidate() }

    private val skin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(253, 220, 181) }
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(74, 55, 40) }
    private val eyeColor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 27, 14) }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 255, 153, 153) }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 100, 80); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val shirt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(124, 58, 237) }
    private val deskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(139, 94, 60) }
    private val deskDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 70, 40) }
    private val bookCover = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(74, 144, 217) }
    private val bookPage = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 255, 255) }
    private val pencil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 193, 7) }
    private val pencilTip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 140, 0) }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(167, 139, 250); isFakeBoldText = true
    }
    private val pillow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 180, 220) }

    private var blinkProgress = 0f
    private var zzzPhase = 0f
    private var studyBob = 0f

    private val blinkAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        repeatMode = ValueAnimator.REVERSE
        repeatCount = 1
        addUpdateListener { blinkProgress = it.animatedValue as Float; invalidate() }
    }
    private val zzzAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { zzzPhase = it.animatedValue as Float; invalidate() }
    }
    private val bobAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { studyBob = it.animatedValue as Float; invalidate() }
    }

    private var blinkTimer: Long = 0L

    init {
        zzzAnim.start()
        bobAnim.pause()
    }

    private fun resetAnimations() {
        if (isStudying) {
            zzzAnim.pause()
            bobAnim.resume()
        } else {
            bobAnim.pause()
            zzzAnim.resume()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        scale = minOf(w, h) / 400f
        canvas.scale(scale, scale)
        val cx = w / (2f * scale)
        val baseY = h / scale - 30f

        val now = System.currentTimeMillis()
        if (now - blinkTimer > 3500) {
            blinkTimer = now
            blinkAnim.start()
        }

        if (isStudying) drawStudying(canvas, cx, baseY)
        else drawSleeping(canvas, cx, baseY)
    }

    private var scale = 1f

    private fun drawStudying(canvas: Canvas, cx: Float, by: Float) {
        val deskTop = by - 75f
        val bob = sin(studyBob * 4 * Math.PI.toFloat()) * 2f

        canvas.drawRoundRect(cx - 100, deskTop, cx + 100, deskTop + 12, 4f, 4f, deskPaint)
        canvas.drawRect(cx - 85, deskTop + 12, cx - 70, deskTop + 55, deskDark)
        canvas.drawRect(cx + 70, deskTop + 12, cx + 85, deskTop + 55, deskDark)

        val bodyTop = deskTop - 60f + bob
        canvas.drawRoundRect(cx - 28, bodyTop, cx + 28, deskTop, 10f, 10f, shirt)

        val headCenterY = bodyTop - 30f
        canvas.drawCircle(cx, headCenterY, 40f, skin)

        val hairPath = Path().apply {
            moveTo(cx - 42, headCenterY - 5)
            quadTo(cx - 35, headCenterY - 48, cx, headCenterY - 48)
            quadTo(cx + 35, headCenterY - 48, cx + 42, headCenterY - 5)
            quadTo(cx + 38, headCenterY - 25, cx + 30, headCenterY - 30)
            quadTo(cx + 35, headCenterY - 40, cx + 25, headCenterY - 42)
            quadTo(cx + 18, headCenterY - 45, cx, headCenterY - 44)
            quadTo(cx - 18, headCenterY - 45, cx - 25, headCenterY - 42)
            quadTo(cx - 35, headCenterY - 40, cx - 30, headCenterY - 30)
            quadTo(cx - 38, headCenterY - 25, cx - 42, headCenterY - 5)
            close()
        }
        canvas.drawPath(hairPath, hair)

        canvas.drawCircle(cx - 24, headCenterY + 6, 8f, blush)
        canvas.drawCircle(cx + 24, headCenterY + 6, 8f, blush)

        val eyeH = 5f - blinkProgress * 4f
        if (eyeH > 1f) {
            canvas.drawOval(cx - 15, headCenterY - 5, cx - 7, headCenterY - 5 + eyeH, eyeColor)
            canvas.drawOval(cx + 7, headCenterY - 5, cx + 15, headCenterY - 5 + eyeH, eyeColor)
            val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(cx - 12, headCenterY - 5 + eyeH * 0.3f, 2.5f, shine)
            canvas.drawCircle(cx + 12, headCenterY - 5 + eyeH * 0.3f, 2.5f, shine)
        } else {
            canvas.drawLine(cx - 17, headCenterY - 3, cx - 5, headCenterY - 3, eyeColor.apply { strokeWidth = 2.5f })
            canvas.drawLine(cx + 5, headCenterY - 3, cx + 17, headCenterY - 3, eyeColor.apply { strokeWidth = 2.5f })
            eyeColor.strokeWidth = 3f
        }

        canvas.drawArc(cx - 8, headCenterY + 8, cx + 8, headCenterY + 18, 0f, -180f, false, mouthPaint)

        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skin.color; strokeWidth = 14f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx - 35, bodyTop + 15, cx - 30, deskTop + 6, armPaint)
        canvas.drawLine(cx + 35, bodyTop + 15, cx + 30, deskTop + 6, armPaint)

        val bookLeft = cx - 35; val bookTop = deskTop - 25f; val bookW = 70f; val bookH = 30f
        canvas.drawRoundRect(bookLeft, bookTop, bookLeft + bookW, bookTop + bookH, 4f, 4f, bookCover)
        canvas.drawRect(bookLeft + 4, bookTop + 3, bookLeft + bookW - 4, bookTop + bookH - 3, bookPage)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 180, 180); strokeWidth = 2f }
        for (i in 0..2) { val ly = bookTop + 8 + i * 8; canvas.drawLine(bookLeft + 8, ly, bookLeft + bookW - 20, ly, linePaint) }

        canvas.save()
        canvas.rotate(-20f, cx + 30, deskTop + 6)
        canvas.drawRoundRect(cx + 25, deskTop - 12, cx + 38, deskTop + 6, 3f, 3f, pencil)
        canvas.drawRect(cx + 25, deskTop - 16, cx + 31, deskTop - 12, pencilTip)
        canvas.restore()
    }

    private fun drawSleeping(canvas: Canvas, cx: Float, by: Float) {
        val deskTop = by - 75f
        val headY = deskTop - 5f

        canvas.drawRoundRect(cx - 100, deskTop, cx + 100, deskTop + 12, 4f, 4f, deskPaint)
        canvas.drawRect(cx - 85, deskTop + 12, cx - 70, deskTop + 55, deskDark)
        canvas.drawRect(cx + 70, deskTop + 12, cx + 85, deskTop + 55, deskDark)

        canvas.drawRoundRect(cx - 45, deskTop - 15, cx + 45, deskTop + 2, 10f, 10f, pillow)

        val bodyTop = deskTop - 35f
        canvas.drawRoundRect(cx - 30, bodyTop, cx + 30, deskTop, 12f, 12f, shirt)

        canvas.drawCircle(cx + 5, headY - 15, 38f, skin)

        val hairPath = Path().apply {
            moveTo(cx - 35, headY - 25)
            quadTo(cx - 30, headY - 55, cx + 5, headY - 58)
            quadTo(cx + 38, headY - 55, cx + 42, headY - 25)
            quadTo(cx + 38, headY - 40, cx + 30, headY - 42)
            quadTo(cx + 32, headY - 48, cx + 20, headY - 50)
            quadTo(cx + 10, headY - 52, cx + 5, headY - 50)
            quadTo(cx - 10, headY - 52, cx - 18, headY - 48)
            quadTo(cx - 28, headY - 46, cx - 25, headY - 38)
            quadTo(cx - 32, headY - 35, cx - 35, headY - 25)
            close()
        }
        canvas.drawPath(hairPath, hair)

        canvas.drawCircle(cx - 18, headY - 12, 9f, blush)
        canvas.drawCircle(cx + 28, headY - 12, 9f, blush)

        val sleepEye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eyeColor.color; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
        canvas.drawArc(cx - 18, headY - 22, cx - 6, headY - 10, 180f, 180f, false, sleepEye)
        canvas.drawArc(cx + 10, headY - 22, cx + 22, headY - 10, 180f, 180f, false, sleepEye)

        canvas.drawCircle(cx + 5, headY - 2, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 100, 80); style = Paint.Style.STROKE; strokeWidth = 2f
        })

        val zBase = deskTop - 65f
        val zColor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 139, 250); isFakeBoldText = true }
        val phase = zzzPhase
        for (i in 0..2) {
            val p = (phase + i * 0.33f) % 1f
            val alpha = (255 * (1f - p)).toInt().coerceIn(0, 255)
            val yOff = -p * 90f
            val xOff = sin((p + i) * 2f) * 15f
            val size = 20f + p * 20f
            zColor.textSize = size; zColor.alpha = alpha
            val text = when (i) { 0 -> "z"; 1 -> "Z"; else -> "Z" }
            canvas.drawText(text, cx + 20 + xOff, zBase + yOff + i * 15f, zColor)
        }
    }
}
