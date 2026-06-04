# Solar Soiling Advisor

An Android app that answers one question for solar-panel owners: **"Do my panels need cleaning?"**

You take a photo of a panel and an on-device AI model judges how dusty it looks, then gives a clear
verdict — *No cleaning needed*, *Worth a clean soon*, or *Time to clean* — with a plain-language reason.

Everything runs **on-device**. No photo or data ever leaves the phone.

---

## How it works

```
First launch ──▶ Setup (system name, panels, location)
                      │
Camera photo ──▶ TFLite classifier ──▶ dirtiness score ──▶ verdict
                 (Clean / Dusty)        (0–100%)            (Clean / Dusty / Dirty)
```

1. **Set up (once)** — on first launch the user gives their system a name, panel count and optional
   location. It's persisted via SharedPreferences ([`SetupStore`](app/src/main/java/com/example/solarsoilingadvisor/data/SetupStore.kt))
   and can be edited later from the **Setup** button in the app bar.
2. **Capture** — the user photographs a panel (`TakePicturePreview`).
3. **Classify** — a bundled TensorFlow Lite model (`app/src/main/assets/model.tflite`) returns a
   continuous *dirtiness score*. It supports both binary (`Clean`/`Dusty`, sigmoid) and multi-class
   severity models; see [`SoilingClassifier`](app/src/main/java/com/example/solarsoilingadvisor/classifier/SoilingClassifier.kt).
4. **Verdict** — the dirtiness score maps straight to a three-level recommendation: clean (&lt;30%),
   getting dusty (30–70%), very dirty (&gt;70%). No money, no settings to tune.

### The advice in plain terms

The UI deliberately hides the jargon. Non-technical users see:

- a **status pill** — *Looks clean / Getting dusty / Very dirty*,
- a green→amber→red **dirtiness bar**,
- a **verdict** — *No cleaning needed / Worth a clean soon / Time to clean*, with a one-sentence reason.

Model verdict, confidence and the raw dirtiness % are tucked behind a *"Show technical details"*
toggle for the curious.

> A pure-Kotlin economic engine ([`CleaningAdvisor`](app/src/main/java/com/example/solarsoilingadvisor/decision/CleaningAdvisor.kt),
> with unit tests) also lives in the repo. It's **not used by the UI** — the app's advice is based
> purely on how the panel looks — but it's kept for reference / future use.

---

## Project structure

| Path | Purpose |
| --- | --- |
| `classifier/SoilingClassifier.kt` | Loads the `.tflite` model, runs inference, produces a dirtiness score. |
| `data/SetupStore.kt` | Persists the user's setup (name, panels, location) in SharedPreferences. |
| `ui/MainActivity.kt` | Compose UI: onboarding/setup, capture, verdict, camera-permission flow. |
| `ui/SoilingViewModel.kt` | Holds UI state, classification, and the saved setup. |
| `ui/theme/` | Material 3 colour scheme (solar blue + sun amber). |
| `ui/widget/DirtinessMeter.kt` | The green→amber→red "how dirty" bar. |
| `decision/CleaningAdvisor.kt` | Reference economic engine (unit-tested, not wired into the UI). |
| `app/src/main/assets/` | `model.tflite` and `labels.txt` (`Clean`, `Dusty`). |

---

## Building & running

Requirements: Android Studio (recent), JDK 17, an Android device/emulator on **API 24+**.

```bash
# Build a debug APK
./gradlew :app:assembleDebug

# Install onto a connected device/emulator
./gradlew :app:installDebug
```

The first time you tap **Take a photo**, Android asks for camera permission:

- **Allowed** → the camera opens.
- **Denied** → a dialog explains why the camera is needed and lets you retry.
- **Denied permanently** ("Don't ask again") → the dialog offers an **Open settings** button
  that deep-links to the app's permission page, so the user is never left stuck.

---

## Build notes

### TensorFlow Lite namespace collision (AGP 9)

This project pins **Android Gradle Plugin 9.2.1**, which promotes "namespace used in multiple
modules" from a warning to a **hard error** in the manifest merger. The TFLite 2.14.0 artifacts ship
colliding namespaces baked into their AAR manifests:

- `tensorflow-lite` and `tensorflow-lite-api` both declare `org.tensorflow.lite`
- `tensorflow-lite-support` and `tensorflow-lite-support-api` both declare `org.tensorflow.lite.support`

Those namespaces can't be changed without forking the libraries. The fix is the documented AGP escape
hatch in [`gradle.properties`](gradle.properties):

```properties
android.uniquePackageNames=false
```

This relaxes the uniqueness check back to a warning so the prebuilt manifests merge.

> **Heads-up:** this flag is an officially *temporary* AGP property and may be removed in a future
> release. If a future AGP bump breaks the build again, the long-term fix is moving to TFLite/LiteRT
> versions that ship non-colliding namespaces.

### LiteRT is intentionally excluded

`mlModelBinding` is **not** enabled and `com.google.ai.edge.litert` is excluded in
[`app/build.gradle.kts`](app/build.gradle.kts). We deliberately use the older, pre-rename
`org.tensorflow.lite.*` 2.14.0 packages, which run the same `.tflite` files without pulling LiteRT.

### App icon

The launcher icon is a custom adaptive icon (sun over a solar panel, defined in
`app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_background.xml`). Adaptive
icons render on API 26+; the legacy raster icons in `mipmap-*/` are only used as a fallback on API 24–25.
