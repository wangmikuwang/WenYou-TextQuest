package io.wenyou.textquest

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 崩溃日志写入：总是写内部与外部应用目录，并（若已授权）写入系统「文档/Documents」目录
 * （通过 SAF 授权一个目录，见设置页“崩溃日志保存位置”）。
 */
object CrashLog {

    fun write(context: Context, text: String, treeUri: String?) {
        // 1) 内部存储
        try { File(context.filesDir, "crash.log").writeText(text) } catch (_: Throwable) {}
        // 2) 外部应用目录（adb 可读）
        context.getExternalFilesDir(null)?.let {
            try { File(it, "crash.log").writeText(text) } catch (_: Throwable) {}
        }
        // 3) 系统 Documents（SAF 目录，若已选择）
        if (!treeUri.isNullOrBlank()) {
            try {
                val tree = Uri.parse(treeUri)
                val root = DocumentFile.fromTreeUri(context, tree) ?: return
                var f = root.findFile("crash.log")
                if (f == null || !f.exists()) {
                    f = root.createFile("text/plain", "crash")
                }
                if (f != null) {
                    context.contentResolver.openOutputStream(f.uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
}
