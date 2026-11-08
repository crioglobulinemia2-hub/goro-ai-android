#!/usr/bin/env python3
"""Build a visible, animated GORO humanoid GLB without third-party packages."""
import json, math, pathlib, struct
ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/"app/src/main/assets/goro/goro_rigged.glb"
DATA=bytearray(); VIEWS=[]; ACCESS=[]; MESHES=[]; NODES=[]
COLORS={"shell":[0.16,0.22,0.29,1],"steel":[0.49,0.61,0.69,1],"trim":[0.055,0.075,0.11,1],"glass":[0.025,0.07,0.10,1],"cyan":[0.12,0.9,1,1],"white":[0.80,0.93,0.99,1],"copper":[0.83,0.38,0.21,1],"shade":[0.095,0.13,0.17,1]}
MATERIALS=[{"name":n,"pbrMetallicRoughness":{"baseColorFactor":c,"metallicFactor":0,"roughnessFactor":0.8},"doubleSided":True,"extensions":{"KHR_materials_unlit":{}}} for n,c in COLORS.items()]
MAT={n:i for i,n in enumerate(COLORS)}
def chunk(payload,target=None):
 while len(DATA)%4:DATA.append(0)
 off=len(DATA);DATA.extend(payload)
 view={"buffer":0,"byteOffset":off,"byteLength":len(payload)}
 if target:view["target"]=target
 VIEWS.append(view);return len(VIEWS)-1
def accessor(view,typ,comp,count,minimum=None,maximum=None):
 item={"bufferView":view,"componentType":comp,"count":count,"type":typ}
 if minimum is not None:item.update(min=minimum,max=maximum)
 ACCESS.append(item);return len(ACCESS)-1
def mesh(name,vertices,faces,material):
 pos=accessor(chunk(b"".join(struct.pack("<fff",*v) for v in vertices),34962),"VEC3",5126,len(vertices),
  [min(v[i] for v in vertices) for i in range(3)],[max(v[i] for v in vertices) for i in range(3)])
 inds=[j for face in faces for j in face]
 ix=accessor(chunk(b"".join(struct.pack("<H",j) for j in inds),34963),"SCALAR",5123,len(inds),[min(inds)],[max(inds)])
 MESHES.append({"name":name,"primitives":[{"attributes":{"POSITION":pos},"indices":ix,"material":MAT[material]}]})
 return len(MESHES)-1
def ellipsoid(rx,ry,rz):
 rings=9;sides=16;vertices=[];faces=[]
 for j in range(rings+1):
  t=math.pi*j/rings
  for i in range(sides):
   p=2*math.pi*i/sides
   vertices.append((rx*math.sin(t)*math.cos(p),ry*math.cos(t),rz*math.sin(t)*math.sin(p)))
 for j in range(rings):
  for i in range(sides):
   a=j*sides+i;b=j*sides+(i+1)%sides;c=(j+1)*sides+i;d=(j+1)*sides+(i+1)%sides
   faces.extend([(a,c,b),(b,c,d)])
 return vertices,faces
