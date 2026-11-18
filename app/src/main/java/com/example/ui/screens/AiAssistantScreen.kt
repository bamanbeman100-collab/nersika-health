package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiAccessPolicy
import com.example.data.ai.AiTaskType
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.UserEntity
import com.example.ui.AiUiState
import com.example.ui.PatientBundle
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    user: UserEntity?,
    bundle: PatientBundle?,
    aiState: AiUiState,
    onRunTask: (AiTaskType, String, MedicalDocumentEntity?, Bitmap?) -> Unit,
    onRetryLast: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var customQuery by rememberSaveable { mutableStateOf("") }

    if (bundle == null) {
        EmptyStateCard(title = s.noItemsTitle, subtitle = s.aiAssistantTitle)
        return
    }

    val isVerifiedClinician = AiAccessPolicy.isVerifiedClinician(user)
    val consentGranted = bundle.patient.consentAiSummarization
    var selectedDocId by rememberSaveable(bundle.patient.id) {
        mutableStateOf(bundle.documents.firstOrNull()?.id)
    }
    val selectedDocument = remember(bundle.documents, selectedDocId) {
        bundle.documents.firstOrNull { it.id == selectedDocId } ?: bundle.documents.firstOrNull()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = decodeBitmapFromUri(context, uri)
            if (bitmap != null) {
                onRunTask(
                    AiTaskType.EXTRACT_DOCUMENT,
                    "Extract clinical entities from uploaded document image.",
                    null,
                    bitmap
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ai_assistant_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ai_assistant_avatar),
                        contentDescription = s.aiAssistantTitle,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.aiAssistantTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = s.aiDisclaimerBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }
        }

        item {
            Surface(color = InfoContainer, shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, null, tint = NavyPrimary)
                    Text(
                        text = s.aiBackendActiveNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = NavyPrimary
                    )
                }
            }
        }

        if (!consentGranted) {
            item {
                Surface(
                    color = CriticalContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_consent_blocked_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = CriticalRed)
                        Text(
                            text = s.aiConsentBlockedBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = CriticalRed
                        )
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onRunTask(AiTaskType.SUMMARIZE_RECORD, "", null, null) },
                    label = { Text(s.aiPromptSummarizeRecord) },
                    modifier = Modifier.testTag("ai_chip_summarize")
                )
                AssistChip(
                    onClick = { onRunTask(AiTaskType.EXTRACT_DOCUMENT, "", selectedDocument, null) },
                    label = { Text(s.aiPromptExtractDocument) },
                    modifier = Modifier.testTag("ai_chip_extract_doc")
                )
                AssistChip(
                    onClick = { onRunTask(AiTaskType.EXPLAIN_LABS, "", null, null) },
                    label = { Text(s.aiPromptExplainLabs) },
                    modifier = Modifier.testTag("ai_chip_explain_labs")
                )
                AssistChip(
                    onClick = { onRunTask(AiTaskType.SUMMARIZE_MEDICATIONS, "", null, null) },
                    label = { Text(s.aiPromptSummarizeMeds) },
                    modifier = Modifier.testTag("ai_chip_summarize_meds")
                )
                AssistChip(
                    onClick = { onRunTask(AiTaskType.IDENTIFY_MISSING_DATA, "", null, null) },
                    label = { Text(s.aiPromptMissingData) },
                    modifier = Modifier.testTag("ai_chip_missing_data")
                )
                AssistChip(
                    onClick = { onRunTask(AiTaskType.ORGANIZE_SYMPTOMS, "", null, null) },
                    label = { Text(s.aiPromptOrganizeSymptoms) },
                    modifier = Modifier.testTag("ai_chip_symptoms")
                )
                if (isVerifiedClinician) {
                    AssistChip(
                        onClick = { onRunTask(AiTaskType.CLINICIAN_RECOMMENDATIONS, "", null, null) },
                        leadingIcon = {
                            Icon(Icons.Filled.MedicalServices, null, modifier = Modifier.size(16.dp), tint = MedicalTeal)
                        },
                        label = { Text(s.aiPromptClinicianRecommendations) },
                        modifier = Modifier.testTag("ai_chip_clinician_recs")
                    )
                }
            }
        }

        if (!isVerifiedClinician) {
            item {
                Surface(
                    color = WarningContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_clinician_gate_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                        Text(
                            text = s.aiClinicianOnlyGateNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyPrimary
                        )
                    }
                }
            }
        }

        // Document Extraction Card (Stored Document + Zero-Permission Photo Picker Multimodal)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.DocumentScanner, null, tint = MedicalTeal)
                        Text(s.aiDocumentExtractionTitle, style = MaterialTheme.typography.titleSmall)
                    }
                    if (bundle.documents.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            bundle.documents.forEach { doc ->
                                FilterChip(
                                    selected = selectedDocument?.id == doc.id,
                                    onClick = { selectedDocId = doc.id },
                                    label = { Text(doc.titleEn) }
                                )
                            }
                        }
                    } else {
                        Text(s.aiNoDocumentSelected, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onRunTask(AiTaskType.EXTRACT_DOCUMENT, "", selectedDocument, null)
                            },
                            enabled = selectedDocument != null,
                            modifier = Modifier.weight(1f).testTag("ai_extract_stored_doc_btn")
                        ) {
                            Text(s.aiSelectStoredDocument, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("ai_pick_doc_image_btn")
                        ) {
                            Icon(Icons.Outlined.ImageSearch, null, modifier = Modifier.size(18.dp))
                            Text(
                                text = s.aiPickDocumentImage,
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Source-Aware Clinical Retrieval Prompt
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    label = { Text(s.aiCustomQuestionHint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("ai_query_input")
                )
                Button(
                    onClick = {
                        if (customQuery.isNotBlank()) {
                            onRunTask(AiTaskType.SOURCE_AWARE_RETRIEVAL, customQuery, null, null)
                        }
                    },
                    modifier = Modifier.testTag("ai_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s.sendButton, modifier = Modifier.size(18.dp))
                }
            }
        }

        when (aiState) {
            AiUiState.Idle -> {
                item {
                    EmptyStateCard(title = s.aiAssistantTitle, subtitle = s.aiPromptSummarizeRecord)
                }
            }

            AiUiState.Loading -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MedicalTeal)
                    }
                }
            }

            is AiUiState.Error -> {
                item {
                    Surface(
                        color = CriticalContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, null, tint = CriticalRed)
                                Text(
                                    text = aiState.message,
                                    color = CriticalRed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedButton(
                                onClick = onRetryLast,
                                modifier = Modifier.testTag("ai_retry_btn")
                            ) {
                                Text(s.aiRetryButton)
                            }
                        }
                    }
                }
            }

            is AiUiState.Success -> {
                val res = aiState.response

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ai_response_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(res.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                            Surface(
                                color = WarningContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("ai_review_required_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.Shield, null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = res.clinicianReviewNotice,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NavyPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Verified Clinical Facts
                if (res.verifiedClinicalFacts.isNotEmpty()) {
                    item {
                        Surface(
                            color = SuccessContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("ai_section_verified_facts")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                    Text(s.aiSectionVerifiedFacts, style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
                                }
                                res.verifiedClinicalFacts.forEach { fact ->
                                    Text("• $fact", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // 2. Patient-Reported & Unverified Info
                if (res.patientReportedAndUnverifiedInfo.isNotEmpty()) {
                    item {
                        Surface(
                            color = WarningContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("ai_section_patient_reported")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.ReportProblem, null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                    Text(s.aiSectionPatientReported, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                }
                                res.patientReportedAndUnverifiedInfo.forEach { item ->
                                    Text("• $item", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // 3. Extracted Structured Entities (when available)
                if (res.extractedEntities.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("ai_section_extracted_entities"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(s.aiSectionExtractedEntities, style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                                res.extractedEntities.forEach { entity ->
                                    Surface(color = LightTeal, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "[${entity.category}] ${entity.label}: ${entity.value}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${entity.provenanceNote} • ${s.aiVerificationRequiredBadge}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WarningAmber
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. AI Clinical Interpretation
                item {
                    Surface(
                        color = InfoContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_section_interpretation")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Psychology, null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                                Text(s.aiSectionInterpretation, style = MaterialTheme.typography.labelLarge, color = InfoBlue)
                            }
                            Text(res.aiInterpretation, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // 5. Clinician-Facing Advisory Recommendations (Verified Clinicians Only)
                if (res.clinicianRecommendations.isNotEmpty()) {
                    item {
                        Surface(
                            color = LightTeal,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("ai_section_clinician_recommendations")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.MedicalServices, null, tint = MedicalTeal, modifier = Modifier.size(16.dp))
                                    Text(s.aiSectionClinicianRecommendations, style = MaterialTheme.typography.labelLarge, color = MedicalTeal)
                                }
                                res.clinicianRecommendations.forEach { rec ->
                                    Text("• $rec", style = MaterialTheme.typography.bodySmall, color = NavyPrimary)
                                }
                            }
                        }
                    }
                }

                // 6. Missing or Uncertain Data
                if (res.missingOrUncertainData.isNotEmpty()) {
                    item {
                        Surface(
                            color = WarningContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("ai_section_missing_data")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Outlined.WarningAmber, null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                    Text(s.aiSectionMissingData, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                                res.missingOrUncertainData.forEach { u ->
                                    Text("• $u", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // 7. Verifiable Record Sources Cited
                item {
                    HorizontalDivider()
                    Surface(
                        color = LightTeal,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_section_sources_cited")
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.aiSectionSourcesCited, style = MaterialTheme.typography.labelMedium, color = NavyPrimary, fontWeight = FontWeight.Bold)
                            res.citedSources.forEach { src ->
                                Text(
                                    text = "[${src.sourceId}] ${src.label} (${src.provenance} • ${src.verificationStatus})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    } catch (_: Exception) {
        null
    }
}
