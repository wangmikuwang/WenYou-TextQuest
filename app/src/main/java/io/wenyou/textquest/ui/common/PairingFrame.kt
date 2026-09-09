package io.wenyou.textquest.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/** Apple Watch 配对风格的帧渲染：深色背景 + 环形青绿光晕 + 白底圆角卡片上的高对比二维码。 */
object PairingFrame {

    private const val BG = 0xFF0A0E1A.toInt()
    private const val GLOW = 0xFF1FD6C4.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()

    /** 渲染一帧。glow 0..1 控制光晕强度（用于脉动）。 */
    fun render(qr: Bitmap, glow: Float, size: Int = 600): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        // 深色背景
        c.drawColor(BG)

        // 环形光晕（径向渐变，中心最亮向外淡出）
        val center = size / 2f
        val glowAlpha = (150 * glow.coerceIn(0f, 1f)).toInt()
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center, center, size * 0.55f,
                intArrayOf(
                    (GLOW and 0x00FFFFFF) or ((glowAlpha * 255 / 150) shl 24),
                    (GLOW and 0x00FFFFFF) or ((glowAlpha * 60 / 150) shl 24),
                    (GLOW and 0x00FFFFFF) or 0x00000000
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(center, center, size * 0.55f, glowPaint)

        // 白底圆角卡片
        val card = (size * 0.84f).toFloat()
        val half = (size - card) / 2f
        val cardRadius = card * 0.055f
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        c.drawRoundRect(RectF(half, half, half + card, half + card), cardRadius, cardRadius, cardPaint)

        // 高对比二维码（缩进卡片内）
        val pad = card * 0.08f
        val qrDest = RectF(half + pad, half + pad, half + card - pad, half + card - pad)
        val side = min(qrDest.width().toInt(), qrDest.height().toInt())
        val src = Rect(0, 0, qr.width, qr.height)
        val dst = Rect(qrDest.left.toInt(), qrDest.top.toInt(), qrDest.left.toInt() + side, qrDest.top.toInt() + side)
        val qrPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(qr, src, dst, qrPaint)

        return out
    }
}
