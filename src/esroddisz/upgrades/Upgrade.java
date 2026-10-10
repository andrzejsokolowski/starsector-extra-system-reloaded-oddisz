package esroddisz.upgrades;

import static esroddisz.upgrades.Effect.flat;
import static esroddisz.upgrades.Effect.mult;
import static esroddisz.upgrades.Effect.pct;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * Every upgrade the bay sells. Each has levels up to the maximum in the settings; its stat lines scale
 * linearly with level, hull quality and the strength setting. Ids are stored in saves - never rename one.
 */
public enum Upgrade {

    DURABILITY("durability", "Durability", Category.COMBAT, 1.0f,
            "Welded armour plates, reinforced bulkheads and hardened conduits.",
            materials(Commodities.METALS, 3, Commodities.HEAVY_MACHINERY, 2, Commodities.RARE_METALS, 1, Commodities.SUPPLIES, 1),
            null,
            Effect.percent("Hull integrity", 30f, (s, id, v) -> pct(s.getHullBonus(), id, v)),
            Effect.percent("Armor rating", 20f, (s, id, v) -> pct(s.getArmorBonus(), id, v)),
            Effect.percent("EMP damage taken", -40f, (s, id, v) -> mult(s.getEmpDamageTakenMult(), id, v)),
            Effect.percent("Weapon and engine durability", 50f, (s, id, v) -> {
                pct(s.getWeaponHealthBonus(), id, v);
                pct(s.getEngineHealthBonus(), id, v);
            }),
            Effect.percent("Weapon and engine repair time in combat", -40f, (s, id, v) -> {
                mult(s.getCombatWeaponRepairTimeMult(), id, v);
                mult(s.getCombatEngineRepairTimeMult(), id, v);
            })),

    WEAPONS("weapons", "Weapons", Category.COMBAT, 1.3f,
            "Overhauled mounts, feeds and focusing optics: every gun hits harder and runs cooler.",
            materials(Commodities.HAND_WEAPONS, 2, Commodities.METALS, 2, Commodities.RARE_METALS, 2, Commodities.HEAVY_MACHINERY, 1),
            null,
            Effect.percent("Weapon damage", 15f, (s, id, v) -> {
                pct(s.getBallisticWeaponDamageMult(), id, v);
                pct(s.getEnergyWeaponDamageMult(), id, v);
                pct(s.getMissileWeaponDamageMult(), id, v);
            }),
            Effect.percent("Weapon flux cost", -20f, (s, id, v) -> {
                mult(s.getBallisticWeaponFluxCostMod(), id, v);
                mult(s.getEnergyWeaponFluxCostMod(), id, v);
                mult(s.getMissileWeaponFluxCostMod(), id, v);
            })),

    TARGETING("targeting", "Targeting", Category.COMBAT, 1.2f,
            "Fire control computers and stabilised turrets that reach further and miss less.",
            materials(Commodities.RARE_METALS, 2, Commodities.VOLATILES, 1, Commodities.HEAVY_MACHINERY, 1, Commodities.HAND_WEAPONS, 1),
            null,
            Effect.percent("Weapon range", 15f, (s, id, v) -> {
                pct(s.getBallisticWeaponRangeBonus(), id, v);
                pct(s.getEnergyWeaponRangeBonus(), id, v);
                pct(s.getMissileWeaponRangeBonus(), id, v);
            }),
            Effect.percent("Weapon turn rate", 50f, (s, id, v) -> {
                pct(s.getWeaponTurnRateBonus(), id, v);
                pct(s.getBeamWeaponTurnRateBonus(), id, v);
            }),
            Effect.percent("Recoil", -40f, (s, id, v) -> {
                mult(s.getMaxRecoilMult(), id, v);
                mult(s.getRecoilPerShotMult(), id, v);
            }),
            Effect.percent("Autofire target leading", 50f, (s, id, v) -> flat(s.getAutofireAimAccuracy(), id, v / 100f)),
            Effect.percent("Projectile speed", 20f, (s, id, v) -> pct(s.getProjectileSpeedMult(), id, v))),

