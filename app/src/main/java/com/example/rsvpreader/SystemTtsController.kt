package com.example.rsvpreader

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

data class TtsEngineOption(
    val packageName: String,
    val label: String
)

data class TtsVoiceOption(
    val name: String,
    val localeTag: String,
    val label: String
)

data class TtsReadyInfo(
    val defaultEnginePackage: String?,
    val engines: List<TtsEngineOption>,
    val voices: List<TtsVoiceOption>,
    val selectedVoiceAvailable: Boolean,
    val fellBackToDefault: Boolean
)

class SystemTtsController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var generation = 0
    private var utteranceCounter = 0
    private var ready = false

    private var activeUtteranceId: String? = null
    private var activeSentenceIndex: Int = -1
    private var activeGlobalCharStart: Int = 0

    var onRangeStart: ((sentenceIndex: Int, globalStart: Int, globalEnd: Int) -> Unit)? = null
    var onDone: ((sentenceIndex: Int) -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null

    val isReady: Boolean get() = ready

    fun initialize(
        enginePackage: String?,
        voiceName: String?,
        speechRate: Float,
        onReady: (TtsReadyInfo) -> Unit,
        onMessage: (String) -> Unit
    ) {
        createEngine(
            requestedEngine = enginePackage,
            requestedVoice = voiceName,
            speechRate = speechRate.coerceIn(0.5f, 2.0f),
            fellBackToDefault = false,
            onReady = onReady,
            onMessage = onMessage
        )
    }

    private fun createEngine(
        requestedEngine: String?,
        requestedVoice: String?,
        speechRate: Float,
        fellBackToDefault: Boolean,
        onReady: (TtsReadyInfo) -> Unit,
        onMessage: (String) -> Unit
    ) {
        val myGeneration = ++generation
        ready = false
        activeUtteranceId = null
        tts?.stop()
        tts?.shutdown()
        tts = null

        var created: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            mainHandler.post {
                if (myGeneration != generation) {
                    created?.shutdown()
                    return@post
                }

                val engine = tts ?: created
                if (engine == null || status != TextToSpeech.SUCCESS) {
                    engine?.shutdown()
                    tts = null
                    ready = false
                    if (requestedEngine != null) {
                        onMessage("선택한 TTS 엔진을 사용할 수 없어 시스템 기본 TTS로 전환했습니다.")
                        createEngine(
                            requestedEngine = null,
                            requestedVoice = null,
                            speechRate = speechRate,
                            fellBackToDefault = true,
                            onReady = onReady,
                            onMessage = onMessage
                        )
                    } else {
                        onError?.invoke("시스템 TTS를 초기화할 수 없습니다.")
                        onMessage("시스템 TTS를 사용할 수 없습니다.")
                    }
                    return@post
                }

                val engines = engine.engines
                    .map { TtsEngineOption(it.name, it.label ?: it.name) }
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }

                if (requestedEngine != null && engines.none { it.packageName == requestedEngine }) {
                    engine.shutdown()
                    tts = null
                    ready = false
                    onMessage("선택한 TTS 엔진이 설치되어 있지 않아 시스템 기본 TTS로 전환했습니다.")
                    createEngine(
                        requestedEngine = null,
                        requestedVoice = null,
                        speechRate = speechRate,
                        fellBackToDefault = true,
                        onReady = onReady,
                        onMessage = onMessage
                    )
                    return@post
                }

                installProgressListener(engine, myGeneration)
                engine.setSpeechRate(speechRate)

                val rawVoices = engine.voices?.toList().orEmpty()
                val voices = rawVoices
                    .map { voice ->
                        val localeName = voice.locale?.getDisplayName(Locale.getDefault())
                            ?.takeIf { it.isNotBlank() }
                            ?: voice.locale?.toLanguageTag().orEmpty()
                        TtsVoiceOption(
                            name = voice.name,
                            localeTag = voice.locale?.toLanguageTag().orEmpty(),
                            label = if (localeName.isBlank()) voice.name else localeName + " · " + voice.name
                        )
                    }
                    .sortedWith(compareBy<TtsVoiceOption> { it.localeTag }.thenBy { it.name })

                var selectedVoiceAvailable = requestedVoice == null
                if (requestedVoice != null) {
                    val selected = rawVoices.firstOrNull { it.name == requestedVoice }
                    if (selected != null && engine.setVoice(selected) == TextToSpeech.SUCCESS) {
                        selectedVoiceAvailable = true
                    } else {
                        onMessage("저장된 TTS 음성을 찾을 수 없어 엔진 기본 음성을 사용합니다.")
                    }
                }

                ready = true
                onReady(
                    TtsReadyInfo(
                        defaultEnginePackage = engine.defaultEngine,
                        engines = engines,
                        voices = voices,
                        selectedVoiceAvailable = selectedVoiceAvailable,
                        fellBackToDefault = fellBackToDefault
                    )
                )
            }
        }

        created = if (requestedEngine == null) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, requestedEngine)
        }
        tts = created
    }

    private fun installProgressListener(engine: TextToSpeech, listenerGeneration: Int) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (listenerGeneration != generation || utteranceId == null || utteranceId != activeUtteranceId) {
                        return@post
                    }
                    val sentence = activeSentenceIndex
                    activeUtteranceId = null
                    onDone?.invoke(sentence)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                dispatchError(listenerGeneration, utteranceId, "TTS 재생 중 오류가 발생했습니다.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                dispatchError(listenerGeneration, utteranceId, "TTS 재생 중 오류가 발생했습니다. (오류 " + errorCode + ")")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                mainHandler.post {
                    if (listenerGeneration != generation || utteranceId == null || utteranceId != activeUtteranceId) {
                        return@post
                    }
                    onRangeStart?.invoke(
                        activeSentenceIndex,
                        activeGlobalCharStart + start,
                        activeGlobalCharStart + end
                    )
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
        })
    }

    private fun dispatchError(listenerGeneration: Int, utteranceId: String?, message: String) {
        mainHandler.post {
            if (listenerGeneration != generation || utteranceId == null || utteranceId != activeUtteranceId) {
                return@post
            }
            activeUtteranceId = null
            onError?.invoke(message)
        }
    }

    fun setSpeechRate(rate: Float) {
        if (ready) tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun speak(text: String, globalCharStart: Int, sentenceIndex: Int): Boolean {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) return false

        val utteranceId = "rsvp_" + generation + "_" + (++utteranceCounter)
        activeUtteranceId = utteranceId
        activeSentenceIndex = sentenceIndex
        activeGlobalCharStart = globalCharStart

        val result = engine.speak(
            text as CharSequence,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            onError?.invoke("TTS가 이 문장을 재생하지 못했습니다.")
            return false
        }
        return true
    }

    fun stop() {
        activeUtteranceId = null
        tts?.stop()
    }

    fun shutdown() {
        ++generation
        ready = false
        activeUtteranceId = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        onRangeStart = null
        onDone = null
        onError = null
    }
}
