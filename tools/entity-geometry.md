# NeroEvents entity geometry

Exact part / cube / UV tables for the three NeroEvents entities. The texture sheets under
`common/src/main/resources/assets/neroevents/textures/entity/` are painted to these layouts by
`tools/gen_event_assets.py`, so the `LayerDefinition`s must use these numbers verbatim.

Conventions (Mojang `ModelPart` space):

- Units are texels (1/16 block). `pivot` is `PartPose.offset(x, y, z)` relative to the parent; the
  root sits at the entity origin with **y = 24 at the feet** and y decreasing upwards (vanilla rig).
- `cube` is `addBox(x, y, z, w, h, d)` relative to the part's pivot; `uv` is `texOffs(u, v)`.
  Boxes use the standard Minecraft box layout (top/bottom in the first `d` rows, then
  east · north · west · south in a row of height `h`). No mirroring anywhere.
- Rotations are `PartPose.offsetAndRotation` in **degrees** here; convert to radians.
- Renderer-side animation (sway, tumble, flutter, hatch swing, leg fold) is applied to the parts
  named below at render time; the rest poses are what the tables give.
- Glow layers (`*_glow.png`) share the base sheet's layout exactly; render them as a second pass
  with the full-bright light value, tinted where the texture is white-grey.

## 1. Supply pod — `neroevents:supply_pod`

