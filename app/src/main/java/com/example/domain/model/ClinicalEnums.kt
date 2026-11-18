package com.example.domain.model

import com.example.i18n.AppStrings

enum class UserRole {
    PATIENT,
    CAREGIVER,
    PHYSICIAN,
    CLINICAL_PHARMACIST,
    NURSE,
    HEALTHCARE_COORDINATOR,
    ORGANIZATION_ADMIN,
    SYSTEM_ADMIN;

    fun displayName(s: AppStrings): String = when (this) {
        PATIENT -> s.rolePatient
        CAREGIVER -> s.roleCaregiver
        PHYSICIAN -> s.rolePhysician
        CLINICAL_PHARMACIST -> s.rolePharmacist
        NURSE -> s.roleNurse
        HEALTHCARE_COORDINATOR -> s.roleCoordinator
        ORGANIZATION_ADMIN -> s.roleOrgAdmin
        SYSTEM_ADMIN -> s.roleSystemAdmin
    }

    val isClinician: Boolean
        get() = this == PHYSICIAN || this == CLINICAL_PHARMACIST || this == NURSE

    val isAdmin: Boolean
        get() = this == ORGANIZATION_ADMIN || this == SYSTEM_ADMIN

    companion object {
        fun fromName(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: PATIENT
    }
}

enum class SourceType {
    PATIENT_REPORTED,
    ORGANIZATION_SOURCED,
    CLINICIAN_VERIFIED,
    DOCUMENT_EXTRACTED,
    AI_INFERRED;

    fun displayName(s: AppStrings): String = when (this) {
        PATIENT_REPORTED -> s.sourcePatientReported
        ORGANIZATION_SOURCED -> s.sourceOrganizationSourced
        CLINICIAN_VERIFIED -> s.sourceClinicianVerified
        DOCUMENT_EXTRACTED -> s.sourceDocumentExtracted
        AI_INFERRED -> s.sourceAiInferred
    }

    companion object {
        fun fromName(raw: String?): SourceType =
            entries.firstOrNull { it.name == raw } ?: PATIENT_REPORTED
    }
}

enum class VerificationStatus {
    VERIFIED,
    NEEDS_REVIEW,
    UNVERIFIED;

    fun displayName(s: AppStrings): String = when (this) {
        VERIFIED -> s.statusVerified
        NEEDS_REVIEW -> s.statusNeedsReview
        UNVERIFIED -> s.statusUnverified
    }

    companion object {
        fun fromName(raw: String?): VerificationStatus =
            entries.firstOrNull { it.name == raw } ?: NEEDS_REVIEW
    }
}

enum class MedicationStatus {
    PRESCRIBED,
    DISPENSED,
    ADMINISTERED,
    PATIENT_REPORTED,
    DISCONTINUED,
    UNCERTAIN;

    fun displayName(s: AppStrings): String = when (this) {
        PRESCRIBED -> s.medStatePrescribed
        DISPENSED -> s.medStateDispensed
        ADMINISTERED -> s.medStateAdministered
        PATIENT_REPORTED -> s.medStatePatientReported
        DISCONTINUED -> s.medStateDiscontinued
        UNCERTAIN -> s.medStateUncertain
    }

    companion object {
        fun fromName(raw: String?): MedicationStatus =
            entries.firstOrNull { it.name == raw } ?: UNCERTAIN
    }
}

enum class AlertSeverity {
    RED,
    AMBER,
    BLUE;

    fun displayName(s: AppStrings): String = when (this) {
        RED -> s.severityCritical
        AMBER -> s.severitySignificant
        BLUE -> s.severityInformational
    }

    companion object {
        fun fromName(raw: String?): AlertSeverity =
            entries.firstOrNull { it.name == raw } ?: BLUE
    }
}

enum class ClinicalDecisionAction {
    PENDING,
    ACCEPT,
    MODIFY,
    DEFER,
    REJECT;

    fun displayName(s: AppStrings): String = when (this) {
        PENDING -> s.pendingReviews
        ACCEPT -> s.actionAccept
        MODIFY -> s.actionModify
        DEFER -> s.actionDefer
        REJECT -> s.actionReject
    }

    companion object {
        fun fromName(raw: String?): ClinicalDecisionAction =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class LabFlag {
    NORMAL,
    LOW,
    HIGH,
    CRITICAL;

    fun displayName(s: AppStrings): String = when (this) {
        NORMAL -> s.flagNormal
        LOW -> s.flagLow
        HIGH -> s.flagHigh
        CRITICAL -> s.flagCritical
    }

    companion object {
        fun fromName(raw: String?): LabFlag =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class DocumentCategory(val labelEn: String, val labelFa: String) {
    LAB_REPORT("Laboratory Report", "گزارش آزمایشگاه"),
    PRESCRIPTION("Prescription", "نسخه دارویی"),
    DISCHARGE_SUMMARY("Discharge Summary", "خلاصه ترخیص"),
    IMAGING("Imaging Report", "گزارش تصویربرداری"),
    CLINICAL_NOTE("Clinical Note", "یادداشت بالینی"),
    REFERRAL("Referral Letter", "ارجاع‌نامه"),
    CERTIFICATE("Medical Certificate", "گواهی پزشکی"),
    OTHER("Other Medical Document", "سایر مدارک پزشکی");

    companion object {
        fun fromName(raw: String?): DocumentCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class PopulationGroup(val labelEn: String, val labelFa: String) {
    ADULT("Adult", "بزرگسال"),
    GERIATRIC("Older Adult (65+)", "سالمند (+۶۵)"),
    PEDIATRIC("Pediatric", "کودک"),
    NEONATAL("Neonatal", "نوزاد"),
    PREGNANCY("Maternal / Pregnancy", "بارداری");

    companion object {
        fun fromName(raw: String?): PopulationGroup =
            entries.firstOrNull { it.name == raw } ?: ADULT
    }
}

enum class MedicalHistoryCategory(val labelEn: String, val labelFa: String) {
    SURGICAL("Past Surgical", "سابقه جراحی"),
    HOSPITALIZATION("Hospitalization", "سابقه بستری"),
    FAMILY("Family History", "سابقه خانوادگی"),
    IMMUNIZATION("Immunization", "واکسیناسیون"),
    SOCIAL("Lifestyle & Social", "سبک زندگی و اجتماعی");

    companion object {
        fun fromName(raw: String?): MedicalHistoryCategory =
            entries.firstOrNull { it.name == raw } ?: HOSPITALIZATION
    }
}

enum class CarePlanStatus(val labelEn: String, val labelFa: String) {
    ACTIVE("Active", "فعال"),
    COMPLETED("Completed", "تکمیل‌شده"),
    PAUSED("Paused", "متوقف موقت");

    companion object {
        fun fromName(raw: String?): CarePlanStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}
