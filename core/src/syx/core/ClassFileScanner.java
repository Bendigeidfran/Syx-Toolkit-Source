package syx.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads compiled loader classes from the user's jar as plain bytes, to see which calls they make and
 * with which constant arguments. Game code is never loaded or run. Anything it can't work out is
 * reported as {@link Unknown}, never guessed.
 */
public final class ClassFileScanner implements AutoCloseable {

	/** What a value on the stack is known to be. */
	public sealed interface Value permits Const, Local, FieldRef, Returned, New, Unknown {
	}

	/** A literal. Null for aconst_null. */
	public record Const(Object value) implements Value {
	}

	/** Whatever a method parameter or local variable held on entry. */
	public record Local(int slot) implements Value {
	}

	public record FieldRef(String owner, String name) implements Value {
	}

	/** What a call gave back. The receiver is what it was called on, or null for a static call. */
	public record Returned(String owner, String name, Value receiver) implements Value {
	}

	/** A new object. The site is where in the code it's made, which tells two of the same class apart. */
	public record New(String className, int site) implements Value {
	}

	public record Unknown() implements Value {
	}

	private static final Unknown UNKNOWN = new Unknown();

	/**
	 * One method or constructor call.
	 *
	 * @param receiver the object it was called on, or null for a static call
	 * @param conditional true if a branch or loop decides whether this runs
	 */
	public record Call(String owner, String name, String descriptor, Value receiver, List<Value> args, int line, boolean conditional) {
	}

	/**
	 * A value stored somewhere the scan can't follow, such as a field or an array.
	 *
	 * @param slot the parameter it was, or -1
	 * @param site where it was made if it's a new object, or -1
	 */
	public record Escape(int slot, int site, int line) {
	}

	public record ScannedMethod(String name, String descriptor, List<Call> calls, List<Escape> escapes, Set<Integer> reassignedSlots) {
	}

	public record ScannedClass(String name, String superName, String sourceFile, List<ScannedMethod> methods) {
		public ScannedMethod method(String methodName, String descriptor) {
			return methods.stream().filter(m -> m.name().equals(methodName) && m.descriptor().equals(descriptor)).findFirst().orElse(null);
		}
	}

	/** The class file couldn't be understood, for example after a game update. */
	public static final class ScanException extends Exception {
		private static final long serialVersionUID = 1L;

		ScanException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final ZipFile jar;

	private ClassFileScanner(ZipFile jar) {
		this.jar = jar;
	}

	public static ClassFileScanner open(Path jarFile) throws IOException {
		return new ClassFileScanner(new ZipFile(jarFile.toFile()));
	}

	/** Scans a class by its dotted or slashed name. Returns null if the jar doesn't have it. */
	public ScannedClass scan(String className) throws ScanException {
		ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			return read(in.readAllBytes());
		} catch (IOException | RuntimeException e) {
			throw new ScanException("Couldn't read the class " + className + ".", e);
		}
	}

	/** True if a class holds this text. A name built at run time still has its fixed part in the class. */
	public boolean mentions(String className, String text) throws ScanException {
		ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
		if (entry == null) {
			return false;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(text);
		} catch (IOException e) {
			throw new ScanException("Couldn't read the class " + className + ".", e);
		}
	}

