# Luminiflux's MTR Expansion Pack (LMEP) · 流光MTR拓展包

A client-side addon for **Minecraft Transit Railway 3.x** that shows you the rail you are
about to lay — its real length, slope and curve radius — while you are still connecting the
two nodes.

面向 **MTR3** 的纯客户端附属模组：在你连接两个节点、还没松手之前，就把这条轨道的**真实长度、
坡度、曲率半径**算给你看。

| | |
|---|---|
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.x |
| MTR | `MTR-forge-1.20.1-3.2.2-hotfix-2` (MTR 3.x) |
| Requires | [Architectury API](https://modrinth.com/mod/architectury-api) 9.2.14 for 1.20.1 Forge (MTR's own dependency) |
| Side | Client only |
| License | MIT |

---

## v1.0 feature — Live Rail Readout

MTR3 gives you no feedback while you are drawing a track: you pick the first node, aim at
the second one, right-click, and only *then* find out how long the result is — or that it
was rejected. LMEP fills that gap.

While you hold a rail connector that already has an anchor node selected and your crosshair
is on another node, a panel in the **bottom-left corner** shows:

* **The track length, live** — the true length along the curve *including the vertical
  rise*, which is the number you actually need when sizing a train. The horizontal
  projection is shown next to it for comparison.
* **ΔXYZ, slope and curve radius.**
* **The accumulated length of the line the anchor belongs to**, so you can plan a whole
  line rather than one segment.
* **Why a connection is refused**, using MTR's own rejection reasons (bad orientation,
  radius too small, cable-car constraints, mismatched transport mode).

The panel is deliberately *not* permanently on screen: it appears only while a rail
connector is in your hand, so it never clutters ordinary play. Before you have picked an
anchor it shows a single prompt line instead of the full readout.

Everything is computed from MTR's own classes, so the numbers cannot disagree with what MTR
actually builds.

### Keys

| Key | Action |
|---|---|
| `K` | Toggle the readout on/off |
| `J` | Toggle the panel |

### Configuration

`config/lmep-client.toml` — master switch, panel toggle, and the search budget for the
line-length walk.

---

## Install

1. Install **Forge 47.x** for Minecraft 1.20.1.
2. Put these in `.minecraft/mods/`:
   * `MTR-forge-1.20.1-3.2.2-hotfix-2.jar`
   * `architectury-9.2.14-forge.jar`
   * `lmep-1.0.0-hotfix-1.jar`
3. Launch the game. LMEP is client-side only — you can install it on a client while
   playing on a server that does not have it.

---

## How it works

MTR3 decides a rail connection in two places, and LMEP mirrors both instead of
approximating them:

1. `mtr.item.ItemNodeModifierBase#onEndClick` validates the click, checks that both ends
   are nodes of the same transport mode, and derives `facingStart` / `facingEnd` from the
   node angles and the bearing between the two nodes.
2. `mtr.item.ItemRailModifier#onConnect` resolves the rail type that is actually stored,
   builds two `mtr.data.Rail` objects, and accepts the connection only when
   `isValid()`, `goodRadius()` and the cable-car constraints all hold.

Mirroring step 2 matters for correctness, not just fidelity: continuous-movement transport
modes silently substitute `CABLE_CAR` / `CABLE_CAR_STATION`, and `RailType#railSlopeStyle`
drives the vertical sag computed in `Rail#getPositionY`. Using the item's own rail type
there would report a **wrong length** for boats and cable cars.

There is **no Mixin**: the readout hangs off Forge events only, so it cannot conflict with
MTR's own mixins. The only reflective access is for two pieces of state MTR keeps private
(`ItemRailModifier.railType` / `isOneWay` and `Rail.r1` / `r2`); if those ever change, LMEP
degrades to "no readout" rather than crashing.

---

## Building

Requires **JDK 17**.

```powershell
# The MTR jar is not committed (~76 MB). Fetch it once:
pwsh -File scripts/fetch-mtr.ps1

./gradlew build
# -> build/libs/lmep-1.0.0-hotfix-1.jar
```

