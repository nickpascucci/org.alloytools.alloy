package edu.mit.csail.sdg.alloy4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class SystemAppearanceTest {

    @Test
    public void mac() {
        assertThat(SystemAppearance.macIsDark(0, "Dark\n")).isTrue();
        // the key is absent in light mode, so the command fails
        assertThat(SystemAppearance.macIsDark(1, "The domain/default pair of (kCFPreferencesAnyApplication, AppleInterfaceStyle) does not exist\n")).isFalse();
        assertThat(SystemAppearance.macIsDark(0, "")).isFalse();
    }

    @Test
    public void windows() {
        String head = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n    AppsUseLightTheme    REG_DWORD    ";
        assertThat(SystemAppearance.windowsIsDark(head + "0x0\r\n\r\n")).isTrue();
        assertThat(SystemAppearance.windowsIsDark(head + "0x1\r\n\r\n")).isFalse();
        assertThat(SystemAppearance.windowsIsDark("ERROR: The system was unable to find the specified registry key or value.")).isFalse();
    }

    @Test
    public void gnome() {
        assertThat(SystemAppearance.gnomeIsDark("'prefer-dark'\n", "'Adwaita'\n")).isTrue();
        assertThat(SystemAppearance.gnomeIsDark("'default'\n", "'Adwaita'\n")).isFalse();
        assertThat(SystemAppearance.gnomeIsDark("'prefer-light'\n", "'Adwaita-dark'\n")).isFalse();
        // desktops that predate color-scheme signal dark mode through the theme name
        assertThat(SystemAppearance.gnomeIsDark("", "'Yaru-dark'\n")).isTrue();
        assertThat(SystemAppearance.gnomeIsDark("'default'\n", "'Adwaita-dark'\n")).isTrue();
        assertThat(SystemAppearance.gnomeIsDark("", "")).isFalse();
    }
}
