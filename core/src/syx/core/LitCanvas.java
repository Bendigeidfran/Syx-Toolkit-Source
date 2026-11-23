package syx.core;

/**
 * A drawing surface as the game's renderer holds it: a diffuse target and a normal target, lit at the end by one
 * ambient light (LightAmbient_f.txt). Shared by the Race Portrait Sim and the Map Sprite Sim.
 */
final class LitCanvas {

	/**
	 * An ambient light, as snake2d's AmbientLight takes it: a strength, a direction and a tilt above the ground.
	 *
	 * @param strength what a pixel facing the light straight on is multiplied by; 0 means no lighting at all
	 */
	record Light(double strength, double x, double y, double z) {
		static Light of(double strength, double directionDegrees, double tiltDegrees) {
			double z = (float) Math.sin(Math.toRadians(tiltDegrees));
			double xy = (float) Math.cos(Math.toRadians(tiltDegrees));
			double x = (float) (xy * (float) Math.cos(Math.toRadians(directionDegrees)));
			double y = (float) (xy * (float) Math.sin(Math.toRadians(directionDegrees)));
			return new Light(strength, x, y, z);
		}

		/** Colours as they are, with no light: what a flat sheet would show at full brightness. */
		static final Light NONE = new Light(0, 0, 0, 1);
	}

	/** What the composer writes for a flat normal (ComposerUtil.java:33). */
	static final double FLAT_XY = 0x80 / 255.0;

	final int width;
	final int height;
	final double[] rgba;
	final double[] normal;
	final boolean[] hasNormal;

	LitCanvas(int width, int height) {
		this.width = width;
		this.height = height;
		this.rgba = new double[width * height * 4];
		this.normal = new double[width * height * 3];
		this.hasNormal = new boolean[width * height];
	}

	/** Source-over blending of one pixel. */
	void over(int x, int y, double r, double g, double b, double a) {
		if (a <= 0 || x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		int i = (y * width + x) * 4;
		double below = rgba[i + 3];
		double out = a + below * (1 - a);
		rgba[i] = (r * a + rgba[i] * below * (1 - a)) / out;
		rgba[i + 1] = (g * a + rgba[i + 1] * below * (1 - a)) / out;
		rgba[i + 2] = (b * a + rgba[i + 2] * below * (1 - a)) / out;
		rgba[i + 3] = out;
	}

	/** The normal target is blended by the normal sample's own alpha. */
	void normal(int x, int y, double nx, double ny, double nz, double a) {
		if (a <= 0 || x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		int p = y * width + x;
		int i = p * 3;
		double oldX = hasNormal[p] ? normal[i] : FLAT_XY;
		double oldY = hasNormal[p] ? normal[i + 1] : FLAT_XY;
		double oldZ = hasNormal[p] ? normal[i + 2] : 1;
		normal[i] = nx * a + oldX * (1 - a);
		normal[i + 1] = ny * a + oldY * (1 - a);
		normal[i + 2] = nz * a + oldZ * (1 - a);
		hasNormal[p] = true;
	}

	/**
	 * LightAmbient_f.txt: colour times the dot of the normal and the light, times the light's strength, added up over
	 * every light. A pixel facing away from them all stays black. With no light of any strength, colours are as they are.
	 */
	int[] lit(Light... lights) {
		boolean any = false;
		for (Light light : lights) {
			any |= light.strength() > 0;
		}
		int[] argb = new int[width * height];
		for (int p = 0; p < argb.length; p++) {
			int i = p * 4;
			double a = rgba[i + 3];
			if (a <= 0) {
				continue;
			}
			double shade = any ? 0 : 1;
			if (any) {
				double x = hasNormal[p] ? normal[p * 3] : FLAT_XY;
				double y = hasNormal[p] ? normal[p * 3 + 1] : FLAT_XY;
				double z = hasNormal[p] ? normal[p * 3 + 2] : 1;
				for (Light light : lights) {
					double dot = (x * 2 - 1) * light.x() + (y * 2 - 1) * light.y() + (z * 2 - 1) * light.z();
					shade += dot > 0 ? dot * light.strength() : 0;
				}
			}
			argb[p] = byteOf(a) << 24 | byteOf(rgba[i] * shade) << 16 | byteOf(rgba[i + 1] * shade) << 8 | byteOf(rgba[i + 2] * shade);
		}
		return argb;
	}

	static int byteOf(double value) {
		return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
	}
}
