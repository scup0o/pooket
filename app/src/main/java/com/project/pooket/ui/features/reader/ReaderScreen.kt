package com.project.pooket.ui.features.reader

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.data.local.note.NormRect
import com.project.pooket.data.local.note.NoteEntity
import com.project.pooket.ui.composable.common.NightLightBottomModal
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
    val clipboardManager = LocalClipboardManager.current

    // --- State Collection ---
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalPages by viewModel.pageCount.collectAsStateWithLifecycle()
    val isVerticalMode by viewModel.isVerticalScrollMode.collectAsStateWithLifecycle()
    val isTextMode by viewModel.isTextMode.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val selectedText by viewModel.currentSelectionText.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    // --- Local UI State ---
    var masterPage by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSwitchingModes by remember { mutableStateOf(false) }

    var isViewportLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    // --- Scroll/Pager State ---
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val listState = rememberLazyListState()

    // --- Zoom State ---
    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    // --- Side Effects ---
    LaunchedEffect(bookUri) { viewModel.loadPdf(bookUri) }

    // 1. Initial Load Sync
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
            delay(100)
            isSwitchingModes = false
        }
    }

    val activeUiPage = remember(isVerticalMode) {
        derivedStateOf { if (isVerticalMode) listState.firstVisibleItemIndex else pagerState.currentPage }
    }
    LaunchedEffect(activeUiPage.value) {
        if (isInitialized && !isSwitchingModes) {
            if (masterPage != activeUiPage.value) {
                masterPage = activeUiPage.value
                viewModel.onPageChanged(masterPage)
            }
        }
    }

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
            ReaderTopBar(showControls, "Pooket Reader", onBack, onShowNotes = { showNotesSheet = true })
        },
        bottomBar = {
            FontSizeControl(showControls && isTextMode, fontSize, viewModel::setFontSize)
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
                                globalOffset = clampOffset(globalOffset + effectivePan, newScale, size.toSize())
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
                                val pageNotes = remember(notes) { notes.filter { it.pageIndex == index } }
                                PdfPageItem(index, viewModel, true, isNightMode, isTextMode, fontSize, globalScale, pageNotes)
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isTextMode && (globalScale <= 1f || isViewportLocked),
                            beyondViewportPageCount = 1
                        ) { index ->
                            val pageNotes = remember(notes) { notes.filter { it.pageIndex == index } }
                            PdfPageItem(index, viewModel, false, isNightMode, isTextMode, fontSize, globalScale,pageNotes)
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
                    Icon(Icons.Default.Menu, "Menu")
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

    // ... (Dialogs and Sheets logic remains the same) ...
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

// ----------------------------------------------------------------------------
// 1. PAGE ITEM WRAPPER (Decides Mode)
// ----------------------------------------------------------------------------
@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isVerticalMode: Boolean,
    isNightMode: Boolean,
    isTextMode: Boolean,
    fontSize: Float,
    currentZoom: Float,
    pageNotes: List<NoteEntity>,
    ) {
    // Shared State for clicking a note icon
    var clickedNoteContent by remember { mutableStateOf<String?>(null) }

    if (isTextMode) {
        PdfTextPage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            isVerticalMode = isVerticalMode,
            fontSize = fontSize,
            pageNotes = pageNotes,
            onNoteClick = { clickedNoteContent = it }
        )
    } else {
        PdfImagePage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            currentZoom = currentZoom,
            pageNotes = pageNotes,
            onNoteClick = { clickedNoteContent = it }
        )
    }

    if (clickedNoteContent != null) {
        NoteContentDialog(content = clickedNoteContent!!) { clickedNoteContent = null }
    }
}

