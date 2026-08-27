import json
import struct
from pathlib import Path


def parse_glb(path: Path) -> None:
    data = path.read_bytes()
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    assert magic == b"glTF", magic
    offset = 12
    chunks: dict[bytes, bytes] = {}
    while offset < length:
        chunk_len, chunk_type = struct.unpack_from("<I4s", data, offset)
        offset += 8
        payload = data[offset : offset + chunk_len]
        offset += chunk_len
        chunks[chunk_type] = payload
    gltf = json.loads(chunks[b"JSON"].decode("utf-8"))
    accessors = gltf.get("accessors", [])
    meshes = gltf.get("meshes", [])
    nodes = gltf.get("nodes", [])
    materials = gltf.get("materials", [])
    pos_acc_ids = set()
    for m in meshes:
        for p in m.get("primitives", []):
            attrs = p.get("attributes", {})
            if "POSITION" in attrs:
                pos_acc_ids.add(attrs["POSITION"])
    mins = maxs = None
    for i in pos_acc_ids:
        acc = accessors[i]
        if "min" in acc and "max" in acc:
            mn, mx = acc["min"], acc["max"]
            if mins is None:
                mins, maxs = mn[:], mx[:]
            else:
                for a in range(3):
                    mins[a] = min(mins[a], mn[a])
                    maxs[a] = max(maxs[a], mx[a])
    children = set()
    for n in nodes:
        for c in n.get("children", []):
            children.add(c)
    roots = []
    for i, n in enumerate(nodes):
        if i not in children:
            roots.append(
                (
                    i,
                    n.get("name"),
                    n.get("translation"),
                    n.get("rotation"),
                    n.get("scale"),
                    n.get("matrix") is not None,
                )
            )
    print("===", path.name, "sizeMB=", round(len(data) / 1e6, 2))
    print(
        " meshes=",
        len(meshes),
        "materials=",
        len(materials),
        "nodes=",
        len(nodes),
        "scenes=",
        len(gltf.get("scenes", [])),
    )
    print(" AABB min=", mins, "max=", maxs)
    if mins and maxs:
        c = [(mins[i] + maxs[i]) / 2 for i in range(3)]
        s = [maxs[i] - mins[i] for i in range(3)]
        print(" center=", c, "size=", s)
        print(" maxY~", maxs[1], "(crown near 0 if prepared)")
    print(" root nodes:", roots[:6])
    for mi, mat in enumerate(materials[:3]):
        pbr = mat.get("pbrMetallicRoughness", {})
        print(
            f" mat[{mi}] name={mat.get('name')} "
            f"baseColorFactor={pbr.get('baseColorFactor')} "
            f"hasBaseColorTex={('baseColorTexture' in pbr)}"
        )
    print()


base = Path(r"c:\Users\beggy\codes\HairConsultant\app\src\main\assets\wigs")
for name in [
    "Meshy_AI_Slicked_Back_texture.glb",
    "Meshy_AI_buzzcut_texture.glb",
    "Meshy_AI_Long_Wavy_Hair_AR_texture.glb",
    "Waves_AR_Prepared.glb",
]:
    parse_glb(base / name)
