package syx.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.SwingUtilities;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import syx.cli.Main;
import syx.core.ClassFileScanner;
import syx.core.Diagnostic;
import syx.core.DiffReportFile;
import syx.core.GameInstall;
import syx.core.GameManifest;
import syx.core.GameParser;
import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.ManifestBuilder;
import syx.core.ManifestStore;
import syx.core.ModCodeDiff;
import syx.core.ModCreator;
import syx.core.ModEntry;
import syx.core.Operations;
import syx.core.ToolkitSettings;
import syx.gui.GameVersionDiffScreen;
import syx.gui.Theme;
import syx.mcp.Server;

/**
 * Game Version Diff. Small "game" and mod classes are compiled here at run time, one for each finding, so no
 * game code is in the repo. With a game folder, the real manifests and front ends are tested too.
 */
public final class ManifestTest {

	private static final Pattern WHITESPACE = Pattern.compile("\\s");
	private static int passed;
	private static int failed;
	private static Path temp;

	private ManifestTest() {
	}

	/** The classes of one version of the small "game". */
	private static Map<String, String> oldGame() {
		Map<String, String> game = new LinkedHashMap<>();
		game.put("g/Removed", "package g; public class Removed { public static int value() { return 1; } }");
		game.put("g/GoneParent", "package g; public class GoneParent { public int inherited() { return 1; } }");
		game.put("g/Base", """
				package g; public class Base {
					public int kept() { return 1; }
					public int gone() { return 1; }
					public int renamedParameters(int a) { return a; }
					public int overridden() { return 1; }
					public void willBeFinal() { }
					public int code() { return 1; }
					public int field;
					public static final int CONST = 1;
					private static final int PRIVATE_CONST = 1;
					public static int privateConst() { return PRIVATE_CONST; }
					public int widened(java.util.ArrayList<String> a) { return 1; }
					public int madeStatic(java.util.ArrayList<String> a) { return 1; }
					public Object narrowed;
					public String retyped;
				}""");
		game.put("g/Top", "package g; public class Top { }");
		game.put("g/Mid", "package g; public class Mid extends Top { public int movesUp() { return 1; } }");
		game.put("g/Child", "package g; public class Child extends Mid { }");
		game.put("g/ListLike", "package g; public class ListLike extends java.util.ArrayList<String> { private static final long serialVersionUID = 1L; }");
		game.put("g/Visible", "package g; public class Visible { public static int hides() { return 1; } }");
		game.put("g/Kind", "package g; public class Kind { public static int flips() { return 1; } }");
		game.put("g/Extendable", "package g; public class Extendable { }");
		game.put("g/Contract", "package g; public abstract class Contract { public abstract int first(); }");
		game.put("g/Replaced", "package g; public class Replaced { public int a() { return 1; } public int b() { return 2; } }");
		game.put("g/Target", "package g; public class Target { private int secret; public int get() { return secret; } }");
		game.put("g/Patched", "package g; public class Patched { }");
		game.put("g/UsesLib", "package g; public class UsesLib extends org.x.Lib { }");
		game.put("org/x/Lib", "package org.x; public class Lib { public int libMethod() { return 1; } }");
		game.put("g/Anon", """
				package g; public class Anon {
					public Object makeB() { return new Runnable() { public void run() { System.out.println("b"); } }; }
				}""");
		game.put("g/Lambda", "package g; public class Lambda { public Runnable make() { return () -> System.out.println(\"x\"); } }");
		game.put("g/ReplacedGone", "package g; public class ReplacedGone { public int x() { return 1; } }");
		game.put("g/Defaults", "package g; public interface Defaults { }");
		game.put("g/Provides", "package g; public interface Provides extends Defaults { }");
		game.put("g/Moved", "package g; public class Moved { public static int value() { return 1; } }");
		game.put("g/Dropped", "package g; public class Dropped { public int gotBy() { return 1; } }");
		game.put("g/UsesDropped", "package g; public class UsesDropped { public int use() { return new Dropped().gotBy(); } }");
		game.put("g/ReplacedChild", "package g; public class ReplacedChild extends Top { }");
		game.put("g/ModKeepsThis", "package g; public class ModKeepsThis { public int use() { return 1; } }");
		return game;
	}

	private static Map<String, String> newGame() {
		Map<String, String> game = new LinkedHashMap<>(oldGame());
		game.remove("g/Removed");
		game.remove("g/GoneParent");
		game.remove("g/ReplacedGone");
		game.put("g/Defaults", "package g; public interface Defaults { int fromDefault(); }");
		game.put("g/Provides", "package g; public interface Provides extends Defaults { @Override default int fromDefault() { return 1; } }");
		game.put("g/Base", """
				package g; public class Base {
					public int kept() { return 1; }
					public int renamedParameters(long a) { return 1; }
					public final void willBeFinal() { }
					public int code() { return 2; }
					public int field;
					public static final int CONST = 2;
					private static final int PRIVATE_CONST = 2;
					public static int privateConst() { return PRIVATE_CONST; }
					public int widened(java.util.List<String> a) { return 1; }
					public static int madeStatic(java.util.List<String> a) { return 1; }
					public String narrowed;
					public Integer retyped;
				}""");
		game.put("g/Top", "package g; public class Top { public int movesUp() { return 1; } }");
		game.put("g/Mid", "package g; public class Mid extends Top { }");
		game.put("g/Visible", "package g; public class Visible { static int hides() { return 1; } }");
		game.put("g/Kind", "package g; public class Kind { public int flips() { return 1; } }");
		game.put("g/Extendable", "package g; public final class Extendable { }");
		game.put("g/Contract", "package g; public abstract class Contract { public abstract int first(); public abstract int second(); public abstract int alreadyThere(); }");
		game.remove("g/Moved");
		game.put("h/Moved", "package h; public class Moved { public static int value() { return 1; } }");
		// What the mod's copy lacks: one the game calls, one only an unused class calls, one nothing calls, and static setup.
		game.put("g/Replaced", """
				package g; public class Replaced {
					static int setup = Integer.parseInt("1");
					public int a() { return 1; } public int b() { return 3; } public int c() { return 4; }
					public int nobodyCalls() { return 5; } public int onlyUnusedCalls() { return 6; } public Object makesUnused() { return new OnlyReplacedUses(); }
					public int viaSubclass() { return 7; } public int viaKept() { return 8; } public Object makesKept() { return new ModKeepsThis(); }
				}""");
		// Named by a game subclass of a replaced class, and by a class only the mod's code keeps reachable.
		game.put("g/SubOfReplaced", "package g; public class SubOfReplaced extends Replaced { }");
		game.put("g/CallsViaSub", "package g; public class CallsViaSub { public int use() { return new SubOfReplaced().viaSubclass(); } }");
		game.put("g/ModKeepsThis", "package g; public class ModKeepsThis { public int use() { return new Replaced().viaKept(); } }");
		game.put("g/CallsC", "package g; public class CallsC { public int use() { return new Replaced().c(); } public int inherited() { return new ReplacedChild().movesUp(); } }");
		// Also uses gotBy, which a reachable class the mod already did without uses too, so that one isn't only cut off.
		game.put("g/OnlyReplacedUses", "package g; public class OnlyReplacedUses { public int use() { return new Replaced().onlyUnusedCalls() + new Dropped().gotBy(); } }");
		game.put("g/Target", "package g; public class Target { private int renamed; public int get() { return renamed; } }");
		// A new anonymous class before the old one, which renumbers it.
		game.put("g/Anon", """
				package g; public class Anon {
					public Object makeA() { return new Runnable() { public void run() { System.out.println("a"); } }; }
					public Object makeB() { return new Runnable() { public void run() { System.out.println("b"); } }; }
				}""");
		return game;
	}

	/** Compiled against the old game, as a mod is. */
	private static Map<String, String> mod() {
		Map<String, String> mod = new LinkedHashMap<>();
		mod.put("m/Uses", """
				package m;
				import g.*;
				public class Uses {
					public int all(Base base, Child child, ListLike list, UsesLib lib, Patched patched) throws Exception {
						int total = Removed.value();
						total += base.kept() + base.gone() + base.renamedParameters(1) + base.code() + base.field;
						total += child.movesUp() + child.hashCode() + list.size() + lib.libMethod();
						total += Visible.hides() + Kind.flips();
						total += patched.added;
						total += base.widened(new java.util.ArrayList<>()) + base.madeStatic(new java.util.ArrayList<>()) + String.valueOf(base.narrowed).length() + base.retyped.length();
						Target.class.getDeclaredField("secret");
						return total;
					}
				}""");
		mod.put("m/OnGone", "package m; public class OnGone extends g.GoneParent { }");
		mod.put("m/Over", """
				package m;
				public class Over extends g.Base {
					@Override public int overridden() { return 2; }
					@Override public void willBeFinal() { }
				}""");
		mod.put("m/OnFinal", "package m; public class OnFinal extends g.Extendable { }");
		mod.put("m/Signs", "package m; public class Signs extends g.Contract { @Override public int first() { return 1; } public int alreadyThere() { return 1; } }");
		mod.put("g/ReplacedGone", "package g; public class ReplacedGone { public int x() { return 9; } }");
		mod.put("m/UsesCopy", "package m; public class UsesCopy { public int use() { return new g.ReplacedGone().x(); } }");
		mod.put("m/GetsDefault", "package m; public class GetsDefault implements g.Provides { }");
		mod.put("g/Dropped", "package g; public class Dropped { }");
		mod.put("g/ReplacedChild", "package g; public class ReplacedChild extends Top { }");
		mod.put("m/NamesKept", "package m; public class NamesKept { public boolean is(Object o) { return o instanceof g.ModKeepsThis; } }");
		mod.put("m/UsesMoved", "package m; public class UsesMoved { public int use() { return g.Moved.value(); } }");
		mod.put("m/Again", "package m; public class Again { public int once() { return g.Removed.value(); } public int twice() { return g.Removed.value() + g.Removed.value(); } }");
		mod.put("g/Replaced", "package g; public class Replaced { public int a() { return 9; } public int b() { return 9; } }");
		mod.put("m/Own", "package m; public class Own { public int mine() { return 1; } public int calls() { return mine(); } }");
		mod.put("m/Leftover", "package m; public class Leftover extends g.Top { public int kept() { return 1; } }");
		return mod;
	}

