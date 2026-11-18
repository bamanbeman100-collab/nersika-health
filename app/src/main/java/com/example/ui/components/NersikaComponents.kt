package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LabResultEntity
import com.example.domain.model.AlertSeverity
import com.example.domain.model.LabFlag
import com.example.domain.model.SourceType
import com.example.domain.model.VerificationStatus
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleFormatter
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoContainer
import com.example.ui.theme.LightTeal
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings

@Composable
fun NersikaBrandMark(modifier: Modifier = Modifier) {
    val s = LocalAppStrings.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(NavyPrimary, MedicalTeal))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.HealthAndSafety,
                contentDescription = s.brandName,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text = s.brandName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = s.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LanguageSwitcherChip(
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    OutlinedButton(
        onClick = onToggle,
        modifier = modifier.testTag("language_switcher_button"),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = s.switchLanguageLabel,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = s.switchLanguageLabel,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun DemoSafetyBanner(modifier: Modifier = Modifier) {
    val s = LocalAppStrings.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WarningContainer,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = s.demoDataBanner,
                tint = WarningAmber,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.demoDataBanner,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Text(
                    text = s.clinicalSafetyNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = NavyPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun SourceAndVerificationRow(
    sourceType: String,
    verificationStatus: String,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val src = SourceType.fromName(sourceType)
    val ver = VerificationStatus.fromName(verificationStatus)
    val (verBg, verFg) = when (ver) {
        VerificationStatus.VERIFIED -> SuccessContainer to SuccessGreen
        VerificationStatus.NEEDS_REVIEW -> WarningContainer to WarningAmber
        VerificationStatus.UNVERIFIED -> CriticalContainer to CriticalRed
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = LightTeal,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = src.displayName(s),
                style = MaterialTheme.typography.labelSmall,
                color = NavyPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Surface(
            color = verBg,
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = ver.displayName(s),
                    tint = verFg,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = ver.displayName(s),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = verFg
                )
            }
        }
    }
}

@Composable
fun RecordActionFooter(
    sourceType: String,
    verificationStatus: String,
    canModify: Boolean,
    canVerify: Boolean,
    onEdit: (() -> Unit)? = null,
    onVerify: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    testTagPrefix: String = "record",
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val ver = VerificationStatus.fromName(verificationStatus)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SourceAndVerificationRow(sourceType = sourceType, verificationStatus = verificationStatus)
        if (canModify || (canVerify && ver != VerificationStatus.VERIFIED)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canVerify && ver != VerificationStatus.VERIFIED && onVerify != null) {
                    OutlinedButton(
                        onClick = onVerify,
                        modifier = Modifier.testTag("${testTagPrefix}_verify_btn")
                    ) {
                        Text(s.verifyEntryButton, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (canModify && onEdit != null) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("${testTagPrefix}_edit_btn")
                    ) {
                        Text(s.editRecordButton, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (canModify && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("${testTagPrefix}_delete_btn")
                    ) {
                        Text(s.deleteRecordButton, color = CriticalRed, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SeverityBadge(severity: AlertSeverity, modifier: Modifier = Modifier) {
    val s = LocalAppStrings.current
    val (bg, fg) = when (severity) {
        AlertSeverity.RED -> CriticalContainer to CriticalRed
        AlertSeverity.AMBER -> WarningContainer to WarningAmber
        AlertSeverity.BLUE -> InfoContainer to InfoBlue
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (severity == AlertSeverity.BLUE) Icons.Outlined.Info else Icons.Filled.Shield,
                contentDescription = severity.displayName(s),
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = severity.displayName(s),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

/**
 * Custom Canvas horizontal reference range bar showing low / normal / high zones and the patient's marker.
 */
@Composable
fun LabRangeBar(
    value: Double,
    refLow: Double,
    refHigh: Double,
    flag: LabFlag,
    modifier: Modifier = Modifier
) {
    val markerColor = when (flag) {
        LabFlag.NORMAL -> SuccessGreen
        LabFlag.HIGH, LabFlag.LOW -> WarningAmber
        LabFlag.CRITICAL -> CriticalRed
    }
    val span = (refHigh - refLow).coerceAtLeast(1.0)
    val minAxis = (refLow - span * 0.45).coerceAtLeast(0.0)
    val maxAxis = refHigh + span * 0.45
    val totalAxis = (maxAxis - minAxis).coerceAtLeast(1.0)
    val lowFraction = ((refLow - minAxis) / totalAxis).toFloat().coerceIn(0.05f, 0.45f)
    val highFraction = ((refHigh - minAxis) / totalAxis).toFloat().coerceIn(0.55f, 0.95f)
    val valueFraction = ((value - minAxis) / totalAxis).toFloat().coerceIn(0.04f, 0.96f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val barHeight = 8.dp.toPx()
        val yTop = (size.height - barHeight) / 2f
        val w = size.width

        // Low warning segment
        drawRoundRect(
            color = WarningAmber.copy(alpha = 0.35f),
            topLeft = Offset(0f, yTop),
            size = Size(w * lowFraction, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Normal green segment
        drawRect(
            color = SuccessGreen.copy(alpha = 0.75f),
            topLeft = Offset(w * lowFraction, yTop),
            size = Size(w * (highFraction - lowFraction), barHeight)
        )
        // High warning segment
        drawRoundRect(
            color = CriticalRed.copy(alpha = 0.45f),
            topLeft = Offset(w * highFraction, yTop),
            size = Size(w * (1f - highFraction), barHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Value marker pin
        val cx = w * valueFraction
        val cy = size.height / 2f
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = markerColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * Custom Canvas multi-point clinical laboratory trend chart with reference zone shading.
 */
@Composable
fun LabTrendChart(
    results: List<LabResultEntity>,
    modifier: Modifier = Modifier
) {
    val lang = LocalAppLanguage.current
    if (results.isEmpty()) return
    val sorted = results.sortedBy { it.resultDateMillis }
    val refLow = sorted.last().referenceLow
    val refHigh = sorted.last().referenceHigh
    val values = sorted.map { it.value }
    val minVal = minOf(values.minOrNull() ?: 0.0, refLow) * 0.85
    val maxVal = maxOf(values.maxOrNull() ?: 10.0, refHigh) * 1.15
    val range = (maxVal - minVal).coerceAtLeast(0.5)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height

            fun yFor(v: Double): Float {
                val norm = ((v - minVal) / range).toFloat().coerceIn(0f, 1f)
                return h - (norm * h)
            }

            // Target normal reference band
            val bandTop = yFor(refHigh)
            val bandBottom = yFor(refLow)
            drawRect(
                color = SuccessGreen.copy(alpha = 0.12f),
                topLeft = Offset(0f, bandTop),
                size = Size(w, (bandBottom - bandTop).coerceAtLeast(2f))
            )

            // Grid lines
            for (i in 0..2) {
                val gy = h * (i / 2f)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val points = if (sorted.size == 1) {
                listOf(Offset(w / 2f, yFor(sorted.first().value)))
            } else {
                sorted.mapIndexed { idx, lab ->
                    val x = (idx.toFloat() / (sorted.size - 1)) * w
                    Offset(x, yFor(lab.value))
                }
            }

            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = MedicalTeal,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            points.forEachIndexed { index, pt ->
                val lab = sorted[index]
                val dotColor = if (lab.value in refLow..refHigh) SuccessGreen else WarningAmber
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = pt)
                drawCircle(color = dotColor, radius = 4.dp.toPx(), center = pt)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            sorted.forEach { lab ->
                Text(
                    text = "${LocaleFormatter.formatNumber(lab.value, lang)} • ${LocaleFormatter.formatDate(lab.resultDateMillis, lang)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LightTeal)
                    .border(1.dp, MedicalTeal.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = title,
                    tint = MedicalTeal
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
