package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.ClinicalModulesCatalog
import com.example.domain.model.PopulationGroup
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.PatientBundle
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.SeverityBadge
import com.example.ui.components.SourceAndVerificationRow
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

private data class DashboardTimelineSnapshot(
    val timestampMillis: Long,
    val title: String,
    val subtitle: String,
    val sourceType: String,
    val verificationStatus: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleDashboardScreen(
    user: UserEntity,
    bundle: PatientBundle?,
    totalPatientsCount: Int,
    unreadNotificationsCount: Int,
    onNavigateRoute: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    val role = UserRole.fromName(user.role)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DemoSafetyBanner(modifier = Modifier.testTag("demo_safety_banner"))
        }

        // Clinical Hero Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(156.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_clinical_hero),
                        contentDescription = s.brandName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NavyPrimary.copy(alpha = 0.68f))
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MedicalTeal,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = role.displayName(s),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "${s.welcomeBack}, ${user.firstName} ${user.lastName}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            if (bundle != null) {
                                val pName = if (isFa) "${bundle.patient.firstNameFa} ${bundle.patient.lastNameFa}"
                                else "${bundle.patient.firstNameEn} ${bundle.patient.lastNameEn}"
                                Text(
                                    text = "$pName • ${bundle.patient.mrn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LightTeal
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { bundle.patient.profileCompletionPct / 100f },
                                        modifier = Modifier.width(120.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MedicalTeal,
                                        trackColor = Color.White.copy(alpha = 0.25f)
                                    )
                                    Text(
                                        text = "${LocaleFormatter.formatNumber(bundle.patient.profileCompletionPct, lang)}% ${s.profileCompletion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bundle == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_consent_blocked_card"),
                    colors = CardDefaults.cardColors(containerColor = CriticalContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = CriticalRed)
                            Text(s.consentRequiredTitle, style = MaterialTheme.typography.titleMedium, color = CriticalRed)
                        }
                        Text(s.consentBlockedMessage, style = MaterialTheme.typography.bodySmall, color = CriticalRed)
                        if (role != UserRole.PATIENT) {
                            Button(onClick = { onNavigateRoute("patients") }) {
                                Text(s.patientDirectory)
                            }
                        }
                    }
                }
            }
        } else {
            val patient = bundle.patient
            val pop = PopulationGroup.fromName(patient.populationGroup)

            // Patient Profile & Consent Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_patient_profile_card"),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.profileSection, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${patient.mrn} • ${LocaleFormatter.formatNumber(patient.ageYears, lang)}y • ${patient.bloodType} • ${if (isFa) pop.labelFa else pop.labelEn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (patient.verifiedIdentity) {
                                Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = s.verifiedIdentityBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ConsentStatusBadge(
                                label = s.consentClinicianLabel,
                                granted = patient.consentClinicianAccess,
                                grantedText = s.consentGrantedLabel,
                                revokedText = s.consentRevokedLabel
                            )
                            ConsentStatusBadge(
                                label = s.consentCaregiverLabel,
                                granted = patient.consentCaregiverAccess,
                                grantedText = s.consentGrantedLabel,
                                revokedText = s.consentRevokedLabel
                            )
                            ConsentStatusBadge(
                                label = s.consentAiLabel,
                                granted = patient.consentAiSummarization,
                                grantedText = s.consentGrantedLabel,
                                revokedText = s.consentRevokedLabel
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onNavigateRoute("records") },
                                modifier = Modifier.testTag("dashboard_open_record_btn")
                            ) {
                                Text(s.navRecords)
                            }
                            OutlinedButton(
                                onClick = { onNavigateRoute("documents") },
                                modifier = Modifier.testTag("dashboard_open_consent_btn")
                            ) {
                                Text("${s.navDocuments} & ${s.navConsent}")
                            }
                        }
                    }
                }
            }

            // Complete Live DB KPI Grid
            item {
                val redAlerts = bundle.cdsReport.alerts.count { it.severity == AlertSeverity.RED }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardKpiCard(
                            title = s.activeConditions,
                            value = LocaleFormatter.formatNumber(bundle.conditions.size, lang),
                            icon = Icons.Filled.HealthAndSafety,
                            accent = InfoBlue,
                            onClick = { onNavigateRoute("records") },
                            tag = "kpi_conditions",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardKpiCard(
                            title = s.medicalHistorySection,
                            value = LocaleFormatter.formatNumber(bundle.medicalHistory.size, lang),
                            icon = Icons.Filled.HistoryEdu,
                            accent = NavyPrimary,
                            onClick = { onNavigateRoute("records") },
                            tag = "kpi_history",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardKpiCard(
                            title = s.currentMedications,
                            value = LocaleFormatter.formatNumber(bundle.medications.size, lang),
                            icon = Icons.Filled.Medication,
                            accent = MedicalTeal,
                            onClick = { onNavigateRoute("medications") },
                            tag = "kpi_medications",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardKpiCard(
                            title = s.knownAllergies,
                            value = LocaleFormatter.formatNumber(bundle.allergies.size, lang),
                            icon = Icons.Filled.Warning,
                            accent = WarningAmber,
                            onClick = { onNavigateRoute("records") },
                            tag = "kpi_allergies",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardKpiCard(
                            title = s.recentLabs,
                            value = LocaleFormatter.formatNumber(bundle.labs.size, lang),
                            icon = Icons.Filled.Biotech,
                            accent = SuccessGreen,
                            onClick = { onNavigateRoute("labs") },
                            tag = "kpi_labs",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardKpiCard(
                            title = s.navDocuments,
                            value = LocaleFormatter.formatNumber(bundle.documents.size, lang),
                            icon = Icons.Filled.Description,
                            accent = InfoBlue,
                            onClick = { onNavigateRoute("documents") },
                            tag = "kpi_documents",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardKpiCard(
                            title = s.carePlansTitle,
                            value = LocaleFormatter.formatNumber(bundle.carePlans.size, lang),
                            icon = Icons.Filled.Assignment,
                            accent = MedicalTeal,
                            onClick = { onNavigateRoute("documents") },
                            tag = "kpi_care_plans",
                            modifier = Modifier.weight(1f)
                        )
                        DashboardKpiCard(
                            title = if (role.isClinician || role.isAdmin) s.patientDirectory else s.openAlerts,
                            value = LocaleFormatter.formatNumber(
                                if (role.isClinician || role.isAdmin) totalPatientsCount else bundle.cdsReport.alerts.size,
                                lang
                            ),
                            icon = if (role.isClinician || role.isAdmin) Icons.Filled.CalendarMonth else Icons.Filled.Shield,
                            accent = if (redAlerts > 0) CriticalRed else WarningAmber,
                            onClick = {
                                onNavigateRoute(if (role.isClinician || role.isAdmin) "patients" else "medications")
                            },
                            tag = "kpi_alerts_or_patients",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Actions
            item {
                Text(s.quickActions, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onNavigateRoute("records") },
                        label = { Text(s.navRecords) },
                        leadingIcon = { Icon(Icons.Filled.HealthAndSafety, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { onNavigateRoute("medications") },
                        label = { Text(s.medicationSafetyTitle) },
                        leadingIcon = { Icon(Icons.Filled.Medication, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { onNavigateRoute("labs") },
                        label = { Text(s.navLabs) },
                        leadingIcon = { Icon(Icons.Filled.Biotech, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { onNavigateRoute("documents") },
                        label = { Text(s.navDocuments) },
                        leadingIcon = { Icon(Icons.Filled.Description, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { onNavigateRoute("ai") },
                        label = { Text(s.navAiAssistant) },
                        leadingIcon = { Icon(Icons.Filled.Psychology, null, Modifier.size(18.dp)) }
                    )
                    if (role.isAdmin) {
                        AssistChip(
                            onClick = { onNavigateRoute("admin") },
                            label = { Text("${s.navAdmin} & ${s.navAuditLogs}") },
                            leadingIcon = { Icon(Icons.Filled.Shield, null, Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            // Longitudinal Timeline Snapshot (Latest Cross-Entity Events)
            item {
                val recentTimeline = buildList {
                    bundle.conditions.forEach { c ->
                        add(DashboardTimelineSnapshot(c.eventDateMillis, if (isFa) c.nameFa else c.nameEn, "${s.conditionsSection} • ${c.authorName}", c.sourceType, c.verificationStatus))
                    }
                    bundle.medicalHistory.forEach { h ->
                        add(DashboardTimelineSnapshot(h.eventDateMillis, if (isFa) h.titleFa else h.titleEn, "${s.medicalHistorySection} • ${h.authorName}", h.sourceType, h.verificationStatus))
                    }
                    bundle.medications.forEach { m ->
                        add(DashboardTimelineSnapshot(m.startDateMillis, "${if (isFa) m.nameFa else m.nameEn} (${m.strength})", "${s.currentMedications} • ${m.prescriberName}", m.sourceType, m.verificationStatus))
                    }
                    bundle.labs.forEach { l ->
                        add(DashboardTimelineSnapshot(l.resultDateMillis, "${if (isFa) l.testNameFa else l.testNameEn}: ${LocaleFormatter.formatNumber(l.value, lang)} ${l.unit}", "${s.recentLabs} • ${l.labName}", l.sourceType, l.verificationStatus))
                    }
                    bundle.documents.forEach { d ->
                        add(DashboardTimelineSnapshot(d.uploadedAtMillis, if (isFa) d.titleFa else d.titleEn, "${s.documentsTitle} • ${d.uploadedBy}", d.sourceType, d.verificationStatus))
                    }
                }.sortedByDescending { it.timestampMillis }.take(3)

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_timeline_preview"),
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
                            Text(s.chronologicalTimeline, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = s.viewAll,
                                style = MaterialTheme.typography.labelLarge,
                                color = MedicalTeal,
                                modifier = Modifier.clickable { onNavigateRoute("records") }
                            )
                        }
                        if (recentTimeline.isEmpty()) {
                            Text(s.noItemsTitle, style = MaterialTheme.typography.bodySmall)
                        } else {
                            recentTimeline.forEach { ev ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = LocaleFormatter.formatDateTime(ev.timestampMillis, lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MedicalTeal
                                        )
                                        Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                        Text(ev.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        SourceAndVerificationRow(ev.sourceType, ev.verificationStatus)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Care Plans & Appointments Preview
            if (bundle.carePlans.isNotEmpty() || bundle.appointments.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_care_plans_preview"),
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
                                Text("${s.carePlansTitle} & ${s.appointmentsTitle}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(
                                    text = s.viewAll,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MedicalTeal,
                                    modifier = Modifier.clickable { onNavigateRoute("documents") }
                                )
                            }
                            bundle.carePlans.take(2).forEach { plan ->
                                val st = CarePlanStatus.fromName(plan.status)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (isFa) plan.titleFa else plan.titleEn,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (isFa) st.labelFa else st.labelEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MedicalTeal,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { plan.progressPct / 100f },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = MedicalTeal
                                        )
                                        SourceAndVerificationRow(plan.sourceType, plan.verificationStatus)
                                    }
                                }
                            }
                            bundle.appointments.firstOrNull()?.let { nextApt ->
                                Text(
                                    text = "${s.appointmentsTitle}: ${nextApt.clinicianName} • ${LocaleFormatter.formatDateTime(nextApt.scheduledAtMillis, lang)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NavyPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Top Safety Alerts Preview
            if (bundle.cdsReport.alerts.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                Text(s.medicationSafetyTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = s.viewAll,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MedicalTeal,
                                    modifier = Modifier.clickable { onNavigateRoute("medications") }
                                )
                            }
                            bundle.cdsReport.alerts.take(2).forEach { alert ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateRoute("medications") }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        SeverityBadge(severity = alert.severity)
                                        Text(
                                            text = if (isFa) alert.titleFa else alert.titleEn,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = if (isFa) alert.explanationFa else alert.explanationEn,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 15 Clinical Intelligence Modules Directory (Phase A vs Coming Soon)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.clinicalModulesTitle, style = MaterialTheme.typography.titleMedium)
                ClinicalModulesCatalog.modules.forEach { mod ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = mod.isPhaseAActive && mod.routeKey.isNotEmpty()) {
                                onNavigateRoute(mod.routeKey)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mod.isPhaseAActive) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = if (mod.isPhaseAActive) LightTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = LocaleFormatter.toLocalizedDigits(mod.number, lang),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mod.isPhaseAActive) NavyPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFa) mod.titleFa else mod.titleEn,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isFa) mod.subtitleFa else mod.subtitleEn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = if (mod.isPhaseAActive) SuccessContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!mod.isPhaseAActive) {
                                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = if (mod.isPhaseAActive) s.activePhaseABadge else s.comingSoonBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mod.isPhaseAActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentStatusBadge(
    label: String,
    granted: Boolean,
    grantedText: String,
    revokedText: String
) {
    Surface(
        color = if (granted) SuccessContainer else CriticalContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "$label: ${if (granted) grantedText else revokedText}",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) SuccessGreen else CriticalRed,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
