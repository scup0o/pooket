package com.project.pooket.ui.features.reader

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.data.local.note.NoteEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    //viewmodel state
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalPages by viewModel.pageCount.collectAsStateWithLifecycle()
    val isVerticalMode by viewModel.isVerticalScrollMode.collectAsStateWithLifecycle()
    val isTextMode by viewModel.isTextMode.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()

    //ui-state
    var masterPage by remember { mutableIntStateOf(0) }
    var isInitialized by remember { mutableStateOf(false) }
    var isSwitchingModes by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { totalPages })
    val listState = rememberLazyListState()
    val activeUiPage = remember(isVerticalMode) {
        derivedStateOf { if (isVerticalMode) listState.firstVisibleItemIndex else pagerState.currentPage }
    }

    //ui-controller
    var showControls by remember { mutableStateOf(true) }


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

    // Load notes initially
    LaunchedEffect(bookUri) {
        viewModel.loadPdf(bookUri)
        viewModel.loadNotes(bookUri) // Call the new loader
    }


    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

    fun clampOffset(proposedOffset: Offset, scale: Float, size: Size): Offset {
        // Available hidden width/height due to zoom
        val maxX = (size.width * scale - size.width) / 2f
        val maxY = (size.height * scale - size.height) / 2f

        // If scale is 1f, maxX/Y is 0, locking content to center
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

                            // DOUBLE TAP TO ZOOM
                            detectTapGestures(
//                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    if (globalScale > 1f) {
                                        // Reset to 1f and Center
                                        globalScale = 1f
                                        globalOffset = Offset.Zero
                                    } else {
                                        // Zoom in 2.5x
                                        globalScale = 2.5f
                                        // No offset change (zooms to center)
                                    }
                                }
                            )
                        }
                        .pointerInput(isVerticalMode, isTextMode) {
                            if (isTextMode) return@pointerInput

                            // ZOOM & PAN GESTURE
                            detectTransformGestures { _, pan, zoom, _ ->
                                // 1. Calculate New Scale (Clamped 1f..5f)
                                val newScale = (globalScale * zoom).coerceIn(1f, 5f)

                                // 2. Calculate New Offset
                                // If we are zooming, we might want to stay centered or follow focus,
                                // but simple panning updates the offset.
                                val proposedOffset = globalOffset + pan

                                // 3. Apply Constraints
                                // Only update offset if we are zoomed in (scale > 1)
                                // If scale is 1, offset forces to Zero (handled by clamp logic)
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
//                        .pointerInput(isVerticalMode, isTextMode) {
//                            detectTapGestures(
//                                onTap = { showControls = !showControls },
//                                onDoubleTap = {
//                                    if (!isTextMode) {
//                                        globalScale = if (globalScale > 1f) 1f else 2.5f
//                                        globalOffset = Offset.Zero
//                                    }
//                                }
//                            )
//                        }
//                        .pointerInput(isVerticalMode, isTextMode) {
//                            if (isTextMode) return@pointerInput
//                            awaitEachGesture {
//                                awaitFirstDown(requireUnconsumed = false)
//                                do {
//                                    val event = awaitPointerEvent()
//                                    val zoomChange = event.calculateZoom()
//                                    val panChange = event.calculatePan()
//
//                                    if (event.changes.size > 1 || globalScale > 1f) {
//                                        globalScale = (globalScale * zoomChange).coerceIn(1f, 5f)
//
//                                        if (isVerticalMode) {
//                                            if (panChange.y != 0f) {
//                                                scope.launch { listState.dispatchRawDelta(-panChange.y / globalScale) }
//                                            }
//                                            val extraWidth = (size.width * (globalScale - 1)) / 2
//                                            val newX = (globalOffset.x + panChange.x).coerceIn(-extraWidth, extraWidth)
//                                            globalOffset = Offset(newX, 0f)
//                                        } else {
//                                            globalOffset += panChange
//                                        }
//                                        event.changes.forEach { it.consume() }
//                                    }
//                                } while (event.changes.any { it.pressed })
//                            }
//                        }
//                        .graphicsLayer {
//                            val scale = if (isTextMode) 1f else globalScale
//                            scaleX = scale
//                            scaleY = scale
//                            translationX = if (isTextMode) 0f else globalOffset.x
//                            translationY = if (isVerticalMode || isTextMode) 0f else globalOffset.y
//                        }
                ) {
                    if (isVerticalMode) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
//                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(
                                count = totalPages,
                                key = { it }
                            ) { index ->
                                PdfPageItem(
                                    index,
                                    viewModel,
                                    true,
                                    isNightMode,
                                    isTextMode,
                                    fontSize,
                                    currentZoom = globalScale // <--- PASS THIS NEW PARAMETER

                                )
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isTextMode && globalScale <= 1f,
                            beyondViewportPageCount = 1
                        ) { index ->
                            PdfPageItem(
                                index,
                                viewModel,
                                false,
                                isNightMode,
                                isTextMode,
                                fontSize,
                                currentZoom = globalScale
                            )
                        }
                    }
                }

                SmallFloatingActionButton(
                    onClick = { showControls = !showControls },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp), // Safe area padding
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
}

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
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pageIndex, isTextMode) {
        if (isTextMode) {
            if (textContent == null) textContent = viewModel.extractText(pageIndex)
        } else {
            if (bitmap == null) {
                bitmap = viewModel.renderPage(pageIndex)
            }
        }
    }
    val selection by viewModel.selectionState.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val pageNotes = remember(allNotes, pageIndex) { allNotes.filter { it.pageIndex == pageIndex } }

    var showNoteDialog by remember { mutableStateOf(false) }
    var clickedNoteContent by remember { mutableStateOf<String?>(null) } // For showing existing note

    val clipboardManager = LocalClipboardManager.current
    var containerSize by remember { mutableStateOf(Size.Zero) }

    if (isTextMode) {
        SelectionContainer {
            Text(
                text = textContent ?: "Extracting text...",
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.5f).sp,
                color = if (isNightMode) Color.LightGray else Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            )
        }
        return
    }

    val currentBitmap = bitmap ?: run {
        Box(Modifier
            .fillMaxWidth()
            .height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Calculate Aspect Ratio of the PDF Page
    val pdfAspectRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
    var layoutSize by remember { mutableStateOf(Size.Zero) }

    // Main Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pdfAspectRatio)
            .background(if (isNightMode) Color.Black else Color.White)
            .onGloballyPositioned { layoutSize = it.size.toSize() }
            // CRITICAL FIX: Only restart gesture if pageIndex changes.
            // Do NOT put 'selection' here, or dragging will break.
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // ACCESS STATE INSIDE THE GESTURE (Live Check)
                    val currentSelection = viewModel.selectionState.value

                    val handleHit = if (currentSelection?.pageIndex == pageIndex) {
                        viewModel.checkHandleHitUI(down.position, layoutSize, currentSelection)
                    } else DragHandle.NONE

                    if (handleHit != DragHandle.NONE) {
                        // --- DRAG HANDLE ---
                        down.consume()
                        viewModel.setDraggingHandle(handleHit)

                        drag(down.id) { change ->
                            change.consume()
                            // Live update without killing gesture
                            viewModel.onDrag(change.position, layoutSize)
                        }
                        viewModel.onDragEnd()
                    } else {
                        // --- SELECTION / LONG PRESS ---
                        try {
                            withTimeout(500) {
                                waitForUpOrCancellation()
                                viewModel.clearSelection()
                            }
                        } catch (e: PointerEventTimeoutCancellationException) {
                            viewModel.onLongPress(pageIndex, down.position, layoutSize)
                            // Allow immediate drag after creation
                            drag(down.id) { change ->
                                change.consume()
                                viewModel.onDrag(change.position, layoutSize)
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

        // 2. CANVAS (Handles & Highlights)
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // A. Highlights
            pageNotes.forEach { note ->
                note.getRects().forEach { rect ->
                    drawRect(
                        color = Color(0x66FFEB3B),
                        topLeft = Offset(rect.left * w, rect.top * h),
                        size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h)
                    )
                }
            }

            // B. Selection
            if (selection?.pageIndex == pageIndex) {
                val rects = selection!!.rects
                rects.forEach { rect ->
                    drawRect(
                        color = Color(0x4D2196F3),
                        topLeft = Offset(rect.left * w, rect.top * h),
                        size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h)
                    )
                }

                // C. WATERDROP HANDLES (SCALED)
                if (rects.isNotEmpty()) {
                    // Sort rects by Y to be safe, though VM should return them sorted
                    val sortedRects = rects.sortedBy { it.top }
                    val firstLine = sortedRects.first()
                    val lastLine = sortedRects.last()

                    val baseRadius = 10.dp.toPx()
                    val scaledRadius = baseRadius / currentZoom

                    // Start Handle -> Left of First Line
                    drawWaterdropHandle(
                        x = firstLine.left * w,
                        y = firstLine.bottom * h, // Draw at bottom of line 1
                        radius = scaledRadius,
                        isLeft = true
                    )

                    // End Handle -> Right of Last Line
                    drawWaterdropHandle(
                        x = lastLine.right * w,
                        y = lastLine.bottom * h, // Draw at bottom of line N
                        radius = scaledRadius,
                        isLeft = false
                    )
                }
            }
        }

        // 3. NOTE BUTTON (SCALED)
        if (currentBitmap != null) {
            pageNotes.forEach { note ->
                val firstRect = note.getRects().firstOrNull()
                if (firstRect != null) {
                    val iconX = firstRect.left * layoutSize.width

                    // INVERSE SCALE for Offset and Size
                    val buttonSize = 24.dp / currentZoom
                    val paddingOffset = 2.dp / currentZoom // slight padding
                    val iconY = with(LocalDensity.current){(firstRect.top * layoutSize.height) - buttonSize.toPx() - paddingOffset.toPx()}

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(iconX.toInt(), iconY.toInt()) }
                            .size(buttonSize) // Scaled size
                            .shadow(1.dp, androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                            .clickable { clickedNoteContent = note.noteContent }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Note",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.padding((4.dp / currentZoom)).fillMaxSize()
                        )
                    }
                }
            }
        }

        // 4. SELECTION TOOLBAR (SCALED POSITION)
        if (selection?.pageIndex == pageIndex) {
            // ALWAYS use the FIRST rect (the top line of selection)
            val topRect = selection!!.rects.firstOrNull()

            if (topRect != null && layoutSize != Size.Zero) {
                val density = LocalDensity.current

                // Gap Calculation:
                // We want a visual gap of 60dp.
                // Because we are inside a scaled container, we must divide by zoom.
                val visualGap = 60.dp
                val scaledGapPx = with(density) { visualGap.toPx() } / currentZoom

                // Position X: Center of the TOP line
                val centerX = ((topRect.left + topRect.right) / 2 * layoutSize.width).toInt()

                // Position Y: Top of the TOP line minus gap
                val topY = (topRect.top * layoutSize.height).toInt() - scaledGapPx.toInt()

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(centerX, topY)
                ) {
                    // Inverse scale the toolbar CONTENT so it stays readable size
                    // If zoom is 2.5x, the popup context might be scaled.
                    // To be safe, we don't scale the content size, just the position.
                    // But if your Toolbar looks tiny, wrap it in Box(Modifier.scale(1f/currentZoom))

                    SelectionToolbarContent(
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(selection!!.selectedText))
                            viewModel.clearSelection()
                        },
                        onNote = { showNoteDialog = true }
                    )
                }
            }
        }
    }

    // DIALOGS
    if (showNoteDialog) {
        NoteInputDialog(
            onDismiss = { showNoteDialog = false },
            onConfirm = { text ->
                viewModel.saveCurrentSelectionAsNote(text)
                showNoteDialog = false
            }
        )
    }

    if (clickedNoteContent != null) {
        AlertDialog(
            onDismissRequest = { clickedNoteContent = null },
            title = { Text("Note") },
            text = { Text(clickedNoteContent!!) },
            confirmButton = {
                TextButton(onClick = {
                    clickedNoteContent = null
                }) { Text("Close") }
            }
        )
    }

