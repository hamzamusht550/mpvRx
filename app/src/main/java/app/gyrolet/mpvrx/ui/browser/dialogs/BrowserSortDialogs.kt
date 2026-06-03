package app.gyrolet.mpvrx.ui.browser.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.FolderSortType
import app.gyrolet.mpvrx.preferences.FolderViewMode
import app.gyrolet.mpvrx.preferences.MediaLayoutMode
import app.gyrolet.mpvrx.preferences.SortOrder
import app.gyrolet.mpvrx.preferences.VideoSortType
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.icons.Icons
import org.koin.compose.koinInject

@Composable
fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showFolderThumbnails by browserPreferences.showFolderThumbnails.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val centerGridTitles by browserPreferences.centerGridTitles.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Grid Columns (${if (isLandscape) "Landscape" else "Portrait"})",
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Video Grid Columns (${if (isLandscape) "Landscape" else "Portrait"})",
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val isAlbumView = folderViewMode == FolderViewMode.AlbumView

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = if (isAlbumView) "Sort & View Options" else "View Options",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries
        .find { it.displayName == typeName }
        ?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
    showSortOptions = isAlbumView,
    viewModeSelector = MultiViewModeSelector(
      label = "View Mode",
      options = listOf(
        ViewModeOption(
          label = "Folder",
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = "Tree",
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = "Library",
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = "Layout",
      firstOptionLabel = "List",
      secondOptionLabel = "Grid",
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    visibilityToggles = buildList {
      add(
        VisibilityToggle(
          label = "Full Name",
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = "Path",
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = "Total Videos",
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = "Total Duration",
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = "Folder Size",
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        )
      )
      add(
        VisibilityToggle(
          label = "Date",
          checked = showDateChip,
          onCheckedChange = { browserPreferences.showDateChip.set(it) },
        )
      )
      if (mediaLayoutMode == MediaLayoutMode.GRID) {
        add(
          VisibilityToggle(
            label = "Folder Thumbnails",
            checked = showFolderThumbnails,
            onCheckedChange = { browserPreferences.showFolderThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Center Titles",
            checked = centerGridTitles,
            onCheckedChange = { browserPreferences.centerGridTitles.set(it) },
          )
        )
      }
    },
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun VideoSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: VideoSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (VideoSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val videoGridColumnsPortrait by browserPreferences.videoGridColumnsPortrait.collectAsState()
  val videoGridColumnsLandscape by browserPreferences.videoGridColumnsLandscape.collectAsState()
  val folderGridColumnsPortrait by browserPreferences.folderGridColumnsPortrait.collectAsState()
  val folderGridColumnsLandscape by browserPreferences.folderGridColumnsLandscape.collectAsState()

  val configuration = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  val videoGridColumns = if (isLandscape) videoGridColumnsLandscape else videoGridColumnsPortrait
  val folderGridColumns = if (isLandscape) folderGridColumnsLandscape else folderGridColumnsPortrait
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()
  val mediaLayoutMode by browserPreferences.mediaLayoutMode.collectAsState()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val centerGridTitles by browserPreferences.centerGridTitles.collectAsState()

  val folderGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Folder Grid Columns (${if (isLandscape) "Landscape" else "Portrait"})",
      currentValue = folderGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.folderGridColumnsLandscape.set(it)
        else browserPreferences.folderGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 2f..4f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  val videoGridColumnSelector = if (mediaLayoutMode == MediaLayoutMode.GRID) {
    GridColumnSelector(
      label = "Grid Columns (${if (isLandscape) "Landscape" else "Portrait"})",
      currentValue = videoGridColumns,
      onValueChange = {
        if (isLandscape) browserPreferences.videoGridColumnsLandscape.set(it)
        else browserPreferences.videoGridColumnsPortrait.set(it)
      },
      valueRange = if (isLandscape) 3f..5f else 1f..3f,
      steps = if (isLandscape) 1 else 1,
    )
  } else null

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort & View Options",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      VideoSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types =
      listOf(
        VideoSortType.Title.displayName,
        VideoSortType.Duration.displayName,
        VideoSortType.Date.displayName,
        VideoSortType.Size.displayName,
      ),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.AccessTime,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    getLabelForType = { type, _ ->
      when (type) {
        VideoSortType.Title.displayName -> Pair("A-Z", "Z-A")
        VideoSortType.Duration.displayName -> Pair("Shortest", "Longest")
        VideoSortType.Date.displayName -> Pair("Oldest", "Newest")
        VideoSortType.Size.displayName -> Pair("Smallest", "Biggest")
        else -> Pair("Asc", "Desc")
      }
    },
    viewModeSelector = MultiViewModeSelector(
      label = "View Mode",
      options = listOf(
        ViewModeOption(
          label = "Folder",
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) },
        ),
        ViewModeOption(
          label = "Tree",
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) },
        ),
        ViewModeOption(
          label = "Library",
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) },
        ),
      ),
    ),
    layoutModeSelector = ViewModeSelector(
      label = "Layout",
      firstOptionLabel = "List",
      secondOptionLabel = "Grid",
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = mediaLayoutMode == MediaLayoutMode.LIST,
      onViewModeChange = { isFirstOption ->
        browserPreferences.mediaLayoutMode.set(
          if (isFirstOption) MediaLayoutMode.LIST else MediaLayoutMode.GRID
        )
      },
    ),
    visibilityToggles =
      buildList {
        add(
          VisibilityToggle(
            label = "Thumbnails",
            checked = showThumbnails,
            onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Subtitle Indicator",
            checked = showSubtitleIndicator,
            onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Full Name",
            checked = unlimitedNameLines,
            onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Size",
            checked = showSizeChip,
            onCheckedChange = { browserPreferences.showSizeChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Resolution",
            checked = showResolutionChip,
            onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Framerate",
            checked = showFramerateInResolution,
            onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Date",
            checked = showDateChip,
            onCheckedChange = { browserPreferences.showDateChip.set(it) },
          )
        )
        add(
          VisibilityToggle(
            label = "Progress Bar",
            checked = showProgressBar,
            onCheckedChange = { browserPreferences.showProgressBar.set(it) },
          )
        )
        if (mediaLayoutMode == MediaLayoutMode.GRID && videoGridColumns > 1) {
          add(
            VisibilityToggle(
              label = "Center Titles",
              checked = centerGridTitles,
              onCheckedChange = { browserPreferences.centerGridTitles.set(it) },
            )
          )
        }
      },
    folderGridColumnSelector = folderGridColumnSelector,
    videoGridColumnSelector = videoGridColumnSelector,
  )
}

