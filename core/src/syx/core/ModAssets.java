package syx.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Reads the files in a mod's version folders, which is what the game loads. Read-only. */
public final class ModAssets {

	/** A file or folder in the mod. */
	public record Node(String name, Path path, boolean folder, List<Node> children) {
	}

	/** One version folder and everything in it. */
	public record VersionTree(String folderName, boolean current, Node root) {
		public String label() {
			return folderName + (current ? " (current version)" : " (outdated)");
		}
	}

	/**
	 * Everything found.
	 *
	 * @param files how many files there are
	 * @param unreadable paths that couldn't be read, which may hide more assets
	 */
	public record Listing(List<VersionTree> versions, int files, List<Path> unreadable) {
	}

	private ModAssets() {
	}

	/** The current version folder comes first. Use a negative version if the game's isn't known. */
	public static Listing read(Path modRoot, int currentMajor) {
		List<Path> unreadable = new ArrayList<>();
		int[] files = { 0 };
		String currentName = "V" + currentMajor;
		List<VersionTree> versions = new ArrayList<>();
		for (Path child : list(modRoot, unreadable)) {
			String name = child.getFileName().toString();
			if (isFolder(child) && name.matches("V\\d+")) {
				versions.add(new VersionTree(name, name.equals(currentName), node(child, files, unreadable)));
			}
		}
		versions.sort(Comparator.comparing(VersionTree::current).reversed().thenComparing(VersionTree::folderName));
		return new Listing(versions, files[0], unreadable);
	}

	private static Node node(Path path, int[] files, List<Path> unreadable) {
		String name = path.getFileName().toString();
		if (!isFolder(path)) {
			files[0]++;
			return new Node(name, path, false, List.of());
		}
		List<Node> children = new ArrayList<>();
		for (Path child : list(path, unreadable)) {
			children.add(node(child, files, unreadable));
		}
		children.sort(Comparator.comparing((Node n) -> !n.folder()).thenComparing(Node::name, String.CASE_INSENSITIVE_ORDER));
		return new Node(name, path, true, children);
	}

	/** Links aren't followed, so they can't lead outside the mod or round in a circle. */
	private static boolean isFolder(Path path) {
		return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
	}

	private static List<Path> list(Path folder, List<Path> unreadable) {
		try (Stream<Path> children = Files.list(folder)) {
			return children.toList();
		} catch (IOException | RuntimeException e) {
			unreadable.add(folder);
			return List.of();
		}
	}
}
