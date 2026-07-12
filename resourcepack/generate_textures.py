import struct, zlib, os

TEXTURES_DIR = "assets/minecraft/textures/item"
os.makedirs(TEXTURES_DIR, exist_ok=True)

def create_png(width, height, pixels):
    def chunk(chunk_type, data):
        c = chunk_type + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    
    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    
    raw = b""
    for row in pixels:
        raw += b"\x00"
        for r, g, b, a in row:
            raw += struct.pack("BBBB", r, g, b, a)
    
    idat = chunk(b"IDAT", zlib.compress(raw))
    iend = chunk(b"IEND", b"")
    return header + ihdr + idat + iend

def draw_star(cx, cy, outer_r, inner_r, color, bg=(0,0,0,0), thickness=1):
    import math
    pixels = [[bg for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            angle = math.atan2(dy, dx)
            dist = math.sqrt(dx*dx + dy*dy)
            # 5-pointed star
            star_angle = (angle + math.pi) % (2 * math.pi / 5)
            star_angle_deg = math.degrees(star_angle) % 72
            if star_angle_deg < 36:
                r = outer_r
            else:
                r = inner_r
            if dist <= r and dist >= r - thickness:
                pixels[y][x] = color
            elif dist < r - 1:
                # fill inner slightly lighter
                pixels[y][x] = (*color[:3], min(255, color[3] - 40) if color[3] > 40 else color[3])
    return pixels

def draw_diamond(cx, cy, size, color, bg=(0,0,0,0)):
    pixels = [[bg for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx), abs(y - cy)
            if dx + dy <= size:
                border = dx + dy >= size - 2
                if border:
                    pixels[y][x] = color
                else:
                    pixels[y][x] = (*color[:3], min(255, color[3] + 30))
    return pixels

def draw_circle(cx, cy, r, color, bg=(0,0,0,0)):
    pixels = [[bg for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            dist = (dx*dx + dy*dy) ** 0.5
            if r - 1.5 <= dist <= r:
                pixels[y][x] = color
            elif dist < r - 1.5:
                pixels[y][x] = (*color[:3], min(255, color[3] + 20))
    return pixels

def draw_rune_symbol(cx, cy, color, symbol_type, bg=(0,0,0,0)):
    icx, icy = int(cx), int(cy)
    pixels = [[bg for _ in range(16)] for _ in range(16)]
    # Circle border
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            dist = (dx*dx + dy*dy) ** 0.5
            if 5.5 <= dist <= 7:
                pixels[y][x] = color
    
    # Symbol inside based on type
    if symbol_type == "fire":
        for y in range(5, 12):
            pixels[y][icx] = color
        for dx in [-1, 1]:
            pixels[5][icx+dx] = color
            pixels[4][icx] = color
        pixels[3][icx] = (*color[:3], 200)
    elif symbol_type == "water":
        for x in range(5, 12):
            y_off = int(1.5 * ((x - 5) % 4) - 1)
            if 0 <= icy + y_off < 16:
                pixels[icy + y_off][x] = color
    elif symbol_type == "earth":
        for i in range(-3, 4):
            if 0 <= icy + i < 16:
                pixels[icy + i][icx] = color
            if 0 <= icx + i < 16:
                pixels[icy][icx + i] = color
    elif symbol_type == "air":
        for i in range(-3, 4):
            if 0 <= icy + i < 16 and 0 <= icx + i < 16:
                pixels[icy + i][icx + i] = color
            if 0 <= icy - i < 16 and 0 <= icx + i < 16:
                pixels[icy - i][icx + i] = color
    elif symbol_type == "void":
        for y in range(16):
            for x in range(16):
                dx, dy = x - cx, y - cy
                dist = (dx*dx + dy*dy) ** 0.5
                if 1.5 <= dist <= 2.5:
                    pixels[y][x] = color
        pixels[icy][icx] = color
    
    return pixels

def draw_nation_crest(color1, color2, pattern, bg=(0,0,0,0)):
    pixels = [[bg for _ in range(16)] for _ in range(16)]
    # Shield shape
    for y in range(2, 14):
        for x in range(3, 13):
            # Shield outline
            if y == 2 or y == 13 or x == 3 or x == 12:
                pixels[y][x] = color1
            elif y == 3 and (x >= 4 and x <= 11):
                pixels[y][x] = color1
            elif y >= 4 and y <= 11:
                if pattern == "cross":
                    if x == 7 or x == 8 or y == 7 or y == 8:
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
                elif pattern == "diagonal":
                    if abs(y - 7) + abs(x - 7) <= 4:
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
                elif pattern == "stripe":
                    if y % 2 == 0:
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
                elif pattern == "dot":
                    if (y == 7 or y == 8) and (x == 7 or x == 8):
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
                elif pattern == "chevron":
                    if y - 4 <= abs(x - 7):
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
                elif pattern == "star":
                    if abs(y-7) + abs(x-7) <= 2:
                        pixels[y][x] = color2
                    else:
                        pixels[y][x] = color1
    # Bottom point
    for x in range(5, 11):
        pixels[12][x] = color1
    pixels[13][6] = color1
    pixels[13][9] = color1
    return pixels

# === ARTIFACT TEXTURES (stars) ===
artifacts = {
    "artifact_common":  {"color": (200, 200, 210, 255), "name": "Common"},
    "artifact_rare":    {"color": (70, 130, 230, 255), "name": "Rare"},
    "artifact_epic":    {"color": (170, 60, 230, 255), "name": "Epic"},
    "artifact_legendary": {"color": (240, 190, 40, 255), "name": "Legendary"},
    "artifact_mythic":  {"color": (220, 40, 40, 255), "name": "Mythic"},
}

for name, info in artifacts.items():
    pixels = draw_star(7.5, 7.5, 6.5, 3.5, info["color"])
    with open(os.path.join(TEXTURES_DIR, f"{name}.png"), "wb") as f:
        f.write(create_png(16, 16, pixels))
    print(f"  Created {name}.png ({info['name']})")

# === RUNE TEXTURES (circles with symbols) ===
runes = {
    "rune_fire":   {"color": (230, 100, 30, 255), "symbol": "fire"},
    "rune_water":  {"color": (40, 140, 220, 255), "symbol": "water"},
    "rune_earth":  {"color": (120, 160, 50, 255), "symbol": "earth"},
    "rune_air":    {"color": (180, 190, 200, 255), "symbol": "air"},
    "rune_void":   {"color": (100, 30, 140, 255), "symbol": "void"},
}

for name, info in runes.items():
    pixels = draw_rune_symbol(7.5, 7.5, info["color"], info["symbol"])
    with open(os.path.join(TEXTURES_DIR, f"{name}.png"), "wb") as f:
        f.write(create_png(16, 16, pixels))
    print(f"  Created {name}.png ({info['symbol']})")

# === NATION TEXTURES (shields/crests) ===
nations = {
    "nation_soyuz":  {"c1": (180, 30, 30, 255), "c2": (240, 200, 50, 255), "pattern": "cross", "name": "Союз"},
    "nation_cheka":  {"c1": (60, 20, 20, 255), "c2": (180, 30, 30, 255), "pattern": "diagonal", "name": "Чека"},
    "nation_veduny": {"c1": (40, 90, 40, 255), "c2": (140, 110, 60, 255), "pattern": "stripe", "name": "Ведуны"},
    "nation_nav":    {"c1": (60, 30, 80, 255), "c2": (120, 40, 120, 255), "pattern": "dot", "name": "Навь"},
    "nation_rus":    {"c1": (30, 70, 140, 255), "c2": (220, 190, 80, 255), "pattern": "chevron", "name": "Русь"},
    "nation_groza":  {"c1": (100, 20, 20, 255), "c2": (40, 10, 10, 255), "pattern": "star", "name": "Гроза"},
}

for name, info in nations.items():
    pixels = draw_nation_crest(info["c1"], info["c2"], info["pattern"])
    with open(os.path.join(TEXTURES_DIR, f"{name}.png"), "wb") as f:
        f.write(create_png(16, 16, pixels))
    print(f"  Created {name}.png ({info['name']})")

# === EXTRA ITEMS ===
# Crystal Legendary — purple diamond shape
pixels = draw_diamond(7.5, 7.5, 5.5, (170, 60, 230, 255))
with open(os.path.join(TEXTURES_DIR, "crystal_legendary.png"), "wb") as f:
    f.write(create_png(16, 16, pixels))
print("  Created crystal_legendary.png (Legendary Crystal)")

# Fusion Scroll — golden circle with star
pixels = draw_star(7.5, 7.5, 5.5, 3.0, (240, 190, 40, 255))
with open(os.path.join(TEXTURES_DIR, "fusion_scroll.png"), "wb") as f:
    f.write(create_png(16, 16, pixels))
print("  Created fusion_scroll.png (Fusion Scroll)")

# ═══════════════════════════════════════════════════════════════
# ДОПОЛНИТЕЛЬНЫЕ РУНЫ (named runes — CMD 15-40)
# ═══════════════════════════════════════════════════════════════
named_runes = {
    "arcane_rune":    {"color": (180, 60, 220, 255), "symbol": "void"},
    "darkness_rune":  {"color": (40, 20, 60, 255), "symbol": "void"},
    "death_rune":     {"color": (60, 60, 60, 255), "symbol": "fire"},
    "earth_rune":     {"color": (120, 100, 40, 255), "symbol": "earth"},
    "farming_rune":   {"color": (80, 160, 40, 255), "symbol": "earth"},
    "fire_rune":      {"color": (220, 80, 20, 255), "symbol": "fire"},
    "flame_rune":     {"color": (240, 140, 20, 255), "symbol": "fire"},
    "fishing_rune":   {"color": (40, 120, 200, 255), "symbol": "water"},
    "health_rune":    {"color": (220, 40, 60, 255), "symbol": "earth"},
    "ice_rune":       {"color": (140, 200, 240, 255), "symbol": "water"},
    "iron_rune":      {"color": (160, 160, 170, 255), "symbol": "earth"},
    "light_rune":     {"color": (240, 240, 180, 255), "symbol": "air"},
    "loot_rune":      {"color": (200, 180, 40, 255), "symbol": "void"},
    "luck_rune":      {"color": (40, 200, 80, 255), "symbol": "air"},
    "mining_rune":    {"color": (140, 120, 80, 255), "symbol": "earth"},
    "nature_rune":    {"color": (60, 180, 60, 255), "symbol": "earth"},
    "speed_rune":     {"color": (60, 180, 220, 255), "symbol": "air"},
    "spirit_rune":    {"color": (180, 140, 220, 255), "symbol": "void"},
    "stone_rune":     {"color": (140, 140, 140, 255), "symbol": "earth"},
    "strength_rune":  {"color": (200, 60, 40, 255), "symbol": "fire"},
    "thunder_rune":   {"color": (240, 220, 60, 255), "symbol": "air"},
    "time_rune":      {"color": (120, 80, 180, 255), "symbol": "void"},
    "water_rune":     {"color": (40, 100, 200, 255), "symbol": "water"},
    "wind_rune":      {"color": (180, 200, 220, 255), "symbol": "air"},
    "xp_rune":        {"color": (60, 220, 180, 255), "symbol": "air"},
}

for name, info in named_runes.items():
    pixels = draw_rune_symbol(7.5, 7.5, info["color"], info["symbol"])
    with open(os.path.join(TEXTURES_DIR, f"{name}.png"), "wb") as f:
        f.write(create_png(16, 16, pixels))
    print(f"  Created {name}.png")

total = len(artifacts) + len(runes) + len(nations) + 2 + len(named_runes)
print(f"\nTotal: {total} textures created")
