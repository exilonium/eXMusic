package app.exmusic.exilonium.ui.screens.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.exilonium.R
import app.exmusic.exilonium.importer.CsvColumns
import app.exmusic.exilonium.importer.CsvPlaylist
import app.exmusic.exilonium.importer.ImportResult
import app.exmusic.exilonium.importer.ImportedSong
import app.exmusic.exilonium.importer.PlaylistImporter
import app.exmusic.exilonium.importer.readCsvPlaylist
import app.exmusic.exilonium.ui.components.themed.DefaultDialog
import app.exmusic.exilonium.ui.components.themed.DialogTextButton
import app.exmusic.exilonium.ui.components.themed.LinearProgressIndicator
import app.exmusic.exilonium.ui.components.themed.TextFieldDialog
import app.exmusic.exilonium.utils.secondary
import app.exmusic.exilonium.utils.semiBold
import app.exmusic.exilonium.utils.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Playlists are tiny; anything bigger than this was not meant to be a playlist. */
private const val MAX_CSV_CHARS = 4 * 1024 * 1024

/**
 * Asks for any openable document. Storage providers report a CSV as whatever they feel like —
 * octet-stream, vnd.ms-excel, text/plain — and [ActivityResultContracts.OpenDocument] always
 * attaches EXTRA_MIME_TYPES, which several of them grey every file out over instead of matching a
 * wildcard against it. What was picked is checked by reading it instead of by type.
 */
private object OpenAnyDocument : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(
        Intent.ACTION_OPEN_DOCUMENT
    )
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")

    override fun parseResult(resultCode: Int, intent: Intent?) =
        intent.takeIf { resultCode == Activity.RESULT_OK }?.data
}

private sealed interface ImportStep {
    data class Mapping(val playlist: CsvPlaylist, val name: String) : ImportStep
    data class Naming(val songs: List<ImportedSong>, val name: String) : ImportStep
    data class Running(val processed: Int, val total: Int) : ImportStep
    data class Finished(val result: ImportResult, val total: Int) : ImportStep
}

/**
 * Imports a playlist exported as CSV. Everything here writes through the normal playlist tables, so
 * it neither reads nor replaces a database backup.
 */
