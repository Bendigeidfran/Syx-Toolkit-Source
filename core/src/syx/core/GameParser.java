package syx.core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The only class that touches game code. It loads the user's own SongsOfSyx.jar at runtime and
 * calls the game's parser through reflection, so the toolkit builds without the jar and can't
 * pick up game code by accident.
 */
public final class GameParser implements AutoCloseable {

	private static final String JSON_CLASS = "snake2d.util.file.Json";
	private static final String DATA_ERROR_CLASS = "snake2d.Errors$DataError";
	private static final String VERSION_CLASS = "game.VERSION";
	private static final String COLOR_CLASS = "snake2d.util.color.ColorImp";
	private static final String COLOR_INTERFACE = "snake2d.util.color.COLOR";

	/** A colour as the game holds it: each channel from 0 to 255, which is half of what a file writes. */
	public record Rgb(int r, int g, int b) {
	}

	/** Thrown when the jar can't provide the parser, for example after a game update. */
	public static final class UnavailableException extends Exception {
		private static final long serialVersionUID = 1L;

		UnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/** The result of parsing one file. At most one of the two is set. */
	public record Outcome(String dataError, Throwable otherFailure) {
		public boolean ok() {
			return dataError == null && otherFailure == null;
		}
	}

	/** A text tag read from a file. The value is only set if the outcome is ok. */
	public record TextValue(String value, Outcome outcome) {
	}

	/** The version of the user's game, read from their own jar. */
	public record GameVersion(int major, int minor) {
	}

	/** How a value is written: quoted text, a plain value such as a number, or a block or list. */
	public enum ValueKind {
		TEXT, PLAIN, OTHER
	}

	/** A top-level tag as the game reads it. The value is null for a block or list. */
	public record TagValue(String key, ValueKind kind, String value) {
	}

	/** Every top-level tag in a file, in file order. The list is only usable if the outcome is ok. */
	public record Tags(List<TagValue> tags, Outcome outcome) {
	}

	private final URLClassLoader loader;
	private final Constructor<?> jsonConstructor;
	private final Method jsonText;
	private final Method jsonTextRequired;
	private final Method jsonValue;
	private final Method jsonKeys;
	private final Method jsonBlock;
	private final Class<?> dataErrorClass;
	private final Field dataErrorText;

	private GameParser(URLClassLoader loader, Class<?> json, Class<?> dataErrorClass) throws ReflectiveOperationException {
		this.loader = loader;
		this.jsonConstructor = json.getConstructor(Path.class);
		this.jsonText = json.getMethod("text", String.class, String.class);
		this.jsonTextRequired = json.getMethod("text", String.class);
		this.jsonValue = json.getMethod("value", String.class);
		this.jsonKeys = json.getMethod("keys");
		this.jsonBlock = json.getMethod("json", String.class);
		this.dataErrorClass = dataErrorClass;
		// DataError.error is the message without the file path appended.
		this.dataErrorText = dataErrorClass.getField("error");
	}

	public static GameParser open(Path jar) throws UnavailableException {
		URLClassLoader loader = null;
		try {
			URL url = jar.toUri().toURL();
			// The platform loader as parent keeps the game's classes and ours apart.
			loader = new URLClassLoader(new URL[] { url }, ClassLoader.getPlatformClassLoader());
			Class<?> json = Class.forName(JSON_CLASS, false, loader);
			Class<?> dataError = Class.forName(DATA_ERROR_CLASS, false, loader);
			return new GameParser(loader, json, dataError);
		} catch (IOException | ReflectiveOperationException | LinkageError e) {
			closeQuietly(loader);
			throw new UnavailableException("Couldn't find the game's parser in " + jar
					+ ". The game version may not be compatible with this toolkit.", e);
		}
	}

	public Outcome parse(Path file) {
		try {
			jsonConstructor.newInstance(file);
			return new Outcome(null, null);
		} catch (InvocationTargetException e) {
			return failure(e.getCause());
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			return new Outcome(null, e);
		}
	}

