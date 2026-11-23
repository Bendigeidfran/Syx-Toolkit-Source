package syx.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import syx.core.ModEntry;
import syx.core.ModLibrary;

/** Lists the mods in the mods folder. All the rules are in {@link ModLibrary}. */
public final class ChooseModScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	private final transient Path modsFolder;
	private final transient Supplier<Path> gameFolder;
	private final transient Consumer<ModEntry> onChosen;
	private final DefaultListModel<ModEntry> mods = new DefaultListModel<>();
	private final JList<ModEntry> list = new JList<>(mods);
	private final JLabel message = new JLabel(" ");
	private final JButton continueButton = new JButton("Continue with this mod");
	private final JButton createButton = new JButton("Create New Mod");
	private boolean loading;

	public ChooseModScreen(Path modsFolder, Supplier<Path> gameFolder, Consumer<ModEntry> onChosen, Runnable onCreate, Runnable onBack) {
		super(new BorderLayout(0, 10));
		this.modsFolder = modsFolder;
		this.gameFolder = gameFolder;
		this.onChosen = onChosen;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
				super.getListCellRendererComponent(owner, value, index, selected, focused);
				setText(((ModEntry) value).describe());
				setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
				return this;
			}
		});
		list.addListSelectionListener(e -> continueButton.setEnabled(list.getSelectedValue() != null && list.getSelectedValue().refusal() == null));
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2) {
					chooseSelected();
				}
			}
		});

		Theme.style(continueButton, "primary");
		Theme.style(createButton, "primary");
		continueButton.setEnabled(false);
		continueButton.addActionListener(e -> chooseSelected());
		createButton.addActionListener(e -> onCreate.run());
		createButton.setVisible(false);
		JButton back = new JButton("Back");
		back.addActionListener(e -> onBack.run());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		buttons.add(back);
		buttons.add(continueButton);
		buttons.add(createButton);

		JPanel bottom = new JPanel(new BorderLayout(0, 8));
		bottom.add(message, BorderLayout.NORTH);
		bottom.add(buttons, BorderLayout.SOUTH);

		JLabel heading = new JLabel("Choose the mod to work on");
		Theme.style(heading, "h2");
		add(heading, BorderLayout.NORTH);
		add(new JScrollPane(list), BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
	}

	/** Reloads the list, with the given folder selected if it's there. */
	public void refresh(String selectFolder) {
		if (loading) {
			return;
		}
		loading = true;
		mods.clear();
		createButton.setVisible(false);
		message.setText("Loading...");
		Path game = gameFolder.get();
		new SwingWorker<ModLibrary.Listing, Void>() {
			@Override
			protected ModLibrary.Listing doInBackground() {
				return ModLibrary.list(modsFolder, game);
			}

			@Override
			protected void done() {
				loading = false;
				try {
					show(get(), selectFolder);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					message.setText("The mods couldn't be listed: " + e.getCause());
				}
			}
		}.execute();
	}

	public boolean isLoading() {
		return loading;
	}

	public int modCount() {
		return mods.size();
	}

	public String messageText() {
		return message.getText();
	}

	public ModEntry selectedMod() {
		return list.getSelectedValue();
	}

	public void select(String folderName) {
		for (int i = 0; i < mods.size(); i++) {
			if (mods.get(i).folderName().equals(folderName)) {
				list.setSelectedIndex(i);
			}
		}
	}

	public void chooseSelected() {
		ModEntry selected = list.getSelectedValue();
		if (selected != null && selected.refusal() != null) {
			message.setText(selected.refusal());
		} else if (selected != null) {
			onChosen.accept(selected);
		}
	}

	private void show(ModLibrary.Listing listing, String selectFolder) {
		for (ModEntry mod : listing.mods()) {
			mods.addElement(mod);
			if (mod.folderName().equals(selectFolder)) {
				list.setSelectedValue(mod, true);
			}
		}
		if (listing.problem() != null) {
			message.setText(listing.problem());
		} else if (mods.isEmpty()) {
			message.setText("There are no mods in " + modsFolder + " yet.");
			createButton.setVisible(true);
		} else {
			message.setText(" ");
		}
	}
}
