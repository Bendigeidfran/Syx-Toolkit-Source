package syx.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/**
 * Keeps a copy of the handbook's tag data, which is downloaded rather than shipped with the app because
 * it can quote the game's files. It's checked for each time the toolkit starts. A new handbook is
 * assumed to be better, but only if it supports the installed game version.
 * Nothing here ever interrupts the user: if the handbook can't be reached, the existing copy stays.
 */
public final class HandbookStore {

	/** Asks for the tag data. A null ETag means there's no copy to compare with. */
	public interface Fetcher {
		Response fetch(String etag) throws IOException;
	}

	/** A reply. The body is empty for a 304, which means nothing has changed. */
	public record Response(int status, String etag, byte[] body) {
	}

	/** What a refresh did. */
	public enum Outcome {
		/** A newer copy for this game version is now in use. */
		UPDATED,
		/** The handbook hasn't changed, so nothing was downloaded. */
		NOT_MODIFIED,
		/** The handbook supports a different game version, so the existing copy stays. */
		WRONG_GAME_VERSION,
		/** The handbook is in a newer format than this toolkit reads, so the existing copy stays. */
		UNSUPPORTED_SCHEMA,
		/** What came back couldn't be used, so the existing copy stays. */
		INVALID,
		/** The handbook couldn't be reached, so the existing copy stays. */
		FAILED,
		/** The game's version isn't known yet, so nothing was asked for. */
		SKIPPED
	}

	/** The copy to show. The data is null if there's no usable copy at all. */
	public record Loaded(HandbookIndex index, boolean matchesGame) {
		public static final Loaded NONE = new Loaded(null, false);

		/** What to tell the user if the descriptions may not fit their game, or null if they do. */
		public String versionNote(GameParser.GameVersion game) {
			if (index == null || matchesGame) {
				return null;
			}
			GameParser.GameVersion written = index.writtenFor();
			return "These descriptions are from a handbook written for "
					+ (written == null ? "an unknown game version" : "game version 0." + written.major() + "." + written.minor())
					+ ", and your game is 0." + game.major() + "." + game.minor() + ", so some may be out of date.";
		}
	}

	public static final int MAX_BYTES = 32 * 1024 * 1024;
	private static final String INDEX_FILE = "tags.json";
	private static final String INFO_FILE = "handbook.properties";
	private static final String ETAG = "etag";
	private static final String REJECTED_ETAG = "rejectedEtag";
	private static final String REJECTED_FOR = "rejectedForGame";

	private final Path folder;
	private final Fetcher fetcher;

	/** For tests and other front ends. */
	public HandbookStore(Path folder, Fetcher fetcher) {
		this.folder = folder;
		this.fetcher = fetcher;
	}

	/** The real store: the toolkit's data folder and the published site. */
	public static HandbookStore openDefault() {
		return new HandbookStore(ToolkitSettings.defaultFile().getParent().resolve("handbook"), HandbookStore::fetchFromSite);
	}

	/**
	 * The copy to show. A copy for another game version can be here after the player's game updates, in
	 * which case it's still shown, and {@link Loaded#versionNote} says so.
	 */
	public Loaded load(GameParser.GameVersion game) {
		HandbookIndex cached = read(cachedJson());
		return cached == null ? Loaded.NONE : new Loaded(cached, matches(cached, game));
	}

