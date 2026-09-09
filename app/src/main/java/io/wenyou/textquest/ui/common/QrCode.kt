package io.wenyou.textquest.ui.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/** 把一段文本编码成二维码位图，并支持保存到本地（相册/应用图片目录）。 */
object QrCode {
    fun encode(content: String, size: Int = 640): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    /** 保存二维码到本地：Android 10+ 存相册 Pictures/WenYou；更早版本存应用图片目录。返回保存位置或 null。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, title: String): String? {
        return try {
            val safeTitle = title.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_").take(40).ifBlank { "share_qr" }
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$safeTitle.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WenYou")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
                if (!ok) return null
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/WenYou"
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
                dir.mkdirs()
                val f = File(dir, "$safeTitle.png")
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.absolutePath
            }
        } catch (_: Throwable) {
            null
        }
    }
}
