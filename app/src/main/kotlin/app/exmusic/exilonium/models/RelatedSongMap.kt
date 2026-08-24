package app.exmusic.exilonium.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A song YouTube recommended off the back of another one. Harvested in the background whenever a
 * song is played, so quick picks can be built from what the library already knows instead of a
 * request per app launch.
 */
@Immutable
@Entity(
    primaryKeys = ["songId", "relatedSongId"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["relatedSongId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RelatedSongMap(
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val relatedSongId: String
)
