package syx.test;

import java.awt.Color;
import java.util.List;

import syx.gui.Theme;
import syx.gui.Theme.Palette;

/**
 * Checks that both themes can be read, by WCAG contrast: 4.5 to 1 for text, and 3 to 1 for ghosted text
 * and the edges of fields, which are the lower limits WCAG sets for less critical content and controls.
 */
public final class ThemeTest {

	private static int passed;
	private static int failed;

	private ThemeTest() {
	}

	public static void main(String[] args) {
		check("dark", Theme.DARK);
		check("light", Theme.LIGHT);
		expect("contrast is measured correctly, black on white is 21", Math.abs(contrast("#000000", "#ffffff") - 21) < 0.01);
		expect("contrast is measured correctly, a colour on itself is 1", Math.abs(contrast("#3a2a17", "#3a2a17") - 1) < 0.01);
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void check(String name, Palette p) {
		for (String background : List.of(p.window(), p.surface())) {
			for (String text : List.of(p.text(), p.help(), p.error(), p.warning(), p.success(), p.link(), p.heading())) {
				readable(name, text, background, 4.5);
			}
			readable(name, p.ghost(), background, 3);
			readable(name, p.border(), background, 3);
			readable(name, p.focus(), background, 3);
		}
		for (String text : List.of(p.text(), p.error(), p.warning())) {
			readable(name, text, p.calloutBackground(), 4.5);
		}
		readable(name, p.topBarText(), p.topBar(), 4.5);
		readable(name, p.primaryButtonText(), p.primaryButton(), 4.5);
		readable(name, p.selectionText(), p.selection(), 4.5);
	}

	private static void readable(String theme, String foreground, String background, double needed) {
		double found = contrast(foreground, background);
		expect(theme + ": " + foreground + " on " + background + " is " + String.format("%.2f", found) + ", and needs " + needed, found >= needed);
	}

	static double contrast(String a, String b) {
		double one = luminance(Color.decode(a));
		double two = luminance(Color.decode(b));
		return (Math.max(one, two) + 0.05) / (Math.min(one, two) + 0.05);
	}

	private static double luminance(Color c) {
		return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
	}

	private static double channel(int value) {
		double v = value / 255.0;
		return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
