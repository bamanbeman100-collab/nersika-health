package com.example.data.auth

import com.example.data.local.UserEntity
import com.example.domain.model.UserRole

enum class AppRoute(val routeKey: String) {
    AUTH("auth"),
    VERIFY_EMAIL("verify_email"),
    ROLE_ONBOARDING("role_onboarding"),
    DASHBOARD("dashboard"),
    PATIENTS("patients"),
    RECORDS("records"),
    MEDICATIONS("medications"),
    LABS("labs"),
    DOCUMENTS("documents"),
    AI_ASSISTANT("ai"),
    ADMIN("admin");

    companion object {
        fun fromKey(key: String?): AppRoute =
            entries.firstOrNull { it.routeKey == key } ?: AUTH
    }
}

sealed interface RouteGuardResult {
    data object Allowed : RouteGuardResult
    data class Denied(
        val errorCode: AuthErrorCode,
        val fallbackRoute: AppRoute
    ) : RouteGuardResult
}

object RouteAccessPolicy {

    fun defaultEntryRoute(user: UserEntity?): AppRoute = when {
        user == null -> AppRoute.AUTH
        !user.emailVerified -> AppRoute.VERIFY_EMAIL
        !user.onboardingCompleted -> AppRoute.ROLE_ONBOARDING
        else -> AppRoute.DASHBOARD
    }

    fun evaluate(user: UserEntity?, target: AppRoute): RouteGuardResult {
        if (target == AppRoute.AUTH) {
            return RouteGuardResult.Allowed
        }

        if (user == null) {
            return RouteGuardResult.Denied(
                errorCode = AuthErrorCode.UNAUTHENTICATED,
                fallbackRoute = AppRoute.AUTH
            )
        }

        if (target == AppRoute.VERIFY_EMAIL) {
            return if (!user.emailVerified) {
                RouteGuardResult.Allowed
            } else {
                RouteGuardResult.Denied(
                    errorCode = AuthErrorCode.EMAIL_ALREADY_VERIFIED,
                    fallbackRoute = defaultEntryRoute(user)
                )
            }
        }

        if (!user.emailVerified) {
            return RouteGuardResult.Denied(
                errorCode = AuthErrorCode.EMAIL_NOT_VERIFIED,
                fallbackRoute = AppRoute.VERIFY_EMAIL
            )
        }

        if (target == AppRoute.ROLE_ONBOARDING) {
            return if (!user.onboardingCompleted) {
                RouteGuardResult.Allowed
            } else {
                RouteGuardResult.Denied(
                    errorCode = AuthErrorCode.UNAUTHORIZED_ROUTE,
                    fallbackRoute = AppRoute.DASHBOARD
                )
            }
        }

        if (!user.onboardingCompleted) {
            return RouteGuardResult.Denied(
                errorCode = AuthErrorCode.ONBOARDING_REQUIRED,
                fallbackRoute = AppRoute.ROLE_ONBOARDING
            )
        }

        val role = UserRole.fromName(user.role)
        return when (target) {
            AppRoute.PATIENTS -> {
                if (role == UserRole.PATIENT) {
                    RouteGuardResult.Denied(
                        errorCode = AuthErrorCode.UNAUTHORIZED_ROLE,
                        fallbackRoute = AppRoute.RECORDS
                    )
                } else {
                    RouteGuardResult.Allowed
                }
            }

            AppRoute.ADMIN -> {
                // Non-admins can view Settings/Notifications inside the hub, OR dedicated admin controls are gated:
                // Let's keep AppRoute.ADMIN as a strictly protected RBAC route for Admin/Settings/Governance
                // Wait: let's keep a separate Settings vs Admin check OR define a dedicated ADMIN_PANEL guard.
                RouteGuardResult.Allowed
            }

            else -> RouteGuardResult.Allowed
        }
    }

    fun canAccessAdminGovernance(user: UserEntity?): RouteGuardResult {
        val base = evaluate(user, AppRoute.ADMIN)
        if (base is RouteGuardResult.Denied) return base
        val role = UserRole.fromName(user?.role)
        return if (role.isAdmin) {
            RouteGuardResult.Allowed
        } else {
            RouteGuardResult.Denied(
                errorCode = AuthErrorCode.UNAUTHORIZED_ROLE,
                fallbackRoute = AppRoute.DASHBOARD
            )
        }
    }
}
