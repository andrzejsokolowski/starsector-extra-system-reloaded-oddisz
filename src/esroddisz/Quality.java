package esroddisz;

import java.awt.Color;

/** Hull quality: names, colours and the starting roll. Quality multiplies the effect of every upgrade. */
public final class Quality {

    private static final float[] BAND_FLOORS = {0f, 0.9f, 1.05f, 1.2f, 1.4f, 1.7f, 2f};
    private static final String[] BAND_NAMES = {
            "Crude", "Standard", "Fine", "Superior", "Exceptional", "Masterwork", "Domain-grade"};
    private static final Color[] BAND_COLORS = {
            new Color(150, 150, 150), new Color(210, 210, 210), new Color(120, 220, 120),
            new Color(90, 170, 255), new Color(190, 120, 255), new Color(255, 170, 60), new Color(80, 240, 240)};

    private Quality() {}

    /**
     * The quality a ship starts with. Seeded by the fleet member's id, so the same hull always rolls the
     * same value and reopening the bay cannot reroll it.
     */
    public static float roll(String memberId, boolean random, float min, float max) {
        if (!random || max <= min) return random ? min : 1f;
        long h = memberId == null ? 0L : memberId.hashCode();
        h ^= 0x5DEECE66DL;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double unit = (h >>> 11) * 0x1.0p-53;
        float value = (float) (min + unit * (max - min));
        return Math.round(value * 100f) / 100f;
    }

    public static String name(float quality) {
        return BAND_NAMES[band(quality)];
    }

    public static Color color(float quality) {
        return BAND_COLORS[band(quality)];
    }

    /** "112% (Fine)". */
    public static String describe(float quality) {
        return percent(quality) + " (" + name(quality) + ")";
    }

    public static String percent(float quality) {
        return Math.round(quality * 100f) + "%";
    }

    private static int band(float quality) {
        int band = 0;
        for (int i = 0; i < BAND_FLOORS.length; i++) {
            if (quality >= BAND_FLOORS[i] - 0.0001f) band = i;
        }
        return band;
    }
}
