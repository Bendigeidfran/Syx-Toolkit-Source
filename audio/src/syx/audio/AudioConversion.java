package syx.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.github.axet.jvorbis.spi.file.EncoderFileFormatType;
import com.github.axet.jvorbis.spi.file.OggVorbisAudioFileWriter;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Lists a mod's audio files and converts one to Ogg Vorbis. The original is moved out of the mod, into
 * {@link #ARCHIVE_FOLDER} beside the packaged app. Nothing outside those two folders is written, and an
 * existing file is never overwritten.
 */
public final class AudioConversion {

	/** Beside the .exe, the same way Manifests and Diff Reports are. */
	public static final String ARCHIVE_FOLDER = "Converted Audio Files";

	/** One fixed Vorbis quality. 0.4 is about 128 kbps for stereo, and the screen doesn't ask. */
	private static final float QUALITY = 0.4f;

	private static final int MAX_NAMES = 100;

	private static final Set<String> AUDIO = Set.of("wav", "mp3", "aiff", "aif", "au", "snd", "ogg");

	/**
	 * One audio file in the mod.
	 *
	 * @param path relative to the mod, with forward slashes
	 * @param ogg whether it is already an .ogg file
	 */
	public record Listed(String path, boolean ogg) {
	}

	/**
	 * A conversion that finished.
	 *
	 * @param ogg the new file, relative to the mod
	 * @param movedTo where the original now is
	 * @param message the sentence the screen shows, which includes that path
	 */
	public record Converted(String ogg, Path movedTo, String message) {
	}

	/** Why a conversion won't be done. Nothing was changed. */
	public static final class Refused extends Exception {
		private static final long serialVersionUID = 1L;

		Refused(String message) {
			super(message);
		}
	}

	private AudioConversion() {
	}

	/**
	 * The folder beside the packaged app, or null when this isn't one. A development run has no .exe to put
	 * the original beside, so conversion is refused rather than leaving it in the mod.
	 */
	public static Path archiveFolder() {
		String app = System.getProperty("jpackage.app-path");
		if (app == null || app.isBlank()) {
			return null;
		}
		Path exe = Path.of(app);
		return exe.getParent() == null ? null : exe.getParent().resolve(ARCHIVE_FOLDER);
	}

	/** Every audio file under the mod, in path order. A link that leaves the mod is skipped. */
	public static List<Listed> list(Path modRoot) throws IOException {
		List<Listed> found = new ArrayList<>();
		if (Files.isDirectory(modRoot, LinkOption.NOFOLLOW_LINKS)) {
			walk(modRoot, modRoot.toRealPath(), modRoot, found);
			found.sort(Comparator.comparing(Listed::path));
		}
		return found;
	}

	/**
	 * Writes an .ogg beside the file and moves the original into {@link #archiveFolder()}.
	 *
	 * @param modFolderName the mod's folder name, kept in the archive so two mods' files stay distinct
	 * @param relativePath a path from {@link #list}, relative to the mod
	 */
	public static Converted convert(Path modRoot, String modFolderName, String relativePath) throws IOException, Refused {
		Path archive = archiveFolder();
		if (archive == null) {
			throw new Refused("Audio can only be converted from the packaged toolkit, so the original can be moved beside its .exe.");
		}
		if (modFolderName == null || modFolderName.isBlank() || modFolderName.contains("/") || modFolderName.contains("\\") || modFolderName.contains("..")) {
			throw new Refused("That mod can't be used.");
		}
		Path relative = relative(relativePath);
		Path source = modRoot.resolve(relative);
		refuseLinks(modRoot, source);
		if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
			throw new Refused("That file isn't in this mod.");
		}
		String extension = extension(source.getFileName().toString());
		if (!AUDIO.contains(extension)) {
			throw new Refused("That isn't an audio file this can convert.");
		}
		if (extension.equals("ogg")) {
			throw new Refused(source.getFileName() + " is already an .ogg file.");
		}
		String base = source.getFileName().toString();
		String oggName = base.substring(0, base.length() - extension.length() - 1) + ".ogg";
		Path ogg = source.resolveSibling(oggName);
		refuseLinks(modRoot, ogg);
		if (Files.exists(ogg, LinkOption.NOFOLLOW_LINKS)) {
			throw new Refused(oggName + " already exists, so nothing was converted.");
		}
		Path appDir = archive.getParent();
		if (appDir == null || !Files.isDirectory(appDir)) {
			throw new Refused("Audio can only be converted from the packaged toolkit, so the original can be moved beside its .exe.");
		}
		Path temp = ogg.resolveSibling(ogg.getFileName() + ".syxtmp");
		Path destination;
		try {
			try (AudioInputStream pcm = pcm(source, extension)) {
				encode(pcm, temp);
			}
			Path wanted = archive.resolve(modFolderName).resolve(relative);
			createFolders(appDir, wanted.getParent());
			destination = unique(wanted);
			refuseLinks(appDir, destination);
			Files.move(source, destination);
			try {
				Files.move(temp, ogg);
			} catch (IOException e) {
				try {
					Files.move(destination, source);
				} catch (IOException restore) {
					e.addSuppressed(restore);
				}
				throw e;
			}
		} finally {
			Files.deleteIfExists(temp);
		}
		String oggRelative = modRoot.relativize(ogg).toString().replace('\\', '/');
		String message = "Converted to " + ogg.getFileName() + ". The original was moved to " + destination + ".";
		return new Converted(oggRelative, destination, message);
	}

	private static void walk(Path dir, Path expectedReal, Path modRoot, List<Listed> found) throws IOException {
		try (var children = Files.newDirectoryStream(dir)) {
			for (Path child : children) {
				if (leaves(child, expectedReal)) {
					continue;
				}
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
					walk(child, expectedReal.resolve(child.getFileName().toString()), modRoot, found);
				} else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) && AUDIO.contains(extension(child.getFileName().toString()))) {
					found.add(new Listed(modRoot.relativize(child).toString().replace('\\', '/'), extension(child.getFileName().toString()).equals("ogg")));
				}
			}
		}
	}

	/** A symbolic link or a junction, which a real-path comparison is what catches on Windows. */
	private static boolean leaves(Path child, Path expectedParent) throws IOException {
		if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
			return true;
		}
		return !child.toRealPath().equals(expectedParent.resolve(child.getFileName().toString()));
	}

	private static Path relative(String text) throws Refused {
		if (text == null || text.isBlank()) {
			throw new Refused("Name the audio file to convert. list_audio_files shows them.");
		}
		Path path;
		try {
			path = Path.of(text);
		} catch (InvalidPathException e) {
			throw new Refused("That isn't a file in this mod.");
		}
		if (path.isAbsolute()) {
			throw new Refused("That isn't a file in this mod.");
		}
		path = path.normalize();
		for (Path part : path) {
			if (part.toString().equals("..")) {
				throw new Refused("That isn't a file in this mod.");
			}
		}
		return path;
	}

	private static void refuseLinks(Path root, Path target) throws IOException, Refused {
		Path relative = root.toAbsolutePath().normalize().relativize(target.toAbsolutePath().normalize());
		if (relative.isAbsolute() || relative.startsWith("..")) {
			throw new Refused("That file isn't in this mod.");
		}
		Path at = root;
		Path expected = root.toRealPath();
		for (Path part : relative) {
			if (part.toString().equals("..")) {
				throw new Refused("That file isn't in this mod.");
			}
			at = at.resolve(part.toString());
			expected = expected.resolve(part.toString());
			if (Files.exists(at, LinkOption.NOFOLLOW_LINKS) && !at.toRealPath().equals(expected)) {
				throw new Refused(part + " is a link to somewhere else, so nothing was written through it.");
			}
		}
	}

	/** The file's own name, or {@code horn (2).wav} when that name is already taken. Nothing is overwritten. */
	private static Path unique(Path wanted) throws IOException, Refused {
		String name = wanted.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String stem = dot < 0 ? name : name.substring(0, dot);
		String ext = dot < 0 ? "" : name.substring(dot);
		for (int attempt = 1; attempt <= MAX_NAMES; attempt++) {
			Path candidate = wanted.resolveSibling(attempt == 1 ? name : stem + " (" + attempt + ")" + ext);
			if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
				return candidate;
			}
		}
		throw new Refused("There are too many files called " + name + " in " + ARCHIVE_FOLDER + ".");
	}

	private static void createFolders(Path root, Path dir) throws IOException, Refused {
		Path relative = root.toAbsolutePath().normalize().relativize(dir.toAbsolutePath().normalize());
		if (relative.isAbsolute() || relative.startsWith("..")) {
			throw new Refused("That file isn't in this mod.");
		}
		Path at = root;
		Path expected = root.toRealPath();
		for (Path part : relative) {
			at = at.resolve(part.toString());
			expected = expected.resolve(part.toString());
			if (Files.exists(at, LinkOption.NOFOLLOW_LINKS)) {
				if (!Files.isDirectory(at, LinkOption.NOFOLLOW_LINKS) || !at.toRealPath().equals(expected)) {
					throw new Refused(part + " is a link to somewhere else, so nothing was written through it.");
				}
			} else {
				Files.createDirectory(at);
			}
		}
	}

	private static AudioInputStream pcm(Path source, String extension) throws IOException, Refused {
		AudioInputStream pcm = extension.equals("mp3") ? mp3(source) : read(source);
		int channels = pcm.getFormat().getChannels();
		if (channels != 1 && channels != 2) {
			pcm.close();
			throw new Refused("This file has " + channels + " channels. Only mono and stereo can be converted.");
		}
		return pcm;
	}

	private static AudioInputStream read(Path source) throws IOException, Refused {
		AudioInputStream in;
		try {
			in = AudioSystem.getAudioInputStream(source.toFile());
		} catch (UnsupportedAudioFileException e) {
			throw new Refused("This file couldn't be read as audio.");
		}
		AudioFormat base = in.getFormat();
		if (base.getSampleRate() < 1) {
			in.close();
			throw new Refused("This file has no sample rate, so it wasn't converted.");
		}
		AudioFormat wanted = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16, base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
		if (wanted.matches(base)) {
			return in;
		}
		if (!AudioSystem.isConversionSupported(wanted, base)) {
			in.close();
			throw new Refused("This file's audio couldn't be read.");
		}
		return AudioSystem.getAudioInputStream(wanted, in);
	}

	private static AudioInputStream mp3(Path source) throws IOException, Refused {
		try (InputStream in = Files.newInputStream(source)) {
			Bitstream bitstream = new Bitstream(in);
			Decoder decoder = new Decoder();
			ByteArrayOutputStream pcm = new ByteArrayOutputStream();
			int channels = -1;
			int rate = -1;
			try {
				Header header;
				while ((header = bitstream.readFrame()) != null) {
					SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
					if (channels < 0) {
						channels = buffer.getChannelCount();
						rate = buffer.getSampleFrequency();
					} else if (buffer.getChannelCount() != channels || buffer.getSampleFrequency() != rate) {
						throw new Refused("This MP3 changes its format part way through, so it wasn't converted.");
					}
					short[] samples = buffer.getBuffer();
					int length = buffer.getBufferLength();
					for (int i = 0; i < length; i++) {
						pcm.write(samples[i] & 0xff);
						pcm.write((samples[i] >> 8) & 0xff);
					}
					bitstream.closeFrame();
				}
			} catch (JavaLayerException e) {
				throw new Refused("This MP3 couldn't be read.");
			} finally {
				try {
					bitstream.close();
				} catch (JavaLayerException e) {
					// The frames already read are what we have.
				}
			}
			if (channels < 0 || rate < 1) {
				throw new Refused("This MP3 has no audio to convert.");
			}
			byte[] data = pcm.toByteArray();
			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
			return new AudioInputStream(new ByteArrayInputStream(data), format, data.length / (channels * 2L));
		}
	}

	private static void encode(AudioInputStream pcm, Path temp) throws IOException, Refused {
		AudioFileFormat.Type type = new EncoderFileFormatType("OggVorbis", "ogg", EncoderFileFormatType.VBR, QUALITY);
		try (OutputStream out = Files.newOutputStream(temp)) {
			new OggVorbisAudioFileWriter().write(pcm, type, out);
		} catch (IllegalArgumentException e) {
			throw new Refused("This audio couldn't be converted.");
		}
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
