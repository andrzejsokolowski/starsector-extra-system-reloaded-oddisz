package esroddisz.hullmods;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import esroddisz.Faults;
import esroddisz.Pricing;
import esroddisz.Quality;
import esroddisz.Settings;
import esroddisz.ShipData;
import esroddisz.augments.Augment;
import esroddisz.upgrades.Upgrade;

/**
 * The hidden hull mod on every upgraded ship. It holds no state of its own: it looks up the ship's record
 * and applies the upgrades and augments in it. A ship without a record is left untouched.
 */
public class ExtraSystemsHullmod extends BaseHullMod {

    public static final String ID = "esro_extra_systems";

    private static final String COMBAT_AUGMENTS_KEY = "esro_combat_augments";

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipData data = ShipData.forStats(stats);
        if (data == null) return;
        float quality = data.getQuality();
        int maxLevel = Settings.maxLevel();
        float strength = Settings.strength();
        // Each upgrade and augment on its own: one that fails must not cost the ship the others,
        // nor crash the refit screen or the battle.
        for (Upgrade upgrade : Upgrade.values()) {
            int level = data.getLevel(upgrade);
            if (level <= 0) continue;
            try {
                upgrade.apply(stats, hullSize, Pricing.power(level, maxLevel, quality, strength), id);
            } catch (RuntimeException e) {
                Faults.report("applying upgrade " + upgrade.getId(), e);
            }
        }
        for (Augment augment : data.getAugments()) {
            try {
                augment.applyStats(stats, hullSize, id + "_" + augment.getId());
            } catch (RuntimeException e) {
                Faults.report("applying augment " + augment.getId(), e);
            }
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShipData data = ShipData.forStats(ship.getMutableStats());
        if (data == null) return;
        List<Augment> combat = new ArrayList<>();
        for (Augment augment : data.getAugments()) {
            try {
                augment.afterShipCreation(ship, id + "_" + augment.getId());
            } catch (RuntimeException e) {
                Faults.report("setting up augment " + augment.getId(), e);
            }
            if (augment.hasCombatEffect()) combat.add(augment);
        }
        ship.setCustomData(COMBAT_AUGMENTS_KEY, combat);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void advanceInCombat(ShipAPI ship, float amount) {
        Object cached = ship.getCustomData().get(COMBAT_AUGMENTS_KEY);
        if (!(cached instanceof List) || ((List<?>) cached).isEmpty()) return;
        if (!ship.isAlive() || ship.isHulk()) return;
        List<Augment> augments = (List<Augment>) cached;
        for (int i = 0; i < augments.size(); i++) {
            Augment augment = augments.get(i);
            try {
                augment.advance(ship, amount);
            } catch (RuntimeException e) {
                // Switch the failing effect off for this ship for the rest of the battle.
                Faults.report("combat effect of augment " + augment.getId(), e);
                List<Augment> rest = new ArrayList<>(augments);
                rest.remove(augment);
                ship.setCustomData(COMBAT_AUGMENTS_KEY, rest);
                return;
            }
        }
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship,
                                          float width, boolean isForModSpec) {
        try {
            ShipData data = ship == null ? null : ShipData.forStats(ship.getMutableStats());
            if (data == null) return;
            addSummary(tooltip, data, hullSize, 10f);
        } catch (RuntimeException e) {
            Faults.report("hull mod tooltip", e);
        }
    }

    /** Quality, upgrade levels and augments of one ship. Shared with the bay's ship screen. */
    public static void addSummary(TooltipMakerAPI tooltip, ShipData data, HullSize hullSize, float pad) {
        Color h = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        float quality = data.getQuality();
        int maxLevel = Settings.maxLevel();

        tooltip.addPara("Hull quality: %s. Every upgrade on this ship works at %s strength.", pad,
                new Color[]{Quality.color(quality), h}, Quality.describe(quality), Quality.percent(quality));

        List<String> levels = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            int level = data.getLevel(upgrade);
            if (level > 0) levels.add(upgrade.getName() + " " + level + "/" + maxLevel);
        }
        if (levels.isEmpty()) {
            tooltip.addPara("No upgrades yet.", gray, 4f);
        } else {
            tooltip.addPara("Upgrades: %s", 4f, h, joined(levels));
        }

        List<Augment> augments = data.getAugments();
        int slots = Settings.augmentSlots(hullSize);
        if (augments.isEmpty()) {
            tooltip.addPara("Augments: none (%s slots).", 4f, gray, h, String.valueOf(slots));
        } else {
            List<String> names = new ArrayList<>();
            for (Augment a : augments) names.add(a.getName());
            tooltip.addPara("Augments (%s/%s): %s", 4f, h,
                    String.valueOf(augments.size()), String.valueOf(slots), joined(names));
        }
    }

    private static String joined(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
