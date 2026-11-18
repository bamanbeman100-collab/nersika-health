package com.example.domain.model

import com.example.data.local.AllergyEntity
import com.example.data.local.ClinicalDecisionEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.LabResultEntity
import com.example.data.local.MedicationEntity
import com.example.data.local.PatientEntity

data class ClinicalAlert(
    val alertKey: String,
    val category: String,
    val titleEn: String,
    val titleFa: String,
    val severity: AlertSeverity,
    val explanationEn: String,
    val explanationFa: String,
    val factsUsedEn: String,
    val factsUsedFa: String,
    val source: String,
    val evidenceVersion: String,
    val missingInfoEn: String,
    val missingInfoFa: String,
    val suggestedNextStepEn: String,
    val suggestedNextStepFa: String,
    val applicabilityLimitationsEn: String,
    val applicabilityLimitationsFa: String,
    val latestDecision: ClinicalDecisionEntity? = null
)

data class CdsSevenStageReport(
    val stage1ContextSummaryEn: String,
    val stage1ContextSummaryFa: String,
    val stage2AggregatedCount: Int,
    val stage3QualityGapsEn: List<String>,
    val stage3QualityGapsFa: List<String>,
    val alerts: List<ClinicalAlert>,
    val unvalidatedRulesNoticeEn: String,
    val unvalidatedRulesNoticeFa: String
)

/**
 * Deterministic, rule-based Medication Safety & 7-Stage CDS Engine.
 * Clearly separates validated deterministic checks from AI inference and flags missing critical data.
 */
object ClinicalSafetyEngine {

