package syx.core.wizard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syx.core.TagSpec;
import syx.core.wizard.Wizard.Choice;
import syx.core.wizard.Wizard.ChoiceStep;
import syx.core.wizard.Wizard.DoneStep;
import syx.core.wizard.Wizard.Field;
import syx.core.wizard.Wizard.FieldsStep;
import syx.core.wizard.Wizard.LookStep;
import syx.core.wizard.Wizard.NameStep;
import syx.core.wizard.Wizard.NumberStep;

/**
 * The race wizard's questions. Each says what its answer does in the game, so each was checked in the game's
 * code, which its evidence names (0.71.44). A question whose effect couldn't be pinned down isn't asked, and
 * stays in the editor: the price of slaves (a recipe in Creator.java:109-141), RESOURCE_RPICE_CAP (only shown),
 * WORLD_BUILDING, equipment, BOOST beyond lifespan, STATS and STATS_ON_SPAWN. Pictures are left to a wizard of
 * their own.
 */
public final class RaceWizard {

	static final String MUL = "BOOST.PHYSICS_DEATH_AGE>MUL";
	static final String ADD = "BOOST.PHYSICS_DEATH_AGE>ADD";
	/** The last question, which can hand over to the portrait wizard. Its answers change nothing in the files. */
	public static final String PORTRAITS_STEP = "portraits";
	public static final String DESIGN_NOW = "Yes, design them now";

	private RaceWizard() {
	}

