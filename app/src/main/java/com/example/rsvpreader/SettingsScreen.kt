package com.example.rsvpreader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val ttsProbe = remember { SystemTtsController(appContext) }
    var ttsInfo by remember { mutableStateOf<TtsReadyInfo?>(null) }
    var ttsStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(ttsProbe) {
        onDispose { ttsProbe.shutdown() }
    }

    LaunchedEffect(settings.ttsEnginePackage, settings.ttsVoiceName) {
        ttsInfo = null
        ttsProbe.initialize(
            enginePackage = settings.ttsEnginePackage,
            voiceName = settings.ttsVoiceName,
            speechRate = settings.ttsRate,
            onReady = { info ->
                ttsInfo = info
                if (info.fellBackToDefault && settings.ttsEnginePackage != null) {
                    onChange(settings.copy(ttsEnginePackage = null, ttsVoiceName = null))
                } else if (settings.ttsVoiceName != null && !info.selectedVoiceAvailable) {
                    onChange(settings.copy(ttsVoiceName = null))
                }
            },
            onMessage = { ttsStatus = it }
        )
    }

    LaunchedEffect(settings.ttsRate) {
        ttsProbe.setSpeechRate(settings.ttsRate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SectionTitle("화면")
            SettingSwitch("읽기 글자에 색상 사용", settings.showFocusColor) { onChange(settings.copy(showFocusColor = it)) }
            Text("읽기 글자·본문 위치 색상", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            val colors = listOf(0xFFE65100, 0xFF1565C0, 0xFF2E7D32, 0xFF7B1FA2, 0xFFC62828, 0xFF00838F)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                colors.forEach { argb ->
                    val selected = settings.focusColorArgb == argb
                    Box(
                        Modifier
                            .size(38.dp)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(4.dp)
                            .background(Color(argb.toInt()), CircleShape)
                            .clickable { onChange(settings.copy(focusColorArgb = argb)) }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SettingPicker(
                label = "안구 이동 영역 위치",
                selectedLabel = if (settings.rsvpAtTop) "상단" else "하단",
                choices = listOf(
                    SettingChoice("top", "상단"),
                    SettingChoice("bottom", "하단")
                ),
                onSelect = { position ->
                    onChange(settings.copy(rsvpAtTop = position == "top"))
                }
            )
            Spacer(Modifier.height(12.dp))
            SettingPicker(
                label = "밝은 모드 배경색",
                selectedLabel = if (settings.lightBackground == LightBackground.WHITE) "흰색" else "따뜻한 크림색",
                choices = listOf(
                    SettingChoice("white", "흰색"),
                    SettingChoice("warm", "따뜻한 크림색")
                ),
                onSelect = { background ->
                    onChange(
                        settings.copy(
                            lightBackground = if (background == "warm") LightBackground.WARM else LightBackground.WHITE
                        )
                    )
                }
            )
            Text(
                "다크 모드에서는 선택값을 유지한 채 어두운 배경을 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitch("다크 모드", settings.darkMode) { onChange(settings.copy(darkMode = it)) }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            SectionTitle("글자 크기")
            Text("문맥 영역 ${settings.contextFontSp.roundToInt()}sp")
            Slider(
                value = settings.contextFontSp,
                onValueChange = { onChange(settings.copy(contextFontSp = it)) },
                valueRange = 14f..34f
            )
            Text("안구 이동 글자 ${settings.rsvpFontSp.roundToInt()}sp")
            Slider(
                value = settings.rsvpFontSp,
                onValueChange = { onChange(settings.copy(rsvpFontSp = it)) },
                valueRange = 18f..60f
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            SectionTitle("읽기 리듬")
            SettingSwitch("문장부호 추가 지연", settings.punctuationDelay) { onChange(settings.copy(punctuationDelay = it)) }
            Text(
                "쉼표·가운데점 등은 약간, 마침표·물음표·느낌표는 더 길게 보여줍니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            SettingSwitch("긴 단어 추가 시간", settings.longWordDelay) { onChange(settings.copy(longWordDelay = it)) }
            Text(
                "단어 표시에서 한글 6글자·영문 11글자부터 시간을 늘립니다. 글자 표시는 적용하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            SectionTitle("TTS")
            val info = ttsInfo
            val defaultLabel = info?.engines
                ?.firstOrNull { it.packageName == info.defaultEnginePackage }
                ?.label
            val engineChoices = buildList {
                add(
                    SettingChoice(
                        value = null,
                        label = if (defaultLabel.isNullOrBlank()) "시스템 기본 TTS" else "시스템 기본 TTS (" + defaultLabel + ")"
                    )
                )
                info?.engines.orEmpty().forEach { add(SettingChoice(it.packageName, it.label)) }
            }
            val currentEngineLabel = engineChoices
                .firstOrNull { it.value == settings.ttsEnginePackage }
                ?.label
                ?: "시스템 기본 TTS"
            SettingPicker(
                label = "TTS 엔진",
                selectedLabel = currentEngineLabel,
                choices = engineChoices,
                onSelect = { packageName ->
                    if (packageName != settings.ttsEnginePackage) {
                        ttsStatus = null
                        onChange(settings.copy(ttsEnginePackage = packageName, ttsVoiceName = null))
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            val voiceChoices = buildList {
                add(SettingChoice(value = null, label = "엔진 기본 음성"))
                info?.voices.orEmpty().forEach { add(SettingChoice(it.name, it.label)) }
            }
            val currentVoiceLabel = voiceChoices
                .firstOrNull { it.value == settings.ttsVoiceName }
                ?.label
                ?: "엔진 기본 음성"
            SettingPicker(
                label = "음성",
                selectedLabel = currentVoiceLabel,
                choices = voiceChoices,
                onSelect = { voiceName ->
                    ttsStatus = null
                    onChange(settings.copy(ttsVoiceName = voiceName))
                }
            )
            Spacer(Modifier.height(10.dp))
            Text("TTS 배속 " + String.format(java.util.Locale.US, "%.1f×", settings.ttsRate))
            Slider(
                value = settings.ttsRate,
                onValueChange = {
                    val rate = ((it * 10).roundToInt() / 10f).coerceIn(0.5f, 2.0f)
                    onChange(settings.copy(ttsRate = rate))
                },
                valueRange = 0.5f..2.0f,
                steps = 14
            )
            Text(
                "설치된 시스템 TTS 엔진과 해당 엔진이 제공하는 음성을 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ttsStatus != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ttsStatus ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}


private data class SettingChoice(
    val value: String?,
    val label: String
)

@Composable
private fun SettingPicker(
    label: String,
    selectedLabel: String,
    choices: List<SettingChoice>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        expanded = false
                        onSelect(choice.value)
                    }
                )
            }
        }
    }
}