// ----------------------------------------------------------------------------
// 2. TEXT MODE PAGE
// ----------------------------------------------------------------------------
@Composable
private fun PdfTextPage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isNightMode: Boolean,
    isVerticalMode: Boolean,
    fontSize: Float,
    pageNotes: List<NoteEntity>,
    onNoteClick: (String) -> Unit
) {
    var textContent by remember { mutableStateOf<String?>(null) }
    var textFieldValue by remember(pageIndex) { mutableStateOf(TextFieldValue()) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textSelection by viewModel.textSelection.collectAsStateWithLifecycle()

    // Sync Selection from ViewModel (e.g., clear command)
    LaunchedEffect(textSelection) {
        if (textSelection == null && !textFieldValue.selection.collapsed) {
            textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
        }
    }

    // Load Text and Apply Highlights
    LaunchedEffect(pageIndex, pageNotes) {
        if (textContent == null) textContent = viewModel.extractText(pageIndex)
        textContent?.let { raw ->
            val annotated = viewModel.processTextHighlights(raw, pageNotes)
            if (textFieldValue.text != raw) textFieldValue = TextFieldValue(annotated)
            else textFieldValue = textFieldValue.copy(annotatedString = annotated)
        }
    }

    // Custom Toolbar to capture "Copy" or "Note"
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
            onHideMenu = {
//                viewModel.clearAllSelection()
            },
            onCopy = {}
        )
    }

    CompositionLocalProvider(
        LocalTextToolbar provides customToolbar,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color(0xFF2196F3),
            backgroundColor = Color(0x662196F3)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        viewModel.clearAllSelection()
                    }
                }
                // Only scroll internally if NOT in Vertical Mode (LazyColumn handles Vertical)
                .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            if (textContent == null) {
                Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        if (it.text == textFieldValue.text) {
                            textFieldValue = it
                            // FIX: If user taps TEXT to move cursor (collapsing selection), clear the toolbar
                            if (it.selection.collapsed) {
                                viewModel.clearAllSelection()
                            }
                        }
                    },                    readOnly = true,
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5).sp,
                        color = if (isNightMode) Color.LightGray else Color.Black,
                        textAlign = TextAlign.Justify
                    ),
                    modifier = Modifier
                        .fillMaxWidth(),
                    onTextLayout = { layoutResult = it }
                )

                // Render Note Icons
                if (layoutResult != null) {
                    val textLen = layoutResult!!.layoutInput.text.text.length // Get actual length

                    pageNotes.forEach { note ->
                        note.textRangeStart?.let { start ->
                            // FIX: Only try to get bounds if text is not empty and start is valid
                            if (textLen > 0 && start < textLen) {
                                val bounds = layoutResult!!.getBoundingBox(start)
                                val iconX = bounds.left
                                val iconY = bounds.top - 24.dp.toPx(LocalDensity.current)
                                NoteIcon(iconX, iconY, 24.dp, onClick = { onNoteClick(note.noteContent) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 3. IMAGE MODE PAGE
// ----------------------------------------------------------------------------
@Composable
private fun PdfImagePage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isNightMode: Boolean,
    currentZoom: Float,
    pageNotes: List<NoteEntity>,
    onNoteClick: (String) -> Unit
) {
    // 1. OPTIMIZATION: Use produceState to handle cancellation automatically on scroll
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = viewModel.renderPage(pageIndex)
    }

    // 2. OPTIMIZATION: Calculate rects efficiently.
    // If the page recycles (scrolls away), this block cancels immediately, saving CPU.
    val noteRectsMap by produceState<Map<Long, List<NormRect>>>(initialValue = emptyMap(), key1 = pageNotes) {
        val newMap = withContext(Dispatchers.Default) {
            val map = mutableMapOf<Long, List<NormRect>>()
            pageNotes.forEach { note ->
                map[note.id] = viewModel.getRectsForNote(note)
            }
            map
        }
        value = newMap
    }

    // 3. OPTIMIZATION: Pass State<T> instead of T.
    // This prevents the parent (PdfImagePage) from recomposing when selection changes.
    // We only read this state inside the Canvas draw block.
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()

    // Fallback for loading
    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBitmap = bitmap!!
    val pdfAspectRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
    var layoutSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pdfAspectRatio)
            .background(if (isNightMode) Color.Black else Color.White)
            .onGloballyPositioned { layoutSize = it.size.toSize() }
            .pointerInput(pageIndex) {
                // Gesture logic remains the same...
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // We read value directly from VM state here to ensure latest data without recomposition
                    val currentSel = viewModel.selectionState.value

                    val handleHit = if (currentSel?.pageIndex == pageIndex) {
                        viewModel.checkHandleHitUI(down.position, layoutSize, currentSel)
                    } else DragHandle.NONE

                    if (handleHit != DragHandle.NONE) {
                        down.consume()
                        viewModel.setDraggingHandle(handleHit)
                        drag(down.id) { change ->
                            change.consume()
                            viewModel.onDrag(change.position, layoutSize)
                        }
                        viewModel.onDragEnd()
                    } else {
                        try {
                            withTimeout(500) { waitForUpOrCancellation() }
                        } catch (e: PointerEventTimeoutCancellationException) {
                            viewModel.onLongPress(pageIndex, down.position, layoutSize)
                            val dragPointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == dragPointerId }
                                if (change == null) break
                                if (change.changedToUp()) { change.consume(); break }
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
            .drawWithContent {
                // 4. OPTIMIZATION: Drawing Layer
                // This block runs every frame during drag, BUT it does not trigger "Recomposition"
                // of the Box or the Image. It only repaints the pixels.

                // A. Draw the PDF Bitmap
                drawContent() // Draws the child (Image)

                val w = size.width
                val h = size.height

                // B. Draw Notes
                pageNotes.forEach { note ->
                    val rects = noteRectsMap[note.id] ?: emptyList()
                    rects.forEach { rect ->
                        drawRect(
                            color = Color(0x66FFEB3B),
                            topLeft = Offset(rect.left * w, rect.top * h),
                            size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h)
                        )
                    }
                }

                // C. Draw Selection
                // We read 'selectionState' HERE. Since we are in the Draw phase,
                // changes to this state trigger 'invalidate', NOT 'recompose'.
                val sel = selectionState
                if (sel?.pageIndex == pageIndex) {
                    val rects = sel.rects
                    rects.forEach { rect ->
                        drawRect(
                            color = Color(0x4D2196F3),
                            topLeft = Offset(rect.left * w, rect.top * h),
                            size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h)
                        )
                    }
                    if (rects.isNotEmpty()) {
                        val sortedRects = rects.sortedBy { it.top }
                        val first = sortedRects.first()
                        val last = sortedRects.last()
                        val baseRadius = 9.dp.toPx()
                        val scaledRadius = baseRadius / currentZoom

                        // Helper extension needs to be available
                        drawAndroidSelectionHandle(first.left * w, first.bottom * h, scaledRadius, true)
                        drawAndroidSelectionHandle(last.right * w, last.bottom * h, scaledRadius, false)
                    }
                }
            }
    ) {
        // The heavy Image is now just "Content" drawn by drawContent()
        // It will NOT reload/re-layout when you drag the selection handle.
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

        // Note Icons Layer (Clickable elements must remain in Composition)
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
                    NoteIcon(iconX, iconY, scaledSize, 4.dp / currentZoom) { onNoteClick(note.noteContent) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 4. UI COMPONENT HELPERS
// ----------------------------------------------------------------------------

@Composable
fun NoteIcon(x: Float, y: Float, size: Dp, iconPadding: Dp = 4.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(size)
            .shadow(1.dp, CircleShape)
            .background(Color.White, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = Icons.Default.Comment,
            contentDescription = "Note",
            tint = Color(0xFFFFC107),
            modifier = Modifier.padding(iconPadding).fillMaxSize()
        )
    }
}

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
            SmallFloatingActionButton(onClick = onPrevPage, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.ChevronLeft, "Prev")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onToggleMode) {
                    Icon(if (isVertical) Icons.Default.SwapVert else Icons.Default.ViewCarousel, "Mode")
                }
                FloatingActionButton(
                    onClick = onToggleLock,
                    containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock")
                }
                FloatingActionButton(
                    onClick = onToggleTextMode,
                    containerColor = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(if (isTextMode) Icons.Default.Image else Icons.Default.TextFields, "Text")
                }
            }
            SmallFloatingActionButton(onClick = onNextPage, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.ChevronRight, "Next")
            }
        }
    }
}

