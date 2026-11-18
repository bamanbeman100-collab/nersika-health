package com.example.data.auth

import com.example.data.local.AuditEventEntity
import com.example.data.local.NersikaDao
import com.example.data.local.PatientEntity
import com.example.data.local.PersistedSession
import com.example.data.local.SecurityHasher
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferencesRepository
import com.example.domain.model.PopulationGroup
import com.example.domain.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepository(
    private val dao: NersikaDao,
    private val prefs: UserPreferencesRepository,
    private val emailGateway: EmailDispatchGateway,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val VERIFICATION_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes
        const val RESET_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes
        const val RESEND_COOLDOWN_MILLIS = 60 * 1000L // 60 seconds
        const val SESSION_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 days
        const val MAX_OTP_ATTEMPTS = 5
        const val MAX_FAILED_LOGINS = 5
        const val LOCKOUT_DURATION_MILLIS = 5 * 60 * 1000L // 5 minutes
    }

    // Ephemeral session is used only when rememberMe == false so closing/recreating the process ends the session.
    private val ephemeralSession = MutableStateFlow<PersistedSession?>(null)

    val isLiveEmailConfigured: Boolean
        get() = emailGateway.isLiveProviderConfigured

    val emailProviderName: String
        get() = emailGateway.providerDisplayName

    fun observeAuthenticatedUser(): Flow<UserEntity?> {
        return combine(prefs.persistedSessionFlow, ephemeralSession) { persisted, ephemeral ->
            ephemeral ?: persisted
        }.flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                dao.observeUserById(session.userId).map { user ->
                    if (user == null) {
                        null
                    } else if (user.tokenVersion != session.tokenVersion || clock() > session.expiresAtMillis) {
                        null
                    } else {
                        user
                    }
                }
            }
        }
    }

    suspend fun registerWithEmail(request: RegistrationRequest): AuthResult<RegistrationOutcome> {
        val firstName = request.firstName.trim()
        val lastName = request.lastName.trim()
        val cleanEmail = AuthValidators.normalizeEmail(request.email)

        if (firstName.isBlank() || lastName.isBlank()) {
            return AuthResult.Failure(AuthErrorCode.REQUIRED_FIELD)
        }
        if (!AuthValidators.isValidEmail(cleanEmail)) {
            return AuthResult.Failure(AuthErrorCode.INVALID_EMAIL)
        }
        if (!AuthValidators.isStrongPassword(request.password)) {
            return AuthResult.Failure(AuthErrorCode.WEAK_PASSWORD)
        }
        if (request.password != request.confirmPassword) {
            return AuthResult.Failure(AuthErrorCode.PASSWORD_MISMATCH)
        }
        if (!request.acceptedTerms) {
            return AuthResult.Failure(AuthErrorCode.TERMS_NOT_ACCEPTED)
        }
        if (!AuthValidators.isAllowedPublicRole(request.role)) {
            recordAudit(
                actorId = "anonymous",
                actorName = cleanEmail,
                actorRole = request.role.name,
                actionType = "ROLE_ESCALATION_BLOCKED",
                targetResource = "auth/register",
                targetPatientId = null,
                outcome = "DENIED",
                details = "Blocked unauthorized public registration for role ${request.role.name}"
            )
            return AuthResult.Failure(AuthErrorCode.UNAUTHORIZED_ROLE)
        }

        if (dao.getUserByEmail(cleanEmail) != null) {
            return AuthResult.Failure(AuthErrorCode.EMAIL_ALREADY_EXISTS)
        }

        val now = clock()
        val userId = "usr_${UUID.randomUUID().toString().take(8)}"
        val patientId = if (request.role == UserRole.PATIENT) "pat_${UUID.randomUUID().toString().take(8)}" else "pat_001"
        val salt = SecurityHasher.generateSalt()
        val passwordHash = SecurityHasher.hashPassword(request.password, salt)

        val otp = SecurityHasher.generateNumericOtp(6)
        val otpHash = SecurityHasher.sha256(otp)

        val newUser = UserEntity(
            id = userId,
            email = cleanEmail,
            passwordHash = passwordHash,
            passwordSalt = salt,
            firstName = firstName,
            lastName = lastName,
            role = request.role.name,
            country = request.country.trim().ifBlank { "Iran" },
            preferredLanguage = request.preferredLanguage.code,
            emailVerified = false,
            verificationTokenHash = otpHash,
            verificationExpiresAtMillis = now + VERIFICATION_TTL_MILLIS,
            verificationSentAtMillis = now,
            verificationAttempts = 0,
            resetTokenHash = null,
            resetExpiresAtMillis = 0L,
            resetAttempts = 0,
            failedLoginAttempts = 0,
            lockedUntilMillis = 0L,
            tokenVersion = 1,
            onboardingCompleted = false,
            mfaEnabled = false,
            organizationName = if (request.role == UserRole.PATIENT) "Personal Health Account" else "Family Caregiver",
            isVerifiedClinician = false,
            isDemo = false,
            linkedPatientId = patientId,
            createdAtMillis = now
        )
        dao.upsertUser(newUser)

        if (request.role == UserRole.PATIENT) {
            dao.upsertPatient(
                PatientEntity(
                    id = patientId,
                    mrn = "NRK-${(40000..99999).random()}",
                    linkedUserId = userId,
                    firstNameEn = firstName,
                    lastNameEn = lastName,
                    firstNameFa = firstName,
                    lastNameFa = lastName,
                    dateOfBirthIso = "1990-01-01",
                    ageYears = 34,
                    sex = "Unspecified",
                    populationGroup = PopulationGroup.ADULT.name,
                    bloodType = "Unknown",
                    phone = "",
                    emergencyContact = "",
                    weightKg = 0.0,
                    isPregnant = false,
                    consentClinicianAccess = true,
                    consentCaregiverAccess = false,
                    consentAiSummarization = false,
                    verifiedIdentity = false,
                    profileCompletionPct = 35
                )
            )
        }

        prefs.setLanguage(request.preferredLanguage)
        issueSession(newUser, rememberMe = true, now = now)

        val dispatchResult = emailGateway.sendVerificationEmail(
            toEmail = cleanEmail,
            recipientName = "$firstName $lastName",
            code = otp,
            language = request.preferredLanguage
        )

        val warningCode = when (dispatchResult) {
            is EmailDispatchResult.Dispatched -> null
            is EmailDispatchResult.ProviderNotConfigured -> AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED
            is EmailDispatchResult.Failed -> AuthErrorCode.EMAIL_DELIVERY_FAILED
        }

        recordAudit(
            actorId = userId,
            actorName = "$firstName $lastName",
            actorRole = request.role.name,
            actionType = "USER_REGISTERED",
            targetResource = "users/$userId",
            targetPatientId = patientId,
            outcome = "RECORDED",
            details = "Registered ${request.role.name}; emailVerified=false; emailDispatched=${dispatchResult is EmailDispatchResult.Dispatched}"
        )

        return AuthResult.Success(
            RegistrationOutcome(
                user = newUser,
                emailDispatched = dispatchResult is EmailDispatchResult.Dispatched,
                dispatchWarning = warningCode
            )
        )
    }

    suspend fun loginWithEmail(
        email: String,
        password: String,
        rememberMe: Boolean
    ): AuthResult<LoginOutcome> {
        val cleanEmail = AuthValidators.normalizeEmail(email)
        if (!AuthValidators.isValidEmail(cleanEmail)) {
            return AuthResult.Failure(AuthErrorCode.INVALID_EMAIL)
        }
        if (password.isBlank()) {
            return AuthResult.Failure(AuthErrorCode.REQUIRED_FIELD)
        }

        val now = clock()
        val user = dao.getUserByEmail(cleanEmail)
        if (user == null) {
            recordAudit(
                actorId = "anonymous",
                actorName = cleanEmail,
                actorRole = "UNAUTHENTICATED",
                actionType = "LOGIN_FAILED",
                targetResource = "auth/login",
                targetPatientId = null,
                outcome = "DENIED",
                details = "Authentication failed: unknown email"
            )
            return AuthResult.Failure(AuthErrorCode.INVALID_CREDENTIALS)
        }

        if (user.lockedUntilMillis > now) {
            val remainingSec = ((user.lockedUntilMillis - now) / 1000L).coerceAtLeast(1L)
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "LOGIN_LOCKED",
                targetResource = "auth/login",
                targetPatientId = user.linkedPatientId,
                outcome = "DENIED",
                details = "Locked out for $remainingSec seconds after repeated failures"
            )
            return AuthResult.Failure(AuthErrorCode.ACCOUNT_LOCKED, retryAfterSeconds = remainingSec)
        }

        val passwordValid = SecurityHasher.verifyPassword(password, user.passwordSalt, user.passwordHash)
        if (!passwordValid) {
            val nextFailures = user.failedLoginAttempts + 1
            val lockUntil = if (nextFailures >= MAX_FAILED_LOGINS) now + LOCKOUT_DURATION_MILLIS else 0L
            dao.updateLoginFailureState(user.id, nextFailures, lockUntil)
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "LOGIN_FAILED",
                targetResource = "auth/login",
                targetPatientId = user.linkedPatientId,
                outcome = "DENIED",
                details = "Invalid password attempt ($nextFailures/$MAX_FAILED_LOGINS)"
            )
            return if (lockUntil > 0L) {
                AuthResult.Failure(
                    AuthErrorCode.ACCOUNT_LOCKED,
                    retryAfterSeconds = LOCKOUT_DURATION_MILLIS / 1000L
                )
            } else {
                AuthResult.Failure(AuthErrorCode.INVALID_CREDENTIALS)
            }
        }

        // Clear failure counter and issue cryptographic session
        dao.updateLoginFailureState(user.id, 0, 0L)
        val refreshed = dao.getUserById(user.id) ?: user
        issueSession(refreshed, rememberMe = rememberMe, now = now)

        val nextRoute = RouteAccessPolicy.defaultEntryRoute(refreshed)
        recordAudit(
            actorId = refreshed.id,
            actorName = "${refreshed.firstName} ${refreshed.lastName}",
            actorRole = refreshed.role,
            actionType = "LOGIN_SUCCESS",
            targetResource = "auth/session",
            targetPatientId = refreshed.linkedPatientId,
            outcome = "ALLOWED",
            details = "Authenticated session started (verified=${refreshed.emailVerified}, onboarded=${refreshed.onboardingCompleted}, remember=$rememberMe)"
        )
        return AuthResult.Success(
            LoginOutcome(
                user = refreshed,
                nextRoute = nextRoute,
                sessionPersisted = rememberMe
            )
        )
    }

    suspend fun verifyEmail(userId: String, rawCode: String): AuthResult<UserEntity> {
        val code = rawCode.trim()
        if (!AuthValidators.isValidVerificationCode(code)) {
            return AuthResult.Failure(AuthErrorCode.INVALID_VERIFICATION_CODE)
        }

        val user = dao.getUserById(userId)
            ?: return AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED)

        if (user.emailVerified) {
            return AuthResult.Failure(AuthErrorCode.EMAIL_ALREADY_VERIFIED)
        }

        val now = clock()
        val storedHash = user.verificationTokenHash
        if (storedHash.isNullOrBlank() || user.verificationExpiresAtMillis <= 0L) {
            return AuthResult.Failure(AuthErrorCode.VERIFICATION_CODE_EXPIRED)
        }
        if (user.verificationAttempts >= MAX_OTP_ATTEMPTS) {
            return AuthResult.Failure(AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS)
        }
        if (now > user.verificationExpiresAtMillis) {
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "EMAIL_VERIFICATION_EXPIRED",
                targetResource = "users/${user.id}",
                targetPatientId = user.linkedPatientId,
                outcome = "DENIED",
                details = "Expired email verification token rejected"
            )
            return AuthResult.Failure(AuthErrorCode.VERIFICATION_CODE_EXPIRED)
        }

        val inputHash = SecurityHasher.sha256(code)
        if (!SecurityHasher.constantTimeEquals(inputHash, storedHash)) {
            val attempts = user.verificationAttempts + 1
            dao.updateVerificationAttempts(user.id, attempts)
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "EMAIL_VERIFICATION_FAILED",
                targetResource = "users/${user.id}",
                targetPatientId = user.linkedPatientId,
                outcome = "DENIED",
                details = "Invalid verification code attempt ($attempts/$MAX_OTP_ATTEMPTS)"
            )
            return if (attempts >= MAX_OTP_ATTEMPTS) {
                AuthResult.Failure(AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS)
            } else {
                AuthResult.Failure(AuthErrorCode.INVALID_VERIFICATION_CODE)
            }
        }

        dao.markEmailVerified(user.id)
        val verifiedUser = dao.getUserById(user.id) ?: user.copy(emailVerified = true)
        recordAudit(
            actorId = user.id,
            actorName = "${user.firstName} ${user.lastName}",
            actorRole = user.role,
            actionType = "EMAIL_VERIFIED",
            targetResource = "users/${user.id}",
            targetPatientId = user.linkedPatientId,
            outcome = "ALLOWED",
            details = "Email ownership cryptographically verified."
        )
        return AuthResult.Success(verifiedUser)
    }

    suspend fun resendVerificationCode(userId: String): AuthResult<Boolean> {
        val user = dao.getUserById(userId)
            ?: return AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED)
        if (user.emailVerified) {
            return AuthResult.Failure(AuthErrorCode.EMAIL_ALREADY_VERIFIED)
        }

        val now = clock()
        val elapsedSinceLast = now - user.verificationSentAtMillis
        if (user.verificationSentAtMillis > 0L && elapsedSinceLast < RESEND_COOLDOWN_MILLIS) {
            val waitSec = ((RESEND_COOLDOWN_MILLIS - elapsedSinceLast) / 1000L).coerceAtLeast(1L)
            return AuthResult.Failure(AuthErrorCode.RESEND_COOLDOWN_ACTIVE, retryAfterSeconds = waitSec)
        }

        val otp = SecurityHasher.generateNumericOtp(6)
        val otpHash = SecurityHasher.sha256(otp)
        dao.updateVerificationChallenge(
            userId = user.id,
            tokenHash = otpHash,
            expiresAt = now + VERIFICATION_TTL_MILLIS,
            sentAt = now
        )

        val dispatchResult = emailGateway.sendVerificationEmail(
            toEmail = user.email,
            recipientName = "${user.firstName} ${user.lastName}",
            code = otp,
            language = AppLanguageFromCode(user.preferredLanguage)
        )

        recordAudit(
            actorId = user.id,
            actorName = "${user.firstName} ${user.lastName}",
            actorRole = user.role,
            actionType = "VERIFICATION_RESEND_REQUESTED",
            targetResource = "users/${user.id}",
            targetPatientId = user.linkedPatientId,
            outcome = if (dispatchResult is EmailDispatchResult.Dispatched) "RECORDED" else "DENIED",
            details = "Verification email dispatch status: ${dispatchResult::class.simpleName}"
        )

        return when (dispatchResult) {
            is EmailDispatchResult.Dispatched -> AuthResult.Success(true)
            is EmailDispatchResult.ProviderNotConfigured -> AuthResult.Failure(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED)
            is EmailDispatchResult.Failed -> AuthResult.Failure(AuthErrorCode.EMAIL_DELIVERY_FAILED, detail = dispatchResult.message)
        }
    }

    suspend fun requestPasswordRecovery(email: String): AuthResult<RecoveryRequestOutcome> {
        val cleanEmail = AuthValidators.normalizeEmail(email)
        if (!AuthValidators.isValidEmail(cleanEmail)) {
            return AuthResult.Failure(AuthErrorCode.INVALID_EMAIL)
        }

        if (!emailGateway.isLiveProviderConfigured) {
            recordAudit(
                actorId = "anonymous",
                actorName = cleanEmail,
                actorRole = "UNAUTHENTICATED",
                actionType = "PASSWORD_RECOVERY_UNCONFIGURED",
                targetResource = "auth/recovery",
                targetPatientId = null,
                outcome = "DENIED",
                details = "Password recovery email requested while live email provider is unconfigured."
            )
            return AuthResult.Failure(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED)
        }

        val user = dao.getUserByEmail(cleanEmail)
        if (user == null) {
            // Anti-enumeration: do not reveal whether the email exists when live provider is configured.
            return AuthResult.Success(RecoveryRequestOutcome(emailDispatched = true))
        }

        val now = clock()
        val token = SecurityHasher.generateNumericOtp(6)
        val tokenHash = SecurityHasher.sha256(token)
        dao.updatePasswordResetChallenge(
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = now + RESET_TTL_MILLIS
        )

        val dispatchResult = emailGateway.sendPasswordResetEmail(
            toEmail = user.email,
            recipientName = "${user.firstName} ${user.lastName}",
            resetToken = token,
            language = AppLanguageFromCode(user.preferredLanguage)
        )

        recordAudit(
            actorId = user.id,
            actorName = "${user.firstName} ${user.lastName}",
            actorRole = user.role,
            actionType = "PASSWORD_RECOVERY_REQUESTED",
            targetResource = "users/${user.id}",
            targetPatientId = null,
            outcome = if (dispatchResult is EmailDispatchResult.Dispatched) "RECORDED" else "DENIED",
            details = "Recovery email dispatch status: ${dispatchResult::class.simpleName}"
        )

        return when (dispatchResult) {
            is EmailDispatchResult.Dispatched -> AuthResult.Success(RecoveryRequestOutcome(emailDispatched = true))
            is EmailDispatchResult.ProviderNotConfigured -> AuthResult.Failure(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED)
            is EmailDispatchResult.Failed -> AuthResult.Failure(AuthErrorCode.EMAIL_DELIVERY_FAILED, detail = dispatchResult.message)
        }
    }

    suspend fun resetPasswordWithToken(
        email: String,
        rawToken: String,
        newPassword: String,
        confirmPassword: String
    ): AuthResult<Unit> {
        val cleanEmail = AuthValidators.normalizeEmail(email)
        val token = rawToken.trim()

        if (!AuthValidators.isValidEmail(cleanEmail)) {
            return AuthResult.Failure(AuthErrorCode.INVALID_EMAIL)
        }
        if (token.isBlank()) {
            return AuthResult.Failure(AuthErrorCode.INVALID_RESET_TOKEN)
        }
        if (!AuthValidators.isStrongPassword(newPassword)) {
            return AuthResult.Failure(AuthErrorCode.WEAK_PASSWORD)
        }
        if (newPassword != confirmPassword) {
            return AuthResult.Failure(AuthErrorCode.PASSWORD_MISMATCH)
        }

        val user = dao.getUserByEmail(cleanEmail)
            ?: return AuthResult.Failure(AuthErrorCode.INVALID_RESET_TOKEN)

        val storedHash = user.resetTokenHash
        if (storedHash.isNullOrBlank() || user.resetExpiresAtMillis <= 0L) {
            return AuthResult.Failure(AuthErrorCode.INVALID_RESET_TOKEN)
        }
        if (user.resetAttempts >= MAX_OTP_ATTEMPTS) {
            return AuthResult.Failure(AuthErrorCode.TOO_MANY_RESET_ATTEMPTS)
        }

        val now = clock()
        if (now > user.resetExpiresAtMillis) {
            return AuthResult.Failure(AuthErrorCode.RESET_TOKEN_EXPIRED)
        }

        val inputHash = SecurityHasher.sha256(token)
        if (!SecurityHasher.constantTimeEquals(inputHash, storedHash)) {
            val attempts = user.resetAttempts + 1
            dao.updateResetAttempts(user.id, attempts)
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "PASSWORD_RESET_FAILED",
                targetResource = "users/${user.id}",
                targetPatientId = null,
                outcome = "DENIED",
                details = "Invalid password reset token attempt ($attempts/$MAX_OTP_ATTEMPTS)"
            )
            return if (attempts >= MAX_OTP_ATTEMPTS) {
                AuthResult.Failure(AuthErrorCode.TOO_MANY_RESET_ATTEMPTS)
            } else {
                AuthResult.Failure(AuthErrorCode.INVALID_RESET_TOKEN)
            }
        }

        val newSalt = SecurityHasher.generateSalt()
        val newHash = SecurityHasher.hashPassword(newPassword, newSalt)
        dao.updatePasswordAndInvalidateTokens(user.id, newHash, newSalt)
        // Revoke any active local or persisted session so stale tokens cannot continue.
        ephemeralSession.value = null
        prefs.clearSession()

        recordAudit(
            actorId = user.id,
            actorName = "${user.firstName} ${user.lastName}",
            actorRole = user.role,
            actionType = "PASSWORD_RESET",
            targetResource = "users/${user.id}",
            targetPatientId = null,
            outcome = "ALLOWED",
            details = "Password updated via verified recovery token; active sessions revoked."
        )
        return AuthResult.Success(Unit)
    }

    suspend fun completeRoleOnboarding(
        userId: String,
        submission: OnboardingSubmission
    ): AuthResult<UserEntity> {
        val user = dao.getUserById(userId)
            ?: return AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED)
        if (!user.emailVerified) {
            return AuthResult.Failure(AuthErrorCode.EMAIL_NOT_VERIFIED)
        }

        val role = UserRole.fromName(user.role)
        if (!submission.clinicalSafetyCharterAccepted) {
            return AuthResult.Failure(AuthErrorCode.TERMS_NOT_ACCEPTED)
        }

        if (role == UserRole.PATIENT && !user.linkedPatientId.isNullOrBlank()) {
            val existing = dao.getPatientById(user.linkedPatientId)
            if (existing != null) {
                val hasWeight = submission.weightKg > 0.0
                val hasEmergency = submission.emergencyContact.isNotBlank()
                val pct = 50 + (if (hasWeight) 20 else 0) + (if (hasEmergency) 20 else 0) + (if (submission.bloodType != "Unknown") 10 else 0)
                dao.upsertPatient(
                    existing.copy(
                        dateOfBirthIso = submission.dateOfBirthIso.ifBlank { existing.dateOfBirthIso },
                        ageYears = submission.ageYears.coerceIn(0, 120),
                        sex = submission.sex.ifBlank { existing.sex },
                        populationGroup = submission.populationGroupName.ifBlank { existing.populationGroup },
                        bloodType = submission.bloodType.ifBlank { existing.bloodType },
                        weightKg = submission.weightKg.coerceAtLeast(0.0),
                        emergencyContact = submission.emergencyContact.trim(),
                        consentClinicianAccess = submission.consentClinicianAccess,
                        consentCaregiverAccess = submission.consentCaregiverAccess,
                        consentAiSummarization = submission.consentAiSummarization,
                        profileCompletionPct = pct.coerceIn(35, 100)
                    )
                )
            }
        }

        if ((role.isClinician || role.isAdmin) && submission.enableMfaRecommendation) {
            dao.updateMfa(user.id, true)
        }

        dao.markOnboardingCompleted(user.id)
        val updated = dao.getUserById(user.id) ?: user.copy(onboardingCompleted = true)
        recordAudit(
            actorId = user.id,
            actorName = "${user.firstName} ${user.lastName}",
            actorRole = user.role,
            actionType = "ROLE_ONBOARDING_COMPLETED",
            targetResource = "users/${user.id}/onboarding",
            targetPatientId = user.linkedPatientId,
            outcome = "ALLOWED",
            details = "Completed ${role.name} onboarding"
        )
        return AuthResult.Success(updated)
    }

    suspend fun guardRouteAccess(user: UserEntity?, route: AppRoute): RouteGuardResult {
        val result = RouteAccessPolicy.evaluate(user, route)
        if (result is RouteGuardResult.Denied) {
            recordAudit(
                actorId = user?.id ?: "anonymous",
                actorName = user?.let { "${it.firstName} ${it.lastName}" } ?: "Unauthenticated Visitor",
                actorRole = user?.role ?: "UNAUTHENTICATED",
                actionType = "UNAUTHORIZED_ROUTE_ACCESS",
                targetResource = "route/${route.routeKey}",
                targetPatientId = user?.linkedPatientId,
                outcome = "DENIED",
                details = "Blocked route '${route.routeKey}' (${result.errorCode.name}); redirected to '${result.fallbackRoute.routeKey}'"
            )
        }
        return result
    }

    suspend fun guardAdminGovernanceAccess(user: UserEntity?): RouteGuardResult {
        val result = RouteAccessPolicy.canAccessAdminGovernance(user)
        if (result is RouteGuardResult.Denied) {
            recordAudit(
                actorId = user?.id ?: "anonymous",
                actorName = user?.let { "${it.firstName} ${it.lastName}" } ?: "Unauthenticated Visitor",
                actorRole = user?.role ?: "UNAUTHENTICATED",
                actionType = "UNAUTHORIZED_ADMIN_ACCESS",
                targetResource = "route/admin_governance",
                targetPatientId = null,
                outcome = "DENIED",
                details = "Blocked admin governance access (${result.errorCode.name})"
            )
        }
        return result
    }

    suspend fun logout(user: UserEntity?) {
        if (user != null) {
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "LOGOUT",
                targetResource = "auth/session",
                targetPatientId = null,
                outcome = "RECORDED",
                details = "Secure logout completed; session token revoked."
            )
            dao.incrementTokenVersion(user.id)
        }
        ephemeralSession.value = null
        prefs.clearSession()
    }

    private suspend fun issueSession(user: UserEntity, rememberMe: Boolean, now: Long) {
        val rawToken = SecurityHasher.generateSessionToken()
        val session = PersistedSession(
            userId = user.id,
            sessionTokenHash = SecurityHasher.sha256(rawToken),
            tokenVersion = user.tokenVersion,
            expiresAtMillis = now + SESSION_TTL_MILLIS
        )
        if (rememberMe) {
            ephemeralSession.value = null
            prefs.saveSession(session)
        } else {
            prefs.clearSession()
            ephemeralSession.value = session
        }
    }

    private suspend fun recordAudit(
        actorId: String,
        actorName: String,
        actorRole: String,
        actionType: String,
        targetResource: String,
        targetPatientId: String?,
        outcome: String,
        details: String
    ) {
        dao.insertAuditEvent(
            AuditEventEntity(
                id = "aud_${UUID.randomUUID().toString().take(10)}",
                actorId = actorId,
                actorName = actorName,
                actorRole = actorRole,
                actionType = actionType,
                targetResource = targetResource,
                targetPatientId = targetPatientId,
                outcome = outcome,
                details = details,
                timestampMillis = clock()
            )
        )
    }

    private fun AppLanguageFromCode(code: String) =
        com.example.i18n.AppLanguage.fromCode(code)
}
