package edu.mit.csail.sdg.alloy4whole;

import java.awt.Window;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import edu.mit.csail.sdg.alloy4.A4Preferences;
import edu.mit.csail.sdg.alloy4.OurTheme;
import edu.mit.csail.sdg.alloy4.OurTheme.Appearance;
import edu.mit.csail.sdg.alloy4.SystemAppearance;
import edu.mit.csail.sdg.alloy4.Util;

/**
 * Applies the light or dark theme to the whole application: the Swing look and
 * feel, the colors of {@link OurTheme}, and every open window.
 */
public final class AppTheme {

    /** How often, in milliseconds, the operating system's appearance is checked. */
    private static final int            POLL_INTERVAL = 3000;

    private static final List<Runnable> listeners     = new CopyOnWriteArrayList<Runnable>();

    /** The theme that is currently installed, or null before the first one is. */
    private static Boolean              installed;

    /** The most recent answer from the operating system. */
    private static volatile boolean     systemDark;

    private static Thread               watcher;

    private AppTheme() {}

    /** Returns the class name of the look and feel for the given theme and platform. */
    static String lookAndFeel(boolean dark, boolean mac) {
        if (mac)
            return dark ? "com.formdev.flatlaf.themes.FlatMacDarkLaf" : "com.formdev.flatlaf.themes.FlatMacLightLaf";
        return dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf";
    }

    /**
     * Tells macOS which appearance to give window title bars; this takes effect
     * only if called before the first window toolkit call of the process.
     */
    public static void prepare() {
        if (!Util.onMac())
            return;
        Appearance choice = A4Preferences.Appearance.get();
        System.setProperty("apple.awt.application.appearance", choice == Appearance.SYSTEM ? "system" : choice == Appearance.DARK ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
    }

    /**
     * Switches the application to the dark or the light theme and restyles every
     * open window; must be called on the event thread.
     */
    public static void apply(boolean dark) {
        if (installed != null && installed == dark)
            return;
        OurTheme.setDark(dark);
        try {
            UIManager.setLookAndFeel(lookAndFeel(dark, Util.onMac()));
        } catch (Exception | LinkageError e) {
            // the themed colors still apply on top of whichever look and feel remains
        }
        installed = dark;
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
            w.repaint();
        }
        for (Runnable listener : listeners)
            listener.run();
    }

    /**
     * Applies the theme chosen in the user's preferences; must be called on the
     * event thread.
     */
    public static void applyPreference() {
        Appearance choice = A4Preferences.Appearance.get();
        if (choice == Appearance.SYSTEM)
            systemDark = SystemAppearance.isDark();
        apply(choice.isDark(() -> systemDark));
    }

    /**
     * Starts watching the operating system's appearance, so that the application
     * follows it for as long as the user's preference says to.
     */
    public static synchronized void followSystem() {
        if (watcher != null)
            return;
        watcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_INTERVAL);
                } catch (InterruptedException e) {
                    return;
                }
                if (A4Preferences.Appearance.get() != Appearance.SYSTEM)
                    continue;
                final boolean now = SystemAppearance.isDark();
                if (now == systemDark)
                    continue;
                systemDark = now;
                SwingUtilities.invokeLater(() -> {
                    if (A4Preferences.Appearance.get() == Appearance.SYSTEM)
                        apply(now);
                });
            }
        }, "system-appearance-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Registers a callback that runs on the event thread after each theme switch. */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }
}
