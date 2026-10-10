package esroddisz.dialog;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption.BaseOptionStoryPointActionDelegate;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption.StoryOptionParams;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import esroddisz.Pricing;
import esroddisz.Quality;
import esroddisz.Settings;
import esroddisz.ShipData;
import esroddisz.augments.Augment;
import esroddisz.campaign.HullmodSync;
import esroddisz.campaign.Payment;
import esroddisz.campaign.Payment.Bill;
import esroddisz.campaign.Payment.Method;
import esroddisz.campaign.Payment.Receipt;
import esroddisz.campaign.Shipyard;
import esroddisz.hullmods.ExtraSystemsHullmod;
import esroddisz.upgrades.Effect;
import esroddisz.upgrades.Upgrade;
import esroddisz.upgrades.Upgrade.Category;

/**
 * The Extra Systems bay. Opened from a market's main menu; takes over the dialog until the player goes
 * back, then hands it to the market again.
 *
 * Every purchase on this visit can be undone in full, last first, so a misclick never costs anything.
 */
public class WorkshopDialog implements InteractionDialogPlugin {

    static final String PICK = "esro_pick";
    static final String LEAVE = "esro_leave";
    static final String BACK_TO_SHIP = "esro_back_ship";
    static final String BACK_TO_AUGMENTS = "esro_back_augments";
    static final String OTHER_SHIP = "esro_other_ship";
    static final String QUALITY = "esro_quality";
    static final String AUGMENTS = "esro_augments";
    static final String REMOVE = "esro_remove";
    static final String PAY = "esro_pay";
    static final String UNDO = "esro_undo";
    static final String FABRICATE = "esro_fabricate";
    static final String CATEGORY = "esro_cat_";
    static final String UP = "esro_up_";
    static final String OVERCLOCK = "esro_oc_";
    static final String DOWN = "esro_down_";
    static final String INSTALL = "esro_install_";
    static final String UNINSTALL = "esro_uninstall_";
    static final String FAB = "esro_fab_";

    enum Screen { MAIN, SHIP, CATEGORY, REMOVE, AUGMENTS, FABRICATE }

    private final InteractionDialogPlugin original;
    private final Map<String, MemoryAPI> memoryMap;
    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private MarketAPI market;
    private Shipyard yard = Shipyard.NONE;

    private Screen screen = Screen.MAIN;
    private Category category = Category.COMBAT;
    private FleetMemberAPI member;
    private Method method = Method.CREDITS;
    private final Deque<Undo> undo = new ArrayDeque<>();

    public WorkshopDialog(InteractionDialogPlugin original, Map<String, MemoryAPI> memoryMap) {
        this.original = original;
        this.memoryMap = memoryMap;
    }

