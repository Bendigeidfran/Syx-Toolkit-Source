package syx.gui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** A small outlined label for a tag's status. */
final class Chip extends JLabel {

	private static final long serialVersionUID = 1L;

	Chip(String text, Theme.Tone tone) {
		super(text);
		// A chip's words are always the toolkit's own, but this keeps a label from ever rendering HTML.
		putClientProperty("html.disable", Boolean.TRUE);
		Theme.style(this, "small");
		Theme.tone(this, tone);
		setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(getForeground());
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1);
		g2.dispose();
	}
}
