package com.example.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Custom View that renders a clean Donut Chart visualization
 * representing expense distribution across categories.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DonutSlice(
        val category: String,
        val value: Float,
        val color: Int
    )

    private var slices: List<DonutSlice> = listOf(
        DonutSlice("Food & Drink", 45f, Color.parseColor("#F59E0B")),
        DonutSlice("Groceries", 25f, Color.parseColor("#10B981")),
        DonutSlice("Transport", 15f, Color.parseColor("#3B82F6")),
        DonutSlice("Others", 15f, Color.parseColor("#8B5CF6"))
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textAlign = Paint.Align.CENTER
        textSize = 42f
        isFakeBoldText = true
    }

    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }

    private val oval = RectF()
    private var centerText: String = "100%"
    private var centerSubText: String = "Budget Used"

    fun setChartData(newSlices: List<DonutSlice>, centerTitle: String = "Finzy", subTitle: String = "Kategori") {
        if (newSlices.isNotEmpty()) {
            this.slices = newSlices
        }
        this.centerText = centerTitle
        this.centerSubText = subTitle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val size = min(width, height)
        val strokeWidth = size * 0.16f
        paint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 16f
        oval.set(
            (width - size) / 2f + padding,
            (height - size) / 2f + padding,
            (width + size) / 2f - padding,
            (height + size) / 2f - padding
        )

        val totalValue = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = (slice.value / totalValue) * 360f
            if (sweepAngle > 0f) {
                paint.color = slice.color
                // Slight gap between slices
                val gap = if (slices.size > 1) 3f else 0f
                val drawnSweep = (sweepAngle - gap).coerceAtLeast(1f)
                canvas.drawArc(oval, startAngle + gap / 2f, drawnSweep, false, paint)
                startAngle += sweepAngle
            }
        }

        // Draw Center Info
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(centerText, centerX, centerY + 8f, centerPaint)
        canvas.drawText(centerSubText, centerX, centerY + 46f, centerSubPaint)
    }
}
