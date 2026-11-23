package syx.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One game version's classes and members, by name, with one-way hashes of their code. It holds no bytecode,
 * string literals or source, as the developer's consent requires. Reading checks that a file holds only these
 * fields, because manifests are passed between people.
 */
public final class GameManifest {

	/** The structure of the file. A different number can't be read. */
	public static final int SCHEMA = 2;
	/** How bodies are hashed. A different number still gives link results, but bodies can't be compared. */
	public static final int HASH_METHOD = 1;
	public static final String FILE_SUFFIX = ".manifest.json.gz";

	/** Far above a real one, which unpacks to about 7 MB, and stops a hostile file filling memory. */
	private static final int MAX_UNPACKED = 64 * 1024 * 1024;
	private static final int MAX_NAME = 2048;
	private static final Pattern HASH = Pattern.compile("[0-9a-f]{32}");
	private static final Pattern JAR_HASH = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern VERSION = Pattern.compile("0\\.\\d{1,6}\\.\\d{1,6}");
	/** Names and descriptors have no spaces in them, so nothing like a line of code can be carried in one. */
	private static final Pattern NAME = Pattern.compile("[^\\s]+");
	private static final Set<String> TOP_KEYS = Set.of("schema", "hash_method", "game_version", "major", "minor", "jar_sha256", "class_versions", "excluded_packages", "classes");
	private static final Set<String> TYPE_KEYS = Set.of("access", "super", "interfaces", "shape", "fields", "methods", "uses");

	/** Thrown when a file can't be used, in words a person can act on. */
	public static final class ManifestException extends Exception {
		private static final long serialVersionUID = 1L;

		ManifestException(String message) {
			super(message);
		}
	}

	/** @param constant a hash of its compile-time value, or null */
	public record Field(int access, String constant) {
	}

	/** @param body a hash of its normalised code, or null if it has none */
	public record Method(int access, String body) {
	}

	/**
	 * @param shape for a numbered anonymous class, a hash of what it declares, which identifies it if it's renumbered
	 * @param fields by name:descriptor
	 * @param methods by name and descriptor, run together
	 * @param uses the other game classes its code names, each with the members it uses there, by the same keys as
	 *        above. It's names only. A class a mod replaces loses members, and this says if the game's code needs them.
	 */
	public record Type(String name, int access, String superName, List<String> interfaces, String shape, Map<String, Field> fields, Map<String, Method> methods, Map<String, List<String>> uses) {
	}

	private final String version;
	private final int major;
	private final int minor;
	private final String jarSha256;
	private final int hashMethod;
	private final Map<Integer, Integer> classVersions;
	private final List<String> excluded;
	private final Map<String, Type> types;

	GameManifest(String version, int major, int minor, String jarSha256, int hashMethod, Map<Integer, Integer> classVersions, List<String> excluded, Map<String, Type> types) {
		this.version = version;
		this.major = major;
		this.minor = minor;
		this.jarSha256 = jarSha256;
		this.hashMethod = hashMethod;
		this.classVersions = Collections.unmodifiableMap(new TreeMap<>(classVersions));
		this.excluded = List.copyOf(excluded);
		this.types = types;
	}

	public String version() {
		return version;
	}

	public int major() {
		return major;
	}

	public int minor() {
		return minor;
	}

	public String jarSha256() {
		return jarSha256;
	}

	public int hashMethod() {
		return hashMethod;
	}

	public Map<Integer, Integer> classVersions() {
		return classVersions;
	}

	public List<String> excludedPackages() {
		return excluded;
	}

	public Map<String, Type> types() {
		return types;
	}

	public Type type(String name) {
		return types.get(name);
	}

	/** What names this manifest: its version and which jar it came from, in case a build keeps its version number. */
	public String id() {
		return idOf(version, jarSha256);
	}

	public static String idOf(String version, String jarSha256) {
		return version + "-" + jarSha256.substring(0, 16);
	}

	public String fileName() {
		return "SongsOfSyx-" + id() + FILE_SUFFIX;
	}

	/** The class file version most classes have, which says what the game was compiled for. */
	public int dominantClassVersion() {
		int best = 0;
		int bestCount = -1;
		for (Map.Entry<Integer, Integer> entry : classVersions.entrySet()) {
			if (entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
			}
		}
		return best;
	}

	/** Positive if this version comes after the other. */
	public int compareVersion(GameManifest other) {
		int byMajor = Integer.compare(major, other.major);
		return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
	}

	static String fieldKey(String name, String descriptor) {
		return name + ":" + descriptor;
	}

	static String methodKey(String name, String descriptor) {
		return name + descriptor;
	}

