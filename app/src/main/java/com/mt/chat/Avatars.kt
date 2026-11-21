package com.mt.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.File

object Avatars {

    const val USER = 0
    const val AI = 1

    private var cacheUser: Bitmap? = null
    private var cacheAi: Bitmap? = null

    private fun file(ctx: Context, which: Int): File =
        File(ctx.filesDir, if (which == USER) "avatar_user.png" else "avatar_ai.png")

    fun isCustom(ctx: Context, which: Int): Boolean = file(ctx, which).exists()

    fun save(ctx: Context, which: Int, uri: Uri): Boolean {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            val bytes = input.readBytes()
            input.close()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
            val square = centerSquare(src)
            val circled = circle(square)
            val bos = ByteArrayOutputStream()
            circled.compress(Bitmap.CompressFormat.PNG, 100, bos)
            file(ctx, which).writeBytes(bos.toByteArray())
            invalidate(which)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear(ctx: Context, which: Int) {
        file(ctx, which).delete()
        invalidate(which)
    }

    fun load(ctx: Context, iv: ImageView, which: Int) {
        var bmp = if (which == USER) cacheUser else cacheAi
        if (bmp == null) {
            bmp = read(ctx, which)
            if (which == USER) cacheUser = bmp else cacheAi = bmp
        }
        iv.setImageBitmap(bmp)
    }

    private fun read(ctx: Context, which: Int): Bitmap {
        val f = file(ctx, which)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return it }
        }
        return default(which)
    }

    private fun invalidate(which: Int) {
        if (which == USER) cacheUser = null else cacheAi = null
    }

    private fun centerSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val crop = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(crop, 512, 512, true)
    }

    private fun circle(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = src.width / 2f
        val clip = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(r, r, r, clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        return out
    }

    private fun default(which: Int): Bitmap {
        val size = 320
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        val colors = if (which == USER)
            intArrayOf(0xFF4F8BFF.toInt(), 0xFF7C5CFF.toInt())
        else
            intArrayOf(0xFF36E7C9.toInt(), 0xFF4F8BFF.toInt())
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors, null, Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r, bg)
        val text = if (which == USER) "我" else "M"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = if (which == USER) 0xFFFFFFFF.toInt() else 0xFF0B1020.toInt()
        tp.textSize = size * 0.42f
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
        val fm = tp.fontMetrics
        val baseline = r - (fm.ascent + fm.descent) / 2f
        c.drawText(text, r, baseline, tp)
        return bmp
    }
}
