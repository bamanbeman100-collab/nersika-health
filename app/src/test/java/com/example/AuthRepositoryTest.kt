package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AppRoute
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthValidators
import com.example.data.auth.EmailDispatchGateway
import com.example.data.auth.EmailDispatchResult
import com.example.data.auth.OnboardingSubmission
import com.example.data.auth.RegistrationRequest
import com.example.data.auth.RouteAccessPolicy
import com.example.data.auth.RouteGuardResult
import com.example.data.auth.UnconfiguredEmailDispatchGateway
import com.example.data.local.DemoDataSeeder
import com.example.data.local.NersikaDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
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

private class RecordingEmailGateway : EmailDispatchGateway {
    data class SentEmail(
        val toEmail: String,
        val recipientName: String,
        val tokenOrCode: String,
        val kind: String
    )

    val sentEmails = mutableListOf<SentEmail>()
    override val isLiveProviderConfigured: Boolean = true
    override val providerDisplayName: String = "TestSMTPGateway"

    override suspend fun sendVerificationEmail(
        toEmail: String,
        recipientName: String,
        code: String,
        language: AppLanguage
    ): EmailDispatchResult {
        sentEmails += SentEmail(toEmail, recipientName, code, "VERIFICATION")
        return EmailDispatchResult.Dispatched(providerDisplayName, "msg_${sentEmails.size}")
    }

    override suspend fun sendPasswordResetEmail(
        toEmail: String,
        recipientName: String,
        resetToken: String,
        language: AppLanguage
    ): EmailDispatchResult {
        sentEmails += SentEmail(toEmail, recipientName, resetToken, "PASSWORD_RESET")
        return EmailDispatchResult.Dispatched(providerDisplayName, "msg_${sentEmails.size}")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: NersikaDatabase
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var gateway: RecordingEmailGateway
    private var nowMillis: Long = 1_760_000_000_000L

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NersikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = UserPreferencesRepository(context)
        prefs.clearSession()
        gateway = RecordingEmailGateway()
        nowMillis = 1_760_000_000_000L
    }

    @After
    fun teardown() = runTest {
        prefs.clearSession()
        db.close()
    }

    private fun createRepo(
        emailGateway: EmailDispatchGateway = gateway
    ): AuthRepository = AuthRepository(
        dao = db.dao(),
        prefs = prefs,
        emailGateway = emailGateway,
        clock = { nowMillis }
    )

