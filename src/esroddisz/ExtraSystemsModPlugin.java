package esroddisz;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import esroddisz.campaign.HullmodSync;

public class ExtraSystemsModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "esr_oddisz";

    @Override
    public void onGameLoad(boolean newGame) {
        // Transient: not written into saves, re-added on every load.
        Global.getSector().addTransientScript(new HullmodSync());
    }
}
