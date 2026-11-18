package com.example.data.clinical

import com.example.data.local.AllergyEntity
import com.example.data.local.AuditEventEntity
import com.example.data.local.CarePlanEntity
import com.example.data.local.ClinicalDecisionEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.LabResultEntity
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.MedicalHistoryEntity
import com.example.data.local.MedicationEntity
import com.example.data.local.NersikaDao
import com.example.data.local.ObservationEntity
import com.example.data.local.PatientEntity
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
import java.util.UUID

enum class ClinicalAccessError {
    UNAUTHENTICATED,
    EMAIL_NOT_VERIFIED,
    ONBOARDING_REQUIRED,
    PATIENT_NOT_FOUND,
    RECORD_NOT_FOUND,
    CONSENT_DENIED,
    UNAUTHORIZED_ROLE,
    UNVERIFIED_CLINICIAN,
    IMMUTABLE_CLINICIAN_RECORD,
    INVALID_INPUT
}

sealed interface ClinicalOpResult<out T> {
    data class Success<T>(val value: T) : ClinicalOpResult<T>
    data class Denied(val error: ClinicalAccessError) : ClinicalOpResult<Nothing>
}

data class PatientProfileUpdateRequest(
    val dateOfBirthIso: String,
    val ageYears: Int,
    val sex: String,
    val populationGroup: PopulationGroup,
    val bloodType: String,
    val phone: String,
    val emergencyContact: String,
    val weightKg: Double,
    val isPregnant: Boolean
)

/**
 * Enforces patient-record read, mutation, consent, and clinician-verification authorization rules.
 */
object ClinicalAuthorizationPolicy {

    private fun baselineUserError(user: UserEntity?): ClinicalAccessError? = when {
        user == null -> ClinicalAccessError.UNAUTHENTICATED
        !user.emailVerified -> ClinicalAccessError.EMAIL_NOT_VERIFIED
        !user.onboardingCompleted -> ClinicalAccessError.ONBOARDING_REQUIRED
        else -> null
    }

    fun evaluateReadAccess(user: UserEntity?, patient: PatientEntity): ClinicalAccessError? {
        baselineUserError(user)?.let { return it }
        val nonNullUser = user!!
        val role = UserRole.fromName(nonNullUser.role)
        return when (role) {
            UserRole.PATIENT ->
                if (nonNullUser.linkedPatientId == patient.id) null else ClinicalAccessError.UNAUTHORIZED_ROLE

            UserRole.CAREGIVER ->
                if (patient.consentCaregiverAccess) null else ClinicalAccessError.CONSENT_DENIED

            UserRole.PHYSICIAN,
            UserRole.CLINICAL_PHARMACIST,
            UserRole.NURSE,
            UserRole.HEALTHCARE_COORDINATOR -> when {
                !nonNullUser.isVerifiedClinician -> ClinicalAccessError.UNVERIFIED_CLINICIAN
                !patient.consentClinicianAccess -> ClinicalAccessError.CONSENT_DENIED
                else -> null
            }

            UserRole.ORGANIZATION_ADMIN,
            UserRole.SYSTEM_ADMIN ->
                if (patient.consentClinicianAccess) null else ClinicalAccessError.CONSENT_DENIED
        }
    }

    fun evaluateProfileEditAccess(user: UserEntity?, patient: PatientEntity): ClinicalAccessError? {
        evaluateReadAccess(user, patient)?.let { return it }
        val role = UserRole.fromName(user!!.role)
        return when {
            role == UserRole.PATIENT && user.linkedPatientId == patient.id -> null
            role.isClinician && user.isVerifiedClinician -> null
            role.isAdmin -> null
            else -> ClinicalAccessError.UNAUTHORIZED_ROLE
        }
    }

    fun evaluateConsentManagementAccess(user: UserEntity?, patient: PatientEntity): ClinicalAccessError? {
        baselineUserError(user)?.let { return it }
        val nonNullUser = user!!
        val role = UserRole.fromName(nonNullUser.role)
        return when {
            role == UserRole.PATIENT && nonNullUser.linkedPatientId == patient.id -> null
            role.isAdmin -> null
            else -> ClinicalAccessError.UNAUTHORIZED_ROLE
        }
    }

    fun evaluateCreateEntryAccess(user: UserEntity?, patient: PatientEntity): ClinicalAccessError? =
        evaluateReadAccess(user, patient)

    fun evaluateModifyOrDeleteEntryAccess(
        user: UserEntity?,
        patient: PatientEntity,
        entrySourceType: String
    ): ClinicalAccessError? {
        evaluateReadAccess(user, patient)?.let { return it }
        val role = UserRole.fromName(user!!.role)
        val source = SourceType.fromName(entrySourceType)
        val isClinicianVerifiedSource =
            source == SourceType.CLINICIAN_VERIFIED || source == SourceType.ORGANIZATION_SOURCED

        if (isClinicianVerifiedSource && !(role.isClinician && user.isVerifiedClinician) && !role.isAdmin) {
            return ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD
        }
        return null
    }

