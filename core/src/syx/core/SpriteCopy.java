package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import syx.core.TagSpec.Kind;

/**
 * Copies one vanilla asset's own sprite sheets into a mod and renames them for the asset being edited.
 * Shared sheets, whose names don't carry that asset's name, stay as the game's. Nothing is overwritten,
 * and nothing is written into the game install.
 */
public final class SpriteCopy {

	private static final String ZIP_ROOT = "data/assets/";
	private static final Pattern FRAME = Pattern.compile("^(\\s*)(\\S+?)(\\s*:\\s*\\S+\\s*)$");

	/** Why a copy was refused. Nothing was written. */
	public enum Why {
		/** The vanilla asset has no sheet named for it. A mine's frames are shared, so this is usual. */
		NONE,
		/** A file of the new name is already in the mod. */
		CLASH,
		/** The game has no such asset, or one of its images is missing. */
		MISSING,
		OTHER
	}

	/** A copy was refused, and nothing was written. */
	public static final class Rejected extends Exception {
		private static final long serialVersionUID = 1L;
		private final Why why;

		Rejected(Why why, String message) {
			super(message);
			this.why = why;
		}

		public Why why() {
			return why;
		}
	}

	/**
	 * What was written.
	 *
	 * @param files paths relative to the mod's folder
	 * @param values sprite fields that now name the copies, by the form's tag path
	 */
	public record Result(List<String> files, Map<String, String> values) {
	}

	private record Sheet(String source, String stem) {
	}

	private record Planned(String source, Path dest, String relative, String modRelative) {
	}

	private SpriteCopy() {
	}

	/** Whether the form has a sprite or frames tag, at any depth. */
	public static boolean hasSprites(AssetSchema schema) {
		return hasSprites(schema.dataTags()) || hasSprites(schema.textTags()) || schema.companions().stream().anyMatch(file -> hasSprites(file.tags()));
	}

