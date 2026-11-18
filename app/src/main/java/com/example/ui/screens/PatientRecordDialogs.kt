package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.clinical.PatientProfileUpdateRequest
import com.example.data.local.AllergyEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.MedicalHistoryEntity
import com.example.data.local.PatientEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.PopulationGroup
import com.example.i18n.AppLanguage
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    patient: PatientEntity,
    onDismiss: () -> Unit,
    onSave: (PatientProfileUpdateRequest) -> Unit
) {
    val s = LocalAppStrings.current
    val isFa = LocalAppLanguage.current == AppLanguage.FA
    var dob by remember { mutableStateOf(patient.dateOfBirthIso) }
    var ageText by remember { mutableStateOf(patient.ageYears.toString()) }
    var sex by remember { mutableStateOf(patient.sex) }
    var popGroup by remember { mutableStateOf(PopulationGroup.fromName(patient.populationGroup)) }
    var bloodType by remember { mutableStateOf(patient.bloodType) }
    var phone by remember { mutableStateOf(patient.phone) }
    var emergency by remember { mutableStateOf(patient.emergencyContact) }
    var weightText by remember { mutableStateOf(if (patient.weightKg > 0) patient.weightKg.toString() else "") }
    var isPregnant by remember { mutableStateOf(patient.isPregnant) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.editProfileButton) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text(s.onboardingDobLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_dob_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = { Text(s.onboardingBloodTypeLabel) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sex,
                        onValueChange = { sex = it },
                        label = { Text(s.onboardingSexLabel) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(s.onboardingWeightLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("profile_weight_input")
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(s.phoneLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                )
                OutlinedTextField(
                    value = emergency,
                    onValueChange = { emergency = it },
                    label = { Text(s.onboardingEmergencyLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_emergency_input")
                )
                Text(s.populationLabel, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PopulationGroup.entries.forEach { pg ->
                        FilterChip(
                            selected = popGroup == pg,
                            onClick = { popGroup = pg },
                            label = { Text(if (isFa) pg.labelFa else pg.labelEn) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPregnant, onCheckedChange = { isPregnant = it })
                    Text(s.pregnancyStatusLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val age = ageText.toIntOrNull() ?: patient.ageYears
                    val weight = weightText.toDoubleOrNull() ?: patient.weightKg
                    if (dob.isNotBlank() && age > 0 && bloodType.isNotBlank()) {
                        onSave(
                            PatientProfileUpdateRequest(
                                dateOfBirthIso = dob,
                                ageYears = age,
                                sex = sex,
                                populationGroup = popGroup,
                                bloodType = bloodType,
                                phone = phone,
                                emergencyContact = emergency,
                                weightKg = weight.coerceAtLeast(0.0),
                                isPregnant = isPregnant
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_profile_btn")
            ) { Text(s.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionDialog(
    initial: ConditionEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, icd10: String, status: String, notes: String) -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(initial?.nameEn ?: "") }
    var icd by remember { mutableStateOf(initial?.icd10Code ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "Active") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) s.addCondition else "${s.editRecordButton} ${s.conditionsSection}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("${s.conditionsSection} *") },
                    modifier = Modifier.fillMaxWidth().testTag("condition_name_input")
                )
                OutlinedTextField(
                    value = icd,
                    onValueChange = { icd = it },
                    label = { Text("ICD-10") },
                    modifier = Modifier.fillMaxWidth().testTag("condition_icd_input")
                )
                Text(s.statusLabel, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Active", "Monitoring", "Resolved").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(s.notesLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("condition_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, icd, status, notes)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_condition_btn")
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalHistoryDialog(
    initial: MedicalHistoryEntity?,
    onDismiss: () -> Unit,
    onSave: (category: MedicalHistoryCategory, title: String, details: String) -> Unit
) {
    val s = LocalAppStrings.current
    val isFa = LocalAppLanguage.current == AppLanguage.FA
    var category by remember {
        mutableStateOf(
            if (initial != null) MedicalHistoryCategory.fromName(initial.category)
            else MedicalHistoryCategory.SURGICAL
        )
    }
    var title by remember { mutableStateOf(initial?.let { if (isFa) it.titleFa else it.titleEn } ?: "") }
    var details by remember { mutableStateOf(initial?.let { if (isFa) it.detailsFa else it.detailsEn } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) s.addMedicalHistory else "${s.editRecordButton} ${s.medicalHistorySection}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.categoryLabel, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MedicalHistoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(if (isFa) cat.labelFa else cat.labelEn) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("${s.medicalHistorySection} *") },
                    modifier = Modifier.fillMaxWidth().testTag("history_title_input")
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(s.notesLabel) },
                    modifier = Modifier.fillMaxWidth().testTag("history_details_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(category, title, details)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_history_btn")
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergyDialog(
    initial: AllergyEntity?,
    onDismiss: () -> Unit,
    onSave: (allergen: String, reaction: String, severity: AlertSeverity) -> Unit
) {
    val s = LocalAppStrings.current
    val isFa = LocalAppLanguage.current == AppLanguage.FA
    var allergen by remember { mutableStateOf(initial?.let { if (isFa) it.allergenFa else it.allergenEn } ?: "") }
    var reaction by remember { mutableStateOf(initial?.let { if (isFa) it.reactionFa else it.reactionEn } ?: "") }
    var sev by remember { mutableStateOf(initial?.let { AlertSeverity.fromName(it.severity) } ?: AlertSeverity.AMBER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) s.addAllergy else "${s.editRecordButton} ${s.allergiesSection}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = allergen,
                    onValueChange = { allergen = it },
                    label = { Text("${s.allergiesSection} *") },
                    modifier = Modifier.fillMaxWidth().testTag("allergy_name_input")
                )
                OutlinedTextField(
                    value = reaction,
                    onValueChange = { reaction = it },
                    label = { Text("Reaction *") },
                    modifier = Modifier.fillMaxWidth().testTag("allergy_reaction_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertSeverity.entries.forEach { option ->
                        FilterChip(
                            selected = sev == option,
                            onClick = { sev = option },
                            label = { Text(option.displayName(s)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (allergen.isNotBlank() && reaction.isNotBlank()) {
                        onSave(allergen, reaction, sev)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_allergy_btn")
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
