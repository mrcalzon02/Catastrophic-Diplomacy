#!/usr/bin/env python3
"""Deterministic Android, Windows and Linux icons from an original approved ship."""
from __future__ import annotations
import argparse
import hashlib
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT=Path(__file__).resolve().parents[1]
SHIP=ROOT/'assets/ships/factions/umbra_foundry/battlecruiser.png'
OUT=ROOT/'build/generated/app-icons'
RES=ROOT/'android/src/main/res'

def emblem(n=1024):
    if not SHIP.is_file():
        raise SystemExit(f'Missing approved ship sprite: {SHIP}')
    with Image.open(SHIP) as original:
        ship=original.convert('RGBA')
    if ship.width<96 or ship.height<96 or ship.getbbox() is None:
        raise SystemExit(f'Invalid original ship art: {SHIP}')
    ship.thumbnail((int(n*.40),int(n*.63)),Image.Resampling.LANCZOS)
    art=Image.new('RGBA',(n,n),(5,9,12,255))
    pix=art.load()
    for y in range(n):
        for x in range(n):
            shine=max(0,22-abs(x-y*.66)//max(1,n//22))
            noise=((x*29+y*17+(x*y)//97)%11)-5
            v=int(10+13*(1-y/n)+shine*.31+noise*.40)
            pix[x,y]=(v,v+4,v+7,255)
    d=ImageDraw.Draw(art)
    s=lambda v:round(v*n/1024)
    hull=[(170,35),(854,35),(989,170),(989,854),
          (854,989),(170,989),(35,854),(35,170)]
    poly=[(s(x),s(y)) for x,y in hull]
    d.line(poly+[poly[0]],fill='#606765',width=s(22),joint='curve')
    d.line(poly+[poly[0]],fill='#131b20',width=s(12),joint='curve')
    d.line([(s(175),s(61)),(s(849),s(61))],fill='#f5a23b',width=s(15))
    d.line([(s(175),s(963)),(s(849),s(963))],fill='#f5a23b',width=s(11))
    for x,y in ((150,145),(874,145),(150,879),(874,879)):
        r=s(15)
        d.ellipse((s(x)-r,s(y)-r,s(x)+r,s(y)+r),fill='#707975')
        d.line([(s(x-7),s(y-7)),(s(x+7),s(y+7))],fill='#1a252a',width=s(5))
    cx,cy,radius=s(512),s(431),s(318)
    d.arc((cx-radius,cy-radius,cx+radius,cy+radius),24,156,fill='#318dae',width=s(10))
    d.arc((cx-radius,cy-radius,cx+radius,cy+radius),204,336,fill='#318dae',width=s(10))
    for deg in (0,45,90,135,180,225,270,315):
        theta=math.radians(deg)
        d.line([(cx+int(s(330)*math.cos(theta)),cy+int(s(330)*math.sin(theta))),
                (cx+int(s(354)*math.cos(theta)),cy+int(s(354)*math.sin(theta)))],
               fill='#69bfce',width=s(7))
    layer=Image.new('RGBA',art.size)
    layer.alpha_composite(ship,((n-ship.width)//2,s(112)))
    mask=layer.getchannel('A').filter(ImageFilter.GaussianBlur(s(22)))
    glow=Image.new('RGBA',art.size)
    glow.paste((42,144,190,85),(0,0,n,n),mask)
    art=Image.alpha_composite(Image.alpha_composite(art,glow),layer)
    d=ImageDraw.Draw(art)
    d.rounded_rectangle((s(289),s(822),s(735),s(928)),radius=s(16),
                        fill='#081215',outline='#6d7570',width=s(7))
    d.line([(s(455),s(844)),(s(351),s(844)),(s(334),s(867)),
            (s(334),s(889)),(s(351),s(908)),(s(455),s(908))],
           fill='#ffa642',width=s(18),joint='curve')
    d.line([(s(536),s(844)),(s(646),s(844)),(s(684),s(869)),
            (s(684),s(883)),(s(646),s(908)),(s(536),s(908)),(s(536),s(844))],
           fill='#ffa642',width=s(18),joint='curve')
    return art

def png(image,path,size):
    path.parent.mkdir(parents=True,exist_ok=True)
    image.resize((size,size),Image.Resampling.LANCZOS).save(path,format='PNG',optimize=True)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    check=parser.parse_args().check
    base=emblem()
    desktop=OUT/'catastrophic-diplomacy.png'
    ico=OUT/'catastrophic-diplomacy.ico'
    files={desktop:512}
    for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),
                         ('xxhdpi',144),('xxxhdpi',192)]:
        files[RES/f'mipmap-{density}/ic_launcher.png']=size
    foreground=RES/'drawable-nodpi/cd_launcher_foreground.png'
    xml=RES/'mipmap-anydpi-v26/ic_launcher.xml'
    colors=RES/'values/cd_launcher_colors.xml'
    fg=Image.new('RGBA',(432,432))
    fg.alpha_composite(base.resize((286,286),Image.Resampling.LANCZOS),(73,73))
    if not check:
        for path,size in files.items():png(base,path,size)
        foreground.parent.mkdir(parents=True,exist_ok=True)
        fg.save(foreground,format='PNG',optimize=True)
        ico.parent.mkdir(parents=True,exist_ok=True)
        base.save(ico,format='ICO',sizes=[(v,v) for v in (16,24,32,48,64,128,256)])
        xml.parent.mkdir(parents=True,exist_ok=True)
        xml.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '  <background android:drawable="@color/cd_launcher_background"/>\n'
            '  <foreground android:drawable="@drawable/cd_launcher_foreground"/>\n'
            '</adaptive-icon>\n',encoding='utf-8')
        colors.parent.mkdir(parents=True,exist_ok=True)
        colors.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
            '<resources><color name="cd_launcher_background">#060A0C</color></resources>\n',
            encoding='utf-8')
    for path,size in files.items():
        with Image.open(path) as im:
            if im.size!=(size,size) or im.getbbox() is None:
                raise SystemExit(f'Invalid app icon: {path}')
        with Image.open(path) as im:im.verify()
    with Image.open(foreground) as im:
        if im.size!=(432,432) or im.getpixel((0,0))[3]!=0:
            raise SystemExit('Invalid Android adaptive foreground')
    with Image.open(ico) as im:
        if im.format!='ICO' or im.size!=(256,256):
            raise SystemExit('Invalid Windows multi-resolution icon')
    if '@drawable/cd_launcher_foreground' not in xml.read_text() or \
       'cd_launcher_background' not in colors.read_text():
        raise SystemExit('Android adaptive launcher XML missing')
    print('PASS original-art icon on all three platforms; SHA-256',
          hashlib.sha256(desktop.read_bytes()).hexdigest())

if __name__=='__main__':
    main()
