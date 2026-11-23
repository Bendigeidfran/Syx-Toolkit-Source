package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Makes a manifest from a game jar, reading its classes as bytes. No game code runs here. */
public final class ManifestBuilder {

	/** Libraries the game bundles, which aren't the game's own code. A deny-list, so a new game package is never missed. */
	public static final List<String> EXCLUDED_PACKAGES = List.of("club", "com", "lwjglExport", "org");

	private static final Set<String> EXCLUDED = Set.copyOf(EXCLUDED_PACKAGES);
	private static final Pattern NUMBERED_CLASS = Pattern.compile(".*\\$\\d+.*");

	private ManifestBuilder() {
	}

	public static GameManifest build(Path jar, GameParser.GameVersion version, BooleanSupplier cancelled) throws IOException {
		String sha = sha256(jar);
		Map<String, GameManifest.Type> types = new TreeMap<>();
		Map<Integer, Integer> classVersions = new TreeMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!isGameClass(entry.getName())) {
					continue;
				}
				if (cancelled.getAsBoolean()) {
					throw new CancellationException();
				}
				ClassFileModel model;
				try (InputStream in = zip.getInputStream(entry)) {
					model = ClassFileModel.read(in.readAllBytes());
				} catch (IOException | RuntimeException e) {
					throw new IOException("Couldn't read the class " + entry.getName() + ": " + e, e);
				}
				classVersions.merge(model.major, 1, Integer::sum);
				types.put(model.name, toType(model));
			}
		}
		// Only now is it known which names are game classes, rather than the JDK's or a library's.
		for (Map.Entry<String, GameManifest.Type> entry : types.entrySet()) {
			GameManifest.Type type = entry.getValue();
			Map<String, List<String>> gameOnly = new TreeMap<>(type.uses());
			gameOnly.keySet().retainAll(types.keySet());
			entry.setValue(new GameManifest.Type(type.name(), type.access(), type.superName(), type.interfaces(), type.shape(), type.fields(), type.methods(), gameOnly));
		}
		return new GameManifest("0." + version.major() + "." + version.minor(), version.major(), version.minor(), sha, GameManifest.HASH_METHOD, classVersions, EXCLUDED_PACKAGES, types);
	}

	private static boolean isGameClass(String entryName) {
		if (!entryName.endsWith(".class") || entryName.startsWith("META-INF/")) {
			return false;
		}
		int slash = entryName.indexOf('/');
		return slash < 0 || !EXCLUDED.contains(entryName.substring(0, slash));
	}

	/** A compiler's own helpers, whose names only mean something inside one build. A mod can't name them from its source. */
	static boolean isCompilerHelper(String name, int access) {
		if (name.startsWith("$SWITCH_TABLE$")) {
			return true;
		}
		return (access & ClassFileModel.ACC_SYNTHETIC) != 0 && (name.startsWith("lambda$") || name.startsWith("access$"));
	}

	static GameManifest.Type toType(ClassFileModel model) {
		Map<String, GameManifest.Field> fields = new TreeMap<>();
		for (ClassFileModel.FieldDef field : model.fields) {
			if (!isCompilerHelper(field.name(), field.access())) {
				fields.put(GameManifest.fieldKey(field.name(), field.descriptor()), new GameManifest.Field(field.access(), field.constantHash()));
			}
		}
		Map<String, GameManifest.Method> methods = new TreeMap<>();
		for (ClassFileModel.MethodDef method : model.methods) {
			if (!isCompilerHelper(method.name(), method.access())) {
				methods.put(GameManifest.methodKey(method.name(), method.descriptor()), new GameManifest.Method(method.access(), method.bodyHash()));
			}
		}
		String shape = NUMBERED_CLASS.matcher(model.name).matches() ? shape(model, fields, methods) : null;
		return new GameManifest.Type(model.name, model.access, model.superName, model.interfaces.stream().sorted().toList(), shape, fields, methods, uses(model));
	}

	/** What its code names in other classes, from every method including compiler helpers, because they run too. */
	private static Map<String, List<String>> uses(ClassFileModel model) {
		Map<String, TreeSet<String>> found = new TreeMap<>();
		for (ClassFileModel.MethodDef method : model.methods) {
			for (ClassFileModel.ClassUse use : method.classUses()) {
				if (!use.name().equals(model.name)) {
					found.computeIfAbsent(use.name(), key -> new TreeSet<>());
				}
			}
			for (ClassFileModel.Use use : method.uses()) {
				if (use.owner().startsWith("[") || use.owner().equals(model.name)) {
					continue;
				}
				boolean field = use.op() <= ClassFileModel.OP_PUTFIELD;
				found.computeIfAbsent(use.owner(), key -> new TreeSet<>()).add(field ? GameManifest.fieldKey(use.name(), use.descriptor()) : GameManifest.methodKey(use.name(), use.descriptor()));
			}
		}
		Map<String, List<String>> out = new TreeMap<>();
		found.forEach((owner, members) -> out.put(owner, List.copyOf(members)));
		return out;
	}

	/** What a numbered class declares, leaving out its number, so the same class can be recognised under another one. */
	private static String shape(ClassFileModel model, Map<String, GameManifest.Field> fields, Map<String, GameManifest.Method> methods) {
		List<String> parts = new ArrayList<>();
		parts.add(String.valueOf(model.access));
		parts.add(model.superName == null ? "" : ClassFileModel.neutral(model.superName));
		model.interfaces.forEach(name -> parts.add("i" + ClassFileModel.neutral(name)));
		fields.forEach((key, field) -> parts.add("f" + ClassFileModel.neutral(key) + "/" + field.access() + "/" + field.constant()));
		methods.forEach((key, method) -> parts.add("m" + ClassFileModel.neutral(key) + "/" + method.access() + "/" + method.body()));
		Collections.sort(parts);
		return ClassFileModel.hash(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
	}

	static String sha256(Path file) throws IOException {
		return HexFormat.of().formatHex(digestOf(file));
	}

	private static byte[] digestOf(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
				in.transferTo(java.io.OutputStream.nullOutputStream());
			}
			return digest.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
