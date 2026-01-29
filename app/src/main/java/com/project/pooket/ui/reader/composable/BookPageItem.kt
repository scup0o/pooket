package com.project.pooket.ui.reader.composable

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString // [EDITED]
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.pooket.data.local.note.NormRect
import com.project.pooket.data.local.note.NoteEntity
import com.project.pooket.ui.reader.DragHandle
import com.project.pooket.ui.reader.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookPageItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isVerticalMode: Boolean,
    isNightMode: Boolean,
    isTextMode: Boolean,
    isEpub: Boolean,
    fontSize: Float,
    currentZoom: Float,
    pageNotes: List<NoteEntity>,
) {
    var clickedNoteContent by remember { mutableStateOf<String?>(null) }

    if (isEpub) {
        EpubPage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            isVerticalMode = isVerticalMode,
            fontSize = fontSize,
            pageNotes = pageNotes,
            onNoteClick = { clickedNoteContent = it }
        )
    } else if (isTextMode) {
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

@Composable
private fun EpubPage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isNightMode: Boolean,
    isVerticalMode: Boolean,
    fontSize: Float,
    pageNotes: List<NoteEntity>,
    onNoteClick: (String) -> Unit
) {
    val pageContent by produceState<AnnotatedString?>(initialValue = null, key1 = pageIndex, key2 = fontSize) {
        value = viewModel.getEpubPageContent(pageIndex)
    }

    if (pageContent == null) {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val rawText = pageContent!!
    var textFieldValue by remember(rawText) { mutableStateOf(TextFieldValue(rawText)) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val visualizedText = remember(rawText, pageNotes, isNightMode) {
        buildAnnotatedString {
            append(rawText)
            pageNotes.forEach { note ->
                val start = note.textRangeStart ?: 0
                val end = note.textRangeEnd ?: 0
                val len = rawText.length
                if (start < len && end > 0) {
                    addStyle(
                        SpanStyle(background = Color(0x66FFEB3B)),
                        start.coerceAtLeast(0),
                        end.coerceAtMost(len)
                    )
                }
            }
        }
    }

    LaunchedEffect(visualizedText) {
        if (textFieldValue.annotatedString.text == visualizedText.text) {
            textFieldValue = textFieldValue.copy(annotatedString = visualizedText)
        }
    }

    val customToolbar = remember {
        CustomTextToolbar(
            onShowMenu = {
                val sel = textFieldValue.selection
                if (!sel.collapsed) {
                    try {
                        val selectedText = rawText.text.substring(sel.start, sel.end)
                        viewModel.setTextSelection(pageIndex, selectedText, sel)
                    } catch (_: Exception) {}
                }
            },
            onHideMenu = { },
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
                .pointerInput(Unit) { detectTapGestures { viewModel.clearAllSelection() } }
                .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    if (it.text == textFieldValue.text) {
                        textFieldValue = it
                        if (it.selection.collapsed) viewModel.clearAllSelection()
                    }
                },
                readOnly = true,
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp,
                    color = if (isNightMode) Color(0xFFD0D0D0) else Color.Black,
                    textAlign = TextAlign.Justify,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                ),
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { layoutResult = it }
            )

            if (layoutResult != null) {
                pageNotes.forEach { note ->
                    note.textRangeStart?.let { start ->
                        if (start < layoutResult!!.layoutInput.text.length) {
                            val bounds = layoutResult!!.getBoundingBox(start)
                            val iconX = bounds.left
                            val iconY = bounds.top - 24.dp.toPx(LocalDensity.current)
                            NoteIcon(iconX, iconY, 24.dp) { onNoteClick(note.noteContent) }
                        }
                    }
                }
            }
        }
    }
}

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

    val customToolbar = remember {
        CustomTextToolbar(
            onShowMenu = {
                val sel = textFieldValue.selection
                if (!sel.collapsed && textContent != null) {
                    try {
                        val selectedText = textContent!!.substring(sel.start, sel.end)
                        viewModel.setTextSelection(pageIndex, selectedText, sel)
                    } catch (_: Exception) {
                    }
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
                .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            if (textContent == null) {
                Box(Modifier
                    .fillMaxWidth()
                    .height(400.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        if (it.text == textFieldValue.text) {
                            textFieldValue = it
                            if (it.selection.collapsed) {
                                viewModel.clearAllSelection()
                            }
                        }
                    }, readOnly = true,
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

                if (layoutResult != null) {
                    val textLen = layoutResult!!.layoutInput.text.text.length

                    pageNotes.forEach { note ->
                        note.textRangeStart?.let { start ->
                            if (textLen > 0 && start < textLen) {
                                val bounds = layoutResult!!.getBoundingBox(start)
                                val iconX = bounds.left
                                val iconY = bounds.top - 24.dp.toPx(LocalDensity.current)
                                NoteIcon(
                                    iconX,
                                    iconY,
                                    24.dp,
                                    onClick = { onNoteClick(note.noteContent) })
                            }
                        }
                    }
                }
            }
        }
    }
}


// [KEPT ORIGINAL]
@Composable
private fun PdfImagePage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isNightMode: Boolean,
    currentZoom: Float,
    pageNotes: List<NoteEntity>,
    onNoteClick: (String) -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = viewModel.renderPage(pageIndex)
    }

    val noteRectsMap by produceState<Map<Long, List<NormRect>>>(
        initialValue = emptyMap(),
        key1 = pageNotes
    ) {
        val newMap = withContext(Dispatchers.Default) {
            val map = mutableMapOf<Long, List<NormRect>>()
            pageNotes.forEach { note ->
                map[note.id] = viewModel.getRectsForNote(note)
            }
            map
        }
        value = newMap
    }

    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()

    if (bitmap == null) {
        Box(Modifier
            .fillMaxWidth()
            .height(400.dp), contentAlignment = Alignment.Center) {
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
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
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
                                if (change.changedToUp()) {
                                    change.consume(); break
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
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height

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

                        drawAndroidSelectionHandle(
                            first.left * w,
                            first.bottom * h,
                            scaledRadius,
                            true
                        )
                        drawAndroidSelectionHandle(
                            last.right * w,
                            last.bottom * h,
                            scaledRadius,
                            false
                        )
                    }
                }
            }
    ) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
            colorFilter = if (isNightMode) ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            ) else null
        )

        if (layoutSize != Size.Zero) {
            pageNotes.forEach { note ->
                val rects = noteRectsMap[note.id] ?: emptyList()
                val first = rects.firstOrNull()
                if (first != null) {
                    val iconX = first.left * layoutSize.width
                    val baseSize = 24.dp
                    val scaledSize = baseSize / currentZoom
                    val padding = 2.dp / currentZoom
                    val iconY =
                        (first.top * layoutSize.height) - scaledSize.toPx(LocalDensity.current) - padding.toPx(
                            LocalDensity.current
                        )
                    NoteIcon(
                        iconX,
                        iconY,
                        scaledSize,
                        4.dp / currentZoom
                    ) { onNoteClick(note.noteContent) }
                }
            }
        }
    }
}


fun DrawScope.drawAndroidSelectionHandle(
    x: Float,
    y: Float,
    radius: Float,
    isLeft: Boolean
) {
    val handleColor = Color(0xFF2196F3)
    val path = Path()

    if (isLeft) {
        val centerX = x - radius
        val centerY = y + radius

        path.moveTo(x, y)
        path.lineTo(x, y + radius)
        path.arcTo(
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
        path.lineTo(x, y)
    } else {
        val centerX = x + radius
        val centerY = y + radius

        path.moveTo(x, y)
        path.lineTo(x, y + radius)
        path.arcTo(
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
        path.lineTo(x, y)
    }

    drawPath(path, handleColor)
}

@Composable
fun Dp.toPx(density: Density) = with(density) { this@toPx.toPx() }