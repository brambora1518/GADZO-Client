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
| **Block entity limit** | Caps how far away chests, signs and Create machines are drawn, with a shorter limit for kinetic blocks. On a Create pack this is the single largest frame-time saving here: every shaft, cog, belt and funnel is a block entity with an animated renderer, a mid-sized factory holds hundreds of them, and vanilla applies no distance limit at all. |
| **Stutter guard** | Watches frame pacing and names the cause the first time it matters — see below. |
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

### Measuring stutter, not frame rate

Average frame rate is close to useless for diagnosing the thing people actually complain about.
A client that renders 200 frames in 900 ms and then one frame in 100 ms reports 201 FPS and
feels terrible. What you notice is the long frames.

So the client records every frame time and reports the **lows** — the mean frame rate across
the worst 1% and 0.1% of frames — on the **Smoothness** HUD element and via `/gadzo perf`.

The more useful half is attribution. Each frame the accumulated pause time reported by every
garbage collector is sampled, and a long frame that coincides with a collection is recorded as
a **GC pause** rather than as an anonymous spike. That turns "it stutters" into "it stutters
because the heap is too big", which is a problem someone can go and fix.

`/gadzo perf` prints the measurements first and the conclusions second, on purpose: the
findings are heuristics over the numbers, and showing the numbers lets you disagree with the
reasoning instead of taking it on faith.

The monitor is careful not to become the problem it measures. Per frame it does two counter
reads and a ring-buffer write; the statistics need a sort, so they are recomputed at most twice
a second.

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
Frametime graph · Smoothness · Combo counter · Target

Two worth calling out:

**Frametime graph.** An average FPS number hides the thing that actually ruins gameplay — the
occasional 80 ms frame. Plotting frame *time* makes a stutter a tall bar rather than a dip in
an already-averaged number, and the 1% low figure says the same thing numerically.

**Smoothness.** 1% and 0.1% lows, stutters in the last minute, how many of them were
collections, and how much of that minute the collector held the game still. Colour-coded by
cause rather than by count — the count tells you nothing you cannot already feel.

**Target.** Health, absorption and distance for whoever you are fighting. The target is held
for a configurable grace period after it leaves the crosshair, because in a fight the camera
rarely stays on the opponent and a panel that vanished instantly would just flicker.

### Survival

| Module | What it does |
| --- | --- |
| **Waypoints** | Mark a place and find it again. Wireframe beam plus a floating label with the distance. `N` drops one where you stand. |
| **Compass** | A heading strip with waypoint bearings marked on it. Markers behind you are pinned to the edge with an arrow rather than dropped. |
| **Death point** | Records where you died as a waypoint, automatically, and tells you how far back it is once you respawn. |
| **Light level** | Block light, sky light, and whether hostile mobs can spawn where you are standing. |
| **Food** | Hunger *and saturation* — the hidden value that decides how long you stay fed and whether you regenerate. |
| **Experience** | Level, points to the next one, and levels remaining before an enchanting table offers its best tier. |
| **Durability** | Everything worn or held that is close to breaking, sorted by what breaks first. |
| **Totems** | How many Totems of Undying you are carrying — a warning colour at zero rather than a number you have to read to interpret. |
| **Elytra** | Altitude, vertical and horizontal speed, and firework rockets left, visible only while actually gliding. |
| **Crop watch** | Growth stage of whatever you are looking at, read generically off any block's `age` property — works on modded crops the same as vanilla ones. |
| **Spawn overlay** | Draws a marker on every block around you where a hostile mob can spawn. Red means always dark; amber means the sky reaches it, so it is safe by day and dangerous after dusk. Lighting a base is otherwise guesswork — the light a torch actually reaches is not something you can see. |
| **Survival alerts** | Low health, drowning, hunger and gear warnings — fired on a threshold being *crossed*, not while it is true. |

Waypoints are stored per world, in their own file, deliberately outside the profile system:
switching from a PvP layout to a building layout should not lose the way home.

The beam is depth-tested and the label is not. A beam that punched through terrain would be a
wall-hack in everything but name; a label floating over a hill is the same information a
compass gives — a direction and a distance.

### Survival helper

Press **H**. Four tabs, three of them live rather than static text:

- **Waypoints** — the real list. Click to show or hide, right-click to delete.
- **Nether calculator** — converts the position you are standing in, and every waypoint. The
  Y axis is deliberately never divided by 8; doing so is the mistake that puts portals in the
  lava sea.
- **Food table** — every edible item in your game, built from the item registry so modded food
  appears too, ranked by saturation rather than by hunger restored.
- **Reference** — mob spawning, ore heights, enchanting, the anvil's prior-work penalty, and
  brewing. Weighted towards rules that changed and whose old versions are still repeated:
  hostile mobs have needed light level **0**, not 7, since 1.18.

