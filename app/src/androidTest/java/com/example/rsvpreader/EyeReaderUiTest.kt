package com.example.rsvpreader

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EyeReaderUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun openReader(text: String = "안녕하세요 만나서 반갑습니다. 오늘은 눈을 움직이며 읽습니다.") {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DocumentStore(context)
        val entry = (store.addDocument("안구 이동 테스트", SourceType.PASTE, null, text) as ImportResult.Success).entry
        rule.activity.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            MaterialTheme {
                ReaderScreen(entry.id, store, remember { ReaderSessionHolder() }, settings,
                    { settings = it }, {}, {})
            }
        }
        rule.waitForIdle()
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun modesNavigationAndPausePreserveReading() {
        openReader()
        rule.onNodeWithContentDescription("읽기 모드 선택").performClick()
        rule.onNodeWithText("자리 채우기", useUnmergedTree = true).performClick()
        rule.onNodeWithText("적용").performClick()
        screenshot("fixed-first")
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.onNodeWithContentDescription("안녕하세요 만나서").assertExists()
        screenshot("fixed-kept")
        rule.onNodeWithContentDescription("읽기 모드 선택").performClick()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.onNodeWithContentDescription("만나서").assertExists()
        screenshot("fixed-current")
        rule.onNodeWithContentDescription("읽기 모드 선택").performClick()
        rule.onNodeWithText("좌우 교대", useUnmergedTree = true).performClick()
        rule.onNodeWithText("글자 단위", useUnmergedTree = true).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.onNodeWithContentDescription("만").assertExists()
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.onNodeWithContentDescription("나").assertExists()
        screenshot("alternating-character-right")
        rule.onNodeWithContentDescription("이전 토큰").performClick()
        rule.onNodeWithContentDescription("만").assertExists()
        rule.onNodeWithText("120/분").assertExists()
        rule.onNodeWithContentDescription("재생").performClick()
        rule.onNodeWithContentDescription("일시정지").performClick()
        rule.onNodeWithContentDescription("재생").assertExists()
    }

    @Test fun settingsPersistWithoutSharingOriginalAppIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assert.assertEquals("com.example.eyemovementreader", context.packageName)
        val store = AppSettingsStore(context)
        val settings = AppSettings(eyeMode = EyeReadingMode.FIXED_POSITION, eyeUnit = EyeReadingUnit.CHARACTER,
            keepPrevious = false, wordRate = 90, characterRate = 110)
        store.save(settings)
        org.junit.Assert.assertEquals(settings, store.load())
    }
}
