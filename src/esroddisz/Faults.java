package esroddisz;

import java.util.HashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;

/**
 * Logs an unexpected error once per place and kind, so code that runs every frame or twice a second
 * cannot flood starsector.log. Used where an error must not take the game down with it: the per-frame
 * combat effects, the stat pass, the background hull mod check and the bay's popups.
 */
public final class Faults {

    private static final Set<String> REPORTED = new HashSet<>();

    private Faults() {}

    public static void report(String where, Throwable error) {
        String key = where + "|" + error.getClass().getName();
        synchronized (REPORTED) {
            if (!REPORTED.add(key)) return;
        }
        try {
            Global.getLogger(Faults.class).error("Extra Systems: " + where + " failed; carrying on without it", error);
        } catch (RuntimeException ignored) {
            // No logger (tests without a game): the error is still contained.
        }
    }
}
