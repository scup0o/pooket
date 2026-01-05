package com.project.pooket.ui.reader

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.data.local.note.NoteEntity
import com.project.pooket.ui.common.NightLightBottomModal
import com.project.pooket.ui.reader.composable.FontSizeControl
import com.project.pooket.ui.reader.composable.NoteInputDialog
import com.project.pooket.ui.reader.composable.NotesListSheet
import com.project.pooket.ui.reader.composable.PageIndicator
import com.project.pooket.ui.reader.composable.PdfPageItem
import com.project.pooket.ui.reader.composable.ReaderControls
import com.project.pooket.ui.reader.composable.SelectionControlBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    //viewmodel-state
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalPages by viewModel.pageCount.collectAsStateWithLifecycle()
    val isVerticalMode by viewModel.isVerticalScrollMode.collectAsStateWithLifecycle()
    val isTextMode by viewModel.isTextMode.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val selectedText by viewModel.currentSelectionText.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    //ui-state
    var masterPage by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSwitchingModes by remember { mutableStateOf(false) }

    var isViewportLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    //scroll/pager-state
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val listState = rememberLazyListState()

    //zoom-state
    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    //init-load
    LaunchedEffect(bookUri) { viewModel.loadPdf(bookUri) }
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

                if (visibleItems.isEmpty()) return@derivedStateOf 0

                val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2

                val centerItem = visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + (item.size / 2)
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                if (!listState.canScrollForward) {
                    return@derivedStateOf (layoutInfo.totalItemsCount - 1)
                }

                return@derivedStateOf centerItem?.index ?: 0
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

    //screen-locking (disable horizontal drag)
    fun clampOffset(proposedOffset: Offset, scale: Float, size: Size): Offset {
        val maxX = (size.width * scale - size.width) / 2f
        val maxY = (size.height * scale - size.height) / 2f
        return Offset(
            proposedOffset.x.coerceIn(-maxX, maxX),
            proposedOffset.y.coerceIn(-maxY, maxY)
        )
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
        containerColor = if(isNightMode) Color.Black else Color.White,
        topBar = {
            ReaderTopBar(
                visible = showControls,
                title = bookTitle,
                onBack = onBack,
                onShowNotes = { showNotesSheet = true })
        },
        bottomBar = {
            FontSizeControl(
                visible = showControls && isTextMode,
                fontSize = fontSize,
                onFontSizeChange = viewModel::setFontSize
            )
        },
        floatingActionButton = {
            ReaderControls(
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
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clipToBounds()
        ) {
            if (isLoading || totalPages == 0) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isVerticalMode, isTextMode) {
                            if (isTextMode) return@pointerInput
                            detectTapGestures(
                                onTap = {
                                    viewModel.clearAllSelection()
//                                    showControls = !showControls
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
                        .pointerInput(isVerticalMode, isTextMode, isViewportLocked) {
                            if (isTextMode) return@pointerInput
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (globalScale * zoom).coerceIn(1f, 5f)
                                val effectivePan = if (isViewportLocked) Offset(0f, pan.y) else pan
                                globalOffset = clampOffset(
                                    proposedOffset = globalOffset + effectivePan,
                                    scale = newScale,
                                    size = size.toSize()
                                )
                                globalScale = newScale
                            }
                        }
                        .graphicsLayer {
                            scaleX = if (isTextMode) 1f else globalScale
                            scaleY = if (isTextMode) 1f else globalScale
                            translationX = if (isTextMode) 0f else globalOffset.x
                            translationY = if (isTextMode || isVerticalMode) 0f else globalOffset.y
                        }
                ) {
                    if (isVerticalMode) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(count = totalPages, key = { it }) { index ->
                                val pageNotes =
                                    remember(notes) { notes.filter { it.pageIndex == index } }
                                PdfPageItem(
                                    pageIndex = index,
                                    viewModel = viewModel,
                                    isVerticalMode = true,
                                    isNightMode = isNightMode,
                                    isTextMode = isTextMode,
                                    fontSize = fontSize,
                                    currentZoom = globalScale,
                                    pageNotes = pageNotes
                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isTextMode && (globalScale <= 1f || isViewportLocked),
                            beyondViewportPageCount = 1
                        ) { index ->
                            val pageNotes =
                                remember(notes) { notes.filter { it.pageIndex == index } }
                            PdfPageItem(
                                pageIndex = index,
                                viewModel = viewModel,
                                isVerticalMode = false,
                                isNightMode = isNightMode,
                                isTextMode = isTextMode,
                                fontSize = fontSize,
                                currentZoom = globalScale,
                                pageNotes = pageNotes
                            )
                        }
                    }
                }
                MenuController(
                    onToggleMenu = { showControls = !showControls },
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                if (showControls) {
                    PageIndicator(
                        currentPage = masterPage + 1,
                        totalPages = totalPages,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isTextMode) 180.dp else 100.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = selectedText != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
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
        }
    }

    if (showNotesSheet) {
        NotesListSheet(
            notes = notes,
            onNoteClick = { note ->
                showNotesSheet = false
                scope.launch {
                    masterPage = note.pageIndex
                    if (isVerticalMode) listState.scrollToItem(masterPage)
                    else pagerState.scrollToPage(masterPage)
                    viewModel.onPageChanged(masterPage)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(visible: Boolean, title: String, onBack: () -> Unit, onShowNotes: () -> Unit) {
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
                        "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = onShowNotes) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Note,
                        "Notes"
                    )
                }
            }
        )
    }
}

@Composable
fun MenuController(
    onToggleMenu: () -> Unit,
    modifier: Modifier,
) {
    SmallFloatingActionButton(
        onClick = onToggleMenu,
        modifier = modifier
            .padding(top = 48.dp, end = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Icon(Icons.Default.Menu, "Menu")
    }
}
