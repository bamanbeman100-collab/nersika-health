package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.local.MedicationEntity
import com.example.data.local.UserEntity
import com.example.domain.model.ClinicalAlert
import com.example.domain.model.ClinicalDecisionAction
import com.example.domain.model.MedicationStatus
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.PatientBundle
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RecordActionFooter
import com.example.ui.components.SeverityBadge
import com.example.ui.components.SourceAndVerificationRow
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationsAndSafetyScreen(
    user: UserEntity,
    bundle: PatientBundle?,
    onReportMedication: (String, String, String, String, MedicationStatus, String) -> Unit,
    onUpdateMedication: (String, String, String, MedicationStatus, String) -> Unit,
    onReconcileMedication: (MedicationEntity, MedicationStatus) -> Unit,
    onDeleteMedication: (String) -> Unit,
    onSubmitClinicalDecision: (ClinicalAlert, ClinicalDecisionAction, String) -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    val role = UserRole.fromName(user.role)

    if (bundle == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoSafetyBanner()
            EmptyStateCard(title = s.consentRequiredTitle, subtitle = s.consentBlockedMessage)
        }
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddMedDialog by remember { mutableStateOf(false) }
    var editingMed by remember { mutableStateOf<MedicationEntity?>(null) }
    var activeAlertForDecision by remember { mutableStateOf<ClinicalAlert?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(s.medicationHistoryTitle, s.medicationReconciliationTitle, s.medicationSafetyTitle)
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        modifier = Modifier.testTag("med_tab_$idx")
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.medicationHistoryTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showAddMedDialog = true },
                            modifier = Modifier.testTag("report_medication_button")
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(s.reportMedication)
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text(s.viewAll) }
                        )
                        MedicationStatus.entries.forEach { st ->
                            FilterChip(
                                selected = statusFilter == st.name,
                                onClick = { statusFilter = if (statusFilter == st.name) null else st.name },
                                label = { Text(st.displayName(s)) }
                            )
                        }
                    }
                }
                val meds = bundle.medications.filter { statusFilter == null || it.status == statusFilter }
                if (meds.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.medicationHistoryTitle) }
                } else {
                    items(meds, key = { it.id }) { med ->
                        val st = MedicationStatus.fromName(med.status)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${if (isFa) med.nameFa else med.nameEn} • ${med.strength}",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(color = InfoContainer, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = st.displayName(s),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NavyPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${med.genericName} • ${med.route} • ${med.frequency} • ${med.prescriberName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (med.notes.isNotBlank()) {
                                    Text(med.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, bundle.patient, med.sourceType) == null
                                val canVerify = ClinicalAuthorizationPolicy.evaluateVerifyAccess(user, bundle.patient) == null
                                RecordActionFooter(
                                    sourceType = med.sourceType,
                                    verificationStatus = med.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = {
                                        editingMed = med
                                        showAddMedDialog = true
                                    },
                                    onVerify = { onReconcileMedication(med, MedicationStatus.fromName(med.status)) },
                                    onDelete = { onDeleteMedication(med.id) },
                                    testTagPrefix = "med_${med.id}"
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Medication Reconciliation
                item {
                    Surface(color = WarningContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = s.reconciliationPrincipleNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = NavyPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                val flagged = bundle.medications.filter { it.reconciliationFlag }
                if (flagged.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = s.statusVerified,
                            subtitle = s.medicationReconciliationTitle
                        )
                    }
                } else {
                    items(flagged, key = { it.id }) { med ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${if (isFa) med.nameFa else med.nameEn} (${med.strength})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${MedicationStatus.fromName(med.status).displayName(s)} • ${med.prescriberName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber
                                )
                                SourceAndVerificationRow(med.sourceType, med.verificationStatus)
                                if (role.isClinician) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onReconcileMedication(med, MedicationStatus.DISPENSED) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                        ) {
                                            Icon(Icons.Filled.FactCheck, null, Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(s.medStateDispensed)
                                        }
                                        OutlinedButton(
                                            onClick = { onReconcileMedication(med, MedicationStatus.DISCONTINUED) }
                                        ) {
                                            Text(s.medStateDiscontinued)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Medication Safety Engine & 7-Stage Clinical Decision Support
                val report = bundle.cdsReport
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(s.sevenStageWorkflowTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isFa) report.stage1ContextSummaryFa else report.stage1ContextSummaryEn,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Stage 2 Aggregated Items: ${LocaleFormatter.formatNumber(report.stage2AggregatedCount, lang)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MedicalTeal
                            )
                            Surface(color = LightTeal, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = if (isFa) report.unvalidatedRulesNoticeFa else report.unvalidatedRulesNoticeEn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                items(report.alerts, key = { it.alertKey }) { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("cds_alert_${alert.alertKey}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SeverityBadge(alert.severity)
                                Text(
                                    text = alert.evidenceVersion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isFa) alert.titleFa else alert.titleEn,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isFa) alert.explanationFa else alert.explanationEn,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            HorizontalDivider()
                            Text(
                                text = "${s.evidenceSourceLabel}: ${alert.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${s.missingInfoLabel}: ${if (isFa) alert.missingInfoFa else alert.missingInfoEn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber
                            )
                            Text(
                                text = "${s.suggestedNextStepLabel}: ${if (isFa) alert.suggestedNextStepFa else alert.suggestedNextStepEn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MedicalTeal
                            )

                            val existingDecision = alert.latestDecision
                            if (existingDecision != null) {
                                val act = ClinicalDecisionAction.fromName(existingDecision.action)
                                Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(
                                                text = "${act.displayName(s)} • ${existingDecision.reviewerName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen
                                            )
                                            Text(existingDecision.rationale, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }

                            if (role.isClinician) {
                                Button(
                                    onClick = { activeAlertForDecision = alert },
                                    modifier = Modifier.fillMaxWidth().testTag("review_alert_btn_${alert.alertKey}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                                ) {
                                    Icon(Icons.Filled.Gavel, null, Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(s.submitDecision)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMedDialog) {
        val current = editingMed
        var name by remember { mutableStateOf(current?.nameEn ?: "") }
        var ingredient by remember { mutableStateOf(current?.activeIngredient ?: "") }
        var strength by remember { mutableStateOf(current?.strength ?: "") }
        var frequency by remember { mutableStateOf(current?.frequency ?: "") }
        var notes by remember { mutableStateOf(current?.notes ?: "") }
        var medStatus by remember {
            mutableStateOf(
                if (current != null) MedicationStatus.fromName(current.status)
                else if (role.isClinician) MedicationStatus.PRESCRIBED else MedicationStatus.PATIENT_REPORTED
            )
        }
        AlertDialog(
            onDismissRequest = {
                showAddMedDialog = false
                editingMed = null
            },
            title = { Text(if (current == null) s.reportMedication else "${s.editRecordButton} ${s.medicationHistoryTitle}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = current == null,
                        label = { Text("Medication Name *") }
                    )
                    if (current == null) {
                        OutlinedTextField(value = ingredient, onValueChange = { ingredient = it }, label = { Text("Active Ingredient") })
                    }
                    OutlinedTextField(value = strength, onValueChange = { strength = it }, label = { Text("Strength (e.g., 500 mg) *") })
                    OutlinedTextField(value = frequency, onValueChange = { frequency = it }, label = { Text("Frequency (e.g., Once daily)") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            MedicationStatus.PATIENT_REPORTED,
                            MedicationStatus.PRESCRIBED,
                            MedicationStatus.DISPENSED,
                            MedicationStatus.DISCONTINUED,
                            MedicationStatus.UNCERTAIN
                        ).forEach { st ->
                            FilterChip(
                                selected = medStatus == st,
                                onClick = { medStatus = st },
                                label = { Text(st.displayName(s)) }
                            )
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(s.notesLabel) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && strength.isNotBlank()) {
                        if (current == null) {
                            onReportMedication(name, ingredient, strength, frequency.ifBlank { "As directed" }, medStatus, notes)
                        } else {
                            onUpdateMedication(current.id, strength, frequency.ifBlank { current.frequency }, medStatus, notes)
                        }
                        showAddMedDialog = false
                        editingMed = null
                    }
                }) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddMedDialog = false
                    editingMed = null
                }) { Text(s.cancel) }
            }
        )
    }

    activeAlertForDecision?.let { alert ->
        var chosenAction by remember { mutableStateOf(ClinicalDecisionAction.ACCEPT) }
        var rationale by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { activeAlertForDecision = null },
            title = { Text(if (isFa) alert.titleFa else alert.titleEn) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ClinicalDecisionAction.ACCEPT,
                            ClinicalDecisionAction.MODIFY,
                            ClinicalDecisionAction.DEFER,
                            ClinicalDecisionAction.REJECT
                        ).forEach { act ->
                            FilterChip(
                                selected = chosenAction == act,
                                onClick = { chosenAction = act },
                                label = { Text(act.displayName(s)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = rationale,
                        onValueChange = { rationale = it; err = null },
                        label = { Text("${s.rationaleLabel} *") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("cds_rationale_input")
                    )
                    if (err != null) {
                        Text(err!!, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rationale.isBlank()) {
                            err = s.rationaleRequiredError
                        } else {
                            onSubmitClinicalDecision(alert, chosenAction, rationale)
                            activeAlertForDecision = null
                        }
                    },
                    modifier = Modifier.testTag("cds_submit_decision_button")
                ) { Text(s.submitDecision) }
            },
            dismissButton = {
                TextButton(onClick = { activeAlertForDecision = null }) { Text(s.cancel) }
            }
        )
    }
}