    MAGAZINES("magazines", "Magazines", Category.COMBAT, 1.0f,
            "Faster autoloaders and deeper ammunition stores.",
            materials(Commodities.VOLATILES, 2, Commodities.METALS, 1, Commodities.HEAVY_MACHINERY, 1, Commodities.HAND_WEAPONS, 1),
            null,
            Effect.percent("Rate of fire", 15f, (s, id, v) -> {
                pct(s.getBallisticRoFMult(), id, v);
                pct(s.getEnergyRoFMult(), id, v);
                pct(s.getMissileRoFMult(), id, v);
            }),
            Effect.percent("Ammunition capacity", 50f, (s, id, v) -> {
                pct(s.getBallisticAmmoBonus(), id, v);
                pct(s.getEnergyAmmoBonus(), id, v);
                pct(s.getMissileAmmoBonus(), id, v);
            }),
            Effect.percent("Ammunition regeneration", 50f, (s, id, v) -> {
                pct(s.getBallisticAmmoRegenMult(), id, v);
                pct(s.getEnergyAmmoRegenMult(), id, v);
                pct(s.getMissileAmmoRegenMult(), id, v);
            })),

    FLUX_GRID("flux_grid", "Flux Grid", Category.COMBAT, 1.2f,
            "Rewired capacitor banks and coolant loops.",
            materials(Commodities.RARE_METALS, 2, Commodities.VOLATILES, 2, Commodities.HEAVY_MACHINERY, 1),
            null,
            Effect.percent("Flux capacity", 20f, (s, id, v) -> pct(s.getFluxCapacity(), id, v)),
            Effect.percent("Flux dissipation", 20f, (s, id, v) -> pct(s.getFluxDissipation(), id, v)),
            Effect.percent("Vent rate", 50f, (s, id, v) -> pct(s.getVentRateMult(), id, v)),
            Effect.percent("Overload duration", -30f, (s, id, v) -> mult(s.getOverloadTimeMod(), id, v))),

    DEFENSES("defenses", "Defenses", Category.COMBAT, 1.1f,
            "Shield emitters or phase coils tuned past their rated limits.",
            materials(Commodities.RARE_METALS, 3, Commodities.VOLATILES, 1, Commodities.HEAVY_MACHINERY, 1, Commodities.SUPPLIES, 1),
            "This ship has neither shields nor a phase cloak.",
            Effect.percent("Shield damage taken", -20f, -60f, (s, id, v) -> mult(s.getShieldDamageTakenMult(), id, v))
                    .when(Upgrade::hasShield),
            Effect.percent("Shield upkeep", -50f, -100f, (s, id, v) -> mult(s.getShieldUpkeepMult(), id, v))
                    .when(Upgrade::hasShield),
            Effect.percent("Shield raise and turn speed", 100f, (s, id, v) -> {
                pct(s.getShieldUnfoldRateMult(), id, v);
                pct(s.getShieldTurnRateMult(), id, v);
            }).when(Upgrade::hasShield),
            Effect.flat("Shield arc (degrees)", 30f, (s, id, v) -> flat(s.getShieldArcBonus(), id, v))
                    .when(Upgrade::hasShield),
            Effect.percent("Phase activation cost", -40f, (s, id, v) -> mult(s.getPhaseCloakActivationCostBonus(), id, v))
                    .when(Upgrade::hasPhase),
            Effect.percent("Phase upkeep", -40f, (s, id, v) -> mult(s.getPhaseCloakUpkeepCostBonus(), id, v))
                    .when(Upgrade::hasPhase),
            Effect.percent("Phase cooldown", -40f, (s, id, v) -> mult(s.getPhaseCloakCooldownBonus(), id, v))
                    .when(Upgrade::hasPhase)),

    ENGINES("engines", "Engines", Category.COMBAT, 0.9f,
            "Uprated drive coils and manoeuvring thrusters.",
            materials(Commodities.VOLATILES, 3, Commodities.METALS, 1, Commodities.RARE_METALS, 1, Commodities.HEAVY_MACHINERY, 1),
            null,
            Effect.percent("Top speed", 15f, (s, id, v) -> pct(s.getMaxSpeed(), id, v)),
            Effect.percent("Acceleration and deceleration", 40f, (s, id, v) -> {
                pct(s.getAcceleration(), id, v);
                pct(s.getDeceleration(), id, v);
            }),
            Effect.percent("Turn rate and turn acceleration", 40f, (s, id, v) -> {
                pct(s.getMaxTurnRate(), id, v);
                pct(s.getTurnAcceleration(), id, v);
            }),
            Effect.whole("Burn level", 2f, (s, id, v) -> flat(s.getMaxBurnLevel(), id, v))),

