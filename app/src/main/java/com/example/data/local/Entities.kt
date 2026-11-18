package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val firstName: String,
    val lastName: String,
    val role: String, // UserRole.name
    val country: String,
    val preferredLanguage: String, // "fa" or "en"
    val emailVerified: Boolean,
    val verificationTokenHash: String?,
    val verificationExpiresAtMillis: Long,
    val verificationSentAtMillis: Long,
    val verificationAttempts: Int,
    val resetTokenHash: String?,
    val resetExpiresAtMillis: Long,
    val resetAttempts: Int,
    val failedLoginAttempts: Int,
    val lockedUntilMillis: Long,
    val tokenVersion: Int,
    val onboardingCompleted: Boolean,
    val mfaEnabled: Boolean,
    val organizationName: String,
    val isVerifiedClinician: Boolean,
    val isDemo: Boolean,
    val linkedPatientId: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String,
    val mrn: String,
    val linkedUserId: String?,
    val firstNameEn: String,
    val lastNameEn: String,
    val firstNameFa: String,
    val lastNameFa: String,
    val dateOfBirthIso: String,
    val ageYears: Int,
    val sex: String,
    val populationGroup: String, // PopulationGroup.name
    val bloodType: String,
    val phone: String,
    val emergencyContact: String,
    val weightKg: Double,
    val isPregnant: Boolean,
    val consentClinicianAccess: Boolean,
    val consentCaregiverAccess: Boolean,
    val consentAiSummarization: Boolean,
    val verifiedIdentity: Boolean,
    val profileCompletionPct: Int
)

@Entity(
    tableName = "conditions",
    indices = [Index("patientId")]
)
data class ConditionEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val nameEn: String,
    val nameFa: String,
    val icd10Code: String,
    val status: String, // Active, Resolved, Monitoring
    val sourceType: String,
    val verificationStatus: String,
    val authorName: String,
    val eventDateMillis: Long,
    val entryDateMillis: Long,
    val notes: String
)

@Entity(
    tableName = "medical_history",
    indices = [Index("patientId")]
)
data class MedicalHistoryEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val category: String, // MedicalHistoryCategory.name
    val titleEn: String,
    val titleFa: String,
    val detailsEn: String,
    val detailsFa: String,
    val sourceType: String,
    val verificationStatus: String,
    val authorName: String,
    val recordedByUserId: String,
    val eventDateMillis: Long,
    val entryDateMillis: Long
)

@Entity(
    tableName = "allergies",
    indices = [Index("patientId")]
)
data class AllergyEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val allergenEn: String,
    val allergenFa: String,
    val reactionEn: String,
    val reactionFa: String,
    val severity: String, // RED, AMBER, BLUE
    val sourceType: String,
    val verificationStatus: String,
    val authorName: String,
    val eventDateMillis: Long,
    val entryDateMillis: Long
)

@Entity(
    tableName = "medications",
    indices = [Index("patientId")]
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val nameEn: String,
    val nameFa: String,
    val genericName: String,
    val brandName: String,
    val activeIngredient: String,
    val strength: String,
    val form: String,
    val route: String,
    val frequency: String,
    val status: String, // MedicationStatus.name
    val sourceType: String,
    val verificationStatus: String,
    val prescriberName: String,
    val reconciliationFlag: Boolean,
    val startDateMillis: Long,
    val notes: String
)

@Entity(
    tableName = "lab_results",
    indices = [Index("patientId")]
)
data class LabResultEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val testNameEn: String,
    val testNameFa: String,
    val loincCode: String,
    val value: Double,
    val unit: String,
    val originalValue: Double,
    val originalUnit: String,
    val referenceLow: Double,
    val referenceHigh: Double,
    val referenceNoteEn: String,
    val referenceNoteFa: String,
    val flag: String, // LabFlag.name
    val labName: String,
    val sourceType: String,
    val verificationStatus: String,
    val collectionDateMillis: Long,
    val resultDateMillis: Long
)

@Entity(
    tableName = "observations",
    indices = [Index("patientId")]
)
data class ObservationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val typeEn: String,
    val typeFa: String,
    val valueText: String,
    val unit: String,
    val sourceType: String,
    val verificationStatus: String,
    val authorName: String,
    val recordedAtMillis: Long
)

@Entity(
    tableName = "medical_documents",
    indices = [Index("patientId")]
)
data class MedicalDocumentEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val titleEn: String,
    val titleFa: String,
    val category: String, // DocumentCategory.name
    val fileUri: String,
    val mimeType: String,
    val fileSizeKb: Long,
    val sourceType: String,
    val verificationStatus: String,
    val extractedSummaryEn: String,
    val extractedSummaryFa: String,
    val uploadedBy: String,
    val uploadedAtMillis: Long
)

@Entity(
    tableName = "care_plans",
    indices = [Index("patientId")]
)
data class CarePlanEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val titleEn: String,
    val titleFa: String,
    val goalEn: String,
    val goalFa: String,
    val progressPct: Int,
    val status: String,
    val sourceType: String,
    val verificationStatus: String,
    val authorName: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "appointments",
    indices = [Index("patientId")]
)
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val clinicianName: String,
    val specialtyEn: String,
    val specialtyFa: String,
    val locationEn: String,
    val locationFa: String,
    val scheduledAtMillis: Long,
    val status: String, // Confirmed, Pending, Completed
    val reasonEn: String,
    val reasonFa: String
)

@Entity(
    tableName = "clinical_decisions",
    indices = [Index("patientId")]
)
data class ClinicalDecisionEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val alertKey: String,
    val titleEn: String,
    val titleFa: String,
    val severity: String,
    val action: String, // ClinicalDecisionAction.name
    val rationale: String,
    val reviewerName: String,
    val evidenceVersion: String,
    val decidedAtMillis: Long
)

@Entity(
    tableName = "notifications",
    indices = [Index("patientId")]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val category: String,
    val titleEn: String,
    val titleFa: String,
    val bodyEn: String,
    val bodyFa: String,
    val priority: String, // RED, AMBER, BLUE
    val isRead: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String,
    val actionType: String,
    val targetResource: String,
    val targetPatientId: String?,
    val outcome: String, // ALLOWED, DENIED, RECORDED
    val details: String,
    val timestampMillis: Long
)
