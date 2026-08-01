#!/usr/bin/env bash
# Renders the mods menu offscreen using the client's real Render2D and Theme.
set -e
cd "$(dirname "$0")"
SRC=../../src/main/java/com/gadzo/client
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT

mkdir -p "$BUILD/com/gadzo/client/ui" "$BUILD/com/gadzo/client/util"
cp "$SRC/ui/Render2D.java" "$SRC/ui/Theme.java" "$BUILD/com/gadzo/client/ui/"
cp "$SRC/util/ColorUtil.java" "$SRC/util/MathUtil.java" "$BUILD/com/gadzo/client/util/"
cp stub/com/gadzo/client/ui/BackgroundBlur.java "$BUILD/com/gadzo/client/ui/"
cp -r net "$BUILD/"
cp Preview.java HelperPreview.java "$BUILD/"

javac -nowarn -d "$BUILD/out" $(find "$BUILD" -name '*.java')
java -cp "$BUILD/out" Preview blur "$(pwd)/blur.png"
java -cp "$BUILD/out" Preview noblur "$(pwd)/noblur.png"
java -cp "$BUILD/out" HelperPreview blur "$(pwd)/helper.png"
echo "wrote blur.png, noblur.png and helper.png"