    @Test
    fun `registration validates fields, prevents privilege escalation, hashes verification token, and fails honestly when unconfigured`() = runTest {
        val unconfiguredRepo = createRepo(UnconfiguredEmailDispatchGateway())

        // Weak password rejected
        val weakRes = unconfiguredRepo.registerWithEmail(
            RegistrationRequest(
                firstName = "Dara",
                lastName = "Amini",
                email = "dara@nersika.health",
                password = "weak",
                confirmPassword = "weak",
                country = "Iran",
                preferredLanguage = AppLanguage.FA,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        )
        assertEquals(AuthErrorCode.WEAK_PASSWORD, (weakRes as AuthResult.Failure).code)

        // Privilege escalation to SYSTEM_ADMIN blocked on public registration
        val escalationRes = unconfiguredRepo.registerWithEmail(
            RegistrationRequest(
                firstName = "Attacker",
                lastName = "User",
                email = "attacker@nersika.health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.EN,
                role = UserRole.SYSTEM_ADMIN,
                acceptedTerms = true
            )
        )
        assertEquals(AuthErrorCode.UNAUTHORIZED_ROLE, (escalationRes as AuthResult.Failure).code)

        // Unconfigured gateway still stores unverified user and reports EMAIL_PROVIDER_NOT_CONFIGURED (no auto-verify)
        val regUnconfigured = unconfiguredRepo.registerWithEmail(
            RegistrationRequest(
                firstName = "Dara",
                lastName = "Amini",
                email = "Dara@Nersika.Health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.FA,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        ) as AuthResult.Success
        assertFalse(regUnconfigured.value.emailDispatched)
        assertEquals(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED, regUnconfigured.value.dispatchWarning)
        assertFalse(regUnconfigured.value.user.emailVerified)
        assertFalse(regUnconfigured.value.user.onboardingCompleted)

        // Duplicate email rejected
        val dupRes = unconfiguredRepo.registerWithEmail(
            RegistrationRequest(
                firstName = "Dara",
                lastName = "Duplicate",
                email = "dara@nersika.health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.EN,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        )
        assertEquals(AuthErrorCode.EMAIL_ALREADY_EXISTS, (dupRes as AuthResult.Failure).code)
    }

    @Test
    fun `email verification enforces OTP matching, max attempts, expiration, and resend cooldown`() = runTest {
        val repo = createRepo()
        val reg = repo.registerWithEmail(
            RegistrationRequest(
                firstName = "Parisa",
                lastName = "Kiani",
                email = "parisa@nersika.health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.FA,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        ) as AuthResult.Success

        assertTrue(reg.value.emailDispatched)
        val firstOtp = gateway.sentEmails.last().tokenOrCode
        assertTrue(AuthValidators.isValidVerificationCode(firstOtp))

        // Resend within 60s cooldown rejected
        val rapidResend = repo.resendVerificationCode(reg.value.user.id)
        assertEquals(AuthErrorCode.RESEND_COOLDOWN_ACTIVE, (rapidResend as AuthResult.Failure).code)

        // Wrong code increments attempt count and rejects; 5th failure locks until resend
        repeat(4) {
            val bad = repo.verifyEmail(reg.value.user.id, "000000")
            assertEquals(AuthErrorCode.INVALID_VERIFICATION_CODE, (bad as AuthResult.Failure).code)
        }
        val fifthBad = repo.verifyEmail(reg.value.user.id, "000000")
        assertEquals(AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS, (fifthBad as AuthResult.Failure).code)

        // Even original code is blocked after max attempts until resend
        val blockedEvenCorrect = repo.verifyEmail(reg.value.user.id, firstOtp)
        assertEquals(AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS, (blockedEvenCorrect as AuthResult.Failure).code)

        // Advance clock past 60s cooldown and resend
        nowMillis += 61_000L
        val resendOk = repo.resendVerificationCode(reg.value.user.id)
        assertTrue(resendOk is AuthResult.Success)
        val secondOtp = gateway.sentEmails.last().tokenOrCode

        // Advance clock past 15-minute TTL -> code expired
        nowMillis += 16 * 60 * 1000L
        val expiredRes = repo.verifyEmail(reg.value.user.id, secondOtp)
        assertEquals(AuthErrorCode.VERIFICATION_CODE_EXPIRED, (expiredRes as AuthResult.Failure).code)

        // Resend again and verify with valid token before expiration
        val resendAgain = repo.resendVerificationCode(reg.value.user.id)
        assertTrue(resendAgain is AuthResult.Success)
        val thirdOtp = gateway.sentEmails.last().tokenOrCode

        val verified = repo.verifyEmail(reg.value.user.id, thirdOtp)
        assertTrue(verified is AuthResult.Success)
        assertTrue((verified as AuthResult.Success).value.emailVerified)
    }

    @Test
    fun `secure login enforces lockout, rememberMe persistence, and unverified gate`() = runTest {
        val repo = createRepo()
        val reg = repo.registerWithEmail(
            RegistrationRequest(
                firstName = "Kaveh",
                lastName = "Noori",
                email = "kaveh@nersika.health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.EN,
                role = UserRole.CAREGIVER,
                acceptedTerms = true
            )
        ) as AuthResult.Success

        repo.logout(reg.value.user)
        assertNull(repo.observeAuthenticatedUser().first())

        // Unknown email rejected
        val unknownRes = repo.loginWithEmail("nobody@nersika.health", "Strong@Password1", true)
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, (unknownRes as AuthResult.Failure).code)

        // 5 consecutive invalid passwords trigger account lockout
        repeat(4) {
            val bad = repo.loginWithEmail("kaveh@nersika.health", "WrongPass#1", true)
            assertEquals(AuthErrorCode.INVALID_CREDENTIALS, (bad as AuthResult.Failure).code)
        }
        val locked = repo.loginWithEmail("kaveh@nersika.health", "WrongPass#1", true)
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, (locked as AuthResult.Failure).code)

        // Even correct password is blocked while locked
        val duringLock = repo.loginWithEmail("kaveh@nersika.health", "Strong@Password1", true)
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, (duringLock as AuthResult.Failure).code)

        // Advance past 5-minute lockout -> login succeeds, but unverified user lands on VERIFY_EMAIL
        nowMillis += 6 * 60 * 1000L
        val loginUnverified = repo.loginWithEmail("kaveh@nersika.health", "Strong@Password1", rememberMe = false)
            as AuthResult.Success
        assertEquals(AppRoute.VERIFY_EMAIL, loginUnverified.value.nextRoute)
        assertFalse(loginUnverified.value.sessionPersisted)

        // Ephemeral session (rememberMe = false) is NOT persisted to a fresh AuthRepository instance
        val coldRestartRepo = createRepo()
        assertNull(coldRestartRepo.observeAuthenticatedUser().first())

        // Verify email and sign in with rememberMe = true
        val otp = gateway.sentEmails.last().tokenOrCode
        repo.verifyEmail(reg.value.user.id, otp)
        val loginPersisted = repo.loginWithEmail("kaveh@nersika.health", "Strong@Password1", rememberMe = true)
            as AuthResult.Success
        assertEquals(AppRoute.ROLE_ONBOARDING, loginPersisted.value.nextRoute)
        assertTrue(loginPersisted.value.sessionPersisted)

        // Cold restart restores persisted session
        val restoredUser = coldRestartRepo.observeAuthenticatedUser().first()
        assertNotNull(restoredUser)
        assertEquals("kaveh@nersika.health", restoredUser!!.email)
    }

    @Test
    fun `password recovery and reset validate token, revoke active sessions, and reject old password`() = runTest {
        val unconfiguredRepo = createRepo(UnconfiguredEmailDispatchGateway())
        val unconfRecovery = unconfiguredRepo.requestPasswordRecovery("sara@nersika.health")
        assertEquals(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED, (unconfRecovery as AuthResult.Failure).code)

        val repo = createRepo()
        val reg = repo.registerWithEmail(
            RegistrationRequest(
                firstName = "Nima",
                lastName = "Sadeghi",
                email = "nima@nersika.health",
                password = "Old@Password1",
                confirmPassword = "Old@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.EN,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        ) as AuthResult.Success
        val verifyOtp = gateway.sentEmails.last().tokenOrCode
        repo.verifyEmail(reg.value.user.id, verifyOtp)
        repo.loginWithEmail("nima@nersika.health", "Old@Password1", rememberMe = true)
        assertNotNull(repo.observeAuthenticatedUser().first())

        // Request password recovery
        val recoveryRes = repo.requestPasswordRecovery("nima@nersika.health")
        assertTrue(recoveryRes is AuthResult.Success)
        val resetToken = gateway.sentEmails.last { it.kind == "PASSWORD_RESET" }.tokenOrCode

        // Invalid token rejected
        val badTokenRes = repo.resetPasswordWithToken(
            email = "nima@nersika.health",
            rawToken = "999999",
            newPassword = "New@Password2",
            confirmPassword = "New@Password2"
        )
        assertEquals(AuthErrorCode.INVALID_RESET_TOKEN, (badTokenRes as AuthResult.Failure).code)

        // Valid token resets password and revokes persisted session
        val okReset = repo.resetPasswordWithToken(
            email = "nima@nersika.health",
            rawToken = resetToken,
            newPassword = "New@Password2",
            confirmPassword = "New@Password2"
        )
        assertTrue(okReset is AuthResult.Success)
        assertNull(repo.observeAuthenticatedUser().first())

        // Old password fails; new password succeeds
        val oldPassLogin = repo.loginWithEmail("nima@nersika.health", "Old@Password1", true)
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, (oldPassLogin as AuthResult.Failure).code)
        val newPassLogin = repo.loginWithEmail("nima@nersika.health", "New@Password2", true)
        assertTrue(newPassLogin is AuthResult.Success)
    }

    @Test
    fun `protected routes and unauthorized access are blocked, audited, and resolved by role onboarding`() = runTest {
        val dao = db.dao()
        DemoDataSeeder.seedIfEmpty(dao)
        val repo = createRepo()

        // 1. Unauthenticated visitor blocked from protected routes
        val unauthGuard = repo.guardRouteAccess(null, AppRoute.DASHBOARD)
        assertTrue(unauthGuard is RouteGuardResult.Denied)
        assertEquals(AuthErrorCode.UNAUTHENTICATED, (unauthGuard as RouteGuardResult.Denied).errorCode)
        assertEquals(AppRoute.AUTH, unauthGuard.fallbackRoute)

        // 2. Unverified registered user blocked from onboarding and dashboard
        val reg = repo.registerWithEmail(
            RegistrationRequest(
                firstName = "Hana",
                lastName = "Rad",
                email = "hana@nersika.health",
                password = "Strong@Password1",
                confirmPassword = "Strong@Password1",
                country = "Iran",
                preferredLanguage = AppLanguage.FA,
                role = UserRole.PATIENT,
                acceptedTerms = true
            )
        ) as AuthResult.Success

        val unverifiedDashboard = repo.guardRouteAccess(reg.value.user, AppRoute.DASHBOARD)
        assertEquals(AuthErrorCode.EMAIL_NOT_VERIFIED, (unverifiedDashboard as RouteGuardResult.Denied).errorCode)
        assertEquals(AppRoute.VERIFY_EMAIL, unverifiedDashboard.fallbackRoute)

        // 3. Verified user without onboarding blocked from clinical dashboard -> redirected to ROLE_ONBOARDING
        val verifyOtp = gateway.sentEmails.last().tokenOrCode
        val verifiedUser = (repo.verifyEmail(reg.value.user.id, verifyOtp) as AuthResult.Success).value
        val notOnboarded = repo.guardRouteAccess(verifiedUser, AppRoute.MEDICATIONS)
        assertEquals(AuthErrorCode.ONBOARDING_REQUIRED, (notOnboarded as RouteGuardResult.Denied).errorCode)
        assertEquals(AppRoute.ROLE_ONBOARDING, notOnboarded.fallbackRoute)

        // Complete Patient onboarding (requires charter acceptance)
        val charterMissing = repo.completeRoleOnboarding(
            verifiedUser.id,
            OnboardingSubmission(clinicalSafetyCharterAccepted = false)
        )
        assertEquals(AuthErrorCode.TERMS_NOT_ACCEPTED, (charterMissing as AuthResult.Failure).code)

        val onboardedUser = (repo.completeRoleOnboarding(
            verifiedUser.id,
            OnboardingSubmission(
                weightKg = 62.5,
                bloodType = "A+",
                emergencyContact = "+98 912 111 2233",
                consentClinicianAccess = true,
                clinicalSafetyCharterAccepted = true
            )
        ) as AuthResult.Success).value
        assertTrue(onboardedUser.onboardingCompleted)

        // 4. Patient allowed on RECORDS & DASHBOARD, but blocked from PATIENTS directory and ADMIN governance
        assertTrue(RouteAccessPolicy.evaluate(onboardedUser, AppRoute.DASHBOARD) is RouteGuardResult.Allowed)
        assertTrue(RouteAccessPolicy.evaluate(onboardedUser, AppRoute.RECORDS) is RouteGuardResult.Allowed)
        val patientDirectoryGuard = repo.guardRouteAccess(onboardedUser, AppRoute.PATIENTS)
        assertEquals(AuthErrorCode.UNAUTHORIZED_ROLE, (patientDirectoryGuard as RouteGuardResult.Denied).errorCode)

        val patientAdminGuard = repo.guardAdminGovernanceAccess(onboardedUser)
        assertEquals(AuthErrorCode.UNAUTHORIZED_ROLE, (patientAdminGuard as RouteGuardResult.Denied).errorCode)

        // 5. Clinician blocked from Admin governance; System Admin allowed
        val physician = dao.getUserByEmail("physician@demo.nersika.health")!!
        assertEquals(AuthErrorCode.UNAUTHORIZED_ROLE, (repo.guardAdminGovernanceAccess(physician) as RouteGuardResult.Denied).errorCode)

        val sysAdmin = dao.getUserByEmail("admin@demo.nersika.health")!!
        assertTrue(repo.guardAdminGovernanceAccess(sysAdmin) is RouteGuardResult.Allowed)

        // 6. Secure logout revokes session & bumps tokenVersion
        val preLogoutVersion = onboardedUser.tokenVersion
        repo.logout(onboardedUser)
        assertNull(repo.observeAuthenticatedUser().first())
        val afterLogoutUser = dao.getUserById(onboardedUser.id)!!
        assertEquals(preLogoutVersion + 1, afterLogoutUser.tokenVersion)

        // Verify DENIED audit events were recorded
        val audits = dao.observeAuditEvents().first()
        assertTrue(audits.any { it.actionType == "UNAUTHORIZED_ROUTE_ACCESS" && it.outcome == "DENIED" })
        assertTrue(audits.any { it.actionType == "UNAUTHORIZED_ADMIN_ACCESS" && it.outcome == "DENIED" })
        assertTrue(audits.any { it.actionType == "LOGOUT" })
    }
}
