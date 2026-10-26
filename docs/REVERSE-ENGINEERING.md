# Laporan Reverse Engineering — CapCut Android 19.9.0

> Status: **analisis statis selesai** untuk pertanyaan arsitektur, model editing, fitur, dan monetisasi.
> Tidak ada eksekusi aplikasi, emulator, atau observasi runtime pada laporan ini.

## 1. Identitas target

| Item | Nilai | Bukti |
|---|---|---|
| Berkas | `/root/proyek/capcut.apk` | berkas yang diberikan pengguna |
| Ukuran berkas | 322.419.575 byte (308 MiB) | `stat` / `ls -l` |
| Ukuran isi APK | 855.740.473 byte, 28.058 entri | `unzip -l` |
| SHA-256 | `5407cedcb9b7e5908d8344dffafe6f93f7d6a2fa3b7b4a4711c66dbf9ea0f572` | `sha256sum` |
| Package | `com.lemon.lvoverseas` | `AndroidManifest.xml` (AXML) |
| Versi | `versionName=19.9.0`, `versionCode=19900200` | manifest |
| SDK | `minSdkVersion=23`, `targetSdkVersion=36`, `compileSdkVersion=36` | manifest |
| ABI | `arm64-v8a`, `armeabi-v7a` (173 + 172 `.so`) | `unzip -l` |
| DEX | 40 `classes*.dex` + `assets/audience_network.dex` | `unzip -l`, header DEX |
| Class | **325.337** class di 40 DEX | parser DEX (offset `class_defs`) |
| Entry point | `com.vega.launcher.ScaffoldApplication`, `com.vega.main.MainActivity` | manifest |
| Komponen | 496 `<activity>`, 51 `<service>`, 30 `<receiver>` | manifest |

### Metode dan batas bukti

REA MCP tidak terpasang pada sesi ini, sehingga alur Android MCP
(`inspect_android_package`) tidak tersedia. Analisis dilakukan dengan alat lokal:

1. Parser AXML mandiri untuk `AndroidManifest.xml` biner.
2. Parser DEX mandiri untuk memetakan 325.337 class ke DEX asalnya.
3. Parser string pool `resources.arsc` untuk inventaris teks UI.
4. JADX CLI 1.x (`/opt/jadx/bin/jadx`) untuk dekompilasi terarah per class
   (`--single-class`) pada DEX yang memuat class tersebut.
5. Inventaris `unzip -l` untuk aset, native library, dan DEX.

Keterbatasan yang harus dipertahankan:

- **Statis saja.** Tidak ada bukti eksekusi, jadi perilaku runtime, nilai server,
  dan hasil render tidak dapat diklaim dari laporan ini.
- Dekompilasi JADX dapat kehilangan tipe generik, inline, dan nama asli
  (banyak class/enum ter-obfuscate atau di-rename).
- APK adalah base APK bundle; split konfigurasi/DPI/feature tidak dianalisis.
- Analisis difokuskan pada arsitektur dan fitur, bukan membongkar algoritma
  efek, model AI, atau protokol akun/pembayaran.
- Tidak ada kredensial, token, atau data pengguna yang diambil.

## 2. Temuan arsitektur

### 2.1 Lapisan aplikasi

```
Launcher / precondition      com.vega.launcher.*
  ├─ ScaffoldApplication
  ├─ DeeplinkActivity / ApplinksHandlerActivity / ShareFromOutsideActivity
  └─ MainActivity (singleTask, portrait, exported)
Main shell                    com.vega.main.*
  ├─ BaseMainActivity + Page enum
  └─ Home / Feed / Discover / AI Lab / User / Draft / ...
Editor                        com.vega.edit.*, com.vega.container.session.*
  ├─ EditActivity (host)
  ├─ SessionManager / CommonSession (lifecycle, coroutine, resource)
  └─ Panel/dock per tool
Editing engine                com.vega.middlebridge.*  →  libmiddle-bridge.so
  ├─ SWIG model: Draft, Track, Segment, CanvasConfig, keyframes
  ├─ LyraSession / LyraServer (native session)
  └─ LVVEModuleJNI (14.167 class SWIG)
Export                        com.vega.export.*, com.lemon.editexport.*
Draft / project               com.vega.draft.*
  └─ folder draft + <name>.json + media + attachment JSON
Ability layer                 com.vega.ability.*
  └─ EditAbility, AsrAbility, RetouchAbility (task-based SPI)
Subscription / pay            com.vega.subscription.*, com.vega.pay.*
Dynamic UI                    liblynx*.so + assets/offline/image_lynx_*
AI / media native             libbytenn, libwhisper, libretouch_sdk, libttffmpeg
```

