package by.mlastovsky.kosht.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import by.mlastovsky.kosht.R

private val Rubel = FontFamily(Font(R.font.rubel))

private val Default = Typography()

val KoshtTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Bold, fontFamily = Rubel),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.Bold, fontFamily = Rubel),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Bold, fontFamily = Rubel),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold, fontFamily = Rubel),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = Rubel),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = Rubel),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFamily = Rubel),
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
        fontFamily = Rubel
    ),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = Rubel),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Rubel),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Rubel),
    bodySmall = Default.bodySmall.copy(fontFamily = Rubel),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.Medium, fontFamily = Rubel),
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium, fontFamily = Rubel),
    labelSmall = Default.labelSmall.copy(fontWeight = FontWeight.Medium, fontFamily = Rubel)
)
