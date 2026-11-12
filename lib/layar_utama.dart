import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'layar_editor_gambar.dart';
import 'layanan_pembaruan_github.dart';
import 'model_berkas_gambar.dart';
import 'saluran_metode.dart';

// [FITUR: Layar Utama Aplikasi] - Pusat kendali Take & paste: pemantauan screenshot, penyimpanan tak terbatas retensi 30 hari, cek pembaruan otomatis GitHub, dan kelola brankas
class LayarUtama extends StatefulWidget {
  const LayarUtama({super.key});

  @override
  State<LayarUtama> createState() => _LayarUtamaState();
}

class _LayarUtamaState extends State<LayarUtama> with WidgetsBindingObserver {
  // Status Layanan Pemantauan
  bool _apakahLayananAktif = false;
  bool _sedangMengubahLayanan = false;

  // Status Izin Sistem Android 16
  Map<String, bool> _statusIzin = {
    'aksesSemuaBerkas': false,
    'notifikasi': false,
    'overlay': false,
    'penghematBaterai': false,
  };

  // Antrean Brankas (Penyimpanan Tak Terbatas)
  List<ModelBerkasGambar> _daftarAntreanBrankas = [];
  bool _sedangMemuatAntrean = true;

  // [FITUR: Status Pemilihan Banyak Gambar]
  bool _modePilihBeberapa = false;
  final Set<String> _kumpulanJalurTerpilih = {};

  // Status Pembaruan GitHub
  bool _sedangMemeriksaUpdate = false;
  InformasiPembaruanGitHub? _infoPembaruan;

