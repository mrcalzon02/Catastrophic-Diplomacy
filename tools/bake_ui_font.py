#!/usr/bin/env python3
"""Bake the vendored SIL OFL Atkinson font; Pillow 13, no runtime font dependency."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
ROOT = Path(__file__).resolve().parents[1] / 'assets/fonts'
SIZE = 17
font = ImageFont.truetype(str(ROOT / 'AtkinsonHyperlegible-Regular.ttf'), SIZE)
codes = set(range(32, 127)) | set(range(160, 256))
for source in (ROOT.parents[1] / 'game/src/main/java').rglob('*.java'):
    codes.update(ord(ch) for ch in source.read_text() if 256 <= ord(ch) < 0x2500)
codes = sorted(codes)
# Fixed cells keep the atlas deterministic and make accidental overlaps impossible.
atlas = Image.new('RGBA', (512, 512), (255, 255, 255, 0))
draw = ImageDraw.Draw(atlas)
ascent, descent = font.getmetrics()
lines = [f'info face="Atkinson Hyperlegible" size={SIZE} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',
         f'common lineHeight={ascent+descent} base={ascent} scaleW=512 scaleH=512 pages=1 packed=0',
         'page id=0 file="atkinson-17.png"', f'chars count={len(codes)}']
for i, code in enumerate(codes):
    ch = chr(code)
    x, y = (i % 16) * 32 + 2, (i // 16) * 28 + 2
    left, top, right, bottom = font.getbbox(ch, anchor='ls')
    w, h = right-left, bottom-top
    assert w <= 28 and h <= 24 and y+h < 512, (code, w, h)
    draw.text((x-left, y-top), ch, font=font, fill='white', anchor='ls')
    lines.append(f'char id={code} x={x} y={y} width={w} height={h} xoffset={left} yoffset={ascent+top} xadvance={round(font.getlength(ch))} page=0 chnl=15')
lines.append('kernings count=0')
atlas.save(ROOT / 'atkinson-17.png')
(ROOT / 'atkinson-17.fnt').write_text('\n'.join(lines)+'\n')
print(f'Baked {len(codes)} glyphs into {atlas.size}')
