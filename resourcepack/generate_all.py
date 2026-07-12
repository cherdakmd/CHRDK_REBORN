import os, json, struct, zlib

RP = "D:/CHRDK DEV/CHRDK/resourcepack"
MODELS = os.path.join(RP, "assets/minecraft/models/item")
TEXTURES = os.path.join(RP, "assets/minecraft/textures/item")
os.makedirs(MODELS, exist_ok=True)
os.makedirs(TEXTURES, exist_ok=True)

def create_png(w, h, pixels):
    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    raw = b""
    for row in pixels:
        raw += b"\x00"
        for r, g, b, a in row:
            raw += struct.pack("BBBB", r, g, b, a)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")

def solid_texture(color):
    return [[color]*16 for _ in range(16)]

def icon_texture(fg, bg=(0,0,0,0)):
    px = [[bg]*16 for _ in range(16)]
    for y in range(2, 14):
        for x in range(3, 13):
            if y == 2 or y == 13 or x == 3 or x == 12:
                px[y][x] = fg
            elif 4 <= y <= 12 and 4 <= x <= 11:
                px[y][x] = (*fg[:3], min(255, fg[3]-30) if fg[3]>30 else fg[3])
    return px

def cross_texture(fg, bg=(0,0,0,0)):
    px = [[bg]*16 for _ in range(16)]
    for i in range(4, 12):
        px[7][i] = fg; px[8][i] = fg
        px[i][7] = fg; px[i][8] = fg
    return px

