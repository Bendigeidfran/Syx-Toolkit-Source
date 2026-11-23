package syx.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JComponent;

import syx.core.PngCodec;
import syx.core.PortraitSim;

/**
 * A face or addon sheet's art, drawn large with its frame grid, to pick frames from. A click, or Space on the frame
 * with the keyboard's outline, picks or unpicks it; picked frames are outlined and numbered in the order picked.
 * Frames with nothing drawn are dimmed. The grid is the game's (RaceFrameMaker.java:139-146, RCrown.java:61-68),
 * and comes from {@link PortraitSim#frameCell}, so this draws what the core says a frame is.
 */
final class FramePicker extends JComponent {

	private static final long serialVersionUID = 1L;

	private transient BufferedImage art;
	private boolean addon;
	private int frames;
	private int scale = 2;
	private transient List<Boolean> empty = List.of();
	private transient List<Integer> picked = List.of();
	private int hovered = -1;
	private int cursor;
	private transient IntConsumer onToggle = index -> {
	};
	private transient IntConsumer onHover = index -> {
	};

	FramePicker() {
		setFocusable(true);
		setToolTipText(PlainTip.of("Click a frame, or move with the arrow keys and press Space, to pick or unpick it."));
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int index = frameAt(e.getX(), e.getY());
				if (index >= 0) {
					cursor = index;
					requestFocusInWindow();
					onToggle.accept(index);
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				hover(frameAt(e.getX(), e.getY()));
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hover(-1);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (frames == 0) {
					return;
				}
				int across = addon ? 1 : 4;
				int moved = switch (e.getKeyCode()) {
				case KeyEvent.VK_LEFT -> cursor - 1;
				case KeyEvent.VK_RIGHT -> cursor + 1;
				case KeyEvent.VK_UP -> cursor - across;
				case KeyEvent.VK_DOWN -> cursor + across;
				default -> cursor;
				};
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					onToggle.accept(cursor);
					e.consume();
					return;
				}
				if (moved != cursor && moved >= 0 && moved < frames) {
					cursor = moved;
					hover(cursor);
					int[] cell = cell(cursor);
					scrollRectToVisible(new java.awt.Rectangle(cell[0], cell[1], cell[2], cell[3]));
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
	}

	/** Shows a sheet. A null image shows nothing. */
	void show(PngCodec.Image image, boolean isAddon, List<Integer> pickedFrames) {
		addon = isAddon;
		if (image == null) {
			art = null;
			frames = 0;
			empty = List.of();
		} else {
			int width = image.width() / 2;
			art = new BufferedImage(Math.max(1, width), Math.max(1, image.height()), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < image.height(); y++) {
				for (int x = 0; x < width; x++) {
					int rgba = image.at(x, y);
					art.setRGB(x, y, (rgba & 0xFF) << 24 | rgba >>> 8);
				}
			}
			frames = PortraitSim.frameCount(isAddon, image.width(), image.height());
			empty = PortraitSim.emptyFrames(image, isAddon);
		}
		picked = List.copyOf(pickedFrames);
		cursor = Math.min(cursor, Math.max(0, frames - 1));
		hovered = -1;
		revalidate();
		repaint();
	}

	void setPicked(List<Integer> pickedFrames) {
		picked = List.copyOf(pickedFrames);
		repaint();
	}

	void setScale(int value) {
		if (value != scale) {
			scale = Math.max(1, value);
			revalidate();
			repaint();
		}
	}

	void onToggle(IntConsumer action) {
		onToggle = action;
	}

	void onHover(IntConsumer action) {
		onHover = action;
	}

	int frameCount() {
		return frames;
	}

	List<Integer> emptyFrames() {
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < empty.size(); i++) {
			if (empty.get(i)) {
				list.add(i);
			}
		}
		return list;
	}

	/** Clicks a frame, as a person would. */
	void clickFrame(int index) {
		onToggle.accept(index);
	}

	private void hover(int index) {
		if (index != hovered) {
			hovered = index;
			repaint();
			onHover.accept(index);
		}
	}

	private int[] cell(int index) {
		int[] cell = PortraitSim.frameCell(addon, index);
		return new int[] { cell[0] * scale, cell[1] * scale, cell[2] * scale, cell[3] * scale };
	}

	private int frameAt(int x, int y) {
		for (int i = 0; i < frames; i++) {
			int[] cell = cell(i);
			if (x >= cell[0] && x < cell[0] + cell[2] && y >= cell[1] && y < cell[1] + cell[3]) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public Dimension getPreferredSize() {
		return art == null ? new Dimension(200, 60) : new Dimension(art.getWidth() * scale, art.getHeight() * scale);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		Theme.Palette palette = Theme.palette(Theme.current());
		Color accent = Color.decode(palette.accent());
		Color border = Color.decode(palette.border());
		g2.setColor(Color.decode(palette.surface()));
		g2.fillRect(0, 0, getWidth(), getHeight());
		if (art == null) {
			g2.dispose();
			return;
		}
		// A checkerboard under the art, so transparent and dark pixels can be told apart.
		for (int y = 0; y < art.getHeight() * scale; y += 8) {
			for (int x = 0; x < art.getWidth() * scale; x += 8) {
				g2.setColor(((x + y) / 8 & 1) == 0 ? new Color(0x3A3A3A) : new Color(0x2E2E2E));
				g2.fillRect(x, y, 8, 8);
			}
		}
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g2.drawImage(art, 0, 0, art.getWidth() * scale, art.getHeight() * scale, null);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
		for (int i = 0; i < frames; i++) {
			int[] cell = cell(i);
			if (i < empty.size() && empty.get(i)) {
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
				g2.setColor(Color.BLACK);
				g2.fillRect(cell[0], cell[1], cell[2], cell[3]);
				g2.setComposite(AlphaComposite.SrcOver);
			}
			g2.setColor(border);
			g2.setStroke(new BasicStroke(1));
			g2.drawRect(cell[0], cell[1], cell[2] - 1, cell[3] - 1);
			g2.setColor(new Color(255, 255, 255, 150));
			g2.drawString(String.valueOf(i), cell[0] + 3, cell[1] + 12);
			int order = picked.indexOf(i);
			if (order >= 0) {
				g2.setColor(accent);
				g2.setStroke(new BasicStroke(3));
				g2.drawRect(cell[0] + 1, cell[1] + 1, cell[2] - 3, cell[3] - 3);
				String label = String.valueOf(order + 1);
				int w = g2.getFontMetrics().stringWidth(label) + 8;
				g2.fillRoundRect(cell[0] + cell[2] - w - 3, cell[1] + 3, w, 16, 8, 8);
				g2.setColor(Color.WHITE);
				g2.drawString(label, cell[0] + cell[2] - w + 1, cell[1] + 15);
			}
			if (i == hovered || isFocusOwner() && i == cursor) {
				g2.setColor(i == hovered ? new Color(255, 255, 255, 200) : accent.brighter());
				g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 4, 3 }, 0));
				g2.drawRect(cell[0] + 1, cell[1] + 1, cell[2] - 3, cell[3] - 3);
			}
		}
		g2.dispose();
	}
}
