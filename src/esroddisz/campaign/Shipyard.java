package esroddisz.campaign;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;

/**
 * What a market's shipbuilding industry offers the bay: a discount, hull quality improvements and
 * augment fabrication. Industries are listed under "esroShipyards" in data/config/settings.json, so other
 * mods' shipyards can be added there without code.
 */
public final class Shipyard {

    /** Extra discount at the player's own colonies. */
    public static final float OWN_COLONY_DISCOUNT = 0.1f;
    public static final float PRISTINE_NANOFORGE_STEP = 0.05f;
    public static final float CORRUPTED_NANOFORGE_STEP = 0.025f;

    public static final Shipyard NONE = new Shipyard(null, 0f, 0f, false, false);

    private final String industryName;
    private final float discount;
    private final float qualityStep;
    private final boolean fabricates;
    private final boolean ownColony;

    Shipyard(String industryName, float discount, float qualityStep, boolean fabricates, boolean ownColony) {
        this.industryName = industryName;
        this.discount = discount;
        this.qualityStep = qualityStep;
        this.fabricates = fabricates;
        this.ownColony = ownColony;
    }

    /** The best functional shipyard at the market. */
    public static Shipyard at(MarketAPI market) {
        if (market == null) return NONE;
        boolean own = market.isPlayerOwned();
        Shipyard best = new Shipyard(null, 0f, 0f, false, own);
        JSONObject config = config();
        if (config == null || market.getIndustries() == null) return best;
        JSONObject byId = config.optJSONObject("industries");
        JSONObject byTag = config.optJSONObject("tags");
        for (Industry industry : market.getIndustries()) {
            if (industry == null || industry.getId() == null || !industry.isFunctional()) continue;
            JSONObject entry = entryFor(industry, byId, byTag);
            if (entry == null) continue;
            float discount = (float) entry.optDouble("discount", 0d);
            float step = (float) entry.optDouble("qualityStep", 0d);
            boolean fabricates = entry.optBoolean("fabricatesAugments", false);
            if (step > 0f) step += nanoforgeStep(industry);
            Shipyard candidate = new Shipyard(industry.getCurrentName(), discount, step, fabricates, own);
            if (candidate.score() > best.score()) best = candidate;
        }
        return best;
    }

    /** An entry for the industry's own id wins; otherwise the first of its tags that is listed. */
    private static JSONObject entryFor(Industry industry, JSONObject byId, JSONObject byTag) {
        if (byId != null && byId.optJSONObject(industry.getId()) != null) return byId.optJSONObject(industry.getId());
        if (byTag == null || industry.getSpec() == null || industry.getSpec().getTags() == null) return null;
        for (String tag : industry.getSpec().getTags()) {
            if (tag != null && byTag.optJSONObject(tag) != null) return byTag.optJSONObject(tag);
        }
        return null;
    }

    private static float nanoforgeStep(Industry industry) {
        SpecialItemData item = industry.getSpecialItem();
        if (item == null || item.getId() == null) return 0f;
        if (Items.PRISTINE_NANOFORGE.equals(item.getId())) return PRISTINE_NANOFORGE_STEP;
        if (Items.CORRUPTED_NANOFORGE.equals(item.getId())) return CORRUPTED_NANOFORGE_STEP;
        return 0f;
    }

    private static JSONObject config() {
        try {
            return Global.getSettings().getJSONObject("esroShipyards");
        } catch (Exception e) {
            return null;
        }
    }

    private float score() {
        return qualityStep * 10f + discount + (fabricates ? 0.5f : 0f);
    }

    public boolean exists() {
        return industryName != null;
    }

    /** The shipyard building's name, or null if the market has none. */
    public String getIndustryName() {
        return industryName;
    }

    /** Discount on everything bought here, including the own-colony bonus. */
    public float getDiscount() {
        return discount + (ownColony ? OWN_COLONY_DISCOUNT : 0f);
    }

    public boolean isOwnColony() {
        return ownColony;
    }

    /** How much one quality improvement adds; 0 when this market cannot improve quality. */
    public float getQualityStep() {
        return qualityStep;
    }

    public boolean canImproveQuality() {
        return qualityStep > 0f;
    }

    public boolean canFabricate() {
        return fabricates;
    }
}
