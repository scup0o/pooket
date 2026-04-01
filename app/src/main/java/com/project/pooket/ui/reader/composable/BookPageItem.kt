package com.project.pooket.ui.reader.composable

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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

private val PurpleHandle = Color(0xFF3A1F79)
private val PurpleSelection = Color(0x667C54D9)
private val NoteHighlight = Color(0x66FFEB3B)

private val EPUB_IMAGE_REGEX = "\\[IMAGE:(.*?)\\]".toRegex()

private sealed class EpubElement(val id: String) {
    data class TextBlock(val uid: Int, val content: AnnotatedString, val globalStartIndex: Int) : EpubElement("text_$uid")
    data class ImageBlock(val path: String, val uid: Int) : EpubElement("img_${path}_$uid")
}

private data class ProcessedEpubPage(val elements: List<EpubElement>, val textStates: Map<Int, TextFieldValue>)

private object PageContentCache {
    private data class CacheKey(val vmHash: Int, val pageIdx: Int, val fontSize: Float, val notesHash: Int)
    private val cache = object : LinkedHashMap<CacheKey, ProcessedEpubPage>(15, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ProcessedEpubPage>) = size > 15
    }
    fun get(vm: ReaderViewModel, pageIndex: Int, fontSize: Float, notes: List<NoteEntity>): ProcessedEpubPage? = synchronized(cache) {
        cache[CacheKey(System.identityHashCode(vm), pageIndex, fontSize, notes.hashCode())]
    }
    fun put(vm: ReaderViewModel, pageIndex: Int, fontSize: Float, notes: List<NoteEntity>, data: ProcessedEpubPage) = synchronized(cache) {
        cache[CacheKey(System.identityHashCode(vm), pageIndex, fontSize, notes.hashCode())] = data
    }
}

@Composable
fun BookPageItem(
    pageIndex: Int, viewModel: ReaderViewModel, isVerticalMode: Boolean, isNightMode: Boolean,
    isTextMode: Boolean, isEpub: Boolean, fontSize: Float, currentZoom: () -> Float,showControl : Boolean?=false, pageNotes: List<NoteEntity>
) {
    val context = LocalContext.current
    var clickedNoteContent by remember { mutableStateOf<String?>(null) }
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()

    val onNoteClick: (String) -> Unit = remember(context) {
        { content ->
            if (content.isNotBlank()) clickedNoteContent = content
            else Toast.makeText(context, "Note empty", Toast.LENGTH_SHORT).show()
        }
    }

    if (isEpub) EpubPage(pageIndex, viewModel, isNightMode, isVerticalMode, fontSize, allNotes, onNoteClick)
    else if (isTextMode) PdfTextPage(pageIndex, viewModel, isNightMode, isVerticalMode, fontSize, pageNotes, onNoteClick)
    else PdfImagePage(pageIndex, viewModel, isNightMode, currentZoom, pageNotes, onNoteClick)

    if (clickedNoteContent != null) {
        NoteContentDialog(content = clickedNoteContent!!) { clickedNoteContent = null }
    }
}

