# Fitur dan Batasan

Dokumen ini memetakan kemampuan iTools, cara kerjanya, dan batasan yang memang disengaja. Semua
fitur tersedia gratis dan lokal. Tidak ada tombol beli, kredit, langganan, iklan, atau watermark.

## Ringkasan ketersediaan

| Area | Fitur | Status |
| --- | --- | --- |
| Proyek | Proyek lokal, template rasio, rename, duplikat, hapus | Tersedia |
| Proyek | Autosave, undo/redo, snapshot JSON | Tersedia |
| Media | Impor video/gambar/audio dari galeri atau SAF | Tersedia |
| Media | Terima media dari intent `SEND` / `SEND_MULTIPLE` | Tersedia |
| Timeline | Multi-track, geser, trim, split, hapus, mute, sembunyikan track | Tersedia |
| Timeline | Zoom, scrubbing, penanda ketukan/hening | Tersedia |
| Teks | Teks kaya, font, warna, stroke, bayangan, latar, perataan | Tersedia |
| Teks | Animasi fade, slide, pop, typewriter | Tersedia |
| Stiker | Emoji sistem, ukuran, rotasi, animasi | Tersedia |
| Warna | 15 filter preset + intensitas | Tersedia |
| Warna | Brightness, contrast, saturation, temperature, tint, vignette, grain | Tersedia |
| Warna | Auto-enhance berbasis histogram lokal | Tersedia |
| Transformasi | Skala, rotasi, posisi, opasitas | Tersedia |
| Animasi | Keyframe transformasi, opasitas, volume | Tersedia |
| Kecepatan | 0,1×–8×, audio ikut di-retime | Tersedia |
| Kecepatan | Reverse video lokal (maks. 15 detik) | Eksperimental |
| Kecepatan | Freeze frame di playhead | Tersedia |
| Transisi | Fade hitam/putih, zoom, geser kiri/atas | Tersedia, di dalam klip |
| Masker | Linear, lingkaran, persegi, feather, inversi | Tersedia |
| Chroma | Key color, similarity, smoothness, spill reduction | Tersedia |
| Audio | Volume, mute, fade in/out, voice-over, musik prosedural | Tersedia |
| Audio | Deteksi ketukan (BPM) dan keheningan | Tersedia |
| Subtitle | Tambah/edit/hapus, impor SRT, ekspor SRT/VTT | Tersedia |
| Subtitle | Bagi naskah ke subtitle memakai timing lokal | Tersedia |
| Kanvas | 9:16, 16:9, 1:1, 4:5, 21:9, 4K, tukar orientasi, warna latar | Tersedia |
| Ekspor | H.264/HEVC, bitrate, fps, fit mode, audio on/off | Tersedia |
| Ekspor | Simpan ke Galeri `Movies/iTools`, bagikan, buka | Tersedia |
| Privasi | Tanpa izin `INTERNET`, tanpa akun/analytics/billing | Tersedia |

## Detail per area

### 1. Proyek dan penyimpanan

- Setiap proyek adalah folder `files/projects/<id>/` berisi `project.json`, `thumb.jpg`, dan
  `assets/`.
- Media yang diimpor **disalin** ke folder proyek, sehingga proyek tetap utuh meskipun file asli
  dipindahkan atau dihapus.
- `project.json` ditulis secara atomik (`.tmp` lalu rename) dan memakai `prettyPrint`, sehingga
  dapat diperiksa atau diarsipkan.
- Autosave berjalan setelah perubahan dengan jeda singkat; tombol simpan manual juga tersedia.
- Undo/redo memakai snapshot proyek (maksimal 60 langkah). Gestur slider dan drag timeline
  dibungkus satu transaksi agar tidak menghasilkan puluhan entri undo.
- Template awal: Vlog vertikal 9:16, YouTube 16:9, Feed persegi 1:1, Cinema 21:9, Story 4:5, dan
  Ultra HD vertikal 4K.

### 2. Impor dan track

- Impor tunggal atau batch dari galeri/SAF. Tipe track dipilih otomatis: audio ke track audio,
  video/gambar ke track video, atau overlay bila diminta.
- Bila track tujuan sudah terisi pada rentang waktu yang sama, iTools membuat track baru alih-alih
  menumpuk klip.
- Track video utama, overlay video, audio, teks, stiker, dan subtitle dapat dimute atau
  disembunyikan. Track subtitle dapat dihapus seluruhnya.
