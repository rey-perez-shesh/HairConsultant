# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Everyday people deciding on their next haircut, usually before visiting a barber or salon: they scan their face or upload a photo, talk it through with an AI consultant, and preview styles on themselves. A planned second audience is barbershop clients, through an integration where a shop's catalog is managed from an admin dashboard.

## Product Purpose
HairConsultant recommends haircuts that suit the user's face shape, hair length, hair texture and stated preferences, then lets them see a chosen cut on themselves before committing to it. It is a capstone project now, intended to become a real product later; success is a user who leaves knowing which cut to ask for and having seen it on their own face.

## Positioning
Recommendations are bound to a real, curated haircut catalog: the AI consultant reasons over the conversation and the user's measured features, but every pick is a catalog entry with its own reference image, so previews and try-ons show a cut a barber can actually reproduce.

## Operating Context
- Face Scan: live front camera with a MediaPipe face mesh and hair mask; the face must sit inside an oval guide and face the camera before Scan is enabled. Try-on uses DeepAR / AR overlays on the live camera.
- Image Upload: the user picks a photo; the app analyzes it, consults in chat, and generates a Gemini image preview of the chosen catalog cut on the photo.
- Chat consultant: a bottom-sheet chat shared across screens, with quick-reply chips and haircut cards inside messages.
- Home: browse the catalog by gender, face shape, hair type and length, grouped by length & texture.
- Profile: account, consultation history, favorites, satisfaction feedback.
- Catalog lives in Firestore, synced to a local Room cache; an admin dashboard is being prepared to manage it.

## Capabilities and Constraints
- Jetpack Compose + Material 3, minSdk 26, phone-first.
- Bottom navigation with four tabs is fixed: Home, Face Scan, Upload, Profile.
- Face Scan and Image Upload stay dark, full-bleed camera/photo screens with overlays.
- Face analysis needs an ARM device; x86_64 emulators can't run it.
- Undecided: tablet layouts; the barbershop integration's scope.

## Brand Commitments
- Name: HairConsultant (kept).
- The previous violet/pink palette is not binding; the color scheme is open.

## Evidence on Hand
- Catalog of ~70 haircuts with hair-only cutout thumbnails (`res/drawable/thumb_*`), four styles still missing images.
- No testimonials, user counts, salon partners or benchmarks exist; none may be invented.

## Product Principles
1. The user's own face is the subject: camera and photo screens give the image room and keep chrome out of the way.
2. Every recommendation is a real catalog cut a barber can reproduce.
3. Explain the why: face shape, length and texture reasoning stays visible, not hidden behind "AI magic".
4. Guide, don't block silently: when something can't proceed (face outside the oval, no reference photo), say what to do.

## Accessibility & Inclusion
Unisex catalog with gender filtering; styles for straight, wavy and curly hair, and a wig path for bald users. Content descriptions on camera controls must state why a control is disabled.