@Composable
private fun EpubPage(
    pageIndex: Int, viewModel: ReaderViewModel, isNightMode: Boolean, isVerticalMode: Boolean,
    fontSize: Float, allNotes: List<NoteEntity>, onNoteClick: (String) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current // ADDED: Required to clear stuck selection cursors
    val vPage = remember(pageIndex, fontSize) { viewModel.getEpubPageMetadata(pageIndex) }
    var processedData by remember(pageIndex, fontSize) {
        mutableStateOf(PageContentCache.get(viewModel, pageIndex, fontSize, allNotes))
    }

    LaunchedEffect(pageIndex, fontSize, allNotes) {
        val cached = PageContentCache.get(viewModel, pageIndex, fontSize, allNotes)
        if (cached != null) processedData = cached
        else {
            if (vPage == null) return@LaunchedEffect
            withContext(Dispatchers.Default) {
                val rawText = viewModel.getEpubPageContent(pageIndex)
                val elements = mutableListOf<EpubElement>()
                var lastIdx = 0
                var idCounter = 0
                EPUB_IMAGE_REGEX.findAll(rawText.text).forEach { match ->
                    if (match.range.first > lastIdx) elements.add(EpubElement.TextBlock(idCounter++, rawText.subSequence(lastIdx, match.range.first), lastIdx))
                    elements.add(EpubElement.ImageBlock(match.groupValues[1], idCounter++))
                    lastIdx = match.range.last + 1
                }
                if (lastIdx < rawText.length) elements.add(EpubElement.TextBlock(idCounter++, rawText.subSequence(lastIdx, rawText.length), lastIdx))

                val pageStart = vPage.startOffset
                val pageEnd = vPage.endOffset
                val visibleNotes = allNotes.filter { it.pageIndex == vPage.chapterIndex && maxOf(it.textRangeStart ?: -1, pageStart) < minOf(it.textRangeEnd ?: -1, pageEnd) }

                val statesMap = elements.filterIsInstance<EpubElement.TextBlock>().associate { element ->
                    val builder = AnnotatedString.Builder(element.content)
                    val blockAbsStart = pageStart + element.globalStartIndex
                    val blockAbsEnd = blockAbsStart + element.content.length
                    visibleNotes.forEach { note ->
                        val nStart = note.textRangeStart ?: 0
                        val nEnd = note.textRangeEnd ?: 0
                        val iStart = maxOf(blockAbsStart, nStart)
                        val iEnd = minOf(blockAbsEnd, nEnd)
                        if (iStart < iEnd) builder.addStyle(SpanStyle(background = NoteHighlight), iStart - blockAbsStart, iEnd - blockAbsStart)
                    }
                    element.uid to TextFieldValue(builder.toAnnotatedString())
                }
                val result = ProcessedEpubPage(elements, statesMap)
                PageContentCache.put(viewModel, pageIndex, fontSize, allNotes, result)
                withContext(Dispatchers.Main) { processedData = result }
            }
        }
    }

    val epubImages by viewModel.epubImages.collectAsStateWithLifecycle(initialValue = emptyMap())
    val globalTextSelection by viewModel.textSelection.collectAsStateWithLifecycle()

    if (processedData == null) {
        Box(Modifier
            .fillMaxWidth()
            .height(LocalConfiguration.current.screenHeightDp.dp - 100.dp))
        return
    }

    val data = processedData!!
    val textStates = remember(data) { mutableStateMapOf<Int, TextFieldValue>().apply { putAll(data.textStates) } }
    var activeBlockId by remember { mutableStateOf<Int?>(null) }

    val onTextChangeHandler = remember(pageIndex, allNotes, vPage, textStates) {
        { uid: Int, nv: TextFieldValue, el: EpubElement ->
            textStates[uid] = nv
            if (!nv.selection.collapsed) {
                if (activeBlockId != uid) {
                    activeBlockId?.let { id -> textStates[id] = textStates[id]?.copy(selection = TextRange.Zero) ?: TextFieldValue() }
                    activeBlockId = uid
                }
                viewModel.setTextSelection(pageIndex, nv.text.substring(nv.selection.min, nv.selection.max), nv.selection)
            } else {
                val cursorAbs = (vPage?.startOffset ?: 0) + nv.selection.start + (el as EpubElement.TextBlock).globalStartIndex
                allNotes.find { it.pageIndex == vPage?.chapterIndex && cursorAbs in (it.textRangeStart ?: -1) until (it.textRangeEnd ?: -1) }?.let {
                    onNoteClick(it.noteContent); viewModel.clearAllSelection()
                } ?: if (activeBlockId == uid) viewModel.clearAllSelection() else {}
            }
        }
    }

    LaunchedEffect(globalTextSelection, textStates) {
        if (globalTextSelection == null) {
            focusManager.clearFocus()
            activeBlockId?.let { id -> textStates[id] = textStates[id]?.copy(selection = TextRange.Zero) ?: TextFieldValue() }
            activeBlockId = null
        }
    }

    val selectionColors = remember { TextSelectionColors(handleColor = PurpleHandle, backgroundColor = PurpleSelection) }
    CompositionLocalProvider(LocalTextToolbar provides object : androidx.compose.ui.platform.TextToolbar {
        override val status = androidx.compose.ui.platform.TextToolbarStatus.Hidden
        override fun hide() {}
        override fun showMenu(r: Rect, copy: (() -> Unit)?, p: (() -> Unit)?, cu: (() -> Unit)?, s: (() -> Unit)?) {}
    }, LocalTextSelectionColors provides selectionColors) {

        val pageModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); viewModel.clearAllSelection() } }

        if (!isVerticalMode) {
            LazyColumn(modifier = pageModifier, contentPadding = PaddingValues(top = 24.dp)) {
                items(items = data.elements, key = { it.id }) { element ->
                    EpubElementItem(element, fontSize, isNightMode, textStates[if (element is EpubElement.TextBlock) element.uid else -1], epubImages, ) { uid, nv -> onTextChangeHandler(uid, nv, element) }
                }
                item{
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        } else {
            Column(modifier = pageModifier.padding(vertical = 24.dp)) {
                data.elements.forEach { element ->
                    EpubElementItem(element, fontSize, isNightMode, textStates[if (element is EpubElement.TextBlock) element.uid else -1], epubImages) { uid, nv -> onTextChangeHandler(uid, nv, element) }
                }
            }
        }
    }
}

