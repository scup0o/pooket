package com.project.pooket.ui.features.reader

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.data.local.note.NormRect
import com.project.pooket.data.local.note.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReaderScreen(
    bookUri: String,
    viewModel: ReaderViewModel = hiltViewModel(),
    isNightMode: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // ViewModel state
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalPages by viewModel.pageCount.collectAsStateWithLifecycle()
    val isVerticalMode by viewModel.isVerticalScrollMode.collectAsStateWithLifecycle()
    val isTextMode by viewModel.isTextMode.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()

    // UI State
    var masterPage by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSwitchingModes by remember { mutableStateOf(false) }
    var isViewportLocked by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { totalPages })
    val listState = rememberLazyListState()
    val activeUiPage = remember(isVerticalMode) {
        derivedStateOf { if (isVerticalMode) listState.firstVisibleItemIndex else pagerState.currentPage }
    }

    // UI Controls
    var showControls by remember { mutableStateOf(true) }
    val selectedText by viewModel.currentSelectionText.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(bookUri) { viewModel.loadPdf(bookUri) }

    LaunchedEffect(isLoading, totalPages) {
        if (!isLoading && totalPages > 0 && !isInitialized) {
            masterPage = initialPage
            if (isVerticalMode) listState.scrollToItem(masterPage)
            else pagerState.scrollToPage(masterPage)
            isInitialized = true
        }
    }

    LaunchedEffect(isVerticalMode, isTextMode) {
        if (isInitialized) {
            isSwitchingModes = true
            if (isVerticalMode) listState.scrollToItem(masterPage)
            else pagerState.scrollToPage(masterPage)
            isSwitchingModes = false
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

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var showNotesSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    // Load notes initially
    LaunchedEffect(bookUri) {
        viewModel.loadPdf(bookUri)
        viewModel.loadNotes(bookUri)
    }

    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    fun clampOffset(proposedOffset: Offset, scale: Float, size: Size): Offset {
        val maxX = (size.width * scale - size.width) / 2f
        val maxY = (size.height * scale - size.height) / 2f
        return Offset(
            proposedOffset.x.coerceIn(-maxX, maxX),
            proposedOffset.y.coerceIn(-maxY, maxY)
        )
    }

    Scaffold(
        containerColor = if (isNightMode) Color.Black else Color.White,
        topBar = {
            ReaderTopBar(
                showControls,
                "Pooket Reader",
                onBack,
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
                onPrevPage = {
                    scope.launch {
                        val target = (masterPage - 1).coerceAtLeast(0)
                        if (isVerticalMode) listState.animateScrollToItem(target)
                        else pagerState.animateScrollToPage(target)
                    }
                },
                onNextPage = {
                    scope.launch {
                        val target = (masterPage + 1).coerceAtMost(totalPages - 1)
                        if (isVerticalMode) listState.animateScrollToItem(target)
                        else pagerState.animateScrollToPage(target)
                    }
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                                    // 1. CLEAR SELECTION (Since we tapped outside/background)
                                    viewModel.clearAllSelection()
                                    // 2. TOGGLE CONTROLS
                                    showControls = !showControls
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
                                // Lock Horizontal Pan if Locked
                                val effectivePan = if (isViewportLocked) Offset(0f, pan.y) else pan
                                val proposedOffset = globalOffset + effectivePan
                                globalOffset = clampOffset(proposedOffset, newScale, size.toSize())
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(count = totalPages, key = { it }) { index ->
                                PdfPageItem(
                                    index, viewModel, true, isNightMode, isTextMode, fontSize,
                                    currentZoom = globalScale
                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isTextMode && (globalScale <= 1f || isViewportLocked),
                            beyondViewportPageCount = 1
                        ) { index ->
                            PdfPageItem(
                                index, viewModel, false, isNightMode, isTextMode, fontSize,
                                currentZoom = globalScale
                            )
                        }
                    }
                }

                SmallFloatingActionButton(
                    onClick = { showControls = !showControls },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }

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
            onConfirm = { noteText ->
                viewModel.saveNote(noteText)
                showNoteDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isVerticalMode: Boolean,
    isNightMode: Boolean,
    isTextMode: Boolean,
    fontSize: Float,
    currentZoom: Float
) {
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val pageNotes = remember(allNotes, pageIndex) { allNotes.filter { it.pageIndex == pageIndex } }
    var clickedNoteContent by remember { mutableStateOf<String?>(null) }

    // --- TEXT MODE LOGIC ---
    if (isTextMode) {
        // (Text Mode logic remains exactly as before...)
        var textContent by remember { mutableStateOf<String?>(null) }
        var textFieldValue by remember(pageIndex, isTextMode) { mutableStateOf(TextFieldValue()) }
        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
        val textSelection by viewModel.textSelection.collectAsStateWithLifecycle()

        LaunchedEffect(textSelection) {
            if (textSelection == null && !textFieldValue.selection.collapsed) {
                textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
            }
        }

        LaunchedEffect(pageIndex, pageNotes) {
            if (textContent == null) textContent = viewModel.extractText(pageIndex)
            textContent?.let { raw ->
                val annotated = viewModel.processTextHighlights(raw, pageNotes)
                if (textFieldValue.text != raw) textFieldValue = TextFieldValue(annotated)
                else textFieldValue = textFieldValue.copy(annotatedString = annotated)
            }
        }

        val bringIntoViewResponder = remember {
            object : BringIntoViewResponder {
                override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero
                override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
            }
        }

        val customToolbar = remember {
            CustomTextToolbar(
                onShowMenu = {
                    val sel = textFieldValue.selection
                    if (!sel.collapsed && textContent != null) {
                        try {
                            val selectedText = textContent!!.substring(sel.start, sel.end)
                            viewModel.setTextSelection(pageIndex, selectedText, sel)
                        } catch (_: Exception) {}
                    }
                },
                onHideMenu = { },
                onCopy = { }
            )
        }

        CompositionLocalProvider(LocalTextToolbar provides customToolbar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                if (textContent == null) {
                    Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { if (it.text == textFieldValue.text) textFieldValue = it },
                        readOnly = true,
                        textStyle = TextStyle(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5).sp,
                            color = if (isNightMode) Color.LightGray else Color.Black,
                            textAlign = TextAlign.Justify
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewResponder(bringIntoViewResponder),
                        onTextLayout = { layoutResult = it }
                    )
                    if (layoutResult != null) {
                        pageNotes.forEach { note ->
                            if (note.textRangeStart != null) {
                                    val bounds = layoutResult!!.getBoundingBox(note.textRangeStart!!)
                                    val iconX = bounds.left
                                    val iconY = bounds.top - 24.dp.toPx(LocalDensity.current)
                                    NoteIcon(iconX, iconY, 24.dp, onClick = { clickedNoteContent = note.noteContent })
                            }
                        }
                    }
                }
            }
        }
        if (clickedNoteContent != null) NoteContentDialog(content = clickedNoteContent!!) { clickedNoteContent = null }
        return
    }

    // --- IMAGE MODE LOGIC ---
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var layoutSize by remember { mutableStateOf(Size.Zero) }
    var noteRectsMap by remember { mutableStateOf<Map<Long, List<NormRect>>>(emptyMap()) }

    LaunchedEffect(pageNotes, isTextMode) {
        withContext(Dispatchers.Default) {
            val newMap = mutableMapOf<Long, List<NormRect>>()
            pageNotes.forEach { note -> newMap[note.id] = viewModel.getRectsForNote(note) }
            withContext(Dispatchers.Main) { noteRectsMap = newMap }
        }
    }

    LaunchedEffect(pageIndex) {
        if (bitmap == null) bitmap = viewModel.renderPage(pageIndex)
    }

    val currentBitmap = bitmap ?: run {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pdfAspectRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
    val selection by viewModel.selectionState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pdfAspectRatio)
            .background(if (isNightMode) Color.Black else Color.White)
            .onGloballyPositioned { layoutSize = it.size.toSize() }
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val currentSel = viewModel.selectionState.value

                    // 1. Check if touching a Drag Handle
                    val handleHit = if (currentSel?.pageIndex == pageIndex) {
                        viewModel.checkHandleHitUI(down.position, layoutSize, currentSel)
                    } else DragHandle.NONE

                    if (handleHit != DragHandle.NONE) {
                        // --- DRAGGING HANDLE ---
                        down.consume()
                        viewModel.setDraggingHandle(handleHit)
                        drag(down.id) { change ->
                            change.consume()
                            viewModel.onDrag(change.position, layoutSize)
                        }
                        viewModel.onDragEnd()
                    } else {
                        // --- LONG PRESS SELECTION ---
                        try {
                            // Wait for long press timeout (e.g. 500ms)
                            withTimeout(500) {
                                waitForUpOrCancellation()
                            }
                            // If we reach here, it was a short tap.
                            // We do nothing and let the Parent `detectTapGestures` handle it.
                        } catch (e: PointerEventTimeoutCancellationException) {
                            // --- TIMEOUT REACHED -> LONG PRESS ---

                            // 1. Trigger Selection Calculation
                            viewModel.onLongPress(pageIndex, down.position, layoutSize)

                            // 2. IMPORTANT: Consume the rest of the gesture (dragging or lifting)
                            // REMOVED awaitPointerEventScope wrapper here
                            val dragPointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == dragPointerId }

                                if (change == null) break // Pointer lost

                                if (change.changedToUp()) {
                                    change.consume() // <--- CRITICAL FIX: Consume UP event
                                    break
                                }

                                if (change.positionChanged()) {
                                    change.consume()
                                    viewModel.onDrag(change.position, layoutSize)
                                }
                            }

                            viewModel.onDragEnd()
                        }
                    }
                }
            }
    ) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
            colorFilter = if (isNightMode) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))) else null
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // Draw Notes
            pageNotes.forEach { note ->
                val rects = noteRectsMap[note.id] ?: emptyList()
                rects.forEach { rect ->
                    drawRect(Color(0x66FFEB3B), Offset(rect.left * w, rect.top * h), Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h))
                }
            }

            // Draw Selection
            if (selection?.pageIndex == pageIndex) {
                val rects = selection!!.rects
                rects.forEach { rect ->
                    drawRect(Color(0x4D2196F3), Offset(rect.left * w, rect.top * h), Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h))
                }
                if (rects.isNotEmpty()) {
                    val sortedRects = rects.sortedBy { it.top }
                    val first = sortedRects.first()
                    val last = sortedRects.last()
                    val baseRadius = 12.dp.toPx()
                    val scaledRadius = baseRadius / currentZoom
                    drawAndroidSelectionHandle(first.left * w, first.bottom * h, scaledRadius, true)
                    drawAndroidSelectionHandle(last.right * w, last.bottom * h, scaledRadius, false)
                }
            }
        }

        if (layoutSize != Size.Zero) {
            pageNotes.forEach { note ->
                val rects = noteRectsMap[note.id] ?: emptyList()
                val first = rects.firstOrNull()
                if (first != null) {
                    val iconX = first.left * layoutSize.width
                    val baseSize = 24.dp
                    val scaledSize = baseSize / currentZoom
                    val padding = 2.dp / currentZoom
                    val iconY = (first.top * layoutSize.height) - scaledSize.toPx(LocalDensity.current) - padding.toPx(LocalDensity.current)
                    NoteIcon(iconX, iconY, scaledSize, 4.dp / currentZoom) { clickedNoteContent = note.noteContent }
                }
            }
        }
    }

    if (clickedNoteContent != null) {
        NoteContentDialog(content = clickedNoteContent!!) { clickedNoteContent = null }
    }
}

