package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAccessDeniedException
import com.example.data.ai.AiClinicalRequest
import com.example.data.ai.AiClinicalResponse
import com.example.data.ai.AiFailureReason
import com.example.data.ai.AiInferenceException
import com.example.data.ai.AiRepository
import com.example.data.ai.AiTaskType
import com.example.data.ai.AiValidationException
import com.example.data.ai.GeminiAiRepository
import com.example.data.auth.AppRoute
import kotlinx.coroutines.CancellationException
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.auth.LoginOutcome
import com.example.data.auth.OnboardingSubmission
import com.example.data.auth.RecoveryRequestOutcome
import com.example.data.auth.RegistrationOutcome
import com.example.data.auth.RegistrationRequest
import com.example.data.auth.RouteGuardResult
import com.example.data.auth.UnconfiguredEmailDispatchGateway
import com.example.data.clinical.ClinicalAccessError
import com.example.data.clinical.ClinicalAuthorizationPolicy
import com.example.data.clinical.ClinicalOpResult
import com.example.data.clinical.ClinicalRecordRepository
import com.example.data.clinical.PatientProfileUpdateRequest
import com.example.data.local.AllergyEntity
import com.example.data.local.AppointmentEntity
import com.example.data.local.AuditEventEntity
import com.example.data.local.CarePlanEntity
import com.example.data.local.ClinicalDecisionEntity
import com.example.data.local.ConditionEntity
import com.example.data.local.DemoDataSeeder
import com.example.data.local.LabResultEntity
import com.example.data.local.MedicalDocumentEntity
import com.example.data.local.MedicalHistoryEntity
import com.example.data.local.MedicationEntity
import com.example.data.local.NersikaDatabase
import com.example.data.local.ObservationEntity
import com.example.data.local.PatientEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserPreferencesRepository
import com.example.domain.model.AlertSeverity
import com.example.domain.model.CarePlanStatus
import com.example.domain.model.CdsSevenStageReport
import com.example.domain.model.ClinicalDecisionAction
import com.example.domain.model.ClinicalSafetyEngine
import com.example.domain.model.DocumentCategory
import com.example.domain.model.MedicalHistoryCategory
import com.example.domain.model.MedicationStatus
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val response: AiClinicalResponse) : AiUiState
    data class Error(val message: String, val reason: AiFailureReason? = null) : AiUiState
}

data class PatientBundle(
    val patient: PatientEntity,
    val conditions: List<ConditionEntity>,
    val medicalHistory: List<MedicalHistoryEntity>,
    val allergies: List<AllergyEntity>,
    val medications: List<MedicationEntity>,
    val labs: List<LabResultEntity>,
    val observations: List<ObservationEntity>,
    val documents: List<MedicalDocumentEntity>,
    val carePlans: List<CarePlanEntity>,
    val appointments: List<AppointmentEntity>,
    val decisions: List<ClinicalDecisionEntity>,
    val patientAuditEvents: List<AuditEventEntity>,
    val cdsReport: CdsSevenStageReport
)

