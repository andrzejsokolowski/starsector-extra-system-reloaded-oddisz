package esroddisz.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import esroddisz.dialog.WorkshopDialog;

/** rules.csv command: opens the Extra Systems bay from a market's main menu. */
public class EsroOpenWorkshop extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        WorkshopDialog workshop = new WorkshopDialog(dialog.getPlugin(), memoryMap);
        dialog.setPlugin(workshop);
        workshop.init(dialog);
        return true;
    }
}
