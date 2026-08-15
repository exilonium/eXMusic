package app.exmusic.exilonium.ui.screens.settings

import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.exmusic.exilonium.LocalPlayerServiceBinder
import app.exmusic.exilonium.R
import app.exmusic.exilonium.preferences.DataPreferences
import app.exmusic.exilonium.preferences.PlayerPreferences
import app.exmusic.exilonium.ui.components.themed.LinearProgressIndicator
import app.exmusic.exilonium.ui.components.themed.SecondaryTextButton
import app.exmusic.exilonium.ui.screens.Route
import app.exmusic.core.data.enums.ExoPlayerDiskCacheSize
import coil3.imageLoader

@OptIn(UnstableApi::class)
@Route
@Composable
fun CacheSettings() = with(DataPreferences) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val imageCache = remember(context) { context.imageLoader.diskCache }

    SettingsCategoryScreen(title = stringResource(R.string.cache)) {
        SettingsDescription(text = stringResource(R.string.cache_description))

        var imageCacheSize by remember(imageCache) { mutableLongStateOf(imageCache?.size ?: 0L) }
        imageCache?.let { diskCache ->
            val formattedSize = remember(imageCacheSize) {
                Formatter.formatShortFileSize(context, imageCacheSize)
            }
            val sizePercentage = remember(imageCacheSize, coilDiskCacheMaxSize) {
                imageCacheSize.toFloat() / coilDiskCacheMaxSize.bytes.coerceAtLeast(1)
            }

            SettingsGroup(
                title = stringResource(R.string.image_cache),
                description = stringResource(
                    R.string.format_cache_space_used_percentage,
                    formattedSize,
                    (sizePercentage * 100).toInt()
                ),
                trailingContent = {
                    SecondaryTextButton(
                        text = stringResource(R.string.clear),
                        onClick = {
                            diskCache.clear()
                            imageCacheSize = 0L
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            ) {
                LinearProgressIndicator(
                    progress = sizePercentage,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .padding(start = 32.dp, end = 16.dp)
                )
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.max_size),
                    selectedValue = coilDiskCacheMaxSize,
                    onValueSelect = { coilDiskCacheMaxSize = it }
                )
            }
        }
        binder?.cache?.let { cache ->
            val diskCacheSize by remember { derivedStateOf { cache.cacheSpace } }
            val formattedSize = remember(diskCacheSize) {
                Formatter.formatShortFileSize(context, diskCacheSize)
            }
            val sizePercentage = remember(diskCacheSize, exoPlayerDiskCacheMaxSize) {
                diskCacheSize.toFloat() / exoPlayerDiskCacheMaxSize.bytes.coerceAtLeast(1)
            }

            SettingsGroup(
                title = stringResource(R.string.song_cache),
                description = if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheSize.Unlimited) stringResource(
                    R.string.format_cache_space_used,
                    formattedSize
                )
                else stringResource(
                    R.string.format_cache_space_used_percentage,
                    formattedSize,
                    (sizePercentage * 100).toInt()
                )
            ) {
                AnimatedVisibility(visible = exoPlayerDiskCacheMaxSize != ExoPlayerDiskCacheSize.Unlimited) {
                    LinearProgressIndicator(
                        progress = sizePercentage,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .padding(start = 32.dp, end = 16.dp)
                    )
                }
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.max_size),
                    selectedValue = exoPlayerDiskCacheMaxSize,
                    onValueSelect = { exoPlayerDiskCacheMaxSize = it }
                )
                SwitchSettingsEntry(
                    title = stringResource(R.string.pause_song_cache),
                    text = stringResource(R.string.pause_song_cache_description),
                    isChecked = PlayerPreferences.pauseCache,
                    onCheckedChange = { PlayerPreferences.pauseCache = it }
                )
            }
        }
    }
}
