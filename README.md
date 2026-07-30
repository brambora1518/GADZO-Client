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
| **Weather render** | Skips drawing rain, snow and optionally the sun/moon/stars. Precipitation is a large pile of camera-facing quads every frame and is one of the few effects that reliably halves the frame rate on older hardware during a storm. The weather itself is unaffected — sky still darkens, mobs still spawn. |

Render tuning ships an **Auto-detect** preset that measures the machine rather than looking up
a GPU model name in a table that would be wrong for anything it hadn't seen.

The dominant input is a short **single-thread CPU benchmark** run once at startup on a
background thread. Core count is close to useless on its own here: Minecraft's render and tick
loops are dominated by one thread, so an eight-thread CPU from 2012 and an eight-thread CPU
from today behave nothing alike, and ranking them the same produces a render distance that
tanks the older machine. GPU class, VRAM and maximum texture size contribute, but secondarily.

**Heap size is deliberately not part of the score.** It is a launcher setting the player chose,
not a property of the hardware, and a large value is as often a misconfiguration as a sign of a
capable system.

### Knowing where your frames go

The **Hardware** HUD element reports GPU load and a verdict: *CPU bound*, *GPU bound* or
*balanced*.

This matters more than it sounds, because most Minecraft tuning advice is aimed at the wrong
half of the pipeline. Vanilla Minecraft is overwhelmingly limited by chunk meshing, entity
handling and draw-call submission — all CPU-side — long before it saturates a discrete GPU's
shader units. If you are CPU bound, turning texture quality down does nothing; render
distance, simulation distance, entity range and particle count are the levers. If you are GPU
bound (usually because of shaders or a high resolution), the opposite holds.

Two consequences baked into the defaults:

- **No preset reduces mipmaps.** Dropping mipmap levels is a habit from cards with under a
  gigabyte of VRAM. On anything modern it *costs* frames, because unmipmapped distant terrain
  thrashes the texture cache. Every preset leaves it at 4.
- **The aggressive presets cut CPU-side settings first** — render distance, simulation
  distance, biome blend — rather than visual fidelity.

GPU load is off by default. The game only measures it while its own `GPU_UTILIZATION` debug
entry is active, so reading the figure means switching that entry on; the module does so only
when you ask, and restores your previous setting when you turn it back off.

### Heap advisory

On startup the client checks the heap allocation and warns if it looks wrong — too large
relative to system RAM, over ~6 GB, or under ~1.5 GB.

Over-allocating is the most common self-inflicted Minecraft performance problem. Vanilla at a
normal render distance is comfortable in 2–4 GB. Past that the garbage collector is not doing
less work, it is doing it in bigger batches: collections become rarer but each one takes
longer, which is felt as periodic stutter rather than a lower average frame rate. A very large
heap also squeezes the OS page cache, which is what makes chunk loading feel sluggish.

### HUD

FPS · CPS · Ping · Coordinates · Memory · Clock · Keystrokes · Armour · Potions · Movement
state · Speed · World info (biome / day / server) · Player stats · Hardware · Session ·
Frametime graph · Combo counter · Target

Two worth calling out:

**Frametime graph.** An average FPS number hides the thing that actually ruins gameplay — the
occasional 80 ms frame. Plotting frame *time* makes a stutter a tall bar rather than a dip in
an already-averaged number, and the 1% low figure says the same thing numerically.

**Target.** Health, absorption and distance for whoever you are fighting. The target is held
for a configurable grace period after it leaves the crosshair, because in a fight the camera
rarely stays on the opponent and a panel that vanished instantly would just flicker.

### Create helper

Press **G**. A reference and stress planner for the [Create](https://modrinth.com/mod/create)
mod.

**Create has no build for Minecraft 26.2** — the Forge/NeoForge project stops at 1.21.1 and the
Fabric port at 1.20.1 — so this cannot read a live kinetic network. It is a planning tool, and
the stress calculator is the part that earns its keep: pick your machines, and it tells you the
per-RPM load and how many water wheels, windmills or steam engines cover it. That is otherwise
a spreadsheet job.

The reference is weighted towards things that do not change between Create versions: how stress
arithmetic works, gear ratios, belt rules, and the order to diagnose a stopped contraption in.
Numeric impact values drift between releases, so they are marked as 0.5.x figures and every
entry that leans on them points at the Engineer's Goggles, which are always authoritative for
the build in front of you.

The single most valuable thing in it is also the simplest: **speed does not buy stress
headroom.** Impact and capacity are both per-RPM, so running a network faster raises
consumption and capacity by exactly the same factor. If you are overstressed, you need more
generators or fewer machines — gearing up changes nothing.

### Commands

Client-side, so they work on any server including vanilla ones — nothing is sent over the
network.

```
/gadzo list [category]        list modules and their state
/gadzo toggle <module>        toggle by loose name match ("ent" finds "Entity culling")
/gadzo profile                show profiles
/gadzo profile save|load <n>  manage profiles
/gadzo hardware               tier, benchmark score, bottleneck, heap advice
/gadzo save                   write the active profile
```

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
radius and an optional blur toggle. Colour settings open a real HSV picker — a
saturation/value field with hue and alpha sliders, the alpha track drawn over a checkerboard
so transparency is legible.

The picker keeps HSV as its editing state rather than re-deriving it from the packed colour
each frame: round-tripping through RGB loses hue whenever saturation or value hits zero, which
would make the hue slider jump around while dragging in the black and white corners.

The title screen carries a small GADZO panel. It is drawn as an overlay through Fabric's
screen events rather than by replacing `TitleScreen` — substituting the whole screen would
mean re-implementing world loading, realms and the accessibility onboarding for a purely
cosmetic gain, and would be a new way to break the path into a world every version.

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
