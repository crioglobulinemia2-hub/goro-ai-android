#!/usr/bin/env python3
"""Build GORO's animated, skinned glTF 2.0 robot asset, stdlib only.

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

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEST = ROOT / "build/generated-avatar/goro_procedural.glb"

JOINTS = [
    ("root", None, (0, 0, 0)),
    ("pelvis", "root", (0, 0.90, 0)),
    ("spine", "pelvis", (0, 0.25, 0)),
    ("chest", "spine", (0, 0.34, 0)),
    ("neck", "chest", (0, 0.27, 0)),
    ("head", "neck", (0, 0.25, 0)),
    ("left_shoulder", "chest", (-0.36, 0.08, 0)),
    ("left_elbow", "left_shoulder", (-0.12, -0.33, 0.035)),
    ("left_wrist", "left_elbow", (-0.025, -0.31, 0.055)),
    ("left_hand", "left_wrist", (-0.03, -0.105, 0)),
    ("right_shoulder", "chest", (0.36, 0.08, 0)),
    ("right_elbow", "right_shoulder", (0.12, -0.33, 0.035)),
    ("right_wrist", "right_elbow", (0.025, -0.31, 0.055)),
    ("right_hand", "right_wrist", (0.03, -0.105, 0)),
    ("left_hip", "pelvis", (-0.19, -0.13, 0)),
    ("left_knee", "left_hip", (-0.02, -0.40, 0.025)),
    ("left_ankle", "left_knee", (-0.015, -0.32, -0.02)),
    ("right_hip", "pelvis", (0.19, -0.13, 0)),
    ("right_knee", "right_hip", (0.02, -0.40, 0.025)),
    ("right_ankle", "right_knee", (0.015, -0.32, -0.02)),
]
for side in ("left", "right"):
    for finger in range(5):
        JOINTS.append((f"{side}_finger_{finger}", f"{side}_hand", ((finger - 2) * 0.030, -0.085, 0.01)))
NAME = {name: i for i, (name, _, _) in enumerate(JOINTS)}
G = {}
for name, parent, pos in JOINTS:
    origin = G[parent] if parent else (0.0, 0.0, 0.0)
    G[name] = tuple(origin[i] + pos[i] for i in range(3))

# Approved character materials: white-blue-gray, pearlescent, metallic cyan.
MAT = {
    "pearl_white": ((0.80, 0.85, 0.92, 1), 0.22, 0.28, (0,0,0)),
    "titanium": ((0.38, 0.44, 0.52, 1), 0.70, 0.24, (0,0,0)),
    "graphite": ((0.009, 0.015, 0.022, 1), 0.35, 0.26, (0,0,0)),
    "visor_glass": ((0.002,0.005,0.011,1),0.45,0.12,(0,0,0)),
    "optical_blue": ((0.06, 0.62, 1.0, 1), 0.03, 0.20, (0.04, 0.55, 0.95)),
    "matte_white": ((0.72, 0.78, 0.86, 1), 0.1, 0.38, (0,0,0)),
}
PARTS = {k: {"p": [], "n": [], "j": [], "w": [], "idx": []} for k in MAT}
FACE = {"p": [], "n": [], "j": [], "w": [], "idx": [], "groups": [], "xrest": []}


def vec(a, b=None):
    n = math.sqrt(sum(v * v for v in a))
    return tuple(v / n for v in a) if n else (0.0, 0.0, 1.0)


def add_vertex(part, point, norm, joint, group=""):
    part["p"].extend(point)
    part["n"].extend(norm)
    part["j"].extend([NAME[joint], 0, 0, 0])
    part["w"].extend([1.0, 0.0, 0.0, 0.0])
    if part is FACE:
        part["groups"].append(group)
        part["xrest"].append(point)
    return len(part["p"]) // 3 - 1


def ellipsoid(center, radius, joint, mat, steps=28, rings=16):
    part = PARTS[mat]
    start = len(part["p"]) // 3
    for i in range(rings + 1):
        a = math.pi * i / rings
        for q in range(steps + 1):
            b = math.pi * 2 * q / steps
            u = math.sin(a) * math.cos(b)
            v = math.cos(a)
            w = math.sin(a) * math.sin(b)
            p = (center[0] + radius[0] * u,
                 center[1] + radius[1] * v,
                 center[2] + radius[2] * w)
            n = vec((u / max(0.0001, radius[0]),
                     v / max(0.0001, radius[1]),
                     w / max(0.0001, radius[2])))
            add_vertex(part, p, n, joint)
    for i in range(rings):
        for q in range(steps):
            k = start + i * (steps + 1) + q
            part["idx"].extend((k, k + 1, k + steps + 1,
                                k + 1, k + steps + 2, k + steps + 1))


def tube(points, radius, joint, mat, group=None, edges=10):
    part = FACE if group is not None else PARTS[mat]
    start = len(part["p"]) // 3
    for k, center in enumerate(points):
        before = points[max(0, k - 1)]
        after = points[min(len(points) - 1, k + 1)]
        tangent = vec(tuple(after[i] - before[i] for i in range(3)))
        cross = vec((tangent[1], -tangent[0], 0.01))
        b = vec((tangent[1] * cross[2] - tangent[2] * cross[1],
                 tangent[2] * cross[0] - tangent[0] * cross[2],
                 tangent[0] * cross[1] - tangent[1] * cross[0]))
        for q in range(edges):
            a = 2 * math.pi * q / edges
            n = tuple(cross[i] * math.cos(a) + b[i] * math.sin(a) for i in range(3))
            point = tuple(center[i] + radius * n[i] for i in range(3))
            add_vertex(part, point, n, joint, group or "")
    for k in range(len(points) - 1):
        for q in range(edges):
            a = start + k * edges + q
            b = start + k * edges + (q + 1) % edges
            part["idx"].extend((a, b, a + edges, b, b + edges, a + edges))


# ---------------------------------------------------------------------------
# Shaped shell helpers (superellipsoid / lofted rings / extruded profiles)
# ---------------------------------------------------------------------------
def _sg_pow(v, e):
    if abs(v) < 1e-9:
        return 0.0
    return math.copysign(abs(v) ** e, v)


def superellipsoid(center, a, b, c, e1, e2, joint, mat, u_seg=24, v_seg=24):
    """Closed superellipsoid shell. e1/e2 > 1 gives rounded-box character."""
    part = PARTS[mat]
    u_seg=max(48,u_seg);v_seg=max(64,v_seg)
    start = len(part["p"]) // 3
    positions = []
    for i in range(u_seg + 1):
        u = -math.pi / 2 + math.pi * i / u_seg
        cu = math.cos(u)
        su = math.sin(u)
        u_term = _sg_pow(cu, 2.0 / e1)
        z_term = _sg_pow(su, 2.0 / e1)
        for j in range(v_seg + 1):
            v = 2.0 * math.pi * j / v_seg
            cv = math.cos(v)
            sv = math.sin(v)
            x_term = _sg_pow(cv, 2.0 / e2)
            y_term = _sg_pow(sv, 2.0 / e2)
            positions.append((
                center[0] + a * u_term * x_term,
                center[1] + b * u_term * y_term,
                center[2] + c * z_term,
            ))
    part["p"].extend(coord for p in positions for coord in p)
    count = len(positions)
    for _ in range(count):
        part["j"].extend([NAME[joint], 0, 0, 0])
        part["w"].extend([1.0, 0.0, 0.0, 0.0])
    for i in range(u_seg):
        for j in range(v_seg):
            k00 = start + i * (v_seg + 1) + j
            k01 = start + i * (v_seg + 1) + j + 1
            k10 = start + (i + 1) * (v_seg + 1) + j
            k11 = start + (i + 1) * (v_seg + 1) + j + 1
            part["idx"].extend((k00, k01, k10, k01, k11, k10))


def torus_ring(center, R, r, axis, joint, mat, seg=24, ring=24):
    """Lofted ring (torus segment) for headphone bands and armor trim."""
    part = PARTS[mat]
    start = len(part["p"]) // 3
    positions = []
    for i in range(ring):
        theta = 2.0 * math.pi * i / ring
        ct = math.cos(theta)
        st = math.sin(theta)
        for j in range(seg):
            phi = 2.0 * math.pi * j / seg
            cp = math.cos(phi)
            sp = math.sin(phi)
            if axis == 1:  # ring in XZ plane, around Y
                tx = center[0] + (R + r * cp) * ct
                ty = center[1] + r * sp
                tz = center[2] + (R + r * cp) * st
            elif axis == 0:  # ring in YZ plane, around X
                tx = center[0] + r * sp
                ty = center[1] + (R + r * cp) * ct
                tz = center[2] + (R + r * cp) * st
            else:  # axis 2, ring in XY plane, around Z
                tx = center[0] + (R + r * cp) * ct
                ty = center[1] + (R + r * cp) * st
                tz = center[2] + r * sp
            positions.append((tx, ty, tz))
    part["p"].extend(coord for p in positions for coord in p)
    count = len(positions)
    for _ in range(count):
        part["j"].extend([NAME[joint], 0, 0, 0])
        part["w"].extend([1.0, 0.0, 0.0, 0.0])
    for i in range(ring):
        i0 = i
        i1 = (i + 1) % ring
        for j in range(seg):
            j0 = j
            j1 = (j + 1) % seg
            k00 = start + i0 * seg + j0
            k01 = start + i0 * seg + j1
            k10 = start + i1 * seg + j0
            k11 = start + i1 * seg + j1
            part["idx"].extend((k00,k01,k10,k01,k11,k10) if axis==1 else (k00,k10,k01,k01,k10,k11))


def rounded_rect_frame(center,width,height,thickness,corner_r,joint,mat,seg=16):
    part=PARTS[mat]; hw=width/2; hh=height/2; cr=min(corner_r,hw,hh)
    profile=[]
    for cx,cy,base in [(hw-cr,hh-cr,0),(-hw+cr,hh-cr,90),(-hw+cr,-hh+cr,180),(hw-cr,-hh+cr,270)]:
        for i in range(seg+1):
            a=math.radians(base+90*i/seg)
            profile.append((cx+cr*math.cos(a),cy+cr*math.sin(a)))
    for sign in [-1,1]:
        start=len(part['p'])//3
        z=center[2]+sign*thickness/2
        add_vertex(part,(center[0],center[1],z),(0,0,sign),joint)
        for x,y in profile:add_vertex(part,(center[0]+x,center[1]+y,z),(0,0,sign),joint)
        for i in range(len(profile)):
            a=start+1+i;b=start+1+(i+1)%len(profile)
            part['idx'].extend((start,a,b) if sign>0 else (start,b,a))
    for i,(x,y) in enumerate(profile):
        x1,y1=profile[(i+1)%len(profile)];normal=vec((y1-y,x-x1,0));start=len(part['p'])//3
        for px,py,z in [(x,y,-1),(x1,y1,-1),(x1,y1,1),(x,y,1)]:
            add_vertex(part,(center[0]+px,center[1]+py,center[2]+z*thickness/2),normal,joint)
        part['idx'].extend((start,start+1,start+2,start,start+2,start+3))


def capsule(p1, p2, r1, r2, joint, mat, seg=12, rings=6):
    """Tapered mechanical cylinder between two points with end caps."""
    part = PARTS[mat]
    start = len(part["p"]) // 3
    axis = vec((p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]))
    length = math.sqrt(sum((p2[i] - p1[i]) ** 2 for i in range(3)))
    if length < 1e-6:
        return
    # Build local frame
    ref = (1.0, 0.0, 0.0) if abs(axis[0]) < 0.9 else (0.0, 1.0, 0.0)
    u = vec((axis[1] * ref[2] - axis[2] * ref[1],
             axis[2] * ref[0] - axis[0] * ref[2],
             axis[0] * ref[1] - axis[1] * ref[0]))
    v = vec((axis[1] * u[2] - axis[2] * u[1],
             axis[2] * u[0] - axis[0] * u[2],
             axis[0] * u[1] - axis[1] * u[0]))
    positions = []
    for ring in range(rings + 1):
        t = ring / rings
        rad = r1 * (1 - t) + r2 * t
        pos = (p1[0] + axis[0] * length * t,
               p1[1] + axis[1] * length * t,
               p1[2] + axis[2] * length * t)
        for s in range(seg + 1):
            ang = 2 * math.pi * s / seg
            n = (u[0] * math.cos(ang) + v[0] * math.sin(ang),
                 u[1] * math.cos(ang) + v[1] * math.sin(ang),
                 u[2] * math.cos(ang) + v[2] * math.sin(ang))
            positions.append((pos[0] + rad * n[0],
                              pos[1] + rad * n[1],
                              pos[2] + rad * n[2],
                              n[0], n[1], n[2]))
    # Write positions and normals
    for i in range(len(positions)):
        pos = positions[i][:3]
        nrm = positions[i][3:]
        part["p"].extend(pos)
        part["n"].extend(nrm)
        part["j"].extend([NAME[joint], 0, 0, 0])
        part["w"].extend([1.0, 0.0, 0.0, 0.0])
    # Indices
    for ring in range(rings):
        for s in range(seg):
            k00 = start + ring * (seg + 1) + s
            k01 = start + ring * (seg + 1) + s + 1
            k10 = start + (ring + 1) * (seg + 1) + s
            k11 = start + (ring + 1) * (seg + 1) + s + 1
            part["idx"].extend((k00, k10, k01, k01, k10, k11))
    # End caps
    def cap(center_pos, radius, cap_axis, inward):
        cstart = len(part["p"]) // 3
        cpos = []
        for s in range(seg):
            ang = 2 * math.pi * s / seg
            n = (u[0] * math.cos(ang) + v[0] * math.sin(ang),
                 u[1] * math.cos(ang) + v[1] * math.sin(ang),
                 u[2] * math.cos(ang) + v[2] * math.sin(ang))
            cpos.append((center_pos[0] + radius * n[0],
                         center_pos[1] + radius * n[1],
                         center_pos[2] + radius * n[2],
                         cap_axis[0] * inward,
                         cap_axis[1] * inward,
                         cap_axis[2] * inward))
        cpos.append((center_pos[0], center_pos[1], center_pos[2],
                     cap_axis[0] * inward, cap_axis[1] * inward, cap_axis[2] * inward))
        for p in cpos:
            part["p"].extend(p[:3])
            part["n"].extend(p[3:])
            part["j"].extend([NAME[joint], 0, 0, 0])
            part["w"].extend([1.0, 0.0, 0.0, 0.0])
        center_idx = cstart + seg
        for s in range(seg):
            s1 = (s + 1) % seg
            if inward > 0:
                part["idx"].extend((center_idx, cstart + s, cstart + s1))
            else:
                part["idx"].extend((center_idx, cstart + s1, cstart + s))
    cap(p1, r1, axis, -1.0)
    cap(p2, r2, axis, 1.0)


# ---------------------------------------------------------------------------
# Approved character geometry assembly
# ---------------------------------------------------------------------------
head = G["head"]
hy = head[1]
chest = G["chest"]
neck = G["neck"]

# --- Head: pearlescent helmet shell (rounded, ~29% of height) ---
superellipsoid(head, 0.425, 0.375, 0.31, 3.2, 3.8, "head", "pearl_white", 26, 18)

# Convex, smooth optical visor, including a rear cap hidden inside the helmet.
visor_z=head[2]+.285
visor_width=.375;visor_height=.275;visor_depth=.085

def visor_surface(x,y):
    radius=((abs(x)/visor_width)**4.5+(abs(y-hy)/visor_height)**4.5)**(1/4.5)
    return visor_z+visor_depth*(1-min(1,radius)**2)

def optical_visor():
    part=PARTS['visor_glass'];start=len(part['p'])//3;segments=64;rings=18
    add_vertex(part,(0,hy,visor_z+visor_depth),(0,0,1),'head')
    for ring in range(1,rings+1):
        r=ring/rings
        for j in range(segments):
            a=2*math.pi*j/segments
            x=visor_width*r*_sg_pow(math.cos(a),2/4.5)
            y=hy+visor_height*r*_sg_pow(math.sin(a),2/4.5)
            add_vertex(part,(x,y,visor_z+visor_depth*(1-r*r)),(0,0,1),'head')
    for j in range(segments):part['idx'].extend((start,start+1+j,start+1+(j+1)%segments))
    for ring in range(rings-1):
        a=start+1+ring*segments;b=a+segments
        for j in range(segments):
            k=(j+1)%segments;part['idx'].extend((a+j,b+j,b+k,a+j,b+k,a+k))
    back=add_vertex(part,(0,hy,visor_z-.015),(0,0,-1),'head')
    last=len(part['p'])//3
    for j in range(segments):
        a=2*math.pi*j/segments
        add_vertex(part,(visor_width*_sg_pow(math.cos(a),2/4.5),hy+visor_height*_sg_pow(math.sin(a),2/4.5),visor_z),(0,0,-1),'head')
    for j in range(segments):part['idx'].extend((back,last+(j+1)%segments,last+j))
optical_visor()
# Slim titanium rim follows the actual rounded visor outline.
outline=[]
for j in range(97):
    a=2*math.pi*j/96
    outline.append((.386*_sg_pow(math.cos(a),2/4.5),hy+.286*_sg_pow(math.sin(a),2/4.5),visor_z-.004))
tube(outline,.009,'head','titanium',edges=8)

# --- Eyes: large cyan curved shapes inside visor ---
for s, group in ((-1, "eye_l"), (1, "eye_r")):
    eye_pts = []
    for i in range(25):
        t = i / 24
        x = s*0.135 + (t-.5)*.16
        y = hy + 0.015 + 0.115 * math.sin(math.pi * t)
        eye_pts.append((x, y, visor_surface(x,y)+.006))
    tube(eye_pts, 0.020, "head", "optical_blue", group)

# --- Headphone rings: metallic cyan/titanium on sides ---
for s in (-1, 1):
    torus_ring((s * 0.425, hy, 0.0), 0.195, 0.034, 0, "head", "titanium", 20, 16)
    torus_ring((s * 0.443, hy, 0.0), 0.158, 0.015, 0, "head", "optical_blue", 16, 12)
    ellipsoid((s*.416,hy,0),(.055,.192,.192),"head","pearl_white",26,20)
    # temple vent details
    for k in range(3):
        y = hy - 0.12 + k * 0.04
        tube([(s * 0.260, y, 0.285), (s * 0.300, y + 0.010, 0.255)],
             0.005, "head", "titanium", edges=9)

# --- Antenna / crown detail ---


# --- Neck: titanium mechanical collar ---
capsule(neck, (head[0], hy - 0.115, head[2]), 0.105, 0.105, "neck", "titanium", 16, 6)

# --- Torso: broad shaped chest armor with panel separations ---
superellipsoid(chest, 0.40, 0.32, 0.24, 2.0, 2.0, "chest", "pearl_white", 26, 18)
# Chest front trim / collar

# Side armor panels
for s in (-1, 1):
    superellipsoid((s * 0.36, chest[1], chest[2]), 0.10, 0.22, 0.16, 2.5, 2.5, "chest", "titanium", 16, 12)

# --- GORO chest badge: actual 3D extruded geometry ---
badge_z = chest[2] + 0.225
# Badge backing plate (rounded rect)

# GORO letters extruded upward from badge
word = "GORO"
stroke = {
    "G": [[(0.43, 0.50), (0.25, 0.53), (0.09, 0.46), (0.08, 0.25), (0.26, 0.09), (0.49, 0.12), (0.49, 0.30), (0.35, 0.30)]],
    "O": [[(0.11, 0.28), (0.17, 0.47), (0.48, 0.49), (0.56, 0.27), (0.47, 0.08), (0.17, 0.07), (0.11, 0.28)]],
    "R": [[(0.10, 0.07), (0.10, 0.50), (0.45, 0.50), (0.54, 0.36), (0.41, 0.27), (0.11, 0.27)],
          [(0.34, 0.27), (0.57, 0.07)]]
}
for j, letter in enumerate(word):
    for line in stroke[letter]:
        pts = [(-0.155 + j * 0.082 + x * 0.105, chest[1] - 0.045 + y * 0.105, badge_z + 0.027) for x, y in line]
        tube(pts, 0.004, "chest", "graphite", edges=8)

# Central articulated waist and pelvis connect the torso to hip joints.
superellipsoid((0,.94,0),.30,.19,.21,3.0,3.2,'pelvis','pearl_white',24,16)
capsule((0,.97,0),(0,1.23,0),.18,.21,'spine','graphite',24,8)
for y in [1.04,1.10,1.16]:torus_ring((0,y,0),.19,.018,1,'spine','titanium',20,24)
# --- Shoulders: broad shaped armor ---
for side in ("left", "right"):
    s = -1 if side == "left" else 1
    shoulder = G[f"{side}_shoulder"]
    superellipsoid(shoulder, 0.175, 0.15, 0.17, 2.2, 2.2, f"{side}_shoulder", "pearl_white", 18, 14)
    # Shoulder trim ring
    torus_ring((shoulder[0] + s * 0.02, shoulder[1] - 0.04, shoulder[2] + 0.10),
               0.055, 0.018, 2, f"{side}_shoulder", "titanium", 14, 12)

# --- Arms: mechanical articulated limbs ---
for side in ("left", "right"):
    s = -1 if side == "left" else 1
    shoulder = G[f"{side}_shoulder"]
    elbow = G[f"{side}_elbow"]
    wrist = G[f"{side}_wrist"]
    hand = G[f"{side}_hand"]

    # Upper arm
    capsule(shoulder, elbow, 0.115, 0.095, f"{side}_shoulder", "pearl_white", 32, 8)
    # Elbow joint collar (graphite) + titanium trim
    ellipsoid(elbow, (0.125, 0.115, 0.125), f"{side}_elbow", "graphite", 18, 12)
    ellipsoid((elbow[0], elbow[1], elbow[2] + 0.095), (0.075, 0.075, 0.030), f"{side}_elbow", "titanium", 20, 12)
    ellipsoid((elbow[0], elbow[1], elbow[2] + 0.118), (0.038, 0.038, 0.014), f"{side}_elbow", "optical_blue", 18, 10)
    # Forearm
    capsule(elbow, wrist, 0.105, 0.090, f"{side}_elbow", "pearl_white", 32, 8)
    # Wrist collar
    ellipsoid(wrist, (0.105, 0.095, 0.100), f"{side}_wrist", "bronze" if "bronze" in MAT else "graphite", 16, 12)
    # Hand block
    ellipsoid((hand[0], hand[1] - 0.055, hand[2] + 0.025), (0.092, 0.065, 0.065), f"{side}_hand", "pearl_white", 16, 12)
    # Fingers: five articulated digits
    for i in range(5):
        p = G[f"{side}_finger_{i}"]
        capsule(p, (p[0] + s * 0.015, p[1] - 0.085, p[2] + 0.050), 0.020, 0.016, f"{side}_finger_{i}", "titanium", 10, 4)
        # fingertip
        ellipsoid((p[0] + s * 0.015, p[1] - 0.085, p[2] + 0.050), (0.018, 0.018, 0.018),
                  f"{side}_finger_{i}", "graphite", 10, 8)

# --- Legs: mechanical articulated limbs, feet visible ---
for side in ("left", "right"):
    s = -1 if side == "left" else 1
    hip = G[f"{side}_hip"]
    knee = G[f"{side}_knee"]
    ankle = G[f"{side}_ankle"]

    # Hip collar
    ellipsoid(hip, (0.135, 0.125, 0.135), f"{side}_hip", "titanium", 18, 12)
    # Thigh
    capsule(hip, knee, 0.135, 0.115, f"{side}_hip", "pearl_white", 32, 8)
    # Knee joint collar
    ellipsoid(knee, (0.145, 0.135, 0.145), f"{side}_knee", "graphite", 18, 12)
    ellipsoid((knee[0], knee[1], knee[2] + 0.135), (0.075, 0.055, 0.028), f"{side}_knee", "titanium", 20, 12)
    ellipsoid((knee[0], knee[1], knee[2] + 0.158), (0.042, 0.036, 0.018), f"{side}_knee", "optical_blue", 18, 10)
    # Shin
    capsule(knee, ankle, 0.120, 0.100, f"{side}_knee", "pearl_white", 32, 8)
    # Ankle collar
    ellipsoid((ankle[0], ankle[1] + 0.035, ankle[2]), (0.115, 0.100, 0.125), f"{side}_ankle", "titanium", 18, 12)
    # Foot shell (visible, shaped)
    ellipsoid((ankle[0], 0.070, ankle[2] + 0.090), (0.200, 0.070, 0.280), f"{side}_ankle", "pearl_white", 22, 14)
    # Foot sole / tread
    ellipsoid((ankle[0], 0.025, ankle[2] + 0.095), (0.205, 0.030, 0.285), f"{side}_ankle", "graphite", 26, 14)
    # Ankle trim detail
    tube([(ankle[0] + s * 0.055, 0.140, ankle[2] + 0.270),
          (ankle[0] + s * 0.055, 0.148, ankle[2] + 0.315)],
         0.006, f"{side}_ankle", "titanium", edges=9)

# --- Back plate and ventilated details ---
back_z = chest[2] - 0.260
superellipsoid((0, chest[1], back_z), 0.275, 0.33, 0.045, 2.2, 2.2, "chest", "titanium", 24, 16)
for y in (1.18, 1.26, 1.34, 1.42, 1.50):
    tube([(-0.165, y, back_z - 0.04), (0.165, y, back_z - 0.04)], 0.008, "chest", "graphite", edges=10)

# ---------------------------------------------------------------------------
# Face: expressive morphable features (eyes, brows, mouth)
# ---------------------------------------------------------------------------
for s, group in ((-1, "brow_l"), (1, "brow_r")):
    pts = [(s * (0.078 + i * 0.012), hy + 0.150 + 0.018 * math.sin(i / 9 * math.pi), visor_surface(s * (0.078+i*.012),hy+.150+.018*math.sin(i/9*math.pi))+.007) for i in range(10)]
    tube(pts, 0.009, "head", "optical_blue", group)
for name, offset in (("mouth_top", 0.010), ("mouth_bottom", -0.014)):
    pts = []
    for i in range(17):
        t = (i - 8) / 8
        x=.135*t;y=hy-.118+offset+.032*t*t
        pts.append((x,y,visor_surface(x,y)+.008))
    tube(pts, 0.010, "head", "optical_blue", name)

MORPHS = ["viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U",
          "viseme_MBP", "eye_blink_left", "eye_blink_right", "brow_raise", "smile"]

# ---------------------------------------------------------------------------
# Normal repair: recompute vertex normals from generated topology so they
# exactly match outward CCW winding (required by verify_surface_orientation).
# ---------------------------------------------------------------------------
def compute_normals(part):
    p = part["p"]
    n = [0.0] * len(p)
    idx = part["idx"]
    for i in range(0, len(idx), 3):
        i0, i1, i2 = idx[i], idx[i + 1], idx[i + 2]
        p0 = p[i0 * 3:i0 * 3 + 3]
        p1 = p[i1 * 3:i1 * 3 + 3]
        p2 = p[i2 * 3:i2 * 3 + 3]
        u = [p1[j] - p0[j] for j in range(3)]
        v = [p2[j] - p0[j] for j in range(3)]
        face = (u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2],
                u[0] * v[1] - u[1] * v[0])
        fn = math.sqrt(sum(f * f for f in face))
        if fn > 0:
            face = tuple(f / fn for f in face)
        for vi in (i0, i1, i2):
            n[vi * 3] += face[0]
            n[vi * 3 + 1] += face[1]
            n[vi * 3 + 2] += face[2]
    for i in range(0, len(n), 3):
        nn = math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2])
        if nn > 0:
            n[i] /= nn
            n[i + 1] /= nn
            n[i + 2] /= nn
        else:
            n[i + 2] = 1.0
    part["n"] = n


for part in PARTS.values():
    compute_normals(part)
compute_normals(FACE)

# ---------------------------------------------------------------------------
# glTF buffer / accessor / mesh writing (preserved format)
# ---------------------------------------------------------------------------
class Buffer:
    def __init__(self):
        self.data = bytearray()
        self.views = []
        self.accessors = []

    def push(self, blob, target=None):
        while len(self.data) % 4:
            self.data.append(0)
        offset = len(self.data)
        self.data.extend(blob)
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(blob)}
        if target is not None:
            view["target"] = target
        self.views.append(view)
        return len(self.views) - 1

    def attr(self, values, fmt, typ, componentType, target=None, minmax=False):
        values = list(values)
        arity = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[typ]
        binary = struct.pack("<" + fmt * len(values), *values)
        vi = self.push(binary, target)
        accessor = {"bufferView": vi, "byteOffset": 0, "componentType": componentType,
                    "count": len(values) // arity, "type": typ}
        if minmax:
            accessor["min"] = [min(values[i::arity]) for i in range(arity)]
            accessor["max"] = [max(values[i::arity]) for i in range(arity)]
        self.accessors.append(accessor)
        return len(self.accessors) - 1


buf = Buffer()


def verify_surface_orientation(part, material):
    positions = part["p"]
    normals = part["n"]
    indices = part["idx"]
    for i in range(0, len(indices), 3):
        vertices = indices[i:i + 3]
        a, b, c = [positions[v * 3:v * 3 + 3] for v in vertices]
        u = [b[j] - a[j] for j in range(3)]
        v = [c[j] - a[j] for j in range(3)]
        face = (u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2],
                u[0] * v[1] - u[1] * v[0])
        dot = sum(face[j] * sum(normals[k * 3 + j] for k in vertices) for j in range(3))
        if dot < -1e-12:
            raise ValueError(f"Reversed triangle in {material}, index {i // 3}")


def primitive(part, material, morphs=False):
    verify_surface_orientation(part, material)
    p = part["p"]
    uv = []
    for i in range(0, len(p), 3):
        x, y, z = p[i:i + 3]
        uv.extend(((x + 1.75) / 3.5, y / 3.0))
    attrs = {
        "POSITION": buf.attr(p, "f", "VEC3", 5126, 34962, True),
        "NORMAL": buf.attr(part["n"], "f", "VEC3", 5126, 34962),
        "TEXCOORD_0": buf.attr(uv, "f", "VEC2", 5126, 34962),
        "JOINTS_0": buf.attr(part["j"], "H", "VEC4", 5123, 34962),
        "WEIGHTS_0": buf.attr(part["w"], "f", "VEC4", 5126, 34962),
    }
    obj = {"attributes": attrs,
           "indices": buf.attr(part["idx"], "I", "SCALAR", 5125, 34963),
           "material": list(MAT).index(material)}
    if morphs:
        targets = []
        for target in MORPHS:
            deltas = []
            for pos, group in zip(part["xrest"], part["groups"]):
                x, y, z = pos
                dx = dy = dz = 0.0
                if target.startswith("eye_blink") and group in ("eye_l", "eye_r"):
                    if (target.endswith("left") and group == "eye_l") or \
                       (target.endswith("right") and group == "eye_r"):
                        dy = -(y - (hy + 0.04)) * 0.85
                elif target == "brow_raise" and group.startswith("brow"):
                    dy = 0.055
                elif target == "smile" and group.startswith("mouth"):
                    dy = 0.035 * abs(x) / 0.13
                elif target.startswith("viseme_") and group.startswith("mouth"):
                    if target == "viseme_A":
                        dy = -0.050 if group == "mouth_bottom" else 0.014
                    elif target == "viseme_E":
                        dy = -0.022 if group == "mouth_bottom" else 0.008
                        dx = x * 0.17
                    elif target == "viseme_I":
                        dy = -0.012 if group == "mouth_bottom" else 0.004
                        dx = x * 0.25
                    elif target == "viseme_O":
                        dy = -0.035 if group == "mouth_bottom" else 0.012
                        dx = -x * 0.24
                    elif target == "viseme_U":
                        dy = -0.020 if group == "mouth_bottom" else 0.010
                        dx = -x * 0.34
                    elif target == "viseme_MBP":
                        dy = 0.014 if group == "mouth_bottom" else -0.011
                deltas.extend((dx, dy, dz))
            targets.append({"POSITION": buf.attr(deltas, "f", "VEC3", 5126, 34962)})
        obj["targets"] = targets
    return obj


primitives = [primitive(part, material) for material, part in PARTS.items() if part["idx"]]
body = {"name": "GORO_Rigged_Body", "primitives": primitives}
face = {"name": "GORO_Expressive_Face",
        "primitives": [primitive(FACE, "optical_blue", True)],
        "weights": [0.0] * len(MORPHS),
        "extras": {"targetNames": MORPHS}}
meshes = [body, face]

nodes = []
for name, parent, pos in JOINTS:
    nodes.append({"name": name, "translation": list(pos), "children": []})
for i, (name, parent, _) in enumerate(JOINTS):
    if parent:
        nodes[NAME[parent]]["children"].append(i)
nodes.extend(({"name": "GORO_Body_Skinned", "mesh": 0, "skin": 0},
              {"name": "GORO_Face_Morphs", "mesh": 1, "skin": 0}))

bind = []
for name, _, _ in JOINTS:
    x, y, z = G[name]
    bind.extend((1.0, 0.0, 0.0, 0.0,
                 0.0, 1.0, 0.0, 0.0,
                 0.0, 0.0, 1.0, 0.0,
                 -x, -y, -z, 1.0))
skin = {"name": "GORO_Skeleton", "joints": list(range(len(JOINTS))), "skeleton": 0,
        "inverseBindMatrices": buf.attr(bind, "f", "MAT4", 5126)}


def qxyz(axis, degrees):
    angle = math.radians(degrees) * 0.5
    return tuple(v * math.sin(angle) for v in axis) + (math.cos(angle),)


# Visible, full-body state clips. The previous draft rotated every joint
# around X, which mostly twisted the shoulders in depth and looked static.
CLIP_DEFS = [
    ("idle", {
        "head": [0, 8, -8, 0], "chest": [0, 3, -3, 0],
        "left_shoulder": [0, -8, -3, 0], "right_shoulder": [0, 8, 3, 0],
        "left_elbow": [0, -4, 0, 0], "right_elbow": [0, 4, 0, 0],
    }),
    ("listening", {
        "head": [0, -13, -6, 0], "chest": [0, 4, 0, 0],
        "left_shoulder": [0, -18, -8, 0], "left_elbow": [0, -24, -8, 0],
        "right_shoulder": [0, 5, 0, 0],
    }),
    ("thinking", {
        "head": [0, 16, 6, 0], "chest": [0, -5, 0, 0],
        "right_shoulder": [0, 30, 18, 0], "right_elbow": [0, -44, -22, 0],
        "right_wrist": [0, 16, 0, 0],
    }),
    ("speaking", {
        "head": [0, 9, -9, 0], "chest": [0, 5, -3, 0],
        "left_shoulder": [0, -20, -7, 0], "right_shoulder": [0, 20, 7, 0],
        "left_elbow": [0, -30, -12, 0], "right_elbow": [0, 30, 12, 0],
        "left_wrist": [0, -14, 8, 0], "right_wrist": [0, 14, -8, 0],
    }),
    ("confirming", {
        "head": [0, 13, 0, 0], "chest": [0, 4, 0, 0],
        "right_shoulder": [0, 36, 10, 0], "right_elbow": [0, -50, -8, 0],
        "right_wrist": [0, 22, 0, 0],
    }),
    ("alert", {
        "head": [0, -20, 13, 0], "chest": [0, 9, 0, 0],
        "left_shoulder": [0, -24, -4, 0], "right_shoulder": [0, 24, 4, 0],
        "left_elbow": [0, -36, -10, 0], "right_elbow": [0, 36, 10, 0],
    }),
]
ANIMATION_AXES = {
    "head": (0, 1, 0), "spine": (1, 0, 0), "chest": (1, 0, 0),
    "left_shoulder": (0, 0, 1), "right_shoulder": (0, 0, 1),
    "left_elbow": (0, 0, 1), "right_elbow": (0, 0, 1),
    "left_wrist": (1, 0, 0), "right_wrist": (1, 0, 0),
}
animations = []
timeinput = buf.attr([0.0, 0.55, 1.15, 1.8], "f", "SCALAR", 5126, minmax=True)
for name, targets in CLIP_DEFS:
    channels = []
    samplers = []
    for bone, values in targets.items():
        axis = ANIMATION_AXES.get(bone, (0, 0, 1))
        quats = [v for degrees in values for v in qxyz(axis, degrees)]
        acc = buf.attr(quats, "f", "VEC4", 5126)
        si = len(samplers)
        samplers.append({"input": timeinput, "output": acc, "interpolation": "LINEAR"})
        channels.append({"sampler": si, "target": {"node": NAME[bone], "path": "rotation"}})
    animations.append({"name": name, "samplers": samplers, "channels": channels})


def png():
    w = h = 4
    scan = b"".join(b"\x00" + bytes((252, 254, 255, 255) * w) for _ in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    return (b"\x89PNG\r\n\x1a\n" +
            chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(scan, 9)) +
            chunk(b"IEND", b""))


imageview = buf.push(png())
materials = []
for name, (color, metal, rough, emission) in MAT.items():
    materials.append({
        "name": name,
        "pbrMetallicRoughness": {"baseColorFactor": color,
                                 "baseColorTexture": {"index": 0},
                                 "metallicFactor": metal,
                                 "roughnessFactor": rough},
        "emissiveFactor": emission,
        "doubleSided": False
    })

gltf = {
    "asset": {"version": "2.0", "generator": "GORO first-party parametric articulated character authoring",
    "extras": {"authoring": "first-party-parametric", "visual_acceptance": false}},
    "scene": 0,
    "scenes": [{"nodes": [0, len(JOINTS), len(JOINTS) + 1]}],
    "nodes": nodes,
    "meshes": meshes,
    "skins": [skin],
    "buffers": [{"byteLength": 0}],
    "bufferViews": buf.views,
    "accessors": buf.accessors,
    "materials": materials,
    "images": [{"bufferView": imageview, "mimeType": "image/png"}],
    "samplers": [{"magFilter": 9729, "minFilter": 9729, "wrapS": 10497, "wrapT": 10497}],
    "textures": [{"sampler": 0, "source": 0}],
    "animations": animations
}

while len(buf.data) % 4:
    buf.data.append(0)
gltf["buffers"][0]["byteLength"] = len(buf.data)
js = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
while len(js) % 4:
    js += b" "
total = 12 + 8 + len(js) + 8 + len(buf.data)
out = b"glTF" + struct.pack("<II", 2, total) + struct.pack("<I4s", len(js), b"JSON") + js
out += struct.pack("<I4s", len(buf.data), b"BIN\x00") + buf.data
DEST.parent.mkdir(parents=True, exist_ok=True)
DEST.write_bytes(out)
print(f"GORO GLB built: {len(out)} bytes, {len(JOINTS)} joints, {len(animations)} animation clips, {len(MORPHS)} face morph targets")