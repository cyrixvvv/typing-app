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
    val uppercaseLabel: String = label.uppercase(),
    val widthRatio: Float = 1f,
    val marginLeft: Float = 0f,
    val isSpace: Boolean = false
) {
    val displayLabel: String get() = label
}

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        val ROWS: List<List<KeyboardKey>> = listOf(
            // Row 1: 1 2 3 4 5 6 7 8 9 0
            listOf(
                KeyboardKey("1", "1", 0.75f), KeyboardKey("2", "2", 0.75f),
                KeyboardKey("3", "3", 0.75f), KeyboardKey("4", "4", 0.75f),
                KeyboardKey("5", "5", 0.75f), KeyboardKey("6", "6", 0.75f),
                KeyboardKey("7", "7", 0.75f), KeyboardKey("8", "8", 0.75f),
                KeyboardKey("9", "9", 0.75f), KeyboardKey("0", "0", 0.75f)
            ),
            // Row 2: Q W E R T Y U I O P
            listOf(
                KeyboardKey("q"), KeyboardKey("w"), KeyboardKey("e"), KeyboardKey("r"),
                KeyboardKey("t"), KeyboardKey("y"), KeyboardKey("u"), KeyboardKey("i"),
                KeyboardKey("o"), KeyboardKey("p")
            ),
            // Row 3: A S D F G H J K L ; '
            listOf(
                KeyboardKey("a"), KeyboardKey("s"), KeyboardKey("d"), KeyboardKey("f"),
                KeyboardKey("g"), KeyboardKey("h"), KeyboardKey("j"), KeyboardKey("k"),
                KeyboardKey("l"), KeyboardKey(";"), KeyboardKey("'")
            ),
            // Row 4: Z X C V B N M , . /
            listOf(
                KeyboardKey("z"), KeyboardKey("x"), KeyboardKey("c"), KeyboardKey("v"),
                KeyboardKey("b"), KeyboardKey("n"), KeyboardKey("m"),
                KeyboardKey(","), KeyboardKey("."), KeyboardKey("/")
            ),
            // Row 5: wide space bar
            listOf(KeyboardKey("Space", "SPACE", widthRatio = 10f, isSpace = true))
        )

        fun keyLabelToChar(key: KeyboardKey): Char? {
            if (key.isSpace) return ' '
            return key.label.firstOrNull()
        }
    }

    var highlightedKey: Char? = null
        set(value) { field = value; postInvalidate() }

    var pressedKeys: Set<Char> = emptySet()
        set(value) { field = value; postInvalidate() }

    // Flash state — only affects highlightedKey
    private var flashActive = false
    private var flashColor: Int = Color.TRANSPARENT

    // Paints
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2C3E50")
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
        color = Color.parseColor("#34495E")
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#F39C12")
    }
    private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#F1C40F")
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#5D6D7E")
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.RED
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

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
        val maxRowWidthRatio = ROWS.maxOf { row ->
            row.sumOf { it.widthRatio.toDouble() }.toFloat()
        }
        val maxRowKeyCount = ROWS.maxOf { it.size }
        keyGap = maxOf(4f, usableWidth * 0.01f)
        keyWidth = (usableWidth - keyGap * (maxRowKeyCount + 1)) / maxRowWidthRatio
        keyHeight = maxOf(keyWidth * 0.55f, 24f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalRows = ROWS.size
        val totalHeight = totalRows * keyHeight + (totalRows - 1) * keyGap
        var rowStartY = (height - totalHeight) / 2f

        ROWS.forEachIndexed { rowIndex, row ->
            val y = rowStartY + rowIndex * (keyHeight + keyGap)
            var currentX = sidePadding
            val rowTotalRatio = row.sumOf { it.widthRatio.toDouble() }.toFloat()
            val rowTotalWidth = rowTotalRatio * keyWidth + (row.size - 1) * keyGap
            val usableWidth = width - sidePadding * 2
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
        val isHighlighted = char != null && char == highlightedKey
        val isPressed = char != null && char in pressedKeys

        when {
            // Flash animation — only affects the highlighted key
            flashActive && isHighlighted -> {
                bgPaint = flashPaint
                borderColor = Color.RED
            }
            // Normal highlight (expected key)
            isHighlighted -> {
                bgPaint = highlightPaint
                borderColor = highlightBorderPaint.color
            }
            // Pressed key feedback
            isPressed -> bgPaint = pressedPaint
        }

        val radius = 12f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, bgPaint)
        keyBorderPaint.color = borderColor
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, keyBorderPaint)

        val label = key.displayLabel
        textPaint.textSize = keyHeight * 0.38f
        val textY = y + h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, x + w / 2f, textY, textPaint)
    }

    fun startFlashAnimation() {
        flashActive = true
        flashPaint.color = Color.RED
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(500)
        animator.interpolator = AnticipateOvershootInterpolator()
        animator.addUpdateListener { animation ->
            flashPaint.color = if (animation.animatedFraction > 0.5f) Color.RED else Color.parseColor("#FF6B6B")
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
        postInvalidate()
    }

    fun resetAll() {
        highlightedKey = null
        pressedKeys = emptySet()
        stopFlashAnimation()
        postInvalidate()
    }
}
