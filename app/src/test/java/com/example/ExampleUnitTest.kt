package com.example

import com.example.data.auth.AuthValidators
import com.example.data.local.AllergyEntity
import com.example.data.local.LabResultEntity
import com.example.data.local.MedicationEntity
import com.example.data.local.PatientEntity
import com.example.data.local.SecurityHasher
import com.example.domain.model.AlertSeverity
import com.example.domain.model.ClinicalSafetyEngine
import com.example.domain.model.LabFlag
import com.example.domain.model.MedicationStatus
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `security hasher creates verifiable salted hash`() {
        val salt = SecurityHasher.generateSalt()
        val hash = SecurityHasher.hashPassword("Nersika@2026", salt)
        assertTrue(SecurityHasher.verifyPassword("Nersika@2026", salt, hash))
        assertFalse(SecurityHasher.verifyPassword("WrongPass", salt, hash))
    }

    @Test
    fun `jalali calendar and persian numerals format accurately`() {
        // March 21, 2026 is Nowruz (1405/01/01)
        val (jy, jm, jd) = LocaleFormatter.gregorianToJalali(2026, 3, 21)
        assertEquals(1405, jy)
        assertEquals(1, jm)
        assertEquals(1, jd)
        assertEquals("۱۴۰۵", LocaleFormatter.toLocalizedDigits("1405", AppLanguage.FA))
    }

    @Test
    fun `clinical safety engine flags allergy, warfarin-aspirin DDI, renal metformin, and missing weight`() {
        val patient = PatientEntity(
            id = "p_test",
            mrn = "NRK-TEST",
            linkedUserId = null,
            firstNameEn = "Test",
            lastNameEn = "Patient",
            firstNameFa = "تست",
            lastNameFa = "بیمار",
            dateOfBirthIso = "1970-01-01",
            ageYears = 56,
            sex = "Female",
            populationGroup = PopulationGroup.ADULT.name,
            bloodType = "A+",
            phone = "",
            emergencyContact = "",
            weightKg = 0.0, // missing weight -> Stage 3 gap
            isPregnant = false,
            consentClinicianAccess = true,
            consentCaregiverAccess = true,
            consentAiSummarization = true,
            verifiedIdentity = true,
            profileCompletionPct = 80
        )

        val meds = listOf(
            MedicationEntity(
                id = "m1", patientId = "p_test", nameEn = "Warfarin", nameFa = "وارفارین",
                genericName = "Warfarin", brandName = "Coumadin", activeIngredient = "Warfarin",
                strength = "5mg", form = "Tab", route = "Oral", frequency = "Daily",
                status = MedicationStatus.DISPENSED.name, sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name, prescriberName = "Dr. X",
                reconciliationFlag = false, startDateMillis = 1000L, notes = ""
            ),
            MedicationEntity(
                id = "m2", patientId = "p_test", nameEn = "Aspirin", nameFa = "آسپرین",
                genericName = "Aspirin", brandName = "ASA", activeIngredient = "Aspirin",
                strength = "80mg", form = "Tab", route = "Oral", frequency = "Daily",
                status = MedicationStatus.PATIENT_REPORTED.name, sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name, prescriberName = "OTC",
                reconciliationFlag = true, startDateMillis = 1000L, notes = ""
            ),
            MedicationEntity(
                id = "m3", patientId = "p_test", nameEn = "Amoxicillin", nameFa = "آموکسی‌سیلین",
                genericName = "Amoxicillin", brandName = "Amoxil", activeIngredient = "Amoxicillin",
                strength = "500mg", form = "Cap", route = "Oral", frequency = "q8h",
                status = MedicationStatus.PRESCRIBED.name, sourceType = SourceType.DOCUMENT_EXTRACTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name, prescriberName = "Clinic",
                reconciliationFlag = true, startDateMillis = 1000L, notes = ""
            ),
            MedicationEntity(
                id = "m4", patientId = "p_test", nameEn = "Metformin", nameFa = "متفورمین",
                genericName = "Metformin", brandName = "Glucophage", activeIngredient = "Metformin",
                strength = "500mg", form = "Tab", route = "Oral", frequency = "BID",
                status = MedicationStatus.PRESCRIBED.name, sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name, prescriberName = "Dr. X",
                reconciliationFlag = false, startDateMillis = 1000L, notes = ""
            )
        )

        val allergies = listOf(
            AllergyEntity(
                id = "a1", patientId = "p_test", allergenEn = "Penicillin", allergenFa = "پنی‌سیلین",
                reactionEn = "Urticaria", reactionFa = "کهیر", severity = AlertSeverity.RED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name, verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. X", eventDateMillis = 1000L, entryDateMillis = 1000L
            )
        )

        val labs = listOf(
            LabResultEntity(
                id = "l1", patientId = "p_test", testNameEn = "eGFR", testNameFa = "eGFR",
                loincCode = "62238-1", value = 41.0, unit = "mL/min", originalValue = 41.0,
                originalUnit = "mL/min", referenceLow = 60.0, referenceHigh = 120.0,
                referenceNoteEn = "", referenceNoteFa = "", flag = LabFlag.LOW.name,
                labName = "Lab", sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = 1000L, resultDateMillis = 1000L
            )
        )

        val report = ClinicalSafetyEngine.evaluatePatient(
            patient = patient,
            medications = meds,
            allergies = allergies,
            conditions = emptyList(),
            labs = labs,
            decisions = emptyList()
        )

        assertTrue(report.stage3QualityGapsEn.any { it.contains("weight", ignoreCase = true) })
        assertTrue(report.alerts.any { it.alertKey.startsWith("allergy_penicillin") && it.severity == AlertSeverity.RED })
        assertTrue(report.alerts.any { it.alertKey.startsWith("ddi_warfarin_nsaid") && it.severity == AlertSeverity.RED })
        assertTrue(report.alerts.any { it.alertKey.startsWith("renal_metformin") && it.severity == AlertSeverity.AMBER })
        assertTrue(report.alerts.any { it.alertKey.startsWith("reconciliation_gap") })
    }

    @Test
    fun `auth validators enforce strict email, strong password, and public role bounds`() {
        assertTrue(AuthValidators.isValidEmail("Dr.Bahrami@Hospital.ir"))
        assertFalse(AuthValidators.isValidEmail("not-an-email"))
        assertFalse(AuthValidators.isValidEmail("missing@tld"))

        assertTrue(AuthValidators.isStrongPassword("Nersika@2026"))
        assertFalse(AuthValidators.isStrongPassword("Sh1!")) // < 8 chars
        assertFalse(AuthValidators.isStrongPassword("nersika@2026")) // missing uppercase
        assertFalse(AuthValidators.isStrongPassword("NERSIKA@2026")) // missing lowercase
        assertFalse(AuthValidators.isStrongPassword("Nersika@Pass")) // missing digit
        assertFalse(AuthValidators.isStrongPassword("Nersika2026A")) // missing special char

        assertTrue(AuthValidators.isAllowedPublicRole(UserRole.PATIENT))
        assertTrue(AuthValidators.isAllowedPublicRole(UserRole.CAREGIVER))
        assertFalse(AuthValidators.isAllowedPublicRole(UserRole.PHYSICIAN))
        assertFalse(AuthValidators.isAllowedPublicRole(UserRole.SYSTEM_ADMIN))
    }
}
