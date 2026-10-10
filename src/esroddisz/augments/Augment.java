package esroddisz.augments;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

/**
 * Augments: rare items installed into a ship's augment slots. Each one is a special item with the same id.
 * Unlike upgrades they do not scale with quality; they add a distinct effect. Ids are stored in saves.
 */
public enum Augment {

    OVERLOAD_DAMPER("esro_overload_damper", "Overload Damper", 300000f,
            materials(Commodities.RARE_METALS, 3, Commodities.HEAVY_MACHINERY, 2, Commodities.VOLATILES, 1),
            null, null,
            "Overload duration -50%.",
            "Once every 30 seconds, an overload is cancelled the moment it starts and flux drops to 75%.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getOverloadTimeMod().modifyMult(id, 0.5f);
        }

        @Override
        public void advance(ShipAPI ship, float amount) {
            State state = State.of(ship, this);
            if (state.timer > 0f) state.timer -= amount;
            FluxTrackerAPI flux = ship.getFluxTracker();
            if (state.timer > 0f || flux == null || !flux.isOverloaded()) return;
            float target = flux.getMaxFlux() * 0.75f;
            if (flux.getHardFlux() > target) flux.setHardFlux(target);
            if (flux.getCurrFlux() > target) flux.setCurrFlux(target);
            flux.stopOverload();
            state.timer = 30f;
            floatText(ship, "Overload dampened", new Color(120, 220, 255));
        }

