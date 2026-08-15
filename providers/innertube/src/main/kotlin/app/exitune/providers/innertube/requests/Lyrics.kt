package app.exitune.providers.innertube.requests

import app.exitune.providers.innertube.Innertube
import app.exitune.providers.innertube.models.BrowseResponse
import app.exitune.providers.innertube.models.NextResponse
import app.exitune.providers.innertube.models.bodies.BrowseBody
import app.exitune.providers.innertube.models.bodies.NextBody
import app.exitune.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

suspend fun Innertube.lyrics(body: NextBody) = runCatchingCancellable {
    val nextResponse = client.post(NEXT) {
        setBody(body)
        @Suppress("all")
        mask(
            "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)"
        )
    }.body<NextResponse>()

    val browseId = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?.getOrNull(1)
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingCancellable null

    val response = client.post(BROWSE) {
        setBody(BrowseBody(browseId = browseId))
        mask("contents.sectionListRenderer.contents.musicDescriptionShelfRenderer.description")
    }.body<BrowseResponse>()

    response.contents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicDescriptionShelfRenderer
        ?.description
        ?.text
}
