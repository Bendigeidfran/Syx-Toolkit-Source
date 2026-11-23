package syx.core;

import java.util.Map;

/**
 * The manifests released with the toolkit, by file name and SHA-256. This is the one place they're pinned:
 * {@code scripts/package.ps1} reads these lines and refuses to build without exactly these files, and the
 * store only calls a file shipped if it matches. Make them with {@code DevManifest build}.
 */
final class ShippedManifests {

	static final Map<String, String> PINS = Map.of(
			"SongsOfSyx-0.70.33-f07a31d3b3b086a2.manifest.json.gz", "73df162c578d185521bc8629b8c7c6d593e986e0789cad79b06c49e998a28832",
			"SongsOfSyx-0.71.44-fe0137c05408e5ee.manifest.json.gz", "fac082afb9d44e882c0a7ae44dc9dcc358de806750661053e52ea6466af272db");

	private ShippedManifests() {
	}
}
