package io.wenyou.textquest.data.repo

import android.util.Base64
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson

/** 分享码：把一部剧情（连同其角色）编码成可复制/可分享的文本，粘贴即可导入。
 *  前缀 WY1: 用于识别与版本化；负载为 URL-safe base64 的 JSON。 */
object ShareCode {
    private const val PREFIX = "WY1:"

    fun encode(bundle: AppBundle): String {
        val json = AppJson.encodeToString(AppBundle.serializer(), bundle)
        val b64 = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return PREFIX + b64
    }

    fun decode(code: String): AppBundle? {
        val cleaned = code.trim().removePrefix(PREFIX)
        if (cleaned.isBlank()) return null
        val payload = cleaned.filterNot { it.isWhitespace() }
        return try {
            val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val text = String(bytes, Charsets.UTF_8)
            AppJson.decodeFromString(AppBundle.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }
}
