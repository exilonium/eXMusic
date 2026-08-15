package app.exitune.android.preferences

import app.exitune.android.GlobalPreferencesHolder
import app.exitune.core.data.enums.AlbumSortBy
import app.exitune.core.data.enums.ArtistSortBy
import app.exitune.core.data.enums.PlaylistSortBy
import app.exitune.core.data.enums.SongSortBy
import app.exitune.core.data.enums.SortOrder

object OrderPreferences : GlobalPreferencesHolder() {
    var songSortOrder by enum(SortOrder.Descending)
    var localSongSortOrder by enum(SortOrder.Descending)
    var playlistSortOrder by enum(SortOrder.Descending)
    var albumSortOrder by enum(SortOrder.Descending)
    var artistSortOrder by enum(SortOrder.Descending)

    var songSortBy by enum(SongSortBy.DateAdded)
    var localSongSortBy by enum(SongSortBy.DateAdded)
    var playlistSortBy by enum(PlaylistSortBy.DateAdded)
    var albumSortBy by enum(AlbumSortBy.DateAdded)
    var artistSortBy by enum(ArtistSortBy.DateAdded)
}
