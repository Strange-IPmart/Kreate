package it.fast4x.rimusic.utils

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.utils.completed
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.Database.Companion.albumTable
import it.fast4x.rimusic.Database.Companion.getAlbumsList
import it.fast4x.rimusic.Database.Companion.getArtistsList
import it.fast4x.rimusic.YTP_PREFIX
import it.fast4x.rimusic.isAutoSyncEnabled
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.SongPlaylistMap
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster

suspend fun importYTMPrivatePlaylists(): Boolean {
    if (isYouTubeSyncEnabled()) {

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        Innertube.library("FEmusic_liked_playlists").completed().onSuccess { page ->

            val ytmPrivatePlaylists = page.items.filterIsInstance<Innertube.PlaylistItem>()
                .filterNot { it.key == "VLLM" || it.key == "VLSE" }

            val localPlaylists = Database.ytmPrivatePlaylists().firstOrNull()

            Database.asyncTransaction {
                (localPlaylists?.filter { playlist -> playlist.browseId !in ytmPrivatePlaylists.map { if (it.key.startsWith("VL")) it.key.substringAfter("VL") else it.key }  })
                    ?.forEach( playlistTable::delete )
            }

            ytmPrivatePlaylists.forEach { remotePlaylist ->
                withContext(Dispatchers.IO) {
                    val playlistIdChecked =
                        if (remotePlaylist.key.startsWith("VL")) remotePlaylist.key.substringAfter("VL") else remotePlaylist.key
                    println("Playlist ${localPlaylists?.map { it.browseId }}")
                    var localPlaylist =
                        localPlaylists?.find { it.browseId == playlistIdChecked }

                    println("Local playlist: $localPlaylist")
                    println("Remote playlist: $remotePlaylist")
                    if (localPlaylist == null && playlistIdChecked.isNotEmpty()) {
                        localPlaylist = Playlist(
                            name = (remotePlaylist.title) ?: "",
                            browseId = playlistIdChecked,
                            isYoutubePlaylist = true,
                            isEditable = (remotePlaylist.isEditable == true)
                        )

                        val playlist = localPlaylist.copy(browseId = playlistIdChecked)
                        Database.playlistTable.upsert( playlist )
                    } else {
                        Database.updatePlaylistName(YTP_PREFIX+remotePlaylist.title, localPlaylist?.id ?: 0L)
                    }

                    Database.playlistWithSongsByBrowseId(playlistIdChecked).firstOrNull()?.let {
                          if (it.playlist.id != 0L && it.songs.isEmpty())
                            it.playlist.id.let { id ->
                                ytmPrivatePlaylistSync(
                                    it.playlist,
                                    id
                                )
                            }
                    }
                }
            }

        }.onFailure {
            println("Error importing YTM private playlists: ${it.stackTraceToString()}")
            return false
        }
        return true
    } else
        return false
}

@OptIn(UnstableApi::class)
fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) {
    playlist.let { plist ->
        Database.asyncTransaction {
            runBlocking(Dispatchers.IO) {
                withContext(Dispatchers.IO) {
                    plist.browseId?.let {
                        YtMusic.getPlaylist(
                            playlistId = it
                        ).completed()
                    }
                }
            }?.getOrNull()?.let { remotePlaylist ->
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.IO) {

                        println("ytmPrivatePlaylistSync Remote playlist editable: ${remotePlaylist.isEditable}")

                        // Update here playlist isEditable flag because library contain playlists but isEditable isn't always available
                        if (remotePlaylist.isEditable == true)
                            Database.playlistTable
                                    .update( playlist.copy(isEditable = true) )

                        if (remotePlaylist.songs.isNotEmpty()) {
                            //Database.clearPlaylist(playlistId)

                            remotePlaylist.songs
                                .map(Innertube.SongItem::asMediaItem)
                                .onEach(Database::insert)
                                .mapIndexed { position, mediaItem ->
                                    SongPlaylistMap(
                                        songId = mediaItem.mediaId,
                                        playlistId = playlistId,
                                        position = position,
                                        setVideoId = mediaItem.mediaMetadata.extras?.getString("setVideoId"),
                                    ).default()
                                }.let( songPlaylistMapTable::insertIgnore )
                        }

                        /*localPlaylistSongs.filter { it.asMediaItem.mediaId !in remotePlaylist.songs.map { it.asMediaItem.mediaId } }
                            .forEach { song ->
                                deleteSongFromPlaylist(song.asMediaItem.mediaId, playlistId)
                            }*/
                    }
                }
            }
        }
    }
}

