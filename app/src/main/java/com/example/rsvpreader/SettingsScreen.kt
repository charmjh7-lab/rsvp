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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
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
            SectionTitle("집중 글자")
            SettingSwitch("집중 글자 색상 표시", settings.showFocusColor) { onChange(settings.copy(showFocusColor = it)) }
            Text("집중색", style = MaterialTheme.typography.labelLarge)
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
            Text("RSVP 단어 ${settings.rsvpFontSp.roundToInt()}sp")
            Slider(
                value = settings.rsvpFontSp,
                onValueChange = { onChange(settings.copy(rsvpFontSp = it)) },
                valueRange = 28f..80f
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
                "13 grapheme 이상인 토큰에 약 30%의 추가 표시 시간을 줍니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            SectionTitle("화면")
            SettingSwitch("다크 모드", settings.darkMode) { onChange(settings.copy(darkMode = it)) }
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
