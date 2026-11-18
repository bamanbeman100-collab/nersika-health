package com.example.data.ai

import com.example.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object AiOutputValidator {

    private val autonomousRxOrDoseRegex = Regex(
        pattern = """(?i)(\b(prescribe|prescription\s*:|rx\s*:|order\s+medication|dispense\s+\d+|start\s+[a-z0-9-]+\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units)|initiate\s+[a-z0-9-]+\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units)|increase\s+.{0,30}\bto\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units)|take\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units|tablet|tablets)|give\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units)|administer\s+\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|units))\b|\d+(?:\.\d+)?\s*mg\s*/\s*kg|\bcalculated\s+dose\b|\brecommended\s+dose\s*:|\btarget\s+dose\s+\d+\s*(?:mg|mcg|ml|units)|\bdosing\s+regimen\s*:|تجویز می‌کنم|نسخه:|مصرف کنید|شروع دارو با دوز|افزایش دوز به|دوز پیشنهادی|میلی‌گرم بر کیلوگرم|دوز محاسبه‌شده)"""
    )

    private val fabricatedReferenceRegex = Regex(
        pattern = """(?i)(\bpmid\b|\bdoi\s*:|10\.\d{4,9}/|\bet\s+al\.|https?://|\bnejm\b|\blancet\b|\bjama\b|\bpubmed\b)"""
    )

    fun parseAndValidate(
        rawJson: String?,
        request: AiClinicalRequest,
        promptContext: BuiltAiPrompt
    ): AiClinicalResponse {
        if (rawJson.isNullOrBlank()) {
            throw AiValidationException(
                AiFailureReason.EMPTY_RESPONSE,
                "Gemini returned an empty response."
            )
        }

        val json = try {
            JSONObject(rawJson)
        } catch (e: JSONException) {
            throw AiValidationException(
                AiFailureReason.MALFORMED_JSON,
                "Malformed structured JSON output from AI model: ${e.message}"
            )
        }

        val title = json.optString("title", "").trim()
        val interpretation = json.optString("aiInterpretation", "").trim()
        if (title.isBlank() || interpretation.isBlank()) {
            throw AiValidationException(
                AiFailureReason.MISSING_REQUIRED_PROVENANCE,
                "AI output omitted required title or clinical interpretation."
            )
        }

        val verifiedFacts = json.optStringList("verifiedClinicalFacts")
        val patientReported = json.optStringList("patientReportedAndUnverifiedInfo")
        val rawRecommendations = json.optStringList("clinicianRecommendations")
        val missingData = json.optStringList("missingOrUncertainData")
        val citedIds = json.optStringList("citedSourceIds").distinct()
        val extracted = json.optExtractedEntities("extractedEntities")

        // 1. Validate references & prevent fabricated citations
        if (citedIds.isEmpty()) {
            throw AiValidationException(
                AiFailureReason.FABRICATED_REFERENCE,
                "AI response omitted verifiable record source citations."
            )
        }

        val unknownIds = citedIds.filterNot { it in promptContext.sourceCatalog.allowedIds }
        if (unknownIds.isNotEmpty()) {
            throw AiValidationException(
                AiFailureReason.FABRICATED_REFERENCE,
                "Blocked unverified or fabricated source reference(s): ${unknownIds.joinToString()}"
            )
        }

        val allTextToScanForCitations = buildList {
            add(title)
            add(interpretation)
            addAll(verifiedFacts)
            addAll(patientReported)
            addAll(rawRecommendations)
            addAll(missingData)
            extracted.forEach {
                add(it.label)
                add(it.value)
                add(it.provenanceNote)
            }
        }
        if (allTextToScanForCitations.any { fabricatedReferenceRegex.containsMatchIn(it) }) {
            throw AiValidationException(
                AiFailureReason.FABRICATED_REFERENCE,
                "Blocked external or fabricated literature citation not present in authorized patient record."
            )
        }

        // 2. Prevent autonomous prescriptions and unverified patient-specific dosing
        val directiveTexts = buildList {
            add(interpretation)
            addAll(rawRecommendations)
            addAll(patientReported)
        }
        if (directiveTexts.any { autonomousRxOrDoseRegex.containsMatchIn(it) }) {
            throw AiValidationException(
                AiFailureReason.UNSAFE_PRESCRIPTION_OR_DOSE,
                "Safety gate blocked autonomous prescription directive or unverified patient-specific dosage."
            )
        }

        // 3. Enforce verified clinician role separation for clinicianRecommendations
        val isVerifiedClinician = AiAccessPolicy.isVerifiedClinician(request.actor)
        val filteredRecommendations = if (
            request.taskType == AiTaskType.CLINICIAN_RECOMMENDATIONS && isVerifiedClinician
        ) {
            if (rawRecommendations.isEmpty()) {
                throw AiValidationException(
                    AiFailureReason.MISSING_REQUIRED_PROVENANCE,
                    "Clinician recommendation summary did not include any advisory review items."
                )
            }
            rawRecommendations
        } else {
            emptyList()
        }

        // 4. Preserve deterministic baseline missing-data checks alongside model output
        val mergedMissingData = (missingData + promptContext.deterministicMissingGaps)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val reviewNotice = if (request.language == AppLanguage.FA) {
            "نیازمند بررسی و تایید پزشک یا متخصص مجاز — خروجی هوش مصنوعی دستور دارویی یا تشخیص قطعی نیست."
        } else {
            "Requires Authorized Clinician Review — AI synthesis and recommendations are advisory only and never medical orders."
        }

        return AiClinicalResponse(
            taskType = request.taskType,
            title = title,
            verifiedClinicalFacts = verifiedFacts,
            patientReportedAndUnverifiedInfo = patientReported,
            aiInterpretation = interpretation,
            clinicianRecommendations = filteredRecommendations,
            extractedEntities = extracted,
            missingOrUncertainData = mergedMissingData,
            citedSourceIds = citedIds,
            citedSources = promptContext.sourceCatalog.resolveAll(citedIds),
            requiresClinicianReview = true,
            clinicianReviewNotice = reviewNotice
        )
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { idx -> arr.optString(idx, "").trim().takeIf { it.isNotEmpty() } }
    }

    private fun JSONObject.optExtractedEntities(key: String): List<ExtractedClinicalEntity> {
        val arr: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val label = obj.optString("label", "").trim()
            val value = obj.optString("value", "").trim()
            if (label.isBlank() || value.isBlank()) return@mapNotNull null
            ExtractedClinicalEntity(
                category = obj.optString("category", "General").trim().ifBlank { "General" },
                label = label,
                value = value,
                provenanceNote = obj.optString("provenanceNote", "Document extracted — Needs Review").trim(),
                requiresClinicianVerification = true
            )
        }
    }
}
