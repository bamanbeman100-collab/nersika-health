package com.example.i18n

interface AppStrings {
    // Brand & Safety Banners
    val brandName: String
    val tagline: String
    val productDescription: String
    val demoDataBanner: String
    val clinicalSafetyNotice: String
    val switchLanguageLabel: String

    // Navigation & Modules
    val navDashboard: String
    val navRecords: String
    val navMedications: String
    val navLabs: String
    val navAiAssistant: String
    val navPatients: String
    val navDocuments: String
    val navMore: String
    val navNotifications: String
    val navAuditLogs: String
    val navSettings: String
    val navAdmin: String
    val navCarePlans: String
    val navAppointments: String
    val navConsent: String

    // Auth & Public
    val welcomeTitle: String
    val signIn: String
    val register: String
    val recoverTab: String
    val emailLabel: String
    val passwordLabel: String
    val confirmPasswordLabel: String
    val newPasswordLabel: String
    val firstNameLabel: String
    val lastNameLabel: String
    val countryLabel: String
    val preferredLanguageLabel: String
    val accountTypeLabel: String
    val rememberMe: String
    val forgotPassword: String
    val termsConsentLabel: String
    val verifyEmailTitle: String
    val verifyEmailSubtitle: String
    val verificationCodeLabel: String
    val verifyAccountButton: String
    val resendCodeButton: String
    val resetPasswordTitle: String
    val sendRecoveryEmailButton: String
    val recoveryTokenLabel: String
    val resetPasswordButton: String
    val recoveryStep1Title: String
    val recoveryStep2Title: String
    val publicRegistrationRoleNote: String
    val emailProviderMissingTitle: String
    val emailProviderMissingBody: String
    val seededAccountsReferenceTitle: String
    val seededAccountsReferenceBody: String
    val showPassword: String
    val hidePassword: String
    val logout: String
    val backToLogin: String
    val passwordRuleLength: String
    val passwordRuleUpper: String
    val passwordRuleLower: String
    val passwordRuleDigit: String
    val passwordRuleSymbol: String

    // Role-Based Onboarding
    val onboardingTitle: String
    val onboardingSubtitle: String
    val onboardingPatientStepTitle: String
    val onboardingCaregiverStepTitle: String
    val onboardingClinicianStepTitle: String
    val onboardingDobLabel: String
    val onboardingSexLabel: String
    val onboardingWeightLabel: String
    val onboardingBloodTypeLabel: String
    val onboardingEmergencyLabel: String
    val onboardingCaregiverNote: String
    val onboardingClinicianNote: String
    val onboardingCharterCheckbox: String
    val completeOnboardingButton: String

    // Validation & Auth Errors
    val errInvalidEmail: String
    val errWeakPassword: String
    val errPasswordMismatch: String
    val errRequiredField: String
    val errAcceptTerms: String
    val errInvalidCredentials: String
    val errAccountLocked: String
    val errEmailExists: String
    val errInvalidVerificationCode: String
    val errVerificationExpired: String
    val errTooManyAttempts: String
    val errResendCooldown: String
    val errInvalidResetToken: String
    val errResetTokenExpired: String
    val errEmailProviderUnconfigured: String
    val errEmailDeliveryFailed: String
    val errUnauthorizedRole: String
    val errUnauthorizedRoute: String
    val msgVerificationSent: String
    val msgRecoveryRequested: String
    val msgPasswordResetSuccess: String

    // Roles
    val rolePatient: String
    val roleCaregiver: String
    val rolePhysician: String
    val rolePharmacist: String
    val roleNurse: String
    val roleCoordinator: String
    val roleOrgAdmin: String
    val roleSystemAdmin: String

    // Dashboard & Summaries
    val welcomeBack: String
    val activeConditions: String
    val currentMedications: String
    val knownAllergies: String
    val recentLabs: String
    val upcomingAppointments: String
    val openAlerts: String
    val pendingReviews: String
    val profileCompletion: String
    val quickActions: String
    val clinicalModulesTitle: String
    val comingSoonBadge: String
    val activePhaseABadge: String
    val viewAll: String

    // Patient Record & Provenance
    val patientDirectory: String
    val searchPatientsPlaceholder: String
    val consentRequiredTitle: String
    val consentBlockedMessage: String
    val verifyIdentityAndAccess: String
    val chronologicalTimeline: String
    val conditionsSection: String
    val allergiesSection: String
    val vitalsSection: String
    val addObservation: String
    val addAllergy: String
    val addCondition: String
    val reportMedication: String
    val uploadDocument: String
    val verifiedIdentityBadge: String
    val pregnancyStatusLabel: String
    val populationLabel: String

    // Provenance & Verification
    val sourcePatientReported: String
    val sourceOrganizationSourced: String
    val sourceClinicianVerified: String
    val sourceDocumentExtracted: String
    val sourceAiInferred: String
    val statusVerified: String
    val statusNeedsReview: String
    val statusUnverified: String

