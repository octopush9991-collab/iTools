# Arsitektur

iTools adalah aplikasi Android satu modul dengan pemisahan jelas antara model proyek, penyimpanan,
pipeline render Media3, dan UI Compose. Tidak ada backend, database, atau sesi jaringan.

## 1. Lapisan

```text
┌──────────────────────────────────────────────────────────────┐
│ UI Compose                                                   │
│ HomeScreen / EditorScreen / SettingsScreen                   │
│ TimelineView, panel alat, dialog, snackbar                   │
└───────────────────────────┬──────────────────────────────────┘
                            │ StateFlow + event
┌───────────────────────────▼──────────────────────────────────┐
│ ViewModel                                                    │
│ HomeViewModel: daftar proyek, template, media masuk          │
│ EditorViewModel: edit, undo/redo, autosave, busy, ekspor     │
└───────────────┬───────────────────────────┬──────────────────┘
                │                           │
┌───────────────▼──────────────┐ ┌──────────▼──────────────────┐
│ ProjectOps (pure functions)  │ │ ProjectStore                │
│ split/trim/move/duplicate    │ │ JSON, aset, thumbnail, probe│
│ keyframe, warna, teks, dsb.  │ │ salinan media per proyek    │
└───────────────┬──────────────┘ └──────────┬──────────────────┘
                │                           │
┌───────────────▼───────────────────────────▼──────────────────┐
│ Render                                                       │
│ CompositionFactory → Media3 Composition                      │
│ PreviewController  → CompositionPlayer                       │
│ ExportEngine       → Transformer                             │
│ FrameTools         → reverse / freeze frame                  │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│ Efek & audio                                                 │
│ ColorGradeEffect, ChromaKeyEffect, MaskEffect,               │
│ KeyframedTransformEffect, OpacityEffect, TransitionEffect,   │
│ OverlayFactory/ProjectOverlay, VolumeAudioProcessor,         │
│ AudioAnalyzer, ProceduralMusic, WavWriter                    │
└──────────────────────────────────────────────────────────────┘
```

Prinsip utama:

1. **Satu sumber kebenaran.** `EditorViewModel` memegang `Project`; semua panel mengirim aksi,
   bukan memutasi model sendiri.
2. **Operasi murni.** `ProjectOps` menerima `Project` dan mengembalikan `Project` baru. Ini membuat
   undo cukup berupa snapshot dan memudahkan pengujian.
3. **Satu pipeline render.** Preview dan ekspor memakai `CompositionFactory` yang sama; perbedaannya
   hanya ukuran output, fps, dan apakah audio disertakan.
4. **Offline secara struktur.** Tidak ada dependensi jaringan, izin `INTERNET`, atau endpoint.

## 2. Model proyek

### Root `Project`

| Field | Arti |
| --- | --- |
| `schema` | Versi format, saat ini `1` |
| `id` | UUID proyek, juga nama folder |
| `name`, `createdAt`, `updatedAt` | Metadata |
| `canvas` | Ukuran, fps, warna latar, fit mode |
| `tracks` | Daftar track dan klip |
| `export` | Lebar, tinggi, fps, codec, bitrate, fit, audio |
| `thumbnail` | Path relatif thumbnail |
| `beatMarkersMs`, `markersMs` | Penanda hasil analisis |

### `Track`

Tipe: `VIDEO`, `OVERLAY`, `AUDIO`, `TEXT`, `STICKER`, `CAPTION`. Setiap track punya `muted`,
`hidden`, dan `locked`.

### `Clip`

Field penting:

- `kind`: `VIDEO`, `IMAGE`, `AUDIO`, `TEXT`, `STICKER`, `CAPTION`.
- `uri`: path relatif `assets/...` atau URI absolut lama.
- `startMs`, `durationMs`: posisi dan durasi **di timeline** (setelah speed).
- `sourceStartMs`, `sourceDurationMs`: rentang sumber **sebelum speed**.
- `sourceTotalMs`: durasi penuh sumber; membatasi perluasan saat trim ujung. `0` berarti tidak
  diketahui (teks, gambar, atau proyek lama).
- `speed`, `volume`, `muted`, `fadeInMs`, `fadeOutMs`, `opacity`.
- `transform`, `color`, `chroma`, `mask`, `text`, `sticker`, `transitionIn/Out`, `keyframes`.
- `reversed`, `sourceWidth`, `sourceHeight`, `sourceRotation`.

Keyframe menyimpan `timeMs` lokal terhadap awal klip beserta skala, rotasi, offset, opasitas, dan
volume. Interpolasi linear ada di `KeyframeMath` dan dipakai oleh efek serta compositor.

### Contoh `project.json`

