package syx.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Everything about the selected tag or field, so the form itself can stay short. Its text is only ever
 * shown as plain text, because some of it comes from a downloaded file.
 */
final class DetailsPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	static final int WIDTH = 330;
	private static final int POPOVER_WIDTH = 380;
	private static final int POPOVER_HEIGHT = 440;
	/** The handbook's colour for the edge of a code block, in both of its themes. */
	private static final Color CODE_EDGE = new Color(0xb08a4a);

	/** A status label to show. */
	record Status(String text, Theme.Tone tone) {
	}

	/**
	 * What to show. Anything null is left out.
	 *
	 * @param rule what the game's code requires
	 * @param evidence where in the game's code that comes from
	 * @param defaultNote what happens when the tag is absent
	 * @param readMore opens the tag's handbook page
	 */
	record Content(String name, List<Status> statuses, String description, String example, String rule, String evidence,
			String defaultNote, Runnable readMore) {
	}

	private final JPanel body = new ScrollablePanel(null, true);
	private final JPanel footer = new JPanel();
	private final JLabel notesHeading = new JLabel();
	private final JPanel notesBody = new JPanel();
	private final transient List<String> shownText = new ArrayList<>();
	private final transient List<String> footerText = new ArrayList<>();
	private boolean notesOpen;
	private String shownName;
	private JButton readMoreButton;
	private JPopupMenu popover;

	DetailsPanel(String hint) {
		super(new BorderLayout(0, 10));
		Theme.style(this, "rail");
		setBorder(BorderFactory.createEmptyBorder(18, 16, 14, 16));
		setPreferredSize(new Dimension(WIDTH, 0));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);
		notesBody.setLayout(new BoxLayout(notesBody, BoxLayout.Y_AXIS));
		notesBody.setOpaque(false);
		notesBody.setAlignmentX(LEFT_ALIGNMENT);
		notesHeading.setAlignmentX(LEFT_ALIGNMENT);
		notesHeading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Theme.style(notesHeading, "semibold");
		notesHeading.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				toggleNotes();
			}
		});
		JScrollPane scroller = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.setOpaque(false);
		scroller.getViewport().setOpaque(false);
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		add(scroller, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
		this.hint = hint;
		showHint();
	}

	private final String hint;

	/** Sits beside the form, for when there's room. */
	void dockIn(Container parent) {
		hidePopover();
		setPreferredSize(new Dimension(WIDTH, 0));
		parent.add(this, BorderLayout.EAST);
	}

	/** Takes it off the screen, for when there's no room. Rows open it as a popover instead. */
	void undockFrom(Container parent) {
		parent.remove(this);
	}

	/** Shows it over the form, beside what opened it. Clicking elsewhere closes it. */
	void showPopover(Component anchor) {
		hidePopover();
		popover = new JPopupMenu();
		popover.setLayout(new BorderLayout());
		setPreferredSize(new Dimension(POPOVER_WIDTH, POPOVER_HEIGHT));
		popover.add(this, BorderLayout.CENTER);
		popover.show(anchor, anchor.getWidth() - POPOVER_WIDTH, anchor.getHeight() + 4);
	}

	void hidePopover() {
		if (popover != null) {
			popover.setVisible(false);
			popover = null;
		}
	}

	boolean isPopoverShowing() {
		return popover != null && popover.isVisible();
	}

	/** Shows the prompt for when nothing is selected. */
	void showHint() {
		clear();
		shownName = null;
		body.add(new WrappedText(hint, Theme.Tone.HELP));
		finish();
	}

	void show(Content content) {
		clear();
		shownName = content.name();
		JLabel name = new JLabel(content.name());
		name.putClientProperty("html.disable", Boolean.TRUE);
		Theme.style(name, "h3");
		Theme.tone(name, Theme.Tone.HEADING);
		name.setAlignmentX(LEFT_ALIGNMENT);
		body.add(name);
		shownText.add(content.name());
		if (!content.statuses().isEmpty()) {
			JPanel statuses = new JPanel();
			statuses.setLayout(new BoxLayout(statuses, BoxLayout.Y_AXIS));
			statuses.setOpaque(false);
			statuses.setAlignmentX(LEFT_ALIGNMENT);
			for (Status status : content.statuses()) {
				statuses.add(Box.createVerticalStrut(4));
				statuses.add(new Chip(status.text(), status.tone()));
				shownText.add(status.text());
			}
			body.add(statuses);
		}
		if (content.description() != null) {
			addGap();
			addText(content.description(), null);
		}
		if (content.example() != null) {
			addGap();
			WrappedText example = new WrappedText(content.example(), Theme.Tone.HELP);
			Theme.style(example, "monospaced");
			Theme.style(example, "codeBlock");
			example.setOpaque(true);
			example.setTabSize(2);
			example.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, CODE_EDGE), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
			body.add(example);
			shownText.add(content.example());
		}
		if (content.defaultNote() != null) {
			addGap();
			addLabelled("If it's left out", content.defaultNote());
		}
		if (content.rule() != null) {
			addGap();
			addLabelled("From the game's code", content.rule());
			if (content.evidence() != null) {
				addText(content.evidence(), Theme.Tone.GHOST);
			}
		}
		readMoreButton = null;
		if (content.readMore() != null) {
			addGap();
			readMoreButton = new JButton("Read more in the handbook");
			readMoreButton.setAlignmentX(LEFT_ALIGNMENT);
			readMoreButton.addActionListener(e -> content.readMore().run());
			body.add(readMoreButton);
		}
		finish();
	}

	private void addLabelled(String label, String text) {
		JLabel heading = new JLabel(label);
		Theme.style(heading, "semibold");
		heading.setAlignmentX(LEFT_ALIGNMENT);
		body.add(heading);
		body.add(Box.createVerticalStrut(2));
		addText(text, Theme.Tone.HELP);
	}

	private void addText(String text, Theme.Tone tone) {
		body.add(new WrappedText(text, tone));
		shownText.add(text);
	}

	private void addGap() {
		body.add(Box.createVerticalStrut(10));
	}

	private void clear() {
		body.removeAll();
		shownText.clear();
		readMoreButton = null;
	}

	private void finish() {
		body.revalidate();
		body.repaint();
	}

	/** Notes that stay at the bottom whichever tag is selected, such as the handbook's credit. They start folded. */
	void setFooter(List<String> notes) {
		footer.removeAll();
		notesBody.removeAll();
		footerText.clear();
		notesOpen = false;
		if (!notes.isEmpty()) {
			for (String note : notes) {
				WrappedText text = new WrappedText(note, Theme.Tone.HELP);
				Theme.style(text, "small");
				text.setAlignmentX(LEFT_ALIGNMENT);
				notesBody.add(text);
				notesBody.add(Box.createVerticalStrut(4));
				footerText.add(note);
			}
			notesBody.setVisible(false);
			updateNotesHeading();
			footer.add(notesHeading);
			footer.add(notesBody);
		}
		footer.revalidate();
		footer.repaint();
	}

	private void toggleNotes() {
		if (footerText.isEmpty()) {
			return;
		}
		notesOpen = !notesOpen;
		notesBody.setVisible(notesOpen);
		updateNotesHeading();
		footer.revalidate();
		footer.repaint();
		revalidate();
		repaint();
	}

	private void updateNotesHeading() {
		// The same markers the form's section cards use, so a closed group looks the same in both places.
		notesHeading.setText((notesOpen ? "\u25BE " : "\u25B8 ") + "Notes");
	}

	boolean isNotesOpen() {
		return notesOpen;
	}

	void clickNotes() {
		toggleNotes();
	}

	String shownName() {
		return shownName;
	}

	List<String> shownText() {
		return List.copyOf(shownText);
	}

	List<String> footerText() {
		return List.copyOf(footerText);
	}

	void clickReadMore() {
		if (readMoreButton != null) {
			readMoreButton.doClick();
		}
	}

	boolean hasReadMore() {
		return readMoreButton != null;
	}
}