	/** Reads a text tag the way the game does, returning the fallback if the tag is absent. */
	public TextValue text(Path file, String key, String fallback) {
		try {
			Object json = jsonConstructor.newInstance(file);
			return new TextValue((String) jsonText.invoke(json, key, fallback), new Outcome(null, null));
		} catch (InvocationTargetException e) {
			return new TextValue(null, failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			return new TextValue(null, new Outcome(null, e));
		}
	}

	/** Reads every top-level tag the way the game does. */
	public Tags tags(Path file) {
		try {
			Object json = jsonConstructor.newInstance(file);
			List<TagValue> tags = new ArrayList<>();
			for (Object key : (Iterable<?>) jsonKeys.invoke(json)) {
				tags.add(tag(json, String.valueOf(key)));
			}
			return new Tags(tags, new Outcome(null, null));
		} catch (InvocationTargetException e) {
			return new Tags(List.of(), failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			return new Tags(List.of(), new Outcome(null, e));
		}
	}

	/**
	 * One read to make with the game's own Json methods, the same way a loader would.
	 *
	 * @param block the nested blocks to enter first, outermost first
	 * @param method the name of the Json method
	 * @param args its arguments, which pick the overload: String, Integer, Double or Boolean
	 */
	public record Read(List<String> block, String method, List<Object> args) {
	}

	/** What a read returned, if the outcome is ok. */
	public record ReadResult(Read read, Object value, Outcome outcome) {
	}

	/**
	 * Makes each read on the file. Unlike a plain parse, this also finds wrong types, ranges and list
	 * contents, because those are only checked when a loader asks for them. Each read is separate, so
	 * one failure doesn't hide the others.
	 */
	public List<ReadResult> read(Path file, List<Read> reads) {
		List<ReadResult> results = new ArrayList<>();
		Object root;
		try {
			root = jsonConstructor.newInstance(file);
		} catch (InvocationTargetException e) {
			Outcome failed = failure(e.getCause());
			for (Read read : reads) {
				results.add(new ReadResult(read, null, failed));
			}
			return results;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			for (Read read : reads) {
				results.add(new ReadResult(read, null, new Outcome(null, e)));
			}
			return results;
		}
		for (Read read : reads) {
			results.add(readOne(root, read));
		}
		return results;
	}

	private ReadResult readOne(Object root, Read read) {
		try {
			Object json = root;
			for (String block : read.block()) {
				json = jsonBlock.invoke(json, block);
			}
			Class<?>[] types = new Class<?>[read.args().size()];
			for (int i = 0; i < types.length; i++) {
				types[i] = primitiveType(read.args().get(i));
			}
			Object value = json.getClass().getMethod(read.method(), types).invoke(json, read.args().toArray());
			return new ReadResult(read, value, new Outcome(null, null));
		} catch (InvocationTargetException e) {
			return new ReadResult(read, null, failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			return new ReadResult(read, null, new Outcome(null, e));
		}
	}

	private static Class<?> primitiveType(Object arg) {
		if (arg instanceof Integer) {
			return int.class;
		}
		if (arg instanceof Double) {
			return double.class;
		}
		if (arg instanceof Boolean) {
			return boolean.class;
		}
		return String.class;
	}

	/** A value couldn't be read. The game would refuse it too. */
	public static final class ReadException extends Exception {
		private static final long serialVersionUID = 1L;
		private final transient Outcome outcome;

		ReadException(String message) {
			this(message, null);
		}

		ReadException(Outcome outcome) {
			this(String.valueOf(outcome), outcome);
		}

		private ReadException(String message, Outcome outcome) {
			super(message);
			this.outcome = outcome;
		}

		/** The game's own message, or null if this wasn't one of its data errors. */
		public String dataError() {
			return outcome == null ? null : outcome.dataError();
		}
	}

	/** One block of a file, read with the game's own Json methods. The game's parser stays the authority. */
	public final class Node {
		private final Object json;

		private Node(Object json) {
			this.json = json;
		}

		private Object call(String method, Class<?>[] types, Object... args) throws ReadException {
			try {
				return json.getClass().getMethod(method, types).invoke(json, args);
			} catch (InvocationTargetException e) {
				throw new ReadException(failure(e.getCause()));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
				throw new ReadException(String.valueOf(e));
			}
		}

		/** A list of blocks with the game's own limits on how many. */
		public List<Node> blocks(String key, int min, int max) throws ReadException {
			List<Node> nodes = new ArrayList<>();
			for (Object item : (Object[]) call("jsons", new Class<?>[] { String.class, int.class, int.class }, key, min, max)) {
				nodes.add(new Node(item));
			}
			return nodes;
		}

		/** A true or false value with a fallback, as the game reads one. */
		public boolean boolOr(String key, boolean fallback) throws ReadException {
			return (Boolean) call("bool", new Class<?>[] { String.class, boolean.class }, key, fallback);
		}

		/** One colour, read by the game's own ColorImp(Json, key). */
		public Rgb color(String key) throws ReadException {
			try {
				Class<?> colors = colorClass();
				return rgb(colors.getConstructor(json.getClass(), String.class).newInstance(json, key));
			} catch (InvocationTargetException e) {
				throw new ReadException(failure(e.getCause()));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				throw new ReadException(String.valueOf(e));
			}
		}

		/** A colour list in any of its forms, read by the game's own ColorImp.cols. */
		public List<Rgb> colors(String key) throws ReadException {
			try {
				Object list = colorClass().getMethod("cols", json.getClass(), String.class).invoke(null, json, key);
				List<Rgb> colors = new ArrayList<>();
				for (Object color : (Iterable<?>) list) {
					colors.add(rgb(color));
				}
				return colors;
			} catch (InvocationTargetException e) {
				throw new ReadException(failure(e.getCause()));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				throw new ReadException(String.valueOf(e));
			}
		}

		public boolean has(String key) throws ReadException {
			return (Boolean) call("has", new Class<?>[] { String.class }, key);
		}

		/** True if the value is a block rather than a single value. */
		public boolean isBlock(String key) throws ReadException {
			return (Boolean) call("jsonIs", new Class<?>[] { String.class }, key);
		}

		public Node block(String key) throws ReadException {
			return new Node(call("json", new Class<?>[] { String.class }, key));
		}

		/** A list of blocks. */
		public List<Node> blocks(String key) throws ReadException {
			List<Node> nodes = new ArrayList<>();
			for (Object item : (Object[]) call("jsons", new Class<?>[] { String.class }, key)) {
				nodes.add(new Node(item));
			}
			return nodes;
		}

		public List<String> keys() throws ReadException {
			List<String> keys = new ArrayList<>();
			for (Object key : (Iterable<?>) call("keys", new Class<?>[0])) {
				keys.add(String.valueOf(key));
			}
			return keys;
		}

		public int integer(String key, int min, int max) throws ReadException {
			return (Integer) call("i", new Class<?>[] { String.class, int.class, int.class }, key, min, max);
		}

		/** A whole number with no limits. */
		public int integer(String key) throws ReadException {
			return (Integer) call("i", new Class<?>[] { String.class }, key);
		}

		/** A decimal with no limits. */
		public double decimal(String key) throws ReadException {
			return (Double) call("d", new Class<?>[] { String.class }, key);
		}

		public boolean bool(String key) throws ReadException {
			return (Boolean) call("bool", new Class<?>[] { String.class }, key);
		}

		public String text(String key) throws ReadException {
			return (String) call("text", new Class<?>[] { String.class }, key);
		}

		public String[] texts(String key) throws ReadException {
			return (String[]) call("texts", new Class<?>[] { String.class }, key);
		}

		public double[] decimals(String key) throws ReadException {
			return (double[]) call("ds", new Class<?>[] { String.class }, key);
		}

		/** True if the value is quoted text. */
		public boolean isText(String key) throws ReadException {
			return (Boolean) call("textIs", new Class<?>[] { String.class }, key);
		}

		/** A list of lists of values. */
		public String[][] lists(String key) throws ReadException {
			return (String[][]) call("values2", new Class<?>[] { String.class }, key);
		}

		/** True if the value is a list rather than a single value. */
		public boolean isList(String key) throws ReadException {
			return (Boolean) call("arrayIs", new Class<?>[] { String.class }, key);
		}

		public String[] values(String key) throws ReadException {
			return (String[]) call("values", new Class<?>[] { String.class }, key);
		}

		public String value(String key) throws ReadException {
			return (String) call("value", new Class<?>[] { String.class }, key);
		}

		public double decimal(String key, double min, double max) throws ReadException {
			return (Double) call("d", new Class<?>[] { String.class, double.class, double.class }, key, min, max);
		}

		public double decimalOr(String key, double min, double max, double fallback) throws ReadException {
			return (Double) call("dTry", new Class<?>[] { String.class, double.class, double.class, double.class }, key, min, max, fallback);
		}
	}

	/** Opens a file for reading its structure. */
	public Node root(Path file) throws ReadException {
		try {
			return new Node(jsonConstructor.newInstance(file));
		} catch (InvocationTargetException e) {
			throw new ReadException(failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			throw new ReadException(String.valueOf(e));
		}
	}

	/** Reads layers of the same file as the game merges them, with the highest priority first and vanilla last. */
	public Node root(List<Path> layers) throws ReadException {
		try {
			return new Node(jsonConstructor.getDeclaringClass().getConstructor(Path[].class).newInstance((Object) layers.toArray(new Path[0])));
		} catch (InvocationTargetException e) {
			throw new ReadException(failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			throw new ReadException(String.valueOf(e));
		}
	}

	private Class<?> colorClass() throws ClassNotFoundException {
		return Class.forName(COLOR_CLASS, true, loader);
	}

	private Rgb rgb(Object color) throws ReflectiveOperationException {
		Class<?> type = Class.forName(COLOR_INTERFACE, true, loader);
		return new Rgb((byte) type.getMethod("red").invoke(color) & 0xFF, (byte) type.getMethod("green").invoke(color) & 0xFF, (byte) type.getMethod("blue").invoke(color) & 0xFF);
	}

	private Object color(Rgb color) throws ReflectiveOperationException {
		return colorClass().getConstructor(int.class, int.class, int.class).newInstance(color.r(), color.g(), color.b());
	}

	/** The game's COLOR.shade, which makes a darkened or brightened copy. */
	public Rgb shade(Rgb color, double amount) throws ReadException {
		return colorCall(() -> rgb(colorClass().getMethod("shade", double.class).invoke(color(color), amount)));
	}

	/** The game's ColorImp.shadeSelf. A channel past 255 wraps around, as it does in the game. */
	public Rgb shadeSelf(Rgb color, double amount) throws ReadException {
		return colorCall(() -> rgb(colorClass().getMethod("shadeSelf", double.class).invoke(color(color), amount)));
	}

	/** The game's COLOR.makeSaturated, which RColors uses for each level of clothes (RColors.java:257). */
	public Rgb saturated(Rgb color, double amount) throws ReadException {
		return colorCall(() -> rgb(Class.forName(COLOR_INTERFACE, true, loader).getMethod("makeSaturated", double.class).invoke(color(color), amount)));
	}

	/** The game's ColorImp.interpolate between two colours. */
	public Rgb interpolate(Rgb from, Rgb to, double part) throws ReadException {
		return colorCall(() -> {
			Class<?> type = Class.forName(COLOR_INTERFACE, true, loader);
			return rgb(colorClass().getMethod("interpolate", type, type, double.class).invoke(colorClass().getConstructor().newInstance(), color(from), color(to), part));
		});
	}

	private interface ColorCall {
		Rgb run() throws ReflectiveOperationException;
	}

	private Rgb colorCall(ColorCall call) throws ReadException {
		try {
			return call.run();
		} catch (InvocationTargetException e) {
			throw new ReadException(failure(e.getCause()));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			throw new ReadException(String.valueOf(e));
		}
	}

	/** The keys of a nested block, in file order, or null if it can't be read as a block. */
	public List<String> blockKeys(Path file, String block) {
		try {
			Object json = jsonBlock.invoke(jsonConstructor.newInstance(file), block);
			List<String> keys = new ArrayList<>();
			for (Object key : (Iterable<?>) jsonKeys.invoke(json)) {
				keys.add(String.valueOf(key));
			}
			return keys;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
			return null;
		}
	}

	/** The game's text() and value() each refuse the other kind, which tells them apart. */
	private TagValue tag(Object json, String key) throws ReflectiveOperationException {
		try {
			return new TagValue(key, ValueKind.TEXT, (String) jsonTextRequired.invoke(json, key));
		} catch (InvocationTargetException notText) {
			if (!dataErrorClass.isInstance(notText.getCause())) {
				throw notText;
			}
		}
		try {
			return new TagValue(key, ValueKind.PLAIN, (String) jsonValue.invoke(json, key));
		} catch (InvocationTargetException notPlain) {
			if (!dataErrorClass.isInstance(notPlain.getCause())) {
				throw notPlain;
			}
		}
		return new TagValue(key, ValueKind.OTHER, null);
	}

	/** Reads the game's version from the user's jar, so it's right after a game update. */
	public GameVersion version() throws UnavailableException {
		try {
			Class<?> version = Class.forName(VERSION_CLASS, true, loader);
			return new GameVersion(version.getField("VERSION_MAJOR").getInt(null), version.getField("VERSION_MINOR").getInt(null));
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new UnavailableException("Couldn't read the game's version from its jar. "
					+ "The game version may not be compatible with this toolkit.", e);
		}
	}

	private Outcome failure(Throwable cause) {
		if (!dataErrorClass.isInstance(cause)) {
			return new Outcome(null, cause);
		}
		try {
			return new Outcome(String.valueOf(dataErrorText.get(cause)), null);
		} catch (IllegalAccessException accessFailure) {
			return new Outcome(null, accessFailure);
		}
	}

	@Override
	public void close() {
		closeQuietly(loader);
	}

	private static void closeQuietly(URLClassLoader loader) {
		if (loader != null) {
			try {
				loader.close();
			} catch (IOException e) {
				// Nothing useful can be done about a jar that won't close.
			}
		}
	}
}
