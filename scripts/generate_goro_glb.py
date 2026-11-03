#!/usr/bin/env python3
"""Build GORO's original, animated, skinned glTF 2.0 robot asset, stdlib only.

A parametric articulated assistant, NOT a photo, image sequence, placeholder or
repackaged third-party character. PBR materials, 2 skins-attached mesh nodes,
10 facial morph targets and six authored skeletal animation clips.
Visual fidelity must still be audited on the target Android hardware.
"""
import json
import math
import pathlib
import struct
import zlib

ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/"app/src/main/assets/goro/goro_rigged.glb"

JOINTS=[
 ("root",None,(0,0,0)),
 ("pelvis","root",(0,.90,0)),
 ("spine","pelvis",(0,.25,0)),
 ("chest","spine",(0,.34,0)),
 ("neck","chest",(0,.27,0)),
 ("head","neck",(0,.25,0)),
 ("left_shoulder","chest",(-.41,.08,0)),
 ("left_elbow","left_shoulder",(-.29,-.33,0)),
 ("left_wrist","left_elbow",(-.22,-.31,0)),
 ("left_hand","left_wrist",(-.03,-.105,0)),
 ("right_shoulder","chest",(.41,.08,0)),
 ("right_elbow","right_shoulder",(.29,-.33,0)),
 ("right_wrist","right_elbow",(.22,-.31,0)),
 ("right_hand","right_wrist",(.03,-.105,0)),
 ("left_hip","pelvis",(-.19,-.13,0)),
 ("left_knee","left_hip",(-.02,-.40,.025)),
 ("left_ankle","left_knee",(-.015,-.32,-.02)),
 ("right_hip","pelvis",(.19,-.13,0)),
 ("right_knee","right_hip",(.02,-.40,.025)),
 ("right_ankle","right_knee",(.015,-.32,-.02)),
]
for side in ("left","right"):
 for finger in range(5):
  JOINTS.append((f"{side}_finger_{finger}",f"{side}_hand",((finger-2)*.030,-.085, .01)))
NAME={name:i for i,(name,_,_) in enumerate(JOINTS)}
G={}
for name,parent,pos in JOINTS:
 origin=G[parent] if parent else (0.,0.,0.)
 G[name]=tuple(origin[i]+pos[i] for i in range(3))

MAT={
 "pearl_white":((.84,.91,.95,1),.53,.22,(0,0,0)),
 "titanium":((.44,.57,.65,1),.81,.19,(0,0,0)),
 "graphite":((.024,.057,.083,1),.42,.29,(0,0,0)),
 "optical_blue":((.05,.53,.98,1),.28,.18,(0.0,.51,.9)),
 "bronze":((.60,.36,.21,1),.69,.27,(0,0,0)),
 "matte_white":((.67,.78,.82,1),.26,.38,(0,0,0)),
}
PARTS={k:{"p":[],"n":[],"j":[],"w":[],"idx":[]} for k in MAT}
FACE={"p":[],"n":[],"j":[],"w":[],"idx":[],"groups":[],"xrest":[]}
def vec(a,b=None):
 n=math.sqrt(sum(v*v for v in a))
 return tuple(v/n for v in a) if n else (0.,0.,1.)
def add_vertex(part,point,norm,joint,group=""):
 part["p"].extend(point);part["n"].extend(norm)
 part["j"].extend([NAME[joint],0,0,0]);part["w"].extend([1.,0.,0.,0.])
 if part is FACE:
  part["groups"].append(group)
  part["xrest"].append(point)
 return len(part["p"])//3-1
def ellipsoid(center,radius,joint,mat,steps=28,rings=16):
 part=PARTS[mat];start=len(part["p"])//3
 for i in range(rings+1):
  a=math.pi*i/rings
  for q in range(steps+1):
   b=math.pi*2*q/steps
   u=math.sin(a)*math.cos(b);v=math.cos(a);w=math.sin(a)*math.sin(b)
   p=(center[0]+radius[0]*u,center[1]+radius[1]*v,center[2]+radius[2]*w)
   n=vec((u/max(.0001,radius[0]),v/max(.0001,radius[1]),w/max(.0001,radius[2])))
   add_vertex(part,p,n,joint)
 for i in range(rings):
  for q in range(steps):
   k=start+i*(steps+1)+q
   part["idx"].extend((k,k+steps+1,k+1,k+1,k+steps+1,k+steps+2))

