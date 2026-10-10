package esroddisz.campaign;

import java.util.ArrayList;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

import esroddisz.Faults;
import esroddisz.ShipData;
import esroddisz.hullmods.ExtraSystemsHullmod;

/**
 * Keeps the hidden hull mod on exactly the player's ships that have upgrades or augments.
 *
 * The hull mod can fall off when the game rebuilds a variant - recovering a disabled ship drops hull mods
 * the player has not learned, for one - and it can be copied onto a fresh hull through a saved loadout.
 * Twice a second this puts it back where it belongs and takes it off where it does not.
 */
public class HullmodSync implements EveryFrameScript {

    private float elapsed;

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        elapsed += amount;
        if (elapsed < 0.5f) return;
        elapsed = 0f;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            // One odd ship, say from another mod, must not stop the campaign.
            try {
                sync(member);
            } catch (RuntimeException e) {
                Faults.report("keeping the hull mod on " + member.getHullId(), e);
            }
        }
    }

    /** Adds or removes the hull mod so it matches the ship's record. Safe to call at any time. */
    public static void sync(FleetMemberAPI member) {
        if (member == null || member.isFighterWing()) return;
        ShipData data = ShipData.get(member);
        boolean wanted = data != null && data.hasEffects();
        if (wanted) {
            if (!isFullyInstalled(member)) install(member);
        } else if (hasAnyTrace(member.getVariant())) {
            uninstall(member);
        }
    }

    static boolean isFullyInstalled(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();
        if (variant == null || !variant.hasHullMod(ExtraSystemsHullmod.ID)) return false;
        String tag = ShipData.OWNER_TAG_PREFIX + member.getId();
        if (!variant.hasTag(tag)) return false;
        for (String slot : moduleSlots(variant)) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module == null) continue;
            if (!module.hasHullMod(ExtraSystemsHullmod.ID) || !module.hasTag(tag)) return false;
        }
        return true;
    }

    static boolean hasAnyTrace(ShipVariantAPI variant) {
        if (variant == null) return false;
        if (variant.hasHullMod(ExtraSystemsHullmod.ID) || hasOwnerTag(variant)) return true;
        for (String slot : moduleSlots(variant)) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module != null && (module.hasHullMod(ExtraSystemsHullmod.ID) || hasOwnerTag(module))) return true;
        }
        return false;
    }

    public static void install(FleetMemberAPI member) {
        ShipVariantAPI variant = editable(member.getVariant());
        addMod(variant);
        String tag = ShipData.OWNER_TAG_PREFIX + member.getId();
        // The tag lets copies of this variant - the refit simulator's, a module's - find the record.
        clearOwnerTags(variant);
        variant.addTag(tag);
        for (String slot : moduleSlots(variant)) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module == null) continue;
            module = editable(module);
            addMod(module);
            clearOwnerTags(module);
            module.addTag(tag);
            variant.setModuleVariant(slot, module);
        }
        member.setVariant(variant, false, true);
        member.updateStats();
    }

    public static void uninstall(FleetMemberAPI member) {
        ShipVariantAPI variant = editable(member.getVariant());
        removeMod(variant);
        clearOwnerTags(variant);
        for (String slot : moduleSlots(variant)) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module == null) continue;
            module = editable(module);
            removeMod(module);
            clearOwnerTags(module);
            variant.setModuleVariant(slot, module);
        }
        member.setVariant(variant, false, true);
        member.updateStats();
    }

    /** Stock and goal variants are shared between ships; edit a private copy instead. */
    static ShipVariantAPI editable(ShipVariantAPI variant) {
        if (variant.isStockVariant() || variant.isGoalVariant() || variant.getSource() != VariantSource.REFIT) {
            variant = variant.clone();
            variant.setSource(VariantSource.REFIT);
        }
        return variant;
    }

    private static void addMod(ShipVariantAPI variant) {
        if (!variant.getPermaMods().contains(ExtraSystemsHullmod.ID)) variant.addPermaMod(ExtraSystemsHullmod.ID, false);
        if (!variant.hasHullMod(ExtraSystemsHullmod.ID)) variant.addMod(ExtraSystemsHullmod.ID);
    }

    private static void removeMod(ShipVariantAPI variant) {
        variant.removePermaMod(ExtraSystemsHullmod.ID);
        variant.removeMod(ExtraSystemsHullmod.ID);
    }

    private static boolean hasOwnerTag(ShipVariantAPI variant) {
        if (variant.getTags() == null) return false;
        for (String tag : variant.getTags()) {
            if (tag != null && tag.startsWith(ShipData.OWNER_TAG_PREFIX)) return true;
        }
        return false;
    }

    private static void clearOwnerTags(ShipVariantAPI variant) {
        if (variant.getTags() == null) return;
        for (String tag : new ArrayList<>(variant.getTags())) {
            if (tag != null && tag.startsWith(ShipData.OWNER_TAG_PREFIX)) variant.removeTag(tag);
        }
    }

    private static Iterable<String> moduleSlots(ShipVariantAPI variant) {
        if (variant.getStationModules() == null) return new ArrayList<>();
        return new ArrayList<>(variant.getStationModules().keySet());
    }
}
