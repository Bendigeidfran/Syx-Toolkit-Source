package syx.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads everything a mod's screens and an agent's calls need, the same way for both. */
public final class Workspace {

	private Workspace() {
	}

	/**
	 * What can be known about a mod in the player's game. Mod files can change at any time, so this is
	 * read afresh when it's needed rather than kept.
	 *
	 * @param versionWarning what creating the mod's version folder would do, or null
	 */
	public record Context(GameParser parser, GameParser.GameVersion version, Path versionFolder, List<AssetCatalog.Entry> entries, GameAssetIndex index,
			String versionWarning, HandbookStore.Loaded handbook, ResourceProducers producers) implements AutoCloseable {

		@Override
		public void close() {
			parser.close();
		}
	}

	/** The caller closes it, which closes the game's parser. */
	public static Context open(Path game, HandbookStore handbook, ModEntry mod) throws GameParser.UnavailableException, IOException {
		GameParser parser = GameParser.open(GameInstall.jarIn(game));
		boolean opened = false;
		try {
			GameParser.GameVersion version = parser.version();
			Path versionFolder = AssetCreator.versionFolder(mod.root(), version);
			Context context = new Context(parser, version, versionFolder, AssetCatalog.load(GameInstall.jarIn(game)), GameAssetIndex.load(game, versionFolder, parser),
					AssetCreator.versionFolderWarning(mod.root(), version), handbook.load(version), ResourceProducers.load(game, versionFolder, parser));
			opened = true;
			return context;
		} finally {
			if (!opened) {
				parser.close();
			}
		}
	}

	/** Home's warning about resources nothing produces, or null. A check that can't run says nothing, rather than raising an alarm it can't back up. */
	public static String producerWarning(ModEntry mod, Path game) {
		if (!GameInstall.isValid(game)) {
			return null;
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Path version = AssetCreator.versionFolder(mod.root(), parser.version());
			if (!Files.isDirectory(version.resolve("assets").resolve("init").resolve("resource"))) {
				return null;
			}
			return ResourceProducers.warning(ResourceProducers.load(game, version, parser).unproducedIn(version));
		} catch (GameParser.UnavailableException | IOException e) {
			return null;
		}
	}

	/** Home's warning about missing sound entries, or null, on the same terms. */
	public static String soundWarning(ModEntry mod, Path game) {
		if (!GameInstall.isValid(game)) {
			return null;
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Path version = AssetCreator.versionFolder(mod.root(), parser.version());
			if (!Files.isDirectory(version.resolve("assets").resolve("init"))) {
				return null;
			}
			return SoundEntries.warning(SoundEntries.load(game, version, parser).missingIn(version, parser));
		} catch (GameParser.UnavailableException | IOException e) {
			return null;
		}
	}
}
