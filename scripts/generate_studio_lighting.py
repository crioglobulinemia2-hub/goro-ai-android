#!/usr/bin/env python3
"""Generate an original analytic HDR studio for GORO's metal and optical visor.
Run cmgen --format=ktx --size=128 --deploy=OUTPUT studio.hdr afterwards.
No external image, texture, credentials or network dependency.
"""
import math
from pathlib import Path
import argparse

def write_studio(path):
    w,h=512,256
    data=bytearray(b'#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 256 +X 512\n')
    for y in range(h):
        lat=math.pi*(.5-(y+.5)/h)
        row=bytearray()
        for x in range(w):
            lon=2*math.pi*((x+.5)/w-.5)
            c=[.07,.09,.13]
            for cx,cy,wx,wy,power,tint in [(-.65,.45,.3,.48,8,(1,.93,.84)),(1.3,.25,.15,.65,5,(.35,.72,1)),(-2,.6,.6,.12,4,(1,1,1))]:
                dx=abs((lon-cx+math.pi)%(2*math.pi)-math.pi)/wx;dy=abs(lat-cy)/wy
                strength=power*math.exp(-max(dx,dy)**10)
                for k in range(3):c[k]+=strength*tint[k]
            m=max(c);frac,e=math.frexp(m);scale=frac*256/m
            row.extend([min(255,int(v*scale)) for v in c]+[e+128])
        # Standard Radiance scanline RLE: literal blocks, four channels.
        data.extend((2,2,w>>8,w&255))
        for channel in range(4):
            values=row[channel::4]
            for start in range(0,w,128):
                block=values[start:start+128];data.append(len(block));data.extend(block)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('output',type=Path);args=parser.parse_args();write_studio(args.output)
