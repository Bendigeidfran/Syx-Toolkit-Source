package syx.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * What a compiled class declares and refers to, read as plain bytes. Nothing is loaded or run. Method
 * bodies are kept only as a one-way hash, so nothing built from this can hold a class's code.
 */
final class ClassFileModel {

	static final int ACC_PUBLIC = 0x0001;
	static final int ACC_PRIVATE = 0x0002;
	static final int ACC_PROTECTED = 0x0004;
	static final int ACC_STATIC = 0x0008;
	static final int ACC_FINAL = 0x0010;
	static final int ACC_BRIDGE = 0x0040;
	static final int ACC_INTERFACE = 0x0200;
	static final int ACC_ABSTRACT = 0x0400;
	static final int ACC_SYNTHETIC = 0x1000;

	static final int OP_GETSTATIC = 0xb2;
	static final int OP_PUTSTATIC = 0xb3;
	static final int OP_GETFIELD = 0xb4;
	static final int OP_PUTFIELD = 0xb5;
	static final int OP_INVOKEVIRTUAL = 0xb6;
	static final int OP_INVOKESPECIAL = 0xb7;
	static final int OP_INVOKESTATIC = 0xb8;
	static final int OP_INVOKEINTERFACE = 0xb9;
	private static final int OP_INVOKEDYNAMIC = 0xba;
	private static final int OP_LDC = 0x12;
	private static final int OP_LDC_W = 0x13;
	private static final int OP_LDC2_W = 0x14;
	private static final int OP_GOTO = 0xa7;
	private static final int OP_JSR = 0xa8;
	private static final int OP_TABLESWITCH = 0xaa;
	private static final int OP_LOOKUPSWITCH = 0xab;
	private static final int OP_NEW = 0xbb;
	private static final int OP_ANEWARRAY = 0xbd;
	private static final int OP_CHECKCAST = 0xc0;
	private static final int OP_INSTANCEOF = 0xc1;
	private static final int OP_WIDE = 0xc4;
	private static final int OP_MULTIANEWARRAY = 0xc5;
	private static final int OP_GOTO_W = 0xc8;
	private static final int OP_JSR_W = 0xc9;

	/** How far back to look for the two constants of a reflective lookup. A list of parameter types is short. */
	private static final int REFLECTION_LOOKBACK = 64;

	/** The numbers compilers give anonymous classes and generated methods, which shift whenever code moves. */
	private static final Pattern NUMBERED = Pattern.compile("\\$\\d+");

	/** A field or method the code uses. The op says how: get, put, or which kind of call. */
	record Use(int op, String owner, String name, String descriptor, int line) {
	}

	/** A class named in the code, by new, a cast, an instanceof, a new array or a class literal. */
	record ClassUse(String name, int line) {
	}

	/** A reflective lookup whose class and name are both written out directly. */
	record ReflectiveLookup(String className, String memberName, boolean field, boolean declaredOnly, int line) {
	}

	/** @param constantHash a hash of its compile-time value, or null if it has none */
	record FieldDef(String name, String descriptor, int access, String constantHash) {
	}

	/** @param bodyHash a hash of the normalised code, or null if the method has no code */
	record MethodDef(String name, String descriptor, int access, String bodyHash, List<Use> uses, List<ClassUse> classUses, List<ReflectiveLookup> lookups) {
	}

	final String name;
	final String superName;
	final int major;
	final int access;
	final List<String> interfaces;
	final List<FieldDef> fields;
	final List<MethodDef> methods;

	private ClassFileModel(String name, String superName, int major, int access, List<String> interfaces, List<FieldDef> fields, List<MethodDef> methods) {
		this.name = name;
		this.superName = superName;
		this.major = major;
		this.access = access;
		this.interfaces = interfaces;
		this.fields = fields;
		this.methods = methods;
	}

	/** Replaces every compiler-given number, so renumbering doesn't look like a change. */
	static String neutral(String text) {
		return NUMBERED.matcher(text).replaceAll("\\$#");
	}

	private record RawMethod(String name, String descriptor, int access, byte[] code, int[][] exceptions, int[][] lines) {
	}

	private record RawField(String name, String descriptor, int access, int constantIndex) {
	}

