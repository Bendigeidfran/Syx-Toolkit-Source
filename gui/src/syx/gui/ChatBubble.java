package syx.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;

/**
 * One message in the wizard's conversation, drawn as a rounded bubble in the theme's colours. The wizard's sit on
 * the left and the person's on the right, in the accent colour, and a card of fields spans the column.
 *
 * <p>A wizard's bubble can have a back: clicking it, or pressing Enter or Space on it, turns it over to show the
 * handbook's words for what the question is about, and again turns it back. A button on the back, such as Read
 * more, does its own thing instead.
 *
 * <p>Its children are laid out one under another at the bubble's width, so wrapped text gets the height it needs.
 * {@link #naturalWidth} and {@link #heightFor} are what the conversation's layout sizes it by.
 */
final class ChatBubble extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int ARC = 18;
	private static final int GAP = 6;
	private static final int MIN_WIDTH = 64;

	/** Tests turn this off, so a screenshot is never taken half faded in. */
	static boolean animate = true;

	enum Side {
		WIZARD, PERSON, CARD
	}

	private final Side side;
	private final transient List<JComponent> front = new ArrayList<>();
	private final transient List<JComponent> back = new ArrayList<>();
	private boolean flipped;
	private float alpha = 1f;
	private transient Runnable onFlip = () -> { };

	ChatBubble(Side side) {
		this.side = side;
		setOpaque(false);
		setLayout(new Stack());
		setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
	}

	Side side() {
		return side;
	}

	void addFront(JComponent component) {
		front.add(component);
		add(component);
		component.setVisible(!flipped);
		listenForFlip(component);
	}

	void addBack(JComponent component) {
		boolean first = back.isEmpty();
		back.add(component);
		add(component);
		component.setVisible(flipped);
		listenForFlip(component);
		if (first) {
			makeFlippable();
		}
	}

	boolean flippable() {
		return !back.isEmpty();
	}

	boolean isFlipped() {
		return flipped;
	}

	void setOnFlip(Runnable run) {
		onFlip = run;
	}

	/** Shows the other side, if it has one. */
	void flip() {
		if (back.isEmpty()) {
			return;
		}
		flipped = !flipped;
		front.forEach(component -> component.setVisible(!flipped));
		back.forEach(component -> component.setVisible(flipped));
		onFlip.run();
		revalidate();
		repaint();
	}

	private void makeFlippable() {
		setFocusable(true);
		setToolTipText(PlainTip.of("Click to see what the handbook says about this, and again to go back."));
		getAccessibleContext().setAccessibleDescription("Press Enter to turn this over and see what the handbook says.");
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				requestFocusInWindow();
				flip();
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
					flip();
					e.consume();
				}
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				repaint();
			}

			@Override
			public void focusLost(FocusEvent e) {
				repaint();
			}
		});
		for (JComponent component : front) {
			listenForFlip(component);
		}
	}

	/** Text and plain panels pass a click on to the bubble, so the whole bubble turns over. Buttons keep theirs. */
	private void listenForFlip(Component component) {
		if (back.isEmpty() || component instanceof AbstractButton) {
			return;
		}
		if (component instanceof JComponent jc && jc.getClientProperty(ChatBubble.class) == null) {
			jc.putClientProperty(ChatBubble.class, Boolean.TRUE);
			jc.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (!(component instanceof JTextComponent text) || text.getSelectedText() == null) {
						ChatBubble.this.requestFocusInWindow();
						flip();
					}
				}
			});
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				listenForFlip(child);
			}
		}
	}

	/** Fades the bubble in, unless animation is off. */
	void fadeIn() {
		if (!animate) {
			return;
		}
		alpha = 0f;
		Timer timer = new Timer(16, null);
		timer.addActionListener(e -> {
			alpha = Math.min(1f, alpha + 0.12f);
			repaint();
			if (alpha >= 1f) {
				timer.stop();
			}
		});
		timer.start();
	}

	@Override
	public void paint(Graphics g) {
		if (alpha >= 1f) {
			super.paint(g);
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		super.paint(g2);
		g2.dispose();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Theme.Palette palette = Theme.palette(Theme.current());
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		Color fill = Color.decode(side == Side.PERSON ? palette.primaryButton() : side == Side.CARD ? palette.window() : palette.surface());
		g2.setColor(fill);
		g2.fill(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC));
		// The corner nearest the speaker is square, which is what makes it read as a speech bubble.
		if (side == Side.WIZARD) {
			g2.fillRect(0, 0, ARC / 2, ARC / 2);
		} else if (side == Side.PERSON) {
			g2.fillRect(w - ARC / 2, h - ARC / 2, ARC / 2, ARC / 2);
		}
		if (side == Side.CARD) {
			g2.setColor(Color.decode(palette.border()));
			g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC));
		}
		if (isFocusOwner()) {
			g2.setColor(Color.decode(palette.focus()));
			g2.setStroke(new BasicStroke(2f));
			g2.draw(new RoundRectangle2D.Float(1, 1, w - 2f, h - 2f, ARC, ARC));
		}
		if (flippable()) {
			paintFlipMark(g2, palette, w);
		}
		g2.dispose();
	}

	/** A small round "i" in the top corner, which says the bubble turns over. On the back it's filled. */
	private void paintFlipMark(Graphics2D g2, Theme.Palette palette, int w) {
		int size = 14;
		int x = w - size - 6;
		int y = 6;
		Color mark = Color.decode(palette.accent());
		g2.setColor(mark);
		g2.setStroke(new BasicStroke(1.3f));
		if (flipped) {
			g2.fillOval(x, y, size, size);
			g2.setColor(Color.decode(palette.surface()));
		} else {
			g2.drawOval(x, y, size, size);
		}
		g2.setFont(getFont().deriveFont(java.awt.Font.BOLD, 10f));
		FontMetrics metrics = g2.getFontMetrics();
		g2.drawString("i", x + (size - metrics.stringWidth("i")) / 2, y + (size + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	/** The width the bubble's contents would like, up to {@code max}: short text gets a short bubble. */
	int naturalWidth(int max) {
		Insets insets = getInsets();
		int room = max - insets.left - insets.right - (flippable() ? 18 : 0);
		int widest = 0;
		for (Component child : getComponents()) {
			if (!child.isVisible()) {
				continue;
			}
			if (child instanceof javax.swing.JTextArea text && text.getLineWrap()) {
				FontMetrics metrics = text.getFontMetrics(text.getFont());
				for (String line : text.getText().split("\n", -1)) {
					widest = Math.max(widest, metrics.stringWidth(line) + 4);
				}
			} else if (child instanceof Container container && container.getLayout() instanceof WrapLayout) {
				int row = 0;
				for (Component item : container.getComponents()) {
					row += item.getPreferredSize().width + 8;
				}
				widest = Math.max(widest, row + 8);
			} else {
				widest = Math.max(widest, child.getPreferredSize().width);
			}
		}
		int width = Math.min(room, widest) + insets.left + insets.right + (flippable() ? 18 : 0);
		return Math.max(Math.min(MIN_WIDTH, max), Math.min(max, width));
	}

	/** The height the bubble needs at this width. */
	int heightFor(int width) {
		Insets insets = getInsets();
		int inner = Math.max(1, width - insets.left - insets.right - (flippable() ? 18 : 0));
		int height = insets.top + insets.bottom;
		boolean first = true;
		for (Component child : getComponents()) {
			if (!child.isVisible()) {
				continue;
			}
			height += (first ? 0 : GAP) + childHeight(child, inner);
			first = false;
		}
		return height;
	}

	private static int childHeight(Component child, int width) {
		if (child instanceof javax.swing.JTextArea || child instanceof Container container && container.getLayout() instanceof WrapLayout) {
			child.setSize(width, Short.MAX_VALUE);
		}
		return child.getPreferredSize().height;
	}

	@Override
	public Dimension getPreferredSize() {
		int width = getWidth() > 0 ? getWidth() : 400;
		return new Dimension(width, heightFor(width));
	}

	/** Lays the visible children out one under another at the bubble's inner width. */
	private final class Stack implements LayoutManager {
		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			return getPreferredSize();
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return new Dimension(MIN_WIDTH, 0);
		}

		@Override
		public void layoutContainer(Container parent) {
			Insets insets = getInsets();
			int inner = Math.max(1, getWidth() - insets.left - insets.right - (flippable() ? 18 : 0));
			int y = insets.top;
			for (Component child : getComponents()) {
				if (!child.isVisible()) {
					continue;
				}
				int height = childHeight(child, inner);
				int width = child instanceof AbstractButton ? Math.min(inner, child.getPreferredSize().width) : inner;
				child.setBounds(insets.left, y, width, height);
				y += height + GAP;
			}
		}
	}
}
