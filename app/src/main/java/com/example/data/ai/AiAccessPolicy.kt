package com.example.data.ai

import com.example.data.clinical.ClinicalAccessError
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.local.PatientEntity
import com.example.data.local.UserEntity
import com.example.domain.model.UserRole

object AiAccessPolicy {

    fun evaluate(
        actor: UserEntity?,
        patient: PatientEntity,
        taskType: AiTaskType
    ): AiFailureReason? {
        if (actor == null) return AiFailureReason.UNAUTHENTICATED
        if (!actor.emailVerified) return AiFailureReason.EMAIL_NOT_VERIFIED
        if (!actor.onboardingCompleted) return AiFailureReason.ONBOARDING_REQUIRED

        val readError = ClinicalAuthorizationPolicy.evaluateReadAccess(actor, patient)
        if (readError != null) {
            return when (readError) {
                ClinicalAccessError.UNVERIFIED_CLINICIAN -> AiFailureReason.CLINICIAN_ROLE_REQUIRED
                else -> AiFailureReason.RECORD_ACCESS_DENIED
            }
        }

        if (!patient.consentAiSummarization) {
            return AiFailureReason.CONSENT_AI_DISABLED
        }

        if (taskType == AiTaskType.CLINICIAN_RECOMMENDATIONS) {
            val role = UserRole.fromName(actor.role)
            if (!role.isClinician || !actor.isVerifiedClinician) {
                return AiFailureReason.CLINICIAN_ROLE_REQUIRED
            }
        }

        return null
    }

    fun isVerifiedClinician(actor: UserEntity?): Boolean {
        if (actor == null) return false
        val role = UserRole.fromName(actor.role)
        return role.isClinician && actor.isVerifiedClinician
    }
}
