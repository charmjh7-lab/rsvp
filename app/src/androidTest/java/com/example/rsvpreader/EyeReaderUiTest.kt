package com.example.rsvpreader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.ParcelFileDescriptor

@RunWith(AndroidJUnit4::class)
class EyeReaderUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun openReader(text: String = "안녕하세요 만나서 반갑습니다. 오늘은 눈을 움직이며 읽습니다.") {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DocumentStore(context)
        store.listDocuments().forEach { store.delete(it.id) }
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
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pipe = automation.executeShellCommandRw("dd of=/sdcard/Download/Eye-Reader-$name.png")
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { it.readBytes() }
        bitmap.recycle()
    }

    @Test fun modesNavigationAndPausePreserveReading() {
        openReader()
        rule.onNodeWithContentDescription("읽기 모드 선택").performClick()
        rule.onNodeWithText("안구 이동 읽기").assertDoesNotExist()
        screenshot("fixed-first")
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.onNodeWithContentDescription("안녕하세요 만나서 반갑습니다.").assertExists()
        screenshot("fixed-kept")
        rule.onNodeWithContentDescription("읽기 모드 선택").performTouchInput { longClick() }
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.onNodeWithContentDescription("만나서 반갑습니다.").assertExists()
        screenshot("fixed-current")
        rule.onNodeWithContentDescription("읽기 모드 선택").performTouchInput { longClick() }
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

    @Test fun fixedLayoutCoordinatesAndLeftRightTargetsStayStable() {
        openReader()
        rule.onNodeWithContentDescription("읽기 모드 선택").performClick()
        rule.onNodeWithText("안구 이동 읽기").assertDoesNotExist()
        rule.waitForIdle()
        val first = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.waitForIdle()
        val second = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        var initialFocusPixels = 0
        var nextFocusPixels = 0
        var changedPixels = 0
        fun ink(pixel: Int): Boolean = android.graphics.Color.red(pixel) > 150 &&
            android.graphics.Color.green(pixel) in 20..150 && android.graphics.Color.blue(pixel) < 100
        for (y in 0 until first.height) for (x in 0 until first.width) {
            val before = first.getPixel(x, y)
            val after = second.getPixel(x, y)
            if (ink(before)) initialFocusPixels++
            if (ink(after)) nextFocusPixels++
            if (before != after) changedPixels++
        }
        org.junit.Assert.assertTrue("First current word must be highlighted", initialFocusPixels > 50)
        org.junit.Assert.assertTrue("Next current word must be highlighted", nextFocusPixels > 50)
        org.junit.Assert.assertTrue("Highlight must move to the next word", changedPixels > 50)
        screenshot("fixed-coordinate-check")
        rule.onNodeWithContentDescription("읽기 모드 선택").performTouchInput { longClick() }
        rule.onNodeWithText("좌우 교대", useUnmergedTree = true).performClick()
        rule.onNodeWithText("글자 단위", useUnmergedTree = true).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.waitForIdle()
        val left = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.waitForIdle()
        val right = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        val leftInk = (0 until left.height).sumOf { y -> (0 until left.width / 2).count { x -> ink(left.getPixel(x,y)) } }
        val rightInk = (0 until right.height).sumOf { y -> (right.width / 2 until right.width).count { x -> ink(right.getPixel(x,y)) } }
        org.junit.Assert.assertTrue(leftInk > 50)
        org.junit.Assert.assertTrue(rightInk > 50)
        screenshot("alternating-side-check")
    }

    @Test fun characterPlaybackReachesFinalCharacterAndStops() {
        openReader("가나 다라.")
        rule.onNodeWithContentDescription("읽기 모드 선택").performTouchInput { longClick() }
        rule.onNodeWithText("글자 단위", useUnmergedTree = true).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.onNodeWithContentDescription("재생").performClick()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithContentDescription(".").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithContentDescription("재생").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("이전 토큰").performClick()
        rule.onNodeWithContentDescription("라", substring = true).assertExists()
        screenshot("character-completed")
    }


    @Test fun tapSwitchesModeAndLongPressOnlyOpensSettings() {
        openReader()
        val button = rule.onNodeWithContentDescription("읽기 모드 선택")
        button.performClick()
        rule.onNodeWithText("자리 채우기 · 단어 · 앞부분 유지 · 3단어 공개").assertExists()
        rule.onNodeWithText("안구 이동 읽기").assertDoesNotExist()
        button.performTouchInput { longClick() }
        rule.onNodeWithText("안구 이동 읽기").assertExists()
        rule.onNodeWithText("자리 채우기", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("글자 단위", useUnmergedTree = true).performClick()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("적용").performClick()
        rule.onNodeWithText("자리 채우기 · 글자 · 이동 창 · 3글자 공개").assertExists()
        rule.onNodeWithContentDescription("다음 토큰").performClick()
        rule.onNodeWithContentDescription("녕하세").assertExists()
        button.performClick()
        rule.onNodeWithText("좌우 교대 · 글자").assertExists()
        rule.onNodeWithContentDescription("녕").assertExists()
        button.performClick()
        rule.onNodeWithText("자리 채우기 · 글자 · 이동 창 · 3글자 공개").assertExists()
        rule.onNodeWithContentDescription("녕하세").assertExists()
        rule.onNodeWithText("안구 이동 읽기").assertDoesNotExist()
        screenshot("fixed-character-tap-switch")
    }

    @Test fun blankPositionGuidesStayPutForWordsAndCombinedCharacters() {
        val doc = TextProcessor.process("안녕하세요 만나서 반갑습니다 cafe\u0301")
        var unit by mutableStateOf(EyeReadingUnit.WORD)
        var active by mutableIntStateOf(0)
        var revealed by mutableStateOf(false)
        rule.activity.setContent {
            val slices = doc.tokens.flatMap { eyeReadingSlices(it, unit) }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    EyeReadingDisplay(doc.sentences.single(), slices[active], slices, true, revealed,
                        AppSettings(eyeMode = EyeReadingMode.FIXED_POSITION, eyeUnit = unit, keepPrevious = false, eyePreviewCount = 1),
                        Modifier.fillMaxWidth().height(300.dp))
                }
            }
        }
        rule.waitForIdle()
        val blank = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        fun guide(pixel: Int): Boolean {
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            return r in 110..220 && maxOf(r, g, b) - minOf(r, g, b) < 20
        }
        val positions = buildList {
            for (y in 0 until blank.height) for (x in 0 until blank.width) {
                if (guide(blank.getPixel(x, y))) add(x to y)
            }
        }
        org.junit.Assert.assertTrue("Empty words already have position guides", positions.size > 100)
        screenshot("fixed-word-blank-guides")
        rule.runOnIdle { revealed = true }
        rule.waitForIdle()
        val first = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        rule.runOnIdle { active = 1 }
        rule.onNodeWithContentDescription("만나서").assertExists()
        val next = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        positions.forEach { (x, y) ->
            org.junit.Assert.assertEquals("First reveal moved a guide", blank.getPixel(x, y), first.getPixel(x, y))
            org.junit.Assert.assertEquals("Hiding the previous word moved a guide", blank.getPixel(x, y), next.getPixel(x, y))
        }
        fun maxRuns(bitmap: Bitmap): Int = (0 until bitmap.height).maxOf { y ->
            var runs = 0
            var previous = false
            for (x in 0 until bitmap.width) {
                val current = guide(bitmap.getPixel(x, y))
                if (current && !previous) runs++
                previous = current
            }
            runs
        }
        rule.runOnIdle { unit = EyeReadingUnit.CHARACTER; active = 0; revealed = false }
        rule.waitForIdle()
        val characters = rule.onNodeWithTag("eye-stage").captureToImage().asAndroidBitmap()
        org.junit.Assert.assertTrue("Character guides are separated into more positions", maxRuns(characters) > maxRuns(blank))
        screenshot("fixed-character-blank-guides")
    }

    @Test fun settingsPersistWithoutSharingOriginalAppIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assert.assertEquals("com.example.eyemovementreader", context.packageName)
        val store = AppSettingsStore(context)
        val settings = AppSettings(eyeMode = EyeReadingMode.FIXED_POSITION, eyeUnit = EyeReadingUnit.CHARACTER,
            keepPrevious = false, eyePreviewCount = 4, wordRate = 90, characterRate = 110)
        store.save(settings)
        org.junit.Assert.assertEquals(settings, store.load())
    }
}
