package esroddisz.items;

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import esroddisz.augments.Augment;

/** Cargo tooltip for augment items: the description, what it does, and where to install it. */
public class AugmentItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler,
                              Object stackSource, boolean useGray) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
        float opad = 10f;
        Augment augment = Augment.byId(getId());
        if (augment != null) {
            tooltip.addPara("Augment effects:", Misc.getHighlightColor(), opad);
            for (String line : augment.getEffectLines()) {
                tooltip.addPara("   " + line, 2f);
            }
            if (augment.getRequirementText() != null) {
                tooltip.addPara(augment.getRequirementText(), Misc.getGrayColor(), opad);
            }
        }
        tooltip.addPara("Install it in a ship's augment slot at the Extra Systems bay of any friendly market "
                + "with a spaceport.", Misc.getGrayColor(), opad);
        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }
}
