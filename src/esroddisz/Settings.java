package esroddisz;

import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import lunalib.lunaSettings.LunaSettings;

/**
 * LunaLib values, read live. A missing or broken value falls back to the default in LunaSettings.csv.
 * Every reader clamps to the same range as the settings page, so a hand-edited file cannot break a save.
 */
public final class Settings {

    /** Where values come from. Tests replace it; the game always reads LunaLib. */
    public interface Source {
        Double getDouble(String field);
        Integer getInt(String field);
        Boolean getBoolean(String field);
    }

    public static final Source LUNALIB = new Source() {
        public Double getDouble(String field) { return LunaSettings.getDouble(ExtraSystemsModPlugin.MOD_ID, field); }
        public Integer getInt(String field) { return LunaSettings.getInt(ExtraSystemsModPlugin.MOD_ID, field); }
        public Boolean getBoolean(String field) { return LunaSettings.getBoolean(ExtraSystemsModPlugin.MOD_ID, field); }
    };

    public static Source source = LUNALIB;

    static final float DEFAULT_COST_MULT = 1f;
    static final float DEFAULT_QUALITY_COST_MULT = 1f;
    static final float DEFAULT_AUGMENT_COST_MULT = 1f;
    static final float DEFAULT_REFUND = 0.5f;
    static final int DEFAULT_MAX_LEVEL = 10;
    static final int DEFAULT_OVERCLOCK_LEVELS = 5;
    static final float DEFAULT_STRENGTH = 1f;
    static final boolean DEFAULT_RANDOM_QUALITY = true;
    static final float DEFAULT_QUALITY_MIN = 0.8f;
    static final float DEFAULT_QUALITY_MAX_START = 1.2f;
    static final float DEFAULT_QUALITY_CAP = 2f;
    static final boolean DEFAULT_REMOVAL_DESTROYS = false;
    static final int[] DEFAULT_SLOTS = {1, 2, 2, 3};

    private Settings() {}

    /** Multiplies the price of every upgrade level. */
    public static float costMult() {
        return number("esro_cost_mult", DEFAULT_COST_MULT, 0.01f, 100f);
    }

    /** Multiplies the price of every hull quality improvement. */
    public static float qualityCostMult() {
        return number("esro_quality_cost_mult", DEFAULT_QUALITY_COST_MULT, 0.01f, 100f);
    }

    /** Multiplies the price of fabricating an augment. */
    public static float augmentCostMult() {
        return number("esro_augment_cost_mult", DEFAULT_AUGMENT_COST_MULT, 0.01f, 100f);
    }

    /** Share of a level's price paid back when it is removed. */
    public static float refundFraction() {
        return number("esro_refund", DEFAULT_REFUND, 0f, 1f);
    }

    /** Levels each upgrade can be bought up to with credits or materials. */
    public static int maxLevel() {
        return whole("esro_max_level", DEFAULT_MAX_LEVEL, 1, 50);
    }

    /** Extra levels past the maximum, each costing a story point on top of the price. */
    public static int overclockLevels() {
        return whole("esro_overclock_levels", DEFAULT_OVERCLOCK_LEVELS, 0, 50);
    }

    /** Multiplies the effect of every upgrade. */
    public static float strength() {
        return number("esro_strength", DEFAULT_STRENGTH, 0.1f, 10f);
    }

    public static boolean randomQuality() {
        return flag("esro_random_quality", DEFAULT_RANDOM_QUALITY);
    }

    /** Lowest starting hull quality a ship can roll. */
    public static float qualityMinStart() {
        return number("esro_quality_min", DEFAULT_QUALITY_MIN, 0.1f, 5f);
    }

    /** Highest starting hull quality a ship can roll. Never below the lowest. */
    public static float qualityMaxStart() {
        return Math.max(qualityMinStart(), number("esro_quality_max_start", DEFAULT_QUALITY_MAX_START, 0.1f, 5f));
    }

    /** The highest hull quality shipyards can reach. */
    public static float qualityCap() {
        return number("esro_quality_cap", DEFAULT_QUALITY_CAP, 1f, 10f);
    }

    public static boolean removalDestroysAugment() {
        return flag("esro_removal_destroys", DEFAULT_REMOVAL_DESTROYS);
    }

    public static int augmentSlots(HullSize size) {
        if (size == null) return 0;
        switch (size) {
            case FRIGATE: return whole("esro_slots_frigate", DEFAULT_SLOTS[0], 0, 10);
            case DESTROYER: return whole("esro_slots_destroyer", DEFAULT_SLOTS[1], 0, 10);
            case CRUISER: return whole("esro_slots_cruiser", DEFAULT_SLOTS[2], 0, 10);
            case CAPITAL_SHIP: return whole("esro_slots_capital", DEFAULT_SLOTS[3], 0, 10);
            default: return 0;
        }
    }

    private static float number(String field, float fallback, float min, float max) {
        Double value;
        try {
            value = source.getDouble(field);
        } catch (RuntimeException e) {
            return fallback;
        }
        if (value == null || value.isNaN() || value.isInfinite()) return fallback;
        return (float) Math.max(min, Math.min(max, value));
    }

    private static int whole(String field, int fallback, int min, int max) {
        Integer value;
        try {
            value = source.getInt(field);
        } catch (RuntimeException e) {
            return fallback;
        }
        if (value == null) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static boolean flag(String field, boolean fallback) {
        Boolean value;
        try {
            value = source.getBoolean(field);
        } catch (RuntimeException e) {
            return fallback;
        }
        return value == null ? fallback : value;
    }
}
