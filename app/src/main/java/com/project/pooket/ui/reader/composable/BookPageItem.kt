package com.project.pooket.ui.reader.composable

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.withTimeout

// Define EpubElement outside or at the top level
private sealed class EpubElement(val id: String) {
    data class TextBlock(val uid: Int, val content: AnnotatedString, val globalStartIndex: Int) :
        EpubElement("text_$uid")

    data class ImageBlock(val path: String, val uid: Int) : EpubElement("img_${path}_$uid")
}

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
    // Memoize the callback to prevent unnecessary recompositions of children
    val onNoteClick: (String) -> Unit = remember { { clickedNoteContent = it } }

    if (isEpub) {
        EpubPage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            isVerticalMode = isVerticalMode,
            fontSize = fontSize,
            pageNotes = pageNotes,
            onNoteClick = onNoteClick
        )
    } else if (isTextMode) {
        PdfTextPage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            isVerticalMode = isVerticalMode,
            fontSize = fontSize,
            pageNotes = pageNotes,
            onNoteClick = onNoteClick
        )
    } else {
        PdfImagePage(
            pageIndex = pageIndex,
            viewModel = viewModel,
            isNightMode = isNightMode,
            currentZoom = currentZoom,
            pageNotes = pageNotes,
            onNoteClick = onNoteClick
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
    val pageContent by produceState<AnnotatedString?>(
        initialValue = null,
        key1 = pageIndex,
        key2 = fontSize
    ) {
        value = viewModel.getEpubPageContent(pageIndex)
    }

    val epubImages by viewModel.epubImages.collectAsStateWithLifecycle(initialValue = emptyMap())
    val globalTextSelection by viewModel.textSelection.collectAsStateWithLifecycle()

    if (pageContent == null) {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val rawText = pageContent!!

    val processedData by produceState<Pair<List<EpubElement>, Map<Int, TextFieldValue>>>(
        initialValue = Pair(emptyList(), emptyMap()),
        key1 = rawText,
        key2 = pageNotes
    ) {
        withContext(Dispatchers.Default) {
            val elements = mutableListOf<EpubElement>()
            val textStr = rawText.text
            val imageRegex = "\\[IMAGE:(.*?)\\]".toRegex()
            var lastIndex = 0
            var blockIdCounter = 0

            imageRegex.findAll(textStr).forEach { match ->
                val rangeStart = match.range.first
                if (rangeStart > lastIndex) {
                    elements.add(
                        EpubElement.TextBlock(
                            blockIdCounter++,
                            rawText.subSequence(lastIndex, rangeStart),
                            lastIndex
                        )
                    )
                }
                elements.add(EpubElement.ImageBlock(match.groupValues[1], blockIdCounter++))
                lastIndex = match.range.last + 1
            }
            if (lastIndex < textStr.length) {
                elements.add(
                    EpubElement.TextBlock(
                        blockIdCounter++,
                        rawText.subSequence(lastIndex, textStr.length),
                        lastIndex
                    )
                )
            }

            val map = mutableMapOf<Int, TextFieldValue>()
            elements.filterIsInstance<EpubElement.TextBlock>().forEach { element ->
                val builder = AnnotatedString.Builder(element.content)
                val gStart = element.globalStartIndex
                val gEnd = gStart + element.content.length

                for (note in pageNotes) {
                    val nStart = note.textRangeStart ?: 0
                    val nEnd = note.textRangeEnd ?: 0
                    if (nEnd <= gStart || nStart >= gEnd) continue

                    val intersectStart = maxOf(gStart, nStart)
                    val intersectEnd = minOf(gEnd, nEnd)
                    if (intersectStart < intersectEnd) {
                        builder.addStyle(
                            SpanStyle(background = Color(0x66FFEB3B)),
                            intersectStart - gStart,
                            intersectEnd - gStart
                        )
                    }
                }
                map[element.uid] = TextFieldValue(builder.toAnnotatedString())
            }
            value = Pair(elements, map)
        }
    }

    val pageElements = processedData.first
    val textStates = remember(processedData.second) { mutableStateMapOf<Int, TextFieldValue>().apply { putAll(processedData.second) } }

    var activeBlockId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(globalTextSelection) {
        if (globalTextSelection == null) {
            textStates.keys.forEach { key ->
                val s = textStates[key]
                if (s != null && !s.selection.collapsed) {
                    textStates[key] = s.copy(selection = TextRange.Zero)
                }
            }
            activeBlockId = null
        }
    }

    val customToolbar = remember(activeBlockId) {
        CustomTextToolbar(
            onShowMenu = {
                activeBlockId?.let { id ->
                    val state = textStates[id] ?: return@let
                    if (!state.selection.collapsed) {
                        val txt = state.text.substring(state.selection.min, state.selection.max)
                        viewModel.setTextSelection(pageIndex, txt, state.selection)
                    }
                }
            },
            onHideMenu = { },
            onCopy = { viewModel.clearAllSelection() }
        )
    }

    val selectionColors = remember {
        TextSelectionColors(handleColor = Color(0xFF2196F3), backgroundColor = Color(0x662196F3))
    }

    CompositionLocalProvider(
        LocalTextToolbar provides customToolbar,
        LocalTextSelectionColors provides selectionColors
    ) {
        if (!isVerticalMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) { detectTapGestures { viewModel.clearAllSelection() } },
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                items(items = pageElements, key = { it.id }) { element ->
                    EpubElementItem(
                        element = element,
                        fontSize = fontSize,
                        isNightMode = isNightMode,
                        textState = textStates[if (element is EpubElement.TextBlock) element.uid else -1],
                        epubImages = epubImages,
                        onTextChange = { uid, newValue ->
                            textStates[uid] = newValue
                            if (!newValue.selection.collapsed) {
                                if (activeBlockId != uid) {
                                    activeBlockId?.let { oldId ->
                                        textStates[oldId] = textStates[oldId]?.copy(selection = TextRange.Zero) ?: TextFieldValue()
                                    }
                                    activeBlockId = uid
                                }

                                val txt = newValue.text.substring(newValue.selection.min, newValue.selection.max)

                                viewModel.setTextSelection(pageIndex, txt, newValue.selection)
                            } else {
                                val localCursor = newValue.selection.start
                                val globalCursor = localCursor + (element as EpubElement.TextBlock).globalStartIndex
                                val hitNote = pageNotes.find { note ->
                                    globalCursor in (note.textRangeStart ?: -1) until (note.textRangeEnd ?: -1)
                                }
                                if (hitNote != null) {
                                    onNoteClick(hitNote.noteContent)
                                    viewModel.clearAllSelection()
                                } else if (activeBlockId == uid) {
                                    viewModel.clearAllSelection()
                                }
                            }
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .pointerInput(Unit) { detectTapGestures { viewModel.clearAllSelection() } }
            ) {
                pageElements.forEach { element ->
                    EpubElementItem(
                        element = element,
                        fontSize = fontSize,
                        isNightMode = isNightMode,
                        textState = textStates[if (element is EpubElement.TextBlock) element.uid else -1],
                        epubImages = epubImages,
                        onTextChange = { uid, newValue ->
                            textStates[uid] = newValue
                            if (!newValue.selection.collapsed) {
                                if (activeBlockId != uid) {
                                    activeBlockId?.let { oldId ->
                                        textStates[oldId] = textStates[oldId]?.copy(selection = TextRange.Zero) ?: TextFieldValue()
                                    }
                                    activeBlockId = uid
                                }
                                val txt = newValue.text.substring(newValue.selection.min, newValue.selection.max)
                                viewModel.setTextSelection(pageIndex, txt, newValue.selection)
                            } else {
                                val localCursor = newValue.selection.start
                                val globalCursor = localCursor + (element as EpubElement.TextBlock).globalStartIndex
                                val hitNote = pageNotes.find { note ->
                                    globalCursor in (note.textRangeStart ?: -1) until (note.textRangeEnd ?: -1)
                                }
                                if (hitNote != null) {
                                    onNoteClick(hitNote.noteContent)
                                    viewModel.clearAllSelection()
                                } else if (activeBlockId == uid) {
                                    viewModel.clearAllSelection()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpubElementItem(
    element: EpubElement,
    fontSize: Float,
    isNightMode: Boolean,
    textState: TextFieldValue?,
    epubImages: Map<String, Bitmap>,
    onTextChange: (Int, TextFieldValue) -> Unit
) {
    when (element) {
        is EpubElement.TextBlock -> {
            BasicTextField(
                value = textState ?: TextFieldValue(),
                onValueChange = { onTextChange(element.uid, it) },
                readOnly = true,
                textStyle = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5).sp,
                    color = if (isNightMode) Color(0xFFD0D0D0) else Color.Black,
                    textAlign = TextAlign.Justify,
                    fontFamily = FontFamily.Serif
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        is EpubElement.ImageBlock -> {
            epubImages[element.path]?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
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
            val annotated = withContext(Dispatchers.Default) {
                viewModel.processTextHighlights(raw, pageNotes)
            }
            if (textFieldValue.text != raw) textFieldValue = TextFieldValue(annotated)
            else textFieldValue = textFieldValue.copy(annotatedString = annotated)
        }
    }

    val selectionColors = remember {
        TextSelectionColors(handleColor = Color(0xFF2196F3), backgroundColor = Color(0x662196F3))
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            if (textContent == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        if (it.text == textFieldValue.text) {
                            textFieldValue = it
                            if (it.selection.collapsed) {
                                layoutResult?.let { res ->
                                    val charIndex = it.selection.start
                                    val note = pageNotes.find { n ->
                                        charIndex in (n.textRangeStart ?: -1)..(n.textRangeEnd ?: -1)
                                    }
                                    if (note != null) onNoteClick(note.noteContent)
                                }
                                viewModel.clearAllSelection()
                            } else {
                                val txt = it.text.substring(it.selection.min, it.selection.max)
                                viewModel.setTextSelection(pageIndex, txt, it.selection)
                            }
                        }
                    },
                    readOnly = true,
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5).sp,
                        color = if (isNightMode) Color.LightGray else Color.Black,
                        textAlign = TextAlign.Justify
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { layoutResult = it }
                )
            }
        }
    }
}

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

    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()

    val noteRectsMap by produceState<Map<Long, List<NormRect>>>(
        initialValue = emptyMap(),
        key1 = pageNotes
    ) {
        withContext(Dispatchers.Default) {
            val map = mutableMapOf<Long, List<NormRect>>()
            pageNotes.forEach { map[it.id] = viewModel.getRectsForNote(it) }
            value = map
        }
    }

    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBitmap = bitmap!!
    var layoutSize by remember { mutableStateOf(Size.Zero) }

    val colorFilter = remember(isNightMode) {
        if (isNightMode) ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        ) else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height.toFloat())
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
                            withTimeout(500) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    val nx = up.position.x / layoutSize.width
                                    val ny = up.position.y / layoutSize.height
                                    val note = pageNotes.find { n ->
                                        noteRectsMap[n.id]?.any { r ->
                                            nx in r.left..r.right && ny in r.top..r.bottom
                                        } == true
                                    }
                                    if (note != null) onNoteClick(note.noteContent)
                                    else viewModel.clearAllSelection()
                                }
                            }
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

                noteRectsMap.forEach { (_, rects) ->
                    rects.forEach { r ->
                        drawRect(
                            Color(0x66FFEB3B),
                            Offset(r.left * w, r.top * h),
                            Size((r.right - r.left) * w, (r.bottom - r.top) * h)
                        )
                    }
                }

                val sel = selectionState
                if (sel?.pageIndex == pageIndex) {
                    sel.rects.forEach { r ->
                        drawRect(
                            Color(0x4D2196F3),
                            Offset(r.left * w, r.top * h),
                            Size((r.right - r.left) * w, (r.bottom - r.top) * h)
                        )
                    }
                    if (sel.rects.isNotEmpty()) {
                        val sorted = sel.rects.sortedBy { it.top }
                        val scaledRadius = 9.dp.toPx() / currentZoom
                        drawAndroidSelectionHandle(
                            sorted.first().left * w,
                            sorted.first().bottom * h,
                            scaledRadius,
                            true
                        )
                        drawAndroidSelectionHandle(
                            sorted.last().right * w,
                            sorted.last().bottom * h,
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
            colorFilter = colorFilter
        )
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