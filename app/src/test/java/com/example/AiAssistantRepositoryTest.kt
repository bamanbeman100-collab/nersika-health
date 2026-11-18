package com.example

import android.graphics.Bitmap
import com.example.data.ai.AiAccessDeniedException
import com.example.data.ai.AiClinicalRequest
import com.example.data.ai.AiFailureReason
import com.example.data.ai.AiInferenceException
import com.example.data.ai.AiPromptBuilder
import com.example.data.ai.AiTaskType
import com.example.data.ai.AiValidationException
import com.example.data.ai.GeminiAiRepository
import com.example.data.ai.GeminiModelGateway
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
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LabFlag
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.MedicationStatus
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiAssistantRepositoryTest {

    private fun actor(
        role: UserRole = UserRole.PHYSICIAN,
        emailVerified: Boolean = true,
        onboardingCompleted: Boolean = true,
        isVerifiedClinician: Boolean = true,
        linkedPatientId: String? = null
    ) = UserEntity(
        id = "usr_${role.name.lowercase()}",
        email = "${role.name.lowercase()}@demo.nersika.health",
        passwordHash = "hash",
        passwordSalt = "salt",
        firstName = "Test",
        lastName = role.name,
        role = role.name,
        country = "Iran",
        preferredLanguage = "en",
        emailVerified = emailVerified,
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
        onboardingCompleted = onboardingCompleted,
        mfaEnabled = false,
        organizationName = "Nersika Hospital",
        isVerifiedClinician = isVerifiedClinician,
        isDemo = true,
        linkedPatientId = linkedPatientId,
        createdAtMillis = 1000L
    )

    private fun samplePatient(
        weightKg: Double = 68.5,
        consentClinician: Boolean = true,
        consentCaregiver: Boolean = true,
        consentAi: Boolean = true
    ) = PatientEntity(
        id = "pat_001",
        mrn = "NRK-10482",
        linkedUserId = "usr_patient",
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
        weightKg = weightKg,
        isPregnant = false,
        consentClinicianAccess = consentClinician,
        consentCaregiverAccess = consentCaregiver,
        consentAiSummarization = consentAi,
        verifiedIdentity = true,
        profileCompletionPct = 90
    )

    private fun baseRequest(
        taskType: AiTaskType = AiTaskType.SUMMARIZE_RECORD,
        language: AppLanguage = AppLanguage.EN,
        actor: UserEntity? = actor(),
        patient: PatientEntity = samplePatient(),
        withFullData: Boolean = true,
        customPrompt: String = "",
        selectedDoc: MedicalDocumentEntity? = null,
        bitmap: Bitmap? = null
    ): AiClinicalRequest {
        val now = 1_760_000_000_000L
        return if (!withFullData) {
            AiClinicalRequest(
                taskType = taskType,
                language = language,
                actor = actor,
                patient = patient,
                conditions = emptyList(),
                customPrompt = customPrompt,
                selectedDocument = selectedDoc,
                documentBitmap = bitmap
            )
        } else {
            AiClinicalRequest(
                taskType = taskType,
                language = language,
                actor = actor,
                patient = patient,
                conditions = listOf(
                    ConditionEntity(
                        id = "cond_1",
                        patientId = patient.id,
                        nameEn = "Type 2 Diabetes Mellitus",
                        nameFa = "دیابت نوع ۲",
                        icd10Code = "E11.9",
                        status = "Active",
                        sourceType = SourceType.CLINICIAN_VERIFIED.name,
                        verificationStatus = VerificationStatus.VERIFIED.name,
                        authorName = "Dr. Tavakoli",
                        eventDateMillis = now,
                        entryDateMillis = now,
                        notes = "Managed"
                    )
                ),
                medicalHistory = listOf(
                    MedicalHistoryEntity(
                        id = "hist_1",
                        patientId = patient.id,
                        category = MedicalHistoryCategory.SURGICAL.name,
                        titleEn = "Appendectomy",
                        titleFa = "آپاندکتومی",
                        detailsEn = "Uncomplicated",
                        detailsFa = "بدون عارضه",
                        sourceType = SourceType.CLINICIAN_VERIFIED.name,
                        verificationStatus = VerificationStatus.VERIFIED.name,
                        authorName = "Dr. Tavakoli",
                        recordedByUserId = "usr_physician",
                        eventDateMillis = now,
                        entryDateMillis = now
                    )
                ),
                medications = listOf(
                    MedicationEntity(
                        id = "med_1",
                        patientId = patient.id,
                        nameEn = "Metformin",
                        nameFa = "متفورمین",
                        genericName = "Metformin",
                        brandName = "Glucophage",
                        activeIngredient = "Metformin",
                        strength = "500 mg",
                        form = "Tablet",
                        route = "Oral",
                        frequency = "Twice daily",
                        status = MedicationStatus.PATIENT_REPORTED.name,
                        sourceType = SourceType.PATIENT_REPORTED.name,
                        verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                        prescriberName = "Patient",
                        reconciliationFlag = true,
                        startDateMillis = now,
                        notes = "Patient takes with meals"
                    )
                ),
                allergies = listOf(
                    AllergyEntity(
                        id = "alg_1",
                        patientId = patient.id,
                        allergenEn = "Penicillin",
                        allergenFa = "پنی‌سیلین",
                        reactionEn = "Rash",
                        reactionFa = "راش پوستی",
                        severity = AlertSeverity.RED.name,
                        sourceType = SourceType.CLINICIAN_VERIFIED.name,
                        verificationStatus = VerificationStatus.VERIFIED.name,
                        authorName = "Dr. Tavakoli",
                        eventDateMillis = now,
                        entryDateMillis = now
                    )
                ),
                labs = listOf(
                    LabResultEntity(
                        id = "lab_1",
                        patientId = patient.id,
                        testNameEn = "HbA1c",
                        testNameFa = "HbA1c",
                        loincCode = "4548-4",
                        value = 7.4,
                        unit = "%",
                        originalValue = 7.4,
                        originalUnit = "%",
                        referenceLow = 4.0,
                        referenceHigh = 6.4,
                        referenceNoteEn = "Target < 7%",
                        referenceNoteFa = "هدف زیر ۷٪",
                        flag = LabFlag.HIGH.name,
                        labName = "Demo Lab",
                        sourceType = SourceType.ORGANIZATION_SOURCED.name,
                        verificationStatus = VerificationStatus.VERIFIED.name,
                        collectionDateMillis = now,
                        resultDateMillis = now
                    )
                ),
                observations = listOf(
                    ObservationEntity(
                        id = "obs_1",
                        patientId = patient.id,
                        typeEn = "Fatigue",
                        typeFa = "خستگی",
                        valueText = "Moderate morning fatigue",
                        unit = "-",
                        sourceType = SourceType.PATIENT_REPORTED.name,
                        verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                        authorName = "Sara",
                        recordedAtMillis = now
                    )
                ),
                documents = listOf(
                    MedicalDocumentEntity(
                        id = "doc_1",
                        patientId = patient.id,
                        titleEn = "Discharge Note",
                        titleFa = "برگه ترخیص",
                        category = DocumentCategory.CLINICAL_NOTE.name,
                        fileUri = "synthetic://discharge.pdf",
                        mimeType = "application/pdf",
                        fileSizeKb = 150L,
                        sourceType = SourceType.DOCUMENT_EXTRACTED.name,
                        verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                        extractedSummaryEn = "Follow-up lipid panel advised",
                        extractedSummaryFa = "پایش چربی خون",
                        uploadedBy = "Sara",
                        uploadedAtMillis = now
                    )
                ),
                carePlans = listOf(
                    CarePlanEntity(
                        id = "cp_1",
                        patientId = patient.id,
                        titleEn = "Glycemic Control",
                        titleFa = "کنترل قند خون",
                        goalEn = "Quarterly HbA1c monitoring",
                        goalFa = "پایش فصلی",
                        progressPct = 60,
                        status = CarePlanStatus.ACTIVE.name,
                        sourceType = SourceType.CLINICIAN_VERIFIED.name,
                        verificationStatus = VerificationStatus.VERIFIED.name,
                        authorName = "Dr. Tavakoli",
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                ),
                customPrompt = customPrompt,
                selectedDocument = selectedDoc,
                documentBitmap = bitmap
            )
        }
    }

    private fun fakeGateway(block: suspend (String, String, Bitmap?) -> String?) = object : GeminiModelGateway {
        override suspend fun generateClinicalJson(
            systemInstruction: String,
            prompt: String,
            image: Bitmap?
        ): String? = block(systemInstruction, prompt, image)
    }

    // --- 1. Output Validation Tests ---

    @Test
    fun `output validator blocks autonomous prescription directives and patient-specific dosing`() = runTest {
        val unsafePayloads = listOf(
            // Prescribe verb + specific dose
            """
            {
              "title": "Summary",
              "verifiedClinicalFacts": ["Type 2 Diabetes [SRC:COND_cond_1]"],
              "patientReportedAndUnverifiedInfo": [],
              "aiInterpretation": "Prescribe Metformin 1000 mg twice daily and start Lisinopril 20 mg daily immediately.",
              "clinicianRecommendations": [],
              "missingOrUncertainData": [],
              "citedSourceIds": ["SRC:COND_cond_1"]
            }
            """.trimIndent(),
            // Weight-based unverified dose calculation inside clinicianRecommendations
            """
            {
              "title": "Clinician Recommendations",
              "verifiedClinicalFacts": ["Type 2 Diabetes [SRC:COND_cond_1]"],
              "patientReportedAndUnverifiedInfo": [],
              "aiInterpretation": "Clinical synthesis prepared for physician review.",
              "clinicianRecommendations": ["Calculated dose: 5 mg/kg daily based on weight."],
              "missingOrUncertainData": [],
              "citedSourceIds": ["SRC:COND_cond_1"]
            }
            """.trimIndent(),
            // Persian prescription directive
            """
            {
              "title": "خلاصه دارویی",
              "verifiedClinicalFacts": ["دیابت نوع ۲ [SRC:COND_cond_1]"],
              "patientReportedAndUnverifiedInfo": [],
              "aiInterpretation": "نسخه: متفورمین ۵۰۰ میلی‌گرم روزانه مصرف کنید.",
              "clinicianRecommendations": [],
              "missingOrUncertainData": [],
              "citedSourceIds": ["SRC:COND_cond_1"]
            }
            """.trimIndent()
        )

        for (payload in unsafePayloads) {
            val repo = GeminiAiRepository(fakeGateway { _, _, _ -> payload })
            val ex = runCatching {
                repo.generateClinicalAssistance(baseRequest(taskType = AiTaskType.CLINICIAN_RECOMMENDATIONS))
            }.exceptionOrNull()
            assertTrue("Expected AiValidationException for $payload", ex is AiValidationException)
            assertEquals(AiFailureReason.UNSAFE_PRESCRIPTION_OR_DOSE, (ex as AiValidationException).reason)
        }
    }

    @Test
    fun `output validator blocks fabricated source IDs and fabricated medical literature references`() = runTest {
        val fabricatedIdRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                """
                {
                  "title": "Source Retrieval",
                  "verifiedClinicalFacts": ["HbA1c 7.4% [SRC:LAB_lab_1]"],
                  "patientReportedAndUnverifiedInfo": [],
                  "aiInterpretation": "HbA1c is mildly elevated.",
                  "clinicianRecommendations": [],
                  "missingOrUncertainData": [],
                  "citedSourceIds": ["SRC:LAB_lab_1", "SRC:FAKE_GUIDELINE_999"]
                }
                """.trimIndent()
            }
        )
        val ex1 = runCatching {
            fabricatedIdRepo.generateClinicalAssistance(baseRequest(taskType = AiTaskType.SOURCE_AWARE_RETRIEVAL, customPrompt = "What is HbA1c?"))
        }.exceptionOrNull()
        assertTrue(ex1 is AiValidationException)
        assertEquals(AiFailureReason.FABRICATED_REFERENCE, (ex1 as AiValidationException).reason)

        val fabricatedCitationRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                """
                {
                  "title": "Lab Explanation",
                  "verifiedClinicalFacts": ["HbA1c 7.4%"],
                  "patientReportedAndUnverifiedInfo": [],
                  "aiInterpretation": "As shown in Johnson et al. 2025 (PMID: 12345678, DOI: 10.1000/abc), HbA1c correlates with glycemic status.",
                  "clinicianRecommendations": [],
                  "missingOrUncertainData": [],
                  "citedSourceIds": ["SRC:LAB_lab_1"]
                }
                """.trimIndent()
            }
        )
        val ex2 = runCatching {
            fabricatedCitationRepo.generateClinicalAssistance(baseRequest(taskType = AiTaskType.EXPLAIN_LABS))
        }.exceptionOrNull()
        assertTrue(ex2 is AiValidationException)
        assertEquals(AiFailureReason.FABRICATED_REFERENCE, (ex2 as AiValidationException).reason)
    }

    @Test
    fun `output validator rejects malformed JSON and empty text`() = runTest {
        val malformedRepo = GeminiAiRepository(fakeGateway { _, _, _ -> "```json {broken```" })
        val ex1 = runCatching {
            malformedRepo.generateClinicalAssistance(baseRequest())
        }.exceptionOrNull()
        assertTrue(ex1 is AiValidationException)
        assertEquals(AiFailureReason.MALFORMED_JSON, (ex1 as AiValidationException).reason)

        val emptyRepo = GeminiAiRepository(fakeGateway { _, _, _ -> "   " })
        val ex2 = runCatching {
            emptyRepo.generateClinicalAssistance(baseRequest())
        }.exceptionOrNull()
        assertTrue(ex2 is AiValidationException)
        assertEquals(AiFailureReason.EMPTY_RESPONSE, (ex2 as AiValidationException).reason)
    }

    @Test
    fun `valid structured extraction and clinician recommendation summary distinguish provenance and require review`() = runTest {
        var receivedBitmap: Bitmap? = null
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val extractionRepo = GeminiAiRepository(
            fakeGateway { _, _, img ->
                receivedBitmap = img
                """
                {
                  "title": "Extracted Clinical Document",
                  "verifiedClinicalFacts": ["Type 2 Diabetes Mellitus (E11.9) [SRC:COND_cond_1]"],
                  "patientReportedAndUnverifiedInfo": ["Document mentions follow-up lipid panel [SRC:DOC_doc_1]"],
                  "aiInterpretation": "Uploaded image and discharge summary list follow-up lab items requiring clinician sign-off.",
                  "clinicianRecommendations": ["Should be cleared for non-recommendation tasks"],
                  "extractedEntities": [
                    {
                      "category": "Lab Order Mentioned",
                      "label": "Lipid Panel",
                      "value": "Follow-up advised",
                      "provenanceNote": "Extracted from uploaded image",
                      "requiresClinicianVerification": false
                    }
                  ],
                  "missingOrUncertainData": [],
                  "citedSourceIds": ["SRC:COND_cond_1", "SRC:DOC_doc_1", "SRC:UPLOADED_IMAGE"]
                }
                """.trimIndent()
            }
        )

        val extracted = extractionRepo.generateClinicalAssistance(
            baseRequest(taskType = AiTaskType.EXTRACT_DOCUMENT, bitmap = bitmap)
        )
        assertEquals(bitmap, receivedBitmap)
        assertTrue(extracted.requiresClinicianReview)
        assertEquals(1, extracted.extractedEntities.size)
        // Validator always overrides verification requirement to true for extracted entities
        assertTrue(extracted.extractedEntities.first().requiresClinicianVerification)
        // Recommendations stripped on non-CLINICIAN_RECOMMENDATIONS task
        assertTrue(extracted.clinicianRecommendations.isEmpty())
        assertEquals(3, extracted.citedSources.size)

        val recRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                """
                {
                  "title": "Clinician Advisory Summary",
                  "verifiedClinicalFacts": ["Active Type 2 Diabetes (HbA1c 7.4%)"],
                  "patientReportedAndUnverifiedInfo": ["Patient-reported Metformin awaits reconciliation"],
                  "aiInterpretation": "Summary highlights glycemic trend and unverified home medication list.",
                  "clinicianRecommendations": [
                    "Review and reconcile patient-reported Metformin adherence during clinic visit.",
                    "Consider ordering baseline renal panel (eGFR/Creatinine) before next therapeutic review."
                  ],
                  "missingOrUncertainData": ["No eGFR value recorded"],
                  "citedSourceIds": ["SRC:COND_cond_1", "SRC:LAB_lab_1", "SRC:MED_med_1"]
                }
                """.trimIndent()
            }
        )
        val clinicianSummary = recRepo.generateClinicalAssistance(
            baseRequest(taskType = AiTaskType.CLINICIAN_RECOMMENDATIONS)
        )
        assertEquals(2, clinicianSummary.clinicianRecommendations.size)
        assertTrue(clinicianSummary.requiresClinicianReview)
    }

    // --- 2. Access Control Tests ---

    @Test
    fun `access control blocks unauthenticated, unverified, consent-disabled, cross-patient, and non-clinician recommendation requests`() = runTest {
        val neverCalledGateway = fakeGateway { _, _, _ ->
            throw AssertionError("Gemini gateway must never be invoked when access is denied")
        }
        val repo = GeminiAiRepository(neverCalledGateway)

        // Unauthenticated
        val r1 = runCatching { repo.generateClinicalAssistance(baseRequest(actor = null)) }.exceptionOrNull()
        assertEquals(AiFailureReason.UNAUTHENTICATED, (r1 as AiAccessDeniedException).reason)

        // Unverified email
        val r2 = runCatching {
            repo.generateClinicalAssistance(baseRequest(actor = actor(emailVerified = false)))
        }.exceptionOrNull()
        assertEquals(AiFailureReason.EMAIL_NOT_VERIFIED, (r2 as AiAccessDeniedException).reason)

        // Consent AI Summarization OFF
        val r3 = runCatching {
            repo.generateClinicalAssistance(baseRequest(patient = samplePatient(consentAi = false)))
        }.exceptionOrNull()
        assertEquals(AiFailureReason.CONSENT_AI_DISABLED, (r3 as AiAccessDeniedException).reason)

        // Unverified clinician
        val r4 = runCatching {
            repo.generateClinicalAssistance(baseRequest(actor = actor(UserRole.PHYSICIAN, isVerifiedClinician = false)))
        }.exceptionOrNull()
        assertEquals(AiFailureReason.CLINICIAN_ROLE_REQUIRED, (r4 as AiAccessDeniedException).reason)

        // Patient trying to access CLINICIAN_RECOMMENDATIONS
        val r5 = runCatching {
            repo.generateClinicalAssistance(
                baseRequest(
                    taskType = AiTaskType.CLINICIAN_RECOMMENDATIONS,
                    actor = actor(UserRole.PATIENT, isVerifiedClinician = false, linkedPatientId = "pat_001")
                )
            )
        }.exceptionOrNull()
        assertEquals(AiFailureReason.CLINICIAN_ROLE_REQUIRED, (r5 as AiAccessDeniedException).reason)

        // Caregiver blocked when caregiver consent is off
        val r6 = runCatching {
            repo.generateClinicalAssistance(
                baseRequest(
                    actor = actor(UserRole.CAREGIVER, isVerifiedClinician = false),
                    patient = samplePatient(consentCaregiver = false)
                )
            )
        }.exceptionOrNull()
        assertEquals(AiFailureReason.RECORD_ACCESS_DENIED, (r6 as AiAccessDeniedException).reason)
    }

    // --- 3. Missing-Data Handling Tests ---

    @Test
    fun `missing data detection identifies missing weight, renal labs, allergies, and reconciliation gaps deterministically`() = runTest {
        var capturedPrompt = ""
        val req = baseRequest(
            taskType = AiTaskType.IDENTIFY_MISSING_DATA,
            patient = samplePatient(weightKg = 0.0),
            withFullData = false
        )
        val built = AiPromptBuilder.build(req)
        assertTrue(built.deterministicMissingGaps.any { it.contains("weight", ignoreCase = true) })
        assertTrue(built.deterministicMissingGaps.any { it.contains("eGFR", ignoreCase = true) })
        assertTrue(built.deterministicMissingGaps.any { it.contains("Allergy", ignoreCase = true) })

        val repo = GeminiAiRepository(
            fakeGateway { _, prompt, _ ->
                capturedPrompt = prompt
                """
                {
                  "title": "Missing Clinical Info Audit",
                  "verifiedClinicalFacts": ["Baseline demographics documented [SRC:PATIENT_pat_001]"],
                  "patientReportedAndUnverifiedInfo": [],
                  "aiInterpretation": "Essential baseline clinical inputs are missing; abstaining from definitive clinical conclusions.",
                  "clinicianRecommendations": [],
                  "missingOrUncertainData": ["Additional baseline check needed"],
                  "citedSourceIds": ["SRC:PATIENT_pat_001"]
                }
                """.trimIndent()
            }
        )

        val res = repo.generateClinicalAssistance(req)
        assertTrue(capturedPrompt.contains("WeightKg=MISSING"))
        assertTrue(res.missingOrUncertainData.any { it.contains("weight", ignoreCase = true) })
        assertTrue(res.missingOrUncertainData.any { it.contains("eGFR", ignoreCase = true) })
        assertTrue(res.missingOrUncertainData.contains("Additional baseline check needed"))
    }

    // --- 4. AI Failure Scenario Tests ---

    @Test
    fun `AI failure scenarios propagate timeout, App Check HTTP 403 network failure, and cancellation cleanly`() = runTest {
        // Timeout
        val timeoutRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                throw AiInferenceException(AiFailureReason.TIMEOUT, "Timed out after 60s")
            }
        )
        val exTimeout = runCatching { timeoutRepo.generateClinicalAssistance(baseRequest()) }.exceptionOrNull()
        assertNotNull(exTimeout)
        assertTrue(exTimeout is AiInferenceException)
        assertEquals(AiFailureReason.TIMEOUT, (exTimeout as AiInferenceException).reason)

        // Network / App Check 403
        val networkRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                throw AiInferenceException(AiFailureReason.NETWORK_OR_APP_CHECK_ERROR, "HTTP 403 App Check token rejected")
            }
        )
        val exNet = runCatching { networkRepo.generateClinicalAssistance(baseRequest()) }.exceptionOrNull()
        assertTrue(exNet is AiInferenceException)
        assertEquals(AiFailureReason.NETWORK_OR_APP_CHECK_ERROR, (exNet as AiInferenceException).reason)

        // Cooperative CancellationException must be rethrown unmodified
        val cancelRepo = GeminiAiRepository(
            fakeGateway { _, _, _ ->
                throw CancellationException("Scope cancelled")
            }
        )
        val exCancel = runCatching { cancelRepo.generateClinicalAssistance(baseRequest()) }.exceptionOrNull()
        assertTrue(exCancel is CancellationException)
        assertFalse(exCancel is AiInferenceException)
    }
}
