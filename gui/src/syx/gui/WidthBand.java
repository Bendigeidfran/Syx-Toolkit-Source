package syx.gui;

/** How much room the window has, which decides how its screens are laid out. */
public enum WidthBand {
	/** The rail, the form and the details panel side by side. */
	WIDE,
	/** No room for the details panel, so each row opens it as a popover instead. */
	MEDIUM,
	/** The rail becomes a menu, and tag names sit above their fields. */
	NARROW;

	private static final int WIDE_FROM = 1100;
	private static final int MEDIUM_FROM = 820;

	public static WidthBand of(int width) {
		if (width >= WIDE_FROM) {
			return WIDE;
		}
		return width >= MEDIUM_FROM ? MEDIUM : NARROW;
	}
}