	public static Wizard.Definition definition() {
		return new Wizard.Definition("race", "Race", "Race", List.of(
				new NameStep("name", "What's your race called?",
						"Write it as players will see it, for one of them, such as Dondorian. You can change the other forms of it next.",
						"INFO.java:16-25 reads NAME as the race's name.", List.of("NAME")),
				new LookStep("look", "Which race is yours most like?",
						"Yours starts as a copy of it: its looks, numbers and words. Then you change what's different. Its own pictures are copied for you to repaint.",
						"A new race is a copy of a vanilla look's files under its own name (RaceSchemas). Only pictures named for that race are copied (SpriteCopy).", List.of("SPRITE_FILE")),
				new FieldsStep("words", "How should the game write about {names}?",
						"These words turn up all over the game's text.",
						"INFO.java:16-25 (NAME, NAMES). RaceInfo.java:49-50 (POSSESSIVE, POSSESSIVES). ThingsCorpses.java:711 and UISubject.java:73 put POSSESSIVE before a word; UpdaterRegions.java:141 uses POSSESSIVES for raiders.",
						List.of(
								new Field("NAME", "One of them", "The race's name for one, such as Dondorian."),
								new Field("NAMES", "More than one", "Such as Dondorians."),
								new Field("POSSESSIVE", "Describing one of them", "Put before a word, as in \"{possessive} corpse\"."),
								new Field("POSSESSIVES", "Describing a group of them", "Used for a group, such as their raiders."))),
				new FieldsStep("description", "Describe {names}.",
						"A short description, and a longer one for those who want to know more.",
						"INFO.java:16-25 reads DESC. RaceInfo.java:51 reads DESC_LONG.",
						List.of(
								new Field("DESC", "In a line or two", ""),
								new Field("DESC_LONG", "At more length", ""))),
				new ChoiceStep("playable", "Should {names} be a playable race?",
						"The game picks from the playable races for the world's regions, raiders and tourists.",
						"Race.java:63 reads PLAYABLE. RACES.java:50-62 lists the playable races, which RDRaces.java:232, Raider.java:44 and TOURISM.java:89 pick from.",
						List.of(new Choice("Yes", Map.of("PLAYABLE", "true")), new Choice("No", Map.of("PLAYABLE", "false")))),
				new NumberStep("lifespan", "How old can a {name} live to be?",
						"Each {name} dies of old age somewhere between about half this and all of it, most of them near the end.",
						"StatsPopulation.java:448-464,936-951: a person dies at 0.45 to 1 times the race's lifespan, plus up to a year. The lifespan is BOOSTABLES.java:148's base for DEATH_AGE, read from the game's code, through the race's BOOST PHYSICS_DEATH_AGE>MUL and >ADD (Race.java:205-225).",
						"years", 1, null, false, List.of(MUL), new Lifespan()),
				new FieldsStep("growing", "How do young {names} grow up?",
						"Counted in game days.",
						"Physics.java:26-33. StatsReproduction.java:302-308: a race only has children when BABY_DAYS is above 0. AIModule_Child.java:181-195: children grow over CHILD_DAYS. StatsPopulation.java:468: an adult is one older than both added together.",
						List.of(
								new Field("PROPERTIES.BABY_DAYS", "Days as a baby", "0 means {names} don't have children of their own."),
								new Field("PROPERTIES.CHILD_DAYS", "Days as a child after that", "They grow to their full size over this time."))),
				new FieldsStep("body", "What are {names} like in body?",
						"",
						"Physics.java:24-29. Humanoid.java:83-86 sizes each person from HEIGHT and WIDTH, and varies the height a little. Humanoids.java:279-282 uses WIDTH for the space one takes up. ThingsCorpses.java:405 checks CORPSE_DECAY. AIModule_Home.java:526-529 only puts one to sleep if SLEEPS.",
						List.of(
								new Field("PROPERTIES.HEIGHT", "Height", "Each one varies a little from this."),
								new Field("PROPERTIES.WIDTH", "Width", "How much space one takes up."),
								new Field("PROPERTIES.CORPSE_DECAY", "Their bodies rot away after death", ""),
								// AIModule_Home.java:526-529: lying down at home, one that sleeps goes to sleep, and one that doesn't stands a while.
								new Field("PROPERTIES.SLEEPS", "They sleep", "If not, they stand at home for a while instead of sleeping."))),
				new FieldsStep("food", "What do {names} like to eat and drink?",
						"",
						"RacePreferrence.java reads PREFERRED.FOOD and PREFERRED.DRINK.",
						List.of(
								new Field("PREFERRED.FOOD", "Food", ""),
								new Field("PREFERRED.DRINK", "Drink", ""))),
				new FieldsStep("homeland", "Where in the world do {names} thrive?",
						"",
						"RacePopulation.java:30-39. RDRace.java:245-249 weighs each region of the world by its climate and land. Immigration.java:64 uses the climate for immigrants to a city.",
						List.of(
								new Field("POPULATION.CLIMATE", "How well they live in each climate", ""),
								new Field("POPULATION.TERRAIN", "How much they like each kind of land", ""))),
				new FieldsStep("numbers", "How common are {names}?",
						"",
						"RacePopulation.java:29,40. RDRace.java:80-81,130 uses these for each region of the world, and Immigration.java:187 caps immigrants to a city by the share.",
						List.of(
								new Field("POPULATION.MAX", "The most of a region's people they can be", ""),
								new Field("POPULATION.GROWTH", "How fast their numbers grow", ""))),
				new FieldsStep("surroundings", "What surroundings do {names} like?",
						"From 0, not at all, to 1, very much.",
						"RacePreferrence.java:130-143,166-176. StatsEnv.java:228-245 rates the building, mountain or open ground where one stands and the four tiles beside it; :249-271 rates a road under them, counting a worn one for less; :274-277 rates a pool they're in. BUILDING_PREF.java:22 and Floors.java:616 read each structure's and road's own liking for each race, which is used where the race sets none. Pools start at 0 (RacePreferrence.java:57).",
						List.of(
								new Field("PREFERRED.STRUCTURE", "Being among each kind of building", "Inside each kind of building, on a mountain, or out in the open. One not listed uses that building's own liking for {names}."),
								new Field("PREFERRED.ROAD", "Walking on each kind of road", "A worn road counts for less. One not listed uses that road's own liking for {names}."),
								new Field("PREFERRED.POOL", "Bathing in each kind of pool", "One not listed is 0."))),
				new FieldsStep("work", "Which jobs do {names} enjoy?",
						"The higher the number, the happier they are working there. Below 0, they dislike it. Jobs not listed use the job's own setting.",
						"RacePreferrence.java:149-165 reads WORK from -10000 to 10000, starting from each job's own default, and skips a room nobody works in. StatsWork.java:139-146 rates an employed one's job by it. RoomEmployment.java:133-136 sets work priority from it. TourismRace.java:47-76 shows tourists the rooms of their five best-liked jobs above 0.",
						List.of(new Field("PREFERRED.WORK", "How much they enjoy each job", ""))),
				new FieldsStep("others", "How do {names} get on with other races?",
						"From 0, they can't stand them, to 1, they're happy alongside them. A race not listed counts as 1.",
						"RacePreferrence.java:207 starts every race at 1, and :278-322 reads OTHER_RACES_REVERSE, then OTHER_RACES, each from 0 to 1, so a race's own OTHER_RACES wins over another's REVERSE. StatsEnv.java:109-137 rates the races one lives among by it. King.java:106 and RaiderArmy.java:227 also use it.",
						List.of(
								new Field("PREFERRED.OTHER_RACES", "How much {names} like each race", ""),
								new Field("PREFERRED.OTHER_RACES_REVERSE", "How much each race likes {names}", "Unless that race says otherwise in its own file."))),
				new FieldsStep("law", "How do {names} feel about crime and punishment?",
						"From 0 to 1. Crimes and punishments not listed use the game's own settings.",
						"RacePreferrence.java:101-128 starts from each crime's and punishment's own setting (CRIMES.java:185-186, CRIME_PUNISHMENTS.java:147) and reads FREEDOM, LAW and PUNISHMENT from 0 to 1. CRIMES.java:228-258 scales how punishing a crime affects their freedom by FREEDOM, and their sense of law by LAW, each times the punishment's own amount (CRIME_PUNISHMENTS.java:178-196).",
						List.of(
								Wizard.Field.column("PREFERRED.CRIME", "FREEDOM", TagSpec.Reference.CRIMES, "How much punishing each crime affects their freedom", ""),
								Wizard.Field.column("PREFERRED.CRIME", "LAW", TagSpec.Reference.CRIMES, "How much punishing each crime affects their sense of law", ""),
								new Field("PREFERRED.PUNISHMENT", "How much each punishment counts with them", "Both effects above are scaled by this. None is the stocks."))),
				new FieldsStep("trade", "What do factions of {names} charge?",
						"A multiple of the usual price. Resources not listed use the resource's own.",
						"RacePreferrence.java:238-254 starts from each resource's own multiple and reads RESOURCE_RPICE_MUL from 0 to 100. NPCStockpile's NPCRes.java:102-106 multiplies a faction's prices by its race's. RESOURCE_RPICE_CAP is only shown in a tooltip (Goods.java:774), so it isn't asked.",
						List.of(new Field("PREFERRED.RESOURCE_RPICE_MUL", "Price of each resource", ""))),
				new FieldsStep("traits", "What traits do {names} tend to have?",
						"The chance, from 0 to 1, that each new one gets each trait. Some traits rule others out.",
						"TRAITS.java:75-82 reads TRAIT from 0 to 1, by RaceStats.java:38. TRAITS.java:85-108 gives each new person each trait with that chance, in a random order, skipping any that one they have already rules out. The vanilla files write TRAITS, which isn't read, so a race copied from them starts with no traits here.",
						List.of(new Field("TRAIT", "Chance of each trait", ""))),
				new FieldsStep("remains", "What do {names} leave behind?",
						"",
						"Race.java:93-103. WorkCannibal.java:215-238 gives each cut of a corpse 1 plus up to a quarter of each amount. M_PlanBarber.java:37-48 makes the grooming amounts once for each open side of the barber's chair.",
						List.of(
								new Field("RESOURCE", "What butchering one of their bodies gives", "Each cut gives up to a quarter of each amount, plus one."),
								new Field("RESOURCE_GROOMING", "What grooming one at the barber gives", "Once for each open side of the chair."))),
				new FieldsStep("soldiers", "What supplies do {possessive} soldiers use?",
						"A whole number times the supply's usual use, so 0 is none. A supply not listed keeps its own setting for {names}.",
						"ResSupplies.java:46-53 reads MILITARY_SUPPLY_USE with i from 0 to 10000. ResSupply.java:51-52 starts at 1 for the races a supply lists and 0 otherwise, and :75-77 multiplies what soldiers need by it.",
						List.of(new Field("MILITARY_SUPPLY_USE", "Each supply, times its usual use", ""))),
				new FieldsStep("raiders", "How often do {names} turn up as raiders and mercenaries?",
						"Compared with other races: 0 never, and twice the number is twice as often.",
						"Physics.java:32 reads RAID_MERCINARY. Raider.java:38-49 and UpdaterRegions.java:150-175 pick a raider's race by it, weighed against every race's. WDivMercenary.java:57-67 picks a mercenary's race by 0.1 plus it, times their share of your realm.",
						List.of(new Field("PROPERTIES.RAID_MERCINARY", "How often, compared with others", ""))),
				new FieldsStep("tourists", "Do {names} visit as tourists?",
						"Leave these out and none come.",
						"TourismRace.java:31-44 reads TOURIST, and without it OCCURENCE and CREDITS are 0. Updater.java:54-60 picks a tourist's race by OCCURENCE, weighed against every race's, leaving out those at 0. Review.java:135 pays the review's score times CREDITS. TourismRace.java:35-39 reads their reviews from the text file named.",
						List.of(
								new Field("TOURIST.OCCURENCE", "How often they come, compared with others", ""),
								new Field("TOURIST.CREDITS", "How much they pay", "Each pays in proportion to this and to how well they rate their visit."),
								new Field("TOURIST.TOURIST_TEXT_FILE", "What their reviews sound like", ""))),
				new FieldsStep("speech", "What do {names} say?",
						"One to a line.",
						"RaceInfo.java reads each of these as a list of lines.",
						List.of(
								new Field("HELLO", "Greetings", ""),
								new Field("GOODBYE", "Farewells", ""),
								new Field("CURSE", "Curses", ""),
								new Field("INSULT", "Insults", ""),
								new Field("LORD", "Words for their ruler", ""),
								new Field("CITY", "Words for their city", ""))),
				new ChoiceStep(PORTRAITS_STEP, "Would you like to design {possessive} portraits now?",
						"The Portrait Design Wizard builds each portrait from layers of frames you pick on the face sheets. It's under Wizards too, for later.",
						"A race's portraits are each TYPES entry's FACE and CHILD's FACE in the sprite file its SPRITE_FILE names (RAppearence.java:47,90-135, RType.java:64), which this race already has from its look.",
						List.of(new Choice(DESIGN_NOW, Map.of()), new Choice("Later", Map.of()))),
				new DoneStep("done", "That's the heart of {names} done.",
						"Everything else, such as their looks, homes, names, needs and boosts, is in the editor.",
						"The race's other tags are all on its form.")),
				RaceWizard::named);
	}

