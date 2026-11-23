package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Where manifests are kept: the Manifests folder beside the toolkit's {@code .exe}, which holds the released
 * ones and the ones a player generates or is given. A rebuild keeps what a player put there. Each is only read
 * as data, and a generated one is never overwritten.
 */
public final class ManifestStore {

	public static final String SHIPPED_FOLDER = "Manifests";
	private static final String GENERATED_INDEX = "generated.sha256";
	private static final String TEMP_SUFFIX = ".syxtmp";

	/** Where a manifest came from, so a report can say how far to trust it. */
	public enum Source {
		SHIPPED("shipped"), GENERATED("generated here"), SHARED("shared");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** A manifest that can be chosen. */
	public record Entry(String id, String version, int major, int minor, String jarSha256, Source source, Path file) {
		/** What a list shows for it. */
		public String label() {
			return version + " (" + source.label() + ")";
		}
	}

	/** @param problems why a file in a folder couldn't be used */
	public record Listing(List<Entry> entries, List<String> problems) {
	}

	/**
	 * The game the toolkit is pointed at.
	 *
	 * @param existing its manifest, if one's already there
	 * @param problem why it can't be read, in which case the rest is null
	 */
	public record Installed(String version, String id, Entry existing, String problem) {
	}

	/** @param created false if that game already had a manifest, so nothing was written */
	public record Generated(Entry entry, boolean created) {
	}

	/** Which versions to start with. Either can be null if there aren't enough manifests. */
	public record Defaults(String oldId, String newId) {
	}

	private record Cached(long size, long modified, Entry entry) {
	}

	private final Path shippedFolder;
	private final Path generatedFolder;
	private final Path reportsFolder;
	private final Map<String, String> pins;
	private final Map<Path, Cached> cache = new HashMap<>();

	/**
	 * For what doesn't save reports.
	 *
	 * @param shippedFolder the folder beside the app, or null when not running a packaged build
	 * @param generatedFolder where generated manifests go, or null if nothing can be generated
	 * @param pins each released file's name and SHA-256
	 */
	public ManifestStore(Path shippedFolder, Path generatedFolder, Map<String, String> pins) {
		this(shippedFolder, generatedFolder, null, pins);
	}

	/** @param reportsFolder where diff reports go, or null if none can be saved */
	public ManifestStore(Path shippedFolder, Path generatedFolder, Path reportsFolder, Map<String, String> pins) {
		this.shippedFolder = shippedFolder;
		this.generatedFolder = generatedFolder;
		this.reportsFolder = reportsFolder;
		this.pins = Map.copyOf(pins);
	}

	/**
	 * The real folders, all beside the app, where a player can find them. Shipped and generated manifests share
	 * the Manifests folder, which a rebuild keeps (scripts/package.ps1). Reports go in their own folder, which a
	 * rebuild clears. Outside a packaged build there's no app folder, so nothing is read or saved.
	 */
	public static ManifestStore openDefault() {
		String app = System.getProperty("jpackage.app-path");
		Path manifests = app == null ? null : Path.of(app).resolveSibling(SHIPPED_FOLDER);
		Path reports = app == null ? null : Path.of(app).resolveSibling(DiffReportFile.FOLDER_NAME);
		return new ManifestStore(manifests, manifests, reports, ShippedManifests.PINS);
	}

	/** These folders, with the real released files' checksums. */
	public static ManifestStore in(Path shippedFolder, Path generatedFolder, Path reportsFolder) {
		return new ManifestStore(shippedFolder, generatedFolder, reportsFolder, ShippedManifests.PINS);
	}

	/** Where diff reports go, or null if none can be saved. */
	public Path reportsFolder() {
		return reportsFolder;
	}

