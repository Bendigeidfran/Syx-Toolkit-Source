package syx.core.wizard;

import java.util.List;

/** Every wizard. A new asset type's wizard is a definition added here. */
public final class Wizards {

	/** What the wizard asks before any of them starts. Picking one is the conversation's first answer. */
	public static final String START_PROMPT = "What would you like to make?";

	private static final List<Wizard.Definition> ALL = List.of(RaceWizard.definition());

	/**
	 * The Portrait Design Wizard, which edits an existing race's portraits rather than making an asset, so it has no
	 * steps here: {@link PortraitWizardEngine} asks its questions. It's listed with the others for agents.
	 */
	public static final Wizard.Definition PORTRAIT = new Wizard.Definition(PortraitWizardEngine.ID, "Race", PortraitWizardEngine.TITLE, List.of(), name -> java.util.Map.of());

	/** Whether an id names the Portrait Design Wizard. */
	public static boolean isPortrait(String id) {
		return PortraitWizardEngine.ID.equalsIgnoreCase(id);
	}

	private Wizards() {
	}

	public static List<Wizard.Definition> all() {
		return ALL;
	}

	/** By id, ignoring case, or null. */
	public static Wizard.Definition find(String id) {
		return id == null ? null : ALL.stream().filter(definition -> definition.id().equalsIgnoreCase(id)).findFirst().orElse(null);
	}
}
