package app.exitune.android.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exitune.android.Database
import app.exitune.android.LocalPlayerAwareWindowInsets
import app.exitune.android.R
import app.exitune.android.models.PipedSession
import app.exitune.android.models.Playlist
import app.exitune.android.models.PlaylistPreview
import app.exitune.android.preferences.DataPreferences
import app.exitune.android.preferences.OrderPreferences
import app.exitune.android.preferences.UIStatePreferences
import app.exitune.android.query
import app.exitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exitune.android.ui.components.themed.Header
import app.exitune.android.ui.components.themed.HeaderIconButton
import app.exitune.android.ui.components.themed.SecondaryTextButton
import app.exitune.android.ui.components.themed.TextFieldDialog
import app.exitune.android.ui.components.themed.VerticalDivider
import app.exitune.android.ui.items.PlaylistItem
import app.exitune.android.ui.screens.Route
import app.exitune.android.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import app.exitune.android.ui.screens.settings.SettingsEntryGroupText
import app.exitune.android.ui.screens.settings.SettingsGroupSpacer
import app.exitune.compose.persist.persist
import app.exitune.compose.persist.persistList
import app.exitune.core.data.enums.BuiltInPlaylist
import app.exitune.core.data.enums.PlaylistSortBy
import app.exitune.core.data.enums.SortOrder
import app.exitune.core.ui.Dimensions
import app.exitune.core.ui.LocalAppearance
import app.exitune.providers.piped.Piped
import app.exitune.providers.piped.models.Session
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import app.exitune.providers.piped.models.PlaylistPreview as PipedPlaylistPreview

