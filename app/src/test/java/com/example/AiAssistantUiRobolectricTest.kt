package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.ai.AiClinicalResponse
import com.example.data.ai.AiFailureReason
import com.example.data.ai.AiTaskType
import com.example.data.ai.ExtractedClinicalEntity
import com.example.data.ai.SourceCatalogEntry
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.PatientEntity
import com.example.data.local.UserEntity
import com.example.domain.model.ClinicalSafetyEngine
import com.example.domain.model.DocumentCategory
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage
import com.example.i18n.EnStrings
import com.example.i18n.FaStrings
import com.example.ui.AiUiState
import com.example.ui.PatientBundle
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.theme.NersikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiAssistantUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testUser(role: UserRole, verifiedClinician: Boolean) = UserEntity(
        id = "usr_${role.name}",
        email = "user@demo.nersika.health",
        passwordHash = "h",
        passwordSalt = "s",
        firstName = "Test",
        lastName = role.name,
        role = role.name,
        country = "Iran",
        preferredLanguage = "en",
        emailVerified = true,
        verificationTokenHash = null,
        verificationExpiresAtMillis = 0L,
        verificationSentAtMillis = 0L,
        verificationAttempts = 0,
        resetTokenHash = null,
        resetExpiresAtMillis = 0L,
        resetAttempts = 0,
        failedLoginAttempts = 0,
        lockedUntilMillis = 0L,
        tokenVersion = 1,
        onboardingCompleted = true,
        mfaEnabled = false,
        organizationName = "Clinic",
        isVerifiedClinician = verifiedClinician,
        isDemo = true,
        linkedPatientId = if (role == UserRole.PATIENT) "pat_001" else null,
        createdAtMillis = 1000L
    )

    private fun testBundle(consentAi: Boolean = true): PatientBundle {
        val patient = PatientEntity(
            id = "pat_001",
            mrn = "NRK-10482",
            linkedUserId = "usr_PATIENT",
            firstNameEn = "Sara",
            lastNameEn = "Bahrami",
            firstNameFa = "سارا",
            lastNameFa = "بهرامی",
            dateOfBirthIso = "1972-04-12",
            ageYears = 54,
            sex = "Female",
            populationGroup = PopulationGroup.ADULT.name,
            bloodType = "A+",
            phone = "+98 912 000 1042",
            emergencyContact = "Darius Bahrami",
            weightKg = 68.5,
            isPregnant = false,
            consentClinicianAccess = true,
            consentCaregiverAccess = true,
            consentAiSummarization = consentAi,
            verifiedIdentity = true,
            profileCompletionPct = 92
        )
        val docs = listOf(
            MedicalDocumentEntity(
                id = "doc_lab_pdf",
                patientId = patient.id,
                titleEn = "Cardiology Clinic Letter",
                titleFa = "نامه کلینیک قلب",
                category = DocumentCategory.CLINICAL_NOTE.name,
                fileUri = "synthetic://cardiology.pdf",
                mimeType = "application/pdf",
                fileSizeKb = 180L,
                sourceType = SourceType.DOCUMENT_EXTRACTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                extractedSummaryEn = "BP 134/82 mmHg documented on external visit.",
                extractedSummaryFa = "فشار خون ۱۳۴/۸۲ ثبت شده",
                uploadedBy = "Sara",
                uploadedAtMillis = 1_760_000_000_000L
            )
        )
        val cds = ClinicalSafetyEngine.evaluatePatient(patient, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return PatientBundle(
            patient = patient,
            conditions = emptyList(),
            medicalHistory = emptyList(),
            allergies = emptyList(),
            medications = emptyList(),
            labs = emptyList(),
            observations = emptyList(),
            documents = docs,
            carePlans = emptyList(),
            appointments = emptyList(),
            decisions = emptyList(),
            patientAuditEvents = emptyList(),
            cdsReport = cds
        )
    }

    @Test
    fun `screen distinguishes verified facts, patient-reported info, AI interpretation, clinician recommendations, and gates clinician chip`() {
        var currentUser by mutableStateOf(testUser(UserRole.PATIENT, verifiedClinician = false))
        var currentLang by mutableStateOf(AppLanguage.EN)
        var layoutDir: LayoutDirection? = null
        var lastTask: AiTaskType? = null
        var lastQuery = ""

        val richResponse = AiClinicalResponse(
            taskType = AiTaskType.CLINICIAN_RECOMMENDATIONS,
            title = "Clinician Clinical Synthesis",
            verifiedClinicalFacts = listOf("Verified HbA1c 7.4% [SRC:LAB_1]"),
            patientReportedAndUnverifiedInfo = listOf("Patient reported home fatigue [SRC:OBS_1]"),
            aiInterpretation = "Elevated HbA1c requires clinician review alongside symptom log.",
            clinicianRecommendations = listOf("Review recent renal function labs before medication adjustment."),
            extractedEntities = listOf(
                ExtractedClinicalEntity("Vital", "External BP", "134/82 mmHg", "From Cardiology Letter", true)
            ),
            missingOrUncertainData = listOf("Recent eGFR result missing"),
            citedSourceIds = listOf("SRC:LAB_1"),
            citedSources = listOf(
                SourceCatalogEntry("SRC:LAB_1", "Lab Result", "HbA1c 7.4%", "ORGANIZATION_SOURCED", "VERIFIED")
            ),
            requiresClinicianReview = true,
            clinicianReviewNotice = EnStrings.aiRequiresClinicianReviewBadge
        )

        var aiState by mutableStateOf<AiUiState>(AiUiState.Success(richResponse))

        composeRule.setContent {
            NersikaTheme(language = currentLang) {
                layoutDir = LocalLayoutDirection.current
                AiAssistantScreen(
                    user = currentUser,
                    bundle = testBundle(),
                    aiState = aiState,
                    onRunTask = { task, query, _, _ ->
                        lastTask = task
                        lastQuery = query
                    },
                    onRetryLast = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Patient does NOT see Clinician Advisory chip, sees gate notice
        assertEquals(LayoutDirection.Ltr, layoutDir)
        composeRule.onNodeWithTag("ai_chip_clinician_recs").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_clinician_gate_notice"))
        composeRule.onNodeWithTag("ai_clinician_gate_notice").assertIsDisplayed()

        // Switch to Verified Physician -> Clinician Advisory chip appears
        currentUser = testUser(UserRole.PHYSICIAN, verifiedClinician = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_chip_clinician_recs").assertIsDisplayed().performClick()
        assertEquals(AiTaskType.CLINICIAN_RECOMMENDATIONS, lastTask)

        // All provenance sections and mandatory review notice are visible cleanly
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_review_required_badge"))
        composeRule.onNodeWithTag("ai_review_required_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_section_verified_facts").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_section_patient_reported").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_extracted_entities"))
        composeRule.onNodeWithTag("ai_section_extracted_entities").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_interpretation"))
        composeRule.onNodeWithTag("ai_section_interpretation").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_clinician_recommendations"))
        composeRule.onNodeWithTag("ai_section_clinician_recommendations").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_missing_data"))
        composeRule.onNodeWithTag("ai_section_missing_data").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_sources_cited"))
        composeRule.onNodeWithTag("ai_section_sources_cited").assertIsDisplayed()

        // Source-aware Q&A input submits SOURCE_AWARE_RETRIEVAL
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_query_input"))
        composeRule.onNodeWithTag("ai_query_input").performTextInput("Check blood pressure note")
        composeRule.onNodeWithTag("ai_send_btn").performClick()
        assertEquals(AiTaskType.SOURCE_AWARE_RETRIEVAL, lastTask)
        assertEquals("Check blood pressure note", lastQuery)

        // Persian RTL localization
        currentLang = AppLanguage.FA
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, layoutDir)
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_section_verified_facts"))
        composeRule.onNodeWithText(FaStrings.aiSectionVerifiedFacts).assertIsDisplayed()
    }

    @Test
    fun `error banner, retry button, document extraction trigger, and consent-off banner render reliably`() {
        var retried = false
        var extractedDocId: String? = null
        var bundle by mutableStateOf(testBundle(consentAi = false))
        var aiState by mutableStateOf<AiUiState>(
            AiUiState.Error(EnStrings.aiErrTimeout, AiFailureReason.TIMEOUT)
        )

        composeRule.setContent {
            NersikaTheme(language = AppLanguage.EN) {
                AiAssistantScreen(
                    user = testUser(UserRole.PHYSICIAN, verifiedClinician = true),
                    bundle = bundle,
                    aiState = aiState,
                    onRunTask = { task, _, doc, _ ->
                        if (task == AiTaskType.EXTRACT_DOCUMENT) {
                            extractedDocId = doc?.id
                        }
                    },
                    onRetryLast = { retried = true }
                )
            }
        }
        composeRule.waitForIdle()

        // Consent disabled warning shown
        composeRule.onNodeWithTag("ai_consent_blocked_banner").assertIsDisplayed()

        // Stored Document Extraction button dispatches selected document
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_extract_stored_doc_btn"))
        composeRule.onNodeWithTag("ai_extract_stored_doc_btn").performClick()
        assertEquals("doc_lab_pdf", extractedDocId)

        // AI Failure banner & Retry
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToNode(hasTestTag("ai_error_banner"))
        composeRule.onNodeWithTag("ai_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText(EnStrings.aiErrTimeout).assertIsDisplayed()
        composeRule.onNodeWithTag("ai_retry_btn").performClick()
        assertTrue(retried)
    }
}
