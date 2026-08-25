# Wig assets (Face AR)

Primary 3D asset (try-on):

`Waves_AR_Prepared.glb` — exported from `Waves_AR_Prepared.blend`
(origin = crown/scalp, hair hangs in −Y)

Legacy / unused by try-on:

- `Meshy_AI__0823201911_texture.glb`
- `Meshy_AI_Layered_Brunette_Hair_0822091245_generate.glb`
- `wavy_black_wig.glb`

## Live 3D AR pipeline

1. Tap a hairstyle → 2D fallback shows while GLB loads (camera stays on).
2. On successful load → transparent SceneView draws the wig over the live camera.
3. Every MediaPipe frame → HeadPoseEstimator → ModelNode transform.
4. On load failure/timeout → stays on 2D fallback only.

Logcat tag: `ArTryOnOverlay`