Texture `entity/supply_pod_t1.png` / `_t2` / `_t3` (64×64, picked by the synced `tier` 1–3),
glow layer `entity/supply_pod_glow.png` (64×64, beacon lens only, white-grey: tint with the
instance's severity colour). Parachute on its own sheet, section 2.

Bounding box 1.0 × 1.5 × 1.0 blocks; the beacon lens pokes 2 texels above the dome.

| Part | Parent | Pivot (x, y, z) | Cube (x, y, z, w, h, d) | texOffs (u, v) | Notes |
|---|---|---|---|---|---|
| `root` | — | 0, 24, 0 | — | — | |
| `leg_ne` | root | 5, 20, -5 | -1, 0, -1, 2, 4, 2 | 0, 42 | landing: rotate z −10° (ne/se), +10° (nw/sw) over 8 t; x likewise outward |
| `leg_nw` | root | -5, 20, -5 | -1, 0, -1, 2, 4, 2 | 0, 42 | same UV as `leg_ne` |
| `leg_se` | root | 5, 20, 5 | -1, 0, -1, 2, 4, 2 | 0, 42 | |
| `leg_sw` | root | -5, 20, 5 | -1, 0, -1, 2, 4, 2 | 0, 42 | |
| `hull` | root | 0, 20, 0 | -7, -14, -7, 14, 14, 14 | 0, 0 | occupies y 6..20 (1 block above the legs) |
| `hatch` | hull | 0, 0, -7 | -4, -8, -1, 8, 8, 1 | 40, 28 | hinge at the hull's bottom-front edge; open = rotate x **−95°** over 10 t, close over 6 t |
| `dome` | hull | 0, -14, 0 | -5, -4, -5, 10, 4, 10 | 0, 28 | sits on the hull top (y 2..6) |
| `beacon_lens` | dome | 0, -4, 0 | -2, -2, -2, 4, 2, 4 | 8, 42 | only part in the glow layer; pulses alpha 0.4→1.0 after landing |
| `line_0` | dome | 4, -4, -4 | -0.5, -14, -0.5, 1, 14, 1 | 24, 42 | shroud lines, rendered only while `landed == false` |
| `line_1` | dome | -4, -4, -4 | -0.5, -14, -0.5, 1, 14, 1 | 24, 42 | |
| `line_2` | dome | 4, -4, 4 | -0.5, -14, -0.5, 1, 14, 1 | 24, 42 | |
| `line_3` | dome | -4, -4, 4 | -0.5, -14, -0.5, 1, 14, 1 | 24, 42 | |

Sheet map (so nothing overlaps): hull 0–56 × 0–28; dome 0–40 × 28–42; hatch 40–58 × 28–37;
leg 0–8 × 42–48; lens 8–24 × 42–48; line 24–28 × 42–57.

Pod element count: 13 parts (4 legs, hull, hatch, dome, lens, 4 lines) + the canopy's 6 = 19.

## 2. Parachute — part of `supply_pod`, separate sheet

Texture `entity/supply_parachute_t1.png` / `_t2` / `_t3` (64×32). The canopy is its own
`ModelPart` tree rendered with the parachute texture; its root hangs 14 texels above the dome
(where the shroud lines end). Culled beyond 96 blocks; detaches on landing and fades over 30 t.

| Part | Parent | Pivot (x, y, z) | Rotation (x, y, z) ° | Cube (x, y, z, w, h, d) | texOffs | Notes |
|---|---|---|---|---|---|---|
| `canopy` | — | 0, -18, 0 | 0, 0, 0 | — | — | relative to the pod `dome` pivot (i.e. world y = dome − 18) |
| `gore_ne` | canopy | 0, 0, 0 | 22.5, 0, -22.5 | 0, -1, -16, 16, 1, 16 | 0, 0 | outer edges droop; flutter adds sin(age·0.35 + i)·2° on x |
| `gore_nw` | canopy | 0, 0, 0 | 22.5, 0, 22.5 | -16, -1, -16, 16, 1, 16 | 0, 0 | same UV |
| `gore_se` | canopy | 0, 0, 0 | -22.5, 0, -22.5 | 0, -1, 0, 16, 1, 16 | 0, 0 | |
| `gore_sw` | canopy | 0, 0, 0 | -22.5, 0, 22.5 | -16, -1, 0, 16, 1, 16 | 0, 0 | |
| `vent_a` | canopy | 0, 0, 0 | 0, 0, 0 | -3, -2, -3, 6, 1, 6 | 0, 17 | apex vent |
| `vent_b` | canopy | 0, 0, 0 | 0, 45, 0 | -3, -3, -3, 6, 1, 6 | 0, 17 | stacked, turned 45° |

Canopy sway: rotate `canopy` by sin(age·0.08)·6° on x and z; the pod lags by 4 ticks.
Tier stripe: the gore's 4-texel band (rows 6–9 of the gore top face) is `NERO_ALLOY` on t2, `GOLD`
on t3, absent on t1 — texture only, same geometry.

## 3. Meteor — `neroevents:meteor`

Texture `entity/meteor.png` (64×64), glow `entity/meteor_glow.png` (fissures only, rendered
full-bright, **not** tinted), heat `entity/meteor_heat.png` (16×64 gradient, see below).
Bounding box 1.2 × 1.2 × 1.2 blocks, centred on the entity position (pivot y 12, not 24).

| Part | Parent | Pivot (x, y, z) | Rotation (x, y, z) ° | Cube (x, y, z, w, h, d) | texOffs |
|---|---|---|---|---|---|
| `root` | — | 0, 12, 0 | tumble: yaw += 7°/t, pitch += 3°/t (seeded per entity) | — | — |
| `core` | root | 0, 0, 0 | 0, 0, 0 | -7, -6, -8, 14, 12, 16 | 0, 0 |
| `chunk_a` | root | 5, -3, 4 | 22.5, 0, 22.5 | -4, -4, -4, 8, 8, 8 | 0, 28 |
| `chunk_b` | root | -5, 3, -4 | 0, 45, 22.5 | -4, -4, -4, 8, 8, 8 | 32, 28 |

Sheet map: core 0–60 × 0–28; chunk_a 0–32 × 28–44; chunk_b 32–64 × 28–44.

Heat pass: a second render of `core` only, scaled 1.4 (→ 1.2 as fall speed drops) about the root,
translucent, textured with `meteor_heat.png` mapped as a plain 16-wide/64-tall quad on every face
(u 0..1, v = face v + (age·0.05 mod 1)); draw it after the base pass with depth-write off.

## 4. Debris — `neroevents:debris`

Texture `entity/debris.png` (64×64), glow `entity/debris_glow.png` (the conduit stub's two end
rings only; sputter alpha `random < 0.3 ? 1 : 0.2` per tick). Bounding box 1.5 × 0.6 × 1.0,
centred (pivot y 5).

| Part | Parent | Pivot (x, y, z) | Rotation (x, y, z) ° | Cube (x, y, z, w, h, d) | texOffs |
|---|---|---|---|---|---|
| `root` | — | 0, 5, 0 | tumble 4°/t on yaw and pitch | — | — |
| `plate_a` | root | 0, 0, 0 | 0, 0, 0 | -8, -1, -5, 16, 2, 10 | 0, 0 |
| `plate_b` | plate_a | 6, 0, 0 | 15, 0, 0 | -2, -1, -5, 12, 2, 10 | 0, 12 |
| `strut` | root | -4, 0, 3 | 0, 0, 35 | -1, -12, -1, 2, 14, 2 | 0, 24 |
| `pipe` | root | 2, -2, -4 | 0, 20, 0 | -1.5, -1.5, -5, 3, 3, 10 | 8, 24 |
| `conduit_stub` | pipe | 0, 0, -5 | 0, 0, 0 | -1, -1, -4, 2, 2, 4 | 34, 24 |

Sheet map: plate_a 0–52 × 0–12; plate_b 0–44 × 12–24; strut 0–8 × 24–40; pipe 8–34 × 24–37;
conduit 34–46 × 24–30.

## 5. Supply pod ground beacon sprite

`entity/pod_beacon_glow.png` is a 16×64 four-frame strip (frametime 3, interpolated) drawn by the
pod renderer as a camera-facing 1×1 block quad at the lens after landing, full-bright, tinted with
the severity colour, alpha 0.4→1.0 over 20 t on a loop. It is not part of the model tree.

## war_beacon BER

`neroevents:war_beacon` is one block. The blockstate has **no properties** (`blockstates/war_beacon.json`
has the single `""` variant → `block/war_beacon`, 11 elements: plinth, chamfer, collar, four feet, four
buttresses, all inside 0..16). Everything above the collar is drawn by `WarBeaconRenderer` from the
synced block entity: the column, the faction band, the emitter head, the rotating emitter ring, the
ground capture ring and the `NN%` label. `block/war_beacon_full.json` (14 elements) is the same pylon
with the three BER boxes added for the **item** model only; its numbers are the ones below, so the
item and the world render match exactly.

Units are texels of the block's local space (origin = the block's min corner, `+x` east, `+z`
south); divide by 16 for the pose stack. All five textures live in the **block atlas**
(`TextureAtlas.LOCATION_BLOCKS`), stitched because the models reference them, so the BER takes
sprites from `Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getModelManager()`
's block atlas (`getAtlas(LOCATION_BLOCKS).getSprite(id)`) and needs no atlas or model registration.
The sprite `uv` columns give the window in the 16×16 texture; map it through `sprite.getU(u/16f)`
/ `sprite.getV(v/16f)` (the ring sprite is animated, so always go through the sprite, never a raw UV).