@Composable
fun SelectionControlBar(onCopy: () -> Unit, onNote: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Text Selected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
                IconButton(onClick = onNote) { Icon(Icons.Default.Edit, "Note") }
                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 8.dp))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
        }
    }
}

// ... (Rest of helpers: NoteContentDialog, NoteInputDialog, NotesListSheet, FontSizeControl, ReaderTopBar, PageIndicator, CustomTextToolbar, drawAndroidSelectionHandle, toPx)
// Ensure you include these existing helpers at the bottom of the file as before.

@Composable
fun NoteContentDialog(content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = { Text(content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

fun DrawScope.drawAndroidSelectionHandle(
    x: Float,
    y: Float,
    radius: Float,
    isLeft: Boolean
) {
    val handleColor = Color(0xFF2196F3) // Standard Android Blue
    val path = Path()

    if (isLeft) {
        // --- LEFT HANDLE (Start) ---
        // Tip points Top-Right (to x, y)
        // Body hangs Bottom-Left
        val centerX = x - radius
        val centerY = y + radius

        path.moveTo(x, y)                          // 1. Start at Tip
        path.lineTo(x, y + radius)                 // 2. Line Vertical Down
        path.arcTo(                                // 3. Arc 270 degrees Clockwise (0 -> 270)
            rect = Rect(
                left = centerX - radius,
                top = centerY - radius,
                right = centerX + radius,
                bottom = centerY + radius
            ),
            forceMoveTo = false,
            startAngleDegrees = 0f,
            sweepAngleDegrees = 270f
        )
        path.lineTo(x, y)                          // 4. Close loop back to Tip
    } else {
        // --- RIGHT HANDLE (End) ---
        // Tip points Top-Left (to x, y)
        // Body hangs Bottom-Right
        val centerX = x + radius
        val centerY = y + radius

        path.moveTo(x, y)                          // 1. Start at Tip
        path.lineTo(x, y + radius)                 // 2. Line Vertical Down
        path.arcTo(                                // 3. Arc 270 degrees Counter-Clockwise (180 -> -90/270)
            rect = Rect(
                left = centerX - radius,
                top = centerY - radius,
                right = centerX + radius,
                bottom = centerY + radius
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -270f,
            forceMoveTo = false
        )
        path.lineTo(x, y)                          // 4. Close loop back to Tip
    }

    drawPath(path, handleColor)
}

@Composable
fun Dp.toPx(density: Density) = with(density) { this@toPx.toPx() }

@Composable
fun NoteInputDialog(initialText: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.focusRequester(focusRequester)) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListSheet(notes: List<NoteEntity>, onNoteClick: (NoteEntity) -> Unit, onDismiss: () -> Unit) {
    NightLightBottomModal(onDismiss = onDismiss) {
        Text("My Notes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
        LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
            items(notes.sortedBy { it.pageIndex }) { note ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onNoteClick(note) }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Page ${note.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (note.noteContent.isNotEmpty()) Text(note.noteContent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("\"${note.originalText}\"", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(Color(0xFFFFF9C4)).padding(4.dp))
                }
                HorizontalDivider()
            }
            if (notes.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("No notes yet.", color = Color.Gray) } }
        }
    }
}

@Composable
fun FontSizeControl(visible: Boolean, fontSize: Float, onFontSizeChange: (Float) -> Unit) {
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                Slider(value = fontSize, onValueChange = onFontSizeChange, valueRange = 12f..40f)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(visible: Boolean, title: String, onBack: () -> Unit, onShowNotes: () -> Unit) {
    AnimatedVisibility(visible, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { IconButton(onClick = onShowNotes) { Icon(Icons.Default.List, "Notes") } })
    }
}

@Composable
fun PageIndicator(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier) {
    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Text(text = "$currentPage / $totalPages", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

class CustomTextToolbar(private val onShowMenu: (Rect) -> Unit, private val onHideMenu: () -> Unit, private val onCopy: () -> Unit) : TextToolbar {
    override val status get() = if (shown) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    private var shown = false
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) { shown = true; onShowMenu(rect) }
    override fun hide() { shown = false; onHideMenu() }
}