@Composable
fun ImportSettingsGroup(modifier: Modifier = Modifier) {
    val (_, typography) = LocalAppearance.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var step by remember { mutableStateOf<ImportStep?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }

    val invalidMessage = stringResource(R.string.import_csv_invalid)
    val chooserMessage = stringResource(R.string.no_file_chooser_installed)

    val launcher = rememberLauncherForActivityResult(contract = OpenAnyDocument) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            val playlist = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        readCsvPlaylist(it.readText(MAX_CSV_CHARS))
                    }
                }.getOrNull()
            }

            if (playlist == null || playlist.rows.isEmpty()) {
                context.toast(invalidMessage)
                return@launch
            }

            step = ImportStep.Mapping(playlist = playlist, name = uri.playlistName(context))
        }
    }

    when (val current = step) {
        is ImportStep.Mapping -> ColumnMappingDialog(
            playlist = current.playlist,
            onDismiss = { step = null },
            onConfirm = { columns ->
                val songs = current.playlist.songs(columns)

                if (songs.isEmpty()) context.toast(invalidMessage)
                else step = ImportStep.Naming(songs = songs, name = current.name)
            }
        )

        is ImportStep.Naming -> TextFieldDialog(
            hintText = stringResource(R.string.enter_playlist_name_prompt),
            initialTextInput = current.name,
            onDismiss = { step = null },
            onAccept = { name ->
                val total = current.songs.size
                step = ImportStep.Running(processed = 0, total = total)

                importJob = coroutineScope.launch {
                    val result = PlaylistImporter.import(
                        songs = current.songs,
                        name = name
                    ) { processed ->
                        step = ImportStep.Running(processed = processed, total = total)
                    }

                    step = ImportStep.Finished(result = result, total = total)
                }
            }
        )

        is ImportStep.Running -> DefaultDialog(onDismiss = { }) {
            BasicText(
                text = stringResource(R.string.import_csv_running),
                style = typography.s.semiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = if (current.total == 0) 0f
                else current.processed.toFloat() / current.total,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            BasicText(
                text = stringResource(
                    R.string.format_import_csv_progress,
                    current.processed,
                    current.total
                ),
                style = typography.xxs.secondary
            )

            DialogTextButton(
                text = stringResource(R.string.cancel),
                onClick = {
                    importJob?.cancel()
                    importJob = null
                    step = null
                }
            )
        }

        is ImportStep.Finished -> DefaultDialog(onDismiss = { step = null }) {
            BasicText(
                text = stringResource(
                    R.string.format_import_csv_finished,
                    current.result.imported,
                    current.total
                ),
                style = typography.s.semiBold
            )

            if (current.result.failed.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                BasicText(
                    text = stringResource(R.string.import_csv_not_found),
                    style = typography.xxs.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    items(items = current.result.failed) { song ->
                        BasicText(
                            text = "${song.title} — ${song.artist}",
                            style = typography.xxs,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            DialogTextButton(
                text = stringResource(R.string.done),
                primary = true,
                onClick = { step = null }
            )
        }

        null -> Unit
    }

    SettingsGroup(
        title = stringResource(R.string.import_playlist),
        modifier = modifier
    ) {
        SettingsEntry(
            title = stringResource(R.string.import_from_csv),
            text = stringResource(R.string.import_from_csv_description),
            onClick = {
                try {
                    launcher.launch(Unit)
                } catch (_: ActivityNotFoundException) {
                    context.toast(chooserMessage)
                }
            }
        )
    }
}

@Composable
private fun ColumnMappingDialog(
    playlist: CsvPlaylist,
    onDismiss: () -> Unit,
    onConfirm: (CsvColumns) -> Unit
) {
    val (_, typography) = LocalAppearance.current

    var columns by remember(playlist) { mutableStateOf(playlist.columns) }
    val indices = remember(playlist) { (0 until playlist.columnCount).toImmutableList() }
    val optional = remember(playlist) {
        (listOf<Int?>(null) + (0 until playlist.columnCount)).toImmutableList()
    }

    val none = stringResource(R.string.none)
    val label: (Int?) -> String = { index ->
        index?.let { playlist.header.getOrNull(it)?.takeIf(String::isNotEmpty) ?: "#${it + 1}" }
            ?: none
    }

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        horizontalPadding = 0.dp
    ) {
        BasicText(
            text = stringResource(R.string.import_csv_columns),
            style = typography.s.semiBold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ValueSelectorSettingsEntry(
            title = stringResource(R.string.import_csv_title_column),
            selectedValue = columns.title,
            values = indices,
            onValueSelect = { columns = columns.copy(title = it) },
            valueText = { label(it) }
        )

        ValueSelectorSettingsEntry(
            title = stringResource(R.string.import_csv_artist_column),
            selectedValue = columns.artist,
            values = indices,
            onValueSelect = { columns = columns.copy(artist = it) },
            valueText = { label(it) }
        )

        ValueSelectorSettingsEntry(
            title = stringResource(R.string.import_csv_album_column),
            selectedValue = columns.album,
            values = optional,
            onValueSelect = { columns = columns.copy(album = it) },
            valueText = { label(it) }
        )

        val preview = remember(columns) { playlist.songs(columns).firstOrNull() }

        BasicText(
            text = preview?.let { "${it.title} — ${it.artist}" }
                ?: stringResource(R.string.import_csv_no_songs),
            style = typography.xxs.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            DialogTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            DialogTextButton(
                text = stringResource(R.string.confirm),
                primary = true,
                enabled = preview != null,
                onClick = { onConfirm(columns) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

private fun InputStream.readText(limit: Int): String {
    val reader = bufferedReader()
    val text = StringBuilder()
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)

    while (text.length < limit) {
        val read = reader.read(buffer)
        if (read <= 0) break

        text.appendRange(buffer, 0, read)
    }

    return text.toString()
}

private fun Uri.playlistName(context: Context): String {
    val name = context.contentResolver
        .query(this, null, null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    return name?.substringBeforeLast('.').orEmpty().ifEmpty {
        context.getString(R.string.new_playlist)
    }
}
