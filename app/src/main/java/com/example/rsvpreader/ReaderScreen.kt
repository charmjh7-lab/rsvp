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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: String,
    store: DocumentStore,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    var document by remember(documentId) { mutableStateOf(store.getDocument(documentId)) }
    var processed by remember(documentId) { mutableStateOf<ProcessedDocument?>(null) }
    var loadError by remember(documentId) { mutableStateOf<String?>(null) }
    var currentIndex by remember(documentId) { mutableIntStateOf(document?.globalTokenIndex ?: 0) }
    var isPlaying by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var fullTextOpen by remember { mutableStateOf(false) }
    var fullTextWasPlaying by remember { mutableStateOf(false) }
    var ttsMode by remember(documentId) { mutableStateOf(settings.lastTtsMode) }
    var ttsReady by remember(documentId) { mutableStateOf(false) }
    var ttsNotice by remember(documentId) { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current.applicationContext
    val ttsController = remember(documentId) { SystemTtsController(appContext) }

    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }

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
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestTtsMode by rememberUpdatedState(ttsMode)

    fun startTtsAt(index: Int): Boolean = speakTtsFromIndex(ttsController, data, index)

    DisposableEffect(ttsController, data) {
        ttsController.onRangeStart = { sentenceIndex, globalStart, globalEnd ->
            if (latestTtsMode && latestIsPlaying) {
                tokenIndexForTtsRange(data, sentenceIndex, globalStart, globalEnd)?.let {
                    currentIndex = it
                }
            }
        }
        ttsController.onDone = { sentenceIndex ->
            if (latestTtsMode && latestIsPlaying) {
                val nextSentence = data.sentences.getOrNull(sentenceIndex + 1)
                if (nextSentence != null && nextSentence.tokenCount > 0) {
                    val nextIndex = nextSentence.tokenStartGlobal
                    currentIndex = nextIndex
                    if (!startTtsAt(nextIndex)) {
                        isPlaying = false
                        ttsNotice = "다음 문장을 TTS로 재생하지 못했습니다."
                    }
                } else {
                    isPlaying = false
                }
            }
        }
        ttsController.onError = { message ->
            isPlaying = false
            ttsNotice = message
        }
        onDispose {
            ttsController.onRangeStart = null
            ttsController.onDone = null
            ttsController.onError = null
        }
    }

    LaunchedEffect(ttsMode, settings.ttsEnginePackage, settings.ttsVoiceName) {
        if (ttsMode) {
            ttsReady = false
            ttsController.initialize(
                enginePackage = settings.ttsEnginePackage,
                voiceName = settings.ttsVoiceName,
                speechRate = settings.ttsRate,
                onReady = { info ->
                    ttsReady = true
                    if (info.fellBackToDefault && settings.ttsEnginePackage != null) {
                        onSettingsChange(settings.copy(ttsEnginePackage = null, ttsVoiceName = null))
                    } else if (settings.ttsVoiceName != null && !info.selectedVoiceAvailable) {
                        onSettingsChange(settings.copy(ttsVoiceName = null))
                    }
                },
                onMessage = { ttsNotice = it }
            )
        } else {
            ttsReady = false
            ttsController.stop()
        }
    }

    LaunchedEffect(ttsMode, settings.ttsRate) {
        if (ttsMode) ttsController.setSpeechRate(settings.ttsRate)
    }

    DisposableEffect(lifecycleOwner, data, documentId, ttsController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isPlaying = false
                    ttsController.stop()
                    store.updatePosition(documentId, data, latestIndex, sync = true)
                }
                Lifecycle.Event.ON_STOP -> {
                    store.updatePosition(documentId, data, latestIndex, sync = true)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ttsController.stop()
            store.updatePosition(documentId, data, latestIndex, sync = true)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun moveTo(index: Int) {
        val target = index.coerceIn(0, data.tokens.lastIndex)
        val continueTts = ttsMode && isPlaying
        if (continueTts) ttsController.stop()
        currentIndex = target
        if (continueTts && !startTtsAt(target)) {
            isPlaying = false
            ttsNotice = "이 위치부터 TTS를 다시 시작하지 못했습니다."
        }
    }

    fun moveToAndPlay(index: Int) {
        val target = index.coerceIn(0, data.tokens.lastIndex)
        ttsController.stop()
        currentIndex = target
        if (ttsMode) {
            if (!ttsReady) {
                isPlaying = false
                ttsNotice = "TTS를 준비하는 중이거나 사용할 수 없습니다."
            } else if (startTtsAt(target)) {
                ttsNotice = null
                isPlaying = true
            } else {
                isPlaying = false
                ttsNotice = "현재 위치에서 TTS를 시작하지 못했습니다."
            }
        } else {
            isPlaying = true
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            isPlaying = false
            ttsController.stop()
        } else if (ttsMode) {
            if (!ttsReady) {
                ttsNotice = "TTS를 준비하는 중이거나 사용할 수 없습니다."
            } else if (startTtsAt(currentIndex)) {
                ttsNotice = null
                isPlaying = true
            } else {
                ttsNotice = "현재 위치에서 TTS를 시작하지 못했습니다."
            }
        } else {
            isPlaying = true
        }
    }

    LaunchedEffect(currentIndex) {
        store.updatePosition(documentId, data, currentIndex)
    }

    LaunchedEffect(isPlaying, ttsMode, currentIndex, settings.wpm, settings.punctuationDelay, settings.longWordDelay) {
        if (!isPlaying || ttsMode) return@LaunchedEffect
        val token = data.tokens[currentIndex]
        delay(delayFor(token, settings))
        if (currentIndex < data.tokens.lastIndex) currentIndex++ else isPlaying = false
    }

    BackHandler(enabled = fullTextOpen) {
        fullTextOpen = false
        fullTextWasPlaying = false
    }
    BackHandler(enabled = speedDialog) { speedDialog = false }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                        isPlaying = false
                        ttsController.stop()
                        store.updatePosition(documentId, data, currentIndex, sync = true)
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "서재로") }
                },
                actions = {
                    IconButton(onClick = {
                        isPlaying = false
                        ttsController.stop()
                        ttsNotice = null
                        val nextMode = !ttsMode
                        ttsMode = nextMode
                        onSettingsChange(settings.copy(lastTtsMode = nextMode))
                    }) {
                        Icon(
                            if (ttsMode) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            if (ttsMode) "TTS 모드 사용 중" else "일반 RSVP 모드 사용 중",
                            tint = if (ttsMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        isPlaying = false
                        ttsController.stop()
                        speedDialog = true
                    }) {
                        Icon(Icons.Default.Speed, null)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (ttsMode) String.format(Locale.US, "%.1f×", settings.ttsRate)
                            else "${settings.wpm} WPM"
                        )
                    }
                    IconButton(onClick = {
                        fullTextWasPlaying = isPlaying
                        isPlaying = false
                        ttsController.stop()
                        fullTextOpen = true
                    }) { Icon(Icons.Default.Subject, "전체 글") }
                }
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
            if (ttsNotice != null) {
                Text(
                    ttsNotice ?: "",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (settings.rsvpAtTop) {
                RSVPWordDisplay(
                    previousToken = data.tokens.getOrNull(currentIndex - 1)?.display,
                    token = currentToken,
                    nextToken = data.tokens.getOrNull(currentIndex + 1)?.display,
                    settings = settings
                )
                HorizontalDivider()
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ContextWindow(
                    data = data,
                    sentenceIndex = currentSentenceIndex,
                    currentGlobalToken = currentIndex,
                    settings = settings,
                    onSentenceClick = { selected ->
                        val sentence = data.sentences[selected]
                        if (sentence.tokenCount > 0) moveToAndPlay(sentence.tokenStartGlobal)
                    }
                )
            }

            if (!settings.rsvpAtTop) {
                HorizontalDivider()
                RSVPWordDisplay(
                    previousToken = data.tokens.getOrNull(currentIndex - 1)?.display,
                    token = currentToken,
                    nextToken = data.tokens.getOrNull(currentIndex + 1)?.display,
                    settings = settings
                )
            }

            PlaybackControls(
                isPlaying = isPlaying,
                canPreviousSentence = currentSentenceIndex > 0,
                canPrevious = currentIndex > 0,
                canNext = currentIndex < data.tokens.lastIndex,
                canNextSentence = currentSentenceIndex < data.sentences.lastIndex,
                onPreviousSentence = {
                    data.sentences.getOrNull(currentSentenceIndex - 1)
                        ?.takeIf { it.tokenCount > 0 }
                        ?.let { moveTo(it.tokenStartGlobal) }
                },
                onPrevious = { moveTo(currentIndex - 1) },
                onPlayPause = { togglePlayback() },
                onNext = { moveTo(currentIndex + 1) },
                onNextSentence = {
                    data.sentences.getOrNull(currentSentenceIndex + 1)
                        ?.takeIf { it.tokenCount > 0 }
                        ?.let { moveTo(it.tokenStartGlobal) }
                }
            )
            Text(
                "${(progress * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
        }

        if (fullTextOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        fullTextOpen = false
                        fullTextWasPlaying = false
                    }
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
                highlightColor = settings.effectiveFocusColor,
                onClose = {
                    fullTextOpen = false
                    fullTextWasPlaying = false
                },
                onSettings = {
                    isPlaying = false
                    ttsController.stop()
                    store.updatePosition(documentId, data, currentIndex, sync = true)
                    fullTextOpen = false
                    onOpenSettings()
                },
                onSentenceClick = { sentenceIndex ->
                    val sentence = data.sentences[sentenceIndex]
                    if (sentence.tokenCount > 0) {
                        val shouldResume = fullTextWasPlaying
                        fullTextWasPlaying = false
                        fullTextOpen = false
                        if (shouldResume) {
                            moveToAndPlay(sentence.tokenStartGlobal)
                        } else {
                            ttsController.stop()
                            isPlaying = false
                            currentIndex = sentence.tokenStartGlobal
                        }
                    } else {
                        fullTextWasPlaying = false
                        fullTextOpen = false
                    }
                },
                modifier = Modifier
                    .width(maxWidth * 0.86f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }
    }

    if (speedDialog) {
        if (ttsMode) {
            TtsSpeedDialog(
                rate = settings.ttsRate,
                onRateChange = {
                    val rate = it.coerceIn(0.5f, 2.0f)
                    onSettingsChange(settings.copy(ttsRate = rate))
                    ttsController.setSpeechRate(rate)
                },
                onDismiss = { speedDialog = false }
            )
        } else {
            SpeedDialog(
                wpm = settings.wpm,
                onWpmChange = { onSettingsChange(settings.copy(wpm = it.coerceIn(60, 1200))) },
                onDismiss = { speedDialog = false }
            )
        }
    }
}

