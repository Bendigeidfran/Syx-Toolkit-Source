package syx.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import syx.core.Diagnostic;
import syx.core.DiffReportFile;
import syx.core.GameInstall;
import syx.core.GameManifest;
import syx.core.GameParser;
import syx.core.ManifestBuilder;
import syx.core.ModCodeDiff;

/**
 * Developer's tool for making the shipped manifests and measuring them. Reads game folders, and writes only
 * into the output folder it's given, never over an existing file.
 * Usage: DevManifest build <game> <output folder>
 *        DevManifest noise <old game> <new game>
 *        DevManifest diff <old game> <new game> <mod root>
 *        DevManifest report <old game> <new game> <mod root> <mod name>
 */
public final class DevManifest {

	private DevManifest() {
	}

	public static void main(String[] args) throws Exception {
		switch (args[0]) {
		case "build" -> build(Paths.get(args[1]), Paths.get(args[2]));
		case "noise" -> noise(Paths.get(args[1]), Paths.get(args[2]));
		case "diff" -> diff(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
		case "report" -> System.out.println(DiffReportFile.markdown(ModCodeDiff.compare(Paths.get(args[3]), manifest(Paths.get(args[1])), "dev", manifest(Paths.get(args[2])), "dev", () -> false), args[4]));
		default -> throw new IllegalArgumentException(args[0]);
		}
	}

	private static GameManifest manifest(Path game) throws Exception {
		GameParser.GameVersion version;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			version = parser.version();
		}
		return ManifestBuilder.build(GameInstall.jarIn(game), version, () -> false);
	}

	private static void build(Path game, Path output) throws Exception {
		long start = System.currentTimeMillis();
		GameManifest first = manifest(game);
		byte[] bytes = first.toBytes();
		byte[] again = manifest(game).toBytes();
		System.out.println(first.id() + ": " + first.types().size() + " classes, " + bytes.length + " bytes, class versions " + first.classVersions() + ", " + (System.currentTimeMillis() - start) + " ms for two builds");
		System.out.println("two builds are byte-identical: " + Arrays.equals(bytes, again));
		GameManifest read = GameManifest.fromBytes(bytes);
		System.out.println("reads back with the same classes: " + (read.types().equals(first.types())));
		Files.createDirectories(output);
		Path target = output.resolve(first.fileName());
		if (Files.exists(target)) {
			System.out.println("already there, not replaced: " + target + " identical: " + Arrays.equals(Files.readAllBytes(target), bytes));
		} else {
			Files.write(target, bytes);
			System.out.println("wrote " + target);
		}
		System.out.println("sha256 " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
		excluded(game);
	}

	/** What's under the excluded packages, so it can be seen that they hold only libraries. */
	private static void excluded(Path game) throws IOException {
		Map<String, Integer> folders = new TreeMap<>();
		try (ZipFile zip = new ZipFile(GameInstall.jarIn(game).toFile())) {
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				String[] parts = entry.getName().split("/");
				if (entry.getName().endsWith(".class") && parts.length > 2 && ManifestBuilder.EXCLUDED_PACKAGES.contains(parts[0])) {
					folders.merge(parts[0] + "/" + parts[1], 1, Integer::sum);
				}
			}
		}
		System.out.println("excluded packages: " + folders);
	}

	/** Compares normalised bodies only where the top-level source file is byte-identical, so any difference is noise. */
	private static void noise(Path oldGame, Path newGame) throws Exception {
		GameManifest oldManifest = manifest(oldGame);
		GameManifest newManifest = manifest(newGame);
		Map<String, String> oldSources = sourceHashes(oldGame);
		Map<String, String> newSources = sourceHashes(newGame);
		int classes = 0;
		int changedClasses = 0;
		int methods = 0;
		int changedMethods = 0;
		int memberChanges = 0;
		List<String> samples = new ArrayList<>();
		for (GameManifest.Type type : newManifest.types().values()) {
			GameManifest.Type before = oldManifest.type(type.name());
			String top = type.name().replaceAll("\\$.*$", "") + ".java";
			if (before == null || oldSources.get(top) == null || !oldSources.get(top).equals(newSources.get(top))) {
				continue;
			}
			classes++;
			boolean changed = false;
			for (Map.Entry<String, GameManifest.Method> method : type.methods().entrySet()) {
				GameManifest.Method was = before.methods().get(method.getKey());
				if (was == null) {
					memberChanges++;
					continue;
				}
				if (method.getValue().body() != null) {
					methods++;
					if (!method.getValue().body().equals(was.body())) {
						changedMethods++;
						changed = true;
						if (samples.size() < 15) {
							samples.add(type.name() + "." + method.getKey());
						}
					}
				}
			}
			changedClasses += changed ? 1 : 0;
		}
		System.out.println("classes whose source file is byte-identical: " + classes + ", of which any method body looks changed: " + changedClasses);
		System.out.println("methods compared: " + methods + ", looking changed: " + changedMethods + " (" + String.format("%.1f", methods == 0 ? 0 : 100.0 * changedMethods / methods) + "%); members only in the new build: " + memberChanges);
		samples.forEach(sample -> System.out.println("  " + sample));
	}

	private static Map<String, String> sourceHashes(Path game) throws Exception {
		Map<String, String> out = new HashMap<>();
		try (ZipFile zip = new ZipFile(game.resolve("info/SongsOfSyx-sources.jar").toFile())) {
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				if (entry.getName().endsWith(".java")) {
					try (InputStream in = zip.getInputStream(entry)) {
						out.put(entry.getName(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
					}
				}
			}
		}
		return out;
	}

	/** Each direction, for looking at what a real mod reports. The findings name what to check by hand. */
	private static void diff(Path oldGame, Path newGame, Path modRoot) throws Exception {
		GameManifest oldManifest = manifest(oldGame);
		GameManifest newManifest = manifest(newGame);
		print(ModCodeDiff.compare(modRoot, oldManifest, "dev", newManifest, "dev", () -> false));
		System.out.println("=== and the other way round");
		print(ModCodeDiff.compare(modRoot, newManifest, "dev", oldManifest, "dev", () -> false));
	}

	private static void print(ModCodeDiff.Report report) {
		System.out.println(report.summary());
		System.out.println(report.bodyNote());
		Map<String, Integer> byCode = new TreeMap<>();
		report.findings().forEach(finding -> byCode.merge(finding.code(), 1, Integer::sum));
		System.out.println(byCode);
		for (Diagnostic finding : report.findings()) {
			System.out.println(finding.severity() + " " + finding.code() + " " + finding.file() + (finding.line() == null ? "" : ":" + finding.line()) + " -> " + finding.tagPath() + " | " + finding.message() + (finding.suggestion() == null ? "" : " | " + finding.suggestion()));
		}
	}
}
