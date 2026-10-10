package esroddisz.tests;

import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI.OptionTooltipCreator;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.SubmarketPlugin.OnClickAction;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.MutableValue;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

import esroddisz.Pricing;
import esroddisz.Quality;
import esroddisz.Settings;
import esroddisz.ShipData;
import esroddisz.augments.Augment;
import esroddisz.campaign.HullmodSync;
import esroddisz.campaign.Payment;
import esroddisz.campaign.Shipyard;
import esroddisz.dialog.WorkshopDialog;
import esroddisz.hullmods.ExtraSystemsHullmod;
import esroddisz.upgrades.Effect;
import esroddisz.upgrades.Upgrade;

/**
 * Standalone checks. Runs in its own JVM against mock game objects and the real game API jar;
 * never touches a live game, a save or the mods folder.
 */
public class Checks {

    private static int assertions;
    private static final String CORE = System.getProperty("starsectorPath", "D:/Games/StarSector") + "/starsector-core";

    public static void main(String[] args) throws Exception {
        Settings.source = new Settings.Source() {
            public Double getDouble(String field) { return null; }
            public Integer getInt(String field) { return null; }
            public Boolean getBoolean(String field) { return null; }
        };
        World.install();

        pricingChecks();
        qualityChecks();
        effectChecks();
        catalogueChecks();
        shipDataChecks();
        statChecks();
        hullmodChecks();
        syncChecks();
        robustnessChecks();
        dialogChecks();
        storageAndMaterialChecks();

        System.out.println("Extra Systems checks passed: " + assertions + " assertions.");
    }

    // ================================================================= pure

    private static void pricingChecks() {
        check(Pricing.effectiveHullValue(0f) == Pricing.effectiveHullValue(Pricing.MIN_HULL_VALUE), "tiny hulls priced at the minimum");
        float prev = 0f;
        for (float v : new float[]{10000f, 50000f, 200000f, 1000000f, 5000000f}) {
            float e = Pricing.effectiveHullValue(v);
            check(e > prev, "effective hull value rises with hull value");
            check(e < Pricing.HULL_VALUE_SOFT_CAP, "effective hull value stays under the soft cap");
            prev = e;
        }
        float last = 0f;
        for (int level = 1; level <= 15; level++) {
            float c = Pricing.levelCost(100000f, 1f, level, 10, 1f);
            check(c > last, "each level costs more than the one before (level " + level + ")");
            last = c;
        }
        float frigateSet = 0f, capitalSet = 0f;
        for (int level = 1; level <= 10; level++) {
            for (Upgrade u : Upgrade.values()) {
                frigateSet += Pricing.levelCost(20000f, u.getCostWeight(), level, 10, 1f);
                capitalSet += Pricing.levelCost(300000f, u.getCostWeight(), level, 10, 1f);
            }
        }
        check(frigateSet > 150000f && frigateSet < 1500000f, "a frigate's full set costs hundreds of thousands: " + frigateSet);
        check(capitalSet > 2000000f && capitalSet < 20000000f, "a capital's full set costs millions: " + capitalSet);
        check(Pricing.qualityCost(100000f, 2f, 0.1f, 1f) > 7f * Pricing.qualityCost(100000f, 1f, 0.1f, 1f),
                "quality gets steeply more expensive");
        check(Pricing.round(12345f) == 12300 && Pricing.round(10f) == 100 && Pricing.round(0f) == 0, "rounding");
        check(near(Pricing.discounted(1000f, 0.25f), 750f) && near(Pricing.discounted(1000f, 5f), 100f), "discount is capped at 90%");
        check(Pricing.power(5, 10, 1.2f, 1f) == 0.6f, "power is level share times quality");
        check(Pricing.power(0, 10, 2f, 1f) == 0f, "no level, no power");
    }

