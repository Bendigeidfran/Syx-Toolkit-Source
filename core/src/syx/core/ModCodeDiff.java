package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a game update means for one code mod. It compares the mod's compiled classes with the manifests of
 * two game versions, and shows no code. References follow the JVM's rules through superclasses and
 * interfaces, because a compiled reference often names a class that only inherits the member. A reference
 * that reaches a class nothing can read is never called broken: a false alarm is worse than a missed problem.
 */
public final class ModCodeDiff {

	public static final String CLASS_REMOVED = "CLASS_REMOVED";
	public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
	public static final String ACCESS_LOST = "ACCESS_LOST";
	public static final String MEMBER_CHANGED = "MEMBER_CHANGED";
	public static final String ABSTRACT_ADDED = "ABSTRACT_ADDED";
	public static final String FINAL_ADDED = "FINAL_ADDED";
	public static final String OVERRIDE_ORPHANED = "OVERRIDE_ORPHANED";
	public static final String CLASS_REPLACED_CHANGED = "CLASS_REPLACED_CHANGED";
	public static final String CLASS_REPLACED_MISSING_MEMBER = "CLASS_REPLACED_MISSING_MEMBER";
	public static final String CLASS_REPLACED_UNREACHED_MEMBER = "CLASS_REPLACED_UNREACHED_MEMBER";
	public static final String REFLECTION_NAME_GONE = "REFLECTION_NAME_GONE";
	public static final String MEMBER_NOT_IN_EITHER = "MEMBER_NOT_IN_EITHER";
	public static final String STALE_MOD_CLASS = "STALE_MOD_CLASS";
	public static final String BODY_CHANGED = "BODY_CHANGED";
	public static final String CONSTANT_CHANGED = "CONSTANT_CHANGED";
	public static final String MOD_CLASS_UNREADABLE = "MOD_CLASS_UNREADABLE";

	/** What a result can't say, which stays on screen and goes to agents with every report. */
	public static final String SCOPE_NOTE = "This compares your mod's compiled jar with the two versions, and shows no game code. It can't see a reflective lookup unless the class and name are written out together, "
			+ "can't trace a changed constant to a mod because compiled mods hold a copy of its value, doesn't check libraries bundled with the game, can't see other mods, and can't see anything a mod patches into the game while it runs. "
			+ "A clean result doesn't prove the mod works: only running it in the game does.";

	private static final String CONFIDENCE_CODE = "code-verified";
	private static final String CONFIDENCE_INFERRED = "inferred";
	private static final String EVIDENCE_LOADING = "ScriptLoad.java:186-193 initialises every class in a script jar, and stops the game with an error if one can't be loaded";
	private static final String EVIDENCE_REACHED = "ErrorHandler.java:75-85 treats NoSuchFieldError and NoSuchMethodError as a code mod's error";
	private static final String EVIDENCE_CLASSPATH = "Main.java:78-100 and Proccesser.java:55-59 put mod jars before the game's on the classpath, so a same-named class replaces the game's";
	private static final int MAX_LISTED = 8;
	private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
	private static final Pattern VERSION_FOLDER = Pattern.compile("V(\\d+)");
	private static final Pattern NUMBERED_CLASS = Pattern.compile(".*\\$\\d+.*");
	private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([^;]+);");

	/** Thrown when two versions can't sensibly be compared. */
	public static final class DiffRefused extends Exception {
		private static final long serialVersionUID = 1L;

		DiffRefused(String message) {
			super(message);
		}
	}

	/**
	 * What came of a diff. Every front end shows this, so they can't differ.
	 *
	 * @param scanned the mod's jars that were read, relative to the mod
	 * @param references how many uses of game code were looked at
	 * @param uncheckable how many reached a class nothing could read, so can't be called fine or broken
	 * @param bodiesCompared whether changed method code could be compared at all
	 * @param bodyNote why, or what that comparison can't promise
	 */
	public record Report(String oldId, String oldVersion, String oldSource, String newId, String newVersion, String newSource, List<String> scanned, int modClasses, int references, int uncheckable,
			boolean bodiesCompared, String bodyNote, List<Diagnostic> findings) {

		public long count(Diagnostic.Severity severity) {
			return findings.stream().filter(finding -> finding.severity() == severity).count();
		}

		public boolean hasCode() {
			return !scanned.isEmpty();
		}

		/** How many game changes, because one is fixed once, and how many findings if that's different. */
		private String counted(Diagnostic.Severity severity, String one, String many) {
			long total = count(severity);
			long changes = findings.stream().filter(finding -> finding.severity() == severity).map(finding -> finding.tagPath() == null ? finding.file() : finding.tagPath()).distinct().count();
			return changes + " " + (changes == 1 ? one : many) + (total == changes ? "" : " (" + total + " findings)");
		}

		/** One or two lines saying what was found, the same everywhere. */
		public String summary() {
			if (!hasCode()) {
				return "This mod has no code to check. The diff only reads jars in a version folder's script folder.";
			}
			String counts = counted(Diagnostic.Severity.ERROR, "break", "breaks") + ", " + counted(Diagnostic.Severity.WARNING, "silent problem", "silent problems") + " and " + counted(Diagnostic.Severity.REVIEW, "to review", "to review") + ", going from " + oldVersion + " to " + newVersion + ". ";
			String checked = references + " uses of game code checked in " + modClasses + (modClasses == 1 ? " class" : " classes") + ".";
			String unknown = uncheckable == 0 ? "" : " " + uncheckable + " couldn't be checked, because they reach a class outside the game and the mod's jars.";
			long tool = count(Diagnostic.Severity.TOOL_FAILURE);
			String failed = tool == 0 ? "" : " " + tool + " of the mod's classes couldn't be read, so they weren't checked.";
			return counts + checked + unknown + failed;
		}

		/** The whole result in JSON's terms, for agents and for comparing front ends. */
		public Map<String, Object> toMap() {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("old", describe(oldId, oldVersion, oldSource));
			out.put("new", describe(newId, newVersion, newSource));
			out.put("summary", summary());
			out.put("scope", SCOPE_NOTE);
			out.put("has_code", hasCode());
			out.put("scanned", scanned);
			out.put("classes_checked", modClasses);
			out.put("references_checked", references);
			out.put("could_not_be_checked", uncheckable);
			out.put("body_comparison", bodiesCompared);
			out.put("body_comparison_note", bodyNote);
			List<Object> list = new ArrayList<>();
			for (Diagnostic finding : findings) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("code", finding.code());
				item.put("severity", finding.severity());
				item.put("group", groupOf(finding.severity()));
				item.put("confidence", finding.confidence());
				item.put("mod_location", finding.file());
				item.put("line", finding.line());
				item.put("game_reference", finding.tagPath());
				item.put("message", finding.message());
				item.put("suggestion", finding.suggestion());
				item.put("evidence", finding.evidence());
				list.add(item);
			}
			out.put("findings", list);
			out.put("has_errors", count(Diagnostic.Severity.ERROR) > 0 || count(Diagnostic.Severity.TOOL_FAILURE) > 0);
			return out;
		}