### Create

Press **G** for the reference, stress planner and gear-ratio solver.

With [Create](https://modrinth.com/mod/create-fabric) installed, the client reads it directly:

**Kinetic readout** — look at any kinetic block and get its speed, the network's stress against
its capacity as a bar, network size, and one line naming the problem when there is one. This is
the Engineer's Goggles readout without the goggles, and it reads the same fields the goggles do
— Create syncs a network's stress to every client that can see one of its blocks.

**Stress alert** — warns *before* a network stalls. A network at 90% has room for nothing, and
the next press will take it down; being told after everything stops is too late.

**Stress planner** — per-block impact and capacity come out of Create's own registry, so a pack
that retunes stress values stays correct without this client knowing anything about it. With
Create absent it falls back to built-in figures (Create 6.0.8 defaults, read out of the mod).

**Gear ratios** — the thing this exists to make obvious is that Create's gearing is *binary*.
Large-to-small doubles, small-to-large halves, and everything else passes speed through
unchanged, so the only reachable ratios are powers of two. Ask for 45 RPM from 64 and the
solver says plainly that no cogwheel chain can do it and you need a Rotational Speed Controller
— rather than offering a near miss.

**Network overlay** — look at any kinetic block and the entire network it belongs to lights up.
Create gives no way to see the shape of a network, so a shaft that is not turning is either
disconnected or overstressed, and telling those apart means following the drive by eye through
walls, floors and encasing. Where the highlight ends is where the connection ends, which is the
answer to nearly every "why is this not spinning" question in the mod. The outline turns red
while the network is overstressed.

**Networks nearby** — the kinetic readout shows one block at a time; this shows the whole
factory. It walks the loaded chunks around you, groups every kinetic block it finds by the
network it actually belongs to, and lists each network's size, stress and load. Scanning every
block entity in a multi-chunk radius is real work, so it runs on a two-second timer while this
tab is open and not at all otherwise — a diagnostic tool has no business being the thing it's
diagnosing.

None of it is a hard dependency. Everything goes through reflection against Create's own class
names, which the loader does not remap, and the whole bridge latches off with a single log line
if a future Create moves something.

**The most useful fact in the reference:** gearing a network up buys no stress headroom, because
impact and capacity both scale with RPM. With one exception, which the reference also covers —
Create sums each block at *its own* speed, so gearing up only the machines and not the generator
really does raise the load.

### Commands

Client-side, so they work on any server including vanilla ones — nothing is sent over the
network.

```
/gadzo list [category]        list modules and their state
/gadzo toggle <module>        toggle by loose name match ("ent" finds "Entity culling")
/gadzo profile                show profiles
/gadzo profile save|load <n>  manage profiles
/gadzo hardware               tier, benchmark score, heap advice
/gadzo perf                   frame-time lows, stutter count, GC attribution, diagnosis
/gadzo wp                     list waypoints in this world
/gadzo wp add|del|toggle <n>  manage waypoints
/gadzo wp clear               remove every waypoint here
/gadzo nether                 convert your position across the portal
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

It is a **command palette**, not a three-column window. One narrow column: a search field that
is always focused, a flat list of every module, and — for whichever row is expanded — that
module's settings inline underneath it. Type to filter, arrows to move, enter to toggle, tab
for settings, escape to back out one layer at a time.

That shape replaced the usual category sidebar on purpose. With thirty-odd modules, picking a
category and then hunting a list is strictly more work than typing three letters, and the
sidebar spent its whole life occupying a fifth of the window to save one keystroke. Removing it
also removed two internal panel edges, which is most of what made the old layout read as a
stack of boxes rather than a single surface.

State is a **dot, not a switch**. Thirty switches in a column is a wall of controls; a dot reads
as status, and the row itself is the control.

The helper screens (**G** and **H**) share the same chrome through a common `GlassScreen` base:
header, a row of text tabs with an accent underline instead of a sidebar, a scrolling body, and
a hint line. Sections inside are separated by space and type weight, never by a border — one
sheet of glass with exactly one edge on it. The world stays visible behind a frosted blur, so
you can tune a HUD element and watch it change.

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

Requires **JDK 17** on this branch (Minecraft 1.20.1's toolchain). `main` needs JDK 25.

```bash
./gradlew build
```

The jar lands in `build/libs/gadzo-client-<version>.jar`.

To run it in a dev environment:

```bash
./gradlew runClient
```

### Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3+ for Minecraft 1.20.1 on this
   branch (26.2 on `main`).
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

**Every panel opens the same way.** A fade plus an 18-pixel slide-up, shared across the mods
menu, the HUD editor, the Create helper and the survival helper — added because three of those
four screens had no entrance animation at all while the mods menu did, which read as an
unfinished corner rather than a deliberate choice. Sidebar rows, machine-list rows and stepper
buttons animate their hover state the same way, rather than snapping instantly.

**The panels are glass, not boxes.** Every surface's fill went from ~94% opaque to 75–90%, so
the blurred world behind actually shows through the panel itself rather than only around its
edges — a nearly-opaque fill over a blurred backdrop reads as an ordinary flat menu that happens
to blur at the border, not frosted glass. Borders came out almost everywhere they were purely
decorative: the search field has no box at rest, only a thin accent underline while focused; a
module row has no background card until it's hovered or selected; a colour swatch is just the
colour. Each window keeps exactly one boundary — a single hairline on its own outer edge — so
there is still a sense of where the glass ends, without every row and control inside drawing
its own.

**Small circular controls got bigger, not smaller.** A toggle knob or a colour-field marker
drawn as a true circle at a 4–5px radius has so few scanlines to describe a curve with that no
amount of anti-aliasing math saves it from looking chunky — the fix is more pixels, not a
cleverer edge. Every such control — the mods-menu and settings-panel toggles, the slider knob,
the colour picker's position marker — is a few pixels larger now and drawn as a rounded square
or a ring instead of a filled dot, which is where most of the "pixelated" look actually came
from.

**A persisted default can never be changed again — unless you plan for it.** The corner radius
is a saved setting, so the value written on a player's first launch outlived every later change
to the default, and a restyle simply could not reach anyone who had already run the client once.
Fixing it took three parts, because the value was defended in three places: `Theme` now owns the
one `DEFAULT_RADIUS` constant, the settings module reads that constant instead of holding a
second copy of the number (its own copy of `8` was quietly overwriting the theme at startup),
and settings marked `styleOwned()` are skipped when loading a profile written under an older
`Theme.STYLE_VERSION`. Colours the player picked on purpose are untouched; the client's own
styling defaults land exactly once.

**Compiling proves a change is valid, not that it is visible.** Several rounds of UI work shipped
without anything on screen moving, for the reason above. `tools/uipreview` exists because of it:
it compiles the client's *real* `Render2D`, `Theme` and `ColorUtil` against stub Minecraft classes
and writes a PNG, so a layout can be looked at before it is shipped rather than after it is
reported as unchanged.

**A HUD element's measurement methods can be called several times a frame.** `HudModule.render`
calls `contentWidth`/`contentHeight` twice each (once directly, once through `resolveX`/
`resolveY`) before `renderContent` runs a fifth time. Elements whose content is cheap to compute
don't need to care; elements that build a list — durability, the Create readout, crop growth —
cache the result for a short, fixed window (50–100 ms) rather than recomputing on every one of
those calls. It is not a visible difference at 20 updates a second, and it is the difference
between one list rebuild a frame and five.

---

## Licence

MIT. See [LICENSE](LICENSE).

Not affiliated with Mojang, Microsoft, Lunar Client or Badlion Client.

---

## Branch: 1.20.1

This branch targets **Minecraft 1.20.1 on Fabric**, which is the last version with a native
[Create Fabric](https://modrinth.com/mod/create-fabric) build. Create runs without Sinytra
Connector here, so a live integration can talk to the mod directly rather than through a
compatibility layer. `main` stays on 26.2.

Differences forced by the older API, rather than by choice:

- **Backdrop blur works, but by a different route.** 1.20.1 has no GUI blur API. It does ship
  an unused vanilla post-effect, `shaders/post/blur.json` — a two-pass separable Gaussian — and
  the client drives it through `PostEffectProcessor` before drawing a panel. Pending GUI draws
  are flushed first, or the blur composites over them instead of under them.
- **No bottleneck verdict.** 1.20.1 has no GPU timing API, so the client cannot say whether a
  frame is CPU- or GPU-bound. That readout is absent rather than faked.
- **The Hardware element names your CPU instead.** 1.20.1 exposes `GlDebugInfo.getCpuInfo()`,
  a real processor model string, which later versions dropped.
- **No sky/celestial toggle** in Weather render — only precipitation is skipped.

Toolchain: Loom 1.7.4 (the last line that supports Yarn mappings), Gradle 8.8, Java 17.

### What this branch has that `main` does not

The Survival and Create categories — waypoints, the compass, death points, the light, food,
experience and durability readouts, the survival alerts, the survival helper screen, and the
whole live Create integration — exist **only here**. They were built against 1.20.1 because
that is where Create runs.

Porting them to 26.2 is real work rather than a copy: the world-render hook, the retained-mode
GUI and the Yarn-to-Mojang mapping change all touch these files, and the Create half would have
nothing to talk to. If you want the survival half on `main` as well, say so and it can be done
as its own pass.
