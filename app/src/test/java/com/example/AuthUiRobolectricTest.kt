package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthResult
import com.example.data.local.UserEntity
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.EnStrings
import com.example.i18n.FaStrings
import com.example.ui.screens.AuthPortalScreen
import com.example.ui.screens.EmailVerificationScreen
import com.example.ui.screens.RoleOnboardingScreen
import com.example.ui.theme.NersikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleUser(
        role: UserRole = UserRole.PATIENT,
        emailVerified: Boolean = false,
        onboardingCompleted: Boolean = false
    ) = UserEntity(
        id = "usr_ui_1",
        email = "sara.ui@nersika.health",
        passwordHash = "hash",
        passwordSalt = "salt",
        firstName = "Sara",
        lastName = "Bahrami",
        role = role.name,
        country = "Iran",
        preferredLanguage = "en",
        emailVerified = emailVerified,
        verificationTokenHash = "tokHash",
        verificationExpiresAtMillis = System.currentTimeMillis() + 60_000L,
        verificationSentAtMillis = System.currentTimeMillis(),
        verificationAttempts = 0,
        resetTokenHash = null,
        resetExpiresAtMillis = 0L,
        resetAttempts = 0,
        failedLoginAttempts = 0,
        lockedUntilMillis = 0L,
        tokenVersion = 1,
        onboardingCompleted = onboardingCompleted,
        mfaEnabled = false,
        organizationName = "Personal",
        isVerifiedClinician = false,
        isDemo = false,
        linkedPatientId = "pat_001",
        createdAtMillis = 1000L
    )

    @Test
    fun `auth portal switches between English LTR and Persian RTL and validates login inputs`() {
        var currentLang by mutableStateOf(AppLanguage.EN)
        var observedLayoutDirection: LayoutDirection? = null
        var loginInvoked = false

        composeRule.setContent {
            NersikaTheme(language = currentLang) {
                observedLayoutDirection = LocalLayoutDirection.current
                AuthPortalScreen(
                    isLiveEmailConfigured = false,
                    authBannerMessage = null,
                    onDismissBanner = {},
                    onToggleLanguage = {
                        currentLang = if (currentLang == AppLanguage.EN) AppLanguage.FA else AppLanguage.EN
                    },
                    onSignIn = { _, _, _, _ -> loginInvoked = true },
                    onRegister = { _, _ -> },
                    onRequestPasswordRecovery = { _, _ -> },
                    onResetPasswordWithToken = { _, _, _, _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(LayoutDirection.Ltr, observedLayoutDirection)
        composeRule.onNodeWithTag("email_provider_unconfigured_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit_button").assertTextContains(EnStrings.signIn)

        // Invalid email triggers localized validation without calling onSignIn
        composeRule.onNodeWithTag("login_email_input").performTextInput("invalid-email")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_error_text").assertTextContains(EnStrings.errInvalidEmail)
        assertFalse(loginInvoked)

        // Toggle to Persian RTL
        composeRule.onNodeWithTag("language_switcher_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(AppLanguage.FA, currentLang)
        assertEquals(LayoutDirection.Rtl, observedLayoutDirection)
        composeRule.onNodeWithText(FaStrings.welcomeTitle).assertIsDisplayed()
    }

    @Test
    fun `email verification screen never leaks verification code and blocks invalid codes`() {
        var verifySubmittedCode: String? = null
        var logoutClicked = false

        composeRule.setContent {
            NersikaTheme(language = AppLanguage.EN) {
                EmailVerificationScreen(
                    user = sampleUser(emailVerified = false),
                    isLiveEmailConfigured = false,
                    onToggleLanguage = {},
                    onVerifyCode = { code, cb ->
                        verifySubmittedCode = code
                        cb(AuthResult.Failure(AuthErrorCode.INVALID_VERIFICATION_CODE))
                    },
                    onResendCode = { cb ->
                        cb(AuthResult.Failure(AuthErrorCode.EMAIL_PROVIDER_NOT_CONFIGURED))
                    },
                    onSignOut = { logoutClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        // Must show honest provider warning and NOT leak any verification code
        composeRule.onNodeWithTag("email_provider_unconfigured_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("verification_code_input").performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag("verify_email_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("123456", verifySubmittedCode)
        composeRule.onNodeWithTag("verification_error_text")
            .assertTextContains(EnStrings.errInvalidVerificationCode)

        // Resend surfaces honest unconfigured provider error
        composeRule.onNodeWithTag("resend_code_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("verification_error_text")
            .assertTextContains(EnStrings.errEmailProviderUnconfigured)

        // Logout button works
        composeRule.onNodeWithTag("verify_logout_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(logoutClicked)
    }

    @Test
    fun `role onboarding screen requires charter consent before completing`() {
        var completed = false

        composeRule.setContent {
            NersikaTheme(language = AppLanguage.FA) {
                RoleOnboardingScreen(
                    user = sampleUser(role = UserRole.PATIENT, emailVerified = true, onboardingCompleted = false),
                    onToggleLanguage = {},
                    onCompleteOnboarding = { _, cb ->
                        completed = true
                        cb(AuthResult.Success(sampleUser(emailVerified = true, onboardingCompleted = true)))
                    },
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Attempt submit without charter -> shows Persian error
        composeRule.onNodeWithTag("complete_onboarding_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(completed)
        composeRule.onNodeWithTag("onboarding_error_text")
            .assertTextContains(FaStrings.errAcceptTerms)

        // Accept charter and submit
        composeRule.onNodeWithTag("onboarding_charter_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("complete_onboarding_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(completed)
    }
}