    // Medications & Safety
    val medicationHistoryTitle: String
    val medicationReconciliationTitle: String
    val medicationSafetyTitle: String
    val clinicalDecisionSupportTitle: String
    val sevenStageWorkflowTitle: String
    val medStatePrescribed: String
    val medStateDispensed: String
    val medStateAdministered: String
    val medStatePatientReported: String
    val medStateDiscontinued: String
    val medStateUncertain: String
    val reconciliationPrincipleNote: String
    val severityCritical: String
    val severitySignificant: String
    val severityInformational: String
    val actionAccept: String
    val actionModify: String
    val actionDefer: String
    val actionReject: String
    val rationaleLabel: String
    val rationaleRequiredError: String
    val submitDecision: String
    val missingInfoLabel: String
    val evidenceSourceLabel: String
    val suggestedNextStepLabel: String

    // Laboratory Intelligence
    val labHistoryTitle: String
    val labUnitNormalizationNote: String
    val labReferenceNote: String
    val flagNormal: String
    val flagHigh: String
    val flagLow: String
    val flagCritical: String
    val addLabResult: String
    val testNameLabel: String
    val valueLabel: String
    val unitLabel: String

    // Documents, Care Plans, Consent
    val documentsTitle: String
    val selectFromPhotos: String
    val selectDocumentFile: String
    val documentCategoryLabel: String
    val markVerified: String
    val carePlansTitle: String
    val appointmentsTitle: String
    val consentManagementTitle: String
    val consentClinicianLabel: String
    val consentCaregiverLabel: String
    val consentAiLabel: String

    // AI Clinical Assistant
    val aiAssistantTitle: String
    val aiBackendActiveNotice: String
    val aiDisclaimerBanner: String
    val aiRequiresClinicianReviewBadge: String
    val aiClinicianOnlyGateNotice: String
    val aiConsentBlockedBanner: String
    val aiPromptSummarizeRecord: String
    val aiPromptExtractDocument: String
    val aiPromptSummarizeMeds: String
    val aiPromptExplainLabs: String
    val aiPromptMissingData: String
    val aiPromptSourceRetrieval: String
    val aiPromptClinicianRecommendations: String
    val aiPromptOrganizeSymptoms: String
    val aiCustomQuestionHint: String
    val sendButton: String
    val aiRetryButton: String
    val aiDocumentExtractionTitle: String
    val aiPickDocumentImage: String
    val aiSelectStoredDocument: String
    val aiNoDocumentSelected: String
    val aiSectionVerifiedFacts: String
    val aiSectionPatientReported: String
    val aiSectionInterpretation: String
    val aiSectionClinicianRecommendations: String
    val aiSectionExtractedEntities: String
    val aiSectionMissingData: String
    val aiSectionSourcesCited: String
    val aiVerificationRequiredBadge: String
    val aiErrTimeout: String
    val aiErrNetworkOrAppCheck: String
    val aiErrUnsafePrescriptionBlocked: String
    val aiErrFabricatedReferenceBlocked: String
    val aiErrMalformedOrEmpty: String
    val aiErrClinicianRoleRequired: String
    val aiErrConsentDisabled: String

    // Patient Profile, Medical History & Longitudinal Record
    val profileSection: String
    val editProfileButton: String
    val medicalHistorySection: String
    val addMedicalHistory: String
    val editRecordButton: String
    val deleteRecordButton: String
    val confirmDeleteTitle: String
    val confirmDeleteMessage: String
    val verifyEntryButton: String
    val phoneLabel: String
    val mrnLabel: String
    val statusLabel: String
    val notesLabel: String
    val categoryLabel: String
    val timelineFilterAll: String
    val timelineFilterDiagnoses: String
    val timelineFilterHistory: String
    val timelineFilterMedications: String
    val timelineFilterLabs: String
    val timelineFilterDocuments: String
    val timelineFilterCarePlans: String
    val timelineFilterVitals: String
    val addCarePlan: String
    val editCarePlan: String
    val carePlanTitleLabel: String
    val carePlanGoalLabel: String
    val carePlanProgressLabel: String
    val consentOwnerOnlyNotice: String
    val consentGrantedLabel: String
    val consentRevokedLabel: String
    val errClinicalImmutableRecord: String
    val errClinicalConsentDenied: String
    val errClinicalUnverifiedClinician: String
    val errClinicalUnauthorizedRole: String

    // Admin, Notifications, Audit, Settings
    val auditLogsTitle: String
    val userManagementTitle: String
    val securityMonitoringTitle: String
    val darkModeLabel: String
    val mfaToggleLabel: String
    val aboutPlatformTitle: String
    val privacyPolicyTitle: String
    val termsOfServiceTitle: String
    val verifyRoleAction: String
    val noItemsTitle: String
    val cancel: String
    val save: String
    val close: String
    val search: String
}
