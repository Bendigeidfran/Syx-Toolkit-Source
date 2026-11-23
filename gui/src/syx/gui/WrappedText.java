package syx.gui;

import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

/** Text that wraps to the space it's given. Unlike a JLabel, it can't be made to render HTML. */
final class WrappedText extends JTextArea {

	private static final long serialVersionUID = 1L;

	WrappedText(String text, Theme.Tone tone) {
		super();
		setEditable(false);
		setFocusable(false);
		setLineWrap(true);
		setWrapStyleWord(true);
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder());
		setFont(new JLabel().getFont());
		if (tone != null) {
			Theme.tone(this, tone);
		}
		setAlignmentX(LEFT_ALIGNMENT);
		// Set before the text, so the caret never queues a scroll. These notes sit in the form's scroll pane.
		((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		setText(text == null ? "" : text);
	}

	/** A caret request must not move the form. This note has no scroll pane of its own. */
	@Override
	public void scrollRectToVisible(Rectangle rectangle) {
	}

	/** A warning that stands out, because it can change what the player's game does. */
	static WrappedText callout(String text) {
		WrappedText note = new WrappedText(text, Theme.Tone.WARNING);
		Theme.style(note, "callout");
		note.setOpaque(true);
		note.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, new java.awt.Color(0xc9962e)), BorderFactory.createEmptyBorder(6, 10, 6, 8)));
		return note;
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
