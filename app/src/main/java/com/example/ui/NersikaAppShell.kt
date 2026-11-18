package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.auth.AppRoute
import com.example.data.auth.RouteAccessPolicy
import com.example.data.auth.RouteGuardResult
import com.example.domain.model.UserRole
import com.example.ui.components.LanguageSwitcherChip
import com.example.ui.components.NersikaBrandMark
import com.example.ui.screens.AdminAuditSettingsScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthPortalScreen
import com.example.ui.screens.DocumentsAndCareScreen
import com.example.ui.screens.EmailVerificationScreen
import com.example.ui.screens.HealthRecordScreen
import com.example.ui.screens.LaboratoryIntelligenceScreen
import com.example.ui.screens.MedicationsAndSafetyScreen
import com.example.ui.screens.PatientDirectoryScreen
import com.example.ui.screens.RoleDashboardScreen
import com.example.ui.screens.RoleOnboardingScreen
import com.example.ui.screens.localizedMessage
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.NersikaTheme

private data class NavDestination(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NersikaRootApp(viewModel: NersikaViewModel = viewModel()) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allPatients by viewModel.allPatients.collectAsStateWithLifecycle()
    val activeBundle by viewModel.activePatientBundle.collectAsStateWithLifecycle()
    val selectedPatientId by viewModel.selectedPatientId.collectAsStateWithLifecycle()
    val consentDeniedId by viewModel.consentDeniedAlert.collectAsStateWithLifecycle()
    val authBanner by viewModel.authBanner.collectAsStateWithLifecycle()
    val unauthorizedRouteError by viewModel.unauthorizedRouteError.collectAsStateWithLifecycle()
    val clinicalOpError by viewModel.clinicalOpError.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val auditEvents by viewModel.auditEvents.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    NersikaTheme(language = language, darkTheme = darkMode) {
        val s = LocalAppStrings.current
        val user = currentUser

        when {
            user == null -> {
                AuthPortalScreen(
                    isLiveEmailConfigured = viewModel.isLiveEmailConfigured,
                    authBannerMessage = authBanner,
                    onDismissBanner = viewModel::clearAuthBanner,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onSignIn = viewModel::signInWithEmail,
                    onRegister = viewModel::registerAccount,
                    onRequestPasswordRecovery = viewModel::requestPasswordRecovery,
                    onResetPasswordWithToken = viewModel::resetPasswordWithToken
                )
            }

            !user.emailVerified -> {
                EmailVerificationScreen(
                    user = user,
                    isLiveEmailConfigured = viewModel.isLiveEmailConfigured,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onVerifyCode = viewModel::verifyEmailCode,
                    onResendCode = viewModel::resendVerificationCode,
                    onSignOut = viewModel::signOut
                )
            }

            !user.onboardingCompleted -> {
                RoleOnboardingScreen(
                    user = user,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onCompleteOnboarding = { submission, cb ->
                        viewModel.completeRoleOnboarding(submission, cb)
                    },
                    onSignOut = viewModel::signOut
                )
            }

            else -> {
                val role = UserRole.fromName(user.role)
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRouteKey = backStackEntry?.destination?.route ?: AppRoute.DASHBOARD.routeKey
                val currentRoute = AppRoute.fromKey(currentRouteKey)

                // Reactive protected route enforcement on any route or user/role change
                LaunchedEffect(currentRoute, user.id, user.role, user.emailVerified, user.onboardingCompleted) {
                    val guard = RouteAccessPolicy.evaluate(user, currentRoute)
                    if (guard is RouteGuardResult.Denied) {
                        navController.navigate(guard.fallbackRoute.routeKey) {
                            popUpTo(AppRoute.DASHBOARD.routeKey) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }

                val navigateProtected: (AppRoute) -> Unit = { target ->
                    viewModel.navigateIfAuthorized(target) { allowedRoute ->
                        navController.navigate(allowedRoute.routeKey) {
                            popUpTo(AppRoute.DASHBOARD.routeKey) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                val navItems = buildList {
                    add(NavDestination(AppRoute.DASHBOARD, s.navDashboard, Icons.Filled.Dashboard))
                    if (role != UserRole.PATIENT) {
                        add(NavDestination(AppRoute.PATIENTS, s.navPatients, Icons.Filled.People))
                    } else {
                        add(NavDestination(AppRoute.RECORDS, s.navRecords, Icons.Filled.HealthAndSafety))
                    }
                    add(NavDestination(AppRoute.MEDICATIONS, s.navMedications, Icons.Filled.Medication))
                    add(NavDestination(AppRoute.LABS, s.navLabs, Icons.Filled.Biotech))
                    add(NavDestination(AppRoute.AI_ASSISTANT, s.navAiAssistant, Icons.Filled.Psychology))
                    add(
                        NavDestination(
                            AppRoute.ADMIN,
                            if (role.isAdmin) s.navAdmin else s.navMore,
                            Icons.Filled.AdminPanelSettings
                        )
                    )
                }

                val unreadCount = notifications.count { !it.isRead }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        topBar = {
                            TopAppBar(
                                title = { NersikaBrandMark() },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                actions = {
                                    IconButton(
                                        onClick = { navigateProtected(AppRoute.DOCUMENTS) },
                                        modifier = Modifier.testTag("topbar_documents_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = s.navDocuments
                                        )
                                    }
                                    IconButton(
                                        onClick = { navigateProtected(AppRoute.ADMIN) },
                                        modifier = Modifier.testTag("topbar_notifications_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadCount > 0) {
                                                    Badge { Text(unreadCount.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Notifications,
                                                contentDescription = s.navNotifications
                                            )
                                        }
                                    }
                                    LanguageSwitcherChip(
                                        onToggle = viewModel::toggleLanguage,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            )
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    navItems.forEach { item ->
                                        NavigationBarItem(
                                            selected = currentRoute == item.route ||
                                                (item.route == AppRoute.PATIENTS && currentRoute == AppRoute.RECORDS),
                                            onClick = { navigateProtected(item.route) },
                                            icon = {
                                                Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp))
                                            },
                                            label = {
                                                Text(
                                                    text = item.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            modifier = Modifier.testTag("nav_${item.route.routeKey}")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (unauthorizedRouteError != null) {
                                Surface(
                                    color = CriticalContainer,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("unauthorized_route_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Lock, null, tint = CriticalRed, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = unauthorizedRouteError!!.localizedMessage(s),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CriticalRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = viewModel::clearUnauthorizedRouteError) {
                                            Text(s.close)
                                        }
                                    }
                                }
                            }

                            if (clinicalOpError != null) {
                                Surface(
                                    color = CriticalContainer,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("clinical_op_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Lock, null, tint = CriticalRed, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = clinicalOpError!!.localizedMessage(s),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CriticalRed,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = viewModel::clearClinicalOpError) {
                                            Text(s.close)
                                        }
                                    }
                                }
                            }

                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (useRail) {
                                    NavigationRail(
                                        modifier = Modifier.fillMaxHeight(),
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        navItems.forEach { item ->
                                            NavigationRailItem(
                                                selected = currentRoute == item.route,
                                                onClick = { navigateProtected(item.route) },
                                                icon = { Icon(item.icon, contentDescription = item.label) },
                                                label = { Text(item.label, maxLines = 1) },
                                                modifier = Modifier.testTag("rail_${item.route.routeKey}")
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = AppRoute.DASHBOARD.routeKey
                                    ) {
                                        composable(AppRoute.DASHBOARD.routeKey) {
                                            RoleDashboardScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                totalPatientsCount = allPatients.size,
                                                unreadNotificationsCount = unreadCount,
                                                onNavigateRoute = { routeStr ->
                                                    navigateProtected(AppRoute.fromKey(routeStr))
                                                }
                                            )
                                        }

                                        composable(AppRoute.PATIENTS.routeKey) {
                                            PatientDirectoryScreen(
                                                patients = allPatients,
                                                selectedPatientId = selectedPatientId,
                                                consentDeniedPatientId = consentDeniedId,
                                                onDismissConsentAlert = viewModel::clearConsentAlert,
                                                onSelectPatient = { patient ->
                                                    if (viewModel.selectPatientWithConsentCheck(patient)) {
                                                        navigateProtected(AppRoute.RECORDS)
                                                    }
                                                }
                                            )
                                        }

                                        composable(AppRoute.RECORDS.routeKey) {
                                            HealthRecordScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                onUpdateProfile = viewModel::updatePatientProfile,
                                                onAddCondition = viewModel::addCondition,
                                                onUpdateCondition = viewModel::updateCondition,
                                                onVerifyCondition = viewModel::verifyCondition,
                                                onDeleteCondition = viewModel::deleteCondition,
                                                onAddMedicalHistory = viewModel::addMedicalHistory,
                                                onUpdateMedicalHistory = viewModel::updateMedicalHistory,
                                                onVerifyMedicalHistory = viewModel::verifyMedicalHistory,
                                                onDeleteMedicalHistory = viewModel::deleteMedicalHistory,
                                                onAddAllergy = viewModel::addAllergy,
                                                onUpdateAllergy = viewModel::updateAllergy,
                                                onVerifyAllergy = viewModel::verifyAllergy,
                                                onDeleteAllergy = viewModel::deleteAllergy,
                                                onAddObservation = viewModel::addObservation,
                                                onVerifyObservation = viewModel::verifyObservation,
                                                onDeleteObservation = viewModel::deleteObservation
                                            )
                                        }

                                        composable(AppRoute.MEDICATIONS.routeKey) {
                                            MedicationsAndSafetyScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                onReportMedication = viewModel::reportOrPrescribeMedication,
                                                onUpdateMedication = viewModel::updateMedication,
                                                onReconcileMedication = viewModel::reconcileMedication,
                                                onDeleteMedication = viewModel::deleteMedication,
                                                onSubmitClinicalDecision = { alert, action, rationale ->
                                                    viewModel.recordClinicalDecision(
                                                        alertKey = alert.alertKey,
                                                        titleEn = alert.titleEn,
                                                        titleFa = alert.titleFa,
                                                        severity = alert.severity,
                                                        evidenceVersion = alert.evidenceVersion,
                                                        action = action,
                                                        rationale = rationale
                                                    )
                                                }
                                            )
                                        }

                                        composable(AppRoute.LABS.routeKey) {
                                            LaboratoryIntelligenceScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                onAddLabResult = viewModel::addLabResult,
                                                onVerifyLabResult = viewModel::verifyLabResult,
                                                onDeleteLabResult = viewModel::deleteLabResult
                                            )
                                        }

                                        composable(AppRoute.DOCUMENTS.routeKey) {
                                            DocumentsAndCareScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                onUploadDocument = viewModel::uploadMedicalDocument,
                                                onUpdateDocumentSummary = viewModel::updateDocumentSummary,
                                                onVerifyDocument = viewModel::verifyDocument,
                                                onDeleteDocument = viewModel::deleteDocument,
                                                onAddCarePlan = viewModel::addCarePlan,
                                                onUpdateCarePlan = viewModel::updateCarePlan,
                                                onVerifyCarePlan = viewModel::verifyCarePlan,
                                                onDeleteCarePlan = viewModel::deleteCarePlan,
                                                onUpdateConsents = viewModel::updatePatientConsents
                                            )
                                        }

                                        composable(AppRoute.AI_ASSISTANT.routeKey) {
                                            AiAssistantScreen(
                                                user = user,
                                                bundle = activeBundle,
                                                aiState = aiState,
                                                onRunTask = viewModel::runAiTask,
                                                onRetryLast = viewModel::retryLastAiTask
                                            )
                                        }

                                        composable(AppRoute.ADMIN.routeKey) {
                                            AdminAuditSettingsScreen(
                                                user = user,
                                                allUsers = allUsers,
                                                notifications = notifications,
                                                auditEvents = auditEvents,
                                                darkMode = darkMode,
                                                onToggleLanguage = viewModel::toggleLanguage,
                                                onToggleDarkMode = viewModel::toggleDarkMode,
                                                onToggleMfa = viewModel::toggleMfa,
                                                onVerifyUserRole = viewModel::verifyUserClinicianRole,
                                                onMarkNotificationRead = viewModel::markNotificationRead,
                                                onSignOut = viewModel::signOut
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
    }
}