	/** Sorted, with no timestamps or paths, so one jar always gives the same bytes. */
	public byte[] toBytes() throws IOException {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schema", SCHEMA);
		root.put("hash_method", hashMethod);
		root.put("game_version", version);
		root.put("major", major);
		root.put("minor", minor);
		root.put("jar_sha256", jarSha256);
		Map<String, Object> versions = new TreeMap<>();
		classVersions.forEach((key, value) -> versions.put(String.valueOf(key), value));
		root.put("class_versions", versions);
		root.put("excluded_packages", excluded.stream().sorted().toList());
		Map<String, Object> classes = new TreeMap<>();
		for (Type type : types.values()) {
			classes.put(type.name(), typeToJson(type));
		}
		root.put("classes", classes);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			gzip.write(JsonText.write(root).getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

	private static Map<String, Object> typeToJson(Type type) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("access", type.access());
		if (type.superName() != null) {
			out.put("super", type.superName());
		}
		out.put("interfaces", type.interfaces().stream().sorted().toList());
		if (type.shape() != null) {
			out.put("shape", type.shape());
		}
		Map<String, Object> fields = new TreeMap<>();
		type.fields().forEach((key, field) -> fields.put(key, member(field.access(), field.constant())));
		out.put("fields", fields);
		Map<String, Object> methods = new TreeMap<>();
		type.methods().forEach((key, method) -> methods.put(key, member(method.access(), method.body())));
		out.put("methods", methods);
		if (!type.uses().isEmpty()) {
			out.put("uses", new TreeMap<>(type.uses()));
		}
		return out;
	}

	private static List<Object> member(int access, String hash) {
		return hash == null ? List.of(access) : List.of(access, hash);
	}

	/** Reads and checks a file, which may have come from anyone. */
	public static GameManifest fromBytes(byte[] bytes) throws ManifestException {
		String text;
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
			byte[] unpacked = in.readNBytes(MAX_UNPACKED + 1);
			if (unpacked.length > MAX_UNPACKED) {
				throw new ManifestException("It's too big to be a manifest.");
			}
			text = new String(unpacked, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new ManifestException("It isn't a gzipped manifest.");
		}
		try {
			return fromJson(JsonText.parse(text));
		} catch (IllegalStateException | NumberFormatException | ClassCastException | NullPointerException e) {
			throw new ManifestException("It isn't a valid manifest: " + e.getMessage());
		}
	}

	private static GameManifest fromJson(Object parsed) throws ManifestException {
		Map<?, ?> root = (Map<?, ?>) parsed;
		onlyKeys(root, TOP_KEYS, "the file");
		for (String required : TOP_KEYS) {
			require(root.containsKey(required), "It has no " + required + ".");
		}
		int schema = integer(root.get("schema"));
		require(schema == SCHEMA, "It uses manifest format " + schema + ", and this toolkit reads format " + SCHEMA + ". A manifest made by this toolkit replaces it, and the old file can be deleted.");
		String version = (String) root.get("game_version");
		require(VERSION.matcher(version).matches(), "Its game version isn't valid.");
		String jar = (String) root.get("jar_sha256");
		require(JAR_HASH.matcher(jar).matches(), "Its jar hash isn't valid.");
		Map<Integer, Integer> versions = new TreeMap<>();
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) root.get("class_versions")).entrySet()) {
			versions.put(Integer.valueOf((String) entry.getKey()), integer(entry.getValue()));
		}
		List<String> excluded = new ArrayList<>();
		for (Object item : (List<?>) root.get("excluded_packages")) {
			excluded.add(name((String) item));
		}
		Map<String, Type> types = new TreeMap<>();
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) root.get("classes")).entrySet()) {
			String typeName = name((String) entry.getKey());
			types.put(typeName, typeFromJson(typeName, (Map<?, ?>) entry.getValue()));
		}
		return new GameManifest(version, integer(root.get("major")), integer(root.get("minor")), jar, integer(root.get("hash_method")), versions, excluded, types);
	}

	private static Type typeFromJson(String typeName, Map<?, ?> json) throws ManifestException {
		onlyKeys(json, TYPE_KEYS, typeName);
		String superName = json.get("super") == null ? null : name((String) json.get("super"));
		List<String> interfaces = new ArrayList<>();
		for (Object item : (List<?>) json.get("interfaces")) {
			interfaces.add(name((String) item));
		}
		String shape = json.get("shape") == null ? null : hash(json.get("shape"));
		Map<String, Field> fields = new TreeMap<>();
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) json.get("fields")).entrySet()) {
			List<?> member = memberList(entry.getValue());
			fields.put(name((String) entry.getKey()), new Field(integer(member.get(0)), member.size() > 1 ? hash(member.get(1)) : null));
		}
		Map<String, Method> methods = new TreeMap<>();
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) json.get("methods")).entrySet()) {
			List<?> member = memberList(entry.getValue());
			methods.put(name((String) entry.getKey()), new Method(integer(member.get(0)), member.size() > 1 ? hash(member.get(1)) : null));
		}
		Map<String, List<String>> uses = new TreeMap<>();
		if (json.get("uses") != null) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) json.get("uses")).entrySet()) {
				List<String> members = new ArrayList<>();
				for (Object member : (List<?>) entry.getValue()) {
					members.add(name((String) member));
				}
				uses.put(name((String) entry.getKey()), members);
			}
		}
		return new Type(typeName, integer(json.get("access")), superName, interfaces, shape, fields, methods, uses);
	}

	private static List<?> memberList(Object value) throws ManifestException {
		List<?> member = (List<?>) value;
		require(member.size() == 1 || member.size() == 2, "A member holds something it shouldn't.");
		return member;
	}

	private static void onlyKeys(Map<?, ?> map, Set<String> allowed, String where) throws ManifestException {
		for (Object key : map.keySet()) {
			require(allowed.contains(key), where + " holds a field a manifest doesn't have: " + key);
		}
	}

	private static String name(String value) throws ManifestException {
		require(value.length() <= MAX_NAME && NAME.matcher(value).matches(), "It holds a name that isn't valid.");
		return value;
	}

	private static String hash(Object value) throws ManifestException {
		String text = (String) value;
		require(HASH.matcher(text).matches(), "It holds a hash that isn't valid.");
		return text;
	}

	private static int integer(Object value) {
		return ((Number) value).intValue();
	}

	private static void require(boolean condition, String problem) throws ManifestException {
		if (!condition) {
			throw new ManifestException(problem);
		}
	}
}
