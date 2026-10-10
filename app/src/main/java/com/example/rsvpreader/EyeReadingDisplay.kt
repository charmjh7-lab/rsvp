package com.example.rsvpreader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EyeReadingDisplay(
    sentence: SentenceUnit,
    slice: EyeReadingSlice,
    sentenceSlices: List<EyeReadingSlice>,
    leftSide: Boolean,
    revealed: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val normalColor = MaterialTheme.colorScheme.onSurface
    val focusColor = if (settings.showFocusColor) settings.effectiveFocusColor else normalColor
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            settings.eyeMode.label + " · " + settings.eyeUnit.label +
                if (settings.eyeMode == EyeReadingMode.FIXED_POSITION) {
                    (if (settings.keepPrevious) " · 앞부분 유지" else " · 이전 ${settings.eyePreviousCount}${settings.eyeUnit.label}") +
                        (if (settings.eyeShowAllFollowing) " · 이후 전체" else " · ${settings.eyePreviewCount}${settings.eyeUnit.label} 공개")
                } else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("eye-stage")) {
            val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val style = TextStyle(fontSize = settings.rsvpFontSp.sp, lineHeight = (settings.rsvpFontSp * 1.5f).sp, color = normalColor, fontWeight = FontWeight.Normal)
            if (settings.eyeMode == EyeReadingMode.ALTERNATING) {
                val sideWidth = (widthPx * 0.46f).toInt().coerceAtLeast(1)
                val result = remember(slice.text, style, sideWidth, heightPx) {
                    var font = settings.rsvpFontSp
                    var layout = measurer.measure(AnnotatedString(slice.text), style.copy(color = focusColor), constraints = Constraints(maxWidth = sideWidth))
                    // Keep the complete unit visible inside its side, including long URLs.
                    while (layout.size.height > heightPx && font > 10f) {
                        font = (font - 1f).coerceAtLeast(10f)
                        layout = measurer.measure(AnnotatedString(slice.text), style.copy(color = focusColor, fontSize = font.sp, lineHeight = (font * 1.2f).sp), constraints = Constraints(maxWidth = sideWidth))
                    }
                    layout
                }
                val scroll = rememberScrollState()
                LaunchedEffect(slice.start) { scroll.scrollTo(0) }
                Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    val canvasHeight = with(density) { maxOf(heightPx, result.size.height).toDp() }
                    Canvas(Modifier.fillMaxWidth().height(canvasHeight).semantics { contentDescription = if (revealed) slice.text else "표시 준비" }) {
                        if (revealed) drawText(result, topLeft = Offset(if (leftSide) 0f else size.width - result.size.width, (size.height - result.size.height) / 2f))
                    }
                }
            } else {
                // The full sentence, including unseen words, owns the layout throughout.
                val layout = remember(sentence.text, style, widthPx) {
                    measurer.measure(AnnotatedString(sentence.text), style, constraints = Constraints(maxWidth = widthPx))
                }
                // Each guide follows the same full-sentence layout as the glyphs.
                // Split wrapped units by line, and leave gaps between character guides.
                val guides = remember(layout, sentenceSlices, density) {
                    buildList {
                        sentenceSlices.forEach { unit ->
                            val start = (unit.start - sentence.start).coerceIn(0, sentence.text.length)
                            val end = (unit.endExclusive - sentence.start).coerceIn(start, sentence.text.length)
                            (start until end).groupBy { layout.getLineForOffset(it) }.forEach { (line, offsets) ->
                                val boxes = offsets.map { layout.getBoundingBox(it) }
                                val left = boxes.minOf { it.left }
                                val right = boxes.maxOf { it.right }
                                val inset = minOf(with(density) { 2.dp.toPx() }, (right - left) * 0.15f)
                                val y = minOf(layout.getLineBaseline(line) + with(density) { settings.rsvpFontSp.sp.toPx() } * 0.22f,
                                    layout.getLineBottom(line) - with(density) { 2.dp.toPx() })
                                if (right > left) add(Offset(left + inset, y) to Offset(right - inset, y))
                            }
                        }
                    }
                }
                val range = eyeVisibleRange(
                    sentence, slice, sentenceSlices, settings.keepPrevious, settings.eyePreviewCount,
                    settings.eyePreviousCount, settings.eyeShowAllFollowing
                )
                val activeStart = (slice.start - sentence.start).coerceIn(0, sentence.text.lastIndex)
                val activeEnd = (slice.endExclusive - sentence.start - 1).coerceIn(activeStart, sentence.text.lastIndex)
                val firstLine = layout.getLineForOffset(activeStart)
                val lastLine = layout.getLineForOffset(activeEnd)
                val lineHeight = (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1f)
                val linesPerPage = (heightPx / lineHeight).toInt().coerceAtLeast(1)
                val pageFirstLine = (firstLine / linesPerPage) * linesPerPage
                val pageTop = layout.getLineTop(pageFirstLine)
                // A single long word can span a page: provide scrolling for that whole
                // word instead of silently clipping the remainder.
                val requiredHeight = maxOf(heightPx.toFloat(), layout.getLineBottom(lastLine) - pageTop)
                val scroll = rememberScrollState()
                LaunchedEffect(pageFirstLine, slice.start) { scroll.scrollTo(0) }
                Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Canvas(Modifier.fillMaxWidth().height(with(density) { requiredHeight.toDp() }).semantics {
                        contentDescription = if (revealed && !range.isEmpty()) sentence.text.substring(range.first, range.last + 1) else "표시 준비"
                    }) {
                        // Guides are visible during preparation and stay put when
                        // previous words are hidden, without exposing their text.
                        clipRect {
                            guides.forEach { (start, end) ->
                                drawLine(guideColor, start - Offset(0f, pageTop), end - Offset(0f, pageTop), strokeWidth = 1.dp.toPx())
                            }
                        }
                        if (revealed && !range.isEmpty()) {
                            val visiblePath = layout.getPathForRange(range.first, range.last + 1)
                            visiblePath.translate(Offset(0f, -pageTop))
                            clipRect {
                                clipPath(visiblePath) { drawText(layout, topLeft = Offset(0f, -pageTop)) }
                                if (settings.showFocusColor) {
                                    val activePath = layout.getPathForRange(activeStart, activeEnd + 1)
                                    activePath.translate(Offset(0f, -pageTop))
                                    clipPath(activePath) {
                                        drawText(layout, color = focusColor, topLeft = Offset(0f, -pageTop))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EyeModeDialog(settings: AppSettings, onChange: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("안구 이동 읽기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EyeReadingMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { onChange(settings.copy(eyeMode = mode)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(settings.eyeMode == mode, onClick = { onChange(settings.copy(eyeMode = mode)) })
                        Text(mode.label)
                    }
                }
                Text(if (settings.eyeMode == EyeReadingMode.ALTERNATING) "왼쪽과 오른쪽을 번갈아 보며 이어 읽습니다." else "밑줄로 단어·글자의 자리를 미리 안내하고 순서대로 표시합니다. 긴 문장은 다음 줄·화면으로 이어집니다.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                EyeReadingUnit.entries.forEach { unit ->
                    Row(Modifier.fillMaxWidth().clickable { onChange(settings.copy(eyeUnit = unit)) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(settings.eyeUnit == unit, onClick = { onChange(settings.copy(eyeUnit = unit)) })
                        Text(unit.label + " 단위")
                    }
                }
                if (settings.eyeMode == EyeReadingMode.FIXED_POSITION) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("앞부분 유지", Modifier.weight(1f))
                        Switch(settings.keepPrevious, { onChange(settings.copy(keepPrevious = it)) })
                    }
                    Text("켜면 지나간 항목을 계속 남깁니다. 끄면 아래에서 이전 항목 수를 설정합니다.", style = MaterialTheme.typography.bodySmall)
                    if (!settings.keepPrevious) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("이전 ${settings.eyeUnit.label} 표시", Modifier.weight(1f))
                            TextButton(
                                onClick = { onChange(settings.copy(eyePreviousCount = (settings.eyePreviousCount - 1).coerceAtLeast(0))) },
                                enabled = settings.eyePreviousCount > 0
                            ) { Text("−") }
                            Text("${settings.eyePreviousCount}")
                            TextButton(
                                onClick = { onChange(settings.copy(eyePreviousCount = (settings.eyePreviousCount + 1).coerceAtMost(EYE_PREVIEW_MAX))) },
                                enabled = settings.eyePreviousCount < EYE_PREVIEW_MAX
                            ) { Text("+") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("이후 문장 끝까지 표시", Modifier.weight(1f))
                        Switch(settings.eyeShowAllFollowing, { onChange(settings.copy(eyeShowAllFollowing = it)) })
                    }
                    if (!settings.eyeShowAllFollowing) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("동시 공개 ${settings.eyeUnit.label} 수", Modifier.weight(1f))
                            TextButton(
                                onClick = { onChange(settings.copy(eyePreviewCount = (settings.eyePreviewCount - 1).coerceAtLeast(EYE_PREVIEW_MIN))) },
                                enabled = settings.eyePreviewCount > EYE_PREVIEW_MIN
                            ) { Text("−") }
                            Text("${settings.eyePreviewCount}")
                            TextButton(
                                onClick = { onChange(settings.copy(eyePreviewCount = (settings.eyePreviewCount + 1).coerceAtMost(EYE_PREVIEW_MAX))) },
                                enabled = settings.eyePreviewCount < EYE_PREVIEW_MAX
                            ) { Text("+") }
                        }
                    }
                    Text("RAP 방식: 앞부분 유지 끔 · 이전 0개 · 이후 문장 끝까지 표시 켬. 현재 읽는 항목만 강조합니다.", style = MaterialTheme.typography.bodySmall)
                }
                Text("단어는 띄어쓰기·문장부호 기준, 글자는 결합 문자 기준으로 나눕니다. TTS 재생 중에는 음성이 가리키는 단어를 표시합니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("적용") } }
    )
}
