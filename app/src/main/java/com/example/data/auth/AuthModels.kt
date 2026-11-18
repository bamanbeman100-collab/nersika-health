package com.example.data.auth

import com.example.data.local.UserEntity
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage

enum class AuthErrorCode {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    PASSWORD_MISMATCH,
    REQUIRED_FIELD,
    TERMS_NOT_ACCEPTED,
    EMAIL_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    EMAIL_NOT_VERIFIED,
    EMAIL_ALREADY_VERIFIED,
    INVALID_VERIFICATION_CODE,
    VERIFICATION_CODE_EXPIRED,
    TOO_MANY_VERIFICATION_ATTEMPTS,
    RESEND_COOLDOWN_ACTIVE,
    INVALID_RESET_TOKEN,
    RESET_TOKEN_EXPIRED,
    TOO_MANY_RESET_ATTEMPTS,
    EMAIL_PROVIDER_NOT_CONFIGURED,
    EMAIL_DELIVERY_FAILED,
    UNAUTHENTICATED,
    UNAUTHORIZED_ROLE,
    UNAUTHORIZED_ROUTE,
    ONBOARDING_REQUIRED
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class Failure(
        val code: AuthErrorCode,
        val retryAfterSeconds: Long = 0L,
        val detail: String? = null
    ) : AuthResult<Nothing>
}

sealed interface EmailDispatchResult {
    data class Dispatched(val providerName: String, val messageId: String) : EmailDispatchResult
    data class ProviderNotConfigured(val reason: String) : EmailDispatchResult
    data class Failed(val errorCode: String, val message: String) : EmailDispatchResult
}

interface EmailDispatchGateway {
    val isLiveProviderConfigured: Boolean
    val providerDisplayName: String

    suspend fun sendVerificationEmail(
        toEmail: String,
        recipientName: String,
        code: String,
        language: AppLanguage
    ): EmailDispatchResult

    suspend fun sendPasswordResetEmail(
        toEmail: String,
        recipientName: String,
        resetToken: String,
        language: AppLanguage
    ): EmailDispatchResult
}

/**
 * Default production-honest gateway when no live SMTP, transactional email API,
 * or Firebase Auth Email/Password provider is configured in this build.
 * Never fakes email delivery and never surfaces secret verification codes to the UI.
 */
class UnconfiguredEmailDispatchGateway : EmailDispatchGateway {
    override val isLiveProviderConfigured: Boolean = false
    override val providerDisplayName: String = "Unconfigured (Firebase Email/Password or SMTP required)"

    override suspend fun sendVerificationEmail(
        toEmail: String,
        recipientName: String,
        code: String,
        language: AppLanguage
    ): EmailDispatchResult = EmailDispatchResult.ProviderNotConfigured(
        "Live email delivery requires enabling Email/Password in Firebase Auth console or configuring an SMTP/Transactional email backend."
    )

    override suspend fun sendPasswordResetEmail(
        toEmail: String,
        recipientName: String,
        resetToken: String,
        language: AppLanguage
    ): EmailDispatchResult = EmailDispatchResult.ProviderNotConfigured(
        "Live password recovery email delivery requires configuring Firebase Auth Email/Password or an SMTP backend."
    )
}

data class PasswordStrengthReport(
    val minLengthMet: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean
) {
    val isStrong: Boolean
        get() = minLengthMet && hasUppercase && hasLowercase && hasDigit && hasSpecialChar

    val progressFraction: Float
        get() {
            val met = listOf(minLengthMet, hasUppercase, hasLowercase, hasDigit, hasSpecialChar).count { it }
            return met / 5f
        }
}

object AuthValidators {
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )
    private val OTP_REGEX = Regex("^\\d{6}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmail(raw: String): Boolean {
        val clean = normalizeEmail(raw)
        return clean.isNotBlank() && clean.length <= 254 && EMAIL_REGEX.matches(clean)
    }

    fun evaluatePassword(password: String): PasswordStrengthReport = PasswordStrengthReport(
        minLengthMet = password.length >= 8,
        hasUppercase = password.any { it.isUpperCase() },
        hasLowercase = password.any { it.isLowerCase() },
        hasDigit = password.any { it.isDigit() },
        hasSpecialChar = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    )

    fun isStrongPassword(password: String): Boolean = evaluatePassword(password).isStrong

    fun isValidVerificationCode(code: String): Boolean = OTP_REGEX.matches(code.trim())

    fun isAllowedPublicRole(role: UserRole): Boolean =
        role == UserRole.PATIENT || role == UserRole.CAREGIVER
}

data class RegistrationRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val country: String,
    val preferredLanguage: AppLanguage,
    val role: UserRole,
    val acceptedTerms: Boolean
)

data class RegistrationOutcome(
    val user: UserEntity,
    val emailDispatched: Boolean,
    val dispatchWarning: AuthErrorCode? = null
)

data class LoginOutcome(
    val user: UserEntity,
    val nextRoute: AppRoute,
    val sessionPersisted: Boolean
)

data class RecoveryRequestOutcome(
    val emailDispatched: Boolean,
    val dispatchWarning: AuthErrorCode? = null
)

data class OnboardingSubmission(
    val dateOfBirthIso: String = "1990-01-01",
    val ageYears: Int = 34,
    val sex: String = "Unspecified",
    val populationGroupName: String = "ADULT",
    val bloodType: String = "Unknown",
    val weightKg: Double = 0.0,
    val emergencyContact: String = "",
    val consentClinicianAccess: Boolean = true,
    val consentCaregiverAccess: Boolean = false,
    val consentAiSummarization: Boolean = false,
    val enableMfaRecommendation: Boolean = false,
    val clinicalSafetyCharterAccepted: Boolean = false
)