def star_texture(color):
    import math
    px = [[(0,0,0,0)]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            dist = (dx*dx + dy*dy)**0.5
            if dist <= 6:
                angle = math.atan2(dy, dx)
                star_a = (angle + math.pi) % (2*math.pi/5)
                deg = math.degrees(star_a) % 72
                r = 6 if deg < 36 else 3
                if r-1.5 <= dist <= r+0.5:
                    px[y][x] = color
                elif dist < r-1.5:
                    px[y][x] = (*color[:3], min(255, color[3]-40))
    return px

# ═══════════════════════════════════════════════════════════════
# ТЕКСТУРЫ
# ═══════════════════════════════════════════════════════════════
items = {}

# --- Расходники ---
items["scroll_cleanse"] = ("П卷 Очищения", icon_texture((200, 180, 60, 255)))
items["totem_revive"] = ("Тотем Крови", icon_texture((200, 40, 40, 255)))
items["scroll_escape"] = ("Сфера Побега", icon_texture((120, 40, 180, 255)))
items["scroll_enchant"] = ("Свиток Чар", icon_texture((60, 160, 200, 255)))
items["repair_kit"] = ("Ремонтный Набор", icon_texture((160, 160, 160, 255)))
items["rune_exchange"] = ("Руна Обмена", icon_texture((140, 60, 200, 255)))
items["totem_fort"] = ("Тотем Укрепления", icon_texture((200, 160, 40, 255)))
items["antidote"] = ("Антидот", icon_texture((40, 200, 80, 255)))

# --- Токены/осколки ---
items["rune_token"] = ("Жетон Рун", icon_texture((200, 170, 40, 255)))
items["artifact_shard"] = ("Осколок Артефакта", icon_texture((80, 180, 200, 255)))

# --- Кристаллы ---
items["crystal_common"] = ("Кристалл Common", icon_texture((60, 180, 80, 255)))
items["crystal_rare"] = ("Кристалл Rare", icon_texture((60, 120, 220, 255)))
items["crystal_ancient"] = ("Кристалл Ancient", icon_texture((220, 60, 60, 255)))

# --- Национальные: Союз ---
items["soviet_pickaxe"] = ("Кирка Пятилетки", icon_texture((180, 30, 30, 255)))
items["soviet_manifesto"] = ("Манифест", icon_texture((200, 40, 40, 255)))
items["soviet_crystal"] = ("Инд. Кристалл", icon_texture((200, 180, 60, 255)))

# --- Национальные: Чека ---
items["kgb_glasses"] = ("Очки Чека", icon_texture((60, 20, 20, 255)))
items["kgb_dagger"] = ("Кинжал Смерша", icon_texture((160, 30, 30, 255)))
items["kgb_serum"] = ("Сыворотка", icon_texture((100, 20, 60, 255)))

# --- Национальные: Ведуны ---
items["pagan_staff"] = ("Посох Ведунов", icon_texture((40, 120, 40, 255)))
items["pagan_amulet"] = ("Оберег Ветра", icon_texture((140, 180, 200, 255)))
items["pagan_brew"] = ("Целебный Отвар", icon_texture((160, 120, 40, 255)))

# --- Национальные: Навь ---
items["pagan_idol"] = ("Идол Нави", icon_texture((80, 20, 100, 255)))
items["pagan_sickle"] = ("Серп Жатвы", icon_texture((120, 100, 60, 255)))
items["pagan_infusion"] = ("Кровь Алтаря", icon_texture((180, 20, 20, 255)))

# --- Национальные: Русь ---
items["imperial_scepter"] = ("Царский Скипетр", icon_texture((200, 170, 40, 255)))
items["imperial_shield"] = ("Богатырский Щит", icon_texture((60, 100, 180, 255)))
items["imperial_bread"] = ("Царский Каравай", icon_texture((200, 160, 60, 255)))

# --- Национальные: Гроза ---
items["imperial_saber"] = ("Сабля Грозы", icon_texture((140, 30, 30, 255)))
items["imperial_shackles"] = ("Кандалы", icon_texture((100, 100, 100, 255)))
items["imperial_cup"] = ("Кубок Грозного", icon_texture((180, 140, 40, 255)))

# --- Свитки/фрагменты/блоки привата ---
items["scroll_safety"] = ("Свиток Сохранения", icon_texture((200, 180, 60, 255)))
items["scroll_chance25"] = ("Свиток Точного Слияния", icon_texture((180, 100, 200, 255)))
items["scroll_chance50"] = ("Свиток Сильного Слияния", icon_texture((120, 40, 180, 255)))
items["scroll_steel"] = ("Свиток Чистой Стали", icon_texture((100, 200, 160, 255)))
items["scroll_discount"] = ("Свиток Скидки", icon_texture((40, 160, 80, 255)))
items["scroll_protect"] = ("Свиток Полной Защиты", icon_texture((60, 160, 220, 255)))
items["scroll_rep"] = ("Свиток Репутации", icon_texture((220, 200, 60, 255)))
items["scroll_speed"] = ("Свиток Скорости", icon_texture((60, 200, 240, 255)))
items["set_fragment"] = ("Фрагмент Сета", icon_texture((200, 160, 40, 255)))
items["claim_block"] = ("Блок Привата", icon_texture((200, 180, 40, 255)))

for name, (desc, pixels) in items.items():
    with open(os.path.join(TEXTURES, f"{name}.png"), "wb") as f:
        f.write(create_png(16, 16, pixels))
    print(f"  {name}.png")

# ═══════════════════════════════════════════════════════════════
# МОДЕЛИ — базовые файлы для каждого Material
# ═══════════════════════════════════════════════════════════════
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 1, 3], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand":  {"rotation": [0, 90, 0], "translation": [0, 1, 3], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand":  {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}
}

