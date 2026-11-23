package syx.core;

import java.nio.file.Path;
import java.util.List;

/**
 * A mod in the mods folder.
 *
 * @param folderName the mod's folder, which is what the app saves as the current mod
 * @param root the mod's root folder
 * @param displayName the NAME from _Info.txt, or the folder name if that can't be read
 * @param versionFolders folders such as V71
 * @param outdated true if it has version folders, but none for the current game version
 * @param problem why the launcher wouldn't load it, or null
 * @param refusal why the toolkit won't open it, or null. Only a link into the game install or the
 *                Workshop folder is refused, because the toolkit never writes there.
 */
public record ModEntry(String folderName, Path root, String displayName, List<String> versionFolders, boolean outdated, String problem, String refusal) {

	public ModEntry(String folderName, Path root, String displayName, List<String> versionFolders, boolean outdated, String problem) {
		this(folderName, root, displayName, versionFolders, outdated, problem, null);
	}

	/** A note about the mod's state, or null if there's nothing to say. */
	public String status() {
		if (refusal != null) {
			return refusal;
		}
		if (problem != null) {
			return problem + ".";
		}
		return outdated ? "This mod is outdated. It only has " + String.join(", ", versionFolders) + "." : null;
	}

	/** One line describing the mod, so every front end says the same thing. */
	public String describe() {
		String text = displayName.equals(folderName) ? displayName : displayName + " (folder: " + folderName + ")";
		if (refusal != null) {
			return text + ": " + refusal;
		}
		if (problem != null) {
			return text + ": " + problem;
		}
		return outdated ? text + ": Outdated, has " + String.join(", ", versionFolders) : text;
	}
}
