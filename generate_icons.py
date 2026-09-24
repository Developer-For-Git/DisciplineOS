import os
import math
from PIL import Image, ImageDraw, ImageFilter

def create_base_icon(size=512, is_round=False):
    # High-resolution master canvas (512x512 with 4x supersampling = 2048x2048)
    scale = 4
    dim = size * scale
    
    # 1. Dark Obsidian Canvas Base (Full bleed to prevent launcher white-matting)
    img = Image.new("RGBA", (dim, dim), (11, 14, 20, 255))
    draw = ImageDraw.Draw(img)

    # Gradient from #151A24 (top) to #0A0D12 (bottom)
    for y in range(dim):
        ratio = y / float(dim)
        r = int(21 * (1 - ratio) + 10 * ratio)
        g = int(26 * (1 - ratio) + 13 * ratio)
        b = int(36 * (1 - ratio) + 18 * ratio)
        draw.line([(0, y), (dim, y)], fill=(r, g, b, 255))

    # Center coordinates
    center_x, center_y = dim // 2, int(dim * 0.53)

    # 2. Subtle Precision Hairline Rim Light (Obsidian / Titanium finish)
    margin = int(dim * 0.03)
    bg_box = [margin, margin, dim - margin, dim - margin]
    corner_radius = int(dim * 0.22)
    stroke_color = (48, 58, 76, 180)
    stroke_width = int(dim * 0.008)
    if is_round:
        draw.ellipse(bg_box, outline=stroke_color, width=stroke_width)
    else:
        draw.rounded_rectangle(bg_box, radius=corner_radius, outline=stroke_color, width=stroke_width)

    # 3. Soft Orange Ambient Glow in Center
    glow_radius = int(dim * 0.38)
    glow = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_box = [center_x - glow_radius, center_y - glow_radius, center_x + glow_radius, center_y + glow_radius]
    glow_draw.ellipse(glow_box, fill=(255, 87, 34, 75))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=int(dim * 0.10)))
    img = Image.alpha_composite(img, glow)

    # 4. Draw the Iconic Discipline Flame Emblem
    flame_canvas = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    f_draw = ImageDraw.Draw(flame_canvas)

    # Flame coordinate helper (scale: 1000x1000 master to canvas)
    def pt(x, y):
        nx = center_x + int((x - 500) * (dim / 1000.0) * 0.60)
        ny = center_y + int((y - 500) * (dim / 1000.0) * 0.60) - int(dim * 0.02)
        return (nx, ny)

    # Outer Flame Silhouette Points
    outer_points = [
        pt(500, 150),  # Top tip
        pt(540, 260),
        pt(590, 360),
        pt(655, 450),
        pt(715, 550),
        pt(730, 650),
        pt(695, 750),
        pt(630, 825),
        pt(550, 865),
        pt(500, 875),  # Bottom center
        pt(450, 865),
        pt(370, 825),
        pt(305, 750),
        pt(270, 650),
        pt(285, 550),
        pt(345, 450),
        pt(410, 360),
        pt(460, 260),
    ]

    # Fill Outer Flame with rich gradient
    flame_mask = Image.new("L", (dim, dim), 0)
    ImageDraw.Draw(flame_mask).polygon(outer_points, fill=255)

    flame_grad = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(flame_grad)
    min_y = min(p[1] for p in outer_points)
    max_y = max(p[1] for p in outer_points)
    span = max_y - min_y
    for y in range(min_y, max_y + 1):
        ratio = (y - min_y) / float(span)
        if ratio < 0.45:
            # Amber Gold (#FFB300) to Electric Orange (#FF5722)
            sub_r = ratio / 0.45
            r = 255
            g = int(185 * (1 - sub_r) + 87 * sub_r)
            b = int(0 * (1 - sub_r) + 34 * sub_r)
        else:
            # Electric Orange (#FF5722) to Deep Crimson (#D84315)
            sub_r = (ratio - 0.45) / 0.55
            r = int(255 * (1 - sub_r) + 216 * sub_r)
            g = int(87 * (1 - sub_r) + 40 * sub_r)
            b = int(34 * (1 - sub_r) + 21 * sub_r)
        fg_draw.line([(0, y), (dim, y)], fill=(r, g, b, 255))

    flame_canvas.paste(flame_grad, (0, 0), flame_mask)

    # 5. Inner Core Flame (Brilliant White-Gold Energy)
    inner_points = [
        pt(500, 370),  # Inner tip
        pt(530, 445),
        pt(570, 530),
        pt(610, 615),
        pt(590, 715),
        pt(545, 780),
        pt(500, 805),  # Inner bottom
        pt(455, 780),
        pt(410, 715),
        pt(390, 615),
        pt(430, 530),
        pt(470, 445),
    ]

    inner_mask = Image.new("L", (dim, dim), 0)
    ImageDraw.Draw(inner_mask).polygon(inner_points, fill=255)

    inner_grad = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    ig_draw = ImageDraw.Draw(inner_grad)
    i_min_y = min(p[1] for p in inner_points)
    i_max_y = max(p[1] for p in inner_points)
    i_span = i_max_y - i_min_y
    for y in range(i_min_y, i_max_y + 1):
        ratio = (y - i_min_y) / float(i_span)
        # White (#FFFFFF) to Gold (#FFD700)
        r = 255
        g = int(255 * (1 - ratio) + 215 * ratio)
        b = int(255 * (1 - ratio) + 64 * ratio)
        ig_draw.line([(0, y), (dim, y)], fill=(r, g, b, 255))

    flame_canvas.paste(inner_grad, (0, 0), inner_mask)

    # 6. Center Discipline Diamond Core
    core_points = [
        pt(500, 470),
        pt(525, 580),
        pt(500, 690),
        pt(475, 580)
    ]
    ImageDraw.Draw(flame_canvas).polygon(core_points, fill=(255, 255, 255, 255))

    # Add soft outer glow to flame and composite
    flame_glow = flame_canvas.filter(ImageFilter.GaussianBlur(radius=int(dim * 0.025)))
    img = Image.alpha_composite(img, flame_glow)
    img = Image.alpha_composite(img, flame_canvas)

    # If is_round, apply a clean circular crop for round launcher
    if is_round:
        circle_mask = Image.new("L", (dim, dim), 0)
        ImageDraw.Draw(circle_mask).ellipse([margin, margin, dim - margin, dim - margin], fill=255)
        round_img = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
        round_img.paste(img, (0, 0), circle_mask)
        img = round_img

    # Downsample with high-quality Lanczos antialiasing
    final_icon = img.resize((size, size), Image.Resampling.LANCZOS)
    return final_icon

