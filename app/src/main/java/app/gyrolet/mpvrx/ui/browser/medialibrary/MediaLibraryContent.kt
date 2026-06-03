package app.gyrolet.mpvrx.ui.browser.medialibrary

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gyrolet.mpvrx.BuildConfig
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.browser.LocalNavigationBarHeight
import app.gyrolet.mpvrx.ui.browser.NavigationBarState
import app.gyrolet.mpvrx.ui.browser.components.BrowserBottomBar
import app.gyrolet.mpvrx.ui.browser.components.BrowserTopBar
import app.gyrolet.mpvrx.ui.browser.dialogs.AddToPlaylistDialog
import app.gyrolet.mpvrx.ui.browser.dialogs.DeleteConfirmationDialog
import app.gyrolet.mpvrx.ui.browser.dialogs.RenameDialog
import app.gyrolet.mpvrx.ui.browser.playlist.ALL_VIDEOS_PLAYLIST_ID
import app.gyrolet.mpvrx.ui.browser.selection.rememberSelectionManager
import app.gyrolet.mpvrx.ui.browser.states.EmptyState
import app.gyrolet.mpvrx.ui.browser.videolist.VideoListContent
import app.gyrolet.mpvrx.ui.browser.videolist.VideoSortDialog
import app.gyrolet.mpvrx.ui.browser.videolist.VideoWithPlaybackInfo
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.utils.clipboard.SafeClipboard
import app.gyrolet.mpvrx.utils.history.RecentlyPlayedOps
import app.gyrolet.mpvrx.utils.media.MediaUtils
import app.gyrolet.mpvrx.utils.sort.SortUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaLibraryContent() {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val backstack = LocalBackStack.current
  val browserPreferences = koinInject<BrowserPreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  val navigationBarHeight = LocalNavigationBarHeight.current

  val viewModel: MediaLibraryViewModel = viewModel(
    factory = MediaLibraryViewModel.factory(context.applicationContext as android.app.Application)
  )
  val videos by viewModel.videos.collectAsState()
  val videosWithPlaybackInfo by viewModel.videosWithPlaybackInfo.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val recentlyPlayedFilePath by viewModel.recentlyPlayedFilePath.collectAsState()

  val videoSortType by browserPreferences.videoSortType.collectAsState()
  val videoSortOrder by browserPreferences.videoSortOrder.collectAsState()
  val playlistMode by playerPreferences.playlistMode.collectAsState()
  val sortedVideosWithInfo = remember(videosWithPlaybackInfo, videoSortType, videoSortOrder) {
    val infoById = videosWithPlaybackInfo.associateBy { it.video.path }
    val sortedVideos = SortUtils.sortVideos(videosWithPlaybackInfo.map { it.video }, videoSortType, videoSortOrder)
    sortedVideos.map { video ->
      infoById[video.path] ?: VideoWithPlaybackInfo(video)
    }
  }

  var searchQuery by rememberSaveable { mutableStateOf("") }
  var isSearching by rememberSaveable { mutableStateOf(false) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  val filteredVideosWithInfo = remember(sortedVideosWithInfo, isSearching, searchQuery) {
    if (isSearching && searchQuery.isNotBlank()) {
      sortedVideosWithInfo.filter { item ->
        item.video.displayName.contains(searchQuery, ignoreCase = true) ||
          item.video.path.contains(searchQuery, ignoreCase = true)
      }
    } else {
      sortedVideosWithInfo
    }
  }

  val selectionManager = rememberSelectionManager(
    items = filteredVideosWithInfo.map { it.video },
    getId = { it.path.hashCode().toLong() },
    onDeleteItems = { items, _ ->
      coroutineScope.launch { viewModel.deleteVideos(items) }
      Pair(items.size, 0)
    },
    onRenameItem = { video, newName ->
      coroutineScope.launch { viewModel.renameVideo(video, newName) }
      Result.success(Unit)
    },
    onOperationComplete = { viewModel.refresh() },
  )

  val isRefreshing = remember { mutableStateOf(false) }
  val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
  val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
  val renameDialogOpen = rememberSaveable { mutableStateOf(false) }
  val addToPlaylistDialogOpen = rememberSaveable { mutableStateOf(false) }
  val isFabVisible = remember { mutableStateOf(true) }
  var showFloatingBottomBar by remember { mutableStateOf(false) }
  val animationDuration = 300
  val lastPlayRequestIndex = remember { mutableIntStateOf(-1) }

  LaunchedEffect(isSearching) {
    if (isSearching) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  LaunchedEffect(selectionManager.isInSelectionMode) {
    showFloatingBottomBar = selectionManager.isInSelectionMode
    NavigationBarState.updateSelectionState(
      inSelectionMode = selectionManager.isInSelectionMode,
      onlyVideos = true,
    )
  }

  fun playFromMediaLibrary(video: Video) {
    if (!playlistMode || sortedVideosWithInfo.size <= 1) {
      MediaUtils.playFile(video, context, "media_library")
      return
    }

    lastPlayRequestIndex.intValue =
      sortedVideosWithInfo.indexOfFirst { it.video.path == video.path }

    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, video.uri).apply {
      setClass(context, PlayerActivity::class.java)
      putExtra("internal_launch", true)
      putExtra("playlist_id", ALL_VIDEOS_PLAYLIST_ID)
      putExtra("playlist_index", lastPlayRequestIndex.intValue.coerceAtLeast(0))
      putExtra("launch_source", "media_library")
      putExtra("title", video.displayName)
    }
    context.startActivity(intent)
  }

  BackHandler(enabled = selectionManager.isInSelectionMode || isSearching) {
    when {
      selectionManager.isInSelectionMode -> selectionManager.clear()
      isSearching -> {
        isSearching = false
        searchQuery = ""
      }
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Scaffold(
    topBar = {
      if (isSearching) {
        SearchBar(
          inputField = {
            SearchBarDefaults.InputField(
              query = searchQuery,
              onQueryChange = { searchQuery = it },
              onSearch = { },
              expanded = false,
              onExpandedChange = { },
              placeholder = { Text("Search videos...") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Search,
                  contentDescription = "Search",
                )
              },
              trailingIcon = {
                IconButton(
                  onClick = {
                    isSearching = false
                    searchQuery = ""
                  },
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                  )
                }
              },
              modifier = Modifier.focusRequester(focusRequester),
            )
          },
          expanded = false,
          onExpandedChange = { },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        ) { }
      } else {
        BrowserTopBar(
          title = "Media Library",
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = filteredVideosWithInfo.size,
          onBackClick = null,
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = { isSearching = true },
          onSettingsClick = {
            backstack.add(app.gyrolet.mpvrx.ui.preferences.PreferencesScreen)
          },
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = {
            if (selectionManager.isSingleSelection) {
              val video = selectionManager.getSelectedItems().firstOrNull()
              if (video != null) {
                val intent = Intent(context, app.gyrolet.mpvrx.ui.mediainfo.MediaInfoActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.data = video.uri
                context.startActivity(intent)
                selectionManager.clear()
              }
            }
          },
          onShareClick = { selectionManager.shareSelected() },
          onCopyClick = {
            val selectedPaths = selectionManager.getSelectedItems().map { it.path }.distinct()
            if (selectedPaths.isNotEmpty()) {
              SafeClipboard.copyPlainText(context, "Selected paths", selectedPaths.joinToString("\n"))
            }
          },
          onPlayClick = { selectionManager.playSelected() },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
          onAddToPlaylistClick =
            if (!BuildConfig.ENABLE_UPDATE_FEATURE) {
              { addToPlaylistDialogOpen.value = true }
            } else {
              null
            },
        )
      }
    },
    floatingActionButton = {
      if (filteredVideosWithInfo.isNotEmpty()) {
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
          tooltip = { PlainTooltip { Text("Play recently played or first video") } },
          state = rememberTooltipState(),
        ) {
          FloatingActionButton(
            modifier = Modifier
              .windowInsetsPadding(WindowInsets.systemBars)
              .padding(bottom = navigationBarHeight)
              .animateFloatingActionButton(
                visible = !selectionManager.isInSelectionMode && isFabVisible.value,
                alignment = Alignment.BottomEnd,
              ),
            onClick = {
              coroutineScope.launch {
                val recentlyPlayedVideos = RecentlyPlayedOps.getRecentlyPlayed(limit = 1)
                val lastPlayed = recentlyPlayedVideos.firstOrNull()
                val targetVideo =
                  if (lastPlayed != null) {
                    filteredVideosWithInfo.firstOrNull { it.video.path == lastPlayed.filePath }?.video
                  } else {
                    null
                  }

                playFromMediaLibrary(targetVideo ?: filteredVideosWithInfo.first().video)
              }
            },
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play recently played or first video")
          }
        }
      }
    }
  ) { padding ->
    val autoScrollToLastPlayed by browserPreferences.autoScrollToLastPlayed.collectAsState()
    val videosWereDeletedOrMoved = false

    Box(modifier = Modifier.fillMaxSize()) {
      if (isSearching && filteredVideosWithInfo.isEmpty() && searchQuery.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          EmptyState(
            icon = Icons.Filled.Search,
            title = "No videos found",
            message = "Try a different search term",
          )
        }
      } else {
        VideoListContent(
          folderId = "media_library",
          videosWithInfo = filteredVideosWithInfo,
          isLoading = isLoading && videos.isEmpty(),
          isRefreshing = isRefreshing,
          recentlyPlayedFilePath = recentlyPlayedFilePath,
          videosWereDeletedOrMoved = videosWereDeletedOrMoved,
          autoScrollToLastPlayed = autoScrollToLastPlayed,
          onRefresh = { viewModel.refresh() },
          selectionManager = selectionManager,
          onVideoClick = { video ->
            if (selectionManager.isInSelectionMode) {
              selectionManager.toggle(video)
            } else {
              playFromMediaLibrary(video)
            }
          },
          onVideoLongClick = { video -> selectionManager.handleLongClick(video) },
          isFabVisible = isFabVisible,
          modifier = Modifier.padding(padding),
          showFloatingBottomBar = showFloatingBottomBar,
        )
      }

      AnimatedVisibility(
        visible = showFloatingBottomBar,
        enter = slideInVertically(
          animationSpec = tween(durationMillis = animationDuration),
          initialOffsetY = { fullHeight -> fullHeight }
        ),
        exit = slideOutVertically(
          animationSpec = tween(durationMillis = animationDuration),
          targetOffsetY = { fullHeight -> fullHeight }
        ),
        modifier = Modifier.align(Alignment.BottomCenter)
      ) {
        BrowserBottomBar(
          isSelectionMode = true,
          onCopyClick = { /* N/A */ },
          onMoveClick = { /* N/A */ },
          onRenameClick = { renameDialogOpen.value = true },
          onDeleteClick = { deleteDialogOpen.value = true },
          onAddToPlaylistClick = { addToPlaylistDialogOpen.value = true },
          showCopy = false,
          showMove = false,
          modifier = Modifier.padding(bottom = if (NavigationBarState.shouldHideNavigationBar) 0.dp else navigationBarHeight)
        )
      }
    }

    if (sortDialogOpen.value) {
      VideoSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = videoSortType,
        sortOrder = videoSortOrder,
        onSortTypeChange = { browserPreferences.videoSortType.set(it) },
        onSortOrderChange = { browserPreferences.videoSortOrder.set(it) }
      )
    }

    if (deleteDialogOpen.value) {
      DeleteConfirmationDialog(
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = {
          selectionManager.deleteSelected()
          deleteDialogOpen.value = false
        },
        itemCount = selectionManager.selectedCount,
        isOpen = deleteDialogOpen.value,
        itemType = "video"
      )
    }

    if (renameDialogOpen.value) {
      val video = selectionManager.getSelectedItems().firstOrNull()
      if (video != null) {
        RenameDialog(
          onDismiss = { renameDialogOpen.value = false },
          onConfirm = { newName ->
            selectionManager.renameSelected(newName)
            renameDialogOpen.value = false
          },
          currentName = video.displayName,
          isOpen = renameDialogOpen.value,
          itemType = "video"
        )
      }
    }

    AddToPlaylistDialog(
      isOpen = addToPlaylistDialogOpen.value,
      videos = selectionManager.getSelectedItems(),
      onDismiss = { addToPlaylistDialogOpen.value = false },
      onSuccess = {
        selectionManager.clear()
        viewModel.refresh()
      },
    )
  }
}
