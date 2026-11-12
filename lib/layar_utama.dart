import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'layar_editor_gambar.dart';
import 'model_berkas_gambar.dart';
import 'saluran_metode.dart';

// [FITUR: Layar Utama Aplikasi] - Pusat kendali layanan pemantauan screenshot Android 16, pengaturan timer kadaluarsa, verifikasi izin sistem, dan antrean brankas 10 gambar
class LayarUtama extends StatefulWidget {
  const LayarUtama({super.key});

  @override
  State<LayarUtama> createState() => _LayarUtamaState();
}

class _LayarUtamaState extends State<LayarUtama> with WidgetsBindingObserver {
  // Status Layanan Latar Depan
  bool _apakahLayananAktif = false;
  bool _sedangMengubahLayanan = false;

  // Status Izin Sistem Android 16
  Map<String, bool> _statusIzin = {
    'aksesSemuaBerkas': false,
    'notifikasi': false,
    'overlay': false,
    'penghematBaterai': false,
  };

  // Timer Kadaluarsa
  final TextEditingController _pengontrolTeksDurasi = TextEditingController(text: '3');
  int _durasiTersimpanMenit = 3;
  int _sisaDetikHitungMundur = 180;

  // Antrean Brankas
  List<ModelBerkasGambar> _daftarAntreanBrankas = [];
  bool _sedangMemuatAntrean = true;

