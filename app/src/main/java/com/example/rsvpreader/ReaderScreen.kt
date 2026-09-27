package com.example.rsvpreader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: String,
    store: DocumentStore,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    var document by remember(documentId) { mutableStateOf(store.getDocument(documentId)) }
    var processed by remember(documentId) { mutableStateOf<ProcessedDocument?>(null) }
    var loadError by remember(documentId) { mutableStateOf<String?>(null) }
    var currentIndex by remember(documentId) { mutableIntStateOf(document?.globalTokenIndex ?: 0) }
    var isPlaying by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var fullTextOpen by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        val text = withContext(Dispatchers.IO) { store.loadText(documentId) }
        if (text == null) {
            loadError = "저장된 문서 본문을 찾지 못했습니다."
        } else {
            val result = withContext(Dispatchers.Default) { TextProcessor.process(text) }
            if (result.tokens.isEmpty()) loadError = "읽을 수 있는 텍스트가 없습니다."
            else {
                processed = result
                currentIndex = (document?.globalTokenIndex ?: 0).coerceIn(0, result.tokens.lastIndex)
            }
        }
    }

    val data = processed
    if (data == null) {
        Scaffold(topBar = { TopAppBar(title = { Text(document?.title ?: "문서") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loadError == null) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError ?: "불러오는 중…")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("서재로") }
                }
            }
        }
        return
    }

    val safeIndex = currentIndex.coerceIn(0, data.tokens.lastIndex)
    val currentToken = data.tokens[safeIndex]
    val currentSentenceIndex = currentToken.sentenceIndex
    val progress = if (data.tokens.size <= 1) 1f else safeIndex.toFloat() / data.tokens.lastIndex.toFloat()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestIndex by rememberUpdatedState(currentIndex)

    DisposableEffect(lifecycleOwner, data, documentId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                store.updatePosition(documentId, data, latestIndex, sync = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            store.updatePosition(documentId, data, latestIndex, sync = true)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun moveTo(index: Int) {
        currentIndex = index.coerceIn(0, data.tokens.lastIndex)
    }

    LaunchedEffect(currentIndex) {
        store.updatePosition(documentId, data, currentIndex)
    }

    LaunchedEffect(isPlaying, currentIndex, settings.wpm, settings.punctuationDelay, settings.longWordDelay) {
        if (!isPlaying) return@LaunchedEffect
        val token = data.tokens[currentIndex]
        delay(delayFor(token, settings))
        if (currentIndex < data.tokens.lastIndex) currentIndex++ else isPlaying = false
    }

    BackHandler(enabled = fullTextOpen) { fullTextOpen = false }
    BackHandler(enabled = speedDialog) { speedDialog = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val shift = if (fullTextOpen) -0.18f else 0f
        val shiftPx = constraints.maxWidth.toFloat() * shift

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shiftPx }
        ) {
            TopAppBar(
                title = { Text(document?.title ?: "문서", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        store.updatePosition(documentId, data, currentIndex, sync = true)
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "서재로") }
                },
                actions = {
                    TextButton(onClick = { isPlaying = false; speedDialog = true }) {
                        Icon(Icons.Default.Speed, null)
                        Spacer(Modifier.width(4.dp))
                        Text("${settings.wpm} WPM")
                    }
                    IconButton(onClick = { isPlaying = false; fullTextOpen = true }) { Icon(Icons.Default.Subject, "전체 글") }
                }
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ContextWindow(
                    data = data,
                    sentenceIndex = currentSentenceIndex,
                    currentGlobalToken = currentIndex,
                    settings = settings,
                    onSentenceClick = { selected ->
                        val sentence = data.sentences[selected]
                        if (sentence.tokenCount > 0) moveTo(sentence.tokenStartGlobal)
                    }
                )
            }

            HorizontalDivider()
            RSVPControls(
                token = currentToken,
                settings = settings,
                isPlaying = isPlaying,
                canPrevious = currentIndex > 0,
                canNext = currentIndex < data.tokens.lastIndex,
                onPrevious = { moveTo(currentIndex - 1) },
                onPlayPause = { isPlaying = !isPlaying },
                onNext = { moveTo(currentIndex + 1) }
            )
            Text(
                "${(progress * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = fullTextOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            FullTextPanel(
                data = data,
                currentSentenceIndex = currentSentenceIndex,
                fontSize = settings.contextFontSp,
                onClose = { fullTextOpen = false },
                onSentenceClick = { sentenceIndex ->
                    val sentence = data.sentences[sentenceIndex]
                    if (sentence.tokenCount > 0) currentIndex = sentence.tokenStartGlobal
                    isPlaying = false
                    fullTextOpen = false
                },
                modifier = Modifier.width(maxWidth * 0.86f).fillMaxHeight()
            )
        }
    }

    if (speedDialog) {
        SpeedDialog(
            wpm = settings.wpm,
            onWpmChange = { onSettingsChange(settings.copy(wpm = it.coerceIn(60, 1200))) },
            onDismiss = { speedDialog = false }
        )
    }
}

private fun delayFor(token: RsvpToken, settings: AppSettings): Long {
    val base = 60_000.0 / settings.wpm.coerceIn(60, 1200)
    var multiplier = 1.0
    if (settings.punctuationDelay) {
        multiplier *= when {
            token.strongPause -> 1.65
            token.weakPause -> 1.25
            else -> 1.0
        }
    }
    if (settings.longWordDelay && token.graphemeCount >= 13) multiplier *= 1.30
    return (base * multiplier).roundToInt().toLong().coerceAtLeast(30L)
}

