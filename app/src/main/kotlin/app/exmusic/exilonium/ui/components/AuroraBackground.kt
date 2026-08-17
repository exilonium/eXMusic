package app.exmusic.exilonium.ui.components

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import app.exmusic.exilonium.utils.thumbnail
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import kotlin.random.Random

private const val PALETTE_THUMBNAIL_SIZE = 200
private const val PALETTE_MAXIMUM_COLOR_COUNT = 16
private const val NOISE_SIZE = 64
private const val NOISE_ALPHA = 0.02f
private val BLUR_RADIUS = 120.dp

// A full cycle of the drift takes over half a minute, so at display rate consecutive frames differ
// by a fraction of a pixel - each one paying for a fullscreen blur. Snapping the drivers to steps
// this coarse is invisible under a 120dp blur and caps the redraws at roughly 36 per second, which
// is what makes the aurora affordable to keep animating behind the player's sheets.
private const val DRIFT_STEP = 0.002f
private const val ROTATION_STEP = 0.25f

private val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Slowly drifting, heavily blurred blobs tinted with the dominant colors of the currently playing
 * artwork. Colors cross-fade whenever the artwork changes.
 *
 * Below API 31 [RenderEffect] is unavailable, so the blobs are drawn as soft radial gradients
 * instead of hard circles.
 */
@Composable
fun AuroraBackground(
    artworkUri: Uri?,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf<Palette?>(null) }

    LaunchedEffect(artworkUri) {
        if (artworkUri == null) {
            palette = null
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(artworkUri.thumbnail(PALETTE_THUMBNAIL_SIZE))
            .allowHardware(false)
            .scale(Scale.FILL)
            .build()

        val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
        val bitmap = image?.asDrawable(context.resources)?.toBitmap()

        if (bitmap != null) palette = Palette
            .from(bitmap)
            .maximumColorCount(PALETTE_MAXIMUM_COLOR_COUNT)
            .generate()
    }

    // The most populated swatches read closer to the artwork than vibrant/muted ever does
    val swatches = remember(palette) {
        palette?.swatches?.sortedByDescending { it.population }.orEmpty()
    }

    val dominant = swatches.getOrNull(0)?.rgb
        ?: palette?.getDominantColor(fallbackColor.toArgb())
        ?: fallbackColor.toArgb()
    val secondary = swatches.getOrNull(1)?.rgb ?: dominant
    val tertiary = swatches.getOrNull(2)?.rgb ?: secondary
    val quaternary = swatches.getOrNull(3)?.rgb ?: tertiary

    val color1 by animateColorAsState(
        targetValue = Color(dominant),
        animationSpec = tween(durationMillis = 1000),
        label = "color1"
    )
    val color2 by animateColorAsState(
        targetValue = Color(secondary),
        animationSpec = tween(durationMillis = 1000),
        label = "color2"
    )
    val color3 by animateColorAsState(
        targetValue = Color(tertiary),
        animationSpec = tween(durationMillis = 1000),
        label = "color3"
    )
    val color4 by animateColorAsState(
        targetValue = Color(quaternary),
        animationSpec = tween(durationMillis = 1000),
        label = "color4"
    )

    val transition = rememberInfiniteTransition(label = "aurora")

    val move1 = transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "move1"
    ).stepped(DRIFT_STEP)
    val move2 = transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 35000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "move2"
    ).stepped(DRIFT_STEP)
    val move3 = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 42000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "move3"
    ).stepped(DRIFT_STEP)
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    ).stepped(ROTATION_STEP)

    // Built once: the layer block reruns on every step of the rotation, and rebuilding the blur
    // there allocated a new RenderEffect each time
    val density = LocalDensity.current
    val blur = remember(density) {
        if (blurSupported) with(density) {
            RenderEffect
                .createBlurEffect(BLUR_RADIUS.toPx(), BLUR_RADIUS.toPx(), Shader.TileMode.MIRROR)
                .asComposeRenderEffect()
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = blur
                    rotationZ = rotation.value
                    scaleX = 1.4f
                    scaleY = 1.4f
                }
        ) {
            val width = size.width
            val height = size.height

            drawRect(color = color1.copy(alpha = 0.5f))

            drawBlob(
                color = color2,
                radius = width * 0.6f,
                center = Offset(x = width * move1.value, y = height * move2.value),
                alpha = 0.7f
            )
            drawBlob(
                color = color3,
                radius = width * 0.7f,
                center = Offset(x = width * (1 - move2.value), y = height * move3.value),
                alpha = 0.6f
            )
            drawBlob(
                color = color4,
                radius = width * 0.5f,
                center = Offset(x = width * move3.value, y = height * (1 - move1.value)),
                alpha = 0.7f
            )
            drawBlob(
                color = color1,
                radius = width * 0.8f,
                center = Offset(x = width * 0.2f, y = height * 0.8f),
                alpha = 0.6f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fallbackColor.copy(alpha = 0.45f))
        )

        // Dithers the blurred gradients, which would otherwise band on 8-bit displays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = rememberNoiseBrush(), alpha = NOISE_ALPHA)
        )
    }
}

/**
 * Rounds this to multiples of [step]. Kept as a [State] so it is read in the draw and layer phases:
 * the aurora then only redraws when a blob has moved far enough to see, not on every frame.
 */
@Composable
private fun State<Float>.stepped(step: Float): State<Float> {
    val source = this

    return remember(source, step) {
        derivedStateOf { (source.value / step).toInt() * step }
    }
}

@Composable
private fun rememberNoiseBrush(): Brush {
    val noise = remember {
        val random = Random(seed = 0)
        val pixels = IntArray(NOISE_SIZE * NOISE_SIZE) {
            val luminance = random.nextInt(from = 0, until = 256)
            (0xff shl 24) or (luminance shl 16) or (luminance shl 8) or luminance
        }

        Bitmap
            .createBitmap(pixels, NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    return remember(noise) {
        ShaderBrush(
            ImageShader(
                image = noise,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
        )
    }
}

private fun DrawScope.drawBlob(
    color: Color,
    radius: Float,
    center: Offset,
    alpha: Float
) = if (blurSupported) drawCircle(
    color = color,
    radius = radius,
    center = center,
    alpha = alpha
) else drawCircle(
    brush = Brush.radialGradient(
        colors = listOf(color, color.copy(alpha = 0f)),
        center = center,
        radius = radius
    ),
    radius = radius,
    center = center,
    alpha = alpha
)
