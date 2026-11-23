package syx.core.wizard;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import syx.core.ClassFileScanner;

/**
 * Numbers the game sets in its code rather than in a data file, read from the player's own jar as plain
 * bytes, so nothing is run and nothing is copied. A step that needs one is only asked when it's found,
 * which is what catches a game update that changes it.
 */
public final class WizardFacts {

	/** Where the physical stats a race can boost are made (BOOSTABLES.java:110-160). */
	static final String PHYSICS = "game.boosting.BOOSTABLES$Physics";

	private final Path jar;
	private final Map<String, Double> bases = new HashMap<>();

	public WizardFacts(Path jar) {
		this.jar = jar;
	}

	/**
	 * A physical stat's base value, as {@code make(key, base, ...)} gives it in the game's code, such as 100
	 * for DEATH_AGE (BOOSTABLES.java:148). Null if the call can't be found or its value isn't a plain number.
	 */
	public Double physicsBase(String key) {
		if (bases.containsKey(key)) {
			return bases.get(key);
		}
		Double found = null;
		try (ClassFileScanner scanner = ClassFileScanner.open(jar)) {
			ClassFileScanner.ScannedClass physics = scanner.scan(PHYSICS);
			if (physics != null) {
				for (ClassFileScanner.ScannedMethod method : physics.methods()) {
					for (ClassFileScanner.Call call : method.calls()) {
						if (call.name().equals("make") && call.args().size() >= 2 && call.args().get(0) instanceof ClassFileScanner.Const name && key.equals(name.value())
								&& call.args().get(1) instanceof ClassFileScanner.Const base && base.value() instanceof Number number) {
							// Two makes for the same key would mean the code changed shape, so neither is trusted.
							found = found == null ? Double.valueOf(number.doubleValue()) : Double.valueOf(Double.NaN);
						}
					}
				}
			}
		} catch (IOException | ClassFileScanner.ScanException e) {
			found = null;
		}
		if (found != null && found.isNaN()) {
			found = null;
		}
		bases.put(key, found);
		return found;
	}
}
