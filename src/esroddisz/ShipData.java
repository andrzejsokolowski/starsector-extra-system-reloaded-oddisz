package esroddisz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import esroddisz.augments.Augment;
import esroddisz.upgrades.Upgrade;

/**
 * One ship's upgrades, quality and augments, keyed by fleet member id.
 *
 * The save holds only plain maps, lists, strings and numbers - never a class of this mod - so renaming
 * or reorganising classes between versions cannot break a save. This class is a view over that record.
 */
public final class ShipData {

    public static final String STORE_KEY = "esro_ships";
    /**
     * Put on an upgraded ship's variant and its modules, so modules and the refit simulator's copies
     * find the record of the ship they belong to. The fleet member id is always checked first.
     */
    public static final String OWNER_TAG_PREFIX = "esro_owner:";

    static final String QUALITY = "quality";
    static final String INVESTED = "invested";
    static final String LEVELS = "levels";
    static final String PAID = "paid";
    static final String AUGMENTS = "augments";

    private final String memberId;
    private final Map<String, Object> record;

    ShipData(String memberId, Map<String, Object> record) {
        this.memberId = memberId;
        this.record = record;
    }

    // ---------------------------------------------------------------- store

    /** The record map in the save, or null when there is no campaign (title screen, missions). */
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> store(boolean create) {
        SectorAPI sector = Global.getSector();
        if (sector == null) return null;
        Map<String, Object> persistent = sector.getPersistentData();
        if (persistent == null) return null;
        Object raw = persistent.get(STORE_KEY);
        if (raw instanceof Map) return (Map<String, Map<String, Object>>) raw;
        if (!create) return null;
        Map<String, Map<String, Object>> fresh = new HashMap<>();
        persistent.put(STORE_KEY, fresh);
        return fresh;
    }

    /** The ship's record, or null if it has never been worked on. */
    public static ShipData get(String memberId) {
        if (memberId == null) return null;
        Map<String, Map<String, Object>> store = store(false);
        if (store == null) return null;
        Map<String, Object> record = store.get(memberId);
        return record == null ? null : new ShipData(memberId, record);
    }

    public static ShipData get(FleetMemberAPI member) {
        return member == null ? null : get(member.getId());
    }

    public static ShipData getOrCreate(FleetMemberAPI member) {
        Map<String, Map<String, Object>> store = store(true);
        Map<String, Object> record = store.get(member.getId());
        if (record == null) {
            record = new HashMap<>();
            store.put(member.getId(), record);
        }
        return new ShipData(member.getId(), record);
    }

    /**
     * Finds the record for ship stats: by fleet member first, then by the owner tag that modules carry.
     * Returns null for any ship without upgrades, so enemy and simulator ships are never touched.
     */
    public static ShipData forStats(MutableShipStatsAPI stats) {
        if (stats == null) return null;
        FleetMemberAPI member = stats.getFleetMember();
        ShipData data = member == null ? null : get(member.getId());
        if (data != null) return data;
        return forOwnerTag(stats.getVariant());
    }

    static ShipData forOwnerTag(ShipVariantAPI variant) {
        if (variant == null || variant.getTags() == null) return null;
        for (String tag : variant.getTags()) {
            if (tag != null && tag.startsWith(OWNER_TAG_PREFIX)) {
                return get(tag.substring(OWNER_TAG_PREFIX.length()));
            }
        }
        return null;
    }

    /** For tests and tools that hold a record directly. */
    public static ShipData wrap(String memberId, Map<String, Object> record) {
        return new ShipData(memberId, record);
    }

    // ---------------------------------------------------------------- record

    public String getMemberId() {
        return memberId;
    }

    /** True when the ship has anything that changes it in game. */
    public boolean hasEffects() {
        if (!augmentIds().isEmpty()) return true;
        for (Object level : levels().values()) {
            if (level instanceof Number && ((Number) level).intValue() > 0) return true;
        }
        return false;
    }

