package by.mlastovsky.kosht.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F2CD),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3D6473),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E9FB),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2EC),
    inversePrimary = Color(0xFF89D6B2),
    surfaceTint = Color(0xFF176B4E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFE9),
    surfaceContainerHigh = Color(0xFFE3EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DE)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF89D6B2),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA4F2CD),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA5CDDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E9FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF176B4E),
    surfaceTint = Color(0xFF89D6B2),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632)
)

@Immutable
data class KoshtColors(
    val income: Color,
    val onIncomeContainer: Color,
    val incomeContainer: Color,
    val expense: Color,
    val onExpenseContainer: Color,
    val expenseContainer: Color
)

val LightKoshtColors = KoshtColors(
    income = Color(0xFF1E8E5A),
    incomeContainer = Color(0xFFD3F2DF),
    onIncomeContainer = Color(0xFF00391F),
    expense = Color(0xFFC2413A),
    expenseContainer = Color(0xFFFFE0DC),
    onExpenseContainer = Color(0xFF3E0300)
)

val DarkKoshtColors = KoshtColors(
    income = Color(0xFF7EDCA9),
    incomeContainer = Color(0xFF1C4531),
    onIncomeContainer = Color(0xFFC9F5DC),
    expense = Color(0xFFFF9D93),
    expenseContainer = Color(0xFF57201B),
    onExpenseContainer = Color(0xFFFFE0DC)
)

val LocalKoshtColors = staticCompositionLocalOf { LightKoshtColors }
