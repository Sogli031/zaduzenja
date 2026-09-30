package com.zaduzenja.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Primary = Color(0xFF0F766E)
    val PrimaryDark = Color(0xFF0B5D57)
    val Accent = Color(0xFFF97316)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFD97706)
    val Error = Color(0xFFDC2626)

    val Background = Color(0xFFFAF7F1)
    val BackgroundAccent = Color(0x66DFF3EF)
    val BackgroundGlow = Color(0x55FFE1C2)

    val Surface = Color(0xFFFFFFFF)
    val SurfaceTint = Color(0xFFF3F5F6)
    val SurfaceMuted = Color(0xFFECE9E2)
    val Outline = Color(0xFFE1D9CD)
    val Shadow = Color(0x14000000)

    val TextPrimary = Color(0xFF1C1C1C)
    val TextSecondary = Color(0xFF5A5A5A)
    val TextMuted = Color(0xFF767676)
    val TextInvert = Color(0xFFFFFFFF)

    val Majica = Color(0xFF2563EB)
    val Cipele = Color(0xFFEA580C)
    val Odelo = Color(0xFF15803D)
}

object AppDimens {
    val Padding = 18.dp
    val Margin = 10.dp
    val BorderRadius = 18.dp
    val CardRadius = 18.dp
    val ChipRadius = 14.dp
    val AppBarMinHeight = 72.dp
    val NavHeight = 64.dp
    val ButtonHeight = 48.dp
}

object AppTextSizes {
    val Title = 26.sp
    val Header = 18.sp
    val Body = 15.sp
    val Caption = 12.sp
    val Tiny = 11.sp
}

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk_wght, weight = FontWeight.Normal),
    Font(R.font.space_grotesk_wght, weight = FontWeight.SemiBold)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Title),
    titleMedium = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Header),
    bodyLarge = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Body),
    bodyMedium = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Caption),
    bodySmall = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Tiny),
    labelMedium = TextStyle(fontFamily = SpaceGroteskFamily, fontSize = AppTextSizes.Tiny)
)

@Composable
fun ZaduzenjaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Primary,
            onPrimary = AppColors.TextInvert,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onSurface = AppColors.TextPrimary,
            onSurfaceVariant = AppColors.TextSecondary,
            error = AppColors.Error
        ),
        typography = AppTypography,
        content = content
    )
}
