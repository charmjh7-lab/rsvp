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
    leftSide: Boolean,
    revealed: Boolean,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val color = if (settings.showFocusColor) settings.effectiveFocusColor else MaterialTheme.colorScheme.onSurface
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            settings.eyeMode.label + " · " + settings.eyeUnit.label +
                if (settings.eyeMode == EyeReadingMode.FIXED_POSITION) (if (settings.keepPrevious) " · 앞부분 유지" else " · 현재만 표시") else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val style = TextStyle(fontSize = settings.rsvpFontSp.sp, lineHeight = (settings.rsvpFontSp * 1.5f).sp, color = color, fontWeight = FontWeight.Normal)
            if (settings.eyeMode == EyeReadingMode.ALTERNATING) {
                val sideWidth = (widthPx * 0.46f).toInt().coerceAtLeast(1)
                val result = remember(slice.text, style, sideWidth, heightPx) {
                    var font = settings.rsvpFontSp
                    var layout = measurer.measure(AnnotatedString(slice.text), style, constraints = Constraints(maxWidth = sideWidth))
                    // Keep the complete unit visible inside its side, including long URLs.
                    while (layout.size.height > heightPx && font > 10f) {
                        font = (font - 1f).coerceAtLeast(10f)
                        layout = measurer.measure(AnnotatedString(slice.text), style.copy(fontSize = font.sp, lineHeight = (font * 1.2f).sp), constraints = Constraints(maxWidth = sideWidth))
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
                val range = eyeVisibleRange(sentence, slice, settings.keepPrevious)
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
                        if (revealed && !range.isEmpty()) {
                            val visiblePath = layout.getPathForRange(range.first, range.last + 1)
                            visiblePath.translate(Offset(0f, -pageTop))
                            clipRect {
                                clipPath(visiblePath) { drawText(layout, topLeft = Offset(0f, -pageTop)) }
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
                Text(if (settings.eyeMode == EyeReadingMode.ALTERNATING) "왼쪽과 오른쪽을 번갈아 보며 이어 읽습니다." else "문장의 자리를 먼저 정한 뒤 순서대로 표시합니다. 긴 문장은 다음 줄·화면으로 이어집니다.", style = MaterialTheme.typography.bodySmall)
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
                    Text("끄면 현재 항목만 남고 앞부분은 다시 빈칸이 됩니다.", style = MaterialTheme.typography.bodySmall)
                }
                Text("단어는 띄어쓰기·문장부호 기준, 글자는 결합 문자 기준으로 나눕니다. TTS 재생 중에는 음성이 가리키는 단어를 표시합니다.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("적용") } }
    )
}
