package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.local.AllergyEntity
import com.example.data.local.AppointmentEntity
import com.example.data.local.AuditEventEntity
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
import com.example.domain.model.ClinicalSafetyEngine
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LabFlag
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.MedicationStatus
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage
import com.example.i18n.EnStrings
import com.example.i18n.FaStrings
import com.example.ui.PatientBundle
import com.example.ui.screens.DocumentsAndCareScreen
import com.example.ui.screens.HealthRecordScreen
import com.example.ui.screens.RoleDashboardScreen
import com.example.ui.theme.NersikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatientRecordUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun demoUser(role: UserRole = UserRole.PATIENT, verifiedClinician: Boolean = false) = UserEntity(
        id = if (role == UserRole.PATIENT) "usr_patient_sara" else "usr_physician_mehr",
        email = if (role == UserRole.PATIENT) "patient@demo.nersika.health" else "physician@demo.nersika.health",
        passwordHash = "h",
        passwordSalt = "s",
        firstName = if (role == UserRole.PATIENT) "Sara" else "Dr. Mehrzad",
        lastName = if (role == UserRole.PATIENT) "Bahrami" else "Tavakoli",
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
        organizationName = "Demo Center",
        isVerifiedClinician = verifiedClinician,
        isDemo = true,
        linkedPatientId = if (role == UserRole.PATIENT) "pat_001" else null,
        createdAtMillis = 1000L
    )

    private fun demoPatient() = PatientEntity(
        id = "pat_001",
        mrn = "NRK-10482",
        linkedUserId = "usr_patient_sara",
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
        emergencyContact = "Darius Bahrami (+98 912 000 8821)",
        weightKg = 68.5,
        isPregnant = false,
        consentClinicianAccess = true,
        consentCaregiverAccess = true,
        consentAiSummarization = true,
        verifiedIdentity = true,
        profileCompletionPct = 92
    )

    private fun demoBundle(): PatientBundle {
        val patient = demoPatient()
        val now = 1_760_000_000_000L
        val conditions = listOf(
            ConditionEntity(
                id = "cond_1",
                patientId = patient.id,
                nameEn = "Type 2 Diabetes Mellitus",
                nameFa = "دیابت نوع ۲",
                icd10Code = "E11.9",
                status = "Active",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                eventDateMillis = now - 10_000_000L,
                entryDateMillis = now - 5_000_000L,
                notes = "Monitored quarterly"
            )
        )
        val history = listOf(
            MedicalHistoryEntity(
                id = "hist_3",
                patientId = patient.id,
                category = MedicalHistoryCategory.FAMILY.name,
                titleEn = "Family History: Type 2 Diabetes",
                titleFa = "سابقه خانوادگی دیابت",
                detailsEn = "Maternal parent history",
                detailsFa = "سابقه در خانواده مادری",
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                recordedByUserId = "usr_patient_sara",
                eventDateMillis = now - 3_000_000L,
                entryDateMillis = now - 3_000_000L
            )
        )
        val allergies = listOf(
            AllergyEntity(
                id = "alg_1",
                patientId = patient.id,
                allergenEn = "Penicillin",
                allergenFa = "پنی‌سیلین",
                reactionEn = "Generalized urticaria",
                reactionFa = "کهیر منتشر",
                severity = AlertSeverity.RED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                eventDateMillis = now - 20_000_000L,
                entryDateMillis = now - 20_000_000L
            )
        )
        val meds = listOf(
            MedicationEntity(
                id = "med_1",
                patientId = patient.id,
                nameEn = "Metformin HCl",
                nameFa = "متفورمین",
                genericName = "Metformin",
                brandName = "Glucophage",
                activeIngredient = "Metformin",
                strength = "500 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Twice daily",
                status = MedicationStatus.PRESCRIBED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                prescriberName = "Dr. Mehrzad Tavakoli",
                reconciliationFlag = false,
                startDateMillis = now - 8_000_000L,
                notes = ""
            )
        )
        val labs = listOf(
            LabResultEntity(
                id = "lab_1",
                patientId = patient.id,
                testNameEn = "HbA1c",
                testNameFa = "هموگلوبین گلیکوزیله",
                loincCode = "4548-4",
                value = 7.2,
                unit = "%",
                originalValue = 7.2,
                originalUnit = "%",
                referenceLow = 4.0,
                referenceHigh = 6.4,
                referenceNoteEn = "Target < 7.0%",
                referenceNoteFa = "هدف زیر ۷٪",
                flag = LabFlag.HIGH.name,
                labName = "Demo Lab",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 2_000_000L,
                resultDateMillis = now - 1_500_000L
            )
        )
        val obs = listOf(
            ObservationEntity(
                id = "obs_1",
                patientId = patient.id,
                typeEn = "Blood Pressure",
                typeFa = "فشار خون",
                valueText = "128/78",
                unit = "mmHg",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Nurse Amir Moradi",
                recordedAtMillis = now - 1_000_000L
            )
        )
        val docs = listOf(
            MedicalDocumentEntity(
                id = "doc_1",
                patientId = patient.id,
                titleEn = "Lab Summary PDF",
                titleFa = "گزارش آزمایش",
                category = DocumentCategory.LAB_REPORT.name,
                fileUri = "synthetic://lab.pdf",
                mimeType = "application/pdf",
                fileSizeKb = 200L,
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                extractedSummaryEn = "Synthetic summary",
                extractedSummaryFa = "خلاصه نمایشی",
                uploadedBy = "Demo Lab",
                uploadedAtMillis = now - 1_200_000L
            )
        )
        val carePlans = listOf(
            CarePlanEntity(
                id = "cp_2",
                patientId = patient.id,
                titleEn = "Home Blood Pressure Goal",
                titleFa = "پایش فشار خون در منزل",
                goalEn = "Log BP twice weekly",
                goalFa = "ثبت هفته‌ای دو بار",
                progressPct = 45,
                status = CarePlanStatus.ACTIVE.name,
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                createdAtMillis = now - 900_000L,
                updatedAtMillis = now - 500_000L
            )
        )
        val appointments = listOf(
            AppointmentEntity(
                id = "apt_1",
                patientId = patient.id,
                clinicianName = "Dr. Mehrzad Tavakoli",
                specialtyEn = "Internal Medicine",
                specialtyFa = "داخلی",
                locationEn = "Demo Clinic",
                locationFa = "کلینیک نمایشی",
                scheduledAtMillis = now + 86_400_000L,
                status = "Confirmed",
                reasonEn = "Follow-up",
                reasonFa = "ویزیت پیگیری"
            )
        )
        val cds = ClinicalSafetyEngine.evaluatePatient(patient, meds, allergies, conditions, labs, emptyList())
        return PatientBundle(
            patient = patient,
            conditions = conditions,
            medicalHistory = history,
            allergies = allergies,
            medications = meds,
            labs = labs,
            observations = obs,
            documents = docs,
            carePlans = carePlans,
            appointments = appointments,
            decisions = emptyList(),
            patientAuditEvents = emptyList(),
            cdsReport = cds
        )
    }

    @Test
    fun `dashboard renders live DB KPIs, fictional non-clinical banner, RTL Persian localization, and consent-blocked fallback`() {
        var currentLang by mutableStateOf(AppLanguage.EN)
        var currentBundle by mutableStateOf<PatientBundle?>(demoBundle())
        var observedLayoutDirection: LayoutDirection? = null
        var navigatedRoute: String? = null

        composeRule.setContent {
            NersikaTheme(language = currentLang) {
                observedLayoutDirection = LocalLayoutDirection.current
                RoleDashboardScreen(
                    user = demoUser(UserRole.PATIENT),
                    bundle = currentBundle,
                    totalPatientsCount = 3,
                    unreadNotificationsCount = 2,
                    onNavigateRoute = { navigatedRoute = it }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(LayoutDirection.Ltr, observedLayoutDirection)
        composeRule.onNodeWithTag("demo_safety_banner").assertIsDisplayed()
        composeRule.onNodeWithText(EnStrings.demoDataBanner).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("dashboard_patient_profile_card"))
        composeRule.onNodeWithTag("dashboard_patient_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_open_record_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("records", navigatedRoute)

        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("kpi_conditions"))
        composeRule.onNodeWithTag("kpi_conditions").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi_history").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("kpi_care_plans"))
        composeRule.onNodeWithTag("kpi_care_plans").assertIsDisplayed()

        // Switch to Persian RTL
        currentLang = AppLanguage.FA
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, observedLayoutDirection)
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("demo_safety_banner"))
        composeRule.onNodeWithText(FaStrings.demoDataBanner).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("dashboard_patient_profile_card"))
        composeRule.onNodeWithText(FaStrings.profileSection).assertIsDisplayed()

        // Revoked consent (null bundle) renders consent-blocked notice
        currentBundle = null
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_lazy_column").performScrollToNode(hasTestTag("dashboard_consent_blocked_card"))
        composeRule.onNodeWithTag("dashboard_consent_blocked_card").assertIsDisplayed()
    }

    @Test
    fun `longitudinal health record filters timeline, creates diagnosis, and enforces source immutability and clinician verify buttons`() {
        var addedConditionName: String? = null
        var verifiedHistoryId: String? = null
        var activeUser by mutableStateOf(demoUser(UserRole.PATIENT))

        composeRule.setContent {
            NersikaTheme(language = AppLanguage.EN) {
                HealthRecordScreen(
                    user = activeUser,
                    bundle = demoBundle(),
                    onUpdateProfile = {},
                    onAddCondition = { name, _, _, _ -> addedConditionName = name },
                    onUpdateCondition = { _, _, _, _, _ -> },
                    onVerifyCondition = {},
                    onDeleteCondition = {},
                    onAddMedicalHistory = { _, _, _ -> },
                    onUpdateMedicalHistory = { _, _, _, _ -> },
                    onVerifyMedicalHistory = { verifiedHistoryId = it },
                    onDeleteMedicalHistory = {},
                    onAddAllergy = { _, _, _ -> },
                    onUpdateAllergy = { _, _, _, _ -> },
                    onVerifyAllergy = {},
                    onDeleteAllergy = {},
                    onAddObservation = { _, _, _ -> },
                    onVerifyObservation = {},
                    onDeleteObservation = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Timeline shows items across domains & filters properly
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("timeline_item_c_cond_1"))
        composeRule.onNodeWithTag("timeline_item_c_cond_1").assertIsDisplayed()

        // Scroll back up to filter row and select Medical History filter
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("timeline_filters_row"))
        composeRule.onNodeWithTag("timeline_filters_row").performScrollToNode(hasTestTag("timeline_filter_history"))
        composeRule.onNodeWithTag("timeline_filter_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("timeline_item_h_hist_3"))
        composeRule.onNodeWithTag("timeline_item_h_hist_3").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline_item_c_cond_1").assertDoesNotExist()

        // Switch to Diagnoses tab: Patient cannot edit/delete CLINICIAN_VERIFIED cond_1, but can add a new Diagnosis
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("record_section_tabs_row"))
        composeRule.onNodeWithTag("record_section_tabs_row").performScrollToNode(hasTestTag("record_tab_diagnoses"))
        composeRule.onNodeWithTag("record_tab_diagnoses").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("condition_card_cond_1"))
        composeRule.onNodeWithTag("condition_card_cond_1").assertIsDisplayed()
        composeRule.onNodeWithTag("cond_cond_1_edit_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("cond_cond_1_delete_btn").assertDoesNotExist()

        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("add_condition_btn"))
        composeRule.onNodeWithTag("add_condition_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("condition_name_input").performTextInput("Seasonal Rhinitis")
        composeRule.onNodeWithTag("save_condition_btn").performClick()
        composeRule.waitForIdle()
        assertEquals("Seasonal Rhinitis", addedConditionName)

        // Switch to Medical History tab: Patient sees Edit/Delete on PATIENT_REPORTED hist_3, NO Verify button
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("record_section_tabs_row"))
        composeRule.onNodeWithTag("record_section_tabs_row").performScrollToNode(hasTestTag("record_tab_history"))
        composeRule.onNodeWithTag("record_tab_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("history_card_hist_3"))
        composeRule.onNodeWithTag("history_card_hist_3").assertIsDisplayed()
        composeRule.onNodeWithTag("hist_hist_3_edit_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("hist_hist_3_verify_btn").assertDoesNotExist()

        // Switch actor to Verified Physician -> Verify button appears and triggers callback
        activeUser = demoUser(UserRole.PHYSICIAN, verifiedClinician = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("health_record_lazy_column").performScrollToNode(hasTestTag("hist_hist_3_verify_btn"))
        composeRule.onNodeWithTag("hist_hist_3_verify_btn").performClick()
        composeRule.waitForIdle()
        assertEquals("hist_3", verifiedHistoryId)
    }

    @Test
    fun `documents and care screen creates care plans and restricts consent switches to owning patient`() {
        var createdPlanTitle: String? = null
        var consentClinicianUpdated = true
        var activeUser by mutableStateOf(demoUser(UserRole.PHYSICIAN, verifiedClinician = true))

        composeRule.setContent {
            NersikaTheme(language = AppLanguage.EN) {
                DocumentsAndCareScreen(
                    user = activeUser,
                    bundle = demoBundle(),
                    onUploadDocument = { _, _, _, _, _ -> },
                    onUpdateDocumentSummary = { _, _, _, _ -> },
                    onVerifyDocument = {},
                    onDeleteDocument = {},
                    onAddCarePlan = { title, _, _, _ -> createdPlanTitle = title },
                    onUpdateCarePlan = { _, _, _, _, _ -> },
                    onVerifyCarePlan = {},
                    onDeleteCarePlan = {},
                    onUpdateConsents = { clin, _, _ -> consentClinicianUpdated = clin }
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Care Plans CRUD UI
        composeRule.onNodeWithTag("docs_care_tab_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("care_plan_card_cp_2").assertIsDisplayed()
        composeRule.onNodeWithTag("add_care_plan_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("care_plan_title_input").performTextInput("Low-Sodium Hydration Plan")
        composeRule.onNodeWithTag("care_plan_goal_input").performTextInput("Limit sodium < 2g daily")
        composeRule.onNodeWithTag("save_care_plan_btn").performClick()
        composeRule.waitForIdle()
        assertEquals("Low-Sodium Hydration Plan", createdPlanTitle)

        // 2. Consent Management: Physician sees notice and disabled switches
        composeRule.onNodeWithTag("docs_care_tab_3").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("consent_authorization_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("consent_clinician_switch").assertIsNotEnabled()
        composeRule.onNodeWithTag("consent_caregiver_switch").assertIsNotEnabled()
        composeRule.onNodeWithTag("consent_ai_switch").assertIsNotEnabled()
        assertTrue(consentClinicianUpdated)

        // 3. Owning Patient sees enabled consent switches and can toggle
        activeUser = demoUser(UserRole.PATIENT)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("consent_clinician_switch").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertFalse(consentClinicianUpdated)
    }
}
