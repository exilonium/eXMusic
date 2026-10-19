package app.exmusic.exilonium.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.exmusic.exilonium.BuildConfig
import app.exmusic.exilonium.UpdateState
import app.exmusic.exilonium.setUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val APK_DIR = "updates"
private const val APK_FILENAME = "update.apk"

fun apkFile(context: Context) =
    File(context.cacheDir, "$APK_DIR/$APK_FILENAME")

fun cleanupApk(context: Context) {
    runCatching { apkFile(context).delete() }
    setUpdateState(UpdateState.Idle)
}

suspend fun downloadApk(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
    val apk = apkFile(context).also { it.parentFile?.mkdirs() }

    setUpdateState(UpdateState.Downloading(0f))

    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()

        val response = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
        val body = response.body ?: error("Empty response body")
        val totalBytes = body.contentLength().takeIf { it > 0 }

        apk.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                var lastPct = -1
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (totalBytes != null) {
                        val pct = (downloaded * 100 / totalBytes).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            setUpdateState(UpdateState.Downloading(pct / 100f))
                        }
                    }
                }
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )

        setUpdateState(UpdateState.ReadyToInstall(uri))
    }.onFailure { e ->
        e.printStackTrace()
        cleanupApk(context)
    }
}

fun launchInstall(context: Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
