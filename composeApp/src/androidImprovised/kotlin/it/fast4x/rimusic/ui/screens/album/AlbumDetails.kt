package it.fast4x.rimusic.ui.screens.album

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.EXPLICIT_PREFIX
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.MODIFIED_PREFIX
import it.fast4x.rimusic.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.SongEntity
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.ItemsList
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.align
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.durationTextToMillis
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.formatAsTime
import it.fast4x.rimusic.utils.getHttpClient
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.languageDestination
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import me.knighthat.component.SongItem
import me.knighthat.component.tab.DeleteAllDownloadedSongsDialog
import me.knighthat.component.tab.DownloadAllSongsDialog
import me.knighthat.component.tab.ItemSelector
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.component.ui.screens.album.AlbumBookmark
import me.knighthat.component.ui.screens.album.AlbumModifier
import me.knighthat.component.ui.screens.album.Translate
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun AlbumDetails(
    navController: NavController,
    browseId: String,
    album: Album?,
    thumbnailPainter: AsyncImagePainter,
    alternatives: List<Innertube.AlbumItem>,
    description: String,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    PersistMapCleanup( "album/${browseId}/songs" )

    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    // Settings
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var items by persistList<SongEntity>( "album/${browseId}/songs" )
    LaunchedEffect( album ) {
        // [album] goes from null to not-null after it's inserted to the database
        if( album == null ) return@LaunchedEffect

        Database.findSongsOfAlbum( browseId )
                .flowOn( Dispatchers.IO )
                .distinctUntilChanged()
                .collect { list ->
                    items = list.filter {
                        !parentalControlEnabled || !it.song.title.contains( EXPLICIT_PREFIX, true )
                    }
                }
    }

    val itemSelector = ItemSelector<SongEntity>()

    fun getMediaItems() = itemSelector.ifEmpty { items }.map( SongEntity::asMediaItem )

    val bookmark = AlbumBookmark( browseId )
    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog { getMediaItems().map( MediaItem::asSong ) }
    val downloadALlDialog = DownloadAllSongsDialog { getMediaItems().map( MediaItem::asSong ) }
    val shuffle = SongShuffler {
        getMediaItems().map( MediaItem::asSong )
    }
    val radio = Radio.init( ::getMediaItems )
    val locator = Locator( lazyListState ) { getMediaItems().map( MediaItem::asSong ) }
    val playNext = PlayNext {
        getMediaItems().let {
            binder?.player?.addNext( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }
    val enqueue = Enqueue {
        getMediaItems().let {
            binder?.player?.enqueue( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }
    val addToPlaylist = PlaylistsMenu.init(
        navController,
        { getMediaItems() },
        { throwable, preview ->
            Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on HomeSongs" )
            throwable.printStackTrace()
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )
    //<editor-fold defaultstate="collapsed" desc="Album modifiers">
    val changeTitle = AlbumModifier.init(
        { updateAlbumTitle(browseId, "$MODIFIED_PREFIX$it") },
        R.drawable.title_edit,
        R.string.update_title,
        album?.title.toString(),
        stringResource( R.string.title )
    )
    val changeAuthors = AlbumModifier.init(
        { updateAlbumAuthors(browseId, "$MODIFIED_PREFIX$it") },
        R.drawable.artists_edit,
        R.string.update_authors,
        album?.authorsText.toString(),
        stringResource( R.string.authors )
    )
    val changeCover = AlbumModifier.init(
        { updateAlbumCover(browseId, "$MODIFIED_PREFIX$it") },
        R.drawable.cover_edit,
        R.string.update_cover,
        album?.thumbnailUrl.toString(),
        stringResource( R.string.cover )
    )
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="Translator">
    val translate = Translate.init()
    val translator = Translator(getHttpClient())
    val languageDestination = languageDestination()
    //</editor-fold>

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailAlbumSizeDp = Dimensions.thumbnails.album

    val thumbnailAlbumSizePx = thumbnailAlbumSizeDp.px

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    downloadALlDialog.Render()
    deleteAllDownloadsDialog.Render()
    changeTitle.Render()
    changeAuthors.Render()
    changeCover.Render()

    DynamicOrientationLayout( thumbnailPainter ) {
        Box(
            Modifier.fillMaxSize()
                .background( colorPalette().background0 )
        ) {
            LazyColumn(
                state = lazyListState,
                userScrollEnabled = items.isNotEmpty(),
                contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
                modifier = Modifier.fillMaxSize()
                    .background( colorPalette().background0 )
            ) {
                item( "header" ) {
                    Box( Modifier.fillMaxWidth() ) {
                        if (!isLandscape)
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.aspectRatio( 4f / 3 )      // Limit height
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                        bottom = Dimensions.fadeSpacingBottom
                                    )
                            )

                        AutoResizeText(
                            text = cleanPrefix( album?.title ?: "..." ),
                            style = typography().l.semiBold,
                            fontSizeRange = FontSizeRange(32.sp, 38.sp),
                            fontWeight = typography().l.semiBold.fontWeight,
                            fontFamily = typography().l.semiBold.fontFamily,
                            color = typography().l.semiBold.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align( Alignment.BottomCenter )
                                .padding( horizontal = 30.dp )
                                .conditional( !disableScrollingText ) {
                                    basicMarquee(iterations = Int.MAX_VALUE)
                                }
                        )

                        HeaderIconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette().text,
                            iconSize = 24.dp,
                            modifier = Modifier.align( Alignment.TopEnd )
                                .padding( top = 5.dp, end = 5.dp ),
                            onClick = {
                                album?.shareUrl?.let { url ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }

                                    context.startActivity(
                                        Intent.createChooser( sendIntent, null )
                                    )
                                }
                            }
                        )
                    }
                }

                item( "album_details" ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val year = album?.year ?: "0000"
                        val songCount = "${items.size} ${stringResource( R.string.songs )}"
                        val totalDuration = items.mapNotNull{ it.song.durationText }
                            .map( ::durationTextToMillis )
                            .sum()
                            .let( ::formatAsTime )

                        BasicText(
                            text = "$year - $songCount - $totalDuration",
                            style = typography().xs.medium,
                            maxLines = 1
                        )
                    }
                }

                item( "action_buttons" ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Place this button alone so we can space it further from other buttons
                        bookmark.ToolBarButton()

                        Spacer( Modifier.width(15.dp) )

                        TabToolBar.Buttons(
                            downloadALlDialog,
                            deleteAllDownloadsDialog,
                            shuffle,
                            radio,
                            locator,
                            itemSelector,
                            changeTitle,
                            changeAuthors,
                            changeCover,
                            playNext,
                            enqueue,
                            addToPlaylist,
                            modifier = Modifier.fillMaxWidth( .8f )
                        )
                    }
                }

                item( "songsTitle" ) {
                    BasicText(
                        text = stringResource(R.string.songs),
                        style = typography().m.semiBold.align(TextAlign.Start),
                        modifier = sectionTextModifier.fillMaxWidth()
                    )
                }
                // Placeholders while songs are loading
                if( items.isEmpty() )
                    items(
                        count = 10,
                        key = { index -> "song_placeholders_no$index" }
                    ) { SongItemPlaceholder() }
                itemsIndexed(
                    items = items,
                    key = { _, song -> song.song.id }
                ) { index, song ->

                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        }
                    ) {
                        var forceRecompose by remember { mutableStateOf(false) }

                        SongItem(
                            song = song.song,
                            navController = navController,
                            showThumbnail = false,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                onDismiss = {
                                                    menuState.hide()
                                                    forceRecompose = true
                                                },
                                                mediaItem = song.asMediaItem,
                                                disableScrollingText = disableScrollingText
                                            )
                                        }
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            items.map( SongEntity::asMediaItem ),
                                            index
                                        )

                                        /*
                                            Due to the small size of checkboxes,
                                            we shouldn't disable [itemSelector]
                                         */
                                    }
                                ),
                            trailingContent = {
                                // It must watch for [selectedItems.size] for changes
                                // Otherwise, state will stay the same
                                val checkedState = remember( itemSelector.size ) {
                                    mutableStateOf( song in itemSelector )
                                }

                                if( itemSelector.isActive )
                                    androidx.compose.material3.Checkbox(
                                        checked = checkedState.value,
                                        onCheckedChange = {
                                            checkedState.value = it
                                            if (it)
                                                itemSelector.add(song)
                                            else
                                                itemSelector.remove(song)
                                        },
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = colorPalette().accent,
                                            uncheckedColor = colorPalette().text
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                            },
                            thumbnailOverlay = {
                                BasicText(
                                    text = "${index + 1}",
                                    style = typography().s
                                        .semiBold
                                        .center
                                        .color(
                                            colorPalette()
                                                .textDisabled
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(thumbnailSizeDp)
                                        .align(Alignment.Center)
                                )
                            }
                        )
                    }
                }

                if( alternatives.isNotEmpty() )
                    item( "alternatives" ) {
                        // Section text
                        BasicText(
                            text = stringResource( R.string.album_alternative_versions ),
                            style = typography().m.semiBold,
                            maxLines = 1,
                            modifier = Modifier.padding( all = 16.dp )
                        )

                        // List all alternatives
                        ItemsList(
                            tag = "album/$browseId/alternatives",
                            headerContent = {},
                            initialPlaceholderCount = 1,
                            continuationPlaceholderCount = 1,
                            emptyItemsText = stringResource( R.string.album_no_alternative_version ),
                            itemsPageProvider = {
                                Result.success(
                                    Innertube.ItemsPage( alternatives, null )
                                )
                            },
                            itemContent = { album ->
                                AlbumItem(
                                    alternative = true,
                                    album = album,
                                    thumbnailSizePx = thumbnailAlbumSizePx,
                                    thumbnailSizeDp = thumbnailAlbumSizeDp,
                                    modifier = Modifier
                                        .clickable {
                                            navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                        },
                                    disableScrollingText = disableScrollingText
                                )
                            },
                            itemPlaceholderContent = {
                                AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                            }
                        )
                    }

                if( description.isNotBlank() )
                    item( "description" ) {
                        val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")

                        BasicText(
                            text = stringResource(R.string.information),
                            style = typography().m.semiBold.align(TextAlign.Start),
                            modifier = sectionTextModifier
                                .fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.padding(
                                vertical = 16.dp,
                                horizontal = 8.dp
                            )
                        ) {
                            translate.ToolBarButton()

                            BasicText(
                                text = "“",
                                style = typography().xxl.semiBold,
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .align(Alignment.Top)
                            )

                            var translatedText by remember { mutableStateOf("") }
                            val nonTranslatedText by remember {
                                mutableStateOf(
                                    if (attributionsIndex == -1) {
                                        description
                                    } else {
                                        description.substring(0, attributionsIndex)
                                    }
                                )
                            }

                            if ( translate.isActive ) {
                                LaunchedEffect(Unit) {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            translator.translate(
                                                nonTranslatedText,
                                                languageDestination,
                                                Language.AUTO
                                            ).translatedText
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    translatedText =
                                        if (result.toString() == "kotlin.Unit") "" else result.toString()
                                }
                            } else translatedText = nonTranslatedText

                            BasicText(
                                text = translatedText,
                                style = typography().xxs.secondary.align(TextAlign.Justify),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .weight(1f)
                            )

                            BasicText(
                                text = "„",
                                style = typography().xxl.semiBold,
                                modifier = Modifier
                                    .offset(y = 4.dp)
                                    .align(Alignment.Bottom)
                            )
                        }

                        if (attributionsIndex != -1) {
                            BasicText(
                                text = stringResource(R.string.from_wikipedia_cca),
                                style = typography().xxs
                                    .color( colorPalette().textDisabled )
                                    .align( TextAlign.Start ),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                            )
                        }
                    }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if ( UiType.ViMusic.isCurrent() && showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.shuffle,
                    onClick = shuffle::onShortClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}
