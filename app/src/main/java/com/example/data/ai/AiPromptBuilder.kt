package com.example.data.ai

import com.example.domain.model.LabFlag
import com.example.domain.model.MedicationStatus
import com.example.domain.model.SourceType
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage

data class BuiltAiPrompt(
    val systemInstruction: String,
    val userPrompt: String,
    val sourceCatalog: SourceCatalog,
    val deterministicMissingGaps: List<String>
)

object AiPromptBuilder {

    fun build(request: AiClinicalRequest): BuiltAiPrompt {
        val isFa = request.language == AppLanguage.FA
        val catalogEntries = mutableListOf<SourceCatalogEntry>()

        val patient = request.patient
        val patSourceId = "SRC:PATIENT_${patient.id}"
        catalogEntries += SourceCatalogEntry(
            sourceId = patSourceId,
            category = "Patient Profile",
            label = if (isFa) "${patient.firstNameFa} ${patient.lastNameFa} (${patient.mrn})"
            else "${patient.firstNameEn} ${patient.lastNameEn} (${patient.mrn})",
            provenance = if (patient.verifiedIdentity) SourceType.ORGANIZATION_SOURCED.name else SourceType.PATIENT_REPORTED.name,
            verificationStatus = if (patient.verifiedIdentity) VerificationStatus.VERIFIED.name else VerificationStatus.NEEDS_REVIEW.name
        )

        val verifiedLines = mutableListOf<String>()
        val unverifiedLines = mutableListOf<String>()

        verifiedLines += "[$patSourceId] Age=${patient.ageYears}, Sex=${patient.sex}, Population=${patient.populationGroup}, Pregnant=${patient.isPregnant}, WeightKg=${if (patient.weightKg > 0) patient.weightKg else "MISSING"}, BloodType=${patient.bloodType}"

        request.conditions.forEach { c ->
            val sid = "SRC:COND_${c.id}"
            catalogEntries += SourceCatalogEntry(sid, "Condition", "${c.nameEn} (${c.icd10Code})", c.sourceType, c.verificationStatus)
            val line = "[$sid] ${c.nameEn} / ${c.nameFa} | ICD-10=${c.icd10Code} | Status=${c.status} | Source=${c.sourceType} | Verif=${c.verificationStatus} | Notes=${c.notes}"
            if (isVerifiedRecord(c.sourceType, c.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.medicalHistory.forEach { h ->
            val sid = "SRC:HIST_${h.id}"
            catalogEntries += SourceCatalogEntry(sid, "Medical History", "${h.category}: ${h.titleEn}", h.sourceType, h.verificationStatus)
            val line = "[$sid] ${h.category}: ${h.titleEn} / ${h.titleFa} — ${h.detailsEn} | Source=${h.sourceType} | Verif=${h.verificationStatus}"
            if (isVerifiedRecord(h.sourceType, h.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.allergies.forEach { a ->
            val sid = "SRC:ALG_${a.id}"
            catalogEntries += SourceCatalogEntry(sid, "Allergy", a.allergenEn, a.sourceType, a.verificationStatus)
            val line = "[$sid] Allergen=${a.allergenEn}/${a.allergenFa} | Reaction=${a.reactionEn} | Severity=${a.severity} | Source=${a.sourceType} | Verif=${a.verificationStatus}"
            if (isVerifiedRecord(a.sourceType, a.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.medications.forEach { m ->
            val sid = "SRC:MED_${m.id}"
            catalogEntries += SourceCatalogEntry(sid, "Medication", "${m.nameEn} ${m.strength} (${m.status})", m.sourceType, m.verificationStatus)
            val line = "[$sid] ${m.nameEn} (${m.activeIngredient}) ${m.strength} ${m.frequency} | LifecycleStatus=${m.status} | ReconFlag=${m.reconciliationFlag} | Source=${m.sourceType} | Verif=${m.verificationStatus} | Notes=${m.notes}"
            if (isVerifiedRecord(m.sourceType, m.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.labs.forEach { l ->
            val sid = "SRC:LAB_${l.id}"
            catalogEntries += SourceCatalogEntry(sid, "Lab Result", "${l.testNameEn}: ${l.value} ${l.unit}", l.sourceType, l.verificationStatus)
            val line = "[$sid] ${l.testNameEn} (${l.loincCode}) = ${l.value} ${l.unit} (Ref ${l.referenceLow}-${l.referenceHigh}, Flag=${l.flag}) | Note=${l.referenceNoteEn} | Source=${l.sourceType} | Verif=${l.verificationStatus}"
            if (isVerifiedRecord(l.sourceType, l.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.observations.forEach { o ->
            val sid = "SRC:OBS_${o.id}"
            catalogEntries += SourceCatalogEntry(sid, "Observation", "${o.typeEn}: ${o.valueText} ${o.unit}", o.sourceType, o.verificationStatus)
            val line = "[$sid] ${o.typeEn}: ${o.valueText} ${o.unit} | Source=${o.sourceType} | Verif=${o.verificationStatus}"
            if (isVerifiedRecord(o.sourceType, o.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.documents.forEach { d ->
            val sid = "SRC:DOC_${d.id}"
            catalogEntries += SourceCatalogEntry(sid, "Document", "${d.titleEn} (${d.category})", d.sourceType, d.verificationStatus)
            val line = "[$sid] ${d.titleEn} [${d.category}] | ExtractedNote=${d.extractedSummaryEn} | Source=${d.sourceType} | Verif=${d.verificationStatus}"
            if (isVerifiedRecord(d.sourceType, d.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        request.carePlans.forEach { p ->
            val sid = "SRC:PLAN_${p.id}"
            catalogEntries += SourceCatalogEntry(sid, "Care Plan", "${p.titleEn} (${p.progressPct}%)", p.sourceType, p.verificationStatus)
            val line = "[$sid] ${p.titleEn}: ${p.goalEn} | Progress=${p.progressPct}% | Status=${p.status} | Source=${p.sourceType} | Verif=${p.verificationStatus}"
            if (isVerifiedRecord(p.sourceType, p.verificationStatus)) verifiedLines += line else unverifiedLines += line
        }

        val alertLines = request.alerts.map { al ->
            val sid = "SRC:CDS_${al.alertKey}"
            catalogEntries += SourceCatalogEntry(sid, "CDS Alert", "${al.severity}: ${al.titleEn}", SourceType.CLINICIAN_VERIFIED.name, VerificationStatus.VERIFIED.name)
            "[$sid] ${al.severity}: ${al.titleEn} — ${al.explanationEn} | Evidence=${al.evidenceVersion}"
        }

        if (request.documentBitmap != null) {
            catalogEntries += SourceCatalogEntry(
                sourceId = "SRC:UPLOADED_IMAGE",
                category = "Uploaded Image",
                label = if (isFa) "تصویر پیوست‌شده توسط کاربر" else "Attached Clinical Document Image",
                provenance = SourceType.DOCUMENT_EXTRACTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name
            )
        }

        val catalog = SourceCatalog(catalogEntries)
        val deterministicGaps = computeDeterministicMissingGaps(request)

        val allowRecommendations = request.taskType == AiTaskType.CLINICIAN_RECOMMENDATIONS &&
            AiAccessPolicy.isVerifiedClinician(request.actor)

        val targetLang = if (isFa) "Persian (fa-IR)" else "English (en-US)"
        val systemInstruction = """
            You are NERSIKA HEALTH Clinical Documentation Assistant powered by Gemini.
            STRICT SAFETY & GOVERNANCE RULES (MANDATORY):
            1. NEVER act as an autonomous physician. Every output requires authorized clinician review.
            2. NEVER generate prescriptions, medication orders, start/stop/titrate drug instructions, or unverified patient-specific doses (no mg/kg calculations or dosing instructions).
            3. NEVER fabricate medical references, journal articles, PMIDs, DOIs, URLs, or fake record IDs.
            4. ONLY cite exact source IDs present in SOURCE_CATALOG below in `citedSourceIds`. Never invent a source ID.
            5. Strictly separate:
               - `verifiedClinicalFacts`: ONLY items from VERIFIED_CLINICAL_FACTS.
               - `patientReportedAndUnverifiedInfo`: ONLY items from PATIENT_REPORTED_AND_UNVERIFIED_INFO or newly extracted document text awaiting verification.
               - `aiInterpretation`: Your clearly labeled AI synthesis explaining patterns, terminology, or completeness without making autonomous clinical orders.
               - `clinicianRecommendations`: ${if (allowRecommendations) "Advisory discussion/review prompts strictly for the authorized clinician (review monitoring, reconciliation, missing tests). NEVER write a prescription or specific new drug dose." else "MUST be an empty array []."}
            6. Do NOT include any fabricated external citations, 'et al.', 'PMID', 'DOI', or 'http://' links anywhere in your JSON.
            7. Output language for all human-readable fields: $targetLang.
            8. Return ONLY valid JSON matching the enforced responseSchema.
        """.trimIndent()

        val taskDirective = when (request.taskType) {
            AiTaskType.SUMMARIZE_RECORD ->
                "Task: Produce a concise longitudinal medical record summary covering conditions, history, allergies, medications, labs, care plans, and active deterministic safety alerts."
            AiTaskType.EXTRACT_DOCUMENT -> {
                val docFocus = request.selectedDocument?.let {
                    "Selected Document [SRC:DOC_${it.id}]: Title='${it.titleEn}', Category=${it.category}, ExtractedNote='${it.extractedSummaryEn}', Verif=${it.verificationStatus}."
                } ?: "Extract structured clinical entities from the attached document image and any unverified notes."
                "Task: Structured Medical Document Extraction. $docFocus Populate `extractedEntities` (each with category, label, value, provenanceNote) and place unverified extractions into `patientReportedAndUnverifiedInfo`. Never convert extracted text into verified facts without clinician review."
            }
            AiTaskType.EXPLAIN_LABS ->
                "Task: Laboratory Report Explanation. Explain abnormal (${request.labs.count { LabFlag.fromName(it.flag) != LabFlag.NORMAL }}) and normal lab findings, unit-normalized context, and age/sex/pregnancy reference caveats without diagnosing."
            AiTaskType.SUMMARIZE_MEDICATIONS ->
                "Task: Medication History & Reconciliation Summary. Clearly distinguish Prescribed, Dispensed, Administered, Patient-Reported, Discontinued, and Uncertain states. Highlight reconciliation flags. Never prescribe or calculate doses."
            AiTaskType.IDENTIFY_MISSING_DATA ->
                "Task: Missing Information & Conflict Detection. Identify missing baseline clinical parameters, unverified entries, overdue labs, and unresolved reconciliation gaps. Abstain from definitive conclusions where critical data is absent."
            AiTaskType.SOURCE_AWARE_RETRIEVAL ->
                "Task: Source-Aware Clinical Retrieval. Answer the user query strictly using indexed sources in SOURCE_CATALOG. Question: \"${request.customPrompt.trim()}\". If the record does not contain the answer, state that in `missingOrUncertainData`."
            AiTaskType.CLINICIAN_RECOMMENDATIONS ->
                "Task: Clinician-Facing Advisory Recommendation Summary. Provide concise, evidence-grounded advisory review items for an authorized clinician (monitoring priorities, lab follow-ups, reconciliation checks, red-flag awareness). Strictly NO autonomous prescriptions or patient-specific doses."
            AiTaskType.ORGANIZE_SYMPTOMS ->
                "Task: Organize Patient-Reported Symptoms & Vitals chronologically from observations and notes without assigning definitive medical diagnoses."
        }

        val userPrompt = buildString {
            appendLine(taskDirective)
            if (request.customPrompt.isNotBlank() && request.taskType != AiTaskType.SOURCE_AWARE_RETRIEVAL) {
                appendLine("Additional Query: ${request.customPrompt.trim()}")
            }
            appendLine()
            appendLine("SOURCE_CATALOG (allowed IDs for citedSourceIds):")
            catalog.entries.forEach { e ->
                appendLine("- ${e.sourceId} | ${e.category} | ${e.label} | ${e.provenance} | ${e.verificationStatus}")
            }
            appendLine()
            appendLine("VERIFIED_CLINICAL_FACTS:")
            if (verifiedLines.isEmpty()) appendLine("- None currently verified.") else verifiedLines.forEach { appendLine("- $it") }
            appendLine()
            appendLine("PATIENT_REPORTED_AND_UNVERIFIED_INFO:")
            if (unverifiedLines.isEmpty()) appendLine("- None.") else unverifiedLines.forEach { appendLine("- $it") }
            appendLine()
            appendLine("DETERMINISTIC_SAFETY_ALERTS:")
            if (alertLines.isEmpty()) appendLine("- None.") else alertLines.forEach { appendLine("- $it") }
            appendLine()
            appendLine("DETERMINISTIC_MISSING_DATA_HINTS:")
            if (deterministicGaps.isEmpty()) appendLine("- Core baseline parameters present.") else deterministicGaps.forEach { appendLine("- $it") }
        }

        return BuiltAiPrompt(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            sourceCatalog = catalog,
            deterministicMissingGaps = deterministicGaps
        )
    }

    private fun isVerifiedRecord(sourceType: String, verificationStatus: String): Boolean {
        val verif = VerificationStatus.fromName(verificationStatus)
        val src = SourceType.fromName(sourceType)
        return verif == VerificationStatus.VERIFIED &&
            (src == SourceType.CLINICIAN_VERIFIED || src == SourceType.ORGANIZATION_SOURCED)
    }

    fun computeDeterministicMissingGaps(request: AiClinicalRequest): List<String> {
        val isFa = request.language == AppLanguage.FA
        val gaps = mutableListOf<String>()
        val p = request.patient
        if (p.weightKg <= 0.0) {
            gaps += if (isFa) "وزن تاییدشده بیمار (کیلوگرم) ثبت نشده است." else "Verified body weight (kg) is missing."
        }
        if (request.labs.none { it.testNameEn.contains("eGFR", true) || it.testNameEn.contains("Creatinine", true) }) {
            gaps += if (isFa) "نتیجه اخیر عملکرد کلیه (eGFR یا کراتینین) در پرونده موجود نیست." else "Recent renal function marker (eGFR or Creatinine) is missing."
        }
        val reconMeds = request.medications.filter {
            it.reconciliationFlag ||
                MedicationStatus.fromName(it.status) == MedicationStatus.PATIENT_REPORTED ||
                MedicationStatus.fromName(it.status) == MedicationStatus.UNCERTAIN
        }
        if (reconMeds.isNotEmpty()) {
            gaps += if (isFa) {
                "${reconMeds.size} قلم دارویی نیازمند تطبیق دارویی و تایید وضعیت مصرف واقعی است."
            } else {
                "${reconMeds.size} medication entry(s) require reconciliation review to confirm actual intake."
            }
        }
        if (request.allergies.isEmpty()) {
            gaps += if (isFa) "وضعیت غربالگری آلرژی دارویی هنوز ثبت یا تایید نشده است." else "Allergy screening confirmation has not been documented."
        }
        val unverifiedDocs = request.documents.count { VerificationStatus.fromName(it.verificationStatus) != VerificationStatus.VERIFIED }
        if (unverifiedDocs > 0) {
            gaps += if (isFa) "$unverifiedDocs مدرک پزشکی استخراج‌شده هنوز توسط پزشک تایید نشده است." else "$unverifiedDocs extracted medical document(s) still await clinician verification."
        }
        return gaps
    }
}