def tube(points,radius,joint,mat,group=None,edges=10):
 part=FACE if group is not None else PARTS[mat]
 start=len(part["p"])//3
 for k,center in enumerate(points):
  before=points[max(0,k-1)];after=points[min(len(points)-1,k+1)]
  tangent=vec(tuple(after[i]-before[i] for i in range(3)))
  cross=vec((tangent[1],-tangent[0],.01))
  b=vec((tangent[1]*cross[2]-tangent[2]*cross[1],
         tangent[2]*cross[0]-tangent[0]*cross[2],
         tangent[0]*cross[1]-tangent[1]*cross[0]))
  for q in range(edges):
   a=2*math.pi*q/edges
   n=tuple(cross[i]*math.cos(a)+b[i]*math.sin(a) for i in range(3))
   point=tuple(center[i]+radius*n[i] for i in range(3))
   add_vertex(part,point,n,joint,group or "")
 for k in range(len(points)-1):
  for q in range(edges):
   a=start+k*edges+q;b=start+k*edges+(q+1)%edges
   part["idx"].extend((a,a+edges,b,b,a+edges,b+edges))

def link(a,b,diameter,joint,mat):
 start=G[a];end=G[b]
 center=tuple((start[i]+end[i])/2 for i in range(3))
 length=math.sqrt(sum((start[i]-end[i])**2 for i in range(3)))
 # Robotic casings with differentiated joint collars; centerline follows the rig.
 r=(diameter,length*.64,diameter)
 ellipsoid(center,r,joint,mat,24,16)

# Torso, armor contours, articulated shoulders, torso core and pelvis.
ellipsoid((0,1.37,-.005),(.44,.48,.245),"chest","pearl_white")
ellipsoid((0,1.47,.231),(.31,.21,.039),"chest","titanium")
ellipsoid((0,1.48,.271),(.16,.145,.037),"chest","graphite")
ellipsoid((0,1.50,.307),(.073,.080,.025),"chest","optical_blue")
ellipsoid((0,1.67,0),(.30,.105,.205),"chest","matte_white")
ellipsoid((0,.95,0),(.30,.23,.22),"pelvis","pearl_white")
ellipsoid((0,1.72,.01),(.115,.155,.11),"neck","titanium")
ellipsoid((0,1.15,.23),(.20,.12,.034),"spine","bronze")
# Layered chest-edge light pipes, articulated armor seams and machined fasteners.
for side in (-1,1):
 tube([(side*.12,1.81,.182),(side*.29,1.72,.204),
       (side*.34,1.52,.199),(side*.31,1.35,.203)],.007,"chest","optical_blue",edges=12)
 tube([(side*.35,1.62,.126),(side*.38,1.46,.125),
       (side*.34,1.30,.119)],.013,"chest","titanium",edges=12)
 ellipsoid((side*.255,1.63,.236),(.027,.022,.013),"chest","graphite",16,10)
 ellipsoid((side*.255,1.63,.247),(.010,.010,.005),"chest","optical_blue",14,9)
 tube([(side*.27,1.26,.192),(side*.19,1.18,.229),
       (side*.10,1.17,.258)],.012,"spine","titanium",edges=12)
for radius,material,line in ((.20,"graphite",.012),(.174,"optical_blue",.010)):
 ring=[(radius*math.cos(2*math.pi*i/40),
        1.48+radius*.81*math.sin(2*math.pi*i/40),.306+line)
       for i in range(41)]
 tube(ring,.0065,"chest",material,edges=10)
for x in (-.27,-.21,-.15,.15,.21,.27):
 tube([(x,1.04,.212),(x,1.00,.229)],.008,"pelvis","graphite",edges=10)
