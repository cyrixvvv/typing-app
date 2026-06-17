package com.honglu.typing.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator

data class KeyboardKey(
    val label: String,
    val uppercaseLabel: String,
    val widthRatio: Float = 1f,
    val marginLeft: Float = 0f,
    val isShift: Boolean = false,
    val isSpace: Boolean = false
) {
    val displayLabel: String
        get() = label
}

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        val ROWS = listOf(
            // Row 1: Q W E R T Y U I O P
            listOf(
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
            ),
            // Row 2: A S D F G H J K L ; '
            listOf(
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
            ),
            // Row 3: Z X C V B N M , . /
            listOf(
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
            ),
            // Row 4: Space bar (wide)
            listOf(
                KeyboardKey("Space", "SPACE", widthRatio = 1f, isSpace = true),
            ),
        )

        fun keyLabelToChar(key: KeyboardKey): Char? {
            if (key.isSpace) return ' '
            if (key.isShift) return null
            return key.label.firstOrNull()
        }
    }

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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Layout state
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keyGap = 6f
    private val sidePadding = 32f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout(w, h)
    }

    private fun calculateLayout(width: Int, height: Int) {
        val usableWidth = width - sidePadding * 2
        // Find the longest row (by total widthRatio)
        val maxRowWidthRatio = ROWS.maxOf { row ->
            row.sumOf { it.widthRatio.toDouble() }.toFloat()
        }
        val maxRowKeyCount = ROWS.maxOf { it.size }
        keyGap = Math.max(4f, usableWidth * 0.012f)
        keyWidth = (usableWidth - keyGap * (maxRowKeyCount + 1)) / maxRowWidthRatio
        keyHeight = keyWidth * 0.6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalRows = ROWS.size
        val totalHeight = totalRows * keyHeight + (totalRows - 1) * keyGap
        var rowStartY = (height - totalHeight) / 2f

        ROWS.forEachIndexed { rowIndex, row ->
            val y = rowStartY + rowIndex * (keyHeight + keyGap)
            var currentX = sidePadding

            // Compute total width of this row
            val rowTotalRatio = row.sumOf { it.widthRatio.toDouble() }.toFloat()
            val rowTotalWidth = rowTotalRatio * keyWidth + (row.size - 1) * keyGap
            val usableWidth = width - sidePadding * 2
            // Center the row horizontally
            val rowOffset = (usableWidth - rowTotalWidth) / 2f
            currentX += rowOffset

            for (key in row) {
                val drawWidth = keyWidth * key.widthRatio
                drawKey(canvas, currentX, y, drawWidth, keyHeight, key)
                currentX += drawWidth + keyGap
            }
        }
    }

    private fun drawKey(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, key: KeyboardKey) {
        val char = if (key.isSpace) ' ' else keyLabelToChar(key)
        var bgPaint = keyPaint
        var borderColor = keyBorderPaint.color

        if (flashActive && char != null && !key.isSpace) {
            bgPaint = if (flashColor == Color.RED) wrongPaint else highlightPaint
        } else if (char != null && char == highlightedKey) {
            bgPaint = highlightPaint
            borderColor = highlightBorderPaint.color
        } else if (char != null && char in pressedKeys) {
            bgPaint = pressedPaint
        } else if (key.isSpace) {
            // Space bar: slightly different shade
            bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#34495E")
            }
        }

        val radius = 12f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, bgPaint)
        keyBorderPaint.color = borderColor
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, keyBorderPaint)

        // Draw label
        val label = key.displayLabel
        textPaint.textSize = keyHeight * 0.38f
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val textY = y + h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, x + w / 2f, textY, textPaint)
    }

    fun startFlashAnimation() {
        flashActive = true
        flashColor = Color.RED
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(500)
        animator.interpolator = AnticipateOvershootInterpolator()
        animator.addUpdateListener { animation ->
            flashColor = if (animation.animatedFraction > 0.5f) Color.RED else Color.WHITE
            postInvalidate()
        }
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
        _flashAnimator = animator
    }

    private var _flashAnimator: ValueAnimator? = null

    fun stopFlashAnimation() {
        flashActive = false
        _flashAnimator?.cancel()
        _flashAnimator = null
        flashColor = Color.TRANSPARENT
        postInvalidate()
    }

    fun resetAll() {
        highlightedKey = null
        pressedKeys = emptySet()
        stopFlashAnimation()
        postInvalidate()
    }
}