// ... (Helper Composables: NoteIcon, NoteContentDialog, drawAndroidSelectionHandle, Dp.toPx, SelectionControlBar, NoteInputDialog, NotesListSheet, FontSizeControl, ReaderTopBar, PageIndicator, CustomTextToolbar)
// Ensure you keep these exactly as they were in the previous iteration.

@Composable
fun ReaderControls(
    visible: Boolean,
    isVertical: Boolean,
    isTextMode: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleTextMode: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallFloatingActionButton(
                onClick = onPrevPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.ChevronLeft, "Prev")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onToggleMode) {
                    Icon(
                        if (isVertical) Icons.Default.SwapVert else Icons.Default.ViewCarousel,
                        "Mode"
                    )
                }
                FloatingActionButton(
                    onClick = onToggleLock,
                    containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        "Lock"
                    )
                }
                FloatingActionButton(
                    onClick = onToggleTextMode,
                    containerColor = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        if (isTextMode) Icons.Default.Image else Icons.Default.TextFields,
                        "Text"
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = onNextPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.ChevronRight, "Next")
            }
        }
    }
}
// ... (Include other helpers: NoteIcon, NoteContentDialog, drawAndroidSelectionHandle, Dp.toPx, SelectionControlBar, NoteInputDialog, NotesListSheet, FontSizeControl, ReaderTopBar, PageIndicator, CustomTextToolbar) ...
@Composable
fun NoteIcon(
    x: Float,
    y: Float,
    size: Dp,
    iconPadding: Dp = 4.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(size)
            .shadow(1.dp, CircleShape)
            .background(Color.White, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // Disable ripple for cleaner look on small icons
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = Icons.Default.Comment,
            contentDescription = "Note",
            tint = Color(0xFFFFC107),
            modifier = Modifier
                .padding(iconPadding)
                .fillMaxSize()
        )
    }
}