# Back plate, spine articulation and ventilated black graphite details.
ellipsoid((0,1.38,-.252),(.265,.32,.045),"chest","titanium",30,18)
for y in (1.20,1.28,1.36,1.44,1.52):
 tube([(-.16,y,-.292),(.16,y,-.292)],.008,"chest","graphite",edges=10)
# Embossed vector GORO logotype on the front armor, actual 3D geometry.
word="GORO"
stroke={
 "G":[[(.43,.50),(.25,.53),(.09,.46),(.08,.25),(.26,.09),(.49,.12),(.49,.30),(.35,.30)]],
 "O":[[(.11,.28),(.17,.47),(.48,.49),(.56,.27),(.47,.08),(.17,.07),(.11,.28)]],
 "R":[[(.10,.07),(.10,.50),(.45,.50),(.54,.36),(.41,.27),(.11,.27)],
      [(.34,.27),(.57,.07)]]
}
for j,letter in enumerate(word):
 for line in stroke[letter]:
  pts=[(-.135+j*.086+x*.061,1.25+y*.056,.286) for x,y in line]
  tube(pts,.0045,"spine","optical_blue",edges=8)
for side in ("left","right"):
 s=-1 if side=="left" else 1
 shoulder=G[f"{side}_shoulder"];elbow=G[f"{side}_elbow"];wrist=G[f"{side}_wrist"];hand=G[f"{side}_hand"]
 ellipsoid(shoulder,(.165,.176,.164),f"{side}_shoulder","bronze")
 link(f"{side}_shoulder",f"{side}_elbow",.128,f"{side}_shoulder","pearl_white")
 ellipsoid(elbow,(.112,.114,.112),f"{side}_elbow","graphite")
 link(f"{side}_elbow",f"{side}_wrist",.112,f"{side}_elbow","titanium")
 ellipsoid(wrist,(.10,.09,.095),f"{side}_wrist","bronze")
 ellipsoid((elbow[0],elbow[1],elbow[2]+.09),(.071,.071,.027),f"{side}_elbow","titanium",22,13)
 ellipsoid((elbow[0],elbow[1],elbow[2]+.111),(.034,.035,.011),f"{side}_elbow","optical_blue",20,12)
 # Recessed servo cooling channels and forearm optical trim.
 tube([(shoulder[0]+s*.035,shoulder[1]-.14,shoulder[2]+.116),
       (elbow[0]-s*.032,elbow[1]+.06,elbow[2]+.098)],
       .011,f"{side}_shoulder","titanium",edges=11)
 tube([(elbow[0]-s*.035,elbow[1]-.03,elbow[2]+.11),
       (wrist[0]-s*.045,wrist[1]+.09,wrist[2]+.10)],
       .011,f"{side}_elbow","optical_blue",edges=11)
 for k in (0,1,2):
  t=(k+1)/4
  px=elbow[0]*(1-t)+wrist[0]*t
  py=elbow[1]*(1-t)+wrist[1]*t
  ellipsoid((px,py,elbow[2]+.119),(.025,.013,.009),
            f"{side}_elbow","graphite",16,9)
 ellipsoid((hand[0],hand[1]-.06,hand[2]+.02),(.112,.105,.075),f"{side}_hand","pearl_white")
 for i in range(5):
  p=G[f"{side}_finger_{i}"]
  tube([p,(p[0]+s*.013,p[1]-.078,p[2]+.045)],.022,f"{side}_finger_{i}","titanium")
 hip=G[f"{side}_hip"];knee=G[f"{side}_knee"];ankle=G[f"{side}_ankle"]
 ellipsoid(hip,(.19,.16,.18),f"{side}_hip","bronze")
 link(f"{side}_hip",f"{side}_knee",.175,f"{side}_hip","pearl_white")
 ellipsoid(knee,(.137,.13,.138),f"{side}_knee","graphite")
 link(f"{side}_knee",f"{side}_ankle",.145,f"{side}_knee","pearl_white")
 ellipsoid((ankle[0],ankle[1]+.035,ankle[2]),(.145,.14,.16),f"{side}_ankle","titanium")
 ellipsoid((ankle[0],.075,ankle[2]+.085),(.19,.075,.275),f"{side}_ankle","pearl_white")
 # Fiber channel, rotating joint details and graphite sole.
 ellipsoid((knee[0],knee[1],knee[2]+.132),(.072,.055,.026),
           f"{side}_knee","titanium",20,12)
 ellipsoid((knee[0],knee[1],knee[2]+.155),(.039,.034,.016),
           f"{side}_knee","optical_blue",20,12)
 tube([(knee[0]+s*.082,knee[1]-.13,knee[2]+.113),
       (ankle[0]+s*.072,ankle[1]+.12,ankle[2]+.122)],
       .0085,f"{side}_knee","optical_blue",edges=10)
 ellipsoid((ankle[0],.026,ankle[2]+.087),(.197,.035,.280),
           f"{side}_ankle","graphite",26,12)
 for t in (-1,0,1):
  tube([(ankle[0]+t*.052,.139,ankle[2]+.265),
        (ankle[0]+t*.052,.145,ankle[2]+.308)],
       .006,f"{side}_ankle","titanium",edges=9)