**Observasi:** codebase utama adalah `com.vega` (159.354 class, 24 DEX).
`com.bytedance` (28.988), `com.xt` (26.111), `com.lemon` (14.069),
`com.ss` (7.915), `cn.capcut` (1.681) adalah lapisan infrastruktur,
editor lama, monetisasi, dan SDK pihak ketiga.

**Inferensi:** CapCut 19.9.0 adalah aplikasi modular monolit dengan UI hybrid:
Compose/View native untuk shell dan panel, Lynx untuk halaman dinamis
(feed, subscription, campaign), dan NLE C++ melalui SWIG untuk timeline.
Tidak ada bukti bahwa editor memakai engine pihak ketiga generik; model
`lvve::adapter` dan `libmiddle-bridge.so` adalah engine internal.

### 2.2 Model editing (NLE)

Dekompilasi `com.vega.middlebridge.swig.LVVETrackType` menunjukkan tipe track:

```text
TrackTypeNone, TrackTypeVideo, TrackTypeAudio, TrackTypeSticker,
TrackTypeText, TrackTypeVideoEffect, TrackTypeFilter, TrackTypeAdjust,
TrackTypeTextToVideoText, TrackTypeTextToVideoTextAudio,
TrackTypeComposition, TrackTypePluginEffect, TrackTypeAdCube, TrackTypeMixed
```

Model `Draft` (dekompilasi `com.vega.middlebridge.swig.Draft`) memiliki:

- `getTracks()`, `getRelationships()`, `getCanvasConfig()`, `getConfig()`,
  `getCover()`, `getExtraInfo()`, `getMutableConfig()`, `getPlatform()`;
- `getCreateTime()`, `getUpdateTime()`, `getDuration()`, `getName()`,
  `getDraftType()`, `deepCopy()`;
- objek `Track` berisi `VectorOfSegment` dan `LVVETrackFlagType`;
- objek `Segment` berisi `TimeRange`, `LVVESegmentSource`,
  `LVVESegmentState`, `VectorOfCommonKeyframes`, dan `LVVEMetaType`.

**Observasi:** `SessionManager` mengelola sesi NLE per ID
(`ConcurrentHashMap<Integer, ISession>`), membuat `LyraSession` lewat
`LyraServer.openSession(...)`, mendaftarkan callback JNI
(`SetEffectRenderIndexFetcher`), dan memakai aturan track global
(`RuleHelper`, `LVVESegTrackType`, `LVVE...JointType`).
`LyraSession` juga menyediakan `invoke`, `invokeAsync`, `invokeSync`, serta
`initDraft(Draft)` dan `initVeWrapper(VEAdapterConfig)`.

**Inferensi:** setiap editor membuka sesi native; draft adalah dokumen
non-linear (track → segment → material/effect/keyframe) yang dimutasi lewat
perintah NLE, bukan model data Kotlin murni. Render preview dan export
memakai jalur native yang sama.

### 2.3 Penyimpanan proyek

`BaseDraftDiskHelper` dan `DraftHelper` menunjukkan struktur:

```
<draft dir>/
  <name>.json
  <name>_temp.json
  <name>_import_res_path_map
  attachment_editing.json
  common_attachment/attachment_image_editor_common.json
  <media / cache / cover>
```

**Observasi:** proyek disimpan sebagai JSON + aset pendamping, dengan file
temp dan path map untuk relokasi material. `DraftExtraManager` memisahkan
metadata tambahan dari dokumen utama.

**Implikasi untuk iTools:** dokumen proyek JSON + folder aset per proyek
adalah pendekatan yang terbukti; iTools memakai skema sendiri yang lebih
sederhana dan dapat dibaca manusia.

### 2.4 Export

Dekompilasi `com.vega.export.edit.view.ExportActivity` menunjukkan pemisahan
panel: `ExportPreparePanel`, `ExportProcessPanel`, `ExportFailPanel`,
`ExportResultPage`, `ExportTopPanel`, `ExportMainPanel`,
`DirectShareExportMainPanel`, serta `ExportStrategyDispatcher` dan
`BaseEditExportViewModel`. String resource memuat opsi 720p/1080p/2K/4K,
30/60 fps, H.264/HEVC, Smart HDR, bitrate, dan "Ultra HD".

**Inferensi:** export adalah state machine UI yang memanggil pipeline native;
resolusi/format adalah konfigurasi permintaan, bukan jalur kode terpisah.

### 2.5 Fitur (bukti string resource)

Dari 295.577 string `resources.arsc` (89.430 string ASCII unik), kategori
yang terkonfirmasi:

- **Trim/split/cut, speed, volume, fade, freeze, reverse, ratio, frame rate**
- **Filter, effect, transition, adjust, keyframe, mask, chroma key/green screen**
- **Text, sticker, caption/subtitle, text-to-speech, voiceover, audio extract**
- **Music, audio effects, vocal separation/beauty, noise reduction/enhance**
- **Template, draft, export, watermark, aspect ratio, canvas, background**
- **AI avatar, AI background, AI edit, AI enhance, AI image/music/script/video,
  AI cutout/matting, AI transition/filter/effect, AI sound effects**
- **Subscription: Pro/Standard/Ultra, free trial, credits, watermark removal**

**Penting:** keberadaan string hanya membuktikan permukaan fitur yang
dideklarasikan, bukan bahwa setiap fitur benar-benar berjalan di semua device.

### 2.6 Monetisasi, privasi, dan ketergantungan jaringan

- Permission mencakup `com.android.vending.BILLING`, `AD_ID`, attribution,
  `INTERNET`, `ACCESS_NETWORK_STATE`, notifikasi, kamera, mikrofon, media,
  kalender, dan overlay (`SYSTEM_ALERT_WINDOW`).
- SDK iklan/pihak ketiga terlihat dari paket `com.applovin`, `com.mbridge`,
  `com.vungle`, `com.facebook`, `com.appsflyer`, `com.google`, serta
  `assets/audience_network.dex`.
- `com.vega.subscription.biz.BaseBenefitServiceImpl` memuat konsep benefit,
  credits, dan pengecekan hak fitur; `com.vega.pay` memuat billing observer.
- `libsscronet.so`, `libttnet`, `libpumbaa-network`, dan `configcenter`
  menunjukkan backend config/telemetry/network.
- Lynx memuat bundle UI dari `assets/offline/image_lynx_*`; halaman tertentu
  dapat diperbarui dari server.

**Inferensi:** fitur "gratis vs berbayar" ditegakkan di klien + server
(benefit/credits), dengan watermark dan batas export sebagai bagian dari
strategi monetisasi. Ini tidak dapat — dan tidak akan — direplikasi iTools.

## 3. Yang diadopsi dan yang sengaja tidak diadopsi

| Aspek CapCut | iTools |
|---|---|
| Track NLE: video/audio/text/sticker/filter/adjust | Diadopsi sebagai model data sendiri |
| Draft = JSON + folder aset | Diadopsi, skema baru dan terbuka |
| Engine NLE native/SWIG proprietary | Diganti Media3 Transformer + efek lokal |
| Lynx/UI dinamis dari server | Tidak dipakai; UI 100% Compose lokal |
| Akun, cloud, feed, template online | Tidak ada |
| Iklan, analytics, attribution, push | Tidak ada; tanpa permission INTERNET |
| Subscription/credits/watermark berbayar | Dihapus; semua fitur terbuka |
| Efek/AI model proprietary | Efek lokal + AI heuristik/offline opsional |
| Billing/Play Integrity | Tidak ada |

## 4. Pertanyaan riset dan status

| Pertanyaan | Status | Bukti |
|---|---|---|
| Siapa target dan versinya? | Terjawab | manifest + digest |
| Bagaimana arsitektur editor? | Terjawab (level arsitektur) | paket, SessionManager, LyraSession, SWIG |
| Bagaimana model timeline? | Terjawab | `LVVETrackType`, `Draft`, `Track`, `Segment` |
| Bagaimana proyek disimpan? | Terjawab | `BaseDraftDiskHelper`, `DraftHelper` |
| Fitur apa yang ada? | Terjawab pada level UI/string | `resources.arsc` |
| Bagaimana efek/AI dieksekusi? | **Sebagian** | native `.so` + nama paket; algoritma tidak dibongkar |
| Bagaimana pembayaran/benefit diverifikasi? | **Tidak** | hanya kelas klien; server tidak dianalisis |
| Apakah semua fitur berjalan offline? | **Tidak** | adanya jaringan, cloud, config center |
| Apakah perilaku runtime sesuai dekompilasi? | **Belum diverifikasi** | tidak ada eksekusi |

## 5. Batas hukum dan rekayasa

Laporan ini hanya memakai artefak yang diberikan pengguna untuk memahami
arsitektur. iTools **tidak** menyalin kode, aset, efek, model, nama merek,
atau protokol CapCut. Implementasi iTools adalah perangkat lunak baru dengan
engine Media3 dan desain data sendiri. Merek "CapCut" hanya dirujuk sebagai
pembanding.