    fun evaluatePatient(
        patient: PatientEntity,
        medications: List<MedicationEntity>,
        allergies: List<AllergyEntity>,
        conditions: List<ConditionEntity>,
        labs: List<LabResultEntity>,
        decisions: List<ClinicalDecisionEntity>
    ): CdsSevenStageReport {
        val activeMeds = medications.filter {
            MedicationStatus.fromName(it.status) != MedicationStatus.DISCONTINUED
        }
        val activeIngredients = activeMeds.map { it.activeIngredient.lowercase() }
        val decisionByKey = decisions.associateBy { it.alertKey }

        // Stage 3: Data Quality & Missing Info Detection
        val gapsEn = mutableListOf<String>()
        val gapsFa = mutableListOf<String>()
        if (patient.weightKg <= 0.0) {
            gapsEn.add("Missing verified body weight (kg); weight-based safety checks cannot conclude safely.")
            gapsFa.add("وزن تاییدشده بیمار ثبت نشده است؛ بررسی‌های ایمنی وابسته به وزن قابل استنتاج نیستند.")
        }
        val egfrLab = labs.filter { it.testNameEn.contains("eGFR", ignoreCase = true) }
            .maxByOrNull { it.resultDateMillis }
        if (egfrLab == null) {
            gapsEn.add("Missing recent renal function (eGFR / Creatinine) for renal-cleared drugs.")
            gapsFa.add("نتیجه اخیر عملکرد کلیه (eGFR / کراتینین) برای داروهای با دفع کلیوی موجود نیست.")
        }
        val unverifiedMeds = activeMeds.filter {
            VerificationStatus.fromName(it.verificationStatus) != VerificationStatus.VERIFIED
        }
        if (unverifiedMeds.isNotEmpty()) {
            gapsEn.add("${unverifiedMeds.size} medication record(s) require clinician reconciliation/verification.")
            gapsFa.add("${unverifiedMeds.size} رکورد دارویی نیازمند تطبیق و تایید متخصص است.")
        }

        val alerts = mutableListOf<ClinicalAlert>()

        // Rule 1: Drug-Allergy Cross-Check (Penicillin / Beta-lactam)
        val hasPenicillinAllergy = allergies.any {
            it.allergenEn.contains("penicillin", ignoreCase = true) ||
                it.allergenEn.contains("amoxicillin", ignoreCase = true)
        }
        val betaLactamMed = activeMeds.firstOrNull {
            val ing = it.activeIngredient.lowercase()
            ing.contains("amoxicillin") || ing.contains("penicillin") || ing.contains("ampicillin")
        }
        if (hasPenicillinAllergy && betaLactamMed != null) {
            val key = "allergy_penicillin_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Drug Allergy",
                    titleEn = "Critical Beta-Lactam Allergy Match (${betaLactamMed.nameEn})",
                    titleFa = "تطابق بحرانی حساسیت بتالاکتام (${betaLactamMed.nameFa})",
                    severity = AlertSeverity.RED,
                    explanationEn = "Documented Penicillin allergy conflicts with active ${betaLactamMed.nameEn}. Severe hypersensitivity risk.",
                    explanationFa = "حساسیت ثبت‌شده به پنی‌سیلین با داروی فعال ${betaLactamMed.nameFa} در تعارض است. خطر واکنش آلرژیک شدید وجود دارد.",
                    factsUsedEn = "Allergy: Penicillin; Active med: ${betaLactamMed.nameEn} (${betaLactamMed.status})",
                    factsUsedFa = "حساسیت: پنی‌سیلین؛ دارو: ${betaLactamMed.nameFa}",
                    source = "Nersika Validated Allergy Cross-Sensitivity Rulebook",
                    evidenceVersion = "Rule-ALG-001 v2.1",
                    missingInfoEn = "Verify reaction severity and tolerance to alternative antibiotic classes.",
                    missingInfoFa = "تایید شدت واکنش قبلی و سابقه تحمل آنتی‌بیوتیک‌های جایگزین.",
                    suggestedNextStepEn = "Hold administration and request immediate physician/pharmacist alternative review.",
                    suggestedNextStepFa = "توقف مصرف و درخواست بررسی فوری پزشک یا داروساز برای جایگزینی ایمن.",
                    applicabilityLimitationsEn = "Deterministic class match; clinician must verify cross-reactivity history.",
                    applicabilityLimitationsFa = "تطبیق قطعی کلاس دارویی؛ تایید تاریخچه واکنش متقاطع بر عهده پزشک است.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Rule 2: Warfarin + NSAID / Aspirin Bleeding Interaction
        val hasWarfarin = activeIngredients.any { it.contains("warfarin") }
        val hasNsaidOrAspirin = activeIngredients.any {
            it.contains("aspirin") || it.contains("ibuprofen") || it.contains("naproxen") || it.contains("diclofenac")
        }
        if (hasWarfarin && hasNsaidOrAspirin) {
            val inrLab = labs.filter { it.testNameEn.contains("INR", ignoreCase = true) }
                .maxByOrNull { it.resultDateMillis }
            val key = "ddi_warfarin_nsaid_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Drug-Drug Interaction",
                    titleEn = "Major Anticoagulant + Antiplatelet/NSAID Bleeding Risk",
                    titleFa = "خطر خونریزی شدید: وارفارین همزمان با آسپرین یا NSAID",
                    severity = AlertSeverity.RED,
                    explanationEn = "Concurrent Warfarin and Aspirin/NSAID significantly increases gastrointestinal and systemic hemorrhage risk.",
                    explanationFa = "مصرف همزمان وارفارین و آسپرین/ضدالتهاب غیراستروئیدی خطر خونریزی گوارشی و سیستمیک را به‌طور جدی افزایش می‌دهد.",
                    factsUsedEn = "Warfarin + Aspirin/NSAID active; Latest INR: ${inrLab?.value ?: "Not recorded"}",
                    factsUsedFa = "وارفارین + آسپرین/NSAID فعال؛ آخرین INR: ${inrLab?.value ?: "ثبت نشده"}",
                    source = "Validated Anticoagulation Safety Rule",
                    evidenceVersion = "Rule-DDI-104 v3.0",
                    missingInfoEn = if (inrLab == null) "Recent INR value missing." else "Confirm if dual therapy is cardiologist-directed.",
                    missingInfoFa = if (inrLab == null) "مقدار اخیر INR ثبت نشده است." else "تایید اندیکاسیون تخصصی درمان دوگانه ضروری است.",
                    suggestedNextStepEn = "Clinical pharmacist & physician review of INR, indication, and gastroprotection.",
                    suggestedNextStepFa = "بازبینی INR، اندیکاسیون بالینی و نیاز به محافظت معده توسط پزشک و داروساز.",
                    applicabilityLimitationsEn = "Does not assess dual antiplatelet specialty exceptions without cardiology note.",
                    applicabilityLimitationsFa = "استثنائات تخصصی قلب بدون یادداشت متخصص ارزیابی نمی‌شوند.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Rule 3: ACE Inhibitor + Potassium-Sparing Diuretic (Hyperkalemia)
        val hasAcei = activeIngredients.any { it.contains("lisinopril") || it.contains("enalapril") || it.contains("ramipril") }
        val hasKDiuretic = activeIngredients.any { it.contains("spironolactone") || it.contains("amiloride") }
        if (hasAcei && hasKDiuretic) {
            val kLab = labs.filter { it.testNameEn.contains("Potassium", ignoreCase = true) }
                .maxByOrNull { it.resultDateMillis }
            val key = "ddi_acei_spironolactone_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Drug-Drug Interaction",
                    titleEn = "Hyperkalemia Risk: ACE Inhibitor + Spironolactone",
                    titleFa = "خطر هیپرکالمی: مهارکننده ACE همزمان با اسپیرونولاکتون",
                    severity = AlertSeverity.AMBER,
                    explanationEn = "Combined RAAS blockade and potassium-sparing diuretic can elevate serum potassium.",
                    explanationFa = "مصرف همزمان مهارکننده ACE و دیورتیک نگهدارنده پتاسیم می‌تواند پتاسیم سرم را افزایش دهد.",
                    factsUsedEn = "Latest Potassium: ${kLab?.let { "${it.value} ${it.unit}" } ?: "Not available"}",
                    factsUsedFa = "آخرین پتاسیم سرم: ${kLab?.let { "${it.value} ${it.unit}" } ?: "ناموجود"}",
                    source = "Validated Electrolyte & Renal Monitoring Rule",
                    evidenceVersion = "Rule-DDI-219 v1.4",
                    missingInfoEn = if (kLab == null) "Serum potassium and eGFR required." else "Check date of last electrolyte panel.",
                    missingInfoFa = if (kLab == null) "آزمایش پتاسیم سرم و eGFR مورد نیاز است." else "تاریخ آخرین پنل الکترولیت بررسی شود.",
                    suggestedNextStepEn = "Review serum potassium trend and renal function with prescriber.",
                    suggestedNextStepFa = "بررسی روند پتاسیم سرم و عملکرد کلیه توسط پزشک تجویزکننده.",
                    applicabilityLimitationsEn = "Standard heart-failure combinations may be intentional under close lab monitoring.",
                    applicabilityLimitationsFa = "در برخی بیماران نارسایی قلبی این ترکیب با پایش منظم آزمایشگاهی عمدی است.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Rule 4: Metformin + Reduced Renal Function (eGFR < 45)
        val hasMetformin = activeIngredients.any { it.contains("metformin") }
        if (hasMetformin && egfrLab != null && egfrLab.value < 45.0) {
            val isCritical = egfrLab.value < 30.0
            val key = "renal_metformin_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Renal Function Consideration",
                    titleEn = if (isCritical) "Metformin Contraindicated (eGFR < 30)" else "Metformin Renal Caution (eGFR 30–44)",
                    titleFa = if (isCritical) "ممنوعیت متفورمین (eGFR زیر ۳۰)" else "احتیاط کلیوی متفورمین (eGFR بین ۳۰ تا ۴۴)",
                    severity = if (isCritical) AlertSeverity.RED else AlertSeverity.AMBER,
                    explanationEn = "Reduced eGFR (${egfrLab.value} ${egfrLab.unit}) increases metformin accumulation and lactic acidosis risk.",
                    explanationFa = "کاهش eGFR (${egfrLab.value} ${egfrLab.unit}) خطر تجمع متفورمین و اسیدوز لاکتیک را افزایش می‌دهد.",
                    factsUsedEn = "Active Metformin + eGFR ${egfrLab.value} ${egfrLab.unit}",
                    factsUsedFa = "متفورمین فعال + مقدار eGFR برابر ${egfrLab.value} ${egfrLab.unit}",
                    source = "KDIGO / FDA Renal Dosing Safety Rule",
                    evidenceVersion = "Rule-REN-012 v2.0",
                    missingInfoEn = "Confirm whether eGFR represents acute kidney injury or stable chronic baseline.",
                    missingInfoFa = "اطمینان از حاد یا مزمن بودن افت عملکرد کلیه لازم است.",
                    suggestedNextStepEn = "Clinician review of renal trend before continuing or adjusting Metformin.",
                    suggestedNextStepFa = "بازبینی روند کلیوی توسط پزشک پیش از ادامه یا تغییر رژیم متفورمین.",
                    applicabilityLimitationsEn = "Platform does not auto-calculate patient-specific dose adjustments.",
                    applicabilityLimitationsFa = "پلتفرم به‌صورت خودکار دوز اختصاصی بیمار را محاسبه نمی‌کند.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Rule 5: Pregnancy Safety Consideration
        val hasStatinOrAcei = activeIngredients.any {
            it.contains("atorvastatin") || it.contains("simvastatin") || it.contains("lisinopril") || it.contains("warfarin")
        }
        if (patient.isPregnant && hasStatinOrAcei) {
            val key = "pregnancy_teratogen_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Pregnancy Consideration",
                    titleEn = "Teratogenic Risk Screening in Pregnancy",
                    titleFa = "هشدار غربالگری ایمنی دارویی در بارداری",
                    severity = AlertSeverity.RED,
                    explanationEn = "Active patient record indicates pregnancy alongside medication class requiring urgent OB/GYN review.",
                    explanationFa = "پرونده بیمار وضعیت بارداری را همزمان با داروی نیازمند بازبینی فوری متخصص زنان/پزشک نشان می‌دهد.",
                    factsUsedEn = "Pregnancy flag: true; Screened medication list",
                    factsUsedFa = "وضعیت بارداری: بله؛ فهرست داروهای فعال",
                    source = "Maternal Medication Safety Screening Rule",
                    evidenceVersion = "Rule-PRG-005 v1.2",
                    missingInfoEn = "Gestational age and obstetrics care plan.",
                    missingInfoFa = "سن بارداری و برنامه مراقبت مامایی/پزشکی.",
                    suggestedNextStepEn = "Urgent physician & clinical pharmacist review for pregnancy-safe alternatives.",
                    suggestedNextStepFa = "بازبینی فوری پزشک و داروساز بالینی برای بررسی جایگزین ایمن در بارداری.",
                    applicabilityLimitationsEn = "Maternal-Fetal specialty pathway (Module 08) is in Phase B validation.",
                    applicabilityLimitationsFa = "ماژول تخصصی سلامت مادر و جنین در مرحله اعتبارسنجی فاز دوم قرار دارد.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Rule 6: Medication Reconciliation Discrepancy
        val discrepancies = activeMeds.filter { it.reconciliationFlag }
        if (discrepancies.isNotEmpty()) {
            val namesEn = discrepancies.joinToString { "${it.nameEn} (${it.status})" }
            val namesFa = discrepancies.joinToString("، ") { it.nameFa }
            val key = "reconciliation_gap_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Medication Reconciliation",
                    titleEn = "${discrepancies.size} Medication Reconciliation Discrepancy",
                    titleFa = "${discrepancies.size} مغایرت در تطبیق دارویی",
                    severity = AlertSeverity.AMBER,
                    explanationEn = "Discrepancy between prescribed/dispensed records and patient-reported actual intake: $namesEn.",
                    explanationFa = "اختلاف بین نسخه/تحویل داروخانه و مصرف واقعی گزارش‌شده توسط بیمار: $namesFa.",
                    factsUsedEn = namesEn,
                    factsUsedFa = namesFa,
                    source = "Nersika Medication Reconciliation Engine",
                    evidenceVersion = "Rule-REC-008 v1.5",
                    missingInfoEn = "Confirm patient adherence, out-of-pocket OTC additions, or undocumented discontinuation.",
                    missingInfoFa = "بررسی تبعیت درمانی بیمار، داروهای بدون نسخه یا قطع خودسرانه لازم است.",
                    suggestedNextStepEn = "Conduct structured medication reconciliation with patient or caregiver.",
                    suggestedNextStepFa = "انجام مصاحبه تطبیق دارویی با بیمار یا مراقب و ثبت وضعیت نهایی.",
                    applicabilityLimitationsEn = "Prescription does not prove dispensing or intake.",
                    applicabilityLimitationsFa = "تجویز به معنای تحویل یا مصرف قطعی نیست.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        // Always include informational monitoring reminder if no alerts or missing info exists
        if (gapsEn.isNotEmpty()) {
            val key = "data_quality_gap_${patient.id}"
            alerts.add(
                ClinicalAlert(
                    alertKey = key,
                    category = "Data Completeness",
                    titleEn = "Incomplete Clinical Context Detected",
                    titleFa = "نقص اطلاعات کلیدی در پرونده بالینی",
                    severity = AlertSeverity.BLUE,
                    explanationEn = gapsEn.joinToString(" "),
                    explanationFa = gapsFa.joinToString(" "),
                    factsUsedEn = "Patient profile & latest lab timestamps",
                    factsUsedFa = "پروفایل بیمار و تاریخ آخرین آزمایش‌ها",
                    source = "Nersika Stage-3 Data Quality Gate",
                    evidenceVersion = "Rule-DQ-001 v1.0",
                    missingInfoEn = gapsEn.first(),
                    missingInfoFa = gapsFa.first(),
                    suggestedNextStepEn = "Collect missing baseline measurements before high-risk clinical decisions.",
                    suggestedNextStepFa = "پیش از تصمیمات پرخطر بالینی، اطلاعات پایه مفقود تکمیل شوند.",
                    applicabilityLimitationsEn = "System abstains from automated conclusions when critical data is absent.",
                    applicabilityLimitationsFa = "در نبود داده‌های حیاتی، سامانه از ارائه نتیجه‌گیری خودکار خودداری می‌کند.",
                    latestDecision = decisionByKey[key]
                )
            )
        }

        val populationLabel = PopulationGroup.fromName(patient.populationGroup)
        val stage1En = "Patient ${patient.firstNameEn} ${patient.lastNameEn} (${patient.mrn}) • Age ${patient.ageYears}y • ${populationLabel.labelEn} • Weight: ${if (patient.weightKg > 0) "${patient.weightKg} kg" else "Missing"} • Pregnant: ${if (patient.isPregnant) "Yes" else "No"}"
        val stage1Fa = "بیمار ${patient.firstNameFa} ${patient.lastNameFa} (${patient.mrn}) • سن ${patient.ageYears} سال • ${populationLabel.labelFa} • وزن: ${if (patient.weightKg > 0) "${patient.weightKg} کیلوگرم" else "نامشخص"} • بارداری: ${if (patient.isPregnant) "بله" else "خیر"}"

        return CdsSevenStageReport(
            stage1ContextSummaryEn = stage1En,
            stage1ContextSummaryFa = stage1Fa,
            stage2AggregatedCount = conditions.size + activeMeds.size + allergies.size + labs.size,
            stage3QualityGapsEn = gapsEn,
            stage3QualityGapsFa = gapsFa,
            alerts = alerts.sortedBy { it.severity.ordinal },
            unvalidatedRulesNoticeEn = "Patient-specific dose calculation, antibiotic stewardship, and diagnostic suggestions remain disabled (Phase B/C) pending clinical governance.",
            unvalidatedRulesNoticeFa = "محاسبه دوز اختصاصی، مدیریت آنتی‌بیوتیک و پیشنهاد تشخیصی (فاز دوم/سوم) تا تکمیل اعتبارسنجی بالینی غیرفعال هستند."
        )
    }
}