	private static boolean hasSprites(List<TagSpec> tags) {
		for (TagSpec tag : tags) {
			if (tag.kind() == Kind.SPRITE || tag.kind() == Kind.FRAMES) {
				return true;
			}
			if (tag.shape() != null && hasSprites(tag.shape().children())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Copies the vanilla asset's own sheets into the mod's version folder and records them on {@code index}.
	 * The returned values are what the form's sprite fields should become. They are not saved here.
	 */
	public static Result copy(AssetCatalog.Entry entry, Path gameFolder, ModEntry mod, GameParser.GameVersion version, GameAssetIndex index, GameParser parser, String vanillaName,
			String assetName) throws IOException, GameParser.ReadException, Rejected {
		if (!hasSprites(entry.schema())) {
			throw new Rejected(Why.OTHER, "A " + entry.type().label().toLowerCase(Locale.ROOT) + " has no sprites to copy.");
		}
		AssetForm.VanillaStart start = AssetForm.vanillaStart(entry, gameFolder, index, parser, vanillaName, assetName, false);
		if (start == null) {
			throw new Rejected(Why.MISSING, "The game has no " + entry.type().label().toLowerCase(Locale.ROOT) + " called " + vanillaName + ".");
		}
		Set<String> stems = new LinkedHashSet<>();
		for (Map.Entry<String, String> value : start.values().entrySet()) {
			TagSpec tag = entry.schema().find(value.getKey());
			if (tag == null || value.getValue() == null || value.getValue().isBlank()) {
				continue;
			}
			if (tag.kind() == Kind.SPRITE || tag.kind() == Kind.PLAIN) {
				stems.add(value.getValue().trim());
			} else if (tag.kind() == Kind.FRAMES) {
				stems.addAll(frameStems(value.getValue()));
			}
		}
		String token = tokenFor(vanillaName, stems);
		if (token == null) {
			throw new Rejected(Why.NONE, vanillaName + " has no sprites named for it, so nothing was copied.");
		}
		String renamed = cased(assetName);
		Map<String, Sheet> chosen = new LinkedHashMap<>();
		for (Map.Entry<String, String> value : start.values().entrySet()) {
			TagSpec tag = entry.schema().find(value.getKey());
			if (tag == null || value.getValue() == null || value.getValue().isBlank()) {
				continue;
			}
			if (tag.kind() == Kind.SPRITE && tag.sprite() != null && containsToken(value.getValue().trim(), token)) {
				addSheet(chosen, tag.sprite().folder() + value.getValue().trim() + ".png", value.getValue().trim());
			} else if (tag.kind() == Kind.FRAMES && tag.sprite() != null) {
				for (String stem : frameStems(value.getValue())) {
					if (containsToken(stem, token)) {
						addSheet(chosen, tag.sprite().folder() + stem + ".png", stem);
					}
				}
			} else if (tag.kind() == Kind.PLAIN && containsToken(value.getValue().trim(), token)) {
				for (String path : index.spriteSheets(value.getValue().trim())) {
					addSheet(chosen, path, value.getValue().trim());
				}
			}
		}
		if (chosen.isEmpty()) {
			throw new Rejected(Why.NONE, vanillaName + " has no sprites named for it, so nothing was copied.");
		}
		Path versionFolder = AssetCreator.versionFolder(mod.root(), version);
		List<Planned> planned = new ArrayList<>();
		List<String> clashes = new ArrayList<>();
		for (Sheet sheet : chosen.values()) {
			String folder = sheet.source().substring(0, sheet.source().lastIndexOf('/') + 1);
			String destRelative = folder + renameStem(sheet.stem(), token, renamed) + ".png";
			Path dest = resolve(versionFolder.resolve("assets"), destRelative);
			refuseLinks(mod.root(), dest);
			String modRelative = mod.root().relativize(dest).toString().replace('\\', '/');
			if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
				clashes.add(modRelative);
			}
			planned.add(new Planned(sheet.source(), dest, destRelative, modRelative));
		}
		if (!clashes.isEmpty()) {
			throw new Rejected(Why.CLASH, "Nothing was copied, because " + String.join(", ", clashes) + (clashes.size() == 1 ? " already exists." : " already exist."));
		}
		Path zipFile = gameFolder.resolve("base").resolve("data.zip");
		List<Path> written = new ArrayList<>();
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			for (Planned item : planned) {
				ZipEntry source = zip.getEntry(ZIP_ROOT + item.source());
				if (source == null) {
					throw new Rejected(Why.MISSING, "The game has no image at " + item.source() + ".");
				}
			}
			for (Planned item : planned) {
				ZipEntry source = zip.getEntry(ZIP_ROOT + item.source());
				Files.createDirectories(item.dest().getParent());
				try (InputStream in = zip.getInputStream(source)) {
					Files.copy(in, item.dest());
				}
				written.add(item.dest());
				index.rememberSprite(item.dest(), item.relative());
			}
		} catch (IOException | Rejected | RuntimeException e) {
			for (Path file : written) {
				Files.deleteIfExists(file);
			}
			throw e;
		}
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> copiedStems = new LinkedHashSet<>();
		chosen.values().forEach(sheet -> copiedStems.add(sheet.stem()));
		for (Map.Entry<String, String> value : start.values().entrySet()) {
			TagSpec tag = entry.schema().find(value.getKey());
			if (tag == null || value.getValue() == null) {
				continue;
			}
			String next = renamedValue(tag, value.getValue(), token, renamed, copiedStems);
			if (next != null && !next.equals(value.getValue())) {
				values.put(value.getKey(), next);
			}
		}
		return new Result(planned.stream().map(Planned::modRelative).toList(), values);
	}

	private static void addSheet(Map<String, Sheet> chosen, String source, String stem) {
		chosen.putIfAbsent(source, new Sheet(source, stem));
	}

	private static String renamedValue(TagSpec tag, String value, String token, String renamed, Set<String> copiedStems) {
		if (tag.kind() == Kind.SPRITE && containsToken(value.trim(), token)) {
			return renameStem(value.trim(), token, renamed);
		}
		if (tag.kind() == Kind.FRAMES) {
			String next = renameFrames(value, token, renamed);
			return next.equals(value) ? null : next;
		}
		if (tag.kind() == Kind.PLAIN && copiedStems.contains(value.trim()) && containsToken(value.trim(), token)) {
			return renameStem(value.trim(), token, renamed);
		}
		return null;
	}

	/** The vanilla file name, first character upper and the rest lower. {@code Q_AMEVIA} is {@code Amevia}: its sheets drop the leading segment. */
	static String tokenFor(String name, Set<String> stems) {
		String full = cased(name);
		if (stems.stream().anyMatch(stem -> containsToken(stem, full))) {
			return full;
		}
		int split = name.indexOf('_');
		if (split > 0 && split < name.length() - 1 && name.substring(0, split).matches("[A-Za-z]+")) {
			String shorter = cased(name.substring(split + 1));
			if (stems.stream().anyMatch(stem -> containsToken(stem, shorter))) {
				return shorter;
			}
		}
		return null;
	}

	/** First character upper, the rest lower, which is how the game names a sheet from its file name. */
	public static String cased(String name) {
		if (name == null || name.isEmpty()) {
			return name;
		}
		return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	/**
	 * The token as a whole name: {@code Human}, {@code CrownHuman} and {@code Human_Village} match, and
	 * {@code Humanoid} does not, because the letters after {@code Human} carry on the word.
	 */
	static boolean containsToken(String stem, String token) {
		int from = 0;
		while (from <= stem.length() - token.length()) {
			int at = stem.indexOf(token, from);
			if (at < 0) {
				return false;
			}
			if (tokenAt(stem, at, token)) {
				return true;
			}
			from = at + 1;
		}
		return false;
	}

	private static boolean tokenAt(String stem, int at, String token) {
		int end = at + token.length();
		boolean start = at == 0 || stem.charAt(at - 1) == '_' || (Character.isUpperCase(stem.charAt(at)) && Character.isLowerCase(stem.charAt(at - 1)));
		boolean finish = end == stem.length() || stem.charAt(end) == '_' || Character.isUpperCase(stem.charAt(end));
		return start && finish;
	}

	static String renameStem(String stem, String token, String renamed) {
		StringBuilder out = new StringBuilder();
		int from = 0;
		while (from < stem.length()) {
			int at = stem.indexOf(token, from);
			if (at < 0 || !tokenAt(stem, at, token)) {
				if (at < 0) {
					out.append(stem, from, stem.length());
					break;
				}
				out.append(stem, from, at + 1);
				from = at + 1;
				continue;
			}
			out.append(stem, from, at).append(renamed);
			from = at + token.length();
		}
		return out.toString();
	}

	private static List<String> frameStems(String text) {
		List<String> stems = new ArrayList<>();
		for (String line : text.split("\\R", -1)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.equals("-")) {
				continue;
			}
			Matcher match = FRAME.matcher(line);
			if (match.matches()) {
				stems.add(match.group(2));
			}
		}
		return stems;
	}

	private static String renameFrames(String text, String token, String renamed) {
		String[] lines = text.split("\\R", -1);
		boolean changed = false;
		for (int i = 0; i < lines.length; i++) {
			Matcher match = FRAME.matcher(lines[i]);
			if (match.matches() && containsToken(match.group(2), token)) {
				lines[i] = match.group(1) + renameStem(match.group(2), token, renamed) + match.group(3);
				changed = true;
			}
		}
		return changed ? String.join("\n", lines) : text;
	}

	private static Path resolve(Path folder, String relative) {
		Path path = folder;
		for (String part : relative.split("/")) {
			path = path.resolve(part);
		}
		return path;
	}

	/** No file this writes is reached through a link, so nothing lands outside the mod. */
	private static void refuseLinks(Path root, Path target) throws IOException, Rejected {
		Path at = root;
		Path expected = root.toRealPath();
		for (Path part : root.relativize(target.normalize())) {
			at = at.resolve(part);
			expected = expected.resolve(part);
			if (Files.exists(at, LinkOption.NOFOLLOW_LINKS) && !(Files.exists(at) && at.toRealPath().equals(expected))) {
				throw new Rejected(Why.OTHER, at + " is a link to somewhere else, so nothing was copied.");
			}
		}
	}
}