def box(x,y,z):
 vs=[(a*x/2,b*y/2,c*z/2) for a,b,c in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
 fs=[(0,2,1),(0,3,2),(4,5,6),(4,6,7),(0,1,5),(0,5,4),(2,3,7),(2,7,6),(1,2,6),(1,6,5),(0,4,7),(0,7,3)]
 return vs,fs
def node(name,geometry=None,material="shell",translation=(0,0,0),parent=None):
 item={"name":name,"translation":list(translation)}
 if geometry:item["mesh"]=mesh(name,*geometry,material)
 NODES.append(item);index=len(NODES)-1
 if parent is not None:NODES[parent].setdefault("children",[]).append(index)
 return index
def egg(name,size,material,pos,parent):return node(name,ellipsoid(*size),material,pos,parent)
def block(name,size,material,pos,parent):return node(name,box(*size),material,pos,parent)
root=node("root");torso=node("spine",parent=root)
egg("Back shell",(.62,.73,.37),"shell",(0,1.45,0),torso)
egg("Chest armor",(.53,.56,.37),"steel",(0,1.49,.1),torso)
egg("Chest display",(.36,.23,.06),"glass",(0,1.56,.48),torso)
egg("Power core",(.15,.15,.038),"cyan",(0,1.56,.531),torso)
egg("Core white",(.055,.055,.042),"white",(0,1.56,.56),torso)
for s in (-1,1):egg("Vent",(.04,.14,.03),"shade",(s*.31,1.28,.42),torso)
hip=node("pelvis",parent=root)
egg("Waist",(.41,.22,.27),"trim",(0,.85,0),hip)
egg("Hip armor",(.50,.31,.32),"steel",(0,.68,0),hip)
neck=node("neck",parent=torso)
block("Neck joint",(.24,.29,.20),"trim",(0,2.05,0),neck)
head=node("head",translation=(0,2.2,0),parent=neck)
egg("Head shell",(.51,.48,.42),"steel",(0,.18,0),head)
egg("Face plate",(.45,.35,.17),"glass",(0,.17,.31),head)
egg("Forehead line",(.26,.025,.023),"copper",(0,.41,.45),head)
for s in (-1,1):
 egg("Temple",(.083,.22,.15),"shell",(s*.49,.18,0),head)
 egg("Antenna",(.036,.16,.034),"cyan",(s*.40,.63,0),head)
eyes=[]
for s in (-1,1):
 eye=node("eye_left" if s<0 else "eye_right",parent=head,translation=(s*.175,.24,.47))
 egg("Eye diode",(.11,.075,.025),"cyan",(0,0,0),eye)
 egg("Eye highlight",(.045,.025,.028),"white",(-.025,.023,.008),eye)
 eyes.append(eye)
mouth=node("mouth",parent=head,translation=(0,.008,.47))
for i in range(9):block("Mouth LED",(.026,.018,.014),"cyan",((i-4)*.036,0,0),mouth)
shoulders=[]
for s in (-1,1):
 shoulder=node("left_shoulder" if s<0 else "right_shoulder",translation=(s*.73,1.82,0),parent=torso)
 shoulders.append(shoulder)
 egg("Shoulder armor",(.31,.31,.32),"steel",(0,-.06,0),shoulder)
 egg("Upper arm",(.21,.45,.24),"shell",(s*.08,-.4,0),shoulder)
 elbow=node("left_elbow" if s<0 else "right_elbow",translation=(s*.12,-.75,0),parent=shoulder)
 egg("Elbow joint",(.18,.18,.18),"copper",(0,0,0),elbow)
 egg("Forearm armor",(.23,.34,.23),"steel",(0,-.32,0),elbow)
 wrist=node("left_wrist" if s<0 else "right_wrist",translation=(0,-.67,0),parent=elbow)
 egg("Hand",(.21,.21,.18),"shade",(0,0,0),wrist)
 for j in range(3):block("Finger",(.07,.17,.12),"steel",((j-1)*.115,-.19,.03),wrist)
 leg=node("left_hip" if s<0 else "right_hip",translation=(s*.30,.61,0),parent=hip)
 egg("Upper leg",(.25,.40,.25),"shell",(0,-.27,0),leg)
 knee=node("left_knee" if s<0 else "right_knee",translation=(0,-.53,0),parent=leg)
 egg("Knee cap",(.21,.20,.22),"copper",(0,0,.04),knee)
 egg("Lower leg",(.22,.34,.23),"steel",(0,-.34,0),knee)
 egg("Boot",(.25,.13,.35),"trim",(0,-.66,.12),knee)
TIMES=[0.,.45,.9,1.35,2.,2.8,3.2,3.6,4.1,4.6,5.2,5.8,6.]
SAMPLES=[];CHANNELS=[]
def animate(index,path,values):
 t=accessor(chunk(b"".join(struct.pack("<f",x) for x in TIMES)),"SCALAR",5126,len(TIMES),[0.],[6.])
 fmt="<"+("f"*len(values[0]))
 v=accessor(chunk(b"".join(struct.pack(fmt,*x) for x in values)),"VEC4" if path=="rotation" else "VEC3",5126,len(values))
 SAMPLES.append({"input":t,"output":v,"interpolation":"LINEAR"})
 CHANNELS.append({"sampler":len(SAMPLES)-1,"target":{"node":index,"path":path}})
def qz(angle):
 a=math.radians(angle/2);return [0,0,math.sin(a),math.cos(a)]
def qx(angle):
 a=math.radians(angle/2);return [math.sin(a),0,0,math.cos(a)]
animate(shoulders[0],"rotation",[qz(v) for v in (0,1,0,-2,0,0,3,8,17,5,0,0,0)])
animate(shoulders[1],"rotation",[qz(v) for v in (0,-1,0,2,0,-12,-35,-55,-55,-12,0,0,0)])
animate(head,"rotation",[qx(v) for v in (0,-2,0,2,0,0,-2,0,2,0,0,0,0)])
for eye in eyes:animate(eye,"scale",[[1,v,1] for v in (1,1,.08,1,1,1,1,1,.08,1,1,1,1)])
animate(mouth,"scale",[[1,v,1] for v in (.9,1.5,.8,1.2,.9,.9,1.6,.9,1.4,1,.9,1,.9)])
scene={"asset":{"version":"2.0","generator":"GORO 3D animated Android avatar"},"extensionsUsed":["KHR_materials_unlit"],"scene":0,
 "scenes":[{"nodes":[root]}],"nodes":NODES,"meshes":MESHES,"materials":MATERIALS,
 "buffers":[{"byteLength":0}],"bufferViews":VIEWS,"accessors":ACCESS,
 "animations":[{"name":"GORO idle and wave","samplers":SAMPLES,"channels":CHANNELS}]}
DATA.extend(b"\x00"*((-len(DATA))%4))
scene["buffers"][0]["byteLength"]=len(DATA)
json_bytes=json.dumps(scene,ensure_ascii=True,separators=(",",":")).encode()
json_bytes+=b" "*((-len(json_bytes))%4)
glb=b"glTF"+struct.pack("<II",2,12+8+len(json_bytes)+8+len(DATA))+struct.pack("<I4s",len(json_bytes),b"JSON")+json_bytes+struct.pack("<I4s",len(DATA),b"BIN\0")+DATA
DEST.parent.mkdir(parents=True,exist_ok=True);DEST.write_bytes(glb)
print("GORO GLB",len(glb),"bytes",len(NODES),"nodes",len(CHANNELS),"animations")
