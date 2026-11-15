package edu.mit.csail.sdg.alloy4;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;

import org.junit.After;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurTheme.Appearance;

public class OurThemeTest {

    @After
    public void reset() {
        OurTheme.setDark(false);
    }

    @Test
    public void appearanceResolvesToDarkOrLight() {
        assertThat(Appearance.LIGHT.isDark(() -> true)).isFalse();
        assertThat(Appearance.DARK.isDark(() -> false)).isTrue();
        assertThat(Appearance.SYSTEM.isDark(() -> true)).isTrue();
        assertThat(Appearance.SYSTEM.isDark(() -> false)).isFalse();
    }

    @Test
    public void themedColorFollowsTheActiveTheme() {
        Color c = OurTheme.color(0x112233, 0xAABBCC);
        assertThat(c.getRGB() & 0xFFFFFF).isEqualTo(0x112233);
        OurTheme.setDark(true);
        assertThat(OurTheme.isDark()).isTrue();
        assertThat(c.getRGB() & 0xFFFFFF).isEqualTo(0xAABBCC);
        assertThat(c.getRed()).isEqualTo(0xAA);
        assertThat(c.getAlpha()).isEqualTo(255);
        OurTheme.setDark(false);
        assertThat(c.getBlue()).isEqualTo(0x33);
    }

    @Test
    public void whiteCanvasBecomesTheDarkCanvas() {
        OurTheme.setDark(true);
        assertThat(OurTheme.onDark(Color.WHITE).getRGB()).isEqualTo(OurTheme.TEXT_BACKGROUND.getRGB());
    }

    @Test
    public void inkBecomesLightAndStaysNeutral() {
        Color ink = OurTheme.onDark(Color.BLACK);
        assertThat(ink.getRed()).isEqualTo(ink.getGreen()).isEqualTo(ink.getBlue());
        assertThat(OurTheme.contrast(ink, OurTheme.onDark(Color.WHITE))).isGreaterThan(10);
    }

    @Test
    public void lightnessOrderIsReversed() {
        Color[] ramp = {
                        Color.BLACK, Color.DARK_GRAY, Color.GRAY, Color.LIGHT_GRAY, Color.WHITE
        };
        for (int i = 1; i < ramp.length; i++)
            assertThat(OurTheme.onDark(ramp[i]).getGreen()).isLessThan(OurTheme.onDark(ramp[i - 1]).getGreen());
    }

    @Test
    public void huesSurvive() {
        for (Color c : new Color[] {
                                    Color.RED, Color.BLUE, new Color(0, 158, 115), new Color(255, 215, 0), new Color(231, 138, 195)
        }) {
            float[] before = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            Color d = OurTheme.onDark(c);
            float[] after = Color.RGBtoHSB(d.getRed(), d.getGreen(), d.getBlue(), null);
            float dh = Math.abs(before[0] - after[0]);
            assertThat(Math.min(dh, 1 - dh)).as("hue of %s -> %s", c, d).isLessThan(0.06f);
            assertThat(after[1]).as("saturation of %s -> %s", c, d).isGreaterThan(0.25f);
        }
    }

    /** Text that was readable on its fill stays readable after both are mapped. */
    @Test
    public void readablePairsStayReadable() {
        Color[][] pairs = {
                           {
                            Color.BLACK, Color.WHITE
                           }, {
                               Color.BLACK, new Color(255, 215, 0)
                           }, {
                               Color.WHITE, Color.BLUE
                           }, {
                               Color.WHITE, Color.BLACK
                           }, {
                               Color.BLACK, new Color(166, 216, 84)
                           }, {
                               Color.BLUE, Color.WHITE
                           }, {
                               Color.RED, Color.WHITE
                           }
        };
        for (Color[] p : pairs)
            assertThat(OurTheme.contrast(OurTheme.onDark(p[0]), OurTheme.onDark(p[1]))).as("%s on %s", p[0], p[1]).isGreaterThan(3.5);
    }

    @Test
    public void translucencyIsKept() {
        assertThat(OurTheme.onDark(new Color(255, 255, 255, 160)).getAlpha()).isEqualTo(160);
    }

    @Test
    public void contrastMatchesWcag() {
        assertThat(OurTheme.contrast(Color.BLACK, Color.WHITE)).isBetween(20.9, 21.1);
        assertThat(OurTheme.contrast(Color.WHITE, Color.WHITE)).isBetween(0.99, 1.01);
    }

    /**
     * Every piece of themed text is legible on the themed text background. The
     * dark theme meets WCAG AA; the light theme keeps its long-standing colors.
     */
    @Test
    public void textColorsAreLegibleInBothThemes() {
        for (boolean dark : new boolean[] {
                                           false, true
        }) {
            OurTheme.setDark(dark);
            for (Color c : OurTheme.textColors())
                assertThat(OurTheme.contrast(c, OurTheme.TEXT_BACKGROUND)).as("dark=%s color=%06x", dark, c.getRGB() & 0xFFFFFF).isGreaterThan(dark ? 4.5 : 3.9);
        }
    }
}
