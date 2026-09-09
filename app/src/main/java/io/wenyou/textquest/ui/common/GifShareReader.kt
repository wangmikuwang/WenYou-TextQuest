package io.wenyou.textquest.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import io.wenyou.textquest.data.repo.ShareCode
import java.io.ByteArrayInputStream
import java.io.InputStream

/** 从「相册识别」的输入流读取分享内容：GIF 则逐帧提取分片并拼接，静态图则直接识别。 */
object GifShareReader {

    fun read(stream: InputStream): String? {
        val bytes = try { stream.readBytes() } catch (_: Throwable) { null } ?: return null
        if (bytes.size < 4) return null
        val isGif = bytes[0].toInt() == 'G'.code && bytes[1].toInt() == 'I'.code &&
            bytes[2].toInt() == 'F'.code && bytes[3].toInt() == '8'.code
        return if (isGif) readGif(bytes) else readStatic(bytes)
    }

    private fun readStatic(bytes: ByteArray): String? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val text = QrCode.decode(bmp)
            bmp.recycle()
            text?.trim()
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readGif(bytes: ByteArray): String? {
        return try {
            val movie = Movie.decodeStream(ByteArrayInputStream(bytes)) ?: return null
            val w = movie.width().coerceAtLeast(1)
            val h = movie.height().coerceAtLeast(1)
            val collected = HashMap<Int, String>()
            var total = 0
            val step = 100
            val duration = movie.duration().coerceAtLeast(step)
            var t = 0
            while (t < duration) {
                movie.setTime(t)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                movie.draw(canvas, 0f, 0f)
                val text = QrCode.decode(bmp)
                bmp.recycle()
                text?.let {
                    val chunk = ShareCode.parseChunk(it)
                    if (chunk != null) {
                        collected.putIfAbsent(chunk.index, chunk.data)
                        total = chunk.total
                        if (total > 0 && collected.size >= total) {
                            return ShareCode.assembleChunks(collected, total)
                        }
                    } else if (it.isNotBlank()) {
                        return it.trim()
                    }
                }
                t += step
            }
            if (total > 0) ShareCode.assembleChunks(collected, total) else null
        } catch (_: Throwable) {
            null
        }
    }
}
