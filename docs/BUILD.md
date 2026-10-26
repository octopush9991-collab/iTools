# Build, Uji, dan Rilis

## 1. Prasyarat

| Kebutuhan | Versi |
| --- | --- |
| JDK | 17 (wajib untuk AGP 8.7.3) |
| Gradle | 8.9 atau lebih baru |
| Android SDK Platform | 35 |
| Android Build Tools | 35.x (ikut SDK Platform 35) |
| Android Studio | Versi yang mendukung AGP 8.7.3 (Ladybug atau lebih baru) |

Proyek menargetkan:

- `compileSdk` / `targetSdk` = 35
- `minSdk` = 26
- Java/Kotlin target = 17
- Kotlin = 2.0.21, AGP = 8.7.3, Media3 = 1.7.1

Pada mesin dengan RAM terbatas, Gradle daemon dan Kotlin daemon bisa kehabisan memori.
`gradle.properties` memakai `-Xmx1536m`; bila build gagal karena OOM, jalankan dengan RAM lebih besar
atau hentikan daemon lain:

```bash
./gradlew --stop
./gradlew :app:assembleDebug --no-daemon
```

## 2. Menyiapkan SDK

Cara termudah adalah membuka folder proyek di Android Studio dan membiarkan SDK Manager memasang
Platform 35. Untuk CLI:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
yes | sdkmanager --licenses
```

Buat `local.properties` di root repositori:

```properties
sdk.dir=/home/<user>/Android/Sdk
```

`local.properties` tidak boleh di-commit (sudah tercakup `.gitignore`).

## 3. Gradle wrapper

Repositori ini belum menyertakan `gradle-wrapper.jar`. Jika Gradle 8.9+ sudah terpasang:

```bash
gradle wrapper --gradle-version 8.9
./gradlew --version
```

Setelah wrapper dibuat, commit `gradlew`, `gradlew.bat`, dan `gradle/wrapper/`. Android Studio juga
dapat membuat wrapper saat proses sync pertama.

Jika ingin memakai Gradle yang sudah terpasang tanpa wrapper:

```bash
gradle :app:assembleDebug
```

## 4. Build

```bash
# debug
./gradlew :app:assembleDebug

# release (butuh signing)
./gradlew :app:assembleRelease

# bundle Play Store
./gradlew :app:bundleRelease
```

Hasil:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

Debug memakai `applicationId` `com.itools.app.debug` dan sufiks versi `-debug`.

## 5. Signing release

Buat keystore sekali:

```bash
keytool -genkeypair -v \
  -keystore itools-release.jks \
  -alias itools \
  -keyalg RSA -keysize 4096 -validity 10000
