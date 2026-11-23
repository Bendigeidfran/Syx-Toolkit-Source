package syx.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import syx.core.AssetCatalog;
import syx.core.AssetForm;
import syx.core.AssetSchema;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookCheck;
import syx.core.HandbookIndex;
import syx.core.HandbookIndex.Section;
import syx.core.HandbookIndex.TagHelp;
import syx.core.HandbookStore;
import syx.core.TagSpec;

/**
 * Tests reading the handbook, keeping a copy, and comparing it with the jar scan. The fixtures are
 * written here. The checks against the real handbook skip when offline, and the checks against the
 * scan skip without a game folder.
 * Usage: HandbookTest [game folder]
 */
public final class HandbookTest {

	private static final String ANIMAL = "assets/init/animal/";
	private static final String RESOURCE = "assets/init/resource/";
	private static final GameParser.GameVersion V71 = new GameParser.GameVersion(71, 44);
	private static final GameParser.GameVersion V72 = new GameParser.GameVersion(72, 1);

	private static int passed;
	private static int failed;

	private HandbookTest() {
	}

	public static void main(String[] args) throws Exception {
		Path temp = Files.createTempDirectory("syx-handbook-test");
		try {
			parsing();
			malformed();
			store(temp);
			handbookKeys();
			sharedEntries();
			Path game = args.length > 0 ? Paths.get(args[0]) : null;
			plantedDisagreement(game);
			if (game != null) {
				mergedLocations(game);
				specificBiography(game);
			}
			realHandbook(game, temp);
		} finally {
			try (Stream<Path> walk = Files.walk(temp)) {
				for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static String quoted(String text) {
		return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
	}

	private static String tag(String description, String example, String required, String range, String effect, boolean unverified, String url) {
		return "{\"summary\":\"unused\",\"description\":" + quoted(description) + ",\"type\":null,\"range\":" + quoted(range) + ",\"required\":" + quoted(required)
				+ ",\"default\":null,\"effect\":" + quoted(effect) + ",\"unverified\":" + unverified + ",\"example\":" + quoted(example) + ",\"url\":" + quoted(url) + "}";
	}

	private static final String PAGE = HandbookIndex.SITE + "compendium/01-animal/";

	/** A small handbook in the same form as the real file. Its hash follows the version unless given. */
	private static String fixture(String version) {
		return fixture(version, "hash-" + version, "How heavy it is.");
	}

	private static String fixture(String version, String hash, String massWords) {
		return "{\"schema\":1,\"game_version\":" + quoted(version) + ",\"content_hash\":" + quoted(hash) + ",\"future_field\":[1,2.5,true,null],\"locations\":{"
				+ "\"assets/init/animal/\":{\"tags\":{"
				+ "\"MASS\":" + tag(massWords + "\n\nSee `SPEED`.", "MASS: 110,", "Yes", "1 or more. The maximum of 500 isn't enforced.", null, false, PAGE + "#mass") + ","
				+ "\"GRAZES\":" + tag("Whether it grazes.", null, "Yes. The tag must be present.", null, "None", false, PAGE + "#grazes") + ","
				+ "\"OLD_TAG\":" + tag("Old.", null, "No", null, "Unread", false, PAGE + "#old") + ","
				+ "\"NOT_REAL\":" + tag("Not in the game.", null, null, null, null, false, PAGE + "#notreal") + ","
				+ "\"NAME\":" + tag("A data tag that shares a text tag's name.", null, "No", null, null, false, PAGE + "#name") + ","
				+ "\"NO_WORDS\":" + tag(null, null, null, null, "Sometimes", false, null) + ","
				+ "\"TODO_TAG\":" + tag("TODO: Not confirmed.", null, "Yes, if the `WIKI` block is present.", null, null, true, "https://example.com/phish") + ","
				+ "\"BOTH\":" + tag("Both.", null, "`NAME` yes; `NAMES` no", null, null, false, HandbookIndex.SITE + "../../elsewhere") + ","
				+ "\"CAUSE\":" + tag("Cause.", null, "Yes, for every cause below", null, null, false, "http://bendigeidfran.github.io/Syx-Modding-Handbook/x") + ","
				+ "\"SOFT\":" + tag("Soft.", null, "No, but if it's missing it never spawns.", null, null, false, "not a url with spaces")
				+ "},\"text_tags\":{"
				+ "\"NAME\":" + tag("The singular name.", null, "Yes", null, null, false, PAGE + "#name-2") + ","
				+ "\"WIKI.CATEGORIES\":" + tag("Never read.", null, "No", null, "Unread", false, PAGE + "#wiki")
				+ "}},"
				+ "\"assets/init/config/Battle.txt\":{\"tags\":{\"MASS\":" + tag("A different file's tag.", null, null, null, null, false, PAGE + "#battle") + "}},"
				+ "\"assets/init/nothing/\":{}"
				+ "}}";
	}

	private static void parsing() throws Exception {
		HandbookIndex index = HandbookIndex.parse(fixture("0.71.44"));
		expect("the version is read", V71.equals(index.writtenFor()));
		expect("it has locations", index.hasLocations());
		expect("the content hash is read", "hash-0.71.44".equals(index.contentHash()));

		TagHelp mass = index.tag(ANIMAL, Section.TAGS, "MASS");
		expect("a tag is found", mass != null);
		expect("paragraphs and backticks are kept: " + mass.description(), mass.description().equals("How heavy it is.\n\nSee `SPEED`."));
		expect("the example is read", "MASS: 110,".equals(mass.example()));
		expect("the summary is read", "unused".equals(mass.summary()));
		String noSummary = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"a\":{\"tags\":{"
				+ "\"T\":{\"description\":\"One.\\n\\nTwo.\",\"summary\":null},\"U\":{\"description\":\"Only.\"},\"V\":{}}}}}";
		HandbookIndex fallback = HandbookIndex.parse(noSummary);
		expect("no summary falls back on the first paragraph", "One.".equals(fallback.tag("a", Section.TAGS, "T").summary()));
		expect("one paragraph falls back on all of it", "Only.".equals(fallback.tag("a", Section.TAGS, "U").summary()));
		expect("no description gives an empty summary", fallback.tag("a", Section.TAGS, "V").summary().isEmpty());
		expect("the link goes to the tag", mass.url().equals(PAGE + "#mass"));
		expect("Required is read", Boolean.TRUE.equals(mass.required()));
		expect("Range is read: " + mass.range(), "1 or more. The maximum of 500 isn't enforced.".equals(mass.range()));
		expect("no effect value means no effect is claimed", mass.effect() == null);
		expect("it isn't marked unverified", !mass.unverified());

		TagHelp grazes = index.tag(ANIMAL, Section.TAGS, "GRAZES");
		expect("Effect None is read", grazes.effect() == HandbookIndex.Effect.NONE);
		expect("Required with more text is read", Boolean.TRUE.equals(grazes.required()));
		expect("no example reads as null", grazes.example() == null);
		expect("Effect Unread is read", index.tag(ANIMAL, Section.TAGS, "OLD_TAG").effect() == HandbookIndex.Effect.UNREAD);
		expect("Required No is read", Boolean.FALSE.equals(index.tag(ANIMAL, Section.TAGS, "OLD_TAG").required()));

		TagHelp noWords = index.tag(ANIMAL, Section.TAGS, "NO_WORDS");
		expect("no description reads as empty", noWords.description().isEmpty());
		expect("null fields mean not stated", noWords.required() == null && noWords.range() == null && noWords.url() == null);
		expect("an unknown effect makes no claim", noWords.effect() == null);

		TagHelp todo = index.tag(ANIMAL, Section.TAGS, "TODO_TAG");
		expect("unverified is read", todo.unverified());
		expect("a conditional yes isn't a plain yes", todo.required() == null);
		expect("a link to another site is dropped", todo.url() == null);
		expect("a mixed Required value isn't read as either", index.tag(ANIMAL, Section.TAGS, "BOTH").required() == null);
		expect("a link that climbs out of the site is dropped", index.tag(ANIMAL, Section.TAGS, "BOTH").url() == null);
		expect("Yes with a qualifier is still yes", Boolean.TRUE.equals(index.tag(ANIMAL, Section.TAGS, "CAUSE").required()));
		expect("a link that isn't HTTPS is dropped", index.tag(ANIMAL, Section.TAGS, "CAUSE").url() == null);
		expect("No with a warning is still no", Boolean.FALSE.equals(index.tag(ANIMAL, Section.TAGS, "SOFT").required()));
		expect("a link that can't be parsed is dropped", index.tag(ANIMAL, Section.TAGS, "SOFT").url() == null);

		expect("a data tag comes from tags", index.tag(ANIMAL, Section.TAGS, "NAME").description().startsWith("A data tag"));
		expect("a text tag comes from text_tags", index.tag(ANIMAL, Section.TEXT_FILE, "NAME").description().equals("The singular name."));
		expect("a text tag isn't found in tags", index.tag(ANIMAL, Section.TAGS, "WIKI.CATEGORIES") == null);
		expect("an unknown tag gives null", index.tag(ANIMAL, Section.TAGS, "NOPE") == null);
		expect("an unknown file gives null", index.tag("assets/init/nope/", Section.TAGS, "MASS") == null);
		expect("a location with no sections gives null", index.tag("assets/init/nothing/", Section.TAGS, "MASS") == null);
		expect("a location can name a file", index.tag("assets/init/config/Battle.txt", Section.TAGS, "MASS").description().startsWith("A different"));
		expect("entries are listed: " + index.headings(ANIMAL, Section.TEXT_FILE), index.headings(ANIMAL, Section.TEXT_FILE).equals(List.of("NAME", "WIKI.CATEGORIES")));

		// A WIKI block's inner tags are described once, and only those fall back on that entry.
		String shared = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{"
				+ "\"assets/init/animal/\":{\"text_tags\":{\"WIKI.NAME\":" + tag("Shared words.", null, null, null, null, false, PAGE + "#w") + ",\"DESC\":" + tag("An animal's.", null, null, null, null, false, PAGE + "#d") + "}},"
				+ "\"assets/init/other/\":{\"text_tags\":{\"WIKI.TEXT\":" + tag("Its own words.", null, null, null, null, false, PAGE + "#o") + "}}}}";
		HandbookIndex withShared = HandbookIndex.parse(shared);
		expect("a WIKI tag falls back on the shared entry", withShared.description("assets/init/other/", Section.TEXT_FILE, "WIKI.NAME").description().equals("Shared words."));
		expect("a file's own entry wins", withShared.description("assets/init/other/", Section.TEXT_FILE, "WIKI.TEXT").description().equals("Its own words."));
		expect("other tags don't fall back", withShared.description("assets/init/other/", Section.TEXT_FILE, "DESC") == null);
		expect("a data tag doesn't fall back", withShared.description("assets/init/other/", Section.TAGS, "WIKI.NAME") == null);
		expect("the comparison doesn't use the fallback", withShared.tag("assets/init/other/", Section.TEXT_FILE, "WIKI.NAME") == null);

		expect("no version reads as unknown", HandbookIndex.parse(fixture(null)).writtenFor() == null);
		expect("a version that can't be read counts as unknown", HandbookIndex.parse(fixture("soon")).writtenFor() == null);
		expect("a version without the leading part is read", V71.equals(HandbookIndex.parse(fixture("71.44")).writtenFor()));
	}

	private static void malformed() {
		String good = fixture("0.71.44");
		String shell = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":";
		for (String bad : List.of("", "{", "[]", "{}", "<html>Not found</html>", good.substring(0, good.length() / 2), good + "extra",
				good.replace("\"schema\":1,", ""), good.replace("\"schema\":1,", "\"schema\":\"1\","),
				good.replace("\"content_hash\":\"hash-0.71.44\",", ""), good.replace("\"hash-0.71.44\"", "\"  \""),
				shell + "3}", shell + "{\"a\":3}}", shell + "{\"a\":{\"tags\":3}}}", shell + "{\"a\":{\"tags\":{\"T\":3}}}}",
				shell + "{\"a\":{\"tags\":{\"T\":{\"description\":3}}}}}", shell + "{\"a\":{\"tags\":{\"T\":{\"unverified\":\"yes\"}}}}}",
				shell + "{\"a\":{\"text_tags\":{\"T\":{\"required\":true}}}}}", shell + "{},}", shell + "{\"a\":\"\\q\"}}")) {
			try {
				HandbookIndex.parse(bad);
				expect("should be refused: " + bad.substring(0, Math.min(60, bad.length())), false);
			} catch (HandbookIndex.ParseException e) {
				expect("malformed input is refused", !(e instanceof HandbookIndex.UnsupportedSchemaException));
			}
		}
		try {
			HandbookIndex.parse("{\"schema\":2,\"something\":\"entirely different\"}");
			expect("a newer format should be refused", false);
		} catch (HandbookIndex.ParseException e) {
			expect("a newer format is reported as such, whatever else it holds", e instanceof HandbookIndex.UnsupportedSchemaException);
		}
	}

	/** A stand-in for the site, which records what it was asked. */
	private static final class FakeSite implements HandbookStore.Fetcher {
		final List<String> sent = new ArrayList<>();
		HandbookStore.Response reply;
		IOException failure;

		@Override
		public HandbookStore.Response fetch(String etag) throws IOException {
			sent.add(etag);
			if (failure != null) {
				throw failure;
			}
			return reply;
		}

		String lastSent() {
			return sent.get(sent.size() - 1);
		}
	}

	private static HandbookStore.Response ok(String version, String etag) {
		return new HandbookStore.Response(200, etag, fixture(version).getBytes(StandardCharsets.UTF_8));
	}

	private static HandbookStore storeIn(Path folder, FakeSite site) {
		return new HandbookStore(folder, site);
	}

	private static void store(Path temp) throws Exception {
		// Nothing at all: no descriptions, and no crash.
		FakeSite site = new FakeSite();
		Path empty = temp.resolve("empty");
		HandbookStore none = storeIn(empty, site);
		expect("no copies means no index", none.load(V71).index() == null);
		expect("no copies has no version note", none.load(V71).versionNote(V71) == null);

		// An unknown game version asks for nothing.
		expect("an unknown game version downloads nothing", none.refresh(null) == HandbookStore.Outcome.SKIPPED && site.sent.isEmpty());

		// A good download is kept, with its ETag.
		Path folder = temp.resolve("cache");
		HandbookStore store = storeIn(folder, site);
		site.reply = ok("0.71.44", "\"one\"");
		expect("a good first download is kept", store.refresh(V71) == HandbookStore.Outcome.UPDATED);
		expect("the first request has no ETag", site.lastSent() == null);
		expect("the copy is used", store.load(V71).index() != null && store.load(V71).matchesGame());
		expect("a matching copy has no version note", store.load(V71).versionNote(V71) == null);
		expect("no temp files are left", onlyExpectedFiles(folder));

		// An unchanged handbook isn't downloaded again.
		byte[] before = Files.readAllBytes(folder.resolve("tags.json"));
		site.reply = new HandbookStore.Response(304, "\"one\"", new byte[0]);
		expect("an unchanged handbook is a 304", store.refresh(V71) == HandbookStore.Outcome.NOT_MODIFIED);
		expect("the saved ETag was sent", "\"one\"".equals(site.lastSent()));
		expect("a 304 changes nothing", java.util.Arrays.equals(before, Files.readAllBytes(folder.resolve("tags.json"))));

		// The site can publish identical content under a new ETag: nothing is replaced, but the ETag is kept.
		site.reply = ok("0.71.44", "\"republished\"");
		expect("the same content under a new ETag counts as unchanged", store.refresh(V71) == HandbookStore.Outcome.NOT_MODIFIED);
		expect("the file isn't rewritten", java.util.Arrays.equals(before, Files.readAllBytes(folder.resolve("tags.json"))));
		site.reply = new HandbookStore.Response(304, "\"republished\"", new byte[0]);
		store.refresh(V71);
		expect("the new ETag is sent next time, so it isn't downloaded again", "\"republished\"".equals(site.lastSent()));

		// New content for this game replaces it.
		site.reply = new HandbookStore.Response(200, "\"two\"", fixture("0.71.44", "changed", "Better words.").getBytes(StandardCharsets.UTF_8));
		expect("new content is kept", store.refresh(V71) == HandbookStore.Outcome.UPDATED);
		expect("and is what's shown", store.load(V71).index().tag(ANIMAL, Section.TAGS, "MASS").description().startsWith("Better words."));
		site.reply = new HandbookStore.Response(304, "\"two\"", new byte[0]);
		store.refresh(V71);
		expect("its ETag is sent next time", "\"two\"".equals(site.lastSent()));

		// A handbook for a different game is refused, and the existing copy stays.
		before = Files.readAllBytes(folder.resolve("tags.json"));
		site.reply = ok("0.72.1", "\"future\"");
		expect("a copy for another game is refused", store.refresh(V71) == HandbookStore.Outcome.WRONG_GAME_VERSION);
		expect("the existing copy stays", java.util.Arrays.equals(before, Files.readAllBytes(folder.resolve("tags.json"))));
		expect("the copy in use still matches", V71.equals(store.load(V71).index().writtenFor()));

		// It isn't fetched again while the player's game is the same.
		site.reply = new HandbookStore.Response(304, "\"future\"", new byte[0]);
		expect("the rejected copy isn't downloaded again", store.refresh(V71) == HandbookStore.Outcome.NOT_MODIFIED);
		expect("the rejected ETag was sent", "\"future\"".equals(site.lastSent()));

		// Once the player's game changes, the same copy is looked at again, and now fits.
		site.reply = ok("0.72.1", "\"future\"");
		expect("after a game update, that copy is accepted", store.refresh(V72) == HandbookStore.Outcome.UPDATED);
		expect("the old ETag was sent, not the rejected one", "\"two\"".equals(site.sent.get(site.sent.size() - 1)));
		expect("the new copy is in use", store.load(V72).matchesGame());
		expect("the note says when the copy doesn't fit: " + store.load(V71).versionNote(V71), store.load(V71).versionNote(V71).contains("0.72.1") && store.load(V71).versionNote(V71).contains("0.71.44"));

		// A format this toolkit can't read: not used, and not fetched again until something changes.
		FakeSite formatSite = new FakeSite();
		HandbookStore formatStore = storeIn(temp.resolve("newer-format"), formatSite);
		formatSite.reply = new HandbookStore.Response(200, "\"v2\"", "{\"schema\":2}".getBytes(StandardCharsets.UTF_8));
		expect("a newer format is refused", formatStore.refresh(V71) == HandbookStore.Outcome.UNSUPPORTED_SCHEMA);
		expect("a newer format isn't kept", formatStore.load(V71).index() == null);
		formatSite.reply = new HandbookStore.Response(304, "\"v2\"", new byte[0]);
		expect("it isn't downloaded again", formatStore.refresh(V71) == HandbookStore.Outcome.NOT_MODIFIED && "\"v2\"".equals(formatSite.lastSent()));
		formatStore.refresh(V72);
		expect("a game update looks again", formatSite.lastSent() == null);

		// Things that can't be used leave the copy alone.
		before = Files.readAllBytes(folder.resolve("tags.json"));
		site.reply = new HandbookStore.Response(200, "\"bad\"", "{ not json".getBytes(StandardCharsets.UTF_8));
		expect("bad JSON is refused", store.refresh(V72) == HandbookStore.Outcome.INVALID);
		site.reply = new HandbookStore.Response(200, "\"nopages\"", "{\"schema\":1,\"game_version\":\"0.72.1\",\"content_hash\":\"h\",\"locations\":{}}".getBytes(StandardCharsets.UTF_8));
		expect("a file with no locations is refused", store.refresh(V72) == HandbookStore.Outcome.INVALID);
		site.reply = new HandbookStore.Response(404, null, new byte[0]);
		expect("a 404 is refused", store.refresh(V72) == HandbookStore.Outcome.INVALID);
		site.reply = new HandbookStore.Response(200, "\"big\"", new byte[HandbookStore.MAX_BYTES + 1]);
		expect("an oversized reply is refused", store.refresh(V72) == HandbookStore.Outcome.INVALID);
		site.reply = ok(null, "\"noversion\"");
		expect("a handbook with no readable version counts as not matching", store.refresh(V72) == HandbookStore.Outcome.WRONG_GAME_VERSION);
		site.reply = null;
		site.failure = new IOException("no connection");
		expect("no connection is only a failed refresh", store.refresh(V72) == HandbookStore.Outcome.FAILED);
		site.failure = null;
		expect("none of those touched the copy", java.util.Arrays.equals(before, Files.readAllBytes(folder.resolve("tags.json"))));
		expect("no temp files are left afterwards", onlyExpectedFiles(folder));

		// A damaged cache is ignored, and replaced at the next chance.
		Path damaged = temp.resolve("damaged");
		Files.createDirectories(damaged);
		Files.writeString(damaged.resolve("tags.json"), "damaged");
		Files.writeString(damaged.resolve("handbook.properties"), "etag=\"stale\"\n");
		FakeSite damagedSite = new FakeSite();
		HandbookStore damagedStore = storeIn(damaged, damagedSite);
		expect("a damaged cache means no copy", damagedStore.load(V71).index() == null);
		damagedSite.reply = ok("0.71.44", "\"fresh\"");
		expect("a damaged cache is replaced", damagedStore.refresh(V71) == HandbookStore.Outcome.UPDATED);
		expect("its ETag isn't sent, so a whole copy comes back", damagedSite.lastSent() == null);
		expect("the new copy is used", damagedStore.load(V71).matchesGame());

		// A first run for a game the handbook doesn't support yet keeps nothing.
		Path firstRun = temp.resolve("first-run");
		FakeSite firstSite = new FakeSite();
		firstSite.reply = ok("0.72.1", "\"future\"");
		HandbookStore firstStore = storeIn(firstRun, firstSite);
		expect("a first download for another game is refused", firstStore.refresh(V71) == HandbookStore.Outcome.WRONG_GAME_VERSION);
		expect("so there's no copy", firstStore.load(V71).index() == null);

		// A copy with no version is marked. A download can't get here, but a file on disk can.
		Path noVersion = temp.resolve("nv");
		Files.createDirectories(noVersion);
		Files.writeString(noVersion.resolve("tags.json"), fixture(null));
		HandbookStore noVersionStore = storeIn(noVersion, new FakeSite());
		expect("a copy with no version is marked unknown: " + noVersionStore.load(V71).versionNote(V71), noVersionStore.load(V71).versionNote(V71).contains("unknown"));
	}

	private static boolean onlyExpectedFiles(Path folder) throws IOException {
		try (Stream<Path> files = Files.list(folder)) {
			return files.allMatch(p -> p.getFileName().toString().equals("tags.json") || p.getFileName().toString().equals("handbook.properties"));
		}
	}

	private static void plantedDisagreement(Path game) throws Exception {
		if (game == null) {
			System.out.println("SKIPPED: comparing with the scan needs a game folder.");
			return;
		}
		AssetSchema schema = animalSchema(game);
		List<HandbookCheck.Finding> findings = HandbookCheck.compare(schema, ANIMAL, HandbookIndex.parse(fixture("0.71.44")));
		// In the fixture: MASS and GRAZES are right, and the rest are planted.
		expect("an entry the game doesn't read is reported: " + findings, findings.stream().anyMatch(f -> f.tag().equals("NOT_REAL") && f.kind() == HandbookCheck.Kind.DISAGREES));
		expect("an entry marked Unread agrees with the scan not finding it", findings.stream().noneMatch(f -> f.tag().equals("OLD_TAG") || f.tag().equals("WIKI.CATEGORIES")));
		expect("a matching tag isn't reported", findings.stream().noneMatch(f -> f.tag().equals("GRAZES")));
		expect("a matching range isn't reported", findings.stream().noneMatch(f -> f.tag().equals("MASS")));
		expect("a missing tag is only a gap", findings.stream().anyMatch(f -> f.tag().equals("SPEED") && f.kind() == HandbookCheck.Kind.NOT_DOCUMENTED));
		String wrongRequired = fixture("0.71.44").replace("Yes. The tag must be present.", "No.");
		expect("a wrong Required is reported", HandbookCheck.compare(schema, ANIMAL, HandbookIndex.parse(wrongRequired)).stream()
				.anyMatch(f -> f.tag().equals("GRAZES") && f.kind() == HandbookCheck.Kind.DISAGREES));
		String unstatedRequired = fixture("0.71.44").replace("\"Yes. The tag must be present.\"", "null");
		expect("an unstated Required is never a disagreement", HandbookCheck.compare(schema, ANIMAL, HandbookIndex.parse(unstatedRequired)).stream()
				.noneMatch(f -> f.tag().equals("GRAZES") && f.kind() == HandbookCheck.Kind.DISAGREES));
		String wrongRange = fixture("0.71.44").replace("The maximum of 500", "The maximum of 5000");
		expect("a wrong Range is reported", HandbookCheck.compare(schema, ANIMAL, HandbookIndex.parse(wrongRange)).stream()
				.anyMatch(f -> f.tag().equals("MASS") && f.kind() == HandbookCheck.Kind.DISAGREES));
		String unstatedRange = fixture("0.71.44").replace("\"1 or more. The maximum of 500 isn't enforced.\"", "null");
		expect("an unstated Range is never a disagreement", HandbookCheck.compare(schema, ANIMAL, HandbookIndex.parse(unstatedRange)).stream()
				.noneMatch(f -> f.tag().equals("MASS") && f.kind() == HandbookCheck.Kind.DISAGREES));
		// "Below" is read as a limit for a whole number, and only when the number is right.
		AssetSchema resource = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals("Resource")).findFirst().orElseThrow().schema();
		for (String[] range : new String[][] { { "0 or more. Stay below 10.", "agree" }, { "0 to 9", "agree" }, { "0 or more. Stay below 11.", "differ" }, { "0 or more. Stay below 100.", "differ" }, { "0 or more", "differ" } }) {
			String json = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"" + RESOURCE + "\":{\"tags\":{\"CATEGORY_DEFAULT\":"
					+ tag("Words.", null, "Yes", range[0], null, false, PAGE + "#c") + "}}}}";
			boolean differs = HandbookCheck.compare(resource, RESOURCE, HandbookIndex.parse(json)).stream().anyMatch(f -> f.tag().equals("CATEGORY_DEFAULT") && f.kind() == HandbookCheck.Kind.DISAGREES);
			expect("'" + range[0] + "' should " + range[1] + " with a limit of 9", differs == range[1].equals("differ"));
		}
		TagSpec mass = schema.tag("MASS");
		expect("the scan's rule is still shown: " + schema.ruleText(mass), schema.ruleText(mass).contains("1 to 500"));
		expect("the sprite rule comes from the code: " + schema.ruleText(schema.tag("SPRITE")), schema.ruleText(schema.tag("SPRITE")).contains("164"));
	}

	private static TagSpec spec(String key, TagSpec.Kind kind, List<TagSpec> children) {
		return new TagSpec(key, kind, false, null, null, null, true, null, null, List.of(), "test", TagSpec.Confidence.CODE_VERIFIED, null, new TagSpec.Shape(children, List.of(), 0, 0, 0));
	}

	/** A companion file's tags and a keyed block's inner tags are found under the handbook's own names. */
	private static void handbookKeys() throws Exception {
		String own = "assets/init/race/";
		String sprite = "assets/init/race/sprite/";
		List<TagSpec> values = List.of(spec("VALUES", TagSpec.Kind.TEXT_LINE, List.of()));
		List<TagSpec> companionTags = List.of(spec("file.sprite.SLEEP_FILE", TagSpec.Kind.TEXT_LINE, List.of()), spec("file.sprite.COLORS", TagSpec.Kind.KEYED_BLOCKS, values),
				spec("file.sprite.TYPES", TagSpec.Kind.BLOCK_LIST, List.of(spec("SPRITE_FILE", TagSpec.Kind.TEXT_LINE, List.of()))));
		AssetSchema.Companion companion = new AssetSchema.Companion("sprite", "Appearance", "init/race/sprite", "SPRITE_FILE", true, "", companionTags, List.of(sprite));
		AssetSchema schema = new AssetSchema(List.of(spec("STATS", TagSpec.Kind.KEYED_BLOCKS, List.of(spec("PRIO", TagSpec.Kind.TEXT_LINE, List.of())))), List.of(), List.of(), List.of(),
				(entered, index, findings) -> { }, List.of(), null, List.of(companion), null);
		for (String[] pair : new String[][] { { "file.sprite.SLEEP_FILE", "SLEEP_FILE" }, { "file.sprite.COLORS", "COLORS" }, { "file.sprite.COLORS.SKIN.VALUES", "COLORS.VALUES" },
				{ "file.sprite.COLORS.VALUES.VALUES", "COLORS.VALUES" }, { "file.sprite.TYPES.2.SPRITE_FILE", "TYPES.SPRITE_FILE" }, { "file.sprite.TYPES.SPRITE_FILE", "TYPES.SPRITE_FILE" },
				{ "STATS.HUNGER.PRIO", "STATS.PRIO" }, { "UPGRADES.0.BOOST", "UPGRADES.BOOST" }, { "file.unknown.TAG", "file.unknown.TAG" },
				{ "file.sprite.COLORS.#.VALUES", "COLORS.VALUES" }, { "file.sprite.TYPES.#.SPRITE_FILE", "TYPES.SPRITE_FILE" }, { "STATS.0.PRIO", "STATS.PRIO" } }) {
			expect(pair[0] + " is " + pair[1] + " in the handbook, and was " + schema.handbookKey(pair[0]), schema.handbookKey(pair[0]).equals(pair[1]));
		}
		String json = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"" + sprite + "\":{\"tags\":{\"SLEEP_FILE\":" + tag("Sleeping.", null, "Yes", null, null, false, PAGE + "#s")
				+ ",\"COLORS.VALUES\":" + tag("Colours.", null, "Yes", null, null, false, PAGE + "#c") + ",\"TYPES.SPRITE_FILE\":" + tag("Body.", null, "Yes", null, null, false, PAGE + "#b") + "}},\"" + own
				+ "\":{\"tags\":{\"STATS.PRIO\":" + tag("Priority.", null, "No", null, null, false, PAGE + "#p") + "}}}}";
		HandbookIndex index = HandbookIndex.parse(json);
		expect("a companion's tag is found", AssetForm.help(index, schema, List.of(sprite), false, "file.sprite.SLEEP_FILE").description().equals("Sleeping."));
		expect("a companion's list tag is found", AssetForm.help(index, schema, List.of(sprite), false, "file.sprite.TYPES.1.SPRITE_FILE").description().equals("Body."));
		expect("a companion's keyed tag is found", AssetForm.help(index, schema, List.of(sprite), false, "file.sprite.COLORS.SKIN.VALUES").description().equals("Colours."));
		expect("the asset's own keyed tag is found", AssetForm.help(index, schema, List.of(own), false, "STATS.HUNGER.PRIO").description().equals("Priority."));
		expect("a companion's tag is only looked for in its own location", AssetForm.help(index, schema, List.of(own), false, "file.sprite.SLEEP_FILE") == null);
		expect("a tag with no entry isn't invented", AssetForm.help(index, schema, List.of(sprite), false, "file.sprite.NOTHING") == null);
		expect("no handbook means no words", AssetForm.help(null, schema, List.of(sprite), false, "file.sprite.SLEEP_FILE") == null);
	}

	/** Tags the game reads the same way are described once, and an exact entry always comes first. */
	private static void sharedEntries() throws Exception {
		String page = PAGE + "#x";
		String sprite = "assets/init/race/sprite/";
		String home = "assets/init/race/home/";
		String rooms = "assets/init/room/";
		String game = "assets/sprite/game/";
		String json = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"" + sprite + "\":{\"tags\":{\"TYPES.OCCURRENCE\":" + tag("Type.", null, "No", null, null, false, page)
				+ ",\"TYPES.COLOR_SKIN\":" + tag("Skin.", null, "Yes", null, null, false, page) + ",\"CHILD.OCCURRENCE\":" + tag("No effect on a child.", null, "No", null, "None", false, page) + "}},\"" + home
				+ "\":{\"tags\":{\"CITIZEN\":" + tag("Citizens.", null, "Yes", null, null, false, page) + ",\"BED_1x1_TOP\":" + tag("Bed.", null, null, null, null, false, page) + ",\"RESOURCES\":" + tag("Needs.", null, null, null, null, false, page)
				+ "}},\"" + game + "\":{\"tags\":{\"FRAMES\":" + tag("Frames.", null, "Yes", null, null, false, page) + ",\"OVERWRITE\":" + tag("Overwrite.", null, "No", null, null, false, page) + "}},\"" + rooms
				+ "\":{\"tags\":{\"ICON.BG\":" + tag("Background.", null, "Yes", null, null, false, page) + "}}}}";
		HandbookIndex index = HandbookIndex.parse(json);
		List<String> sprites = List.of(sprite);
		List<String> homes = List.of(home);
		List<String> room = List.of("assets/init/room/PASTURE_*", rooms);
		expect("a child's own entry wins", index.description(sprites, Section.TAGS, "CHILD.OCCURRENCE").description().startsWith("No effect"));
		expect("otherwise a child uses the type's", index.description(sprites, Section.TAGS, "CHILD.COLOR_SKIN").description().equals("Skin."));
		expect("a child's tag isn't invented", index.description(sprites, Section.TAGS, "CHILD.NOTHING") == null);
		expect("CHILD elsewhere isn't a race's child", index.description(homes, Section.TAGS, "CHILD.COLOR_SKIN") == null);
		expect("a home's class has its entry", index.description(homes, Section.TAGS, "CITIZEN").description().equals("Citizens."));
		expect("its furniture is described once", index.description(homes, Section.TAGS, "NOBLE.BED_1x1_TOP").description().equals("Bed."));
		expect("a sheet's tags are the shared ones", index.description(homes, Section.TAGS, "SLAVE.BED_1x1_TOP.FRAMES").description().equals("Frames."));
		expect("its resources are the home's", index.description(homes, Section.TAGS, "CITIZEN.BED_1x1_TOP.RESOURCES").description().equals("Needs."));
		expect("an overwrite's colour is the overwrite's", index.description(homes, Section.TAGS, "CITIZEN.BED_1x1_TOP.OVERWRITE.COLOR").description().equals("Overwrite."));
		expect("a colour's parts have no entry", index.description(homes, Section.TAGS, "CITIZEN.BED_1x1_TOP.OVERWRITE.COLOR.R") == null);
		expect("a class only applies to a home", index.description(sprites, Section.TAGS, "CITIZEN.BED_1x1_TOP") == null);
		expect("a recipe's icon is a room's", index.description(room, Section.TAGS, "INDUSTRIES.INDUSTRY.ICON.BG").description().equals("Background."));
		expect("and so is a farm's single one", index.description(room, Section.TAGS, "INDUSTRY.ICON.BG").description().equals("Background."));
		expect("only rooms have that", index.description(homes, Section.TAGS, "INDUSTRY.ICON.BG") == null);

		expect("a colour's part says so", AssetForm.withoutHelp("COLOR_CLOTHES.0.R").equals(AssetForm.COLOR_PART) && AssetForm.withoutHelp("file.sprite.COLORS.#.VALUES.#.B").equals(AssetForm.COLOR_PART));
		expect("a tag named R outside a list doesn't", AssetForm.withoutHelp("WORK.R").equals(AssetForm.NOT_DESCRIBED) && AssetForm.withoutHelp("SPEED").equals(AssetForm.NOT_DESCRIBED));

		for (String[] cell : new String[][] { { "Yes", "true" }, { "Yes, for every cause below", "true" }, { "Yes, for all of them", "true" }, { "No", "false" }, { "Yes, for bathhouses", null }, { "Yes, if there's a block", null }, { "Yes, for room types that have items", null }, { "Yes for bathhouses", null },
				{ "No, except for canteens", null }, { "No. Default `150`.", "false" } }) {
			String withCell = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"a\":{\"tags\":{\"T\":" + tag("Words.", null, cell[0], null, null, false, page) + "}}}}";
			Boolean required = HandbookIndex.parse(withCell).tag("a", Section.TAGS, "T").required();
			expect("'" + cell[0] + "' is read as " + cell[1] + ", and was " + required, cell[1] == null ? required == null : required == Boolean.parseBoolean(cell[1]));
		}
	}

	/** A specific biography is read like a biography, but what it leaves out comes from the biography. */
	private static void specificBiography(Path game) {
		AssetSchema race = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals("Race")).findFirst().orElseThrow().schema();
		expect("a biography's absent line is no lines", "No lines".equals(race.tag("file.bio.HAPPY").gameDefault()));
		expect("a specific biography's keeps the biography's", "The biography's own lines".equals(race.tag("file.bioSpecific.HAPPY").gameDefault()));
		expect("its own location is tried before the biography's", race.companion("bioSpecific").handbookLocations().equals(List.of("assets/text/race/bio/specific/", "assets/text/race/bio/")));
	}

	/** The type's own entry wins, and only its own location can report entries the table doesn't know. */
	private static void mergedLocations(Path game) throws Exception {
		String own = "assets/init/room/FARM_*";
		String shared = "assets/init/room/";
		String json = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"" + own + "\":{\"tags\":{\"INDOORS\":"
				+ tag("Own words.", null, "Yes", null, null, false, PAGE + "#o") + "}},\"" + shared + "\":{\"tags\":{\"INDOORS\":"
				+ tag("Shared words.", null, "No", null, null, false, PAGE + "#s") + ",\"MINI_COLOR\":" + tag("Shared colour.", null, "Yes", null, null, false, PAGE + "#c")
				+ ",\"SOMETHING_ELSE\":" + tag("Another room's tag.", null, "Yes", null, null, false, PAGE + "#e") + "}}}}";
		HandbookIndex index = HandbookIndex.parse(json);
		List<String> locations = List.of(own, shared);
		expect("the type's own entry wins", index.description(locations, Section.TAGS, "INDOORS").description().equals("Own words."));
		expect("a shared entry is used when it has none", index.description(locations, Section.TAGS, "MINI_COLOR").description().equals("Shared colour."));
		expect("an entry in neither isn't invented", index.description(locations, Section.TAGS, "NOTHING") == null);
		AssetSchema farm = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals("Farm")).findFirst().orElseThrow().schema();
		List<HandbookCheck.Finding> findings = HandbookCheck.compare(farm, locations, index);
		expect("the shared No loses to the type's Yes: " + findings, findings.stream().noneMatch(f -> f.tag().equals("INDOORS") && f.kind() == HandbookCheck.Kind.DISAGREES));
		expect("a shared entry for other rooms isn't reported: " + findings, findings.stream().noneMatch(f -> f.tag().equals("SOMETHING_ELSE")));
		for (String[] range : new String[][] { { "1 to 32 rows", "agree" }, { "1 to 31 rows", "differ" } }) {
			String rows = "{\"schema\":1,\"game_version\":\"0.71.44\",\"content_hash\":\"h\",\"locations\":{\"" + own + "\":{\"tags\":{\"MINI_COLOR_PATTERN\":"
					+ tag("Words.", null, "No", range[0], null, false, PAGE + "#p") + "}}}}";
			boolean differs = HandbookCheck.compare(farm, locations, HandbookIndex.parse(rows)).stream().anyMatch(f -> f.tag().equals("MINI_COLOR_PATTERN") && f.kind() == HandbookCheck.Kind.DISAGREES);
			expect("a row limit of '" + range[0] + "' should " + range[1] + " with the code's 32", differs == range[1].equals("differ"));
		}
	}

	private static AssetSchema animalSchema(Path game) {
		return AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals("Animal")).findFirst().orElseThrow().schema();
	}

