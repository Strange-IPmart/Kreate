package me.knighthat.component.tab

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.PopupType
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.components.themed.SmartMessage
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@UnstableApi
class Locator private constructor(
    firstColorState: MutableState<Boolean>,
    private val binder: PlayerServiceModern.Binder?,
    private val scrollableState: ScrollableState,
    private val getSongs: () -> List<Song>
): MenuIcon, DynamicColor, Descriptive {

    companion object {
        @Composable
        operator fun invoke(
            scrollableState: ScrollableState,
            getSongs: () -> List<Song>
        ): Locator {
            val binder = LocalPlayerServiceBinder.current
            val mediaItem = binder?.player?.currentMediaItem

            return Locator(
                firstColorState = remember( mediaItem ) {
                    mutableStateOf(mediaItem != null)
                },
                binder = binder,
                scrollableState = scrollableState,
                getSongs = getSongs
            )
        }
    }

    val position: Int
        get() = getSongs().map( Song::id ).indexOf( binder?.player?.currentMediaItem?.mediaId )

    override val iconId: Int = R.drawable.locate
    override val messageId: Int = R.string.info_find_the_song_that_is_playing
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstColor: Boolean by firstColorState

    override fun onShortClick() {
        if( isFirstColor ) {
            val mediaItem = binder?.player?.currentMediaItem
            // Capture songs here to prevent unwanted outcome
            // when a list is captured multiple times
            val songs = getSongs()

            Timber.tag("locator").d("LocateComponent.onShortClick songs ${songs.size} -> mediaItem ${mediaItem?.mediaId}")

            if( position == -1 )      // Playing song isn't inside [songs()]
                SmartMessage(
                    // TODO Add this string to strings.xml
                    message = "Couldn't find playing song on current list",
                    context = appContext(),
                    type = PopupType.Warning
                )
            else
                runBlocking {
                    when( scrollableState ) {
                        is LazyListState -> scrollableState.scrollToItem( position )
                        is LazyGridState -> scrollableState.scrollToItem( position )
                    }
                }
        } else
            SmartMessage(
                // TODO Add this string to strings.xml
                message = "No song is playing at the moment",
                context = appContext(),
                type = PopupType.Warning
            )
    }
}