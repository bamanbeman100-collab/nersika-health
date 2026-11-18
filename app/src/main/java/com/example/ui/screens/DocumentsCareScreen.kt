package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.local.CarePlanEntity
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.UserEntity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.DocumentCategory
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.PatientBundle
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RecordActionFooter
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentsAndCareScreen(
    user: UserEntity,
    bundle: PatientBundle?,
    onUploadDocument: (String, DocumentCategory, String, String, String) -> Unit,
    onUpdateDocumentSummary: (String, String, DocumentCategory, String) -> Unit,
    onVerifyDocument: (MedicalDocumentEntity) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onAddCarePlan: (String, String, Int, CarePlanStatus) -> Unit,
    onUpdateCarePlan: (String, String, String, Int, CarePlanStatus) -> Unit,
    onVerifyCarePlan: (String) -> Unit,
    onDeleteCarePlan: (String) -> Unit,
    onUpdateConsents: (Boolean, Boolean, Boolean) -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA

    if (bundle == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoSafetyBanner()
            EmptyStateCard(title = s.consentRequiredTitle, subtitle = s.consentBlockedMessage)
        }
        return
    }

    val patient = bundle.patient
    val canVerify = remember(user, patient) {
        ClinicalAuthorizationPolicy.evaluateVerifyAccess(user, patient) == null
    }
    val canManageConsent = remember(user, patient) {
        ClinicalAuthorizationPolicy.evaluateConsentManagementAccess(user, patient) == null
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingUri by remember { mutableStateOf<String?>(null) }
    var pendingMime by remember { mutableStateOf("application/octet-stream") }
    var editingDoc by remember { mutableStateOf<MedicalDocumentEntity?>(null) }
    var showDocDetailsDialog by remember { mutableStateOf(false) }

    var showCarePlanDialog by remember { mutableStateOf(false) }
    var editingCarePlan by remember { mutableStateOf<CarePlanEntity?>(null) }
    var pendingDeleteFn by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri.toString()
            pendingMime = "image/*"
            editingDoc = null
            showDocDetailsDialog = true
        }
    }

    // Zero-permission Storage Access Framework Document Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri.toString()
            pendingMime = "application/pdf"
            editingDoc = null
            showDocDetailsDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DemoSafetyBanner() }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(s.documentsTitle, s.carePlansTitle, s.appointmentsTitle, s.navConsent)
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        modifier = Modifier.testTag("docs_care_tab_$idx")
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_photo_doc_btn")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(s.selectFromPhotos)
                        }
                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            modifier = Modifier.testTag("pick_pdf_doc_btn")
                        ) {
                            Icon(Icons.Filled.FolderOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(s.selectDocumentFile)
                        }
                    }
                }

                if (bundle.documents.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.documentsTitle) }
                } else {
                    items(bundle.documents, key = { it.id }) { doc ->
                        val cat = DocumentCategory.fromName(doc.category)
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, doc.sourceType) == null
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("document_card_${doc.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Description, null, tint = MedicalTeal)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (isFa) doc.titleFa else doc.titleEn, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${if (isFa) cat.labelFa else cat.labelEn} • ${doc.uploadedBy} • ${LocaleFormatter.formatDate(doc.uploadedAtMillis, lang)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = if (isFa) doc.extractedSummaryFa else doc.extractedSummaryEn,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                RecordActionFooter(
                                    sourceType = doc.sourceType,
                                    verificationStatus = doc.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = {
                                        editingDoc = doc
                                        showDocDetailsDialog = true
                                    },
                                    onVerify = { onVerifyDocument(doc) },
                                    onDelete = { pendingDeleteFn = { onDeleteDocument(doc.id) } },
                                    testTagPrefix = "doc_${doc.id}"
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                item {
                    Button(
                        onClick = {
                            editingCarePlan = null
                            showCarePlanDialog = true
                        },
                        modifier = Modifier.testTag("add_care_plan_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(s.addCarePlan)
                    }
                }
                if (bundle.carePlans.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.carePlansTitle) }
                } else {
                    items(bundle.carePlans, key = { it.id }) { plan ->
                        val st = CarePlanStatus.fromName(plan.status)
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, plan.sourceType) == null
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("care_plan_card_${plan.id}"),
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
                                    Text(
                                        text = if (isFa) plan.titleFa else plan.titleEn,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(color = LightTeal, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = if (isFa) st.labelFa else st.labelEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NavyPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(if (isFa) plan.goalFa else plan.goalEn, style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(
                                    progress = { plan.progressPct / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MedicalTeal
                                )
                                Text(
                                    text = "${LocaleFormatter.formatNumber(plan.progressPct, lang)}% • ${plan.authorName} • ${LocaleFormatter.formatDate(plan.updatedAtMillis, lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                RecordActionFooter(
                                    sourceType = plan.sourceType,
                                    verificationStatus = plan.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = {
                                        editingCarePlan = plan
                                        showCarePlanDialog = true
                                    },
                                    onVerify = { onVerifyCarePlan(plan.id) },
                                    onDelete = { pendingDeleteFn = { onDeleteCarePlan(plan.id) } },
                                    testTagPrefix = "cp_${plan.id}"
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                if (bundle.appointments.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.appointmentsTitle) }
                } else {
                    items(bundle.appointments, key = { it.id }) { apt ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.CalendarMonth, null, tint = MedicalTeal)
                                        Text(apt.clinicianName, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Surface(color = LightTeal, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = apt.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(if (isFa) apt.specialtyFa else apt.specialtyEn, style = MaterialTheme.typography.bodySmall)
                                Text(if (isFa) apt.locationFa else apt.locationEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (isFa) apt.reasonFa else apt.reasonEn, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = LocaleFormatter.formatDateTime(apt.scheduledAtMillis, lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MedicalTeal
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                // Privacy & Consent Management
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("consent_management_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(s.consentManagementTitle, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = if (canManageConsent) InfoContainer else CriticalContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = s.consentOwnerOnlyNotice,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canManageConsent) NavyPrimary else CriticalRed,
                                    modifier = Modifier.padding(10.dp).testTag("consent_authorization_notice")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.consentClinicianLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = patient.consentClinicianAccess,
                                    enabled = canManageConsent,
                                    onCheckedChange = { onUpdateConsents(it, patient.consentCaregiverAccess, patient.consentAiSummarization) },
                                    modifier = Modifier.testTag("consent_clinician_switch")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.consentCaregiverLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = patient.consentCaregiverAccess,
                                    enabled = canManageConsent,
                                    onCheckedChange = { onUpdateConsents(patient.consentClinicianAccess, it, patient.consentAiSummarization) },
                                    modifier = Modifier.testTag("consent_caregiver_switch")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.consentAiLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = patient.consentAiSummarization,
                                    enabled = canManageConsent,
                                    onCheckedChange = { onUpdateConsents(patient.consentClinicianAccess, patient.consentCaregiverAccess, it) },
                                    modifier = Modifier.testTag("consent_ai_switch")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDocDetailsDialog && (pendingUri != null || editingDoc != null)) {
        val currentDoc = editingDoc
        var title by remember { mutableStateOf(currentDoc?.let { if (isFa) it.titleFa else it.titleEn } ?: "") }
        var category by remember {
            mutableStateOf(
                if (currentDoc != null) DocumentCategory.fromName(currentDoc.category)
                else DocumentCategory.LAB_REPORT
            )
        }
        var summary by remember { mutableStateOf(currentDoc?.let { if (isFa) it.extractedSummaryFa else it.extractedSummaryEn } ?: "") }

        AlertDialog(
            onDismissRequest = {
                showDocDetailsDialog = false
                editingDoc = null
            },
            title = { Text(if (currentDoc == null) s.uploadDocument else "${s.editRecordButton} ${s.documentsTitle}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("${s.documentsTitle} *") })
                    Text(s.documentCategoryLabel, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DocumentCategory.entries.take(5).forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(if (isFa) cat.labelFa else cat.labelEn) }
                            )
                        }
                    }
                    OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text(s.notesLabel) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        if (currentDoc == null) {
                            onUploadDocument(title, category, pendingUri!!, pendingMime, summary)
                        } else {
                            onUpdateDocumentSummary(currentDoc.id, title, category, summary)
                        }
                        showDocDetailsDialog = false
                        editingDoc = null
                    }
                }) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDocDetailsDialog = false
                    editingDoc = null
                }) { Text(s.cancel) }
            }
        )
    }

    if (showCarePlanDialog) {
        val current = editingCarePlan
        var title by remember { mutableStateOf(current?.let { if (isFa) it.titleFa else it.titleEn } ?: "") }
        var goal by remember { mutableStateOf(current?.let { if (isFa) it.goalFa else it.goalEn } ?: "") }
        var progressText by remember { mutableStateOf((current?.progressPct ?: 25).toString()) }
        var status by remember {
            mutableStateOf(
                if (current != null) CarePlanStatus.fromName(current.status) else CarePlanStatus.ACTIVE
            )
        }
        AlertDialog(
            onDismissRequest = { showCarePlanDialog = false },
            title = { Text(if (current == null) s.addCarePlan else s.editCarePlan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("${s.carePlanTitleLabel} *") },
                        modifier = Modifier.fillMaxWidth().testTag("care_plan_title_input")
                    )
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it },
                        label = { Text("${s.carePlanGoalLabel} *") },
                        modifier = Modifier.fillMaxWidth().testTag("care_plan_goal_input")
                    )
                    OutlinedTextField(
                        value = progressText,
                        onValueChange = { progressText = it },
                        label = { Text(s.carePlanProgressLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CarePlanStatus.entries.forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(if (isFa) st.labelFa else st.labelEn) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pct = (progressText.toIntOrNull() ?: 0).coerceIn(0, 100)
                        if (title.isNotBlank() && goal.isNotBlank()) {
                            if (current == null) onAddCarePlan(title, goal, pct, status)
                            else onUpdateCarePlan(current.id, title, goal, pct, status)
                            showCarePlanDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_care_plan_btn")
                ) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showCarePlanDialog = false }) { Text(s.cancel) } }
        )
    }

    pendingDeleteFn?.let { deleteFn ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFn = null },
            title = { Text(s.confirmDeleteTitle) },
            text = { Text(s.confirmDeleteMessage) },
            confirmButton = {
                Button(onClick = {
                    deleteFn()
                    pendingDeleteFn = null
                }) { Text(s.deleteRecordButton) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteFn = null }) { Text(s.cancel) } }
        )
    }
}