    fun evaluateVerifyAccess(user: UserEntity?, patient: PatientEntity): ClinicalAccessError? {
        evaluateReadAccess(user, patient)?.let { return it }
        val nonNullUser = user!!
        val role = UserRole.fromName(nonNullUser.role)
        return when {
            !role.isClinician -> ClinicalAccessError.UNAUTHORIZED_ROLE
            !nonNullUser.isVerifiedClinician -> ClinicalAccessError.UNVERIFIED_CLINICIAN
            else -> null
        }
    }
}

class ClinicalRecordRepository(private val dao: NersikaDao) {

    private fun provenanceForActor(user: UserEntity): Pair<SourceType, VerificationStatus> {
        val role = UserRole.fromName(user.role)
        return if (role.isClinician && user.isVerifiedClinician) {
            SourceType.CLINICIAN_VERIFIED to VerificationStatus.VERIFIED
        } else {
            SourceType.PATIENT_REPORTED to VerificationStatus.NEEDS_REVIEW
        }
    }

    private suspend fun audit(
        actor: UserEntity?,
        actionType: String,
        targetResource: String,
        targetPatientId: String?,
        outcome: String,
        details: String
    ) {
        dao.insertAuditEvent(
            AuditEventEntity(
                id = "aud_${UUID.randomUUID().toString().take(10)}",
                actorId = actor?.id ?: "anonymous",
                actorName = actor?.let { "${it.firstName} ${it.lastName}" } ?: "Unauthenticated Actor",
                actorRole = actor?.role ?: "UNAUTHENTICATED",
                actionType = actionType,
                targetResource = targetResource,
                targetPatientId = targetPatientId,
                outcome = outcome,
                details = details,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun loadPatientOrDeny(
        actor: UserEntity?,
        patientId: String,
        actionType: String,
        resource: String
    ): Pair<PatientEntity?, ClinicalOpResult.Denied?> {
        val patient = dao.getPatientById(patientId)
        if (patient == null) {
            audit(actor, actionType, resource, patientId, "DENIED", "Patient not found: $patientId")
            return null to ClinicalOpResult.Denied(ClinicalAccessError.PATIENT_NOT_FOUND)
        }
        return patient to null
    }

    // --- 1. Patient Profile CRUD ---

    suspend fun updatePatientProfile(
        actor: UserEntity?,
        patientId: String,
        request: PatientProfileUpdateRequest
    ): ClinicalOpResult<PatientEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "PATIENT_PROFILE_UPDATE", "patients/$patientId")
        if (missing != null) return missing

        val err = ClinicalAuthorizationPolicy.evaluateProfileEditAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "PATIENT_PROFILE_UPDATE", "patients/$patientId", patientId, "DENIED", "Error=${err.name}")
            return ClinicalOpResult.Denied(err)
        }

