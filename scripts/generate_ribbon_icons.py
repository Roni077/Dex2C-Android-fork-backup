#!/usr/bin/env python3
import os
import shutil
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res")
DOCS_ICON_DIR = os.path.join(PROJECT_ROOT, "docs", "icon")

LIGHT_RAW = os.path.join(DOCS_ICON_DIR, "Gemini_Generated_Image_gv4yazgv4yazgv4y.png")
DARK_RAW = os.path.join(DOCS_ICON_DIR, "Gemini_Generated_Image_kz4bwjkz4bwjkz4b.png")

def make_squircle_mask(size, radius_ratio=0.22):
    # Oversample 4x for high quality anti-aliasing
    os_size = size * 4
    mask = Image.new("L", (os_size, os_size), 0)
    draw = ImageDraw.Draw(mask)
    radius = int(os_size * radius_ratio)
    draw.rounded_rectangle([0, 0, os_size - 1, os_size - 1], radius=radius, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)

def make_round_mask(size):
    os_size = size * 4
    mask = Image.new("L", (os_size, os_size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, os_size - 1, os_size - 1], fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)

def extract_badge(raw_img_path, target_size=1024):
    img = Image.open(raw_img_path).convert("RGBA")
    # Squircle bounding box: [204, 204, 1843, 1842] (size 1639x1638)
    box = (204, 204, 1843, 1842)
    cropped = img.crop(box).resize((target_size, target_size), Image.Resampling.LANCZOS)
    
    mask = make_squircle_mask(target_size, radius_ratio=0.215)
    badge = cropped.copy()
    badge.putalpha(mask)
    return badge

def extract_glyph(badge_1024):
    # Extract ribbon glyph centered on a 108dp adaptive canvas
    # Target canvas size: 512x512
    # In 108dp canvas, safe zone is 72dp (ratio ~ 66.6%)
    # At 512x512, 72dp/108dp is ~ 341px.
    arr = np.array(badge_1024)
    # Bright ribbon is mint and white; background is dark
    diff = arr[:, :, 1].astype(float) - 60
    # Threshold with smooth transition
    mask = np.clip((diff - 20) / 40.0, 0, 1)
    
    glyph_raw = np.zeros((1024, 1024, 4), dtype=np.uint8)
    glyph_raw[:, :, :3] = arr[:, :, :3]
    glyph_raw[:, :, 3] = (mask * 255).astype(np.uint8)
    
    # Crop to ribbon bounds
    ribbon_pixels = np.argwhere(glyph_raw[:, :, 3] > 60)
    y0, x0 = ribbon_pixels.min(axis=0)
    y1, x1 = ribbon_pixels.max(axis=0)
    
    # Extra 4px padding
    y0 = max(0, y0 - 4)
    x0 = max(0, x0 - 4)
    y1 = min(1023, y1 + 4)
    x1 = min(1023, x1 + 4)
    
    cropped_glyph = Image.fromarray(glyph_raw).crop((x0, y0, x1, y1))
    
    # Scale cropped glyph into the 72dp safe zone of a 512x512 canvas
    # Max safe dimension ~ 310px
    target_safe = 310
    scale = min(target_safe / cropped_glyph.width, target_safe / cropped_glyph.height)
    new_w = int(cropped_glyph.width * scale)
    new_h = int(cropped_glyph.height * scale)
    scaled_glyph = cropped_glyph.resize((new_w, new_h), Image.Resampling.LANCZOS)
    
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    offset_x = (512 - new_w) // 2
    offset_y = (512 - new_h) // 2
    canvas.paste(scaled_glyph, (offset_x, offset_y), scaled_glyph)
    return canvas

