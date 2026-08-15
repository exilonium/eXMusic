package app.exmusic.exilonium.ui.screens.album

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.exmusic.compose.persist.PersistMapCleanup
import app.exmusic.compose.persist.persist
import app.exmusic.compose.persist.persistList
import app.exmusic.compose.routing.RouteHandler
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.core.ui.utils.stateFlowSaver
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Album
import app.exmusic.exilonium.models.Song
import app.exmusic.exilonium.models.SongAlbumMap
import app.exmusic.exilonium.query
import app.exmusic.exilonium.transaction
import app.exmusic.exilonium.ui.components.themed.Header
import app.exmusic.exilonium.ui.components.themed.HeaderIconButton
import app.exmusic.exilonium.ui.components.themed.HeaderPlaceholder
import app.exmusic.exilonium.ui.components.themed.PlaylistInfo
import app.exmusic.exilonium.ui.components.themed.Scaffold
import app.exmusic.exilonium.ui.components.themed.adaptiveThumbnailContent
import app.exmusic.exilonium.ui.items.AlbumItem
import app.exmusic.exilonium.ui.items.AlbumItemPlaceholder
import app.exmusic.exilonium.ui.screens.GlobalRoutes
import app.exmusic.exilonium.ui.screens.Route
import app.exmusic.exilonium.ui.screens.albumRoute
import app.exmusic.exilonium.ui.screens.searchresult.ItemsPage
import app.exmusic.exilonium.utils.asMediaItem
import app.exmusic.exilonium.utils.completed
import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.bodies.BrowseBody
import app.exmusic.providers.innertube.requests.albumPage
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Route
@Composable
fun AlbumScreen(browseId: String) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val tabIndexState = rememberSaveable(saver = stateFlowSaver()) { MutableStateFlow(0) }
    val tabIndex by tabIndexState.collectAsState()

    var album by persist<Album?>("album/$browseId/album")
    var albumPage by persist<Innertube.PlaylistOrAlbumPage?>("album/$browseId/albumPage")
    var songs by persistList<Song>("album/$browseId/songs")

    PersistMapCleanup(prefix = "album/$browseId/")

    LaunchedEffect(Unit) {
        Database
            .albumSongs(browseId)
            .distinctUntilChanged()
            .combine(
                Database
                    .album(browseId)
                    .distinctUntilChanged()
                    .cancellable()
            ) { currentSongs, currentAlbum ->
                album = currentAlbum
                songs = currentSongs.toImmutableList()

                withContext(Dispatchers.IO) {
                    Innertube.albumPage(BrowseBody(browseId = browseId))
                        ?.completed()
                        ?.onSuccess { newAlbumPage ->
                            albumPage = newAlbumPage

                            transaction {
                                Database.clearAlbum(browseId)

                                Database.upsert(
                                    album = Album(
                                        id = browseId,
                                        title = newAlbumPage.title,
                                        description = newAlbumPage.description,
                                        thumbnailUrl = newAlbumPage.thumbnail?.url,
                                        year = newAlbumPage.year,
                                        authorsText = newAlbumPage.authors
                                            ?.joinToString("") { it.name.orEmpty() },
                                        shareUrl = newAlbumPage.url,
                                        timestamp = System.currentTimeMillis(),
                                        bookmarkedAt = album?.bookmarkedAt,
                                        otherInfo = newAlbumPage.otherInfo
                                    ),
                                    songAlbumMaps = newAlbumPage
                                        .songsPage
                                        ?.items
                                        ?.map { it.asMediaItem }
                                        ?.onEach { Database.insert(it) }
                                        ?.mapIndexed { position, mediaItem ->
                                            SongAlbumMap(
                                                songId = mediaItem.mediaId,
                                                albumId = browseId,
                                                position = position
                                            )
                                        } ?: emptyList()
                                )
                            }
                        }?.exceptionOrNull()?.printStackTrace()
                }
            }.collect()
    }

    RouteHandler {
        GlobalRoutes()

        Content {
            val headerContent: @Composable (
                beforeContent: (@Composable () -> Unit)?,
                afterContent: (@Composable () -> Unit)?
            ) -> Unit = { beforeContent, afterContent ->
                if (album?.timestamp == null) HeaderPlaceholder(modifier = Modifier.shimmer())
                else {
                    val (colorPalette) = LocalAppearance.current
                    val context = LocalContext.current

                    Header(title = album?.title ?: stringResource(R.string.unknown)) {
                        beforeContent?.invoke()

                        Spacer(modifier = Modifier.weight(1f))

                        afterContent?.invoke()

                        HeaderIconButton(
                            icon = if (album?.bookmarkedAt == null) R.drawable.bookmark_outline
                            else R.drawable.bookmark,
                            color = colorPalette.accent,
                            onClick = {
                                val bookmarkedAt =
                                    if (album?.bookmarkedAt == null) System.currentTimeMillis() else null

                                query {
                                    album
                                        ?.copy(bookmarkedAt = bookmarkedAt)
                                        ?.let(Database::update)
                                }
                            }
                        )

                        HeaderIconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette.text,
                            onClick = {
                                album?.shareUrl?.let { url ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }

                                    context.startActivity(
                                        Intent.createChooser(sendIntent, null)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            val thumbnailContent = adaptiveThumbnailContent(
                isLoading = album?.timestamp == null,
                url = album?.thumbnailUrl
            )

            Scaffold(
                key = "album",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = { newTab -> tabIndexState.update { newTab } },
                tabColumnContent = {
                    tab(0, R.string.songs, R.drawable.musical_notes, canHide = false)
                    tab(1, R.string.other_versions, R.drawable.disc)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> AlbumSongs(
                            songs = songs,
                            headerContent = headerContent,
                            thumbnailContent = thumbnailContent,
                            afterHeaderContent = {
                                if (album == null) PlaylistInfo(playlist = albumPage)
                                else PlaylistInfo(playlist = album)
                            }
                        )

                        1 -> {
                            ItemsPage(
                                tag = "album/$browseId/alternatives",
                                header = headerContent,
                                initialPlaceholderCount = 1,
                                continuationPlaceholderCount = 1,
                                emptyItemsText = stringResource(R.string.no_alternative_version),
                                provider = albumPage?.let {
                                    {
                                        Result.success(
                                            Innertube.ItemsPage(
                                                items = albumPage?.otherVersions,
                                                continuation = null
                                            )
                                        )
                                    }
                                },
                                itemContent = { album ->
                                    AlbumItem(
                                        album = album,
                                        thumbnailSize = Dimensions.thumbnails.album,
                                        modifier = Modifier.clickable { albumRoute(album.key) }
                                    )
                                },
                                itemPlaceholderContent = {
                                    AlbumItemPlaceholder(thumbnailSize = Dimensions.thumbnails.album)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
