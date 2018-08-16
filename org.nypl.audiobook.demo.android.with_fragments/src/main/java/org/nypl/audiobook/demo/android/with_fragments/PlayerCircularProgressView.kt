package org.nypl.audiobook.demo.android.with_fragments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PlayerCircularProgressView(context: Context, attrs: AttributeSet) : View(context, attrs) {

  private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = Color.parseColor("#ff0000")
    this.isAntiAlias = true
  }

  private val rectOuter = RectF()

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val w = this.width.toFloat()
    val h = this.height.toFloat()
    this.rectOuter.set(0.0f, 0.0f, w, h)
  }

  private var progressValue = 0.0f
  var progress: Float
    get() =
      this.progressValue
    set(value) {
      this.progressValue = Math.min(1.0f, Math.max(0.0f, value))
      this.invalidate()
    }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)

    if (canvas != null) {
      canvas.drawArc(
        this.rectOuter,
        -90.0f,
        this.progress * 360.0f,
        true,
        this.arcPaint)
    }
  }
}
