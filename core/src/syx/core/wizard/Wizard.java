package syx.core.wizard;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import syx.core.TagSpec;

/**
 * A guided way to make one asset type, as data. Each step asks one thing in plain words and says which tags
 * its answer goes into. The engine ({@link WizardEngine}) does all the work, so a new asset type only needs a
 * new definition. A step never asks for a file or a tag by name.
 *
 * <p>Prompts are written for the wizard, not taken from the handbook, by the user's decision on 2026-11-13.
 * So each step records the game code that backs what it says, and the handbook's own words for the tags
 * stay one click away. {@code {name}}, {@code {names}}, {@code {possessive}} and {@code {possessives}} in
 * a prompt are filled from the asset's own words as they are when it's shown ({@link Phrase}).
 */
public final class Wizard {

	private Wizard() {
	}

	/**
	 * @param id how calls name it, such as race
	 * @param typeLabel the catalog type it makes, which must be enabled for it to be offered
	 * @param title what a person picks from the list
	 * @param steps the questions in order. The first is a {@link NameStep} and the second a {@link LookStep}
	 * @param named the values the typed name fills in when the files are made, such as a race's NAME
	 */
	public record Definition(String id, String typeLabel, String title, List<Step> steps, Function<String, Map<String, String>> named) {
		public Step step(String stepId) {
			return steps.stream().filter(step -> step.id().equals(stepId)).findFirst().orElse(null);
		}

		public int indexOf(String stepId) {
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(stepId)) {
					return i;
				}
			}
			return -1;
		}
	}

	/**
	 * One question.
	 *
	 * @param help one or two sentences under the question, in plain words
	 * @param evidence the game code behind what the prompt and help say
	 */
	public sealed interface Step permits NameStep, LookStep, ChoiceStep, NumberStep, FieldsStep, DoneStep {
		String id();

		String prompt();

		String help();

		String evidence();

		/** Whether the step is asked, given the asset's values. A step that isn't is passed over. */
		default boolean applies(Map<String, String> values) {
			return true;
		}

		/** The tags this step works on, for the handbook's words about them. */
		List<String> keys();
	}

	/** What the asset is called. Its answer names the files, and nothing is written until the look is chosen. */
	public record NameStep(String id, String prompt, String help, String evidence, List<String> keys) implements Step {
	}

	/** Which vanilla asset to start from. Its answer writes the asset's files, so it can't be changed afterwards. */
	public record LookStep(String id, String prompt, String help, String evidence, List<String> keys) implements Step {
	}

	/**
	 * One of a few answers, each a set of values.
	 *
	 * @param changes the values it sets, by tag path. A null removes a tag
	 */
	public record Choice(String label, Map<String, String> changes) {
	}

	public record ChoiceStep(String id, String prompt, String help, String evidence, List<Choice> choices, Predicate<Map<String, String>> when) implements Step {
		public ChoiceStep(String id, String prompt, String help, String evidence, List<Choice> choices) {
			this(id, prompt, help, evidence, choices, values -> true);
		}

		@Override
		public boolean applies(Map<String, String> values) {
			return when.test(values);
		}

		@Override
		public List<String> keys() {
			return choices.stream().flatMap(choice -> choice.changes().keySet().stream()).distinct().toList();
		}
	}

	/** Turns a number a person thinks in, such as years, into the values the game reads, and back. */
	public interface Converter {
		/** Why this can't be asked with this game, or null. A step that can't be asked is passed over and said so. */
		String problem(WizardFacts facts);

		/** The number as the person thinks of it, from the asset's values, or null if it can't be worked out. */
		String read(Map<String, String> values, WizardFacts facts);

		/** The values that give this number. The answer has already been checked against the step's range. */
		Map<String, String> write(double answer, Map<String, String> values, WizardFacts facts);
	}

	/**
	 * One number.
	 *
	 * @param unit what it's counted in, such as years
	 * @param max the most allowed, or null for no limit
	 */
	public record NumberStep(String id, String prompt, String help, String evidence, String unit, double min, Double max, boolean whole, List<String> keys, Converter converter)
			implements Step {
	}

	/**
	 * A tag on a {@link FieldsStep}. Its kind, range, default and options come from the schema, not from here.
	 *
	 * @param key the tag's path, as values are keyed. A map, such as POPULATION.CLIMATE, is one field with an amount per name
	 * @param column for a block of blocks keyed by name, such as PREFERRED.CRIME, the number inside each that this field
	 *        asks for, such as FREEDOM. It's then one amount per name, like a map. Null otherwise
	 * @param options where the names come from when the schema's tag doesn't say, because a wrong name there isn't
	 *        something the checker should report. Null to use the schema's
	 */
	public record Field(String key, String label, String help, String column, TagSpec.Reference options) {
		public Field(String key, String label, String help) {
			this(key, label, help, null, null);
		}

		/** One number inside each named block, such as each crime's FREEDOM, with the names from {@code options}. */
		public static Field column(String key, String column, TagSpec.Reference options, String label, String help) {
			return new Field(key, label, help, column, options);
		}
	}

	/** Several tags at once, each starting from the asset's own value. */
	public record FieldsStep(String id, String prompt, String help, String evidence, List<Field> fields) implements Step {
		@Override
		public List<String> keys() {
			return fields.stream().map(Field::key).distinct().toList();
		}
	}

	/** The end, with what's left to do in the editor. */
	public record DoneStep(String id, String prompt, String help, String evidence) implements Step {
		@Override
		public List<String> keys() {
			return List.of();
		}
	}
}