private fun speakTtsFromIndex(
    controller: SystemTtsController,
    data: ProcessedDocument,
    index: Int
): Boolean {
    if (data.tokens.isEmpty()) return false
    val safeIndex = index.coerceIn(0, data.tokens.lastIndex)
    val token = data.tokens[safeIndex]
    val sentence = data.sentences.getOrNull(token.sentenceIndex) ?: return false
    val start = token.start.coerceIn(sentence.start, sentence.endExclusive)
    if (start >= sentence.endExclusive) return false
    val text = data.normalizedText.substring(start, sentence.endExclusive)
    return controller.speak(text, start, token.sentenceIndex)
}

private fun tokenIndexForTtsRange(
    data: ProcessedDocument,
    sentenceIndex: Int,
    globalStart: Int,
    globalEnd: Int
): Int? {
    val sentence = data.sentences.getOrNull(sentenceIndex) ?: return null
    if (sentence.tokenCount <= 0) return null
    val from = sentence.tokenStartGlobal
    val to = (from + sentence.tokenCount).coerceAtMost(data.tokens.size)
    val tokens = data.tokens.subList(from, to)

    return tokens.firstOrNull { globalStart >= it.start && globalStart < it.endExclusive }?.globalIndex
        ?: tokens.firstOrNull { globalStart < it.endExclusive && globalEnd > it.start }?.globalIndex
        ?: tokens.firstOrNull { it.start >= globalStart }?.globalIndex
        ?: tokens.lastOrNull()?.globalIndex
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
    val effectiveFocusColor = settings.effectiveFocusColor
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        for (i in start..end) {
            val sentence = data.sentences[i]
            val current = i == sentenceIndex
            val token = if (current) data.tokens.getOrNull(currentGlobalToken) else null
            val annotated = remember(sentence.text, token?.globalIndex, effectiveFocusColor) {
                AnnotatedString.Builder(sentence.text).apply {
                    if (current && token != null) {
                        val localStart = (token.start - sentence.start).coerceIn(0, sentence.text.length)
                        val localEnd = (token.endExclusive - sentence.start).coerceIn(localStart, sentence.text.length)
                        if (localEnd > localStart) {
                            addStyle(
                                SpanStyle(
                                    background = effectiveFocusColor.copy(alpha = if (settings.darkMode) 0.34f else 0.28f),
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
private fun RSVPWordDisplay(
    previousToken: String?,
    token: RsvpToken,
    nextToken: String?,
    settings: AppSettings
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        FocusToken(
            previousText = previousToken,
            text = token.display,
            nextText = nextToken,
            settings = settings
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    canPreviousSentence: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    canNextSentence: Boolean,
    onPreviousSentence: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onNextSentence: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPreviousSentence,
            enabled = canPreviousSentence,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(
                Icons.Default.FastRewind,
                "이전 문장",
                modifier = Modifier.size(29.dp)
            )
        }
        IconButton(
            onClick = onPrevious,
            enabled = canPrevious,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(
                Icons.Default.ArrowLeft,
                "이전 토큰",
                modifier = Modifier.size(30.dp)
            )
        }
        Surface(shape = RoundedCornerShape(50), tonalElevation = 4.dp) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(66.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "일시정지" else "재생",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = canNext,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(
                Icons.Default.ArrowRight,
                "다음 토큰",
                modifier = Modifier.size(30.dp)
            )
        }
        IconButton(
            onClick = onNextSentence,
            enabled = canNextSentence,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(
                Icons.Default.FastForward,
                "다음 문장",
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
private fun FocusToken(
    previousText: String?,
    text: String,
    nextText: String?,
    settings: AppSettings
) {
    val measurer = rememberTextMeasurer()
    val focusRange = remember(text) { TextProcessor.focusGraphemeRange(text) }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val neighborColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val focusColor = if (settings.showFocusColor) settings.effectiveFocusColor else baseColor
    val annotated = remember(text, focusRange, focusColor) {
        AnnotatedString.Builder(text).apply {
            if (focusRange != null) addStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold), focusRange.first, focusRange.last + 1)
        }.toAnnotatedString()
    }
    val currentResult = remember(annotated, settings.rsvpFontSp, baseColor) {
        measurer.measure(
            text = annotated,
            style = TextStyle(fontSize = settings.rsvpFontSp.sp, color = baseColor, fontWeight = FontWeight.Medium)
        )
    }
    val neighborFontSp = settings.rsvpFontSp * 0.62f
    val previousResult = remember(previousText, neighborFontSp, neighborColor) {
        previousText?.let {
            measurer.measure(
                text = AnnotatedString(it),
                style = TextStyle(fontSize = neighborFontSp.sp, color = neighborColor, fontWeight = FontWeight.Normal)
            )
        }
    }
    val nextResult = remember(nextText, neighborFontSp, neighborColor) {
        nextText?.let {
            measurer.measure(
                text = AnnotatedString(it),
                style = TextStyle(fontSize = neighborFontSp.sp, color = neighborColor, fontWeight = FontWeight.Normal)
            )
        }
    }

    Canvas(Modifier.fillMaxWidth().height(94.dp)) {
        val focusCenter = if (focusRange != null && text.isNotEmpty()) {
            val first = currentResult.getBoundingBox(focusRange.first)
            val lastOffset = focusRange.last.coerceAtMost(text.lastIndex)
            val last = currentResult.getBoundingBox(lastOffset)
            (first.left + last.right) / 2f
        } else {
            currentResult.size.width / 2f
        }

        val currentX = size.width / 2f - focusCenter
        val currentY = (size.height - currentResult.size.height) / 2f
        val gap = 22.dp.toPx()

        previousResult?.let { previous ->
            drawText(
                textLayoutResult = previous,
                topLeft = Offset(
                    x = currentX - gap - previous.size.width,
                    y = (size.height - previous.size.height) / 2f
                )
            )
        }

        drawText(
            textLayoutResult = currentResult,
            topLeft = Offset(currentX, currentY)
        )

        nextResult?.let { next ->
            drawText(
                textLayoutResult = next,
                topLeft = Offset(
                    x = currentX + currentResult.size.width + gap,
                    y = (size.height - next.size.height) / 2f
                )
            )
        }
    }
}

@Composable
private fun FullTextPanel(
    data: ProcessedDocument,
    currentSentenceIndex: Int,
    fontSize: Float,
    highlightColor: Color,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
                TextButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(currentSentenceIndex.coerceIn(0, data.sentences.lastIndex))
                        }
                    }
                ) { Text("현재 위치") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
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
                            .background(if (current) highlightColor.copy(alpha = 0.20f) else Color.Transparent)
                            .clickable { onSentenceClick(i) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
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


@Composable
private fun TtsSpeedDialog(rate: Float, onRateChange: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TTS 음성 속도") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    String.format(Locale.US, "%.1f×", rate),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onRateChange(((rate * 10).roundToInt() - 1).coerceAtLeast(5) / 10f)
                    }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Slider(
                        value = rate,
                        onValueChange = { onRateChange(((it * 10).roundToInt() / 10f).coerceIn(0.5f, 2.0f)) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        onRateChange(((rate * 10).roundToInt() + 1).coerceAtMost(20) / 10f)
                    }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Text(
                    "TTS 모드에서는 WPM·문장부호 지연 대신 실제 음성 재생 시간이 기준이 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
