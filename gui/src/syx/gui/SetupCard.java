package syx.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A card of fixed width for the steps before a mod is chosen. Its height follows its wrapped text. */
final class SetupCard extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int WIDTH = 520;
	private static final int GUTTER = 48;
	private static final int PADDING = 28;

	SetupCard(Component... items) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		Theme.style(this, "card");
		setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
		for (int i = 0; i < items.length; i++) {
			if (items[i] instanceof JComponent item) {
				item.setAlignmentX(LEFT_ALIGNMENT);
			}
			if (items[i] instanceof JButton) {
				items[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, items[i].getMaximumSize().height));
			}
			add(items[i]);
			if (i < items.length - 1) {
				add(Box.createVerticalStrut(12));
			}
		}
	}

	/** Wrapped text only knows its height once it has a width, so this gives it one first. */
	@Override
	public Dimension getPreferredSize() {
		Container parent = getParent();
		int width = parent == null || parent.getWidth() <= GUTTER ? WIDTH : Math.min(WIDTH, parent.getWidth() - GUTTER);
		int inner = width - 2 * PADDING;
		for (Component child : getComponents()) {
			if (child instanceof WrappedText) {
				child.setSize(inner, Short.MAX_VALUE);
			}
		}
		((BoxLayout) getLayout()).invalidateLayout(this);
		return new Dimension(width, getLayout().preferredLayoutSize(this).height);
	}

	@Override
	public Dimension getMaximumSize() {
		return getPreferredSize();
	}

	/** A screen with this card in the middle of it. */
	JPanel centred() {
		JPanel screen = new JPanel(new GridBagLayout());
		screen.add(this);
		return screen;
	}
}