- Media yang hilang tidak memotong durasi komposisi: preview/ekspor mengisi slotnya dengan warna
  latar (video) atau keheningan (audio), lalu menampilkan pesan yang sesuai.

### 3. Timeline

- Sumbu waktu dengan penggaris, playhead, dan indikator durasi.
- Ketuk klip untuk memilih; geser untuk memindahkan; tarik tepi kiri/kanan untuk trim.
- Split pada posisi playhead; keyframe ikut dibagi dan digeser relatif terhadap klip baru.
- Snapping/geser dibatasi agar klip tidak bertumpuk. Memindahkan klip melewati tetangganya tidak
  menukar urutan otomatis; gunakan celah kosong atau geser tetangga terlebih dahulu.
- Trim ujung dibatasi oleh awal klip berikutnya dan sisa durasi sumber. Klip tidak dapat lebih
  pendek dari 100 ms.
- Menandai ketukan/keheningan menambahkan garis bantu pada timeline; tidak memotong klip otomatis.
- Zoom timeline mengubah kerapatan piksel per detik, bukan skala preview.

### 4. Teks dan stiker

- Teks dirender menjadi bitmap di perangkat, lalu dikomposisikan sebagai overlay. Karena itu hasil
  preview dan ekspor identik dan tidak membutuhkan font server.
- Properti teks: isi, ukuran, warna, latar, stroke, bayangan, letter spacing, perataan, dan
  keluarga font sistem (sans, serif, mono, cursive).
- Animasi teks: tanpa animasi, fade in, slide up, pop, dan typewriter.
- Stiker memakai emoji dari font sistem. Tampilan emoji bergantung pada versi Android dan font
  emoji perangkat; tidak ada aset emoji berlisensi yang dibundel.
- Durasi overlay dapat diatur 0,5–15 detik; posisi/animasi memakai keyframe yang sama dengan video.

### 5. Warna, filter, dan auto-enhance

- 15 filter preset: Asli, Vivid, Hangat, Dingin, Mono, Noir, Vintage, Film, Fade, Teal Orange,
  Pastel, Cyberpunk, Golden, Forest, dan Sunset.
- Filter disimpan sebagai nilai warna di JSON, bukan sebagai LUT eksternal.
- Koreksi manual: brightness, contrast, saturation, temperature, tint, vignette, grain.
- Auto-enhance menganalisis histogram frame lokal lalu mengusulkan koreksi. Ini heuristik, bukan
  model AI awan.

### 6. Transformasi, opasitas, dan keyframe

- Transformasi: skala X/Y, rotasi, offset X/Y, opasitas.
- Keyframe menyimpan skala, rotasi, offset, opasitas, dan volume pada waktu tertentu.
- Interpolasi linear antar keyframe, dipakai sama persis oleh preview dan ekspor.
- Menambah keyframe saat playhead berada di dalam klip; keyframe terdekat (toleransi ±16 ms)
  diganti. Menghapus keyframe memakai keyframe terdekat.
- Bila klip sudah memiliki keyframe, perubahan slider transformasi/opasitas/volume ditulis sebagai
  keyframe pada playhead.

### 7. Kecepatan, reverse, dan freeze frame

- Kecepatan 0,1×–8× mengubah durasi timeline dan ikut mempercepat/melambatkan audio.
- Fade audio/video 0–2,5 detik per sisi; fade dibatasi setengah durasi klip.
- **Reverse** adalah re-render lokal bingkai demi bingkai:
  - Maksimal 15 detik dan 24 fps.
  - Audio dibuang.
  - Hasilnya menjadi file video baru di folder proyek.
  - Cocok untuk klip pendek; klip panjang akan ditolak agar tidak kehabisan memori.
- **Freeze frame** mengambil bitmap pada playhead, menyimpannya sebagai JPEG, lalu menambahkannya
  sebagai klip gambar 2,5 detik di track overlay. Klip asli tidak berubah.

### 8. Transisi

- Jenis: fade hitam, fade putih, zoom, geser kiri, geser atas, durasi 100–1500 ms.
- Transisi diterapkan pada awal/akhir satu klip, bukan sebagai overlap dua klip.
- Media3 Transformer tidak mendukung dua klip yang saling tumpang tindih dalam satu sequence, jadi
  crossfade antar-klip tidak tersedia. Ini batasan arsitektur, bukan fitur yang dikunci.

