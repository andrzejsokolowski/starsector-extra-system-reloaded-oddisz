# Extra System Reloaded - Oddisz Edition

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.
For requested gameplay changes and bug fixes, complete the release workflow unless the user requests otherwise.
Questions, investigations, and instruction-only changes do not require a new version or tag.
Respect tool approval decisions. These instructions do not change sandbox permissions.

## Releases

Andrzej approved the first public release, 0.1.0, on 2026-10-10. The repository is public. From now on
every requested change follows the release workflow below without asking again.

## Origin

A from-scratch remake of the idea behind Extra System (Originem) and Extra System Reloaded
(Iridescens and President Matt Damon). None of their code or art is used. Their source is kept for
reference in `../ExtraSystemReloaded (original)`, outside this repository; never copy from it.
Requested on the Starsector forum by a player who wants to sink credits, materials and relics into
individual ships, likes over-the-top builds, and dislikes Exotica Technologies buffing enemy fleets.
Hence the locked rule below.

## Locked design decisions

- Only the player's ships are ever modified. No enemy, derelict or NPC fleet gets upgrades or augments.
- Upgrades: levels up to the LunaLib maximum (default 10), effects scale linearly with level, hull
  quality and the strength setting. Reductions have floors so nothing reaches zero.
- Levels past the maximum are overclocks: one story point each, on top of the price.
- Hull quality: rolled per ship from its fleet member id (default 80-120%), fixed on first purchase,
  raised only at shipyards (Heavy Industry or anything tagged `heavyindustry`, Orbital Works and the
  AoTD VoK equivalents). It multiplies every upgrade.
- Augments are special items: found in `rare_tech` salvage, fabricated at Orbital Works. Removing one
  returns it unless the setting says otherwise.
- Payment: credits, materials (cargo first, then this market's storage) or Industrial Evolution
  components when IndEvo is enabled. Every purchase on a visit can be undone in full.
- Refunds for removed levels are a share of what was actually paid for that level.

## Layout

- `src/esroddisz/ExtraSystemsModPlugin` - adds the transient `HullmodSync` on every load.
- `src/esroddisz/Settings` - LunaLib reads with defaults and clamps; tests swap `Settings.source`.
- `src/esroddisz/ShipData` - one ship's record. The save holds only plain maps, lists, strings and
  numbers under `esro_ships` in sector persistent data; never put a class of this mod in it.
- `src/esroddisz/Pricing`, `Quality` - pure formulas, covered by the checks.
- `src/esroddisz/upgrades/Upgrade` - every upgrade, its stat lines (`Effect`), weights and materials.
  Upgrade and augment ids are stored in saves: never rename one.
- `src/esroddisz/augments/Augment` - every augment: stat changes, listeners and per-frame combat effects.
- `src/esroddisz/hullmods/ExtraSystemsHullmod` - the hidden permanent hull mod that applies a ship's
  record. It holds no state of its own.
- `src/esroddisz/campaign/HullmodSync` - keeps the hull mod and the `esro_owner:` tag on exactly the
  player's upgraded ships and their modules.
- `src/esroddisz/campaign/Shipyard`, `Payment` - market facilities and bills/receipts.
- `src/esroddisz/dialog/WorkshopDialog` - the Extra Systems bay, opened by the `EsroOpenWorkshop` rule
  command from `data/campaign/rules.csv`.
- `data/config/settings.json` - `esroShipyards`: shipyard industries by id and by tag.
- `tools/make_icons.py` - draws every icon in `graphics/` (run with Pillow from the project root).
- `test/esroddisz/tests/Checks` - standalone checks against mock game objects, run by `gradlew check`.

## Game API traps

- `TooltipMakerAPI` and `TextPanelAPI` `addPara` overloads that take highlights run `String.format`;
  the plain ones do not. Double any literal `%` spliced into a highlighted format (`WorkshopDialog.esc`).
  The checks fail on any highlighted text that does not format.
- LunaLib draws every field name, description and text in `LunaSettings.csv` through that same
  formatting, so a percent sign there crashes the settings page (0.1.0 did, on the Hull Quality tab).
  Write "percent" instead; `checkReleaseFiles` rejects any `%` in that file.
- Story point options: the game spends the point on confirmation, before `optionSelected` runs.
  Give it back if the purchase then fails.
- Only vanilla sound ids may be played; the checks verify the ones in use.
- Anything the game calls on its own - stat passes, combat frames, the background hull mod check,
  option popups, the picker callback - is wrapped so an error is logged once (`Faults`) and switched
  off instead of crashing the game. Keep new code of that kind wrapped the same way.
- Point Defense Integrator converts small guns only when a medium or large non-PD gun remains; the AI
  treats a ship whose guns are all point defense as unarmed and hangs back.

## Required release workflow

1. Preserve unrelated changes from the user and other agents.
2. Implement the requested change and update the version in mod_info.json and esr_oddisz.version.
3. Run ./gradlew releaseZip to build, check, and package ExtraSystemReloadedOddiszEdition.zip.
4. Make sure that the ZIP contains the updated metadata, current JAR, data and graphics.
5. Commit the completed change on main with the existing personal Git identity.
6. Tag the commit v<version> and push the commit and tag to the personal GitHub origin, create a draft
   GitHub Release for that exact tag, attach the tested ZIP, publish it, and mark a new stable release
   as latest unless the user requests otherwise.
7. Make sure that the public version file and the downloaded ZIP report the tagged version, and
   compare the downloaded ZIP's SHA-256 hash with the tested local ZIP.
8. Give the user the release link and local ZIP location.

Every new version tag created or pushed by an agent requires a matching published release with the ZIP attached.
Do not stop after pushing a tag. A tag is not a release download.
If a matching draft exists, inspect and complete it instead of creating a duplicate.
If publication fails, report the blocker and the incomplete release.
Do not overwrite published assets, replace tags, or mark older releases as latest without a specific request.

## Update metadata

Keep mod_info.json, esr_oddisz.version, and data/config/version/version_files.csv consistent
(`gradlew check` enforces it). Use forum topic 36143 in both metadata files: a string in mod_info.json,
a number in the .version file.

https://fractalsoftworks.com/forum/index.php?topic=36143.0
Use this update URL in updateCheckURL and masterVersionFile:

https://raw.githubusercontent.com/andrzejsokolowski/starsector-extra-system-reloaded-oddisz/main/esr_oddisz.version

Use this directDownloadURL and the exact asset name ExtraSystemReloadedOddiszEdition.zip:

https://github.com/andrzejsokolowski/starsector-extra-system-reloaded-oddisz/releases/latest/download/ExtraSystemReloadedOddiszEdition.zip

## Release notes: changelog only

Write only the changelog since the previous published GitHub Release (the first was v0.1.0). No dependency reminders, installation guides, forum links,
build narration or test-status statements on the release page.
