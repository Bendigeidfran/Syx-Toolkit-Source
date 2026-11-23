package syx.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tracks what one operation creates, so a failure can remove exactly that and nothing else. */
final class CreatedPaths {

	private final List<Path> created = new ArrayList<>();

	/** Creates a folder that must not exist yet. */
	void createFolder(Path folder) throws IOException {
		Files.createDirectory(folder);
		created.add(folder);
	}

	/** Creates a folder and any missing parents. Folders that already exist are left alone. */
	void createMissingFolders(Path folder) throws IOException {
		List<Path> missing = new ArrayList<>();
		for (Path p = folder.toAbsolutePath(); p != null && !Files.exists(p); p = p.getParent()) {
			missing.add(p);
		}
		Collections.reverse(missing);
		for (Path p : missing) {
			createFolder(p);
		}
	}

	void add(Path path) {
		created.add(path);
	}

	/** Removes everything recorded, newest first. Anything that can't be removed is never forced. */
	void removeAll() {
		for (int i = created.size() - 1; i >= 0; i--) {
			try {
				Files.deleteIfExists(created.get(i));
			} catch (IOException e) {
				// It stays where it is. Nothing that existed before this operation is at risk.
			}
		}
		created.clear();
	}
}
