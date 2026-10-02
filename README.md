# CropSense

<p align="center">
  <img
    src="https://github.com/user-attachments/assets/d7854955-fad3-4f89-9012-7108bf3cfe08"
    width="320"
    alt="CropSense app screenshot"
  />
</p>

Android app that identifies crop diseases from a leaf photo. Two TensorFlow Lite models run on the device, so it works without a network connection and images are never sent anywhere.

## How it works

```
image
  -> scale to 224x224, normalize RGB to float32 in [0, 1]
  -> plant_detector.tflite    leaf / not leaf    (pipeline stops here if not a leaf)
  -> model.tflite             38-class output
  -> crop, disease, confidence
```

1. The user picks an image from the gallery or takes one with the system camera.
2. The bitmap is decoded, scaled to 224x224, and written into a float32 `ByteBuffer` with each RGB channel divided by 255.
3. `plant_detector.tflite` runs first. If the image is not a leaf, the pipeline stops and shows a message. Nothing else runs.
4. `model.tflite` runs on the same buffer and returns a 38-class distribution. The highest class is split on `___` into crop and disease.
5. The result card shows crop, disease, and confidence. The bar is green at 80% or above, amber from 60% to 80%, red below 60%. Under 60% a message asks for a clearer photo. If the disease is `healthy`, the card uses the green success style instead.

Inference runs on `Dispatchers.IO` from `MainViewModel`. Both models are memory-mapped from the APK assets rather than copied out, which is why `noCompress += "tflite"` is set in `app/build.gradle.kts`.

## Supported classes

38 classes over 14 crops, from PlantVillage. The list is defined in `ml/DiseaseClassifier.kt`.

| Crop | Conditions |
|------|-----------|
| Apple | Apple scab, Black rot, Cedar apple rust, healthy |
| Blueberry | healthy |
| Cherry (including sour) | Powdery mildew, healthy |
| Corn (maize) | Cercospora leaf spot / Gray leaf spot, Common rust, Northern Leaf Blight, healthy |
| Grape | Black rot, Esca (Black Measles), Leaf blight (Isariopsis Leaf Spot), healthy |
| Orange | Haunglongbing (Citrus greening) |
| Peach | Bacterial spot, healthy |
| Pepper, bell | Bacterial spot, healthy |
| Potato | Early blight, Late blight, healthy |
| Raspberry | healthy |
| Soybean | healthy |
| Squash | Powdery mildew |
| Strawberry | Leaf scorch, healthy |
| Tomato | Bacterial spot, Early blight, Late blight, Leaf Mold, Septoria leaf spot, Spider mites (two-spotted), Target Spot, Yellow Leaf Curl Virus, Mosaic virus, healthy |

## Build and run

Requires Android Studio, JDK 11, and Android SDK 36. `minSdk` is 28 (Android 9.0), Kotlin is 2.0.21, AGP is 8.12.3.

```bash
git clone https://github.com/thesatyam161/cropsense.git
cd cropsense
```

Open the folder in Android Studio, let Gradle sync, then run on a device or emulator with API 28 or higher.

Both model files ship in `app/src/main/assets/` (`plant_detector.tflite` 8.5 MB, `model.tflite` 9.1 MB), so no download or setup step is needed.

## Dependencies

```kotlin
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
implementation("androidx.compose.material:material-icons-extended:1.6.1")
implementation("io.coil-kt:coil-compose:2.5.0")
implementation(platform(libs.androidx.compose.bom))  // Compose BOM 2024.09.00
implementation(libs.androidx.material3)
implementation(libs.androidx.activity.compose)       // 1.12.2 via version catalog
```

`androidx.activity:activity-compose` is declared twice in `app/build.gradle.kts`, once pinned at 1.8.2 and once through the version catalog at 1.12.2. Gradle resolves to the higher one, so the pinned declaration is dead and can be deleted.

## Code layout

```
app/src/main/java/com/example/cropsense/
├── MainActivity.kt              Compose UI: preview, picker buttons, result cards
├── MainViewModel.kt             Holds result/loading/error state, launches inference
├── ml/DiseaseClassifier.kt      Loads both interpreters, runs the two-stage pipeline
├── model/PredictionResponse.kt  Result data class
└── ui/theme/                    Dark Material 3 colors and typography

app/src/main/assets/
├── plant_detector.tflite        Stage 1, leaf vs not leaf
└── model.tflite                 Stage 2, 38-class classifier
```

There is no backend and no network code in the project.

## Known rough edges

- The crop label is not cleaned up, so the UI shows the raw class prefix such as `Corn_(maize)` or `Pepper,_bell`. Only the disease string is title-cased.
- The camera button uses `TakePicturePreview`, which returns a small preview bitmap saved to the cache directory, not a full-resolution photo. Gallery images give better results.
- `INTERNET` permission is declared in the manifest but nothing uses it. It can be removed.
- The theme is hardcoded dark. There is no light theme and it does not follow the system setting.
- `applicationId` is still the template default `com.example.cropsense`.
- The repository has no LICENSE file.

## Credits

- Dataset: [PlantVillage](https://github.com/spMohanty/PlantVillage-Dataset)
- Runtime: [TensorFlow Lite](https://www.tensorflow.org/lite)