  // Langganan Aliran Peristiwa Native
  StreamSubscription<dynamic>? _langgananPeristiwa;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _inisialisasiDataAwal();
    _daftarkanAliranPeristiwa();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _langgananPeristiwa?.cancel();
    _pengontrolTeksDurasi.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _periksaStatusIzinSistem();
      _muatDaftarAntreanBrankas();
      _periksaStatusLayanan();
    }
  }

  // [FITUR: Inisialisasi Data Awal] - Memeriksa status service, izin, sisa waktu, dan antrean saat aplikasi dibuka
  Future<void> _inisialisasiDataAwal() async {
    await _periksaStatusIzinSistem();
    await _periksaStatusLayanan();
    await _muatDaftarAntreanBrankas();
    final sisa = await SaluranPenghubungNative.dapatkanSisaWaktuDetik();
    if (sisa > 0 && mounted) {
      setState(() => _sisaDetikHitungMundur = sisa);
    }
  }

  // [FITUR: Langganan Peristiwa Native Realtime] - Menangkap sinyal detik berkurang, antrean berubah, dan timer habis dari Kotlin
  void _daftarkanAliranPeristiwa() {
    _langgananPeristiwa = SaluranPenghubungNative.dengarkanPeristiwa().listen(
      (dataPeristiwa) {
        if (!mounted || dataPeristiwa is! Map<dynamic, dynamic>) return;

        final jenis = dataPeristiwa['jenis']?.toString();
        switch (jenis) {
          case 'detik_berkurang':
            final sisa = (dataPeristiwa['sisaDetik'] as num?)?.toInt() ?? 0;
            setState(() => _sisaDetikHitungMundur = sisa);
            break;

          case 'antrean_diperbarui':
            final raw = dataPeristiwa['antrean'] as List<dynamic>? ?? [];
            final antreanBaru = raw
                .whereType<Map<dynamic, dynamic>>()
                .map((peta) => ModelBerkasGambar.dariPeta(peta))
                .toList();
            setState(() {
              _daftarAntreanBrankas = antreanBaru;
              _sedangMemuatAntrean = false;
            });
            break;

          case 'timer_kadaluarsa':
            setState(() {
              _daftarAntreanBrankas.clear();
              _sisaDetikHitungMundur = _durasiTersimpanMenit * 60;
            });
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                backgroundColor: Color(0xFFE11D48),
                content: Text('Waktu timer habis: Brankas dibersihkan & Clipboard dikosongkan'),
              ),
            );
            break;

          case 'status_layanan':
            final aktif = dataPeristiwa['aktif'] == true;
            setState(() => _apakahLayananAktif = aktif);
            break;
        }
      },
      onError: (e) {
        debugPrint('Kesalahan aliran peristiwa: $e');
      },
    );
  }

  // [FITUR: Periksa Izin Sistem] - Mendapatkan status izin All Files Access, Notifikasi, Overlay, dan Baterai
  Future<void> _periksaStatusIzinSistem() async {
    final status = await SaluranPenghubungNative.periksaStatusIzin();
    if (mounted) {
      setState(() => _statusIzin = status);
    }
  }

  // [FITUR: Periksa Status Layanan] - Mendeteksi apakah LayananTangkapanLayar sedang aktif
  Future<void> _periksaStatusLayanan() async {
    final aktif = await SaluranPenghubungNative.apakahLayananBerjalan();
    if (mounted) {
      setState(() => _apakahLayananAktif = aktif);
    }
  }

  // [FITUR: Muat Daftar Antrean Brankas] - Membaca berkas brankas privat aplikasi
  Future<void> _muatDaftarAntreanBrankas() async {
    final daftar = await SaluranPenghubungNative.dapatkanDaftarAntrean();
    if (mounted) {
      setState(() {
        _daftarAntreanBrankas = daftar;
        _sedangMemuatAntrean = false;
      });
    }
  }

  // [FITUR: Sakelar Layanan Pemantauan] - Mengaktifkan atau mematikan layanan foreground
  Future<void> _ubahStatusLayanan(bool nilaiBaru) async {
    setState(() => _sedangMengubahLayanan = true);

    if (nilaiBaru) {
      // Pastikan izin telah diberikan
      final semuaIzinTerpenuhi = _statusIzin['aksesSemuaBerkas'] == true &&
          _statusIzin['overlay'] == true;

      if (!semuaIzinTerpenuhi) {
        _tampilkanDialogPeringatanIzin();
        setState(() => _sedangMengubahLayanan = false);
        return;
      }

      final sukses = await SaluranPenghubungNative.mulaiLayanan(_durasiTersimpanMenit * 60);
      if (mounted) {
        setState(() {
          _apakahLayananAktif = sukses;
          _sedangMengubahLayanan = false;
        });
      }
    } else {
      final sukses = await SaluranPenghubungNative.hentikanLayanan();
      if (mounted) {
        setState(() {
          _apakahLayananAktif = !sukses;
          _sedangMengubahLayanan = false;
        });
      }
    }
  }

  // [FITUR: Simpan Durasi Timer Baru] - Mengupdate masa aktif antrean dari input TextField
  Future<void> _simpanDurasiTimer() async {
    final angka = int.tryParse(_pengontrolTeksDurasi.text.trim());
    if (angka == null || angka <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          backgroundColor: Colors.redAccent,
          content: Text('Masukkan angka durasi menit yang valid (minimal 1 menit)!'),
        ),
      );
      return;
    }

    final detik = angka * 60;
    await SaluranPenghubungNative.aturDurasiTimer(detik);
    if (mounted) {
      setState(() {
        _durasiTersimpanMenit = angka;
        _sisaDetikHitungMundur = detik;
      });
      FocusScope.of(context).unfocus();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: const Color(0xFF10B981),
          content: Text('Durasi timer berhasil diatur: $angka Menit ($detik Detik)'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        title: const Row(
          children: [
            Icon(Icons.content_cut, color: Color(0xFF00A651), size: 22),
            SizedBox(width: 8),
            Text(
              'Take & paste',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70),
            tooltip: 'Segarkan Antrean & Izin',
            onPressed: () {
              _periksaStatusIzinSistem();
              _muatDaftarAntreanBrankas();
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await _periksaStatusIzinSistem();
          await _muatDaftarAntreanBrankas();
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // 1. Sakelar Utama Layanan Android 16
            _bangunKartuSakelarLayanan(),

            const SizedBox(height: 16),

            // 2. Pengaturan Timer Kadaluarsa & Hitung Mundur
            _bangunKartuPengaturanTimer(),

            const SizedBox(height: 16),

            // 3. Panel Status Izin Android 16
            _bangunKartuPanelIzin(),

            const SizedBox(height: 20),

            // 4. Daftar Antrean Brankas (Maksimal 10 Gambar)
            _bangunBagianDaftarAntrean(),
          ],
        ),
      ),
    );
  }

  // [FITUR: Kartu Sakelar Layanan Utama] - Menampilkan toggle ON/OFF dan indikator status aktif
  Widget _bangunKartuSakelarLayanan() {
    final warnaStatus = _apakahLayananAktif ? const Color(0xFF10B981) : Colors.grey;

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: _apakahLayananAktif ? const Color(0xFF10B981).withValues(alpha: 0.5) : const Color(0xFF334155),
          width: 1.5,
        ),
      ),
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: warnaStatus.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(
              _apakahLayananAktif ? Icons.security : Icons.power_settings_new,
              color: warnaStatus,
              size: 26,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Layanan Pemantauan',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                const SizedBox(height: 2),
                Text(
                  _apakahLayananAktif
                      ? 'Aktif (Foreground Service API 36)'
                      : 'Nonaktif (Ketuk untuk menyalakan)',
                  style: TextStyle(
                    fontSize: 12,
                    color: _apakahLayananAktif ? const Color(0xFF4ADE80) : Colors.grey.shade400,
                  ),
                ),
              ],
            ),
          ),
          if (_sedangMengubahLayanan)
            const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF38BDF8)),
            )
          else
            Switch(
              value: _apakahLayananAktif,
              onChanged: _ubahStatusLayanan,
              activeColor: const Color(0xFF10B981),
            ),
        ],
      ),
    );
  }

  // [FITUR: Kartu Pengaturan Timer Kadaluarsa] - TextField durasi menit, tombol simpan, dan visual hitung mundur
  Widget _bangunKartuPengaturanTimer() {
    final menit = _sisaDetikHitungMundur ~/ 60;
    final detik = _sisaDetikHitungMundur % 60;
    final teksHitungMundur = '${menit.toString().padLeft(2, '0')}:${detik.toString().padLeft(2, '0')}';
    final totalDetik = _durasiTersimpanMenit * 60;
    final rasioProgress = totalDetik > 0 ? (_sisaDetikHitungMundur / totalDetik).clamp(0.0, 1.0) : 0.0;

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.timer_outlined, color: Color(0xFFF59E0B), size: 20),
              SizedBox(width: 8),
              Text(
                'Timer Kadaluarsa Otomatis',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            'Setelah waktu habis, seluruh screenshot brankas dihapus dan clipboard dikosongkan.',
            style: TextStyle(fontSize: 12, color: Colors.white60),
          ),
          const SizedBox(height: 14),

          // Tampilan Real-time Hitung Mundur
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF0F172A),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: const Color(0xFF334155)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Sisa Waktu Aktif',
                        style: TextStyle(fontSize: 11, color: Colors.grey),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        teksHitungMundur,
                        style: TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: _sisaDetikHitungMundur < 30 ? const Color(0xFFF43F5E) : const Color(0xFF38BDF8),
                          letterSpacing: 2,
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(
                  width: 44,
                  height: 44,
                  child: CircularProgressIndicator(
                    value: rasioProgress,
                    backgroundColor: const Color(0xFF334155),
                    color: _sisaDetikHitungMundur < 30 ? const Color(0xFFF43F5E) : const Color(0xFF38BDF8),
                    strokeWidth: 4,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 14),

          // Input Angka Durasi & Tombol Simpan
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _pengontrolTeksDurasi,
                  keyboardType: TextInputType.number,
                  style: const TextStyle(color: Colors.white, fontSize: 14),
                  decoration: InputDecoration(
                    labelText: 'Durasi Aktif (Menit)',
                    labelStyle: const TextStyle(color: Colors.grey, fontSize: 12),
                    hintText: 'Contoh: 1, 3, 5, 10',
                    hintStyle: const TextStyle(color: Colors.white30, fontSize: 12),
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    filled: true,
                    fillColor: const Color(0xFF0F172A),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Color(0xFF334155)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Color(0xFF334155)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Color(0xFF38BDF8)),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              ElevatedButton.icon(
                onPressed: _simpanDurasiTimer,
                icon: const Icon(Icons.check, size: 16),
                label: const Text('Simpan'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF3B82F6),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // [FITUR: Kartu Panel Status Izin Android 16] - Menampilkan status izin All Files, Notifikasi, Overlay, dan Baterai
  Widget _bangunKartuPanelIzin() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(Icons.admin_panel_settings_outlined, color: Color(0xFF818CF8), size: 20),
              SizedBox(width: 8),
              Text(
                'Status Izin Sistem Android 16',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 1. Izin Akses Semua File
          _bangunBarisIzin(
            judul: 'Akses Semua Berkas (MANAGE_EXTERNAL_STORAGE)',
            keterangan: 'Wajib untuk hapus senyap screenshot dari galeri publik',
            diberikan: _statusIzin['aksesSemuaBerkas'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinAksesSemuaBerkas,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          // 2. Izin Notifikasi
          _bangunBarisIzin(
            judul: 'Notifikasi Foreground (POST_NOTIFICATIONS)',
            keterangan: 'Menjaga layanan pemantau tetap stabil di latar belakang',
            diberikan: _statusIzin['notifikasi'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinNotifikasi,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          // 3. Izin Overlay
          _bangunBarisIzin(
            judul: 'Tampilkan di Atas Aplikasi (Overlay Window)',
            keterangan: 'Diperlukan untuk gelembung melayang & bypass clipboard fokus Android 16',
            diberikan: _statusIzin['overlay'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinOverlay,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          // 4. Pengecualian Penghemat Baterai
          _bangunBarisIzin(
            judul: 'Abaikan Penghemat Baterai (Ignore Battery)',
            keterangan: 'Mencegah OS menghentikan timer secara paksa saat layar mati',
            diberikan: _statusIzin['penghematBaterai'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaAbaikanPenghematBaterai,
          ),
        ],
      ),
    );
  }

  // [FITUR: Baris Indikator Izin] - Menampilkan status tombol pintas pengaturan izin
  Widget _bangunBarisIzin({
    required String judul,
    required String keterangan,
    required bool diberikan,
    required VoidCallback padaBukaPengaturan,
  }) {
    return Row(
      children: [
        Icon(
          diberikan ? Icons.check_circle : Icons.error_outline,
          color: diberikan ? const Color(0xFF10B981) : const Color(0xFFF43F5E),
          size: 20,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                judul,
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white),
              ),
              const SizedBox(height: 2),
              Text(
                keterangan,
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        TextButton(
          onPressed: padaBukaPengaturan,
          style: TextButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            minimumSize: Size.zero,
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            backgroundColor: diberikan ? const Color(0xFF0F172A) : const Color(0xFF3B82F6).withValues(alpha: 0.2),
          ),
          child: Text(
            diberikan ? 'Aktif' : 'Izinkan',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: diberikan ? const Color(0xFF4ADE80) : const Color(0xFF60A5FA),
            ),
          ),
        ),
      ],
    );
  }

  // [FITUR: Bagian Daftar Antrean Brankas] - Menampilkan header aksi (Kirim Semua, Kosongkan) dan grid thumbnail
  Widget _bangunBagianDaftarAntrean() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                const Icon(Icons.photo_library_outlined, color: Color(0xFF38BDF8), size: 20),
                const SizedBox(width: 8),
                Text(
                  'Antrean Brankas (${_daftarAntreanBrankas.length}/10)',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                ),
              ],
            ),
            Row(
              children: [
                // Tombol Kirim Semua (Multi-Share)
                if (_daftarAntreanBrankas.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(right: 6.0),
                    child: ElevatedButton.icon(
                      onPressed: SaluranPenghubungNative.bagikanSemuaAntrean,
                      icon: const Icon(Icons.share, size: 14),
                      label: const Text('Kirim Semua', style: TextStyle(fontSize: 11)),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF10B981),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                  ),

                // Tombol Kosongkan Brankas
                if (_daftarAntreanBrankas.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.delete_sweep, color: Color(0xFFF43F5E), size: 20),
                    tooltip: 'Kosongkan Seluruh Antrean',
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    onPressed: _konfirmasiKosongkanBrankas,
                  ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 10),

        if (_sedangMemuatAntrean)
          const Center(
            child: Padding(
              padding: EdgeInsets.all(32.0),
              child: CircularProgressIndicator(color: Color(0xFF38BDF8)),
            ),
          )
        else if (_daftarAntreanBrankas.isEmpty)
          Container(
            padding: const EdgeInsets.all(32),
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFF334155)),
            ),
            child: const Center(
              child: Column(
                children: [
                  Icon(Icons.inventory_2_outlined, color: Colors.grey, size: 48),
                  SizedBox(height: 12),
                  Text(
                    'Brankas Masih Kosong',
                    style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 15),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Ambil tangkapan layar (screenshot) di HP Anda untuk melihatnya masuk ke sini secara otomatis tanpa tersimpan di Galeri.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ],
              ),
            ),
          )
        else
          // Grid Thumbnail Antrean 10 Gambar
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              childAspectRatio: 0.72,
            ),
            itemCount: _daftarAntreanBrankas.length,
            itemBuilder: (konteks, indeks) {
              final butir = _daftarAntreanBrankas[indeks];
              return _bangunKartuThumbnail(butir);
            },
          ),
      ],
    );
  }

  // [FITUR: Kartu Thumbnail Screenshot] - Kartu preview dengan tombol Edit (Crop & Coret), Salin ke Clipboard, dan Hapus
  Widget _bangunKartuThumbnail(ModelBerkasGambar butir) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Gambar Thumbnail
          Expanded(
            child: GestureDetector(
              onTap: () => _bukaLayarEditor(butir),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Image.file(
                    File(butir.jalurBerkas),
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Center(
                      child: Icon(Icons.broken_image, color: Colors.grey),
                    ),
                  ),
                  Positioned(
                    top: 6,
                    right: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.65),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        butir.dapatkanUkuranTerbaca(),
                        style: const TextStyle(fontSize: 10, color: Colors.white),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Tombol Aksi Gambar
          Container(
            color: const Color(0xFF0F172A),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Tombol Edit (Buka Editor Crop & Coret)
                InkWell(
                  onTap: () => _bukaLayarEditor(butir),
                  borderRadius: BorderRadius.circular(6),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFF3B82F6).withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.edit, size: 12, color: Color(0xFF60A5FA)),
                        SizedBox(width: 4),
                        Text(
                          'Edit',
                          style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF60A5FA)),
                        ),
                      ],
                    ),
                  ),
                ),

                // Tombol Salin ke Papan Klip
                IconButton(
                  icon: const Icon(Icons.copy, size: 16, color: Color(0xFF38BDF8)),
                  tooltip: 'Salin ke Clipboard',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  onPressed: () async {
                    final sukses = await SaluranPenghubungNative.salinGambarKePapanKlip(butir.jalurBerkas);
                    if (sukses && mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          backgroundColor: Color(0xFF10B981),
                          content: Text('Gambar berhasil disalin ke clipboard!'),
                          duration: Duration(seconds: 2),
                        ),
                      );
                    }
                  },
                ),

                // Tombol Hapus Satu Gambar
                IconButton(
                  icon: const Icon(Icons.delete_outline, size: 16, color: Color(0xFFF43F5E)),
                  tooltip: 'Hapus dari Brankas',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  onPressed: () async {
                    await SaluranPenghubungNative.hapusGambarDariAntrean(butir.jalurBerkas);
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // [FITUR: Navigasi ke Editor Gambar] - Membuka halaman layar_editor_gambar.dart
  Future<void> _bukaLayarEditor(ModelBerkasGambar butir) async {
    final hasilSimpan = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => LayarEditorGambar(berkasGambar: butir),
      ),
    );

    if (hasilSimpan == true) {
      _muatDaftarAntreanBrankas();
    }
  }

  // [FITUR: Konfirmasi Pembersihan Brankas] - Dialog keamanan sebelum mengosongkan seluruh antrean
  void _konfirmasiKosongkanBrankas() {
    showDialog(
      context: context,
      builder: (konteks) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Kosongkan Seluruh Brankas?', style: TextStyle(color: Colors.white)),
        content: const Text(
          'Seluruh tangkapan layar aktif di brankas akan dihapus dan clipboard dikosongkan. Aksi ini tidak dapat dibatalkan.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(konteks).pop(),
            child: const Text('Batal', style: TextStyle(color: Colors.grey)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFF43F5E)),
            onPressed: () {
              Navigator.of(konteks).pop();
              SaluranPenghubungNative.kosongkanSemuaAntrean();
            },
            child: const Text('Kosongkan', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  // [FITUR: Dialog Peringatan Izin] - Memberitahu pengguna jika izin wajib belum diberikan
  void _tampilkanDialogPeringatanIzin() {
    showDialog(
      context: context,
      builder: (konteks) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Izin Wajib Diperlukan', style: TextStyle(color: Colors.white)),
        content: const Text(
          'Untuk menjalankan pemantauan tangkapan layar dan bypass clipboard di Android 16, Anda wajib memberikan:\n\n1. Izin Akses Semua Berkas\n2. Izin Tampilkan di Atas Aplikasi (Overlay)\n\nSilakan tekan tombol "Izinkan" pada panel status izin.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF3B82F6)),
            onPressed: () => Navigator.of(konteks).pop(),
            child: const Text('Mengerti', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }
}