	/** Says why these two can't be compared, or null if they can. */
	public static String problemWith(Entry oldEntry, Entry newEntry) {
		return ModCodeDiff.comparisonProblem(oldEntry.jarSha256(), oldEntry.version(), oldEntry.major(), oldEntry.minor(), newEntry.jarSha256(), newEntry.version(), newEntry.major(), newEntry.minor());
	}

	/** Reads and writes nothing, for what doesn't use manifests. */
	public static ManifestStore none() {
		return new ManifestStore(null, null, Map.of());
	}

	/** Every usable manifest, newest first. If two folders hold one game build, the most trusted is listed. */
	public synchronized Listing list() throws IOException {
		Set<String> generated = readIndex();
		List<Entry> all = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		readFolder(shippedFolder, true, generated, all, problems);
		if (generatedFolder != null && !generatedFolder.equals(shippedFolder)) {
			readFolder(generatedFolder, false, generated, all, problems);
		}
		all.sort(Comparator.comparingInt((Entry entry) -> entry.source().ordinal()));
		Map<String, Entry> byJar = new HashMap<>();
		for (Entry entry : all) {
			byJar.putIfAbsent(entry.jarSha256(), entry);
		}
		List<Entry> entries = new ArrayList<>(byJar.values());
		entries.sort(Comparator.comparingInt(Entry::major).thenComparingInt(Entry::minor).thenComparing(Entry::id).reversed());
		return new Listing(List.copyOf(entries), List.copyOf(problems));
	}

	private void readFolder(Path folder, boolean shipped, Set<String> generated, List<Entry> into, List<String> problems) throws IOException {
		if (folder == null || !Files.isDirectory(folder)) {
			return;
		}
		List<Path> files;
		try (Stream<Path> listed = Files.list(folder)) {
			files = listed.filter(file -> file.getFileName().toString().endsWith(GameManifest.FILE_SUFFIX) && Files.isRegularFile(file)).sorted().toList();
		}
		for (Path file : files) {
			try {
				into.add(read(file, shipped, generated));
			} catch (GameManifest.ManifestException | IOException e) {
				problems.add(file.getFileName() + " can't be used: " + e.getMessage());
			}
		}
	}

	/** A manifest is several megabytes unpacked, so a file that hasn't changed isn't read again. */
	private Entry read(Path file, boolean shipped, Set<String> generated) throws IOException, GameManifest.ManifestException {
		long size = Files.size(file);
		long modified = Files.getLastModifiedTime(file).toMillis();
		Cached cached = cache.get(file);
		Source source = sourceOf(file, shipped, generated);
		if (cached != null && cached.size() == size && cached.modified() == modified) {
			Entry entry = cached.entry();
			return entry.source() == source ? entry : new Entry(entry.id(), entry.version(), entry.major(), entry.minor(), entry.jarSha256(), source, file);
		}
		GameManifest manifest = GameManifest.fromBytes(Files.readAllBytes(file));
		Entry entry = new Entry(manifest.id(), manifest.version(), manifest.major(), manifest.minor(), manifest.jarSha256(), source, file);
		cache.put(file, new Cached(size, modified, entry));
		return entry;
	}

	/** A released file matching its pin is shipped, one this toolkit made is generated, and anything else was shared. */
	private Source sourceOf(Path file, boolean shipped, Set<String> generated) throws IOException {
		String hash = ManifestBuilder.sha256(file);
		if (shipped && hash.equals(pins.get(file.getFileName().toString()))) {
			return Source.SHIPPED;
		}
		return generated.contains(hash) ? Source.GENERATED : Source.SHARED;
	}

	private Set<String> readIndex() throws IOException {
		if (generatedFolder == null) {
			return Set.of();
		}
		Path index = generatedFolder.resolve(GENERATED_INDEX);
		return Files.isRegularFile(index) ? new LinkedHashSet<>(Files.readAllLines(index, StandardCharsets.UTF_8)) : Set.of();
	}

	public synchronized Entry find(String id) throws IOException {
		return list().entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
	}

