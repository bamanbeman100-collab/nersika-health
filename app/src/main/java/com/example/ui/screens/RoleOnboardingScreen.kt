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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthResult
import com.example.data.auth.OnboardingSubmission
import com.example.data.local.UserEntity
import com.example.domain.model.PopulationGroup
import com.example.domain.model.UserRole
import com.example.ui.components.LanguageSwitcherChip
import com.example.ui.components.NersikaBrandMark
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleOnboardingScreen(
    user: UserEntity,
    onToggleLanguage: () -> Unit,
    onCompleteOnboarding: (OnboardingSubmission, (AuthResult<UserEntity>) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val role = UserRole.fromName(user.role)

    var dobIso by rememberSaveable { mutableStateOf("1990-01-01") }
    var sex by rememberSaveable { mutableStateOf("Female") }
    var populationGroup by rememberSaveable { mutableStateOf(PopulationGroup.ADULT) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var bloodType by rememberSaveable { mutableStateOf("O+") }
    var emergencyContact by rememberSaveable { mutableStateOf("") }

    var consentClinician by rememberSaveable { mutableStateOf(true) }
    var consentCaregiver by rememberSaveable { mutableStateOf(false) }
    var consentAi by rememberSaveable { mutableStateOf(false) }
    var enableMfa by rememberSaveable { mutableStateOf(role.isClinician || role.isAdmin) }
    var charterAccepted by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NersikaBrandMark(modifier = Modifier.weight(1f))
                    LanguageSwitcherChip(onToggle = onToggleLanguage)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (role.isClinician || role.isAdmin) Icons.Filled.Security else Icons.Filled.HealthAndSafety,
                                contentDescription = null,
                                tint = MedicalTeal
                            )
                            Column {
                                Text(s.onboardingTitle, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "${user.firstName} ${user.lastName} • ${role.displayName(s)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MedicalTeal
                                )
                            }
                        }

                        Text(
                            text = s.onboardingSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        when {
                            role == UserRole.PATIENT -> {
                                Surface(color = LightTeal, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        text = s.onboardingPatientStepTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NavyPrimary,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = dobIso,
                                        onValueChange = { dobIso = it },
                                        label = { Text(s.onboardingDobLabel) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = weightText,
                                        onValueChange = { weightText = it },
                                        label = { Text(s.onboardingWeightLabel) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f).testTag("onboarding_weight_input")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = sex,
                                        onValueChange = { sex = it },
                                        label = { Text(s.onboardingSexLabel) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = bloodType,
                                        onValueChange = { bloodType = it },
                                        label = { Text(s.onboardingBloodTypeLabel) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text(s.populationLabel, style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PopulationGroup.entries.forEach { pg ->
                                        FilterChip(
                                            selected = populationGroup == pg,
                                            onClick = { populationGroup = pg },
                                            label = {
                                                Text(if (lang == com.example.i18n.AppLanguage.FA) pg.labelFa else pg.labelEn)
                                            }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = emergencyContact,
                                    onValueChange = { emergencyContact = it },
                                    label = { Text(s.onboardingEmergencyLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(s.consentManagementTitle, style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.consentClinicianLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = consentClinician, onCheckedChange = { consentClinician = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.consentCaregiverLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = consentCaregiver, onCheckedChange = { consentCaregiver = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.consentAiLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = consentAi, onCheckedChange = { consentAi = it })
                                }
                            }

                            role == UserRole.CAREGIVER -> {
                                Surface(color = InfoContainer, shape = RoundedCornerShape(10.dp)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = s.onboardingCaregiverStepTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = s.onboardingCaregiverNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }
                            }

                            else -> {
                                Surface(color = InfoContainer, shape = RoundedCornerShape(10.dp)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Filled.VerifiedUser, null, tint = MedicalTeal)
                                            Text(
                                                text = s.onboardingClinicianStepTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = NavyPrimary
                                            )
                                        }
                                        Text(
                                            text = s.onboardingClinicianNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.mfaToggleLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = enableMfa, onCheckedChange = { enableMfa = it })
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { charterAccepted = !charterAccepted; errorMessage = null }
                                .testTag("onboarding_charter_row")
                        ) {
                            Checkbox(
                                checked = charterAccepted,
                                onCheckedChange = { charterAccepted = it; errorMessage = null },
                                modifier = Modifier.testTag("onboarding_charter_checkbox")
                            )
                            Text(
                                text = s.onboardingCharterCheckbox,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = CriticalRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("onboarding_error_text")
                            )
                        }

                        Button(
                            onClick = {
                                if (!charterAccepted) {
                                    errorMessage = AuthErrorCode.TERMS_NOT_ACCEPTED.localizedMessage(s)
                                } else {
                                    val weight = weightText.toDoubleOrNull() ?: 0.0
                                    onCompleteOnboarding(
                                        OnboardingSubmission(
                                            dateOfBirthIso = dobIso,
                                            ageYears = 34,
                                            sex = sex,
                                            populationGroupName = populationGroup.name,
                                            bloodType = bloodType,
                                            weightKg = weight,
                                            emergencyContact = emergencyContact,
                                            consentClinicianAccess = consentClinician,
                                            consentCaregiverAccess = consentCaregiver,
                                            consentAiSummarization = consentAi,
                                            enableMfaRecommendation = enableMfa,
                                            clinicalSafetyCharterAccepted = charterAccepted
                                        )
                                    ) { result ->
                                        if (result is AuthResult.Failure) {
                                            errorMessage = result.code.localizedMessage(s, result.retryAfterSeconds)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("complete_onboarding_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal)
                        ) {
                            Text(s.completeOnboardingButton)
                        }

                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.align(Alignment.End).testTag("onboarding_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(s.logout)
                        }
                    }
                }
            }
        }
    }
}
