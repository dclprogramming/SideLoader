package com.sideloader.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Downloads {
    /** Downloads [link] on a background thread. Callbacks fire on that thread. */
    fun start(ctx: Context, link: String, disposition: String?,
              onProgress: (Int) -> Unit, onDone: (Result<File>) -> Unit) {
        Thread {
            onDone(runCatching {
                var url = URL(link)
                var conn: HttpURLConnection
                var hops = 0
                while (true) { // follow redirects manually (handles http -> https)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000; readTimeout = 15000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    if (conn.responseCode in 300..399 && hops++ < 5) {
                        url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue
                    }
                    break
                }
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }

                val header = conn.getHeaderField("Content-Disposition") ?: disposition ?: ""
                val raw = Regex("filename=\"?([^\";]+)").find(header)?.groupValues?.get(1)
                    ?: url.path.substringAfterLast('/').ifBlank { "download" }
                val name = raw.replace(Regex("[^\\w.\\-]"), "_") // blocks path traversal
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                val out = File(dir, name)

                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); done += n
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
                out
            })
        }.start()
    }

    /** Opens the system installer; sends the user to the "install unknown apps" screen first if needed. */
    fun install(a: Activity, file: File) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${a.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.provider", file)
        a.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
