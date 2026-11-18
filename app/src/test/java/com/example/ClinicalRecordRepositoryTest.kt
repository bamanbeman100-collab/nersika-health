package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.clinical.ClinicalAccessError
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.clinical.ClinicalOpResult
import com.example.data.clinical.ClinicalRecordRepository
import com.example.data.clinical.PatientProfileUpdateRequest
import com.example.data.local.DemoDataSeeder
import com.example.data.local.NersikaDatabase
import com.example.data.local.SecurityHasher
import com.example.data.local.UserEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.ClinicalDecisionAction
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LabFlag
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.MedicationStatus
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClinicalRecordRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: NersikaDatabase
    private lateinit var repo: ClinicalRecordRepository

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NersikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DemoDataSeeder.seedIfEmpty(db.dao())
        repo = ClinicalRecordRepository(db.dao())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun makeCustomUser(
        id: String,
        role: UserRole,
        emailVerified: Boolean = true,
        onboardingCompleted: Boolean = true,
        isVerifiedClinician: Boolean = false,
        linkedPatientId: String? = null
    ): UserEntity {
        val salt = SecurityHasher.generateSalt()
        return UserEntity(
            id = id,
            email = "$id@nersika.health",
            passwordHash = SecurityHasher.hashPassword("Strong@2026!", salt),
            passwordSalt = salt,
            firstName = role.name.take(6),
            lastName = "Tester",
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
            organizationName = "Test Clinic",
            isVerifiedClinician = isVerifiedClinician,
            isDemo = false,
            linkedPatientId = linkedPatientId,
            createdAtMillis = 1_760_000_000_000L
        )
    }

    @Test
    fun `read access and baseline auth gates enforce email verification, onboarding, clinician verification, and consent`() = runTest {
        val dao = db.dao()
        val pat1 = dao.getPatientById("pat_001")!!
        val pat2 = dao.getPatientById("pat_002")!!
        val pat3 = dao.getPatientById("pat_003")!! // All consents false

        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val physician = dao.getUserByEmail("physician@demo.nersika.health")!!
        val unverifiedEmailUser = makeCustomUser("u_unver", UserRole.PATIENT, emailVerified = false, linkedPatientId = "pat_001")
        val notOnboardedUser = makeCustomUser("u_noob", UserRole.PATIENT, onboardingCompleted = false, linkedPatientId = "pat_001")
        val unverifiedClinician = makeCustomUser("u_doc_unver", UserRole.PHYSICIAN, isVerifiedClinician = false)
        val caregiver = makeCustomUser("u_care", UserRole.CAREGIVER)

        assertEquals(ClinicalAccessError.UNAUTHENTICATED, ClinicalAuthorizationPolicy.evaluateReadAccess(null, pat1))
        assertEquals(ClinicalAccessError.EMAIL_NOT_VERIFIED, ClinicalAuthorizationPolicy.evaluateReadAccess(unverifiedEmailUser, pat1))
        assertEquals(ClinicalAccessError.ONBOARDING_REQUIRED, ClinicalAuthorizationPolicy.evaluateReadAccess(notOnboardedUser, pat1))

        // Patient can only read own linked patient record
        assertNull(ClinicalAuthorizationPolicy.evaluateReadAccess(sara, pat1))
        assertEquals(ClinicalAccessError.UNAUTHORIZED_ROLE, ClinicalAuthorizationPolicy.evaluateReadAccess(sara, pat2))
        assertEquals(ClinicalAccessError.UNAUTHORIZED_ROLE, ClinicalAuthorizationPolicy.evaluateReadAccess(sara, pat3))

        // Verified physician can read consented patients, blocked on pat_003; unverified physician always blocked
        assertNull(ClinicalAuthorizationPolicy.evaluateReadAccess(physician, pat1))
        assertNull(ClinicalAuthorizationPolicy.evaluateReadAccess(physician, pat2))
        assertEquals(ClinicalAccessError.CONSENT_DENIED, ClinicalAuthorizationPolicy.evaluateReadAccess(physician, pat3))
        assertEquals(ClinicalAccessError.UNVERIFIED_CLINICIAN, ClinicalAuthorizationPolicy.evaluateReadAccess(unverifiedClinician, pat1))

        // Caregiver follows caregiver consent
        assertNull(ClinicalAuthorizationPolicy.evaluateReadAccess(caregiver, pat1))
        assertEquals(ClinicalAccessError.CONSENT_DENIED, ClinicalAuthorizationPolicy.evaluateReadAccess(caregiver, pat3))

        // Mutation attempted by unauthenticated actor is denied & audited
        val unauthAdd = repo.addCondition(null, "pat_001", "Asthma", "J45")
        assertEquals(ClinicalAccessError.UNAUTHENTICATED, (unauthAdd as ClinicalOpResult.Denied).error)
        val audits = dao.observeAuditEventsForPatient("pat_001").first()
        assertTrue(audits.any { it.actionType == "CONDITION_CREATE" && it.outcome == "DENIED" })
    }

    @Test
    fun `consent management is restricted to owning patient or admin and revoking clinician consent blocks access`() = runTest {
        val dao = db.dao()
        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val physician = dao.getUserByEmail("physician@demo.nersika.health")!!
        val pharmacist = dao.getUserByEmail("pharmacist@demo.nersika.health")!!
        val admin = dao.getUserByEmail("admin@demo.nersika.health")!!
        val caregiver = makeCustomUser("u_care_2", UserRole.CAREGIVER)
        val otherPatient = makeCustomUser("u_pat_other", UserRole.PATIENT, linkedPatientId = "pat_002")

        // Clinicians, caregivers, and other patients CANNOT alter pat_001 consent flags
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.updatePatientConsents(physician, "pat_001", false, false, false) as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.updatePatientConsents(pharmacist, "pat_001", false, false, false) as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.updatePatientConsents(caregiver, "pat_001", true, false, false) as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.updatePatientConsents(otherPatient, "pat_001", false, false, false) as ClinicalOpResult.Denied).error
        )
        assertTrue(dao.getPatientById("pat_001")!!.consentClinicianAccess)

        // Owning patient revokes clinician & AI access -> physician is immediately blocked
        val revokeRes = repo.updatePatientConsents(sara, "pat_001", clinicianAccess = false, caregiverAccess = true, aiSummarization = false)
        assertTrue(revokeRes is ClinicalOpResult.Success)
        val updatedPat = dao.getPatientById("pat_001")!!
        assertFalse(updatedPat.consentClinicianAccess)
        assertFalse(updatedPat.consentAiSummarization)
        assertEquals(ClinicalAccessError.CONSENT_DENIED, ClinicalAuthorizationPolicy.evaluateReadAccess(physician, updatedPat))

        val blockedMedCreate = repo.addMedication(physician, "pat_001", "Lisinopril", "Lisinopril", "10 mg", "Daily", MedicationStatus.PRESCRIBED, "")
        assertEquals(ClinicalAccessError.CONSENT_DENIED, (blockedMedCreate as ClinicalOpResult.Denied).error)

        // System Admin can restore consent governance when needed
        val adminRestore = repo.updatePatientConsents(admin, "pat_001", clinicianAccess = true, caregiverAccess = true, aiSummarization = true)
        assertTrue(adminRestore is ClinicalOpResult.Success)
        assertNull(ClinicalAuthorizationPolicy.evaluateReadAccess(physician, dao.getPatientById("pat_001")!!))
    }

    @Test
    fun `patient profile CRUD updates demographics, recalculates completeness, and blocks caregivers`() = runTest {
        val dao = db.dao()
        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val caregiver = makeCustomUser("u_care_prof", UserRole.CAREGIVER)

        val request = PatientProfileUpdateRequest(
            dateOfBirthIso = "1972-04-12",
            ageYears = 54,
            sex = "Female",
            populationGroup = PopulationGroup.ADULT,
            bloodType = "A+",
            phone = "+98 912 999 0000",
            emergencyContact = "Darius Bahrami (+98 912 000 8821)",
            weightKg = 66.2,
            isPregnant = false
        )

        // Caregiver cannot modify patient profile
        val caregiverDenied = repo.updatePatientProfile(caregiver, "pat_001", request)
        assertEquals(ClinicalAccessError.UNAUTHORIZED_ROLE, (caregiverDenied as ClinicalOpResult.Denied).error)

        // Owning patient updates profile -> persisted with 100% completion
        val okRes = repo.updatePatientProfile(sara, "pat_001", request) as ClinicalOpResult.Success
        assertEquals(66.2, okRes.value.weightKg, 0.01)
        assertEquals("+98 912 999 0000", okRes.value.phone)
        assertEquals(100, okRes.value.profileCompletionPct)

        val fromDb = dao.getPatientById("pat_001")!!
        assertEquals(66.2, fromDb.weightKg, 0.01)
        assertEquals(100, fromDb.profileCompletionPct)

        // Audit trail recorded both denied & allowed events
        val audits = dao.observeAuditEventsForPatient("pat_001").first()
        assertTrue(audits.any { it.actionType == "PATIENT_PROFILE_UPDATE" && it.outcome == "DENIED" })
        assertTrue(audits.any { it.actionType == "PATIENT_PROFILE_UPDATED" && it.outcome == "ALLOWED" })
    }

    @Test
    fun `diagnoses, medical history, allergies, and observations support full CRUD and enforce provenance immutability`() = runTest {
        val dao = db.dao()
        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val physician = dao.getUserByEmail("physician@demo.nersika.health")!!
        val caregiver = makeCustomUser("u_care_rec", UserRole.CAREGIVER)

        // 1. Patient creates Diagnosis -> PATIENT_REPORTED + NEEDS_REVIEW
        val condCreated = (repo.addCondition(sara, "pat_001", "Migraine with Aura", "G43.1", "Active", "Triggered by stress")
            as ClinicalOpResult.Success).value
        assertEquals(SourceType.PATIENT_REPORTED.name, condCreated.sourceType)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, condCreated.verificationStatus)

        // Patient updates own reported diagnosis
        val condUpdated = (repo.updateCondition(sara, condCreated.id, "Chronic Migraine", "G43.7", "Monitoring", "Reduced frequency")
            as ClinicalOpResult.Success).value
        assertEquals("Chronic Migraine", condUpdated.nameEn)
        assertEquals("Monitoring", condUpdated.status)

        // Patient & Caregiver cannot verify records
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.verifyCondition(sara, condCreated.id) as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.verifyCondition(caregiver, condCreated.id) as ClinicalOpResult.Denied).error
        )

        // Patient cannot edit or delete clinician-verified diagnosis ("cond_1")
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.updateCondition(sara, "cond_1", "Tampered", "E11.9", "Resolved", "") as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteCondition(sara, "cond_1") as ClinicalOpResult.Denied).error
        )

        // Physician verifies patient's diagnosis and can also delete patient's entry when cleaning up
        assertTrue(repo.verifyCondition(physician, condCreated.id) is ClinicalOpResult.Success)
        assertEquals(VerificationStatus.VERIFIED.name, dao.getConditionById(condCreated.id)!!.verificationStatus)
        assertTrue(repo.deleteCondition(physician, condCreated.id) is ClinicalOpResult.Success)
        assertNull(dao.getConditionById(condCreated.id))

        // 2. Medical History CRUD
        val histCreated = (repo.addMedicalHistory(sara, "pat_001", MedicalHistoryCategory.SOCIAL, "Former Light Smoker", "Quit 2016")
            as ClinicalOpResult.Success).value
        assertEquals(MedicalHistoryCategory.SOCIAL.name, histCreated.category)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, histCreated.verificationStatus)

        val histUpdated = (repo.updateMedicalHistory(sara, histCreated.id, MedicalHistoryCategory.SOCIAL, "Non-Smoker Since 2016", "Confirmed 10 yrs tobacco-free")
            as ClinicalOpResult.Success).value
        assertEquals("Non-Smoker Since 2016", histUpdated.titleEn)
        assertEquals(VerificationStatus.VERIFIED.name, run {
            repo.verifyMedicalHistory(physician, histCreated.id)
            dao.getMedicalHistoryById(histCreated.id)!!.verificationStatus
        })

        // Patient cannot delete clinician/org medical history ("hist_1")
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteMedicalHistory(sara, "hist_1") as ClinicalOpResult.Denied).error
        )
        // Patient can delete their own patient-reported entry
        assertTrue(repo.deleteMedicalHistory(sara, histCreated.id) is ClinicalOpResult.Success)
        assertNull(dao.getMedicalHistoryById(histCreated.id))

        // 3. Allergies CRUD
        val algCreated = (repo.addAllergy(sara, "pat_001", "Peanuts", "Throat tightness", AlertSeverity.RED)
            as ClinicalOpResult.Success).value
        assertEquals(AlertSeverity.RED.name, algCreated.severity)
        val algUpdated = (repo.updateAllergy(sara, algCreated.id, "Tree Nuts", "Anaphylactic reaction", AlertSeverity.RED)
            as ClinicalOpResult.Success).value
        assertEquals("Tree Nuts", algUpdated.allergenEn)
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteAllergy(sara, "alg_1") as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.verifyAllergy(physician, algCreated.id) is ClinicalOpResult.Success)
        assertEquals(VerificationStatus.VERIFIED.name, dao.getAllergyById(algCreated.id)!!.verificationStatus)
        assertTrue(repo.deleteAllergy(sara, algCreated.id) is ClinicalOpResult.Success)

        // 4. Observations CRUD
        val obsCreated = (repo.addObservation(sara, "pat_001", "Fasting Glucose", "118", "mg/dL")
            as ClinicalOpResult.Success).value
        assertEquals("Fasting Glucose", obsCreated.typeEn)
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteObservation(sara, "obs_1") as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.verifyObservation(physician, obsCreated.id) is ClinicalOpResult.Success)
        assertTrue(repo.deleteObservation(sara, obsCreated.id) is ClinicalOpResult.Success)
    }

    @Test
    fun `medication CRUD enforces status safety, reconciliation rules, and clinician-only CDS decisions`() = runTest {
        val dao = db.dao()
        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val pharmacist = dao.getUserByEmail("pharmacist@demo.nersika.health")!!

        // Patient trying to add a PRESCRIBED med is safely clamped to PATIENT_REPORTED + reconciliationFlag=true
        val patMed = (repo.addMedication(
            actor = sara,
            patientId = "pat_001",
            name = "Ibuprofen OTC",
            activeIngredient = "Ibuprofen",
            strength = "400 mg",
            frequency = "PRN",
            status = MedicationStatus.PRESCRIBED,
            notes = "For knee pain"
        ) as ClinicalOpResult.Success).value
        assertEquals(MedicationStatus.PATIENT_REPORTED.name, patMed.status)
        assertTrue(patMed.reconciliationFlag)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, patMed.verificationStatus)

        // Patient can update strength/notes, cannot delete clinician/org medication med_1
        val patMedUpd = (repo.updateMedication(sara, patMed.id, "200 mg", "PRN", MedicationStatus.PRESCRIBED, "Lowered dose")
            as ClinicalOpResult.Success).value
        assertEquals("200 mg", patMedUpd.strength)
        assertEquals(MedicationStatus.PATIENT_REPORTED.name, patMedUpd.status) // Still cannot self-escalate to PRESCRIBED
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteMedication(sara, "med_1") as ClinicalOpResult.Denied).error
        )

        // Patient cannot reconcile medications or sign CDS decisions
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.reconcileMedication(sara, patMed.id, MedicationStatus.DISCONTINUED) as ClinicalOpResult.Denied).error
        )
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.recordClinicalDecision(
                actor = sara,
                patientId = "pat_001",
                alertKey = "ddi_warfarin_aspirin",
                titleEn = "Warfarin + Aspirin",
                titleFa = "وارفارین + آسپرین",
                severity = AlertSeverity.RED,
                evidenceVersion = "CDS-2026.1",
                action = ClinicalDecisionAction.ACCEPT,
                rationale = "Test"
            ) as ClinicalOpResult.Denied).error
        )

        // Clinical Pharmacist reconciles medication -> sets DISCONTINUED, VERIFIED, reconciliationFlag=false
        assertTrue(repo.reconcileMedication(pharmacist, patMed.id, MedicationStatus.DISCONTINUED) is ClinicalOpResult.Success)
        val afterRecon = dao.getMedicationById(patMed.id)!!
        assertEquals(MedicationStatus.DISCONTINUED.name, afterRecon.status)
        assertEquals(VerificationStatus.VERIFIED.name, afterRecon.verificationStatus)
        assertFalse(afterRecon.reconciliationFlag)

        // Pharmacist records clinical decision -> persisted & audited
        val decRes = repo.recordClinicalDecision(
            actor = pharmacist,
            patientId = "pat_001",
            alertKey = "ddi_warfarin_aspirin",
            titleEn = "Warfarin + Aspirin",
            titleFa = "وارفارین + آسپرین",
            severity = AlertSeverity.RED,
            evidenceVersion = "CDS-2026.1",
            action = ClinicalDecisionAction.MODIFY,
            rationale = "Advised patient to stop OTC NSAIDs and monitor INR in 7 days."
        )
        assertTrue(decRes is ClinicalOpResult.Success)
        val decisions = dao.observeDecisions("pat_001").first()
        assertTrue(decisions.any { it.alertKey == "ddi_warfarin_aspirin" && it.action == ClinicalDecisionAction.MODIFY.name })

        // Clean up patient-reported med
        assertTrue(repo.deleteMedication(sara, patMed.id) is ClinicalOpResult.Success)
    }

    @Test
    fun `labs, medical documents, and care plans support full CRUD, provenance verification, and audit logging`() = runTest {
        val dao = db.dao()
        val sara = dao.getUserByEmail("patient@demo.nersika.health")!!
        val nurse = dao.getUserByEmail("nurse@demo.nersika.health")!!

        // 1. Lab Results CRUD
        val lab = (repo.addLabResult(sara, "pat_001", "Fasting Lipid LDL", 162.0, "mg/dL", 0.0, 100.0)
            as ClinicalOpResult.Success).value
        assertEquals(LabFlag.HIGH.name, lab.flag)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, lab.verificationStatus)
        assertEquals(
            ClinicalAccessError.UNAUTHORIZED_ROLE,
            (repo.verifyLabResult(sara, lab.id) as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.verifyLabResult(nurse, lab.id) is ClinicalOpResult.Success)
        assertEquals(VerificationStatus.VERIFIED.name, dao.getLabResultById(lab.id)!!.verificationStatus)
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteLabResult(sara, "lab_3") as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.deleteLabResult(sara, lab.id) is ClinicalOpResult.Success)

        // 2. Medical Documents CRUD
        val doc = (repo.addDocument(
            actor = sara,
            patientId = "pat_001",
            title = "Ophthalmology Screening Photo",
            category = DocumentCategory.IMAGING,
            uriString = "content://media/picker/0/101",
            mimeType = "image/jpeg",
            extractedNote = "Annual diabetic retinopathy check"
        ) as ClinicalOpResult.Success).value
        assertEquals(SourceType.DOCUMENT_EXTRACTED.name, doc.sourceType)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, doc.verificationStatus)

        val docUpdated = (repo.updateDocumentSummary(
            actor = sara,
            docId = doc.id,
            title = "Retinal Screening Report",
            category = DocumentCategory.CLINICAL_NOTE,
            summary = "No proliferative changes noted"
        ) as ClinicalOpResult.Success).value
        assertEquals("Retinal Screening Report", docUpdated.titleEn)
        assertTrue(repo.verifyDocument(nurse, doc.id) is ClinicalOpResult.Success)
        assertEquals(VerificationStatus.VERIFIED.name, dao.getDocumentById(doc.id)!!.verificationStatus)
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteDocument(sara, "doc_1") as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.deleteDocument(sara, doc.id) is ClinicalOpResult.Success)

        // 3. Care Plans CRUD
        val plan = (repo.addCarePlan(
            actor = sara,
            patientId = "pat_001",
            title = "Daily 20-Min Walk Goal",
            goal = "Walk 20 minutes after dinner 5 days/week",
            progressPct = 30,
            status = CarePlanStatus.ACTIVE
        ) as ClinicalOpResult.Success).value
        assertEquals(SourceType.PATIENT_REPORTED.name, plan.sourceType)
        assertEquals(VerificationStatus.NEEDS_REVIEW.name, plan.verificationStatus)

        val planUpdated = (repo.updateCarePlan(
            actor = sara,
            planId = plan.id,
            title = "Daily 30-Min Walk Goal",
            goal = "Walk 30 minutes 5 days/week",
            progressPct = 100,
            status = CarePlanStatus.COMPLETED
        ) as ClinicalOpResult.Success).value
        assertEquals(100, planUpdated.progressPct)
        assertEquals(CarePlanStatus.COMPLETED.name, planUpdated.status)

        assertTrue(repo.verifyCarePlan(nurse, plan.id) is ClinicalOpResult.Success)
        assertEquals(VerificationStatus.VERIFIED.name, dao.getCarePlanById(plan.id)!!.verificationStatus)
        assertEquals(
            ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD,
            (repo.deleteCarePlan(sara, "cp_1") as ClinicalOpResult.Denied).error
        )
        assertTrue(repo.deleteCarePlan(sara, plan.id) is ClinicalOpResult.Success)

        // Verify persistent audit log captures traceability for every operation
        val audits = dao.observeAuditEventsForPatient("pat_001").first()
        assertNotNull(audits.firstOrNull { it.actionType == "LAB_VERIFIED" })
        assertNotNull(audits.firstOrNull { it.actionType == "DOCUMENT_VERIFIED" })
        assertNotNull(audits.firstOrNull { it.actionType == "CARE_PLAN_VERIFIED" })
    }
}