# Helmet, face plate, antenna, side headphones and expressive optical face.
head=G["head"];hy=head[1]
ellipsoid((0,hy,.02),(.36,.34,.30),"head","pearl_white",34,20)
ellipsoid((0,hy-.005,.271),(.300,.243,.086),"head","graphite",34,20)
ellipsoid((0,hy+.22,-.05),(.295,.055,.24),"head","titanium")
ellipsoid((0,hy+.35,0),(.068,.052,.064),"head","optical_blue")
# Precision visor bezels, temple vents and titanium head crown ribbons.
bezel=[(.288*math.cos(2*math.pi*i/56),hy+.015+.228*math.sin(2*math.pi*i/56),.324)
       for i in range(57)]
tube(bezel,.009,"head","titanium",edges=12)
for s in (-1,1):
 for k in range(4):
  y=hy-.135+k*.035
  tube([(s*.257,y,.290),(s*.298,y+.011,.260)],.005,"head","bronze",edges=9)
 tube([(s*.268,hy+.233,.201),(s*.32,hy+.135,.165)],.010,"head","optical_blue",edges=10)
for s in (-1,1):
 ellipsoid((s*.347,hy+.02,.0),(.074,.13,.13),"head","titanium")
 ellipsoid((s*.393,hy+.025,.037),(.025,.088,.083),"head","optical_blue")
for s,group in ((-1,"eye_l"),(1,"eye_r")):
 pts=[]
 for i in range(13):
  t=i/12
  x=s*.143+s*.086*(t-.5)*2
  y=hy+.04+.058*math.sin(math.pi*t)
  pts.append((x,y,.356))
 tube(pts,.013,"head","optical_blue",group)
# Irises, brows and two separate mouth lips are independent morphable geometry.
for s,group in ((-1,"brow_l"),(1,"brow_r")):
 pts=[(s*(.076+i*.013),hy+.155+.017*math.sin(i/9*math.pi),.351) for i in range(10)]
 tube(pts,.009,"head","optical_blue",group)
for name,offset in (("mouth_top",.010),("mouth_bottom",-.014)):
 pts=[]
 for i in range(17):
  t=(i-8)/8
  pts.append((.130*t,hy-.121+offset+.030*t*t,.354))
 tube(pts,.0095,"head","optical_blue",name)

MORPHS=["viseme_A","viseme_E","viseme_I","viseme_O","viseme_U",
        "viseme_MBP","eye_blink_left","eye_blink_right","brow_raise","smile"]