suspend fun importYTMSubscribedChannels(): Boolean {
    println("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        Innertube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->

            val ytmArtists = page.items.filterIsInstance<Innertube.ArtistItem>()

            println("YTM artists: $ytmArtists")

            ytmArtists.forEach { remoteArtist ->
                withContext(Dispatchers.IO) {

                    var localArtist = Database.artist(remoteArtist.key).firstOrNull()
                    println("Local artist: $localArtist")
                    println("Remote artist: $remoteArtist")

                    if (localArtist == null) {
                        localArtist = Artist(
                            id = remoteArtist.key,
                            name = remoteArtist.title,
                            thumbnailUrl = remoteArtist.thumbnail?.url,
                            bookmarkedAt = System.currentTimeMillis(),
                            isYoutubeArtist = true
                        )
                        Database.artistTable.insertReplace( localArtist )
                    } else {
                        localArtist.copy(
                            bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                            thumbnailUrl = remoteArtist.thumbnail?.url,
                            isYoutubeArtist = true
                        ).let( Database.artistTable::update )
                    }


                }
            }
            val Artists = getArtistsList().firstOrNull()
            Database.asyncTransaction {
                Artists?.filter {artist -> artist?.isYoutubeArtist == true && artist.id !in ytmArtists.map { it.key } }?.forEach { artist ->

                    artist?.copy(isYoutubeArtist = false, bookmarkedAt = null)
                          ?.let( artistTable::update )
                }
            }
        }
            .onFailure {
                println("Error importing YTM subscribed artists channels: ${it.stackTraceToString()}")
                return false
            }
        return true
    } else
        return false
}

suspend fun importYTMLikedAlbums(): Boolean {
    println("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        Innertube.library("FEmusic_liked_albums").completed().onSuccess { page ->

            val ytmAlbums = page.items.filterIsInstance<Innertube.AlbumItem>()

            println("YTM albums: $ytmAlbums")

            ytmAlbums.forEach { remoteAlbum ->
                withContext(Dispatchers.IO) {

                    var localAlbum = Database.album(remoteAlbum.key).firstOrNull()
                    println("Local album: $localAlbum")
                    println("Remote album: $remoteAlbum")

                    if (localAlbum == null) {
                        localAlbum = Album(
                            id = remoteAlbum.key,
                            title = remoteAlbum.title,
                            thumbnailUrl = remoteAlbum.thumbnail?.url,
                            bookmarkedAt = System.currentTimeMillis(),
                            year = remoteAlbum.year,
                            authorsText = remoteAlbum.authors?.getOrNull(1)?.name,
                            isYoutubeAlbum = true
                        )
                        Database.albumTable.insertReplace( localAlbum )
                    } else {
                        localAlbum.copy(
                            isYoutubeAlbum = true,
                            bookmarkedAt = localAlbum.bookmarkedAt ?: System.currentTimeMillis(),
                            thumbnailUrl = remoteAlbum.thumbnail?.url)
                            .let( albumTable::update )
                    }

                }
            }
            val Albums = getAlbumsList().firstOrNull()
            Database.asyncTransaction {
                Albums?.filter {album -> album?.isYoutubeAlbum == true && album.id !in ytmAlbums.map { it.key } }?.forEach { album->

                    album?.copy(isYoutubeAlbum = false, bookmarkedAt = null)
                         ?.let( albumTable::update )
                }
            }
        }
            .onFailure {
                println("Error importing YTM liked albums: ${it.stackTraceToString()}")
                return false
            }
        return true
    } else
        return false
}

suspend fun removeYTSongFromPlaylist(
    songId: String,
    playlistBrowseId: String,
    playlistId: Long,
): Boolean {

    println("removeYTSongFromPlaylist removeSongFromPlaylist params songId = $songId, playlistBrowseId = $playlistBrowseId, playlistId = $playlistId")

    if (isYouTubeSyncEnabled()) {
        Database.asyncTransaction {
            CoroutineScope(Dispatchers.IO).launch {
                val songSetVideoId = Database.getSetVideoIdFromPlaylist(songId, playlistId).firstOrNull()
                println("removeYTSongFromPlaylist removeSongFromPlaylist songSetVideoId = $songSetVideoId")
                if (songSetVideoId != null)
                    YtMusic.removeFromPlaylist(playlistId = playlistBrowseId, videoId =  songId, setVideoId = songSetVideoId)
            }
        }

        return true
    } else
        return false
}


@Composable
fun autoSyncToolbutton(messageId: Int): MenuIcon = object : MenuIcon, DynamicColor, Descriptive {

    override var isFirstColor: Boolean by rememberPreference(autosyncKey, false)
    override val iconId: Int = R.drawable.sync
    override val messageId: Int = messageId
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        isFirstColor = !isFirstColor
    }
}

