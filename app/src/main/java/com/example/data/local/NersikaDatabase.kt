package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LabFlag
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.MedicationStatus
import com.example.domain.model.PopulationGroup
import com.example.domain.model.SourceType
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationStatus
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Database(
    entities = [
        UserEntity::class,
        PatientEntity::class,
        ConditionEntity::class,
        MedicalHistoryEntity::class,
        AllergyEntity::class,
        MedicationEntity::class,
        LabResultEntity::class,
        ObservationEntity::class,
        MedicalDocumentEntity::class,
        CarePlanEntity::class,
        AppointmentEntity::class,
        ClinicalDecisionEntity::class,
        NotificationEntity::class,
        AuditEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NersikaDatabase : RoomDatabase() {
    abstract fun dao(): NersikaDao

    companion object {
        @Volatile
        private var INSTANCE: NersikaDatabase? = null

        fun getInstance(context: Context): NersikaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NersikaDatabase::class.java,
                    "nersika_health_clinical.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object SecurityHasher {
    private const val PBKDF2_ITERATIONS = 100_000
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun generateNumericOtp(digits: Int = 6): String {
        val bound = Math.pow(10.0, digits.toDouble()).toInt()
        val floor = Math.pow(10.0, (digits - 1).toDouble()).toInt()
        val value = floor + random.nextInt(bound - floor)
        return value.toString()
    }

    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean =
        constantTimeEquals(hashPassword(password, salt), expectedHash)
}

object DemoDataSeeder {
    suspend fun seedIfEmpty(dao: NersikaDao) {
        if (dao.getUserByEmail("patient@demo.nersika.health") != null) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        fun makeDemoUser(
            id: String,
            email: String,
            firstName: String,
            lastName: String,
            role: UserRole,
            org: String,
            lang: String,
            linkedPatientId: String? = null
        ): UserEntity {
            val salt = SecurityHasher.generateSalt()
            val hash = SecurityHasher.hashPassword("Demo@2026!", salt)
            return UserEntity(
                id = id,
                email = email,
                passwordHash = hash,
                passwordSalt = salt,
                firstName = firstName,
                lastName = lastName,
                role = role.name,
                country = "Iran",
                preferredLanguage = lang,
                emailVerified = true,
                verificationTokenHash = null,
                verificationExpiresAtMillis = 0L,
                verificationSentAtMillis = 0L,
                verificationAttempts = 0,
                resetTokenHash = null,
                resetExpiresAtMillis = 0L,
                resetAttempts = 0,
                failedLoginAttempts = 0,
                lockedUntilMillis = 0L,
                tokenVersion = 1,
                onboardingCompleted = true,
                mfaEnabled = role.isClinician || role.isAdmin,
                organizationName = org,
                isVerifiedClinician = role.isClinician || role.isAdmin,
                isDemo = true,
                linkedPatientId = linkedPatientId,
                createdAtMillis = now - 30 * day
            )
        }

        val demoUsers = listOf(
            makeDemoUser(
                id = "usr_patient_sara",
                email = "patient@demo.nersika.health",
                firstName = "Sara",
                lastName = "Bahrami",
                role = UserRole.PATIENT,
                org = "Personal Health Account",
                lang = "fa",
                linkedPatientId = "pat_001"
            ),
            makeDemoUser(
                id = "usr_physician_mehr",
                email = "physician@demo.nersika.health",
                firstName = "Dr. Mehrzad",
                lastName = "Tavakoli",
                role = UserRole.PHYSICIAN,
                org = "Tehran Heart & Clinical Center",
                lang = "en"
            ),
            makeDemoUser(
                id = "usr_pharmacist_neda",
                email = "pharmacist@demo.nersika.health",
                firstName = "Dr. Neda",
                lastName = "Shirazi",
                role = UserRole.CLINICAL_PHARMACIST,
                org = "Tehran Heart & Clinical Center",
                lang = "en"
            ),
            makeDemoUser(
                id = "usr_nurse_amir",
                email = "nurse@demo.nersika.health",
                firstName = "Amir",
                lastName = "Moradi",
                role = UserRole.NURSE,
                org = "Tehran Heart & Clinical Center",
                lang = "fa"
            ),
            makeDemoUser(
                id = "usr_admin_leila",
                email = "admin@demo.nersika.health",
                firstName = "Leila",
                lastName = "Farhadi",
                role = UserRole.SYSTEM_ADMIN,
                org = "Nersika Clinical Governance",
                lang = "en"
            )
        )
        demoUsers.forEach { dao.upsertUser(it) }

        val patients = listOf(
            PatientEntity(
                id = "pat_001",
                mrn = "NRK-10482",
                linkedUserId = "usr_patient_sara",
                firstNameEn = "Sara",
                lastNameEn = "Bahrami",
                firstNameFa = "سارا",
                lastNameFa = "بهرامی",
                dateOfBirthIso = "1972-04-12",
                ageYears = 54,
                sex = "Female",
                populationGroup = PopulationGroup.ADULT.name,
                bloodType = "A+",
                phone = "+98 912 000 1042",
                emergencyContact = "Darius Bahrami (+98 912 000 8821)",
                weightKg = 68.5,
                isPregnant = false,
                consentClinicianAccess = true,
                consentCaregiverAccess = true,
                consentAiSummarization = true,
                verifiedIdentity = true,
                profileCompletionPct = 92
            ),
            PatientEntity(
                id = "pat_002",
                mrn = "NRK-20914",
                linkedUserId = null,
                firstNameEn = "Reza",
                lastNameEn = "Karimi",
                firstNameFa = "رضا",
                lastNameFa = "کریمی",
                dateOfBirthIso = "1953-09-23",
                ageYears = 73,
                sex = "Male",
                populationGroup = PopulationGroup.GERIATRIC.name,
                bloodType = "O+",
                phone = "+98 912 000 2091",
                emergencyContact = "Maryam Karimi (+98 912 000 4410)",
                weightKg = 74.0,
                isPregnant = false,
                consentClinicianAccess = true,
                consentCaregiverAccess = true,
                consentAiSummarization = true,
                verifiedIdentity = true,
                profileCompletionPct = 88
            ),
            PatientEntity(
                id = "pat_003",
                mrn = "NRK-30567",
                linkedUserId = null,
                firstNameEn = "Mina",
                lastNameEn = "Rahmani",
                firstNameFa = "مینا",
                lastNameFa = "رحمانی",
                dateOfBirthIso = "1996-02-08",
                ageYears = 30,
                sex = "Female",
                populationGroup = PopulationGroup.PREGNANCY.name,
                bloodType = "B+",
                phone = "+98 912 000 3056",
                emergencyContact = "Omid Rahmani (+98 912 000 7731)",
                weightKg = 0.0, // Intentionally missing to trigger Stage-3 completeness check!
                isPregnant = true,
                consentClinicianAccess = false, // Consent disabled to demonstrate consent gate!
                consentCaregiverAccess = false,
                consentAiSummarization = false,
                verifiedIdentity = true,
                profileCompletionPct = 68
            )
        )
        patients.forEach { dao.upsertPatient(it) }

        // Conditions for pat_001 (Sara Bahrami)
        listOf(
            ConditionEntity(
                id = "cond_1",
                patientId = "pat_001",
                nameEn = "Type 2 Diabetes Mellitus",
                nameFa = "دیابت نوع ۲",
                icd10Code = "E11.9",
                status = "Active",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                eventDateMillis = now - 400 * day,
                entryDateMillis = now - 30 * day,
                notes = "Managed with glycemic monitoring and renal follow-up."
            ),
            ConditionEntity(
                id = "cond_2",
                patientId = "pat_001",
                nameEn = "Atrial Fibrillation (Paroxysmal)",
                nameFa = "فیبریلاسیون دهلیزی حمله‌ای",
                icd10Code = "I48.0",
                status = "Active",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Tehran Heart Center",
                eventDateMillis = now - 180 * day,
                entryDateMillis = now - 25 * day,
                notes = "Anticoagulation therapy active."
            ),
            ConditionEntity(
                id = "cond_3",
                patientId = "pat_002",
                nameEn = "Stage 3 Chronic Kidney Disease & Hypertension",
                nameFa = "بیماری مزمن کلیوی مرحله ۳ و پرفشاری خون",
                icd10Code = "N18.3 / I10",
                status = "Active",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                eventDateMillis = now - 320 * day,
                entryDateMillis = now - 12 * day,
                notes = "Monitor eGFR and serum potassium."
            )
        ).forEach { dao.insertCondition(it) }

        // Medical History (Fictional Demonstration Data)
        listOf(
            MedicalHistoryEntity(
                id = "hist_1",
                patientId = "pat_001",
                category = MedicalHistoryCategory.SURGICAL.name,
                titleEn = "Laparoscopic Appendectomy",
                titleFa = "آپاندکتومی لاپاراسکوپیک",
                detailsEn = "Uncomplicated procedure performed in 2008 (synthetic demo history).",
                detailsFa = "جراحی بدون عارضه در سال ۱۳۸۷ (داده آزمایشی نمایشی).",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Tehran General Surgical Record",
                recordedByUserId = "usr_physician_mehr",
                eventDateMillis = now - 2800 * day,
                entryDateMillis = now - 30 * day
            ),
            MedicalHistoryEntity(
                id = "hist_2",
                patientId = "pat_001",
                category = MedicalHistoryCategory.IMMUNIZATION.name,
                titleEn = "Annual Influenza Vaccination",
                titleFa = "واکسیناسیون فصلی آنفولانزا",
                detailsEn = "Quadrivalent seasonal vaccine recorded prior to winter cycle (demo entry).",
                detailsFa = "تزریق واکسن چهارظرفیتی فصلی ثبت‌شده (داده نمایشی).",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Nurse Amir Moradi",
                recordedByUserId = "usr_nurse_amir",
                eventDateMillis = now - 95 * day,
                entryDateMillis = now - 95 * day
            ),
            MedicalHistoryEntity(
                id = "hist_3",
                patientId = "pat_001",
                category = MedicalHistoryCategory.FAMILY.name,
                titleEn = "Family History: Type 2 Diabetes & CAD",
                titleFa = "سابقه خانوادگی: دیابت نوع ۲ و بیماری عروق کرونر",
                detailsEn = "Reported in maternal parent; flagged for ongoing lipid and glycemic follow-up.",
                detailsFa = "سابقه در خانواده مادری گزارش شده است؛ نیازمند پایش متابولیک.",
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                recordedByUserId = "usr_patient_sara",
                eventDateMillis = now - 45 * day,
                entryDateMillis = now - 45 * day
            ),
            MedicalHistoryEntity(
                id = "hist_4",
                patientId = "pat_002",
                category = MedicalHistoryCategory.HOSPITALIZATION.name,
                titleEn = "Inpatient Hypertensive Observation",
                titleFa = "بستری کوتاه‌مدت جهت کنترل پرفشاری خون",
                detailsEn = "48-hour inpatient stabilization and renal baseline workup (synthetic demo).",
                detailsFa = "پایش ۴۸ ساعته فشار خون و ارزیابی عملکرد کلیه (داده نمایشی).",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Tehran Heart Center",
                recordedByUserId = "usr_physician_mehr",
                eventDateMillis = now - 160 * day,
                entryDateMillis = now - 158 * day
            )
        ).forEach { dao.upsertMedicalHistory(it) }

        // Allergies
        listOf(
            AllergyEntity(
                id = "alg_1",
                patientId = "pat_001",
                allergenEn = "Penicillin",
                allergenFa = "پنی‌سیلین",
                reactionEn = "Generalized urticaria & facial swelling",
                reactionFa = "کهیر منتشر و تورم صورت",
                severity = AlertSeverity.RED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                eventDateMillis = now - 900 * day,
                entryDateMillis = now - 30 * day
            ),
            AllergyEntity(
                id = "alg_2",
                patientId = "pat_001",
                allergenEn = "Sulfonamides (Reported)",
                allergenFa = "سولفونامیدها (گزارش بیمار)",
                reactionEn = "Mild skin rash",
                reactionFa = "بثورات پوستی خفیف",
                severity = AlertSeverity.AMBER.name,
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                eventDateMillis = now - 500 * day,
                entryDateMillis = now - 5 * day
            ),
            AllergyEntity(
                id = "alg_3",
                patientId = "pat_003",
                allergenEn = "Latex",
                allergenFa = "لاتکس",
                reactionEn = "Contact dermatitis",
                reactionFa = "درماتیت تماسی",
                severity = AlertSeverity.AMBER.name,
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Mina Rahmani",
                eventDateMillis = now - 200 * day,
                entryDateMillis = now - 4 * day
            )
        ).forEach { dao.insertAllergy(it) }

        // Medications (distinct lifecycle states + reconciliation & interaction examples)
        listOf(
            MedicationEntity(
                id = "med_1",
                patientId = "pat_001",
                nameEn = "Warfarin Sodium",
                nameFa = "وارفارین سدیم",
                genericName = "Warfarin",
                brandName = "Coumadin",
                activeIngredient = "Warfarin",
                strength = "5 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Once daily",
                status = MedicationStatus.DISPENSED.name,
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                prescriberName = "Dr. Mehrzad Tavakoli",
                reconciliationFlag = false,
                startDateMillis = now - 150 * day,
                notes = "Target INR 2.0–3.0"
            ),
            MedicationEntity(
                id = "med_2",
                patientId = "pat_001",
                nameEn = "Aspirin (OTC Self-Reported)",
                nameFa = "آسپرین (گزارش مصرف بیمار)",
                genericName = "Aspirin",
                brandName = "Aspirin 80",
                activeIngredient = "Aspirin",
                strength = "80 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Once daily",
                status = MedicationStatus.PATIENT_REPORTED.name,
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                prescriberName = "Unverified OTC",
                reconciliationFlag = true,
                startDateMillis = now - 7 * day,
                notes = "Patient reported taking OTC aspirin for headaches."
            ),
            MedicationEntity(
                id = "med_3",
                patientId = "pat_001",
                nameEn = "Metformin HCl",
                nameFa = "متفورمین هیدروکلراید",
                genericName = "Metformin",
                brandName = "Glucophage",
                activeIngredient = "Metformin",
                strength = "500 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Twice daily with meals",
                status = MedicationStatus.PRESCRIBED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                prescriberName = "Dr. Mehrzad Tavakoli",
                reconciliationFlag = false,
                startDateMillis = now - 300 * day,
                notes = "Monitor renal function."
            ),
            MedicationEntity(
                id = "med_4",
                patientId = "pat_001",
                nameEn = "Amoxicillin (Pending Dental Rx)",
                nameFa = "آموکسی‌سیلین (نسخه دندانپزشکی)",
                genericName = "Amoxicillin",
                brandName = "Amoxil",
                activeIngredient = "Amoxicillin",
                strength = "500 mg",
                form = "Capsule",
                route = "Oral",
                frequency = "Every 8 hours",
                status = MedicationStatus.PRESCRIBED.name,
                sourceType = SourceType.DOCUMENT_EXTRACTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                prescriberName = "External Dental Clinic",
                reconciliationFlag = true,
                startDateMillis = now - 1 * day,
                notes = "Extracted from uploaded prescription photo; allergy review required."
            ),
            MedicationEntity(
                id = "med_5",
                patientId = "pat_002",
                nameEn = "Lisinopril",
                nameFa = "لیزینوپریل",
                genericName = "Lisinopril",
                brandName = "Zestril",
                activeIngredient = "Lisinopril",
                strength = "10 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Once daily",
                status = MedicationStatus.DISPENSED.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                prescriberName = "Dr. Mehrzad Tavakoli",
                reconciliationFlag = false,
                startDateMillis = now - 210 * day,
                notes = ""
            ),
            MedicationEntity(
                id = "med_6",
                patientId = "pat_002",
                nameEn = "Spironolactone",
                nameFa = "اسپیرونولاکتون",
                genericName = "Spironolactone",
                brandName = "Aldactone",
                activeIngredient = "Spironolactone",
                strength = "25 mg",
                form = "Tablet",
                route = "Oral",
                frequency = "Once daily",
                status = MedicationStatus.ADMINISTERED.name,
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                prescriberName = "Dr. Mehrzad Tavakoli",
                reconciliationFlag = false,
                startDateMillis = now - 45 * day,
                notes = "Monitor potassium."
            )
        ).forEach { dao.upsertMedication(it) }

        // Lab Results (multi-point series for trend charts + reference ranges)
        listOf(
            LabResultEntity(
                id = "lab_1",
                patientId = "pat_001",
                testNameEn = "HbA1c",
                testNameFa = "هموگلوبین گلیکوزیله (HbA1c)",
                loincCode = "4548-4",
                value = 8.1,
                unit = "%",
                originalValue = 8.1,
                originalUnit = "%",
                referenceLow = 4.0,
                referenceHigh = 6.4,
                referenceNoteEn = "Adult glycemic control target < 7.0%",
                referenceNoteFa = "هدف کنترل قند خون بزرگسالان زیر ۷.۰٪",
                flag = LabFlag.HIGH.name,
                labName = "Masoud Central Lab",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 120 * day,
                resultDateMillis = now - 119 * day
            ),
            LabResultEntity(
                id = "lab_2",
                patientId = "pat_001",
                testNameEn = "HbA1c",
                testNameFa = "هموگلوبین گلیکوزیله (HbA1c)",
                loincCode = "4548-4",
                value = 7.6,
                unit = "%",
                originalValue = 7.6,
                originalUnit = "%",
                referenceLow = 4.0,
                referenceHigh = 6.4,
                referenceNoteEn = "Adult glycemic control target < 7.0%",
                referenceNoteFa = "هدف کنترل قند خون بزرگسالان زیر ۷.۰٪",
                flag = LabFlag.HIGH.name,
                labName = "Masoud Central Lab",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 60 * day,
                resultDateMillis = now - 59 * day
            ),
            LabResultEntity(
                id = "lab_3",
                patientId = "pat_001",
                testNameEn = "HbA1c",
                testNameFa = "هموگلوبین گلیکوزیله (HbA1c)",
                loincCode = "4548-4",
                value = 7.2,
                unit = "%",
                originalValue = 7.2,
                originalUnit = "%",
                referenceLow = 4.0,
                referenceHigh = 6.4,
                referenceNoteEn = "Improving trend; verify dietary adherence",
                referenceNoteFa = "روند رو به بهبود؛ پایش منظم ادامه یابد",
                flag = LabFlag.HIGH.name,
                labName = "Masoud Central Lab",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 5 * day,
                resultDateMillis = now - 4 * day
            ),
            LabResultEntity(
                id = "lab_4",
                patientId = "pat_001",
                testNameEn = "eGFR (CKD-EPI)",
                testNameFa = "نرخ فیلتراسیون گلومرولی (eGFR)",
                loincCode = "62238-1",
                value = 41.0,
                unit = "mL/min/1.73m²",
                originalValue = 41.0,
                originalUnit = "mL/min/1.73m²",
                referenceLow = 60.0,
                referenceHigh = 120.0,
                referenceNoteEn = "Reduced renal filtration; review Metformin dosing",
                referenceNoteFa = "کاهش عملکرد کلیه؛ بازبینی مصرف متفورمین ضروری است",
                flag = LabFlag.LOW.name,
                labName = "Masoud Central Lab",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 5 * day,
                resultDateMillis = now - 4 * day
            ),
            LabResultEntity(
                id = "lab_5",
                patientId = "pat_001",
                testNameEn = "INR (Coagulation)",
                testNameFa = "شاخص انعقادی (INR)",
                loincCode = "6301-6",
                value = 2.6,
                unit = "ratio",
                originalValue = 2.6,
                originalUnit = "ratio",
                referenceLow = 2.0,
                referenceHigh = 3.0,
                referenceNoteEn = "Therapeutic Warfarin target range (AFib)",
                referenceNoteFa = "محدوده درمانی هدف برای وارفارین",
                flag = LabFlag.NORMAL.name,
                labName = "Tehran Heart Lab",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 3 * day,
                resultDateMillis = now - 3 * day
            ),
            LabResultEntity(
                id = "lab_6",
                patientId = "pat_001",
                testNameEn = "Serum Potassium",
                testNameFa = "پتاسیم سرم",
                loincCode = "2823-3",
                value = 4.4,
                unit = "mmol/L",
                originalValue = 4.4,
                originalUnit = "mEq/L",
                referenceLow = 3.5,
                referenceHigh = 5.1,
                referenceNoteEn = "Normalized from mEq/L (1:1 equivalence)",
                referenceNoteFa = "هم‌تراز‌شده از واحد mEq/L",
                flag = LabFlag.NORMAL.name,
                labName = "Masoud Central Lab",
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 5 * day,
                resultDateMillis = now - 4 * day
            ),
            LabResultEntity(
                id = "lab_7",
                patientId = "pat_002",
                testNameEn = "Serum Potassium",
                testNameFa = "پتاسیم سرم",
                loincCode = "2823-3",
                value = 5.5,
                unit = "mmol/L",
                originalValue = 5.5,
                originalUnit = "mmol/L",
                referenceLow = 3.5,
                referenceHigh = 5.1,
                referenceNoteEn = "Elevated potassium on ACEi + Spironolactone",
                referenceNoteFa = "افزایش پتاسیم در مصرف همزمان ACEi و اسپیرونولاکتون",
                flag = LabFlag.HIGH.name,
                labName = "Tehran Heart Lab",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                collectionDateMillis = now - 2 * day,
                resultDateMillis = now - 2 * day
            )
        ).forEach { dao.insertLabResult(it) }

        // Observations / Vitals
        listOf(
            ObservationEntity(
                id = "obs_1",
                patientId = "pat_001",
                typeEn = "Blood Pressure",
                typeFa = "فشار خون",
                valueText = "128/78",
                unit = "mmHg",
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Nurse Amir Moradi",
                recordedAtMillis = now - 2 * day
            ),
            ObservationEntity(
                id = "obs_2",
                patientId = "pat_001",
                typeEn = "Patient Symptom: Mild Dizziness",
                typeFa = "گزارش علامت بیمار: سرگیجه خفیف صبحگاهی",
                valueText = "Reported 2 mornings",
                unit = "Self-report",
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                recordedAtMillis = now - 1 * day
            )
        ).forEach { dao.insertObservation(it) }

        // Medical Documents
        listOf(
            MedicalDocumentEntity(
                id = "doc_1",
                patientId = "pat_001",
                titleEn = "Comprehensive Metabolic & Glycemic Panel",
                titleFa = "گزارش پنل متابولیک و قند خون",
                category = DocumentCategory.LAB_REPORT.name,
                fileUri = "synthetic://lab_panel_bahrami.pdf",
                mimeType = "application/pdf",
                fileSizeKb = 412L,
                sourceType = SourceType.ORGANIZATION_SOURCED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                extractedSummaryEn = "HbA1c 7.2%, eGFR 41 mL/min/1.73m², K+ 4.4 mmol/L",
                extractedSummaryFa = "HbA1c برابر ۷.۲٪، eGFR برابر ۴۱ و پتاسیم ۴.۴",
                uploadedBy = "Masoud Central Lab",
                uploadedAtMillis = now - 4 * day
            ),
            MedicalDocumentEntity(
                id = "doc_2",
                patientId = "pat_001",
                titleEn = "External Dental Clinic Prescription",
                titleFa = "نسخه کلینیک دندانپزشکی",
                category = DocumentCategory.PRESCRIPTION.name,
                fileUri = "synthetic://dental_rx_photo.jpg",
                mimeType = "image/jpeg",
                fileSizeKb = 820L,
                sourceType = SourceType.DOCUMENT_EXTRACTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                extractedSummaryEn = "Document extracted: Amoxicillin 500mg q8h — requires Penicillin allergy verification",
                extractedSummaryFa = "استخراج از مدرک: آموکسی‌سیلین ۵۰۰ — نیازمند بررسی حساسیت پنی‌سیلین",
                uploadedBy = "Sara Bahrami",
                uploadedAtMillis = now - 1 * day
            )
        ).forEach { dao.insertDocument(it) }

        // Care Plans
        listOf(
            CarePlanEntity(
                id = "cp_1",
                patientId = "pat_001",
                titleEn = "Anticoagulation & Renal-Safe Glycemic Care Plan",
                titleFa = "برنامه مراقبت ضدانعقادی و کنترل قند با پایش کلیوی",
                goalEn = "Maintain INR 2.0–3.0, avoid NSAIDs/Penicillins, and recheck eGFR in 4 weeks.",
                goalFa = "حفظ INR در محدوده ۲ تا ۳، پرهیز از NSAID و پنی‌سیلین و تکرار eGFR در ۴ هفته.",
                progressPct = 72,
                status = CarePlanStatus.ACTIVE.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                createdAtMillis = now - 20 * day,
                updatedAtMillis = now - 2 * day
            ),
            CarePlanEntity(
                id = "cp_2",
                patientId = "pat_001",
                titleEn = "Home Blood Pressure & Symptom Monitoring Goal",
                titleFa = "هدف پایش فشار خون و علائم سرگیجه در منزل",
                goalEn = "Log morning BP twice weekly and report orthostatic dizziness prior to follow-up.",
                goalFa = "ثبت فشار خون صبحگاهی هفته‌ای دو بار و گزارش سرگیجه پیش از ویزیت.",
                progressPct = 45,
                status = CarePlanStatus.ACTIVE.name,
                sourceType = SourceType.PATIENT_REPORTED.name,
                verificationStatus = VerificationStatus.NEEDS_REVIEW.name,
                authorName = "Sara Bahrami",
                createdAtMillis = now - 10 * day,
                updatedAtMillis = now - 1 * day
            ),
            CarePlanEntity(
                id = "cp_3",
                patientId = "pat_002",
                titleEn = "CKD Stage 3 Electrolyte & ACEi Safety Monitoring",
                titleFa = "پایش الکترولیت و ایمنی دارویی بیماری کلیوی مرحله ۳",
                goalEn = "Repeat serum potassium and creatinine within 14 days.",
                goalFa = "تکرار آزمایش پتاسیم و کراتینین سرم طی ۱۴ روز آینده.",
                progressPct = 60,
                status = CarePlanStatus.ACTIVE.name,
                sourceType = SourceType.CLINICIAN_VERIFIED.name,
                verificationStatus = VerificationStatus.VERIFIED.name,
                authorName = "Dr. Mehrzad Tavakoli",
                createdAtMillis = now - 14 * day,
                updatedAtMillis = now - 2 * day
            )
        ).forEach { dao.upsertCarePlan(it) }

        // Appointments
        listOf(
            AppointmentEntity(
                id = "apt_1",
                patientId = "pat_001",
                clinicianName = "Dr. Mehrzad Tavakoli",
                specialtyEn = "Cardiology & Internal Medicine",
                specialtyFa = "قلب و داخلی",
                locationEn = "Tehran Heart Clinic • Room 204",
                locationFa = "کلینیک قلب تهران • اتاق ۲۰۴",
                scheduledAtMillis = now + 2 * day,
                status = "Confirmed",
                reasonEn = "Medication reconciliation & INR / eGFR follow-up",
                reasonFa = "تطبیق دارویی و پیگیری آزمایش‌های INR و eGFR"
            ),
            AppointmentEntity(
                id = "apt_2",
                patientId = "pat_001",
                clinicianName = "Dr. Neda Shirazi",
                specialtyEn = "Clinical Pharmacy Consultation",
                specialtyFa = "مشاوره داروسازی بالینی",
                locationEn = "Telehealth / Outpatient Pharmacy",
                locationFa = "مشاوره دارویی سرپایی",
                scheduledAtMillis = now + 5 * day,
                status = "Pending",
                reasonEn = "OTC Aspirin & Penicillin allergy safety review",
                reasonFa = "بازبینی ایمنی آسپرین و حساسیت پنی‌سیلین"
            )
        ).forEach { dao.upsertAppointment(it) }

        // Notifications
        listOf(
            NotificationEntity(
                id = "notif_1",
                patientId = "pat_001",
                category = "Medication Safety",
                titleEn = "Critical Allergy & DDI Review Required",
                titleFa = "نیاز به بررسی فوری حساسیت و تداخل دارویی",
                bodyEn = "Extracted dental prescription (Amoxicillin) conflicts with Penicillin allergy; Warfarin + OTC Aspirin flagged.",
                bodyFa = "نسخه استخراج‌شده آموکسی‌سیلین با حساسیت پنی‌سیلین تداخل دارد؛ تداخل وارفارین و آسپرین ثبت شد.",
                priority = AlertSeverity.RED.name,
                isRead = false,
                createdAtMillis = now - 3_600_000L
            ),
            NotificationEntity(
                id = "notif_2",
                patientId = "pat_001",
                category = "Laboratory",
                titleEn = "New eGFR & HbA1c Results Posted",
                titleFa = "ثبت نتایج جدید آزمایش eGFR و HbA1c",
                bodyEn = "eGFR 41 mL/min/1.73m² flagged below normal range; clinician review scheduled.",
                bodyFa = "مقدار eGFR برابر ۴۱ پایین‌تر از محدوده طبیعی ثبت شد؛ برای بررسی پزشک ارسال گردید.",
                priority = AlertSeverity.AMBER.name,
                isRead = false,
                createdAtMillis = now - 4 * day
            )
        ).forEach { dao.insertNotification(it) }

        // Initial Audit Events
        listOf(
            AuditEventEntity(
                id = "aud_1",
                actorId = "system",
                actorName = "Nersika Clinical Engine",
                actorRole = UserRole.SYSTEM_ADMIN.name,
                actionType = "DEMO_SEED_INITIALIZED",
                targetResource = "patients/pat_001..pat_003",
                targetPatientId = "pat_001",
                outcome = "RECORDED",
                details = "Synthetic demonstration records initialized (non-clinical).",
                timestampMillis = now - 2 * day
            ),
            AuditEventEntity(
                id = "aud_2",
                actorId = "usr_physician_mehr",
                actorName = "Dr. Mehrzad Tavakoli",
                actorRole = UserRole.PHYSICIAN.name,
                actionType = "RECORD_ACCESSED",
                targetResource = "patients/pat_001",
                targetPatientId = "pat_001",
                outcome = "ALLOWED",
                details = "Consent verified; longitudinal health record opened.",
                timestampMillis = now - 1 * day
            )
        ).forEach { dao.insertAuditEvent(it) }
    }
}
