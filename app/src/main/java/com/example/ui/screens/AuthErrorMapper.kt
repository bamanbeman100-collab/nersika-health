package com.example.ui.screens

import com.example.data.auth.AuthErrorCode
import com.example.data.clinical.ClinicalAccessError
import com.example.i18n.AppStrings

fun AuthErrorCode.localizedMessage(s: AppStrings, retryAfterSeconds: Long = 0L): String {
    val base = when (this) {
        AuthErrorCode.INVALID_EMAIL -> s.errInvalidEmail
        AuthErrorCode.WEAK_PASSWORD -> s.errWeakPassword
        AuthErrorCode.PASSWORD_MISMATCH -> s.errPasswordMismatch
        AuthErrorCode.REQUIRED_FIELD -> s.errRequiredField
        AuthErrorCode.TERMS_NOT_ACCEPTED -> s.errAcceptTerms
        AuthErrorCode.EMAIL_ALREADY_EXISTS -> s.errEmailExists
        AuthErrorCode.INVALID_CREDENTIALS -> s.errInvalidCredentials
        AuthErrorCode.ACCOUNT_LOCKED -> s.errAccountLocked
        AuthErrorCode.EMAIL_NOT_VERIFIED -> s.verifyEmailSubtitle
        AuthErrorCode.EMAIL_ALREADY_VERIFIED -> s.statusVerified
        AuthErrorCode.INVALID_VERIFICATION_CODE -> s.errInvalidVerificationCode
        AuthErrorCode.VERIFICATION_CODE_EXPIRED -> s.errVerificationExpired
        AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS -> s.errTooManyAttempts
        AuthErrorCode.RESEND_COOLDOWN_ACTIVE -> s.errResendCooldown
        AuthErrorCode.INVALID_RESET_TOKEN -> s.errInvalidResetToken
        AuthErrorCode.RESET_TOKEN_EXPIRED -> s.errResetTokenExpired
        AuthErrorCode.TOO_MANY_RESET_ATTEMPTS -> s.errTooManyAttempts
        AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED -> s.errEmailProviderUnconfigured
        AuthErrorCode.EMAIL_DELIVERY_FAILED -> s.errEmailDeliveryFailed
        AuthErrorCode.UNAUTHENTICATED -> s.errUnauthorizedRoute
        AuthErrorCode.UNAUTHORIZED_ROLE -> s.errUnauthorizedRole
        AuthErrorCode.UNAUTHORIZED_ROUTE -> s.errUnauthorizedRoute
        AuthErrorCode.ONBOARDING_REQUIRED -> s.onboardingSubtitle
    }
    return if (retryAfterSeconds > 0L) "$base (${retryAfterSeconds}s)" else base
}

fun ClinicalAccessError.localizedMessage(s: AppStrings): String = when (this) {
    ClinicalAccessError.UNAUTHENTICATED -> s.errUnauthorizedRoute
    ClinicalAccessError.EMAIL_NOT_VERIFIED -> s.verifyEmailSubtitle
    ClinicalAccessError.ONBOARDING_REQUIRED -> s.onboardingSubtitle
    ClinicalAccessError.PATIENT_NOT_FOUND,
    ClinicalAccessError.RECORD_NOT_FOUND -> s.noItemsTitle
    ClinicalAccessError.CONSENT_DENIED -> s.errClinicalConsentDenied
    ClinicalAccessError.UNAUTHORIZED_ROLE -> s.errClinicalUnauthorizedRole
    ClinicalAccessError.UNVERIFIED_CLINICIAN -> s.errClinicalUnverifiedClinician
    ClinicalAccessError.IMMUTABLE_CLINICIAN_RECORD -> s.errClinicalImmutableRecord
    ClinicalAccessError.INVALID_INPUT -> s.errRequiredField
}
