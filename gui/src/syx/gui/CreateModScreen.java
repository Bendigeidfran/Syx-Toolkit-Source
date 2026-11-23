package syx.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import syx.core.ModCreator;
import syx.core.ModEntry;

/** Asks for a name and creates the mod. All the rules are in {@link ModCreator}. */
public final class CreateModScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	private final transient Path modsFolder;
	private final transient Supplier<Path> gameFolder;
	private final transient Consumer<ModEntry> onCreated;
	private final JTextField nameField = new JTextField(30);
	private final JButton createButton = new JButton("Create");
	private final WrappedText message = new WrappedText(" ", Theme.Tone.HELP);

	public CreateModScreen(Path modsFolder, Supplier<Path> gameFolder, Consumer<ModEntry> onCreated, Runnable onBack) {
		super(new GridBagLayout());
		this.modsFolder = modsFolder;
		this.gameFolder = gameFolder;
		this.onCreated = onCreated;

		JLabel prompt = new JLabel("What do you want to call your mod?");
		Theme.style(prompt, "h3");
		WrappedText location = new WrappedText("It will be created in " + modsFolder, Theme.Tone.HELP);
		Theme.style(createButton, "primary");
		createButton.addActionListener(e -> create());
		nameField.addActionListener(e -> create());
		nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameField.getPreferredSize().height));
		JButton back = new JButton("Back");
		back.addActionListener(e -> onBack.run());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(back);
		buttons.add(createButton);

		add(new SetupCard(prompt, nameField, location, message, buttons));
	}

	public void reset() {
		nameField.setText("");
		message.setText(" ");
		createButton.setEnabled(true);
	}

	public void setModName(String name) {
		nameField.setText(name);
	}

	public String messageText() {
		return message.getText();
	}

	public void create() {
		if (!createButton.isEnabled()) {
			return;
		}
		String name = nameField.getText();
		Path game = gameFolder.get();
		createButton.setEnabled(false);
		Theme.tone(message, Theme.Tone.HELP);
		message.setText("Creating...");
		new SwingWorker<ModEntry, Void>() {
			@Override
			protected ModEntry doInBackground() throws ModCreator.CreationException {
				return ModCreator.create(modsFolder, game, name);
			}

			@Override
			protected void done() {
				createButton.setEnabled(true);
				try {
					ModEntry created = get();
					message.setText(" ");
					onCreated.accept(created);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					Theme.tone(message, Theme.Tone.ERROR);
					message.setText(e.getCause().getMessage());
				}
			}
		}.execute();
	}
}
