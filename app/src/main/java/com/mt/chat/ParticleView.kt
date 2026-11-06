package com.mt.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/** 背景粒子点阵（还原网页版背景） */
class ParticleView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {

    private class P(var x: Float, var y: Float, val r: Float, var vx: Float, var vy: Float, val a: Float)

    private val pts = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pts.clear()
        val n = (w * h / 18000).coerceIn(20, 70)
        repeat(n) {
            pts.add(
                P(
                    Random.nextFloat() * w, Random.nextFloat() * h,
                    Random.nextFloat() * 1.6f + 0.6f,
                    (Random.nextFloat() - 0.5f) * 0.25f,
                    (Random.nextFloat() - 0.5f) * 0.25f,
                    Random.nextFloat() * 0.5f + 0.2f
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (p in pts) {
            p.x += p.vx
            p.y += p.vy
            if (p.x < 0) p.x = w
            if (p.x > w) p.x = 0f
            if (p.y < 0) p.y = h
            if (p.y > h) p.y = 0f
            paint.color = Color.argb((p.a * 255).toInt(), 120, 160, 255)
            canvas.drawCircle(p.x, p.y, p.r, paint)
        }
        postInvalidateDelayed(33)
    }
}
