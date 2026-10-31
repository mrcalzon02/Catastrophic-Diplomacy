#!/usr/bin/env python3
"""Build separated thrusterless missile body/6-frame plume atlases from original hi-res FX cutouts.
Originals are never overwritten; every mapping references the source crop/ID and original SHA256.
"""
import argparse, hashlib, io, json, math, zipfile
from PIL import Image
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('source',type=Path,help='Source-verified approved crops ZIP (never overwritten)')
parser.add_argument('output',type=Path,help='Destination directory for generated atlases and pack')
options=parser.parse_args()
SOURCE=options.source
OUTPUT=options.output
OUTPUT.mkdir(parents=True,exist_ok=True)

with zipfile.ZipFile(SOURCE) as z:
    manifest=json.loads(z.read('assets/effects/library_slices/approved_manifest.json'))
    selected=[]
    for entry in manifest['sprites']:
        if entry['category']!='guided':continue
        item=entry['id'];r=entry['row'];c=entry['column']
        if item.startswith('01_') and 4<=r<=6:
            selected.append((entry,'standard' if r<6 else 'micro',.30))
        elif item.startswith('03_') and ((r==0 and c<6) or (r==1 and c in (0,1,3,4,5))):
            selected.append((entry,'heavy' if r==1 else 'torpedo',.36))
        elif item.startswith('04_') and (r==6 and c in (1,2,3,4,5)):
            selected.append((entry,'heavy',.37))
    # Keep a stable, contiguous family ordering for one texture region lookup per missile.
    selected.sort(key=lambda a:({'micro':0,'standard':1,'heavy':2,'torpedo':3}[a[1]],a[0]['id']))
    print('selected count=',len(selected),'types=',{k:sum(1 for x in selected if x[1]==k) for k in ('micro','standard','heavy','torpedo')})
    BODY_W,BODY_H=256,128
    PLUME_W,PLUME_H=128,96
    COLS=8;FRAME_COUNT=6
    rows=math.ceil(len(selected)/COLS)
    body_atlas=Image.new('RGBA',(COLS*BODY_W,rows*BODY_H),(0,0,0,0))
    # pack six frames side by side for each body ID, with 12 columns (2 sprites per atlas row)
    PCOLS=12;PROWS=math.ceil((len(selected)*FRAME_COUNT)/PCOLS)
    plume_atlas=Image.new('RGBA',(PCOLS*PLUME_W,PROWS*PLUME_H),(0,0,0,0))
    meta=[]
    for index,(record, family,portion) in enumerate(selected):
        image=Image.open(io.BytesIO(z.read('assets/'+record['path']))).convert('RGBA')
        # All selected source missiles travel right. Determine attached motor/metal seam
        # from opaque mid-height pixels near the geometric rear, not from a regular grid.
        W,H=image.size
        lo=max(2,int(W*portion*.66)); hi=min(W-3,int(W*portion*1.32))
        candidates=[]
        for x in range(lo,hi):
            weight=0
            for y in range(max(0,H//2-H//4),min(H,H//2+H//4)):
                R,G,B,A=image.getpixel((x,y))
                if A<55: continue
                # metal / solid body: lower luminance, less luminous saturated color
                mx=max(R,G,B); mn=min(R,G,B)
                if mx<220 and (mx-mn)<135:weight+=A/255
            candidates.append((weight,x))
        candidates.sort(reverse=True)
        cut=round(W*portion)
        if candidates and candidates[0][0]>=3:
            cut=int(max(W*.19,min(W*.53,candidates[0][1]-3)))
        # Keep a few full resolution pixels on each side of the nozzle boundary,
        # but never bake the long flame into a coasting missile.
        body=image.copy()
        pix=body.load()
        for x in range(min(W,cut+4)):
            fade=max(0.,min(1.,(x-cut)/4.0))
            for y in range(H):
                R,G,B,A=pix[x,y];pix[x,y]=(R,G,B,int(A*fade))
        bnd=body.getbbox()
        if bnd is None:raise ValueError('no body '+record['id'])
        body=body.crop(bnd)
        flame=image.copy();pix=flame.load()
        for x in range(max(0,cut-5),W):
            fade=max(0.,min(1.,(cut-x)/5.0))
            for y in range(H):
                R,G,B,A=pix[x,y]; pix[x,y]=(R,G,B,int(A*fade))
        pb=flame.getbbox()
        if pb is None:
            # No actual plume in art: use retained translucent colored nozzle tip,
            # but never invent flame imagery. This variant gets no authored plume.
            flame=Image.new('RGBA',(1,1),(0,0,0,0))
            plume_present=False
        else:
            flame=flame.crop(pb)
            plume_present=bool(flame.width>6 and flame.getchannel('A').getextrema()[1]>30)
        if body.width>BODY_W-10 or body.height>BODY_H-10:
            # Runtime atlas may downscale; source cutout and exact origin are in the archive.
            body.thumbnail((BODY_W-10,BODY_H-10),Image.Resampling.LANCZOS)
        bx=(index%COLS)*BODY_W+(BODY_W-body.width)//2
        by=(index//COLS)*BODY_H+(BODY_H-body.height)//2
        body_atlas.alpha_composite(body,(bx,by))
        # Flame is visually behind its parent and scales during actual propulsion states.
        for f in range(FRAME_COUNT):
            if not plume_present:continue
            pulse=(0.92,1.08,1.03,.87,1.14,.98)[f]
            length=(.93,1.08,.97,1.13,1.02,.89)[f]
            target_w=max(1,min(PLUME_W-8,round(flame.width*min(1.,(PLUME_W-8)/flame.width)*length)))
            target_h=max(1,min(PLUME_H-8,flame.height))
            plume=flame.resize((target_w,target_h),Image.Resampling.LANCZOS)
            alpha=plume.getchannel('A').point(lambda v:int(min(255,v*pulse)))
            plume.putalpha(alpha)
            slot=index*FRAME_COUNT+f
            px=(slot%PCOLS)*PLUME_W+(PLUME_W-plume.width)//2
            py=(slot//PCOLS)*PLUME_H+(PLUME_H-plume.height)//2
            plume_atlas.alpha_composite(plume,(px,py))
        meta.append(dict(index=index,source_id=record['id'],source_sha256=record['sha256'],
            family=family,original_size=[W,H],source_bbox=record['source_bbox'],
            split_x_original=cut,body_width=body.width,body_height=body.height,
            plume_width=flame.width,plume_height=flame.height,has_plume=plume_present))
    files={
        'effects/ordnance/library/missile_bodies.png':body_atlas,
        'effects/ordnance/library/missile_plumes.png':plume_atlas,
    }
    result=dict(schema='cd-ordnance-separated-v1',source_archive_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        count=len(meta),body_columns=COLS,body_cell=[BODY_W,BODY_H],plume_columns=PCOLS,
        plume_cell=[PLUME_W,PLUME_H],plume_frames=FRAME_COUNT,
        variant_families={k:[x['index'] for x in meta if x['family']==k] for k in ('micro','standard','heavy','torpedo')},
        variants=meta)
    (OUTPUT/'missile_manifest.json').write_text(json.dumps(result,indent=2)+'\n')
    pngs={}
    for path,im in files.items():
        dest=OUTPUT/Path(path).name;im.save(dest,optimize=True)
        pngs[path]=dest.read_bytes()
    dst=OUTPUT/'Catastrophic_Diplomacy_Separated_Missiles_v1.zip'
    with zipfile.ZipFile(dst,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as pack:
        for path,png in pngs.items():pack.writestr(path,png)
        pack.writestr('effects/ordnance/library/missile_manifest.json',json.dumps(result,indent=2))
    print('atlas',body_atlas.size,plume_atlas.size,'zip',dst.stat().st_size,'checksum',hashlib.sha256(dst.read_bytes()).hexdigest())
