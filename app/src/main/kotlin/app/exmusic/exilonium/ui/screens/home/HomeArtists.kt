package app.exmusic.exilonium.ui.screens.home

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.exmusic.compose.persist.persistList
import app.exmusic.core.data.enums.ArtistSortBy
import app.exmusic.core.data.enums.SortOrder
import app.exmusic.core.ui.Dimensions
import app.exmusic.core.ui.LocalAppearance
import app.exmusic.exilonium.Database
import app.exmusic.exilonium.LocalPlayerAwareWindowInsets
import app.exmusic.exilonium.R
import app.exmusic.exilonium.models.Artist
import app.exmusic.exilonium.preferences.OrderPreferences
import app.exmusic.exilonium.ui.components.screenBackground
import app.exmusic.exilonium.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.exmusic.exilonium.ui.components.themed.Header
import app.exmusic.exilonium.ui.components.themed.HeaderIconButton
import app.exmusic.exilonium.ui.items.ArtistItem
import app.exmusic.exilonium.ui.screens.Route
import kotlinx.collections.immutable.toImmutableList

@Route
@Composable
fun HomeArtistList(
    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    val (colorPalette) = LocalAppearance.current

    var items by persistList<Artist>("home/artists")

    LaunchedEffect(artistSortBy, artistSortOrder) {
        Database
            .artists(artistSortBy, artistSortOrder)
            .collect { items = it.toImmutableList() }
    }

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (artistSortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(
            durationMillis = 400,
            easing = LinearEasing
        ),
        label = ""
    )

    val lazyGridState = rememberLazyGridState()

    Box {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(Dimensions.thumbnails.song * 2 + Dimensions.items.verticalPadding * 2),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(colorPalette.screenBackground)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0,
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Header(title = stringResource(R.string.artists)) {
                    HeaderIconButton(
                        icon = R.drawable.text,
                        enabled = artistSortBy == ArtistSortBy.Name,
                        onClick = { artistSortBy = ArtistSortBy.Name }
                    )

                    HeaderIconButton(
                        icon = R.drawable.time,
                        enabled = artistSortBy == ArtistSortBy.DateAdded,
                        onClick = { artistSortBy = ArtistSortBy.DateAdded }
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    HeaderIconButton(
                        icon = R.drawable.arrow_up,
                        color = colorPalette.text,
                        onClick = { artistSortOrder = !artistSortOrder },
                        modifier = Modifier.graphicsLayer { rotationZ = sortOrderIconRotation }
                    )
                }
            }

            items(items = items, key = Artist::id) { artist ->
                ArtistItem(
                    artist = artist,
                    thumbnailSize = Dimensions.thumbnails.song * 2,
                    alternative = true,
                    modifier = Modifier
                        .clickable(onClick = { onArtistClick(artist) })
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyGridState = lazyGridState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
