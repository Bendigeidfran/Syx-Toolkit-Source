package syx.gui;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/** Fills the width of its scroll pane unless its contents are wider, and scrolls at a useful speed. */
final class ScrollablePanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;
	private static final int UNIT = 16;

	private final boolean alwaysFitWidth;

	ScrollablePanel(LayoutManager layout) {
		this(layout, false);
	}

	/** With alwaysFitWidth, it wraps or squeezes its contents rather than ever scrolling sideways. */
	ScrollablePanel(LayoutManager layout, boolean alwaysFitWidth) {
		super(layout);
		this.alwaysFitWidth = alwaysFitWidth;
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
		return UNIT;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
		return Math.max(UNIT, visible.height - UNIT);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return alwaysFitWidth || getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
	}
}
