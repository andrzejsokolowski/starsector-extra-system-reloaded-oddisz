package esroddisz.campaign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;

import esroddisz.Pricing;

/**
 * Ways to pay the bay, and the bills they produce. Materials and components come out of the fleet's
 * cargo first and then out of this market's storage; a receipt remembers exactly where each unit came
 * from so a purchase can be undone without a trace.
 */
public final class Payment {

    public static final String SHIP_COMPONENTS = "IndEvo_parts";
    public static final String RELIC_COMPONENTS = "IndEvo_rare_parts";

    public enum Method {
        CREDITS("Credits"),
        MATERIALS("Materials"),
        COMPONENTS("Industrial Evolution components");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** The next method in the cycle that is available in this game. */
        public Method next() {
            Method m = this;
            do {
                m = values()[(m.ordinal() + 1) % values().length];
            } while (!m.isAvailable());
            return m;
        }

        public boolean isAvailable() {
            return this != COMPONENTS || componentsAvailable();
        }
    }

    /** What a purchase asks for: credits and/or commodities. */
    public static final class Bill {
        private final int credits;
        private final Map<String, Integer> commodities;
        /** Credit value of the bill, used for the "invested" total. */
        private final float value;

        Bill(int credits, Map<String, Integer> commodities, float value) {
            this.credits = credits;
            this.commodities = Collections.unmodifiableMap(commodities);
            this.value = value;
        }

        public int getCredits() {
            return credits;
        }

        public Map<String, Integer> getCommodities() {
            return commodities;
        }

        public float getValue() {
            return value;
        }

        public boolean isFree() {
            return credits <= 0 && commodities.isEmpty();
        }
    }

    /** Exactly what was taken, and from where. */
    public static final class Receipt {
        final int credits;
        final Map<String, Integer> fromCargo = new LinkedHashMap<>();
        final Map<String, Integer> fromStorage = new LinkedHashMap<>();
        final float value;

        Receipt(int credits, float value) {
            this.credits = credits;
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }

    private Payment() {}

    /**
     * Builds the bill for a credit price. {@code materials} is the commodity mix for the materials method;
     * {@code relic} picks relic components rather than ship components for the components method.
     */
    public static Bill bill(Method method, int creditPrice, Map<String, Float> materials, boolean relic) {
        if (creditPrice <= 0) return new Bill(0, Collections.<String, Integer>emptyMap(), 0f);
        switch (method) {
            case MATERIALS: {
                Map<String, Integer> amounts = materialAmounts(creditPrice * Pricing.MATERIAL_SHARE, materials);
                if (!amounts.isEmpty()) return new Bill(0, amounts, valueOf(amounts));
                break;
            }
            case COMPONENTS: {
                String id = relic ? RELIC_COMPONENTS : SHIP_COMPONENTS;
                float price = basePrice(id);
                if (price > 0f) {
                    int units = Math.max(1, (int) Math.ceil(creditPrice * Pricing.COMPONENT_SHARE / price));
                    Map<String, Integer> amounts = new LinkedHashMap<>();
                    amounts.put(id, units);
                    return new Bill(0, amounts, valueOf(amounts));
                }
                break;
            }
            default:
                break;
        }
        return new Bill(creditPrice, Collections.<String, Integer>emptyMap(), creditPrice);
    }

    /** Splits a credit value over the material mix by base price. Every listed commodity costs at least 1. */
    static Map<String, Integer> materialAmounts(float value, Map<String, Float> mix) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (mix == null || mix.isEmpty()) return result;
        float total = 0f;
        for (Float share : mix.values()) total += Math.max(0f, share);
        if (total <= 0f) return result;
        for (Map.Entry<String, Float> e : mix.entrySet()) {
            if (e.getValue() <= 0f) continue;
            float price = basePrice(e.getKey());
            if (price <= 0f) continue;
            int units = Math.max(1, (int) Math.ceil(value * e.getValue() / total / price));
            result.put(e.getKey(), units);
        }
        return result;
    }

    public static boolean canAfford(Bill bill, CargoAPI cargo, CargoAPI storage) {
        if (bill.credits > 0 && cargo.getCredits().get() < bill.credits) return false;
        for (Map.Entry<String, Integer> e : bill.commodities.entrySet()) {
            if (available(e.getKey(), cargo, storage) < e.getValue()) return false;
        }
        return true;
    }

    /** Units of a commodity in the fleet plus this market's storage. */
    public static int available(String commodity, CargoAPI cargo, CargoAPI storage) {
        float total = cargo.getCommodityQuantity(commodity);
        if (storage != null) total += storage.getCommodityQuantity(commodity);
        return (int) Math.floor(total + 0.001f);
    }

    /** Takes the bill. Returns null and takes nothing if it cannot be paid in full. */
    public static Receipt pay(Bill bill, CargoAPI cargo, CargoAPI storage) {
        if (!canAfford(bill, cargo, storage)) return null;
        Receipt receipt = new Receipt(bill.credits, bill.value);
        if (bill.credits > 0) cargo.getCredits().subtract(bill.credits);
        for (Map.Entry<String, Integer> e : bill.commodities.entrySet()) {
            String id = e.getKey();
            int needed = e.getValue();
            int fromCargo = Math.min(needed, (int) Math.floor(cargo.getCommodityQuantity(id) + 0.001f));
            if (fromCargo > 0) {
                cargo.removeCommodity(id, fromCargo);
                receipt.fromCargo.put(id, fromCargo);
            }
            int rest = needed - fromCargo;
            if (rest > 0 && storage != null) {
                storage.removeCommodity(id, rest);
                receipt.fromStorage.put(id, rest);
            }
        }
        return receipt;
    }

    /** Puts everything on the receipt back where it came from. */
    public static void refund(Receipt receipt, CargoAPI cargo, CargoAPI storage) {
        if (receipt == null) return;
        if (receipt.credits > 0) cargo.getCredits().add(receipt.credits);
        for (Map.Entry<String, Integer> e : receipt.fromCargo.entrySet()) {
            cargo.addCommodity(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : receipt.fromStorage.entrySet()) {
            if (storage != null) {
                storage.addCommodity(e.getKey(), e.getValue());
            } else {
                cargo.addCommodity(e.getKey(), e.getValue());
            }
        }
    }

    public static boolean componentsAvailable() {
        return Global.getSettings().getModManager().isModEnabled("IndEvo")
                && basePrice(SHIP_COMPONENTS) > 0f && basePrice(RELIC_COMPONENTS) > 0f;
    }

    public static String commodityName(String id) {
        CommoditySpecAPI spec = spec(id);
        return spec == null ? id : spec.getName();
    }

    static float basePrice(String id) {
        CommoditySpecAPI spec = spec(id);
        return spec == null ? 0f : spec.getBasePrice();
    }

    private static CommoditySpecAPI spec(String id) {
        try {
            return Global.getSettings().getCommoditySpec(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static float valueOf(Map<String, Integer> amounts) {
        float total = 0f;
        for (Map.Entry<String, Integer> e : amounts.entrySet()) total += basePrice(e.getKey()) * e.getValue();
        return total;
    }
}
