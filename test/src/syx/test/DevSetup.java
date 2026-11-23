package syx.test;

import syx.core.GameFolderFinder;
import syx.core.ModEntry;
import syx.core.ModLibrary;
import syx.core.ModsFolder;

/** Development helper: prints what the app finds on this machine. Read-only. */
public final class DevSetup {

	private DevSetup() {
	}

	public static void main(String[] args) {
		GameFolderFinder.Result result = GameFolderFinder.find();
		System.out.println("game=" + result.found().orElse(null) + " placesChecked=" + result.checked().size());
		result.checked().forEach(p -> System.out.println("  checked " + p));
		System.out.println("mods=" + ModsFolder.standard());
		if (result.found().isPresent()) {
			ModLibrary.Listing listing = ModLibrary.list(ModsFolder.standard(), result.found().get());
			System.out.println("listingProblem=" + listing.problem());
			for (ModEntry mod : listing.mods()) {
				System.out.println("  " + mod.describe() + " | versions=" + mod.versionFolders() + " outdated=" + mod.outdated());
			}
		}
	}
}
