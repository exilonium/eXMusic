package app.exmusic.exilonium.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.DownloadService.sendAddDownload
import app.exmusic.exilonium.BuildConfig
import app.exmusic.core.ui.utils.isAtLeastAndroid11
import app.exmusic.core.ui.utils.isAtLeastAndroid6

context(context: Context)
inline fun <reified T> intent(): Intent = Intent(context, T::class.java)

inline fun <reified T : BroadcastReceiver> Context.broadcastPendingIntent(
    requestCode: Int = 0,
    flags: Int = if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0
): PendingIntent = PendingIntent.getBroadcast(this, requestCode, intent<T>(), flags)

inline fun <reified T : Activity> Context.activityPendingIntent(
    requestCode: Int = 0,
    @PendingIntentCompat.Flags flags: Int = 0,
    block: Intent.() -> Unit = { }
) = pendingIntent(
    intent = intent<T>().apply(block),
    requestCode = requestCode,
    flags = flags
)

fun Context.pendingIntent(
    intent: Intent,
    requestCode: Int = 0,
    @PendingIntentCompat.Flags flags: Int = 0
): PendingIntent {
    val flags = (if (isAtLeastAndroid6) PendingIntent.FLAG_IMMUTABLE else 0) or flags
    return PendingIntent.getActivity(this, requestCode, intent, flags)
}

val Context.isIgnoringBatteryOptimizations
    get() = !isAtLeastAndroid6 ||
        getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true

fun Context.toast(
    @StringRes
    message: Int,
    duration: ToastDuration = ToastDuration.Short
) = toast(
    message = getString(message),
    duration = duration
)

fun Context.toast(message: String, duration: ToastDuration = ToastDuration.Short) =
    Toast.makeText(this, message, duration.length).show()

@JvmInline
value class ToastDuration private constructor(internal val length: Int) {
    companion object {
        val Short = ToastDuration(length = Toast.LENGTH_SHORT)
        val Long = ToastDuration(length = Toast.LENGTH_LONG)
    }
}

fun launchYouTubeMusic(
    context: Context,
    endpoint: String,
    tryWithoutBrowser: Boolean = true
): Boolean {
    return try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://music.youtube.com/${endpoint.dropWhile { it == '/' }}".toUri()
        ).apply {
            if (tryWithoutBrowser && isAtLeastAndroid11) {
                flags = Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER
            }
        }
        intent.`package` =
            context.applicationContext.packageManager.queryIntentActivities(intent, 0)
                .firstOrNull {
                    it?.activityInfo?.packageName != null &&
                        BuildConfig.APPLICATION_ID !in it.activityInfo.packageName
                }?.activityInfo?.packageName
                ?: return false
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        if (tryWithoutBrowser) launchYouTubeMusic(
            context = context,
            endpoint = endpoint,
            tryWithoutBrowser = false
        ) else false
    }
}

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Should be called in the context of an Activity")
}

fun Context.hasPermission(permission: String) = ContextCompat.checkSelfPermission(
    applicationContext,
    permission
) == PackageManager.PERMISSION_GRANTED

@OptIn(UnstableApi::class)
inline fun <reified T : DownloadService> Context.download(request: DownloadRequest) = runCatching {
    sendAddDownload(this, T::class.java, request, true)
}.recoverCatching {
    sendAddDownload(this, T::class.java, request, false)
}