### Static boxes (solid pass, `RenderType.solid()` or `cutout()`, world light, normal shading)

| Box | from (x, y, z) | to (x, y, z) | Faces → sprite, uv (u0, v0, u1, v1) | Notes |
|---|---|---|---|---|
| `column` | 5, 14, 5 | 11, 28, 11 | n/e/s/w → `block/war_beacon_column` (5, 1, 11, 15); up → same (0, 0, 6, 6); no down | lower 2 texels sit inside the model's collar; `up` is hidden by the head, draw it anyway (cheap) |
| `head` | 3, 28, 3 | 13, 32, 13 | n/e/s/w → `block/war_beacon_head` (0, 10, 10, 14); up → same (0, 0, 10, 10); down → `block/war_beacon_base` (3, 3, 13, 13) | the socket in the top face is painted, not geometry |

Column (5 faces) + head (6 faces) = 11 quads; nothing else is lit by world light.

### Faction band (emissive pass, full-bright `0x00F000F0`, tinted)

| Box | from | to | Faces → sprite, uv | Tint |
|---|---|---|---|---|
| `faction_band` | 4, 24, 4 | 12, 26, 12 | n/e/s/w → `block/war_beacon_band` (4, 7, 12, 9); up and down → `block/war_beacon_column` (0, 8, 8, 16) | side faces × `bandColour()`; up/down untinted |

The band texture is a white-grey mask (WHITE core row with GREY_MD ticks over a GREY_LT row), so
the tint **is** the colour. Render with `RenderType.entityTranslucentEmissive(LOCATION_BLOCKS)` or
`entityCutoutNoCull(LOCATION_BLOCKS)` at full-bright; `"shade": false` in the model terms, i.e. do
not multiply by the face normal. Because the band is a BER part it recolours the tick the BE
packet lands (no chunk re-mesh), which is also why the block model carries no `tintindex`.

```
bandColour():
  owner == 0 (NONE) → WAR  B388FF
  owner == 1        → colourA
  owner == 2        → colourB
  contested         → (gameTime / 10) % 2 == 0 ? colourA : colourB      // 10 t on / 10 t off
```
(`owner` byte as the BE syncs it; if it encodes NONE as −1 and A/B as 0/1, map accordingly.)

### Emitter ring (emissive pass, same render type as the band, tinted `bandColour()`)

Eight vertical segment plates on an octagon around the head, textured with the animated sprite
`block/war_beacon_ring` (16×96, 6 frames, frametime 2, interpolated — the strip is one segment
plate: a horizontal bar with a scan dot that walks left→right over the six frames).

| Value | Texels | Blocks |
|---|---|---|
| ring centre `C` | (8, 30, 8) | (0.5, 1.875, 0.5) |
| plate-centre radius `R` | 7.5 | 0.46875 |
| segments `N` | 8 (every 45°) | |
| half chord `h` = R·sin(22.5°) | 2.8701 | 0.17938 |
| half height `v` | 1.4351 (plate is a 2:1 bar, see UV) | 0.08969 |
| UV window | u 0..16, v 4..12 of the current frame | `sprite.getU(0)`, `getU(1)`, `sprite.getV(4/16f)`, `getV(12/16f)` |
| yaw rate | idle **2°/t**, contested **6°/t** | `yaw = (gameTime + partialTick) * rate`, keep it continuous: accumulate per BE, do not recompute from a rate that may have switched |