```json
{
  "schema": 1,
  "id": "0f4f...",
  "name": "Vlog liburan",
  "canvas": { "width": 1080, "height": 1920, "fps": 30, "background": -16777216, "fit": "FIT" },
  "tracks": [
    {
      "id": "a1...",
      "type": "VIDEO",
      "clips": [
        {
          "id": "c1...",
          "kind": "VIDEO",
          "uri": "assets/video_1710000000000.mp4",
          "startMs": 0,
          "durationMs": 4200,
          "sourceStartMs": 500,
          "sourceDurationMs": 4200,
          "sourceTotalMs": 12000,
          "speed": 1.0
        }
      ]
    }
  ],
  "export": { "width": 1080, "height": 1920, "fps": 30, "codec": "H264", "bitrateMbps": 12 }
}
```

`Json` memakai `ignoreUnknownKeys = true`, `encodeDefaults = true`, dan `prettyPrint = true`.
Field baru harus memiliki nilai default agar proyek lama tetap terbaca.

## 3. Penyimpanan

`ProjectStore` memakai penyimpanan privat aplikasi:

```text
files/projects/<projectId>/
├── project.json
├── project.json.tmp        # ditulis lalu di-rename (atomik)
├── thumb.jpg
└── assets/
    ├── video_<timestamp>.mp4
    ├── image_<timestamp>.jpg
    ├── voice_<timestamp>.m4a
    ├── reverse_<timestamp>.mp4
    └── music_<timestamp>.wav
```

- Impor menyalin media ke `assets/`; proyek tidak bergantung pada izin URI sementara.
- `resolve()` mengubah path relatif menjadi `file://` dan membiarkan `content://`/`file://` apa
  adanya untuk kompatibilitas.
- Cache preview, filler, silence, dan ekspor sementara ada di `cacheDir` dan dapat dibersihkan dari
  Pengaturan.
- Ekspor final memakai MediaStore `Movies/iTools` (Android 10+) atau direktori aplikasi
  `Movies/iTools` + `FileProvider` (fallback).

## 4. Alur edit dan undo

```text
Panel / Timeline
    │ aksi (setSpeed, trimEndBy, setMask, ...)
    ▼
EditorViewModel.edit / apply
    │ ProjectOps.xxx(project, ...)
    ▼
Project baru ──► pushUndo(project lama)
    │
    ├─► _state.update(project, previewVersion + 1, undoCount)
    └─► scheduleSave(project)
```

- `previewVersion` memicu rebuild preview dengan debounce ±330 ms di `EditorScreen`.
- Autosave memakai debounce dan `ProjectStore.save()`.
- Slider memakai transaksi:

```text
beginTransaction()  -> simpan baseline pertama
...banyak perubahan...
endTransaction()    -> pushUndo(baseline) bila berubah, lalu bersihkan baseline
```

- `beginTransaction()` idempoten; `endTransaction()` dipanggil saat gestur selesai, panel ditutup,
  panel diganti, atau tombol back ditekan agar baseline tidak tertinggal.
- Operasi async memakai `launchBusy()` dengan `try/finally`, sehingga indikator sibuk selalu
  dibersihkan meskipun terjadi error.

## 5. Pipeline render

### 5.1 Composition

`CompositionFactory.build()` menghasilkan:

1. Satu sequence video utama (track `VIDEO` pertama yang visible).
2. Satu sequence untuk setiap track `OVERLAY`.
3. Satu sequence audio untuk setiap track `AUDIO` (bila audio disertakan).

Media3 menolak gap pada sequence, jadi:

- Celah visual diisi item gambar 64×64: warna latar untuk video utama, transparan untuk overlay.
- Celah audio diisi WAV senyap 8 kHz mono yang di-cache per durasi.
- Klip dengan media hilang tetap mempertahankan slot waktunya (filler/silence), bukan memotong
  komposisi.

Setiap item video dinormalisasi ke ukuran output dengan `Presentation` dan `FitMode`:

- `FIT` → letterbox,
- `CROP` → isi lalu potong,
- `STRETCH` → regangkan.

### 5.2 Efek per klip

Urutan efek video kira-kira:

1. Speed effect (bila `speed != 1` dan bukan gambar).
2. `Presentation` (ukuran kanvas).
3. Transformasi/keyframe dan opasitas (track utama).
4. `ColorGradeEffect`.
5. `ChromaKeyEffect` (bila ada).
6. `MaskEffect` (bila ada).
7. `TransitionEffect` (bila ada).

Audio memakai `SpeedChangingAudioProcessor` bila perlu dan `VolumeAudioProcessor` untuk volume,
fade, serta keyframe volume.

### 5.3 Overlay teks/stiker/subtitle

`OverlayFactory` merender `TextSpec`/`StickerSpec` menjadi bitmap dengan `Canvas` Android. Setiap
overlay menjadi `ProjectOverlay` yang mengimplementasikan `BitmapOverlay` Media3. Overlay
dikomposisikan pada level composition lewat `OverlayEffect`, sehingga hasilnya menyatu dengan video
dan ikut terekspor.

Posisi/animasi overlay memakai NDC `-1..1`. Skala relatif terhadap piksel overlay. Alpha
menggabungkan opasitas/keyframe dengan fade in/out.

### 5.4 Compositor

