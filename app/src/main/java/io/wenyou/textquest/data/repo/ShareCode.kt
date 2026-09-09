package io.wenyou.textquest.data.repo

import android.util.Base64
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 分享码：把一部剧情（连同其角色）编码成可复制/可分享的文本，粘贴/扫码即可导入。
 *
 *  负载先经 deflate 压缩再 base64，显著缩小体积，从而能放进单个二维码（约 3KB 容量）。
 *  前缀 WY2: 表示压缩负载；同时兼容旧的 WY1:（未压缩 base64）。 */
object ShareCode {
    private const val PREFIX_COMPRESSED = "WY2:"
    private const val PREFIX_LEGACY = "WY1:"

    fun encode(bundle: AppBundle): String {
        val json = AppJson.encodeToString(AppBundle.serializer(), bundle)
        val compressed = deflate(json.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(
            compressed,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return PREFIX_COMPRESSED + b64
    }

    fun decode(code: String): AppBundle? {
        val cleaned = code.trim()
        val pre = when {
            cleaned.startsWith(PREFIX_COMPRESSED) -> PREFIX_COMPRESSED
            cleaned.startsWith(PREFIX_LEGACY) -> PREFIX_LEGACY
            else -> null
        }
        val payload = cleaned.removePrefix(PREFIX_COMPRESSED).removePrefix(PREFIX_LEGACY)
            .filterNot { it.isWhitespace() }
        if (payload.isBlank()) return null
        return try {
            val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jsonBytes = if (pre == PREFIX_COMPRESSED) inflate(bytes) else bytes
            AppJson.decodeFromString(AppBundle.serializer(), String(jsonBytes, Charsets.UTF_8))
        } catch (_: Throwable) {
            null
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream(bytes.size / 2)
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 2)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}
