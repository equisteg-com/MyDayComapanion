package com.equisteg.mydaycompanion.ai

import com.equisteg.mydaycompanion.domain.ai.LocalLlmEngine
import com.equisteg.mydaycompanion.domain.ai.LocalModelControl
import com.equisteg.mydaycompanion.domain.ai.LocalModelStatus
import com.equisteg.mydaycompanion.domain.ai.ModelDownload
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Gemini Nano through ML Kit's Prompt API (build doc D-03). The model runs inside Android's
 * AICore system service on supported phones; this app has no INTERNET permission, and the
 * model download is done by AICore itself. Output goes back to the orchestrator as untrusted
 * text and is schema-validated there.
 */
class MlKitNanoEngine : LocalLlmEngine, LocalModelControl {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override val version: String = "mlkit-genai-prompt/1.0.0-beta4"

    override suspend fun status(): LocalModelStatus = when (model.checkStatus()) {
        FeatureStatus.AVAILABLE -> LocalModelStatus.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> LocalModelStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> LocalModelStatus.DOWNLOADING
        else -> LocalModelStatus.UNAVAILABLE
    }

    override suspend fun generate(prompt: String, maxOutputTokens: Int): String {
        val limit = maxOutputTokens
        val request = generateContentRequest(TextPart(prompt)) {
            // Extraction wants the same answer every time, not creativity.
            temperature = 0f
            topK = 1
            seed = 0
            candidateCount = 1
            this.maxOutputTokens = limit
        }
        return model.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
    }

    override fun download(): Flow<ModelDownload> = model.download()
        .map { s ->
            when (s) {
                is DownloadStatus.DownloadStarted -> ModelDownload.Started
                is DownloadStatus.DownloadProgress -> ModelDownload.Progress(s.totalBytesDownloaded)
                is DownloadStatus.DownloadFailed -> ModelDownload.Failed
                else -> if (s == DownloadStatus.DownloadCompleted) ModelDownload.Done else ModelDownload.Started
            }
        }
        .catch { emit(ModelDownload.Failed) }
}
