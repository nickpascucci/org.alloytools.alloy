package edu.mit.csail.sdg.alloy4;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * The light and dark color themes of the application.
 * <p>
 * Colors obtained from {@link #color(int, int)} and the constants of this class
 * follow the active theme at paint time, so a component that was given one of
 * them needs only a repaint after {@link #setDark(boolean)}.
 * <p>
 * Drawings that were colored for a white canvas (the instance graphs) are
 * carried over to the dark canvas with {@link #onDark(Color)}.
 */
public final class OurTheme {

    /** The user's choice of theme. */
    public enum Appearance {

                            /** Follow the operating system's light/dark setting. */
                            SYSTEM("Follow system"),
                            /** Always use the light theme. */
                            LIGHT("Light"),
                            /** Always use the dark theme. */
                            DARK("Dark");

        private final String label;

        private Appearance(String label) {
            this.label = label;
        }

        /**
         * Returns true if this choice calls for the dark theme, consulting systemDark
         * only when the choice is to follow the system.
         */
        public boolean isDark(BooleanSupplier systemDark) {
            return this == DARK || (this == SYSTEM && systemDark.getAsBoolean());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** A color whose value depends on the active theme. */
    private static final class Themed extends Color {

        private static final long serialVersionUID = 0;

        private final int         light, dark;

        Themed(int lightRGB, int darkRGB) {
            super(lightRGB);
            light = 0xFF000000 | lightRGB;
            dark = 0xFF000000 | darkRGB;
        }

        /** Every other accessor of Color derives its answer from this one. */
        @Override
        public int getRGB() {
            return OurTheme.dark ? dark : light;
        }
    }

    private static volatile boolean dark;

    // ---- text areas -----------------------------------------------------

    /** The background of text areas and of the graph canvas. */
    public static final Color       TEXT_BACKGROUND      = color(0xFFFFFF, 0x1E1E1E);

    /** Regular text. */
    public static final Color       TEXT                 = color(0x000000, 0xD4D4D4);

    /** Secondary text such as hints. */
    public static final Color       TEXT_MUTED           = color(0x808080, 0x8C8C8C);

    /** Text that reports an error. */
    public static final Color       TEXT_ERROR           = color(0xFF0000, 0xFF6B68);

    /** Text that reports a successful result. */
    public static final Color       TEXT_GOOD            = color(0x0000FF, 0x6CA8FF);

    /** Error text in the message log, and the "modified" marker. */
    public static final Color       TEXT_WARNING         = color(0xB33333, 0xF28B82);

    /** A clickable link. */
    public static final Color       LINK                 = color(0x4D4DE6, 0x7FA8FF);

    /** A clickable link under the mouse. */
    public static final Color       LINK_HOVER           = color(0xE64D4D, 0xFF8A80);

    // ---- syntax highlighting ---------------------------------------------

    public static final Color       SYNTAX_NUMBER        = color(0xA80A0A, 0xF28B82);
    public static final Color       SYNTAX_KEYWORD       = color(0x1E1EA8, 0x7FA8FF);
    public static final Color       SYNTAX_STRING        = color(0xA80AA8, 0xD69AE0);
    public static final Color       SYNTAX_COMMENT       = color(0x0A940A, 0x6AAB73);
    public static final Color       SYNTAX_HEADING       = color(0x772222, 0xE8A0A0);
    public static final Color       SYNTAX_YAML_BAR      = color(0xD86556, 0xD86556);
    public static final Color       SYNTAX_YAML_LINE     = color(0xC58D6D, 0xC58D6D);
    public static final Color       SYNTAX_MARKER        = color(0x84D5D0, 0x3F7672);

    // ---- highlights behind text -------------------------------------------

    /** The strongest of the three unsat core shades. */
    public static final Color       CORE_STRONG          = color(0xF21A1A, 0x9C2222);
    public static final Color       CORE                 = color(0xE66666, 0x742C2C);
    public static final Color       CORE_WEAK            = color(0xE6B3B3, 0x4A2727);

    /** An occurrence found by a text search. */
    public static final Color       FOUND                = color(0xFFFF00, 0x665500);

    /** A text field whose content is invalid. */
    public static final Color       INVALID              = color(0xFFC8C8, 0x742C2C);

    // ---- chrome -----------------------------------------------------------

    /** Toolbars, status bars and other surfaces around the content. */
    public static final Color       PANEL                = color(0xE6E6E6, 0x2B2D30);

    /** Thin lines that separate regions. */
    public static final Color       BORDER               = color(0xC0C0C0, 0x45474A);

    /** A stronger outline, such as around a text field. */
    public static final Color       OUTLINE              = color(0x404040, 0x6A6D70);

    /** An editor tab that is not showing. */
    public static final Color       TAB_INACTIVE         = color(0xCCCCCC, 0x2B2D30);

    /** An editor tab that is not showing and holds a highlight. */
    public static final Color       TAB_INACTIVE_MARKED  = color(0xB38080, 0x6B3A3A);

    /** The label of the showing editor tab when it holds a highlight. */
    public static final Color       TAB_ACTIVE_MARKED    = color(0x803333, 0xF28B82);

    /** The header of a selected item in a list of panels. */
    public static final Color       HEADER_SELECTED      = color(0xC8E1FF, 0x2F4F78);

    /** The header of an item in a list of panels. */
    public static final Color       HEADER               = color(0xEEEEEE, 0x2B2D30);

    /** A state of a trace that lies outside the states on show. */
    public static final Color       TRACE_OUTSIDE        = color(0x787878, 0x4E5154);

    /** The line number of the line that holds the caret, and the bar behind it. */
    public static final Color       CURRENT_LINE_NUMBER  = color(0xFFFF00, 0x1E1E1E);
    public static final Color       CURRENT_LINE_BAR     = color(0x000000, 0xD7BA7D);

    private OurTheme() {}

    /** Returns every foreground color that is drawn as running text on {@link #TEXT_BACKGROUND}. */
    static List<Color> textColors() {
        return Arrays.asList(TEXT, TEXT_ERROR, TEXT_GOOD, TEXT_WARNING, LINK, SYNTAX_NUMBER, SYNTAX_KEYWORD, SYNTAX_STRING, SYNTAX_COMMENT, SYNTAX_HEADING);
    }

    /** Returns true if the dark theme is active. */
    public static boolean isDark() {
        return dark;
    }

    /**
     * Switches between the light and the dark theme. Components that are already
     * showing must be repainted by the caller.
     */
    public static void setDark(boolean dark) {
        OurTheme.dark = dark;
    }

    /** Returns a color that is lightRGB in the light theme and darkRGB in the dark theme. */
    public static Color color(int lightRGB, int darkRGB) {
        return new Themed(lightRGB, darkRGB);
    }

    /** Returns the given color as a "#rrggbb" string, as it is in the active theme. */
    public static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    // ---- carrying a white-canvas drawing over to the dark canvas ------------

    /** The lightness, in OKLab, of the dark canvas and of ink on it. */
    private static final double CANVAS = lab(0x1E, 0x1E, 0x1E)[0], INK = 0.93;

    /**
     * Mid-tones are lifted by this exponent, since a saturated color needs more
     * lightness to stand out from a dark canvas than it needs darkness to stand out
     * from a white one.
     */
    private static final double LIFT   = 0.6;

    /** Remembers the answers of {@link #onDark(Color)}, keyed by ARGB. */
    private static final Map<Integer,Color> CARRIED = new ConcurrentHashMap<Integer,Color>();

    /**
     * Maps a color chosen for a white canvas to its counterpart on the dark canvas.
     * <p>
     * Perceptual lightness is reversed (white becomes the dark canvas, black
     * becomes light ink) while hue is kept, so colors remain recognizable and text
     * keeps its contrast against the fill behind it. Alpha is preserved.
     */
    public static Color onDark(Color c) {
        if (CARRIED.size() > 4096)
            CARRIED.clear();
        return CARRIED.computeIfAbsent(c.getRGB(), argb -> carry(new Color(argb, true)));
    }

    private static Color carry(Color c) {
        double[] lab = lab(c.getRed(), c.getGreen(), c.getBlue());
        double l = CANVAS + Math.pow(Math.max(0, 1 - lab[0]), LIFT) * (INK - CANVAS);
        // shrink the chroma until the color fits in sRGB at its new lightness
        double lo = 0, hi = 1;
        double[] rgb = rgb(l, lab[1], lab[2]);
        if (!displayable(rgb)) {
            for (int i = 0; i < 20; i++) {
                double mid = (lo + hi) / 2;
                if (displayable(rgb(l, lab[1] * mid, lab[2] * mid)))
                    lo = mid;
                else
                    hi = mid;
            }
            rgb = rgb(l, lab[1] * lo, lab[2] * lo);
        }
        return new Color(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]), c.getAlpha());
    }

    /** Returns the WCAG contrast ratio (1..21) between two colors. */
    public static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * decode(c.getRed()) + 0.7152 * decode(c.getGreen()) + 0.0722 * decode(c.getBlue());
    }

    /** Converts an sRGB channel (0..255) to linear light (0..1). */
    private static double decode(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Converts linear light to an sRGB channel (0..255). */
    private static int encode(double linear) {
        double v = Math.min(1, Math.max(0, linear));
        v = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }

    private static boolean displayable(double[] linearRGB) {
        final double eps = 1e-4;
        for (double v : linearRGB)
            if (v < -eps || v > 1 + eps)
                return false;
        return true;
    }

    /** Converts sRGB to OKLab {L, a, b}. */
    private static double[] lab(int red, int green, int blue) {
        double r = decode(red), g = decode(green), b = decode(blue);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
                             0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s, 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }

    /** Converts OKLab to linear sRGB, which may lie outside 0..1. */
    private static double[] rgb(double L, double a, double b) {
        double l = L + 0.3963377774 * a + 0.2158037573 * b;
        double m = L - 0.1055613458 * a - 0.0638541728 * b;
        double s = L - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[] {
                             4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s, -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        };
    }
}