@Composable
fun NoteContentDialog(content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = { Text(content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAndroidSelectionHandle(
    x: Float,
    y: Float,
    radius: Float,
    isLeft: Boolean
) {
    val color = Color(0xFF2196F3)
    val path = androidx.compose.ui.graphics.Path()

    val circleCenterY = y + radius
    val circleCenterX = if (isLeft) x - (radius * 0.5f) else x + (radius * 0.5f)

    path.moveTo(x, y)

    if (isLeft) {
        path.quadraticBezierTo(
            x - radius, y + (radius * 0.5f),
            x - radius, y + radius
        )
        path.arcTo(
            rect = Rect(
                center = Offset(x - (radius * 0.8f), y + radius),
                radius = radius
            ),
            startAngleDegrees = 135f,
            sweepAngleDegrees = 270f,
            forceMoveTo = false
        )
        path.lineTo(x, y)
    } else {
        path.quadraticBezierTo(
            x + radius, y + (radius * 0.5f),
            x + radius, y + radius
        )
        path.arcTo(
            rect = Rect(
                center = Offset(x + (radius * 0.8f), y + radius),
                radius = radius
            ),
            startAngleDegrees = 135f,
            sweepAngleDegrees = -270f,
            forceMoveTo = false
        )
        path.lineTo(x, y)
    }
    drawPath(path, color)
}

@Composable
fun Dp.toPx(density: androidx.compose.ui.unit.Density) = with(density) { this@toPx.toPx() }

@Composable
fun SelectionControlBar(
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest, // Distinct color
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Text Selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, "Copy")
                }
                IconButton(onClick = onNote) {
                    Icon(Icons.Default.Edit, "Note")
                }
                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 8.dp))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
        }
    }
}

