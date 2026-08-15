package app.exitune.android.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import app.exitune.android.utils.thumbnail
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

class BitmapProvider(
    private val getBitmapSize: () -> Int,
    private val getColor: (isDark: Boolean) -> Int,
    private val context: Context
) {
    @set:Synchronized
    var lastUri: Uri? = null
        private set

    @set:Synchronized
    private var lastBitmap: Bitmap? = null
        get() = field?.takeUnless { it.isRecycled }
        set(value) {
            field = value
            listener?.invoke(value)
        }
    private var lastIsSystemInDarkMode = false
    private var currentTask: Disposable? = null

    private lateinit var defaultBitmap: Bitmap
    val bitmap get() = lastBitmap ?: defaultBitmap

    private var listener: ((Bitmap?) -> Unit)? = null

    init {
        setDefaultBitmap()
    }

    fun setDefaultBitmap(): Boolean {
        val isSystemInDarkMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        var oldBitmap: Bitmap? = null
        if (::defaultBitmap.isInitialized) {
            if (isSystemInDarkMode == lastIsSystemInDarkMode) return false
            oldBitmap = defaultBitmap
        }

        lastIsSystemInDarkMode = isSystemInDarkMode

        val size = getBitmapSize()
        defaultBitmap = createBitmap(size, size).applyCanvas {
            drawColor(getColor(isSystemInDarkMode))
        }
        oldBitmap?.recycle()

        return lastBitmap == null
    }

    fun load(
        uri: Uri?,
        onDone: (Bitmap) -> Unit = { }
    ) {
        if (lastUri == uri) {
            listener?.invoke(lastBitmap)
            return
        }

        lastUri = uri

        if (uri == null) {
            lastBitmap = null
            onDone(bitmap)
            return
        }

        val oldTask = currentTask
        currentTask = context.applicationContext.imageLoader.enqueue(
            ImageRequest.Builder(context.applicationContext)
                .data(uri.thumbnail(getBitmapSize()))
                .allowHardware(false)
                .listener(
                    onError = { _, _ ->
                        lastBitmap = null
                        onDone(bitmap)
                    },
                    onSuccess = { _, result ->
                        lastBitmap = result.image.run { toBitmap(width, height) }
                        onDone(bitmap)
                    }
                )
                .build()
        )
        oldTask?.dispose()
    }

    fun setListener(callback: ((Bitmap?) -> Unit)?) {
        listener = callback
        listener?.invoke(lastBitmap)
    }
}
