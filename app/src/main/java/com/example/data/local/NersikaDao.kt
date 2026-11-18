package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NersikaDao {

    // Users & Auth
    @Query("SELECT * FROM users ORDER BY createdAtMillis ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUserById(id: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query(
        "UPDATE users SET emailVerified = 1, verificationTokenHash = NULL, verificationExpiresAtMillis = 0, verificationAttempts = 0 WHERE id = :userId"
    )
    suspend fun markEmailVerified(userId: String)

    @Query(
        "UPDATE users SET verificationTokenHash = :tokenHash, verificationExpiresAtMillis = :expiresAt, verificationSentAtMillis = :sentAt, verificationAttempts = 0 WHERE id = :userId"
    )
    suspend fun updateVerificationChallenge(
        userId: String,
        tokenHash: String,
        expiresAt: Long,
        sentAt: Long
    )

    @Query("UPDATE users SET verificationAttempts = :attempts WHERE id = :userId")
    suspend fun updateVerificationAttempts(userId: String, attempts: Int)

    @Query(
        "UPDATE users SET resetTokenHash = :tokenHash, resetExpiresAtMillis = :expiresAt, resetAttempts = 0 WHERE id = :userId"
    )
    suspend fun updatePasswordResetChallenge(
        userId: String,
        tokenHash: String,
        expiresAt: Long
    )

    @Query("UPDATE users SET resetAttempts = :attempts WHERE id = :userId")
    suspend fun updateResetAttempts(userId: String, attempts: Int)

    @Query(
        "UPDATE users SET passwordHash = :hash, passwordSalt = :salt, resetTokenHash = NULL, resetExpiresAtMillis = 0, resetAttempts = 0, failedLoginAttempts = 0, lockedUntilMillis = 0, tokenVersion = tokenVersion + 1 WHERE id = :userId"
    )
    suspend fun updatePasswordAndInvalidateTokens(userId: String, hash: String, salt: String)

    @Query(
        "UPDATE users SET failedLoginAttempts = :attempts, lockedUntilMillis = :lockedUntil WHERE id = :userId"
    )
    suspend fun updateLoginFailureState(userId: String, attempts: Int, lockedUntil: Long)

    @Query("UPDATE users SET tokenVersion = tokenVersion + 1 WHERE id = :userId")
    suspend fun incrementTokenVersion(userId: String)

    @Query("UPDATE users SET onboardingCompleted = 1 WHERE id = :userId")
    suspend fun markOnboardingCompleted(userId: String)

    @Query("UPDATE users SET mfaEnabled = :enabled WHERE id = :userId")
    suspend fun updateMfa(userId: String, enabled: Boolean)

    @Query("UPDATE users SET isVerifiedClinician = :verified WHERE id = :userId")
    suspend fun updateClinicianVerification(userId: String, verified: Boolean)

    // Patients
    @Query("SELECT * FROM patients ORDER BY firstNameEn ASC")
    fun observeAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :patientId LIMIT 1")
    fun observePatientById(patientId: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :patientId LIMIT 1")
    suspend fun getPatientById(patientId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatient(patient: PatientEntity)

    @Query(
        "UPDATE patients SET consentClinicianAccess = :clinician, consentCaregiverAccess = :caregiver, consentAiSummarization = :ai WHERE id = :patientId"
    )
    suspend fun updatePatientConsents(
        patientId: String,
        clinician: Boolean,
        caregiver: Boolean,
        ai: Boolean
    )

    // Conditions / Diagnoses
    @Query("SELECT * FROM conditions WHERE patientId = :patientId ORDER BY eventDateMillis DESC")
    fun observeConditions(patientId: String): Flow<List<ConditionEntity>>

    @Query("SELECT * FROM conditions WHERE id = :id LIMIT 1")
    suspend fun getConditionById(id: String): ConditionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: ConditionEntity)

    @Query("UPDATE conditions SET verificationStatus = :status WHERE id = :id")
    suspend fun updateConditionVerification(id: String, status: String)

    @Query("DELETE FROM conditions WHERE id = :id")
    suspend fun deleteConditionById(id: String)

    // Medical History
    @Query("SELECT * FROM medical_history WHERE patientId = :patientId ORDER BY eventDateMillis DESC")
    fun observeMedicalHistory(patientId: String): Flow<List<MedicalHistoryEntity>>

    @Query("SELECT * FROM medical_history WHERE id = :id LIMIT 1")
    suspend fun getMedicalHistoryById(id: String): MedicalHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedicalHistory(entry: MedicalHistoryEntity)

    @Query("UPDATE medical_history SET verificationStatus = :status WHERE id = :id")
    suspend fun updateMedicalHistoryVerification(id: String, status: String)

    @Query("DELETE FROM medical_history WHERE id = :id")
    suspend fun deleteMedicalHistoryById(id: String)

    // Allergies
    @Query("SELECT * FROM allergies WHERE patientId = :patientId ORDER BY eventDateMillis DESC")
    fun observeAllergies(patientId: String): Flow<List<AllergyEntity>>

    @Query("SELECT * FROM allergies WHERE id = :id LIMIT 1")
    suspend fun getAllergyById(id: String): AllergyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllergy(allergy: AllergyEntity)

    @Query("UPDATE allergies SET verificationStatus = :status WHERE id = :id")
    suspend fun updateAllergyVerification(id: String, status: String)

    @Query("DELETE FROM allergies WHERE id = :id")
    suspend fun deleteAllergyById(id: String)

    // Medications
    @Query("SELECT * FROM medications WHERE patientId = :patientId ORDER BY startDateMillis DESC")
    fun observeMedications(patientId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getMedicationById(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(medication: MedicationEntity)

    @Query("UPDATE medications SET status = :status, verificationStatus = :verificationStatus, reconciliationFlag = :reconciliationFlag WHERE id = :medId")
    suspend fun reconcileMedication(
        medId: String,
        status: String,
        verificationStatus: String,
        reconciliationFlag: Boolean
    )

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedicationById(id: String)

    // Lab Results
    @Query("SELECT * FROM lab_results WHERE patientId = :patientId ORDER BY resultDateMillis DESC")
    fun observeLabResults(patientId: String): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results WHERE id = :id LIMIT 1")
    suspend fun getLabResultById(id: String): LabResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabResult(lab: LabResultEntity)

    @Query("UPDATE lab_results SET verificationStatus = :status WHERE id = :id")
    suspend fun updateLabResultVerification(id: String, status: String)

    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun deleteLabResultById(id: String)

    // Observations / Vitals
    @Query("SELECT * FROM observations WHERE patientId = :patientId ORDER BY recordedAtMillis DESC")
    fun observeObservations(patientId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE id = :id LIMIT 1")
    suspend fun getObservationById(id: String): ObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    @Query("UPDATE observations SET verificationStatus = :status WHERE id = :id")
    suspend fun updateObservationVerification(id: String, status: String)

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteObservationById(id: String)

    // Medical Documents
    @Query("SELECT * FROM medical_documents WHERE patientId = :patientId ORDER BY uploadedAtMillis DESC")
    fun observeDocuments(patientId: String): Flow<List<MedicalDocumentEntity>>

    @Query("SELECT * FROM medical_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): MedicalDocumentEntity?

    @Query("SELECT COUNT(*) FROM medical_documents")
    fun observeTotalDocumentsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: MedicalDocumentEntity)

    @Query("UPDATE medical_documents SET verificationStatus = :status WHERE id = :docId")
    suspend fun updateDocumentVerification(docId: String, status: String)

    @Query("DELETE FROM medical_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    // Care Plans
    @Query("SELECT * FROM care_plans WHERE patientId = :patientId ORDER BY updatedAtMillis DESC")
    fun observeCarePlans(patientId: String): Flow<List<CarePlanEntity>>

    @Query("SELECT * FROM care_plans WHERE id = :id LIMIT 1")
    suspend fun getCarePlanById(id: String): CarePlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCarePlan(plan: CarePlanEntity)

    @Query("UPDATE care_plans SET verificationStatus = :status WHERE id = :id")
    suspend fun updateCarePlanVerification(id: String, status: String)

    @Query("DELETE FROM care_plans WHERE id = :id")
    suspend fun deleteCarePlanById(id: String)

    // Appointments
    @Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY scheduledAtMillis ASC")
    fun observeAppointments(patientId: String): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppointment(appointment: AppointmentEntity)

    // Clinical Decisions
    @Query("SELECT * FROM clinical_decisions WHERE patientId = :patientId ORDER BY decidedAtMillis DESC")
    fun observeDecisions(patientId: String): Flow<List<ClinicalDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: ClinicalDecisionEntity)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY createdAtMillis DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    // Audit Events
    @Query("SELECT * FROM audit_events ORDER BY timestampMillis DESC LIMIT 200")
    fun observeAuditEvents(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE targetPatientId = :patientId ORDER BY timestampMillis DESC LIMIT 100")
    fun observeAuditEventsForPatient(patientId: String): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(event: AuditEventEntity)
}