	/**
	 * Asks for a newer copy. An unchanged handbook isn't downloaded again, because the ETag of the
	 * copy kept here is sent with the request. The site's ETag can change when the content hasn't, so
	 * if a download has the same content hash as the cache, only the ETag is updated. A copy for another
	 * game version is never kept.
	 */
	public Outcome refresh(GameParser.GameVersion game) {
		if (game == null) {
			return Outcome.SKIPPED;
		}
		Properties info = readInfo();
		Response response;
		try {
			response = fetcher.fetch(etagToSend(info, game));
		} catch (IOException | RuntimeException e) {
			return Outcome.FAILED;
		}
		if (response.status() == 304) {
			return Outcome.NOT_MODIFIED;
		}
		if (response.status() != 200 || response.body() == null || response.body().length > MAX_BYTES) {
			return Outcome.INVALID;
		}
		String json = new String(response.body(), StandardCharsets.UTF_8);
		try {
			HandbookIndex index;
			try {
				index = HandbookIndex.parse(json);
			} catch (HandbookIndex.UnsupportedSchemaException e) {
				remember(info, response, game);
				return Outcome.UNSUPPORTED_SCHEMA;
			} catch (HandbookIndex.ParseException e) {
				return Outcome.INVALID;
			}
			if (!index.hasLocations()) {
				return Outcome.INVALID;
			}
			if (!matches(index, game)) {
				remember(info, response, game);
				return Outcome.WRONG_GAME_VERSION;
			}
			Properties updated = new Properties();
			if (response.etag() != null) {
				updated.setProperty(ETAG, response.etag());
			}
			HandbookIndex cached = read(cachedJson());
			boolean sameContent = cached != null && cached.contentHash().equals(index.contentHash());
			if (!sameContent) {
				Files.createDirectories(folder);
				replace(folder.resolve(INDEX_FILE), response.body());
			}
			writeInfo(updated);
			return sameContent ? Outcome.NOT_MODIFIED : Outcome.UPDATED;
		} catch (IOException e) {
			return Outcome.FAILED;
		}
	}

	/** So the same copy isn't fetched again until the player's game or this toolkit's reader changes. */
	private void remember(Properties info, Response response, GameParser.GameVersion game) throws IOException {
		if (response.etag() != null) {
			info.setProperty(REJECTED_ETAG, response.etag());
			info.setProperty(REJECTED_FOR, rejectedFor(game));
			writeInfo(info);
		}
	}

	private String etagToSend(Properties info, GameParser.GameVersion game) {
		String rejected = info.getProperty(REJECTED_ETAG);
		if (rejected != null && rejectedFor(game).equals(info.getProperty(REJECTED_FOR))) {
			return rejected;
		}
		return read(cachedJson()) == null ? null : info.getProperty(ETAG);
	}

	private static boolean matches(HandbookIndex index, GameParser.GameVersion game) {
		return game != null && game.equals(index.writtenFor());
	}

	private static String rejectedFor(GameParser.GameVersion game) {
		return game.major() + "." + game.minor() + " format " + HandbookIndex.SCHEMA;
	}

	private static HandbookIndex read(String json) {
		if (json == null) {
			return null;
		}
		try {
			return HandbookIndex.parse(json);
		} catch (HandbookIndex.ParseException e) {
			return null;
		}
	}

	private String cachedJson() {
		try {
			Path file = folder.resolve(INDEX_FILE);
			return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private Properties readInfo() {
		Properties info = new Properties();
		try (InputStream in = Files.newInputStream(folder.resolve(INFO_FILE))) {
			info.load(in);
		} catch (IOException | RuntimeException e) {
			return new Properties();
		}
		return info;
	}

	private void writeInfo(Properties info) throws IOException {
		Files.createDirectories(folder);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		info.store(bytes, null);
		replace(folder.resolve(INFO_FILE), bytes.toByteArray());
	}

	private void replace(Path file, byte[] content) throws IOException {
		Path temp = Files.createTempFile(folder, "handbook", ".tmp");
		try {
			Files.write(temp, content);
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/** The real download, from the fixed HTTPS address. It only ever reads. */
	public static Response fetchFromSite(String etag) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(HandbookIndex.TAGS_URL).toURL().openConnection();
		try {
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(20_000);
			connection.setRequestProperty("Accept-Encoding", "gzip");
			connection.setRequestProperty("User-Agent", "SyxToolkit");
			if (etag != null) {
				connection.setRequestProperty("If-None-Match", etag);
			}
			int status = connection.getResponseCode();
			String received = connection.getHeaderField("ETag");
			if (status != 200) {
				return new Response(status, received, new byte[0]);
			}
			try (InputStream raw = connection.getInputStream();
					InputStream in = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
				byte[] body = in.readNBytes(MAX_BYTES + 1);
				return new Response(status, received, body);
			}
		} finally {
			connection.disconnect();
		}
	}
}
