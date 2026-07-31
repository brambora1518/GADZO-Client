# UI preview harness

Renders the client's real `Render2D` and `Theme` into a PNG so a restyle can be judged
before it ships, instead of after someone installs it.

This exists because of a specific failure: three consecutive rounds of visual changes were
made, built, and shipped without anyone able to see the result, and all three came back as
"looks the same". Compiling proves a change is *valid*, not that it is *visible*. The only
way to close that gap without a display is to render the same code offscreen and look at it.

## Running

```
cd tools/uipreview
./render.sh
```

Writes `blur.png` (backdrop blurred, as the shader leaves it) and `noblur.png` (the plain
dim fallback).

## What is real and what is not

Real: `Render2D`, `Theme`, `ColorUtil`, `MathUtil` — copied from `src/` at run time, so the
preview cannot drift from the shipped colours and geometry.

Stubbed: `DrawContext` blends into a `BufferedImage`; `TextRenderer` approximates Minecraft's
metrics at 6px per character; `BackgroundBlur` is a no-op, with the blur simulated by a box
blur on the backdrop instead of the GL post pass.

Mirrored by hand: the panel layout in `Preview.java` duplicates `ClickGuiScreen`'s constants.
That is the one place this can drift — if the menu's layout constants change, change them
here too or the preview stops telling the truth.
