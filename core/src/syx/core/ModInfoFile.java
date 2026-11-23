package syx.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import syx.core.TagLocator.Located;

/**
 * Reads and edits a mod's _Info.txt. Only changed values are touched, so comments, order, spacing
 * and unknown tags stay byte-identical. A save only replaces the file after the game's own parser
 * has read the result back correctly.
 */
public final class ModInfoFile {

	/** Says why a file can't be edited or saved. The file on disk is unchanged. */
	public static final class InfoFileException extends Exception {
		private static final long serialVersionUID = 1L;

		InfoFileException(String message) {
			super(message);
		}
	}

	/**
	 * A tag in the file.
	 *
	 * @param known the field, or null for a tag the game doesn't read here
	 * @param value what the game reads, or the empty string for a block or list
	 * @param wrongForm true if it's written in a form the game would reject
	 */
	public record Field(String key, ModInfoField known, String value, boolean wrongForm) {
		public boolean removable() {
			return known == null || known.removable();
		}
	}

	private final Path modRoot;
	private final Path file;
	private final byte[] originalBytes;
	private final String text;
	private final List<GameParser.TagValue> tags;
	private final List<Located> located;

	private ModInfoFile(Path modRoot, Path file, byte[] originalBytes, String text, List<GameParser.TagValue> tags, List<Located> located) {
		this.modRoot = modRoot;
		this.file = file;
		this.originalBytes = originalBytes;
		this.text = text;
		this.tags = tags;
		this.located = located;
	}

