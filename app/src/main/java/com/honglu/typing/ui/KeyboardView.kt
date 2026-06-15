package com.honglu.typing.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.honglu.typing.engine.TypingEngine

/**
 * Data model for a single keyboard key.
 */
data class KeyboardKey(
    val label: String,
    val uppercaseLabel: String,
    val widthRatio: Float = 1f,   // relative width multiplier
    val marginLeft: Float = 0f,   // offset from left edge (key widths)
    val isShift: Boolean = false,
    val isTab: Boolean = false,
    val isCapsLock: Boolean = false,
    val isShiftLock: Boolean = false,
    val isEnter: Boolean = false,
    val isBackspace: Boolean = false,
    val isSpace: Boolean = false
) {
    val displayLabel: String
        get() = if (isShift && !isShiftLock) uppercaseLabel else label
}

/**
 * Custom QWERTY keyboard View.
 * Draws key rectangles with labels, supports highlighting the expected key,
 * marking pressed keys, and flashing animation for timeout warning.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 3 rows of keys
    companion object {
        val ROWS = listOf(
            // Row 1: ~ Q W E R T Y U I O P [ ]
            listOf(
                KeyboardKey("`", "`", 0.8f, marginLeft = 0f),
                KeyboardKey("q", "Q"),
                KeyboardKey("w", "W"),
                KeyboardKey("e", "E"),
                KeyboardKey("r", "R"),
                KeyboardKey("t", "T"),
                KeyboardKey("y", "Y"),
                KeyboardKey("u", "U"),
                KeyboardKey("i", "I"),
                KeyboardKey("o", "O"),
                KeyboardKey("p", "P"),
                KeyboardKey("[", "["),
                KeyboardKey("]", "]")
            ),
            // Row 2: Tab A S D F G H J K L ; ' Enter
            listOf(
                KeyboardKey("Tab", "TAB", 1.3f, marginLeft = 0f),
                KeyboardKey("a", "A"),
                KeyboardKey("s", "S"),
                KeyboardKey("d", "D"),
                KeyboardKey("f", "F"),
                KeyboardKey("g", "G"),
                KeyboardKey("h", "H"),
                KeyboardKey("j", "J"),
                KeyboardKey("k", "K"),
                KeyboardKey("l", "L"),
                KeyboardKey(";", ";"),
                KeyboardKey("'", "'"),
                KeyboardKey("Enter", "ENTER", 1.5f)
            ),
            // Row 3: Shift Z X C V B N M , . / Shift
            listOf(
                KeyboardKey("Shift", "SHIFT", 1.6f, marginLeft = 0f),
                KeyboardKey("z", "Z"),
                KeyboardKey("x", "X"),
                KeyboardKey("c", "C"),
                KeyboardKey("v", "V"),
                KeyboardKey("b", "B"),
                KeyboardKey("n", "N"),
                KeyboardKey("m", "M"),
                KeyboardKey(",", ","),
                KeyboardKey(".", "."),
                KeyboardKey("/", "/"),
                KeyboardKey("Shift", "SHIFT", 1.6f)
            )
        )

        // Map key label to char for matching
        fun keyLabelToChar(key: KeyboardKey): Char? {
            if (key.isTab || key.isShift || key.isEnter) return null
            return key.label.firstOrNull()
        }
    }

    // Rendering state
    var highlightedKey: Char? = null
        set(value) {
            field = value
            postInvalidate()
        }

    var pressedKeys: Set<Char> = emptySet()
        set(value) {
            field = value
            postInvalidate()
        }

    private var flashActive = false
    private var flashColor: Int = Color.TRANSPARENT

    // Paint objects
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2C3E50")
    }

    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#34495E")
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F39C12")
    }

    private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#F1C40F")
    }

    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5D6D7E")
    }

    private val wrongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E74C3C")
    }

    private val correctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2ECC71")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isBold = true
    }

    // Layout calculations
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keyGap = 0f
    private var rowStartY = 0f
    private var totalKeyRowWidth = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout(w, h)
    }

    private fun calculateLayout(width: Int, height: Int) {
        val padding = 32f
        val usableWidth = width - padding * 2
        val usableHeight = height - padding * 2 - 60f // leave space for last row

        keyGap = 6f
        val totalRowKeys = 13f // max keys in a row (row 2)

        // Calculate key width based on total width
        val totalKeyWidth = totalRowKeys + 0.3f + 0.3f // extra width for Tab and Enter keys
        keyWidth = (usableWidth - keyGap * (totalRowKeys + 1)) / totalKeyWidth
        keyHeight = keyWidth * 0.65f

        val rowsAvailable = height - padding * 2
        val keyRows = 3
        rowStartY = (rowsAvailable - keyHeight * keyRows - keyGap * (keyRows - 1)) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var currentX = 32f // left padding

        ROWS.forEachIndexed { rowIndex, row ->
            val y = rowStartY + rowIndex * (keyHeight + keyGap)

            row.forEach { key ->
                val drawWidth = keyWidth * key.widthRatio

                // Calculate x position with marginLeft
                var xPos = currentX
                if (key.marginLeft > 0) {
                    xPos = currentX + key.marginLeft * keyWidth + keyGap
                }

                drawKey(canvas, xPos, y, drawWidth, keyHeight, key)

                // Advance cursor
                currentX = xPos + drawWidth + keyGap
            }
        }
    }

    private fun drawKey(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        key: KeyboardKey
    ) {
        val char = keyLabelToChar(key)

        // Determine paint based on state
        var bgPaint = keyPaint
        var borderColor = keyBorderPaint.color

        if (flashActive && char != null) {
            bgPaint = if (flashColor == Color.RED) wrongPaint else highlightPaint
        } else if (char == highlightedKey && char != null) {
            bgPaint = highlightPaint
            borderColor = highlightBorderPaint.color
        } else if (char in pressedKeys) {
            bgPaint = pressedPaint
        }

        // Draw key background
        val radius = 12f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, bgPaint)

        // Draw border
        keyBorderPaint.color = borderColor
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, keyBorderPaint)

        // Draw label
        val label = key.displayLabel
        textPaint.textSize = keyWidth * 0.45f
        val textY = y + h / 2f - textPaint.textBoundsCenterOffset(textPaint, label)
        canvas.drawText(label, x + w / 2f, textY, textPaint)
    }

    /**
     * Start flash animation for timeout warning.
     */
    fun startFlashAnimation() {
        flashActive = true
        flashColor = Color.RED
        val duration = 500L

        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(duration)
        animator.interpolator = android.view.animation.AnticipateOvershootInterpolator()
        animator.addUpdateListener { animation ->
            flashColor = if (animation.animatedFraction > 0.5f) Color.RED else Color.WHITE
            postInvalidate()
        }
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()

        // Store for cleanup
        _flashAnimator = animator
    }

    private var _flashAnimator: ValueAnimator? = null

    /**
     * Stop flash animation and reset visual state.
     */
    fun stopFlashAnimation() {
        flashActive = false
        _flashAnimator?.cancel()
        _flashAnimator = null
        flashColor = Color.TRANSPARENT
        postInvalidate()
    }

    /**
     * Reset all visual state.
     */
    fun resetAll() {
        highlightedKey = null
        pressedKeys = emptySet()
        stopFlashAnimation()
        postInvalidate()
    }

    /**
     * Get paint text bounds center offset for text vertically centering.
     */
    private fun Paint.textBoundsCenterOffset(textPaint: Paint, text: String): Float {
        val rect = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, rect)
        val ascent = textPaint.ascent()
        val bottom = rect.bottom.toFloat()
        return -ascent / 2f + (bottom - rect.bottom) / 2f
    }
}