	/** The words a typed name fills in. The person checks them on the next question. */
	static Map<String, String> named(String display) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("NAME", display);
		values.put("NAMES", NameForms.pluralGuess(display));
		values.put("POSSESSIVE", display);
		values.put("POSSESSIVES", display);
		return values;
	}

	/**
	 * Years, to and from the race's boost. The game's lifespan is (base + each positive ADD) times each MUL, plus
	 * each negative ADD, never below the boostable's minimum (Race.java:205-222). Only MUL is written, so any ADD a
	 * race has stays as it is and is allowed for.
	 */
	static final class Lifespan implements Wizard.Converter {
		@Override
		public String problem(WizardFacts facts) {
			return facts.physicsBase("DEATH_AGE") == null ? "the game's base lifespan couldn't be found in its code, so years can't be worked out." : null;
		}

		@Override
		public String read(Map<String, String> values, WizardFacts facts) {
			Double base = facts.physicsBase("DEATH_AGE");
			Double mul = number(values.get(MUL), 1);
			Double add = number(values.get(ADD), 0);
			if (base == null || mul == null || add == null) {
				return null;
			}
			double years = (base + Math.max(add, 0)) * mul + Math.min(add, 0);
			return format(Math.max(years, 0), 1);
		}

		@Override
		public Map<String, String> write(double answer, Map<String, String> values, WizardFacts facts) {
			double base = facts.physicsBase("DEATH_AGE");
			Double add = number(values.get(ADD), 0);
			double extra = add == null ? 0 : add;
			double mul = (answer - Math.min(extra, 0)) / (base + Math.max(extra, 0));
			return Map.of(MUL, format(mul, 4));
		}

		private static Double number(String text, double absent) {
			if (text == null || text.isBlank()) {
				return absent;
			}
			try {
				return Double.parseDouble(text.strip());
			} catch (NumberFormatException e) {
				return null;
			}
		}

		private static String format(double value, int places) {
			java.math.BigDecimal rounded = java.math.BigDecimal.valueOf(value).setScale(places, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
			return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
		}
	}
}
