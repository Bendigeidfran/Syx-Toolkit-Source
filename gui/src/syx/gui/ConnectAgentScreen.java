package syx.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import syx.core.AgentSetup;

/**
 * Shows how to connect the player's own agent. Everything it says comes from {@link AgentSetup}. It only
 * copies text, so another app's settings are never changed from here.
 */
public final class ConnectAgentScreen extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String INTRO = "You can have your own AI agent do what this app does: create mods, read the handbook, and create and edit assets, with the same checks and warnings. "
			+ "The agent, its account and any costs are your own. The toolkit holds no keys and calls no AI service. Your agent's app decides what it asks you to approve, so check that it asks before changing files if you want it to. "
			+ "What your agent reads, including your mod's files, may be sent to whoever provides that agent.";
	private static final String READ_ONLY_NOTE = "Read-only leaves out everything that can change files, so an agent can look and check but not write.";
	private static final String NOT_PACKAGED = "This only works in the packaged app, which includes the command line this needs.";

	private final JPanel clients = new JPanel();
	private final JCheckBox readOnly = new JCheckBox("Read-only");
	private final JButton test = new JButton("Test the connection");
	private final WrappedText testResult = new WrappedText(" ", Theme.Tone.HELP);
	private final transient Path launcher;
	private transient Consumer<String> clipboard = text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	private final transient List<JTextArea> shown = new ArrayList<>();
	private final transient List<JButton> copies = new ArrayList<>();

	public ConnectAgentScreen() {
		this(AgentSetup.launcher());
	}

	public ConnectAgentScreen(Path launcher) {
		this.launcher = launcher;
		setLayout(new BorderLayout());
		ScrollablePanel body = new ScrollablePanel(null, true);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
		JLabel heading = new JLabel("Connect an agent");
		Theme.style(heading, "h2");
		heading.setAlignmentX(LEFT_ALIGNMENT);
		body.add(heading);
		WrappedText intro = new WrappedText(INTRO, Theme.Tone.HELP);
		intro.setAlignmentX(LEFT_ALIGNMENT);
		body.add(intro);
		clients.setLayout(new BoxLayout(clients, BoxLayout.Y_AXIS));
		clients.setAlignmentX(LEFT_ALIGNMENT);
		if (launcher == null) {
			WrappedText missing = new WrappedText(NOT_PACKAGED, Theme.Tone.WARNING);
			missing.setAlignmentX(LEFT_ALIGNMENT);
			body.add(missing);
		} else {
			JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
			options.setAlignmentX(LEFT_ALIGNMENT);
			options.add(readOnly);
			options.add(test);
			options.setMaximumSize(new Dimension(Integer.MAX_VALUE, options.getPreferredSize().height));
			body.add(options);
			WrappedText note = new WrappedText(READ_ONLY_NOTE, Theme.Tone.HELP);
			note.setAlignmentX(LEFT_ALIGNMENT);
			body.add(note);
			testResult.setAlignmentX(LEFT_ALIGNMENT);
			body.add(testResult);
			body.add(clients);
			readOnly.addActionListener(e -> rebuild());
			test.addActionListener(e -> runTest());
			rebuild();
		}
		JScrollPane scroller = new JScrollPane(body);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		add(scroller, BorderLayout.CENTER);
	}

	private void rebuild() {
		clients.removeAll();
		shown.clear();
		copies.clear();
		for (AgentSetup.Client client : AgentSetup.clients(launcher, readOnly.isSelected())) {
			JLabel name = new JLabel(client.name());
			Theme.style(name, "semibold");
			name.setAlignmentX(LEFT_ALIGNMENT);
			name.setBorder(BorderFactory.createEmptyBorder(14, 0, 2, 0));
			clients.add(name);
			WrappedText where = new WrappedText(client.where(), Theme.Tone.HELP);
			where.setAlignmentX(LEFT_ALIGNMENT);
			clients.add(where);
			JTextArea text = new JTextArea(client.text());
			text.setEditable(false);
			text.setLineWrap(true);
			text.setAlignmentX(LEFT_ALIGNMENT);
			Theme.style(text, "monospaced");
			text.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			shown.add(text);
			clients.add(text);
			JButton copy = new JButton("Copy");
			copy.setAlignmentX(LEFT_ALIGNMENT);
			copy.addActionListener(e -> clipboard.accept(client.text()));
			copies.add(copy);
			clients.add(copy);
		}
		clients.revalidate();
		clients.repaint();
	}

	private void runTest() {
		test.setEnabled(false);
		testResult.setText("Testing...");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				return AgentSetup.test(launcher);
			}

			@Override
			protected void done() {
				test.setEnabled(true);
				try {
					testResult.setText(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					testResult.setText("The test failed: " + e.getCause());
				}
			}
		}.execute();
	}

	/** Replaces the clipboard, so tests don't change the user's. */
	public void setClipboard(Consumer<String> replacement) {
		clipboard = replacement;
	}

	public List<String> shownSetups() {
		return shown.stream().map(JTextArea::getText).toList();
	}

	public void clickCopy(int at) {
		copies.get(at).doClick();
	}

	public void setReadOnly(boolean on) {
		if (readOnly.isSelected() != on) {
			readOnly.doClick();
		}
	}

	public boolean isOfferingSetup() {
		return launcher != null;
	}
}
