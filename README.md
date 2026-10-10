# Extra System Reloaded - Oddisz Edition

Pour credits, materials and story points into the ships you love.

Every friendly market with a spaceport gets an **Extra Systems bay** in its main menu. Pick a ship
from your fleet (or from your storage there) and buy upgrade levels, raise the hull's quality, and
slot rare augments with effects of their own. Only your ships are ever modified: enemy fleets never
get any of it.

This is a from-scratch remake of the idea behind *Extra System* by Originem and *Extra System
Reloaded* by Iridescens and President Matt Damon, built for Starsector 0.98a. None of their code or
art is used.

## Upgrades

Each upgrade has 10 levels (changeable in the settings). Every level makes all of its stats a little
stronger; the values below are for the maximum level at 100% hull quality.

| Upgrade | At maximum level |
| --- | --- |
| Durability | Hull +30%, armor +20%, EMP damage taken -40%, weapon and engine durability +50%, combat repair time -40% |
| Weapons | Weapon damage +15%, weapon flux cost -20% |
| Targeting | Weapon range +15%, turn rate +50%, recoil -40%, autofire leading +50%, projectile speed +20% |
| Magazines | Rate of fire +15%, ammunition capacity +50%, ammunition regeneration +50% |
| Flux Grid | Flux capacity +20%, dissipation +20%, vent rate +50%, overload duration -30% |
| Defenses | Shields: damage taken -20%, upkeep -50%, raise and turn speed +100%, arc +30 degrees. Phase: activation cost, upkeep and cooldown -40% |
| Engines | Top speed +15%, acceleration +40%, turning +40%, burn level +2 |
| Ship System | Cooldown -40%, flux cost -40%, range +30%; systems with charges get +2 charges and +50% regeneration |
| Hangars | Fighter replacement time -30%, replacement rate recovers 50% faster and drains 40% slower, engagement range +30% |
| Logistics | Monthly supplies, fuel use and supplies to recover -40%, required crew -30% |
| Cargo Holds | Cargo, fuel and crew capacity +40% |
| Sensors | Sensor strength +40%, sensor profile -40% |
| Conditioning | Peak performance time +40% (up to +80% on frigates), CR loss after peak -40%, CR per deployment -30%, daily CR recovery and hull repair +50% |

**Overclocking:** past the maximum, up to 5 more levels can be bought for a story point each, on top
of the price.

Prices follow the hull's value, so a full set costs a few hundred thousand credits on a frigate and
several million on a capital ship. Any level can be removed again for half of what you paid for it.

## Hull quality

Every hull has a quality between 80% and 120% that multiplies the effect of all its upgrades. Markets
with a working **Heavy Industry** (+5% per improvement) or **Orbital Works** (+10%) can raise it, up
to 200%; a nanoforge in the building adds a little more. Modded heavy industries count too. Each
improvement costs more than the last.

## Augments

Rare devices that slot into a ship (1 slot on frigates, 2 on destroyers and cruisers, 3 on capitals).
They turn up in salvage and can be fabricated at markets with Orbital Works. Removing one returns it
to your cargo.

| Augment | Effect |
| --- | --- |
| Overload Damper | Overload duration -50%. Once every 30 seconds an overload is cancelled the moment it starts. |
| Nanolattice Regenerator | Armor regenerates 2% per second in combat, up to twice the ship's total armor per battle. |
| Cryo-Flux Exchanger | Dissipation and vent rate +25%; with shields up, 25% of dissipation also bleeds off hard flux. |
| Temporal Capacitor | System cooldown and flux cost -50%; systems with charges get +1 charge and twice the regeneration. |
| Phase Coil Tuner | Phase activation cost, upkeep and cooldown -50%. Phase ships only. |
| Point Defense Integrator | Small ballistic and energy weapons act as point defense; +50% damage to fighters and missiles; PD range +100. |
| Hangar Microforge | Fighter replacement time -40%; replacement rate recovers twice as fast and drains half as fast. Carriers only. |
| Missile Autoforge | Missile ammunition +25%; every 45 seconds each missile weapon reloads 30% of its ammunition. |
| Vent Thrusters | Vent rate +30%; while venting, top speed +50%, acceleration +100% and turning +50%. |
| Fire Control Equalizer | Ballistic and energy weapons with a base range under 800 gain up to +200 range, never past 800. |

## Paying

- **Credits.**
- **Materials** from your cargo and then from your storage at that market, worth 60% of the price.
- **Industrial Evolution components**, if IndEvo is installed: ship components for upgrades, relic
  components for hull quality and augments.

Shipyards give a discount (10% for Heavy Industry, 15% for Orbital Works), and your own colonies
another 10%. Every purchase on a visit can be undone in full, last first.

## Settings

Open LunaLib's settings (`Shift+F2` in the campaign) and pick **Extra System Reloaded - Oddisz
Edition**. Maximum level, overclock levels, upgrade strength, starting and maximum hull quality,
augment slots, removal rules and every price multiplier are there.

## Compatibility

- Requires [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658.0).
- Industrial Evolution is optional; its components become a payment option.
- Can be added to an existing save. It cannot be removed from a save once a ship has been upgraded,
  because upgraded ships carry its hidden hull mod.
- Uses its own ids, hull mod and items, so it does not clash with Exotica Technologies.

## Building

Set `starsectorPath` in `gradle.properties`, then run `gradlew releaseZip`. The build runs every
check and writes `ExtraSystemReloadedOddiszEdition.zip` next to this file, ready for a mod manager.
`python tools/make_icons.py` redraws the icons (needs Pillow).