def create_adaptive_foreground(size=512):
    # Adaptive icon foreground has 108dp viewport with 72dp safe zone.
    # Dimensions: 512x512 with flame scaled down to fit the 66% center safe area.
    scale = 4
    dim = size * scale
    img = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    center_x, center_y = dim // 2, int(dim * 0.52)

    # Helper function scaled to 60% of original to stay well inside the 72dp safe zone
    def pt(x, y):
        nx = center_x + int((x - 500) * (dim / 1000.0) * 0.46)
        ny = center_y + int((y - 500) * (dim / 1000.0) * 0.46)
        return (nx, ny)

    flame_canvas = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))

    outer_points = [
        pt(500, 150),
        pt(540, 260),
        pt(590, 360),
        pt(655, 450),
        pt(715, 550),
        pt(730, 650),
        pt(695, 750),
        pt(630, 825),
        pt(550, 865),
        pt(500, 875),
        pt(450, 865),
        pt(370, 825),
        pt(305, 750),
        pt(270, 650),
        pt(285, 550),
        pt(345, 450),
        pt(410, 360),
        pt(460, 260),
    ]

    flame_mask = Image.new("L", (dim, dim), 0)
    ImageDraw.Draw(flame_mask).polygon(outer_points, fill=255)

    flame_grad = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(flame_grad)
    min_y = min(p[1] for p in outer_points)
    max_y = max(p[1] for p in outer_points)
    span = max_y - min_y
    for y in range(min_y, max_y + 1):
        ratio = (y - min_y) / float(span)
        if ratio < 0.45:
            sub_r = ratio / 0.45
            r = 255
            g = int(185 * (1 - sub_r) + 87 * sub_r)
            b = int(0 * (1 - sub_r) + 34 * sub_r)
        else:
            sub_r = (ratio - 0.45) / 0.55
            r = int(255 * (1 - sub_r) + 216 * sub_r)
            g = int(87 * (1 - sub_r) + 40 * sub_r)
            b = int(34 * (1 - sub_r) + 21 * sub_r)
        fg_draw.line([(0, y), (dim, y)], fill=(r, g, b, 255))

    flame_canvas.paste(flame_grad, (0, 0), flame_mask)

    inner_points = [
        pt(500, 370),
        pt(530, 445),
        pt(570, 530),
        pt(610, 615),
        pt(590, 715),
        pt(545, 780),
        pt(500, 805),
        pt(455, 780),
        pt(410, 715),
        pt(390, 615),
        pt(430, 530),
        pt(470, 445),
    ]

    inner_mask = Image.new("L", (dim, dim), 0)
    ImageDraw.Draw(inner_mask).polygon(inner_points, fill=255)

    inner_grad = Image.new("RGBA", (dim, dim), (0, 0, 0, 0))
    ig_draw = ImageDraw.Draw(inner_grad)
    i_min_y = min(p[1] for p in inner_points)
    i_max_y = max(p[1] for p in inner_points)
    i_span = i_max_y - i_min_y
    for y in range(i_min_y, i_max_y + 1):
        ratio = (y - i_min_y) / float(i_span)
        r = 255
        g = int(255 * (1 - ratio) + 215 * ratio)
        b = int(255 * (1 - ratio) + 64 * ratio)
        ig_draw.line([(0, y), (dim, y)], fill=(r, g, b, 255))

    flame_canvas.paste(inner_grad, (0, 0), inner_mask)

    core_points = [
        pt(500, 470),
        pt(525, 580),
        pt(500, 690),
        pt(475, 580)
    ]
    ImageDraw.Draw(flame_canvas).polygon(core_points, fill=(255, 255, 255, 255))

    flame_glow = flame_canvas.filter(ImageFilter.GaussianBlur(radius=int(dim * 0.03)))
    img = Image.alpha_composite(img, flame_glow)
    img = Image.alpha_composite(img, flame_canvas)

    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    base_res = "c:/Users/LOL/Desktop/justC/DisciplineOS/app/src/main/res"

    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
        "drawable": 512
    }

    # Generate standard square/squircle icons
    master_square = create_base_icon(512, is_round=False)
    master_round = create_base_icon(512, is_round=True)
    master_fg = create_adaptive_foreground(512)

    for folder, size in sizes.items():
        dir_path = os.path.join(base_res, folder)
        os.makedirs(dir_path, exist_ok=True)

        # Standard icon (Solid bleed background for 100% compatibility)
        sq = master_square.resize((size, size), Image.Resampling.LANCZOS)
        sq_path = os.path.join(dir_path, "ic_launcher.png")
        sq.save(sq_path, "PNG")

        # Round icon
        rd = master_round.resize((size, size), Image.Resampling.LANCZOS)
        rd_path = os.path.join(dir_path, "ic_launcher_round.png")
        rd.save(rd_path, "PNG")

        # Adaptive foreground PNG (DPI specific)
        fg = master_fg.resize((size, size), Image.Resampling.LANCZOS)
        fg_path = os.path.join(dir_path, "ic_launcher_foreground.png")
        fg.save(fg_path, "PNG")

        print(f"Generated {folder}: sq, rd, fg ({size}x{size})")

if __name__ == "__main__":
    main()