`ProjectVideoCompositorSettings` memberi setiap input posisi di kanvas:

- Input 0 (video utama) selalu memenuhi frame.
- Input overlay memakai `OverlaySettings` dinamis berdasarkan waktu presentasi: cari klip aktif,
  hitung transform/opasitas/keyframe, lalu set alpha, anchor, skala, dan rotasi.
- Bila tidak ada klip aktif pada waktu itu, alpha dibuat `0` sehingga overlay transparan.

### 5.5 Preview

`PreviewController` memiliki `CompositionPlayer` dengan
`PreviewingMultipleInputVideoGraph.Factory()`. Karena satu player hanya memuat satu composition,
setiap perubahan membuat player baru:

- Posisi terakhir dipertahankan.
- Status playing dipertahankan; bila berada di akhir, playback kembali ke awal.
- Ukuran preview dibatasi short edge 720 px untuk menghemat GPU/RAM.
- Error player dipublikasikan sebagai state dan dapat dicoba ulang dari UI.

### 5.6 Ekspor

`ExportEngine`:

1. Membangun composition dengan ukuran/fps dari `project.export`.
2. Membuat `DefaultEncoderFactory` dengan bitrate dan codec yang dipilih.
3. Menjalankan `Transformer` di main thread (butuh Looper).
4. Melaporkan progress lewat `ProgressHolder`.
5. Menyalin hasil ke MediaStore/penyimpanan aplikasi dan mengembalikan `State.Success`.
6. `cancel()` menghentikan Transformer dan membersihkan file sementara.

`FrameDropEffect` ditambahkan di level composition untuk menurunkan fps. Efek ini tidak dapat
menaikkan fps di atas frame sumber.

## 6. Audio dan alat lokal

- `AudioAnalyzer.decodeEnvelope()` mendekode audio menjadi envelope energi; dipakai untuk BPM dan
  deteksi keheningan.
- `ProceduralMusic` membuat WAV dengan osilator dan pola lokal; tidak memakai sampel pihak ketiga.
- `VoiceRecorder` memakai `MediaRecorder` ke file proyek.
- `SubtitleIO` mem-parse/menulis SRT dan VTT tanpa pustaka eksternal.
- `AutoEnhance` mengambil frame, menghitung statistik warna, lalu mengusulkan `ColorGrade`.
- `FrameTools.reverse()` mengambil frame pada interval 24 fps (maks. 15 detik), membalik urutannya,
  dan meng-encode ulang menjadi video tanpa audio.
- `FrameTools.freezeFrame()` mengambil bitmap pada waktu tertentu dan menyimpannya sebagai JPEG.

## 7. Threading

| Pekerjaan | Thread |
| --- | --- |
| Compose & state | Main |
| Baca/tulis JSON, salin media, thumbnail | `Dispatchers.IO` |
| CompositionPlayer / Transformer | Main (butuh Looper) |
| Decode audio, analisis, tulis WAV | `Dispatchers.IO` |
| Reverse/freeze frame | Coroutine ViewModel + IO untuk file |

`launchBusy()` membungkus operasi berat, menangani `CancellationException`, dan selalu membersihkan
status sibuk.

## 8. Navigasi dan lifecycle

- `MainActivity` mengaktifkan edge-to-edge, menangani intent `SEND`/`SEND_MULTIPLE`, lalu meneruskan
  URI ke `HomeViewModel`.
- `IToolsRoot` memakai Navigation Compose: `home`, `editor/{projectId}`, `settings`.
- `EditorScreen` membuat `CompositionFactory`, `PreviewController`, dan `ExportEngine` dengan
  `remember`; `DisposableEffect` merilis player saat layar ditinggalkan.
- `BackHandler` menutup panel/rename terlebih dahulu, lalu menyimpan proyek dan kembali.

## 9. Titik ekstensi

- **Efek baru:** implementasikan `GlEffect`/`BaseGlShaderProgram` atau `BitmapOverlay`, lalu
  daftarkan di `CompositionFactory` dan model.
- **Alat lokal baru:** tambahkan fungsi murni/IO di `core/tools`, aksi di `EditorViewModel`, dan
  panel di `ui/editor`.
- **Format ekspor baru:** tambahkan preset di `Defaults.kt` dan sesuaikan `ExportEngine`.
- **Format proyek:** naikkan `Project.SCHEMA`, tambahkan field dengan default, dan dokumentasikan
  migrasi. `ignoreUnknownKeys` menjaga kompatibilitas baca.

## 10. Batasan arsitektur

- Media3 tidak mendukung klip yang tumpang tindih dalam satu sequence, sehingga transisi berupa
  fade/zoom/slide di dalam klip, bukan crossfade.
- Satu composition player hanya untuk satu composition; setiap edit membangun player baru.
- Compositor kustom harus mengikuti kontrak `VideoCompositorSettings` dan `OverlaySettings`.
- FrameDropEffect hanya menurunkan fps.
- Tidak ada sinkronisasi atau backup awan; seluruh data berada di perangkat.