	private static void realHandbook(Path game, Path temp) throws Exception {
		HandbookStore.Response response;
		try {
			response = HandbookStore.fetchFromSite(null);
		} catch (javax.net.ssl.SSLException e) {
			// A runtime that can't do HTTPS is a real failure, unlike being offline.
			expect("HTTPS works on this runtime: " + e, false);
			return;
		} catch (IOException e) {
			System.out.println("SKIPPED: the real handbook couldn't be reached: " + e);
			return;
		}
		expect("the real handbook replied: " + response.status(), response.status() == 200);
		HandbookIndex index = HandbookIndex.parse(new String(response.body(), StandardCharsets.UTF_8));
		expect("the real handbook has locations", index.hasLocations());
		expect("its version is read: " + index.writtenFor(), index.writtenFor() != null);
		expect("it has a content hash", !index.contentHash().isBlank());
		TagHelp grazes = index.tag(ANIMAL, Section.TAGS, "GRAZES");
		expect("the real GRAZES is found", grazes != null);
		if (grazes != null) {
			System.out.println("OBSERVED real GRAZES: " + grazes.description().replace('\n', ' ') + " | required=" + grazes.required() + " effect=" + grazes.effect() + " url=" + grazes.url());
			expect("the real GRAZES is marked as having no effect", grazes.effect() == HandbookIndex.Effect.NONE);
			expect("its link is kept", grazes.url() != null);
			expect("its example is read", grazes.example() != null && grazes.example().contains("GRAZES"));
		}
		expect("the real handbook has an ETag: " + response.etag(), response.etag() != null);
		HandbookStore.Response again = HandbookStore.fetchFromSite(response.etag());
		expect("asking again with the ETag downloads nothing: " + again.status(), again.status() == 304 && again.body().length == 0);

		// The whole round trip, through the store, against the real site.
		if (index.writtenFor() != null) {
			Path folder = temp.resolve("real");
			HandbookStore store = new HandbookStore(folder, HandbookStore::fetchFromSite);
			expect("the store keeps the real handbook", store.refresh(index.writtenFor()) == HandbookStore.Outcome.UPDATED);
			expect("and then finds nothing new", store.refresh(index.writtenFor()) == HandbookStore.Outcome.NOT_MODIFIED);
		}

		if (game != null) {
			for (String[] type : new String[][] { { "Animal", ANIMAL }, { "Resource", RESOURCE } }) {
				AssetSchema schema = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals(type[0])).findFirst().orElseThrow().schema();
				for (HandbookCheck.Finding finding : HandbookCheck.compare(schema, type[1], index)) {
					System.out.println("OBSERVED " + type[0] + " " + finding.kind() + " " + finding.tag() + ": " + finding.message());
					expect("the scan and handbook agree on " + type[0] + " " + finding.tag() + ": " + finding.message(), finding.kind() != HandbookCheck.Kind.DISAGREES);
				}
			}
			// Farms merge a shared location with their own, and crops have one location.
			for (String label : List.of("Farm", "Crop", "Mine", "Minable resource", "Refiner", "Workshop", "Pasture", "Fishery", "Orchard", "Hunter", "Temple", "Shrine", "Tavern", "Eatery", "Canteen", "Well", "Bath", "Lavatory", "Barber", "Physician", "Pleasure", "Market", "Stage", "Speaker", "Fight pit", "Grand arena", "Race")) {
				AssetCatalog.Entry entry = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals(label)).findFirst().orElseThrow();
				expect(label + " is available: " + entry.unavailableReason(), entry.available());
				for (HandbookCheck.Finding finding : HandbookCheck.compare(entry.schema(), entry.type().handbookLocations(), index)) {
					System.out.println("OBSERVED " + label + " " + finding.kind() + " " + finding.tag() + ": " + finding.message());
					expect("the table and handbook agree on " + label + " " + finding.tag() + ": " + finding.message(), finding.kind() != HandbookCheck.Kind.DISAGREES);
				}
			}
			AssetCatalog.Entry raceEntry = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(e -> e.type().label().equals("Race")).findFirst().orElseThrow();
			AssetSchema race = raceEntry.schema();
			List<String> raceSprite = race.companion("sprite").handbookLocations();
			expect("a race's sprite file finds its entry", AssetForm.help(index, race, raceSprite, false, "file.sprite.SLEEP_FILE") != null);
			expect("and so does a tag in its list of types", AssetForm.help(index, race, raceSprite, false, "file.sprite.TYPES.0.FACE.3.FRAMES") != null);
			expect("and a tag in a colour collection of any name", AssetForm.help(index, race, raceSprite, false, "file.sprite.COLORS.MY_OWN_NAME.VALUES") != null);
			expect("a race's own keyed tag finds its entry", AssetForm.help(index, race, raceEntry.type().handbookLocations(), false, "STATS.ANY_STAT.PRIO") != null);
			for (AssetSchema.Companion companion : race.companions()) {
				if (companion.id().equals("bio") || companion.id().equals("king") || companion.id().equals("raid") || companion.id().equals("tourist")) {
					TagSpec first = companion.tags().get(0);
					expect(companion.id() + "'s " + first.key() + " finds its entry", AssetForm.help(index, race, companion.handbookLocations(), false, first.key()) != null);
				}
			}
			List<String> fishery = List.of("assets/init/room/FISHERY_*", "assets/init/room/");
			TagHelp fisheryFloor = index.description(fishery, Section.TAGS, "FLOOR");
			expect("a fishery's own FLOOR entry wins, and says it does nothing", fisheryFloor != null && fisheryFloor.description().contains("ignores this tag"));
			List<String> mine = List.of("assets/init/room/MINE_*", "assets/init/room/");
			expect("a mine's own entry is found", index.description(mine, Section.TAGS, "MINABLE") != null);
			expect("a sprite's entry is found", index.description(mine, Section.TAGS, "SPRITES.WORK_1X1") != null);
			expect("a sprite's inner tags fall back on the sprite page", index.description(mine, Section.TAGS, "SPRITES.WORK_1X1.FPS") != null && index.description(mine, Section.TAGS, "SPRITES.WORK_1X1.FRAMES") != null);
			expect("the sprite page agrees that FRAMES is needed, and FPS isn't", Boolean.TRUE.equals(index.description(mine, Section.TAGS, "SPRITES.WORK_1X1.FRAMES").required()) && Boolean.FALSE.equals(index.description(mine, Section.TAGS, "SPRITES.WORK_1X1.FPS").required()));
			expect("the fallback doesn't reach other tags", index.description(mine, Section.TAGS, "SPRITES.WORK_1X1.NOTHING") == null && index.description(mine, Section.TAGS, "WORK.NOTHING") == null && index.description(mine, Section.TAGS, "WORK.FPS") == null);
			List<String> farm = List.of("assets/init/room/FARM_*", "assets/init/room/");
			expect("a farm's own entry is found", index.description(farm, Section.TAGS, "GROWABLE") != null);
			expect("a shared entry is found through the merge", index.description(farm, Section.TAGS, "MINI_COLOR") != null);
			expect("a nested entry is found", index.description(farm, Section.TAGS, "WORK.SHIFT_OFFSET") != null);
			expect("a text entry is found", index.description(farm, Section.TEXT_FILE, "WORK.TITLE") != null);
			expect("STATS' tags are now described", index.description(farm, Section.TEXT_FILE, "STATS.NAME") != null && index.description(farm, Section.TEXT_FILE, "STATS.DESC") != null);
			expect("a farm's WORK is required by its own entry", Boolean.TRUE.equals(index.description(farm, Section.TAGS, "WORK").required()));
			expect("and so is its OUT", Boolean.TRUE.equals(index.description(farm, Section.TAGS, "INDUSTRY.OUT").required()));
			expect("the crop's image entry is found", index.description(List.of("assets/init/resource/growable/"), Section.TAGS, "SPRITE.SPRITE") != null);
			expect("the handbook now describes Resource", index.tag(RESOURCE, Section.TAGS, "SPRITE_DEBRIS") != null);
			expect("Resource's WIKI tags find the shared entries", index.tag(RESOURCE, Section.TEXT_FILE, "WIKI.NAME") == null && index.description(RESOURCE, Section.TEXT_FILE, "WIKI.NAME") != null);
		}
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
