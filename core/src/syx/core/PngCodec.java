package syx.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Reads PNG files to raw RGBA samples, the way the game's stb_image does (SnakeImage.java:40): no gamma or
 * colour management, 16-bit samples cut to their high byte. Core only sees java.base, so ImageIO isn't available.
 */
public final class PngCodec {

	/** Each pixel is 0xRRGGBBAA, row by row from the top. */
	public record Image(int width, int height, int[] rgba) {
		public int at(int x, int y) {
			return rgba[y * width + x];
		}
	}

	private static final long SIGNATURE = 0x89504E470D0A1A0AL;
	private static final int[] PASS_X = { 0, 4, 0, 2, 0, 1, 0 };
	private static final int[] PASS_Y = { 0, 0, 4, 0, 2, 0, 1 };
	private static final int[] STEP_X = { 8, 8, 4, 4, 2, 2, 1 };
	private static final int[] STEP_Y = { 8, 8, 8, 4, 4, 2, 2 };

	private PngCodec() {
	}

	public static Image decode(byte[] file) throws IOException {
		try {
			return read(file);
		} catch (IndexOutOfBoundsException | DataFormatException e) {
			throw new IOException("This PNG is damaged: " + e.getMessage());
		}
	}

	private static Image read(byte[] file) throws IOException, DataFormatException {
		if (file.length < 33 || int64(file, 0) != SIGNATURE) {
			throw new IOException("This isn't a PNG file.");
		}
		int width = 0;
		int height = 0;
		int depth = 0;
		int type = -1;
		int interlace = 0;
		byte[] palette = null;
		byte[] alpha = null;
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int at = 8;
		while (at + 8 <= file.length) {
			int length = int32(file, at);
			String kind = new String(file, at + 4, 4, StandardCharsets.ISO_8859_1);
			int body = at + 8;
			switch (kind) {
			case "IHDR" -> {
				width = int32(file, body);
				height = int32(file, body + 4);
				depth = file[body + 8] & 0xFF;
				type = file[body + 9] & 0xFF;
				interlace = file[body + 12] & 0xFF;
			}
			case "PLTE" -> palette = java.util.Arrays.copyOfRange(file, body, body + length);
			case "tRNS" -> alpha = java.util.Arrays.copyOfRange(file, body, body + length);
			case "IDAT" -> data.write(file, body, length);
			default -> {
				// Other chunks don't affect the samples.
			}
			}
			if (kind.equals("IEND")) {
				break;
			}
			at = body + length + 4;
		}
		if (width <= 0 || height <= 0 || width > 16384 || height > 16384 || type < 0) {
			throw new IOException("This PNG has no usable size.");
		}
		int channels = switch (type) {
		case 0, 3 -> 1;
		case 2 -> 3;
		case 4 -> 2;
		case 6 -> 4;
		default -> throw new IOException("This PNG has an unknown colour type.");
		};
		byte[] raw = inflate(data.toByteArray());
		int[] rgba = new int[width * height];
		int used = 0;
		for (int pass = 0; pass < (interlace == 0 ? 1 : 7); pass++) {
			int stepX = interlace == 0 ? 1 : STEP_X[pass];
			int stepY = interlace == 0 ? 1 : STEP_Y[pass];
			int startX = interlace == 0 ? 0 : PASS_X[pass];
			int startY = interlace == 0 ? 0 : PASS_Y[pass];
			int columns = (width - startX + stepX - 1) / stepX;
			int rows = (height - startY + stepY - 1) / stepY;
			if (startX >= width || startY >= height) {
				continue;
			}
			used = readPass(raw, used, columns, rows, channels, depth, type, palette, alpha, rgba, width, startX, startY, stepX, stepY);
		}
		return new Image(width, height, rgba);
	}

	private static int readPass(byte[] raw, int offset, int columns, int rows, int channels, int depth, int type, byte[] palette, byte[] alpha, int[] rgba, int width, int startX, int startY, int stepX, int stepY) throws IOException {
		int bytes = (columns * channels * depth + 7) / 8;
		int perPixel = Math.max(1, channels * depth / 8);
		byte[] previous = new byte[bytes];
		byte[] line = new byte[bytes];
		int at = offset;
		for (int row = 0; row < rows; row++) {
			int filter = raw[at++] & 0xFF;
			System.arraycopy(raw, at, line, 0, bytes);
			at += bytes;
			unfilter(filter, line, previous, perPixel);
			for (int column = 0; column < columns; column++) {
				rgba[(startY + row * stepY) * width + startX + column * stepX] = pixel(line, column, channels, depth, type, palette, alpha);
			}
			byte[] swap = previous;
			previous = line;
			line = swap;
		}
		return at;
	}

	private static void unfilter(int filter, byte[] line, byte[] previous, int perPixel) throws IOException {
		for (int i = 0; i < line.length; i++) {
			int left = i >= perPixel ? line[i - perPixel] & 0xFF : 0;
			int up = previous[i] & 0xFF;
			int corner = i >= perPixel ? previous[i - perPixel] & 0xFF : 0;
			int add = switch (filter) {
			case 0 -> 0;
			case 1 -> left;
			case 2 -> up;
			case 3 -> (left + up) / 2;
			case 4 -> paeth(left, up, corner);
			default -> throw new IOException("This PNG has an unknown filter.");
			};
			line[i] = (byte) (line[i] + add);
		}
	}