@Route
@Composable
fun HomePlaylists(
    onBuiltInPlaylist: (BuiltInPlaylist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPipedPlaylistClick: (Session, PipedPlaylistPreview) -> Unit,
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    val (colorPalette) = LocalAppearance.current

    var isCreatingANewPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isCreatingANewPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        onDismiss = { isCreatingANewPlaylist = false },
        onAccept = { text ->
            query {
                Database.insert(Playlist(name = text))
            }
        }
    )
    var items by persistList<PlaylistPreview>("home/playlists")
    var pipedSessions by persist<Map<PipedSession, List<PipedPlaylistPreview>?>>("home/piped")

    LaunchedEffect(playlistSortBy, playlistSortOrder) {
        Database
            .playlistPreviews(playlistSortBy, playlistSortOrder)
            .collect { items = it.toImmutableList() }
    }

    LaunchedEffect(Unit) {
        Database.pipedSessions().collect { sessions ->
            pipedSessions = sessions.associateWith { session ->
                async {
                    Piped.playlist.list(session = session.toApiSession())?.getOrNull()
                }
            }.mapValues { (_, value) -> value.await() }
        }
    }

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (playlistSortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = ""
    )

    val lazyGridState = rememberLazyGridState()

    val builtInPlaylists by BuiltInPlaylistScreen.shownPlaylistsAsState()

    Box {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = if (UIStatePreferences.playlistsAsGrid)
                GridCells.Adaptive(Dimensions.thumbnails.playlist + Dimensions.items.alternativePadding * 2)
            else GridCells.Fixed(1),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.items.alternativePadding),
            verticalArrangement = if (UIStatePreferences.playlistsAsGrid)
                Arrangement.spacedBy(Dimensions.items.alternativePadding)
            else Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette.background0)
        ) {
            item(key = "header", contentType = 0, span = { GridItemSpan(maxLineSpan) }) {
                Header(title = stringResource(R.string.playlists)) {
                    SecondaryTextButton(
                        text = stringResource(R.string.new_playlist),
                        onClick = { isCreatingANewPlaylist = true }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    HeaderIconButton(
                        icon = if (UIStatePreferences.playlistsAsGrid) R.drawable.grid else R.drawable.list,
                        onClick = {
                            UIStatePreferences.playlistsAsGrid = !UIStatePreferences.playlistsAsGrid
                        }
                    )

                    VerticalDivider(modifier = Modifier.height(8.dp))

                    HeaderIconButton(
                        icon = R.drawable.medical,
                        enabled = playlistSortBy == PlaylistSortBy.SongCount,
                        onClick = { playlistSortBy = PlaylistSortBy.SongCount }
                    )

                    HeaderIconButton(
                        icon = R.drawable.text,
                        enabled = playlistSortBy == PlaylistSortBy.Name,
                        onClick = { playlistSortBy = PlaylistSortBy.Name }
                    )

                    HeaderIconButton(
                        icon = R.drawable.time,
                        enabled = playlistSortBy == PlaylistSortBy.DateAdded,
                        onClick = { playlistSortBy = PlaylistSortBy.DateAdded }
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    HeaderIconButton(
                        icon = R.drawable.arrow_up,
                        color = colorPalette.text,
                        onClick = { playlistSortOrder = !playlistSortOrder },
                        modifier = Modifier.graphicsLayer { rotationZ = sortOrderIconRotation }
                    )
                }
            }

            // TODO: clean up (also in BuiltInPlaylistScreen): icon etc. could live in BuiltInPlaylist (cleans up duplicate code mess)

            if (BuiltInPlaylist.Favorites in builtInPlaylists) item(key = "favorites") {
                PlaylistItem(
                    icon = R.drawable.heart,
                    colorTint = colorPalette.red,
                    name = stringResource(R.string.favorites),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = UIStatePreferences.playlistsAsGrid,
                    modifier = Modifier
                        .animateItem()
                        .clickable { onBuiltInPlaylist(BuiltInPlaylist.Favorites) }
                )
            }

            if (BuiltInPlaylist.Offline in builtInPlaylists) item(key = "offline") {
                PlaylistItem(
                    icon = R.drawable.airplane,
                    colorTint = colorPalette.blue,
                    name = stringResource(R.string.offline),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = UIStatePreferences.playlistsAsGrid,
                    modifier = Modifier
                        .animateItem()
                        .clickable { onBuiltInPlaylist(BuiltInPlaylist.Offline) }
                )
            }

            if (BuiltInPlaylist.Top in builtInPlaylists) item(key = "top") {
                PlaylistItem(
                    icon = R.drawable.trending,
                    colorTint = colorPalette.red,
                    name = stringResource(
                        R.string.format_my_top_playlist,
                        DataPreferences.topListLength
                    ),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = UIStatePreferences.playlistsAsGrid,
                    modifier = Modifier
                        .animateItem()
                        .clickable { onBuiltInPlaylist(BuiltInPlaylist.Top) }
                )
            }

            if (BuiltInPlaylist.History in builtInPlaylists) item(key = "history") {
                PlaylistItem(
                    icon = R.drawable.history,
                    colorTint = colorPalette.textDisabled,
                    name = stringResource(R.string.history),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = UIStatePreferences.playlistsAsGrid,
                    modifier = Modifier
                        .animateItem()
                        .clickable { onBuiltInPlaylist(BuiltInPlaylist.History) }
                )
            }

            items(
                items = items,
                key = { it.playlist.id }
            ) { playlistPreview ->
                PlaylistItem(
                    playlist = playlistPreview,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = UIStatePreferences.playlistsAsGrid,
                    modifier = Modifier
                        .clickable(onClick = { onPlaylistClick(playlistPreview.playlist) })
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }

            pipedSessions
                ?.ifEmpty { null }
                ?.filter { it.value?.isNotEmpty() == true }
                ?.forEach { (session, playlists) ->
                    item(
                        key = "piped-header-${session.username}",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SettingsGroupSpacer()
                        SettingsEntryGroupText(title = session.username)
                    }

                    playlists?.let {
                        items(
                            items = playlists,
                            key = { "piped-${session.username}-${it.id}" }
                        ) { playlist ->
                            PlaylistItem(
                                name = playlist.name,
                                songCount = playlist.videoCount,
                                channelName = null,
                                thumbnailUrl = playlist.thumbnailUrl.toString(),
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = UIStatePreferences.playlistsAsGrid,
                                modifier = Modifier
                                    .clickable(onClick = {
                                        onPipedPlaylistClick(
                                            session.toApiSession(),
                                            playlist
                                        )
                                    })
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            )
                        }
                    }
                }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyGridState = lazyGridState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