	/**
	 * A method's body hash, as the version diff computes it ({@link ClassFileModel}), or null if the class or
	 * method is missing. Pins code whose limit isn't a Json read, such as the size of an array a tag indexes.
	 */
	public String bodyHash(String className, String name, String descriptor) throws ScanException {
		ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			for (ClassFileModel.MethodDef method : ClassFileModel.read(in.readAllBytes()).methods) {
				if (method.name().equals(name) && method.descriptor().equals(descriptor)) {
					return method.bodyHash();
				}
			}
			return null;
		} catch (IOException | RuntimeException e) {
			throw new ScanException("Couldn't read the class " + className + ".", e);
		}
	}

	@Override
	public void close() throws IOException {
		jar.close();
	}

	record MemberRef(String owner, String name, String descriptor) {
	}

	/** A class file's constant pool, which {@link ClassFileModel} reads too. */
	static final class Pool {
		final Object[] entries;
		final int[] first;
		final int[] second;
		final int[] tags;

		Pool(DataInputStream in) throws IOException {
			int count = in.readUnsignedShort();
			entries = new Object[count];
			first = new int[count];
			second = new int[count];
			tags = new int[count];
			for (int i = 1; i < count; i++) {
				int tag = in.readUnsignedByte();
				tags[i] = tag;
				switch (tag) {
				case 1 -> entries[i] = in.readUTF();
				case 3 -> entries[i] = in.readInt();
				case 4 -> entries[i] = in.readFloat();
				case 5 -> entries[i++] = in.readLong();
				case 6 -> entries[i++] = in.readDouble();
				case 7, 8, 16, 19, 20 -> first[i] = in.readUnsignedShort();
				case 9, 10, 11, 12, 17, 18 -> {
					first[i] = in.readUnsignedShort();
					second[i] = in.readUnsignedShort();
				}
				case 15 -> {
					first[i] = in.readUnsignedByte();
					second[i] = in.readUnsignedShort();
				}
				default -> throw new IllegalStateException("Unknown constant type " + tag);
				}
			}
		}

		String utf(int index) {
			return (String) entries[index];
		}

		String className(int index) {
			return utf(first[index]);
		}

		MemberRef member(int index) {
			int nameAndType = second[index];
			String owner = tags[index] == 18 ? "" : className(first[index]);
			return new MemberRef(owner, utf(first[nameAndType]), utf(second[nameAndType]));
		}

		Value constant(int index) {
			return switch (tags[index]) {
			case 3, 4, 5, 6 -> new Const(entries[index]);
			case 8 -> new Const(utf(first[index]));
			default -> UNKNOWN;
			};
		}
	}

	private static ScannedClass read(byte[] bytes) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
		if (in.readInt() != 0xCAFEBABE) {
			throw new IllegalStateException("Not a class file");
		}
		in.readInt();
		Pool pool = new Pool(in);
		in.readUnsignedShort();
		String name = pool.className(in.readUnsignedShort());
		int superIndex = in.readUnsignedShort();
		String superName = superIndex == 0 ? null : pool.className(superIndex);
		in.skipBytes(in.readUnsignedShort() * 2);
		int fields = in.readUnsignedShort();
		for (int i = 0; i < fields; i++) {
			in.skipBytes(6);
			skipAttributes(in);
		}
		List<ScannedMethod> methods = new ArrayList<>();
		int methodCount = in.readUnsignedShort();
		for (int i = 0; i < methodCount; i++) {
			int access = in.readUnsignedShort();
			String methodName = pool.utf(in.readUnsignedShort());
			String descriptor = pool.utf(in.readUnsignedShort());
			int attributes = in.readUnsignedShort();
			ScannedMethod scanned = new ScannedMethod(methodName, descriptor, List.of(), List.of(), Set.of());
			for (int a = 0; a < attributes; a++) {
				String attribute = pool.utf(in.readUnsignedShort());
				int length = in.readInt();
				if (attribute.equals("Code")) {
					scanned = readCode(in, pool, methodName, descriptor, (access & 0x0008) != 0);
				} else {
					in.skipBytes(length);
				}
			}
			methods.add(scanned);
		}
		String sourceFile = null;
		int classAttributes = in.readUnsignedShort();
		for (int a = 0; a < classAttributes; a++) {
			String attribute = pool.utf(in.readUnsignedShort());
			int length = in.readInt();
			if (attribute.equals("SourceFile")) {
				sourceFile = pool.utf(in.readUnsignedShort());
			} else {
				in.skipBytes(length);
			}
		}
		return new ScannedClass(name, superName, sourceFile, methods);
	}

	private static void skipAttributes(DataInputStream in) throws IOException {
		int attributes = in.readUnsignedShort();
		for (int a = 0; a < attributes; a++) {
			in.readUnsignedShort();
			in.skipBytes(in.readInt());
		}
	}

	private static ScannedMethod readCode(DataInputStream in, Pool pool, String name, String descriptor, boolean isStatic) throws IOException {
		in.readUnsignedShort();
		in.readUnsignedShort();
		byte[] code = new byte[in.readInt()];
		in.readFully(code);
		List<Integer> handlers = new ArrayList<>();
		int exceptions = in.readUnsignedShort();
		for (int i = 0; i < exceptions; i++) {
			in.readUnsignedShort();
			in.readUnsignedShort();
			handlers.add(in.readUnsignedShort());
			in.readUnsignedShort();
		}
		Map<Integer, Integer> lines = new HashMap<>();
		int attributes = in.readUnsignedShort();
		for (int a = 0; a < attributes; a++) {
			String attribute = pool.utf(in.readUnsignedShort());
			int length = in.readInt();
			if (attribute.equals("LineNumberTable")) {
				int count = in.readUnsignedShort();
				for (int i = 0; i < count; i++) {
					int pc = in.readUnsignedShort();
					lines.put(pc, in.readUnsignedShort());
				}
			} else {
				in.skipBytes(length);
			}
		}
		return new Simulator(pool, code, name, descriptor, isStatic, handlers, lines).run();
	}

	/** A stack entry. Long and double take two slots in class files, which dup2 and pop2 care about. */
	private record Entry(Value value, boolean wide) {
	}

	private record Frame(List<Entry> stack, Map<Integer, Value> locals) {
	}

	/**
	 * Walks a method's instructions once, in order, keeping track of the stack. Where two paths meet,
	 * anything they disagree on becomes unknown.
	 */
	private static final class Simulator {
		private final Pool pool;
		private final byte[] code;
		private final String name;
		private final String descriptor;
		private final List<Integer> handlers;
		private final Map<Integer, Integer> lines;
		private final Deque<Entry> stack = new ArrayDeque<>();
		private final Map<Integer, Value> locals = new HashMap<>();
		private final Map<Integer, Frame> pending = new HashMap<>();
		private final Set<Integer> parameterSlots = new HashSet<>();
		private final List<int[]> conditionalRanges = new ArrayList<>();
		private final List<PendingCall> calls = new ArrayList<>();
		private final List<Escape> escapes = new ArrayList<>();
		private final Set<Integer> reassigned = new HashSet<>();
		private final List<int[]> loops = new ArrayList<>();
		private final List<int[]> writes = new ArrayList<>();
		private Map<Integer, Set<Integer>> loopChanges = Map.of();
		private int pc;

		private record PendingCall(int pc, MemberRef ref, Value receiver, List<Value> args) {
		}

		Simulator(Pool pool, byte[] code, String name, String descriptor, boolean isStatic, List<Integer> handlers, Map<Integer, Integer> lines) {
			this.pool = pool;
			this.code = code;
			this.name = name;
			this.descriptor = descriptor;
			this.handlers = handlers;
			this.lines = lines;
			int slot = 0;
			if (!isStatic) {
				locals.put(slot, new Local(slot));
				parameterSlots.add(slot);
				slot++;
			}
			for (String type : parameterTypes(descriptor)) {
				locals.put(slot, new Local(slot));
				parameterSlots.add(slot);
				slot += isWide(type) ? 2 : 1;
			}
		}

		/**
		 * The first pass finds the loops. The second knows which variables each loop changes, so it
		 * doesn't take a loop counter's starting value for a constant.
		 */
		ScannedMethod run() {
			Map<Integer, Value> initialLocals = new HashMap<>(locals);
			pass();
			Map<Integer, Set<Integer>> found = changedInLoops();
			if (!found.isEmpty()) {
				loopChanges = found;
				stack.clear();
				locals.clear();
				locals.putAll(initialLocals);
				pending.clear();
				conditionalRanges.clear();
				calls.clear();
				escapes.clear();
				reassigned.clear();
				pass();
			}
			return finish();
		}

		private Map<Integer, Set<Integer>> changedInLoops() {
			Map<Integer, Set<Integer>> changes = new HashMap<>();
			for (int[] loop : loops) {
				for (int[] write : writes) {
					if (write[0] >= loop[0] && write[0] <= loop[1]) {
						changes.computeIfAbsent(loop[0], k -> new HashSet<>()).add(write[1]);
					}
				}
			}
			return changes;
		}

		private void pass() {
			for (int handler : handlers) {
				conditionalRanges.add(new int[] { handler, code.length });
				pending.put(handler, new Frame(List.of(new Entry(UNKNOWN, false)), Map.of()));
			}
			boolean reachable = true;
			pc = 0;
			while (pc < code.length) {
				Frame waiting = pending.remove(pc);
				if (waiting != null) {
					if (reachable) {
						mergeInto(waiting);
					} else {
						stack.clear();
						stack.addAll(waiting.stack());
						locals.clear();
						locals.putAll(waiting.locals());
					}
				}
				for (int slot : loopChanges.getOrDefault(pc, Set.of())) {
					locals.put(slot, UNKNOWN);
				}
				reachable = step();
			}
		}

		private ScannedMethod finish() {
			List<Call> finished = new ArrayList<>();
			for (PendingCall call : calls) {
				finished.add(new Call(call.ref().owner(), call.ref().name(), call.ref().descriptor(), call.receiver(), call.args(),
						lineAt(call.pc()), isConditional(call.pc())));
			}
			return new ScannedMethod(name, descriptor, finished, escapes, reassigned);
		}

		private boolean isConditional(int at) {
			return conditionalRanges.stream().anyMatch(range -> at >= range[0] && at < range[1]);
		}

		private int lineAt(int at) {
			int best = -1;
			int line = -1;
			for (Map.Entry<Integer, Integer> entry : lines.entrySet()) {
				if (entry.getKey() <= at && entry.getKey() > best) {
					best = entry.getKey();
					line = entry.getValue();
				}
			}
			return line;
		}

		private void mergeInto(Frame other) {
			List<Entry> mine = new ArrayList<>(stack);
			List<Entry> theirs = other.stack();
			stack.clear();
			for (int i = 0; i < mine.size(); i++) {
				Entry a = mine.get(i);
				Entry b = i < theirs.size() ? theirs.get(i) : null;
				stack.addLast(b != null && a.value().equals(b.value()) ? a : new Entry(UNKNOWN, a.wide()));
			}
			Set<Integer> slots = new HashSet<>(locals.keySet());
			slots.addAll(other.locals().keySet());
			for (int slot : slots) {
				if (!Objects.equals(locals.get(slot), other.locals().get(slot))) {
					locals.put(slot, UNKNOWN);
				}
			}
		}

		private void jumpTo(int target) {
			if (target <= pc) {
				loops.add(new int[] { target, pc });
			} else {
				Frame existing = pending.get(target);
				Frame now = new Frame(new ArrayList<>(stack), new HashMap<>(locals));
				if (existing == null) {
					pending.put(target, now);
				} else {
					// Merge the two waiting frames using the live state as scratch space.
					Deque<Entry> savedStack = new ArrayDeque<>(stack);
					Map<Integer, Value> savedLocals = new HashMap<>(locals);
					mergeInto(existing);
					pending.put(target, new Frame(new ArrayList<>(stack), new HashMap<>(locals)));
					stack.clear();
					stack.addAll(savedStack);
					locals.clear();
					locals.putAll(savedLocals);
				}
			}
		}

		private void conditionalJump(int target) {
			conditionalRanges.add(target > pc ? new int[] { pc, target } : new int[] { target, pc });
			jumpTo(target);
		}

		/** Runs one instruction. Returns false if the next instruction can't be reached from this one. */
		private boolean step() {
			int op = code[pc] & 0xFF;
			int start = pc;
			int next = start + 1;
			switch (op) {
			case 0 -> {
			}
			case 1 -> push(new Const(null));
			case 2, 3, 4, 5, 6, 7, 8 -> push(new Const(op - 3));
			case 9, 10 -> pushWide(new Const((long) (op - 9)));
			case 11, 12, 13 -> push(new Const((float) (op - 11)));
			case 14, 15 -> pushWide(new Const((double) (op - 14)));
			case 16 -> {
				push(new Const((int) code[pc + 1]));
				next = start + 2;
			}
			case 17 -> {
				push(new Const((int) (short) u2(pc + 1)));
				next = start + 3;
			}
			case 18 -> {
				push(pool.constant(u1(pc + 1)));
				next = start + 2;
			}
			case 19 -> {
				push(pool.constant(u2(pc + 1)));
				next = start + 3;
			}
			case 20 -> {
				pushWide(pool.constant(u2(pc + 1)));
				next = start + 3;
			}
			case 21, 23, 25 -> {
				load(u1(pc + 1), false);
				next = start + 2;
			}
			case 22, 24 -> {
				load(u1(pc + 1), true);
				next = start + 2;
			}
			case 26, 27, 28, 29 -> load(op - 26, false);
			case 30, 31, 32, 33 -> load(op - 30, true);
			case 34, 35, 36, 37 -> load(op - 34, false);
			case 38, 39, 40, 41 -> load(op - 38, true);
			case 42, 43, 44, 45 -> load(op - 42, false);
			case 46, 48, 50, 51, 52, 53 -> binary(false);
			case 47, 49 -> binary(true);
			case 54, 55, 56, 57, 58 -> {
				store(u1(pc + 1));
				next = start + 2;
			}
			case 59, 60, 61, 62 -> store(op - 59);
			case 63, 64, 65, 66 -> store(op - 63);
			case 67, 68, 69, 70 -> store(op - 67);
			case 71, 72, 73, 74 -> store(op - 71);
			case 75, 76, 77, 78 -> store(op - 75);
			case 79, 80, 81, 82, 83, 84, 85, 86 -> {
				escape(pop());
				pop();
				pop();
			}
			case 87 -> pop();
			case 88 -> {
				if (!pop().wide()) {
					pop();
				}
			}
			case 89 -> stack.addLast(stack.peekLast());
			case 90 -> {
				Entry a = pop();
				Entry b = pop();
				stack.addLast(a);
				stack.addLast(b);
				stack.addLast(a);
			}
			case 91 -> {
				Entry a = pop();
				Entry b = pop();
				if (b.wide()) {
					stack.addLast(a);
					stack.addLast(b);
					stack.addLast(a);
				} else {
					Entry c = pop();
					stack.addLast(a);
					stack.addLast(c);
					stack.addLast(b);
					stack.addLast(a);
				}
			}
			case 92 -> {
				Entry a = pop();
				if (a.wide()) {
					stack.addLast(a);
					stack.addLast(a);
				} else {
					Entry b = pop();
					stack.addLast(b);
					stack.addLast(a);
					stack.addLast(b);
					stack.addLast(a);
				}
			}
			case 93 -> {
				Entry a = pop();
				if (a.wide()) {
					Entry b = pop();
					stack.addLast(a);
					stack.addLast(b);
					stack.addLast(a);
				} else {
					Entry b = pop();
					Entry c = pop();
					stack.addLast(b);
					stack.addLast(a);
					stack.addLast(c);
					stack.addLast(b);
					stack.addLast(a);
				}
			}
			case 94 -> {
				// Rare, and never holds anything the scan needs, so everything nearby becomes unknown.
				List<Entry> taken = new ArrayList<>();
				int slots = 0;
				while (slots < 4 && !stack.isEmpty()) {
					Entry e = pop();
					taken.add(0, new Entry(UNKNOWN, e.wide()));
					slots += e.wide() ? 2 : 1;
				}
				int dupSlots = 0;
				List<Entry> copied = new ArrayList<>();
				for (int i = taken.size() - 1; i >= 0 && dupSlots < 2; i--) {
					copied.add(0, taken.get(i));
					dupSlots += taken.get(i).wide() ? 2 : 1;
				}
				copied.forEach(stack::addLast);
				taken.forEach(stack::addLast);
			}
			case 95 -> {
				Entry a = pop();
				Entry b = pop();
				stack.addLast(a);
				stack.addLast(b);
			}
			case 96, 98, 100, 102, 104, 106, 108, 110, 112, 114 -> binary(false);
			case 97, 99, 101, 103, 105, 107, 109, 111, 113, 115 -> binary(true);
			case 116, 118 -> {
				pop();
				push(UNKNOWN);
			}
			case 117, 119 -> {
				pop();
				pushWide(UNKNOWN);
			}
			case 120, 122, 124, 126, 128, 130 -> binary(false);
			case 121, 123, 125, 127, 129, 131 -> binary(true);
			case 132 -> {
				writes.add(new int[] { pc, u1(pc + 1) });
				locals.put(u1(pc + 1), UNKNOWN);
				next = start + 3;
			}
			case 133, 135, 138, 140, 141, 143 -> {
				pop();
				pushWide(UNKNOWN);
			}
			case 134, 136, 137, 139, 142, 144, 145, 146, 147 -> {
				pop();
				push(UNKNOWN);
			}
			case 148, 149, 150, 151, 152 -> binary(false);
			case 153, 154, 155, 156, 157, 158, 198, 199 -> {
				pop();
				conditionalJump(start + (short) u2(pc + 1));
				next = start + 3;
			}
			case 159, 160, 161, 162, 163, 164, 165, 166 -> {
				pop();
				pop();
				conditionalJump(start + (short) u2(pc + 1));
				next = start + 3;
			}
			case 167 -> {
				int target = start + (short) u2(pc + 1);
				if (target < pc || isConditional(pc)) {
					conditionalRanges.add(target > pc ? new int[] { pc, target } : new int[] { target, pc });
				}
				jumpTo(target);
				pc = start + 3;
				return false;
			}
			case 200 -> {
				int target = start + s4(pc + 1);
				conditionalRanges.add(target > pc ? new int[] { pc, target } : new int[] { target, pc });
				jumpTo(target);
				pc = start + 5;
				return false;
			}
			case 170 -> {
				pop();
				int table = (start + 4) & ~3;
				int low = s4(table + 4);
				int high = s4(table + 8);
				conditionalJump(start + s4(table));
				for (int i = 0; i <= high - low; i++) {
					conditionalJump(start + s4(table + 12 + i * 4));
				}
				pc = table + 12 + (high - low + 1) * 4;
				return false;
			}
			case 171 -> {
				pop();
				int table = (start + 4) & ~3;
				int pairs = s4(table + 4);
				conditionalJump(start + s4(table));
				for (int i = 0; i < pairs; i++) {
					conditionalJump(start + s4(table + 8 + i * 8 + 4));
				}
				pc = table + 8 + pairs * 8;
				return false;
			}
			case 172, 173, 174, 175, 176, 191 -> {
				escape(pop());
				pc = next;
				return false;
			}
			case 177 -> {
				pc = next;
				return false;
			}
			case 178 -> {
				MemberRef ref = pool.member(u2(pc + 1));
				pushTyped(new FieldRef(ref.owner(), ref.name()), ref.descriptor());
				next = start + 3;
			}
			case 179 -> {
				escape(pop());
				next = start + 3;
			}
			case 180 -> {
				MemberRef ref = pool.member(u2(pc + 1));
				pop();
				pushTyped(new FieldRef(ref.owner(), ref.name()), ref.descriptor());
				next = start + 3;
			}
			case 181 -> {
				escape(pop());
				pop();
				next = start + 3;
			}
			case 182, 183 -> {
				invoke(pool.member(u2(pc + 1)), true);
				next = start + 3;
			}
			case 184 -> {
				invoke(pool.member(u2(pc + 1)), false);
				next = start + 3;
			}
			case 185 -> {
				invoke(pool.member(u2(pc + 1)), true);
				next = start + 5;
			}
			case 186 -> {
				invoke(pool.member(u2(pc + 1)), false);
				next = start + 5;
			}
			case 187 -> {
				push(new New(pool.className(u2(pc + 1)), start));
				next = start + 3;
			}
			case 188 -> {
				pop();
				push(UNKNOWN);
				next = start + 2;
			}
			case 189 -> {
				pop();
				push(UNKNOWN);
				next = start + 3;
			}
			case 190 -> {
				pop();
				push(UNKNOWN);
			}
			case 192 -> next = start + 3;
			case 193 -> {
				pop();
				push(UNKNOWN);
				next = start + 3;
			}
			case 194, 195 -> pop();
			case 196 -> next = wide(start);
			case 197 -> {
				int dimensions = u1(pc + 3);
				for (int i = 0; i < dimensions; i++) {
					pop();
				}
				push(UNKNOWN);
				next = start + 4;
			}
			default -> throw new IllegalStateException("Unsupported instruction " + op + " in " + name + descriptor);
			}
			pc = next;
			return true;
		}

		private int wide(int start) {
			int op = u1(start + 1);
			int slot = u2(start + 2);
			switch (op) {
			case 21, 23, 25 -> load(slot, false);
			case 22, 24 -> load(slot, true);
			case 54, 55, 56, 57, 58 -> store(slot);
			case 132 -> {
				writes.add(new int[] { pc, slot });
				locals.put(slot, UNKNOWN);
				return start + 6;
			}
			default -> throw new IllegalStateException("Unsupported wide instruction " + op);
			}
			return start + 4;
		}

		private void invoke(MemberRef ref, boolean hasReceiver) {
			List<String> types = parameterTypes(ref.descriptor());
			List<Value> args = new ArrayList<>();
			for (int i = 0; i < types.size(); i++) {
				args.add(0, pop().value());
			}
			Value receiver = hasReceiver ? pop().value() : null;
			calls.add(new PendingCall(pc, ref, receiver, args));
			String returned = ref.descriptor().substring(ref.descriptor().indexOf(')') + 1);
			if (!returned.equals("V")) {
				pushTyped(new Returned(ref.owner(), ref.name(), receiver), returned);
			}
		}

		private void load(int slot, boolean wide) {
			stack.addLast(new Entry(locals.getOrDefault(slot, UNKNOWN), wide));
		}

		private void store(int slot) {
			writes.add(new int[] { pc, slot });
			if (parameterSlots.contains(slot)) {
				reassigned.add(slot);
			}
			locals.put(slot, pop().value());
		}

		private void escape(Entry entry) {
			if (entry.value() instanceof Local local) {
				escapes.add(new Escape(local.slot(), -1, lineAt(pc)));
			} else if (entry.value() instanceof New made) {
				escapes.add(new Escape(-1, made.site(), lineAt(pc)));
			}
		}

		private void binary(boolean wideResult) {
			pop();
			pop();
			if (wideResult) {
				pushWide(UNKNOWN);
			} else {
				push(UNKNOWN);
			}
		}

		private void pushTyped(Value value, String type) {
			if (isWide(type)) {
				pushWide(value);
			} else {
				push(value);
			}
		}

		private void push(Value value) {
			stack.addLast(new Entry(value, false));
		}

		private void pushWide(Value value) {
			stack.addLast(new Entry(value, true));
		}

		private Entry pop() {
			if (stack.isEmpty()) {
				throw new IllegalStateException("Stack ran out at " + pc + " in " + name + descriptor);
			}
			return stack.removeLast();
		}

		private int u1(int at) {
			return code[at] & 0xFF;
		}

		private int u2(int at) {
			return (u1(at) << 8) | u1(at + 1);
		}

		private int s4(int at) {
			return (u1(at) << 24) | (u1(at + 1) << 16) | (u1(at + 2) << 8) | u1(at + 3);
		}
	}

	private static boolean isWide(String type) {
		return type.equals("J") || type.equals("D");
	}

	/** Splits a method descriptor's parameters into their type strings. */
	public static List<String> parameterTypes(String descriptor) {
		List<String> types = new ArrayList<>();
		int i = 1;
		while (descriptor.charAt(i) != ')') {
			int begin = i;
			while (descriptor.charAt(i) == '[') {
				i++;
			}
			if (descriptor.charAt(i) == 'L') {
				i = descriptor.indexOf(';', i);
			}
			i++;
			types.add(descriptor.substring(begin, i));
		}
		return types;
	}

	/** The local variable slot of a parameter. Slot 0 is "this" on a non-static method. */
	public static int slotOfParameter(String descriptor, int parameter, boolean isStatic) {
		int slot = isStatic ? 0 : 1;
		List<String> types = parameterTypes(descriptor);
		for (int i = 0; i < parameter; i++) {
			slot += isWide(types.get(i)) ? 2 : 1;
		}
		return slot;
	}
}
