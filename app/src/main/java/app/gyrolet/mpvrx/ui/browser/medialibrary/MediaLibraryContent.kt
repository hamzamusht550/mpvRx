package app.gyrolet.mpvrx.ui.browser.medialibrary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.browser.LocalNavigationBarHeight
import app.gyrolet.mpvrx.ui.browser.NavigationBarState
import app.gyrolet.mpvrx.ui.browser.components.BrowserBottomBar
import app.gyrolet.mpvrx.ui.browser.components.BrowserTopBar
import app.gyrolet.mpvrx.ui.browser.dialogs.DeleteConfirmationDialog
import app.gyrolet.mpvrx.ui.browser.dialogs.RenameDialog
import app.gyrolet.mpvrx.ui.browser.selection.rememberSelectionManager
import app.gyrolet.mpvrx.ui.browser.videolist.VideoListContent
import app.gyrolet.mpvrx.ui.browser.videolist.VideoSortDialog
import app.gyrolet.mpvrx.ui.browser.videolist.VideoWithPlaybackInfo
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
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
  val sortedVideosWithInfo = remember(videosWithPlaybackInfo, videoSortType, videoSortOrder) {
    val infoById = videosWithPlaybackInfo.associateBy { it.video.path }
    val sortedVideos = SortUtils.sortVideos(videosWithPlaybackInfo.map { it.video }, videoSortType, videoSortOrder)
    sortedVideos.map { video ->
      infoById[video.path] ?: VideoWithPlaybackInfo(video)
    }
  }

  val selectionManager = rememberSelectionManager(
    items = sortedVideosWithInfo.map { it.video },
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
  val isFabVisible = remember { mutableStateOf(true) }
  var showFloatingBottomBar by remember { mutableStateOf(false) }
  val animationDuration = 300

  LaunchedEffect(selectionManager.isInSelectionMode) {
    showFloatingBottomBar = selectionManager.isInSelectionMode
    NavigationBarState.updateSelectionState(
      inSelectionMode = selectionManager.isInSelectionMode,
      onlyVideos = true,
    )
  }

  BackHandler(enabled = selectionManager.isInSelectionMode) {
    selectionManager.clear()
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
      BrowserTopBar(
        title = "Media Library",
        isInSelectionMode = selectionManager.isInSelectionMode,
        selectedCount = selectionManager.selectedCount,
        totalCount = sortedVideosWithInfo.size,
        onBackClick = null,
        onCancelSelection = { selectionManager.clear() },
        onSortClick = { sortDialogOpen.value = true },
        onSettingsClick = {
          backstack.add(app.gyrolet.mpvrx.ui.preferences.PreferencesScreen)
        },
        isSingleSelection = selectionManager.isSingleSelection,
        onInfoClick = {
          if (selectionManager.isSingleSelection) {
            val video = selectionManager.getSelectedItems().firstOrNull()
            if (video != null) {
              MediaUtils.playFile(video.path, context, "media_library_info")
              selectionManager.clear()
            }
          }
        },
        onShareClick = { selectionManager.shareSelected() },
        onPlayClick = { selectionManager.playSelected() },
        onSelectAll = { selectionManager.selectAll() },
        onInvertSelection = { selectionManager.invertSelection() },
        onDeselectAll = { selectionManager.clear() },
      )
    },
    floatingActionButton = {
      if (sortedVideosWithInfo.isNotEmpty()) {
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

                if (lastPlayed != null && sortedVideosWithInfo.any { it.video.path == lastPlayed.filePath }) {
                  MediaUtils.playFile(lastPlayed.filePath, context, "recently_played_button")
                } else {
                  MediaUtils.playFile(sortedVideosWithInfo.first().video, context, "first_video_button")
                }
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
      VideoListContent(
        folderId = "media_library",
        videosWithInfo = sortedVideosWithInfo,
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
            MediaUtils.playFile(video, context, "video_list")
          }
        },
        onVideoLongClick = { video -> selectionManager.handleLongClick(video) },
        isFabVisible = isFabVisible,
        modifier = Modifier.padding(padding),
        showFloatingBottomBar = showFloatingBottomBar,
      )

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
          onAddToPlaylistClick = { /* N/A */ },
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
  }
}