	/**
	 * An earlier build of m/Leftover, whose anonymous class a build that isn't clean leaves in the jar, and
	 * classes built against it that aren't leftovers but still call what the current one lacks: a named class,
	 * its anonymous class, and an anonymous class the mod ships for a game class it doesn't replace, which the
	 * game's own class creates.
	 */
	private static Map<String, String> staleBuild() {
		return Map.of("m/Leftover", "package m; public class Leftover extends g.Top { public int gone() { return 1; } public Runnable make() { return new Runnable() { public void run() { gone(); } }; } }",
				"m/CallsOld", "package m; public class CallsOld { public int use(Leftover l) { return l.gone(); } public Runnable later(Leftover l) { return new Runnable() { public void run() { l.gone(); } }; } }",
				"g/Anon", "package g; public class Anon { public Object makeB() { return new Runnable() { public void run() { new m.Leftover().gone(); } }; } }");
	}

	public static void main(String[] args) throws Exception {
		temp = Files.createTempDirectory("syx-manifest");
		System.setProperty(ToolkitSettings.FILE_OVERRIDE_PROPERTY, temp.resolve("settings/settings.properties").toString());
		Files.createDirectories(temp.resolve("settings"));
		try {
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			Fixture fixture = null;
			if (compiler == null) {
				System.out.println("SKIPPED the compiled fixtures: this Java has no compiler.");
			} else {
				fixture = new Fixture(compiler);
				findings(fixture);
				syntheticConstructors(compiler);
				extraction(fixture);
				reading(fixture);
				store(fixture);
				reports(fixture);
			}
			heroesOfSyx();
			if (args.length > 0) {
				Path game = Paths.get(args[0]);
				realGame(game);
				if (fixture != null) {
					parity(game, fixture);
				}
			} else {
				System.out.println("SKIPPED the real game: no game folder given.");
			}
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** The small game and mod, compiled once. */
	private static final class Fixture {
		final GameManifest oldManifest;
		final GameManifest newManifest;
		final GameManifest otherTarget;
		final byte[] oldBytes;
		final byte[] newBytes;
		final Path modRoot;
		final Path modJar;

		Fixture(JavaCompiler compiler) throws Exception {
			Path oldClasses = compile(compiler, "old", oldGame(), null, "21");
			Path newClasses = compile(compiler, "new", newGame(), null, "21");
			Path olderTarget = compile(compiler, "target", newGame(), null, "11");
			oldManifest = manifest(oldClasses, "old.jar", 1, 0);
			newManifest = manifest(newClasses, "new.jar", 2, 0);
			otherTarget = manifest(olderTarget, "target.jar", 2, 1);
			oldBytes = oldManifest.toBytes();
			newBytes = newManifest.toBytes();
			// What a mod can add while the game runs, which only its compile can see.
			Map<String, String> patched = Map.of("g/Patched", "package g; public class Patched { public int added; }");
			Path stubs = compile(compiler, "stubs", patched, oldClasses, "21");
			Path modClasses = compile(compiler, "mod", mod(), stubs + java.io.File.pathSeparator + oldClasses, "21");
			// Only the old build's anonymous class is left behind, as a build that isn't clean does.
			Path stale = compile(compiler, "stale", staleBuild(), oldClasses, "21");
			Files.copy(stale.resolve("m/Leftover$1.class"), modClasses.resolve("m/Leftover$1.class"));
			Files.copy(stale.resolve("m/CallsOld.class"), modClasses.resolve("m/CallsOld.class"));
			Files.copy(stale.resolve("m/CallsOld$1.class"), modClasses.resolve("m/CallsOld$1.class"));
			Files.createDirectories(modClasses.resolve("g"));
			Files.copy(stale.resolve("g/Anon$1.class"), modClasses.resolve("g/Anon$1.class"));
			modRoot = temp.resolve("modroot");
			modJar = modRoot.resolve("V1/script/Fixture.jar");
			Files.createDirectories(modJar.getParent());
			jar(modClasses, modJar);
		}

		private GameManifest manifest(Path classes, String name, int major, int minor) throws Exception {
			Path jar = temp.resolve(name);
			jar(classes, jar);
			return ManifestBuilder.build(jar, new GameParser.GameVersion(major, minor), () -> false);
		}
	}

	private static Path compile(JavaCompiler compiler, String name, Map<String, String> sources, Object classpath, String release) throws IOException {
		Path src = temp.resolve("src-" + name);
		Path out = temp.resolve("classes-" + name);
		Files.createDirectories(out);
		List<String> arguments = new ArrayList<>(List.of("--release", release, "-nowarn", "-d", out.toString()));
		if (classpath != null) {
			arguments.add("-cp");
			arguments.add(classpath.toString());
		}
		for (Map.Entry<String, String> source : sources.entrySet()) {
			Path file = src.resolve(source.getKey() + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue());
			arguments.add(file.toString());
		}
		ByteArrayOutputStream errors = new ByteArrayOutputStream();
		int exit = compiler.run(null, null, errors, arguments.toArray(new String[0]));
		if (exit != 0) {
			throw new IllegalStateException("A fixture didn't compile: " + errors);
		}
		return out;
	}

	private static void jar(Path classes, Path jar) throws IOException {
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar)); Stream<Path> walk = Files.walk(classes)) {
			for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
				out.putNextEntry(new ZipEntry(classes.relativize(file).toString().replace('\\', '/')));
				out.write(Files.readAllBytes(file));
				out.closeEntry();
			}
		}
	}

	private static boolean has(ModCodeDiff.Report report, String code, String reference) {
		return report.findings().stream().anyMatch(finding -> finding.code().equals(code) && finding.tagPath() != null && finding.tagPath().contains(reference));
	}

	private static Diagnostic find(ModCodeDiff.Report report, String code, String reference) {
		return report.findings().stream().filter(finding -> finding.code().equals(code) && finding.tagPath() != null && finding.tagPath().contains(reference)).findFirst().orElse(null);
	}

	private static void findings(Fixture f) throws Exception {
		ModCodeDiff.Report report = ModCodeDiff.compare(f.modRoot, f.oldManifest, "shipped", f.newManifest, "shared", () -> false);
		String all = report.findings().stream().map(d -> d.code() + " " + d.tagPath() + " in " + d.file()).toList().toString();
		expect("the jar is scanned: " + report.scanned(), report.scanned().equals(List.of("V1/script/Fixture.jar")));
		expect("a removed class a method uses: " + all, has(report, ModCodeDiff.CLASS_REMOVED, "g.Removed"));
		expect("a removed superclass, which stops loading: " + all, find(report, ModCodeDiff.CLASS_REMOVED, "g.GoneParent") != null && find(report, ModCodeDiff.CLASS_REMOVED, "g.GoneParent").message().contains("while loading"));
		expect("a removed method: " + all, has(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.gone()"));
		Diagnostic parameters = find(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.renamedParameters");
		expect("a changed signature lists what's there now: " + parameters, parameters != null && parameters.suggestion() != null && parameters.suggestion().contains("renamedParameters(long)"));
		expect("but a long doesn't take an int as the same source, so no rebuild is suggested: " + parameters, parameters != null && !parameters.suggestion().contains("rebuilding"));
		Diagnostic widened = find(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.widened");
		expect("a parameter that now takes a type above the old one only needs a rebuild: " + widened, widened != null && widened.severity() == Diagnostic.Severity.ERROR && widened.suggestion().contains("rebuilding"));
		Diagnostic narrowed = find(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.narrowed");
		expect("so does a field whose type is now below the old one: " + narrowed, narrowed != null && narrowed.suggestion().contains("rebuilding"));
		Diagnostic madeStatic = find(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.madeStatic");
		expect("nor one that's now static, which an instance call can't reach: " + madeStatic, madeStatic != null && !madeStatic.suggestion().contains("rebuilding"));
		Diagnostic retyped = find(report, ModCodeDiff.MEMBER_REMOVED, "g.Base.retyped");
		expect("but not one whose type merely changed, which source may not compile against: " + retyped, retyped != null && !retyped.suggestion().contains("rebuilding"));
		expect("it has a line in the mod", parameters != null && parameters.line() != null && parameters.line() > 0);
		expect("less visible: " + all, has(report, ModCodeDiff.ACCESS_LOST, "g.Visible.hides"));
		expect("static to instance: " + all, has(report, ModCodeDiff.MEMBER_CHANGED, "g.Kind.flips"));
		expect("a new abstract method: " + all, has(report, ModCodeDiff.ABSTRACT_ADDED, "g.Contract.second"));
		expect("a class made final: " + all, has(report, ModCodeDiff.FINAL_ADDED, "g.Extendable"));
		expect("a method made final: " + all, has(report, ModCodeDiff.FINAL_ADDED, "g.Base.willBeFinal"));
		expect("an override with nothing left to override: " + all, has(report, ModCodeDiff.OVERRIDE_ORPHANED, "g.Base.overridden"));
		expect("a replaced class the game changed: " + all, has(report, ModCodeDiff.CLASS_REPLACED_CHANGED, "g.Replaced"));
		Diagnostic missing = find(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "g.Replaced.c()");
		expect("the game calling what a replaced class lacks is a break, naming who calls it: " + missing, missing != null && missing.severity() == Diagnostic.Severity.ERROR && missing.suggestion().contains("g.CallsC"));
		expect("and it comes first: " + report.findings().get(0).code(), report.findings().get(0).code().equals(ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER));
		expect("a member nothing calls isn't reported: " + all, !all.contains("nobodyCalls"));
		expect("nor static setup, which nothing can call: " + all, !has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "static setup") && !has(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "static setup"));
		expect("nor a member only classes the mod also replaces use: " + all, !has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "makesUnused"));
		expect("nor one a replaced class inherits, as the JVM would find it: " + all, !all.contains("ReplacedChild.movesUp"));
		Diagnostic unreached = find(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "g.Replaced.onlyUnusedCalls");
		expect("a member only an unreachable game class uses is only to review: " + unreached, unreached != null && unreached.severity() == Diagnostic.Severity.REVIEW && !has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "onlyUnusedCalls"));
		expect("and it names the replaced class that cuts it off, which will need it once updated: " + unreached, unreached != null && unreached.message().contains("(g.Replaced)") && unreached.message().contains("up to date with"));
		Diagnostic gotBy = find(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "g.Dropped.gotBy");
		expect("one the mod already did without in the old version is only to review: " + gotBy, gotBy != null && !has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "gotBy"));
		expect("and isn't said to be cut off by a replaced class: " + gotBy, gotBy != null && gotBy.message().contains("already worked without it") && !gotBy.message().contains("up to date"));
		expect("a game subclass of a replaced class calling what the copy lacks is a break: " + all, has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "g.SubOfReplaced.viaSubclass") && find(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "g.SubOfReplaced.viaSubclass").message().contains("your copy of g.Replaced"));
		expect("a class only the mod's own code names is still reached, so that's a break too: " + all, has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "g.Replaced.viaKept") && !has(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "viaKept"));
		Diagnostic moved = find(report, ModCodeDiff.CLASS_REMOVED, "g.Moved");
		expect("a class that moved says where to: " + moved, moved != null && moved.suggestion().contains("h.Moved"));
		expect("one that's just gone doesn't invent a place: " + find(report, ModCodeDiff.CLASS_REMOVED, "g.Removed"), find(report, ModCodeDiff.CLASS_REMOVED, "g.Removed").suggestion() == null);
		expect("a name looked up by reflection: " + all, has(report, ModCodeDiff.REFLECTION_NAME_GONE, "g.Target.secret"));
		expect("a member a mod adds while running is only to review: " + all, find(report, ModCodeDiff.MEMBER_NOT_IN_EITHER, "g.Patched.added") != null && find(report, ModCodeDiff.MEMBER_NOT_IN_EITHER, "g.Patched.added").severity() == Diagnostic.Severity.REVIEW);
		Diagnostic stale = find(report, ModCodeDiff.STALE_MOD_CLASS, "m.Leftover.gone");
		expect("a class an earlier build left behind is said to be one, to review: " + stale, stale != null && stale.severity() == Diagnostic.Severity.REVIEW && stale.message().contains("m.Leftover$1") && stale.message().contains("clean rebuild"));
		expect("and it's the only one from that class: " + all, report.findings().stream().filter(d -> d.file().startsWith("m.Leftover$1")).count() == 1 && !has(report, ModCodeDiff.MEMBER_REMOVED, "m.Leftover"));
		for (String user : List.of("m.CallsOld.use", "m.CallsOld$1.run", "g.Anon$1.run")) {
			Diagnostic ownCopy = report.findings().stream().filter(d -> d.file().startsWith(user + " ")).findFirst().orElse(null);
			expect(user + ", which something creates, calling what the mod's own class lacks, says so and isn't a leftover: " + ownCopy, ownCopy != null && ownCopy.code().equals(ModCodeDiff.MEMBER_NOT_IN_EITHER)
					&& ownCopy.severity() == Diagnostic.Severity.REVIEW && ownCopy.message().contains("Your copy of m.Leftover") && !ownCopy.message().contains("neither"));
		}
		expect("changed code is to review, when comparable: " + all, find(report, ModCodeDiff.BODY_CHANGED, "g.Base.code") != null && find(report, ModCodeDiff.BODY_CHANGED, "g.Base.code").severity() == Diagnostic.Severity.REVIEW);
		expect("a changed constant is to review: " + all, has(report, ModCodeDiff.CONSTANT_CHANGED, "g.Base.CONST"));

		// What must not be reported.
		expect("a class the mod has its own copy of isn't gone, though the game's is: " + all, !has(report, ModCodeDiff.CLASS_REMOVED, "g.ReplacedGone"));
		expect("but the mod is told nothing in the game may use its copy now: " + all, has(report, ModCodeDiff.CLASS_REPLACED_CHANGED, "g.ReplacedGone"));
		expect("a new abstract method the mod already has isn't asked for: " + all, !has(report, ModCodeDiff.ABSTRACT_ADDED, "alreadyThere"));
		expect("nor one an interface provides a default for: " + all, !has(report, ModCodeDiff.ABSTRACT_ADDED, "fromDefault"));
		expect("a member that moved up a class isn't removed: " + all, !has(report, ModCodeDiff.MEMBER_REMOVED, "movesUp"));
		expect("nor one the JDK provides: " + all, !all.contains("hashCode") && !all.contains("size"));
		expect("a private constant can't be in a mod: " + all, !has(report, ModCodeDiff.CONSTANT_CHANGED, "PRIVATE_CONST"));
		expect("nor is anything said about the mod's own calls: " + all, !all.contains("m.Own"));
		expect("a chain reaching a bundled library can't be called broken: " + all + " " + report.uncheckable(), !all.contains("libMethod") && report.uncheckable() == 1);
		expect("and the summary says so: " + report.summary(), report.summary().contains("1 couldn't be checked"));
		expect("each method it reads is named plainly: " + all, !all.contains("Lg/") && !all.contains("(I)"));

		// Against the version it was built for, which is the rule vanilla's zero errors is to data files.
		ModCodeDiff.Report same = ModCodeDiff.compare(f.modRoot, f.oldManifest, "shipped", f.oldManifest, "shipped", () -> false);
		List<String> noise = same.findings().stream().filter(d -> d.severity() != Diagnostic.Severity.REVIEW).map(d -> d.code() + " " + d.tagPath()).toList();
		expect("a mod has no breaks or silent problems against what it was built for: " + noise, noise.isEmpty());
		// The leftover class and the class built against it are the mod's own problems, so they show against any version.
		expect("only what it adds itself, what it does without and its own stale calls are left to review: " + same.findings(), same.findings().size() == 6 && same.findings().stream().allMatch(d -> d.code().equals(ModCodeDiff.MEMBER_NOT_IN_EITHER) || d.code().equals(ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER) || d.code().equals(ModCodeDiff.STALE_MOD_CLASS)));

		// Compiled for a different Java, so bodies aren't comparable.
		expect("a different compile target is noticed: " + f.otherTarget.dominantClassVersion(), f.otherTarget.dominantClassVersion() != f.oldManifest.dominantClassVersion());
		ModCodeDiff.Report target = ModCodeDiff.compare(f.modRoot, f.oldManifest, "a", f.otherTarget, "b", () -> false);
		expect("so changed code isn't compared: " + target.bodyNote(), !target.bodiesCompared() && !has(target, ModCodeDiff.BODY_CHANGED, "") && target.bodyNote().contains("compiled differently"));
		expect("and the breaks still are", has(target, ModCodeDiff.MEMBER_REMOVED, "g.Base.gone()"));
		expect("and a replaced class says there may be more", find(target, ModCodeDiff.CLASS_REPLACED_CHANGED, "g.Replaced").message().contains("may be more"));

		// Refusals.
		expect("the same version is refused: " + ModCodeDiff.problemWith(f.oldManifest, f.oldManifest), ModCodeDiff.problemWith(f.oldManifest, f.oldManifest).contains("same version"));
		expect("old newer than new is refused: " + ModCodeDiff.problemWith(f.newManifest, f.oldManifest), ModCodeDiff.problemWith(f.newManifest, f.oldManifest).contains("Swap"));
		boolean refused = false;
		try {
			ModCodeDiff.run(f.modRoot, f.newManifest, "a", f.oldManifest, "b", () -> false);
		} catch (ModCodeDiff.DiffRefused e) {
			refused = true;
		}
		expect("and run refuses it", refused);

		// Which jars.
		Path versions = temp.resolve("versions");
		Files.createDirectories(versions.resolve("V1/script"));
		Files.createDirectories(versions.resolve("V2/script"));
		Files.copy(f.modJar, versions.resolve("V1/script/One.jar"));
		ModCodeDiff.Report oldFolder = ModCodeDiff.compare(versions, f.oldManifest, "a", f.newManifest, "b", () -> false);
		expect("with no jar for the new version, the old version's: " + oldFolder.scanned(), oldFolder.scanned().equals(List.of("V1/script/One.jar")));
		Files.copy(f.modJar, versions.resolve("V2/script/Two.jar"));
		ModCodeDiff.Report newFolder = ModCodeDiff.compare(versions, f.oldManifest, "a", f.newManifest, "b", () -> false);
		expect("the new version's jars come first: " + newFolder.scanned(), newFolder.scanned().equals(List.of("V2/script/Two.jar")));
		Path noCode = temp.resolve("nocode");
		Files.createDirectories(noCode.resolve("V1/assets"));
		ModCodeDiff.Report none = ModCodeDiff.compare(noCode, f.oldManifest, "a", f.newManifest, "b", () -> false);
		expect("a mod with no jar isn't an error: " + none.summary(), !none.hasCode() && none.findings().isEmpty() && none.summary().contains("no code"));

		Path broken = temp.resolve("broken");
		Files.createDirectories(broken.resolve("V2/script"));
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(broken.resolve("V2/script/Bad.jar")))) {
			out.putNextEntry(new ZipEntry("m/Bad.class"));
			out.write(new byte[] { 1, 2, 3 });
			out.closeEntry();
		}
		ModCodeDiff.Report bad = ModCodeDiff.compare(broken, f.oldManifest, "a", f.newManifest, "b", () -> false);
		expect("a class that can't be read is the tool's problem, not a break: " + bad.findings(), bad.findings().size() == 1 && bad.findings().get(0).severity() == Diagnostic.Severity.TOOL_FAILURE && bad.summary().contains("couldn't be read"));
		boolean stopped = false;
		try {
			ModCodeDiff.compare(f.modRoot, f.oldManifest, "a", f.newManifest, "b", () -> true);
		} catch (java.util.concurrent.CancellationException e) {
			stopped = true;
		}
		expect("cancelling stops it", stopped);
	}

	/**
	 * Older Java gives a private nested class's constructor a synthetic twin for its outer class to call, and
	 * newer Java doesn't. 0.70.33 and 0.71.44 differ like that, which once filled Silent with 15 classes whose
	 * only change was that twin.
	 */
	private static void syntheticConstructors(JavaCompiler compiler) throws Exception {
		String source = "package g; public class Nest { private static class Inner { private Inner() { } } public Object make() { return new Inner(); } }";
		Path older;
		try {
			older = compile(compiler, "nest-old", Map.of("g/Nest", source), null, "8");
		} catch (IllegalStateException e) {
			System.out.println("SKIPPED synthetic constructors: this JDK can't compile for Java 8.");
			return;
		}
		Path newer = compile(compiler, "nest-new", Map.of("g/Nest", source), null, "21");
		Path mod = compile(compiler, "nest-mod", Map.of("g/Nest", source), null, "21");
		Path oldJar = temp.resolve("nest-old.jar");
		Path newJar = temp.resolve("nest-new.jar");
		jar(older, oldJar);
		jar(newer, newJar);
		GameManifest before = ManifestBuilder.build(oldJar, new GameParser.GameVersion(1, 0), () -> false);
		GameManifest after = ManifestBuilder.build(newJar, new GameParser.GameVersion(2, 0), () -> false);
		boolean twin = before.type("g/Nest$Inner").methods().keySet().stream().anyMatch(key -> key.startsWith("<init>(Lg/Nest$1;)"));
		expect("the old build has the synthetic twin, so this tests something: " + before.type("g/Nest$Inner").methods().keySet(), twin);
		Path modRoot = temp.resolve("nest-modroot");
		Files.createDirectories(modRoot.resolve("V1/script"));
		jar(mod, modRoot.resolve("V1/script/Nest.jar"));
		ModCodeDiff.Report report = ModCodeDiff.compare(modRoot, before, "a", after, "b", () -> false);
		String all = report.findings().stream().map(d -> d.code() + " " + d.tagPath() + ": " + d.suggestion()).toList().toString();
		expect("a replaced class whose only change is the compiler's own isn't said to have changed: " + all, !has(report, ModCodeDiff.CLASS_REPLACED_CHANGED, "g.Nest"));
	}

	/**
	 * Heroes of Syx's v70 jar, which replaces about 200 game classes, against the shipped 0.70.33 and 0.71.44.
	 * Each expectation was checked on 2026-11-06 against the author's own port to 0.71.44 (urliamo/HeroesOfSyx,
	 * 78f64d2 to 1b843e5), and the stale classes with javap and the jar's timestamps. It needs the mod installed
	 * and the manifests in manifests/.
	 */
	private static void heroesOfSyx() throws Exception {
		Path jar = Paths.get(String.valueOf(System.getenv("APPDATA")), "songsofsyx", "mods", "Heroes of Syx V70", "V70", "script", "Heroes of Syx.jar");
		Path oldFile = Paths.get("manifests", "SongsOfSyx-0.70.33-f07a31d3b3b086a2" + GameManifest.FILE_SUFFIX);
		Path newFile = Paths.get("manifests", "SongsOfSyx-0.71.44-fe0137c05408e5ee" + GameManifest.FILE_SUFFIX);
		if (!Files.isRegularFile(jar) || !Files.isRegularFile(oldFile) || !Files.isRegularFile(newFile)) {
			System.out.println("SKIPPED Heroes of Syx: the mod or the shipped manifests aren't here.");
			return;
		}
		Path modRoot = temp.resolve("heroes");
		Files.createDirectories(modRoot.resolve("V70/script"));
		Files.copy(jar, modRoot.resolve("V70/script/Heroes of Syx.jar"));
		ModCodeDiff.Report report = ModCodeDiff.run(modRoot, GameManifest.fromBytes(Files.readAllBytes(oldFile)), "a", GameManifest.fromBytes(Files.readAllBytes(newFile)), "b", () -> false);
		expect("the breaks are as the port confirmed, and Silent lost only the compiler's constructors: " + report.summary(), report.summary().startsWith("135 breaks (400 findings), 51 silent problems (67 findings) and 12 to review"));

		// WPATHING$1$1 and $1$1$1 are from an earlier build, and call what the mod's current WPATHING$1 doesn't have.
		for (String member : List.of("world.map.pathing.WPATHING$1.generate", "world.map.pathing.WPATHING$1.validateInit")) {
			Diagnostic stale = find(report, ModCodeDiff.STALE_MOD_CLASS, member);
			expect(member + " is from a leftover class: " + stale, stale != null && stale.message().contains("WPATHING$1$1"));
			expect("and isn't said to be in neither game version", !has(report, ModCodeDiff.MEMBER_NOT_IN_EITHER, member));
		}

		// Changed only by the synthetic constructors Java 8 made, which 0.71.44's build doesn't have.
		for (String quiet : List.of("game.battle.thread.status.DivsTileMap$Tile", "world.WORLD$Data", "game.battle.thread.general.offence.StepEarlyThrone$Dep", "world.entity.caravan.Shipment$P")) {
			expect(quiet + " isn't said to have changed", !has(report, ModCodeDiff.CLASS_REPLACED_CHANGED, quiet));
		}
		expect("but a real change to a replaced class still is", has(report, ModCodeDiff.CLASS_REPLACED_CHANGED, "view.world.ui.camps.CampInfo"));

		// The author's port left the code using these unchanged, so a rebuild was all they needed.
		for (String member : List.of("settlement.room.main.util.RoomBits.inc", "settlement.room.main.util.RoomBits.set(settlement.room.main.RoomInstance")) {
			Diagnostic hinted = find(report, ModCodeDiff.MEMBER_REMOVED, member);
			expect(member + " says a rebuild should fix it: " + hinted, hinted != null && hinted.suggestion().contains("rebuilding"));
		}
		// Types that changed to unrelated ones can't be proven to compile, and the port edited most such code.
		for (String member : List.of("settlement.thing.halfEntity.HalfEnts.transports", "util.GUTIL.debugger", "game.faction.Faction.buyer", "world.region.pop.RDRaces.maxPop", "init.paths.PATHS.CONFIG")) {
			Diagnostic plain = find(report, ModCodeDiff.MEMBER_REMOVED, member);
			expect(member + " isn't said to need only a rebuild: " + plain, plain != null && (plain.suggestion() == null || !plain.suggestion().contains("rebuilding")));
		}

		// The author added each of these when bringing the replaced classes up to date.
		Diagnostic deal = find(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "game.faction.diplomacy.deal.Deal.setFactionAndClear");
		expect("an unreached member names the replaced class that cuts it off: " + deal, deal != null && deal.severity() == Diagnostic.Severity.REVIEW && deal.message().contains("view.world.ui.faction.UIDiplomacy"));
	}

	private static void extraction(Fixture f) throws Exception {
		GameManifest.Type anonOld = f.oldManifest.type("g/Anon$1");
		GameManifest.Type anonNew = f.newManifest.type("g/Anon$2");
		expect("renumbering an anonymous class doesn't change a body that makes it", f.oldManifest.type("g/Anon").methods().get("makeB()Ljava/lang/Object;").body().equals(f.newManifest.type("g/Anon").methods().get("makeB()Ljava/lang/Object;").body()));
		expect("and its shape identifies it under its new number", anonOld.shape() != null && anonOld.shape().equals(anonNew.shape()));
		expect("a different anonymous class has a different shape", !anonOld.shape().equals(f.newManifest.type("g/Anon$1").shape()));
		expect("a changed body changes its hash", !f.oldManifest.type("g/Base").methods().get("code()I").body().equals(f.newManifest.type("g/Base").methods().get("code()I").body()));
		expect("an unchanged one doesn't", f.oldManifest.type("g/Base").methods().get("kept()I").body().equals(f.newManifest.type("g/Base").methods().get("kept()I").body()));
		expect("a compiler's lambda method is left out: " + f.oldManifest.type("g/Lambda").methods().keySet(), f.oldManifest.type("g/Lambda").methods().keySet().stream().noneMatch(key -> key.startsWith("lambda$")));
		expect("bundled libraries are left out", f.oldManifest.type("org/x/Lib") == null && f.oldManifest.excludedPackages().contains("org"));
		expect("private members are kept, which reflection reaches", f.oldManifest.type("g/Target").fields().containsKey("secret:I"));
		expect("what a class uses in other game classes is recorded: " + f.newManifest.type("g/CallsC").uses(), f.newManifest.type("g/CallsC").uses().get("g/Replaced").contains("c()I"));
		expect("not what it uses in the JDK", f.newManifest.type("g/Lambda").uses().isEmpty());
		expect("nor in itself", !f.newManifest.type("g/CallsC").uses().containsKey("g/CallsC"));
		expect("nor in a bundled library", !f.oldManifest.type("g/UsesLib").uses().containsKey("org/x/Lib"));
		expect("the compile target is recorded: " + f.oldManifest.classVersions(), f.oldManifest.dominantClassVersion() == 65);
		Path jar = temp.resolve("old.jar");
		GameManifest again = ManifestBuilder.build(jar, new GameParser.GameVersion(1, 0), () -> false);
		expect("one jar always gives the same bytes", java.util.Arrays.equals(again.toBytes(), f.oldBytes));
		expect("it's named by version and jar: " + f.oldManifest.fileName(), f.oldManifest.fileName().equals("SongsOfSyx-0.1.0-" + f.oldManifest.jarSha256().substring(0, 16) + ".manifest.json.gz"));
		String text = unpack(f.oldBytes);
		expect("it holds no spaces, so no text from the code: ", !WHITESPACE.matcher(text).find());
		expect("a string in the code isn't in the file", !text.contains("\"b\"") && !text.contains("\"x\""));
		boolean stopped = false;
		try {
			ManifestBuilder.build(jar, new GameParser.GameVersion(1, 0), () -> true);
		} catch (java.util.concurrent.CancellationException e) {
			stopped = true;
		}
		expect("cancelling stops it", stopped);
		Path bad = temp.resolve("badgame.jar");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(bad))) {
			out.putNextEntry(new ZipEntry("g/Bad.class"));
			out.write(new byte[] { 1, 2, 3 });
			out.closeEntry();
		}
		boolean refused = false;
		try {
			ManifestBuilder.build(bad, new GameParser.GameVersion(1, 0), () -> false);
		} catch (IOException e) {
			refused = e.getMessage().contains("g/Bad.class");
		}
		expect("an unreadable game class stops a manifest, rather than leaving a hole in it", refused);
	}

	private static String unpack(byte[] bytes) throws IOException {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static byte[] pack(String text) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

	private static String readProblem(byte[] bytes) {
		try {
			GameManifest.fromBytes(bytes);
			return null;
		} catch (GameManifest.ManifestException e) {
			return e.getMessage();
		}
	}

	private static void reading(Fixture f) throws Exception {
		GameManifest read = GameManifest.fromBytes(f.oldBytes);
		expect("a manifest reads back the same", read.types().equals(f.oldManifest.types()) && read.id().equals(f.oldManifest.id()));
		expect("and writes back the same", java.util.Arrays.equals(read.toBytes(), f.oldBytes));
		String text = unpack(f.oldBytes);
		expect("an extra field at the top is refused", readProblem(pack(text.replaceFirst("\\{", "{\"source\":\"x\","))) != null);
		expect("an extra field in a class is refused", readProblem(pack(text.replaceFirst("\"access\":", "\"code\":\"x\",\"access\":"))) != null);
		expect("text in a name is refused", readProblem(pack(text.replaceFirst("\"g/Anon\"", "\"int x = 1;\""))) != null);
		String badHash = text.replaceFirst(",\"[0-9a-f]{32}\"\\]", ",\"not_a_hash\"]");
		expect("the test really does change a member's hash", !badHash.equals(text));
		expect("a hash that isn't one is refused: " + readProblem(pack(badHash)), String.valueOf(readProblem(pack(badHash))).contains("hash"));
		expect("too many items in a member are refused", readProblem(pack(text.replaceFirst("\\[(\\d+),\"([0-9a-f]{32})\"\\]", "[$1,\"$2\",\"code\"]"))) != null);
		expect("a newer format is refused and said: " + readProblem(pack(text.replaceFirst("\"schema\":2", "\"schema\":9"))), String.valueOf(readProblem(pack(text.replaceFirst("\"schema\":2", "\"schema\":9")))).contains("format 9"));
		String badUse = text.replaceFirst("(\"uses\":\\{\"g/Dropped\":\\[\"<init>\\(\\)V\",)\"gotBy\\(\\)I\"", "$1\"int x = 1;\"");
		expect("the test really does change a use", !badUse.equals(text));
		expect("text in a use is refused: " + readProblem(pack(badUse)), String.valueOf(readProblem(pack(badUse))).contains("name"));
		expect("a bad version is refused", readProblem(pack(text.replaceFirst("\"game_version\":\"[^\"]+\"", "\"game_version\":\"new\""))) != null);
		expect("missing fields are refused", readProblem(pack("{}")) != null);
		expect("something that isn't JSON is refused", readProblem(pack("hello")) != null);
		expect("something that isn't gzip is refused", readProblem(new byte[] { 1, 2, 3 }) != null);
		expect("nor does it throw anything else on wrong types", readProblem(pack("{\"schema\":\"x\",\"hash_method\":1,\"game_version\":1,\"major\":1,\"minor\":1,\"jar_sha256\":1,\"class_versions\":1,\"excluded_packages\":1,\"classes\":1}")) != null);
		byte[] huge = pack(" ".repeat(65 * 1024 * 1024));
		expect("a file that unpacks far too big is refused: " + readProblem(huge), String.valueOf(readProblem(huge)).contains("too big"));
	}

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void store(Fixture f) throws Exception {
		Path shipped = temp.resolve("store/Manifests");
		Path generated = temp.resolve("store/generated");
		Files.createDirectories(shipped);
		Files.createDirectories(generated);
		ManifestStore empty = new ManifestStore(shipped, generated, Map.of());
		expect("nothing to list is fine", empty.list().entries().isEmpty() && empty.list().problems().isEmpty());
		expect("the store that holds nothing lists nothing", ManifestStore.none().list().entries().isEmpty());

		Files.write(shipped.resolve(f.oldManifest.fileName()), f.oldBytes);
		Files.write(shipped.resolve(f.newManifest.fileName()), f.newBytes);
		ManifestStore pinned = new ManifestStore(shipped, generated, Map.of(f.oldManifest.fileName(), sha(f.oldBytes), f.newManifest.fileName(), "0".repeat(64)));
		ManifestStore.Listing listing = pinned.list();
		expect("newest comes first: " + listing.entries(), listing.entries().size() == 2 && listing.entries().get(0).version().equals(f.newManifest.version()));
		expect("a file matching its pin is shipped", listing.entries().get(1).source() == ManifestStore.Source.SHIPPED);
		expect("one that doesn't is only shared", listing.entries().get(0).source() == ManifestStore.Source.SHARED);
		expect("the label says which: " + listing.entries().get(1).label(), listing.entries().get(1).label().equals("0.1.0 (shipped)"));

		Files.write(generated.resolve("SharedByHand" + GameManifest.FILE_SUFFIX), f.oldBytes);
		ManifestStore.Listing twice = pinned.list();
		expect("the same build in two folders is listed once, as the most trusted", twice.entries().size() == 2 && twice.entries().get(1).source() == ManifestStore.Source.SHIPPED);
		Files.delete(generated.resolve("SharedByHand" + GameManifest.FILE_SUFFIX));

		Files.write(generated.resolve("Broken" + GameManifest.FILE_SUFFIX), new byte[] { 1, 2 });
		Files.writeString(generated.resolve("notes.txt"), "not a manifest");
		ManifestStore.Listing withBroken = pinned.list();
		expect("a broken file is reported and doesn't stop the others: " + withBroken.problems(), withBroken.entries().size() == 2 && withBroken.problems().size() == 1 && withBroken.problems().get(0).startsWith("Broken"));
		Files.delete(generated.resolve("Broken" + GameManifest.FILE_SUFFIX));

		String renamedFile = "AnyName" + GameManifest.FILE_SUFFIX;
		Files.write(temp.resolve("store/" + renamedFile), f.oldBytes);
		ManifestStore other = new ManifestStore(temp.resolve("store"), null, Map.of());
		expect("a file is known by what's in it, not its name", other.list().entries().get(0).id().equals(f.oldManifest.id()));

		ManifestStore.Entry older = listing.entries().get(1);
		ManifestStore.Entry newer = listing.entries().get(0);
		expect("the old and new are found by id", pinned.find(older.id()) != null && pinned.find("0.0.0-none") == null);
		expect("they can be compared", ManifestStore.problemWith(older, newer) == null);
		expect("not the wrong way round", ManifestStore.problemWith(newer, older) != null);
		expect("nor the same one", ManifestStore.problemWith(older, older) != null);
		ManifestStore.Installed notInstalled = new ManifestStore.Installed("0.3.0", "x", null, null);
		ManifestStore.Defaults defaults = ManifestStore.defaults(listing.entries(), notInstalled);
		expect("with no manifest for the game, start from the newest two: " + defaults, defaults.newId().equals(newer.id()) && defaults.oldId().equals(older.id()));
		ManifestStore.Defaults atOld = ManifestStore.defaults(listing.entries(), new ManifestStore.Installed(older.version(), older.id(), older, null));
		expect("the installed game is new, with nothing older to pair it with: " + atOld, atOld.newId().equals(older.id()) && atOld.oldId() == null);
		expect("no manifests, no defaults", ManifestStore.defaults(List.of(), notInstalled).newId() == null);
		expect("an unusable game folder is said", new ManifestStore(shipped, generated, Map.of()).installed(temp.resolve("nowhere")).problem() != null);
	}

	private static void reports(Fixture f) throws Exception {
		ModCodeDiff.Report report = ModCodeDiff.compare(f.modRoot, f.oldManifest, "shipped", f.newManifest, "shared", () -> false);
		String text = DiffReportFile.markdown(report, "My Mod");
		expect("it's titled by the mod and versions: " + text.lines().findFirst().orElse(""), text.startsWith("# My Mod: 0.1.0 to 0.2.0\n"));
		expect("it says what was compared, and where each came from", text.contains("- Old: 0.1.0 (shipped), `" + f.oldManifest.id() + "`") && text.contains("- New: 0.2.0 (shared), `" + f.newManifest.id() + "`") && text.contains("`V1/script/Fixture.jar`"));
		expect("it holds the summary", text.contains("uses of game code checked"));
		int breaks = text.indexOf("## Breaks");
		int silent = text.indexOf("## Silent");
		int review = text.indexOf("## To review");
		int limits = text.indexOf("## What this report can't tell you");
		expect("breaks come first, then silent, then to review, then the limits", breaks > 0 && breaks < silent && silent < review && review < limits);
		expect("the limits are always there", text.contains(report.bodyNote()) && text.contains("only running it in the game does"));
		String heading = "### `g.Removed`";
		expect("one game change has one heading: " + heading, text.indexOf(heading) >= 0 && text.indexOf(heading) == text.lastIndexOf(heading));
		String group = text.substring(text.indexOf(heading), text.indexOf("###", text.indexOf(heading) + 4));
		expect("its message appears once, above its places: " + group, group.indexOf("isn't in 0.2.0") == group.lastIndexOf("isn't in 0.2.0"));
		expect("every place using it is listed under it: " + group, group.contains("`m.Again.once (Fixture.jar)`") && group.contains("`m.Again.twice (Fixture.jar)`") && group.contains("`m.Uses.all (Fixture.jar)`"));
		expect("with its line and how many times: " + group, group.matches("(?s).*`m\\.Again\\.twice \\(Fixture\\.jar\\)`, line \\d+ \\(2 places\\).*"));
		String mixed = text.substring(text.indexOf("### `g.GoneParent`"), text.indexOf("###", text.indexOf("### `g.GoneParent`") + 4));
		expect("places that say different things each say theirs: " + mixed, mixed.contains("while loading") && mixed.contains("This fails if the game reaches"));
		expect("a now-in-the-game hint is kept", text.contains("Now in the game:") && text.contains("renamedParameters(long)"));
		expect("nothing raw is left in it", !text.contains("places)\n\n- ") && !text.contains("(I)I"));
		expect("a constant has no place to list", !text.contains("a class your mod uses"));
		expect("the same inputs give the same text", text.equals(DiffReportFile.markdown(ModCodeDiff.compare(f.modRoot, f.oldManifest, "shipped", f.newManifest, "shared", () -> false), "My Mod")));
		expect("it holds no path from this computer", !text.contains(temp.toString()) && !text.contains(temp.toString().replace('\\', '/')) && !text.contains(System.getProperty("user.name") + "\\") && !text.contains("C:\\"));
		expect("a name can't change how the page reads", DiffReportFile.markdown(report, "Bad `<img src=x>` *name*\nline").startsWith("# Bad '\\<img src=x>' \\*name\\* line: "));

		// The file.
		Path folder = temp.resolve("reports/Diff Reports");
		expect("it's named by the mod and versions: " + DiffReportFile.fileName(report, "My Mod"), DiffReportFile.fileName(report, "My Mod").equals("My Mod - 0.1.0 to 0.2.0.md"));
		expect("a character a file name can't hold is replaced", DiffReportFile.fileName(report, "a/b:c?.").equals("a_b_c_ - 0.1.0 to 0.2.0.md"));
		ModCodeDiff.Report sameVersion = ModCodeDiff.compare(f.modRoot, f.newManifest, "a", f.newManifest, "b", () -> false);
		expect("two builds of one version are told apart: " + DiffReportFile.fileName(sameVersion, "M"), DiffReportFile.fileName(sameVersion, "M").contains(f.newManifest.id()));
		DiffReportFile.Saved first = DiffReportFile.save(folder, report, "My Mod", "My Mod");
		expect("saving makes the folder and the file: " + first.file(), first.created() && first.file().equals(folder.resolve("My Mod - 0.1.0 to 0.2.0.md")) && Files.readString(first.file()).equals(text));
		long modified = Files.getLastModifiedTime(first.file()).toMillis();
		DiffReportFile.Saved second = DiffReportFile.save(folder, report, "My Mod", "My Mod");
		expect("the same report again writes nothing", !second.created() && second.file().equals(first.file()) && Files.getLastModifiedTime(first.file()).toMillis() == modified);
		ModCodeDiff.Report other = ModCodeDiff.compare(f.modRoot, f.oldManifest, "shipped", f.otherTarget, "shared", () -> false);
		Path fake = folder.resolve(DiffReportFile.fileName(other, "My Mod"));
		Files.writeString(fake, "someone's own notes");
		DiffReportFile.Saved numbered = DiffReportFile.save(folder, other, "My Mod", "My Mod");
		expect("a different file under that name is kept, and this one gets a number: " + numbered.file(), numbered.created() && numbered.file().getFileName().toString().endsWith(" (2).md") && Files.readString(fake).equals("someone's own notes"));
		expect("no temp files are left", noneEndWith(folder, ".syxtmp"));
		Path noCode = temp.resolve("reports/nocode");
		Files.createDirectories(noCode);
		ModCodeDiff.Report none = ModCodeDiff.compare(noCode, f.oldManifest, "a", f.newManifest, "b", () -> false);
		expect("a mod with no code has no report: " + DiffReportFile.problemWith(none), DiffReportFile.problemWith(none) != null && DiffReportFile.problemWith(report) == null);
		Path clean = temp.resolve("reports/clean");
		Files.createDirectories(clean.resolve("V1/script"));
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(clean.resolve("V1/script/Empty.jar")))) {
			out.putNextEntry(new ZipEntry("readme.txt"));
			out.closeEntry();
		}
		String nothing = DiffReportFile.markdown(ModCodeDiff.compare(clean, f.oldManifest, "a", f.newManifest, "b", () -> false), "Clean");
		expect("a clean report says so, and still says what it can't tell you", nothing.contains("Nothing in this update was found") && nothing.contains("What this report can't tell you") && !nothing.contains("## Breaks"));
		expect("the store that can't save has no folder", ManifestStore.none().reportsFolder() == null);
		expect("a test's store keeps its own folder", new ManifestStore(null, null, folder, Map.of()).reportsFolder().equals(folder));
		// Shipped and generated sharing one folder, as they do beside the .exe.
		Path one = temp.resolve("reports/one");
		Files.createDirectories(one);
		Files.write(one.resolve(f.oldManifest.fileName()), f.oldBytes);
		Files.write(one.resolve(f.newManifest.fileName()), f.newBytes);
		ManifestStore together = new ManifestStore(one, one, null, Map.of(f.oldManifest.fileName(), sha(f.oldBytes)));
		ManifestStore.Listing both = together.list();
		expect("one folder lists each file once: " + both.entries(), both.entries().size() == 2);
		expect("a pinned file there is shipped, and another is shared", both.entries().stream().anyMatch(e -> e.id().equals(f.oldManifest.id()) && e.source() == ManifestStore.Source.SHIPPED)
				&& both.entries().stream().anyMatch(e -> e.id().equals(f.newManifest.id()) && e.source() == ManifestStore.Source.SHARED));
	}

	@SuppressWarnings("unchecked")
	private static void realGame(Path game) throws Exception {
		GameParser.GameVersion version;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			version = parser.version();
		}
		long before = fingerprint(game);
		GameManifest current = ManifestBuilder.build(GameInstall.jarIn(game), version, () -> false);
		byte[] bytes = current.toBytes();
		expect("the real game extracts, and more than a few classes: " + current.types().size(), current.types().size() > 5000);
		expect("to the same bytes each time", java.util.Arrays.equals(ManifestBuilder.build(GameInstall.jarIn(game), version, () -> false).toBytes(), bytes));
		expect("and reads back", GameManifest.fromBytes(bytes).types().equals(current.types()));
		String text = unpack(bytes);
		expect("it holds no spaces, so no text from the code", !WHITESPACE.matcher(text).find());
		try (ClassFileScanner scanner = ClassFileScanner.open(GameInstall.jarIn(game))) {
			int checked = 0;
			for (String name : List.of("init.paths.PATHS", "init.resources.RESOURCES", "settlement.entity.animal.AnimalSpecies", "game.VERSION", "init.Main", "script.ScriptLoad")) {
				ClassFileScanner.ScannedClass scanned = scanner.scan(name);
				if (scanned == null) {
					continue;
				}
				for (ClassFileScanner.ScannedMethod method : scanned.methods()) {
					for (ClassFileScanner.Call call : method.calls()) {
						for (ClassFileScanner.Value arg : call.args()) {
							if (arg instanceof ClassFileScanner.Const constant && constant.value() instanceof String literal && literal.length() >= 3) {
								checked++;
								if (text.contains("\"" + literal + "\"")) {
									expect("a string in the game's code isn't in the manifest: " + literal, false);
								}
							}
						}
					}
				}
			}
			expect("a sample of the game's strings were looked for: " + checked, checked > 20);
		}

		// Generating never overwrites, and never touches the game.
		Path generated = temp.resolve("real/generated");
		ManifestStore store = new ManifestStore(null, generated, Map.of());
		ManifestStore.Installed first = store.installed(game);
		expect("the game has no manifest yet: " + first, first.problem() == null && first.existing() == null && first.id().equals(current.id()));
		ManifestStore.Generated made = store.generate(game, () -> false);
		expect("generating makes one: " + made, made.created() && made.entry().source() == ManifestStore.Source.GENERATED && made.entry().id().equals(current.id()));
		byte[] written = Files.readAllBytes(made.entry().file());
		long modified = Files.getLastModifiedTime(made.entry().file()).toMillis();
		ManifestStore.Generated again = store.generate(game, () -> false);
		expect("generating again writes nothing", !again.created() && java.util.Arrays.equals(Files.readAllBytes(made.entry().file()), written) && Files.getLastModifiedTime(made.entry().file()).toMillis() == modified);
		expect("and it's now found", store.installed(game).existing() != null);
		expect("no temp files are left", noneEndWith(generated, ".syxtmp"));
		// An older format's file of the same name is listed as unusable, and isn't this toolkit's to replace.
		byte[] outdated = pack(unpack(written).replaceFirst("\"schema\":2", "\"schema\":1"));
		Files.write(made.entry().file(), outdated);
		ManifestStore.Generated beside = store.generate(game, () -> false);
		expect("a new one goes beside an old format's: " + beside.entry().file().getFileName(), beside.created() && !beside.entry().file().equals(made.entry().file()) && java.util.Arrays.equals(Files.readAllBytes(made.entry().file()), outdated));
		expect("and the old one is said to be replaceable: " + store.list().problems(), store.list().problems().size() == 1 && store.list().problems().get(0).contains("can be deleted"));
		Files.delete(beside.entry().file());
		Files.delete(made.entry().file());
		boolean stopped = false;
		try {
			store.generate(game, () -> true);
		} catch (java.util.concurrent.CancellationException e) {
			stopped = true;
		}
		expect("cancelling writes nothing", stopped && noneEndWith(generated, ".syxtmp") && noneEndWith(generated, GameManifest.FILE_SUFFIX));
		expect("the game's folder is unchanged", fingerprint(game) == before);

		// The real mod and the real old game, if they're on this machine.
		// The mod was built for 0.71.44, which the mirror keeps whatever the installed game has moved on to.
		Path oldGame = Paths.get("Source", "Old Game Source", "Songs of Syx");
		Path builtFor = Paths.get("Source", "Game Source", "Install Mirror");
		Path elohi = game.getParent().getParent().resolve("workshop/content/1162750/3620581808/V71/script/SongsOfElohi.jar");
		if (!Files.isRegularFile(GameInstall.jarIn(oldGame)) || !Files.isRegularFile(GameInstall.jarIn(builtFor)) || !Files.isRegularFile(elohi)) {
			System.out.println("SKIPPED the real mod: an old game copy or the Workshop mod isn't here.");
			return;
		}
		GameManifest older;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(oldGame))) {
			older = ManifestBuilder.build(GameInstall.jarIn(oldGame), parser.version(), () -> false);
		}
		current = ManifestBuilder.build(GameInstall.jarIn(builtFor), new GameParser.GameVersion(71, 44), () -> false);
		Path modRoot = temp.resolve("elohi");
		Files.createDirectories(modRoot.resolve("V71/script"));
		Files.copy(elohi, modRoot.resolve("V71/script/SongsOfElohi.jar"));
		ModCodeDiff.Report forward = ModCodeDiff.run(modRoot, older, "a", current, "b", () -> false);
		List<String> breaks = forward.findings().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR || d.severity() == Diagnostic.Severity.WARNING).map(d -> d.code() + " " + d.tagPath() + " in " + d.file()).toList();
		expect("a real mod built for this version has no breaks or silent problems: " + breaks, breaks.isEmpty());
		expect("what it patches in while running is left to review", forward.findings().stream().filter(d -> d.code().equals(ModCodeDiff.MEMBER_NOT_IN_EITHER)).count() == 3);
		expect("and nothing was unreadable: " + forward.summary(), forward.count(Diagnostic.Severity.TOOL_FAILURE) == 0);
		expect("these versions were compiled differently, so code isn't compared: " + forward.bodyNote(), !forward.bodiesCompared());
		ModCodeDiff.Report backward = ModCodeDiff.compare(modRoot, current, "a", older, "b", () -> false);
		// Each of these was checked by hand against both versions' source files on 2026-11-05.
		expect("the other way, a class the old version doesn't have: " + backward.summary(), has(backward, ModCodeDiff.CLASS_REMOVED, "init.trade.TRADABLE"));
		expect("a superclass it doesn't have", find(backward, ModCodeDiff.CLASS_REMOVED, "game.faction.trade.FBUYER") != null);
		expect("a method it doesn't have", has(backward, ModCodeDiff.MEMBER_REMOVED, "init.paths.PATH.gets"));
		expect("a field whose type differs lists the one there", find(backward, ModCodeDiff.MEMBER_REMOVED, "view.sett.SettView.ui").suggestion().contains("UIPanelTopSett"));
		expect("a method the mod overrode", has(backward, ModCodeDiff.OVERRIDE_ORPHANED, "snake2d.util.sets.Tree.capacity"));
		expect("a name looked up by reflection", has(backward, ModCodeDiff.REFLECTION_NAME_GONE, "view.sett.ui.standing.UICitizens.race"));
		expect("the private constant isn't listed", !has(forward, ModCodeDiff.CONSTANT_CHANGED, "DataRandom.rd"));
		expect("a public one is", has(forward, ModCodeDiff.CONSTANT_CHANGED, "Immigration.MAX_POPULATION"));
		regionsRework(game, current);
	}

	/**
	 * The first real mod that replaces game classes, against the real update. Each expectation was checked on
	 * 2026-11-05 with a separate javap-based checker sharing no code with the toolkit. It needs that mod installed
	 * and the installed game to be 0.71.84.
	 */
	private static void regionsRework(Path game, GameManifest v44) throws Exception {
		Path jar = ToolkitPaths.modsFolderJar();
		if (!Files.isRegularFile(jar) || !GameManifest.idOf("0.71.84", ManifestSha.of(GameInstall.jarIn(game))).equals("0.71.84-3ae4b301342f3dd0")) {
			System.out.println("SKIPPED Regions Rework: the mod isn't installed, or the installed game isn't that build of 0.71.84.");
			return;
		}
		GameManifest v84 = ManifestBuilder.build(GameInstall.jarIn(game), new GameParser.GameVersion(71, 84), () -> false);
		Path modRoot = temp.resolve("regions");
		Files.createDirectories(modRoot.resolve("V71/script"));
		Files.copy(jar, modRoot.resolve("V71/script/Region rework.jar"));
		ModCodeDiff.Report report = ModCodeDiff.run(modRoot, v44, "a", v84, "b", () -> false);
		String all = report.findings().stream().filter(d -> d.severity() != Diagnostic.Severity.REVIEW).map(d -> d.code() + " " + d.tagPath()).toList().toString();

		// The game's own code reading what the mod's copy lacks, which fails while building region data.
		Diagnostic count = find(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "world.region.RD$RDInit.count : world.region.RD$DataRegion");
		expect("RDInit.count is a break, naming the game classes that read it: " + count, count != null && count.severity() == Diagnostic.Severity.ERROR && count.suggestion().contains("world.region.RDDistance") && count.suggestion().contains("world.region.RDHealth"));
		Diagnostic rCount = find(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, "world.region.RD$RDInit.rCount : world.region.RD$DataFaction");
		expect("so is RDInit.rCount: " + rCount, rCount != null && rCount.suggestion().contains("world.region.RDDistance") && rCount.suggestion().contains("world.region.RData"));
		expect("they're the only ones: " + all, report.findings().stream().filter(d -> d.code().equals(ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER)).count() == 2);
		Diagnostic tmp = find(report, ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER, "settlement.room.main.ROOMS.tmpRooms");
		expect("a member only a class the mod leaves unused reads is only to review: " + tmp, tmp != null && tmp.suggestion().contains("ROOMS$RoomInitAction"));

		// The six false alarms and two wrong reasons from the first version of this check.
		for (String wrong : List.of("this$0", "static setup", "froms", "tos :", "amountHack", "hover(snake2d.util.gui.GUI_BOX", "<clinit>")) {
			expect("nothing's reported missing about " + wrong + ": " + all, report.findings().stream().noneMatch(d -> (d.code().equals(ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER) || d.code().equals(ModCodeDiff.CLASS_REPLACED_UNREACHED_MEMBER)) && d.tagPath().contains(wrong)));
		}
		expect("the game calling update on a replaced class isn't a failure, because it inherits one", !has(report, ModCodeDiff.CLASS_REPLACED_MISSING_MEMBER, ".update("));
		expect("but the mod's update no longer overriding anything is said: " + all, has(report, ModCodeDiff.OVERRIDE_ORPHANED, "settlement.tilemap.TileMap$Resource.update") && has(report, ModCodeDiff.OVERRIDE_ORPHANED, "world.WORLD$WorldResource.update"));

		// What the mod's own code uses.
		expect("moved classes say where to: " + find(report, ModCodeDiff.CLASS_REMOVED, "snake2d.PathTile"), find(report, ModCodeDiff.CLASS_REMOVED, "snake2d.PathTile").suggestion().contains("snake2d.pathing.PathTile")
				&& find(report, ModCodeDiff.CLASS_REMOVED, "game.debug.Profiler").suggestion().contains("game.profile.Profiler"));
		for (String member : List.of("settlement.stats.equip.StatsEquip.collAll", "util.GUTIL.flooder", "world.map.pathing.WDirMap.pushSimple")) {
			expect("the mod's own use of " + member + " is a break: " + all, has(report, ModCodeDiff.MEMBER_REMOVED, member));
		}
		expect("a game change is counted once: " + report.summary(), report.summary().startsWith("8 breaks (13 findings)"));
		expect("the 22 changed methods the separate checker found: " + report.findings().stream().filter(d -> d.code().equals(ModCodeDiff.BODY_CHANGED)).map(Diagnostic::tagPath).distinct().count(), report.findings().stream().filter(d -> d.code().equals(ModCodeDiff.BODY_CHANGED)).map(Diagnostic::tagPath).distinct().count() == 22);

		// On the version it's built for, which it really does work on.
		ModCodeDiff.Report control = ModCodeDiff.compare(modRoot, v44, "a", v44, "b", () -> false);
		expect("against 0.71.44 itself there's nothing: " + control.findings(), control.findings().isEmpty());
	}

	private static final class ToolkitPaths {
		static Path modsFolderJar() {
			return Paths.get(String.valueOf(System.getenv("APPDATA")), "songsofsyx", "mods", "Regions Rework", "V71", "script", "Region rework.jar");
		}
	}

	private static final class ManifestSha {
		static String of(Path file) throws Exception {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		}
	}

	private static boolean noneEndWith(Path folder, String suffix) throws IOException {
		if (!Files.isDirectory(folder)) {
			return true;
		}
		try (Stream<Path> files = Files.list(folder)) {
			return files.noneMatch(file -> file.getFileName().toString().endsWith(suffix));
		}
	}

	private static long fingerprint(Path folder) throws IOException {
		long total = 0;
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.toList()) {
				total = total * 31 + file.getFileName().toString().hashCode() + (Files.isRegularFile(file) ? Files.size(file) + Files.getLastModifiedTime(file).toMillis() : 0);
			}
		}
		return total;
	}

	/** The screen, the operations, the command line and the MCP server must give the same report. */
	@SuppressWarnings("unchecked")
	private static void parity(Path game, Fixture f) throws Exception {
		Path mods = temp.resolve("parity/mods");
		Files.createDirectories(mods);
		ToolkitSettings settings = new ToolkitSettings(ToolkitSettings.defaultFile());
		ManifestStore unpackaged = ManifestStore.openDefault();
		expect("outside the packaged app there's nowhere beside an .exe to save a report", unpackaged.reportsFolder() == null);
		expect("nor to generate a manifest", unpackaged.list().entries().isEmpty());
		// A pretend packaged app in the test's own folder, where the store and the command line both look.
		Path appFolder = temp.resolve("parity/app");
		Files.createDirectories(appFolder);
		System.setProperty("jpackage.app-path", appFolder.resolve("Syx Toolkit.exe").toString());
		ManifestStore store = ManifestStore.openDefault();
		expect("in the packaged app, reports go beside the .exe: " + store.reportsFolder(), store.reportsFolder().equals(appFolder.resolve("Diff Reports")));
		Path generated = appFolder.resolve("Manifests");
		Files.createDirectories(generated);
		Files.write(generated.resolve(f.oldManifest.fileName()), f.oldBytes);
		Files.write(generated.resolve(f.newManifest.fileName()), f.newBytes);
		Operations operations = new Operations(() -> game, mods, settings, new HandbookStore(temp.resolve("parity/handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), store, false);
		ModEntry mod = ModCreator.create(mods, game, "Through All");
		Files.createDirectories(mod.root().resolve("V1/script"));
		Files.copy(f.modJar, mod.root().resolve("V1/script/Fixture.jar"));

		Operations.Result listed = operations.call("list_manifests", Map.of());
		expect("agents can list versions: " + listed.payload(), listed.outcome() == Operations.Outcome.OK && ((List<?>) listed.payload().get("manifests")).size() == 2);
		expect("with what the app would start with: " + listed.payload(), f.newManifest.id().equals(listed.payload().get("default_new")) && f.oldManifest.id().equals(listed.payload().get("default_old")));
		expect("each says where it came from", ((List<Map<String, Object>>) listed.payload().get("manifests")).stream().allMatch(entry -> "shared".equals(entry.get("source"))));
		expect("and none of the player's real ones are read", !String.valueOf(listed.payload()).contains("generated here"));

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("mod", mod.folderName());
		input.put("old", f.oldManifest.id());
		input.put("new", f.newManifest.id());
		Operations.Result direct = operations.call("diff_mod", input);
		expect("agents can run the diff: " + direct.outcome(), direct.outcome() == Operations.Outcome.OK && direct.hasErrors());
		String expected = JsonText.write(direct.payload());
		expect("the result holds the breaks", expected.contains(ModCodeDiff.MEMBER_REMOVED));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int exit = Main.run(new String[] { "--game", game.toString(), "--mods", mods.toString(), "--json", "diff_mod", "--mod", mod.folderName(), "--old", f.oldManifest.id(), "--new", f.newManifest.id() }, new PrintStream(bytes, true, StandardCharsets.UTF_8));
		String cli = JsonText.write(JsonText.parse(bytes.toString(StandardCharsets.UTF_8)));
		expect("the command line gives the same, and exits 1 for breaks: " + exit, exit == 1 && cli.equals(expected));

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
		meta.put("io.modelcontextprotocol/clientCapabilities", new LinkedHashMap<String, Object>());
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("name", "diff_mod");
		params.put("arguments", input);
		params.put("_meta", meta);
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("jsonrpc", "2.0");
		request.put("id", 1.0);
		request.put("method", "tools/call");
		params.put("_meta", meta);
		request.put("params", params);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new Server(operations, new ByteArrayInputStream((JsonText.write(request) + "\n").getBytes(StandardCharsets.UTF_8)), out).run();
		Map<String, Object> reply = (Map<String, Object>) JsonText.parse(out.toString(StandardCharsets.UTF_8).trim());
		List<Map<String, Object>> content = (List<Map<String, Object>>) ((Map<String, Object>) reply.get("result")).get("content");
		expect("the MCP server gives the same", JsonText.write(JsonText.parse((String) content.get(0).get("text"))).equals(expected));

		GameVersionDiffScreen[] holder = new GameVersionDiffScreen[1];
		SwingUtilities.invokeAndWait(() -> {
			Theme.install(ToolkitSettings.ThemeChoice.DARK);
			holder[0] = new GameVersionDiffScreen(() -> game, store, folder -> { });
			holder[0].open(mod);
		});
		GameVersionDiffScreen screen = holder[0];
		waitUntilIdle(screen);
		expect("the screen starts with the same versions", f.oldManifest.id().equals(screen.selectedOld()) && f.newManifest.id().equals(screen.selectedNew()));
		expect("and can run them: " + screen.reasonText(), screen.canRun());
		SwingUtilities.invokeAndWait(screen::runDiff);
		waitUntilIdle(screen);
		expect("the screen gives the same report", screen.lastReport() != null && JsonText.write(JsonText.parse(JsonText.write(screen.lastReport().toMap()))).equals(expected));
		expect("and shows every finding: " + screen.resultCount(), screen.resultCount() == screen.lastReport().findings().size());
		expect("with the selected one in full: " + screen.detailText(), screen.detailText().contains(screen.lastReport().findings().get(0).message()));
		// Exporting, which every front end must save the same bytes for.
		expect("the screen can export: " + screen.canExport(), screen.canExport());
		SwingUtilities.invokeAndWait(screen::export);
		Path saved = appFolder.resolve("Diff Reports/Through All - 0.1.0 to 0.2.0.md");
		expect("Export Report saves it beside the .exe: " + screen.exportNoteText(), Files.isRegularFile(saved) && screen.exportNoteText().contains(saved.toString()));
		String fromScreen = Files.readString(saved);
		Operations.Result exported = operations.call("export_diff_report", input);
		expect("an agent's export finds that same report, and writes nothing: " + exported.payload().get("created"), exported.outcome() == Operations.Outcome.OK && Boolean.FALSE.equals(exported.payload().get("created")) && saved.toString().equals(exported.payload().get("file")));
		expect("and gets the same text", fromScreen.equals(exported.payload().get("report")));
		ByteArrayOutputStream exportOut = new ByteArrayOutputStream();
		int exportExit = Main.run(new String[] { "--game", game.toString(), "--mods", mods.toString(), "--json", "export_diff_report", "--mod", mod.folderName(), "--old", f.oldManifest.id(), "--new", f.newManifest.id() }, new PrintStream(exportOut, true, StandardCharsets.UTF_8));
		expect("so does the command line: " + exportExit, exportExit == 0 && fromScreen.equals(((Map<String, Object>) JsonText.parse(exportOut.toString(StandardCharsets.UTF_8))).get("report")));
		try (Stream<Path> folderFiles = Files.list(appFolder.resolve("Diff Reports"))) {
			expect("and there's still one file", folderFiles.count() == 1);
		}
		expect("an agent can't export when read-only", new Operations(() -> game, mods, settings, new HandbookStore(temp.resolve("parity/handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), store, true).call("export_diff_report", input).outcome() == Operations.Outcome.REFUSED);
		Operations noFolder = new Operations(() -> game, mods, settings, new HandbookStore(temp.resolve("parity/handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), unpackaged, false);
		expect("nor with nowhere to save: " + noFolder.call("export_diff_report", input).payload(), noFolder.call("export_diff_report", input).outcome() == Operations.Outcome.REFUSED);
		ModEntry noCodeMod = ModCreator.create(mods, game, "No Code");
		Operations.Result noReport = operations.call("export_diff_report", Map.of("mod", noCodeMod.folderName(), "old", f.oldManifest.id(), "new", f.newManifest.id()));
		expect("nor for a mod with no code: " + noReport.payload(), noReport.outcome() == Operations.Outcome.REFUSED && String.valueOf(noReport.payload().get("refused")).contains("no code"));

		SwingUtilities.invokeAndWait(() -> {
			screen.selectOld(f.newManifest.id());
			screen.selectNew(f.oldManifest.id());
		});
		expect("the screen won't run them the wrong way round, and says why: " + screen.reasonText(), !screen.canRun() && screen.reasonText().contains("Swap"));
		Operations.Result refused = operations.call("diff_mod", Map.of("mod", mod.folderName(), "old", f.newManifest.id(), "new", f.oldManifest.id()));
		expect("nor will an agent, in the same words", refused.outcome() == Operations.Outcome.REFUSED && screen.reasonText().equals(refused.payload().get("refused")));
		Operations.Result unknown = operations.call("diff_mod", Map.of("mod", mod.folderName(), "old", "nope", "new", f.oldManifest.id()));
		expect("an unknown version is refused", unknown.outcome() == Operations.Outcome.REFUSED);

		Operations readOnly = new Operations(() -> game, mods, settings, new HandbookStore(temp.resolve("parity/handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), store, true);
		expect("generating is refused when read-only", readOnly.call("generate_manifest", Map.of()).outcome() == Operations.Outcome.REFUSED);
		expect("reading and diffing aren't", readOnly.call("diff_mod", input).outcome() == Operations.Outcome.OK);
		Operations.Result made = operations.call("generate_manifest", Map.of());
		expect("an agent can generate one: " + made.payload(), made.outcome() == Operations.Outcome.OK && Boolean.TRUE.equals(made.payload().get("created")));
		expect("it goes in Manifests beside the .exe, with the shipped ones: " + made.payload().get("file"), Path.of(String.valueOf(made.payload().get("file"))).getParent().equals(generated));
		// The same jar always gives the same bytes, so a released build's comes out as the shipped file.
		expect("and is listed as generated here, or as shipped if it's a released build: " + made.payload().get("source"), "generated here".equals(made.payload().get("source")) || "shipped".equals(made.payload().get("source")));
		Files.write(generated.resolve("Shared" + GameManifest.FILE_SUFFIX), f.oldBytes);
		expect("one put there by hand is shared, though it's the same folder", ((List<Map<String, Object>>) operations.call("list_manifests", Map.of()).payload().get("manifests")).stream().anyMatch(entry -> f.oldManifest.id().equals(entry.get("id")) && "shared".equals(entry.get("source"))));
		Files.delete(generated.resolve("Shared" + GameManifest.FILE_SUFFIX));
		Operations.Result madeAgain = operations.call("generate_manifest", Map.of());
		expect("and again writes nothing", madeAgain.outcome() == Operations.Outcome.OK && Boolean.FALSE.equals(madeAgain.payload().get("created")));
	}

	private static void waitUntilIdle(GameVersionDiffScreen screen) throws Exception {
		long stop = System.currentTimeMillis() + 60000;
		boolean[] busy = { true };
		while (busy[0] && System.currentTimeMillis() < stop) {
			SwingUtilities.invokeAndWait(() -> busy[0] = screen.isBusy());
			Thread.sleep(20);
		}
		SwingUtilities.invokeAndWait(() -> { });
	}

	private static void deleteFolder(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static void expect(String what, boolean condition) {
		if (condition) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + what);
		}
	}
}
