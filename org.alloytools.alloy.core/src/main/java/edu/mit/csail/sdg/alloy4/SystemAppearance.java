package edu.mit.csail.sdg.alloy4;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Detects whether the operating system is currently set to a dark appearance. */
public final class SystemAppearance {

    private static final Pattern WINDOWS_DARK = Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x0\\b");

    private SystemAppearance() {}

    /**
     * Interprets the result of <code>defaults read -g AppleInterfaceStyle</code>; the
     * key exists only while the dark appearance is active.
     */
    static boolean macIsDark(int exitCode, String output) {
        return exitCode == 0 && output.trim().equalsIgnoreCase("Dark");
    }

    /**
     * Interprets the output of <code>reg query ...\Themes\Personalize /v AppsUseLightTheme</code>.
     */
    static boolean windowsIsDark(String output) {
        return WINDOWS_DARK.matcher(output).find();
    }

    /**
     * Interprets the output of <code>gsettings get org.gnome.desktop.interface color-scheme</code>
     * and of <code>... gtk-theme</code>. An explicit color scheme wins; desktops
     * without one signal the dark appearance through the theme's name.
     */
    static boolean gnomeIsDark(String colorScheme, String gtkTheme) {
        if (colorScheme.contains("prefer-dark"))
            return true;
        if (colorScheme.contains("prefer-light"))
            return false;
        return gtkTheme.toLowerCase().contains("dark");
    }

    /**
     * Returns true if the operating system currently uses a dark appearance. This
     * runs a short-lived system command, so call it off the event thread when
     * polling. An appearance that cannot be determined counts as light.
     */
    public static boolean isDark() {
        try {
            if (Util.onMac()) {
                String[] out = new String[1];
                int exit = run(out, "defaults", "read", "-g", "AppleInterfaceStyle");
                return macIsDark(exit, out[0]);
            }
            if (Util.onWindows()) {
                String[] out = new String[1];
                run(out, "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
                return windowsIsDark(out[0]);
            }
            String[] scheme = new String[1], theme = new String[1];
            run(scheme, "gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            run(theme, "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
            return gnomeIsDark(scheme[0], theme[0]);
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs a command, stores its standard output in out[0], and returns its exit code. */
    private static int run(String[] out, String... command) throws Exception {
        out[0] = "";
        Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
        try {
            p.getOutputStream().close();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                byte[] chunk = new byte[1024];
                for (int n; buf.size() < 65536 && (n = in.read(chunk)) > 0;)
                    buf.write(chunk, 0, n);
            }
            if (!p.waitFor(2, TimeUnit.SECONDS))
                return -1;
            out[0] = buf.toString("UTF-8");
            return p.exitValue();
        } finally {
            p.destroyForcibly();
        }
    }
}