class Buffer:
 def __init__(self):self.data=bytearray();self.views=[];self.accessors=[]
 def push(self,blob,target=None):
  while len(self.data)%4:self.data.append(0)
  offset=len(self.data);self.data.extend(blob)
  view={"buffer":0,"byteOffset":offset,"byteLength":len(blob)}
  if target is not None:view["target"]=target
  self.views.append(view);return len(self.views)-1
 def attr(self,values,fmt,typ,componentType,target=None,minmax=False):
  values=list(values);binary=struct.pack("<"+fmt*len(values),*values)
  vi=self.push(binary,target)
  arity={"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4,"MAT4":16}[typ]
  accessor={"bufferView":vi,"byteOffset":0,"componentType":componentType,
    "count":len(values)//arity,"type":typ}
  if minmax:
   accessor["min"]=[min(values[i::arity]) for i in range(arity)]
   accessor["max"]=[max(values[i::arity]) for i in range(arity)]
  self.accessors.append(accessor);return len(self.accessors)-1
buf=Buffer()
def primitive(part,material,morphs=False):
 p=part["p"]
 # glTF requires TEXCOORD_0 whenever a material references baseColorTexture.
 uv=[]
 for i in range(0,len(p),3):
  x,y,z=p[i:i+3]
  uv.extend(((x+1.75)/3.5,y/3.0))
 attrs={
  "POSITION":buf.attr(p,"f","VEC3",5126,34962,True),
  "NORMAL":buf.attr(part["n"],"f","VEC3",5126,34962),
  "TEXCOORD_0":buf.attr(uv,"f","VEC2",5126,34962),
  "JOINTS_0":buf.attr(part["j"],"H","VEC4",5123,34962),
  "WEIGHTS_0":buf.attr(part["w"],"f","VEC4",5126,34962),
 }
 obj={"attributes":attrs,"indices":buf.attr(part["idx"],"I","SCALAR",5125,34963),"material":list(MAT).index(material)}
 if morphs:
  targets=[]
  for target in MORPHS:
   deltas=[]
   for pos,group in zip(part["xrest"],part["groups"]):
    x,y,z=pos;dx=dy=dz=0.
    if target.startswith("eye_blink") and group in ("eye_l","eye_r"):
     if (target.endswith("left") and group=="eye_l") or (target.endswith("right") and group=="eye_r"):
      dy=-(y-(hy+.04))*.85
    elif target=="brow_raise" and group.startswith("brow"):dy=.055
    elif target=="smile" and group.startswith("mouth"):dy=.035*abs(x)/.13
    elif target.startswith("viseme_") and group.startswith("mouth"):
     if target=="viseme_A":dy=(-.050 if group=="mouth_bottom" else .014)
     elif target=="viseme_E":dy=(-.022 if group=="mouth_bottom" else .008);dx=x*.17
     elif target=="viseme_I":dy=(-.012 if group=="mouth_bottom" else .004);dx=x*.25
     elif target=="viseme_O":dy=(-.035 if group=="mouth_bottom" else .012);dx=-x*.24
     elif target=="viseme_U":dy=(-.020 if group=="mouth_bottom" else .010);dx=-x*.34
     elif target=="viseme_MBP":dy=(.014 if group=="mouth_bottom" else -.011)
    deltas.extend((dx,dy,dz))
   targets.append({"POSITION":buf.attr(deltas,"f","VEC3",5126,34962)})
  obj["targets"]=targets
 return obj
primitives=[primitive(part,material) for material,part in PARTS.items() if part["idx"]]
body={"name":"GORO_Rigged_Body","primitives":primitives}
face={"name":"GORO_Expressive_Face","primitives":[primitive(FACE,"optical_blue",True)],
      "weights":[0.]*len(MORPHS),"extras":{"targetNames":MORPHS}}
meshes=[body,face]
nodes=[]
for name,parent,pos in JOINTS:
 nodes.append({"name":name,"translation":list(pos),"children":[]})
for i,(name,parent,_) in enumerate(JOINTS):
 if parent:nodes[NAME[parent]]["children"].append(i)
# Skin mesh entities are world-space: skin inverse bind translations compensate the bind pose.
nodes.extend(({"name":"GORO_Body_Skinned","mesh":0,"skin":0},
              {"name":"GORO_Face_Morphs","mesh":1,"skin":0}))
bind=[]
for name,_,_ in JOINTS:
 x,y,z=G[name]
 bind.extend((1.,0.,0.,0., 0.,1.,0.,0., 0.,0.,1.,0., -x,-y,-z,1.))
skin={"name":"GORO_Skeleton","joints":list(range(len(JOINTS))),"skeleton":0,
      "inverseBindMatrices":buf.attr(bind,"f","MAT4",5126)}

def qxyz(axis,degrees):
 angle=math.radians(degrees)*.5
 return tuple(v*math.sin(angle) for v in axis)+(math.cos(angle),)
animations=[]
clipdefs=[
 ("idle",{"head":[0,2,-2,0],"left_shoulder":[0,1,0,0],"right_shoulder":[0,-1,0,0]}),
 ("listening",{"head":[0,-5,-3,0],"left_shoulder":[0,-3,0,0]}),
 ("thinking",{"head":[0,8,3,0],"right_elbow":[0,18,18,0]}),
 ("speaking",{"head":[0,4,-4,0],"left_shoulder":[0,-12,3,0],"right_shoulder":[0,14,-3,0]}),
 ("confirming",{"head":[0,11,0,0],"right_shoulder":[0,-22,-8,0]}),
 ("alert",{"head":[0,-7,5,0],"left_elbow":[0,-12,12,0]}),
]
timeinput=buf.attr([0.,.55,1.15,1.8],"f","SCALAR",5126)
for name,targets in clipdefs:
 channels=[];samplers=[]
 for bone,values in targets.items():
  rotate=qxyz((1,0,0),0)
  axis=(0,0,1) if bone=="head" else (1,0,0)
  quats=[v for degrees in values for v in qxyz(axis,degrees)]
  acc=buf.attr(quats,"f","VEC4",5126)
  si=len(samplers)
  samplers.append({"input":timeinput,"output":acc,"interpolation":"LINEAR"})
  channels.append({"sampler":si,"target":{"node":NAME[bone],"path":"rotation"}})
 animations.append({"name":name,"samplers":samplers,"channels":channels})
# Small built-in PBR albedo texture (true image, not a screenshot or face sprite).
def png():
 w=h=4
 scan=b"".join(b"\x00"+bytes((252,254,255,255)*w) for _ in range(h))
 def chunk(tag,data):
  return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
 return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(scan,9))+chunk(b"IEND",b"")
imageview=buf.push(png())
materials=[]
for name,(color,metal,rough,emission) in MAT.items():
 materials.append({
   "name":name,"pbrMetallicRoughness":{"baseColorFactor":color,
    "baseColorTexture":{"index":0},"metallicFactor":metal,"roughnessFactor":rough},
   "emissiveFactor":emission,"doubleSided":False
 })
gltf={
 "asset":{"version":"2.0","generator":"GORO procedural articulated robot authoring"},
 "scene":0,"scenes":[{"nodes":[0,len(JOINTS),len(JOINTS)+1]}],
 "nodes":nodes,"meshes":meshes,"skins":[skin],
 "buffers":[{"byteLength":0}],"bufferViews":buf.views,"accessors":buf.accessors,
 "materials":materials,"images":[{"bufferView":imageview,"mimeType":"image/png"}],
 "samplers":[{"magFilter":9729,"minFilter":9729,"wrapS":10497,"wrapT":10497}],
 "textures":[{"sampler":0,"source":0}],
 "animations":animations
}
while len(buf.data)%4:buf.data.append(0)
gltf["buffers"][0]["byteLength"]=len(buf.data)
js=json.dumps(gltf,separators=(",",":"),ensure_ascii=False).encode("utf-8")
while len(js)%4:js+=b" "
total=12+8+len(js)+8+len(buf.data)
out=b"glTF"+struct.pack("<II",2,total)+struct.pack("<I4s",len(js),b"JSON")+js
out+=struct.pack("<I4s",len(buf.data),b"BIN\x00")+buf.data
DEST.parent.mkdir(parents=True,exist_ok=True)
DEST.write_bytes(out)
print(f"GORO GLB built: {len(out)} bytes, {len(JOINTS)} joints, {len(animations)} animation clips, {len(MORPHS)} face morph targets")
