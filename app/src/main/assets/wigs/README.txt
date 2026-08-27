# Wig assets (Face AR)

Default try-on GLB:

`Meshy_AI__buzzcut_texture.glb` — Boy Cut

Dedicated AR mappings (`HairstyleArCatalog`):

- `Meshy_AI__buzzcut_texture.glb` ← SHORT_STRAIGHT_0 "Boy Cut"
- `Meshy_AI_layered_hair_3k_tris_curtain_texture.glb` ← SHORT_STRAIGHT_1 "Curtains"

Legacy / unused by these two cards:

- `Waves_AR_Prepared.glb`
- `Meshy_AI__buzzcut_texture.glb` / `Meshy_AI_buzzcut_texture.glb` (older buzz names)
- `Meshy_AI_Slicked_Back_texture.glb`
- `Meshy_AI_Long_Wavy_Hair_AR_texture.glb`
- `wavy_black_wig.glb`

## Live 3D AR pipeline

1. Tap a hairstyle → previous ModelNode is disposed; only the selected GLB loads.
2. Transparent SceneView draws the wig over the live camera.
3. MediaPipe → HeadPoseEstimator → ModelNode (+ per-asset local fit).
4. Boy Cut / Curtains: face cutout disabled (real camera face stays visible).

Logcat tag: `ArTryOnOverlay`
