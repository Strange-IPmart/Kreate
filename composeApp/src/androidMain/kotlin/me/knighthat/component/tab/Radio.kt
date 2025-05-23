package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlayFromBeginning

@UnstableApi
class Radio private constructor(
    private val binder: PlayerServiceModern.Binder?,
    private val songs: () -> List<Song>
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ): Radio =
            Radio(
                LocalPlayerServiceBinder.current,
                songs
            )
    }

    override val iconId: Int = R.drawable.radio
    override val messageId: Int = R.string.info_start_radio
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        // Capture list here to prevent
        // unequals lists between calls.
        val songs = songs().map( Song::asMediaItem )

        binder?.stopRadio()
        binder?.player?.forcePlayFromBeginning( songs )
        binder?.setupRadio(
            NavigationEndpoint.Endpoint.Watch( videoId = songs.first().mediaId )
        )
    }
}