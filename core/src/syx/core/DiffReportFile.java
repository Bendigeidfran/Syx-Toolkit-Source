package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A diff's report as Markdown, to read or pass on. It's grouped by what changed in the game, because one
 * change is fixed once however many places use it. It holds names and line numbers only, with no game code
 * and no paths from this computer, and no timestamp, so the same inputs always give the same text.
 */
public final class DiffReportFile {

	public static final String FOLDER_NAME = "Diff Reports";
	private static final int MAX_TRIES = 1000;
	private static final Pattern PLACES = Pattern.compile(" \\((\\d+) places\\)$");
	private static final Pattern NOT_IN_A_FILE_NAME = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");

	/** @param created false if an identical report was already there, so nothing was written */
	public record Saved(Path file, boolean created, String text) {
	}

	private DiffReportFile() {
	}

	/** Says why this report can't be saved, or null if it can. Every front end asks here, so they say the same. */
	public static String problemWith(ModCodeDiff.Report report) {
		return report.hasCode() ? null : report.summary();
	}

	public static String markdown(ModCodeDiff.Report report, String modName) {
		StringBuilder out = new StringBuilder();
		out.append("# ").append(plain(modName)).append(": ").append(report.oldVersion()).append(" to ").append(report.newVersion()).append("\n\n");
		out.append(plain(report.summary())).append("\n\n");
		out.append("- Old: ").append(report.oldVersion()).append(" (").append(report.oldSource()).append("), ").append(code(report.oldId())).append('\n');
		out.append("- New: ").append(report.newVersion()).append(" (").append(report.newSource()).append("), ").append(code(report.newId())).append('\n');
		out.append("- Scanned: ").append(String.join(", ", report.scanned().stream().map(DiffReportFile::code).toList())).append("\n\n");
		if (report.findings().isEmpty()) {
			out.append("Nothing in this update was found to affect this mod.\n\n");
		}
		section(out, report, Diagnostic.Severity.ERROR, "Breaks: these stop the game loading, or fail when it reaches this code");
		section(out, report, Diagnostic.Severity.WARNING, "Silent: nothing here fails by itself, but your mod may not work as intended");
		section(out, report, Diagnostic.Severity.REVIEW, "To review: worth a look, and not shown to be a problem");
		section(out, report, Diagnostic.Severity.TOOL_FAILURE, "Not checked: the toolkit couldn't read these");
		out.append("## What this report can't tell you\n\n");
		out.append(plain(report.bodyNote())).append("\n\n");
		out.append(plain(ModCodeDiff.SCOPE_NOTE)).append('\n');
		return out.toString();
	}

	private static void section(StringBuilder out, ModCodeDiff.Report report, Diagnostic.Severity severity, String heading) {
		Map<String, List<Diagnostic>> groups = new LinkedHashMap<>();
		for (Diagnostic finding : report.findings()) {
			if (finding.severity() == severity) {
				groups.computeIfAbsent(finding.code() + "|" + finding.tagPath(), key -> new ArrayList<>()).add(finding);
			}
		}
		if (groups.isEmpty()) {
			return;
		}
		out.append("## ").append(heading).append("\n\n");
		for (List<Diagnostic> group : groups.values()) {
			group(out, group);
		}
	}

	private static void group(StringBuilder out, List<Diagnostic> group) {
		String reference = group.get(0).tagPath();
		out.append("### ").append(reference == null ? "In the mod" : code(reference)).append("\n\n");
		Set<String> messages = new LinkedHashSet<>();
		Set<String> suggestions = new LinkedHashSet<>();
		for (Diagnostic finding : group) {
			messages.add(withoutCount(finding.message()));
			if (finding.suggestion() != null) {
				suggestions.add(finding.suggestion());
			}
		}
		boolean oneMessage = messages.size() == 1;
		if (oneMessage) {
			out.append(plain(messages.iterator().next())).append("\n\n");
		}
		suggestions.forEach(suggestion -> out.append(plain(suggestion)).append("\n\n"));
		// A constant can't be traced to a place in the mod, so it has none to list.
		if (group.get(0).code().equals(ModCodeDiff.CONSTANT_CHANGED)) {
			return;
		}
		for (Diagnostic finding : group) {
			out.append("- ").append(code(finding.file()));
			if (finding.line() != null) {
				out.append(", line ").append(finding.line());
			}
			Matcher count = PLACES.matcher(finding.message());
			if (count.find()) {
				out.append(" (").append(count.group(1)).append(" places)");
			}
			if (!oneMessage) {
				out.append(": ").append(plain(withoutCount(finding.message())));
			}
			out.append('\n');
		}
		out.append('\n');
	}

	private static String withoutCount(String message) {
		return PLACES.matcher(message).replaceFirst("");
	}

	/** Names come from a mod's jar, so nothing in one may change how the page reads. */
	private static String plain(String text) {
		return oneLine(text).replace("`", "'").replace("<", "\\<").replace("*", "\\*");
	}

	private static String code(String text) {
		return "`" + oneLine(text).replace('`', '\'') + "`";
	}

	private static String oneLine(String text) {
		return text.replaceAll("\\s*[\\r\\n]+\\s*", " ");
	}

	/** What a report for this mod and these versions is called. Two builds of one version are told apart by their ids. */
	public static String fileName(ModCodeDiff.Report report, String modFolder) {
		boolean sameVersion = report.oldVersion().equals(report.newVersion());
		String from = sameVersion ? report.oldId() : report.oldVersion();
		String to = sameVersion ? report.newId() : report.newVersion();
		String mod = NOT_IN_A_FILE_NAME.matcher(modFolder).replaceAll("_").replaceAll("[. ]+$", "");
		return (mod.isEmpty() ? "Mod" : mod) + " - " + from + " to " + to + ".md";
	}

	/**
	 * Puts the report in the folder. Nothing is overwritten: the same report already there writes nothing, and a
	 * different one under that name gets a number, because the mod may have changed since.
	 */
	public static Saved save(Path folder, ModCodeDiff.Report report, String modFolder, String modName) throws IOException {
		String text = markdown(report, modName);
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		Files.createDirectories(folder);
		String name = fileName(report, modFolder);
		String stem = name.substring(0, name.length() - ".md".length());
		for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
			Path target = folder.resolve(attempt == 1 ? name : stem + " (" + attempt + ").md");
			if (Files.exists(target)) {
				if (Arrays.equals(Files.readAllBytes(target), bytes)) {
					return new Saved(target, false, text);
				}
				continue;
			}
			Path temp = target.resolveSibling(target.getFileName() + ".syxtmp");
			try {
				Files.write(temp, bytes);
				SafeWrite.commit(List.of(new SafeWrite.Change(target, temp, null)));
			} finally {
				Files.deleteIfExists(temp);
			}
			return new Saved(target, true, text);
		}
		throw new IOException("There are too many reports called " + name + " in " + folder + ".");
	}
}
