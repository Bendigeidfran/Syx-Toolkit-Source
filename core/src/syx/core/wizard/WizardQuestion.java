package syx.core.wizard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the wizard shows right now, as data. The app draws its chat from this and agents read the same thing
 * through {@code Operations}, so the two can't ask different questions or report different problems.
 *
 * @param name the asset's name as its files use it, or null before it has one
 * @param display the name as typed, or null before it has one
 * @param kind name, look, choice, number, fields or done
 * @param position which question this is, from 1, out of {@code total}
 * @param note something to say before the question, such as what the last answer did, or null
 * @param findings what's wrong or worth knowing, about this question's tags or anything that blocks saving
 * @param refused why the last answer wasn't taken, or null. Nothing was written when it's set
 * @param history the questions answered so far, with each answer as the files hold it now
 */
public record WizardQuestion(String wizard, String mod, String name, String display, String step, String kind, String prompt, String help, String evidence, int position,
		int total, String note, List<ChoiceView> choices, NumberView number, List<FieldView> fields, List<HandbookEntry> handbook, List<Problem> findings, String refused,
		boolean canGoBack, boolean canSkip, List<Exchange> history, PortraitView portrait, Handover handover) {

	public WizardQuestion(String wizard, String mod, String name, String display, String step, String kind, String prompt, String help, String evidence, int position, int total,
			String note, List<ChoiceView> choices, NumberView number, List<FieldView> fields, List<HandbookEntry> handbook, List<Problem> findings, String refused, boolean canGoBack,
			boolean canSkip, List<Exchange> history) {
		this(wizard, mod, name, display, step, kind, prompt, help, evidence, position, total, note, choices, number, fields, handbook, findings, refused, canGoBack, canSkip, history, null,
				null);
	}

	/** Another wizard to go on to, with what to start it with, such as the portrait wizard for the race just made. */
	public record Handover(String wizard, String name) {
	}

	/**
	 * What a portrait question shows beyond its choices and fields.
	 *
	 * @param portrait which portrait it's about, an index into {@code portraits}, or -1
	 * @param layers that portrait's layers, bottom first
	 * @param frames for a frames question, what's picked and what can be picked from
	 * @param addons for a headwear question, its entries
	 * @param addonSheets for a headwear question, the addon sheets there are
	 */
	public record PortraitView(String race, int portrait, List<String> portraits, List<LayerView> layers, FramesView frames, List<AddonView> addons, List<SheetView> addonSheets) {
	}

	/** One layer: its place, its name or null, its frames as FILE: INDEX, and a line about who has it and how it's drawn. */
	public record LayerView(int index, String name, List<String> frames, String summary) {
	}

	/** @param sheet the sheet to show first */
	public record FramesView(List<String> picked, String sheet, List<SheetView> sheets) {
	}

	/** @param source mod, game, or mod over game (a mod's sheet with the game's name, which replaces it for every race) */
	public record SheetView(String name, boolean addon, String source, int width, int height, int frames, String problem) {
	}

	public record AddonView(String file, String offX, String offY) {
	}

	/** One answer button. {@code current} is the one the asset's values match now. */
	public record ChoiceView(String id, String label, boolean current) {
	}

	/** @param value the number as things stand, or null if it can't be worked out */
	public record NumberView(String value, String unit, double min, Double max, boolean whole) {
	}

	/**
	 * One tag on a fields question.
	 *
	 * @param input number, whole_number, yes_no, text, one (a single name from options), text_lines, rows, picks or amounts
	 * @param state set (in the file), ghosted (absent, with the game's default), add (absent, with no default), or kept (written in a form this can't show)
	 * @param value what the file holds, or null when absent. Rows are one to a line, and picks are comma separated
	 * @param amounts for amounts, what the file holds for each name that has one
	 * @param defaultText what the game uses when the tag is absent, or null if it has no default
	 * @param options what can be picked, or each name that can have an amount, or null
	 * @param keptAsWritten for kept, the value as the game reads it
	 * @param min the lowest a number can be, or null if the game sets none
	 * @param max the highest a number can be, or null if the game sets none
	 * @param maxEnforced whether the game refuses a number above {@code max}. When it doesn't, a typed number may go over it
	 * @param column for amounts inside a block per name, such as each crime's FREEDOM, that number's tag, or null
	 */
	public record FieldView(String key, String label, String help, String input, String state, String value, Map<String, String> amounts, String defaultText, List<String> options,
			String range, boolean required, String rule, String evidence, String keptAsWritten, Double min, Double max, boolean maxEnforced, String column) {

		/**
		 * Whether a slider can be offered: a number with both ends set by the game, close enough together for a slider
		 * to pick from. A range such as -10000 to 10000, for numbers that are mostly near 1, would only get in the way.
		 */
		public boolean hasSlider() {
			boolean number = input.equals("number") || input.equals("whole_number") || input.equals("amounts");
			return number && min != null && max != null && max > min && max - min <= (input.equals("whole_number") ? 1000 : 100);
		}

		/** The key one name's amount is saved under, such as POPULATION.CLIMATE.COLD or PREFERRED.CRIME.THEFT.FREEDOM. */
		public String amountKey(String name) {
			return key + "." + name + (column == null ? "" : "." + column);
		}

		/** The name an amount's key is for, or null if the key isn't one of this field's amounts. */
		public String nameIn(String amountKey) {
			String start = key + ".";
			String end = column == null ? "" : "." + column;
			if (!amountKey.startsWith(start) || !amountKey.endsWith(end) || amountKey.length() <= start.length() + end.length()) {
				return null;
			}
			String name = amountKey.substring(start.length(), amountKey.length() - end.length());
			return name.contains(".") ? null : name;
		}
	}

	/**
	 * The handbook's words for a tag this question works on.
	 *
	 * @param description the handbook's text, or a note that it has none, never null
	 * @param url the tag's page on the handbook's own site, or null
	 */
	public record HandbookEntry(String label, String tag, String description, String example, String url, boolean unverified) {
	}

	/** @param tag the tag it's about, or null for the asset as a whole */
	public record Problem(String tag, String severity, boolean blocksSaving, String message) {
	}

	/** A question already answered. */
	public record Exchange(String step, String prompt, String answer, List<HandbookEntry> handbook) {
	}

	/** This question with why an answer wasn't taken. */
	WizardQuestion refusedWith(String message, List<Problem> problems) {
		return new WizardQuestion(wizard, mod, name, display, step, kind, prompt, help, evidence, position, total, null, choices, number, fields, handbook, problems, message, canGoBack,
				canSkip, history, portrait, handover);
	}

	/** This question with a note to say before it. */
	WizardQuestion withNote(String text) {
		return new WizardQuestion(wizard, mod, name, display, step, kind, prompt, help, evidence, position, total, text, choices, number, fields, handbook, findings, refused, canGoBack,
				canSkip, history, portrait, handover);
	}

	/** This question with another wizard to go on to. */
	WizardQuestion withHandover(Handover next) {
		return new WizardQuestion(wizard, mod, name, display, step, kind, prompt, help, evidence, position, total, note, choices, number, fields, handbook, findings, refused, canGoBack,
				canSkip, history, portrait, next);
	}

	/** In JSON's terms, for agents. */
	public Map<String, Object> toMap() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("wizard", wizard);
		out.put("mod", mod);
		out.put("name", name);
		out.put("display_name", display);
		out.put("step", step);
		out.put("kind", kind);
		out.put("position", position);
		out.put("total", total);
		out.put("note", note);
		out.put("prompt", prompt);
		out.put("help", help);
		out.put("evidence", evidence);
		if (refused != null) {
			out.put("refused", refused);
		}
		if (!choices.isEmpty()) {
			List<Object> list = new ArrayList<>();
			for (ChoiceView choice : choices) {
				list.add(map("id", choice.id(), "label", choice.label(), "current", choice.current()));
			}
			out.put("choices", list);
		}
		if (number != null) {
			out.put("number", map("value", number.value(), "unit", number.unit(), "min", number.min(), "max", number.max(), "whole", number.whole()));
		}
		if (!fields.isEmpty()) {
			List<Object> list = new ArrayList<>();
			for (FieldView field : fields) {
				Map<String, Object> row = map("key", field.key(), "label", field.label(), "help", field.help(), "input", field.input(), "state", field.state(), "value", field.value());
				if (field.input().equals("amounts")) {
					row.put("amounts", new LinkedHashMap<String, Object>(field.amounts()));
					if (field.column() != null) {
						row.put("column", field.column());
					}
				}
				row.put("default", field.defaultText());
				if (field.options() != null) {
					row.put("options", new ArrayList<Object>(field.options()));
				}
				row.put("range", field.range());
				row.put("min", field.min());
				row.put("max", field.max());
				row.put("max_enforced", field.maxEnforced());
				row.put("required", field.required());
				row.put("rule", field.rule());
				row.put("evidence", field.evidence());
				if (field.keptAsWritten() != null) {
					row.put("kept_as_written", field.keptAsWritten());
				}
				list.add(row);
			}
			out.put("fields", list);
		}
		out.put("handbook", handbookList(handbook));
		List<Object> problems = new ArrayList<>();
		for (Problem problem : findings) {
			problems.add(map("tag", problem.tag(), "severity", problem.severity(), "blocks_saving", problem.blocksSaving(), "message", problem.message()));
		}
		out.put("findings", problems);
		out.put("can_go_back", canGoBack);
		out.put("can_skip", canSkip);
		List<Object> answered = new ArrayList<>();
		for (Exchange exchange : history) {
			answered.add(map("step", exchange.step(), "prompt", exchange.prompt(), "answer", exchange.answer(), "handbook", handbookList(exchange.handbook())));
		}
		out.put("history", answered);
		if (portrait != null) {
			out.put("portrait", portraitMap(portrait));
		}
		if (handover != null) {
			out.put("next_wizard", map("wizard", handover.wizard(), "display_name", handover.name(), "how", "wizard_start with wizard " + handover.wizard() + " and this display_name."));
		}
		out.put("how_to_answer", howToAnswer(kind));
		return out;
	}

	private static Map<String, Object> portraitMap(PortraitView view) {
		Map<String, Object> out = map("race", view.race(), "portrait", view.portrait(), "portraits", new ArrayList<Object>(view.portraits()));
		if (!view.layers().isEmpty()) {
			List<Object> layers = new ArrayList<>();
			for (LayerView layer : view.layers()) {
				layers.add(map("index", layer.index(), "name", layer.name(), "frames", new ArrayList<Object>(layer.frames()), "summary", layer.summary()));
			}
			out.put("layers", layers);
		}
		if (view.frames() != null) {
			out.put("picked", new ArrayList<Object>(view.frames().picked()));
			out.put("sheet", view.frames().sheet());
			out.put("sheets", sheetList(view.frames().sheets()));
		}
		if (view.addons() != null) {
			List<Object> entries = new ArrayList<>();
			for (AddonView addon : view.addons()) {
				entries.add(map("file", addon.file(), "offx", addon.offX(), "offy", addon.offY()));
			}
			out.put("entries", entries);
			out.put("addon_sheets", sheetList(view.addonSheets()));
		}
		return out;
	}

	private static List<Object> sheetList(List<SheetView> sheets) {
		List<Object> list = new ArrayList<>();
		for (SheetView sheet : sheets) {
			list.add(map("name", sheet.name(), "source", sheet.source(), "width", sheet.width(), "height", sheet.height(), "frames", sheet.frames(), "problem", sheet.problem()));
		}
		return list;
	}

	private static List<Object> handbookList(List<HandbookEntry> entries) {
		List<Object> list = new ArrayList<>();
		for (HandbookEntry entry : entries) {
			list.add(map("label", entry.label(), "tag", entry.tag(), "description", entry.description(), "example", entry.example(), "url", entry.url(), "unverified", entry.unverified()));
		}
		return list;
	}

	static String howToAnswer(String kind) {
		return switch (kind) {
		case "name" -> "wizard_start with display_name.";
		case "look", "choice" -> "wizard_answer with answer {\"choice\": \"<id>\"}, an id from choices.";
		case "number" -> "wizard_answer with answer {\"value\": \"<number>\"}.";
		case "fields" -> "wizard_answer with answer {\"values\": {\"<key>\": \"<value>\"}}, giving only what changes. A null puts a tag back to absent. For amounts, the key is the field's key, a dot and the name, such as POPULATION.CLIMATE.TEMPERATE, then a dot and the column if the field has one, such as PREFERRED.CRIME.THEFT.FREEDOM. A one field takes one of its options. Picks are comma separated, and rows are one to a line.";
		case "done" -> "wizard_answer with answer {} finishes. Everything else is in the asset's form: open_asset.";
		case "race" -> "wizard_start with display_name set to one of the choices' ids, a race's name.";
		case "text" -> "wizard_answer with answer {\"value\": \"<text>\"}.";
		case "layers" -> "wizard_answer with answer {\"choice\": \"<id>\"}, an id from choices: add, next, or frames, tweak, up, down, top, bottom or remove followed by : and a layer's index. Layers are drawn in order, the first at the bottom. preview_portrait draws it.";
		case "frames" -> "wizard_answer with answer {\"frames\": [\"<sheet>: <index>\", ...]}, in the order they're to be picked from. Frames count across then down from 0, 4 to a row; preview_face_sheet shows a sheet with its grid. For a new layer, \"at\" says where it's drawn: 0 is the bottom, behind everything (as back hair is), and the number of layers, the default, is the top.";
		case "addons" -> "wizard_answer with answer {\"entries\": [{\"file\": \"<addon sheet>\", \"offx\": 0, \"offy\": 0}, ...]}, at least one. Every row of a sheet can be picked by the game.";
		case "portrait_done" -> "wizard_answer with answer {} finishes. The Race Portrait Sim (open_portrait, preview_portrait) shows and edits everything.";
		default -> "";
		};
	}

	private static Map<String, Object> map(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}
}
