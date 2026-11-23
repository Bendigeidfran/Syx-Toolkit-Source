package syx.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.fonts.roboto_mono.FlatRobotoMonoFont;

import syx.core.ToolkitSettings.ThemeChoice;

/**
 * The app's look, in the handbook's colours. Screens name a {@link Tone} or a text style rather than a
 * colour or font, so that they follow a change of theme.
 */
public final class Theme {

	/** The colours of one theme, as #rrggbb. */
	public record Palette(String window, String surface, String topBar, String topBarText, String accent, String link,
			String heading, String calloutBackground, String calloutEdge, String primaryButton, String primaryButtonText,
			String text, String help, String ghost, String error, String warning, String success,
			String selection, String selectionText, String border, String focus) {
	}

	/**
	 * The window, surface, top bar, accent, link, heading, callout and primary button colours are the handbook's.
	 * It has no error, warning or success colours, so those are chosen here, and ThemeTest checks they can be read.
	 */
	public static final Palette DARK = new Palette("#1e2029", "#272935", "#3a2a17", "#fbf3e2", "#d9b26f", "#d9b26f",
			"#e8d3ad", "#2e2619", "#c9962e", "#d9b26f", "#2a1e10",
			"#e3e4ea", "#a4a6ae", "#8c8e95", "#f28b82", "#e5a34a", "#9ccc65",
			"#5a4428", "#fbf3e2", "#747789", "#d9b26f");

	public static final Palette LIGHT = new Palette("#ffffff", "#f4efe6", "#3a2a17", "#fbf3e2", "#b08a4a", "#7a4f12",
			"#3a2a17", "#fbf3e2", "#c9962e", "#3a2a17", "#fbf3e2",
			"#2a1e10", "#5a4428", "#857660", "#b3261e", "#8a5a00", "#3f6b1f",
			"#e8d3ad", "#2a1e10", "#94866f", "#7a4f12");

	/** What a piece of text means, which decides its colour. */
	public enum Tone {
		HELP("toneHelp"), GHOST("toneGhost"), ERROR("toneError"), WARNING("toneWarning"), SUCCESS("toneSuccess"), HEADING("toneHeading");

		private final String styleClass;

		Tone(String styleClass) {
			this.styleClass = styleClass;
		}
	}

	private static ThemeChoice current = ThemeChoice.DARK;
	private static boolean fontsInstalled;

	private Theme() {
	}

	public static Palette palette(ThemeChoice choice) {
		return choice == ThemeChoice.LIGHT ? LIGHT : DARK;
	}

	public static ThemeChoice current() {
		return current;
	}

	/** Sets the look before any window exists. */
	public static void install(ThemeChoice choice) {
		if (!fontsInstalled) {
			FlatRobotoFont.install();
			FlatRobotoMonoFont.install();
			FlatLaf.setPreferredFontFamily(FlatRobotoFont.FAMILY);
			FlatLaf.setPreferredLightFontFamily(FlatRobotoFont.FAMILY_LIGHT);
			FlatLaf.setPreferredSemiboldFontFamily(FlatRobotoFont.FAMILY_SEMIBOLD);
			FlatLaf.setPreferredMonospacedFontFamily(FlatRobotoMonoFont.FAMILY);
			fontsInstalled = true;
		}
		current = choice;
		ToolTipManager.sharedInstance().setInitialDelay(400);
		ToolTipManager.sharedInstance().setDismissDelay(30_000);
		FlatLaf.setGlobalExtraDefaults(defaults(palette(choice)));
		FlatLaf laf = choice == ThemeChoice.LIGHT ? new FlatLightLaf() : new FlatDarkLaf();
		FlatLaf.setup(laf);
	}

	/** Changes the look of every open window, without losing anything in them. */
	public static void switchTo(ThemeChoice choice) {
		install(choice);
		FlatLaf.updateUI();
	}