`build/libs/lmep-1.0.0-hotfix-1.jar` is already reobfuscated and is the file to ship.

### Note: `runClient` is not usable for this addon

The published MTR 3.x Forge jar is **SRG-named** (verified by disassembly:
`ItemRailModifier` stores `appendHoverText` as `m_7373_`) while its class names are already
Mojang-official. SRG only renames *Minecraft's* members, never MTR's own, so LMEP compiles
and links against the unmodified release jar — which is exactly what players install.

The consequence is that MTR itself cannot run inside ForgeGradle's Mojang-mapped dev
runtime, so `runClient` will not get you a working game. Verify against a real Forge
installation instead, which is what this addon was validated against.

---

## Verification status

What was actually checked, and how:

| Check | Method | Result |
|---|---|---|
| Compiles against the real MTR release jar | `gradlew build` | ✅ |
| Reobfuscation is correct | `javap` on the shipped jar — Minecraft members must be SRG (`BlockPos.m_123341_`, `Vec3.f_82479_`, `BlockState.m_60734_`) | ✅ |
| The jar links against the unmodified MTR jar | Same disassembly — every `mtr.*` member LMEP calls must keep its original name (`Rail.getLength/getPosition/goodRadius/isValid/render`, `BlockNode.getAngle`, `ClientData.RAILS`, …) | ✅ |
| No unmapped Minecraft access left behind | Scanned the shipped jar for non-SRG `net/minecraft/**/getXxx(` references | ✅ none |
| CJK metadata survives the build | Unpacked `mods.toml` from the built jar | ✅ |
| Loads in a real game | Launched a genuine Forge 1.20.1-47.2.0 client with MTR 3.2.2-hotfix-2 + Architectury 9.2.14 + LMEP in `mods/`; it reached the main menu | ✅ |
| The declared MTR version range is accepted | Same run — Forge enforces `mandatory=true` dependencies before loading a mod, and LMEP loaded | ✅ |
| Client-side setup actually executes | That run produced `config/lmep-client.toml` containing every registered option; Forge only writes it after the mod registers its config spec | ✅ |
| The readout panel | Confirmed working in game by the author | ✅ |

Two caveats about that run, neither related to LMEP: the asset objects were only partially
downloaded, so the log is full of `NoSuchFileException` for panorama and icon files; and MTR
itself throws a `RuntimeException` out of `MTRClient.init` because Architectury rejects its
late key-bind registration (`key.mtr.debug_cycle_positive registered after event`). Neither
stops the game, and both happen with or without LMEP installed.

## Changelog

### 1.0.0-hotfix-1
* The HUD panel is no longer permanently on screen. It now appears only while a rail
  connector is held, which is the only time it has anything to say.
* The HUD panel moved from the top-right to the bottom-left corner.

### 1.0.0
* First release.

## Known limitations (v1.0)

* The readout only appears when the crosshair is on a node block, because that is the only
  case in which MTR would accept the click. Aiming at empty space shows nothing.
* MTR's "a platform or siding already exists here" rejection cannot be reproduced on the
  client: it needs `RailwayData`, which only exists server-side. LMEP may therefore report a
  valid connection that the server then refuses.
* The anchor position is read from the connector item's NBT, the same mechanism MTR's own
  "selected block" tooltip uses. It arrives via the normal server→client inventory sync, so
  it can lag by a tick.
* The accumulated line length is a bounded search (depth and visit budgets, configurable),
  so on a very large network it is a close estimate rather than an exact longest path.

## Roadmap

* Node placement preview (`placeNodeAndConnect` equivalent).
* Per-segment colouring of the accumulated line.
* Localisation beyond `en_us` / `zh_cn` / `zh_tw`.

## Credits

* **Minecraft Transit Railway** by Jonathan Ho (jonafanho) — the API surface and the rail
  maths that LMEP reuses. All rights to MTR remain with its author.
* LMEP is an independent addon. It does not modify, patch, or redistribute MTR.
