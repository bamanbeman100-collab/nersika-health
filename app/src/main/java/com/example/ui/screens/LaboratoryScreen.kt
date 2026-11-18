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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.local.UserEntity
import com.example.domain.model.LabFlag
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.PatientBundle
import com.example.ui.components.DemoSafetyBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LabRangeBar
import com.example.ui.components.LabTrendChart
import com.example.ui.components.RecordActionFooter
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaboratoryIntelligenceScreen(
    user: UserEntity,
    bundle: PatientBundle?,
    onAddLabResult: (String, Double, String, Double, Double) -> Unit,
    onVerifyLabResult: (String) -> Unit,
    onDeleteLabResult: (String) -> Unit
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var abnormalOnly by rememberSaveable { mutableStateOf(false) }
    var showAddLabDialog by remember { mutableStateOf(false) }

    val labs = remember(bundle.labs, searchQuery, abnormalOnly) {
        bundle.labs.filter { lab ->
            val matchesText = searchQuery.isBlank() ||
                lab.testNameEn.contains(searchQuery, true) ||
                lab.testNameFa.contains(searchQuery, true) ||
                lab.loincCode.contains(searchQuery, true)
            val matchesFlag = !abnormalOnly || LabFlag.fromName(lab.flag) != LabFlag.NORMAL
            matchesText && matchesFlag
        }
    }

    // Group HbA1c or any multi-point series for trend chart
    val trendSeries = remember(bundle.labs) {
        bundle.labs.groupBy { it.testNameEn }.values.maxByOrNull { it.size } ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.labHistoryTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { showAddLabDialog = true }, modifier = Modifier.testTag("add_lab_btn")) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(s.addLabResult)
                }
            }
        }

        item {
            Surface(color = InfoContainer, shape = RoundedCornerShape(10.dp)) {
                Text(
                    text = s.labUnitNormalizationNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = NavyPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (trendSeries.size >= 2) {
            item {
                val seriesTitle = if (isFa) trendSeries.first().testNameFa else trendSeries.first().testNameEn
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$seriesTitle (${trendSeries.first().unit})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LabTrendChart(results = trendSeries)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(s.search) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !abnormalOnly,
                    onClick = { abnormalOnly = false },
                    label = { Text(s.viewAll) }
                )
                FilterChip(
                    selected = abnormalOnly,
                    onClick = { abnormalOnly = !abnormalOnly },
                    label = { Text("${s.flagHigh} / ${s.flagLow}") }
                )
            }
        }

        if (labs.isEmpty()) {
            item { EmptyStateCard(s.noItemsTitle, s.labHistoryTitle) }
        } else {
            items(labs, key = { it.id }) { lab ->
                val flag = LabFlag.fromName(lab.flag)
                val (badgeBg, badgeFg) = when (flag) {
                    LabFlag.NORMAL -> SuccessContainer to SuccessGreen
                    LabFlag.HIGH, LabFlag.LOW -> WarningContainer to WarningAmber
                    LabFlag.CRITICAL -> CriticalContainer to CriticalRed
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFa) lab.testNameFa else lab.testNameEn,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "LOINC ${lab.loincCode} • ${lab.labName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "${LocaleFormatter.formatNumber(lab.value, lang)} ${lab.unit} (${flag.displayName(s)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LabRangeBar(
                            value = lab.value,
                            refLow = lab.referenceLow,
                            refHigh = lab.referenceHigh,
                            flag = flag
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${s.labReferenceNote}: ${LocaleFormatter.formatNumber(lab.referenceLow, lang)}–${LocaleFormatter.formatNumber(lab.referenceHigh, lang)} ${lab.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = LocaleFormatter.formatDate(lab.resultDateMillis, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (lab.originalUnit != lab.unit) {
                            Text(
                                text = "Original: ${LocaleFormatter.formatNumber(lab.originalValue, lang)} ${lab.originalUnit} → Normalized: ${LocaleFormatter.formatNumber(lab.value, lang)} ${lab.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary
                            )
                        }
                        Text(
                            text = if (isFa) lab.referenceNoteFa else lab.referenceNoteEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val canModify = ClinicalAuthorizationPolicy.evaluateModifyOrDeleteEntryAccess(user, patient, lab.sourceType) == null
                        RecordActionFooter(
                            sourceType = lab.sourceType,
                            verificationStatus = lab.verificationStatus,
                            canModify = canModify,
                            canVerify = canVerify,
                            onVerify = { onVerifyLabResult(lab.id) },
                            onDelete = { onDeleteLabResult(lab.id) },
                            testTagPrefix = "lab_${lab.id}"
                        )
                    }
                }
            }
        }
    }

    if (showAddLabDialog) {
        var name by remember { mutableStateOf("") }
        var valStr by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("mg/dL") }
        var lowStr by remember { mutableStateOf("70") }
        var highStr by remember { mutableStateOf("100") }

        AlertDialog(
            onDismissRequest = { showAddLabDialog = false },
            title = { Text(s.addLabResult) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("${s.testNameLabel} *") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = valStr,
                            onValueChange = { valStr = it },
                            label = { Text("${s.valueLabel} *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("${s.unitLabel} *") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lowStr,
                            onValueChange = { lowStr = it },
                            label = { Text("Ref Low") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = highStr,
                            onValueChange = { highStr = it },
                            label = { Text("Ref High") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val v = valStr.toDoubleOrNull()
                    val lo = lowStr.toDoubleOrNull() ?: 0.0
                    val hi = highStr.toDoubleOrNull() ?: 100.0
                    if (name.isNotBlank() && v != null && unit.isNotBlank()) {
                        onAddLabResult(name, v, unit, lo, hi)
                        showAddLabDialog = false
                    }
                }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showAddLabDialog = false }) { Text(s.cancel) } }
        )
    }
}
