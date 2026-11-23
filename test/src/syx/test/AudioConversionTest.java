package syx.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import syx.audio.AudioConversion;

/** Converts generated audio, with no game files. The original moves beside a pretend packaged app. */
public final class AudioConversionTest {

	/** A one-frame MPEG-2.5 MP3 that JLayer can read. It is not from the game. */
	private static final byte[] MP3 = hex("FFE318C40000000348000000004C414D45332E39382E3200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

	private static int passed;
	private static int failed;

	private AudioConversionTest() {
	}

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("syx-audio");
		String previous = System.getProperty("jpackage.app-path");
		try {
			Path app = root.resolve("app");
			Path mod = root.resolve("mods/My Mod");
			Files.createDirectories(app);
			Files.createDirectories(mod);
			System.setProperty("jpackage.app-path", app.resolve("Syx Toolkit.exe").toString());
			converts(mod, app);
			refuses(mod, app);
		} finally {
			if (previous == null) {
				System.clearProperty("jpackage.app-path");
			} else {
				System.setProperty("jpackage.app-path", previous);
			}
			delete(root);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void converts(Path mod, Path app) throws Exception {
		byte[] wav = writeWav(mod.resolve("sounds/horn.wav"));
		Files.write(mod.resolve("sounds/voice.mp3"), MP3);
		Files.write(mod.resolve("sounds/done.ogg"), new byte[] { 'O', 'g', 'g', 'S' });
		var listed = AudioConversion.list(mod);
		expect("wav, mp3 and ogg are listed: " + listed, listed.stream().anyMatch(file -> file.path().equals("sounds/horn.wav") && !file.ogg()) && listed.stream().anyMatch(file -> file.path().equals("sounds/voice.mp3") && !file.ogg()) && listed.stream().anyMatch(file -> file.path().equals("sounds/done.ogg") && file.ogg()));

		AudioConversion.Converted wavResult = AudioConversion.convert(mod, "My Mod", "sounds/horn.wav");
		expect("the wav becomes an ogg and the message names where the original went: " + wavResult.message(), wavResult.ogg().equals("sounds/horn.ogg") && wavResult.message().contains(wavResult.movedTo().toString()) && wavResult.movedTo().startsWith(app.resolve("Converted Audio Files")));
		expect("the ogg is Vorbis and the original left the mod", isVorbis(mod.resolve("sounds/horn.ogg")) && !Files.exists(mod.resolve("sounds/horn.wav")));
		expect("the original was moved intact: " + wavResult.movedTo(), Files.isRegularFile(wavResult.movedTo()) && java.util.Arrays.equals(wav, Files.readAllBytes(wavResult.movedTo())) && wavResult.movedTo().equals(app.resolve("Converted Audio Files").resolve("My Mod").resolve("sounds/horn.wav")));

		AudioConversion.Converted mp3Result = AudioConversion.convert(mod, "My Mod", "sounds/voice.mp3");
		expect("the mp3 becomes an ogg too", mp3Result.ogg().equals("sounds/voice.ogg") && isVorbis(mod.resolve("sounds/voice.ogg")) && !Files.exists(mod.resolve("sounds/voice.mp3")));

		Files.delete(mod.resolve("sounds/horn.ogg"));
		byte[] again = writeWav(mod.resolve("sounds/horn.wav"));
		AudioConversion.Converted second = AudioConversion.convert(mod, "My Mod", "sounds/horn.wav");
		expect("a second file of the same name is numbered: " + second.movedTo(), second.movedTo().getFileName().toString().equals("horn (2).wav") && java.util.Arrays.equals(again, Files.readAllBytes(second.movedTo())) && Files.isRegularFile(wavResult.movedTo()));
	}

	private static void refuses(Path mod, Path app) throws Exception {
		byte[] notes = "hello".getBytes(StandardCharsets.UTF_8);
		Files.write(mod.resolve("notes.txt"), notes);
		expect("a text file is refused", refused(mod, "notes.txt").contains("isn't an audio file"));
		expect("and is untouched", java.util.Arrays.equals(notes, Files.readAllBytes(mod.resolve("notes.txt"))));

		byte[] existing = writeWav(mod.resolve("sounds/bell.wav"));
		Files.write(mod.resolve("sounds/bell.ogg"), new byte[] { 1, 2, 3 });
		expect("an ogg already beside it is refused", refused(mod, "sounds/bell.wav").contains("already exists"));
		expect("so the wav stays and the ogg is unchanged", java.util.Arrays.equals(existing, Files.readAllBytes(mod.resolve("sounds/bell.wav"))) && Files.readAllBytes(mod.resolve("sounds/bell.ogg")).length == 3);

		expect("an ogg itself is refused", refused(mod, "sounds/done.ogg").contains("already an .ogg"));
		expect("a path outside the mod is refused", refused(mod, "../horn.wav").contains("isn't a file in this mod"));

		Path outside = mod.resolveSibling("outside");
		Files.createDirectories(outside);
		writeWav(outside.resolve("linked.wav"));
		Path link = mod.resolve("sounds/linked");
		if (junction(link, outside)) {
			try {
				expect("a file through a link isn't listed", AudioConversion.list(mod).stream().noneMatch(file -> file.path().contains("linked")));
				expect("and converting it is refused", refused(mod, "sounds/linked/linked.wav").contains("link"));
				expect("the file outside is untouched", Files.isRegularFile(outside.resolve("linked.wav")));
			} finally {
				removeJunction(link);
			}
		} else {
			System.out.println("OBSERVED junctions can't be made here, so that case wasn't run.");
		}

		byte[] stayed = writeWav(mod.resolve("sounds/stay.wav"));
		String previous = System.getProperty("jpackage.app-path");
		System.clearProperty("jpackage.app-path");
		try {
			expect("without a packaged app it is refused", refused(mod, "sounds/stay.wav").contains("packaged toolkit"));
			expect("and the file stays", java.util.Arrays.equals(stayed, Files.readAllBytes(mod.resolve("sounds/stay.wav"))));
		} finally {
			System.setProperty("jpackage.app-path", previous);
		}
		expect("the archive is beside the app", AudioConversion.archiveFolder().equals(app.resolve("Converted Audio Files")));
	}

	private static String refused(Path mod, String path) {
		try {
			AudioConversion.convert(mod, "My Mod", path);
			expect(path + " should have been refused", false);
			return "";
		} catch (AudioConversion.Refused e) {
			expect(path + " is refused: " + e.getMessage(), true);
			return e.getMessage();
		} catch (IOException e) {
			expect(path + " failed: " + e, false);
			return "";
		}
	}

	private static byte[] writeWav(Path file) throws Exception {
		Files.createDirectories(file.getParent());
		AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
		byte[] silence = new byte[2205 * 2];
		AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(silence), format, silence.length / 2), AudioFileFormat.Type.WAVE, file.toFile());
		return Files.readAllBytes(file);
	}

	private static boolean isVorbis(Path file) throws IOException {
		byte[] bytes = Files.readAllBytes(file);
		if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') {
			return false;
		}
		return new String(bytes, StandardCharsets.ISO_8859_1).contains("vorbis");
	}

	private static boolean junction(Path link, Path target) throws IOException, InterruptedException {
		Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		return process.waitFor() == 0 && Files.exists(link);
	}

	private static void removeJunction(Path link) throws IOException, InterruptedException {
		new ProcessBuilder("cmd", "/c", "rmdir", link.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
	}

	private static byte[] hex(String text) {
		byte[] bytes = new byte[text.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
		}
		return bytes;
	}

	private static void delete(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (var walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
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
