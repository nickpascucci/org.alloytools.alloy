package edu.mit.csail.sdg.alloy4whole;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.After;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurTheme;

public class AppThemeTest {

    @After
    public void reset() throws Exception {
        SwingUtilities.invokeAndWait(() -> AppTheme.apply(false));
    }

    private static double brightness(Color c) {
        return (c.getRed() + c.getGreen() + c.getBlue()) / 3.0;
    }

    @Test
    public void picksTheLookAndFeelForThemeAndPlatform() {
        assertEquals("com.formdev.flatlaf.FlatLightLaf", AppTheme.lookAndFeel(false, false));
        assertEquals("com.formdev.flatlaf.FlatDarkLaf", AppTheme.lookAndFeel(true, false));
        assertEquals("com.formdev.flatlaf.themes.FlatMacLightLaf", AppTheme.lookAndFeel(false, true));
        assertEquals("com.formdev.flatlaf.themes.FlatMacDarkLaf", AppTheme.lookAndFeel(true, true));
    }

    @Test
    public void applyingSwitchesWidgetsAndThemeColorsTogether() throws Exception {
        SwingUtilities.invokeAndWait(() -> AppTheme.apply(true));
        assertTrue(OurTheme.isDark());
        assertTrue(brightness(UIManager.getColor("Panel.background")) < 100);
        assertTrue(brightness(UIManager.getColor("Label.foreground")) > 150);

        SwingUtilities.invokeAndWait(() -> AppTheme.apply(false));
        assertFalse(OurTheme.isDark());
        assertTrue(brightness(UIManager.getColor("Panel.background")) > 200);
    }

    @Test
    public void listenersHearEverySwitchButNoRepeats() throws Exception {
        int[] heard = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.apply(false);
            AppTheme.addListener(() -> heard[0]++);
            AppTheme.apply(true);
            AppTheme.apply(true);
            AppTheme.apply(false);
        });
        assertEquals(2, heard[0]);
    }
}
