# DeepAR hairstyle effects — admin dashboard only.
#
# Try-on no longer maps these APK files in Kotlin. Upload a `.deepar` in the
# hairconsultant-admin dashboard for each haircut and set:
#   arType = DEEPAR   (or AUTO with a URL ending in .deepar)
#   arAssetUrl = Firebase Storage download URL
#   arAssetVersion = bump on every re-upload
#
# The app downloads the file via ArAssetCache and opens DeepAR try-on.
# Files left under this folder are unused leftovers from older APK-bundled try-on.