### 9. Masker dan chroma key

- Masker linear, lingkaran, dan persegi dengan pusat X/Y, lebar, tinggi, rotasi, feather, dan
  inversi.
- Chroma key memilih warna kunci RGB, similarity, smoothness, dan spill reduction.
- Keduanya diimplementasikan sebagai shader GL kustom yang berjalan di pipeline Media3, sehingga
  berlaku di preview dan ekspor.
- Area transparan hasil masker/chroma mengikuti warna latar kanvas pada track video utama. Pada
  track overlay, area transparan memperlihatkan track di bawahnya.

### 10. Audio

- Volume 0–200%, mute per klip dan per track, fade in/out.
- Voice-over direkam dari mikrofon ke `assets/voice_*.m4a`, lalu diletakkan pada playhead.
- Musik prosedural dibuat dengan osilator lokal (tanpa sampel pihak ketiga) selama durasi proyek,
  dibatasi 10–180 detik.
- Analisis audio memakai decoder Media3 dan heuristik energi:
  - Deteksi ketukan menghasilkan perkiraan BPM dan daftar waktu ketukan.
  - Deteksi keheningan menghasilkan daftar awal segmen hening.
  - Tidak ada speech-to-text dan tidak ada pengiriman audio ke server.

### 11. Subtitle

- Subtitle adalah clip pada track `CAPTION`; dapat dipilih, digeser, diubah durasinya, dan dihapus.
- Impor SRT lewat file picker; ekspor SRT dan VTT lewat `CreateDocument`.
- Naskah ke subtitle: satu baris = satu subtitle. Bila ada audio/video, pembagian waktu mengikuti
  segmen hening; bila tidak ada, durasi dibagi merata. Ini bukan transkripsi otomatis.
- Gaya subtitle memakai properti teks yang sama (ukuran, warna, latar, tebal, bayangan).

### 12. Kanvas dan ekspor

- Kanvas menentukan rasio, dimensi dasar, fps proyek, fit mode, dan warna latar.
- Preset ekspor dihitung ulang dari rasio kanvas agar tidak terjadi peregangan; label menampilkan
  dimensi hasil perhitungan.
- Preset: 720p/1080p/2K/4K pada 30 atau 60 fps, masing-masing dengan bitrate awal yang wajar.
- Codec: H.264 (paling kompatibel) atau HEVC (lebih kecil, dukungan perangkat bervariasi).
- `FrameDropEffect` hanya menurunkan fps; ekspor tidak dapat menambah fps di atas sumber.
- Ekspor memakai `Transformer` dengan encoder perangkat. Hasil disimpan ke MediaStore
  `Movies/iTools` pada Android 10+, atau ke penyimpanan aplikasi dengan `FileProvider` sebagai
  fallback.
- Tidak ada watermark, batas durasi, resolusi berbayar, atau antrean server.

## Batasan yang diketahui

1. **Crossfade antar-klip tidak didukung.** Transisi berjalan di dalam durasi klip.
2. **Reverse terbatas 15 detik, 24 fps, tanpa audio.** Ini fitur eksperimental berbasis urutan
   frame, bukan reverse streaming.
3. **FPS ekspor tidak bisa lebih tinggi dari sumber.** Tidak ada frame interpolation.
4. **Tidak ada speech-to-text.** Subtitle dari naskah hanya membantu timing.
5. **Tidak ada pencampuran lebih dari satu track video utama.** Track overlay terpisah dapat
   ditumpuk, tetapi urutan kompositing mengikuti urutan track.
6. **Emoji bergantung pada font sistem.** Tampilan dapat berbeda antar perangkat.
7. **Timeline tidak melakukan ripple delete atau auto-reorder.** Menggeser klip tidak menggeser
   klip lain secara otomatis.
8. **Beberapa operasi berat (4K, reverse, ekspor) membutuhkan RAM dan codec perangkat.** Perangkat
   kelas bawah dapat menolak resolusi tinggi.
9. **Project JSON adalah format internal.** Kompatibilitas versi lama dijaga dengan nilai default,
   tetapi format dapat berubah antar versi aplikasi.
10. **Belum ada verifikasi build/runtime di lingkungan ini.** Lihat [BUILD.md](BUILD.md).