    // ================================================================= plugin

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        this.market = dialog.getInteractionTarget() == null ? null : dialog.getInteractionTarget().getMarket();
        this.yard = Shipyard.at(market);
        showMain(true);
    }

    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof String)) return;
        String id = (String) optionData;
        try {
            select(id);
        } catch (RuntimeException e) {
            Global.getLogger(WorkshopDialog.class).error("Extra Systems bay failed on option " + id, e);
            text.addPara("The engineers report a fault in the bay's systems. Nothing was charged for that step.",
                    Misc.getNegativeHighlightColor());
        }
    }

    private void select(String id) {
        if (id.equals(LEAVE)) {
            leave();
        } else if (id.equals(PICK) || id.equals(OTHER_SHIP)) {
            showMain(true);
        } else if (id.equals(BACK_TO_SHIP)) {
            showShip();
        } else if (id.equals(BACK_TO_AUGMENTS)) {
            showAugments();
        } else if (id.startsWith(CATEGORY)) {
            category = Category.valueOf(id.substring(CATEGORY.length()));
            showCategory();
        } else if (id.equals(PAY)) {
            method = method.next();
            refresh();
        } else if (id.equals(UNDO)) {
            undoLast();
        } else if (id.equals(QUALITY)) {
            improveQuality();
        } else if (id.equals(AUGMENTS)) {
            showAugments();
        } else if (id.equals(REMOVE)) {
            showRemoval();
        } else if (id.equals(FABRICATE)) {
            showFabrication();
        } else if (id.startsWith(UP)) {
            buyLevel(Upgrade.valueOf(id.substring(UP.length())), false);
        } else if (id.startsWith(OVERCLOCK)) {
            buyLevel(Upgrade.valueOf(id.substring(OVERCLOCK.length())), true);
        } else if (id.startsWith(DOWN)) {
            removeLevel(Upgrade.valueOf(id.substring(DOWN.length())));
        } else if (id.startsWith(INSTALL)) {
            install(Augment.valueOf(id.substring(INSTALL.length())));
        } else if (id.startsWith(UNINSTALL)) {
            uninstall(Augment.valueOf(id.substring(UNINSTALL.length())));
        } else if (id.startsWith(FAB)) {
            fabricate(Augment.valueOf(id.substring(FAB.length())));
        }
    }

    public void optionMousedOver(String optionText, Object optionData) {
    }

    public void advance(float amount) {
    }

    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    public Object getContext() {
        return null;
    }

    public Map<String, MemoryAPI> getMemoryMap() {
        return memoryMap;
    }

    // ================================================================= screens

    private void showMain(boolean openPicker) {
        screen = Screen.MAIN;
        member = null;
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        text.clear();
        text.addPara("The Extra Systems engineers can take any of your ships past its factory limits - "
                + "for a price. Pick a ship from your fleet or from storage here.");
        describeShipyard();
        addCreditsLine();

        options.clearOptions();
        options.addOption("Choose a ship", PICK);
        addUndoOption();
        options.addOption("Back to the market", LEAVE);
        options.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

        if (openPicker) openPicker();
    }

    private void describeShipyard() {
        Color h = Misc.getHighlightColor();
        if (yard.exists()) {
            List<String> perks = new ArrayList<>();
            if (yard.canImproveQuality()) perks.add("hull quality improvements of +" + Quality.percent(yard.getQualityStep()));
            if (yard.canFabricate()) perks.add("augment fabrication");
            String perkText = perks.isEmpty() ? "" : " It also offers " + joined(perks, " and ") + ".";
            // The highlight overloads run String.format, so a literal percent sign must be doubled.
            text.addPara(esc("The local " + yard.getIndustryName()) + " cuts every price by %s." + esc(perkText),
                    h, Quality.percent(yard.getDiscount()));
        } else {
            text.addPara("There is no working Heavy Industry or Orbital Works here, so hull quality cannot be "
                    + "improved and augments cannot be fabricated. Upgrades and installing augments still work.",
                    Misc.getGrayColor());
            if (yard.isOwnColony()) {
                text.addPara("Your own colony gives you %s off.", h, Quality.percent(yard.getDiscount()));
            }
        }
    }

    private void openPicker() {
        List<FleetMemberAPI> pool = new ArrayList<>();
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (!m.isFighterWing()) pool.add(m);
        }
        CargoAPI storage = storage();
        if (storage != null && storage.getMothballedShips() != null) {
            for (FleetMemberAPI m : storage.getMothballedShips().getMembersListCopy()) {
                if (!m.isFighterWing()) pool.add(m);
            }
        }
        if (pool.isEmpty()) {
            text.addPara("You have no ships here to work on.");
            return;
        }
        int cols = 7;
        int rows = Math.max(1, Math.min(4, (pool.size() + cols - 1) / cols));
        dialog.showFleetMemberPickerDialog("Choose a ship for the Extra Systems bay", "Choose", "Cancel",
                rows, cols, 58f, true, false, pool, new FleetMemberPickerListener() {
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members == null || members.isEmpty()) return;
                        member = members.get(0);
                        showShip();
                    }

                    public void cancelledFleetMemberPicking() {
                    }
                });
    }

    private void showShip() {
        screen = Screen.SHIP;
        dialog.getVisualPanel().showFleetMemberInfo(member);
        text.clear();
        addShipHeader();
        ShipData data = ShipData.get(member);
        TooltipMakerAPI summary = text.beginTooltip();
        if (data == null) {
            float q = ShipData.startingQuality(member.getId());
            summary.addPara("Hull quality: %s. Every upgrade on this ship will work at %s strength.", 0f,
                    new Color[]{Quality.color(q), Misc.getHighlightColor()}, Quality.describe(q), Quality.percent(q));
            summary.addPara("This ship has not been worked on yet.", Misc.getGrayColor(), 4f);
        } else {
            ExtraSystemsHullmod.addSummary(summary, data, member.getHullSpec().getHullSize(), 0f);
            if (data.getInvested() > 0f) {
                summary.addPara("Invested so far: %s.", 4f, Misc.getHighlightColor(),
                        Misc.getDGSCredits(data.getInvested()));
            }
        }
        text.addTooltip();
        addPaymentLine();

        options.clearOptions();
        for (Category c : Category.values()) {
            options.addOption(c.getTitle(), CATEGORY + c.name());
        }
        addQualityOption();
        int slots = Settings.augmentSlots(member.getHullSpec().getHullSize());
        int used = data == null ? 0 : data.getAugments().size();
        options.addOption("Augments (" + used + "/" + slots + " slots)", AUGMENTS);
        options.addOption("Remove upgrades", REMOVE);
        if (data == null || data.totalLevels() == 0) {
            options.setEnabled(REMOVE, false);
            options.setTooltip(REMOVE, "This ship has no upgrade levels to remove.");
        }
        addPayOption();
        addUndoOption();
        options.addOption("Choose another ship", OTHER_SHIP);
        options.addOption("Back to the market", LEAVE);
        options.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void showCategory() {
        screen = Screen.CATEGORY;
        text.clear();
        addShipHeader();
        ShipData data = ShipData.get(member);
        text.addPara(category.getTitle() + ". Hover over an upgrade to see what the next level does and what it costs.");
        TooltipMakerAPI list = text.beginTooltip();
        boolean any = false;
        for (Upgrade u : Upgrade.values()) {
            if (u.getCategory() != category) continue;
            int level = data == null ? 0 : data.getLevel(u);
            if (level <= 0) continue;
            addEffectList(list, u, level, level, any ? 6f : 0f);
            any = true;
        }
        if (!any) list.addPara("None of these upgrades are installed yet.", Misc.getGrayColor(), 0f);
        text.addTooltip();
        addPaymentLine();

        options.clearOptions();
        for (Upgrade u : Upgrade.values()) {
            if (u.getCategory() == category) addUpgradeOption(u, data);
        }
        addPayOption();
        addUndoOption();
        options.addOption("Back", BACK_TO_SHIP);
        options.setShortcut(BACK_TO_SHIP, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void showRemoval() {
        screen = Screen.REMOVE;
        text.clear();
        addShipHeader();
        int percent = Math.round(Settings.refundFraction() * 100f);
        text.addPara("Removing a level refunds %s of what you paid for it, in credits. "
                + "Story points spent on overclocked levels are not returned.", Misc.getHighlightColor(), percent + "%");

        options.clearOptions();
        ShipData data = ShipData.get(member);
        for (Upgrade u : Upgrade.values()) {
            int level = data == null ? 0 : data.getLevel(u);
            if (level <= 0) continue;
            String option = DOWN + u.name();
            options.addOption(u.getName() + " " + level + " to " + (level - 1) + " (refund "
                    + Misc.getDGSCredits(refundFor(u, level)) + ")", option);
            final Upgrade upgrade = u;
            final int current = level;
            options.addOptionTooltipAppender(option, (tooltip, hadOtherText) ->
                    addEffectList(tooltip, upgrade, current, current - 1, hadOtherText ? 10f : 0f));
        }
        addUndoOption();
        options.addOption("Back", BACK_TO_SHIP);
        options.setShortcut(BACK_TO_SHIP, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void showAugments() {
        screen = Screen.AUGMENTS;
        text.clear();
        addShipHeader();
        ShipData data = ShipData.get(member);
        List<Augment> installed = data == null ? new ArrayList<Augment>() : data.getAugments();
        int slots = Settings.augmentSlots(member.getHullSpec().getHullSize());
        text.addPara("Augment slots: %s used of %s. Augments are rare devices found in salvage"
                        + (yard.canFabricate() ? " or fabricated here" : "")
                        + ". Removing one " + (Settings.removalDestroysAugment() ? "destroys it." : "returns it to your cargo."),
                Misc.getHighlightColor(), String.valueOf(installed.size()), String.valueOf(slots));
        if (!installed.isEmpty()) {
            TooltipMakerAPI list = text.beginTooltip();
            float pad = 0f;
            for (Augment a : installed) {
                addAugmentDescription(list, a, pad);
                pad = 8f;
            }
            text.addTooltip();
        }

        options.clearOptions();
        ShipHullSpecAPI hull = member.getHullSpec();
        MutableShipStatsAPI stats = member.getStats();
        for (Augment a : Augment.values()) {
            final Augment augment = a;
            if (installed.contains(a)) {
                String option = UNINSTALL + a.name();
                options.addOption("Remove " + a.getName(), option);
                options.addOptionTooltipAppender(option, (tooltip, had) -> addAugmentDescription(tooltip, augment, had ? 10f : 0f));
                continue;
            }
            int owned = augmentsOwned(a);
            if (owned <= 0) continue;
            String option = INSTALL + a.name();
            options.addOption("Install " + a.getName() + " (" + owned + " owned)", option);
            options.addOptionTooltipAppender(option, (tooltip, had) -> addAugmentDescription(tooltip, augment, had ? 10f : 0f));
            if (!a.canInstallOn(hull, stats)) {
                options.setEnabled(option, false);
                options.setTooltip(option, a.getRequirementText());
            } else if (installed.size() >= slots) {
                options.setEnabled(option, false);
                options.setTooltip(option, "Every augment slot on this ship is in use.");
            }
        }
        if (yard.canFabricate()) options.addOption("Fabricate augments", FABRICATE);
        addUndoOption();
        options.addOption("Back", BACK_TO_SHIP);
        options.setShortcut(BACK_TO_SHIP, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void showFabrication() {
        screen = Screen.FABRICATE;
        text.clear();
        text.addPara("The " + yard.getIndustryName() + " can fabricate augments to order. "
                + "Finished augments go into your cargo.");
        addPaymentLine();

        options.clearOptions();
        for (Augment a : Augment.values()) {
            final Augment augment = a;
            String option = FAB + a.name();
            final Bill bill = Payment.bill(method, fabricationPrice(a), a.getMaterials(), true);
            options.addOption("Fabricate " + a.getName() + " (" + billSummary(bill) + ")", option);
            options.addOptionTooltipAppender(option, (tooltip, had) -> {
                addAugmentDescription(tooltip, augment, had ? 10f : 0f);
                addBill(tooltip, bill, 10f);
            });
            if (!Payment.canAfford(bill, cargo(), storage())) options.setEnabled(option, false);
        }
        addPayOption();
        addUndoOption();
        options.addOption("Back", BACK_TO_AUGMENTS);
        options.setShortcut(BACK_TO_AUGMENTS, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void refresh() {
        switch (screen) {
            case MAIN: showMain(false); break;
            case SHIP: showShip(); break;
            case CATEGORY: showCategory(); break;
            case REMOVE: showRemoval(); break;
            case AUGMENTS: showAugments(); break;
            case FABRICATE: showFabrication(); break;
        }
    }

    private void refresh(String message, Color color) {
        refresh();
        text.addPara(message, color);
    }

    // ================================================================= options

    private void addUpgradeOption(Upgrade u, ShipData data) {
        String option = UP + u.name();
        int level = data == null ? 0 : data.getLevel(u);
        int max = Settings.maxLevel();
        int cap = max + Settings.overclockLevels();
        final Upgrade upgrade = u;
        final int current = level;

        if (!u.appliesTo(member.getHullSpec(), member.getStats())) {
            options.addOption(u.getName() + " (not available)", option);
            options.setEnabled(option, false);
            options.setTooltip(option, u.getNotApplicableReason());
            return;
        }
        if (level >= cap) {
            options.addOption(u.getName() + " " + level + "/" + max + " (fully upgraded)", option);
            options.setEnabled(option, false);
            options.addOptionTooltipAppender(option, (tooltip, had) -> addEffectList(tooltip, upgrade, current, current, had ? 10f : 0f));
            return;
        }

        boolean overclock = level >= max;
        if (overclock) option = OVERCLOCK + u.name();
        final Bill bill = Payment.bill(method, upgradePrice(u, level + 1), u.getMaterials(), false);
        String label = overclock
                ? "Overclock " + u.getName() + " to " + (level + 1) + " (" + billSummary(bill) + " + 1 story point)"
                : u.getName() + " " + level + "/" + max + " to " + (level + 1) + " (" + billSummary(bill) + ")";
        options.addOption(label, option);
        final boolean isOverclock = overclock;
        options.addOptionTooltipAppender(option, (tooltip, had) -> {
            tooltip.addPara(upgrade.getDescription(), had ? 10f : 0f);
            addEffectList(tooltip, upgrade, current, current + 1, 10f);
            addBill(tooltip, bill, 10f);
            if (isOverclock) {
                tooltip.addPara("Levels past %s are overclocked and each one also costs a story point.", 10f,
                        Misc.getStoryOptionColor(), String.valueOf(Settings.maxLevel()));
                tooltip.addStoryPointUseInfo(10f, 1, 0f, true);
            }
        });
        boolean affordable = Payment.canAfford(bill, cargo(), storage());
        if (overclock) {
            makeStoryOption(option);
            if (Global.getSector().getPlayerStats().getStoryPoints() < 1) affordable = false;
        }
        if (!affordable) options.setEnabled(option, false);
    }

    /** The vanilla story point option: gold text and a confirmation that spends the point. */
    private void makeStoryOption(Object option) {
        StoryOptionParams params = new StoryOptionParams(option, 1, "fullyIntegrateAICore",
                Sounds.STORY_POINT_SPEND_TECHNOLOGY, null);
        BaseOptionStoryPointActionDelegate delegate = new BaseOptionStoryPointActionDelegate(dialog, params);
        dialog.makeStoryOption(option, 1, 0f, Sounds.STORY_POINT_SPEND_TECHNOLOGY);
        options.addOptionConfirmation(option, delegate);
        options.setStoryOptionParams(option, params, delegate);
    }

    private void addQualityOption() {
        ShipData data = ShipData.get(member);
        float q = data == null ? ShipData.startingQuality(member.getId()) : data.getQuality();
        float cap = Settings.qualityCap();
        if (!yard.canImproveQuality()) {
            options.addOption("Improve hull quality", QUALITY);
            options.setEnabled(QUALITY, false);
            options.setTooltip(QUALITY, "Only a market with a working Heavy Industry or Orbital Works can improve hull quality.");
            return;
        }
        if (q >= cap - 0.0001f) {
            options.addOption("Improve hull quality (at the maximum of " + Quality.percent(cap) + ")", QUALITY);
            options.setEnabled(QUALITY, false);
            return;
        }
        float next = Math.min(cap, q + yard.getQualityStep());
        final Bill bill = Payment.bill(method, qualityPrice(q, next), materialsForQuality(), true);
        options.addOption("Improve hull quality to " + Quality.percent(next) + " (" + billSummary(bill) + ")", QUALITY);
        final float from = q;
        final float to = next;
        options.addOptionTooltipAppender(QUALITY, (tooltip, had) -> {
            tooltip.addPara("Hull quality multiplies the effect of every upgrade on this ship. "
                    + "From %s to %s.", had ? 10f : 0f, new Color[]{Quality.color(from), Quality.color(to)},
                    Quality.describe(from), Quality.describe(to));
            tooltip.addPara("Each improvement costs more than the last. The highest quality is %s.", 4f,
                    Misc.getHighlightColor(), Quality.percent(Settings.qualityCap()));
            addBill(tooltip, bill, 10f);
        });
        if (!Payment.canAfford(bill, cargo(), storage())) options.setEnabled(QUALITY, false);
    }

    private void addPayOption() {
        Method next = method.next();
        if (next == method) return;
        options.addOption("Pay with: " + method.getLabel() + " (switch to " + next.getLabel().toLowerCase() + ")", PAY);
        options.setTooltip(PAY, "Credits are simplest. Materials cost less: the bay takes metals, machinery and "
                + "the like worth " + Math.round(Pricing.MATERIAL_SHARE * 100f) + "% of the price, from your cargo "
                + "and then from your storage here."
                + (Method.COMPONENTS.isAvailable() ? " Industrial Evolution components are worth even more to "
                + "the engineers: ship components pay for upgrades, relic components for hull quality and augments." : ""));
    }

    private void addUndoOption() {
        if (undo.isEmpty()) return;
        options.addOption("Undo: " + undo.peek().label, UNDO);
        options.setTooltip(UNDO, "Reverses the last change made on this visit and returns everything it cost.");
    }

    // ================================================================= actions

    private void buyLevel(Upgrade u, boolean overclock) {
        ShipData data = ShipData.getOrCreate(member);
        data.fixQuality();
        int level = data.getLevel(u);
        int max = Settings.maxLevel();
        if (level >= max + Settings.overclockLevels() || overclock != (level >= max)) {
            // The story point was already spent by the confirmation; the level is no longer on offer.
            if (overclock) Global.getSector().getPlayerStats().addStoryPoints(1);
            refresh();
            return;
        }
        Bill bill = Payment.bill(method, upgradePrice(u, level + 1), u.getMaterials(), false);
        Receipt receipt = Payment.pay(bill, cargo(), storage());
        if (receipt == null) {
            if (overclock) Global.getSector().getPlayerStats().addStoryPoints(1);
            refresh("You cannot afford that.", Misc.getNegativeHighlightColor());
            return;
        }
        final FleetMemberAPI ship = member;
        final int previous = level;
        data.setLevel(u, level + 1);
        data.setPaid(u, data.getPaid(u) + receipt.getValue());
        data.addInvested(receipt.getValue());
        shipChanged(ship);
        push(u.getName() + " level " + (level + 1) + " on " + ship.getShipName(), ship, () -> {
            ShipData d = ShipData.getOrCreate(ship);
            d.setLevel(u, previous);
            d.setPaid(u, d.getPaid(u) - receipt.getValue());
            d.addInvested(-receipt.getValue());
            Payment.refund(receipt, cargo(), storage());
            if (overclock) Global.getSector().getPlayerStats().addStoryPoints(1);
            shipChanged(ship);
        });
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        refresh(u.getName() + " is now level " + (level + 1) + ".", Misc.getPositiveHighlightColor());
    }

    private void removeLevel(Upgrade u) {
        ShipData data = ShipData.get(member);
        int level = data == null ? 0 : data.getLevel(u);
        if (level <= 0) {
            refresh();
            return;
        }
        // Each level counts as an equal share of everything paid for the upgrade, however it was paid.
        float share = data.getPaid(u) / level;
        int refund = refundFor(u, level);
        cargo().getCredits().add(refund);
        data.setLevel(u, level - 1);
        data.setPaid(u, data.getPaid(u) - share);
        data.addInvested(-share);
        final FleetMemberAPI ship = member;
        shipChanged(ship);
        push("removing " + u.getName() + " level " + level + " on " + ship.getShipName(), ship, () -> {
            ShipData d = ShipData.getOrCreate(ship);
            d.setLevel(u, level);
            d.setPaid(u, d.getPaid(u) + share);
            d.addInvested(share);
            cargo().getCredits().subtract(refund);
            shipChanged(ship);
        });
        refresh(u.getName() + " is now level " + (level - 1) + ". Refunded " + Misc.getDGSCredits(refund) + ".",
                Misc.getHighlightColor());
    }

    private void improveQuality() {
        if (!yard.canImproveQuality()) return;
        ShipData data = ShipData.getOrCreate(member);
        data.fixQuality();
        float q = data.getQuality();
        float cap = Settings.qualityCap();
        if (q >= cap - 0.0001f) {
            refresh();
            return;
        }
        float next = Math.min(cap, q + yard.getQualityStep());
        Bill bill = Payment.bill(method, qualityPrice(q, next), materialsForQuality(), true);
        Receipt receipt = Payment.pay(bill, cargo(), storage());
        if (receipt == null) {
            refresh("You cannot afford that.", Misc.getNegativeHighlightColor());
            return;
        }
        data.setQuality(next);
        data.addInvested(receipt.getValue());
        final FleetMemberAPI ship = member;
        shipChanged(ship);
        push("hull quality " + Quality.percent(next) + " on " + ship.getShipName(), ship, () -> {
            ShipData d = ShipData.getOrCreate(ship);
            d.setQuality(q);
            d.addInvested(-receipt.getValue());
            Payment.refund(receipt, cargo(), storage());
            shipChanged(ship);
        });
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        refresh("Hull quality is now " + Quality.describe(next) + ".", Misc.getPositiveHighlightColor());
    }

    private void install(Augment a) {
        ShipData data = ShipData.getOrCreate(member);
        int slots = Settings.augmentSlots(member.getHullSpec().getHullSize());
        if (data.hasAugment(a) || data.getAugments().size() >= slots
                || !a.canInstallOn(member.getHullSpec(), member.getStats())) {
            refresh();
            return;
        }
        CargoAPI from = takeAugment(a);
        if (from == null) {
            refresh("You no longer have a " + a.getName() + ".", Misc.getNegativeHighlightColor());
            return;
        }
        data.fixQuality();
        data.addAugment(a);
        final FleetMemberAPI ship = member;
        shipChanged(ship);
        push("installing " + a.getName() + " on " + ship.getShipName(), ship, () -> {
            ShipData.getOrCreate(ship).removeAugment(a);
            from.addSpecial(new SpecialItemData(a.getId(), null), 1);
            shipChanged(ship);
        });
        Global.getSoundPlayer().playUISound("ui_industry_install_any_item", 1f, 1f);
        refresh(a.getName() + " installed.", Misc.getPositiveHighlightColor());
    }

    private void uninstall(Augment a) {
        ShipData data = ShipData.get(member);
        if (data == null || !data.hasAugment(a)) {
            refresh();
            return;
        }
        boolean destroy = Settings.removalDestroysAugment();
        data.removeAugment(a);
        if (!destroy) cargo().addSpecial(new SpecialItemData(a.getId(), null), 1);
        final FleetMemberAPI ship = member;
        shipChanged(ship);
        push("removing " + a.getName() + " from " + ship.getShipName(), ship, () -> {
            if (!destroy) {
                CargoAPI cargo = cargo();
                SpecialItemData item = new SpecialItemData(a.getId(), null);
                if (cargo.getQuantity(CargoItemType.SPECIAL, item) < 1f) return;
                cargo.removeItems(CargoItemType.SPECIAL, item, 1);
            }
            ShipData.getOrCreate(ship).addAugment(a);
            shipChanged(ship);
        });
        refresh(destroy ? a.getName() + " removed. It did not survive the extraction."
                : a.getName() + " removed and returned to your cargo.", Misc.getHighlightColor());
    }

    private void fabricate(Augment a) {
        if (!yard.canFabricate()) return;
        Bill bill = Payment.bill(method, fabricationPrice(a), a.getMaterials(), true);
        Receipt receipt = Payment.pay(bill, cargo(), storage());
        if (receipt == null) {
            refresh("You cannot afford that.", Misc.getNegativeHighlightColor());
            return;
        }
        SpecialItemData item = new SpecialItemData(a.getId(), null);
        cargo().addSpecial(item, 1);
        push("fabricating " + a.getName(), null, () -> {
            CargoAPI cargo = cargo();
            if (cargo.getQuantity(CargoItemType.SPECIAL, item) < 1f) return;
            cargo.removeItems(CargoItemType.SPECIAL, item, 1);
            Payment.refund(receipt, cargo, storage());
        });
        Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        refresh(a.getName() + " fabricated and added to your cargo.", Misc.getPositiveHighlightColor());
    }

    private void undoLast() {
        Undo last = undo.poll();
        if (last == null) {
            refresh();
            return;
        }
        last.action.run();
        refresh("Undone: " + last.label + ".", Misc.getHighlightColor());
    }

    private void push(String label, FleetMemberAPI ship, Runnable action) {
        undo.push(new Undo(label, action));
    }

    /** After any change: put the hull mod on or off, recompute stats and redraw the ship. */
    private void shipChanged(FleetMemberAPI ship) {
        if (ship == null) return;
        HullmodSync.sync(ship);
        ship.updateStats();
        if (ship == member && screen != Screen.MAIN) dialog.getVisualPanel().showFleetMemberInfo(ship);
    }

    private void leave() {
        dialog.setPlugin(original);
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        text.addPara("You leave the Extra Systems bay.");
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    // ================================================================= prices

    private int upgradePrice(Upgrade u, int level) {
        float raw = Pricing.levelCost(member.getHullSpec().getBaseValue(), u.getCostWeight(), level,
                Settings.maxLevel(), Settings.costMult());
        return Pricing.round(Pricing.discounted(raw, yard.getDiscount()));
    }

    /** The refund for removing the top level: the configured share of that level's part of what was paid. */
    private int refundFor(Upgrade u, int level) {
        ShipData data = ShipData.get(member);
        if (data == null || level <= 0) return 0;
        return Math.round(data.getPaid(u) / level * Settings.refundFraction());
    }

    private int qualityPrice(float from, float to) {
        float raw = Pricing.qualityCost(member.getHullSpec().getBaseValue(), from, to - from, Settings.qualityCostMult());
        return Pricing.round(Pricing.discounted(raw, yard.getDiscount()));
    }

    private int fabricationPrice(Augment a) {
        return Pricing.round(Pricing.discounted(Pricing.augmentCost(a.getFabricationCost(), Settings.augmentCostMult()),
                yard.getDiscount()));
    }

    private static Map<String, Float> materialsForQuality() {
        return Upgrade.DURABILITY.getMaterials();
    }

    // ================================================================= text helpers

    private void addShipHeader() {
        HullSize size = member.getHullSpec().getHullSize();
        String where = isInStorage(member) ? "in storage here" : "in your fleet";
        LabelAPI label = text.addPara("%s, " + esc(member.getHullSpec().getHullNameWithDashClass()) + " "
                + sizeName(size) + ", " + where + ".", Misc.getHighlightColor(), member.getShipName());
        label.setHighlightColors(Misc.getBasePlayerColor());
    }

    private void addCreditsLine() {
        text.addPara("You have %s.", Misc.getHighlightColor(), Misc.getDGSCredits(cargo().getCredits().get()));
    }

    private void addPaymentLine() {
        if (method == Method.CREDITS) {
            text.addPara("Paying with credits. You have %s.", Misc.getHighlightColor(),
                    Misc.getDGSCredits(cargo().getCredits().get()));
        } else if (method == Method.MATERIALS) {
            text.addPara("Paying with materials from your cargo" + (storage() != null ? " and storage here" : "") + ".",
                    Misc.getHighlightColor());
        } else {
            text.addPara("Paying with Industrial Evolution components from your cargo"
                    + (storage() != null ? " and storage here" : "") + ".", Misc.getHighlightColor());
        }
    }

    /** "Hull integrity +12% (next +15%)" for each line that applies to this ship. */
    private void addEffectList(TooltipMakerAPI tooltip, Upgrade u, int fromLevel, int toLevel, float pad) {
        ShipData data = ShipData.get(member);
        float q = data == null ? ShipData.startingQuality(member.getId()) : data.getQuality();
        int max = Settings.maxLevel();
        float strength = Settings.strength();
        HullSize size = member.getHullSpec().getHullSize();
        float fromPower = Pricing.power(fromLevel, max, q, strength);
        float toPower = Pricing.power(toLevel, max, q, strength);
        Color h = Misc.getHighlightColor();
        Color pos = Misc.getPositiveHighlightColor();
        tooltip.addPara(u.getName() + " (level %s of %s)", pad, h, String.valueOf(toLevel), String.valueOf(max));
        for (Effect e : u.getEffects()) {
            if (!e.appliesTo(member.getHullSpec(), member.getStats())) continue;
            float from = e.valueAt(fromPower, size);
            float to = e.valueAt(toPower, size);
            if (fromLevel == toLevel) {
                tooltip.addPara("   " + e.getLabel() + ": %s", 2f, h, e.format(to));
            } else {
                tooltip.addPara("   " + e.getLabel() + ": %s, then %s", 2f, new Color[]{h, toLevel > fromLevel ? pos : h},
                        e.format(from), e.format(to));
            }
        }
    }

    private static void addAugmentDescription(TooltipMakerAPI tooltip, Augment a, float pad) {
        tooltip.addPara(a.getName(), Misc.getHighlightColor(), pad);
        for (String line : a.getEffectLines()) {
            tooltip.addPara("   " + line, 2f);
        }
        if (a.getRequirementText() != null) {
            tooltip.addPara("   " + a.getRequirementText(), Misc.getGrayColor(), 2f);
        }
    }

    private void addBill(TooltipMakerAPI tooltip, Bill bill, float pad) {
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        if (bill.getCredits() > 0) {
            float have = cargo().getCredits().get();
            tooltip.addPara("Price: %s (you have %s).", pad, new Color[]{have >= bill.getCredits() ? h : bad, h},
                    Misc.getDGSCredits(bill.getCredits()), Misc.getDGSCredits(have));
        }
        if (!bill.getCommodities().isEmpty()) {
            tooltip.addPara("Price:", pad);
            for (Map.Entry<String, Integer> e : bill.getCommodities().entrySet()) {
                int have = Payment.available(e.getKey(), cargo(), storage());
                tooltip.addPara("   " + Payment.commodityName(e.getKey()) + ": %s (you have %s)", 2f,
                        new Color[]{have >= e.getValue() ? h : bad, h},
                        Misc.getWithDGS(e.getValue()), Misc.getWithDGS(have));
            }
        }
    }

    private static String billSummary(Bill bill) {
        if (bill.getCredits() > 0) return Misc.getDGSCredits(bill.getCredits());
        if (bill.getCommodities().size() == 1) {
            Map.Entry<String, Integer> e = bill.getCommodities().entrySet().iterator().next();
            return Misc.getWithDGS(e.getValue()) + " " + Payment.commodityName(e.getKey()).toLowerCase();
        }
        return "materials worth " + Misc.getDGSCredits(bill.getValue());
    }

    // ================================================================= cargo helpers

    private CargoAPI cargo() {
        return Global.getSector().getPlayerFleet().getCargo();
    }

    /** This market's storage, if the player can use it. */
    private CargoAPI storage() {
        if (market == null || !Misc.playerHasStorageAccess(market)) return null;
        return Misc.getStorageCargo(market);
    }

    private boolean isInStorage(FleetMemberAPI m) {
        CargoAPI storage = storage();
        return storage != null && storage.getMothballedShips() != null
                && storage.getMothballedShips().getMembersListCopy().contains(m);
    }

    private int augmentsOwned(Augment a) {
        SpecialItemData item = new SpecialItemData(a.getId(), null);
        float total = cargo().getQuantity(CargoItemType.SPECIAL, item);
        CargoAPI storage = storage();
        if (storage != null) total += storage.getQuantity(CargoItemType.SPECIAL, item);
        return (int) Math.floor(total + 0.001f);
    }

    /** Removes one augment item, from cargo first. Returns where it came from, or null if there was none. */
    private CargoAPI takeAugment(Augment a) {
        SpecialItemData item = new SpecialItemData(a.getId(), null);
        CargoAPI cargo = cargo();
        if (cargo.getQuantity(CargoItemType.SPECIAL, item) >= 1f) {
            cargo.removeItems(CargoItemType.SPECIAL, item, 1);
            return cargo;
        }
        CargoAPI storage = storage();
        if (storage != null && storage.getQuantity(CargoItemType.SPECIAL, item) >= 1f) {
            storage.removeItems(CargoItemType.SPECIAL, item, 1);
            return storage;
        }
        return null;
    }

    private static String sizeName(HullSize size) {
        switch (size) {
            case FRIGATE: return "frigate";
            case DESTROYER: return "destroyer";
            case CRUISER: return "cruiser";
            case CAPITAL_SHIP: return "capital ship";
            default: return "ship";
        }
    }

    /** Escapes text spliced into a format string for the highlight overloads of addPara. */
    static String esc(String s) {
        return s == null ? "" : s.replace("%", "%%");
    }

    private static String joined(List<String> parts, String last) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? last : ", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static final class Undo {
        final String label;
        final Runnable action;

        Undo(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }
}
