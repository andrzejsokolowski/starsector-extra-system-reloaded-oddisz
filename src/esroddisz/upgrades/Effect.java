package esroddisz.upgrades;

import java.util.Locale;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.StatBonus;

/**
 * One stat line of an upgrade, such as "Hull integrity +30%". The value is given for full power
 * (maximum level at 100% quality); the upgrade scales it linearly with level, quality and strength.
 * Reductions are floored so no amount of quality can push a stat to zero or below.
 */
public final class Effect {

    /** Writes the value into the ship's stats. */
    public interface Applier {
        void apply(MutableShipStatsAPI stats, String id, float value);
    }

    /** Whether this line does anything on a hull. Stats may be null outside a ship (tooltips). */
    public interface Condition {
        boolean test(ShipHullSpecAPI hull, MutableShipStatsAPI stats);
    }

    private final String label;
    private final float full;
    private final boolean percent;
    private final boolean whole;
    private final float floor;
    private final float[] hullSizeMult;
    private final Applier applier;
    private final Condition condition;

    private Effect(String label, float full, boolean percent, boolean whole, float floor,
                   float[] hullSizeMult, Applier applier, Condition condition) {
        this.label = label;
        this.full = full;
        this.percent = percent;
        this.whole = whole;
        this.floor = floor;
        this.hullSizeMult = hullSizeMult;
        this.applier = applier;
        this.condition = condition;
    }

    /** A percentage line; negative values are reductions floored at {@code floor} percent. */
    public static Effect percent(String label, float full, float floor, Applier applier) {
        return new Effect(label, full, true, false, floor, null, applier, null);
    }

    public static Effect percent(String label, float full, Applier applier) {
        return percent(label, full, -80f, applier);
    }

    /** A flat line, such as degrees of shield arc. */
    public static Effect flat(String label, float full, Applier applier) {
        return new Effect(label, full, false, false, -Float.MAX_VALUE, null, applier, null);
    }

    /** A whole-number line, such as burn levels or system charges. Rounds down. */
    public static Effect whole(String label, float full, Applier applier) {
        return new Effect(label, full, false, true, -Float.MAX_VALUE, null, applier, null);
    }

    public Effect when(Condition condition) {
        return new Effect(label, full, percent, whole, floor, hullSizeMult, applier, condition);
    }

    /** Multipliers for frigate, destroyer, cruiser and capital. */
    public Effect byHullSize(float frigate, float destroyer, float cruiser, float capital) {
        return new Effect(label, full, percent, whole, floor,
                new float[]{frigate, destroyer, cruiser, capital}, applier, condition);
    }

    public String getLabel() {
        return label;
    }

    public boolean isPercent() {
        return percent;
    }

    /** True for reductions, which read as good news when they grow. */
    public boolean isReduction() {
        return full < 0f;
    }

    public boolean appliesTo(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (condition == null) return true;
        try {
            return condition.test(hull, stats);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public float valueAt(float power, HullSize size) {
        if (power <= 0f) return 0f;
        float value = full * power * sizeMult(size);
        if (whole) value = (float) Math.floor(value + 0.0001f);
        if (value < floor) value = floor;
        return value;
    }

    public void apply(MutableShipStatsAPI stats, String id, float power, HullSize size) {
        float value = valueAt(power, size);
        if (value == 0f) return;
        applier.apply(stats, id, value);
    }

    /** "+12%", "-30%", "+2", "+1.5%". */
    public String format(float value) {
        String sign = value > 0f ? "+" : (value < 0f ? "-" : "");
        float abs = Math.abs(value);
        String number;
        if (whole || abs >= 10f || Math.abs(abs - Math.round(abs)) < 0.05f) {
            number = String.valueOf(Math.round(abs));
        } else {
            number = String.format(Locale.ROOT, "%.1f", abs);
        }
        return sign + number + (percent ? "%" : "");
    }

    private float sizeMult(HullSize size) {
        if (hullSizeMult == null || size == null) return 1f;
        switch (size) {
            case FRIGATE: return hullSizeMult[0];
            case DESTROYER: return hullSizeMult[1];
            case CRUISER: return hullSizeMult[2];
            case CAPITAL_SHIP: return hullSizeMult[3];
            default: return 1f;
        }
    }

    // ------------------------------------------------------------ helpers for appliers

    public static void pct(MutableStat stat, String id, float value) {
        stat.modifyPercent(id, value);
    }

    public static void pct(StatBonus stat, String id, float value) {
        stat.modifyPercent(id, value);
    }

    /** A reduction or increase written as a multiplier: -40 becomes x0.6. */
    public static void mult(MutableStat stat, String id, float value) {
        stat.modifyMult(id, Math.max(0f, 1f + value / 100f));
    }

    public static void mult(StatBonus stat, String id, float value) {
        stat.modifyMult(id, Math.max(0f, 1f + value / 100f));
    }

    public static void flat(MutableStat stat, String id, float value) {
        stat.modifyFlat(id, value);
    }

    public static void flat(StatBonus stat, String id, float value) {
        stat.modifyFlat(id, value);
    }
}
