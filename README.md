# iTools

Editor video Android lokal-first dengan pola kerja mirip editor video pendek populer, tetapi
dibangun dari nol memakai Android native, Kotlin, Jetpack Compose, dan AndroidX Media3.

Semua fitur dapat dipakai tanpa akun, langganan, kredit, iklan, paywall, atau watermark. Aplikasi
tidak meminta izin `INTERNET`, sehingga seluruh proses impor, pratinjau, analisis, dan ekspor
berjalan di perangkat.

> iTools bukan produk CapCut dan tidak berafiliasi dengan ByteDance. Tidak ada kode, aset, efek,
> model, merek, atau protokol CapCut yang disalin. Analisis perilaku produk pembanding dicatat
> terpisah di [docs/REVERSE-ENGINEERING.md](docs/REVERSE-ENGINEERING.md).

## Status

Repositori ini berisi kode sumber dan dokumentasi. Lingkungan pembuatan proyek ini tidak memiliki
Android SDK, Gradle, maupun `kotlinc`, jadi APK belum dibangun atau diuji di perangkat. Lihat
[docs/BUILD.md](docs/BUILD.md) untuk langkah build dan daftar hal yang perlu diverifikasi runtime.

## Kemampuan utama

- Proyek multi-track: video utama, overlay video, audio, teks, stiker, dan subtitle.
- Timeline gestur: pilih, geser, potong awal/akhir, zoom, scrubbing, dan penanda ketukan/hening.
- Editing non-destruktif: undo/redo, autosave, snapshot JSON yang dapat dibaca manusia.
- Teks, stiker emoji, subtitle, animasi teks, dan gaya subtitle.
- Filter warna, koreksi warna, auto-enhance lokal, transformasi, opasitas, dan keyframe.
- Kecepatan 0,1×–8×, fade audio/video, mute, volume, dan fade in/out.
- Masker bentuk, chroma key, transisi batas klip, serta overlay berbasis bitmap.
- Alat lokal: deteksi ketukan, deteksi keheningan, musik prosedural, rekaman suara, balik klip,
  freeze frame, impor/ekspor SRT & VTT, dan pembuatan subtitle dari naskah.
- Ekspor H.264/HEVC ke `Movies/iTools` tanpa watermark dan tanpa batas durasi.
- Kanvas 9:16, 16:9, 1:1, 4:5, dan 4K, dengan preset ekspor yang mengikuti rasio kanvas.

Detail per fitur dan batasannya ada di [docs/FEATURES.md](docs/FEATURES.md).

## Teknologi

| Bagian | Pilihan |
| --- | --- |
| Bahasa | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Media | AndroidX Media3 1.7.1 (ExoPlayer, Transformer, Effect, UI Compose) |
| Serialisasi | kotlinx.serialization JSON |
| Preferensi | DataStore Preferences |
| Navigasi | Navigation Compose |
| Thumbnail | Coil |
| Build | AGP 8.7.3, compileSdk/targetSdk 35, minSdk 26, Java 17 |

## Struktur repositori

```text
iTools/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml          # tanpa izin INTERNET
│       ├── java/com/itools/app/
│       │   ├── core/
│       │   │   ├── model/               # Project, Clip, keyframe, preset
│       │   │   ├── project/             # penyimpanan JSON + aset
│       │   │   ├── render/              # composition, preview, export, frame tools
│       │   │   ├── effects/             # shader GL, overlay, transisi
│       │   │   ├── audio/               # analisis, musik prosedural, WAV
│       │   │   ├── media/               # metadata media
│       │   │   └── settings/            # DataStore
│       │   └── ui/
│       │       ├── home/                # daftar proyek & template
│       │       ├── editor/              # timeline, panel alat, ViewModel
│       │       ├── settings/            # preferensi aplikasi
│       │       ├── components/          # widget bersama
│       │       └── theme/               # warna & tipografi
│       └── res/                         # ikon, string, tema, FileProvider
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD.md
│   ├── FEATURES.md
│   └── REVERSE-ENGINEERING.md
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Mulai cepat

Prasyarat: JDK 17, Android SDK Platform 35, dan Gradle 8.9 atau lebih baru (atau Android Studio
yang mendukung AGP 8.7.3).

```bash
# dari root repositori
gradle wrapper --gradle-version 8.9   # sekali saja bila gradle-wrapper.jar belum ada
./gradlew :app:assembleDebug
```

APK debug dihasilkan di `app/build/outputs/apk/debug/`. Panduan lengkap, signing, dan
troubleshooting ada di [docs/BUILD.md](docs/BUILD.md).

## Privasi

- Tidak ada `INTERNET` di manifest.
- Tidak ada SDK iklan, analitik, akun, billing, atau push.
- Media yang diimpor disalin ke penyimpanan privat aplikasi per proyek.
- Analisis audio/gambar berjalan lokal memakai decoder dan heuristik, bukan layanan awan.
- Berbagi/membuka hasil ekspor memakai `FileProvider` dan intent sistem.

## Dokumentasi

- [Fitur dan batasan](docs/FEATURES.md)
- [Arsitektur dan format proyek](docs/ARCHITECTURE.md)
- [Build, uji, dan rilis](docs/BUILD.md)
- [Analisis produk pembanding](docs/REVERSE-ENGINEERING.md)