	public GameManifest load(Entry entry) throws IOException, GameManifest.ManifestException {
		return GameManifest.fromBytes(Files.readAllBytes(entry.file()));
	}

	/** Says which game is installed, and whether it already has a manifest. */
	public synchronized Installed installed(Path game) throws IOException {
		String problem = GameInstall.problemWith(game);
		if (problem != null) {
			return new Installed(null, null, null, problem);
		}
		GameParser.GameVersion version;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			version = parser.version();
		} catch (GameParser.UnavailableException e) {
			return new Installed(null, null, null, e.getMessage());
		}
		String sha = ManifestBuilder.sha256(GameInstall.jarIn(game));
		String shown = "0." + version.major() + "." + version.minor();
		Entry existing = list().entries().stream().filter(entry -> entry.jarSha256().equals(sha)).findFirst().orElse(null);
		return new Installed(shown, GameManifest.idOf(shown, sha), existing, null);
	}

	/** New is the installed game's if there is one, otherwise the newest, and old is the next one down. */
	public static Defaults defaults(List<Entry> entries, Installed installed) {
		if (entries.isEmpty()) {
			return new Defaults(null, null);
		}
		Entry chosen = installed.existing() != null ? installed.existing() : entries.get(0);
		int at = entries.indexOf(chosen);
		for (int i = at + 1; i < entries.size(); i++) {
			Entry candidate = entries.get(i);
			if (candidate.major() != chosen.major() || candidate.minor() != chosen.minor()) {
				return new Defaults(candidate.id(), chosen.id());
			}
		}
		return new Defaults(null, chosen.id());
	}

	/**
	 * Makes a manifest of the installed game, unless there already is one. Nothing in the game's folder is
	 * written, and neither is an existing manifest.
	 */
	public synchronized Generated generate(Path game, BooleanSupplier cancelled) throws IOException, GameParser.UnavailableException {
		if (generatedFolder == null) {
			throw new IOException("Manifests can only be generated from the packaged toolkit, beside its .exe.");
		}
		Installed installed = installed(game);
		if (installed.problem() != null) {
			throw new IOException(installed.problem());
		}
		if (installed.existing() != null) {
			return new Generated(installed.existing(), false);
		}
		GameParser.GameVersion version;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			version = parser.version();
		}
		GameManifest manifest = ManifestBuilder.build(GameInstall.jarIn(game), version, cancelled);
		byte[] bytes = manifest.toBytes();
		Files.createDirectories(generatedFolder);
		// A file of this name it can't read, such as an older format, isn't this toolkit's to replace.
		Path target = generatedFolder.resolve(manifest.fileName());
		for (int attempt = 2; Files.exists(target); attempt++) {
			target = generatedFolder.resolve(manifest.fileName().replace(GameManifest.FILE_SUFFIX, " (" + attempt + ")" + GameManifest.FILE_SUFFIX));
		}
		Path temp = generatedFolder.resolve(target.getFileName() + TEMP_SUFFIX);
		Path index = generatedFolder.resolve(GENERATED_INDEX);
		Path indexTemp = generatedFolder.resolve(GENERATED_INDEX + TEMP_SUFFIX);
		byte[] oldIndex = Files.isRegularFile(index) ? Files.readAllBytes(index) : null;
		try {
			Files.write(temp, bytes);
			String hash = ManifestBuilder.sha256(temp);
			String before = oldIndex == null ? "" : new String(oldIndex, StandardCharsets.UTF_8);
			Files.writeString(indexTemp, before + hash + System.lineSeparator(), StandardCharsets.UTF_8);
			SafeWrite.commit(List.of(new SafeWrite.Change(target, temp, null), new SafeWrite.Change(index, indexTemp, oldIndex)));
		} finally {
			Files.deleteIfExists(temp);
			Files.deleteIfExists(indexTemp);
		}
		Entry entry = find(manifest.id());
		return new Generated(entry, true);
	}
}