  // Langganan Aliran Peristiwa Native
  StreamSubscription<dynamic>? _langgananPeristiwa;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _inisialisasiDataAwal();
    _daftarkanAliranPeristiwa();
    _periksaPembaruanGitHub(diamDiam: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _langgananPeristiwa?.cancel();
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

  // [FITUR: Inisialisasi Data Awal] - Memeriksa status service, izin, dan seluruh berkas brankas saat aplikasi dibuka
  Future<void> _inisialisasiDataAwal() async {
    await _periksaStatusIzinSistem();
    await _periksaStatusLayanan();
    await _muatDaftarAntreanBrankas();
  }

  // [FITUR: Langganan Peristiwa Native Realtime] - Menangkap sinyal saat antrean berubah dan status layanan dari Kotlin
  void _daftarkanAliranPeristiwa() {
    _langgananPeristiwa = SaluranPenghubungNative.dengarkanPeristiwa().listen(
      (dataPeristiwa) {
        if (!mounted || dataPeristiwa is! Map<dynamic, dynamic>) return;

        final jenis = dataPeristiwa['jenis']?.toString();
        switch (jenis) {
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

          case 'status_layanan':
            final aktif = dataPeristiwa['aktif'] == true;
            setState(() => _apakahLayananAktif = aktif);
            break;

          case 'buka_editor':
            final jalur = dataPeristiwa['jalurBerkas']?.toString();
            if (jalur != null && File(jalur).existsSync()) {
              final berkasFisik = File(jalur);
              final butir = ModelBerkasGambar(
                idBerkas: berkasFisik.uri.pathSegments.last,
                namaBerkas: berkasFisik.uri.pathSegments.last,
                jalurBerkas: jalur,
                waktuDibuatMs: berkasFisik.lastModifiedSync().millisecondsSinceEpoch,
                ukuranBerkasByte: berkasFisik.lengthSync(),
              );
              _bukaLayarEditor(butir);
            }
            break;
        }
      },
      onError: (e) {
        debugPrint('Kesalahan aliran peristiwa: $e');
      },
    );
  }

  // [FITUR: Pemeriksaan Pembaruan GitHub] - Mendeteksi apakah ada push/commit terbaru di repositori GitHub
  Future<void> _periksaPembaruanGitHub({bool diamDiam = false}) async {
    if (_sedangMemeriksaUpdate) return;
    setState(() => _sedangMemeriksaUpdate = true);

    final info = await LayananPembaruanGitHub.periksaPembaruanDiGitHub();

    if (mounted) {
      setState(() {
        _infoPembaruan = info;
        _sedangMemeriksaUpdate = false;
      });

      if (info != null && info.apakahAdaPembaruan) {
        _tampilkanDialogPembaruan(info);
      } else if (!diamDiam) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: Color(0xFF10B981),
            content: Text('Aplikasi sudah menggunakan versi terbaru dari GitHub!'),
          ),
        );
      }
    }
  }

  // [FITUR: Dialog Pembaruan GitHub] - Pop-up interaktif saat ada rilis commit baru
  void _tampilkanDialogPembaruan(InformasiPembaruanGitHub info) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (konteks) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Row(
          children: [
            Icon(Icons.system_update_alt, color: Color(0xFF38BDF8), size: 24),
            SizedBox(width: 10),
            Text('Pembaruan GitHub Tersedia!', style: TextStyle(color: Colors.white, fontSize: 16)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Terdapat pembaruan kode terbaru yang telah dipush ke repositori GitHub:',
              style: TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFF0F172A),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFF334155)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.commit, color: Color(0xFF10B981), size: 16),
                      const SizedBox(width: 6),
                      Text(
                        'Komit: ${info.shaKomit}',
                        style: const TextStyle(color: Color(0xFF4ADE80), fontWeight: FontWeight.bold, fontSize: 12),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    info.pesanKomit,
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(konteks).pop(),
            child: const Text('Nanti', style: TextStyle(color: Colors.grey)),
          ),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF00A651)),
            icon: const Icon(Icons.open_in_browser, size: 16, color: Colors.white),
            label: const Text('Buka GitHub', style: TextStyle(color: Colors.white)),
            onPressed: () {
              Navigator.of(konteks).pop();
              SaluranPenghubungNative.bukaTautanWeb(info.tautanWeb);
            },
          ),
        ],
      ),
    );
  }

  // [FITUR: Periksa Izin Sistem] - Mendapatkan status izin All Files, Notifikasi, Overlay, dan Baterai
  Future<void> _periksaStatusIzinSistem() async {
    final status = await SaluranPenghubungNative.periksaStatusIzin();
    if (mounted) {
      setState(() => _statusIzin = status);
    }
  }

  // [FITUR: Periksa Status Layanan] - Mendeteksi apakah service berjalan
  Future<void> _periksaStatusLayanan() async {
    final aktif = await SaluranPenghubungNative.apakahLayananBerjalan();
    if (mounted) {
      setState(() => _apakahLayananAktif = aktif);
    }
  }

  // [FITUR: Muat Seluruh Berkas Brankas] - Membaca seluruh screenshot brankas privat tanpa limit
  Future<void> _muatDaftarAntreanBrankas() async {
    final daftar = await SaluranPenghubungNative.dapatkanDaftarAntrean();
    if (mounted) {
      setState(() {
        _daftarAntreanBrankas = daftar;
        _sedangMemuatAntrean = false;
      });
    }
  }

  // [FITUR: Sakelar Layanan Pemantauan] - Mengaktifkan atau mematikan layanan latar belakang
  Future<void> _ubahStatusLayanan(bool nilaiBaru) async {
    setState(() => _sedangMengubahLayanan = true);

    if (nilaiBaru) {
      final semuaIzinTerpenuhi = _statusIzin['aksesSemuaBerkas'] == true &&
          _statusIzin['overlay'] == true;

      if (!semuaIzinTerpenuhi) {
        _tampilkanDialogPeringatanIzin();
        setState(() => _sedangMengubahLayanan = false);
        return;
      }

      final sukses = await SaluranPenghubungNative.mulaiLayanan();
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
          // Tombol Cek Pembaruan GitHub
          IconButton(
            icon: _sedangMemeriksaUpdate
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF38BDF8)),
                  )
                : const Icon(Icons.cloud_sync_outlined, color: Color(0xFF38BDF8)),
            tooltip: 'Cek Pembaruan GitHub',
            onPressed: () => _periksaPembaruanGitHub(diamDiam: false),
          ),
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70),
            tooltip: 'Segarkan Berkas & Izin',
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
          await _periksaPembaruanGitHub(diamDiam: true);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Banner Notifikasi jika Ada Update GitHub
            if (_infoPembaruan != null && _infoPembaruan!.apakahAdaPembaruan)
              _bangunBannerPembaruanGitHub(),

            // 1. Sakelar Utama Layanan Android 16
            _bangunKartuSakelarLayanan(),

            const SizedBox(height: 16),

            // 2. Info Brankas & Retensi 30 Hari
            _bangunKartuRetensi30Hari(),

            const SizedBox(height: 16),

            // 3. Panel Status Izin Android 16
            _bangunKartuPanelIzin(),

            const SizedBox(height: 20),

            // 4. Daftar Seluruh Screenshot Brankas (Penyimpanan Tak Terbatas)
            _bangunBagianDaftarAntrean(),
          ],
        ),
      ),
    );
  }

  // [FITUR: Banner Pembaruan GitHub] - Tampil di atas layar saat ada push baru di GitHub
  Widget _bangunBannerPembaruanGitHub() {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF064E3B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF10B981)),
      ),
      child: Row(
        children: [
          const Icon(Icons.new_releases_outlined, color: Color(0xFF4ADE80), size: 24),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Versi Baru Tersedia di GitHub!',
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                ),
                Text(
                  _infoPembaruan?.pesanKomit ?? 'Pembaruan terbaru terdeteksi',
                  style: const TextStyle(color: Colors.white70, fontSize: 11),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF00A651),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              minimumSize: Size.zero,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
            onPressed: () {
              if (_infoPembaruan != null) {
                SaluranPenghubungNative.bukaTautanWeb(_infoPembaruan!.tautanWeb);
              }
            },
            child: const Text('Update', style: TextStyle(fontSize: 11)),
          ),
        ],
      ),
    );
  }

  // [FITUR: Kartu Sakelar Layanan Utama]
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
                      ? 'Aktif (Auto-Tempel & Bilah Melayang Siap)'
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

  // [FITUR: Kartu Info Retensi 30 Hari & Penyimpanan Tak Terbatas] - Menggantikan timer kadaluarsa pendek
  Widget _bangunKartuRetensi30Hari() {
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
              Icon(Icons.all_inclusive, color: Color(0xFF38BDF8), size: 20),
              SizedBox(width: 8),
              Text(
                'Penyimpanan Tak Terbatas (30 Hari)',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            'Seluruh screenshot disimpan aman di brankas privat tanpa batasan kuota antrean. Berkas yang berusia lebih dari 30 hari akan dibersihkan secara otomatis.',
            style: TextStyle(fontSize: 12, color: Colors.white70),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: const Color(0xFF0F172A),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: const Color(0xFF334155)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(Icons.photo_library, color: Color(0xFF00A651), size: 20),
                    const SizedBox(width: 8),
                    Text(
                      '${_daftarAntreanBrankas.length} Foto Tersimpan',
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                  ],
                ),
                Text(
                  'Retensi: 30 Hari',
                  style: TextStyle(color: Colors.grey.shade400, fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // [FITUR: Kartu Panel Status Izin Android 16]
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

          _bangunBarisIzin(
            judul: 'Akses Semua Berkas (MANAGE_EXTERNAL_STORAGE)',
            keterangan: 'Hapus senyap screenshot dari galeri publik tanpa pop-up',
            diberikan: _statusIzin['aksesSemuaBerkas'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinAksesSemuaBerkas,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          _bangunBarisIzin(
            judul: 'Notifikasi Foreground (POST_NOTIFICATIONS)',
            keterangan: 'Menjaga layanan pemantau tetap stabil di latar belakang',
            diberikan: _statusIzin['notifikasi'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinNotifikasi,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          _bangunBarisIzin(
            judul: 'Tampilkan di Atas Aplikasi (Overlay Window)',
            keterangan: 'Diperlukan untuk bilah samping Game Turbo & bypass clipboard',
            diberikan: _statusIzin['overlay'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaIzinOverlay,
          ),
          const Divider(color: Color(0xFF334155), height: 16),

          _bangunBarisIzin(
            judul: 'Abaikan Penghemat Baterai (Ignore Battery)',
            keterangan: 'Mencegah sistem mematikan pemantauan saat layar mati',
            diberikan: _statusIzin['penghematBaterai'] == true,
            padaBukaPengaturan: SaluranPenghubungNative.mintaAbaikanPenghematBaterai,
          ),
        ],
      ),
    );
  }

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

  // [FITUR: Bagian Daftar Antrean Brankas Tanpa Batas]
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
                  'Brankas Foto (${_daftarAntreanBrankas.length})',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                ),
              ],
            ),
            Row(
              children: [
                if (_daftarAntreanBrankas.isNotEmpty) ...[
                  // Tombol Pilih Beberapa / Batal Pilih
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        _modePilihBeberapa = !_modePilihBeberapa;
                        _kumpulanJalurTerpilih.clear();
                      });
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _modePilihBeberapa ? const Color(0xFF7F1D1D) : const Color(0xFF1E3A8A),
                      foregroundColor: _modePilihBeberapa ? const Color(0xFFFCA5A5) : const Color(0xFF93C5FD),
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: Text(_modePilihBeberapa ? 'Batal Pilih' : 'Pilih Beberapa', style: const TextStyle(fontSize: 11)),
                  ),
                  const SizedBox(width: 6),

                  // Tombol Salin Terpilih (jika dalam mode pilih beberapa)
                  if (_modePilihBeberapa) ...[
                    ElevatedButton.icon(
                      onPressed: () async {
                        if (_kumpulanJalurTerpilih.isEmpty) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Pilih minimal 1 gambar terlebih dahulu')),
                          );
                          return;
                        }
                        final sukses = await SaluranPenghubungNative.salinBanyakGambarKePapanKlip(_kumpulanJalurTerpilih.toList());
                        if (sukses && mounted) {
                          final jumlah = _kumpulanJalurTerpilih.length;
                          setState(() {
                            _modePilihBeberapa = false;
                            _kumpulanJalurTerpilih.clear();
                          });
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              backgroundColor: const Color(0xFF10B981),
                              content: Text('$jumlah foto berhasil disalin ke clipboard!'),
                            ),
                          );
                        }
                      },
                      icon: const Icon(Icons.copy, size: 12),
                      label: Text(
                        _kumpulanJalurTerpilih.isEmpty ? 'Salin' : 'Salin (${_kumpulanJalurTerpilih.length})',
                        style: const TextStyle(fontSize: 11),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2563EB),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                    const SizedBox(width: 6),
                    ElevatedButton.icon(
                      onPressed: () async {
                        if (_kumpulanJalurTerpilih.isEmpty) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Pilih minimal 1 gambar terlebih dahulu')),
                          );
                          return;
                        }
                        await SaluranPenghubungNative.bagikanBanyakGambar(_kumpulanJalurTerpilih.toList());
                      },
                      icon: const Icon(Icons.share, size: 12),
                      label: Text(
                        _kumpulanJalurTerpilih.isEmpty ? 'Kirim' : 'Kirim (${_kumpulanJalurTerpilih.length})',
                        style: const TextStyle(fontSize: 11),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF10B981),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                  ] else
                    ElevatedButton.icon(
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
                  const SizedBox(width: 6),
                  IconButton(
                    icon: const Icon(Icons.delete_sweep, color: Color(0xFFF43F5E), size: 20),
                    tooltip: 'Kosongkan Seluruh Brankas',
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    onPressed: _konfirmasiKosongkanBrankas,
                  ),
                ],
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
                    'Ambil screenshot di HP Anda untuk melihatnya masuk ke sini secara otomatis tanpa tersimpan di Galeri.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ],
              ),
            ),
          )
        else
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

  // [FITUR: Kartu Thumbnail Screenshot Interaktif] - Checkbox pojok kiri atas (mode pilih), klik 2 kali buka pratinjau foto jelas di pop up, tekan lama menu pop up simpan & edit
  Widget _bangunKartuThumbnail(ModelBerkasGambar butir) {
    final terpilih = _kumpulanJalurTerpilih.contains(butir.jalurBerkas);

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: terpilih ? const Color(0xFF38BDF8) : const Color(0xFF334155),
          width: terpilih ? 2 : 1,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: GestureDetector(
              onTap: () {
                if (_modePilihBeberapa) {
                  setState(() {
                    if (terpilih) {
                      _kumpulanJalurTerpilih.remove(butir.jalurBerkas);
                    } else {
                      _kumpulanJalurTerpilih.add(butir.jalurBerkas);
                    }
                  });
                } else {
                  _bukaLayarEditor(butir);
                }
              },
              // [FITUR: Klik 2 Kali (Double Tap) -> Perlihatkan Foto dengan Jelas + Pilih Teks Google Lens]
              onDoubleTap: () {
                _tampilkanPratinjauFotoJelas(butir);
              },
              // [FITUR: Tekan Lama (Long Press) -> Pop Up Simpan ke Galeri & Edit]
              onLongPress: () {
                _tampilkanDialogOpsiFoto(butir);
              },
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

                  // [FITUR: Checkbox di Pojok Kiri Atas] - Muncul saat mode pilih beberapa aktif, default non-aktif
                  if (_modePilihBeberapa)
                    Positioned(
                      top: 6,
                      left: 6,
                      child: Container(
                        width: 24,
                        height: 24,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: terpilih ? const Color(0xFF10B981) : Colors.black.withValues(alpha: 0.6),
                          border: Border.all(
                            color: Colors.white,
                            width: terpilih ? 1.5 : 2,
                          ),
                        ),
                        child: terpilih
                            ? const Center(
                                child: Icon(Icons.check, size: 16, color: Colors.white),
                              )
                            : null,
                      ),
                    ),

                  // Label Ukuran File
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
          Container(
            color: const Color(0xFF0F172A),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
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
                          content: Text('Foto berhasil disalin ke clipboard!'),
                          duration: Duration(seconds: 2),
                        ),
                      );
                    }
                  },
                ),
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

  // [FITUR: Dialog Opsi Foto saat Tekan Lama] - Simpan ke galeri atau buka editor
  void _tampilkanDialogOpsiFoto(ModelBerkasGambar butir) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1E293B),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (konteks) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.shade600,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 14),
              Text(
                butir.namaBerkas,
                style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 14),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 12),
              ListTile(
                leading: const Icon(Icons.save_alt, color: Color(0xFF10B981)),
                title: const Text('Simpan ke Galeri', style: TextStyle(color: Colors.white)),
                subtitle: const Text('Ekspor langsung ke Pictures/AutoTempelEdit', style: TextStyle(color: Colors.grey, fontSize: 12)),
                onTap: () async {
                  Navigator.of(konteks).pop();
                  final sukses = await SaluranPenghubungNative.simpanGambarKeGaleriPublik(butir.jalurBerkas);
                  if (sukses && mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        backgroundColor: Color(0xFF10B981),
                        content: Text('Foto berhasil disimpan ke galeri!'),
                      ),
                    );
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.edit, color: Color(0xFF38BDF8)),
                title: const Text('Edit Gambar', style: TextStyle(color: Colors.white)),
                subtitle: const Text('Crop, coret, mirror, dan putar derajat', style: TextStyle(color: Colors.grey, fontSize: 12)),
                onTap: () {
                  Navigator.of(konteks).pop();
                  _bukaLayarEditor(butir);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  // [FITUR: Pratinjau Foto Jelas & Pilih Teks Google Lens] - Tekan 2x memunculkan foto resolusi penuh + tombol Pilih Teks OCR
  void _tampilkanPratinjauFotoJelas(ModelBerkasGambar butir) {
    showDialog(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.92),
      builder: (konteks) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: EdgeInsets.zero,
        child: Scaffold(
          backgroundColor: Colors.transparent,
          appBar: AppBar(
            backgroundColor: const Color(0xFF1E293B).withValues(alpha: 0.9),
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.close, color: Colors.white),
              onPressed: () => Navigator.of(konteks).pop(),
            ),
            title: Text(
              butir.namaBerkas,
              style: const TextStyle(fontSize: 14, color: Colors.white),
              overflow: TextOverflow.ellipsis,
            ),
            actions: [
              // Tombol Pilih Teks (Google Lens)
              Padding(
                padding: const EdgeInsets.only(right: 8.0),
                child: ElevatedButton.icon(
                  onPressed: () => _jalankanDeteksiTeksOcr(butir),
                  icon: const Icon(Icons.document_scanner, size: 16),
                  label: const Text('Pilih Teks', style: TextStyle(fontSize: 12)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF2563EB),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                ),
              ),
              // Tombol Salin Gambar
              IconButton(
                icon: const Icon(Icons.copy, color: Color(0xFF38BDF8), size: 20),
                tooltip: 'Salin ke Clipboard',
                onPressed: () async {
                  final sukses = await SaluranPenghubungNative.salinGambarKePapanKlip(butir.jalurBerkas);
                  if (sukses && mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        backgroundColor: Color(0xFF10B981),
                        content: Text('Foto berhasil disalin ke clipboard!'),
                      ),
                    );
                  }
                },
              ),
              // Tombol Simpan ke Galeri
              IconButton(
                icon: const Icon(Icons.save_alt, color: Color(0xFF10B981), size: 20),
                tooltip: 'Simpan ke Galeri',
                onPressed: () async {
                  final sukses = await SaluranPenghubungNative.simpanGambarKeGaleriPublik(butir.jalurBerkas);
                  if (sukses && mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        backgroundColor: Color(0xFF10B981),
                        content: Text('Foto berhasil disimpan ke galeri!'),
                      ),
                    );
                  }
                },
              ),
            ],
          ),
          body: Center(
            child: InteractiveViewer(
              panEnabled: true,
              boundaryMargin: const EdgeInsets.all(20),
              minScale: 0.5,
              maxScale: 4.0,
              child: Image.file(
                File(butir.jalurBerkas),
                fit: BoxFit.contain,
              ),
            ),
          ),
        ),
      ),
    );
  }

  // [FITUR: Deteksi Teks OCR Google Lens] - Mengekstrak teks dari screenshot secara offline dan menyajikannya dalam tampilan selectable
  Future<void> _jalankanDeteksiTeksOcr(ModelBerkasGambar butir) async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: Card(
          color: Color(0xFF1E293B),
          child: Padding(
            padding: EdgeInsets.all(20.0),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(color: Color(0xFF38BDF8)),
                SizedBox(width: 16),
                Text('Mendeteksi teks dengan Google Lens...', style: TextStyle(color: Colors.white)),
              ],
            ),
          ),
        ),
      ),
    );

    final hasilOcr = await SaluranPenghubungNative.deteksiTeksDariGambar(butir.jalurBerkas);

    if (mounted) {
      Navigator.of(context).pop(); // Tutup loading dialog

      final sukses = hasilOcr?['sukses'] == true;
      final teks = (hasilOcr?['teks'] as String? ?? '').trim();

      if (sukses && teks.isNotEmpty) {
        _tampilkanModalTeksTerdeteksi(teks);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Tidak ada teks yang terdeteksi pada gambar ini'),
          ),
        );
      }
    }
  }

  // [FITUR: Modal Teks Terdeteksi ala Google Lens] - Pengguna bisa memilih per huruf/kata dan menyalinnya
  void _tampilkanModalTeksTerdeteksi(String teks) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1E293B),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (konteks) => SafeArea(
        child: Padding(
          padding: EdgeInsets.only(
            left: 16,
            right: 16,
            top: 16,
            bottom: MediaQuery.of(konteks).viewInsets.bottom + 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.document_scanner, color: Color(0xFF38BDF8), size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Teks Terdeteksi (Google Lens)',
                        style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 15),
                      ),
                    ],
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.grey),
                    onPressed: () => Navigator.of(konteks).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                'Tekan dan tahan pada teks di bawah untuk memilih huruf/kata tertentu, atau ketuk tombol untuk menyalin semua:',
                style: TextStyle(color: Colors.white70, fontSize: 12),
              ),
              const SizedBox(height: 12),
              Container(
                constraints: const BoxConstraints(maxHeight: 220),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFF0F172A),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFF334155)),
                ),
                child: SingleChildScrollView(
                  child: SelectableText(
                    teks,
                    style: const TextStyle(fontSize: 13, color: Color(0xFFE2E8F0), height: 1.4),
                    showCursor: true,
                    cursorColor: const Color(0xFF38BDF8),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              ElevatedButton.icon(
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: teks));
                  Navigator.of(konteks).pop();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      backgroundColor: Color(0xFF10B981),
                      content: Text('Seluruh teks berhasil disalin ke clipboard!'),
                    ),
                  );
                },
                icon: const Icon(Icons.copy, size: 16),
                label: const Text('Salin Semua Teks'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF10B981),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

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

  void _konfirmasiKosongkanBrankas() {
    showDialog(
      context: context,
      builder: (konteks) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Kosongkan Seluruh Brankas?', style: TextStyle(color: Colors.white)),
        content: const Text(
          'Seluruh tangkapan layar di brankas akan dihapus dan clipboard dikosongkan. Aksi ini tidak dapat dibatalkan.',
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

  void _tampilkanDialogPeringatanIzin() {
    showDialog(
      context: context,
      builder: (konteks) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text('Izin Wajib Diperlukan', style: TextStyle(color: Colors.white)),
        content: const Text(
          'Untuk menjalankan pemantauan tangkapan layar, bilah Game Turbo, dan bypass clipboard di Android 16, Anda wajib memberikan:\n\n1. Izin Akses Semua Berkas\n2. Izin Tampilkan di Atas Aplikasi (Overlay)\n\nSilakan tekan tombol "Izinkan" pada panel status izin.',
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
