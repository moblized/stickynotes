package app.stickynotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.stickynotes.R

// ------------------------------------------------------------------ Sticky note colors

enum class NoteColor(val label: String, val light: Color, val dark: Color) {
    LEMON("Lemon", Color(0xFFFFF0A0), Color(0xFF5A5120)),
    PEACH("Peach", Color(0xFFFFD6B0), Color(0xFF6A4426)),
    PINK("Pink", Color(0xFFFFCCDD), Color(0xFF6A2E46)),
    LAVENDER("Lavender", Color(0xFFE2D4FF), Color(0xFF4A3A6E)),
    SKY("Sky", Color(0xFFC8E5FF), Color(0xFF274A66)),
    MINT("Mint", Color(0xFFC6F1DA), Color(0xFF285442)),
    LIME("Lime", Color(0xFFE4F4AE), Color(0xFF4A5620)),
    SAND("Sand", Color(0xFFEDE2CF), Color(0xFF4F4639)),
    PAPER("Paper", Color(0xFFFFFFFF), Color(0xFF3A3936));

    fun color(dark: Boolean): Color = if (dark) this.dark else light

    companion object {
        fun of(index: Int): NoteColor = entries.getOrElse(index) { LEMON }
    }
}

/** Ink for text on a sticky note. */
fun noteInk(dark: Boolean): Color = if (dark) Color(0xFFF2EEE4) else Color(0xFF2C2A25)

/** The darker paper behind a sticky note's folded corner. */
fun foldColor(base: Color, dark: Boolean): Color =
    lerp(base, if (dark) Color.Black else Color(0xFF8A6A2A), if (dark) 0.35f else 0.22f)

// ------------------------------------------------------------------ Handwriting fonts

@OptIn(ExperimentalTextApi::class)
private val caveat = FontFamily(
    Font(R.font.caveat, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.caveat, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** The note fonts offered in Settings. [scale] evens out how big each one looks. */
enum class NoteFont(val label: String, val family: FontFamily, val scale: Float) {
    CAVEAT("Caveat", caveat, 1.22f),
    PATRICK_HAND("Patrick Hand", FontFamily(Font(R.font.patrick_hand)), 1.06f),
    INDIE_FLOWER("Indie Flower", FontFamily(Font(R.font.indie_flower)), 1.0f),
    KALAM(
        "Kalam",
        FontFamily(Font(R.font.kalam_regular), Font(R.font.kalam_bold, FontWeight.Bold)),
        0.98f,
    ),
    SHADOWS_INTO_LIGHT("Shadows Into Light", FontFamily(Font(R.font.shadows_into_light)), 1.08f),
    GOCHI_HAND("Gochi Hand", FontFamily(Font(R.font.gochi_hand)), 1.06f),
    SYSTEM("Plain (no handwriting)", FontFamily.Default, 0.94f);

    companion object {
        fun of(name: String): NoteFont = entries.firstOrNull { it.name == name } ?: CAVEAT
    }
}

/** Text styles for note content, built from the chosen font. */
data class NoteTypography(
    val font: NoteFont,
    val title: TextStyle,
    val body: TextStyle,
    val cardTitle: TextStyle,
    val cardBody: TextStyle,
) {
    companion object {
        fun from(font: NoteFont): NoteTypography {
            val s = font.scale
            return NoteTypography(
                font = font,
                title = TextStyle(fontFamily = font.family, fontWeight = FontWeight.Bold, fontSize = (23 * s).sp, lineHeight = (29 * s).sp),
                body = TextStyle(fontFamily = font.family, fontSize = (19 * s).sp, lineHeight = (26 * s).sp),
                cardTitle = TextStyle(fontFamily = font.family, fontWeight = FontWeight.Bold, fontSize = (18 * s).sp, lineHeight = (22 * s).sp),
                cardBody = TextStyle(fontFamily = font.family, fontSize = (15.5f * s).sp, lineHeight = (20 * s).sp),
            )
        }
    }
}

val LocalNoteTypography = staticCompositionLocalOf { NoteTypography.from(NoteFont.CAVEAT) }
val LocalDarkTheme = compositionLocalOf { false }

// ------------------------------------------------------------------ App theme

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6B5D3F),
    secondaryContainer = Color(0xFFF4E1BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4A6547),
    background = Color(0xFFF8F4EA),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFF8F4EA),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEBE2CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3EEE2),
    surfaceContainer = Color(0xFFEFE9DB),
    surfaceContainerHigh = Color(0xFFE9E3D5),
    surfaceContainerHighest = Color(0xFFE3DDCF),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEBC348),
    onPrimary = Color(0xFF3F2E00),
    primaryContainer = Color(0xFF5B4400),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFD7C5A0),
    secondaryContainer = Color(0xFF52462A),
    onSecondaryContainer = Color(0xFFF4E1BB),
    tertiary = Color(0xFFB0CFA9),
    background = Color(0xFF1B1A17),
    onBackground = Color(0xFFE9E2D6),
    surface = Color(0xFF1B1A17),
    onSurface = Color(0xFFE9E2D6),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFF151412),
    surfaceContainerLow = Color(0xFF22201C),
    surfaceContainer = Color(0xFF262420),
    surfaceContainerHigh = Color(0xFF312E29),
    surfaceContainerHighest = Color(0xFF3C3934),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4D4639),
)

/** Dark scheme with true-black backgrounds; raised surfaces stay just off-black so they're still visible. */
private val PureBlackColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0A),
    surfaceContainer = Color(0xFF121211),
    surfaceContainerHigh = Color(0xFF1A1A18),
    surfaceContainerHighest = Color(0xFF232320),
    surfaceVariant = Color(0xFF2A2824),
)

@Composable
fun StickyTheme(
    darkTheme: Boolean,
    noteFont: NoteFont,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalNoteTypography provides NoteTypography.from(noteFont),
    ) {
        MaterialTheme(
            colorScheme = when {
                darkTheme && pureBlack -> PureBlackColors
                darkTheme -> DarkColors
                else -> LightColors
            },
            content = content,
        )
    }
}
