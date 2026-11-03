#!/usr/bin/env python3
"""Build 42 fixed 96x96 RGBA armor impact masks for Catastrophic Diplomacy.

Offline authored artwork: R soot, G heat, B exposed metal/laser oxide,
A surviving plating. Six deliberately different wounds per family. No
per-frame procedural geometry in the battle renderer. A 96px mask spans
the same 2.667 defense cells as the legacy 24px assets.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import argparse
import json
import math
import random

SIZE = 96
FAMILIES = ("laser","kinetic","plasma","particle","missile","torpedo","fighter")
ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "assets" / "damage_masks" / "hires96"

def jagged(cx,cy,rx,ry,seed,n=20):
    rng=random.Random(seed)
    return [(round(cx+math.cos(math.tau*i/n)*rx*rng.uniform(.75,1.16)),
             round(cy+math.sin(math.tau*i/n)*ry*rng.uniform(.75,1.16)))
            for i in range(n)]

def line(draw,points,value,width=1):
    if len(points)>1: draw.line(points,fill=value,width=width,joint="curve")
    if width>=3:
        for x,y in points[1:-1]:
            draw.ellipse((x-width//2,y-width//2,x+width//2,y+width//2),fill=value)

def create(family,v):
    fields=[Image.new("L",(SIZE,SIZE),0) for _ in range(4)]
    soot,heat,metal,hole=[ImageDraw.Draw(im) for im in fields]
    rng=random.Random(341731+v*521+FAMILIES.index(family)*100003)
    ox=(0,2,-2,3,-3,1)[v]; oy=(0,-2,2,-1,1,3)[v]
    def X(x):return round(x+ox)
    def Y(y):return round(y+oy)
    def path(d,points,value,width=1):
        line(d,[(X(x),Y(y)) for x,y in points],value,width)
    def dot(d,x,y,r,value):
        d.ellipse((X(x-r),Y(y-r),X(x+r),Y(y+r)),fill=value)
    def poly(d,points,value):
        d.polygon([(X(x),Y(y)) for x,y in points],fill=value)
    def cracks(paths,m=150,s=80):
        for points in paths:
            path(soot,points,s,2)
            path(metal,[(x+.75,y-1) for x,y in points],m,1)
    def crater(cx,cy,rx,ry,index,h=0,m=190):
        outer=jagged(X(cx),Y(cy),rx+4,ry+3,500+v*17+index,22)
        rim=jagged(X(cx),Y(cy),rx+1,ry+1,600+v*37+index,22)
        inner=jagged(X(cx),Y(cy),max(2,rx-3),max(2,ry-3),
                     700+v*41+index,21)
        soot.polygon(outer,fill=155);metal.polygon(outer,fill=m)
        soot.polygon(rim,fill=180);metal.polygon(rim,fill=65)
        hole.polygon(inner,fill=235)
        if h:heat.polygon(rim,fill=h)
        for i in (0,2,5,9,13,17):
            line(metal,[outer[i],outer[(i+1)%len(outer)]],min(255,m+30),1)

    if family=="laser":
        recipes=[
            [[(27,62),(34,53),(46,44),(60,33),(66,28)]],
            [[(27,31),(37,40),(48,49),(61,58),(69,66)]],
            [[(26,65),(36,56),(46,46),(57,37),(70,31),(64,39),(72,46)]],
            [[(34,29),(39,40),(44,51),(48,62)],[(53,32),(57,42),(62,52),(65,62)]],
            [[(35,42),(40,37)],[(45,45),(49,41)],[(54,53),(58,48)],
             [(63,38),(66,35)],[(46,64),(50,61)]],
            [[(30,34),(42,46),(52,45),(61,56),(70,50)]]]
        for j,p in enumerate(recipes[v]):
            path(soot,p,205,5 if j==0 else 3);path(heat,p,170,3)
            path(metal,[(x+1,y-2) for x,y in p],205,2)
            path(hole,p,235,2 if v==4 else 1)
            for x,y in p[1:-1:2]:dot(heat,x,y,2,225)
        if v==2:cracks([[(45,46),(50,40),(54,34)],[(60,40),(65,45)]],175,125)
        if v==5:cracks([[(52,46),(59,36),(64,33)],[(61,56),(68,60)]],175,125)
        fields[1]=fields[1].filter(ImageFilter.GaussianBlur(1.6))
        fields[2]=Image.blend(fields[2],
              fields[2].filter(ImageFilter.GaussianBlur(1)),.20)

    elif family=="kinetic":
        cx,cy,rx,ry=[(47,48,13,11),(45,48,15,8),(49,50,10,13),
                     (45,45,13,10),(46,49,9,9),(50,46,8,8)][v]
        crater(cx,cy,rx,ry,v)
        if v==1:
            poly(hole,[(24,51),(40,40),(50,46),(69,36),(59,52),(44,61)],235)
            path(metal,[(24,49),(37,37),(55,45),(70,35)],225,2)
        if v==2:
            crater(61,40,5,5,103,m=182);crater(35,62,4,5,104,m=180)
        if v==3:poly(hole,[(37,27),(46,39),(64,42),(54,53),(63,63),(40,58)],230)
        if v==4:crater(60,58,5,5,200,m=195)
        marks=[[(cx-8,cy-9),(cx-20,cy-19),(cx-27,cy-31)],
               [(cx+5,cy-9),(cx+15,cy-22),(cx+22,cy-26)],
               [(cx+10,cy+5),(cx+23,cy+10),(cx+31,cy+17)],
               [(cx-7,cy+9),(cx-19,cy+17),(cx-23,cy+27)],
               [(cx-11,cy-1),(cx-23,cy+1),(cx-30,cy-2)]]
        cracks(marks[:3+(v%3)],205,140)
        for i in range(11):
            a=rng.uniform(0,math.tau);r=rng.uniform(18,32)
            dot(metal,cx+math.cos(a)*r,cy+math.sin(a)*r,
                rng.choice((.5,1)),rng.randrange(90,155))

    elif family=="plasma":
        recipes=[
            [[(41,26),(39,42),(48,50),(45,64),(42,75)]],
            [[(28,46),(44,45),(51,56),(64,61),(76,58)]],
            [[(43,35),(48,49),(38,66),(29,74)]],
            [[(58,28),(47,44),(50,52),(62,63),(69,76)]],
            [[(42,30),(43,48),(54,61)],[(52,47),(65,39)],[(47,57),(34,70)]],
            [[(61,26),(56,40),(42,49),(42,63),(59,70),(68,60)]]]
        for p in recipes[v]:
            path(soot,p,150,11);path(heat,p,175,8)
            path(hole,p,160,4)
            path(metal,[(x-3,y-1) for x,y in p],155,2)
        for i in range(8+v%3):
            x=rng.randrange(25,74);y=rng.randrange(27,77)
            if v%2==0 and x<42:continue
            r=rng.randrange(1,4)
            dot(heat,x,y,r,185);dot(soot,x-1,y+1,r+1,105)
        if v==2:path(heat,[(48,49),(56,42),(65,34)],180,4)
        fields[1]=Image.blend(fields[1],
            fields[1].filter(ImageFilter.GaussianBlur(2.3)),.22)

    elif family=="particle":
        routes=[[(24,65),(33,56),(46,43),(54,38)],
                [(24,34),(36,42),(44,50),(57,59)],
                [(31,59),(43,48),(52,41),(64,34)],
                [(29,54),(41,49),(52,49),(68,45)],
                [(34,35),(45,46),(59,57)],
                [(25,54),(41,49),(51,46),(67,37)]]
        p=routes[v]
        path(soot,p,120,4)
        path(metal,[(x+1,y-2) for x,y in p],120,1)
        path(hole,p,145,1)
        drills=[[(54,38,4)],[(39,44,3),(57,58,4)],
                [(48,47,3),(65,34,3)],[(44,49,4),(62,47,3)],
                [(43,45,2),(59,58,2),(49,51,2)],
                [(48,48,4),(63,39,3)]][v]
        for x,y,r in drills:
            dot(soot,x,y,r+3,220)
            dot(metal,x+1,y-1,r+2,155)
            dot(hole,x,y,r,245)
        if v in (1,4):cracks([[(45,47),(49,39),(52,35)]],100,65)

    elif family=="missile":
        clusters=[[(46,48,14,11)],[(39,43,10,8),(61,56,9,7)],
                  [(47,50,14,10)],[(40,53,8,9),(56,40,10,7)],
                  [(44,48,12,8)],[(46,47,8,8),(60,57,5,5),(34,38,5,5)]]
        for i,(x,y,rx,ry) in enumerate(clusters[v]):
            crater(x,y,rx,ry,300+i+v*20,h=18,m=165)
        if v==2:
            poly(hole,[(28,50),(37,58),(48,67),(59,58),
                       (65,55),(54,46),(40,45)],240)
            path(metal,[(29,48),(34,59),(44,69),(58,62)],235,2)
        if v==4:
            poly(hole,[(27,60),(40,47),(57,39),
                       (71,35),(56,45),(46,56),(39,68)],220)
        spall=[[(23,43),(32,40),(35,33)],
               [(61,40),(70,34),(75,26)],
               [(58,62),(66,67),(71,78)],
               [(32,61),(25,72),(19,74)]]
        cracks(spall[:2+v%3],105,100)
        for i in range(12):
            dot(soot,rng.randrange(18,80),rng.randrange(18,80),
                1,rng.randrange(80,170))

    elif family=="torpedo":
        breaches=[
            [(27,46),(38,34),(47,40),(58,28),(70,39),(60,48),
             (73,57),(54,66),(41,62),(26,72),(32,57)],
            [(32,28),(44,39),(51,29),(59,42),(73,48),(60,58),
             (58,73),(44,61),(34,70),(28,55)],
            [(24,41),(38,43),(37,26),(49,39),(65,29),(63,47),
             (76,52),(60,61),(56,76),(44,65),(29,72)],
            [(29,27),(42,38),(50,29),(59,39),(71,30),(63,47),
             (72,62),(56,57),(46,75),(42,58),(24,61)],
            [(24,50),(36,29),(47,39),(57,23),(64,42),(79,52),
             (61,53),(69,74),(49,64),(34,76)],
            [(31,32),(43,43),(54,28),(61,39),(73,45),(61,56),
             (71,64),(50,73),(37,59),(23,54)]]
        p=breaches[v]
        poly(soot,p,190);poly(metal,p,120)
        poly(hole,[(48+(x-48)*.74,49+(y-49)*.74) for x,y in p],230)
        for i in range(0,len(p)-1,2):
            path(metal,[p[i],p[i+1]],230,2)
            x,y=p[i]
            path(soot,[(x,y),(x+(x-48)*.33,y+(y-49)*.34)],180,2)
        if v in (1,3,5):
            poly(hole,[(26,49),(36,38),(44,48),(35,60)],245)
        else:
            poly(hole,[(57,51),(65,45),(71,55),(61,65)],250)
        cracks([[(29,31),(21,24),(16,15)],
                [(69,41),(78,36),(83,30)],
                [(40,67),(32,77),(25,82)],
                [(67,61),(73,73),(80,77)]],180,135)
        for i in range(9):
            dot(metal,rng.randrange(18,82),rng.randrange(17,81),
                rng.choice((1,2)),rng.randrange(75,165))

    elif family=="fighter":
        clusters=[[(47,47)],[(39,44),(61,56)],
                  [(39,38),(58,38),(48,59)],
                  [(41,38),(55,52)],
                  [(31,33),(58,58)],
                  [(33,44),(49,50),(65,60)]]
        for i,(x,y) in enumerate(clusters[v]):
            dot(soot,x,y,5 if v in (0,3) else 4,195)
            dot(hole,x,y,2,250)
            dot(metal,x+2,y-2,4,160)
            dot(hole,x,y,2,240)
            if i==0 or v%2==0:
                cracks([[(x,y),(x-9,y-6),(x-15,y-13)],
                        [(x,y),(x+10,y-7),(x+15,y-15)],
                        [(x,y),(x+6,y+9),(x+10,y+16)]],140,95)
        if v==4:
            path(soot,[(26,30),(42,46),(58,63),(70,71)],120,3)
            path(metal,[(27,29),(45,45),(60,63),(71,71)],110,1)

    # Deliberate fine breakup inside EXISTING authored material only.
    # Holes retain their sharp alpha boundaries and never become soft ovals.
    for layer in range(3):
        im=fields[layer].copy()
        fields[layer]=im
        pixels=im.load()
        for i in range(150):
            x=rng.randrange(11,85);y=rng.randrange(11,85)
            if pixels[x,y]>85 and rng.random()<.42:
                pixels[x,y]=max(0,min(255,pixels[x,y]+
                            rng.choice((-47,-27,19,31))))
    channels=[im.tobytes() for im in fields]
    rgba=bytearray(SIZE*SIZE*4)
    for i in range(SIZE*SIZE):
        rgba[i*4]=channels[0][i]
        rgba[i*4+1]=channels[1][i]
        rgba[i*4+2]=channels[2][i]
        rgba[i*4+3]=255-channels[3][i]
    return Image.frombytes("RGBA",(SIZE,SIZE),bytes(rgba))

def generate(destination,preview=None):
    destination.mkdir(parents=True,exist_ok=True)
    report={}
    gallery=Image.new("RGB",(918,1046),(15,18,25))
    painter=ImageDraw.Draw(gallery)
    for row,family in enumerate(FAMILIES):
        atlas=Image.new("RGBA",(576,96),(0,0,0,255))
        frames=[]
        for variant in range(6):
            image=create(family,variant)
            atlas.paste(image,(variant*96,0))
            show=Image.new("RGB",(96,96),(16,19,26))
            px=image.load();out=show.load()
            colored=0;cut=0
            for y in range(96):
                for x in range(96):
                    r,g,b,a=px[x,y]
                    if max(r,g,b,255-a)>18:colored+=1
                    if a<127:cut+=1
                    # Human-readable mask-field preview, NOT game hull art.
                    out[x,y]=(min(255,int(r*.45+g*.95+b*.43+(255-a)*.35)),
                              min(255,int(r*.32+g*.46+b*.77+(255-a)*.09)),
                              min(255,int(r*.30+g*.13+b*.93+(255-a)*.12)))
            frames.append({"variant":variant+1,"coloredPixels":colored,"cutPixels":cut})
            gallery.paste(show.resize((118,118),Image.Resampling.NEAREST),
                          (145+variant*128,8+row*144))
            painter.text((145+variant*128,127+row*144),
                         "v"+str(variant+1),fill=(180,185,200))
        atlas.save(destination/(family+".png"),optimize=True)
        painter.text((6,59+row*144),family.upper(),fill="white")
        report[family]=frames
    (destination/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
    if preview:
        Path(preview).parent.mkdir(parents=True,exist_ok=True)
        gallery.save(preview,optimize=True)
    for family,frames in report.items():
        print(family,"96x96 x6",[(r["coloredPixels"],r["cutPixels"])
                                  for r in frames])
    return report

if __name__=="__main__":
    arg=argparse.ArgumentParser()
    arg.add_argument("--out",type=Path,default=DEFAULT)
    arg.add_argument("--preview",type=Path,default=None)
    args=arg.parse_args()
    generate(args.out,args.preview)
