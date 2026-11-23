package syx.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Moves finished files into place together, putting back what was there if any of them fails. */
public final class SafeWrite {

	/**
	 * One file to put in place.
	 *
	 * @param temp the finished file, already checked
	 * @param original what the target held, or null if it must not exist yet
	 */
	public record Change(Path target, Path temp, byte[] original) {
	}

	private SafeWrite() {
	}

	public static void commit(List<Change> changes) throws IOException {
		List<Change> done = new ArrayList<>();
		try {
			for (Change change : changes) {
				if (change.original() == null) {
					// Without REPLACE_EXISTING, this refuses to overwrite a file that has just appeared.
					Files.move(change.temp(), change.target());
				} else {
					replace(change.temp(), change.target());
				}
				done.add(change);
			}
		} catch (IOException | RuntimeException e) {
			for (int i = done.size() - 1; i >= 0; i--) {
				restore(done.get(i), e);
			}
			throw e;
		}
	}

	private static void replace(Path temp, Path target) throws IOException {
		try {
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void restore(Change change, Exception cause) {
		try {
			if (change.original() == null) {
				Files.deleteIfExists(change.target());
			} else {
				Files.write(change.target(), change.original());
			}
		} catch (IOException e) {
			cause.addSuppressed(e);
		}
	}
}
