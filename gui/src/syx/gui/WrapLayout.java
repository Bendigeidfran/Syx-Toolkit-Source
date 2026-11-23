package syx.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A flow of components that wraps onto as many lines as it needs, and asks for the height that takes. A plain
 * FlowLayout asks for one line however narrow it is, so wrapped buttons would be cut off.
 */
final class WrapLayout extends FlowLayout {

	private static final long serialVersionUID = 1L;

	WrapLayout(int align, int hgap, int vgap) {
		super(align, hgap, vgap);
	}

	@Override
	public Dimension preferredLayoutSize(Container target) {
		return size(target, true);
	}

	@Override
	public Dimension minimumLayoutSize(Container target) {
		Dimension minimum = size(target, false);
		minimum.width -= getHgap() + 1;
		return minimum;
	}

	private Dimension size(Container target, boolean preferred) {
		synchronized (target.getTreeLock()) {
			int targetWidth = target.getWidth();
			if (targetWidth == 0) {
				targetWidth = Integer.MAX_VALUE;
			}
			Insets insets = target.getInsets();
			int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);
			Dimension size = new Dimension(0, 0);
			int rowWidth = 0;
			int rowHeight = 0;
			for (Component component : target.getComponents()) {
				if (!component.isVisible()) {
					continue;
				}
				Dimension d = preferred ? component.getPreferredSize() : component.getMinimumSize();
				if (rowWidth + d.width > maxWidth && rowWidth > 0) {
					addRow(size, rowWidth, rowHeight);
					rowWidth = 0;
					rowHeight = 0;
				}
				if (rowWidth != 0) {
					rowWidth += getHgap();
				}
				rowWidth += d.width;
				rowHeight = Math.max(rowHeight, d.height);
			}
			addRow(size, rowWidth, rowHeight);
			size.width += insets.left + insets.right + getHgap() * 2;
			size.height += insets.top + insets.bottom + getVgap() * 2;
			return size;
		}
	}

	private void addRow(Dimension size, int rowWidth, int rowHeight) {
		size.width = Math.max(size.width, rowWidth);
		if (size.height > 0) {
			size.height += getVgap();
		}
		size.height += rowHeight;
	}
}
