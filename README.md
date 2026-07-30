# GADZO Client

A custom Minecraft client built as a Fabric mod for **Minecraft 26.2** — a modern interface,
a drag-and-drop HUD editor, and the optimisation controls people install Lunar or Badlion for,
in a codebase you can actually read and change.

Client-side only. Nothing here touches the server, sends packets it shouldn't, or gives an
unfair advantage: every module is a readout, a render setting, or a UI convenience.

---

## What's in it

### Performance

| Module | What it does |
| --- | --- |
| **Render tuning** | Render/simulation distance, entity distance, biome blend, mipmaps, particles, clouds, AO, vignette, entity shadows and view bobbing — in one panel, with `Quality` → `Extreme` presets. |
| **Dynamic FPS** | Drops the frame cap while the window is unfocused, and optionally while a menu is open. Restores your own limit on disable. |
| **Entity culling** | Stops drawing entities past a configurable distance, with separate limits for dropped items, item frames and armour stands. Players are never culled. |
| **Particle limiter** | Budgets particle spawns per tick, so one explosion can't spike the frame time. |
| **Screen effects** | Scales down nausea, portal warp, darkness pulse and glint animation — a performance win and a motion-comfort control. |

### HUD

FPS · CPS · Ping · Coordinates · Memory · Clock · Keystrokes · Armour · Potions · Movement
state · Combo counter

Every element supports anchoring, pixel offsets, scaling, a background plate, an optional
border and a text-shadow toggle — and each has its own settings on top of that (the FPS
readout colour-codes itself, the armour display shows durability bars, keystrokes can show
live CPS on the mouse keys, and so on).

### Visual

**Fullbright** — raises brightness past the vanilla ceiling by writing the gamma option, so it
composes correctly with shaders.
**Zoom** — hold-to-zoom with configurable level, easing and sensitivity scaling.

---

## The HUD editor

Press **Right Ctrl** in game.

- Drag any element to move it.
- It snaps to screen edges, screen centre lines, and the edges of other elements — with a
  guide line drawn for whichever snap is active.
- Arrow keys nudge by 1px, Shift+arrows by 10px.
- `R` resets the element under the cursor.
- On release the element re-anchors to its nearest corner, which is what keeps a layout
  correct when you change resolution or GUI scale.

Positions are stored as *anchor + offset*, never as absolute pixels.

## The mods menu

Press **Right Shift** in game.

Category sidebar, fuzzy search across names and descriptions, live toggles, and a settings
panel with animated switches, sliders, dropdowns, colour swatches and keybind capture. The
world stays visible behind a frosted-glass blur, so you can tune a HUD element and watch it
change.

Themes: Dark, Midnight and Light, with a static / gradient / rainbow accent, adjustable corner
radius and an optional blur toggle.

---

## Building

Requires **JDK 25** (Minecraft 26.2's toolchain).

```bash
./gradlew build
```

The jar lands in `build/libs/gadzo-client-<version>.jar`.

To run it in a dev environment:

```bash
./gradlew runClient
```

### Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3+ for Minecraft 26.2.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
3. Drop `gadzo-client-<version>.jar` into `mods/`.

---

## How it's put together

```
com.gadzo.client
├── core/
│   ├── setting/    Boolean, Number, Enum, Colour and Keybind settings with JSON round-trip
│   ├── module/     Module base class, categories, registry and tick driver
│   ├── hud/        Anchored, draggable HUD element base + the render pass
│   ├── input/      CPS and combo tracking, fed from input callbacks rather than ticks
│   └── config/     Profile load/save (atomic writes, forward-compatible)
├── modules/        The actual features, grouped by category
├── ui/
│   ├── Render2D    Anti-aliased rounded rectangles, gradients, shadows, scissors
│   ├── Theme       Elevation-based palette and accent engine
│   ├── screen/     Mods menu, HUD editor, setting widgets
│   └── notify/     Toast stack
└── mixin/          Six small, targeted injections
```

A few decisions worth knowing about if you're going to work on this:

**Rounded corners are rasterised on the CPU.** Minecraft 26 moved the GUI to a retained-mode
system (`GuiGraphicsExtractor`) that gives you rectangles, vertical gradients and text — but no
rounded shapes. `Render2D` draws them as horizontal spans, one fill per scanline through the
corner bands, with a partial-alpha pixel at each span end whose coverage comes from the circle
equation. That's where the anti-aliasing comes from. A radius-8 corner costs about 48 fills,
which is nothing for a menu.

**Animations are wall-clock driven.** `Animation` uses `System.nanoTime()`, not tick counts, so
everything runs at the same speed at 30 fps and at 240, and keeps running while the game is
paused. Retargeting mid-flight restarts the curve from the current value so direction changes
don't snap.

**CPS and combos come from input callbacks**, not from polling on tick — a 20 Hz sample would
cap CPS at 20 and hide exactly the bursts the counter exists to show.

**`Minecraft.getInstance()` is never cached in a static field.** Fabric runs client entrypoints
from inside the `Minecraft` constructor, so a field initialised at class-load time can capture
`null`.

**Configs are written atomically** — to a temp file, then moved into place — so a crash
mid-save leaves the previous profile intact rather than a truncated one. Missing keys fall back
to defaults, so a config from an older build still loads after new modules are added.

---

## Licence

MIT. See [LICENSE](LICENSE).

Not affiliated with Mojang, Microsoft, Lunar Client or Badlion Client.
