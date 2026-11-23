package syx.core;

import java.util.List;

/** One finding, in the structure every front end shares. */
public record Diagnostic(
		String code,
		Severity severity,
		String confidence,
		String file,
		Integer line,
		String tagPath,
		String message,
		String suggestion,
		List<String> evidence,
		String docs) {

	public enum Severity {
		ERROR,
		WARNING,
		/** Worth a look, and not shown to be a problem. */
		REVIEW,
		/** The tool couldn't do the check. This says nothing about the mod. */
		TOOL_FAILURE
	}

	public static final String SYNTAX_ERROR = "SYNTAX_ERROR";
	public static final String FILE_READ_FAILED = "FILE_READ_FAILED";
	public static final String PARSER_FAILED = "PARSER_FAILED";
	public static final String GAME_JAR_MISSING = "GAME_JAR_MISSING";
	public static final String GAME_PARSER_UNAVAILABLE = "GAME_PARSER_UNAVAILABLE";

	static Diagnostic syntaxError(String file, Integer line, String message) {
		return new Diagnostic(SYNTAX_ERROR, Severity.ERROR, "code-verified", file, line, null, message, null, List.of(), null);
	}

	static Diagnostic toolFailure(String code, String file, String message) {
		return new Diagnostic(code, Severity.TOOL_FAILURE, null, file, null, null, message, null, List.of(), null);
	}
}
