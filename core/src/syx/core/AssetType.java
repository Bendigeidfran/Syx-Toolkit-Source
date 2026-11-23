package syx.core;

import java.util.List;

/**
 * A kind of asset a modder can add under a new name, or, with a fixed name, one the game builds from a single
 * file of its own that a mod can only override.
 *
 * @param initFolder the folder under assets/init
 * @param textFolder the folder under assets/text, or null if the type has no text file
 * @param loader the game class that reads it, or null if that hasn't been reviewed
 * @param filePrefix what the game requires a file's name to start with, such as FARM_
 * @param handbookLocations where the handbook describes it, with the most specific first
 * @param fixedName the only name the asset can have, or null if the user picks one. The game reads exactly
 *                  {@code filePrefix + fixedName}, such as _COURT (court/ROOM_COURT.java:33), and merges a mod's
 *                  file of that name over its own (Json.java:29-42), so the asset is always an override
 */
public record AssetType(String label, String initFolder, String textFolder, String loader, String filePrefix, List<String> handbookLocations, String fixedName) {

	public AssetType(String label, String initFolder, String textFolder, String loader, String filePrefix, List<String> handbookLocations) {
		this(label, initFolder, textFolder, loader, filePrefix, handbookLocations, null);
	}

	public AssetType(String label, String initFolder, String textFolder, String loader) {
		this(label, initFolder, textFolder, loader, "", List.of("assets/init/" + initFolder + "/"));
	}

	/** A room the game builds from one file of its own, such as _COURT.txt, which a mod can only override. */
	public static AssetType fixedRoom(String label, String loader, String file) {
		return new AssetType(label, "room", "room", loader, "_", List.of("assets/init/room/" + file + ".txt", "assets/init/room/"), file.substring(1));
	}

	/** What every front end files the room types under. */
	public static final String ROOMS_GROUP = "Rooms";

	/** The group this type is listed under, or null if it's listed on its own. The generic Room has no prefix, so it isn't in the group. */
	public String group() {
		return "room".equals(initFolder) && !filePrefix.isEmpty() ? ROOMS_GROUP : null;
	}

	/** True if the asset overrides one of the game's own files, so its name is fixed and absent tags keep the game's values. */
	public boolean isOverride() {
		return fixedName != null;
	}

	/** The name of the asset's files, without .txt. */
	public String fileName(String name) {
		return filePrefix + name;
	}

	/** True if a file of this name, without .txt, is one of this type's. */
	public boolean ownsFile(String stem) {
		return isOverride() ? stem.equals(fileName(fixedName)) : stem.startsWith(filePrefix);
	}
}