    private static void qualityChecks() {
        float a = Quality.roll("member-1", true, 0.8f, 1.2f);
        check(a == Quality.roll("member-1", true, 0.8f, 1.2f), "quality roll is stable for one ship");
        Set<Float> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            float q = Quality.roll("ship_" + i, true, 0.8f, 1.2f);
            check(q >= 0.8f && q <= 1.2f, "quality roll stays in range: " + q);
            seen.add(q);
        }
        check(seen.size() > 20, "quality rolls vary between ships");
        check(Quality.roll("x", false, 0.8f, 1.2f) == 1f, "random quality off starts at 100%");
        check(Quality.name(0.85f).equals("Crude") && Quality.name(1f).equals("Standard")
                && Quality.name(2f).equals("Domain-grade"), "quality band names");
        check(Quality.describe(1.12f).equals("112% (Fine)"), "quality description: " + Quality.describe(1.12f));
    }

    private static void effectChecks() {
        Effect cut = Effect.percent("Test", -40f, (s, id, v) -> { });
        check(cut.valueAt(1f, HullSize.CRUISER) == -40f, "reduction at full power");
        check(cut.valueAt(5f, HullSize.CRUISER) == -80f, "reductions stop at their floor");
        Effect whole = Effect.whole("Burn", 2f, (s, id, v) -> { });
        check(whole.valueAt(0.49f, HullSize.FRIGATE) == 0f && whole.valueAt(0.5f, HullSize.FRIGATE) == 1f
                && whole.valueAt(1.5f, HullSize.FRIGATE) == 3f, "whole effects round down");
        Effect sized = Effect.percent("PPT", 40f, (s, id, v) -> { }).byHullSize(2f, 1.5f, 1.25f, 1f);
        check(sized.valueAt(1f, HullSize.FRIGATE) == 80f && sized.valueAt(1f, HullSize.CAPITAL_SHIP) == 40f, "hull size scaling");
        check(cut.format(-40f).equals("-40%") && cut.format(1.5f).equals("+1.5%") && cut.format(12.4f).equals("+12%"),
                "percent formatting");
        check(whole.format(2f).equals("+2"), "whole formatting");
    }

    private static void catalogueChecks() throws Exception {
        Set<String> ids = new HashSet<>();
        Set<String> commodities = new HashSet<>(World.commodityPrices.keySet());
        for (Upgrade u : Upgrade.values()) {
            check(ids.add(u.getId()), "upgrade ids are unique: " + u.getId());
            check(!u.getEffects().isEmpty(), u.getName() + " has effects");
            check(!u.getMaterials().isEmpty(), u.getName() + " has materials");
            for (String c : u.getMaterials().keySet()) check(commodities.contains(c), u.getName() + " uses a real commodity: " + c);
            check(Upgrade.byId(u.getId()) == u, "upgrade lookup by id");
        }
        Set<String> augmentIds = new HashSet<>();
        for (Augment a : Augment.values()) {
            check(augmentIds.add(a.getId()) && a.getId().startsWith("esro_"), "augment ids are unique and prefixed: " + a.getId());
            check(!a.getEffectLines().isEmpty(), a.getName() + " describes its effect");
            for (String c : a.getMaterials().keySet()) check(commodities.contains(c), a.getName() + " uses a real commodity: " + c);
            check(Augment.byId(a.getId()) == a, "augment lookup by id");
        }
        check(Augment.EqualizerRange.bonusFor(400f) == 200f && Augment.EqualizerRange.bonusFor(700f) == 100f
                && Augment.EqualizerRange.bonusFor(900f) == 0f, "range equalizer bonus");
        String sounds = new String(Files.readAllBytes(Paths.get(CORE, "data/config/sounds.json")), StandardCharsets.UTF_8);
        for (String sound : new String[]{"ui_upgrade_industry", "ui_industry_install_any_item", "ui_cargo_special_tech_drop"}) {
            check(sounds.contains("\"" + sound + "\""), "vanilla has sound " + sound);
        }
        JSONObject yards = World.shipyardConfig();
        check(yards.getJSONObject("industries").has("orbitalworks") && yards.getJSONObject("tags").has("heavyindustry"),
                "settings.json lists the vanilla shipyards");
    }

    private static void shipDataChecks() {
        Map<String, Object> record = new HashMap<>();
        ShipData data = ShipData.wrap("m1", record);
        check(!data.hasEffects(), "empty record has no effects");
        data.setLevel(Upgrade.WEAPONS, 3);
        data.addAugment(Augment.NANOLATTICE);
        data.addAugment(Augment.NANOLATTICE);
        data.setQuality(1.234567f);
        check(data.hasEffects() && data.getLevel(Upgrade.WEAPONS) == 3, "levels are stored");
        check(data.getAugments().size() == 1, "an augment is stored once");
        check(Math.abs(data.getQuality() - 1.235f) < 0.0001f, "quality is stored rounded");
        data.setLevel(Upgrade.WEAPONS, 0);
        data.removeAugment(Augment.NANOLATTICE);
        check(!data.hasEffects(), "clearing levels and augments clears effects");
        ((List<String>) record.get("augments")).add("esro_from_a_future_version");
        check(data.getAugments().isEmpty(), "unknown augment ids are skipped");

        // The save must hold plain values only, so the mod's classes never end up in it.
        Map<String, Map<String, Object>> store = new HashMap<>();
        Map<String, Object> saved = new HashMap<>();
        ShipData.wrap("m2", saved).setLevel(Upgrade.DURABILITY, 4);
        ShipData.wrap("m2", saved).addAugment(Augment.OVERLOAD_DAMPER);
        ShipData.wrap("m2", saved).setQuality(1.1f);
        ShipData.wrap("m2", saved).addInvested(12345f);
        store.put("m2", saved);
        XStream xs = new XStream(new StaxDriver());
        String xml = xs.toXML(store);
        check(!xml.contains("esroddisz"), "saved data names no mod class:\n" + xml);
    }

    // ================================================================= stats

    private static void statChecks() {
        World.Hull shieldHull = new World.Hull("shield_cruiser", HullSize.CRUISER, ShieldType.FRONT, 0, "fortressshield", 120000f);
        World.Stats stats = new World.Stats(shieldHull, null);
        for (Upgrade u : Upgrade.values()) u.apply(stats.proxy, HullSize.CRUISER, 1f, "t");
        check(eff(stats.bonus("getHullBonus")) == 130f, "durability adds 30% hull");
        check(eff(stats.bonus("getArmorBonus")) == 120f, "durability adds 20% armor");
        check(near(eff(stats.stat("getEmpDamageTakenMult")), 60f), "durability cuts EMP damage by 40%");
        check(near(eff(stats.stat("getBallisticWeaponDamageMult")), 115f), "weapons add damage");
        check(near(eff(stats.bonus("getEnergyWeaponFluxCostMod")), 80f), "weapons cut flux cost");
        check(near(eff(stats.stat("getFluxCapacity")), 120f), "flux grid adds capacity");
        check(near(eff(stats.stat("getShieldDamageTakenMult")), 80f), "shield ship gets shield efficiency");
        check(eff(stats.bonus("getPhaseCloakCooldownBonus")) == 100f, "shield ship gets no phase changes");
        check(eff(stats.bonus("getShieldArcBonus")) == 130f, "shield arc is flat degrees");
        check(eff(stats.stat("getMaxBurnLevel")) == 102f, "engines add two burn levels");
        check(eff(stats.bonus("getSystemUsesBonus")) == 102f, "limited system gains charges");
        check(eff(stats.bonus("getFighterWingRange")) == 100f, "no fighter bays, no hangar changes");
        check(near(eff(stats.bonus("getPeakCRDuration")), 150f), "cruiser peak time scales by 1.25");
        check(near(eff(stats.stat("getSuppliesPerMonth")), 60f), "logistics cuts supply use");
        check(near(eff(stats.dynamic(Stats.REPLACEMENT_RATE_DECREASE_MULT)), 100f), "no hangar dynamic stats on a gunship");

        World.Hull phaseCarrier = new World.Hull("phase_carrier", HullSize.DESTROYER, ShieldType.PHASE, 2, "unlimited_system", 80000f);
        World.Stats ps = new World.Stats(phaseCarrier, null);
        for (Upgrade u : Upgrade.values()) u.apply(ps.proxy, HullSize.DESTROYER, 3f, "t");
        check(near(eff(ps.bonus("getPhaseCloakCooldownBonus")), 20f), "phase cooldown cut floors at -80%");
        check(eff(ps.stat("getShieldDamageTakenMult")) == 100f, "phase ship gets no shield changes");
        check(eff(ps.bonus("getSystemUsesBonus")) == 100f, "unlimited system gains no charges");
        check(near(eff(ps.stat("getFighterRefitTimeMult")), 20f), "carrier fighter refit floors at -80%");
        check(near(eff(ps.dynamic(Stats.REPLACEMENT_RATE_INCREASE_MULT)), 250f), "carrier replacement recovery scales");
        check(near(eff(ps.bonus("getPeakCRDuration")), 280f), "destroyer peak time at power 3");

        check(Upgrade.DEFENSES.appliesTo(shieldHull.proxy, null) && Upgrade.DEFENSES.appliesTo(phaseCarrier.proxy, null),
                "defenses fit shield and phase ships");
        World.Hull bare = new World.Hull("bare", HullSize.FRIGATE, ShieldType.NONE, 0, null, 5000f);
        check(!Upgrade.DEFENSES.appliesTo(bare.proxy, null) && !Upgrade.SYSTEMS.appliesTo(bare.proxy, null)
                && !Upgrade.HANGARS.appliesTo(bare.proxy, null), "upgrades that cannot work are refused");
        check(!Augment.MISSILE_AUTOFORGE.canInstallOn(bare.proxy, null), "missile autoforge needs missile slots");
        bare.slots.add(World.slot(WeaponType.COMPOSITE));
        check(Augment.MISSILE_AUTOFORGE.canInstallOn(bare.proxy, null), "composite slots can mount missiles");
        check(Augment.PHASE_COIL.canInstallOn(phaseCarrier.proxy, null) && !Augment.PHASE_COIL.canInstallOn(shieldHull.proxy, null),
                "phase coil needs a phase ship");

        for (Augment a : Augment.values()) {
            World.Stats as = new World.Stats(phaseCarrier, null);
            a.applyStats(as.proxy, HullSize.DESTROYER, "aug");
        }
    }

    private static void hullmodChecks() {
        World.reset();
        World.Member member = World.member("hm1", "ISS Hammer", new World.Hull("hammer", HullSize.CRUISER, ShieldType.OMNI, 0, "fortressshield", 150000f));
        World.Stats stats = new World.Stats(member.hull, member.proxy);
        ExtraSystemsHullmod mod = new ExtraSystemsHullmod();
        mod.applyEffectsBeforeShipCreation(HullSize.CRUISER, stats.proxy, ExtraSystemsHullmod.ID);
        check(eff(stats.bonus("getHullBonus")) == 100f, "a ship without a record is untouched");

        ShipData data = ShipData.getOrCreate(member.proxy);
        data.setQuality(1.5f);
        data.setLevel(Upgrade.DURABILITY, 10);
        data.addAugment(Augment.CRYO_EXCHANGER);
        World.Stats upgraded = new World.Stats(member.hull, member.proxy);
        mod.applyEffectsBeforeShipCreation(HullSize.CRUISER, upgraded.proxy, ExtraSystemsHullmod.ID);
        check(near(eff(upgraded.bonus("getHullBonus")), 145f), "hull bonus scales with quality: " + eff(upgraded.bonus("getHullBonus")));
        check(near(eff(upgraded.stat("getFluxDissipation")), 125f), "augment stats apply");

        World.Stats module = new World.Stats(member.hull, null);
        module.variant.tags.add(ShipData.OWNER_TAG_PREFIX + "hm1");
        mod.applyEffectsBeforeShipCreation(HullSize.CRUISER, module.proxy, ExtraSystemsHullmod.ID);
        check(near(eff(module.bonus("getHullBonus")), 145f), "modules find their ship through the owner tag");

        TooltipMakerAPI tip = World.tooltip(new ArrayList<String>());
        ShipAPI ship = World.ship(upgraded.proxy);
        mod.addPostDescriptionSection(tip, HullSize.CRUISER, ship, 400f, false);
    }

    private static void syncChecks() {
        World.reset();
        World.Member member = World.member("s1", "ISS Anvil", new World.Hull("anvil", HullSize.CAPITAL_SHIP, ShieldType.FRONT, 0, null, 300000f));
        World.Variant moduleVariant = new World.Variant(member.hull);
        member.variant.modules.put("WS0001", moduleVariant);
        member.variant.stock = true;
        HullmodSync.sync(member.proxy);
        check(!member.current().hasHullMod(ExtraSystemsHullmod.ID), "no record, no hull mod");

        ShipData.getOrCreate(member.proxy).setLevel(Upgrade.ENGINES, 1);
        HullmodSync.sync(member.proxy);
        World.Variant v = member.current();
        check(v != member.variant, "a shared stock variant is copied before editing");
        check(v.permaMods.contains(ExtraSystemsHullmod.ID) && v.hasHullMod(ExtraSystemsHullmod.ID), "hull mod installed as permanent");
        check(v.tags.contains(ShipData.OWNER_TAG_PREFIX + "s1"), "the ship carries its owner tag");
        World.Variant m = v.modules.get("WS0001");
        check(m != moduleVariant && m.hasHullMod(ExtraSystemsHullmod.ID) && m.tags.contains(ShipData.OWNER_TAG_PREFIX + "s1"),
                "modules get the hull mod and the owner tag");
        int updates = member.updates;
        HullmodSync.sync(member.proxy);
        check(member.updates == updates, "an up-to-date ship is left alone");

        ShipData.getOrCreate(member.proxy).setLevel(Upgrade.ENGINES, 0);
        HullmodSync.sync(member.proxy);
        v = member.current();
        check(!v.hasHullMod(ExtraSystemsHullmod.ID) && !v.permaMods.contains(ExtraSystemsHullmod.ID), "hull mod removed with the last upgrade");
        check(v.tags.isEmpty(), "owner tag removed with the last upgrade");
        check(!v.modules.get("WS0001").hasHullMod(ExtraSystemsHullmod.ID) && v.modules.get("WS0001").tags.isEmpty(),
                "module traces removed too");

        World.Member copy = World.member("s2", "ISS Copy", member.hull);
        copy.variant.permaMods.add(ExtraSystemsHullmod.ID);
        copy.variant.hullMods.add(ExtraSystemsHullmod.ID);
        HullmodSync.sync(copy.proxy);
        check(!copy.current().hasHullMod(ExtraSystemsHullmod.ID), "a hull mod copied by a loadout is removed");
    }

    private static void robustnessChecks() {
        // Point Defense Integrator: never leaves a ship with only point defense guns.
        WeaponAPI smallGun = World.weapon(WeaponType.BALLISTIC, WeaponSize.SMALL, false);
        WeaponAPI smallBeam = World.weapon(WeaponType.ENERGY, WeaponSize.SMALL, false);
        WeaponAPI mediumGun = World.weapon(WeaponType.BALLISTIC, WeaponSize.MEDIUM, false);
        WeaponAPI mediumPd = World.weapon(WeaponType.ENERGY, WeaponSize.MEDIUM, true);
        WeaponAPI missile = World.weapon(WeaponType.MISSILE, WeaponSize.MEDIUM, false);
        check(Augment.pdConversions(Arrays.asList(smallGun, smallBeam)).isEmpty(), "a ship of small guns keeps them");
        check(Augment.pdConversions(Arrays.asList(smallGun, smallBeam, missile)).isEmpty(), "missiles do not count as a main gun");
        check(Augment.pdConversions(Arrays.asList(smallGun, mediumPd)).isEmpty(), "a point defense medium does not count");
        List<WeaponAPI> converted = Augment.pdConversions(Arrays.asList(smallGun, smallBeam, mediumGun, missile));
        check(converted.size() == 2 && converted.contains(smallGun) && converted.contains(smallBeam),
                "small guns convert when a medium gun remains");
        check(Augment.pdConversions(null).isEmpty(), "no weapons, no conversions");

        // The background check must survive a ship that throws.
        World.reset();
        FleetMemberAPI broken = World.proxy(FleetMemberAPI.class, (m, a) -> {
            if (m.getName().equals("getId")) throw new IllegalStateException("broken modded ship");
            return null;
        });
        World.Member fine = World.member("r1", "ISS Fine", new World.Hull("fine", HullSize.FRIGATE, ShieldType.FRONT, 0, null, 20000f));
        World.fleet.add(broken);
        World.fleet.add(fine.proxy);
        ShipData.getOrCreate(fine.proxy).setLevel(Upgrade.ENGINES, 1);
        org.apache.log4j.Logger faultLog = org.apache.log4j.Logger.getLogger(esroddisz.Faults.class);
        org.apache.log4j.Level before = faultLog.getLevel();
        faultLog.setLevel(org.apache.log4j.Level.OFF);
        new HullmodSync().advance(1f);
        faultLog.setLevel(before);
        check(fine.current().hasHullMod(ExtraSystemsHullmod.ID), "the other ships are still looked after");

        // Every augment icon is registered so combat can always draw it.
        try {
            String settings = new String(Files.readAllBytes(Paths.get("data/config/settings.json")), StandardCharsets.UTF_8);
            for (Augment a : Augment.values()) {
                check(settings.contains("\"" + a.getId() + "\":\"" + Augment.iconPath(a) + "\""),
                        "icon registered in settings.json: " + a.getId());
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    // ================================================================= dialog

    private static void dialogChecks() {
        World.reset();
        World.Hull hull = new World.Hull("eagle", HullSize.CRUISER, ShieldType.FRONT, 0, "highenergyfocus", 120000f);
        World.Member eagle = World.member("d1", "ISS Eagle", hull);
        World.fleet.add(eagle.proxy);
        World.credits.set(10000000f);
        World.Dialog d = new World.Dialog(World.market("orbitalworks", false));
        InteractionDialogPlugin original = World.proxy(InteractionDialogPlugin.class, (m, a) -> null);
        d.plugin = original;
        WorkshopDialog workshop = new WorkshopDialog(original, new HashMap<String, MemoryAPI>());
        d.plugin = workshop;
        workshop.init(d.proxy);
        check(d.options.containsKey("esro_pick") && d.options.containsKey("esro_leave"), "main screen options");
        check(d.picker != null && d.pickerPool.contains(eagle.proxy), "the ship picker opens with the fleet");
        check(d.textContains("Orbital Works"), "the shipyard is described");

        d.picker.pickedFleetMembers(Arrays.asList(eagle.proxy));
        check(d.options.containsKey("esro_cat_COMBAT") && d.options.containsKey("esro_cat_FLEET"), "ship screen lists both groups");
        check(d.enabled("esro_quality"), "orbital works can improve quality");
        check(!d.enabled("esro_remove"), "nothing to remove on a fresh ship");
        d.exerciseTooltips();

        d.select("esro_cat_COMBAT");
        check(d.options.containsKey("esro_up_DURABILITY") && !d.enabled("esro_up_HANGARS"), "combat upgrades, hangars refused");
        d.exerciseTooltips();
        float before = World.credits.get();
        d.select("esro_up_DURABILITY");
        ShipData data = ShipData.get(eagle.proxy);
        check(data != null && data.getLevel(Upgrade.DURABILITY) == 1, "a level was bought");
        float paid = before - World.credits.get();
        check(paid > 0f && paid < 50000f, "the first level is cheap: " + paid);
        check(eagle.current().hasHullMod(ExtraSystemsHullmod.ID), "the hull mod goes on with the first level");
        check(data.hasStoredQuality(), "starting quality is fixed on the first purchase");
        check(d.options.containsKey("esro_undo"), "the purchase can be undone");

        d.select("esro_undo");
        check(World.credits.get() == before, "undo refunds every credit");
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.DURABILITY) == 0, "undo removes the level");
        check(!eagle.current().hasHullMod(ExtraSystemsHullmod.ID), "undo takes the hull mod off again");

        for (int i = 0; i < 10; i++) d.select("esro_up_WEAPONS");
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.WEAPONS) == 10, "bought up to the maximum");
        check(d.options.containsKey("esro_oc_WEAPONS") && d.storyOptions.contains("esro_oc_WEAPONS"), "past the maximum is an overclock");
        check(!d.enabled("esro_oc_WEAPONS"), "no story points, no overclock");
        World.storyPoints = 2;
        d.select("esro_back_ship");
        d.select("esro_cat_COMBAT");
        check(d.enabled("esro_oc_WEAPONS"), "overclock offered with a story point");
        d.select("esro_oc_WEAPONS");
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.WEAPONS) == 11, "overclocked level bought");
        workshop.optionSelected("stale option", "esro_up_WEAPONS");
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.WEAPONS) == 11, "a normal option cannot buy an overclocked level");
        d.exerciseTooltips();

        d.select("esro_back_ship");
        d.select("esro_remove");
        check(d.options.containsKey("esro_down_WEAPONS"), "removal lists bought upgrades");
        d.exerciseTooltips();
        float beforeRefund = World.credits.get();
        float paidBefore = ShipData.get(eagle.proxy).getPaid(Upgrade.WEAPONS);
        d.select("esro_down_WEAPONS");
        float refunded = World.credits.get() - beforeRefund;
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.WEAPONS) == 10, "removing a level lowers it");
        check(Math.abs(refunded - paidBefore / 11f * 0.5f) <= 1f, "the refund is half of that level's share: " + refunded);
        check(near(ShipData.get(eagle.proxy).getPaid(Upgrade.WEAPONS), paidBefore * 10f / 11f), "paid total drops by one share");
        d.select("esro_undo");
        check(ShipData.get(eagle.proxy).getLevel(Upgrade.WEAPONS) == 11 && World.credits.get() == beforeRefund
                && near(ShipData.get(eagle.proxy).getPaid(Upgrade.WEAPONS), paidBefore), "undoing a removal restores it exactly");
        d.select("esro_down_WEAPONS");

        d.select("esro_back_ship");
        float q = ShipData.get(eagle.proxy).getQuality();
        d.select("esro_quality");
        check(near(ShipData.get(eagle.proxy).getQuality(), q + 0.1f), "orbital works adds 10% quality");
        d.exerciseTooltips();

        World.addSpecial("esro_overload_damper", 1);
        d.select("esro_augments");
        check(d.options.containsKey("esro_install_OVERLOAD_DAMPER"), "owned augments can be installed");
        d.exerciseTooltips();
        d.select("esro_install_OVERLOAD_DAMPER");
        check(ShipData.get(eagle.proxy).hasAugment(Augment.OVERLOAD_DAMPER) && World.special("esro_overload_damper") == 0,
                "installing uses up the item");
        d.select("esro_uninstall_OVERLOAD_DAMPER");
        check(!ShipData.get(eagle.proxy).hasAugment(Augment.OVERLOAD_DAMPER) && World.special("esro_overload_damper") == 1,
                "removal returns the item");
        d.select("esro_undo");
        check(ShipData.get(eagle.proxy).hasAugment(Augment.OVERLOAD_DAMPER) && World.special("esro_overload_damper") == 0,
                "undoing a removal puts the augment back");

        d.select("esro_fabricate");
        d.exerciseTooltips();
        float beforeFab = World.credits.get();
        d.select("esro_fab_TEMPORAL_CAPACITOR");
        check(World.special("esro_temporal_capacitor") == 1 && World.credits.get() < beforeFab, "fabrication sells an augment");
        d.select("esro_undo");
        check(World.special("esro_temporal_capacitor") == 0 && World.credits.get() == beforeFab, "fabrication can be undone");

        d.select("esro_back_augments");
        d.select("esro_back_ship");
        d.select("esro_pay");
        d.select("esro_cat_FLEET");
        check(!d.enabled("esro_up_LOGISTICS"), "materials you do not have cannot pay");
        World.cargoCommodities.put("supplies", 100000f);
        World.cargoCommodities.put("metals", 100000f);
        World.cargoCommodities.put("heavy_machinery", 100000f);
        World.cargoCommodities.put("organics", 100000f);
        d.select("esro_back_ship");
        d.select("esro_cat_FLEET");
        float creditsBefore = World.credits.get();
        d.select("esro_up_LOGISTICS");
        check(World.credits.get() == creditsBefore && World.cargoCommodities.get("supplies") < 100000f, "materials pay instead of credits");
        d.exerciseTooltips();

        d.select("esro_back_ship");
        check(d.options.containsKey("esro_leave"), "the ship screen can go back to the market");
        // The market's own menu is rebuilt by the rules engine, which the mock does not have; the bay logs
        // that failure. Only the hand-back matters here, so keep the expected error out of the output.
        org.apache.log4j.Logger bayLog = org.apache.log4j.Logger.getLogger(WorkshopDialog.class);
        org.apache.log4j.Level level = bayLog.getLevel();
        bayLog.setLevel(org.apache.log4j.Level.OFF);
        workshop.optionSelected("Back to the market", "esro_leave");
        bayLog.setLevel(level);
        check(d.plugin == original, "leaving hands the dialog back to the market");
        check(d.formatProblems.isEmpty(), "every highlighted text formats cleanly: " + d.formatProblems);

        World.Dialog bare = new World.Dialog(World.market("farming", false));
        WorkshopDialog plain = new WorkshopDialog(original, new HashMap<String, MemoryAPI>());
        bare.plugin = plain;
        plain.init(bare.proxy);
        bare.picker.pickedFleetMembers(Arrays.asList(eagle.proxy));
        check(!bare.enabled("esro_quality"), "no shipyard, no quality improvements");
        bare.select("esro_augments");
        check(!bare.options.containsKey("esro_fabricate"), "no shipyard, no fabrication");
        check(bare.formatProblems.isEmpty(), "formats clean without a shipyard: " + bare.formatProblems);

        Shipyard own = Shipyard.at(World.market("orbitalworks", true));
        check(near(own.getDiscount(), 0.25f), "own colony adds to the shipyard discount");
        Shipyard modded = Shipyard.at(World.market("some_modded_heavy_industry", false));
        check(modded.canImproveQuality() && !modded.canFabricate(), "tagged modded heavy industries count as shipyards");
    }

    private static void storageAndMaterialChecks() {
        Map<String, Float> mix = Upgrade.DURABILITY.getMaterials();
        Payment.Bill bill = Payment.bill(Payment.Method.MATERIALS, 100000, mix, false);
        check(bill.getCredits() == 0 && !bill.getCommodities().isEmpty(), "material bills ask for commodities");
        check(bill.getValue() >= 100000 * Pricing.MATERIAL_SHARE && bill.getValue() < 100000 * Pricing.MATERIAL_SHARE + 2000f,
                "materials are worth the material share: " + bill.getValue());
        Payment.Bill credits = Payment.bill(Payment.Method.COMPONENTS, 5000, mix, false);
        check(credits.getCredits() == 5000, "components fall back to credits without Industrial Evolution");

        World.reset();
        World.storageEnabled = true;
        World.cargoCommodities.put("metals", 5f);
        World.storageCommodities.put("metals", 100f);
        CargoAPI cargo = World.cargo();
        CargoAPI storage = World.storageCargo();
        Payment.Bill metals = Payment.bill(Payment.Method.MATERIALS, 3000, singleMix("metals"), false);
        int units = metals.getCommodities().get("metals");
        check(units > 5, "the bill needs more metal than the fleet carries: " + units);
        Payment.Receipt receipt = Payment.pay(metals, cargo, storage);
        check(receipt != null && World.cargoCommodities.get("metals") == 0f
                && World.storageCommodities.get("metals") == 105f - units, "cargo first, then storage");
        Payment.refund(receipt, cargo, storage);
        check(World.cargoCommodities.get("metals") == 5f && World.storageCommodities.get("metals") == 100f, "refund returns to the right place");
        World.storageCommodities.put("metals", 0f);
        check(Payment.pay(metals, cargo, storage) == null && World.cargoCommodities.get("metals") == 5f, "an unaffordable bill takes nothing");
    }

    private static Map<String, Float> singleMix(String id) {
        Map<String, Float> m = new LinkedHashMap<>();
        m.put(id, 1f);
        return m;
    }

    // ================================================================= helpers

    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }

    static float eff(MutableStat stat) {
        return stat.getModifiedValue();
    }

    static float eff(StatBonus bonus) {
        return bonus.computeEffective(100f);
    }

    // ================================================================= mock world

    /** A minimal campaign: one fleet, one cargo hold, one storage, one market. */
    static final class World {
        static final Map<String, Object> persistent = new HashMap<>();
        static final List<FleetMemberAPI> fleet = new ArrayList<>();
        static final MutableValue credits = new MutableValue(0f);
        static final Map<String, Float> cargoCommodities = new HashMap<>();
        static final Map<String, Float> storageCommodities = new HashMap<>();
        static final Map<String, Float> specials = new HashMap<>();
        static final Map<String, Float> commodityPrices = new HashMap<>();
        static int storyPoints;
        static boolean storageEnabled;

        interface Handler {
            Object call(Method m, Object[] a) throws Throwable;
        }

        @SuppressWarnings("unchecked")
        static <T> T proxy(Class<T> type, Handler handler) {
            Class<?>[] interfaces = type == LabelAPI.class ? new Class<?>[]{type, UIComponentAPI.class} : new Class<?>[]{type};
            return (T) Proxy.newProxyInstance(Checks.class.getClassLoader(), interfaces, (p, m, a) -> {
                if (m.getName().equals("toString")) return "mock " + type.getSimpleName();
                if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                if (m.getName().equals("equals")) return p == a[0];
                Object value = handler.call(m, a == null ? new Object[0] : a);
                if (value != null) return value;
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == float.class) return 0f;
                if (r == double.class) return 0d;
                if (r == long.class) return 0L;
                if (r == Color.class) return Color.WHITE;
                if (r.isInstance(p)) return p;
                return null;
            });
        }

        static void install() throws Exception {
            for (List<String> row : csv(Paths.get(CORE, "data/campaign/commodities.csv"))) {
                if (row.size() > 3 && !row.get(1).isEmpty()) {
                    try {
                        commodityPrices.put(row.get(1), Float.parseFloat(row.get(3)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            final JSONObject yards = shipyardConfig();
            Global.setSettings(proxy(SettingsAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getCommoditySpec": {
                        final String id = (String) a[0];
                        if (!commodityPrices.containsKey(id)) return null;
                        return proxy(CommoditySpecAPI.class, (m2, a2) -> {
                            if (m2.getName().equals("getBasePrice")) return commodityPrices.get(id);
                            if (m2.getName().equals("getName")) return id;
                            if (m2.getName().equals("getId")) return id;
                            return null;
                        });
                    }
                    case "getModManager":
                        return proxy(ModManagerAPI.class, (m2, a2) -> false);
                    case "getShipSystemSpec": {
                        final String id = (String) a[0];
                        return proxy(ShipSystemSpecAPI.class, (m2, a2) ->
                                m2.getName().equals("getMaxUses") ? (id.startsWith("unlimited") ? Integer.MAX_VALUE : 3) : null);
                    }
                    case "getJSONObject":
                        return "esroShipyards".equals(a[0]) ? yards : null;
                    case "getBonusXP":
                        return 0f;
                    case "getColor":
                        return Color.WHITE;
                    case "getString":
                        return "";
                    default:
                        return null;
                }
            }));
            Global.setSoundPlayer(proxy(SoundPlayerAPI.class, (m, a) -> null));
            Global.setSector(proxy(SectorAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getPersistentData": return persistent;
                    case "getPlayerFleet": return playerFleet();
                    case "getPlayerStats": return playerStats();
                    case "getPlayerFaction": return proxy(com.fs.starfarer.api.campaign.FactionAPI.class, (m2, a2) -> null);
                    default: return null;
                }
            }));
        }

        static void reset() {
            persistent.clear();
            fleet.clear();
            credits.set(0f);
            cargoCommodities.clear();
            storageCommodities.clear();
            specials.clear();
            storyPoints = 0;
            storageEnabled = false;
        }

        static JSONObject shipyardConfig() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (String line : Files.readAllLines(Paths.get("data/config/settings.json"), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("#")) continue;
                sb.append(line).append('\n');
            }
            return new JSONObject(sb.toString()).getJSONObject("esroShipyards");
        }

        static CampaignFleetAPI playerFleet() {
            return proxy(CampaignFleetAPI.class, (m, a) -> {
                if (m.getName().equals("getCargo")) return cargo();
                if (m.getName().equals("getFleetData")) return fleetData(fleet);
                return null;
            });
        }

        static FleetDataAPI fleetData(final List<FleetMemberAPI> members) {
            return proxy(FleetDataAPI.class, (m, a) ->
                    m.getName().equals("getMembersListCopy") ? new ArrayList<>(members) : null);
        }

        static MutableCharacterStatsAPI playerStats() {
            return proxy(MutableCharacterStatsAPI.class, (m, a) -> {
                if (m.getName().equals("getStoryPoints")) return storyPoints;
                if (m.getName().equals("addStoryPoints") && a.length == 1) storyPoints += (Integer) a[0];
                return null;
            });
        }

        static CargoAPI cargo() {
            return cargo(cargoCommodities, true);
        }

        static CargoAPI storageCargo() {
            return cargo(storageCommodities, false);
        }

        static CargoAPI cargo(final Map<String, Float> commodities, final boolean player) {
            return proxy(CargoAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getCredits": return credits;
                    case "getCommodityQuantity": return commodities.getOrDefault((String) a[0], 0f);
                    case "removeCommodity": commodities.merge((String) a[0], -(Float) a[1], Float::sum); return null;
                    case "addCommodity": commodities.merge((String) a[0], (Float) a[1], Float::sum); return null;
                    case "getMothballedShips": return fleetData(new ArrayList<FleetMemberAPI>());
                    case "getQuantity":
                        if (player && a[0] == CargoItemType.SPECIAL) return specials.getOrDefault(((SpecialItemData) a[1]).getId(), 0f);
                        return 0f;
                    case "removeItems":
                        if (player && a[0] == CargoItemType.SPECIAL) specials.merge(((SpecialItemData) a[1]).getId(), -(Float) a[2], Float::sum);
                        return true;
                    case "addSpecial":
                        if (player) specials.merge(((SpecialItemData) a[0]).getId(), (Float) a[1], Float::sum);
                        return null;
                    default: return null;
                }
            });
        }

        static void addSpecial(String id, int n) {
            specials.merge(id, (float) n, Float::sum);
        }

        static int special(String id) {
            return Math.round(specials.getOrDefault(id, 0f));
        }

        static MarketAPI market(final String industryId, final boolean playerOwned) {
            final Industry industry = proxy(Industry.class, (m, a) -> {
                switch (m.getName()) {
                    case "getId": return industryId;
                    case "isFunctional": return true;
                    case "getCurrentName": return industryId.equals("orbitalworks") ? "Orbital Works" : "Some Works";
                    case "getSpec":
                        return proxy(IndustrySpecAPI.class, (m2, a2) -> m2.getName().equals("getTags")
                                ? new HashSet<>(industryId.contains("heavy") ? Arrays.asList("heavyindustry", "industrial") : Arrays.asList("industrial"))
                                : null);
                    default: return null;
                }
            });
            final SubmarketAPI storage = proxy(SubmarketAPI.class, (m, a) -> {
                if (m.getName().equals("getCargo")) return storageCargo();
                if (m.getName().equals("getPlugin")) return proxy(SubmarketPlugin.class, (m2, a2) ->
                        m2.getName().equals("getOnClickAction") ? OnClickAction.OPEN_SUBMARKET : null);
                return null;
            });
            return proxy(MarketAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getIndustries": return new ArrayList<>(Arrays.asList(industry));
                    case "isPlayerOwned": return playerOwned;
                    case "getSubmarket": return storageEnabled ? storage : null;
                    case "getName": return "Testport";
                    default: return null;
                }
            });
        }

        static WeaponAPI weapon(final WeaponType type, final WeaponSize size, final boolean pd) {
            return proxy(WeaponAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getType": return type;
                    case "getSize": return size;
                    case "hasAIHint": return pd && a[0] == WeaponAPI.AIHints.PD;
                    default: return null;
                }
            });
        }

        static WeaponSlotAPI slot(final WeaponType type) {
            return proxy(WeaponSlotAPI.class, (m, a) -> m.getName().equals("getWeaponType") ? type : null);
        }

        static Member member(String id, String name, Hull hull) {
            return new Member(id, name, hull);
        }

        static TooltipMakerAPI tooltip(final List<String> out) {
            return proxy(TooltipMakerAPI.class, (m, a) -> {
                if (m.getName().startsWith("addPara") || m.getName().equals("addTitle") || m.getName().equals("addSectionHeading")) {
                    out.add(Dialog.render(m, a, null));
                    return proxy(LabelAPI.class, (m2, a2) -> null);
                }
                return null;
            });
        }

        static ShipAPI ship(final MutableShipStatsAPI stats) {
            final Map<String, Object> custom = new HashMap<>();
            return proxy(ShipAPI.class, (m, a) -> {
                switch (m.getName()) {
                    case "getMutableStats": return stats;
                    case "getCustomData": return custom;
                    case "setCustomData": custom.put((String) a[0], a[1]); return null;
                    default: return null;
                }
            });
        }

        static List<List<String>> csv(java.nio.file.Path path) throws Exception {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"') {
                        if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                        else quoted = false;
                    } else field.append(c);
                } else if (c == '"') quoted = true;
                else if (c == ',') { row.add(field.toString()); field.setLength(0); }
                else if (c == '\n') { row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>(); }
                else if (c != '\r') field.append(c);
            }
            if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
            return rows;
        }

        // ------------------------------------------------------------ ships

        static final class Hull {
            final ShipHullSpecAPI proxy;
            final List<WeaponSlotAPI> slots = new ArrayList<>();

            Hull(final String id, final HullSize size, final ShieldType shield, final int bays, final String system, final float value) {
                proxy = World.proxy(ShipHullSpecAPI.class, (m, a) -> {
                    switch (m.getName()) {
                        case "getHullId": return id;
                        case "getHullSize": return size;
                        case "getShieldType": return shield;
                        case "isPhase": return shield == ShieldType.PHASE;
                        case "getFighterBays": return bays;
                        case "getShipSystemId": return system;
                        case "getBaseValue": return value;
                        case "getHullName": return id;
                        case "getHullNameWithDashClass": return id + "-class";
                        case "getAllWeaponSlotsCopy": return new ArrayList<>(slots);
                        default: return null;
                    }
                });
            }
        }

        static final class Variant {
            final Hull hull;
            final Set<String> hullMods = new LinkedHashSet<>();
            final Set<String> permaMods = new LinkedHashSet<>();
            final Set<String> tags = new LinkedHashSet<>();
            final Map<String, Variant> modules = new LinkedHashMap<>();
            VariantSource source = VariantSource.REFIT;
            boolean stock;
            final ShipVariantAPI proxy;

            Variant(Hull hull) {
                this.hull = hull;
                this.proxy = World.proxy(ShipVariantAPI.class, this::call);
                variants.put(proxy, this);
            }

            boolean hasHullMod(String id) {
                return hullMods.contains(id);
            }

            Variant copy() {
                Variant v = new Variant(hull);
                v.hullMods.addAll(hullMods);
                v.permaMods.addAll(permaMods);
                v.tags.addAll(tags);
                for (Map.Entry<String, Variant> e : modules.entrySet()) v.modules.put(e.getKey(), e.getValue().copy());
                v.source = source;
                return v;
            }

            Object call(Method m, Object[] a) {
                switch (m.getName()) {
                    case "getHullSpec": return hull.proxy;
                    case "getHullSize": return hull.proxy.getHullSize();
                    case "hasHullMod": return hullMods.contains((String) a[0]);
                    case "getHullMods": return new ArrayList<>(hullMods);
                    case "getPermaMods": return new LinkedHashSet<>(permaMods);
                    case "addPermaMod": permaMods.add((String) a[0]); hullMods.add((String) a[0]); return null;
                    case "removePermaMod": permaMods.remove((String) a[0]); return null;
                    case "addMod": hullMods.add((String) a[0]); return null;
                    case "removeMod": hullMods.remove((String) a[0]); return null;
                    case "getTags": return new ArrayList<>(tags);
                    case "hasTag": return tags.contains((String) a[0]);
                    case "addTag": tags.add((String) a[0]); return null;
                    case "removeTag": tags.remove((String) a[0]); return null;
                    case "getStationModules": {
                        Map<String, String> result = new LinkedHashMap<>();
                        for (String slot : modules.keySet()) result.put(slot, "module_variant");
                        return result;
                    }
                    case "getModuleVariant": {
                        Variant v = modules.get((String) a[0]);
                        return v == null ? null : v.proxy;
                    }
                    case "setModuleVariant": modules.put((String) a[0], byProxy((ShipVariantAPI) a[1])); return null;
                    case "isStockVariant": return stock;
                    case "isGoalVariant": return false;
                    case "getSource": return source;
                    case "setSource": source = (VariantSource) a[0]; return null;
                    case "clone": return copy().proxy;
                    default: return null;
                }
            }
        }

        static final Map<ShipVariantAPI, Variant> variants = new HashMap<>();

        static Variant byProxy(ShipVariantAPI proxy) {
            Variant v = variants.get(proxy);
            if (v == null) throw new IllegalStateException("unknown variant");
            return v;
        }

        static final class Member {
            final String id;
            final String name;
            final Hull hull;
            Variant variant;
            ShipVariantAPI currentProxy;
            int updates;
            final FleetMemberAPI proxy;
            final Stats stats;

            Member(String id, String name, Hull hull) {
                this.id = id;
                this.name = name;
                this.hull = hull;
                this.variant = register(new Variant(hull));
                this.currentProxy = variant.proxy;
                this.proxy = World.proxy(FleetMemberAPI.class, this::call);
                this.stats = new Stats(hull, proxy);
            }

            Variant current() {
                return byProxy(currentProxy);
            }

            Object call(Method m, Object[] a) {
                switch (m.getName()) {
                    case "getId": return id;
                    case "getShipName": return name;
                    case "getHullSpec": return hull.proxy;
                    case "getHullId": return hull.proxy.getHullId();
                    case "getVariant": return currentProxy;
                    case "setVariant": currentProxy = (ShipVariantAPI) a[0]; register(byProxyOrNew((ShipVariantAPI) a[0])); return null;
                    case "getStats": return stats.proxy;
                    case "updateStats": updates++; return null;
                    case "isFighterWing": return false;
                    default: return null;
                }
            }
        }

        static Variant register(Variant v) {
            variants.put(v.proxy, v);
            for (Variant m : v.modules.values()) register(m);
            return v;
        }

        static Variant byProxyOrNew(ShipVariantAPI proxy) {
            Variant v = variants.get(proxy);
            if (v != null) return v;
            throw new IllegalStateException("variant was not created by the mock");
        }

        /** Ship stats backed by the real MutableStat and StatBonus classes, base value 100. */
        static final class Stats {
            final Map<String, Object> stats = new HashMap<>();
            final Map<String, MutableStat> dynamicStats = new HashMap<>();
            final Map<String, StatBonus> dynamicMods = new HashMap<>();
            final Variant variant;
            final MutableShipStatsAPI proxy;

            Stats(Hull hull, final FleetMemberAPI member) {
                variant = register(new Variant(hull));
                proxy = World.proxy(MutableShipStatsAPI.class, (m, a) -> {
                    if (m.getName().equals("getFleetMember")) return member;
                    if (m.getName().equals("getVariant")) return variant.proxy;
                    if (m.getName().equals("getDynamic")) {
                        return World.proxy(DynamicStatsAPI.class, (m2, a2) -> {
                            if (m2.getName().equals("getStat")) return dynamicStats.computeIfAbsent((String) a2[0], k -> new MutableStat(100f));
                            if (m2.getName().equals("getMod")) return dynamicMods.computeIfAbsent((String) a2[0], k -> new StatBonus());
                            return null;
                        });
                    }
                    if (m.getName().equals("getNumFighterBays")) {
                        return stats.computeIfAbsent("getNumFighterBays", k -> new MutableStat(hull.proxy.getFighterBays()));
                    }
                    if (a.length == 0 && m.getReturnType() == MutableStat.class) {
                        return stats.computeIfAbsent(m.getName(), k -> new MutableStat(100f));
                    }
                    if (a.length == 0 && m.getReturnType() == StatBonus.class) {
                        return stats.computeIfAbsent(m.getName(), k -> new StatBonus());
                    }
                    return null;
                });
            }

            MutableStat stat(String getter) {
                return (MutableStat) stats.computeIfAbsent(getter, k -> new MutableStat(100f));
            }

            StatBonus bonus(String getter) {
                return (StatBonus) stats.computeIfAbsent(getter, k -> new StatBonus());
            }

            MutableStat dynamic(String key) {
                return dynamicStats.computeIfAbsent(key, k -> new MutableStat(100f));
            }
        }

        // ------------------------------------------------------------ dialog

        static final class Dialog {
            final Map<Object, String> options = new LinkedHashMap<>();
            final Map<Object, Boolean> enabledFlags = new HashMap<>();
            final Map<Object, OptionTooltipCreator> appenders = new HashMap<>();
            final Set<Object> storyOptions = new HashSet<>();
            final List<String> text = new ArrayList<>();
            final List<String> formatProblems = new ArrayList<>();
            FleetMemberPickerListener picker;
            List<FleetMemberAPI> pickerPool;
            InteractionDialogPlugin plugin;
            final InteractionDialogAPI proxy;

            Dialog(final MarketAPI market) {
                final SectorEntityToken target = World.proxy(SectorEntityToken.class, (m, a) ->
                        m.getName().equals("getMarket") ? market : null);
                final TextPanelAPI textPanel = World.proxy(TextPanelAPI.class, (m, a) -> {
                    if (m.getName().equals("clear")) { text.clear(); return null; }
                    if (m.getName().equals("addPara") || m.getName().equals("addParagraph")) {
                        text.add(render(m, a, formatProblems));
                        return World.proxy(LabelAPI.class, (m2, a2) -> null);
                    }
                    if (m.getName().equals("beginTooltip")) return tooltipFor(text, formatProblems);
                    return null;
                });
                final OptionPanelAPI optionPanel = World.proxy(OptionPanelAPI.class, (m, a) -> {
                    switch (m.getName()) {
                        case "clearOptions":
                            options.clear(); enabledFlags.clear(); appenders.clear(); storyOptions.clear();
                            return null;
                        case "addOption":
                            if (options.containsKey(a[1])) throw new IllegalStateException("duplicate option " + a[1]);
                            options.put(a[1], (String) a[0]);
                            return null;
                        case "setEnabled": enabledFlags.put(a[0], (Boolean) a[1]); return null;
                        case "addOptionTooltipAppender":
                            if (!options.containsKey(a[0])) throw new IllegalStateException("tooltip for missing option " + a[0]);
                            appenders.put(a[0], (OptionTooltipCreator) a[1]);
                            return null;
                        case "setTooltip":
                        case "setShortcut":
                        case "addOptionConfirmation":
                        case "setStoryOptionParams":
                            if (!options.containsKey(a[0])) throw new IllegalStateException(m.getName() + " for missing option " + a[0]);
                            return null;
                        case "hasOption": return options.containsKey(a[0]);
                        default: return null;
                    }
                });
                proxy = World.proxy(InteractionDialogAPI.class, (m, a) -> {
                    switch (m.getName()) {
                        case "getTextPanel": return textPanel;
                        case "getOptionPanel": return optionPanel;
                        case "getVisualPanel": return World.proxy(VisualPanelAPI.class, (m2, a2) -> null);
                        case "getInteractionTarget": return target;
                        case "getPlugin": return plugin;
                        case "setPlugin": plugin = (InteractionDialogPlugin) a[0]; return null;
                        case "makeStoryOption": storyOptions.add(a[0]); return null;
                        case "showFleetMemberPickerDialog":
                            pickerPool = new ArrayList<>((List<FleetMemberAPI>) a[8]);
                            picker = (FleetMemberPickerListener) a[9];
                            return null;
                        default: return null;
                    }
                });
                plugin = null;
            }

            void select(String option) {
                if (!options.containsKey(option)) throw new AssertionError("no option " + option + " in " + options.keySet());
                if (!enabled(option)) throw new AssertionError("option " + option + " is disabled");
                InteractionDialogPlugin current = plugin;
                current.optionSelected(options.get(option), option);
                for (String line : text) {
                    if (line.contains("fault")) throw new AssertionError("the bay reported a fault after " + option + ": " + line);
                }
            }

            boolean enabled(Object option) {
                return enabledFlags.getOrDefault(option, true);
            }

            boolean textContains(String s) {
                for (String line : text) if (line.contains(s)) return true;
                return false;
            }

            void exerciseTooltips() {
                for (OptionTooltipCreator creator : appenders.values()) {
                    creator.createTooltip(tooltipFor(new ArrayList<String>(), formatProblems), false);
                }
            }

            static TooltipMakerAPI tooltipFor(final List<String> out, final List<String> problems) {
                return World.proxy(TooltipMakerAPI.class, (m, a) -> {
                    if (m.getName().startsWith("addPara") || m.getName().equals("addTitle")) {
                        out.add(render(m, a, problems));
                        return World.proxy(LabelAPI.class, (m2, a2) -> null);
                    }
                    return null;
                });
            }

            /** Mirrors the game: the overloads with highlights run String.format, the plain ones do not. */
            static String render(Method m, Object[] a, List<String> problems) {
                String format = (String) a[0];
                String[] highlights = null;
                for (Object arg : a) if (arg instanceof String[]) highlights = (String[]) arg;
                if (highlights == null) return format;
                try {
                    return String.format(format, (Object[]) highlights);
                } catch (RuntimeException e) {
                    String problem = "'" + format + "' -> " + e;
                    if (problems != null) problems.add(problem);
                    else throw new AssertionError(problem);
                    return format;
                }
            }
        }
    }
}
