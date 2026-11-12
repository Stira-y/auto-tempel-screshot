import 'dart:io';
import 'dart:math';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'komponen_kanvas_coret.dart';
import 'komponen_pemotong_gambar.dart';
import 'model_berkas_gambar.dart';
import 'saluran_metode.dart';

// [FITUR: Layar Editor Gambar] - Halaman pengeditan tangkapan layar 100% offline dengan alat crop rasio, coret/anotasi bebas, rotasi derajat, cermin/mirror, dan simpan permanen ke galeri
class LayarEditorGambar extends StatefulWidget {
  final ModelBerkasGambar berkasGambar;

  const LayarEditorGambar({
    super.key,
    required this.berkasGambar,
  });

  @override
  State<LayarEditorGambar> createState() => _LayarEditorGambarState();
}

enum _ModeAlatEditor {
  coret,
  potong,
  transformasi,
}

class _LayarEditorGambarState extends State<LayarEditorGambar> {
  _ModeAlatEditor _modeAktif = _ModeAlatEditor.transformasi;

  // State Coret-coret
  final List<GarisCoretan> _daftarCoretan = [];
  GarisCoretan? _coretanSedangDitarik;
  Color _warnaKuasDipilih = const Color(0xFFFF3B30); // Merah default
  double _ketebalanKuas = 6.0;

  // Pilihan palet warna sesuai spesifikasi (Merah, Kuning, Hijau, Hitam, Putih)
  final List<Color> _paletWarnaTersedia = const [
    Color(0xFFFF3B30), // Merah
    Color(0xFFFFCC00), // Kuning
    Color(0xFF34C759), // Hijau
    Color(0xFF000000), // Hitam
    Color(0xFFFFFFFF), // Putih
  ];

  // State Pemotongan (Crop)
  RasioPotong _rasioPotongDipilih = RasioPotong.bebas;
  Rect _areaPotongAktif = Rect.zero;
  Rect _batasGambarPadaLayar = Rect.zero;

  // [FITUR: State Transformasi Mirror & Rotasi Derajat]
  bool _cerminHorizontal = false;
  bool _cerminVertikal = false;
  int _rotasiKelipatan90 = 0; // 0, 1, 2, 3 (0°, 90°, 180°, 270°)
  double _sudutRotasiDerajat = 0.0; // Slider derajat halus (-45° s/d +45°)

  double get _totalRotasiRadian =>
      (_rotasiKelipatan90 * 90 + _sudutRotasiDerajat) * (pi / 180.0);

  // Metadata Gambar Asli
  ui.Image? _gambarAsli;
  bool _sedangMemuat = true;
  bool _sedangMenyimpan = false;

  @override
  void initState() {
    super.initState();
    _muatGambarAsli();
  }

