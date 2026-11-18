package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AuditEventEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.UserEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.UserRole
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LanguageSwitcherChip
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.LightTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen

@Composable
fun AdminAuditSettingsScreen(
    user: UserEntity,
    allUsers: List<UserEntity>,
    notifications: List<NotificationEntity>,
    auditEvents: List<AuditEventEntity>,
    darkMode: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleMfa: (Boolean) -> Unit,
    onVerifyUserRole: (UserEntity) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    val role = UserRole.fromName(user.role)

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var auditFilter by rememberSaveable { mutableStateOf("") }
    var showPolicyDialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val tabs = buildList {
                    add(s.navNotifications)
                    add(s.navAuditLogs)
                    if (role.isAdmin) add(s.navAdmin)
                    add(s.navSettings)
                }
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size)
                    ) {
                        Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        val effectiveTab = if (!role.isAdmin && selectedTab >= 2) selectedTab + 1 else selectedTab

        when (effectiveTab) {
            0 -> {
                if (notifications.isEmpty()) {
                    item { EmptyStateCard(s.noItemsTitle, s.navNotifications) }
                } else {
                    items(notifications, key = { it.id }) { notif ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkNotificationRead(notif.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else LightTeal
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SeverityBadge(AlertSeverity.fromName(notif.priority))
                                    Text(
                                        text = LocaleFormatter.formatDateTime(notif.createdAtMillis, lang),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(if (isFa) notif.titleFa else notif.titleEn, style = MaterialTheme.typography.titleSmall)
                                Text(if (isFa) notif.bodyFa else notif.bodyEn, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            1 -> {
                item {
                    OutlinedTextField(
                        value = auditFilter,
                        onValueChange = { auditFilter = it },
                        label = { Text("${s.search} (${s.auditLogsTitle})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val filteredAudit = auditEvents.filter {
                    auditFilter.isBlank() ||
                        it.actionType.contains(auditFilter, true) ||
                        it.actorName.contains(auditFilter, true) ||
                        it.outcome.contains(auditFilter, true)
                }
                items(filteredAudit, key = { it.id }) { ev ->
                    val isDenied = ev.outcome == "DENIED"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ev.actionType, style = MaterialTheme.typography.titleSmall)
                                Surface(
                                    color = if (isDenied) CriticalContainer else SuccessContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ev.outcome,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDenied) CriticalRed else SuccessGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("${ev.actorName} (${ev.actorRole}) → ${ev.targetResource}", style = MaterialTheme.typography.bodySmall)
                            Text(ev.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = LocaleFormatter.formatDateTime(ev.timestampMillis, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MedicalTeal
                            )
                        }
                    }
                }
            }

            2 -> {
                // Admin User & Role Management
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Security, null, tint = MedicalTeal)
                                Text(s.securityMonitoringTitle, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "Total Accounts: ${LocaleFormatter.formatNumber(allUsers.size, lang)} • Audit Events: ${LocaleFormatter.formatNumber(auditEvents.size, lang)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                items(allUsers, key = { it.id }) { u ->
                    val r = UserRole.fromName(u.role)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${u.firstName} ${u.lastName}", style = MaterialTheme.typography.titleSmall)
                                Text("${r.displayName(s)} • ${u.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { onVerifyUserRole(u) }) {
                                Icon(Icons.Filled.VerifiedUser, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(if (u.isVerifiedClinician) s.statusVerified else s.verifyRoleAction)
                            }
                        }
                    }
                }
            }

            3 -> {
                // Settings, Language, Dark Mode, MFA, Legal & Sign Out
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("${user.firstName} ${user.lastName}", style = MaterialTheme.typography.titleMedium)
                            Text("${role.displayName(s)} • ${user.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.preferredLanguageLabel, style = MaterialTheme.typography.bodyMedium)
                                LanguageSwitcherChip(onToggle = onToggleLanguage)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.darkModeLabel, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = darkMode,
                                    onCheckedChange = onToggleDarkMode,
                                    modifier = Modifier.testTag("dark_mode_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.mfaToggleLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = user.mfaEnabled,
                                    onCheckedChange = onToggleMfa
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showPolicyDialog = s.aboutPlatformTitle },
                                    modifier = Modifier.weight(1f)
                                ) { Text(s.aboutPlatformTitle, maxLines = 1) }
                                OutlinedButton(
                                    onClick = { showPolicyDialog = s.privacyPolicyTitle },
                                    modifier = Modifier.weight(1f)
                                ) { Text(s.privacyPolicyTitle, maxLines = 1) }
                            }

                            Button(
                                onClick = onSignOut,
                                modifier = Modifier.fillMaxWidth().testTag("logout_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CriticalRed)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(s.logout)
                            }
                        }
                    }
                }
            }
        }
    }

    showPolicyDialog?.let { title ->
        AlertDialog(
            onDismissRequest = { showPolicyDialog = null },
            title = { Text(title) },
            text = {
                Text(
                    "${s.brandName} — ${s.productDescription}\n\n${s.clinicalSafetyNotice}\n\n${s.demoDataBanner}"
                )
            },
            confirmButton = {
                Button(onClick = { showPolicyDialog = null }) { Text(s.close) }
            }
        )
    }
}