@Composable
fun NoteInputDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text("Enter your note here...") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListSheet(
    notes: List<NoteEntity>,
    onNoteClick: (NoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "My Notes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally)
        )
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.6f)
        ) {
            items(notes.sortedBy { it.pageIndex }) { note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNoteClick(note) }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Page ${note.pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            SimpleDateFormat(
                                "MMM dd, HH:mm",
                                Locale.getDefault()
                            ).format(Date(note.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (note.noteContent.isNotEmpty()) {
                        Text(
                            note.noteContent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "\"${note.originalText}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color(0xFFFFF9C4))
                            .padding(4.dp)
                    )
                }
                HorizontalDivider()
            }
            if (notes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No notes yet.", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun FontSizeControl(visible: Boolean, fontSize: Float, onFontSizeChange: (Float) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Surface(
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Font Size: ${fontSize.toInt()}sp",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..40f
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(visible: Boolean, title: String, onBack: () -> Unit, onShowNotes: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it }
    ) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = onShowNotes) {
                    Icon(Icons.Default.List, "Notes")
                }
            }
        )
    }
}

@Composable
fun PageIndicator(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Text(
            text = "$currentPage / $totalPages",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

class CustomTextToolbar(
    private val onShowMenu: (Rect) -> Unit,
    private val onHideMenu: () -> Unit,
    private val onCopy: () -> Unit
) : TextToolbar {

    override val status: TextToolbarStatus
        get() = if (shown) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    private var shown = false

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        shown = true
        onShowMenu(rect)
    }

    override fun hide() {
        shown = false
        onHideMenu()
    }
}