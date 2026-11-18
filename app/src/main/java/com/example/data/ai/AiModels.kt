package com.example.data.ai

import android.graphics.Bitmap
import com.example.data.local.AllergyEntity
import com.example.data.local.CarePlanEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.LabResultEntity
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.MedicalHistoryEntity
import com.example.data.local.MedicationEntity
import com.example.data.local.ObservationEntity
import com.example.data.local.PatientEntity
import com.example.data.local.UserEntity
import com.example.domain.model.ClinicalAlert
import com.example.i18n.AppLanguage

enum class AiTaskType {
    SUMMARIZE_RECORD,
    EXTRACT_DOCUMENT,
    EXPLAIN_LABS,
    SUMMARIZE_MEDICATIONS,
    IDENTIFY_MISSING_DATA,
    SOURCE_AWARE_RETRIEVAL,
    CLINICIAN_RECOMMENDATIONS,
    ORGANIZE_SYMPTOMS
}

enum class AiFailureReason {
    UNAUTHENTICATED,
    EMAIL_NOT_VERIFIED,
    ONBOARDING_REQUIRED,
    CONSENT_AI_DISABLED,
    CLINICIAN_ROLE_REQUIRED,
    RECORD_ACCESS_DENIED,
    TIMEOUT,
    NETWORK_OR_APP_CHECK_ERROR,
    EMPTY_RESPONSE,
    MALFORMED_JSON,
    UNSAFE_PRESCRIPTION_OR_DOSE,
    FABRICATED_REFERENCE,
    MISSING_REQUIRED_PROVENANCE
}

class AiAccessDeniedException(
    val reason: AiFailureReason,
    message: String
) : Exception(message)

class AiValidationException(
    val reason: AiFailureReason,
    message: String
) : Exception(message)

class AiInferenceException(
    val reason: AiFailureReason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

data class ExtractedClinicalEntity(
    val category: String,
    val label: String,
    val value: String,
    val provenanceNote: String,
    val requiresClinicianVerification: Boolean = true
)

data class SourceCatalogEntry(
    val sourceId: String,
    val category: String,
    val label: String,
    val provenance: String,
    val verificationStatus: String
)

data class SourceCatalog(
    val entries: List<SourceCatalogEntry>
) {
    val allowedIds: Set<String> = entries.map { it.sourceId }.toSet()

    fun resolveAll(ids: List<String>): List<SourceCatalogEntry> {
        val byId = entries.associateBy { it.sourceId }
        return ids.mapNotNull { byId[it] }.distinctBy { it.sourceId }
    }
}

data class AiClinicalRequest(
    val taskType: AiTaskType,
    val language: AppLanguage,
    val actor: UserEntity?,
    val patient: PatientEntity,
    val conditions: List<ConditionEntity>,
    val medicalHistory: List<MedicalHistoryEntity> = emptyList(),
    val medications: List<MedicationEntity> = emptyList(),
    val allergies: List<AllergyEntity> = emptyList(),
    val labs: List<LabResultEntity> = emptyList(),
    val observations: List<ObservationEntity> = emptyList(),
    val documents: List<MedicalDocumentEntity> = emptyList(),
    val carePlans: List<CarePlanEntity> = emptyList(),
    val alerts: List<ClinicalAlert> = emptyList(),
    val customPrompt: String = "",
    val selectedDocument: MedicalDocumentEntity? = null,
    val documentBitmap: Bitmap? = null
)

data class AiClinicalResponse(
    val taskType: AiTaskType,
    val title: String,
    val verifiedClinicalFacts: List<String>,
    val patientReportedAndUnverifiedInfo: List<String>,
    val aiInterpretation: String,
    val clinicianRecommendations: List<String> = emptyList(),
    val extractedEntities: List<ExtractedClinicalEntity> = emptyList(),
    val missingOrUncertainData: List<String>,
    val citedSourceIds: List<String>,
    val citedSources: List<SourceCatalogEntry>,
    val requiresClinicianReview: Boolean = true,
    val clinicianReviewNotice: String,
    val modelName: String = "gemini-3.8-flash",
    val generatedAtMillis: Long = System.currentTimeMillis()
)
