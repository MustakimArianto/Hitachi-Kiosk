#!/usr/bin/env python3
"""Builds the debug demo photos from the full-size originals (docs/ui-design/photo-guidelines.md, section 4).

Usage (needs Pillow, with WebP support; a throwaway venv is enough):

    python3 -m venv /tmp/demo-images-venv && /tmp/demo-images-venv/bin/pip install pillow
    /tmp/demo-images-venv/bin/python scripts/demo-images/make_demo_images.py Assets app/src/debug/assets/demo

`Assets/` holds the square originals (it is not committed: 44 MB). The output is what `DemoImages` in `src/debug`
serves to the fakes and the Prism mock.

Product photos: the square original sits on a 2:1 canvas (1600 x 800) at 82 % of the canvas height, so a slot of any
shape from square to 2:1 shows the whole subject and only trims margin. The margin continues the photo's own edges:
flat edge colour above and below, the edge columns stretched left and right, both blurred and faded into the photo.
Idle slides: the original, at 90 % of the canvas width, sits near the top of a 9:16 canvas (1080 x 1920), because the
full-screen slot is cropped to a portrait and the white sheet covers its bottom third (the Ken Burns zoom of 1.06 and
the side margin keep the subject clear of the screen edges). The logo is only resized.
"""

import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

# Output name -> original file name (without .png).
PRODUCTS = {
    "cheeseburger": "Gourmet Cheeseburger on Charcoal Background",
    "double_burger": "Moody Double Cheeseburger with Caramelized Onions",
    "chicken_burger": "Glazed Crispy Chicken Sandwich",
    "bbq_bacon_burger": "Gourmet Bacon BBQ Cheeseburger",
    "fried_chicken": "Crispy Golden Fried Chicken Duo",
    "wings_six": "Six Spicy Glazed Chicken Wings",
    "wings_three": "Spicy Crispy Chicken Wings on Black Plate",
    "nuggets_nine": "Crispy Golden Chicken Nuggets on Black Plate",
    "nuggets_four": "Golden Crispy Nuggets on Black Plate",
    "sambal_bowl": "Urban Grill Sambal Chicken Bowl",
    "teriyaki_bowl": "Urban Grill Teriyaki Chicken Bowl",
    "fries": "Urban Grill Golden Fries",
    "cheese_fries": "Cheesy Urban Grill Loaded Fries",
    "lychee_tea": "Iced Lychee Mint Tea on Ice",
    "cola": "Crisp Cola with Ice and Bubbles",
    "iced_latte": "Iced Latte in Glass",
    "water": "Sculpted Clear Water Bottle",
    "caramel_sundae": "Urban Grill Caramel Sundae",
    "chocolate_sundae": "Urban Grill Chocolate Swirl Sundae",
    "soft_serve": "Urban Grill Matcha Soft-Serve Cup",
}
# Idle slide name -> product it is made from.
IDLE = {"idle_burger": "double_burger", "idle_wings": "wings_six", "idle_sundae": "caramel_sundae"}
# The "Make it a meal" option adds fries and a drink: its photo shows those two side by side.
MEAL_ADDON = ("meal_addon", ("fries", "cola"))
LOGO = ("logo", "Urban Grill Burger Emblem")

CANVAS_W, CANVAS_H, SUBJECT_FILL = 1600, 800, 0.82
IDLE_W, IDLE_H, IDLE_TOP, IDLE_FILL = 1080, 1920, 150, 0.9
LOGO_SIZE = 512
WEBP_QUALITY = 82


def feather(w: int, h: int, fade: int, horizontal: bool) -> Image.Image:
    """An opacity mask that is 0 at the two edges and 1 after `fade` pixels."""
    mask = Image.new("L", (w, h), 255)
    px = mask.load()
    for i in range(fade):
        value = int(255 * i / fade)
        for j in range(h if horizontal else w):
            if horizontal:
                px[i, j] = value
                px[w - 1 - i, j] = value
            else:
                px[j, i] = value
                px[j, h - 1 - i] = value
    return mask


