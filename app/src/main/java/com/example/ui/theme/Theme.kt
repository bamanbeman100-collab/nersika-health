package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.EnStrings
import com.example.i18n.FaStrings

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnStrings }

private val NersikaLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = LightTeal,
    onPrimaryContainer = NavyPrimary,
    secondary = MedicalTeal,
    onSecondary = Color.White,
    secondaryContainer = InfoContainer,
    onSecondaryContainer = NavySecondary,
    tertiary = NavySecondary,
    onTertiary = Color.White,
    background = BgLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = CriticalRed,
    onError = Color.White,
    errorContainer = CriticalContainer,
    onErrorContainer = CriticalRed
)

private val NersikaDarkColorScheme = darkColorScheme(
    primary = DarkTeal,
    onPrimary = DarkBg,
    primaryContainer = DarkTealContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = MedicalTeal,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = NavySecondary,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFFF6B6B),
    onError = DarkBg,
    errorContainer = Color(0xFF4A1818),
    onErrorContainer = Color(0xFFFFD6D6)
)

val NersikaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun NersikaTheme(
    language: AppLanguage,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NersikaDarkColorScheme else NersikaLightColorScheme
    val layoutDirection = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = if (language == AppLanguage.FA) FaStrings else EnStrings

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides language,
        LocalAppStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = nersikaTypography(language),
            shapes = NersikaShapes,
            content = content
        )
    }
}
