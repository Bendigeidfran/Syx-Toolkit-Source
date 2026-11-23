package syx.test;

import java.nio.file.Paths;

import syx.core.Diagnostic;
import syx.core.SyntaxChecker;

/** Development helper: prints the core's result for a folder. Usage: DevCheck <folder> <game folder> */
public final class DevCheck {

	private DevCheck() {
	}

	public static void main(String[] args) {
		SyntaxChecker.Result r = SyntaxChecker.check(Paths.get(args[0]), Paths.get(args[1]), (d, t, f) -> {
		}, () -> false);
		for (Diagnostic d : r.diagnostics()) {
			System.out.println(d.code() + " | " + d.file() + " | " + d.line() + " | " + d.message());
		}
		System.out.println("status=" + r.status() + " found=" + r.filesFound() + " checked=" + r.filesChecked()
				+ " skipped=" + r.filesSkipped() + " folders=" + r.foldersScanned());
		System.out.println(r.summary());
	}
}