def mean_colour(strip: Image.Image):
    return strip.resize((1, 1), Image.BOX).getpixel((0, 0))


def extend_horizontal(img: Image.Image, width: int) -> Image.Image:
    w, h = img.size
    pad = (width - w) // 2
    left = img.crop((0, 0, 6, h)).resize((pad + 40, h), Image.BILINEAR)
    right = img.crop((w - 6, 0, w, h)).resize((width - pad - w + 40, h), Image.BILINEAR)
    canvas = Image.new("RGB", (width, h))
    canvas.paste(left, (-40, 0))
    canvas.paste(right, (pad + w, 0))
    canvas.paste(img, (pad, 0))
    canvas = canvas.filter(ImageFilter.GaussianBlur(28))
    canvas.paste(img, (pad, 0), feather(w, h, 70, True))
    return canvas


def extend_vertical(img: Image.Image, height: int, top: int) -> Image.Image:
    w, h = img.size
    canvas = Image.new("RGB", (w, height))
    canvas.paste(Image.new("RGB", (w, top + 40), mean_colour(img.crop((0, 0, w, 24)))), (0, -40))
    canvas.paste(Image.new("RGB", (w, height - top - h + 40), mean_colour(img.crop((0, h - 24, w, h)))), (0, top + h))
    canvas.paste(img, (0, top))
    canvas = canvas.filter(ImageFilter.GaussianBlur(40))
    canvas.paste(img, (0, top), feather(w, h, 120, False))
    return canvas


def product_photo(original: Image.Image) -> Image.Image:
    side = int(CANVAS_H * SUBJECT_FILL)
    column = extend_vertical(original.resize((side, side), Image.LANCZOS), CANVAS_H, (CANVAS_H - side) // 2)
    return extend_horizontal(column, CANVAS_W)


def pair_original(left: Image.Image, right: Image.Image) -> Image.Image:
    """Two photos with the same dark studio background side by side on one square. Each fades to black at its edges and
    the two are merged by keeping the lighter pixel, so no rectangle shows."""
    size = left.width
    scaled = int(size * 0.7)
    fade = scaled // 4
    mask = ImageChops.multiply(feather(scaled, scaled, fade, True), feather(scaled, scaled, fade, False))
    black = Image.new("RGB", (scaled, scaled))
    canvas = Image.new("RGB", (size, size))
    for img, centre in ((left, 0.3), (right, 0.7)):
        layer = Image.new("RGB", (size, size))
        faded = Image.composite(img.resize((scaled, scaled), Image.LANCZOS), black, mask)
        layer.paste(faded, (int(size * centre) - scaled // 2, (size - scaled) // 2))
        canvas = ImageChops.lighter(canvas, layer)
    return canvas


def idle_slide(original: Image.Image) -> Image.Image:
    side = int(IDLE_W * IDLE_FILL)
    column = extend_vertical(original.resize((side, side), Image.LANCZOS), IDLE_H, IDLE_TOP)
    return extend_horizontal(column, IDLE_W)


def save(img: Image.Image, path: Path) -> None:
    img.save(path, "WEBP", quality=WEBP_QUALITY, method=6)


def main(source: Path, target: Path) -> None:
    target.mkdir(parents=True, exist_ok=True)
    originals = {name: Image.open(source / f"{file}.png").convert("RGB") for name, file in PRODUCTS.items()}
    for name, original in originals.items():
        save(product_photo(original), target / f"{name}.webp")
    for name, base in IDLE.items():
        save(idle_slide(originals[base]), target / f"{name}.webp")
    name, (left, right) = MEAL_ADDON
    save(product_photo(pair_original(originals[left], originals[right])), target / f"{name}.webp")
    name, file = LOGO
    logo = Image.open(source / f"{file}.png").convert("RGB").resize((LOGO_SIZE, LOGO_SIZE), Image.LANCZOS)
    save(logo, target / f"{name}.webp")
    print(f"wrote {len(PRODUCTS) + len(IDLE) + 2} images to {target}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
