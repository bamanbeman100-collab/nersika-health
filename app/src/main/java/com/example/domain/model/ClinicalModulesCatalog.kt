package com.example.domain.model

data class ClinicalModuleSpec(
    val number: String,
    val titleEn: String,
    val titleFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
    val isPhaseAActive: Boolean,
    val routeKey: String
)

object ClinicalModulesCatalog {
    val modules: List<ClinicalModuleSpec> = listOf(
        ClinicalModuleSpec(
            number = "01",
            titleEn = "Longitudinal Health Record",
            titleFa = "پرونده طولی سلامت",
            subtitleEn = "Conditions, timeline, vitals & provenance",
            subtitleFa = "سوابق بیماری، خط زمانی و اصالت داده",
            isPhaseAActive = true,
            routeKey = "records"
        ),
        ClinicalModuleSpec(
            number = "02",
            titleEn = "Medication History Intelligence",
            titleFa = "هوشمندی تاریخچه دارویی",
            subtitleEn = "Prescribed vs dispensed vs intake reconciliation",
            subtitleFa = "تفکیک تجویز، تحویل و مصرف واقعی",
            isPhaseAActive = true,
            routeKey = "medications"
        ),
        ClinicalModuleSpec(
            number = "03",
            titleEn = "Medication Safety",
            titleFa = "ایمنی دارویی",
            subtitleEn = "Allergy, DDI, duplication & renal/pregnancy checks",
            subtitleFa = "بررسی آلرژی، تداخلات و هم‌پوشانی درمانی",
            isPhaseAActive = true,
            routeKey = "medications"
        ),
        ClinicalModuleSpec(
            number = "04",
            titleEn = "Patient-Specific Dose Support",
            titleFa = "پشتیبانی دوز اختصاصی بیمار",
            subtitleEn = "Requires validated rules & regulatory review",
            subtitleFa = "نیازمند قواعد اعتبارسنجی‌شده و تایید رگولاتوری",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "05",
            titleEn = "Antibiotic Stewardship",
            titleFa = "مدیریت مصرف آنتی‌بیوتیک",
            subtitleEn = "Phase B clinical pathway (under validation)",
            subtitleFa = "مسیر بالینی فاز دوم (در حال اعتبارسنجی)",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "06",
            titleEn = "Diagnostic Decision Support",
            titleFa = "پشتیبانی تصمیم تشخیصی",
            subtitleEn = "Disabled until clinical evidence governance completes",
            subtitleFa = "غیرفعال تا تکمیل حاکمیت شواهد بالینی",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "07",
            titleEn = "Laboratory Intelligence",
            titleFa = "هوشمندی آزمایشگاهی",
            subtitleEn = "Normalized units, trends & reference ranges",
            subtitleFa = "هم‌ترازی واحدها، نمودار روند و بازه مرجع",
            isPhaseAActive = true,
            routeKey = "labs"
        ),
        ClinicalModuleSpec(
            number = "08",
            titleEn = "Maternal & Fetal Health",
            titleFa = "سلامت مادر و جنین",
            subtitleEn = "Phase B pregnancy-specific safety pathway",
            subtitleFa = "مسیر ایمنی اختصاصی بارداری در فاز دوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "09",
            titleEn = "Neonatal Health",
            titleFa = "سلامت نوزادان",
            subtitleEn = "Phase B weight/gestational-age pathway",
            subtitleFa = "مسیر تخصصی نوزادان بر اساس سن بارداری",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "10",
            titleEn = "Pediatric Health",
            titleFa = "سلامت کودکان",
            subtitleEn = "Phase B pediatric growth & safety pathway",
            subtitleFa = "مسیر رشد و ایمنی کودکان در فاز دوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "11",
            titleEn = "Geriatric Health",
            titleFa = "سلامت سالمندان",
            subtitleEn = "Phase B polypharmacy & Beers criteria pathway",
            subtitleFa = "مدیریت چنددارویی سالمندان در فاز دوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "12",
            titleEn = "Chronic Disease Management",
            titleFa = "مدیریت بیماری‌های مزمن",
            subtitleEn = "Phase B longitudinal glycemic/BP pathways",
            subtitleFa = "پایش طولی دیابت و پرفشاری خون در فاز دوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "13",
            titleEn = "Remote Care & Follow-up",
            titleFa = "مراقبت از راه دور و پیگیری",
            subtitleEn = "Phase C tele-monitoring workflows",
            subtitleFa = "گردش کار پایش از راه دور در فاز سوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "14",
            titleEn = "Treatment Response Monitoring",
            titleFa = "پایش پاسخ به درمان",
            subtitleEn = "Phase C longitudinal outcome tracking",
            subtitleFa = "پایش پیامدهای درمانی در فاز سوم",
            isPhaseAActive = false,
            routeKey = ""
        ),
        ClinicalModuleSpec(
            number = "15",
            titleEn = "Prevention & Screening",
            titleFa = "پیشگیری و غربالگری",
            subtitleEn = "Phase C population screening schedules",
            subtitleFa = "برنامه غربالگری جمعیتی در فاز سوم",
            isPhaseAActive = false,
            routeKey = ""
        )
    )
}
