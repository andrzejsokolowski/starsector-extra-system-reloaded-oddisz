package esroddisz;

/**
 * Price formulas. Pure arithmetic so the curve can be checked without a game.
 *
 * Every price starts from the hull's base value, softened for very expensive hulls so a modded
 * 2-million-credit capital does not cost ten times what a vanilla one does. Upgrade levels get steeper
 * towards the top: a frigate's full set costs a few hundred thousand credits, a capital's several million.
 */
public final class Pricing {

    /** Hulls cheaper than this are priced as if they cost this much, so tiny ships are not free. */
    public static final float MIN_HULL_VALUE = 10000f;
    /** The point where the hull value starts to flatten out. */
    public static final float HULL_VALUE_SOFT_CAP = 400000f;
    /** Share of the effective hull value one top-weight level costs at the maximum level. */
    public static final float LEVEL_COST_FACTOR = 0.5f;
    /** Share of the effective hull value a +10% quality step costs at 100% quality. */
    public static final float QUALITY_COST_FACTOR = 3f;
    /** Market value of the materials asked for, as a share of the credit price. */
    public static final float MATERIAL_SHARE = 0.6f;
    /** Market value of the industrial components asked for, as a share of the credit price. */
    public static final float COMPONENT_SHARE = 0.5f;

    private Pricing() {}

    public static float effectiveHullValue(float baseValue) {
        float v = Math.max(baseValue, MIN_HULL_VALUE);
        return v / (1f + v / HULL_VALUE_SOFT_CAP);
    }

    /**
     * Credits for one level. {@code level} is the level being bought (1 for the first). Levels past
     * {@code maxLevel} keep climbing the same curve.
     */
    public static float levelCost(float hullBaseValue, float weight, int level, int maxLevel, float mult) {
        float t = level / (float) Math.max(1, maxLevel);
        return effectiveHullValue(hullBaseValue) * LEVEL_COST_FACTOR * weight * (0.1f + 0.9f * t * t) * mult;
    }

    /** Credits for raising quality by {@code step} from {@code currentQuality}. */
    public static float qualityCost(float hullBaseValue, float currentQuality, float step, float mult) {
        float q = Math.max(0.1f, currentQuality);
        return effectiveHullValue(hullBaseValue) * QUALITY_COST_FACTOR * step * q * q * q * mult;
    }

    /** Credits for fabricating an augment worth {@code basePrice}. */
    public static float augmentCost(float basePrice, float mult) {
        return basePrice * mult;
    }

    /** Applies a market discount (0.15 = 15% off). */
    public static float discounted(float cost, float discount) {
        return cost * (1f - Math.max(0f, Math.min(0.9f, discount)));
    }

    /** Prices shown and charged are rounded to the nearest 100 credits, never below 100. */
    public static int round(float cost) {
        if (cost <= 0f) return 0;
        return Math.max(100, Math.round(cost / 100f) * 100);
    }

    /** How strongly an upgrade works: level share of the maximum, times quality, times the global strength. */
    public static float power(int level, int maxLevel, float quality, float strength) {
        if (level <= 0) return 0f;
        return level / (float) Math.max(1, maxLevel) * quality * strength;
    }
}