```
for i in 0..7:
  θ  = yaw + i·45°                                   // degrees
  cx = C.x + R·cos θ ;  cz = C.z + R·sin θ            // plate centre on the octagon
  tx = −sin θ ;  tz = cos θ                           // tangent (plate runs along it)
  nx =  cos θ ;  nz = sin θ                           // outward normal
  v0 = (cx − h·tx, C.y − v, cz − h·tz)  uv (u0, v1)   // bottom-left as seen from outside
  v1 = (cx + h·tx, C.y − v, cz + h·tz)  uv (u1, v1)
  v2 = (cx + h·tx, C.y + v, cz + h·tz)  uv (u1, v0)
  v3 = (cx − h·tx, C.y + v, cz − h·tz)  uv (u0, v0)
  emit v0 v1 v2 v3 with normal (nx, 0, nz); if the render type culls, emit v3 v2 v1 v0 as well
```
The plate top (31.4) clears the head top (32) by nothing visible from above because the head's
10×10 cap overhangs the plates by 2.5 texels: the ring reads as a halo around the head's waist.
Alpha: vertex alpha 1.0; the sprite's own alpha carries the glow falloff.

### Capture ring on the ground (translucent position-colour pass, no texture)

A flat annulus lying on the ground plane around the block, drawn with a `POSITION_COLOR`
translucent, no-cull type (`RenderType.debugFilledBox()` is the vanilla one; depth-test on,
depth-write off is what you want so grass does not z-fight it).

| Value | Blocks (block-local) |
|---|---|
| centre | (0.5, **0.1**, 0.5) — 0.1 above the block's bottom plane, i.e. above the ground block the beacon stands on |
| radius `Rg` | `captureRadius + 0.5` (the event's `capture_radius`, a Chebyshev distance; default 6 → 6.5) |
| inner / outer edge | `Rg − 0.25` / `Rg + 0.25` (band 0.5 wide) |
| segments | 24 (15° each), one quad each: inner(k), inner(k+1), outer(k+1), outer(k) |
| direction of angle φ | `dir(φ) = (sin φ, 0, −cos φ)`: φ = 0° is **north**, φ grows **clockwise seen from above** (N → E → S → W) |

```
k-th segment covers φ ∈ [15k°, 15(k+1)°]
base colour  = owner == NONE ? WAR @ α 0.25 : ownerColour @ α 0.35
sweep        = progressTicks / captureTicks  (0..1), drawn from north clockwise over sweep·360°:
               full segments k < floor(sweep·24) in contestingColour @ α 0.45,
               the partial segment k = floor(sweep·24) split at φ = sweep·360° (contesting | base),
               the rest in base colour
contested    = every segment in ((gameTime / 10) % 2 == 0 ? colourA : colourB) @ α 0.35, sweep frozen
contestingColour = the colour of the side that is not the owner (owner A → colourB, owner B → colourA);
               owner NONE → the BE has no side for the progress: use WAR unless the BE syncs a
               `progressSide` byte (recommended, 1 byte), then colourA/colourB by it
```
`captureRadius` is not among the block entity's planned synced fields (`instanceId, index, owner,
progressTicks, captureTicks, contested, colourA, colourB`); the renderer needs it, so the block
entity should sync it as one byte (default 6) — until then draw with 6.

### `NN%` label

Only while `progressTicks > 0 && !contested`: `pct = progressTicks * 100 / captureTicks`, text
`pct + "%"` (digits only — never a faction name), font colour `EventColours.TITLE` (GUI chrome
ARGB), background 0, see-through false, full-bright. Pose: translate to (0.5, **2.3**, 0.5),
multiply by `camera.rotation()`, scale (0.02, −0.02, 0.02), draw centred (`x = −font.width(text) / 2`).

### Culling (`shouldRender` / `getRenderBoundingBox`)

| Part | Max distance (blocks) |
|---|---|
| column, head, band, emitter ring | 96 |
| ground ring | 64 |
| `NN%` label | 32 |

Render bounds: `AABB(pos).inflate(captureRadius + 1, 0, captureRadius + 1).expandTowards(0, 2, 0)`
so the ground ring is not clipped when the block itself is off-screen. Always frustum-check first.

### Item

`items/war_beacon.json` → `item/war_beacon` → `block/war_beacon_full` with a constant tint
`B388FF` (= 11766015) on tint index 0 (the band); the item is the creative-only decorative
`BlockItem`. Display transforms scale the 2-block pylon to 0.3 in the GUI.

### Element / quad budget

Block model 11 elements; BER 3 boxes + 8 ring plates (16 quads if
double-sided) + 24 ground quads (+1 split) + one text draw. Emissive texels: band 4 × 8 × 2 = 64 on
a pylon of ~1 100 visible texels (≈ 6 %), plus the ring sprite — inside the 12 % budget.
