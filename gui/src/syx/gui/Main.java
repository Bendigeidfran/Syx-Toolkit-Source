package syx.gui;

import javax.swing.SwingUtilities;

import syx.core.GameFolderFinder;
import syx.core.HandbookStore;
import syx.core.ManifestStore;
import syx.core.ModsFolder;
import syx.core.ToolkitSettings;

public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			ToolkitSettings settings = ToolkitSettings.openDefault();
			Theme.install(settings.theme());
			MainScreen screen = new MainScreen(settings, ModsFolder.standard(),
					GameFolderFinder::find, MainScreen::pickGameFolder, HomeScreen::openInFileManager,
					HandbookStore.openDefault(), ManifestStore.openDefault(), AssetEditorScreen::openInBrowser);
			screen.setVisible(true);
			screen.start();
		});
	}
}