	public static ModInfoFile load(Path modRoot, GameParser parser) throws InfoFileException {
		Path file = modRoot.resolve("_Info.txt");
		if (!Files.exists(file)) {
			return new ModInfoFile(modRoot, file, null, "", List.of(), List.of());
		}
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
		} catch (IOException e) {
			throw new InfoFileException("_Info.txt couldn't be read: " + e.getMessage());
		}
		String text = decode(bytes);
		GameParser.Tags read = parser.tags(file);
		if (!read.outcome().ok()) {
			throw new InfoFileException(describe(read.outcome()));
		}
		List<Located> located = scan(text);
		if (!keysOf(read.tags()).equals(located.stream().map(Located::key).toList())) {
			throw new InfoFileException("_Info.txt is laid out in a way this tool can't safely edit, so it hasn't been changed.");
		}
		return new ModInfoFile(modRoot, file, bytes, text, read.tags(), located);
	}

	/** Every tag in file order. */
	public List<Field> fields() {
		List<Field> fields = new ArrayList<>();
		for (GameParser.TagValue tag : tags) {
			ModInfoField known = ModInfoField.find(tag.key());
			boolean wrongForm = known != null && (tag.kind() != (known.kind().isText() ? GameParser.ValueKind.TEXT : GameParser.ValueKind.PLAIN));
			fields.add(new Field(tag.key(), known, tag.value() == null ? "" : tag.value(), wrongForm));
		}
		return fields;
	}

	/** The valid fields that aren't in the file yet. */
	public List<ModInfoField> missingFields() {
		List<String> present = keysOf(tags);
		return Arrays.stream(ModInfoField.values()).filter(f -> !present.contains(f.name())).toList();
	}

	/**
	 * Returns why a field's value can't be saved, or null if it's fine. A value that's unchanged and
	 * in the right form is always fine, because editing another field shouldn't be blocked by it.
	 */
	public String problemWith(ModInfoField field, String value) {
		GameParser.TagValue existing = tag(field.name());
		return existing != null && !isChange(field, existing, value) ? null : field.problemWith(value);
	}

	/** The value in the file for a field, or null if it isn't there or is a block or list. */
	public String existingValue(ModInfoField field) {
		GameParser.TagValue existing = tag(field.name());
		return existing == null ? null : existing.value();
	}

	public boolean hasChanges(Map<ModInfoField, String> values) {
		return hasChanges(values, Set.of());
	}

	/** Whether saving these values and removing these tags would change anything. */
	public boolean hasChanges(Map<ModInfoField, String> values, Set<String> removals) {
		if (removals.stream().anyMatch(key -> tag(key) != null)) {
			return true;
		}
		for (Map.Entry<ModInfoField, String> entry : values.entrySet()) {
			GameParser.TagValue existing = tag(entry.getKey().name());
			if (!removals.contains(entry.getKey().name()) && (existing == null || isChange(entry.getKey(), existing, entry.getValue()))) {
				return true;
			}
		}
		return false;
	}

	public boolean save(Map<ModInfoField, String> values, GameParser parser) throws InfoFileException {
		return save(values, Set.of(), parser);
	}

	/**
	 * Saves the given values and removes the given tags. A tag being removed wins over a value for it.
	 * Returns false if there was nothing to change, so nothing was written.
	 */
	public boolean save(Map<ModInfoField, String> requested, Set<String> removals, GameParser parser) throws InfoFileException {
		Map<ModInfoField, String> values = new LinkedHashMap<>(requested);
		values.keySet().removeIf(field -> removals.contains(field.name()));
		for (String key : removals) {
			ModInfoField known = ModInfoField.find(key);
			if (known != null && !known.removable()) {
				throw new InfoFileException(key + " can't be removed, because the launcher would list the mod as ???.");
			}
		}
		for (Map.Entry<ModInfoField, String> entry : values.entrySet()) {
			String problem = problemWith(entry.getKey(), entry.getValue());
			if (problem != null) {
				throw new InfoFileException(entry.getKey() + ": " + problem);
			}
		}
		if (!hasChanges(values, removals)) {
			return false;
		}
		checkUnchangedOnDisk();

		String updated = withChanges(values, removals);
		Path temp = null;
		try {
			temp = Files.createTempFile(modRoot, "_Info", ".syxtmp");
			Files.write(temp, updated.getBytes(StandardCharsets.UTF_8));
			checkReadsBack(parser, temp, values, removals);
			checkUnchangedOnDisk();
			replaceOriginal(temp);
			return true;
		} catch (IOException e) {
			throw new InfoFileException("_Info.txt couldn't be saved, and hasn't been changed: " + e.getMessage());
		} finally {
			deleteQuietly(temp);
		}
	}

	private boolean isChange(ModInfoField field, GameParser.TagValue existing, String value) {
		GameParser.ValueKind right = field.kind().isText() ? GameParser.ValueKind.TEXT : GameParser.ValueKind.PLAIN;
		return existing.kind() != right || !value.equals(existing.value());
	}

	private String withChanges(Map<ModInfoField, String> values, Set<String> removals) {
		String eol = text.contains("\r\n") ? "\r\n" : "\n";
		StringBuilder result = new StringBuilder(text);

		// Working from the end keeps the earlier positions valid.
		for (int i = located.size() - 1; i >= 0; i--) {
			Located place = located.get(i);
			ModInfoField field = ModInfoField.find(place.key());
			if (removals.contains(place.key())) {
				removeEntry(result, place);
			} else if (field != null && values.containsKey(field) && isChange(field, tags.get(i), values.get(field))) {
				result.replace(place.start(), place.end(), encode(field, values.get(field), eol));
			}
		}

		List<ModInfoField> added = values.keySet().stream().filter(f -> tag(f.name()) == null)
				.sorted().toList();
		if (!added.isEmpty()) {
			Located last = located.isEmpty() ? null : located.get(located.size() - 1);
			if (last != null && !last.hasComma() && !removals.contains(last.key())) {
				// That value is at the very end of the file, so it's the last thing here.
				result.append(',');
			}
			if (result.length() > 0 && result.charAt(result.length() - 1) != '\n' && result.charAt(result.length() - 1) != '\r') {
				result.append(eol);
			}
			for (ModInfoField field : added) {
				result.append(field.name()).append(": ").append(encode(field, values.get(field), eol)).append(',').append(eol);
			}
		}
		return result.toString();
	}

	/** Removes a tag, and its line if nothing else is on it. A comment beside it is kept. */
	private static void removeEntry(StringBuilder result, Located place) {
		int[] range = TagLocator.entryRange(result, place);
		result.delete(range[0], range[1]);
	}

	private static String encode(ModInfoField field, String value, String eol) {
		return field.kind().isText() ? "\"" + value.replace("\n", eol) + "\"" : value;
	}

	/** The game's parser has the final word on whether the new file is right. */
	private void checkReadsBack(GameParser parser, Path temp, Map<ModInfoField, String> values, Set<String> removals) throws InfoFileException {
		GameParser.Tags read = parser.tags(temp);
		if (!read.outcome().ok()) {
			throw new InfoFileException("The change was refused, because the game couldn't read the result. " + describe(read.outcome()));
		}
		List<String> expectedKeys = new ArrayList<>(keysOf(tags));
		expectedKeys.removeAll(removals);
		values.keySet().stream().filter(f -> tag(f.name()) == null).sorted().forEach(f -> expectedKeys.add(f.name()));
		if (!keysOf(read.tags()).equals(expectedKeys)) {
			throw new InfoFileException("The change was refused, because the game would read different tags from the file.");
		}
		for (GameParser.TagValue after : read.tags()) {
			ModInfoField field = ModInfoField.find(after.key());
			String expected;
			GameParser.ValueKind expectedKind;
			if (field != null && values.containsKey(field)) {
				expected = values.get(field);
				expectedKind = field.kind().isText() ? GameParser.ValueKind.TEXT : GameParser.ValueKind.PLAIN;
			} else {
				GameParser.TagValue before = tag(after.key());
				expected = before.value();
				expectedKind = before.kind();
			}
			if (after.kind() != expectedKind || !Objects.equals(after.value(), expected)) {
				throw new InfoFileException("The change was refused, because the game would read " + after.key() + " differently.");
			}
		}
	}

	private void checkUnchangedOnDisk() throws InfoFileException {
		try {
			boolean exists = Files.exists(file);
			boolean same = originalBytes == null ? !exists : exists && Arrays.equals(originalBytes, Files.readAllBytes(file));
			if (!same) {
				throw new InfoFileException("_Info.txt has changed since it was opened. Please go back and open it again, so nothing is lost.");
			}
		} catch (IOException e) {
			throw new InfoFileException("_Info.txt couldn't be read: " + e.getMessage());
		}
	}

	private void replaceOriginal(Path temp) throws IOException {
		try {
			Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private GameParser.TagValue tag(String key) {
		return tags.stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
	}

	private static List<String> keysOf(List<GameParser.TagValue> tags) {
		return tags.stream().map(GameParser.TagValue::key).toList();
	}

	private static String describe(GameParser.Outcome outcome) {
		if (outcome.dataError() != null) {
			SyntaxChecker.SyntaxProblem problem = SyntaxChecker.translate(outcome.dataError());
			String where = problem.line() == null ? "" : " on line " + problem.line();
			return "_Info.txt has a mistake" + where + ". " + problem.message() + " Please fix this in a text editor first.";
		}
		return "_Info.txt couldn't be checked: " + outcome.otherFailure();
	}

	private static String decode(byte[] bytes) throws InfoFileException {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new InfoFileException("_Info.txt isn't saved as UTF-8, so editing it here could damage it. Please resave it as UTF-8 first.");
		}
	}

	private static void deleteQuietly(Path temp) {
		if (temp != null) {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException e) {
				// A leftover temp file is harmless to the game, and there's nothing more to do here.
			}
		}
	}

	/** load() refuses the file unless these keys match the game's parser, and save() checks the result with it. */
	private static List<Located> scan(String content) throws InfoFileException {
		try {
			return TagLocator.scan(content);
		} catch (RuntimeException e) {
			throw new InfoFileException("_Info.txt is laid out in a way this tool can't safely edit, so it hasn't been changed.");
		}
	}
}