    SYSTEMS("systems", "Ship System", Category.COMBAT, 1.1f,
            "An overclocked ship system that recharges faster and, if it has charges, holds more of them.",
            materials(Commodities.RARE_METALS, 3, Commodities.VOLATILES, 1, Commodities.HEAVY_MACHINERY, 1),
            "This ship has no ship system.",
            Effect.percent("System cooldown", -40f, (s, id, v) -> mult(s.getSystemCooldownBonus(), id, v))
                    .when(Upgrade::hasSystem),
            Effect.whole("System charges", 2f, (s, id, v) -> flat(s.getSystemUsesBonus(), id, v))
                    .when(Upgrade::hasSystemCharges),
            Effect.percent("Charge regeneration", 50f, (s, id, v) -> pct(s.getSystemRegenBonus(), id, v))
                    .when(Upgrade::hasSystemCharges),
            Effect.percent("System range", 30f, (s, id, v) -> pct(s.getSystemRangeBonus(), id, v))
                    .when(Upgrade::hasSystem),
            Effect.percent("System flux cost", -40f, -100f, (s, id, v) -> mult(s.getSystemFluxCostBonus(), id, v))
                    .when(Upgrade::hasSystem)),

    HANGARS("hangars", "Hangars", Category.COMBAT, 1.0f,
            "Automated assembly lines that keep the flight decks full.",
            materials(Commodities.HEAVY_MACHINERY, 3, Commodities.METALS, 2, Commodities.SUPPLIES, 1, Commodities.VOLATILES, 1),
            "This ship has no fighter bays.",
            Effect.percent("Fighter replacement time", -30f, (s, id, v) -> mult(s.getFighterRefitTimeMult(), id, v))
                    .when(Upgrade::hasFighterBays),
            Effect.percent("Replacement rate recovery", 50f,
                    (s, id, v) -> pct(s.getDynamic().getStat(Stats.REPLACEMENT_RATE_INCREASE_MULT), id, v))
                    .when(Upgrade::hasFighterBays),
            Effect.percent("Replacement rate loss", -40f,
                    (s, id, v) -> mult(s.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT), id, v))
                    .when(Upgrade::hasFighterBays),
            Effect.percent("Fighter engagement range", 30f, (s, id, v) -> pct(s.getFighterWingRange(), id, v))
                    .when(Upgrade::hasFighterBays)),

    LOGISTICS("logistics", "Logistics", Category.FLEET, 0.6f,
            "Leaner maintenance cycles and automated systems that need fewer hands.",
            materials(Commodities.SUPPLIES, 2, Commodities.METALS, 1, Commodities.HEAVY_MACHINERY, 1, Commodities.ORGANICS, 1),
            null,
            Effect.percent("Monthly supply use", -40f, (s, id, v) -> mult(s.getSuppliesPerMonth(), id, v)),
            Effect.percent("Fuel use", -40f, (s, id, v) -> mult(s.getFuelUseMod(), id, v)),
            Effect.percent("Supplies to recover", -40f, (s, id, v) -> mult(s.getSuppliesToRecover(), id, v)),
            Effect.percent("Required crew", -30f, (s, id, v) -> mult(s.getMinCrewMod(), id, v))),

    HOLDS("holds", "Cargo Holds", Category.FLEET, 0.5f,
            "Rebuilt holds, tanks and crew quarters.",
            materials(Commodities.METALS, 3, Commodities.HEAVY_MACHINERY, 1, Commodities.SUPPLIES, 1),
            null,
            Effect.percent("Cargo capacity", 40f, (s, id, v) -> pct(s.getCargoMod(), id, v)),
            Effect.percent("Fuel capacity", 40f, (s, id, v) -> pct(s.getFuelMod(), id, v)),
            Effect.percent("Crew capacity", 40f, (s, id, v) -> pct(s.getMaxCrewMod(), id, v))),

    SENSORS("sensors", "Sensors", Category.FLEET, 0.5f,
            "Better sensor arrays and a quieter emissions profile.",
            materials(Commodities.RARE_METALS, 2, Commodities.VOLATILES, 1, Commodities.HEAVY_MACHINERY, 1),
            null,
            Effect.percent("Sensor strength", 40f, (s, id, v) -> pct(s.getSensorStrength(), id, v)),
            Effect.percent("Sensor profile", -40f, (s, id, v) -> mult(s.getSensorProfile(), id, v))),

    CONDITIONING("conditioning", "Conditioning", Category.FLEET, 0.8f,
            "Crew drills, spare parts and redundancies that keep the ship fighting longer. "
                    + "Peak performance time grows more on smaller hulls.",
            materials(Commodities.SUPPLIES, 3, Commodities.ORGANICS, 1, Commodities.HEAVY_MACHINERY, 1),
            null,
            Effect.percent("Peak performance time", 40f, (s, id, v) -> pct(s.getPeakCRDuration(), id, v))
                    .byHullSize(2f, 1.5f, 1.25f, 1f),
            Effect.percent("CR loss after peak performance", -40f, (s, id, v) -> mult(s.getCRLossPerSecondPercent(), id, v)),
            Effect.percent("CR per deployment", -30f, -60f, (s, id, v) -> mult(s.getCRPerDeploymentPercent(), id, v)),
            Effect.percent("Daily CR recovery", 50f, (s, id, v) -> pct(s.getBaseCRRecoveryRatePercentPerDay(), id, v)),
            Effect.percent("Hull repair rate", 50f, (s, id, v) -> pct(s.getRepairRatePercentPerDay(), id, v)));

    private final String id;
    private final String name;
    private final Category category;
    private final float costWeight;
    private final String description;
    private final Map<String, Float> materials;
    private final String notApplicableReason;
    private final List<Effect> effects;

    Upgrade(String id, String name, Category category, float costWeight, String description,
            Map<String, Float> materials, String notApplicableReason, Effect... effects) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.costWeight = costWeight;
        this.description = description;
        this.materials = Collections.unmodifiableMap(materials);
        this.notApplicableReason = notApplicableReason;
        this.effects = Collections.unmodifiableList(Arrays.asList(effects));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public float getCostWeight() {
        return costWeight;
    }

    public String getDescription() {
        return description;
    }

    /** Commodity id to share of the material bill. */
    public Map<String, Float> getMaterials() {
        return materials;
    }

    public List<Effect> getEffects() {
        return effects;
    }

    public String getNotApplicableReason() {
        return notApplicableReason == null ? "Not available for this ship." : notApplicableReason;
    }

    /** True if at least one stat line does something on this hull. */
    public boolean appliesTo(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        for (Effect e : effects) {
            if (e.appliesTo(hull, stats)) return true;
        }
        return false;
    }

    /** Writes every applicable line at {@code power} into the stats under {@code idPrefix}. */
    public void apply(MutableShipStatsAPI stats, HullSize size, float power, String idPrefix) {
        if (power <= 0f) return;
        ShipHullSpecAPI hull = stats.getVariant() == null ? null : stats.getVariant().getHullSpec();
        String id = idPrefix + "_" + this.id;
        for (Effect e : effects) {
            if (e.appliesTo(hull, stats)) e.apply(stats, id, power, size);
        }
    }

    public static Upgrade byId(String id) {
        for (Upgrade u : values()) {
            if (u.id.equals(id)) return u;
        }
        return null;
    }

    public enum Category {
        COMBAT("Combat upgrades"),
        FLEET("Fleet upgrades");

        private final String title;

        Category(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    // ------------------------------------------------------------ conditions

    static boolean hasShield(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        ShieldType type = hull.getShieldType();
        return type == ShieldType.FRONT || type == ShieldType.OMNI;
    }

    static boolean hasPhase(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        return hull.getShieldType() == ShieldType.PHASE || hull.isPhase();
    }

    static boolean hasSystem(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        String system = hull.getShipSystemId();
        return system != null && !system.isEmpty();
    }

    /** Systems with a limited number of charges. Unlimited systems report a huge count or none at all. */
    static boolean hasSystemCharges(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (!hasSystem(hull, stats)) return false;
        ShipSystemSpecAPI spec = Global.getSettings().getShipSystemSpec(hull.getShipSystemId());
        if (spec == null) return false;
        int uses = spec.getMaxUses(stats);
        return uses > 0 && uses < 1000;
    }

    static boolean hasFighterBays(ShipHullSpecAPI hull, MutableShipStatsAPI stats) {
        if (hull.getFighterBays() > 0) return true;
        return stats != null && stats.getNumFighterBays().getModifiedValue() > 0f;
    }

    private static Map<String, Float> materials(Object... pairs) {
        Map<String, Float> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        }
        return result;
    }
}