@Composable
fun FileSystemSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  isAtRoot: Boolean = true,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val folderViewMode by browserPreferences.folderViewMode.collectAsState()
  val folderSortType by browserPreferences.folderSortType.collectAsState()
  val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
  val showVideoThumbnails by browserPreferences.showVideoThumbnails.collectAsState()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val showSizeChip by browserPreferences.showSizeChip.collectAsState()
  val showResolutionChip by browserPreferences.showResolutionChip.collectAsState()
  val showFramerateInResolution by browserPreferences.showFramerateInResolution.collectAsState()
  val showProgressBar by browserPreferences.showProgressBar.collectAsState()
  val showSubtitleIndicator by browserPreferences.showSubtitleIndicator.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort & View Options",
    sortType = folderSortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let {
        browserPreferences.folderSortType.set(it)
      }
    },
    sortOrderAsc = folderSortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      browserPreferences.folderSortOrder.set(
        if (isAsc) SortOrder.Ascending
        else SortOrder.Descending,
      )
    },
    types = listOf(
      FolderSortType.Title.displayName,
      FolderSortType.Date.displayName,
      FolderSortType.Size.displayName,
    ),
    icons = listOf(
      Icons.Filled.Title,
      Icons.Filled.CalendarToday,
      Icons.Filled.SwapVert,
    ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
    showSortOptions = true,
    viewModeSelector = MultiViewModeSelector(
      label = "View Mode",
      options = listOf(
        ViewModeOption(
          label = "Folder",
          icon = Icons.Filled.ViewModule,
          isSelected = folderViewMode == FolderViewMode.AlbumView,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.AlbumView) }
        ),
        ViewModeOption(
          label = "Tree",
          icon = Icons.Filled.AccountTree,
          isSelected = folderViewMode == FolderViewMode.FileManager,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.FileManager) }
        ),
        ViewModeOption(
          label = "Library",
          icon = Icons.Filled.VideoLibrary,
          isSelected = folderViewMode == FolderViewMode.MediaLibrary,
          onClick = { browserPreferences.folderViewMode.set(FolderViewMode.MediaLibrary) }
        ),
      )
    ),
    layoutModeSelector = ViewModeSelector(
      label = "Layout",
      firstOptionLabel = "List",
      secondOptionLabel = "Grid",
      firstOptionIcon = Icons.Filled.ViewList,
      secondOptionIcon = Icons.Filled.GridView,
      isFirstOptionSelected = true, // Always list mode
      onViewModeChange = { /* Disabled - do nothing */ },
    ),
    folderGridColumnSelector = null,
    videoGridColumnSelector = null,
    enableViewModeOptions = isAtRoot,
    enableLayoutModeOptions = false, // Disabled/grayed out
    visibilityToggles = listOf(
      VisibilityToggle(
        label = "Video Thumbnails",
        checked = showVideoThumbnails,
        onCheckedChange = { browserPreferences.showVideoThumbnails.set(it) },
      ),
      VisibilityToggle(
        label = "Full Name",
        checked = unlimitedNameLines,
        onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
      ),
      VisibilityToggle(
        label = "Path",
        checked = showFolderPath,
        onCheckedChange = { browserPreferences.showFolderPath.set(it) },
      ),
      VisibilityToggle(
        label = "Total Videos",
        checked = showTotalVideosChip,
        onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
      ),
      VisibilityToggle(
        label = "Folder Size",
        checked = showTotalSizeChip,
        onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = "Size",
        checked = showSizeChip,
        onCheckedChange = { browserPreferences.showSizeChip.set(it) },
      ),
      VisibilityToggle(
        label = "Resolution",
        checked = showResolutionChip,
        onCheckedChange = { browserPreferences.showResolutionChip.set(it) },
      ),
      VisibilityToggle(
        label = "Framerate",
        checked = showFramerateInResolution,
        onCheckedChange = { browserPreferences.showFramerateInResolution.set(it) },
      ),
      VisibilityToggle(
        label = "Subtitle",
        checked = showSubtitleIndicator,
        onCheckedChange = { browserPreferences.showSubtitleIndicator.set(it) },
      ),
      VisibilityToggle(
        label = "Progress Bar",
        checked = showProgressBar,
        onCheckedChange = { browserPreferences.showProgressBar.set(it) },
      ),
    )
  )
}
