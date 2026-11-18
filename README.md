# NERSIKA HEALTH — Android Clinical Decision & Medication Safety Platform

**Tagline:** Every Patient. One Intelligent Health Record.  
**شعار فارسی:** هر بیمار، یک پرونده سلامت هوشمند و یکپارچه

> **Clinical Safety Rule:** NERSIKA HEALTH IS NOT AN AUTONOMOUS DOCTOR. AI recommendations are NOT medical orders. All high-risk clinical decisions must be reviewed and approved by an authorized healthcare professional.

---

## Architecture & Implementation Status

### Implemented & Tested (Phase A MVP + Production-Oriented Email Auth)
- **Production-Oriented Email Authentication (`data/auth/`):** Real PBKDF2-HMAC-SHA256 (`100,000` iterations, constant-time verification) login & registration, brute-force account lockout, SHA-256 hashed 6-digit email verification & password recovery tokens with 15-minute expiry and attempt limits, DataStore cryptographic session token persistence (`rememberMe` & `tokenVersion` revocation), role-based onboarding (`RoleOnboardingScreen`), secure logout, and RBAC `RouteAccessPolicy` enforcement across `NavHost` routes.
- **Zero Simulated Authentication:** Passwordless quick-login bypasses and plaintext verification code leaks are disabled. When no live email dispatch backend is configured, `UnconfiguredEmailDispatchGateway` surfaces `EMAIL_PROVIDER_NOT_CONFIGURED` honestly without auto-verifying accounts.
- **Bilingual Experience (`fa-IR` RTL & `en-US` LTR):** Full runtime switching across all screens using bundled `Vazirmatn`, `Plus Jakarta Sans`, and `JetBrains Mono` fonts, Persian numeral conversion, and Gregorian/Jalali calendar formatting (`LocaleFormatter`).
- **Local Room Clinical Database (`NersikaDatabase` v2):** SQLite persistence for Users (with security/lockout/onboarding fields), Patients, Conditions, Allergies, Medications, Laboratory Results, Observations, Medical Documents, Care Plans, Appointments, Clinical Decisions, Notifications, and Immutable Audit Events.
- **Role-Based & Consent-Based Access Control:** Supports Patient, Caregiver, Physician, Clinical Pharmacist, Nurse, Healthcare Coordinator, Organization Admin, and System Admin dashboards. Public registration strictly limits self-signup to Patient and Caregiver roles. Clinician access enforces active patient consent (`ConsentGate`) and logs `ALLOWED` / `DENIED` attempts.
- **Medication History Intelligence & Reconciliation:** Tracks distinct `PRESCRIBED`, `DISPENSED`, `ADMINISTERED`, `PATIENT_REPORTED`, `DISCONTINUED`, and `UNCERTAIN` states with side-by-side discrepancy review.
- **Deterministic Medication Safety & 7-Stage CDS Engine (`ClinicalSafetyEngine`):** Evaluates drug-allergy cross-checks, validated drug-drug interactions, renal function alerts, pregnancy warnings, reconciliation discrepancies, and missing baseline data. Clinicians record `ACCEPT`, `MODIFY`, `DEFER`, or `REJECT` decisions with mandatory rationale.
- **Laboratory Intelligence:** Custom Compose Canvas `LabRangeBar` indicators, multi-point `LabTrendChart`, unit normalization display, and population-specific reference range notes.
- **Zero-Permission Document Upload:** Uses Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`OpenDocument`).
- **15 Clinical Modules Directory:** Phase A modules active; Phase B/C specialty modules visibly labeled **Coming Soon / به‌زودی**.

### Remaining External Configuration Requirements
- **Live Email Delivery (`EmailDispatchGateway`):** Configure an SMTP/transactional email backend or enable **Email/Password** authentication manually in the Firebase Console (AI Studio automated Firebase provisioning supports Google Sign-In only and was declined in this workspace). Until connected, live verification and password-reset emails cannot be delivered to external inboxes.
- **Cloud Sync & Live Gemini AI (`AiRepository`):** `AiRepository` currently uses `PlaceholderAiRepository` returning clearly labeled offline sample clinical summaries. Connecting live Firestore sync and Gemini via Firebase AI Logic requires provisioning Firebase.
- **Synthetic Demo Data:** Pre-seeded clinical records carry the mandatory `DEMO DATA — NOT FOR CLINICAL USE` / `داده آزمایشی — غیرقابل استفاده برای تصمیم‌گیری بالینی` banner; pre-seeded accounts require real email/password authentication.

---

## Running Tests & Verification
- Unit & Robolectric tests: `gradle :app:testDebugUnitTest`
- Debug APK build: `gradle :app:assembleDebug`
