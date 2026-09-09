package io.wenyou.textquest.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Apple Watch 配对风格的帧渲染：深色背景 + 青绿光晕（离散色带）+ 白底圆角卡片上的高对比二维码。 */
object PairingFrame {

    private const val BG = 0xFF0A0E1A.toInt()
    private const val GLOW = 0xFF1FD6C4.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()

    fun render(qr: Bitmap, glow: Float, size: Int = 400): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(BG)

        val rings = 6
        val radialMax = size * 0.55f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (r in rings downTo 1) {
            val radius = radialMax * r / rings
            val g = glow.coerceIn(0f, 1f)
            val alpha = (150 * g * (0.35f + 0.65f * r / rings)).toInt().coerceIn(0, 255)
            paint.color = (0xFF shl 24) or blend(GLOW, BG, alpha / 255f)
            c.drawCircle(size / 2f, size / 2f, radius, paint)
        }

        val card = size * 0.84f
        val half = (size - card) / 2f
        val cardRadius = card * 0.055f
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        c.drawRoundRect(RectF(half, half, half + card, half + card), cardRadius, cardRadius, cardPaint)

        val pad = card * 0.08f
        val side = (card - pad * 2).toInt()
        val src = Rect(0, 0, qr.width, qr.height)
        val dst = Rect((half + pad).toInt(), (half + pad).toInt(),
            (half + pad).toInt() + side, (half + pad).toInt() + side)
        val qrPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(qr, src, dst, qrPaint)

        return out
    }

    private fun blend(fg: Int, bg: Int, ratio: Float): Int {
        val a = ratio.coerceIn(0f, 1f)
        val r = (((fg shr 16) and 0xFF) * a + ((bg shr 16) and 0xFF) * (1 - a)).toInt()
        val g = (((fg shr 8) and 0xFF) * a + ((bg shr 8) and 0xFF) * (1 - a)).toInt()
        val b = (((fg) and 0xFF) * a + ((bg) and 0xFF) * (1 - a)).toInt()
        return (r shl 16) or (g shl 8) or b
    }
}