@OptIn(ExperimentalCoroutinesApi::class)
class NersikaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NersikaDatabase.getInstance(application)
    private val dao = db.dao()
    private val prefs = UserPreferencesRepository(application)
    private val authRepository = AuthRepository(
        dao = dao,
        prefs = prefs,
        emailGateway = UnconfiguredEmailDispatchGateway()
    )
    private val clinicalRepository = ClinicalRecordRepository(dao)
    private var aiRepository: AiRepository = GeminiAiRepository()
    private var lastAiRequest: AiClinicalRequest? = null

    val isLiveEmailConfigured: Boolean
        get() = authRepository.isLiveEmailConfigured

    val language: StateFlow<AppLanguage> = prefs.languageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.EN)

    val darkMode: StateFlow<Boolean> = prefs.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUser: StateFlow<UserEntity?> = authRepository.observeAuthenticatedUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allUsers: StateFlow<List<UserEntity>> = dao.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatients: StateFlow<List<PatientEntity>> = dao.observeAllPatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications = dao.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditEvents = dao.observeAuditEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPatientId = MutableStateFlow("pat_001")
    val selectedPatientId: StateFlow<String> = _selectedPatientId.asStateFlow()

    private val _consentDeniedAlert = MutableStateFlow<String?>(null)
    val consentDeniedAlert: StateFlow<String?> = _consentDeniedAlert.asStateFlow()

    private val _clinicalOpError = MutableStateFlow<ClinicalAccessError?>(null)
    val clinicalOpError: StateFlow<ClinicalAccessError?> = _clinicalOpError.asStateFlow()

    private val _authBanner = MutableStateFlow<String?>(null)
    val authBanner: StateFlow<String?> = _authBanner.asStateFlow()

    private val _unauthorizedRouteError = MutableStateFlow<AuthErrorCode?>(null)
    val unauthorizedRouteError: StateFlow<AuthErrorCode?> = _unauthorizedRouteError.asStateFlow()

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val _isSeeded = MutableStateFlow(false)
    val isSeeded: StateFlow<Boolean> = _isSeeded.asStateFlow()

    init {
        viewModelScope.launch {
            DemoDataSeeder.seedIfEmpty(dao)
            _isSeeded.value = true
        }
        // Automatically bind patient account to its linked patient ID when signed in
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val role = UserRole.fromName(user.role)
                    if (role == UserRole.PATIENT && !user.linkedPatientId.isNullOrBlank()) {
                        _selectedPatientId.value = user.linkedPatientId
                    }
                }
            }
        }
    }

    val activePatientBundle: StateFlow<PatientBundle?> = combine(_selectedPatientId, currentUser) { pid, user ->
        pid to user
    }.flatMapLatest { (patientId, user) ->
        if (patientId.isBlank() || user == null) {
            flowOf(null)
        } else {
            val baseFlow: Flow<List<Any?>> = combine(
                dao.observePatientById(patientId),
                dao.observeConditions(patientId),
                dao.observeMedicalHistory(patientId),
                dao.observeAllergies(patientId),
                dao.observeMedications(patientId),
                dao.observeLabResults(patientId)
            ) { arr -> arr.toList() }

            val extraFlow: Flow<List<Any?>> = combine(
                dao.observeObservations(patientId),
                dao.observeDocuments(patientId),
                dao.observeCarePlans(patientId),
                dao.observeAppointments(patientId),
                dao.observeDecisions(patientId),
                dao.observeAuditEventsForPatient(patientId)
            ) { arr -> arr.toList() }

            combine(baseFlow, extraFlow) { b, e ->
                val patient = b[0] as? PatientEntity ?: return@combine null
                if (ClinicalAuthorizationPolicy.evaluateReadAccess(user, patient) != null) {
                    return@combine null
                }
                @Suppress("UNCHECKED_CAST")
                val conditions = b[1] as List<ConditionEntity>
                @Suppress("UNCHECKED_CAST")
                val medicalHistory = b[2] as List<MedicalHistoryEntity>
                @Suppress("UNCHECKED_CAST")
                val allergies = b[3] as List<AllergyEntity>
                @Suppress("UNCHECKED_CAST")
                val medications = b[4] as List<MedicationEntity>
                @Suppress("UNCHECKED_CAST")
                val labs = b[5] as List<LabResultEntity>
                @Suppress("UNCHECKED_CAST")
                val observations = e[0] as List<ObservationEntity>
                @Suppress("UNCHECKED_CAST")
                val documents = e[1] as List<MedicalDocumentEntity>
                @Suppress("UNCHECKED_CAST")
                val carePlans = e[2] as List<CarePlanEntity>
                @Suppress("UNCHECKED_CAST")
                val appointments = e[3] as List<AppointmentEntity>
                @Suppress("UNCHECKED_CAST")
                val decisions = e[4] as List<ClinicalDecisionEntity>
                @Suppress("UNCHECKED_CAST")
                val patientAuditEvents = e[5] as List<AuditEventEntity>

                val cds = ClinicalSafetyEngine.evaluatePatient(
                    patient = patient,
                    medications = medications,
                    allergies = allergies,
                    conditions = conditions,
                    labs = labs,
                    decisions = decisions
                )

                PatientBundle(
                    patient = patient,
                    conditions = conditions,
                    medicalHistory = medicalHistory,
                    allergies = allergies,
                    medications = medications,
                    labs = labs,
                    observations = observations,
                    documents = documents,
                    carePlans = carePlans,
                    appointments = appointments,
                    decisions = decisions,
                    patientAuditEvents = patientAuditEvents,
                    cdsReport = cds
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (language.value == AppLanguage.EN) AppLanguage.FA else AppLanguage.EN
            prefs.setLanguage(next)
        }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }

    fun clearAuthBanner() {
        _authBanner.value = null
    }

    fun clearUnauthorizedRouteError() {
        _unauthorizedRouteError.value = null
    }

    fun clearConsentAlert() {
        _consentDeniedAlert.value = null
    }

    fun clearClinicalOpError() {
        _clinicalOpError.value = null
    }

    // --- Production-Oriented Authentication & Role Access ---

    fun signInWithEmail(
        email: String,
        password: String,
        rememberMe: Boolean,
        onResult: (AuthResult<LoginOutcome>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.loginWithEmail(email, password, rememberMe)
            onResult(res)
        }
    }

    fun registerAccount(
        request: RegistrationRequest,
        onResult: (AuthResult<RegistrationOutcome>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.registerWithEmail(request)
            if (res is AuthResult.Success && !res.value.user.linkedPatientId.isNullOrBlank()) {
                _selectedPatientId.value = res.value.user.linkedPatientId
            }
            onResult(res)
        }
    }

    fun verifyEmailCode(inputCode: String, onResult: (AuthResult<UserEntity>) -> Unit) {
        viewModelScope.launch {
            val uid = currentUser.value?.id
            if (uid.isNullOrBlank()) {
                onResult(AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED))
                return@launch
            }
            onResult(authRepository.verifyEmail(uid, inputCode))
        }
    }

    fun resendVerificationCode(onResult: (AuthResult<Boolean>) -> Unit) {
        viewModelScope.launch {
            val uid = currentUser.value?.id
            if (uid.isNullOrBlank()) {
                onResult(AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED))
                return@launch
            }
            onResult(authRepository.resendVerificationCode(uid))
        }
    }

    fun requestPasswordRecovery(
        email: String,
        onResult: (AuthResult<RecoveryRequestOutcome>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(authRepository.requestPasswordRecovery(email))
        }
    }

    fun resetPasswordWithToken(
        email: String,
        token: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (AuthResult<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.resetPasswordWithToken(email, token, newPassword, confirmPassword)
            if (res is AuthResult.Success) {
                _authBanner.value = if (language.value == AppLanguage.FA) {
                    com.example.i18n.FaStrings.msgPasswordResetSuccess
                } else {
                    com.example.i18n.EnStrings.msgPasswordResetSuccess
                }
            }
            onResult(res)
        }
    }

    fun completeRoleOnboarding(
        submission: OnboardingSubmission,
        onResult: (AuthResult<UserEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val uid = currentUser.value?.id
            if (uid.isNullOrBlank()) {
                onResult(AuthResult.Failure(AuthErrorCode.UNAUTHENTICATED))
                return@launch
            }
            onResult(authRepository.completeRoleOnboarding(uid, submission))
        }
    }

    fun navigateIfAuthorized(target: AppRoute, onAuthorized: (AppRoute) -> Unit) {
        viewModelScope.launch {
            when (val guard = authRepository.guardRouteAccess(currentUser.value, target)) {
                is RouteGuardResult.Allowed -> {
                    _unauthorizedRouteError.value = null
                    onAuthorized(target)
                }
                is RouteGuardResult.Denied -> {
                    _unauthorizedRouteError.value = guard.errorCode
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout(currentUser.value)
            _aiState.value = AiUiState.Idle
            _unauthorizedRouteError.value = null
        }
    }

    // --- Context-Based & Consent-Based Patient Selection ---

    fun selectPatientWithConsentCheck(patient: PatientEntity): Boolean {
        val user = currentUser.value ?: return false
        val readError = ClinicalAuthorizationPolicy.evaluateReadAccess(user, patient)
        val allowed = readError == null

        viewModelScope.launch {
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "PATIENT_RECORD_ACCESS_REQUEST",
                targetResource = "patients/${patient.id}",
                targetPatientId = patient.id,
                outcome = if (allowed) "ALLOWED" else "DENIED",
                details = if (allowed) "Care relationship & consent verified." else "Access blocked: ${readError?.name}"
            )
        }

        if (allowed) {
            _selectedPatientId.value = patient.id
            _consentDeniedAlert.value = null
            _clinicalOpError.value = null
            _aiState.value = AiUiState.Idle
        } else {
            _consentDeniedAlert.value = patient.id
        }
        return allowed
    }

    private fun handleOpResult(res: ClinicalOpResult<*>) {
        if (res is ClinicalOpResult.Denied) {
            _clinicalOpError.value = res.error
        } else {
            _clinicalOpError.value = null
        }
    }

    // --- Clinical & Patient Record Mutations (Authorization-Enforced & Audited) ---

    fun updatePatientConsents(clinician: Boolean, caregiver: Boolean, ai: Boolean) {
        viewModelScope.launch {
            val res = clinicalRepository.updatePatientConsents(
                actor = currentUser.value,
                patientId = _selectedPatientId.value,
                clinicianAccess = clinician,
                caregiverAccess = caregiver,
                aiSummarization = ai
            )
            handleOpResult(res)
        }
    }

    fun updatePatientWeight(weightKg: Double) {
        viewModelScope.launch {
            val res = clinicalRepository.updatePatientWeight(currentUser.value, _selectedPatientId.value, weightKg)
            handleOpResult(res)
        }
    }

    fun updatePatientProfile(request: PatientProfileUpdateRequest) {
        viewModelScope.launch {
            val res = clinicalRepository.updatePatientProfile(currentUser.value, _selectedPatientId.value, request)
            handleOpResult(res)
        }
    }

    fun addCondition(name: String, icd10: String, status: String = "Active", notes: String = "") {
        viewModelScope.launch {
            val res = clinicalRepository.addCondition(currentUser.value, _selectedPatientId.value, name, icd10, status, notes)
            handleOpResult(res)
        }
    }

    fun updateCondition(conditionId: String, name: String, icd10: String, status: String, notes: String) {
        viewModelScope.launch {
            val res = clinicalRepository.updateCondition(currentUser.value, conditionId, name, icd10, status, notes)
            handleOpResult(res)
        }
    }

    fun verifyCondition(conditionId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyCondition(currentUser.value, conditionId))
        }
    }

    fun deleteCondition(conditionId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteCondition(currentUser.value, conditionId))
        }
    }

    fun addMedicalHistory(category: MedicalHistoryCategory, title: String, details: String) {
        viewModelScope.launch {
            val res = clinicalRepository.addMedicalHistory(currentUser.value, _selectedPatientId.value, category, title, details)
            handleOpResult(res)
        }
    }

    fun updateMedicalHistory(historyId: String, category: MedicalHistoryCategory, title: String, details: String) {
        viewModelScope.launch {
            val res = clinicalRepository.updateMedicalHistory(currentUser.value, historyId, category, title, details)
            handleOpResult(res)
        }
    }

    fun verifyMedicalHistory(historyId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyMedicalHistory(currentUser.value, historyId))
        }
    }

    fun deleteMedicalHistory(historyId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteMedicalHistory(currentUser.value, historyId))
        }
    }

    fun addAllergy(allergen: String, reaction: String, severity: AlertSeverity) {
        viewModelScope.launch {
            val res = clinicalRepository.addAllergy(currentUser.value, _selectedPatientId.value, allergen, reaction, severity)
            handleOpResult(res)
        }
    }

    fun updateAllergy(allergyId: String, allergen: String, reaction: String, severity: AlertSeverity) {
        viewModelScope.launch {
            val res = clinicalRepository.updateAllergy(currentUser.value, allergyId, allergen, reaction, severity)
            handleOpResult(res)
        }
    }

    fun verifyAllergy(allergyId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyAllergy(currentUser.value, allergyId))
        }
    }

    fun deleteAllergy(allergyId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteAllergy(currentUser.value, allergyId))
        }
    }

    fun addObservation(typeTitle: String, valueText: String, unit: String) {
        viewModelScope.launch {
            val res = clinicalRepository.addObservation(currentUser.value, _selectedPatientId.value, typeTitle, valueText, unit)
            handleOpResult(res)
        }
    }

    fun verifyObservation(obsId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyObservation(currentUser.value, obsId))
        }
    }

    fun deleteObservation(obsId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteObservation(currentUser.value, obsId))
        }
    }

    fun reportOrPrescribeMedication(
        name: String,
        activeIngredient: String,
        strength: String,
        frequency: String,
        status: MedicationStatus,
        notes: String
    ) {
        viewModelScope.launch {
            val res = clinicalRepository.addMedication(
                actor = currentUser.value,
                patientId = _selectedPatientId.value,
                name = name,
                activeIngredient = activeIngredient,
                strength = strength,
                frequency = frequency,
                status = status,
                notes = notes
            )
            handleOpResult(res)
        }
    }

    fun updateMedication(medId: String, strength: String, frequency: String, status: MedicationStatus, notes: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.updateMedication(currentUser.value, medId, strength, frequency, status, notes))
        }
    }

    fun reconcileMedication(med: MedicationEntity, finalStatus: MedicationStatus) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.reconcileMedication(currentUser.value, med.id, finalStatus))
        }
    }

    fun deleteMedication(medId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteMedication(currentUser.value, medId))
        }
    }

    fun addLabResult(
        testName: String,
        numericValue: Double,
        unit: String,
        refLow: Double,
        refHigh: Double
    ) {
        viewModelScope.launch {
            val res = clinicalRepository.addLabResult(
                actor = currentUser.value,
                patientId = _selectedPatientId.value,
                testName = testName,
                numericValue = numericValue,
                unit = unit,
                refLow = refLow,
                refHigh = refHigh
            )
            handleOpResult(res)
        }
    }

    fun verifyLabResult(labId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyLabResult(currentUser.value, labId))
        }
    }

    fun deleteLabResult(labId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteLabResult(currentUser.value, labId))
        }
    }

    fun uploadMedicalDocument(
        title: String,
        category: DocumentCategory,
        uriString: String,
        mimeType: String,
        extractedNote: String
    ) {
        viewModelScope.launch {
            val res = clinicalRepository.addDocument(
                actor = currentUser.value,
                patientId = _selectedPatientId.value,
                title = title,
                category = category,
                uriString = uriString,
                mimeType = mimeType,
                extractedNote = extractedNote
            )
            handleOpResult(res)
        }
    }

    fun updateDocumentSummary(docId: String, title: String, category: DocumentCategory, summary: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.updateDocumentSummary(currentUser.value, docId, title, category, summary))
        }
    }

    fun verifyDocument(doc: MedicalDocumentEntity) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyDocument(currentUser.value, doc.id))
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteDocument(currentUser.value, docId))
        }
    }

    fun addCarePlan(title: String, goal: String, progressPct: Int, status: CarePlanStatus) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.addCarePlan(currentUser.value, _selectedPatientId.value, title, goal, progressPct, status))
        }
    }

    fun updateCarePlan(planId: String, title: String, goal: String, progressPct: Int, status: CarePlanStatus) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.updateCarePlan(currentUser.value, planId, title, goal, progressPct, status))
        }
    }

    fun verifyCarePlan(planId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.verifyCarePlan(currentUser.value, planId))
        }
    }

    fun deleteCarePlan(planId: String) {
        viewModelScope.launch {
            handleOpResult(clinicalRepository.deleteCarePlan(currentUser.value, planId))
        }
    }

    fun recordClinicalDecision(
        alertKey: String,
        titleEn: String,
        titleFa: String,
        severity: AlertSeverity,
        evidenceVersion: String,
        action: ClinicalDecisionAction,
        rationale: String
    ) {
        viewModelScope.launch {
            val res = clinicalRepository.recordClinicalDecision(
                actor = currentUser.value,
                patientId = _selectedPatientId.value,
                alertKey = alertKey,
                titleEn = titleEn,
                titleFa = titleFa,
                severity = severity,
                evidenceVersion = evidenceVersion,
                action = action,
                rationale = rationale
            )
            handleOpResult(res)
        }
    }

    fun runAiTask(
        taskType: AiTaskType,
        customPrompt: String = "",
        selectedDocument: MedicalDocumentEntity? = null,
        documentBitmap: Bitmap? = null
    ) {
        val bundle = activePatientBundle.value ?: return
        val user = currentUser.value ?: return
        val request = AiClinicalRequest(
            taskType = taskType,
            language = language.value,
            actor = user,
            patient = bundle.patient,
            conditions = bundle.conditions,
            medicalHistory = bundle.medicalHistory,
            medications = bundle.medications,
            allergies = bundle.allergies,
            labs = bundle.labs,
            observations = bundle.observations,
            documents = bundle.documents,
            carePlans = bundle.carePlans,
            alerts = bundle.cdsReport.alerts,
            customPrompt = customPrompt,
            selectedDocument = selectedDocument,
            documentBitmap = documentBitmap
        )
        executeAiRequest(request, user, bundle.patient.id)
    }

    fun retryLastAiTask() {
        val prev = lastAiRequest ?: return
        val user = currentUser.value ?: return
        executeAiRequest(prev.copy(language = language.value, actor = user), user, prev.patient.id)
    }

    private fun executeAiRequest(request: AiClinicalRequest, user: UserEntity, patientId: String) {
        lastAiRequest = request
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            try {
                val res = aiRepository.generateClinicalAssistance(request)
                _aiState.value = AiUiState.Success(res)
                recordAudit(
                    actorId = user.id,
                    actorName = "${user.firstName} ${user.lastName}",
                    actorRole = user.role,
                    actionType = "AI_ASSISTANCE_QUERIED",
                    targetResource = "ai/${request.taskType.name}",
                    targetPatientId = patientId,
                    outcome = "ALLOWED",
                    details = "Gemini structured response validated (sources=${res.citedSourceIds.size}, requiresReview=${res.requiresClinicianReview})"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiAccessDeniedException) {
                Log.w("NersikaViewModel", "AI access denied: ${e.reason}", e)
                val msg = localizedAiErrorMessage(e.reason, e.message)
                _aiState.value = AiUiState.Error(msg, e.reason)
                recordAudit(
                    actorId = user.id,
                    actorName = "${user.firstName} ${user.lastName}",
                    actorRole = user.role,
                    actionType = "AI_ACCESS_DENIED",
                    targetResource = "ai/${request.taskType.name}",
                    targetPatientId = patientId,
                    outcome = "DENIED",
                    details = "Blocked by AI access policy: ${e.reason.name}"
                )
            } catch (e: AiValidationException) {
                Log.e("NersikaViewModel", "AI output validation blocked response: ${e.reason}", e)
                val msg = localizedAiErrorMessage(e.reason, e.message)
                _aiState.value = AiUiState.Error(msg, e.reason)
                recordAudit(
                    actorId = user.id,
                    actorName = "${user.firstName} ${user.lastName}",
                    actorRole = user.role,
                    actionType = "AI_OUTPUT_VALIDATION_FAILED",
                    targetResource = "ai/${request.taskType.name}",
                    targetPatientId = patientId,
                    outcome = "BLOCKED",
                    details = "Output validator rejected payload: ${e.reason.name}"
                )
            } catch (e: AiInferenceException) {
                Log.e("NersikaViewModel", "Gemini inference failure: ${e.reason}", e)
                val msg = localizedAiErrorMessage(e.reason, e.message)
                _aiState.value = AiUiState.Error(msg, e.reason)
                recordAudit(
                    actorId = user.id,
                    actorName = "${user.firstName} ${user.lastName}",
                    actorRole = user.role,
                    actionType = "AI_INFERENCE_FAILED",
                    targetResource = "ai/${request.taskType.name}",
                    targetPatientId = patientId,
                    outcome = "ERROR",
                    details = "${e.reason.name}: ${e.localizedMessage ?: "error"}"
                )
            } catch (e: Exception) {
                Log.e("NersikaViewModel", "Unexpected AI assistant error", e)
                val msg = e.localizedMessage ?: localizedAiErrorMessage(AiFailureReason.NETWORK_OR_APP_CHECK_ERROR, null)
                _aiState.value = AiUiState.Error(msg, AiFailureReason.NETWORK_OR_APP_CHECK_ERROR)
                recordAudit(
                    actorId = user.id,
                    actorName = "${user.firstName} ${user.lastName}",
                    actorRole = user.role,
                    actionType = "AI_INFERENCE_FAILED",
                    targetResource = "ai/${request.taskType.name}",
                    targetPatientId = patientId,
                    outcome = "ERROR",
                    details = e.localizedMessage ?: "Unexpected error"
                )
            }
        }
    }

    private fun localizedAiErrorMessage(reason: AiFailureReason, fallback: String?): String {
        val s = if (language.value == AppLanguage.FA) com.example.i18n.FaStrings else com.example.i18n.EnStrings
        return when (reason) {
            AiFailureReason.TIMEOUT -> s.aiErrTimeout
            AiFailureReason.NETWORK_OR_APP_CHECK_ERROR ->
                if (!fallback.isNullOrBlank()) "${s.aiErrNetworkOrAppCheck} ($fallback)" else s.aiErrNetworkOrAppCheck
            AiFailureReason.UNSAFE_PRESCRIPTION_OR_DOSE -> s.aiErrUnsafePrescriptionBlocked
            AiFailureReason.FABRICATED_REFERENCE -> s.aiErrFabricatedReferenceBlocked
            AiFailureReason.EMPTY_RESPONSE,
            AiFailureReason.MALFORMED_JSON,
            AiFailureReason.MISSING_REQUIRED_PROVENANCE -> s.aiErrMalformedOrEmpty
            AiFailureReason.CLINICIAN_ROLE_REQUIRED -> s.aiErrClinicianRoleRequired
            AiFailureReason.CONSENT_AI_DISABLED -> s.aiErrConsentDisabled
            AiFailureReason.UNAUTHENTICATED,
            AiFailureReason.EMAIL_NOT_VERIFIED,
            AiFailureReason.ONBOARDING_REQUIRED,
            AiFailureReason.RECORD_ACCESS_DENIED -> s.errUnauthorizedRole
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { dao.markNotificationRead(id) }
    }

    fun toggleMfa(enabled: Boolean) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            dao.updateMfa(user.id, enabled)
            recordAudit(
                actorId = user.id,
                actorName = "${user.firstName} ${user.lastName}",
                actorRole = user.role,
                actionType = "MFA_CONFIG_UPDATED",
                targetResource = "users/${user.id}/mfa",
                targetPatientId = null,
                outcome = "RECORDED",
                details = "MFA enabled=$enabled"
            )
        }
    }

    fun verifyUserClinicianRole(targetUser: UserEntity) {
        viewModelScope.launch {
            val admin = currentUser.value ?: return@launch
            val guard = authRepository.guardAdminGovernanceAccess(admin)
            if (guard is RouteGuardResult.Denied) {
                _unauthorizedRouteError.value = guard.errorCode
                return@launch
            }
            dao.updateClinicianVerification(targetUser.id, !targetUser.isVerifiedClinician)
            recordAudit(
                actorId = admin.id,
                actorName = "${admin.firstName} ${admin.lastName}",
                actorRole = admin.role,
                actionType = "ADMIN_ROLE_VERIFICATION_CHANGED",
                targetResource = "users/${targetUser.id}",
                targetPatientId = null,
                outcome = "ALLOWED",
                details = "Set verified=${!targetUser.isVerifiedClinician} for ${targetUser.email}"
            )
        }
    }

    private suspend fun recordAudit(
        actorId: String,
        actorName: String,
        actorRole: String,
        actionType: String,
        targetResource: String,
        targetPatientId: String?,
        outcome: String,
        details: String
    ) {
        dao.insertAuditEvent(
            AuditEventEntity(
                id = "aud_${UUID.randomUUID().toString().take(10)}",
                actorId = actorId,
                actorName = actorName,
                actorRole = actorRole,
                actionType = actionType,
                targetResource = targetResource,
                targetPatientId = targetPatientId,
                outcome = outcome,
                details = details,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}
