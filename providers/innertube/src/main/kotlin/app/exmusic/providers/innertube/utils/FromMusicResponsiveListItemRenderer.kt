package app.exmusic.providers.innertube.utils

import app.exmusic.providers.innertube.Innertube
import app.exmusic.providers.innertube.models.MusicResponsiveListItemRenderer
import app.exmusic.providers.innertube.models.NavigationEndpoint
import app.exmusic.providers.innertube.models.isExplicit
import app.exmusic.providers.innertube.models.largest

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer) =
    Innertube.SongItem(
        info = renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.let {
                if (it.navigationEndpoint?.endpoint is NavigationEndpoint.Endpoint.Watch) Innertube.Info(
                    name = it.text,
                    endpoint = it.navigationEndpoint.endpoint as NavigationEndpoint.Endpoint.Watch
                ) else null
            },
        // This inspection is not true
        authors = @Suppress("FilterIsInstanceResultIsAlwaysEmpty") renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.map { Innertube.Info(name = it.text, endpoint = it.navigationEndpoint?.endpoint) }
            ?.filterIsInstance<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>()
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        explicit = renderer.badges.isExplicit,
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.largest
    ).takeIf { it.info?.endpoint?.videoId != null }