        @Override
        public boolean hasCombatEffect() {
            return true;
        }
    },

    NANOLATTICE("esro_nanolattice", "Nanolattice Regenerator", 350000f,
            materials(Commodities.RARE_METALS, 2, Commodities.METALS, 3, Commodities.HEAVY_MACHINERY, 1),
            null, null,
            "Armor regenerates 2% of its maximum per second in combat.",
            "Each battle it can restore up to twice the ship's total armor.") {
        @Override
        public void advance(ShipAPI ship, float amount) {
            if (amount <= 0f) return;
            ArmorGridAPI grid = ship.getArmorGrid();
            if (grid == null) return;
            float[][] cells = grid.getGrid();
            if (cells == null || cells.length == 0) return;
            float max = grid.getMaxArmorInCell();
            if (max <= 0f) return;
            State state = State.of(ship, this);
            if (!state.started) {
                state.started = true;
                state.pool = max * cells.length * cells[0].length * 2f;
            }
            if (state.pool <= 0f) return;
            float perCell = max * 0.02f * amount;
            boolean changed = false;
            for (int x = 0; x < cells.length && state.pool > 0f; x++) {
                for (int y = 0; y < cells[x].length && state.pool > 0f; y++) {
                    float current = grid.getArmorValue(x, y);
                    if (current >= max) continue;
                    float add = Math.min(perCell, Math.min(max - current, state.pool));
                    grid.setArmorValue(x, y, current + add);
                    state.pool -= add;
                    changed = true;
                }
            }
            if (changed) ship.syncWithArmorGridState();
        }

        @Override
        public boolean hasCombatEffect() {
            return true;
        }
    },

    CRYO_EXCHANGER("esro_cryo_exchanger", "Cryo-Flux Exchanger", 300000f,
            materials(Commodities.VOLATILES, 3, Commodities.RARE_METALS, 2, Commodities.HEAVY_MACHINERY, 1),
            null, null,
            "Flux dissipation +25%.",
            "Vent rate +25%.",
            "With shields up, 25% of dissipation also bleeds off hard flux.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getFluxDissipation().modifyPercent(id, 25f);
            stats.getVentRateMult().modifyPercent(id, 25f);
            stats.getHardFluxDissipationFraction().modifyFlat(id, 0.25f);
        }
    },

    TEMPORAL_CAPACITOR("esro_temporal_capacitor", "Temporal Capacitor", 350000f,
            materials(Commodities.RARE_METALS, 3, Commodities.VOLATILES, 2),
            "This ship has no ship system.", Augment::hasSystem,
            "Ship system cooldown -50% and flux cost -50%.",
            "Systems with charges: +1 charge and +100% charge regeneration.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getSystemCooldownBonus().modifyMult(id, 0.5f);
            stats.getSystemFluxCostBonus().modifyMult(id, 0.5f);
            ShipHullSpecAPI hull = hull(stats);
            if (hull != null && hasSystemCharges(hull, stats)) {
                stats.getSystemUsesBonus().modifyFlat(id, 1f);
                stats.getSystemRegenBonus().modifyPercent(id, 100f);
            }
        }
    },

    PHASE_COIL("esro_phase_coil", "Phase Coil Tuner", 300000f,
            materials(Commodities.RARE_METALS, 3, Commodities.VOLATILES, 1, Commodities.HEAVY_MACHINERY, 1),
            "Only phase ships can use this.", Augment::hasPhase,
            "Phase activation cost and upkeep -50%.",
            "Phase cooldown -50%.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getPhaseCloakActivationCostBonus().modifyMult(id, 0.5f);
            stats.getPhaseCloakUpkeepCostBonus().modifyMult(id, 0.5f);
            stats.getPhaseCloakCooldownBonus().modifyMult(id, 0.5f);
        }
    },

    PD_INTEGRATOR("esro_pd_integrator", "Point Defense Integrator", 250000f,
            materials(Commodities.HAND_WEAPONS, 2, Commodities.RARE_METALS, 2, Commodities.HEAVY_MACHINERY, 1),
            null, null,
            "Small ballistic and energy weapons act as point defense, as long as the ship also mounts a "
                    + "medium or large gun that is not point defense.",
            "Damage to fighters and missiles +50%.",
            "Point defense weapon range +100.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getDamageToFighters().modifyPercent(id, 50f);
            stats.getDamageToMissiles().modifyPercent(id, 50f);
            stats.getNonBeamPDWeaponRangeBonus().modifyFlat(id, 100f);
            stats.getBeamPDWeaponRangeBonus().modifyFlat(id, 100f);
        }

        @Override
        public void afterShipCreation(ShipAPI ship, String id) {
            for (WeaponAPI weapon : pdConversions(ship.getAllWeapons())) weapon.setPD(true);
        }
    },

    HANGAR_FORGE("esro_hangar_forge", "Hangar Microforge", 300000f,
            materials(Commodities.HEAVY_MACHINERY, 3, Commodities.METALS, 2, Commodities.RARE_METALS, 1),
            "This ship has no fighter bays.", Augment::hasFighterBays,
            "Fighter replacement time -40%.",
            "Replacement rate recovers twice as fast and drains half as fast.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getFighterRefitTimeMult().modifyMult(id, 0.6f);
            stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_INCREASE_MULT).modifyPercent(id, 100f);
            stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 0.5f);
        }
    },

    MISSILE_AUTOFORGE("esro_missile_autoforge", "Missile Autoforge", 300000f,
            materials(Commodities.VOLATILES, 2, Commodities.METALS, 2, Commodities.HEAVY_MACHINERY, 2),
            "This ship has no slots that can mount missiles.", Augment::hasMissileSlots,
            "Missile ammunition +25%.",
            "Every 45 seconds, each missile weapon reloads 30% of its ammunition.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getMissileAmmoBonus().modifyPercent(id, 25f);
        }

        @Override
        public void advance(ShipAPI ship, float amount) {
            State state = State.of(ship, this);
            state.timer += amount;
            if (state.timer < 45f) return;
            state.timer = 0f;
            boolean reloaded = false;
            if (ship.getAllWeapons() == null) return;
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                if (weapon.getType() != WeaponType.MISSILE || !weapon.usesAmmo()) continue;
                int max = weapon.getMaxAmmo();
                int ammo = weapon.getAmmo();
                if (max <= 0 || ammo >= max) continue;
                int add = Math.max(1, Math.round(max * 0.3f));
                weapon.setAmmo(Math.min(max, ammo + add));
                reloaded = true;
            }
            if (reloaded) floatText(ship, "Missiles reloaded", new Color(255, 200, 120));
        }

        @Override
        public boolean hasCombatEffect() {
            return true;
        }
    },

    VENT_THRUSTERS("esro_vent_thrusters", "Vent Thrusters", 250000f,
            materials(Commodities.VOLATILES, 3, Commodities.METALS, 1, Commodities.HEAVY_MACHINERY, 1),
            null, null,
            "Vent rate +30%.",
            "While venting: top speed +50%, acceleration +100%, turning +50%.") {
        @Override
        public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
            stats.getVentRateMult().modifyPercent(id, 30f);
        }

        @Override
        public void advance(ShipAPI ship, float amount) {
            State state = State.of(ship, this);
            FluxTrackerAPI flux = ship.getFluxTracker();
            boolean venting = flux != null && flux.isVenting() && ship.isAlive();
            if (venting == state.active) {
                if (venting) status(ship, this, "Vent thrusters engaged");
                return;
            }
            state.active = venting;
            MutableShipStatsAPI stats = ship.getMutableStats();
            String id = "esro_vent_thrusters_active";
            if (venting) {
                stats.getMaxSpeed().modifyPercent(id, 50f);
                stats.getAcceleration().modifyPercent(id, 100f);
                stats.getDeceleration().modifyPercent(id, 100f);
                stats.getMaxTurnRate().modifyPercent(id, 50f);
                stats.getTurnAcceleration().modifyPercent(id, 50f);
                status(ship, this, "Vent thrusters engaged");
            } else {
                stats.getMaxSpeed().unmodify(id);
                stats.getAcceleration().unmodify(id);
                stats.getDeceleration().unmodify(id);
                stats.getMaxTurnRate().unmodify(id);
                stats.getTurnAcceleration().unmodify(id);
            }
        }

        @Override
        public boolean hasCombatEffect() {
            return true;
        }
    },

    RANGE_EQUALIZER("esro_range_equalizer", "Fire Control Equalizer", 300000f,
            materials(Commodities.RARE_METALS, 3, Commodities.HAND_WEAPONS, 1, Commodities.HEAVY_MACHINERY, 1),
            null, null,
            "Ballistic and energy weapons with a base range under 800 gain up to +200 range, never past 800.") {
        @Override
        public void afterShipCreation(ShipAPI ship, String id) {
            if (!ship.hasListenerOfClass(EqualizerRange.class)) ship.addListener(new EqualizerRange());
        }
    };

    /** Item base price: what a market pays for one, and what the item is worth in loot. */
    public static final float ITEM_VALUE_SHARE = 0.3f;

    private final String id;
    private final String name;
    private final float fabricationCost;
    private final Map<String, Float> materials;
    private final String requirementText;
    private final Requirement requirement;
    private final List<String> effectLines;

    Augment(String id, String name, float fabricationCost, Map<String, Float> materials,
            String requirementText, Requirement requirement, String... effectLines) {
        this.id = id;
        this.name = name;
        this.fabricationCost = fabricationCost;
        this.materials = Collections.unmodifiableMap(materials);
        this.requirementText = requirementText;
        this.requirement = requirement;
        this.effectLines = Collections.unmodifiableList(Arrays.asList(effectLines));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Credits to fabricate one at a shipyard, before discounts and the cost setting. */
    public float getFabricationCost() {
        return fabricationCost;
    }

    public Map<String, Float> getMaterials() {
        return materials;
    }

    public List<String> getEffectLines() {
        return effectLines;
    }

    /** Null when any ship can take it. */
    public String getRequirementText() {
        return requirementText;
    }

    public boolean canInstallOn(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (requirement == null) return true;
        try {
            return requirement.test(hull, stats);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Stat changes, applied before the ship is created. */
    public void applyStats(MutableShipStatsAPI stats, HullSize size, String id) {
    }

    /** Listeners and weapon changes, applied once the ship exists. */
    public void afterShipCreation(ShipAPI ship, String id) {
    }

    /** Called every combat frame for ships that carry this augment. */
    public void advance(ShipAPI ship, float amount) {
    }

    /** True if {@link #advance} does anything, so ships without such augments skip the per-frame work. */
    public boolean hasCombatEffect() {
        return false;
    }

    public static Augment byId(String id) {
        for (Augment a : values()) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }

    interface Requirement {
        boolean test(ShipHullSpecAPI hull, MutableShipStatsAPI stats);
    }

    /** Per-ship combat state, kept in the ship's custom data for the length of one battle. */
    static final class State {
        float timer;
        float pool;
        boolean started;
        boolean active;

        static State of(ShipAPI ship, Augment augment) {
            String key = "esro_state_" + augment.id;
            Object existing = ship.getCustomData().get(key);
            if (existing instanceof State) return (State) existing;
            State fresh = new State();
            ship.setCustomData(key, fresh);
            return fresh;
        }
    }

    /** Range bonus for short-ranged guns. Only base range counts, so other range bonuses stack on top. */
    public static final class EqualizerRange implements WeaponBaseRangeModifier {
        public static final float THRESHOLD = 800f;
        public static final float BONUS = 200f;

        public float getWeaponBaseRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f;
        }

        public float getWeaponBaseRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return 1f;
        }

        public float getWeaponBaseRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            if (weapon == null || weapon.getSpec() == null) return 0f;
            WeaponType type = weapon.getType();
            if (type != WeaponType.BALLISTIC && type != WeaponType.ENERGY) return 0f;
            return bonusFor(weapon.getSpec().getMaxRange());
        }

        public static float bonusFor(float baseRange) {
            if (baseRange >= THRESHOLD || baseRange <= 0f) return 0f;
            return Math.min(BONUS, THRESHOLD - baseRange);
        }
    }

    // ------------------------------------------------------------ helpers

    /**
     * The small guns the Point Defense Integrator turns into point defense. None at all if that would
     * leave the ship without a medium or large gun for fighting ships: the AI treats a ship whose guns
     * are all point defense as having nothing to attack with, and hangs back.
     */
    public static List<WeaponAPI> pdConversions(List<WeaponAPI> weapons) {
        List<WeaponAPI> small = new java.util.ArrayList<>();
        if (weapons == null) return small;
        boolean keepsMainGun = false;
        for (WeaponAPI weapon : weapons) {
            WeaponType type = weapon.getType();
            if (type != WeaponType.BALLISTIC && type != WeaponType.ENERGY) continue;
            if (weapon.getSize() == WeaponSize.SMALL) {
                small.add(weapon);
            } else if (!weapon.hasAIHint(WeaponAPI.AIHints.PD)) {
                keepsMainGun = true;
            }
        }
        if (!keepsMainGun) small.clear();
        return small;
    }

    static ShipHullSpecAPI hull(MutableShipStatsAPI stats) {
        return stats == null || stats.getVariant() == null ? null : stats.getVariant().getHullSpec();
    }

    static boolean hasSystem(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        String system = hull.getShipSystemId();
        return system != null && !system.isEmpty();
    }

    static boolean hasSystemCharges(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (!hasSystem(hull, stats)) return false;
        try {
            com.fs.starfarer.api.combat.ShipSystemSpecAPI spec = Global.getSettings().getShipSystemSpec(hull.getShipSystemId());
            if (spec == null) return false;
            int uses = spec.getMaxUses(stats);
            return uses > 0 && uses < 1000;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean hasPhase(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        return hull.getShieldType() == ShieldType.PHASE || hull.isPhase();
    }

    static boolean hasFighterBays(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (hull.getFighterBays() > 0) return true;
        return stats != null && stats.getNumFighterBays().getModifiedValue() > 0f;
    }

    static boolean hasMissileSlots(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        for (WeaponSlotAPI slot : hull.getAllWeaponSlotsCopy()) {
            if (slot.isDecorative() || slot.isSystemSlot() || slot.isStationModule()) continue;
            WeaponType type = slot.getWeaponType();
            if (type == WeaponType.MISSILE || type == WeaponType.COMPOSITE
                    || type == WeaponType.SYNERGY || type == WeaponType.UNIVERSAL) {
                return true;
            }
        }
        return false;
    }

    static void floatText(ShipAPI ship, String text, Color color) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship.getLocation() == null) return;
        engine.addFloatingText(ship.getLocation(), text, 24f, color, ship, 0.5f, 1f);
    }

    static void status(ShipAPI ship, Augment augment, String text) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.getPlayerShip() != ship) return;
        engine.maintainStatusForPlayerShip("esro_status_" + augment.id, iconPath(augment), augment.name, text, false);
    }

    /** Every icon is also registered under the "esro" sprite category in settings.json, so it is always loaded. */
    public static String iconPath(Augment augment) {
        return "graphics/icons/cargo/" + augment.id + ".png";
    }

    private static Map<String, Float> materials(Object... pairs) {
        Map<String, Float> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        }
        return result;
    }
}
