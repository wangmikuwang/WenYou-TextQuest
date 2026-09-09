package io.wenyou.textquest.ui.common

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** 轻量 GIF89a 动图编码器（已用 System.Drawing 解码验证：含 QR 的帧内容正确）。
 *  采用“无压缩字面量 + 周期 clear”的规范 LZW，保证任何解码器（含 Movie/gallery）都能正确解析。 */
object GifEncoder {

    fun encode(frames: List<Bitmap>, delayMs: Int = 1500, loopForever: Boolean = true): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        val w = frames[0].width
        val h = frames[0].height

        val colorIndex = HashMap<Int, Int>()
        val palette = mutableListOf<Int>()
        frames.forEach { bm ->
            val px = IntArray(bm.width * bm.height)
            bm.getPixels(px, 0, bm.width, 0, 0, bm.width, bm.height)
            px.forEach { c ->
                if (!colorIndex.containsKey(c) && colorIndex.size < 255) {
                    colorIndex[c] = colorIndex.size
                    palette.add(c)
                }
            }
        }
        val gctSize = Integer.highestOneBit((palette.size * 2).coerceAtLeast(2)).coerceAtLeast(2)
        val sizeBits = Integer.numberOfTrailingZeros(gctSize) - 1

        val out = ByteArrayOutputStream()
        writeAscii(out, "GIF89a")
        writeShort(out, w)
        writeShort(out, h)
        out.write(0x80 or 0x70 or sizeBits.coerceIn(0, 7))
        out.write(0)
        out.write(0)
        for (i in 0 until gctSize) {
            val c = if (i < palette.size) palette[i] else 0
            out.write(c shr 16 and 0xFF)
            out.write(c shr 8 and 0xFF)
            out.write(c and 0xFF)
        }

        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        writeAscii(out, "NETSCAPE2.0")
        out.write(0x03)
        out.write(0x01)
        writeShort(out, if (loopForever) 0 else 0)
        out.write(0x00)

        val indexedFrames = frames.map { bm ->
            val px = IntArray(bm.width * bm.height)
            bm.getPixels(px, 0, bm.width, 0, 0, bm.width, bm.height)
            ByteArray(px.size) { i -> (colorIndex[px[i]] ?: 0).toByte() }
        }
        val minCodeSize = Integer.toBinaryString((palette.size - 1)).length.coerceAtLeast(2)

        indexedFrames.forEach { indexed ->
            out.write(0x21)
            out.write(0xF9)
            out.write(0x04)
            out.write(0x00)
            writeShort(out, (delayMs / 10).coerceAtLeast(2))
            out.write(0x00)
            out.write(0x00)
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, w)
            writeShort(out, h)
            out.write(0x00)
            writeLzw(out, indexed, minCodeSize)
        }

        out.write(0x3B)
        return out.toByteArray()
    }

    /** 规范 LZW：字面量 + 每批 4 码后 clear，码长恒定，各解码器均可正确解析。 */
    private fun writeLzw(out: ByteArrayOutputStream, indices: ByteArray, minCodeSize: Int) {
        if (indices.isEmpty()) return
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val codeSize = minCodeSize + 1
        val batch = 4
        val payload = ByteArrayOutputStream()
        val bw = BitWriter(payload)
        var i = 0
        while (i < indices.size) {
            bw.write(clear, codeSize)
            var n = 0
            while (i < indices.size && n < batch) {
                bw.write(indices[i].toInt(), codeSize)
                i++
                n++
            }
        }
        bw.write(eoi, codeSize)
        bw.close()

        val bytes = payload.toByteArray()
        var pos = 0
        while (pos < bytes.size) {
            val n = minOf(255, bytes.size - pos)
            out.write(n)
            out.write(bytes, pos, n)
            pos += n
        }
        out.write(0)
    }

    private class BitWriter(private val out: ByteArrayOutputStream) {
        private var acc = 0
        private var bits = 0
        fun write(code: Int, size: Int) {
            acc = acc or (code shl bits)
            bits += size
            while (bits >= 8) {
                out.write(acc and 0xFF)
                acc = acc ushr 8
                bits -= 8
            }
        }
        fun close() {
            if (bits > 0) {
                out.write(acc and 0xFF)
                acc = 0
                bits = 0
            }
        }
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeAscii(out: ByteArrayOutputStream, s: String) {
        s.forEach { out.write(it.code) }
    }
}