	static ClassFileModel read(byte[] bytes) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
		if (in.readInt() != 0xCAFEBABE) {
			throw new IOException("Not a class file");
		}
		in.readUnsignedShort();
		int major = in.readUnsignedShort();
		ClassFileScanner.Pool pool = new ClassFileScanner.Pool(in);
		int access = in.readUnsignedShort();
		String name = pool.className(in.readUnsignedShort());
		int superIndex = in.readUnsignedShort();
		String superName = superIndex == 0 ? null : pool.className(superIndex);
		List<String> interfaces = new ArrayList<>();
		int interfaceCount = in.readUnsignedShort();
		for (int i = 0; i < interfaceCount; i++) {
			interfaces.add(pool.className(in.readUnsignedShort()));
		}
		List<RawField> rawFields = new ArrayList<>();
		int fieldCount = in.readUnsignedShort();
		for (int i = 0; i < fieldCount; i++) {
			rawFields.add(readField(in, pool));
		}
		List<RawMethod> rawMethods = new ArrayList<>();
		int methodCount = in.readUnsignedShort();
		for (int i = 0; i < methodCount; i++) {
			rawMethods.add(readMethod(in, pool));
		}
		// Bootstrap methods come after the methods that use them, so code can only be walked once they're read.
		int[][] bootstraps = new int[0][];
		int classAttributes = in.readUnsignedShort();
		for (int a = 0; a < classAttributes; a++) {
			String attribute = pool.utf(in.readUnsignedShort());
			int length = in.readInt();
			if (attribute.equals("BootstrapMethods")) {
				bootstraps = new int[in.readUnsignedShort()][];
				for (int b = 0; b < bootstraps.length; b++) {
					int method = in.readUnsignedShort();
					int[] entry = new int[in.readUnsignedShort() + 1];
					entry[0] = method;
					for (int arg = 1; arg < entry.length; arg++) {
						entry[arg] = in.readUnsignedShort();
					}
					bootstraps[b] = entry;
				}
			} else {
				in.skipBytes(length);
			}
		}
		List<FieldDef> fields = new ArrayList<>();
		for (RawField field : rawFields) {
			fields.add(new FieldDef(field.name(), field.descriptor(), field.access(), field.constantIndex() == 0 ? null : hashConstant(pool, bootstraps, field.constantIndex())));
		}
		List<MethodDef> methods = new ArrayList<>();
		for (RawMethod method : rawMethods) {
			methods.add(new Walker(pool, bootstraps, method).walk());
		}
		return new ClassFileModel(name, superName, major, access, interfaces, fields, methods);
	}

	private static RawField readField(DataInputStream in, ClassFileScanner.Pool pool) throws IOException {
		int access = in.readUnsignedShort();
		String name = pool.utf(in.readUnsignedShort());
		String descriptor = pool.utf(in.readUnsignedShort());
		int constantIndex = 0;
		int attributes = in.readUnsignedShort();
		for (int a = 0; a < attributes; a++) {
			String attribute = pool.utf(in.readUnsignedShort());
			int length = in.readInt();
			if (attribute.equals("ConstantValue")) {
				constantIndex = in.readUnsignedShort();
			} else {
				in.skipBytes(length);
			}
		}
		return new RawField(name, descriptor, access, constantIndex);
	}

	private static RawMethod readMethod(DataInputStream in, ClassFileScanner.Pool pool) throws IOException {
		int access = in.readUnsignedShort();
		String name = pool.utf(in.readUnsignedShort());
		String descriptor = pool.utf(in.readUnsignedShort());
		byte[] code = null;
		int[][] exceptions = new int[0][];
		int[][] lines = new int[0][];
		int attributes = in.readUnsignedShort();
		for (int a = 0; a < attributes; a++) {
			String attribute = pool.utf(in.readUnsignedShort());
			int length = in.readInt();
			if (!attribute.equals("Code")) {
				in.skipBytes(length);
				continue;
			}
			in.readUnsignedShort();
			in.readUnsignedShort();
			code = new byte[in.readInt()];
			in.readFully(code);
			exceptions = new int[in.readUnsignedShort()][];
			for (int e = 0; e < exceptions.length; e++) {
				exceptions[e] = new int[] { in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort() };
			}
			int codeAttributes = in.readUnsignedShort();
			List<int[]> found = new ArrayList<>();
			for (int c = 0; c < codeAttributes; c++) {
				String codeAttribute = pool.utf(in.readUnsignedShort());
				int codeLength = in.readInt();
				if (codeAttribute.equals("LineNumberTable")) {
					int count = in.readUnsignedShort();
					for (int l = 0; l < count; l++) {
						found.add(new int[] { in.readUnsignedShort(), in.readUnsignedShort() });
					}
				} else {
					in.skipBytes(codeLength);
				}
			}
			lines = found.toArray(new int[0][]);
		}
		return new RawMethod(name, descriptor, access, code, exceptions, lines);
	}

	private static String hashConstant(ClassFileScanner.Pool pool, int[][] bootstraps, int index) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		writeConstant(out, pool, bootstraps, index);
		return hash(bytes.toByteArray());
	}

	/** SHA-256 cut to 128 bits, as lowercase hex. */
	static String hash(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void writeText(DataOutputStream out, String text) throws IOException {
		byte[] utf = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		out.writeInt(utf.length);
		out.write(utf);
	}

	/** A constant by what it means, never by where it sits in the pool, which moves between builds. */
	private static void writeConstant(DataOutputStream out, ClassFileScanner.Pool pool, int[][] bootstraps, int index) throws IOException {
		int tag = pool.tags[index];
		out.writeByte(tag);
		switch (tag) {
		case 3 -> out.writeInt((Integer) pool.entries[index]);
		case 4 -> out.writeInt(Float.floatToRawIntBits((Float) pool.entries[index]));
		case 5 -> out.writeLong((Long) pool.entries[index]);
		case 6 -> out.writeLong(Double.doubleToRawLongBits((Double) pool.entries[index]));
		case 7 -> writeText(out, neutral(pool.className(index)));
		case 8 -> writeText(out, pool.utf(pool.first[index]));
		case 15 -> {
			out.writeByte(pool.first[index]);
			writeMember(out, pool.member(pool.second[index]));
		}
		case 16 -> writeText(out, neutral(pool.utf(pool.first[index])));
		case 17, 18 -> {
			writeBootstrap(out, pool, bootstraps, pool.first[index]);
			// Its first number is the bootstrap method, so it has no owner to read.
			int nameAndType = pool.second[index];
			writeText(out, neutral(pool.utf(pool.first[nameAndType])));
			writeText(out, neutral(pool.utf(pool.second[nameAndType])));
		}
		default -> throw new IOException("Unexpected constant type " + tag);
		}
	}

	private static void writeMember(DataOutputStream out, ClassFileScanner.MemberRef member) throws IOException {
		writeText(out, neutral(member.owner()));
		writeText(out, neutral(member.name()));
		writeText(out, neutral(member.descriptor()));
	}

	private static void writeBootstrap(DataOutputStream out, ClassFileScanner.Pool pool, int[][] bootstraps, int which) throws IOException {
		if (which >= bootstraps.length) {
			throw new IOException("A call names a bootstrap method the class doesn't have");
		}
		for (int index : bootstraps[which]) {
			writeConstant(out, pool, bootstraps, index);
		}
	}

	/** Walks one method's code for what it refers to, and hashes it in a form that doesn't depend on layout. */
	private static final class Walker {
		private final ClassFileScanner.Pool pool;
		private final int[][] bootstraps;
		private final RawMethod method;
		private final byte[] code;
		private final List<Use> uses = new ArrayList<>();
		private final List<ClassUse> classUses = new ArrayList<>();
		private final List<ReflectiveLookup> lookups = new ArrayList<>();
		private final TreeMap<Integer, Integer> lineStarts = new TreeMap<>();
		/** For each instruction in order: a class literal's name, a string literal, or a marker for a call. */
		private final List<Object> recent = new ArrayList<>();

		private static final Object CALL = new Object();
		private static final Object OTHER = new Object();

		private record ClassLiteral(String name) {
		}

		Walker(ClassFileScanner.Pool pool, int[][] bootstraps, RawMethod method) {
			this.pool = pool;
			this.bootstraps = bootstraps;
			this.method = method;
			this.code = method.code();
			for (int[] line : method.lines()) {
				lineStarts.put(line[0], line[1]);
			}
		}

		MethodDef walk() throws IOException {
			if (code == null) {
				return new MethodDef(method.name(), method.descriptor(), method.access(), null, List.of(), List.of(), List.of());
			}
			// Jumps are hashed by instruction number, because byte offsets change with the width of nearby operands.
			int[] instructionAt = new int[code.length + 1];
			int count = 0;
			for (int pc = 0; pc < code.length; pc += length(pc)) {
				instructionAt[pc] = count++;
			}
			instructionAt[code.length] = count;
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			for (int pc = 0; pc < code.length; pc += length(pc)) {
				emit(pc, out, instructionAt);
			}
			for (int[] entry : method.exceptions()) {
				out.writeInt(instructionAt[entry[0]]);
				out.writeInt(instructionAt[entry[1]]);
				out.writeInt(instructionAt[entry[2]]);
				writeText(out, entry[3] == 0 ? "" : neutral(pool.className(entry[3])));
			}
			return new MethodDef(method.name(), method.descriptor(), method.access(), hash(bytes.toByteArray()), uses, classUses, lookups);
		}

		private int lineAt(int pc) {
			var entry = lineStarts.floorEntry(pc);
			return entry == null ? -1 : entry.getValue();
		}

		private int u1(int pc) {
			return code[pc] & 0xFF;
		}

		private int u2(int pc) {
			return (u1(pc) << 8) | u1(pc + 1);
		}

		private int s2(int pc) {
			return (short) u2(pc);
		}

		private int s4(int pc) {
			return (u1(pc) << 24) | (u1(pc + 1) << 16) | (u1(pc + 2) << 8) | u1(pc + 3);
		}

		private int length(int pc) {
			int op = u1(pc);
			if (op == OP_TABLESWITCH) {
				int start = (pc + 4) & ~3;
				return start + 12 + (s4(start + 8) - s4(start + 4) + 1) * 4 - pc;
			}
			if (op == OP_LOOKUPSWITCH) {
				int start = (pc + 4) & ~3;
				return start + 8 + s4(start + 4) * 8 - pc;
			}
			if (op == OP_WIDE) {
				return u1(pc + 1) == 0x84 ? 6 : 4;
			}
			if (op == 0x10 || op == OP_LDC || (op >= 0x15 && op <= 0x19) || (op >= 0x36 && op <= 0x3a) || op == 0xa9 || op == 0xbc) {
				return 2;
			}
			if (op == 0x11 || op == OP_LDC_W || op == OP_LDC2_W || op == 0x84 || (op >= 0x99 && op <= OP_JSR) || op == 0xc6 || op == 0xc7
					|| (op >= OP_GETSTATIC && op <= OP_INVOKESTATIC) || op == OP_NEW || op == OP_ANEWARRAY || op == OP_CHECKCAST || op == OP_INSTANCEOF) {
				return 3;
			}
			if (op == OP_MULTIANEWARRAY) {
				return 4;
			}
			if (op == OP_INVOKEINTERFACE || op == OP_INVOKEDYNAMIC || op == OP_GOTO_W || op == OP_JSR_W) {
				return 5;
			}
			return 1;
		}

		private void emit(int pc, DataOutputStream out, int[] instructionAt) throws IOException {
			int op = u1(pc);
			Object seen = OTHER;
			if (op == OP_LDC || op == OP_LDC_W || op == OP_LDC2_W) {
				// Which form of ldc a constant gets depends on where it sits in the pool.
				int index = op == OP_LDC ? u1(pc + 1) : u2(pc + 1);
				out.writeByte(OP_LDC);
				writeConstant(out, pool, bootstraps, index);
				seen = literal(index, pc);
			} else if (op >= OP_GETSTATIC && op <= OP_INVOKEINTERFACE) {
				out.writeByte(op);
				ClassFileScanner.MemberRef member = pool.member(u2(pc + 1));
				writeMember(out, member);
				uses.add(new Use(op, member.owner(), member.name(), member.descriptor(), lineAt(pc)));
				if (op >= OP_INVOKEVIRTUAL) {
					checkLookup(member, pc);
					seen = CALL;
				}
			} else if (op == OP_INVOKEDYNAMIC) {
				out.writeByte(op);
				writeConstant(out, pool, bootstraps, u2(pc + 1));
				useHandles(pc);
				seen = CALL;
			} else if (op == OP_NEW || op == OP_ANEWARRAY || op == OP_CHECKCAST || op == OP_INSTANCEOF || op == OP_MULTIANEWARRAY) {
				out.writeByte(op);
				String named = pool.className(u2(pc + 1));
				writeText(out, neutral(named));
				if (op == OP_MULTIANEWARRAY) {
					out.writeByte(u1(pc + 3));
				}
				addClassUse(named, pc);
			} else if (op == OP_TABLESWITCH) {
				out.writeByte(op);
				int start = (pc + 4) & ~3;
				int low = s4(start + 4);
				int high = s4(start + 8);
				out.writeInt(instructionAt[pc + s4(start)]);
				out.writeInt(low);
				out.writeInt(high);
				for (int i = 0; i <= high - low; i++) {
					out.writeInt(instructionAt[pc + s4(start + 12 + i * 4)]);
				}
			} else if (op == OP_LOOKUPSWITCH) {
				out.writeByte(op);
				int start = (pc + 4) & ~3;
				int pairs = s4(start + 4);
				out.writeInt(instructionAt[pc + s4(start)]);
				for (int i = 0; i < pairs; i++) {
					out.writeInt(s4(start + 8 + i * 8));
					out.writeInt(instructionAt[pc + s4(start + 12 + i * 8)]);
				}
			} else if ((op >= 0x99 && op <= OP_JSR) || op == 0xc6 || op == 0xc7) {
				out.writeByte(op);
				out.writeInt(instructionAt[pc + s2(pc + 1)]);
			} else if (op == OP_GOTO_W || op == OP_JSR_W) {
				out.writeByte(op == OP_GOTO_W ? OP_GOTO : OP_JSR);
				out.writeInt(instructionAt[pc + s4(pc + 1)]);
			} else if (op == OP_WIDE) {
				// The same as the narrow form, with a local variable number too big to fit.
				out.writeByte(u1(pc + 1));
				out.writeShort(u2(pc + 2));
				if (u1(pc + 1) == 0x84) {
					out.writeShort(u2(pc + 4));
				}
			} else {
				out.write(code, pc, length(pc));
			}
			recent.add(seen);
		}

		private void addClassUse(String named, int pc) {
			String element = elementClass(named);
			if (element != null) {
				classUses.add(new ClassUse(element, lineAt(pc)));
			}
		}

		private Object literal(int index, int pc) {
			return switch (pool.tags[index]) {
			case 7 -> {
				String named = pool.className(index);
				addClassUse(named, pc);
				yield new ClassLiteral(named);
			}
			case 8 -> pool.utf(pool.first[index]);
			default -> OTHER;
			};
		}

		/** A method reference such as Foo::bar links through its bootstrap arguments, so it counts as a use. */
		private void useHandles(int pc) {
			int[] bootstrap = bootstraps[pool.first[u2(pc + 1)]];
			for (int i = 1; i < bootstrap.length; i++) {
				if (pool.tags[bootstrap[i]] == 15) {
					ClassFileScanner.MemberRef member = pool.member(pool.second[bootstrap[i]]);
					uses.add(new Use(opOfHandle(pool.first[bootstrap[i]]), member.owner(), member.name(), member.descriptor(), lineAt(pc)));
				}
			}
		}

		private static int opOfHandle(int kind) {
			return switch (kind) {
			case 1 -> OP_GETFIELD;
			case 2 -> OP_GETSTATIC;
			case 3 -> OP_PUTFIELD;
			case 4 -> OP_PUTSTATIC;
			case 5 -> OP_INVOKEVIRTUAL;
			case 6 -> OP_INVOKESTATIC;
			case 7, 8 -> OP_INVOKESPECIAL;
			default -> OP_INVOKEINTERFACE;
			};
		}

		/** Foo.class.getDeclaredField("x"), or a method lookup with its parameter types after the name. */
		private void checkLookup(ClassFileScanner.MemberRef member, int pc) {
			if (!member.owner().equals("java/lang/Class")) {
				return;
			}
			boolean field = member.name().equals("getDeclaredField") || member.name().equals("getField");
			boolean declared = member.name().equals("getDeclaredField") || member.name().equals("getDeclaredMethod");
			if (!field && !member.name().equals("getDeclaredMethod") && !member.name().equals("getMethod")) {
				return;
			}
			int oldest = Math.max(1, recent.size() - (field ? 1 : REFLECTION_LOOKBACK));
			for (int i = recent.size() - 1; i >= oldest; i--) {
				Object seen = recent.get(i);
				if (seen == CALL) {
					return;
				}
				if (seen instanceof String text) {
					if (recent.get(i - 1) instanceof ClassLiteral literal && elementClass(literal.name()) != null) {
						lookups.add(new ReflectiveLookup(elementClass(literal.name()), text, field, declared, lineAt(pc)));
					}
					return;
				}
			}
		}
	}

	/** A class name without any array around it, or null for an array of a primitive. */
	static String elementClass(String named) {
		if (!named.startsWith("[")) {
			return named;
		}
		String element = named.substring(named.lastIndexOf('[') + 1);
		return element.startsWith("L") && element.endsWith(";") ? element.substring(1, element.length() - 1) : null;
	}
}
