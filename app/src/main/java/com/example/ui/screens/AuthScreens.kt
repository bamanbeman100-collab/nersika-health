package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthValidators
import com.example.data.auth.LoginOutcome
import com.example.data.auth.RecoveryRequestOutcome
import com.example.data.auth.RegistrationOutcome
import com.example.data.auth.RegistrationRequest
import com.example.data.local.UserEntity
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.LanguageSwitcherChip
import com.example.ui.components.NersikaBrandMark
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

private enum class AuthTab { LOGIN, REGISTER, RECOVER }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthPortalScreen(
    isLiveEmailConfigured: Boolean,
    authBannerMessage: String?,
    onDismissBanner: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSignIn: (String, String, Boolean, (AuthResult<LoginOutcome>) -> Unit) -> Unit,
    onRegister: (RegistrationRequest, (AuthResult<RegistrationOutcome>) -> Unit) -> Unit,
    onRequestPasswordRecovery: (String, (AuthResult<RecoveryRequestOutcome>) -> Unit) -> Unit,
    onResetPasswordWithToken: (String, String, String, String, (AuthResult<Unit>) -> Unit) -> Unit
) {
    val s = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current
    var selectedTab by rememberSaveable { mutableStateOf(AuthTab.LOGIN) }

    // Login state
    var loginEmail by rememberSaveable { mutableStateOf("") }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var loginPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(true) }
    var loginError by remember { mutableStateOf<String?>(null) }

    // Register state
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var regPassword by rememberSaveable { mutableStateOf("") }
    var regConfirm by rememberSaveable { mutableStateOf("") }
    var regCountry by rememberSaveable { mutableStateOf("Iran") }
    var regRole by rememberSaveable { mutableStateOf(UserRole.PATIENT) }
    var regLang by rememberSaveable { mutableStateOf(currentLang) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var regPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var regError by remember { mutableStateOf<String?>(null) }

    // Two-step Password Recovery & Reset state
    var recoveryEmail by rememberSaveable { mutableStateOf("") }
    var recoveryToken by rememberSaveable { mutableStateOf("") }
    var resetNewPassword by rememberSaveable { mutableStateOf("") }
    var resetConfirmPassword by rememberSaveable { mutableStateOf("") }
    var recoveryStep1Feedback by remember { mutableStateOf<String?>(null) }
    var recoveryStep1Success by remember { mutableStateOf(false) }
    var resetStep2Feedback by remember { mutableStateOf<String?>(null) }
    var resetStep2Success by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NersikaBrandMark(modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    LanguageSwitcherChip(onToggle = onToggleLanguage)
                }

                DemoSafetyBanner()

                if (!isLiveEmailConfigured) {
                    EmailProviderStatusBanner()
                }

                if (!authBannerMessage.isNullOrBlank()) {
                    Surface(
                        color = InfoContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_banner_notice")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = authBannerMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = NavyPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissBanner) {
                                Text(s.close)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = s.welcomeTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.productDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedTab == AuthTab.LOGIN,
                                onClick = { selectedTab = AuthTab.LOGIN; loginError = null },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                modifier = Modifier.testTag("auth_tab_login")
                            ) { Text(s.signIn, maxLines = 1) }

                            SegmentedButton(
                                selected = selectedTab == AuthTab.REGISTER,
                                onClick = { selectedTab = AuthTab.REGISTER; regError = null },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                modifier = Modifier.testTag("auth_tab_register")
                            ) { Text(s.register, maxLines = 1) }

                            SegmentedButton(
                                selected = selectedTab == AuthTab.RECOVER,
                                onClick = {
                                    selectedTab = AuthTab.RECOVER
                                    if (recoveryEmail.isBlank() && loginEmail.isNotBlank()) {
                                        recoveryEmail = loginEmail
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                modifier = Modifier.testTag("auth_tab_recover")
                            ) { Text(s.recoverTab, maxLines = 1) }
                        }

                        when (selectedTab) {
                            AuthTab.LOGIN -> {
                                OutlinedTextField(
                                    value = loginEmail,
                                    onValueChange = { loginEmail = it; loginError = null },
                                    label = { Text(s.emailLabel) },
                                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_email_input")
                                )
                                OutlinedTextField(
                                    value = loginPassword,
                                    onValueChange = { loginPassword = it; loginError = null },
                                    label = { Text(s.passwordLabel) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                            Icon(
                                                imageVector = if (loginPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (loginPasswordVisible) s.hidePassword else s.showPassword
                                            )
                                        }
                                    },
                                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_password_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                                    ) {
                                        Checkbox(
                                            checked = rememberMe,
                                            onCheckedChange = { rememberMe = it },
                                            modifier = Modifier.testTag("remember_me_checkbox")
                                        )
                                        Text(s.rememberMe, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(
                                        onClick = {
                                            recoveryEmail = loginEmail
                                            selectedTab = AuthTab.RECOVER
                                        },
                                        modifier = Modifier.testTag("forgot_password_button")
                                    ) {
                                        Text(s.forgotPassword)
                                    }
                                }
                                if (loginError != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.ErrorOutline, null, tint = CriticalRed, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = loginError!!,
                                            color = CriticalRed,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.testTag("login_error_text")
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        when {
                                            !AuthValidators.isValidEmail(loginEmail) -> {
                                                loginError = s.errInvalidEmail
                                            }
                                            loginPassword.isBlank() -> {
                                                loginError = s.errRequiredField
                                            }
                                            else -> {
                                                onSignIn(loginEmail, loginPassword, rememberMe) { result ->
                                                    if (result is AuthResult.Failure) {
                                                        loginError = result.code.localizedMessage(s, result.retryAfterSeconds)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("login_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                                ) {
                                    Text(s.signIn)
                                }
                            }

                            AuthTab.REGISTER -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = firstName,
                                        onValueChange = { firstName = it; regError = null },
                                        label = { Text("${s.firstNameLabel} *") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("register_first_name_input")
                                    )
                                    OutlinedTextField(
                                        value = lastName,
                                        onValueChange = { lastName = it; regError = null },
                                        label = { Text("${s.lastNameLabel} *") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("register_last_name_input")
                                    )
                                }
                                OutlinedTextField(
                                    value = regEmail,
                                    onValueChange = { regEmail = it; regError = null },
                                    label = { Text("${s.emailLabel} *") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("register_email_input")
                                )
                                OutlinedTextField(
                                    value = regPassword,
                                    onValueChange = { regPassword = it; regError = null },
                                    label = { Text("${s.passwordLabel} *") },
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                            Icon(
                                                imageVector = if (regPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (regPasswordVisible) s.hidePassword else s.showPassword
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("register_password_input")
                                )
                                PasswordStrengthChecklist(password = regPassword)

                                OutlinedTextField(
                                    value = regConfirm,
                                    onValueChange = { regConfirm = it; regError = null },
                                    label = { Text("${s.confirmPasswordLabel} *") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("register_confirm_input")
                                )
                                OutlinedTextField(
                                    value = regCountry,
                                    onValueChange = { regCountry = it },
                                    label = { Text(s.countryLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(s.accountTypeLabel, style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = regRole == UserRole.PATIENT,
                                        onClick = { regRole = UserRole.PATIENT },
                                        label = { Text(s.rolePatient) },
                                        modifier = Modifier.testTag("register_role_patient")
                                    )
                                    FilterChip(
                                        selected = regRole == UserRole.CAREGIVER,
                                        onClick = { regRole = UserRole.CAREGIVER },
                                        label = { Text(s.roleCaregiver) },
                                        modifier = Modifier.testTag("register_role_caregiver")
                                    )
                                }
                                Surface(color = InfoContainer, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = s.publicRegistrationRoleNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NavyPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Text(s.preferredLanguageLabel, style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = regLang == AppLanguage.FA,
                                        onClick = { regLang = AppLanguage.FA },
                                        label = { Text("فارسی (RTL)") }
                                    )
                                    FilterChip(
                                        selected = regLang == AppLanguage.EN,
                                        onClick = { regLang = AppLanguage.EN },
                                        label = { Text("English (LTR)") }
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { acceptedTerms = !acceptedTerms }
                                ) {
                                    Checkbox(
                                        checked = acceptedTerms,
                                        onCheckedChange = { acceptedTerms = it },
                                        modifier = Modifier.testTag("register_terms_checkbox")
                                    )
                                    Text(s.termsConsentLabel, style = MaterialTheme.typography.bodySmall)
                                }
                                if (regError != null) {
                                    Text(
                                        text = regError!!,
                                        color = CriticalRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("register_error_text")
                                    )
                                }
                                Button(
                                    onClick = {
                                        when {
                                            firstName.isBlank() || lastName.isBlank() -> regError = s.errRequiredField
                                            !AuthValidators.isValidEmail(regEmail) -> regError = s.errInvalidEmail
                                            !AuthValidators.isStrongPassword(regPassword) -> regError = s.errWeakPassword
                                            regPassword != regConfirm -> regError = s.errPasswordMismatch
                                            !acceptedTerms -> regError = s.errAcceptTerms
                                            else -> {
                                                onRegister(
                                                    RegistrationRequest(
                                                        firstName = firstName,
                                                        lastName = lastName,
                                                        email = regEmail,
                                                        password = regPassword,
                                                        confirmPassword = regConfirm,
                                                        country = regCountry,
                                                        preferredLanguage = regLang,
                                                        role = regRole,
                                                        acceptedTerms = acceptedTerms
                                                    )
                                                ) { result ->
                                                    if (result is AuthResult.Failure) {
                                                        regError = result.code.localizedMessage(s, result.retryAfterSeconds)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("register_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                                ) {
                                    Text(s.register)
                                }
                            }

                            AuthTab.RECOVER -> {
                                Text(s.resetPasswordTitle, style = MaterialTheme.typography.titleMedium)

                                Text(s.recoveryStep1Title, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                OutlinedTextField(
                                    value = recoveryEmail,
                                    onValueChange = {
                                        recoveryEmail = it
                                        recoveryStep1Feedback = null
                                        resetStep2Feedback = null
                                    },
                                    label = { Text(s.emailLabel) },
                                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("recovery_email_input")
                                )
                                Button(
                                    onClick = {
                                        if (!AuthValidators.isValidEmail(recoveryEmail)) {
                                            recoveryStep1Success = false
                                            recoveryStep1Feedback = s.errInvalidEmail
                                        } else {
                                            onRequestPasswordRecovery(recoveryEmail) { res ->
                                                when (res) {
                                                    is AuthResult.Success -> {
                                                        recoveryStep1Success = true
                                                        recoveryStep1Feedback = s.msgRecoveryRequested
                                                    }
                                                    is AuthResult.Failure -> {
                                                        recoveryStep1Success = false
                                                        recoveryStep1Feedback = res.code.localizedMessage(s, res.retryAfterSeconds)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("recovery_request_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                                ) {
                                    Text(s.sendRecoveryEmailButton)
                                }
                                if (recoveryStep1Feedback != null) {
                                    Text(
                                        text = recoveryStep1Feedback!!,
                                        color = if (recoveryStep1Success) SuccessGreen else CriticalRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("recovery_step1_feedback")
                                    )
                                }

                                HorizontalDivider()

                                Text(s.recoveryStep2Title, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                OutlinedTextField(
                                    value = recoveryToken,
                                    onValueChange = { recoveryToken = it; resetStep2Feedback = null },
                                    label = { Text(s.recoveryTokenLabel) },
                                    leadingIcon = { Icon(Icons.Filled.Key, null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("recovery_token_input")
                                )
                                OutlinedTextField(
                                    value = resetNewPassword,
                                    onValueChange = { resetNewPassword = it; resetStep2Feedback = null },
                                    label = { Text(s.newPasswordLabel) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("reset_new_password_input")
                                )
                                PasswordStrengthChecklist(password = resetNewPassword)
                                OutlinedTextField(
                                    value = resetConfirmPassword,
                                    onValueChange = { resetConfirmPassword = it; resetStep2Feedback = null },
                                    label = { Text(s.confirmPasswordLabel) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("reset_confirm_password_input")
                                )
                                if (resetStep2Feedback != null) {
                                    Text(
                                        text = resetStep2Feedback!!,
                                        color = if (resetStep2Success) SuccessGreen else CriticalRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("recovery_step2_feedback")
                                    )
                                }
                                Button(
                                    onClick = {
                                        when {
                                            !AuthValidators.isValidEmail(recoveryEmail) -> {
                                                resetStep2Success = false
                                                resetStep2Feedback = s.errInvalidEmail
                                            }
                                            recoveryToken.isBlank() -> {
                                                resetStep2Success = false
                                                resetStep2Feedback = s.errInvalidResetToken
                                            }
                                            !AuthValidators.isStrongPassword(resetNewPassword) -> {
                                                resetStep2Success = false
                                                resetStep2Feedback = s.errWeakPassword
                                            }
                                            resetNewPassword != resetConfirmPassword -> {
                                                resetStep2Success = false
                                                resetStep2Feedback = s.errPasswordMismatch
                                            }
                                            else -> {
                                                onResetPasswordWithToken(
                                                    recoveryEmail,
                                                    recoveryToken,
                                                    resetNewPassword,
                                                    resetConfirmPassword
                                                ) { res ->
                                                    when (res) {
                                                        is AuthResult.Success -> {
                                                            resetStep2Success = true
                                                            resetStep2Feedback = s.msgPasswordResetSuccess
                                                        }
                                                        is AuthResult.Failure -> {
                                                            resetStep2Success = false
                                                            resetStep2Feedback = res.code.localizedMessage(s, res.retryAfterSeconds)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                                ) {
                                    Text(s.resetPasswordButton)
                                }
                            }
                        }
                    }
                }

                // Read-only security notice card (no passwordless demo login bypasses!)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = s.seededAccountsReferenceTitle,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = s.seededAccountsReferenceBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailProviderStatusBanner() {
    val s = LocalAppStrings.current
    Surface(
        color = WarningContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("email_provider_unconfigured_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WarningAmber)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = s.emailProviderMissingTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = s.emailProviderMissingBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = NavyPrimary
                )
            }
        }
    }
}

@Composable
private fun PasswordStrengthChecklist(password: String) {
    if (password.isEmpty()) return
    val s = LocalAppStrings.current
    val report = remember(password) { AuthValidators.evaluatePassword(password) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { report.progressFraction },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = when {
                report.isStrong -> SuccessGreen
                report.progressFraction >= 0.6f -> WarningAmber
                else -> CriticalRed
            }
        )
        PasswordRuleRow(report.minLengthMet, s.passwordRuleLength)
        PasswordRuleRow(report.hasUppercase, s.passwordRuleUpper)
        PasswordRuleRow(report.hasLowercase, s.passwordRuleLower)
        PasswordRuleRow(report.hasDigit, s.passwordRuleDigit)
        PasswordRuleRow(report.hasSpecialChar, s.passwordRuleSymbol)
    }
}

@Composable
private fun PasswordRuleRow(met: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailVerificationScreen(
    user: UserEntity,
    isLiveEmailConfigured: Boolean,
    onToggleLanguage: () -> Unit,
    onVerifyCode: (String, (AuthResult<UserEntity>) -> Unit) -> Unit,
    onResendCode: ((AuthResult<Boolean>) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    val s = LocalAppStrings.current
    var codeInput by rememberSaveable { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = null,
                            tint = MedicalTeal,
                            modifier = Modifier.size(36.dp)
                        )
                        LanguageSwitcherChip(onToggle = onToggleLanguage)
                    }
                    Text(s.verifyEmailTitle, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${user.email} — ${s.verifyEmailSubtitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isLiveEmailConfigured) {
                        EmailProviderStatusBanner()
                    }
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it; errorMsg = null; infoMsg = null },
                        label = { Text(s.verificationCodeLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verification_code_input")
                    )
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = CriticalRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("verification_error_text")
                        )
                    }
                    if (infoMsg != null) {
                        Text(
                            text = infoMsg!!,
                            color = SuccessGreen,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("verification_info_text")
                        )
                    }
                    Button(
                        onClick = {
                            onVerifyCode(codeInput) { res ->
                                if (res is AuthResult.Failure) {
                                    errorMsg = res.code.localizedMessage(s, res.retryAfterSeconds)
                                    infoMsg = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_email_button")
                    ) {
                        Text(s.verifyAccountButton)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onResendCode { res ->
                                    when (res) {
                                        is AuthResult.Success -> {
                                            infoMsg = s.msgVerificationSent
                                            errorMsg = null
                                        }
                                        is AuthResult.Failure -> {
                                            errorMsg = res.code.localizedMessage(s, res.retryAfterSeconds)
                                            infoMsg = null
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("resend_code_button")
                        ) {
                            Text(s.resendCodeButton)
                        }
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("verify_logout_button")
                        ) {
                            Text(s.logout)
                        }
                    }
                }
            }
        }
    }
}