def main():
    print("Processing Light and Dark source images...")
    badge_light = extract_badge(LIGHT_RAW, 1024)
    badge_dark = extract_badge(DARK_RAW, 1024)
    
    # Save source / preview drawables
    drawable_dir = os.path.join(RES_DIR, "drawable")
    os.makedirs(drawable_dir, exist_ok=True)
    
    print("Saving high-resolution drawables...")
    badge_light.save(os.path.join(drawable_dir, "ic_dex2c_light_src.png"), optimize=True)
    badge_dark.save(os.path.join(drawable_dir, "ic_dex2c_dark_src.png"), optimize=True)
    # Default flagship icon drawable
    badge_dark.save(os.path.join(drawable_dir, "ic_dex2c_icon_src.png"), optimize=True)
    
    # Extract foreground glyphs for adaptive icons
    print("Extracting adaptive icon foregrounds...")
    fg_light = extract_glyph(badge_light)
    fg_dark = extract_glyph(badge_dark)
    fg_light.save(os.path.join(drawable_dir, "ic_launcher_foreground_light.png"), optimize=True)
    fg_dark.save(os.path.join(drawable_dir, "ic_launcher_foreground_dark.png"), optimize=True)
    
    # Density buckets for mipmaps
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    
    print("Generating mipmap densities (square & round)...")
    for folder, size in densities.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        round_mask = make_round_mask(size)
        
        # 1. Dark icon (default)
        dark_sq = badge_dark.resize((size, size), Image.Resampling.LANCZOS)
        dark_sq.save(os.path.join(folder_path, "ic_launcher_dark.png"), optimize=True)
        
        dark_rd = dark_sq.copy()
        dark_rd.putalpha(round_mask)
        dark_rd.save(os.path.join(folder_path, "ic_launcher_dark_round.png"), optimize=True)
        
        # 2. Light icon
        light_sq = badge_light.resize((size, size), Image.Resampling.LANCZOS)
        light_sq.save(os.path.join(folder_path, "ic_launcher_light.png"), optimize=True)
        
        light_rd = light_sq.copy()
        light_rd.putalpha(round_mask)
        light_rd.save(os.path.join(folder_path, "ic_launcher_light_round.png"), optimize=True)
        
        # 3. Base icon (uses dark by default for standard mipmap)
        dark_sq.save(os.path.join(folder_path, "ic_launcher.png"), optimize=True)
        dark_rd.save(os.path.join(folder_path, "ic_launcher_round.png"), optimize=True)
        
        # Clean up legacy mipmap PNGs in this bucket
        for legacy_prefix in ["ic_launcher_classic", "ic_launcher_cursive", "ic_launcher_monogram", "ic_launcher_shodo"]:
            for suffix in [".png", "_round.png"]:
                legacy_file = os.path.join(folder_path, legacy_prefix + suffix)
                if os.path.exists(legacy_file):
                    os.remove(legacy_file)
                    print(f"Removed legacy: {legacy_file}")
                    
    print("Deleting legacy drawables and mipmap XMLs...")
    legacy_drawables = [
        "ic_dex2c_classic_src.png",
        "ic_dex2c_cursive_src.png",
        "ic_dex2c_monogram_src.png",
        "ic_dex2c_shodo_src.png",
        "ic_launcher_foreground_classic.xml",
        "ic_launcher_foreground_cursive.xml",
        "ic_launcher_foreground_monogram.xml",
        "ic_launcher_foreground_shodo.xml",
        "ic_launcher_monochrome_classic.xml",
        "ic_launcher_monochrome_cursive.xml",
        "ic_launcher_monochrome_monogram.xml",
        "ic_launcher_monochrome_shodo.xml",
    ]
    for ld in legacy_drawables:
        f = os.path.join(drawable_dir, ld)
        if os.path.exists(f):
            os.remove(f)
            print(f"Removed legacy drawable: {ld}")
            
    anydpi_dir = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    legacy_anydpi = [
        "ic_launcher_classic.xml",
        "ic_launcher_classic_round.xml",
        "ic_launcher_cursive.xml",
        "ic_launcher_cursive_round.xml",
        "ic_launcher_monogram.xml",
        "ic_launcher_monochrome_monogram.xml",
        "ic_launcher_monogram_round.xml",
        "ic_launcher_shodo.xml",
        "ic_launcher_shodo_round.xml",
    ]
    for la in legacy_anydpi:
        f = os.path.join(anydpi_dir, la)
        if os.path.exists(f):
            os.remove(f)
            print(f"Removed legacy anydpi: {la}")
            
    print("Asset pipeline completed successfully!")

if __name__ == "__main__":
    main()
