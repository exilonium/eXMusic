package app.exmusic.providers.innertube.models.bodies

import app.exmusic.providers.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context = Context.DefaultWeb,
    val query: String,
    /**
     * Which kind of result to search for; left out for an unfiltered search.
     */
    val params: String? = null
)
