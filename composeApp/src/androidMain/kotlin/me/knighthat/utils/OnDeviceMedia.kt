package me.knighthat.utils

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.OnDeviceSongSortBy
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.models.Format
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.LOCAL_KEY_PREFIX
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import it.fast4x.rimusic.utils.isAtLeastAndroid11
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

val PROJECTION by lazy {
    var projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID,
        if (isAtLeastAndroid10) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        },
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.IS_MUSIC,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE,
    )
    if ( isAtLeastAndroid11 )
        projection += MediaStore.Audio.Media.BITRATE

    return@lazy projection
}
val ALBUM_URI = "content://media/external/audio/albumart".toUri()

private fun blacklistedPaths( context: Context ): Set<String> {
    val file = File(context.filesDir, "Blacklisted_paths.txt")
    return  if( file.exists() )
        file.readLines().toSet()
    else
        emptySet()
}

private fun Context.getLocalSongs(
    uri: Uri,
    sortBy: OnDeviceSongSortBy,
    sortOrder: SortOrder
): Map<Song, String> {
    val results = mutableMapOf<Song, String>()
    val blacklistedPaths = blacklistedPaths( this )

    contentResolver.query(
        uri,
        PROJECTION,
        null,
        null,
        "${sortBy.value} COLLATE NOCASE ${sortOrder.asSqlString}"
    )?.use {  cursor ->
        val idColumn = cursor.getColumnIndex( MediaStore.Audio.Media._ID )
        val nameColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DISPLAY_NAME )
        val durationColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DURATION )
        val artistColumn = cursor.getColumnIndex( MediaStore.Audio.Media.ARTIST )
        val albumIdColumn = cursor.getColumnIndex( MediaStore.Audio.Media.ALBUM_ID )
        val relativePathColumn = if (isAtLeastAndroid10) {
            cursor.getColumnIndex( MediaStore.Audio.Media.RELATIVE_PATH )
        } else {
            cursor.getColumnIndex( MediaStore.Audio.Media.DATA )
        }
        val titleColumn = cursor.getColumnIndex( MediaStore.Audio.Media.TITLE )
        val isMusicColumn = cursor.getColumnIndex( MediaStore.Audio.Media.IS_MUSIC )
        val mimeTypeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.MIME_TYPE )
        val bitrateColumn = if (isAtLeastAndroid11) cursor.getColumnIndex( MediaStore.Audio.Media.BITRATE ) else -1
        val fileSizeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.SIZE )
        val dateModifiedColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DATE_MODIFIED )

        while( cursor.moveToNext() && cursor.getInt( isMusicColumn ) == 1 ) {
            val relPath = cursor.getString( relativePathColumn ).apply {
                if( !isAtLeastAndroid10 ) substringAfterLast( "/" )
            }
            if( blacklistedPaths.contains( relPath ) ) continue

            // Nullable so SongItem can display "--:--"
            // TODO apply some non-null method
            val durationText =
                cursor.getInt( durationColumn )
                      .takeIf { it > 0 }
                      ?.milliseconds
                      ?.toComponents { hrs, mins, secs, _ ->
                          if( hrs > 0 )
                              "%02d:%02d:%02d".format( hrs, mins, secs )
                          else
                              "%02d:%02d".format( mins, secs )
                      }
            val id = cursor.getLong( idColumn )
            val title = cursor.getString( titleColumn ) ?: cursor.getString( nameColumn )
            val artist = cursor.getString( artistColumn )
            val albumUri = ContentUris.withAppendedId( ALBUM_URI, cursor.getLong( albumIdColumn ) )
            val song = Song( "$LOCAL_KEY_PREFIX$id", title, artist, durationText, albumUri.toString() )

            val mimeType = cursor.getString( mimeTypeColumn )
            val bitrate = if( isAtLeastAndroid11 ) cursor.getLong( bitrateColumn ) else 0
            val fileSize = cursor.getLong( fileSizeColumn )
            val dateModified = cursor.getLong( dateModifiedColumn )
            val format = Format( song.id, 0, mimeType, bitrate, fileSize, dateModified )

            Database.asyncTransaction {
                songTable.insertIgnore( song )
                formatTable.upsert( format )
            }

            results[song] = relPath
        }
    }

    return results
}

fun Context.getLocalSongs(
    sortBy: OnDeviceSongSortBy,
    sortOrder: SortOrder
): Flow<Map<Song, String>> = callbackFlow {

    val uri =
        if (isAtLeastAndroid10)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange( selfChange: Boolean ) {
            trySend( getLocalSongs( uri, sortBy, sortOrder ) )
        }
    }

    contentResolver.registerContentObserver( uri, true, observer )

    trySend( getLocalSongs( uri, sortBy, sortOrder ) )

    awaitClose {
        contentResolver.unregisterContentObserver( observer )
    }
}