	private static int paeth(int a, int b, int c) {
		int p = a + b - c;
		int pa = Math.abs(p - a);
		int pb = Math.abs(p - b);
		int pc = Math.abs(p - c);
		if (pa <= pb && pa <= pc) {
			return a;
		}
		return pb <= pc ? b : c;
	}

	private static int pixel(byte[] line, int column, int channels, int depth, int type, byte[] palette, byte[] alpha) throws IOException {
		if (type == 3) {
			int index = sample(line, column, 0, 1, depth);
			if (palette == null || index * 3 + 2 >= palette.length) {
				throw new IOException("This PNG names a colour its palette doesn't have.");
			}
			int a = alpha != null && index < alpha.length ? alpha[index] & 0xFF : 255;
			return pack(palette[index * 3] & 0xFF, palette[index * 3 + 1] & 0xFF, palette[index * 3 + 2] & 0xFF, a);
		}
		int[] raw = new int[channels];
		for (int channel = 0; channel < channels; channel++) {
			raw[channel] = sample(line, column, channel, channels, depth);
		}
		int a = type == 4 ? scale(raw[1], depth) : type == 6 ? scale(raw[3], depth) : 255;
		if (alpha != null && (type == 0 || type == 2) && matchesKey(raw, alpha, channels, depth)) {
			a = 0;
		}
		if (type == 0 || type == 4) {
			int gray = scale(raw[0], depth);
			return pack(gray, gray, gray, a);
		}
		return pack(scale(raw[0], depth), scale(raw[1], depth), scale(raw[2], depth), a);
	}

	private static boolean matchesKey(int[] raw, byte[] alpha, int channels, int depth) {
		for (int channel = 0; channel < channels; channel++) {
			int key = (alpha[channel * 2] & 0xFF) << 8 | alpha[channel * 2 + 1] & 0xFF;
			if (raw[channel] != (depth == 16 ? key : key & 0xFF)) {
				return false;
			}
		}
		return true;
	}

	private static int sample(byte[] line, int column, int channel, int channels, int depth) {
		if (depth == 16) {
			int at = (column * channels + channel) * 2;
			return (line[at] & 0xFF) << 8 | line[at + 1] & 0xFF;
		}
		if (depth == 8) {
			return line[column * channels + channel] & 0xFF;
		}
		int bit = (column * channels + channel) * depth;
		return (line[bit / 8] & 0xFF) >> 8 - depth - bit % 8 & (1 << depth) - 1;
	}

	/** stb_image keeps the high byte of a 16-bit sample, and stretches 1, 2 and 4-bit samples to 255. */
	private static int scale(int value, int depth) {
		return switch (depth) {
		case 16 -> value >> 8;
		case 8 -> value;
		case 4 -> value * 17;
		case 2 -> value * 85;
		default -> value * 255;
		};
	}

	private static int pack(int r, int g, int b, int a) {
		return r << 24 | g << 16 | b << 8 | a;
	}

	private static byte[] inflate(byte[] data) throws DataFormatException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(data);
			ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
			byte[] buffer = new byte[65536];
			while (!inflater.finished()) {
				int count = inflater.inflate(buffer);
				if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				out.write(buffer, 0, count);
			}
			return out.toByteArray();
		} finally {
			inflater.end();
		}
	}

	/** Writes 0xAARRGGBB pixels as an 8-bit RGBA PNG. */
	static byte[] encode(int width, int height, int[] argb) throws IOException {
		byte[] raw = new byte[(width * 4 + 1) * height];
		int at = 0;
		for (int y = 0; y < height; y++) {
			raw[at++] = 0;
			for (int x = 0; x < width; x++) {
				int pixel = argb[y * width + x];
				raw[at++] = (byte) (pixel >> 16);
				raw[at++] = (byte) (pixel >> 8);
				raw[at++] = (byte) pixel;
				raw[at++] = (byte) (pixel >> 24);
			}
		}
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		try {
			deflater.setInput(raw);
			deflater.finish();
			byte[] buffer = new byte[65536];
			while (!deflater.finished()) {
				packed.write(buffer, 0, deflater.deflate(buffer));
			}
		} finally {
			deflater.end();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		putLong(out, SIGNATURE);
		byte[] header = new byte[13];
		putInt(header, 0, width);
		putInt(header, 4, height);
		header[8] = 8;
		header[9] = 6;
		chunk(out, "IHDR", header);
		chunk(out, "IDAT", packed.toByteArray());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String kind, byte[] body) throws IOException {
		byte[] name = kind.getBytes(StandardCharsets.ISO_8859_1);
		byte[] length = new byte[4];
		putInt(length, 0, body.length);
		out.write(length);
		out.write(name);
		out.write(body);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(body);
		byte[] sum = new byte[4];
		putInt(sum, 0, (int) crc.getValue());
		out.write(sum);
	}

	private static void putInt(byte[] into, int at, int value) {
		into[at] = (byte) (value >> 24);
		into[at + 1] = (byte) (value >> 16);
		into[at + 2] = (byte) (value >> 8);
		into[at + 3] = (byte) value;
	}

	private static void putLong(ByteArrayOutputStream out, long value) {
		for (int shift = 56; shift >= 0; shift -= 8) {
			out.write((int) (value >> shift));
		}
	}

	private static int int32(byte[] file, int at) {
		return (file[at] & 0xFF) << 24 | (file[at + 1] & 0xFF) << 16 | (file[at + 2] & 0xFF) << 8 | file[at + 3] & 0xFF;
	}

	private static long int64(byte[] file, int at) {
		return ((long) int32(file, at)) << 32 | int32(file, at + 4) & 0xFFFFFFFFL;
	}
}