```

Tambahkan ke `~/.gradle/gradle.properties` (jangan commit):

```properties
ITOOLS_STORE_FILE=/absolute/path/itools-release.jks
ITOOLS_STORE_PASSWORD=...
ITOOLS_KEY_ALIAS=itools
ITOOLS_KEY_PASSWORD=...
```

Lalu tambahkan `signingConfigs` di `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ITOOLS_STORE_FILE") ?: "")
            storePassword = System.getenv("ITOOLS_STORE_PASSWORD")
            keyAlias = System.getenv("ITOOLS_KEY_ALIAS")
            keyPassword = System.getenv("ITOOLS_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Sesuaikan sumber kredensial dengan kebijakan CI Anda. Jangan menulis password ke repositori.

## 6. Pemeriksaan kualitas

```bash
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # butuh perangkat/emulator
```

Saat ini belum ada unit test khusus untuk `ProjectOps`/`CompositionFactory`. Kandidat test yang
paling bernilai:

1. `ProjectOps.split` mempertahankan total durasi dan rentang sumber.
2. `trimEnd` tidak melewati durasi sumber (`sourceTotalMs`) atau awal klip berikutnya.
3. `duplicateClip` tidak pernah menimpa klip lain.
4. `KeyframeMath` pada batas awal/akhir dan satu keyframe.
5. `SubtitleIO` round-trip SRT/VTT, termasuk timestamp dengan koma/titik.
6. `VolumeAudioProcessor` pada fade 0 dan durasi sangat pendek.

## 7. Verifikasi offline

Manifest tidak memiliki izin `INTERNET`. Setelah APK dibuat:

```bash
# bila aapt/aapt2 tersedia
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk

# atau periksa manifest gabungan
grep -R "android.permission.INTERNET" app/build/intermediates/merged_manifest || true
```

Periksa juga bahwa tidak ada dependensi iklan/billing/analytics di
`./gradlew :app:dependencies`.

## 8. Catatan Media3

- Banyak API yang dipakai bertanda `@UnstableApi`; file terkait memakai
  `@OptIn(UnstableApi::class)` atau `@androidx.annotation.OptIn(UnstableApi::class)`.
- `CompositionPlayer` dan `Transformer` harus dibuat di thread dengan `Looper` (main thread).
- `CompositionPlayer` menolak composition dengan gap; `CompositionFactory` mengisi celah dengan
  filler/silence.
- `GlProgram`/`BaseGlShaderProgram` mengharuskan field yang dipakai di `configure` bersifat lazy;
  efek kustom yang ada sudah mengikuti pola ini.
- `FrameDropEffect` hanya menurunkan fps.
- `setDurationUs` pada `EditedMediaItem` adalah durasi sumber sebelum clipping, sehingga klip yang
  di-trim mengirim `sourceStartMs + sourceDurationMs`.

## 9. Troubleshooting

| Gejala | Penyebab umum | Tindakan |
| --- | --- | --- |
| `Unsupported class file major version` | JDK terlalu baru/terlalu lama | Pakai JDK 17 |
| `SDK location not found` | `local.properties` belum ada | Set `sdk.dir` |
| Build OOM | RAM kecil, daemon menumpuk | `./gradlew --stop`, naikkan `-Xmx`, `--no-daemon` |
| Preview hitam | Codec perangkat tidak mendukung sumber | Coba H.264/720p, cek `adb logcat` |
| `ExportException` codec | Resolusi/bitrate/fps di luar dukungan encoder | Turunkan resolusi/bitrate, coba H.264 |
| 4K gagal | Batas encoder/RAM | Ekspor 1080p |
| Reverse gagal | Klip > 15 detik atau memori habis | Potong klip, coba resolusi lebih rendah |
| HEVC tidak bisa diputar | Dukungan perangkat | Ekspor H.264 |
| Suara tidak ada | Track/klip mute atau `includeAudio` mati | Cek mute dan opsi ekspor |
| Subtitle tidak terlihat | Tidak ada track caption atau di luar durasi | Cek panel Subtitle dan posisi klip |

Log yang berguna:

```bash
adb logcat -s Transformer:* CompositionPlayer:* ExoPlayerImpl:* AndroidRuntime:E
```

## 10. Rilis

Checklist sebelum merilis:

- [ ] `./gradlew :app:lintRelease`
- [ ] `./gradlew :app:assembleRelease`
- [ ] Uji impor video/gambar/audio di perangkat fisik.
- [ ] Uji preview, scrub, trim, split, undo/redo, autosave.
- [ ] Uji ekspor H.264 1080p dan 720p.
- [ ] Uji ekspor tanpa audio dan dengan audio.
- [ ] Uji impor/ekspor SRT dan VTT.
- [ ] Uji reverse klip pendek dan freeze frame.
- [ ] Pastikan tidak ada izin `INTERNET` pada APK final.
- [ ] Perbarui `versionCode`/`versionName` di `app/build.gradle.kts`.
- [ ] Perbarui dokumentasi bila format proyek berubah.

## 11. Status verifikasi di repositori ini

Kode ditulis dan ditinjau secara statis. Lingkungan pembuatan tidak memiliki Android SDK, Gradle,
maupun `kotlinc`, sehingga **belum ada build APK atau pengujian runtime** yang dilakukan. Setelah
SDK tersedia, jalankan minimal:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

dan uji manual pada perangkat fisik. Perhatikan khusus area berikut:

1. Komposisi overlay dan `BitmapOverlay` pada berbagai rasio kanvas.
2. Sinkronisasi audio setelah klip audio yang filenya hilang.
3. Transisi di dalam klip pada batas 100 ms dan 1500 ms.
4. Perilaku preview saat diedit ketika sedang diputar.
5. Ekspor 60 fps dari sumber 30 fps (harus turun/bertahan, bukan menambah detail).