# material_id -> [(cmd, model_name)]
material_overrides = {
    "paper":                [(40, "scroll_cleanse"), (54, "scroll_safety"), (55, "scroll_chance25"),
                             (56, "scroll_chance50"), (57, "scroll_steel"), (58, "scroll_discount"),
                             (59, "set_fragment"), (60, "scroll_rep")],
    "totem_of_undying":     [(41, "totem_revive"), (62, "scroll_protect")],
    "ender_pearl":          [(42, "scroll_escape")],
    "book":                 [(43, "scroll_enchant"), (61, "soviet_manifesto")],
    "anvil":                [(44, "repair_kit")],
    "enchanted_book":       [(45, "rune_exchange")],
    "beacon":               [(46, "totem_fort")],
    "milk_bucket":          [(47, "antidote")],
    "gold_nugget":          [(48, "rune_token")],
    "iron_nugget":          [(77, "imperial_shackles")],
    "sugar":                [(80, "scroll_speed")],
    "gold_block":           [(80, "claim_block")],
    "emerald_block":        [(80, "claim_block")],
    "diamond_block":        [(80, "claim_block")],
    "prismarine_shard":     [(49, "artifact_shard")],
    "nether_star":          [(1, "artifact_common"), (2, "artifact_rare"), (3, "artifact_epic"),
                             (4, "artifact_legendary"), (5, "artifact_mythic"),
                             (6, "crystal_legendary"),
                             (10, "rune_fire"), (11, "rune_water"), (12, "rune_earth"),
                             (13, "rune_air"), (14, "rune_void"),
                             (15, "arcane_rune"), (16, "darkness_rune"), (17, "death_rune"),
                             (18, "earth_rune"), (19, "farming_rune"),
                             (20, "fire_rune"), (21, "flame_rune"), (22, "fishing_rune"),
                             (23, "health_rune"), (24, "ice_rune"),
                             (25, "iron_rune"), (26, "light_rune"), (27, "loot_rune"),
                             (28, "luck_rune"), (29, "mining_rune"),
                             (30, "fusion_scroll"),
                             (31, "nature_rune"), (32, "speed_rune"), (33, "spirit_rune"),
                             (34, "stone_rune"), (35, "strength_rune"),
                             (36, "thunder_rune"), (37, "time_rune"),
                             (38, "water_rune"), (39, "wind_rune"), (40, "xp_rune")],
    "prismarine_crystals":  [(49, "artifact_shard")],
    "emerald":              [(50, "crystal_common")],
    "diamond":              [(51, "crystal_rare")],
    "heart_of_the_sea":     [(52, "crystal_ancient")],
    "iron_pickaxe":         [(60, "soviet_pickaxe")],
    "quartz":               [(62, "soviet_crystal")],
    "leather_helmet":       [(63, "kgb_glasses")],
    "iron_sword":           [(64, "kgb_dagger"), (65, "imperial_saber")],
    "glass_bottle":         [(66, "kgb_serum")],
    "wooden_hoe":           [(67, "pagan_staff")],
    "feather":              [(68, "pagan_amulet")],
    "honey_bottle":         [(69, "pagan_brew"), (70, "imperial_cup")],
    "wither_skeleton_skull":[(71, "pagan_idol")],
    "iron_hoe":             [(72, "pagan_sickle")],
    "dragon_breath":        [(73, "pagan_infusion")],
    "blaze_rod":            [(74, "imperial_scepter")],
    "shield":               [(75, "imperial_shield")],
    "bread":                [(76, "imperial_bread")],
}

for mat, overrides in material_overrides.items():
    # 1. Override model for each item
    for cmd, model_name in overrides:
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"minecraft:item/{model_name}", "layer1": f"minecraft:item/{model_name}"}, "display": DISPLAY}
        with open(os.path.join(MODELS, f"{model_name}.json"), "w") as f:
            json.dump(model, f, indent=2)
        print(f"  {model_name}.json")

    # 2. Base material model with overrides
    overrides_list = [{"predicate": {"custom_model_data": cmd}, "model": f"item/{mn}"} for cmd, mn in overrides]
    base = {"parent": "minecraft:item/generated", "textures": {"layer0": f"minecraft:item/{mat}"}, "overrides": overrides_list}
    
    base_path = os.path.join(MODELS, f"{mat}.json")
    if os.path.exists(base_path):
        with open(base_path, "r") as f:
            existing = json.load(f)
        if "overrides" in existing:
            existing["overrides"].extend(overrides_list)
        else:
            existing["overrides"] = overrides_list
        with open(base_path, "w") as f:
            json.dump(existing, f, indent=2)
        print(f"  {mat}.json (merged)")
    else:
        with open(base_path, "w") as f:
            json.dump(base, f, indent=2)
        print(f"  {mat}.json (new)")

print(f"\nTotal: {len(items)} textures + {len(material_overrides)} base models")
