#!/usr/bin/env python3
"""Gera texturas (pixel art), modelos, blockstates, receitas, loot table, tags e idiomas do MineTouhou.

Uso:  python3 tools/gen_assets.py          (a partir da raiz do projeto)
Requer Pillow. Os arquivos gerados ficam versionados; rode o script só se quiser alterar o visual.
Todas as imagens são desenhadas aqui, pixel a pixel: nenhuma arte do Touhou é usada.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/minetouhou")
DATA = os.path.join(ROOT, "src/main/resources/data")

STATES = ["off", "booting", "awaiting_cd", "verifying", "ready", "running", "error"]


def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def wj(path, obj):
    w(path, json.dumps(obj, indent="\t", ensure_ascii=False) + "\n")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# --------------------------------------------------------------------------- texturas do bloco

BEIGE = hexc("c9c3ae")
BEIGE_L = hexc("ddd8c6")
BEIGE_D = hexc("a8a28e")
BEIGE_DD = hexc("8a846f")
DARK = hexc("23252b")
GRAY = hexc("4a4d57")
GRAY_L = hexc("6b6f7b")
RED = hexc("c2272d")
GREEN = hexc("3fbf5f")


def tex_case():
    """16x16: frente (0..3,0..10), lateral (4..15,0..10), faixa lisa (0..15,11..15)."""
    im = Image.new("RGBA", (16, 16), BEIGE)
    d = ImageDraw.Draw(im)
    # faixa lisa inferior (topo/fundo/traseira)
    d.rectangle([0, 11, 15, 15], fill=BEIGE)
    for x in range(0, 16, 3):
        d.point((x, 12), fill=BEIGE_L)
    # frente 4x11
    d.rectangle([0, 0, 3, 10], fill=BEIGE)
    d.rectangle([0, 0, 0, 10], fill=BEIGE_L)
    d.rectangle([3, 0, 3, 10], fill=BEIGE_D)
    d.rectangle([1, 1, 2, 2], fill=DARK)          # baia do drive óptico
    d.line([(1, 2), (2, 2)], fill=GRAY_L)
    d.rectangle([1, 4, 2, 4], fill=BEIGE_DD)      # 2ª baia
    d.point((1, 7), fill=GRAY)                    # botão power
    d.point((2, 7), fill=GRAY)
    d.point((2, 9), fill=GREEN)                   # led
    d.point((1, 9), fill=RED)                     # led hd
    # lateral 12x11 com aberturas de ventilação
    d.rectangle([4, 0, 15, 10], fill=BEIGE)
    d.rectangle([4, 0, 15, 0], fill=BEIGE_L)
    d.rectangle([4, 10, 15, 10], fill=BEIGE_D)
    for y in range(3, 8):
        d.line([(7, y), (12, y)], fill=BEIGE_DD if y % 2 else BEIGE_D)
    return im


def tex_monitor():
    """16x16: frente (0..10,0..9), lateral (11..13,0..9), liso (0..10,10..15)."""
    im = Image.new("RGBA", (16, 16), BEIGE)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 10, 9], fill=BEIGE)
    d.rectangle([0, 0, 10, 0], fill=BEIGE_L)
    d.rectangle([0, 9, 10, 9], fill=BEIGE_D)
    d.rectangle([0, 0, 0, 9], fill=BEIGE_L)
    d.rectangle([10, 0, 10, 9], fill=BEIGE_D)
    d.point((9, 8), fill=GREEN)                   # led do monitor
    d.rectangle([11, 0, 13, 9], fill=BEIGE_D)
    d.rectangle([11, 0, 11, 9], fill=BEIGE)
    d.rectangle([0, 10, 10, 15], fill=BEIGE)
    for x in range(1, 10, 2):
        d.line([(x, 12), (x, 14)], fill=BEIGE_D)  # grade de ventilação da traseira
    return im


def tex_misc():
    """16x16: teclado (0..7,0..2), tecla/lateral, mouse, base/suporte escuros."""
    im = Image.new("RGBA", (16, 16), GRAY)
    d = ImageDraw.Draw(im)
    # teclado visto de cima 8x3
    d.rectangle([0, 0, 7, 2], fill=BEIGE_D)
    for x in range(0, 8):
        for y in range(0, 3):
            if (x + y) % 2 == 0:
                d.point((x, y), fill=BEIGE_L)
    d.rectangle([2, 2, 5, 2], fill=BEIGE_L)       # barra de espaço
    # lateral do teclado/mouse
    d.rectangle([0, 4, 7, 4], fill=BEIGE_DD)
    # mouse visto de cima (1.5x2 -> região 2x2)
    d.rectangle([9, 0, 10, 1], fill=BEIGE_L)
    d.point((9, 0), fill=BEIGE_D)
    d.point((10, 0), fill=BEIGE_D)
    # base e suporte
    d.rectangle([0, 8, 15, 15], fill=GRAY)
    for x in range(0, 16, 2):
        d.point((x, 9), fill=GRAY_L)
    return im


def screen_base(bg):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 11], fill=bg)
    return im, d


def tex_screen(state):
    """Cada tela usa a região 16x12 do topo (a face do monitor é 4:3)."""
    if state == "off":
        im, d = screen_base(hexc("0d1014"))
        for i in range(0, 6):                      # reflexo diagonal
            d.point((2 + i, 1 + i // 2), fill=hexc("1b2027"))
            d.point((3 + i, 1 + i // 2), fill=hexc("151a20"))
    elif state == "booting":
        im, d = screen_base(hexc("0b1030"))
        d.rectangle([3, 3, 12, 4], outline=hexc("8a93c8"))
        d.rectangle([4, 4, 8, 4], fill=hexc("c9d1ff"))
        d.rectangle([5, 7, 10, 7], fill=hexc("3a4280"))
    elif state == "awaiting_cd":
        im, d = screen_base(hexc("0b1030"))
        cx, cy = 7.5, 5.5
        for y in range(12):
            for x in range(16):
                dist = math.hypot(x - cx, y - cy)
                if dist <= 3.6:
                    d.point((x, y), fill=hexc("cfd4de") if dist > 1.2 else hexc("0b1030"))
        d.point((7, 5), fill=hexc("0b1030"))
        d.point((8, 5), fill=hexc("0b1030"))
        d.point((7, 6), fill=hexc("0b1030"))
        d.point((8, 6), fill=hexc("0b1030"))
        d.rectangle([4, 10, 11, 10], fill=hexc("6c75b8"))
    elif state == "verifying":
        im, d = screen_base(hexc("07140d"))
        for i, y in enumerate((2, 5, 8)):
            d.line([(5, y), (12 - i, y)], fill=hexc("2f8f4a"))
            d.point((2, y), fill=GREEN)
            d.point((3, y + 1) if False else (3, y), fill=GREEN)
    elif state == "ready":
        im, d = screen_base(hexc("2b6fd6"))
        d.rectangle([0, 10, 15, 11], fill=hexc("c9c3ae"))   # barra de tarefas
        d.rectangle([1, 10, 3, 11], fill=hexc("3fbf5f"))
        d.rectangle([5, 2, 10, 7], fill=hexc("f4f4f4"))      # janela
        d.rectangle([5, 2, 10, 2], fill=hexc("1b3f8c"))
        d.point((7, 4), fill=RED)
        d.point((7, 5), fill=RED)
        d.point((8, 4), fill=RED)
        d.point((8, 5), fill=RED)
    elif state == "running":
        im, d = screen_base(hexc("14061f"))
        stars = [(1, 1), (5, 0), (11, 2), (14, 1), (3, 5), (13, 6), (2, 9), (9, 10), (15, 9), (6, 3)]
        for p in stars:
            d.point(p, fill=hexc("9a8fd0"))
        d.point((8, 8), fill=hexc("f4f4f4"))                 # nave
        d.point((8, 9), fill=hexc("e05a8a"))
        for (x, y) in [(6, 2), (8, 3), (10, 2), (7, 5), (9, 5)]:   # balas
            d.point((x, y), fill=RED)
        d.point((8, 1), fill=hexc("f2d46b"))
    elif state == "error":
        im, d = screen_base(hexc("1233a8"))
        for i in range(5):
            d.point((5 + i, 2 + i), fill=hexc("ff5a5a"))
            d.point((9 - i, 2 + i), fill=hexc("ff5a5a"))
        d.rectangle([3, 9, 12, 9], fill=hexc("9db4ff"))
    else:
        raise ValueError(state)
    # brilho do vidro (sutil) em todas
    px = im.load()
    for x in range(0, 16):
        r, g, b, a = px[x, 0]
        px[x, 0] = (min(255, r + 18), min(255, g + 18), min(255, b + 18), 255)
    return im


# --------------------------------------------------------------------------- CD (32x32)

FONT = {
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"],
    "T": ["XXXXX", "..X..", "..X..", "..X..", "..X.."],
    "H": ["X...X", "X...X", "XXXXX", "X...X", "X...X"],
}


def tex_cd():
    n = 32
    c = (n - 1) / 2.0
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r > 15.4:
                continue
            if r > 14.4:
                col = hexc("7d838e")                           # aro externo
            elif r < 2.3:
                continue                                       # furo central
            elif r < 4.2:
                col = hexc("e4e7ec")                           # cubo (hub)
            elif r < 7.2:
                col = hexc("c2272d") if r > 5.0 else hexc("9e1c22")   # detalhe vermelho
            else:
                # prata com reflexo diagonal
                t = (dx + dy) / (2 * 15.0)
                base = 0xcd - int(28 * max(0.0, t)) + int(24 * max(0.0, -t))
                base = max(0x90, min(0xf4, base))
                band = (int(round(math.atan2(dy, dx) * 6 / math.pi)) % 2 == 0)
                shade = 8 if band else 0
                col = (min(255, base + shade), min(255, base + 4 + shade), min(255, base + 12 + shade), 255)
            px[x, y] = col
    # "MTH" 5x5 por letra, uma coluna de espaço entre elas, centralizado, linhas 21..25
    text_w = 5 * 3 + 2
    x0 = int(c - text_w / 2 + 0.5)
    y0 = 21
    ink = hexc("5b0f14")
    for i, ch in enumerate("MTH"):
        for ry, row in enumerate(FONT[ch]):
            for rx, v in enumerate(row):
                if v == "X":
                    px[x0 + i * 6 + rx, y0 + ry] = ink
    # brilho
    for (x, y) in [(8, 5), (9, 4), (10, 4), (7, 6)]:
        px[x, y] = hexc("f7f9fc")
    return im


def tex_icon():
    base = Image.new("RGBA", (32, 32), hexc("1b1f2a"))
    d = ImageDraw.Draw(base)
    d.rectangle([2, 4, 29, 23], fill=BEIGE)                # monitor
    d.rectangle([4, 6, 27, 21], fill=hexc("14061f"))
    scr = tex_screen("running").crop((0, 0, 16, 12)).resize((22, 15), Image.NEAREST)
    base.paste(scr, (5, 6))
    d.rectangle([12, 24, 19, 26], fill=GRAY)
    d.rectangle([8, 27, 23, 28], fill=GRAY)
    cd = tex_cd().resize((14, 14), Image.NEAREST)
    base.alpha_composite(cd, (17, 16))
    return base.resize((128, 128), Image.NEAREST)


# --------------------------------------------------------------------------- modelos

def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    return f


def element(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def pc_base_model():
    case_front = face("#case", [0, 0, 4, 11])
    case_side = face("#case", [4, 0, 16, 11])
    case_plain_tb = face("#case", [0, 11, 4, 16])
    mon_front = face("#monitor", [0, 0, 11, 10])
    mon_side = face("#monitor", [11, 0, 14, 10])
    mon_back = face("#monitor", [0, 10, 11, 16])
    mon_tb = face("#monitor", [0, 10, 11, 13])
    dark = face("#misc", [0, 8, 16, 16])
    els = [
        # torre
        element([0, 0, 3], [4, 11, 15], {
            "north": case_front, "south": case_plain_tb, "east": case_side, "west": case_side,
            "up": case_plain_tb, "down": case_plain_tb}),
        # corpo do monitor
        element([5, 5, 5], [16, 15, 8], {
            "north": mon_front, "south": mon_back, "east": mon_side, "west": mon_side,
            "up": mon_tb, "down": mon_tb}),
        # tela (plano fino à frente do corpo do monitor; área 9 x 6.75 = 4:3)
        element([6, 7, 4.9], [15, 13.75, 4.9], {"north": {"texture": "#screen", "uv": [0, 0, 16, 12]}}),
        # suporte e base
        element([9, 1, 6], [12, 5, 7], {k: dark for k in ("north", "south", "east", "west", "up", "down")}),
        element([7, 0, 5], [14, 1, 8], {k: dark for k in ("north", "south", "east", "west", "up", "down")}),
        # teclado
        element([5, 0, 1], [13, 1, 4], {
            "up": face("#misc", [0, 0, 8, 3]),
            "north": face("#misc", [0, 4, 8, 5]), "south": face("#misc", [0, 4, 8, 5]),
            "east": face("#misc", [0, 4, 3, 5]), "west": face("#misc", [0, 4, 3, 5])}),
        # mouse
        element([14, 0, 2], [15.5, 0.8, 4], {
            "up": face("#misc", [9, 0, 11, 2]),
            "north": face("#misc", [9, 0, 11, 1]), "south": face("#misc", [9, 0, 11, 1]),
            "east": face("#misc", [9, 0, 11, 1]), "west": face("#misc", [9, 0, 11, 1])}),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "minetouhou:block/pc_case",
            "case": "minetouhou:block/pc_case",
            "monitor": "minetouhou:block/pc_monitor",
            "misc": "minetouhou:block/pc_misc",
        },
        "elements": els,
    }


def main():
    tex_dir = os.path.join(ASSETS, "textures")
    os.makedirs(os.path.join(tex_dir, "block"), exist_ok=True)
    os.makedirs(os.path.join(tex_dir, "item"), exist_ok=True)

    tex_case().save(os.path.join(tex_dir, "block/pc_case.png"))
    tex_monitor().save(os.path.join(tex_dir, "block/pc_monitor.png"))
    tex_misc().save(os.path.join(tex_dir, "block/pc_misc.png"))
    for s in STATES:
        tex_screen(s).save(os.path.join(tex_dir, f"block/screen_{s}.png"))
    tex_cd().save(os.path.join(tex_dir, "item/cd.png"))
    tex_icon().save(os.path.join(ASSETS, "icon.png"))

    wj(os.path.join(ASSETS, "models/block/pc_base.json"), pc_base_model())
    for s in STATES:
        wj(os.path.join(ASSETS, f"models/block/pc_{s}.json"), {
            "parent": "minetouhou:block/pc_base",
            "textures": {"screen": f"minetouhou:block/screen_{s}"},
        })

    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for s in STATES:
            v = {"model": f"minetouhou:block/pc_{s}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},state={s}"] = v
    wj(os.path.join(ASSETS, "blockstates/pc.json"), {"variants": variants})

    wj(os.path.join(ASSETS, "models/item/pc.json"), {"parent": "minetouhou:block/pc_ready"})
    wj(os.path.join(ASSETS, "models/item/cd.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minetouhou:item/cd"},
    })

    # idiomas
    wj(os.path.join(ASSETS, "lang/en_us.json"), {
        "block.minetouhou.pc": "MineTouhou PC",
        "item.minetouhou.cd": "MineTouhou CD",
    })
    wj(os.path.join(ASSETS, "lang/pt_br.json"), {
        "block.minetouhou.pc": "PC MineTouhou",
        "item.minetouhou.cd": "CD MineTouhou",
    })

    # receitas (formato 1.21.1)
    concrete_colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                       "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
    wj(os.path.join(DATA, "minetouhou/tags/item/concrete.json"), {
        "replace": False,
        "values": [f"minecraft:{c}_concrete" for c in concrete_colors],
    })
    wj(os.path.join(DATA, "minetouhou/recipe/pc.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["RIC", "RIC", "RIC"],
        "key": {
            "R": {"item": "minecraft:redstone"},
            "I": {"item": "minecraft:iron_ingot"},
            "C": {"tag": "minetouhou:concrete"},
        },
        "result": {"id": "minetouhou:pc", "count": 1},
    })
    wj(os.path.join(DATA, "minetouhou/recipe/cd.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["III", "IWI", "II "],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "W": {"item": "minecraft:red_wool"},
        },
        "result": {"id": "minetouhou:cd", "count": 1},
    })

    # o bloco solta a si mesmo
    wj(os.path.join(DATA, "minetouhou/loot_table/blocks/pc.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "minetouhou:pc"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "minetouhou:blocks/pc",
    })
    wj(os.path.join(DATA, "minecraft/tags/block/mineable/pickaxe.json"), {
        "replace": False,
        "values": ["minetouhou:pc"],
    })


if __name__ == "__main__":
    sys.exit(main())