@Composable
private fun ContextWindow(
    data: ProcessedDocument,
    sentenceIndex: Int,
    currentGlobalToken: Int,
    settings: AppSettings,
    onSentenceClick: (Int) -> Unit
) {
    val start = (sentenceIndex - 2).coerceAtLeast(0)
    val end = (sentenceIndex + 2).coerceAtMost(data.sentences.lastIndex)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        for (i in start..end) {
            val sentence = data.sentences[i]
            val current = i == sentenceIndex
            val token = if (current) data.tokens.getOrNull(currentGlobalToken) else null
            val annotated = remember(sentence.text, token?.globalIndex, settings.focusColorArgb) {
                AnnotatedString.Builder(sentence.text).apply {
                    if (current && token != null) {
                        val localStart = (token.start - sentence.start).coerceIn(0, sentence.text.length)
                        val localEnd = (token.endExclusive - sentence.start).coerceIn(localStart, sentence.text.length)
                        if (localEnd > localStart) {
                            addStyle(
                                SpanStyle(
                                    background = settings.focusColor.copy(alpha = 0.28f),
                                    fontWeight = FontWeight.SemiBold
                                ),
                                localStart,
                                localEnd
                            )
                        }
                    }
                }.toAnnotatedString()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (current) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSentenceClick(i) }
                    .padding(horizontal = 12.dp, vertical = if (current) 10.dp else 7.dp)
            ) {
                Text(
                    annotated,
                    fontSize = settings.contextFontSp.sp,
                    lineHeight = (settings.contextFontSp * 1.45f).sp,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun RSVPControls(
    token: RsvpToken,
    settings: AppSettings,
    isPlaying: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FocusToken(token.display, settings)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = canPrevious) { Icon(Icons.Default.ArrowLeft, "이전 토큰") }
            Surface(shape = RoundedCornerShape(50), tonalElevation = 3.dp) {
                IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "일시정지" else "재생") }
            }
            IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.Default.ArrowRight, "다음 토큰") }
        }
    }
}

@Composable
private fun FocusToken(text: String, settings: AppSettings) {
    val measurer = rememberTextMeasurer()
    val focusRange = remember(text) { TextProcessor.focusGraphemeRange(text) }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val focusColor = if (settings.showFocusColor) settings.focusColor else baseColor
    val annotated = remember(text, focusRange, focusColor) {
        AnnotatedString.Builder(text).apply {
            if (focusRange != null) addStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold), focusRange.first, focusRange.last + 1)
        }.toAnnotatedString()
    }
    val result = remember(annotated, settings.rsvpFontSp, baseColor) {
        measurer.measure(
            text = annotated,
            style = TextStyle(fontSize = settings.rsvpFontSp.sp, color = baseColor, fontWeight = FontWeight.Medium)
        )
    }
    Canvas(Modifier.fillMaxWidth().height(94.dp)) {
        val focusCenter = if (focusRange != null && text.isNotEmpty()) {
            val first = result.getBoundingBox(focusRange.first)
            val lastOffset = (focusRange.last).coerceAtMost(text.lastIndex)
            val last = result.getBoundingBox(lastOffset)
            (first.left + last.right) / 2f
        } else result.size.width / 2f
        drawText(
            textLayoutResult = result,
            topLeft = Offset(size.width / 2f - focusCenter, (size.height - result.size.height) / 2f)
        )
    }
}

@Composable
private fun FullTextPanel(
    data: ProcessedDocument,
    currentSentenceIndex: Int,
    fontSize: Float,
    onClose: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentSentenceIndex) {
        listState.scrollToItem(currentSentenceIndex.coerceIn(0, data.sentences.lastIndex))
    }
    Surface(modifier = modifier, shadowElevation = 14.dp, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("전체 글", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기") }
            }
            HorizontalDivider()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(data.sentences.size, key = { it }) { i ->
                    val current = i == currentSentenceIndex
                    Text(
                        data.sentences[i].text,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (current) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent)
                            .clickable { onSentenceClick(i) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialog(wpm: Int, onWpmChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var directInput by remember { mutableStateOf(false) }
    var input by remember(wpm) { mutableStateOf(wpm.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("읽기 속도") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (directInput) {
                    TextField(
                        value = input,
                        onValueChange = { input = it.filter(Char::isDigit).take(4) },
                        label = { Text("WPM (60–1200)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        input.toIntOrNull()?.let { onWpmChange(it.coerceIn(60, 1200)); input = it.coerceIn(60, 1200).toString() }
                        directInput = false
                    }) { Text("적용") }
                } else {
                    Text(
                        "$wpm WPM",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clickable { input = wpm.toString(); directInput = true }.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onWpmChange((wpm - 10).coerceAtLeast(60)) }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Slider(
                        value = wpm.toFloat(),
                        onValueChange = { onWpmChange((it / 10).roundToInt() * 10) },
                        valueRange = 60f..1200f,
                        steps = 113,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onWpmChange((wpm + 10).coerceAtMost(1200)) }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Text("숫자를 누르면 직접 입력할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
