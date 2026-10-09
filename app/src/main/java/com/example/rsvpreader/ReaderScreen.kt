package com.example.rsvpreader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
    sessionHolder: ReaderSessionHolder,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val initialSession = remember(documentId) { sessionHolder.snapshot(documentId) }
    var document by remember(documentId) { mutableStateOf(store.getDocument(documentId)) }
    var processed by remember(documentId) { mutableStateOf(initialSession?.processed) }
    var loadError by remember(documentId) { mutableStateOf<String?>(null) }
    var currentIndex by remember(documentId) {
        mutableIntStateOf(initialSession?.globalTokenIndex ?: document?.globalTokenIndex ?: 0)
    }
    var isPlaying by remember { mutableStateOf(false) }
    var sliceIndex by remember(documentId) { mutableIntStateOf(0) }
    var leftSide by remember(documentId) { mutableStateOf(true) }
    var revealReady by remember(documentId) { mutableStateOf(false) }
    var modeDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var fullTextOpen by remember { mutableStateOf(false) }
    var ttsMode by remember(documentId) { mutableStateOf(settings.lastTtsMode) }
    var ttsReady by remember(documentId) { mutableStateOf(false) }
    var ttsNotice by remember(documentId) { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current.applicationContext
    val view = LocalView.current
    val ttsController = remember(documentId) { SystemTtsController(appContext) }

    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(ttsController) {
        onDispose { ttsController.shutdown() }
    }

    LaunchedEffect(documentId) {
        if (processed != null) return@LaunchedEffect

        val memoryResult = store.getMemoryProcessed(documentId)
        if (memoryResult != null) {
            val targetIndex = (document?.globalTokenIndex ?: 0)
                .coerceIn(0, memoryResult.tokens.lastIndex)
            processed = memoryResult
            currentIndex = targetIndex
            sessionHolder.update(documentId, memoryResult, targetIndex)
            return@LaunchedEffect
        }

        val files = withContext(Dispatchers.IO) {
            store.loadProcessedFiles(documentId)
        }
        if (files == null) {
            loadError = "저장된 문서 본문을 찾지 못했습니다."
            return@LaunchedEffect
        }

        val cached = if (files.cacheBytes != null) {
            withContext(Dispatchers.Default) {
                store.decodeProcessedCache(files)
            }
        } else {
            null
        }

        val result = cached ?: withContext(Dispatchers.Default) {
            TextProcessor.processNormalized(files.text)
        }

        if (result.tokens.isEmpty()) {
            loadError = "읽을 수 있는 텍스트가 없습니다."
            return@LaunchedEffect
        }

        val targetIndex = (document?.globalTokenIndex ?: 0)
            .coerceIn(0, result.tokens.lastIndex)
        processed = result
        currentIndex = targetIndex
        store.rememberProcessed(documentId, result)
        sessionHolder.update(documentId, result, targetIndex)

        if (cached == null) {
            withContext(Dispatchers.IO) {
                store.writeProcessedCache(documentId, result, files.contentHash)
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

    SideEffect {
        sessionHolder.update(documentId, data, currentIndex)
    }

    val safeIndex = currentIndex.coerceIn(0, data.tokens.lastIndex)
    val currentToken = data.tokens[safeIndex]
    val slices = remember(currentToken, settings.eyeUnit, ttsMode) { eyeReadingSlices(currentToken, if (ttsMode) EyeReadingUnit.WORD else settings.eyeUnit) }
    val activeSlice = slices[sliceIndex.coerceIn(0, slices.lastIndex)]
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
        ttsController.onRangeUnavailable = {
            if (latestTtsMode && latestIsPlaying) {
                ttsNotice = "이 TTS 엔진은 단어 위치 정보를 제공하지 않아 문장 단위로 표시합니다."
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
            ttsController.onRangeUnavailable = null
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
        sliceIndex = 0
        leftSide = true
        revealReady = true
        if (continueTts && !startTtsAt(target)) {
            isPlaying = false
            ttsNotice = "이 위치부터 TTS를 다시 시작하지 못했습니다."
        }
    }

    fun moveByUnit(direction: Int) {
        if (!ttsMode && settings.eyeUnit == EyeReadingUnit.CHARACTER) {
            val candidate = sliceIndex + direction
            if (candidate in slices.indices) {
                sliceIndex = candidate
                leftSide = !leftSide
                revealReady = true
            } else {
                val target = currentIndex + direction
                if (target in data.tokens.indices) {
                    moveTo(target)
                    if (direction < 0) sliceIndex = eyeReadingSlices(data.tokens[target], settings.eyeUnit).lastIndex
                }
            }
        } else moveTo(currentIndex + direction)
    }

    fun moveToAndPlay(index: Int) {
        val target = index.coerceIn(0, data.tokens.lastIndex)
        ttsController.stop()
        currentIndex = target
        sliceIndex = 0
        leftSide = true
        revealReady = false
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

    LaunchedEffect(documentId, data) {
        var lastSavedIndex = latestIndex
        while (true) {
            delay(1_500L)
            val indexToSave = latestIndex
            if (indexToSave != lastSavedIndex) {
                store.updatePosition(documentId, data, indexToSave)
                lastSavedIndex = indexToSave
            }
        }
    }

    LaunchedEffect(isPlaying, ttsMode, currentIndex, sliceIndex, settings.eyeMode, settings.eyeUnit, settings.eyeRate, settings.punctuationDelay, settings.longWordDelay) {
        if (!isPlaying || ttsMode) return@LaunchedEffect
        if (!revealReady) {
            delay(400L)
            revealReady = true
        }
        delay(eyeReadingDelay(currentToken, activeSlice, settings))
        if (sliceIndex < slices.lastIndex) {
            sliceIndex++
            leftSide = !leftSide
        } else if (currentIndex < data.tokens.lastIndex) {
            val next = currentIndex + 1
            if (data.tokens[next].sentenceIndex != currentSentenceIndex) revealReady = false
            sliceIndex = 0
            leftSide = !leftSide
            currentIndex = next
        } else isPlaying = false
    }

    BackHandler(enabled = fullTextOpen) {
        fullTextOpen = false
    }
    BackHandler(enabled = speedDialog) { speedDialog = false }
    BackHandler(enabled = modeDialog) { modeDialog = false }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val eyeDisplayHeight = (maxHeight * 0.38f).coerceIn(100.dp, 300.dp)
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
                        sliceIndex = 0
                        revealReady = true
                        ttsMode = nextMode
                        onSettingsChange(settings.copy(lastTtsMode = nextMode))
                    }) {
                        Icon(
                            if (ttsMode) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            if (ttsMode) "TTS 모드 사용 중" else "안구 이동 모드 사용 중",
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
                            else "${settings.eyeRate}/분"
                        )
                    }
                    IconButton(onClick = {
                        isPlaying = false
                        ttsController.stop()
                        modeDialog = true
                    }) { Icon(Icons.Default.SwapHoriz, "읽기 모드 선택") }
                    IconButton(onClick = {
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
                EyeReadingDisplay(
                    sentence = data.sentences[currentSentenceIndex],
                    slice = activeSlice,
                    leftSide = if (ttsMode) safeIndex % 2 == 0 else leftSide,
                    revealed = ttsMode || revealReady,
                    settings = settings,
                    modifier = Modifier.fillMaxWidth().height(eyeDisplayHeight)
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
                EyeReadingDisplay(
                    sentence = data.sentences[currentSentenceIndex],
                    slice = activeSlice,
                    leftSide = if (ttsMode) safeIndex % 2 == 0 else leftSide,
                    revealed = ttsMode || revealReady,
                    settings = settings,
                    modifier = Modifier.fillMaxWidth().height(eyeDisplayHeight)
                )
            }

            PlaybackControls(
                isPlaying = isPlaying,
                canPreviousSentence = currentSentenceIndex > 0,
                canPrevious = currentIndex > 0 || (!ttsMode && sliceIndex > 0),
                canNext = currentIndex < data.tokens.lastIndex || (!ttsMode && sliceIndex < slices.lastIndex),
                canNextSentence = currentSentenceIndex < data.sentences.lastIndex,
                onPreviousSentence = {
                    data.sentences.getOrNull(currentSentenceIndex - 1)
                        ?.takeIf { it.tokenCount > 0 }
                        ?.let { moveTo(it.tokenStartGlobal) }
                },
                onPrevious = { moveByUnit(-1) },
                onPlayPause = { togglePlayback() },
                onNext = { moveByUnit(1) },
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
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        if (fullTextOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        fullTextOpen = false
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
                toc = document?.toc.orEmpty(),
                currentSentenceIndex = currentSentenceIndex,
                fontSize = settings.contextFontSp,
                highlightColor = settings.effectiveFocusColor,
                onClose = {
                    fullTextOpen = false
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
                    fullTextOpen = false
                    if (sentence.tokenCount > 0) {
                        moveToAndPlay(sentence.tokenStartGlobal)
                    }
                },
                onTocSelect = { entry ->
                    moveTo(entry.globalTokenIndex)
                },
                modifier = Modifier
                    .width(maxWidth * 0.86f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }
    }

    if (modeDialog) {
        EyeModeDialog(settings, onChange = {
            sliceIndex = 0
            revealReady = true
            leftSide = true
            onSettingsChange(it)
        }, onDismiss = { modeDialog = false })
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
                wpm = settings.eyeRate,
                unitLabel = settings.eyeUnit.label,
                onWpmChange = { onSettingsChange(settings.withEyeRate(it)) },
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

@Composable
private fun ContextWindow(
    data: ProcessedDocument,
    sentenceIndex: Int,
    currentGlobalToken: Int,
    settings: AppSettings,
    onSentenceClick: (Int) -> Unit
) {
    val effectiveFocusColor = settings.effectiveFocusColor
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        val textWidthPx = with(density) { (maxWidth - 24.dp).coerceAtLeast(1.dp).roundToPx() }
        val availableHeightPx = with(density) { maxHeight.roundToPx() }
        val currentPaddingPx = with(density) { 28.dp.roundToPx() }
        val neighborPaddingPx = with(density) { 22.dp.roundToPx() }
        val lineHeightPx = with(density) { (settings.contextFontSp * 1.45f).sp.roundToPx() }.coerceAtLeast(1)
        val textStyle = TextStyle(
            fontSize = settings.contextFontSp.sp,
            lineHeight = (settings.contextFontSp * 1.45f).sp
        )

        fun measuredBlockHeight(index: Int): Int {
            val textHeight = measurer.measure(
                text = AnnotatedString(data.sentences[index].text),
                style = textStyle,
                constraints = Constraints(maxWidth = textWidthPx)
            ).size.height
            return textHeight + if (index == sentenceIndex) currentPaddingPx else neighborPaddingPx
        }

        val visibleIndices = remember(
            sentenceIndex,
            settings.contextFontSp,
            textWidthPx,
            availableHeightPx,
            data.sentences
        ) {
            val selected = mutableListOf(sentenceIndex)
            var usedHeight = measuredBlockHeight(sentenceIndex)
            for (distance in 1..2) {
                listOf(sentenceIndex - distance, sentenceIndex + distance).forEach { candidate ->
                    if (candidate in data.sentences.indices) {
                        val height = measuredBlockHeight(candidate)
                        if (usedHeight + height <= availableHeightPx) {
                            selected += candidate
                            usedHeight += height
                        }
                    }
                }
            }
            selected.sorted()
        }

        val fullCurrentHeight = measuredBlockHeight(sentenceIndex)
        val currentMaxLines = if (fullCurrentHeight <= availableHeightPx) {
            Int.MAX_VALUE
        } else {
            ((availableHeightPx - currentPaddingPx).coerceAtLeast(lineHeightPx) / lineHeightPx).coerceAtLeast(1)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            visibleIndices.forEach { i ->
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
                        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        maxLines = if (current) currentMaxLines else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
    val navigationIconColor = MaterialTheme.colorScheme.onSurface
    val disabledNavigationIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navigationPressedColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDarkSurface) 0.24f else 0.12f
    )
    val playPressedColor = MaterialTheme.colorScheme.primary.copy(
        alpha = if (isDarkSurface) 0.28f else 0.18f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaybackNavigationButton(
            enabled = canPreviousSentence,
            onClick = onPreviousSentence,
            pressedColor = navigationPressedColor
        ) {
            Icon(
                Icons.Default.FastRewind,
                "이전 문장",
                modifier = Modifier.size(29.dp),
                tint = if (canPreviousSentence) navigationIconColor else disabledNavigationIconColor
            )
        }
        PlaybackNavigationButton(
            enabled = canPrevious,
            onClick = onPrevious,
            pressedColor = navigationPressedColor
        ) {
            Icon(
                Icons.Default.ArrowLeft,
                "이전 토큰",
                modifier = Modifier.size(30.dp),
                tint = if (canPrevious) navigationIconColor else disabledNavigationIconColor
            )
        }
        PlaybackPlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            pressedColor = playPressedColor
        )
        PlaybackNavigationButton(
            enabled = canNext,
            onClick = onNext,
            pressedColor = navigationPressedColor
        ) {
            Icon(
                Icons.Default.ArrowRight,
                "다음 토큰",
                modifier = Modifier.size(30.dp),
                tint = if (canNext) navigationIconColor else disabledNavigationIconColor
            )
        }
        PlaybackNavigationButton(
            enabled = canNextSentence,
            onClick = onNextSentence,
            pressedColor = navigationPressedColor
        ) {
            Icon(
                Icons.Default.FastForward,
                "다음 문장",
                modifier = Modifier.size(29.dp),
                tint = if (canNextSentence) navigationIconColor else disabledNavigationIconColor
            )
        }
    }
}

@Composable
private fun PlaybackPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    pressedColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = Modifier
            .size(66.dp)
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        tonalElevation = if (pressed) 2.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (pressed) pressedColor else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "일시정지" else "재생",
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun PlaybackNavigationButton(
    enabled: Boolean,
    onClick: () -> Unit,
    pressedColor: Color,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(58.dp)
            .graphicsLayer {
                val scale = if (pressed && enabled) 0.96f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (pressed && enabled) pressedColor else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun FullTextPanel(
    data: ProcessedDocument,
    toc: List<DocumentTocEntry>,
    currentSentenceIndex: Int,
    fontSize: Float,
    highlightColor: Color,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onTocSelect: (DocumentTocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tocOpen by remember { mutableStateOf(false) }

    LaunchedEffect(currentSentenceIndex, tocOpen) {
        if (!tocOpen) {
            listState.scrollToItem(currentSentenceIndex.coerceIn(0, data.sentences.lastIndex))
        }
    }

    Surface(modifier = modifier, shadowElevation = 14.dp, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (tocOpen) "목차" else "전체 글",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (toc.isNotEmpty()) {
                    TextButton(onClick = { tocOpen = !tocOpen }) {
                        Text(if (tocOpen) "본문" else "목차")
                    }
                }
                TextButton(
                    onClick = {
                        if (tocOpen) {
                            tocOpen = false
                        } else {
                            scope.launch {
                                listState.animateScrollToItem(
                                    currentSentenceIndex.coerceIn(0, data.sentences.lastIndex)
                                )
                            }
                        }
                    }
                ) { Text("현재 위치") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기") }
            }
            HorizontalDivider()

            if (tocOpen && toc.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(toc.size, key = { it }) { index ->
                        val entry = toc[index]
                        Text(
                            entry.title,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.35f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tocOpen = false
                                    onTocSelect(entry)
                                }
                                .padding(
                                    start = (18 + entry.level.coerceAtMost(6) * 18).dp,
                                    end = 18.dp,
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(data.sentences.size, key = { it }) { i ->
                        val current = i == currentSentenceIndex
                        Text(
                            data.sentences[i].text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.5f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (current) highlightColor.copy(alpha = 0.20f)
                                    else Color.Transparent
                                )
                                .clickable { onSentenceClick(i) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            color = if (current) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialog(wpm: Int, unitLabel: String, onWpmChange: (Int) -> Unit, onDismiss: () -> Unit) {
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
                        onValueChange = { input = it.filter(Char::isDigit).take(3) },
                        label = { Text("분당 $unitLabel 표시 ($EYE_RATE_MIN–$EYE_RATE_MAX)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        input.toIntOrNull()?.let {
                            val clamped = it.coerceIn(EYE_RATE_MIN, EYE_RATE_MAX)
                            onWpmChange(clamped)
                            input = clamped.toString()
                        }
                        directInput = false
                    }) { Text("적용") }
                } else {
                    Text("$wpm $unitLabel/분", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { input = wpm.toString(); directInput = true }.padding(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onWpmChange((wpm - 10).coerceAtLeast(EYE_RATE_MIN)) }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Slider(
                        value = wpm.coerceIn(EYE_RATE_MIN, EYE_RATE_MAX).toFloat(),
                        onValueChange = { onWpmChange(((it / 10).roundToInt() * 10).coerceIn(EYE_RATE_MIN, EYE_RATE_MAX)) },
                        valueRange = EYE_RATE_MIN.toFloat()..EYE_RATE_MAX.toFloat(),
                        steps = ((EYE_RATE_MAX - EYE_RATE_MIN) / 10 - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onWpmChange((wpm + 10).coerceAtMost(EYE_RATE_MAX)) }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                }
                Text("숫자를 누르면 직접 입력할 수 있습니다. 표시 간격은 약 ${60_000 / wpm}ms이며 문장부호·긴 단어는 더 오래 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "TTS 모드에서는 표시 속도·문장부호 지연 대신 실제 음성 재생 시간이 기준이 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