//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .then(if (isVerticalMode) Modifier.wrapContentHeight() else Modifier.fillMaxSize())
//            .background(if (isNightMode) Color.Black else Color.White),
//        contentAlignment = Alignment.TopStart
//    ) {
//        if (isTextMode) {
//            SelectionContainer {
//                Text(
//                    text = textContent ?: "Extracting text...",
//                    fontSize = fontSize.sp,
//                    lineHeight = (fontSize * 1.5f).sp,
//                    color = if (isNightMode) Color.LightGray else Color.Black,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(24.dp)
//                        .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
//                )
//            }
//        } else {
//            val currentBitmap = bitmap
//            if (currentBitmap != null) {
//                Image(
//                    bitmap = currentBitmap.asImageBitmap(),
//                    contentDescription = null,
//                    contentScale = if (isVerticalMode) ContentScale.FillWidth else ContentScale.Fit,
//                    modifier = Modifier.fillMaxWidth(),
//                    colorFilter = if (isNightMode) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
//                        -1f, 0f, 0f, 0f, 255f,
//                        0f, -1f, 0f, 0f, 255f,
//                        0f, 0f, -1f, 0f, 255f,
//                        0f, 0f, 0f, 1f, 0f
//                    ))) else null
//                )
//            } else {
//                Box(Modifier.fillMaxWidth().height(450.dp), contentAlignment = Alignment.Center) {
//                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
//                }
//            }
//        }
//    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaterdropHandle(x: Float, y: Float, radius: Float, isLeft: Boolean) {
    val color = Color(0xFF2196F3)

    // Draw line
    drawLine(
        color = color,
        start = Offset(x, y - (radius * 1.5f)),
        end = Offset(x, y + (radius * 0.5f)),
        strokeWidth = (2.dp.toPx() / (radius / 10.dp.toPx())) // Scale stroke slightly or keep thin
    )

    // Draw Drop
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(x, y + (radius * 0.8f))
    )
}
@Composable
fun SelectionToolbarContent(onCopy: () -> Unit, onNote: () -> Unit) {
    // Centering Hack: Shift left by 50% of approximate width (60dp)
    Box(modifier = Modifier.offset(x = (-60).dp)) {
        Row(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(Icons.Default.ContentCopy, "Copy", onCopy)
            VerticalDivider(modifier = Modifier
                .height(20.dp)
                .width(1.dp), color = Color.Gray)
            ToolbarButton(Icons.Default.Edit, "Note", onNote)
        }
    }
}

@Composable
fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelLarge
        )
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
fun ReaderControls(
    visible: Boolean,
    isVertical: Boolean,
    isTextMode: Boolean,
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
                    onClick = onToggleTextMode,
                    containerColor = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        if (isTextMode) Icons.Default.Image else Icons.Default.TextFields,
                        "Text Mode"
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