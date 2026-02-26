package com.project.pooket.ui.reader

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.filled.AirlineStops
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.ui.common.NightLightDropDownMenu
import com.project.pooket.ui.reader.composable.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val NoJumpSpec = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        0f
}

//screen-locking (disable horizontal drag)
private fun clampOffset(proposedOffset: Offset, scale: Float, size: Size): Offset {
    val maxX = (size.width * scale - size.width) / 2f
    val maxY = (size.height * scale - size.height) / 2f
    return Offset(
        proposedOffset.x.coerceIn(-maxX, maxX),
        proposedOffset.y.coerceIn(-maxY, maxY)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookTitle: String,
    bookUri: String,
    viewModel: ReaderViewModel = hiltViewModel(),
    isNightMode: Boolean,
    onBack: () -> Unit,
) {
    //services
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    //viewmodel-state
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalPages by viewModel.pageCount.collectAsStateWithLifecycle()
    val isVerticalMode by viewModel.isVerticalScrollMode.collectAsStateWithLifecycle()
    val isEpub by viewModel.isEpub.collectAsStateWithLifecycle()
    val isTextModeState by viewModel.isTextMode.collectAsStateWithLifecycle()
    val isTextMode = isEpub || isTextModeState

    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val selectedText by viewModel.currentSelectionText.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    val notesByPage = remember(notes) { notes.groupBy { it.pageIndex } }

    //ui-state
    var masterPage by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSwitchingModes by remember { mutableStateOf(false) }

    var isViewportLocked by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showNotesSheet by rememberSaveable { mutableStateOf(false) }
    var showNoteDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectPageDialog by rememberSaveable { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var bookDarkMode by rememberSaveable { mutableStateOf(isNightMode) }

    //scroll/pager-state
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val listState = rememberLazyListState()

    //zoom-state
    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    //init-load
    LaunchedEffect(bookUri) { viewModel.loadBook(bookUri) }
    LaunchedEffect(isLoading, totalPages) {
        if (!isLoading && totalPages > 0 && !isInitialized) {
            masterPage = initialPage
            if (isVerticalMode) listState.scrollToItem(masterPage)
            else pagerState.scrollToPage(masterPage)
            isInitialized = true
        }
    }

    //view-switching
    LaunchedEffect(isVerticalMode, isTextMode) {
        if (isInitialized) {
            isSwitchingModes = true
            if (isVerticalMode) listState.scrollToItem(masterPage)
            else pagerState.scrollToPage(masterPage)
            delay(100)
            isSwitchingModes = false
        }
    }
    val activeUiPage = remember(isVerticalMode) {
        derivedStateOf {
            if (isVerticalMode) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isEmpty()) return@derivedStateOf masterPage

                val viewportCenter =
                    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2

                val centerItem = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + (item.size / 2)
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                if (!listState.canScrollForward) {
                    return@derivedStateOf (layoutInfo.totalItemsCount - 1)
                }

                return@derivedStateOf centerItem?.index
                    ?: masterPage // changed from 0 to avoid jumping to start
            } else {
                pagerState.currentPage
            }
        }
    }
    LaunchedEffect(activeUiPage.value) {
        if (isInitialized && !isSwitchingModes) {
            if (masterPage != activeUiPage.value) {
                masterPage = activeUiPage.value
                viewModel.onPageChanged(masterPage)
            }
        }
    }

    //switch page
    val onNextPageAction: () -> Unit = {
        scope.launch {
            val target = (masterPage + 1).coerceAtMost(totalPages - 1)
            if (isVerticalMode) listState.animateScrollToItem(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    val onPrevPageAction: () -> Unit = {
        scope.launch {
            val target = (masterPage - 1).coerceAtLeast(0)
            if (isVerticalMode) listState.animateScrollToItem(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    Scaffold(
        containerColor = if (bookDarkMode) Color.Black else Color.White,
        topBar = {
            ReaderTopBar(
                visible = showControls && selectedText == null,
                dropdownExpanded = dropdownExpanded,
                bookDarkMode = bookDarkMode,
                title = bookTitle,
                onBack = onBack,
                onShowNotes = { showNotesSheet = true },
                onGoToPage = {
                    showSelectPageDialog = true
                    dropdownExpanded = false},
                onHideMenu = { showControls = false },
                onHideDropdown = {dropdownExpanded = !dropdownExpanded},
                onToggleBookDarkMode = {bookDarkMode = it})

            AnimatedVisibility(
                visible = selectedText != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                SelectionControlBar(
                    onCopy = {
                        selectedText?.let { clipboardManager.setText(AnnotatedString(it)) }
                        viewModel.clearAllSelection()
                    },
                    onNote = { showNoteDialog = true },
                    onClose = { viewModel.clearAllSelection() }
                )
            }
        },
        bottomBar = {
            FontSizeControl(
                visible = showControls && isTextMode,
                fontSize = fontSize,
                onFontSizeChange = { size -> viewModel.setFontSize(size, masterPage) }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f)
                ) { PageIndicator(
                    currentPage = masterPage + 1,
                    totalPages = totalPages,
                    modifier = Modifier
                )}

                ReaderControls(
                    isEpub = isEpub,
                    visible = showControls,
                    isVertical = isVerticalMode,
                    isTextMode = isTextMode,
                    isLocked = isViewportLocked,
                    onToggleLock = { isViewportLocked = !isViewportLocked },
                    onToggleMode = viewModel::toggleReadingMode,
                    onToggleTextMode = viewModel::toggleTextMode,
                    onPrevPage = onPrevPageAction,
                    onNextPage = onNextPageAction
                )
            }

        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides NoJumpSpec) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clipToBounds()
                    .onGloballyPositioned { coordinates ->
                        val size = coordinates.size.toSize()
                        if (size.width > 0) {
                            viewModel.onScreenSizeReady(size, density)
                        }
                    }
            ) {

                if (isLoading || totalPages == 0) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            //tap top to show menu gesture
                            .pointerInput(Unit) {
                                val topZonePx = 80.dp.toPx()
                                val touchSlop = viewConfiguration.touchSlop

                                awaitPointerEventScope {
                                    while (true) {
                                        val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                        val downChange =
                                            downEvent.changes.find { it.changedToDown() }

                                        if (downChange != null && downChange.position.y <= topZonePx) {
                                            val startPos = downChange.position
                                            var isTap = true

                                            do {
                                                val event =
                                                    awaitPointerEvent(PointerEventPass.Initial)
                                                val change =
                                                    event.changes.find { it.id == downChange.id }

                                                if (change == null) {
                                                    isTap = false; break
                                                }

                                                if (change.positionChanged()) {
                                                    val distance =
                                                        (change.position - startPos).getDistance()
                                                    if (distance > touchSlop) {
                                                        isTap = false
                                                    }
                                                }

                                                if (change.changedToUp()) {
                                                    if (isTap && !showControls) {
                                                        showControls = true
                                                    }
                                                    break
                                                }
                                            } while (change.pressed && !change.isConsumed)
                                        }
                                    }
                                }
                            }
                            // tap_gesture
                            .pointerInput(isVerticalMode, isTextMode) {
                                if (isTextMode) {
                                    detectTapGestures(onTap = {
                                        viewModel.clearAllSelection()
                                        // showControls = !showControls
                                    })
                                } else {
                                    detectTapGestures(
                                        onTap = {
                                            viewModel.clearAllSelection()
                                            // showControls = !showControls
                                        },
                                        onDoubleTap = {
                                            if (globalScale > 1f) {
                                                globalScale = 1f
                                                globalOffset = Offset.Zero
                                            } else {
                                                globalScale = 2.5f
                                            }
                                        }
                                    )
                                }
                            }
                            // zoom_gesture
                            .pointerInput(isVerticalMode, isTextMode, isViewportLocked) {
                                if (isTextMode) return@pointerInput
                                awaitEachGesture {
                                    var pan = Offset.Zero
                                    var zoom = 1f
                                    var pastTouchSlop = false
                                    val touchSlop = viewConfiguration.touchSlop

                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val canceled = event.changes.any { it.isConsumed }
                                        if (!canceled) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()

                                            if (!pastTouchSlop) {
                                                zoom *= zoomChange
                                                pan += panChange
                                                val centroidSize =
                                                    event.calculateCentroidSize(useCurrent = false)
                                                val panMotion = pan.getDistance()
                                                if (panMotion > touchSlop ||
                                                    abs(1f - zoom) * centroidSize > touchSlop
                                                ) {
                                                    pastTouchSlop = true
                                                }
                                            }

                                            if (pastTouchSlop) {
                                                val newScale =
                                                    (globalScale * zoomChange).coerceIn(1f, 5f)
                                                val effectivePan = if (isViewportLocked) Offset(
                                                    0f,
                                                    panChange.y
                                                ) else panChange

                                                if (isViewportLocked && event.changes.size == 1) {
                                                    val change = event.changes[0]
                                                    if (abs(panChange.y) > 0.1f) change.consume()
                                                } else {
                                                    event.changes.forEach { it.consume() }
                                                }

                                                val proposedOffset = globalOffset + effectivePan
                                                val clampedOffset = clampOffset(
                                                    proposedOffset,
                                                    newScale,
                                                    size.toSize()
                                                )

                                                globalScale = newScale
                                                globalOffset = clampedOffset

                                                if (isVerticalMode && (proposedOffset.y - clampedOffset.y) != 0f) {
                                                    listState.dispatchRawDelta(-(proposedOffset.y - clampedOffset.y))
                                                }
                                            }
                                        }
                                    } while (!canceled && event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer {
                                scaleX = if (isTextMode) 1f else globalScale
                                scaleY = if (isTextMode) 1f else globalScale
                                translationX = if (isTextMode) 0f else globalOffset.x
                                translationY = if (isTextMode) 0f else globalOffset.y
                            }
                    ) {
                        val pageContent: @Composable (Int) -> Unit = { index ->
                            BookPageItem(
                                pageIndex = index,
                                viewModel = viewModel,
                                isVerticalMode = isVerticalMode,
                                isNightMode = bookDarkMode,
                                isTextMode = isTextMode,
                                isEpub = isEpub,
                                fontSize = fontSize,
                                currentZoom = { globalScale },
                                pageNotes = notesByPage[index] ?: emptyList()
                            )
                        }

                        if (isVerticalMode) {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 10.dp),
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = isTextMode || globalScale <= 1.01f
                            ) {
                                items(
                                    count = totalPages,
                                    key = { it }) { index -> pageContent(index) }
                            }
                        } else {
                            HorizontalPager(
                                contentPadding = PaddingValues(top = 10.dp),
                                state = pagerState,
                                userScrollEnabled = isTextMode || globalScale <= 1.01f || isViewportLocked,
                                beyondViewportPageCount = 1
                            ) { index -> pageContent(index) }
                        }
                    }


                }


            }
        }
    }

    if (showNotesSheet) {
        NotesListSheet(
            notes = notes,
            onNoteClick = { note ->
                dropdownExpanded = false
                val targetPage = viewModel.getPageForNote(note)
                showNotesSheet = false
                scope.launch {
                    masterPage = targetPage
                    if (isVerticalMode) {
                        listState.scrollToItem(targetPage)
                    } else {
                        pagerState.scrollToPage(targetPage)
                    }
                    viewModel.onPageChanged(targetPage)
                }
            },
            onDismiss = { showNotesSheet = false }
        )
    }
    if (showNoteDialog) {
        NoteInputDialog(
            onDismiss = { showNoteDialog = false },
            onConfirm = { viewModel.saveNote(it); showNoteDialog = false }
        )
    }
    if (showSelectPageDialog) {
        PageNumberInput(
            onInputPageNumber = { page ->
                scope.launch {
                    if (page > totalPages - 1) masterPage = totalPages - 1 else masterPage = page
                    if (isVerticalMode) listState.scrollToItem(masterPage)
                    else pagerState.scrollToPage(masterPage)
                    viewModel.onPageChanged(masterPage)
                    showSelectPageDialog = false

                }
            },
            onDismiss = { showSelectPageDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    visible: Boolean,
    dropdownExpanded : Boolean,
    bookDarkMode : Boolean,
    title: String,
    onBack: () -> Unit,
    onShowNotes: () -> Unit,
    onGoToPage: () -> Unit,
    onHideMenu: () -> Unit,
    onHideDropdown: () -> Unit,
    onToggleBookDarkMode: (Boolean) ->Unit,
) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it }) {
        TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        "Back", modifier = Modifier.size(20.dp)
                    )
                }
            },
            actions = {
                Row {
                    IconButton(
                        onClick = onHideMenu
                    ) {
                        Icon(
                            Icons.Rounded.Airplay, null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = onHideDropdown,

                            ) {
                            Icon(
                                Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        NightLightDropDownMenu(
                            expanded = dropdownExpanded, onDismissRequest = onHideDropdown
                        ) {
                            DropdownMenuItem(
                                text = { Text("Notes") },
                                onClick = onShowNotes,
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Draw,
                                        "Notes",
                                        modifier = Modifier.size(20.dp)
                                    )
                                })
                            DropdownMenuItem(
                                text = { Text("Go to page...") },
                                onClick = onGoToPage,
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Signpost,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("Night time")
                                },
                                onClick = {},
                                trailingIcon = {
                                    Switch(
                                        checked = bookDarkMode,
                                        onCheckedChange = onToggleBookDarkMode,
                                        thumbContent = {
                                            val iconSize = 20.dp
                                            if (bookDarkMode) Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(iconSize))
                                            else Icon(Icons.Rounded.WbSunny, null, modifier = Modifier.size(iconSize))
                                        },
                                        modifier = Modifier
                                            .scale(0.7f)
                                            .requiredSize(width = 40.dp, height = 24.dp),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Transparent,
//                                            checkedThumbColor = Color(0xE249497E),
                                            checkedTrackColor = Color(0xFF1E1F75),
                                            checkedIconColor = Color(0xFFCDC7DE),
                                            uncheckedThumbColor = Color(0xFFFFD600),
                                            uncheckedTrackColor = Color(0xFF90CAF9),
                                            uncheckedIconColor = Color(0xFFF57C00),
                                            uncheckedBorderColor = Color.Transparent
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

            }
        )
    }
}