        if (request.dateOfBirthIso.isBlank() || request.ageYears <= 0 || request.bloodType.isBlank() || request.weightKg < 0.0) {
            return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)
        }

        val completion = calculateProfileCompletion(
            dob = request.dateOfBirthIso,
            sex = request.sex,
            bloodType = request.bloodType,
            phone = request.phone,
            emergencyContact = request.emergencyContact,
            weightKg = request.weightKg
        )

        val updated = patient.copy(
            dateOfBirthIso = request.dateOfBirthIso.trim(),
            ageYears = request.ageYears,
            sex = request.sex.trim().ifBlank { patient.sex },
            populationGroup = request.populationGroup.name,
            bloodType = request.bloodType.trim(),
            phone = request.phone.trim(),
            emergencyContact = request.emergencyContact.trim(),
            weightKg = request.weightKg,
            isPregnant = request.isPregnant,
            profileCompletionPct = completion
        )
        dao.upsertPatient(updated)
        audit(
            actor = actor,
            actionType = "PATIENT_PROFILE_UPDATED",
            targetResource = "patients/$patientId",
            targetPatientId = patientId,
            outcome = "ALLOWED",
            details = "Profile updated (weight=${request.weightKg}kg, completion=$completion%)"
        )
        return ClinicalOpResult.Success(updated)
    }

    suspend fun updatePatientWeight(
        actor: UserEntity?,
        patientId: String,
        weightKg: Double
    ): ClinicalOpResult<PatientEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "PATIENT_WEIGHT_UPDATE", "patients/$patientId")
        if (missing != null) return missing
        val p = patient!!
        if (weightKg <= 0.0) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)
        return updatePatientProfile(
            actor = actor,
            patientId = patientId,
            request = PatientProfileUpdateRequest(
                dateOfBirthIso = p.dateOfBirthIso,
                ageYears = p.ageYears,
                sex = p.sex,
                populationGroup = PopulationGroup.fromName(p.populationGroup),
                bloodType = p.bloodType,
                phone = p.phone,
                emergencyContact = p.emergencyContact,
                weightKg = weightKg,
                isPregnant = p.isPregnant
            )
        )
    }

    private fun calculateProfileCompletion(
        dob: String,
        sex: String,
        bloodType: String,
        phone: String,
        emergencyContact: String,
        weightKg: Double
    ): Int {
        var score = 20 // Base identity
        if (dob.isNotBlank()) score += 15
        if (sex.isNotBlank()) score += 10
        if (bloodType.isNotBlank() && bloodType != "Unknown") score += 15
        if (phone.isNotBlank()) score += 15
        if (emergencyContact.isNotBlank()) score += 10
        if (weightKg > 0.0) score += 15
        return score.coerceIn(0, 100)
    }

    // --- 2. Consent Management ---

    suspend fun updatePatientConsents(
        actor: UserEntity?,
        patientId: String,
        clinicianAccess: Boolean,
        caregiverAccess: Boolean,
        aiSummarization: Boolean
    ): ClinicalOpResult<PatientEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "CONSENT_UPDATE", "patients/$patientId/consent")
        if (missing != null) return missing

        val err = ClinicalAuthorizationPolicy.evaluateConsentManagementAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CONSENT_UPDATE", "patients/$patientId/consent", patientId, "DENIED", "Unauthorized consent mutation: ${err.name}")
            return ClinicalOpResult.Denied(err)
        }

        dao.updatePatientConsents(patientId, clinicianAccess, caregiverAccess, aiSummarization)
        val updated = patient.copy(
            consentClinicianAccess = clinicianAccess,
            consentCaregiverAccess = caregiverAccess,
            consentAiSummarization = aiSummarization
        )
        audit(
            actor = actor,
            actionType = "CONSENT_UPDATED",
            targetResource = "patients/$patientId/consent",
            targetPatientId = patientId,
            outcome = "ALLOWED",
            details = "Clinician=$clinicianAccess, Caregiver=$caregiverAccess, AI=$aiSummarization"
        )
        return ClinicalOpResult.Success(updated)
    }

    // --- 3. Diagnoses (ConditionEntity) CRUD ---

    suspend fun addCondition(
        actor: UserEntity?,
        patientId: String,
        name: String,
        icd10: String,
        status: String = "Active",
        notes: String = ""
    ): ClinicalOpResult<ConditionEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "CONDITION_CREATE", "patients/$patientId/conditions")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CONDITION_CREATE", "patients/$patientId/conditions", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (name.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val (source, verif) = provenanceForActor(actor!!)
        val now = System.currentTimeMillis()
        val entity = ConditionEntity(
            id = "cond_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            nameEn = name.trim(),
            nameFa = name.trim(),
            icd10Code = icd10.trim().ifBlank { "Unspecified" },
            status = status.trim().ifBlank { "Active" },
            sourceType = source.name,
            verificationStatus = verif.name,
            authorName = "${actor.firstName} ${actor.lastName}",
            eventDateMillis = now,
            entryDateMillis = now,
            notes = notes.trim()
        )
        dao.insertCondition(entity)
        audit(actor, "CONDITION_ADDED", "conditions/${entity.id}", patientId, "RECORDED", "${entity.nameEn} (${verif.name})")
        return ClinicalOpResult.Success(entity)
    }

    suspend fun updateCondition(
        actor: UserEntity?,
        conditionId: String,
        name: String,
        icd10: String,
        status: String,
        notes: String
    ): ClinicalOpResult<ConditionEntity> {
        val existing = dao.getConditionById(conditionId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CONDITION_UPDATE", "conditions/$conditionId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "CONDITION_UPDATE", "conditions/$conditionId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (name.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val updated = existing.copy(
            nameEn = name.trim(),
            nameFa = name.trim(),
            icd10Code = icd10.trim().ifBlank { "Unspecified" },
            status = status.trim().ifBlank { existing.status },
            notes = notes.trim(),
            entryDateMillis = System.currentTimeMillis()
        )
        dao.insertCondition(updated)
        audit(actor, "CONDITION_UPDATED", "conditions/$conditionId", existing.patientId, "ALLOWED", "${updated.nameEn} status=${updated.status}")
        return ClinicalOpResult.Success(updated)
    }

    suspend fun verifyCondition(actor: UserEntity?, conditionId: String): ClinicalOpResult<Unit> {
        val existing = dao.getConditionById(conditionId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CONDITION_VERIFY", "conditions/$conditionId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CONDITION_VERIFY", "conditions/$conditionId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateConditionVerification(conditionId, VerificationStatus.VERIFIED.name)
        audit(actor, "CONDITION_VERIFIED", "conditions/$conditionId", existing.patientId, "ALLOWED", "Verified ${existing.nameEn}")
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteCondition(actor: UserEntity?, conditionId: String): ClinicalOpResult<Unit> {
        val existing = dao.getConditionById(conditionId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CONDITION_DELETE", "conditions/$conditionId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "CONDITION_DELETE", "conditions/$conditionId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteConditionById(conditionId)
        audit(actor, "CONDITION_DELETED", "conditions/$conditionId", existing.patientId, "ALLOWED", "Deleted ${existing.nameEn}")
        return ClinicalOpResult.Success(Unit)
    }

    // --- 4. Medical History CRUD ---

    suspend fun addMedicalHistory(
        actor: UserEntity?,
        patientId: String,
        category: MedicalHistoryCategory,
        title: String,
        details: String
    ): ClinicalOpResult<MedicalHistoryEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "HISTORY_CREATE", "patients/$patientId/history")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "HISTORY_CREATE", "patients/$patientId/history", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val (source, verif) = provenanceForActor(actor!!)
        val now = System.currentTimeMillis()
        val entry = MedicalHistoryEntity(
            id = "hist_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            category = category.name,
            titleEn = title.trim(),
            titleFa = title.trim(),
            detailsEn = details.trim(),
            detailsFa = details.trim(),
            sourceType = source.name,
            verificationStatus = verif.name,
            authorName = "${actor.firstName} ${actor.lastName}",
            recordedByUserId = actor.id,
            eventDateMillis = now,
            entryDateMillis = now
        )
        dao.upsertMedicalHistory(entry)
        audit(actor, "HISTORY_ADDED", "medical_history/${entry.id}", patientId, "RECORDED", "${category.name}: ${entry.titleEn}")
        return ClinicalOpResult.Success(entry)
    }

    suspend fun updateMedicalHistory(
        actor: UserEntity?,
        historyId: String,
        category: MedicalHistoryCategory,
        title: String,
        details: String
    ): ClinicalOpResult<MedicalHistoryEntity> {
        val existing = dao.getMedicalHistoryById(historyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "HISTORY_UPDATE", "medical_history/$historyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "HISTORY_UPDATE", "medical_history/$historyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val updated = existing.copy(
            category = category.name,
            titleEn = title.trim(),
            titleFa = title.trim(),
            detailsEn = details.trim(),
            detailsFa = details.trim(),
            entryDateMillis = System.currentTimeMillis()
        )
        dao.upsertMedicalHistory(updated)
        audit(actor, "HISTORY_UPDATED", "medical_history/$historyId", existing.patientId, "ALLOWED", updated.titleEn)
        return ClinicalOpResult.Success(updated)
    }

    suspend fun verifyMedicalHistory(actor: UserEntity?, historyId: String): ClinicalOpResult<Unit> {
        val existing = dao.getMedicalHistoryById(historyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "HISTORY_VERIFY", "medical_history/$historyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "HISTORY_VERIFY", "medical_history/$historyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateMedicalHistoryVerification(historyId, VerificationStatus.VERIFIED.name)
        audit(actor, "HISTORY_VERIFIED", "medical_history/$historyId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteMedicalHistory(actor: UserEntity?, historyId: String): ClinicalOpResult<Unit> {
        val existing = dao.getMedicalHistoryById(historyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "HISTORY_DELETE", "medical_history/$historyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "HISTORY_DELETE", "medical_history/$historyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteMedicalHistoryById(historyId)
        audit(actor, "HISTORY_DELETED", "medical_history/$historyId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 5. Allergies CRUD ---

    suspend fun addAllergy(
        actor: UserEntity?,
        patientId: String,
        allergen: String,
        reaction: String,
        severity: AlertSeverity
    ): ClinicalOpResult<AllergyEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "ALLERGY_CREATE", "patients/$patientId/allergies")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "ALLERGY_CREATE", "patients/$patientId/allergies", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (allergen.isBlank() || reaction.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val (source, verif) = provenanceForActor(actor!!)
        val now = System.currentTimeMillis()
        val allergy = AllergyEntity(
            id = "alg_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            allergenEn = allergen.trim(),
            allergenFa = allergen.trim(),
            reactionEn = reaction.trim(),
            reactionFa = reaction.trim(),
            severity = severity.name,
            sourceType = source.name,
            verificationStatus = verif.name,
            authorName = "${actor.firstName} ${actor.lastName}",
            eventDateMillis = now,
            entryDateMillis = now
        )
        dao.insertAllergy(allergy)
        audit(actor, "ALLERGY_RECORDED", "allergies/${allergy.id}", patientId, "RECORDED", "${allergy.allergenEn} (${severity.name})")
        return ClinicalOpResult.Success(allergy)
    }

    suspend fun updateAllergy(
        actor: UserEntity?,
        allergyId: String,
        allergen: String,
        reaction: String,
        severity: AlertSeverity
    ): ClinicalOpResult<AllergyEntity> {
        val existing = dao.getAllergyById(allergyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "ALLERGY_UPDATE", "allergies/$allergyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "ALLERGY_UPDATE", "allergies/$allergyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (allergen.isBlank() || reaction.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val updated = existing.copy(
            allergenEn = allergen.trim(),
            allergenFa = allergen.trim(),
            reactionEn = reaction.trim(),
            reactionFa = reaction.trim(),
            severity = severity.name,
            entryDateMillis = System.currentTimeMillis()
        )
        dao.insertAllergy(updated)
        audit(actor, "ALLERGY_UPDATED", "allergies/$allergyId", existing.patientId, "ALLOWED", updated.allergenEn)
        return ClinicalOpResult.Success(updated)
    }

    suspend fun verifyAllergy(actor: UserEntity?, allergyId: String): ClinicalOpResult<Unit> {
        val existing = dao.getAllergyById(allergyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "ALLERGY_VERIFY", "allergies/$allergyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "ALLERGY_VERIFY", "allergies/$allergyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateAllergyVerification(allergyId, VerificationStatus.VERIFIED.name)
        audit(actor, "ALLERGY_VERIFIED", "allergies/$allergyId", existing.patientId, "ALLOWED", existing.allergenEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteAllergy(actor: UserEntity?, allergyId: String): ClinicalOpResult<Unit> {
        val existing = dao.getAllergyById(allergyId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "ALLERGY_DELETE", "allergies/$allergyId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "ALLERGY_DELETE", "allergies/$allergyId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteAllergyById(allergyId)
        audit(actor, "ALLERGY_DELETED", "allergies/$allergyId", existing.patientId, "ALLOWED", existing.allergenEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 6. Observations / Vitals CRUD ---

    suspend fun addObservation(
        actor: UserEntity?,
        patientId: String,
        typeTitle: String,
        valueText: String,
        unit: String
    ): ClinicalOpResult<ObservationEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "OBSERVATION_CREATE", "patients/$patientId/observations")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "OBSERVATION_CREATE", "patients/$patientId/observations", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (typeTitle.isBlank() || valueText.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val (source, verif) = provenanceForActor(actor!!)
        val obs = ObservationEntity(
            id = "obs_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            typeEn = typeTitle.trim(),
            typeFa = typeTitle.trim(),
            valueText = valueText.trim(),
            unit = unit.trim().ifBlank { "-" },
            sourceType = source.name,
            verificationStatus = verif.name,
            authorName = "${actor.firstName} ${actor.lastName}",
            recordedAtMillis = System.currentTimeMillis()
        )
        dao.insertObservation(obs)
        audit(actor, "OBSERVATION_ADDED", "observations/${obs.id}", patientId, "RECORDED", "${obs.typeEn}: ${obs.valueText}")
        return ClinicalOpResult.Success(obs)
    }

    suspend fun verifyObservation(actor: UserEntity?, obsId: String): ClinicalOpResult<Unit> {
        val existing = dao.getObservationById(obsId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "OBSERVATION_VERIFY", "observations/$obsId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "OBSERVATION_VERIFY", "observations/$obsId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateObservationVerification(obsId, VerificationStatus.VERIFIED.name)
        audit(actor, "OBSERVATION_VERIFIED", "observations/$obsId", existing.patientId, "ALLOWED", existing.typeEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteObservation(actor: UserEntity?, obsId: String): ClinicalOpResult<Unit> {
        val existing = dao.getObservationById(obsId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "OBSERVATION_DELETE", "observations/$obsId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "OBSERVATION_DELETE", "observations/$obsId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteObservationById(obsId)
        audit(actor, "OBSERVATION_DELETED", "observations/$obsId", existing.patientId, "ALLOWED", existing.typeEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 7. Medications CRUD & Reconciliation ---

    suspend fun addMedication(
        actor: UserEntity?,
        patientId: String,
        name: String,
        activeIngredient: String,
        strength: String,
        frequency: String,
        status: MedicationStatus,
        notes: String
    ): ClinicalOpResult<MedicationEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "MEDICATION_CREATE", "patients/$patientId/medications")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "MEDICATION_CREATE", "patients/$patientId/medications", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (name.isBlank() || strength.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val role = UserRole.fromName(actor!!.role)
        val (source, verif) = provenanceForActor(actor)
        val effectiveStatus = if (!role.isClinician && (status == MedicationStatus.PRESCRIBED || status == MedicationStatus.DISPENSED || status == MedicationStatus.ADMINISTERED)) {
            MedicationStatus.PATIENT_REPORTED
        } else {
            status
        }
        val needsRecon = !role.isClinician || effectiveStatus == MedicationStatus.PATIENT_REPORTED || effectiveStatus == MedicationStatus.UNCERTAIN

        val med = MedicationEntity(
            id = "med_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            nameEn = name.trim(),
            nameFa = name.trim(),
            genericName = activeIngredient.ifBlank { name }.trim(),
            brandName = name.trim(),
            activeIngredient = activeIngredient.ifBlank { name }.trim(),
            strength = strength.trim(),
            form = "Tablet",
            route = "Oral",
            frequency = frequency.trim().ifBlank { "As directed" },
            status = effectiveStatus.name,
            sourceType = source.name,
            verificationStatus = verif.name,
            prescriberName = "${actor.firstName} ${actor.lastName}",
            reconciliationFlag = needsRecon,
            startDateMillis = System.currentTimeMillis(),
            notes = notes.trim()
        )
        dao.upsertMedication(med)
        audit(actor, "MEDICATION_RECORDED", "medications/${med.id}", patientId, "RECORDED", "${med.nameEn} (${med.status})")
        return ClinicalOpResult.Success(med)
    }

    suspend fun updateMedication(
        actor: UserEntity?,
        medicationId: String,
        strength: String,
        frequency: String,
        status: MedicationStatus,
        notes: String
    ): ClinicalOpResult<MedicationEntity> {
        val existing = dao.getMedicationById(medicationId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "MEDICATION_UPDATE", "medications/$medicationId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "MEDICATION_UPDATE", "medications/$medicationId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        val role = UserRole.fromName(actor!!.role)
        val updatedStatus = if (role.isClinician && actor.isVerifiedClinician) status else MedicationStatus.fromName(existing.status)
        val updated = existing.copy(
            strength = strength.trim().ifBlank { existing.strength },
            frequency = frequency.trim().ifBlank { existing.frequency },
            status = updatedStatus.name,
            notes = notes.trim()
        )
        dao.upsertMedication(updated)
        audit(actor, "MEDICATION_UPDATED", "medications/$medicationId", existing.patientId, "ALLOWED", "${updated.nameEn} (${updated.status})")
        return ClinicalOpResult.Success(updated)
    }

    suspend fun reconcileMedication(
        actor: UserEntity?,
        medicationId: String,
        finalStatus: MedicationStatus
    ): ClinicalOpResult<Unit> {
        val existing = dao.getMedicationById(medicationId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "MEDICATION_RECONCILE", "medications/$medicationId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "MEDICATION_RECONCILE", "medications/$medicationId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.reconcileMedication(
            medId = medicationId,
            status = finalStatus.name,
            verificationStatus = VerificationStatus.VERIFIED.name,
            reconciliationFlag = false
        )
        audit(actor, "MEDICATION_RECONCILED", "medications/$medicationId", existing.patientId, "ALLOWED", "Reconciled ${existing.nameEn} -> ${finalStatus.name}")
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteMedication(actor: UserEntity?, medicationId: String): ClinicalOpResult<Unit> {
        val existing = dao.getMedicationById(medicationId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "MEDICATION_DELETE", "medications/$medicationId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "MEDICATION_DELETE", "medications/$medicationId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteMedicationById(medicationId)
        audit(actor, "MEDICATION_DELETED", "medications/$medicationId", existing.patientId, "ALLOWED", existing.nameEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 8. Lab Results CRUD ---

    suspend fun addLabResult(
        actor: UserEntity?,
        patientId: String,
        testName: String,
        numericValue: Double,
        unit: String,
        refLow: Double,
        refHigh: Double
    ): ClinicalOpResult<LabResultEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "LAB_CREATE", "patients/$patientId/labs")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "LAB_CREATE", "patients/$patientId/labs", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (testName.isBlank() || unit.isBlank() || refHigh < refLow) {
            return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)
        }

        val (source, verif) = provenanceForActor(actor!!)
        val role = UserRole.fromName(actor.role)
        val now = System.currentTimeMillis()
        val flag = when {
            numericValue > refHigh -> LabFlag.HIGH
            numericValue < refLow -> LabFlag.LOW
            else -> LabFlag.NORMAL
        }
        val lab = LabResultEntity(
            id = "lab_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            testNameEn = testName.trim(),
            testNameFa = testName.trim(),
            loincCode = "Custom",
            value = numericValue,
            unit = unit.trim(),
            originalValue = numericValue,
            originalUnit = unit.trim(),
            referenceLow = refLow,
            referenceHigh = refHigh,
            referenceNoteEn = "Verify age/sex/pregnancy applicability",
            referenceNoteFa = "تطبیق بازه مرجع با سن، جنس و بارداری بررسی شود",
            flag = flag.name,
            labName = if (role.isClinician) "Clinic Lab Entry" else "Patient Entered Lab",
            sourceType = source.name,
            verificationStatus = verif.name,
            collectionDateMillis = now,
            resultDateMillis = now
        )
        dao.insertLabResult(lab)
        audit(actor, "LAB_RESULT_ADDED", "lab_results/${lab.id}", patientId, "RECORDED", "$testName=$numericValue $unit (${flag.name})")
        return ClinicalOpResult.Success(lab)
    }

    suspend fun verifyLabResult(actor: UserEntity?, labId: String): ClinicalOpResult<Unit> {
        val existing = dao.getLabResultById(labId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "LAB_VERIFY", "lab_results/$labId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "LAB_VERIFY", "lab_results/$labId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateLabResultVerification(labId, VerificationStatus.VERIFIED.name)
        audit(actor, "LAB_VERIFIED", "lab_results/$labId", existing.patientId, "ALLOWED", existing.testNameEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteLabResult(actor: UserEntity?, labId: String): ClinicalOpResult<Unit> {
        val existing = dao.getLabResultById(labId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "LAB_DELETE", "lab_results/$labId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "LAB_DELETE", "lab_results/$labId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteLabResultById(labId)
        audit(actor, "LAB_DELETED", "lab_results/$labId", existing.patientId, "ALLOWED", existing.testNameEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 9. Medical Documents CRUD ---

    suspend fun addDocument(
        actor: UserEntity?,
        patientId: String,
        title: String,
        category: DocumentCategory,
        uriString: String,
        mimeType: String,
        extractedNote: String
    ): ClinicalOpResult<MedicalDocumentEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "DOCUMENT_CREATE", "patients/$patientId/documents")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "DOCUMENT_CREATE", "patients/$patientId/documents", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank() || uriString.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val role = UserRole.fromName(actor!!.role)
        val verif = if (role.isClinician && actor.isVerifiedClinician) {
            VerificationStatus.VERIFIED
        } else {
            VerificationStatus.NEEDS_REVIEW
        }
        val doc = MedicalDocumentEntity(
            id = "doc_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            titleEn = title.trim(),
            titleFa = title.trim(),
            category = category.name,
            fileUri = uriString,
            mimeType = mimeType,
            fileSizeKb = 256L,
            sourceType = SourceType.DOCUMENT_EXTRACTED.name,
            verificationStatus = verif.name,
            extractedSummaryEn = extractedNote.trim().ifBlank { "Uploaded document awaiting clinician review." },
            extractedSummaryFa = extractedNote.trim().ifBlank { "مدرک بارگذاری‌شده در انتظار بررسی پزشک است." },
            uploadedBy = "${actor.firstName} ${actor.lastName}",
            uploadedAtMillis = System.currentTimeMillis()
        )
        dao.insertDocument(doc)
        audit(actor, "DOCUMENT_UPLOADED", "medical_documents/${doc.id}", patientId, "RECORDED", "${doc.titleEn} (${category.name})")
        return ClinicalOpResult.Success(doc)
    }

    suspend fun updateDocumentSummary(
        actor: UserEntity?,
        docId: String,
        title: String,
        category: DocumentCategory,
        summary: String
    ): ClinicalOpResult<MedicalDocumentEntity> {
        val existing = dao.getDocumentById(docId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "DOCUMENT_UPDATE", "medical_documents/$docId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "DOCUMENT_UPDATE", "medical_documents/$docId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val updated = existing.copy(
            titleEn = title.trim(),
            titleFa = title.trim(),
            category = category.name,
            extractedSummaryEn = summary.trim().ifBlank { existing.extractedSummaryEn },
            extractedSummaryFa = summary.trim().ifBlank { existing.extractedSummaryFa }
        )
        dao.insertDocument(updated)
        audit(actor, "DOCUMENT_UPDATED", "medical_documents/$docId", existing.patientId, "ALLOWED", updated.titleEn)
        return ClinicalOpResult.Success(updated)
    }

    suspend fun verifyDocument(actor: UserEntity?, docId: String): ClinicalOpResult<Unit> {
        val existing = dao.getDocumentById(docId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "DOCUMENT_VERIFY", "medical_documents/$docId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "DOCUMENT_VERIFY", "medical_documents/$docId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateDocumentVerification(docId, VerificationStatus.VERIFIED.name)
        audit(actor, "DOCUMENT_VERIFIED", "medical_documents/$docId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteDocument(actor: UserEntity?, docId: String): ClinicalOpResult<Unit> {
        val existing = dao.getDocumentById(docId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "DOCUMENT_DELETE", "medical_documents/$docId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "DOCUMENT_DELETE", "medical_documents/$docId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteDocumentById(docId)
        audit(actor, "DOCUMENT_DELETED", "medical_documents/$docId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 10. Care Plans CRUD ---

    suspend fun addCarePlan(
        actor: UserEntity?,
        patientId: String,
        title: String,
        goal: String,
        progressPct: Int,
        status: CarePlanStatus = CarePlanStatus.ACTIVE
    ): ClinicalOpResult<CarePlanEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "CARE_PLAN_CREATE", "patients/$patientId/care_plans")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateCreateEntryAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CARE_PLAN_CREATE", "patients/$patientId/care_plans", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank() || goal.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val (source, verif) = provenanceForActor(actor!!)
        val now = System.currentTimeMillis()
        val plan = CarePlanEntity(
            id = "cp_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            titleEn = title.trim(),
            titleFa = title.trim(),
            goalEn = goal.trim(),
            goalFa = goal.trim(),
            progressPct = progressPct.coerceIn(0, 100),
            status = status.name,
            sourceType = source.name,
            verificationStatus = verif.name,
            authorName = "${actor.firstName} ${actor.lastName}",
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.upsertCarePlan(plan)
        audit(actor, "CARE_PLAN_ADDED", "care_plans/${plan.id}", patientId, "RECORDED", plan.titleEn)
        return ClinicalOpResult.Success(plan)
    }

    suspend fun updateCarePlan(
        actor: UserEntity?,
        planId: String,
        title: String,
        goal: String,
        progressPct: Int,
        status: CarePlanStatus
    ): ClinicalOpResult<CarePlanEntity> {
        val existing = dao.getCarePlanById(planId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CARE_PLAN_UPDATE", "care_plans/$planId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "CARE_PLAN_UPDATE", "care_plans/$planId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (title.isBlank() || goal.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val updated = existing.copy(
            titleEn = title.trim(),
            titleFa = title.trim(),
            goalEn = goal.trim(),
            goalFa = goal.trim(),
            progressPct = progressPct.coerceIn(0, 100),
            status = status.name,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertCarePlan(updated)
        audit(actor, "CARE_PLAN_UPDATED", "care_plans/$planId", existing.patientId, "ALLOWED", "${updated.titleEn} (${updated.progressPct}%)")
        return ClinicalOpResult.Success(updated)
    }

    suspend fun verifyCarePlan(actor: UserEntity?, planId: String): ClinicalOpResult<Unit> {
        val existing = dao.getCarePlanById(planId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CARE_PLAN_VERIFY", "care_plans/$planId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CARE_PLAN_VERIFY", "care_plans/$planId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.updateCarePlanVerification(planId, VerificationStatus.VERIFIED.name)
        audit(actor, "CARE_PLAN_VERIFIED", "care_plans/$planId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    suspend fun deleteCarePlan(actor: UserEntity?, planId: String): ClinicalOpResult<Unit> {
        val existing = dao.getCarePlanById(planId)
            ?: return ClinicalOpResult.Denied(ClinicalAccessError.RECORD_NOT_FOUND)
        val (patient, missing) = loadPatientOrDeny(actor, existing.patientId, "CARE_PLAN_DELETE", "care_plans/$planId")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(actor, patient!!, existing.sourceType)
        if (err != null) {
            audit(actor, "CARE_PLAN_DELETE", "care_plans/$planId", existing.patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        dao.deleteCarePlanById(planId)
        audit(actor, "CARE_PLAN_DELETED", "care_plans/$planId", existing.patientId, "ALLOWED", existing.titleEn)
        return ClinicalOpResult.Success(Unit)
    }

    // --- 11. Clinical Safety Decisions (Clinician Only) ---

    suspend fun recordClinicalDecision(
        actor: UserEntity?,
        patientId: String,
        alertKey: String,
        titleEn: String,
        titleFa: String,
        severity: AlertSeverity,
        evidenceVersion: String,
        action: ClinicalDecisionAction,
        rationale: String
    ): ClinicalOpResult<ClinicalDecisionEntity> {
        val (patient, missing) = loadPatientOrDeny(actor, patientId, "CDS_DECISION", "cds/$alertKey")
        if (missing != null) return missing
        val err = ClinicalAuthorizationPolicy.evaluateVerifyAccess(actor, patient!!)
        if (err != null) {
            audit(actor, "CDS_DECISION", "cds/$alertKey", patientId, "DENIED", err.name)
            return ClinicalOpResult.Denied(err)
        }
        if (rationale.isBlank()) return ClinicalOpResult.Denied(ClinicalAccessError.INVALID_INPUT)

        val entity = ClinicalDecisionEntity(
            id = "dec_${UUID.randomUUID().toString().take(8)}",
            patientId = patientId,
            alertKey = alertKey,
            titleEn = titleEn,
            titleFa = titleFa,
            severity = severity.name,
            action = action.name,
            rationale = rationale.trim(),
            reviewerName = "${actor!!.firstName} ${actor.lastName}",
            evidenceVersion = evidenceVersion,
            decidedAtMillis = System.currentTimeMillis()
        )
        dao.insertDecision(entity)
        audit(actor, "CLINICAL_DECISION_RECORDED", "cds/$alertKey", patientId, "ALLOWED", "Action=${action.name}; ${rationale.take(80)}")
        return ClinicalOpResult.Success(entity)
    }
}
