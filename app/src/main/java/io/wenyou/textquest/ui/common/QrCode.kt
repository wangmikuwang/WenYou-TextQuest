package io.wenyou.textquest.ui.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/** 把一段文本编码成二维码位图，支持保存到本地，以及从位图识别二维码文本。 */
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

    /** 从位图中识别二维码文本（识别不到返回 null）。 */
    fun decode(bitmap: Bitmap): String? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val bmp = BinaryBitmap(HybridBinarizer(source))
            val hints: Map<DecodeHintType, Any> = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",
                DecodeHintType.TRY_HARDER to true
            )
            MultiFormatReader().decode(bmp, hints).text
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
