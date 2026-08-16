# Never farm

> **English** | [简体中文](./README.md)

An idle-farming mod for NeoForge 1.21.1. Drop down one block, and it collects animals, feeds the herd, and runs the breeding for you — while you go do literally anything else.

Version: `1.0.1` ｜ License: MIT ｜ Author: QiCai

---

## What is this

Vanilla animal husbandry is a chore: feeding, collecting, keeping the babies from wandering off, babysitting the breeding. This mod compresses your entire farm into a single block, and lets the sunrise and sunset do the rest.

**The idea in one line: animals go in the block → the block does the work → animals come out when they should.**

---

## Features

- **Collection & release**: right-click to vacuum up adult animals in range; the block releases `threshold` of them every sunrise, or you can force-release anytime
- **Type binding & lock**: the block auto-binds to the first animal type it collects; hold a spawn egg to pre-bind and keep the wrong species out
- **Auto feeding**: deposit feed (plants/seeds/etc.) into the block — the block feeds, not you
- **Threshold grazing**: set how many animals get released at sunrise (2 / 4 / 6) so the pen never overflows
- **Breeding control** (on by default): any breeding event — manually fed or not — immediately pulls parents *and* baby back into the block, released at the next sunrise. It's an anti-overpopulation measure
- **Redstone auto-breeding**: power the block with a redstone signal and it spends stored feed to spawn 4–6 babies at sunrise, fully hands-off
- **AI herding**: released animals are leashed to the block (configurable radius) and pulled back if they wander off
- **Chunk keep-loaded** (off by default): OPs can run `/neverfarm keepLoaded` to weakly load a 3×3 chunk area around each block, so the machine keeps running while you're away
- **Don't Starve survival DLC** (off by default): a bit of realism for your ranch — every daily settlement consumes feed; run out and both stored animals and un-fed mobs in the area get a wither debuff
- **Jade integration**: with Jade installed, look at the block to see bound type, stored count, threshold and feed — no UI needed
- **Data-preserving drops**: breaking the block drops an item carrying everything (animals, feed, threshold, binding). Re-place it elsewhere and the farm is intact

---

## How to use

### Placing it

Grab the **Never Farm Block** from the "Never Farm" creative tab. Place it in the middle of your farm and lure adult animals within range.

> The scan area is roughly the 3×3 chunks around the block (48 blocks radius). Lure animals with wheat/carrots and they'll follow you to it.

### Interactions

| Action | Effect |
| --- | --- |
| Right-click (empty hand) | Collect eligible animals in range (adult, fed, unnamed, sheep not shorn) |
| Right-click (with feed) | Deposit feed, up to 64 per block |
| Right-click (empty hand again) | Withdraw 64 feed |
| **Hold right-click** (≥250ms) | Open the threshold slider: move the mouse to pick 2 / 4 / 6, **release right-click to commit** |
| Sneak + right-click | Release 8 animals; 3 quick clicks within 40 ticks = release everything |
| Sneak + left-click | Toggle type lock. Empty hand = bind to nearest animal; spawn egg = pre-bind that type. Cannot unlock while animals are stored |

### Redstone auto-breeding

1. Give the block a redstone signal (button, lever, redstone dust — anything powered)
2. Keep enough feed in the block
3. At sunrise it spawns 4–6 babies per run (and takes a break while more than 12 babies are around)

### Don't Starve DLC

Set `donnotstarve.enable` to `true` in `never_farm-common.toml`:

- Each daily settlement consumes feed proportional to the stored count (`consumeRate`)
- Not enough feed → stored animals are marked as starving (debuff on release) and nearby mobs that weren't fed by a player are debuffed too
- Default debuff: Wither for 300 seconds, affecting cows, sheep, pigs — all configurable

### Keep-loaded

- Config `general.keepLoaded` (default `false`)
- Or in-game as an OP (permission level 2):

```
/neverfarm keepLoaded        # toggle
/neverfarm keepLoaded true   # or set explicitly
```

Each block weakly loads the surrounding 3×3 chunks, so sunrise/sunset settlement runs even with nobody nearby.

---

## Commands

| Command | Permission | Effect |
| --- | --- | --- |
| `/neverfarm keepLoaded [true\|false]` | OP 2 | Global toggle for chunk keep-loaded |
| `/neverfarm autobreed [true\|false]` | OP 2 | Global toggle for redstone auto-breeding |

---

## Configuration

The config lives at `config/never_farm-common.toml` (under `.minecraft/config/` in singleplayer, in the server root on a dedicated server).

| Key | Default | Description |
| --- | --- | --- |
| `general.maxSlots` | `32` | Max animals stored per block (16–64) |
| `general.checkInterval` | `Sunrise_Sunset` | Auto-work schedule (only sunrise/sunset supported for now) |
| `general.enableAIRestrict` | `true` | AI herding for released animals |
| `general.aiRestrictRadius` | `8` | Herding radius in blocks; animals pulled back beyond this |
| `general.keepLoaded` | `false` | Keep-loaded on by default |
| `general.enforceBreedingControl` | `true` | Breeding control (absorb parents + baby) |
| `donnotstarve.enable` | `false` | Survival DLC toggle |
| `donnotstarve.starveDeathsPerCheck` | `2` | Animals starved per settlement |
| `donnotstarve.consumeRate` | `1.0` | Daily feed consumption ratio (per stored animal) |
| `donnotstarve.affectedMobs` | cow/sheep/pig | Entity IDs affected by the feed rules |
| `donnotstarve.debuffEffect` | `WITHER` | Starvation debuff (registry names like `minecraft:wither` work too) |
| `donnotstarve.debuffDuration` | `300` | Debuff duration (seconds) |

---

## Running it on your server

1. Install NeoForge 21.1.248 on the server
2. Drop the mod jar into `mods/`
3. (Optional) Install Jade for the block HUD — works fine without it
4. Restart. OPs remember to run `/neverfarm keepLoaded true` if you want things to keep running while players are offline

---

## Development

Requires: Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 / Gradle (wrapper included).

```bash
./gradlew runClient         # run the client
./gradlew runServer         # run the server (working dir run/)
./gradlew runGameTestServer # run automated game tests
```

Quick source map:

```
src/main/java/dev/never_farm/
├── Never_farm.java         # main class: blocks/items/BE/creative tab registration
├── Config.java             # configuration
├── block/WorkBlock.java    # the block (includes data-preserving drops)
├── blockentity/WorkBlockEntity.java  # core logic: collect/release/breed/settle
├── handler/                # event handlers (schedule/breeding/interaction/commands/AI)
├── network/                # C2S/S2C payloads (threshold, block actions, data requests)
├── client/                 # threshold slider screen, Jade plugin
└── gametest/               # automated tests
```

---

## Changelog

- `1.0.1`: current. Keep-loaded command, redstone breeding toggle, data-preserving drops, Jade integration
- Tests: `WorkBlockGameTests` run via `runGameTestServer`, covering collect/release/threshold paths

---

## Roadmap / ideas

- More work time points (beyond sunrise/sunset)
- Multi-dimension/world stress testing
- Crafting recipes
- Staggered settlement for servers with many blocks (settlement currently fires globally on the same tick)

Found a problem? Open an issue, or go try it in-game and come back angry.