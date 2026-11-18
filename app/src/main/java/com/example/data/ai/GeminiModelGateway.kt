package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "GeminiModelGateway"
private const val GEMINI_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_IMAGE_BYTES = 18 * 1024 * 1024 // < 20MB inline limit

interface GeminiModelGateway {
    suspend fun generateClinicalJson(
        systemInstruction: String,
        prompt: String,
        image: Bitmap? = null
    ): String?
}

class FirebaseGeminiModelGateway : GeminiModelGateway {

    private val clinicalResponseSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Concise clinical section title in requested language"),
                "verifiedClinicalFacts" to Schema.array(
                    items = Schema.string(),
                    description = "Facts strictly from CLINICIAN_VERIFIED or ORGANIZATION_SOURCED record entries"
                ),
                "patientReportedAndUnverifiedInfo" to Schema.array(
                    items = Schema.string(),
                    description = "Entries from PATIENT_REPORTED, DOCUMENT_EXTRACTED, or NEEDS_REVIEW records"
                ),
                "aiInterpretation" to Schema.string(
                    description = "AI clinical synthesis and terminology explanation without prescriptions or dosing orders"
                ),
                "clinicianRecommendations" to Schema.array(
                    items = Schema.string(),
                    description = "Advisory review considerations ONLY for verified clinicians; empty [] otherwise"
                ),
                "extractedEntities" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "category" to Schema.string(description = "Entity category e.g. Medication, Lab, Diagnosis, Vital"),
                            "label" to Schema.string(description = "Normalized field label"),
                            "value" to Schema.string(description = "Extracted value"),
                            "provenanceNote" to Schema.string(description = "Document extraction provenance note"),
                            "requiresClinicianVerification" to Schema.boolean(description = "Always true for unverified extractions")
                        )
                    ),
                    description = "Structured clinical items extracted from documents or images"
                ),
                "missingOrUncertainData" to Schema.array(
                    items = Schema.string(),
                    description = "Missing baseline data, incomplete labs, or reconciliation uncertainties"
                ),
                "citedSourceIds" to Schema.array(
                    items = Schema.string(),
                    description = "Exact source IDs verbatim from SOURCE_CATALOG"
                )
            ),
            optionalProperties = listOf("extractedEntities", "clinicianRecommendations")
        )
    }

    private val clinicalConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = clinicalResponseSchema
            temperature = 0.2f
        }
    }

    private val defaultModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = clinicalConfig
        )
    }

    override suspend fun generateClinicalJson(
        systemInstruction: String,
        prompt: String,
        image: Bitmap?
    ): String? {
        return try {
            val safeImage = image?.let(::ensureUnderInlinePayloadLimit)
            val response = withTimeout(GEMINI_TIMEOUT_MS) {
                defaultModel.generateContent(
                    content {
                        text(systemInstruction)
                        text(prompt)
                        if (safeImage != null) {
                            image(safeImage)
                        }
                    }
                )
            }
            response.text
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini clinical generation timed out after 60 seconds", e)
            throw AiInferenceException(
                AiFailureReason.TIMEOUT,
                "Gemini request timed out after 60 seconds. Please retry.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic inference failed", e)
            throw AiInferenceException(
                AiFailureReason.NETWORK_OR_APP_CHECK_ERROR,
                e.localizedMessage ?: "Firebase AI Logic request failed.",
                e
            )
        }
    }

    private fun ensureUnderInlinePayloadLimit(bitmap: Bitmap): Bitmap {
        var current = bitmap
        val stream = ByteArrayOutputStream()
        current.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        if (stream.size() <= MAX_INLINE_IMAGE_BYTES) return current

        val maxDim = max(current.width, current.height)
        val scale = if (maxDim > 1600) 1600f / maxDim.toFloat() else 0.7f
        val targetW = (current.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (current.height * scale).roundToInt().coerceAtLeast(1)
        current = Bitmap.createScaledBitmap(current, targetW, targetH, true)
        return current
    }
}