    public boolean hasStoredQuality() {
        return record.get(QUALITY) instanceof Number;
    }

    /** The stored quality, or the starting roll if the ship has never been worked on. */
    public float getQuality() {
        Object q = record.get(QUALITY);
        if (q instanceof Number) return ((Number) q).floatValue();
        return startingQuality(memberId);
    }

    public void setQuality(float quality) {
        record.put(QUALITY, Math.round(quality * 1000f) / 1000f);
    }

    /** Stores the starting roll so later changes to the quality settings do not move this ship. */
    public void fixQuality() {
        if (!hasStoredQuality()) setQuality(startingQuality(memberId));
    }

    public static float startingQuality(String memberId) {
        return Quality.roll(memberId, Settings.randomQuality(), Settings.qualityMinStart(), Settings.qualityMaxStart());
    }

    public float getInvested() {
        Object v = record.get(INVESTED);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    public void addInvested(float credits) {
        record.put(INVESTED, Math.max(0f, getInvested() + credits));
    }

    public int getLevel(Upgrade upgrade) {
        Object v = levels().get(upgrade.getId());
        return v instanceof Number ? Math.max(0, ((Number) v).intValue()) : 0;
    }

    public void setLevel(Upgrade upgrade, int level) {
        Map<String, Object> levels = levelsForWrite();
        if (level <= 0) {
            levels.remove(upgrade.getId());
        } else {
            levels.put(upgrade.getId(), level);
        }
    }

    /** Credit value paid for the upgrade's current levels, whatever it was paid with. */
    @SuppressWarnings("unchecked")
    public float getPaid(Upgrade upgrade) {
        Object v = record.get(PAID);
        if (!(v instanceof Map)) return 0f;
        Object paid = ((Map<String, Object>) v).get(upgrade.getId());
        return paid instanceof Number ? ((Number) paid).floatValue() : 0f;
    }

    @SuppressWarnings("unchecked")
    public void setPaid(Upgrade upgrade, float value) {
        Object v = record.get(PAID);
        Map<String, Object> paid;
        if (v instanceof Map) {
            paid = (Map<String, Object>) v;
        } else {
            paid = new HashMap<>();
            record.put(PAID, paid);
        }
        if (value <= 0f) {
            paid.remove(upgrade.getId());
        } else {
            paid.put(upgrade.getId(), value);
        }
    }

    public int totalLevels() {
        int total = 0;
        for (Upgrade u : Upgrade.values()) total += getLevel(u);
        return total;
    }

    /** Installed augments in install order. Unknown ids (from a newer version) are skipped. */
    public List<Augment> getAugments() {
        List<Augment> result = new ArrayList<>();
        for (String id : augmentIds()) {
            Augment a = Augment.byId(id);
            if (a != null && !result.contains(a)) result.add(a);
        }
        return result;
    }

    public boolean hasAugment(Augment augment) {
        return augmentIds().contains(augment.getId());
    }

    public void addAugment(Augment augment) {
        List<String> ids = augmentIdsForWrite();
        if (!ids.contains(augment.getId())) ids.add(augment.getId());
    }

    public void removeAugment(Augment augment) {
        augmentIdsForWrite().remove(augment.getId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> levels() {
        Object v = record.get(LEVELS);
        return v instanceof Map ? (Map<String, Object>) v : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> levelsForWrite() {
        Object v = record.get(LEVELS);
        if (v instanceof Map) return (Map<String, Object>) v;
        Map<String, Object> fresh = new HashMap<>();
        record.put(LEVELS, fresh);
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private List<String> augmentIds() {
        Object v = record.get(AUGMENTS);
        return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> augmentIdsForWrite() {
        Object v = record.get(AUGMENTS);
        if (v instanceof List) return (List<String>) v;
        List<String> fresh = new ArrayList<>();
        record.put(AUGMENTS, fresh);
        return fresh;
    }
}