	private static Map<String, String> defaults(Palette p) {
		Map<String, String> d = new LinkedHashMap<>();
		d.put("@background", p.window());
		d.put("@foreground", p.text());
		d.put("@componentBackground", p.surface());
		d.put("@buttonBackground", p.surface());
		d.put("@menuBackground", p.surface());
		d.put("@accentColor", p.accent());
		d.put("@selectionBackground", p.selection());
		d.put("@selectionForeground", p.selectionText());
		d.put("@selectionInactiveBackground", p.selection());
		d.put("@selectionInactiveForeground", p.selectionText());
		d.put("@disabledForeground", p.ghost());
		d.put("Component.borderColor", p.border());
		d.put("Component.disabledBorderColor", p.border());
		d.put("Component.focusedBorderColor", p.focus());
		d.put("Component.focusColor", p.focus());
		d.put("Button.borderColor", p.border());
		d.put("Button.focusedBorderColor", p.focus());
		d.put("TextField.inactiveForeground", p.ghost());
		d.put("TextArea.inactiveForeground", p.ghost());
		d.put("ComboBox.disabledForeground", p.ghost());
		d.put("Label.disabledForeground", p.ghost());
		d.put("TitlePane.unifiedBackground", "false");
		d.put("RootPane.activeBorderColor", p.border());
		d.put("RootPane.inactiveBorderColor", p.border());
		d.put("TitlePane.background", p.topBar());
		d.put("TitlePane.inactiveBackground", p.topBar());
		d.put("TitlePane.foreground", p.topBarText());
		d.put("TitlePane.inactiveForeground", p.topBarText());
		d.put("TitlePane.embeddedForeground", p.topBarText());
		d.put("MenuBar.background", p.topBar());
		d.put("MenuBar.foreground", p.topBarText());
		d.put("MenuBar.borderColor", p.topBar());
		d.put("Component.arc", "8");
		d.put("Button.arc", "8");
		d.put("TextComponent.arc", "8");
		d.put("Component.focusWidth", "1");
		d.put("ScrollBar.showButtons", "false");
		d.put("ScrollBar.width", "10");
		d.put("ScrollBar.thumbArc", "999");
		d.put("ScrollBar.thumbInsets", "2,2,2,2");
		d.put("[style].toneHelp", "foreground: " + p.help());
		d.put("[style].toneGhost", "foreground: " + p.ghost());
		d.put("[style].toneError", "foreground: " + p.error());
		d.put("[style].toneWarning", "foreground: " + p.warning());
		d.put("[style].toneSuccess", "foreground: " + p.success());
		d.put("[style].toneHeading", "foreground: " + p.heading());
		d.put("[style]Button.primary", "background: " + p.primaryButton() + "; foreground: " + p.primaryButtonText() + "; borderColor: " + p.primaryButton() + "; font: semibold");
		d.put("[style]Button.onTopBar", "foreground: " + p.topBarText() + "; toolbar.hoverBackground: " + "#5a4428" + "; toolbar.pressedBackground: " + "#2a1e10");
		d.put("[style]Label.onTopBar", "foreground: " + p.topBarText());
		d.put("[style]Panel.card", "background: " + p.surface() + "; arc: 16");
		d.put("[style]Panel.rail", "background: " + p.surface());
		d.put("[style]Panel.zebra", "background: " + p.surface());
		d.put("[style]ToggleButton.railItem", "toolbar.selectedBackground: " + p.selection() + "; selectedForeground: " + p.selectionText() + "; font: semibold");
		d.put("[style]TextArea.codeBlock", "background: " + p.window());
		d.put("[style]TextArea.callout", "background: " + p.calloutBackground());
		d.put("[style]TextArea.onAccent", "foreground: " + p.primaryButtonText());
		d.put("TextField.background", p.window());
		d.put("TextField.inactiveBackground", p.surface());
		d.put("TextField.disabledBackground", p.surface());
		d.put("TextArea.background", p.window());
		d.put("ComboBox.background", p.window());
		d.put("ComboBox.buttonBackground", p.window());
		d.put("ComboBox.popupBackground", p.surface());
		d.put("Tree.showDefaultIcons", "true");
		d.put("Tree.rowHeight", "24");
		return d;
	}

	/** The theme's colour for a problem, for a component that draws its own marking. */
	public static java.awt.Color errorColor() {
		return java.awt.Color.decode(palette(current).error());
	}

	/** Gives text a meaning, replacing any it had. */
	public static void tone(JComponent component, Tone tone) {
		List<String> classes = classes(component);
		classes.removeIf(c -> c.startsWith("tone"));
		classes.add(tone.styleClass);
		component.putClientProperty(FlatClientProperties.STYLE_CLASS, String.join(" ", classes));
	}

	/** Adds a text style, such as h2, h3, semibold, small or monospaced. */
	public static void style(JComponent component, String styleClass) {
		List<String> classes = classes(component);
		if (!classes.contains(styleClass)) {
			classes.add(styleClass);
		}
		component.putClientProperty(FlatClientProperties.STYLE_CLASS, String.join(" ", classes));
	}

	private static List<String> classes(JComponent component) {
		Object value = component.getClientProperty(FlatClientProperties.STYLE_CLASS);
		List<String> classes = new ArrayList<>();
		if (value instanceof String text && !text.isBlank()) {
			classes.addAll(List.of(text.trim().split("\\s+")));
		}
		return classes;
	}
}
