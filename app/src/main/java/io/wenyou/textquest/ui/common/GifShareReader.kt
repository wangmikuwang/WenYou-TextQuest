package io.wenyou.textquest.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import io.wenyou.textquest.data.repo.ShareCode
import java.io.InputStream
import java.nio.ByteBuffer

/** 从「相册识别」输入流读取分享内容：GIF 用 Glide gifdecoder 逐帧提取并拼接；静态图直接识别。 */
object GifShareReader {

    /**
     * Glide gifdecoder 需要帧位图供给者。这里直接创建新 Bitmap，不关心池化复用；
     * 读取后不回收（解码器可能在帧间复用/引用），只在一次性识别后交给 GC。
     */
    private object OpaqueBitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) {}
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(ints: IntArray) {}
    }

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

    private fun readGif(bytes: ByteArray): String? {
        return try {
            val header = GifHeaderParser().setData(bytes).parseHeader()
            if (header.numFrames < 1) return null
            val decoder = StandardGifDecoder(OpaqueBitmapProvider, header, ByteBuffer.wrap(bytes))
            val collected = HashMap<Int, String>()
            var total = 0
            for (i in 0 until header.numFrames) {
                val frame = decoder.getNextFrame() ?: break
                decoder.advance()
                val text = QrCode.decode(frame)
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
            }
            if (total > 0) ShareCode.assembleChunks(collected, total) else null
        } catch (_: Throwable) {
            null
        }
    }
}