@Composable
private fun EpubElementItem( el: EpubElement, fs: Float, isNm: Boolean, ts: TextFieldValue?, imgs: Map<String, Bitmap>, onTc: (Int, TextFieldValue) -> Unit) {
    when (el) {
        is EpubElement.TextBlock -> {
            BasicTextField(
                value = ts ?: TextFieldValue(),
                onValueChange = { onTc(el.uid, it) },
                readOnly = true,
                textStyle = TextStyle(
                    fontSize = fs.sp,
                    lineHeight = (fs * 1.5).sp,
                    color = if (isNm) Color(0xFFD0D0D0) else Color.Black,
                    textAlign = TextAlign.Justify,
                    fontFamily = FontFamily.Serif
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }
        is EpubElement.ImageBlock -> imgs[el.path]?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)) }
    }
}

@Composable
private fun PdfImagePage(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isNightMode: Boolean,
    currentZoom: () -> Float,
    pageNotes: List<NoteEntity>,
    onNoteClick: (String) -> Unit
) {
    val bitmap by produceState<Bitmap?>(null, pageIndex) {
        value = viewModel.renderPage(pageIndex, isThumbnail = true)

        kotlinx.coroutines.delay(150)

        value = viewModel.renderPage(pageIndex, isThumbnail = false)
    }

    if (bitmap == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(if (isNightMode) Color.Black else Color.White)
        )
        return
    }
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val noteRectsMap by produceState<Map<Long, List<NormRect>>>(emptyMap(), pageNotes) {
        value = withContext(Dispatchers.Default) { pageNotes.associate { it.id to viewModel.getRectsForNote(it) } }
    }

    val currentNotes by rememberUpdatedState(pageNotes)
    val currentRectsMap by rememberUpdatedState(noteRectsMap)

    if (bitmap == null) {
        Box(Modifier
            .fillMaxWidth()
            .height(400.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val currentBitmap = bitmap!!
    var layoutSize by remember { mutableStateOf(Size.Zero) }
    val colorFilter = remember(isNightMode) { if (isNightMode) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))) else null }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height.toFloat())
            .background(if (isNightMode) Color.Black else Color.White)
            .onGloballyPositioned { layoutSize = it.size.toSize() }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    noteRectsMap.forEach { (_, rects) ->
                        rects.forEach { r ->
                            drawRect(
                                NoteHighlight,
                                Offset(r.left * w, r.top * h),
                                Size((r.right - r.left) * w, (r.bottom - r.top) * h)
                            )
                        }
                    }
                    selectionState?.takeIf { it.pageIndex == pageIndex }?.let { sel ->
                        sel.rects.forEach { r ->
                            drawRect(
                                PurpleSelection,
                                Offset(r.left * w, r.top * h),
                                Size((r.right - r.left) * w, (r.bottom - r.top) * h)
                            )
                        }
                        if (sel.rects.isNotEmpty()) {
                            val sorted = sel.rects.sortedBy { it.top }
                            val rad = 9.dp.toPx() / currentZoom()
                            drawAndroidSelectionHandle(
                                sorted.first().left * w,
                                sorted.first().bottom * h,
                                rad,
                                true,
                                PurpleHandle
                            )
                            drawAndroidSelectionHandle(
                                sorted.last().right * w,
                                sorted.last().bottom * h,
                                rad,
                                false,
                                PurpleHandle
                            )
                        }
                    }
                }
            }
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
                                    val note = currentNotes.find { n ->
                                        currentRectsMap[n.id]?.any { r ->
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

@Composable
private fun PdfTextPage(
    pageIndex: Int, viewModel: ReaderViewModel, isNightMode: Boolean, isVerticalMode: Boolean,
    fontSize: Float, pageNotes: List<NoteEntity>, onNoteClick: (String) -> Unit
) {
    var textContent by remember { mutableStateOf<String?>(null) }
    var textFieldValue by remember(pageIndex) { mutableStateOf(TextFieldValue()) }
    var resolvedNoteBounds by remember { mutableStateOf<List<Pair<Pair<Int, Int>, NoteEntity>>>(emptyList()) }
    val textSelection by viewModel.textSelection.collectAsStateWithLifecycle()

    LaunchedEffect(textSelection) { if (textSelection == null && !textFieldValue.selection.collapsed) textFieldValue = textFieldValue.copy(selection = TextRange.Zero) }
    LaunchedEffect(pageIndex, pageNotes) {
        if (textContent == null) textContent = viewModel.extractText(pageIndex)
        textContent?.let { raw ->
            val annotated = withContext(Dispatchers.Default) {
                resolvedNoteBounds = pageNotes.mapNotNull { n -> viewModel.getNoteTextBounds(raw, n)?.let { it to n } }
                viewModel.processTextHighlights(raw, pageNotes)
            }
            textFieldValue = if (textFieldValue.text != raw) TextFieldValue(annotated) else textFieldValue.copy(annotatedString = annotated)
        }
    }

    val selectionColors = remember { TextSelectionColors(handleColor = PurpleHandle, backgroundColor = PurpleSelection) }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors, LocalTextToolbar provides object : androidx.compose.ui.platform.TextToolbar {
        override val status = androidx.compose.ui.platform.TextToolbarStatus.Hidden
        override fun hide() {}
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {}
    }) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .then(if (!isVerticalMode) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            if (textContent == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else BasicTextField(
                value = textFieldValue,
                onValueChange = { nv ->
                    if (nv.text == textFieldValue.text) {
                        textFieldValue = nv
                        if (nv.selection.collapsed) {
                            val charIdx = nv.selection.start
                            resolvedNoteBounds.find { charIdx >= it.first.first && charIdx < it.first.second }?.second?.let { onNoteClick(it.noteContent) }
                            viewModel.clearAllSelection()
                        } else viewModel.setTextSelection(pageIndex, nv.text.substring(nv.selection.min, nv.selection.max), nv.selection)
                    }
                },
                readOnly = true,
                textStyle = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.5).sp, color = if (isNightMode) Color.LightGray else Color.Black, textAlign = TextAlign.Justify),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


fun DrawScope.drawAndroidSelectionHandle(x: Float, y: Float, radius: Float, isLeft: Boolean, color: Color) {
    val path = Path().apply {
        if (isLeft) {
            val cx = x - radius; val cy = y + radius
            moveTo(x, y); lineTo(x, y + radius)
            arcTo(Rect(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 270f, false)
            lineTo(x, y)
        } else {
            val cx = x + radius; val cy = y + radius
            moveTo(x, y); lineTo(x, y + radius)
            arcTo(Rect(cx - radius, cy - radius, cx + radius, cy + radius), 180f, -270f, false)
            lineTo(x, y)
        }
    }
    drawPath(path, color)
}

@Composable fun Dp.toPx(density: Density) = with(density) { this@toPx.toPx() }