package com.project.pooket.ui.features.reader

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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

    var globalScale by remember { mutableFloatStateOf(1f) }
    var globalOffset by remember { mutableStateOf(Offset.Zero) }

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

    Scaffold(
        containerColor = if (isNightMode) Color.Black else Color.White,
        topBar = { ReaderTopBar(showControls, "Pooket Reader", onBack) },
        bottomBar = {
            FontSizeControl(visible = showControls && isTextMode, fontSize = fontSize, onFontSizeChange = viewModel::setFontSize)
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
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    if (!isTextMode) {
                                        globalScale = if (globalScale > 1f) 1f else 2.5f
                                        globalOffset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(isVerticalMode, isTextMode) {
                            if (isTextMode) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    if (event.changes.size > 1 || globalScale > 1f) {
                                        globalScale = (globalScale * zoomChange).coerceIn(1f, 5f)

                                        if (isVerticalMode) {
                                            if (panChange.y != 0f) {
                                                scope.launch { listState.dispatchRawDelta(-panChange.y / globalScale) }
                                            }
                                            val extraWidth = (size.width * (globalScale - 1)) / 2
                                            val newX = (globalOffset.x + panChange.x).coerceIn(-extraWidth, extraWidth)
                                            globalOffset = Offset(newX, 0f)
                                        } else {
                                            globalOffset += panChange
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer {
                            val scale = if (isTextMode) 1f else globalScale
                            scaleX = scale
                            scaleY = scale
                            translationX = if (isTextMode) 0f else globalOffset.x
                            translationY = if (isVerticalMode || isTextMode) 0f else globalOffset.y
                        }
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
                                PdfPageItem(index, viewModel, true, isNightMode, isTextMode, fontSize)
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = !isTextMode && globalScale <= 1f,
                            beyondViewportPageCount = 1
                        ) { index ->
                            PdfPageItem(index, viewModel, false, isNightMode, isTextMode, fontSize)
                        }
                    }
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
}

@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: ReaderViewModel,
    isVerticalMode: Boolean,
    isNightMode: Boolean,
    isTextMode: Boolean,
    fontSize: Float
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isVerticalMode) Modifier.wrapContentHeight() else Modifier.fillMaxSize())
            .background(if (isNightMode) Color.Black else Color.White),
        contentAlignment = Alignment.TopStart
    ) {
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
        } else {
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = if (isVerticalMode) ContentScale.FillWidth else ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                    colorFilter = if (isNightMode) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))) else null
                )
            } else {
                Box(Modifier.fillMaxWidth().height(450.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
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
            SmallFloatingActionButton(onClick = onPrevPage, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.ChevronLeft, "Prev")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = onToggleMode) {
                    Icon(if (isVertical) Icons.Default.SwapVert else Icons.Default.ViewCarousel, "Mode")
                }
                FloatingActionButton(
                    onClick = onToggleTextMode,
                    containerColor = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(if (isTextMode) Icons.Default.Image else Icons.Default.TextFields, "Text Mode")
                }
            }

            SmallFloatingActionButton(onClick = onNextPage, containerColor = MaterialTheme.colorScheme.surface) {
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
                Text("Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
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
fun ReaderTopBar(visible: Boolean, title: String, onBack: () -> Unit) {
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