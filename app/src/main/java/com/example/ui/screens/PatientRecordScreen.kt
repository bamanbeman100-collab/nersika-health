package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.clinical.PatientProfileUpdateRequest
import com.example.data.local.AllergyEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.MedicalHistoryEntity
import com.example.data.local.PatientEntity
import com.example.data.local.UserEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.PopulationGroup
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.PatientBundle
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RecordActionFooter
import com.example.ui.components.SeverityBadge
import com.example.ui.components.SourceAndVerificationRow
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen

private enum class TimelineCategory {
    ALL, DIAGNOSIS, HISTORY, MEDICATION, LAB, DOCUMENT, CARE_PLAN, VITAL
}

private data class UnifiedTimelineEvent(
    val key: String,
    val category: TimelineCategory,
    val categoryLabel: String,
    val timestampMillis: Long,
    val title: String,
    val subtitle: String,
    val sourceType: String,
    val verificationStatus: String
)

@Composable
fun PatientDirectoryScreen(
    patients: List<PatientEntity>,
    selectedPatientId: String,
    consentDeniedPatientId: String?,
    onDismissConsentAlert: () -> Unit,
    onSelectPatient: (PatientEntity) -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(patients, query) {
        if (query.isBlank()) patients
        else patients.filter {
            it.firstNameEn.contains(query, true) ||
                it.lastNameEn.contains(query, true) ||
                it.firstNameFa.contains(query, true) ||
                it.lastNameFa.contains(query, true) ||
                it.mrn.contains(query, true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DemoSafetyBanner()
            Spacer(Modifier.height(8.dp))
            Text(s.patientDirectory, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(s.searchPatientsPlaceholder) },
                leadingIcon = { Icon(Icons.Filled.PersonSearch, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("patient_search_input")
            )
        }

        items(filtered, key = { it.id }) { patient ->
            val name = if (isFa) "${patient.firstNameFa} ${patient.lastNameFa}" else "${patient.firstNameEn} ${patient.lastNameEn}"
            val pop = PopulationGroup.fromName(patient.populationGroup)
            val isSelected = patient.id == selectedPatientId

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPatient(patient) }
                    .testTag("patient_card_${patient.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) LightTeal else MaterialTheme.colorScheme.surface
                )
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${patient.mrn} • ${LocaleFormatter.formatNumber(patient.ageYears, lang)}y • ${if (isFa) pop.labelFa else pop.labelEn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = if (patient.consentClinicianAccess) SuccessContainer else CriticalContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (patient.consentClinicianAccess) Icons.Filled.VerifiedUser else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (patient.consentClinicianAccess) SuccessGreen else CriticalRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (patient.consentClinicianAccess) s.verifiedIdentityBadge else s.consentRequiredTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (patient.consentClinicianAccess) SuccessGreen else CriticalRed
                                )
                            }
                        }
                    }
                    Text(
                        text = s.verifyIdentityAndAccess,
                        style = MaterialTheme.typography.labelMedium,
                        color = MedicalTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (consentDeniedPatientId != null) {
        AlertDialog(
            onDismissRequest = onDismissConsentAlert,
            title = { Text(s.consentRequiredTitle, color = CriticalRed) },
            text = { Text(s.consentBlockedMessage) },
            confirmButton = {
                Button(onClick = onDismissConsentAlert) { Text(s.close) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthRecordScreen(
    user: UserEntity,
    bundle: PatientBundle?,
    onUpdateProfile: (PatientProfileUpdateRequest) -> Unit,
    onAddCondition: (String, String, String, String) -> Unit,
    onUpdateCondition: (String, String, String, String, String) -> Unit,
    onVerifyCondition: (String) -> Unit,
    onDeleteCondition: (String) -> Unit,
    onAddMedicalHistory: (MedicalHistoryCategory, String, String) -> Unit,
    onUpdateMedicalHistory: (String, MedicalHistoryCategory, String, String) -> Unit,
    onVerifyMedicalHistory: (String) -> Unit,
    onDeleteMedicalHistory: (String) -> Unit,
    onAddAllergy: (String, String, AlertSeverity) -> Unit,
    onUpdateAllergy: (String, String, String, AlertSeverity) -> Unit,
    onVerifyAllergy: (String) -> Unit,
    onDeleteAllergy: (String) -> Unit,
    onAddObservation: (String, String, String) -> Unit,
    onVerifyObservation: (String) -> Unit,
    onDeleteObservation: (String) -> Unit
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
    val canEditProfile = remember(user, patient) {
        ClinicalAuthorizationPolicy.evaluateProfileEditAccess(user, patient) == null
    }
    val canVerify = remember(user, patient) {
        ClinicalAuthorizationPolicy.evaluateVerifyAccess(user, patient) == null
    }

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var timelineCategory by rememberSaveable { mutableStateOf(TimelineCategory.ALL) }
    var timelineQuery by rememberSaveable { mutableStateOf("") }

    var showProfileDialog by remember { mutableStateOf(false) }
    var editingCondition by remember { mutableStateOf<ConditionEntity?>(null) }
    var showCreateCondition by remember { mutableStateOf(false) }
    var editingHistory by remember { mutableStateOf<MedicalHistoryEntity?>(null) }
    var showCreateHistory by remember { mutableStateOf(false) }
    var editingAllergy by remember { mutableStateOf<AllergyEntity?>(null) }
    var showCreateAllergy by remember { mutableStateOf(false) }
    var showObsDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val unifiedEvents = remember(bundle, isFa, lang, s, timelineCategory, timelineQuery) {
        buildList {
            bundle.conditions.forEach { c ->
                add(UnifiedTimelineEvent("c_${c.id}", TimelineCategory.DIAGNOSIS, s.timelineFilterDiagnoses, c.eventDateMillis, if (isFa) c.nameFa else c.nameEn, "${c.icd10Code} • ${c.status} • ${c.authorName}", c.sourceType, c.verificationStatus))
            }
            bundle.medicalHistory.forEach { h ->
                val hCat = MedicalHistoryCategory.fromName(h.category)
                add(UnifiedTimelineEvent("h_${h.id}", TimelineCategory.HISTORY, if (isFa) hCat.labelFa else hCat.labelEn, h.eventDateMillis, if (isFa) h.titleFa else h.titleEn, "${if (isFa) h.detailsFa else h.detailsEn} • ${h.authorName}", h.sourceType, h.verificationStatus))
            }
            bundle.allergies.forEach { a ->
                add(UnifiedTimelineEvent("a_${a.id}", TimelineCategory.VITAL, s.allergiesSection, a.eventDateMillis, if (isFa) a.allergenFa else a.allergenEn, "${if (isFa) a.reactionFa else a.reactionEn} • ${a.authorName}", a.sourceType, a.verificationStatus))
            }
            bundle.medications.forEach { m ->
                add(UnifiedTimelineEvent("m_${m.id}", TimelineCategory.MEDICATION, s.timelineFilterMedications, m.startDateMillis, "${if (isFa) m.nameFa else m.nameEn} (${m.strength})", "${m.frequency} • ${m.prescriberName}", m.sourceType, m.verificationStatus))
            }
            bundle.labs.forEach { l ->
                add(UnifiedTimelineEvent("l_${l.id}", TimelineCategory.LAB, s.timelineFilterLabs, l.resultDateMillis, "${if (isFa) l.testNameFa else l.testNameEn}: ${LocaleFormatter.formatNumber(l.value, lang)} ${l.unit}", "${l.labName} • LOINC ${l.loincCode}", l.sourceType, l.verificationStatus))
            }
            bundle.documents.forEach { d ->
                add(UnifiedTimelineEvent("d_${d.id}", TimelineCategory.DOCUMENT, s.timelineFilterDocuments, d.uploadedAtMillis, if (isFa) d.titleFa else d.titleEn, "${if (isFa) d.extractedSummaryFa else d.extractedSummaryEn} • ${d.uploadedBy}", d.sourceType, d.verificationStatus))
            }
            bundle.carePlans.forEach { cp ->
                add(UnifiedTimelineEvent("cp_${cp.id}", TimelineCategory.CARE_PLAN, s.timelineFilterCarePlans, cp.updatedAtMillis, if (isFa) cp.titleFa else cp.titleEn, "${LocaleFormatter.formatNumber(cp.progressPct, lang)}% • ${cp.authorName}", cp.sourceType, cp.verificationStatus))
            }
            bundle.observations.forEach { o ->
                add(UnifiedTimelineEvent("o_${o.id}", TimelineCategory.VITAL, s.timelineFilterVitals, o.recordedAtMillis, "${if (isFa) o.typeFa else o.typeEn}: ${o.valueText} ${o.unit}", o.authorName, o.sourceType, o.verificationStatus))
            }
        }
            .filter { ev ->
                (timelineCategory == TimelineCategory.ALL || ev.category == timelineCategory) &&
                    (timelineQuery.isBlank() || ev.title.contains(timelineQuery, true) || ev.subtitle.contains(timelineQuery, true))
            }
            .sortedByDescending { it.timestampMillis }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("health_record_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DemoSafetyBanner() }

        // Patient Identity Header Card
        item {
            val pName = if (isFa) "${patient.firstNameFa} ${patient.lastNameFa}" else "${patient.firstNameEn} ${patient.lastNameEn}"
            val pop = PopulationGroup.fromName(patient.populationGroup)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("record_identity_header"),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${patient.mrn} • ${patient.dateOfBirthIso} • ${patient.bloodType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (patient.verifiedIdentity) {
                            Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = s.verifiedIdentityBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(color = LightTeal, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "${s.populationLabel}: ${if (isFa) pop.labelFa else pop.labelEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(color = LightTeal, shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.MonitorWeight, null, Modifier.size(14.dp), tint = NavyPrimary)
                                Text(
                                    text = if (patient.weightKg > 0) "${LocaleFormatter.formatNumber(patient.weightKg, lang)} kg"
                                    else s.missingInfoLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary
                                )
                            }
                        }
                        if (canEditProfile) {
                            OutlinedButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("edit_profile_btn")
                            ) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(s.editProfileButton)
                            }
                        }
                    }
                }
            }
        }

        // Scrollable 6-Tab Record Navigator (prevents narrow-screen text clipping in EN & FA)
        item {
            val sectionTabs = listOf(
                Triple(0, s.timelineFilterAll + " • " + s.chronologicalTimeline, "record_tab_timeline"),
                Triple(1, s.profileSection, "record_tab_profile"),
                Triple(2, s.conditionsSection, "record_tab_diagnoses"),
                Triple(3, s.medicalHistorySection, "record_tab_history"),
                Triple(4, s.allergiesSection, "record_tab_allergies"),
                Triple(5, s.vitalsSection, "record_tab_vitals")
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("record_section_tabs_row")
            ) {
                items(sectionTabs, key = { it.first }) { (idx, label, tag) ->
                    FilterChip(
                        selected = selectedSection == idx,
                        onClick = { selectedSection = idx },
                        label = { Text(label) },
                        modifier = Modifier.testTag(tag)
                    )
                }
            }
        }

        when (selectedSection) {
            // 0: Unified Chronological Health Timeline
            0 -> {
                item {
                    OutlinedTextField(
                        value = timelineQuery,
                        onValueChange = { timelineQuery = it },
                        label = { Text(s.search) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("timeline_search_input")
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("timeline_filters_row")
                    ) {
                        val filters = listOf(
                            TimelineCategory.ALL to s.timelineFilterAll,
                            TimelineCategory.DIAGNOSIS to s.timelineFilterDiagnoses,
                            TimelineCategory.HISTORY to s.timelineFilterHistory,
                            TimelineCategory.MEDICATION to s.timelineFilterMedications,
                            TimelineCategory.LAB to s.timelineFilterLabs,
                            TimelineCategory.DOCUMENT to s.timelineFilterDocuments,
                            TimelineCategory.CARE_PLAN to s.timelineFilterCarePlans,
                            TimelineCategory.VITAL to s.timelineFilterVitals
                        )
                        items(filters, key = { it.first.name }) { (cat, label) ->
                            FilterChip(
                                selected = timelineCategory == cat,
                                onClick = { timelineCategory = cat },
                                label = { Text(label) },
                                modifier = Modifier.testTag("timeline_filter_${cat.name.lowercase()}")
                            )
                        }
                    }
                }

                if (unifiedEvents.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.chronologicalTimeline) }
                } else {
                    items(unifiedEvents, key = { it.key }) { ev ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("timeline_item_${ev.key}"),
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
                                        text = LocaleFormatter.formatDateTime(ev.timestampMillis, lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MedicalTeal
                                    )
                                    Surface(color = LightTeal, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = ev.categoryLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                Text(ev.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SourceAndVerificationRow(sourceType = ev.sourceType, verificationStatus = ev.verificationStatus)
                            }
                        }
                    }
                }
            }

            // 1: Complete Patient Profile & Provenance / Audit Summary
            1 -> {
                item {
                    val pop = PopulationGroup.fromName(patient.populationGroup)
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("full_profile_details_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.profileSection, style = MaterialTheme.typography.titleMedium)
                                if (canEditProfile) {
                                    OutlinedButton(onClick = { showProfileDialog = true }) {
                                        Text(s.editProfileButton)
                                    }
                                }
                            }
                            HorizontalDivider()
                            ProfileFieldRow(s.mrnLabel, patient.mrn)
                            ProfileFieldRow(s.onboardingDobLabel, "${patient.dateOfBirthIso} (${LocaleFormatter.formatNumber(patient.ageYears, lang)}y)")
                            ProfileFieldRow(s.onboardingSexLabel, patient.sex)
                            ProfileFieldRow(s.populationLabel, if (isFa) pop.labelFa else pop.labelEn)
                            ProfileFieldRow(s.onboardingBloodTypeLabel, patient.bloodType)
                            ProfileFieldRow(s.onboardingWeightLabel, if (patient.weightKg > 0) "${LocaleFormatter.formatNumber(patient.weightKg, lang)} kg" else s.missingInfoLabel)
                            ProfileFieldRow(s.phoneLabel, patient.phone)
                            ProfileFieldRow(s.onboardingEmergencyLabel, patient.emergencyContact)
                            ProfileFieldRow(s.pregnancyStatusLabel, if (patient.isPregnant) "Yes / بله" else "No / خیر")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { patient.profileCompletionPct / 100f },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MedicalTeal
                                )
                                Text("${LocaleFormatter.formatNumber(patient.profileCompletionPct, lang)}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (bundle.patientAuditEvents.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.auditLogsTitle, style = MaterialTheme.typography.titleSmall)
                                bundle.patientAuditEvents.take(5).forEach { aud ->
                                    Text(
                                        text = "${aud.outcome} • ${aud.actionType} (${aud.actorName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (aud.outcome == "DENIED") CriticalRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2: Diagnoses & Conditions CRUD
            2 -> {
                item {
                    OutlinedButton(
                        onClick = { showCreateCondition = true },
                        modifier = Modifier.testTag("add_condition_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(s.addCondition)
                    }
                }
                if (bundle.conditions.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.conditionsSection) }
                } else {
                    items(bundle.conditions, key = { it.id }) { cond ->
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, cond.sourceType) == null
                        Card(modifier = Modifier.fillMaxWidth().testTag("condition_card_${cond.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isFa) cond.nameFa else cond.nameEn, style = MaterialTheme.typography.titleSmall)
                                Text("ICD-10: ${cond.icd10Code} • ${cond.status} • ${cond.authorName}", style = MaterialTheme.typography.bodySmall)
                                if (cond.notes.isNotBlank()) Text(cond.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RecordActionFooter(
                                    sourceType = cond.sourceType,
                                    verificationStatus = cond.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = { editingCondition = cond },
                                    onVerify = { onVerifyCondition(cond.id) },
                                    onDelete = { pendingDeleteAction = { onDeleteCondition(cond.id) } },
                                    testTagPrefix = "cond_${cond.id}"
                                )
                            }
                        }
                    }
                }
            }

            // 3: Medical History CRUD
            3 -> {
                item {
                    OutlinedButton(
                        onClick = { showCreateHistory = true },
                        modifier = Modifier.testTag("add_history_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(s.addMedicalHistory)
                    }
                }
                if (bundle.medicalHistory.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.medicalHistorySection) }
                } else {
                    items(bundle.medicalHistory, key = { it.id }) { entry ->
                        val cat = MedicalHistoryCategory.fromName(entry.category)
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, entry.sourceType) == null
                        Card(modifier = Modifier.fillMaxWidth().testTag("history_card_${entry.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isFa) entry.titleFa else entry.titleEn, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Surface(color = LightTeal, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = if (isFa) cat.labelFa else cat.labelEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NavyPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${entry.authorName} • ${LocaleFormatter.formatDate(entry.eventDateMillis, lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MedicalTeal
                                )
                                if (entry.detailsEn.isNotBlank()) {
                                    Text(if (isFa) entry.detailsFa else entry.detailsEn, style = MaterialTheme.typography.bodySmall)
                                }
                                RecordActionFooter(
                                    sourceType = entry.sourceType,
                                    verificationStatus = entry.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = { editingHistory = entry },
                                    onVerify = { onVerifyMedicalHistory(entry.id) },
                                    onDelete = { pendingDeleteAction = { onDeleteMedicalHistory(entry.id) } },
                                    testTagPrefix = "hist_${entry.id}"
                                )
                            }
                        }
                    }
                }
            }

            // 4: Allergies CRUD
            4 -> {
                item {
                    OutlinedButton(
                        onClick = { showCreateAllergy = true },
                        modifier = Modifier.testTag("add_allergy_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(s.addAllergy)
                    }
                }
                if (bundle.allergies.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.allergiesSection) }
                } else {
                    items(bundle.allergies, key = { it.id }) { alg ->
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, alg.sourceType) == null
                        Card(modifier = Modifier.fillMaxWidth().testTag("allergy_card_${alg.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isFa) alg.allergenFa else alg.allergenEn, style = MaterialTheme.typography.titleSmall)
                                    SeverityBadge(AlertSeverity.fromName(alg.severity))
                                }
                                Text(if (isFa) alg.reactionFa else alg.reactionEn, style = MaterialTheme.typography.bodySmall)
                                RecordActionFooter(
                                    sourceType = alg.sourceType,
                                    verificationStatus = alg.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onEdit = { editingAllergy = alg },
                                    onVerify = { onVerifyAllergy(alg.id) },
                                    onDelete = { pendingDeleteAction = { onDeleteAllergy(alg.id) } },
                                    testTagPrefix = "alg_${alg.id}"
                                )
                            }
                        }
                    }
                }
            }

            // 5: Observations & Vitals CRUD
            5 -> {
                item {
                    OutlinedButton(
                        onClick = { showObsDialog = true },
                        modifier = Modifier.testTag("add_obs_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(s.addObservation)
                    }
                }
                if (bundle.observations.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.vitalsSection) }
                } else {
                    items(bundle.observations, key = { it.id }) { obs ->
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, obs.sourceType) == null
                        Card(modifier = Modifier.fillMaxWidth().testTag("obs_card_${obs.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${if (isFa) obs.typeFa else obs.typeEn}: ${obs.valueText} ${obs.unit}", style = MaterialTheme.typography.titleSmall)
                                Text("${obs.authorName} • ${LocaleFormatter.formatDateTime(obs.recordedAtMillis, lang)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RecordActionFooter(
                                    sourceType = obs.sourceType,
                                    verificationStatus = obs.verificationStatus,
                                    canModify = canModify,
                                    canVerify = canVerify,
                                    onVerify = { onVerifyObservation(obs.id) },
                                    onDelete = { pendingDeleteAction = { onDeleteObservation(obs.id) } },
                                    testTagPrefix = "obs_${obs.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        EditProfileDialog(
            patient = patient,
            onDismiss = { showProfileDialog = false },
            onSave = onUpdateProfile
        )
    }

    if (showCreateCondition || editingCondition != null) {
        val current = editingCondition
        ConditionDialog(
            initial = current,
            onDismiss = {
                showCreateCondition = false
                editingCondition = null
            },
            onSave = { name, icd, status, notes ->
                if (current == null) onAddCondition(name, icd, status, notes)
                else onUpdateCondition(current.id, name, icd, status, notes)
            }
        )
    }

    if (showCreateHistory || editingHistory != null) {
        val current = editingHistory
        MedicalHistoryDialog(
            initial = current,
            onDismiss = {
                showCreateHistory = false
                editingHistory = null
            },
            onSave = { cat, title, details ->
                if (current == null) onAddMedicalHistory(cat, title, details)
                else onUpdateMedicalHistory(current.id, cat, title, details)
            }
        )
    }

    if (showCreateAllergy || editingAllergy != null) {
        val current = editingAllergy
        AllergyDialog(
            initial = current,
            onDismiss = {
                showCreateAllergy = false
                editingAllergy = null
            },
            onSave = { allergen, reaction, sev ->
                if (current == null) onAddAllergy(allergen, reaction, sev)
                else onUpdateAllergy(current.id, allergen, reaction, sev)
            }
        )
    }

    if (showObsDialog) {
        var title by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showObsDialog = false },
            title = { Text(s.addObservation) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("${s.vitalsSection} *") })
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("${s.valueLabel} *") })
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(s.unitLabel) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && value.isNotBlank()) {
                        onAddObservation(title, value, unit)
                        showObsDialog = false
                    }
                }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showObsDialog = false }) { Text(s.cancel) } }
        )
    }

    pendingDeleteAction?.let { deleteFn ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAction = null },
            title = { Text(s.confirmDeleteTitle) },
            text = { Text(s.confirmDeleteMessage) },
            confirmButton = {
                Button(onClick = {
                    deleteFn()
                    pendingDeleteAction = null
                }) { Text(s.deleteRecordButton) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteAction = null }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}