		private static Map<String, Object> describe(String id, String version, String source) {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("id", id);
			out.put("version", version);
			out.put("source", source);
			return out;
		}
	}

	/** Says why these two can't be compared, or null if they can. Every front end asks here, so they refuse alike. */
	public static String problemWith(GameManifest oldManifest, GameManifest newManifest) {
		return comparisonProblem(oldManifest.jarSha256(), oldManifest.version(), oldManifest.major(), oldManifest.minor(), newManifest.jarSha256(), newManifest.version(), newManifest.major(), newManifest.minor());
	}

	static String comparisonProblem(String oldJar, String oldVersion, int oldMajor, int oldMinor, String newJar, String newVersion, int newMajor, int newMinor) {
		if (oldJar.equals(newJar)) {
			return "Those are the same version, so there's nothing to compare. Choose two different versions.";
		}
		if (oldMajor > newMajor || (oldMajor == newMajor && oldMinor > newMinor)) {
			return "The old version, " + oldVersion + ", is newer than the new version, " + newVersion + ". Swap them.";
		}
		return null;
	}

	/** The word a finding is grouped under, the same everywhere. */
	public static String groupOf(Diagnostic.Severity severity) {
		return switch (severity) {
		case ERROR -> "Breaks";
		case WARNING -> "Silent";
		case REVIEW -> "Review";
		case TOOL_FAILURE -> "Tool problem";
		};
	}

	/** Runs the diff, after refusing a pair that makes no sense. */
	public static Report run(Path modRoot, GameManifest oldManifest, String oldSource, GameManifest newManifest, String newSource, BooleanSupplier cancelled) throws DiffRefused, IOException {
		String problem = problemWith(oldManifest, newManifest);
		if (problem != null) {
			throw new DiffRefused(problem);
		}
		return compare(modRoot, oldManifest, oldSource, newManifest, newSource, cancelled);
	}

	/** Runs the diff on any two manifests. Tests use this to compare in both directions. */
	public static Report compare(Path modRoot, GameManifest oldManifest, String oldSource, GameManifest newManifest, String newSource, BooleanSupplier cancelled) throws IOException {
		Set<String> scanned = new LinkedHashSet<>();
		List<Path> jars = jarsToScan(modRoot, oldManifest.major(), newManifest.major());
		jars.forEach(jar -> scanned.add(modRoot.relativize(jar).toString().replace('\\', '/')));
		Run run = new Run(oldManifest, newManifest, cancelled);
		for (Path jar : jars) {
			run.addJar(jar);
		}
		run.check();
		return new Report(oldManifest.id(), oldManifest.version(), oldSource, newManifest.id(), newManifest.version(), newSource, List.copyOf(scanned), run.modClasses.size(), run.references, run.uncheckable,
				run.bodiesCompared, run.bodyNote(), run.sortedFindings());
	}

	/** New's version folder if it has jars, else Old's, else every version folder that has any. */
	static List<Path> jarsToScan(Path modRoot, int oldMajor, int newMajor) throws IOException {
		TreeMap<Integer, List<Path>> byVersion = new TreeMap<>();
		if (!Files.isDirectory(modRoot)) {
			return List.of();
		}
		try (Stream<Path> folders = Files.list(modRoot)) {
			for (Path folder : (Iterable<Path>) folders::iterator) {
				Matcher matcher = VERSION_FOLDER.matcher(folder.getFileName().toString());
				Path script = folder.resolve("script");
				if (matcher.matches() && Files.isDirectory(script)) {
					List<Path> jars;
					try (Stream<Path> files = Files.list(script)) {
						jars = files.filter(file -> Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().toList();
					}
					if (!jars.isEmpty()) {
						byVersion.put(Integer.valueOf(matcher.group(1)), jars);
					}
				}
			}
		}
		if (byVersion.containsKey(newMajor)) {
			return byVersion.get(newMajor);
		}
		if (byVersion.containsKey(oldMajor)) {
			return byVersion.get(oldMajor);
		}
		return byVersion.values().stream().flatMap(List::stream).toList();
	}

	/** A class in either the mod, a manifest or the JDK, seen the same way. */
	private record Node(String name, String superName, List<String> interfaces, int access, Map<String, Integer> fields, Map<String, Integer> methods, boolean fromGame, boolean fromMod) {
		boolean isInterface() {
			return (access & ClassFileModel.ACC_INTERFACE) != 0;
		}
	}

	private enum Status {
		FOUND, MISSING, UNKNOWN
	}

	/**
	 * @param owner where the member is, when found
	 * @param touchedGame whether the search passed through a game class at all
	 */
	private record Resolved(Status status, Node owner, int access, boolean touchedGame) {
	}

	/** The classes the JDK has, read as bytes from our own runtime. A class it doesn't have is unknown, not missing. */
	private static final class JdkClasses {
		private final Map<String, Node> cache = new HashMap<>();
		private final Set<String> absent = new HashSet<>();

		Node find(String name) {
			if (absent.contains(name)) {
				return null;
			}
			Node known = cache.get(name);
			if (known != null) {
				return known;
			}
			if (!(name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/"))) {
				absent.add(name);
				return null;
			}
			try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
				if (in == null) {
					absent.add(name);
					return null;
				}
				Node node = fromModel(ClassFileModel.read(in.readAllBytes()), false, false);
				cache.put(name, node);
				return node;
			} catch (IOException | RuntimeException e) {
				absent.add(name);
				return null;
			}
		}
	}

	private static Node fromModel(ClassFileModel model, boolean fromGame, boolean fromMod) {
		Map<String, Integer> fields = new HashMap<>();
		model.fields.forEach(field -> fields.put(GameManifest.fieldKey(field.name(), field.descriptor()), field.access()));
		Map<String, Integer> methods = new HashMap<>();
		model.methods.forEach(method -> methods.put(GameManifest.methodKey(method.name(), method.descriptor()), method.access()));
		return new Node(model.name, model.superName, model.interfaces, model.access, fields, methods, fromGame, fromMod);
	}

	private static Node fromType(GameManifest.Type type) {
		Map<String, Integer> fields = new HashMap<>();
		type.fields().forEach((key, field) -> fields.put(key, field.access()));
		Map<String, Integer> methods = new HashMap<>();
		type.methods().forEach((key, method) -> methods.put(key, method.access()));
		return new Node(type.name(), type.superName(), type.interfaces(), type.access(), fields, methods, true, false);
	}

	/** One game version as the mod's classes would see it: a class in the mod's jars comes first, as on the game's classpath. */
	private static final class World {
		final GameManifest manifest;
		private final Map<String, Node> mod;
		private final JdkClasses jdk;
		private final Map<String, Node> game = new HashMap<>();

		World(GameManifest manifest, Map<String, Node> mod, JdkClasses jdk) {
			this.manifest = manifest;
			this.mod = mod;
			this.jdk = jdk;
		}

		Node node(String name) {
			Node own = mod.get(name);
			if (own != null) {
				return own;
			}
			Node seen = game.get(name);
			if (seen != null) {
				return seen;
			}
			GameManifest.Type type = manifest.type(name);
			if (type != null) {
				Node node = fromType(type);
				game.put(name, node);
				return node;
			}
			return jdk.find(name);
		}

		/** Looks up the superclasses first, then every interface they have, as the JVM does for methods. */
		Resolved resolve(String owner, String key, boolean field) {
			boolean incomplete = false;
			boolean touchedGame = false;
			List<String> interfacesToSearch = new ArrayList<>();
			Set<String> visited = new HashSet<>();
			String current = owner;
			while (current != null && visited.add(current)) {
				Node node = node(current);
				if (node == null) {
					incomplete = true;
					break;
				}
				touchedGame |= node.fromGame();
				Integer access = (field ? node.fields() : node.methods()).get(key);
				if (access != null) {
					return new Resolved(Status.FOUND, node, access, touchedGame);
				}
				interfacesToSearch.addAll(node.interfaces());
				current = node.superName();
			}
			Deque<String> queue = new ArrayDeque<>(interfacesToSearch);
			while (!queue.isEmpty()) {
				String name = queue.poll();
				if (!visited.add(name)) {
					continue;
				}
				Node node = node(name);
				if (node == null) {
					incomplete = true;
					continue;
				}
				touchedGame |= node.fromGame();
				Integer access = (field ? node.fields() : node.methods()).get(key);
				if (access != null && (field || (access & (ClassFileModel.ACC_PRIVATE | ClassFileModel.ACC_STATIC)) == 0)) {
					return new Resolved(Status.FOUND, node, access, touchedGame);
				}
				queue.addAll(node.interfaces());
				if (node.superName() != null && !node.superName().equals("java/lang/Object")) {
					queue.add(node.superName());
				}
			}
			return new Resolved(incomplete ? Status.UNKNOWN : Status.MISSING, null, 0, touchedGame);
		}

		/** Every class and interface above this one, and whether any couldn't be read. */
		Ancestors ancestors(Node start) {
			List<Node> found = new ArrayList<>();
			boolean incomplete = false;
			Set<String> visited = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>();
			if (start.superName() != null) {
				queue.add(start.superName());
			}
			queue.addAll(start.interfaces());
			while (!queue.isEmpty()) {
				String name = queue.poll();
				if (!visited.add(name)) {
					continue;
				}
				Node node = node(name);
				if (node == null) {
					incomplete = true;
					continue;
				}
				found.add(node);
				if (node.superName() != null) {
					queue.add(node.superName());
				}
				queue.addAll(node.interfaces());
			}
			return new Ancestors(found, incomplete);
		}

		boolean isSubclass(String child, String parent) {
			String current = child;
			Set<String> visited = new HashSet<>();
			while (current != null && visited.add(current)) {
				if (current.equals(parent)) {
					return true;
				}
				Node node = node(current);
				current = node == null ? null : node.superName();
			}
			return false;
		}
	}

	private record Ancestors(List<Node> nodes, boolean incomplete) {
	}

	/**
	 * @param unused game classes nothing can reach with the mod installed
	 * @param namedBy for each class, the game classes that name it
	 */
	private record Reach(Set<String> unused, Map<String, Set<String>> namedBy) {
	}

	/** A finding before it's made, which counts how many places in one mod class say the same. */
	private static final class Pending {
		final String code;
		final Diagnostic.Severity severity;
		final String confidence;
		final String location;
		final int line;
		final String reference;
		final String message;
		final String suggestion;
		final List<String> evidence;
		int count = 1;

		Pending(String code, Diagnostic.Severity severity, String confidence, String location, int line, String reference, String message, String suggestion, List<String> evidence) {
			this.code = code;
			this.severity = severity;
			this.confidence = confidence;
			this.location = location;
			this.line = line;
			this.reference = reference;
			this.message = message;
			this.suggestion = suggestion;
			this.evidence = evidence;
		}
	}

	private static final class Run {
		final GameManifest oldManifest;
		final GameManifest newManifest;
		final BooleanSupplier cancelled;
		final Map<String, ClassFileModel> modClasses = new TreeMap<>();
		final Map<String, String> jarOf = new HashMap<>();
		final Map<String, Node> modNodes = new HashMap<>();
		final Map<String, Pending> pending = new LinkedHashMap<>();
		final JdkClasses jdk = new JdkClasses();
		final boolean bodiesCompared;
		final World oldWorld;
		final World newWorld;
		final Set<String> referencedGameClasses = new TreeSet<>();
		/** The mod's numbered classes nothing can create, such as class files a build left behind. */
		Set<String> orphans = Set.of();
		int references;
		int uncheckable;

		Run(GameManifest oldManifest, GameManifest newManifest, BooleanSupplier cancelled) {
			this.oldManifest = oldManifest;
			this.newManifest = newManifest;
			this.cancelled = cancelled;
			this.bodiesCompared = oldManifest.hashMethod() == newManifest.hashMethod() && oldManifest.dominantClassVersion() == newManifest.dominantClassVersion();
			this.oldWorld = new World(oldManifest, modNodes, jdk);
			this.newWorld = new World(newManifest, modNodes, jdk);
		}

		String bodyNote() {
			if (oldManifest.hashMethod() != newManifest.hashMethod()) {
				return "Changed method code isn't compared, because these manifests hash code differently.";
			}
			if (!bodiesCompared) {
				return "Changed method code isn't compared, because the game was compiled differently for " + oldManifest.version() + " (class file version " + oldManifest.dominantClassVersion() + ") and "
						+ newManifest.version() + " (class file version " + newManifest.dominantClassVersion() + "), so nearly every method would look changed.";
			}
			return "Changed method code is compared, and a difference only shows the compiled code differs, not that behaviour changed. From 0.71.44 to 0.71.84, about 0.5% of methods looked changed in classes whose source was identical, so a few of these may be nothing.";
		}

		void addJar(Path jar) throws IOException {
			String jarName = jar.getFileName().toString();
			try (ZipFile zip = new ZipFile(jar.toFile())) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) {
						continue;
					}
					checkCancelled();
					try (InputStream in = zip.getInputStream(entry)) {
						byte[] bytes = in.readNBytes(MAX_CLASS_BYTES + 1);
						if (bytes.length > MAX_CLASS_BYTES) {
							throw new IOException("it's too big to be a class");
						}
						ClassFileModel model = ClassFileModel.read(bytes);
						// The first jar on the classpath wins, as it does in the game.
						if (modClasses.putIfAbsent(model.name, model) == null) {
							jarOf.put(model.name, jarName);
							modNodes.put(model.name, fromModel(model, false, true));
						}
					} catch (IOException | RuntimeException e) {
						add(new Pending(MOD_CLASS_UNREADABLE, Diagnostic.Severity.TOOL_FAILURE, null, jarName + ": " + entry.getName(), -1, null, "This class in the mod couldn't be read, so it wasn't checked: " + e.getMessage(), null, List.of()));
					}
				}
			}
		}

		private void checkCancelled() {
			if (cancelled.getAsBoolean()) {
				throw new CancellationException();
			}
		}

		void check() {
			orphans = orphanedClasses();
			for (ClassFileModel model : modClasses.values()) {
				checkCancelled();
				checkHierarchy(model);
				checkDeclaredTypes(model);
				checkMethods(model);
				checkOverrides(model);
				checkAbstract(model);
				checkReplaced(model);
			}
			checkGameUses();
			checkConstants();
		}

		/**
		 * The game's own code calling what a mod's copy of a class doesn't have. Compiled references prove this,
		 * where a member merely being absent proves nothing: it may be used by nothing, or only by other classes
		 * the mod replaces too. A member the copy inherits is found, as the JVM would find it.
		 */
		private void checkGameUses() {
			Set<String> replaced = new TreeSet<>(modClasses.keySet());
			replaced.retainAll(newManifest.types().keySet());
			if (replaced.isEmpty()) {
				return;
			}
			Map<String, Boolean> reachesMod = new HashMap<>();
			Map<String, TreeSet<String>> users = new TreeMap<>();
			for (GameManifest.Type type : newManifest.types().values()) {
				if (replaced.contains(type.name())) {
					continue;
				}
				checkCancelled();
				type.uses().forEach((owner, members) -> {
					if (modClassAbove(owner, reachesMod)) {
						for (String member : members) {
							// A manifest leaves out compiler helpers, so one is only known to be missing from the mod's own copy.
							if (!modClasses.containsKey(owner) && ManifestBuilder.isCompilerHelper(member, ClassFileModel.ACC_SYNTHETIC)) {
								continue;
							}
							if (newWorld.resolve(owner, member, member.indexOf('(') < 0).status() == Status.MISSING) {
								users.computeIfAbsent(owner + "\n" + member, key -> new TreeSet<>()).add(type.name());
							}
						}
					}
				});
			}
			if (users.isEmpty()) {
				return;
			}
			Reach reach = classesOnlyReplacedOnesUse(replaced);
			users.forEach((link, from) -> {
				String owner = link.substring(0, link.indexOf('\n'));
				String member = link.substring(link.indexOf('\n') + 1);
				boolean field = member.indexOf('(') < 0;
				String shown = dotted(owner) + "." + (field ? prettyField(member) : prettyMethod(member));
				String copy = firstModClass(owner);
				List<String> live = from.stream().filter(user -> !reach.unused().contains(user) && !alreadyMissing(user, owner, member, field)).toList();
				if (!live.isEmpty()) {
					add(new Pending(CLASS_REPLACED_MISSING_MEMBER, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(modClasses.get(copy), null), -1, shown,
							"The game's own code uses this in " + newManifest.version() + ", and your copy of " + dotted(copy) + " doesn't have it. This fails when the game reaches that code, however your mod is written.",
							"Used by: " + limited(live.stream().map(ModCodeDiff::dotted).toList()), List.of(EVIDENCE_CLASSPATH, EVIDENCE_REACHED)));
					return;
				}
				Set<String> through = new TreeSet<>();
				boolean onlyCutOff = true;
				for (String user : from) {
					if (reach.unused().contains(user)) {
						through.addAll(replacedNamers(user, reach, replaced));
					} else {
						onlyCutOff = false;
					}
				}
				String message;
				if (onlyCutOff && !through.isEmpty()) {
					message = "Your copy of " + dotted(copy) + " doesn't have this. The game code using it is only reached through game classes your mod replaces (" + limited(through.stream().map(ModCodeDiff::dotted).toList())
							+ "), so it probably isn't reached now. If you bring those copies up to date with " + newManifest.version() + ", they'll likely need it.";
				} else {
					message = "Your copy of " + dotted(copy) + " doesn't have this. The game classes using it are probably never reached with your mod, because only classes you replace use them, or your mod already worked without it.";
				}
				add(new Pending(CLASS_REPLACED_UNREACHED_MEMBER, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(modClasses.get(copy), null), -1, shown, message,
						"Used by: " + limited(from.stream().map(ModCodeDiff::dotted).toList()), List.of()));
			});
		}

		/** The replaced classes that name this one, directly or through other classes nothing else can reach. */
		private static Set<String> replacedNamers(String user, Reach reach, Set<String> replaced) {
			Set<String> found = new TreeSet<>();
			Set<String> visited = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>(List.of(user));
			while (!queue.isEmpty()) {
				String name = queue.poll();
				if (!visited.add(name)) {
					continue;
				}
				for (String namer : reach.namedBy().getOrDefault(name, Set.of())) {
					if (replaced.contains(namer)) {
						found.add(namer);
					} else if (reach.unused().contains(namer)) {
						queue.add(namer);
					}
				}
			}
			return found;
		}

		private boolean modClassAbove(String start, Map<String, Boolean> known) {
			Boolean seen = known.get(start);
			if (seen != null) {
				return seen;
			}
			boolean found = firstModClass(start) != null;
			known.put(start, found);
			return found;
		}

		/** The class itself or the nearest above it that's in the mod's jars, or null. */
		private String firstModClass(String start) {
			Set<String> visited = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				String name = queue.poll();
				if (!visited.add(name)) {
					continue;
				}
				Node node = newWorld.node(name);
				if (node == null) {
					continue;
				}
				if (node.fromMod()) {
					return name;
				}
				if (node.superName() != null) {
					queue.add(node.superName());
				}
				queue.addAll(node.interfaces());
			}
			return null;
		}

		/** The mod got by without this in the old version too, so this update didn't change anything for it. */
		private boolean alreadyMissing(String user, String owner, String member, boolean field) {
			GameManifest.Type before = oldManifest.type(user);
			List<String> members = before == null ? null : before.uses().get(owner);
			return members != null && members.contains(member) && oldWorld.resolve(owner, member, field).status() == Status.MISSING;
		}

		/**
		 * Game classes that nothing can reach with this mod installed: everything that names them is a class it
		 * replaces, or another such class, and the mod's own classes don't name them. A class nothing names may be
		 * an entry point or loaded by name, so it counts as reached, and so does a loop of classes naming each other.
		 */
		private Reach classesOnlyReplacedOnesUse(Set<String> replaced) {
			Map<String, Set<String>> namedBy = gameNamers(newManifest);
			Set<String> namedByMod = new HashSet<>();
			for (ClassFileModel model : modClasses.values()) {
				namedByMod.addAll(namesIn(model));
			}
			Set<String> unused = new HashSet<>();
			boolean changed = true;
			while (changed) {
				changed = false;
				for (Map.Entry<String, Set<String>> entry : namedBy.entrySet()) {
					String name = entry.getKey();
					if (replaced.contains(name) || namedByMod.contains(name) || unused.contains(name)) {
						continue;
					}
					if (entry.getValue().stream().allMatch(user -> user.equals(name) || replaced.contains(user) || unused.contains(user))) {
						unused.add(name);
						changed = true;
					}
				}
			}
			return new Reach(unused, namedBy);
		}

		/** For each class, the game classes that name it in their code or as a parent. */
		private static Map<String, Set<String>> gameNamers(GameManifest manifest) {
			Map<String, Set<String>> namedBy = new HashMap<>();
			for (GameManifest.Type type : manifest.types().values()) {
				type.uses().keySet().forEach(owner -> namedBy.computeIfAbsent(owner, key -> new HashSet<>()).add(type.name()));
				List<String> parents = new ArrayList<>(type.interfaces());
				if (type.superName() != null) {
					parents.add(type.superName());
				}
				parents.forEach(parent -> namedBy.computeIfAbsent(parent, key -> new HashSet<>()).add(type.name()));
			}
			return namedBy;
		}

		/** Every class a mod class names in its code or as a parent. */
		private static Set<String> namesIn(ClassFileModel model) {
			Set<String> names = new HashSet<>(model.interfaces);
			if (model.superName != null) {
				names.add(model.superName);
			}
			for (ClassFileModel.MethodDef method : model.methods) {
				method.uses().forEach(use -> names.add(use.owner()));
				method.classUses().forEach(use -> names.add(use.name()));
			}
			return names;
		}

		/**
		 * The mod's numbered classes that nothing can create: no other mod class names them, except ones like
		 * them, and no game class it doesn't replace does either. An anonymous class is only made where it's
		 * written, so one nothing names is usually a class file an earlier build left in the jar.
		 */
		private Set<String> orphanedClasses() {
			Map<String, Set<String>> namers = new HashMap<>();
			for (ClassFileModel model : modClasses.values()) {
				for (String name : namesIn(model)) {
					if (!name.equals(model.name) && modClasses.containsKey(name)) {
						namers.computeIfAbsent(name, key -> new HashSet<>()).add(model.name);
					}
				}
			}
			// A game class the mod doesn't replace could make one, in either version.
			Set<String> gameNamed = new HashSet<>();
			for (GameManifest manifest : List.of(oldManifest, newManifest)) {
				gameNamers(manifest).forEach((name, users) -> {
					if (modClasses.containsKey(name) && users.stream().anyMatch(user -> !modClasses.containsKey(user))) {
						gameNamed.add(name);
					}
				});
			}
			Set<String> found = new HashSet<>();
			boolean changed = true;
			while (changed) {
				changed = false;
				for (String name : modClasses.keySet()) {
					if (found.contains(name) || gameNamed.contains(name) || !NUMBERED_CLASS.matcher(name).matches()) {
						continue;
					}
					if (namers.getOrDefault(name, Set.of()).stream().allMatch(found::contains)) {
						found.add(name);
						changed = true;
					}
				}
			}
			return found;
		}

		/** The class that's probably this one, if the game moved it to another package. */
		private String movedTo(String name) {
			String simple = name.substring(name.lastIndexOf('/') + 1);
			List<String> found = newManifest.types().keySet().stream().filter(other -> other.endsWith("/" + simple) && oldManifest.type(other) == null).map(ModCodeDiff::dotted).toList();
			return found.isEmpty() ? null : "A class of that name is now at: " + limited(found);
		}

		private boolean inGame(String name) {
			return oldManifest.type(name) != null || newManifest.type(name) != null;
		}

		private boolean isGone(String name) {
			return !modClasses.containsKey(name) && oldManifest.type(name) != null && newManifest.type(name) == null;
		}

		private String where(ClassFileModel model, String method) {
			String member = method == null ? "" : method.equals("<init>") ? ".constructor" : method.equals("<clinit>") ? ".static setup" : "." + method;
			return dotted(model.name) + member + " (" + jarOf.get(model.name) + ")";
		}

		private void add(Pending finding) {
			String key = finding.code + "|" + finding.location + "|" + finding.reference + "|" + finding.message;
			Pending existing = pending.get(key);
			if (existing == null) {
				pending.put(key, finding);
			} else {
				existing.count++;
			}
		}

		private void checkHierarchy(ClassFileModel model) {
			List<String> parents = new ArrayList<>(model.interfaces);
			if (model.superName != null) {
				parents.add(model.superName);
			}
			for (String parent : parents) {
				if (modClasses.containsKey(parent)) {
					continue;
				}
				if (inGame(parent)) {
					referencedGameClasses.add(parent);
				}
				if (isGone(parent)) {
					add(new Pending(CLASS_REMOVED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, null), -1, dotted(parent),
							"Your class is built on " + dotted(parent) + ", which isn't in " + newManifest.version() + ". The game stops with an error while loading your mod.", movedTo(parent), List.of(EVIDENCE_LOADING)));
				}
			}
			if (model.superName != null && !modClasses.containsKey(model.superName)) {
				GameManifest.Type oldType = oldManifest.type(model.superName);
				GameManifest.Type newType = newManifest.type(model.superName);
				if (oldType != null && newType != null && (oldType.access() & ClassFileModel.ACC_FINAL) == 0 && (newType.access() & ClassFileModel.ACC_FINAL) != 0) {
					add(new Pending(FINAL_ADDED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, null), -1, dotted(model.superName),
							"Your class extends " + dotted(model.superName) + ", which is final in " + newManifest.version() + ". The game stops with an error while loading your mod.", null, List.of(EVIDENCE_LOADING)));
				}
			}
		}

		private void checkDeclaredTypes(ClassFileModel model) {
			Map<String, String> mentioned = new LinkedHashMap<>();
			for (ClassFileModel.FieldDef field : model.fields) {
				if ((field.access() & ClassFileModel.ACC_SYNTHETIC) == 0) {
					classesIn(field.descriptor()).forEach(name -> mentioned.putIfAbsent(name, field.name()));
				}
			}
			for (ClassFileModel.MethodDef method : model.methods) {
				if ((method.access() & ClassFileModel.ACC_SYNTHETIC) == 0) {
					classesIn(method.descriptor()).forEach(name -> mentioned.putIfAbsent(name, method.name()));
				}
			}
			mentioned.forEach((name, member) -> {
				if (isGone(name)) {
					add(new Pending(CLASS_REMOVED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, member), -1, dotted(name),
							member + " names " + dotted(name) + " in its type, and that class isn't in " + newManifest.version() + ". This won't compile, and it fails if the game reaches it.", movedTo(name), List.of(EVIDENCE_REACHED)));
				}
			});
		}

		private void checkMethods(ClassFileModel model) {
			for (ClassFileModel.MethodDef method : model.methods) {
				for (ClassFileModel.ClassUse use : method.classUses()) {
					if (isGone(use.name())) {
						references++;
						classGone(model, method, use.name(), use.line());
					} else if (!modClasses.containsKey(use.name()) && inGame(use.name())) {
						references++;
						referencedGameClasses.add(use.name());
					}
				}
				for (ClassFileModel.Use use : method.uses()) {
					checkUse(model, method, use);
				}
				for (ClassFileModel.ReflectiveLookup lookup : method.lookups()) {
					checkLookup(model, method, lookup);
				}
			}
		}

		private void classGone(ClassFileModel model, ClassFileModel.MethodDef method, String name, int line) {
			add(new Pending(CLASS_REMOVED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, method.name()), line, dotted(name),
					"Your code uses " + dotted(name) + ", which isn't in " + newManifest.version() + ". This fails if the game reaches this code.", movedTo(name), List.of(EVIDENCE_REACHED)));
		}

		private void checkUse(ClassFileModel model, ClassFileModel.MethodDef method, ClassFileModel.Use use) {
			String owner = use.owner();
			if (owner.startsWith("[")) {
				return;
			}
			boolean ownClass = modClasses.containsKey(owner);
			if (!ownClass && !inGame(owner)) {
				return;
			}
			boolean field = use.op() <= ClassFileModel.OP_PUTFIELD;
			String key = field ? GameManifest.fieldKey(use.name(), use.descriptor()) : GameManifest.methodKey(use.name(), use.descriptor());
			if (isGone(owner)) {
				references++;
				classGone(model, method, owner, use.line());
				return;
			}
			Resolved now = newWorld.resolve(owner, key, field);
			if (ownClass && !now.touchedGame() && now.status() != Status.UNKNOWN) {
				// Only the mod's own classes, which an update can't change.
				return;
			}
			references++;
			if (!ownClass) {
				referencedGameClasses.add(owner);
			}
			Resolved before = oldWorld.resolve(owner, key, field);
			switch (now.status()) {
			case UNKNOWN -> uncheckable++;
			case MISSING -> memberGone(model, method, use, field, before);
			case FOUND -> {
				if (now.owner().fromGame()) {
					referencedGameClasses.add(now.owner().name());
				}
				checkStillUsable(model, method, use, field, now, before);
				if (!field) {
					reviewBody(model, method.name(), use.line(), use.name(), use.descriptor(), key, now, before);
				}
			}
			}
		}

		private void memberGone(ClassFileModel model, ClassFileModel.MethodDef method, ClassFileModel.Use use, boolean field, Resolved before) {
			String shown = pretty(use.owner(), use.name(), use.descriptor(), field);
			if (before.status() == Status.FOUND) {
				String now = sameNamed(use.owner(), use.name(), field);
				String hint = rebuildHint(model.name, use, field);
				String suggestion = now == null ? hint : hint == null ? now : now + "\n" + hint;
				add(new Pending(MEMBER_REMOVED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, method.name()), use.line(), shown,
						"Your code uses this, and it isn't in " + newManifest.version() + ". This fails if the game reaches this code.", suggestion, List.of(EVIDENCE_REACHED)));
			} else if (before.status() == Status.MISSING && modClasses.containsKey(use.owner())) {
				// The mod's own class lacks it, so the game's versions don't come into it.
				if (orphans.contains(model.name)) {
					add(new Pending(STALE_MOD_CLASS, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(model, method.name()), use.line(), shown,
							"Nothing in your mod uses " + dotted(model.name) + ", and it calls this, which your copy of " + dotted(use.owner())
									+ " doesn't have. It looks like a leftover from an earlier build. A clean rebuild of the mod should remove it.",
							null, List.of()));
				} else {
					add(new Pending(MEMBER_NOT_IN_EITHER, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(model, method.name()), use.line(), shown,
							"Your copy of " + dotted(use.owner()) + " doesn't have this, and nothing it's built on does. That was already so with " + oldManifest.version()
									+ ", so this update didn't cause it, but it fails if the game reaches this code, unless your mod adds it while the game runs.",
							null, List.of()));
				}
			} else if (before.status() == Status.MISSING) {
				// This update didn't remove it. A mod can add members to game classes while the game runs, which nothing here can see.
				add(new Pending(MEMBER_NOT_IN_EITHER, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(model, method.name()), use.line(), shown,
						"Your code uses this, and neither " + oldManifest.version() + " nor " + newManifest.version() + " has it. That's fine if your mod adds it while the game runs. Otherwise the mod was built for another version, and this fails if the game reaches this code.",
						sameNamed(use.owner(), use.name(), field), List.of()));
			} else {
				uncheckable++;
			}
		}

		/** Members of the same name that exist now, which is often all it takes to see what happened. */
		private String sameNamed(String owner, String name, boolean field) {
			Set<String> found = new TreeSet<>();
			Set<String> visited = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>(List.of(owner));
			while (!queue.isEmpty()) {
				String current = queue.poll();
				if (!visited.add(current)) {
					continue;
				}
				Node node = newWorld.node(current);
				if (node == null || !node.fromGame()) {
					continue;
				}
				if (field) {
					node.fields().keySet().stream().filter(key -> key.startsWith(name + ":")).forEach(key -> found.add(prettyField(key)));
				} else {
					node.methods().keySet().stream().filter(key -> key.startsWith(name + "(")).forEach(key -> found.add(prettyMethod(key)));
				}
				if (node.superName() != null) {
					queue.add(node.superName());
				}
				queue.addAll(node.interfaces());
			}
			return found.isEmpty() ? null : "Now in the game: " + limited(List.copyOf(found));
		}

		/**
		 * Says a rebuild should be all this needs, when the game still has a member of the same name and kind that
		 * source written for the old one compiles against: parameters that each take what the old ones did, and a
		 * field or result of the same type or one below it. A type that merely changed isn't enough, because source
		 * that names the old type, or uses what only it had, won't compile: the Heroes of Syx port needed edits for
		 * most of those, and the hierarchy can't tell which.
		 */
		private String rebuildHint(String from, ClassFileModel.Use use, boolean field) {
			int op = use.op();
			boolean wantStatic = op == ClassFileModel.OP_GETSTATIC || op == ClassFileModel.OP_PUTSTATIC || op == ClassFileModel.OP_INVOKESTATIC;
			List<String> oldParameters = field ? List.of() : parameterTypes(use.descriptor());
			boolean constructor = use.name().equals("<init>");
			Set<String> visited = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>(List.of(use.owner()));
			while (!queue.isEmpty()) {
				String current = queue.poll();
				if (!visited.add(current)) {
					continue;
				}
				Node node = newWorld.node(current);
				if (node == null) {
					continue;
				}
				if (node.fromGame()) {
					Map<String, Integer> members = field ? node.fields() : node.methods();
					String prefix = use.name() + (field ? ":" : "(");
					for (Map.Entry<String, Integer> member : new TreeMap<>(members).entrySet()) {
						int access = member.getValue();
						if (!member.getKey().startsWith(prefix) || ((access & ClassFileModel.ACC_STATIC) != 0) != wantStatic || (access & ClassFileModel.ACC_SYNTHETIC) != 0
								|| !usable(from, new Resolved(Status.FOUND, node, access, true), newWorld)) {
							continue;
						}
						String descriptor = member.getKey().substring(member.getKey().indexOf(field ? ':' : '(') + (field ? 1 : 0));
						boolean fits = field ? valueFits(use.descriptor(), descriptor)
								: parametersFit(oldParameters, parameterTypes(descriptor)) && valueFits(use.descriptor().substring(use.descriptor().indexOf(')') + 1), descriptor.substring(descriptor.indexOf(')') + 1));
						if (!fits) {
							continue;
						}
						String shown = dotted(node.name()) + "." + (field ? prettyField(member.getKey()) : prettyMethod(member.getKey()));
						return "Source that used the old one still compiles against " + shown + ", so rebuilding your mod against " + newManifest.version() + " should fix this with no change to its source.";
					}
				}
				// A constructor belongs to its own class only.
				if (constructor) {
					break;
				}
				if (node.superName() != null) {
					queue.add(node.superName());
				}
				queue.addAll(node.interfaces());
			}
			return null;
		}

		/** Whether each old argument still fits: the same type, or for a class, one the new parameter's type is above. */
		private boolean parametersFit(List<String> oldParameters, List<String> newParameters) {
			if (oldParameters.size() != newParameters.size()) {
				return false;
			}
			for (int i = 0; i < oldParameters.size(); i++) {
				String before = oldParameters.get(i);
				String after = newParameters.get(i);
				if (!before.equals(after) && !isBelow(before, after)) {
					return false;
				}
			}
			return true;
		}

		/** Whether a value of the new type still goes where the old one did: the same type, one below it, or anything where nothing was given back. */
		private boolean valueFits(String before, String after) {
			return before.equals(after) || before.equals("V") || isBelow(after, before);
		}

		/** Whether one class type is below another in the new game. Arrays and primitives must match exactly instead. */
		private boolean isBelow(String lower, String upper) {
			if (!lower.startsWith("L") || !upper.startsWith("L")) {
				return false;
			}
			String upperName = upper.substring(1, upper.length() - 1);
			Node node = newWorld.node(lower.substring(1, lower.length() - 1));
			if (node == null) {
				return false;
			}
			Ancestors ancestors = newWorld.ancestors(node);
			return !ancestors.incomplete() && ancestors.nodes().stream().anyMatch(above -> above.name().equals(upperName));
		}

		private void checkStillUsable(ClassFileModel model, ClassFileModel.MethodDef method, ClassFileModel.Use use, boolean field, Resolved now, Resolved before) {
			if (before.status() != Status.FOUND || !now.owner().fromGame()) {
				return;
			}
			String shown = pretty(use.owner(), use.name(), use.descriptor(), field);
			if (usable(model.name, now, newWorld) || !usable(model.name, before, oldWorld)) {
				if (kindChanged(use, now, newWorld) && !kindChanged(use, before, oldWorld)) {
					add(new Pending(MEMBER_CHANGED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, method.name()), use.line(), shown,
							"This changed between static and instance, or between a class and an interface, in " + newManifest.version() + ". This fails if the game reaches this code.", null, List.of("IncompatibleClassChangeError, by the JVM's rules for linking")));
				}
				return;
			}
			add(new Pending(ACCESS_LOST, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, method.name()), use.line(), shown,
					"Your code can't reach this any more, because it's less visible in " + newManifest.version() + ". This fails if the game reaches this code.", null, List.of("IllegalAccessError, by the JVM's rules for linking")));
		}

		private boolean usable(String from, Resolved resolved, World world) {
			Node owner = resolved.owner();
			if (owner.fromMod() || owner.name().equals(from)) {
				return true;
			}
			String ownPackage = packageOf(from);
			boolean samePackage = ownPackage.equals(packageOf(owner.name()));
			if ((owner.access() & ClassFileModel.ACC_PUBLIC) == 0 && !samePackage) {
				return false;
			}
			int access = resolved.access();
			if ((access & ClassFileModel.ACC_PUBLIC) != 0) {
				return true;
			}
			if ((access & ClassFileModel.ACC_PRIVATE) != 0) {
				return false;
			}
			if ((access & ClassFileModel.ACC_PROTECTED) != 0) {
				return samePackage || world.isSubclass(from, owner.name());
			}
			return samePackage;
		}

		private static boolean kindChanged(ClassFileModel.Use use, Resolved resolved, World world) {
			boolean isStatic = (resolved.access() & ClassFileModel.ACC_STATIC) != 0;
			int op = use.op();
			if (op == ClassFileModel.OP_GETSTATIC || op == ClassFileModel.OP_PUTSTATIC || op == ClassFileModel.OP_INVOKESTATIC) {
				return !isStatic;
			}
			if (isStatic) {
				return true;
			}
			if (op == ClassFileModel.OP_INVOKEINTERFACE) {
				return !resolved.owner().isInterface() && !resolved.owner().name().equals("java/lang/Object");
			}
			if (op == ClassFileModel.OP_INVOKEVIRTUAL) {
				Node named = world.node(use.owner());
				return named != null && named.isInterface();
			}
			return false;
		}

		private void reviewBody(ClassFileModel model, String method, int line, String name, String descriptor, String key, Resolved now, Resolved before) {
			if (!bodiesCompared || before.status() != Status.FOUND || !now.owner().fromGame() || !before.owner().fromGame()) {
				return;
			}
			String newBody = body(newManifest, now.owner().name(), key);
			String oldBody = body(oldManifest, before.owner().name(), key);
			if (newBody != null && oldBody != null && !newBody.equals(oldBody)) {
				add(new Pending(BODY_CHANGED, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(model, method), line, pretty(now.owner().name(), name, descriptor, false),
						"Your code relies on this, and its compiled code differs in " + newManifest.version() + ". That doesn't mean it's a problem, only that it's worth a look.", null, List.of()));
			}
		}

		private static String body(GameManifest manifest, String owner, String key) {
			GameManifest.Type type = manifest.type(owner);
			GameManifest.Method method = type == null ? null : type.methods().get(key);
			return method == null ? null : method.body();
		}

		private void checkLookup(ClassFileModel model, ClassFileModel.MethodDef method, ClassFileModel.ReflectiveLookup lookup) {
			String name = lookup.className();
			if (modClasses.containsKey(name) || oldManifest.type(name) == null || newManifest.type(name) == null) {
				return;
			}
			if (hasNamed(oldWorld, name, lookup) && !hasNamed(newWorld, name, lookup)) {
				add(new Pending(REFLECTION_NAME_GONE, Diagnostic.Severity.WARNING, CONFIDENCE_INFERRED, where(model, method.name()), lookup.line(), dotted(name) + "." + lookup.memberName(),
						"Your code looks this up by name, and " + dotted(name) + " has nothing called that in " + newManifest.version() + ".", sameNamed(name, lookup.memberName(), lookup.field()), List.of()));
			}
		}

		private static boolean hasNamed(World world, String className, ClassFileModel.ReflectiveLookup lookup) {
			Node start = world.node(className);
			if (start == null) {
				return false;
			}
			if (declaresNamed(start, lookup)) {
				return true;
			}
			if (lookup.declaredOnly()) {
				return false;
			}
			Ancestors ancestors = world.ancestors(start);
			// Something unreadable above it might have the name, so it can't be called gone.
			return ancestors.incomplete() || ancestors.nodes().stream().anyMatch(node -> declaresNamed(node, lookup));
		}

		private static boolean declaresNamed(Node node, ClassFileModel.ReflectiveLookup lookup) {
			Set<String> keys = lookup.field() ? node.fields().keySet() : node.methods().keySet();
			String prefix = lookup.memberName() + (lookup.field() ? ":" : "(");
			return keys.stream().anyMatch(key -> key.startsWith(prefix));
		}

		/** A mod's method that overrode the game's can stop doing so with no reference to show for it. */
		private void checkOverrides(ClassFileModel model) {
			Node self = modNodes.get(model.name);
			Ancestors oldAncestors = oldWorld.ancestors(self);
			Ancestors newAncestors = newWorld.ancestors(self);
			for (ClassFileModel.MethodDef method : model.methods) {
				int access = method.access();
				if (method.name().startsWith("<") || (access & (ClassFileModel.ACC_STATIC | ClassFileModel.ACC_PRIVATE | ClassFileModel.ACC_SYNTHETIC | ClassFileModel.ACC_BRIDGE)) != 0) {
					continue;
				}
				String key = GameManifest.methodKey(method.name(), method.descriptor());
				Hit before = overridden(model.name, key, oldAncestors);
				if (before == null) {
					continue;
				}
				Hit now = overridden(model.name, key, newAncestors);
				String shown = pretty(before.owner().name(), method.name(), method.descriptor(), false);
				if (now == null) {
					if (!newAncestors.incomplete()) {
						add(new Pending(OVERRIDE_ORPHANED, Diagnostic.Severity.WARNING, CONFIDENCE_CODE, where(model, method.name()), -1, shown,
								"Your method replaced this, and it isn't in " + newManifest.version() + ". Nothing crashes, but the game won't call your method there any more.", sameNamed(before.owner().name(), method.name(), false), List.of()));
					}
					continue;
				}
				if ((now.access() & ClassFileModel.ACC_FINAL) != 0 && (before.access() & ClassFileModel.ACC_FINAL) == 0) {
					add(new Pending(FINAL_ADDED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, method.name()), -1, pretty(now.owner().name(), method.name(), method.descriptor(), false),
							"Your method replaces this, and it's final in " + newManifest.version() + ". The game stops with an error while loading your mod.", null, List.of(EVIDENCE_LOADING)));
				}
				String oldBody = body(oldManifest, before.owner().name(), key);
				String newBody = body(newManifest, now.owner().name(), key);
				if (bodiesCompared && oldBody != null && newBody != null && !oldBody.equals(newBody)) {
					add(new Pending(BODY_CHANGED, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, where(model, method.name()), -1, pretty(now.owner().name(), method.name(), method.descriptor(), false),
							"Your method replaces this, and its compiled code differs in " + newManifest.version() + ". That doesn't mean it's a problem, only that it's worth a look.", null, List.of()));
				}
			}
		}

		private record Hit(Node owner, int access) {
		}

		private static Hit overridden(String from, String key, Ancestors ancestors) {
			for (Node node : ancestors.nodes()) {
				if (!node.fromGame()) {
					continue;
				}
				Integer access = node.methods().get(key);
				if (access == null || (access & (ClassFileModel.ACC_PRIVATE | ClassFileModel.ACC_STATIC)) != 0) {
					continue;
				}
				boolean visible = (access & (ClassFileModel.ACC_PUBLIC | ClassFileModel.ACC_PROTECTED)) != 0 || packageOf(from).equals(packageOf(node.name()));
				if (visible) {
					return new Hit(node, access);
				}
			}
			return null;
		}

		private void checkAbstract(ClassFileModel model) {
			if ((model.access & (ClassFileModel.ACC_ABSTRACT | ClassFileModel.ACC_INTERFACE)) != 0) {
				return;
			}
			Map<String, String> now = unimplemented(modNodes.get(model.name), newWorld);
			if (now == null || now.isEmpty()) {
				return;
			}
			Map<String, String> before = unimplemented(modNodes.get(model.name), oldWorld);
			if (before == null) {
				return;
			}
			now.forEach((key, owner) -> {
				if (!before.containsKey(key)) {
					add(new Pending(ABSTRACT_ADDED, Diagnostic.Severity.ERROR, CONFIDENCE_CODE, where(model, null), -1, dotted(owner) + "." + prettyMethod(key),
							"Your class must now provide this, because it's abstract in " + newManifest.version() + ". This fails if the game calls it, and won't compile.", null, List.of("AbstractMethodError, by the JVM's rules for linking")));
				}
			});
		}

		/** Abstract game methods nothing implements, by key and where they're declared, or null if a class above can't be read. */
		private static Map<String, String> unimplemented(Node start, World world) {
			Ancestors ancestors = world.ancestors(start);
			if (ancestors.incomplete()) {
				return null;
			}
			// The class chain, most derived first: the first declaration of a method is the one that counts.
			Map<String, Integer> firstInChain = new HashMap<>();
			Set<String> chain = new HashSet<>();
			Node current = start;
			while (current != null) {
				chain.add(current.name());
				for (Map.Entry<String, Integer> method : current.methods().entrySet()) {
					if ((method.getValue() & (ClassFileModel.ACC_STATIC | ClassFileModel.ACC_PRIVATE)) == 0) {
						firstInChain.putIfAbsent(method.getKey(), method.getValue());
					}
				}
				current = current.superName() == null ? null : world.node(current.superName());
			}
			Set<String> defaults = new HashSet<>();
			Map<String, String> wanted = new TreeMap<>();
			for (Node node : ancestors.nodes()) {
				for (Map.Entry<String, Integer> method : node.methods().entrySet()) {
					int access = method.getValue();
					if ((access & (ClassFileModel.ACC_STATIC | ClassFileModel.ACC_PRIVATE)) != 0) {
						continue;
					}
					if ((access & ClassFileModel.ACC_ABSTRACT) != 0) {
						if (node.fromGame()) {
							wanted.putIfAbsent(method.getKey(), node.name());
						}
					} else if (node.isInterface() && !chain.contains(node.name())) {
						defaults.add(method.getKey());
					}
				}
			}
			Map<String, String> missing = new TreeMap<>();
			wanted.forEach((key, owner) -> {
				Integer first = firstInChain.get(key);
				boolean implemented = first == null ? defaults.contains(key) : (first & ClassFileModel.ACC_ABSTRACT) == 0;
				if (!implemented) {
					missing.put(key, owner);
				}
			});
			return missing;
		}

		/** A class in the mod with a game class's name replaces it, so changes to the game's copy don't reach players. */
		private void checkReplaced(ClassFileModel model) {
			String name = model.name;
			GameManifest.Type oldType = oldManifest.type(name);
			GameManifest.Type newType = newManifest.type(name);
			// A numbered class's name says nothing about what it is between versions.
			if ((oldType == null && newType == null) || NUMBERED_CLASS.matcher(name).matches()) {
				return;
			}
			if (newType == null) {
				add(new Pending(CLASS_REPLACED_CHANGED, Diagnostic.Severity.WARNING, CONFIDENCE_CODE, where(model, null), -1, dotted(name),
						"Your mod replaces this game class, and the game doesn't have it in " + newManifest.version() + ", so nothing in the game may use your copy now.", null, List.of(EVIDENCE_CLASSPATH)));
				return;
			}
			if (oldType == null) {
				return;
			}
			List<String> changes = differences(oldType, newType);
			if (!changes.isEmpty()) {
				String caveat = bodiesCompared ? "" : " Method code can't be compared between these versions, so there may be more.";
				add(new Pending(CLASS_REPLACED_CHANGED, Diagnostic.Severity.WARNING, CONFIDENCE_CODE, where(model, null), -1, dotted(name),
						"Your mod replaces this game class, and the game's copy changed in " + newManifest.version() + " in " + changes.size() + (changes.size() == 1 ? " way" : " ways") + ". Those changes don't reach players of your mod." + caveat,
						"Changed: " + limited(changes), List.of(EVIDENCE_CLASSPATH)));
			}
		}

		private List<String> differences(GameManifest.Type oldType, GameManifest.Type newType) {
			List<String> changes = new ArrayList<>();
			Set<String> fields = new TreeSet<>(oldType.fields().keySet());
			fields.addAll(newType.fields().keySet());
			for (String key : fields) {
				GameManifest.Field before = oldType.fields().get(key);
				GameManifest.Field after = newType.fields().get(key);
				if (((before == null ? after.access() : before.access()) & ClassFileModel.ACC_SYNTHETIC) != 0) {
					// The compiler's own, such as this$0, whose presence says nothing about the source.
					continue;
				}
				if (before == null || after == null || before.access() != after.access() || !java.util.Objects.equals(before.constant(), after.constant())) {
					changes.add(prettyField(key));
				}
			}
			Set<String> methods = new TreeSet<>(oldType.methods().keySet());
			methods.addAll(newType.methods().keySet());
			for (String key : methods) {
				GameManifest.Method before = oldType.methods().get(key);
				GameManifest.Method after = newType.methods().get(key);
				if (((before == null ? after.access() : before.access()) & ClassFileModel.ACC_SYNTHETIC) != 0) {
					// The compiler's own, such as the access constructors older Java made for private nested classes.
					continue;
				}
				if (before == null || after == null || before.access() != after.access() || (bodiesCompared && !java.util.Objects.equals(before.body(), after.body()))) {
					changes.add(prettyMethod(key));
				}
			}
			return changes;
		}

		/** A compiled mod holds a copy of a constant's value, so only the classes it uses can be pointed to. */
		private void checkConstants() {
			for (String name : referencedGameClasses) {
				GameManifest.Type before = oldManifest.type(name);
				GameManifest.Type after = newManifest.type(name);
				if (before == null || after == null) {
					continue;
				}
				before.fields().forEach((key, field) -> {
					GameManifest.Field now = after.fields().get(key);
					// A private constant can't be compiled into a mod's code.
					if (now != null && (now.access() & ClassFileModel.ACC_PRIVATE) == 0 && field.constant() != null && now.constant() != null && !field.constant().equals(now.constant())) {
						add(new Pending(CONSTANT_CHANGED, Diagnostic.Severity.REVIEW, CONFIDENCE_INFERRED, "a class your mod uses", -1, dotted(name) + "." + key.substring(0, key.indexOf(':')),
								"This constant's value differs in " + newManifest.version() + ". If your code uses it, the compiled mod still holds the old value until you recompile.", null, List.of()));
					}
				});
			}
		}

		List<Diagnostic> sortedFindings() {
			List<Pending> sorted = new ArrayList<>(pending.values());
			// The game's own code failing comes first, because it fails however the mod's own code is fixed.
			sorted.sort(Comparator.<Pending>comparingInt(finding -> rank(finding.severity)).thenComparing(finding -> finding.code.equals(CLASS_REPLACED_MISSING_MEMBER) ? "" : finding.code).thenComparing(finding -> finding.location).thenComparing(finding -> finding.reference == null ? "" : finding.reference));
			List<Diagnostic> out = new ArrayList<>();
			for (Pending finding : sorted) {
				String message = finding.count == 1 ? finding.message : finding.message + " (" + finding.count + " places)";
				out.add(new Diagnostic(finding.code, finding.severity, finding.confidence, finding.location, finding.line < 0 ? null : finding.line, finding.reference, message, finding.suggestion, finding.evidence, null));
			}
			return List.copyOf(out);
		}

		private static int rank(Diagnostic.Severity severity) {
			return switch (severity) {
			case ERROR -> 0;
			case WARNING -> 1;
			case REVIEW -> 2;
			case TOOL_FAILURE -> 3;
			};
		}
	}

	private static List<String> classesIn(String descriptor) {
		List<String> out = new ArrayList<>();
		Matcher matcher = CLASS_IN_DESCRIPTOR.matcher(descriptor);
		while (matcher.find()) {
			out.add(matcher.group(1));
		}
		return out;
	}

	/** Each parameter's descriptor, as written, from a method descriptor. */
	private static List<String> parameterTypes(String descriptor) {
		List<String> out = new ArrayList<>();
		int at = descriptor.indexOf('(') + 1;
		int end = descriptor.indexOf(')');
		while (at < end) {
			int start = at;
			while (descriptor.charAt(at) == '[') {
				at++;
			}
			at = descriptor.charAt(at) == 'L' ? descriptor.indexOf(';', at) + 1 : at + 1;
			out.add(descriptor.substring(start, at));
		}
		return out;
	}

	private static String limited(List<String> items) {
		int shown = Math.min(items.size(), MAX_LISTED);
		String text = String.join("; ", items.subList(0, shown));
		return items.size() > shown ? text + "; and " + (items.size() - shown) + " more" : text;
	}

	private static String packageOf(String name) {
		int slash = name.lastIndexOf('/');
		return slash < 0 ? "" : name.substring(0, slash);
	}

	private static String dotted(String name) {
		return name.replace('/', '.');
	}

	private static String pretty(String owner, String name, String descriptor, boolean field) {
		return dotted(owner) + "." + (field ? prettyField(GameManifest.fieldKey(name, descriptor)) : prettyMethod(GameManifest.methodKey(name, descriptor)));
	}

	private static String prettyField(String key) {
		int colon = key.indexOf(':');
		return key.substring(0, colon) + " : " + typeName(key.substring(colon + 1), new int[1]);
	}

	private static String prettyMethod(String key) {
		int open = key.indexOf('(');
		int close = key.indexOf(')');
		String descriptor = key.substring(open + 1, close);
		List<String> parameters = new ArrayList<>();
		int[] at = new int[1];
		while (at[0] < descriptor.length()) {
			parameters.add(typeName(descriptor, at));
		}
		String name = key.substring(0, open);
		if (name.equals("<init>")) {
			return "constructor(" + String.join(", ", parameters) + ")";
		}
		if (name.equals("<clinit>")) {
			return "static setup";
		}
		return name + "(" + String.join(", ", parameters) + ") : " + typeName(key.substring(close + 1), new int[1]);
	}

	/** One type from a descriptor, in words a modder knows, moving the position past it. */
	private static String typeName(String descriptor, int[] at) {
		int dimensions = 0;
		while (descriptor.charAt(at[0]) == '[') {
			dimensions++;
			at[0]++;
		}
		char kind = descriptor.charAt(at[0]++);
		String base = switch (kind) {
		case 'Z' -> "boolean";
		case 'B' -> "byte";
		case 'C' -> "char";
		case 'S' -> "short";
		case 'I' -> "int";
		case 'J' -> "long";
		case 'F' -> "float";
		case 'D' -> "double";
		case 'V' -> "void";
		case 'L' -> {
			int end = descriptor.indexOf(';', at[0]);
			String name = dotted(descriptor.substring(at[0], end));
			at[0] = end + 1;
			yield name;
		}
		default -> String.valueOf(kind);
		};
		return base + "[]".repeat(dimensions);
	}

	private ModCodeDiff() {
	}
}