  // [FITUR: Muat Data Gambar Asli] - Mendekode berkas gambar dari disk ke objek ui.Image untuk rendering kanvas
  Future<void> _muatGambarAsli() async {
    try {
      final berkas = File(widget.berkasGambar.jalurBerkas);
      final byteGambar = await berkas.readAsBytes();
      final kodek = await ui.instantiateImageCodec(byteGambar);
      final bingkai = await kodek.getNextFrame();

      if (mounted) {
        setState(() {
          _gambarAsli = bingkai.image;
          _sedangMemuat = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _sedangMemuat = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Gagal memuat gambar: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF111827),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1F2937),
        elevation: 0,
        title: Text(
          widget.berkasGambar.namaBerkas,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white),
          overflow: TextOverflow.ellipsis,
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.of(context).pop(),
        ),
        actions: [
          // Tombol Simpan ke Galeri
          Padding(
            padding: const EdgeInsets.only(right: 8.0),
            child: ElevatedButton.icon(
              onPressed: _sedangMenyimpan ? null : _simpanHasilEditKeGaleri,
              icon: _sedangMenyimpan
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.save_alt, size: 18),
              label: Text(_sedangMenyimpan ? 'Menyimpan...' : 'Simpan ke Galeri'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF10B981),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
          ),
        ],
      ),
      body: _sedangMemuat
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF3B82F6)))
          : Column(
              children: [
                // Area Kanvas Utama Gambar
                Expanded(
                  child: LayoutBuilder(
                    builder: (konteks, batasan) {
                      _hitungBatasTampilanGambar(batasan);

                      return Center(
                        child: SizedBox(
                          width: _batasGambarPadaLayar.width,
                          height: _batasGambarPadaLayar.height,
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              // Wadah Gambar Dasar + Lapisan Coretan yang terhubung dengan Transformasi Mirror & Putar
                              Transform(
                                alignment: Alignment.center,
                                transform: Matrix4.identity()
                                  ..scale(_cerminHorizontal ? -1.0 : 1.0, _cerminVertikal ? -1.0 : 1.0)
                                  ..rotateZ(_totalRotasiRadian),
                                child: Stack(
                                  fit: StackFit.expand,
                                  children: [
                                    Image.file(
                                      File(widget.berkasGambar.jalurBerkas),
                                      fit: BoxFit.contain,
                                    ),

                                    // Lapisan Coret-coret Anotasi Bebas
                                    if (_modeAktif == _ModeAlatEditor.coret)
                                      GestureDetector(
                                        onPanStart: (rincian) {
                                          setState(() {
                                            _coretanSedangDitarik = GarisCoretan(
                                              titikKoordinat: [rincian.localPosition],
                                              warna: _warnaKuasDipilih,
                                              ketebalan: _ketebalanKuas,
                                            );
                                          });
                                        },
                                        onPanUpdate: (rincian) {
                                          setState(() {
                                            _coretanSedangDitarik?.titikKoordinat.add(rincian.localPosition);
                                          });
                                        },
                                        onPanEnd: (_) {
                                          if (_coretanSedangDitarik != null) {
                                            setState(() {
                                              _daftarCoretan.add(_coretanSedangDitarik!);
                                              _coretanSedangDitarik = null;
                                            });
                                          }
                                        },
                                        child: CustomPaint(
                                          size: Size(_batasGambarPadaLayar.width, _batasGambarPadaLayar.height),
                                          painter: PelukisCoretan(
                                            daftarCoretan: _daftarCoretan,
                                            coretanAktif: _coretanSedangDitarik,
                                          ),
                                        ),
                                      )
                                    else
                                      CustomPaint(
                                        size: Size(_batasGambarPadaLayar.width, _batasGambarPadaLayar.height),
                                        painter: PelukisCoretan(
                                          daftarCoretan: _daftarCoretan,
                                        ),
                                      ),
                                  ],
                                ),
                              ),

                              // Lapisan Kotak Potong Interaktif
                              if (_modeAktif == _ModeAlatEditor.potong)
                                Positioned.fill(
                                  child: KomponenPemotongGambar(
                                    batasGambar: Rect.fromLTWH(
                                      0,
                                      0,
                                      _batasGambarPadaLayar.width,
                                      _batasGambarPadaLayar.height,
                                    ),
                                    rasio: _rasioPotongDipilih,
                                    padaAreaPotongBerubah: (areaBaru) {
                                      _areaPotongAktif = areaBaru;
                                    },
                                  ),
                                ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),

                // Panel Pengaturan Alat di Bagian Bawah
                _bangunPanelAlatBawah(),
              ],
            ),
    );
  }

  // [FITUR: Hitung Proporsi Tampilan Gambar] - Memetakan ukuran asli gambar ke ruang layar perangkat
  void _hitungBatasTampilanGambar(BoxConstraints batasan) {
    if (_gambarAsli == null) return;

    final lebarAsli = _gambarAsli!.width.toDouble();
    final tinggiAsli = _gambarAsli!.height.toDouble();

    final rasioGambar = lebarAsli / tinggiAsli;
    final rasioLayar = batasan.maxWidth / batasan.maxHeight;

    double lebarRender;
    double tinggiRender;

    if (rasioGambar > rasioLayar) {
      lebarRender = batasan.maxWidth;
      tinggiRender = lebarRender / rasioGambar;
    } else {
      tinggiRender = batasan.maxHeight;
      lebarRender = tinggiRender * rasioGambar;
    }

    _batasGambarPadaLayar = Rect.fromLTWH(0, 0, lebarRender, tinggiRender);
    if (_areaPotongAktif == Rect.zero) {
      _areaPotongAktif = _batasGambarPadaLayar;
    }
  }

  // [FITUR: Panel Navigasi dan Kontrol Alat Bawah] - Memilih antara mode Coret, Potong, dan Transformasi Putar/Cermin
  Widget _bangunPanelAlatBawah() {
    return Container(
      color: const Color(0xFF1F2937),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Kontrol Spesifik Sesuai Mode Aktif
            if (_modeAktif == _ModeAlatEditor.coret)
              _bangunKontrolCoretan()
            else if (_modeAktif == _ModeAlatEditor.potong)
              _bangunKontrolPotong()
            else
              _bangunKontrolTransformasi(),

            const SizedBox(height: 12),
            const Divider(color: Color(0xFF374151), height: 1),
            const SizedBox(height: 8),

            // Tab Pemilih Mode Alat Utama
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _bangunTombolMode(
                  ikon: Icons.brush,
                  label: 'Coret',
                  terpilih: _modeAktif == _ModeAlatEditor.coret,
                  padaKetuk: () => setState(() => _modeAktif = _ModeAlatEditor.coret),
                ),
                _bangunTombolMode(
                  ikon: Icons.crop,
                  label: 'Potong',
                  terpilih: _modeAktif == _ModeAlatEditor.potong,
                  padaKetuk: () => setState(() => _modeAktif = _ModeAlatEditor.potong),
                ),
                _bangunTombolMode(
                  ikon: Icons.rotate_right,
                  label: 'Putar & Mirror',
                  terpilih: _modeAktif == _ModeAlatEditor.transformasi,
                  padaKetuk: () => setState(() => _modeAktif = _ModeAlatEditor.transformasi),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // [FITUR: Kontrol Transformasi Mirror & Rotasi Derajat] - Rotasi 90 derajat, slider putar derajat halus, dan cermin horizontal/vertikal
  Widget _bangunKontrolTransformasi() {
    return Column(
      children: [
        // Baris Tombol Aksi Cepat
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            // Tombol Putar 90 Derajat
            ElevatedButton.icon(
              onPressed: () {
                setState(() {
                  _rotasiKelipatan90 = (_rotasiKelipatan90 + 1) % 4;
                });
              },
              icon: const Icon(Icons.rotate_90_degrees_cw, size: 16),
              label: Text('+90° (${_rotasiKelipatan90 * 90}°)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF374151),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              ),
            ),

            // Tombol Mirror Horizontal
            FilterChip(
              label: const Text('Cermin H'),
              selected: _cerminHorizontal,
              avatar: const Icon(Icons.flip, size: 16, color: Colors.white),
              selectedColor: const Color(0xFF3B82F6),
              backgroundColor: const Color(0xFF374151),
              labelStyle: const TextStyle(color: Colors.white, fontSize: 11),
              onSelected: (val) => setState(() => _cerminHorizontal = val),
            ),

            // Tombol Mirror Vertikal
            FilterChip(
              label: const Text('Cermin V'),
              selected: _cerminVertikal,
              avatar: const Icon(Icons.swap_vert, size: 16, color: Colors.white),
              selectedColor: const Color(0xFF3B82F6),
              backgroundColor: const Color(0xFF374151),
              labelStyle: const TextStyle(color: Colors.white, fontSize: 11),
              onSelected: (val) => setState(() => _cerminVertikal = val),
            ),

            // Tombol Reset Transformasi
            IconButton(
              icon: const Icon(Icons.restart_alt, color: Colors.amberAccent),
              tooltip: 'Reset Putar & Cermin',
              onPressed: () {
                setState(() {
                  _rotasiKelipatan90 = 0;
                  _sudutRotasiDerajat = 0.0;
                  _cerminHorizontal = false;
                  _cerminVertikal = false;
                });
              },
            ),
          ],
        ),

        const SizedBox(height: 8),

        // Slider Rotasi Derajat Bebas (-45° s/d +45°)
        Row(
          children: [
            const Icon(Icons.screen_rotation_outlined, color: Colors.grey, size: 20),
            Expanded(
              child: Slider(
                value: _sudutRotasiDerajat,
                min: -45.0,
                max: 45.0,
                divisions: 90,
                activeColor: const Color(0xFF38BDF8),
                inactiveColor: Colors.grey.shade700,
                onChanged: (val) => setState(() => _sudutRotasiDerajat = val),
              ),
            ),
            SizedBox(
              width: 50,
              child: Text(
                '${_sudutRotasiDerajat.toStringAsFixed(1)}°',
                style: const TextStyle(color: Colors.white70, fontSize: 12),
              ),
            ),
          ],
        ),
      ],
    );
  }

  // [FITUR: Kontrol Coretan] - Palet warna (Merah, Kuning, Hijau, Hitam, Putih), penggeser kuas, dan tombol Undo
  Widget _bangunKontrolCoretan() {
    return Column(
      children: [
        // Pilihan Warna dan Tombol Undo
        Row(
          children: [
            // Palet 5 Warna Utama
            Expanded(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: _paletWarnaTersedia.map((warna) {
                  final apakahDipilih = _warnaKuasDipilih == warna;
                  return GestureDetector(
                    onTap: () => setState(() => _warnaKuasDipilih = warna),
                    child: Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: warna,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: apakahDipilih ? const Color(0xFF3B82F6) : Colors.grey.shade600,
                          width: apakahDipilih ? 3 : 1.5,
                        ),
                        boxShadow: apakahDipilih
                            ? [const BoxShadow(color: Color(0xFF3B82F6), blurRadius: 6, spreadRadius: 1)]
                            : null,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),

            // Tombol Undo (Batalkan Coretan Terakhir)
            IconButton(
              icon: const Icon(Icons.undo, color: Colors.white),
              tooltip: 'Batalkan Coretan',
              onPressed: _daftarCoretan.isNotEmpty
                  ? () {
                      setState(() {
                        _daftarCoretan.removeLast();
                      });
                    }
                  : null,
            ),

            // Tombol Hapus Semua Coretan
            IconButton(
              icon: const Icon(Icons.delete_outline, color: Colors.redAccent),
              tooltip: 'Hapus Semua Coretan',
              onPressed: _daftarCoretan.isNotEmpty
                  ? () {
                      setState(() {
                        _daftarCoretan.clear();
                      });
                    }
                  : null,
            ),
          ],
        ),

        const SizedBox(height: 8),

        // Slider Ketebalan Kuas
        Row(
          children: [
            const Icon(Icons.line_weight, color: Colors.grey, size: 20),
            Expanded(
              child: Slider(
                value: _ketebalanKuas,
                min: 2.0,
                max: 24.0,
                activeColor: _warnaKuasDipilih,
                inactiveColor: Colors.grey.shade700,
                onChanged: (nilaiBaru) => setState(() => _ketebalanKuas = nilaiBaru),
              ),
            ),
            Text(
              '${_ketebalanKuas.toInt()}px',
              style: const TextStyle(color: Colors.white70, fontSize: 12),
            ),
          ],
        ),
      ],
    );
  }

  // [FITUR: Kontrol Pemotongan] - Pilihan rasio potong (Bebas, 1:1, 4:3, 16:9, 9:16)
  Widget _bangunKontrolPotong() {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          _bangunTombolRasio('Bebas', RasioPotong.bebas),
          _bangunTombolRasio('1:1', RasioPotong.persegi1Banding1),
          _bangunTombolRasio('4:3', RasioPotong.standar4Banding3),
          _bangunTombolRasio('16:9', RasioPotong.lebar16Banding9),
          _bangunTombolRasio('9:16', RasioPotong.vertikal9Banding16),
        ],
      ),
    );
  }

  // [FITUR: Tombol Pilihan Rasio] - Komponen tombol chip rasio
  Widget _bangunTombolRasio(String label, RasioPotong rasio) {
    final terpilih = _rasioPotongDipilih == rasio;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0),
      child: ChoiceChip(
        label: Text(label),
        selected: terpilih,
        onSelected: (val) {
          if (val) setState(() => _rasioPotongDipilih = rasio);
        },
        selectedColor: const Color(0xFF3B82F6),
        backgroundColor: const Color(0xFF374151),
        labelStyle: TextStyle(
          color: terpilih ? Colors.white : Colors.grey.shade300,
          fontSize: 12,
        ),
      ),
    );
  }

  // [FITUR: Tombol Pemilih Mode Utama] - Beralih antara Coret dan Potong
  Widget _bangunTombolMode({
    required IconData ikon,
    required String label,
    required bool terpilih,
    required VoidCallback padaKetuk,
  }) {
    return InkWell(
      onTap: padaKetuk,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        decoration: BoxDecoration(
          color: terpilih ? const Color(0xFF3B82F6).withValues(alpha: 0.2) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: terpilih ? const Color(0xFF3B82F6) : Colors.transparent,
          ),
        ),
        child: Row(
          children: [
            Icon(ikon, color: terpilih ? const Color(0xFF60A5FA) : Colors.grey, size: 20),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                color: terpilih ? const Color(0xFF60A5FA) : Colors.grey,
                fontWeight: terpilih ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  // [FITUR: Simpan Hasil Edit ke Galeri] - Mengekspor gambar yang sudah di-crop, dicoret, di-mirror, dan diputar dari brankas privat ke galeri publik HP via MediaStore
  Future<void> _simpanHasilEditKeGaleri() async {
    if (_gambarAsli == null) return;

    setState(() => _sedangMenyimpan = true);

    try {
      final lebarAsli = _gambarAsli!.width.toDouble();
      final tinggiAsli = _gambarAsli!.height.toDouble();

      // 1. Hitung dimensi kanvas setelah rotasi derajat & cermin
      final totalRadian = _totalRotasiRadian;
      final cosSudut = cos(totalRadian).abs();
      final sinSudut = sin(totalRadian).abs();

      final lebarRotasi = (lebarAsli * cosSudut + tinggiAsli * sinSudut).round().clamp(1, 100000);
      final tinggiRotasi = (lebarAsli * sinSudut + tinggiAsli * cosSudut).round().clamp(1, 100000);

      // 2. Render foto asli + seluruh coretan dengan transformasi penuh (Rotasi + Cermin)
      final perekamTrans = ui.PictureRecorder();
      final kanvasTrans = Canvas(perekamTrans);

      kanvasTrans.save();
      kanvasTrans.translate(lebarRotasi / 2.0, tinggiRotasi / 2.0);
      kanvasTrans.rotate(totalRadian);
      kanvasTrans.scale(_cerminHorizontal ? -1.0 : 1.0, _cerminVertikal ? -1.0 : 1.0);
      kanvasTrans.translate(-lebarAsli / 2.0, -tinggiAsli / 2.0);

      // Gambar foto asli
      kanvasTrans.drawImage(_gambarAsli!, Offset.zero, Paint());

      // Gambar goresan coretan di atas foto
      final skalaCoretX = lebarAsli / _batasGambarPadaLayar.width;
      final skalaCoretY = tinggiAsli / _batasGambarPadaLayar.height;
      kanvasTrans.save();
      kanvasTrans.scale(skalaCoretX, skalaCoretY);

      for (final garis in _daftarCoretan) {
        if (garis.titikKoordinat.isEmpty) continue;
        final kuas = Paint()
          ..color = garis.warna
          ..strokeWidth = garis.ketebalan
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round
          ..style = PaintingStyle.stroke
          ..isAntiAlias = true;

        if (garis.titikKoordinat.length == 1) {
          kanvasTrans.drawCircle(garis.titikKoordinat.first, garis.ketebalan / 2, kuas..style = PaintingStyle.fill);
        } else {
          final jalur = Path();
          jalur.moveTo(garis.titikKoordinat.first.dx, garis.titikKoordinat.first.dy);
          for (int i = 1; i < garis.titikKoordinat.length; i++) {
            jalur.lineTo(garis.titikKoordinat[i].dx, garis.titikKoordinat[i].dy);
          }
          kanvasTrans.drawPath(jalur, kuas);
        }
      }
      kanvasTrans.restore();
      kanvasTrans.restore();

      final gambarTransformed = await perekamTrans.endRecording().toImage(lebarRotasi, tinggiRotasi);

      // 3. Jika pengguna menerapkan pemotongan (Crop), potong hasil transformasi tersebut
      ui.Image gambarAkhir = gambarTransformed;
      if (_modeAktif == _ModeAlatEditor.potong &&
          _areaPotongAktif.width > 0 &&
          _areaPotongAktif != _batasGambarPadaLayar) {
        final cropSkalaX = lebarRotasi / _batasGambarPadaLayar.width;
        final cropSkalaY = tinggiRotasi / _batasGambarPadaLayar.height;

        final rectCrop = Rect.fromLTRB(
          (_areaPotongAktif.left * cropSkalaX).clamp(0.0, lebarRotasi.toDouble()),
          (_areaPotongAktif.top * cropSkalaY).clamp(0.0, tinggiRotasi.toDouble()),
          (_areaPotongAktif.right * cropSkalaX).clamp(0.0, lebarRotasi.toDouble()),
          (_areaPotongAktif.bottom * cropSkalaY).clamp(0.0, tinggiRotasi.toDouble()),
        );

        final lebarCrop = rectCrop.width.round();
        final tinggiCrop = rectCrop.height.round();

        if (lebarCrop > 0 && tinggiCrop > 0) {
          final perekamCrop = ui.PictureRecorder();
          final kanvasCrop = Canvas(perekamCrop);
          kanvasCrop.drawImageRect(
            gambarTransformed,
            rectCrop,
            Rect.fromLTWH(0, 0, lebarCrop.toDouble(), tinggiCrop.toDouble()),
            Paint(),
          );
          gambarAkhir = await perekamCrop.endRecording().toImage(lebarCrop, tinggiCrop);
        }
      }

      final byteData = await gambarAkhir.toByteData(format: ui.ImageByteFormat.png);

      if (byteData == null) {
        throw Exception('Gagal mengonversi gambar ke format PNG');
      }

      final byteUint8 = byteData.buffer.asUint8List();

      // 4. Kirim byte gambar ke Native Kotlin untuk disimpan ke MediaStore Pictures/AutoTempelEdit
      final stempelWaktu = DateTime.now().millisecondsSinceEpoch;
      final namaBerkasSimpan = 'AutoTempel_$stempelWaktu.png';

      final uriHasil = await SaluranPenghubungNative.simpanHasilEditKeGaleri(
        byteUint8,
        namaBerkas: namaBerkasSimpan,
      );

      if (mounted) {
        setState(() => _sedangMenyimpan = false);

        if (uriHasil != null) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              backgroundColor: const Color(0xFF10B981),
              content: const Row(
                children: [
                  Icon(Icons.check_circle, color: Colors.white),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Tersimpan di Galeri (Pictures/AutoTempelEdit) & disalin ke Clipboard!',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                ],
              ),
              duration: const Duration(seconds: 4),
              action: SnackBarAction(
                label: 'Selesai',
                textColor: Colors.white,
                onPressed: () => Navigator.of(context).pop(true),
              ),
            ),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              backgroundColor: Colors.redAccent,
              content: Text('Gagal menyimpan gambar ke Galeri'),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _sedangMenyimpan = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: Colors.redAccent,
            content: Text('Terjadi kesalahan saat menyimpan: $e'),
          ),
        );
      }
    }
  }
}
