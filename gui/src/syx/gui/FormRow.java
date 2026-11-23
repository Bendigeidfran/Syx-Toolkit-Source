package syx.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One tag or field on one line: its name and status, its editor, and Add or Remove. A problem appears
 * under the editor only when there is one. Used by both editors, which keep all the rules.
 */
final class FormRow extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int NAME_WIDTH = 155;
	private static final int ACTION_WIDTH = 66;
	private static final String INSTALLED = "syx.formRowInstalled";

	private final JLabel name;
	private final JPanel nameColumn = new JPanel();
	private final JPanel chips = new JPanel();
	private final JPanel action = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
	private final JPanel endColumn = new JPanel(new BorderLayout(4, 0));
	private final JButton infoButton = new JButton("i");
	private final JPanel editorColumn = new JPanel(new BorderLayout(0, 3));
	private final WrappedText problem = new WrappedText(" ", Theme.Tone.ERROR);
	private final JComponent editor;
	private transient Runnable onSelect = () -> { };
	private String tip;
	private boolean selected;
	private transient Consumer<Component> onInfo = anchor -> { };
	private boolean stacked;
	private boolean alwaysStacked;
	private String foldTitle;
	private boolean folded;
	private transient Runnable onFold;

	FormRow(String displayName, JComponent editor, boolean ghost) {
		this.editor = editor;
		name = new JLabel(displayName);
		name.putClientProperty("html.disable", Boolean.TRUE);
		Theme.style(name, "semibold");
		if (ghost) {
			Theme.tone(name, Theme.Tone.GHOST);
		}
		name.setAlignmentX(LEFT_ALIGNMENT);
		chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
		chips.setAlignmentX(LEFT_ALIGNMENT);
		chips.setVisible(false);
		nameColumn.setLayout(new BoxLayout(nameColumn, BoxLayout.Y_AXIS));
		nameColumn.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		nameColumn.add(name);
		nameColumn.add(chips);
		problem.setVisible(false);
		Theme.style(infoButton, "small");
		infoButton.setMargin(new java.awt.Insets(1, 7, 1, 7));
		infoButton.setVisible(false);
		infoButton.addActionListener(e -> {
			onSelect.run();
			onInfo.accept(infoButton);
		});
		endColumn.add(action, BorderLayout.CENTER);
		JPanel infoHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		infoHolder.add(infoButton);
		endColumn.add(infoHolder, BorderLayout.EAST);
		editorColumn.add(editor, BorderLayout.NORTH);
		editorColumn.add(problem, BorderLayout.CENTER);
		setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		arrange();
		install(this);
	}

	/** Always puts the name above the editor, for an editor that needs the width. */
	void setAlwaysStacked() {
		alwaysStacked = true;
		setStacked(true);
	}

	/** Lays the row out for the room the window has. */
	void setBand(WidthBand band) {
		infoButton.setVisible(band != WidthBand.WIDE);
		setStacked(alwaysStacked || band == WidthBand.NARROW);
	}

	/** What the row's info button does, when the details panel isn't on screen. */
	void setOnInfo(Consumer<Component> run) {
		onInfo = run;
	}

	boolean isInfoShowing() {
		return infoButton.isVisible();
	}

	void clickInfo() {
		infoButton.doClick();
	}

	private void setStacked(boolean value) {
		if (stacked != value) {
			stacked = value;
			arrange();
			revalidate();
		}
	}

	boolean isStacked() {
		return stacked;
	}

	private void arrange() {
		removeAll();
		if (stacked) {
			setLayout(new BorderLayout(0, 4));
			chips.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
			nameColumn.setLayout(new BoxLayout(nameColumn, BoxLayout.X_AXIS));
			nameColumn.setPreferredSize(null);
			nameColumn.setMinimumSize(null);
			JPanel header = new JPanel(new BorderLayout(8, 0));
			header.add(nameColumn, BorderLayout.CENTER);
			header.add(endColumn, BorderLayout.EAST);
			add(header, BorderLayout.NORTH);
			add(editorColumn, BorderLayout.CENTER);
		} else {
			setLayout(new BorderLayout(10, 0));
			chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
			nameColumn.setLayout(new BoxLayout(nameColumn, BoxLayout.Y_AXIS));
			nameColumn.setPreferredSize(new Dimension(NAME_WIDTH, 0));
			nameColumn.setMinimumSize(new Dimension(NAME_WIDTH, 0));
			action.setPreferredSize(new Dimension(ACTION_WIDTH, action.getPreferredSize().height));
			add(nameColumn, BorderLayout.WEST);
			add(editorColumn, BorderLayout.CENTER);
			add(endColumn, BorderLayout.EAST);
		}
	}

	/** The name column has no height of its own, so this makes room for its status chips. */
	@Override
	public Dimension getPreferredSize() {
		if (!stacked) {
			int chipsHeight = chips.isVisible() ? chips.getLayout().preferredLayoutSize(chips).height : 0;
			nameColumn.setPreferredSize(new Dimension(NAME_WIDTH, name.getPreferredSize().height + chipsHeight + 4));
		}
		return super.getPreferredSize();
	}

	/** Takes only the height it needs, however much room there is. */
	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	void addChip(String text, Theme.Tone tone, String chipTip) {
		Chip chip = new Chip(text, tone);
		chips.add(Box.createVerticalStrut(3));
		chips.add(chip);
		chips.setVisible(true);
		if (chipTip != null) {
			chip.setToolTipText(PlainTip.of(chipTip));
		}
	}

	/** The button at the end of the row, such as Add or Remove. */
	private transient JButton actionButton;

	/** Clicks the row's Add or Remove button. */
	void clickAction() {
		actionButton.doClick();
	}

	/**
	 * Makes the heading a control that folds the row. The marker shows which way it is. The button at the
	 * end of the row is left as it is.
	 */
	void setFolding(String title, Runnable onToggle) {
		foldTitle = title;
		folded = true;
		onFold = onToggle;
		name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		name.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				onFold.run();
			}
		});
		showFold();
	}

	void setFolded(boolean value) {
		folded = value;
		showFold();
	}

	void clickHeading() {
		onFold.run();
	}

	String headingText() {
		return name.getText();
	}

	private void showFold() {
		name.setText((folded ? "\u25B8 " : "\u25BE ") + foldTitle);
	}

	void setAction(String text, Runnable run) {
		JButton button = new JButton(text);
		actionButton = button;
		Theme.style(button, "small");
		button.setMargin(new java.awt.Insets(1, 6, 1, 6));
		button.addActionListener(e -> run.run());
		action.removeAll();
		action.add(button);
		arrange();
		applyTip(button);
	}

	/** What shows when hovering anywhere on the row. It's made safe by {@link PlainTip}. */
	void setTip(String text) {
		tip = PlainTip.of(text);
		applyTip(this);
	}

	String tip() {
		return tip;
	}

	private void applyTip(Component component) {
		if (component instanceof FormRow && component != this) {
			return;
		}
		if (component == infoButton) {
			infoButton.setToolTipText("Show the details");
		} else if (component instanceof JComponent item && !(component instanceof Chip)) {
			item.setToolTipText(tip);
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				applyTip(child);
			}
		}
	}

	void setOnSelect(Runnable run) {
		onSelect = run;
	}

	/** Clicking or focusing anything in the row selects it, except a row nested inside it. */
	private void install(Component component) {
		if (component instanceof FormRow && component != this) {
			return;
		}
		// Rearranging the row adds the same parts again, and each should only be set up once.
		if (component instanceof JComponent item) {
			if (item.getClientProperty(INSTALLED) != null) {
				return;
			}
			item.putClientProperty(INSTALLED, Boolean.TRUE);
		}
		if (component instanceof JPanel panel && component != this) {
			panel.setOpaque(false);
		}
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				onSelect.run();
			}
		});
		component.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				onSelect.run();
			}
		});
		if (component instanceof Container container) {
			container.addContainerListener(new ContainerAdapter() {
				@Override
				public void componentAdded(ContainerEvent e) {
					install(e.getChild());
					applyTip(e.getChild());
				}
			});
			for (Component child : container.getComponents()) {
				install(child);
			}
		}
	}

	void setProblem(String text, Theme.Tone tone) {
		boolean has = text != null && !text.isBlank();
		problem.setText(has ? text : " ");
		problem.setVisible(has);
		if (has && tone != null) {
			Theme.tone(problem, tone);
		}
	}

	String problemText() {
		return problem.getText();
	}

	void setSelectedRow(boolean value) {
		if (selected != value) {
			selected = value;
			repaint();
		}
	}

	boolean isSelectedRow() {
		return selected;
	}

	/** Brings the row into view and puts the cursor in its editor. */
	void reveal() {
		scrollRectToVisible(new Rectangle(0, 0, getWidth(), getHeight()));
		focusFirst(editor);
	}

	private static boolean focusFirst(Component component) {
		if (component.isFocusable() && component.isEnabled() && component.isShowing() && !(component instanceof Container container && hasFocusableChild(container))) {
			component.requestFocusInWindow();
			return true;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				if (focusFirst(child)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasFocusableChild(Container container) {
		return container instanceof JPanel && container.getComponentCount() > 0;
	}

	/** Lets tests act as if the row's editor gained focus, whether or not the window has it. */
	void simulateFocus() {
		for (java.awt.event.FocusListener listener : editor.getFocusListeners()) {
			listener.focusGained(new FocusEvent(editor, FocusEvent.FOCUS_GAINED));
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (selected) {
			Theme.Palette palette = Theme.palette(Theme.current());
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Color.decode(palette.calloutBackground()));
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
			g2.setColor(Color.decode(palette.accent()));
			g2.fillRoundRect(0, 2, 3, getHeight() - 4, 3, 3);
			g2.dispose();
		}
	}
}
