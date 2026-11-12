import 'package:flutter/services.dart';
import 'model_berkas_gambar.dart';

// [FITUR: Jembatan Saluran Platform] - Menghubungkan antarmuka Flutter dengan API native Android Kotlin
class SaluranPenghubungNative {
  static const MethodChannel _saluranMetode = MethodChannel('com.example.apk_auto_tempel_srcht/saluran_utama');
  static const EventChannel _saluranPeristiwa = EventChannel('com.example.apk_auto_tempel_srcht/saluran_peristiwa');

  // [FITUR: Pemeriksaan Izin Sistem] - Mengambil status izin akses berkas, notifikasi, overlay, dan baterai
  static Future<Map<String, bool>> periksaStatusIzin() async {
    try {
      final hasil = await _saluranMetode.invokeMethod<Map<dynamic, dynamic>>('periksaIzin');
      if (hasil == null) return {};
      return hasil.map((kunci, nilai) => MapEntry(kunci.toString(), nilai == true));
    } catch (_) {
      return {};
    }
  }

  // [FITUR: Permintaan Izin Akses Semua Berkas] - Membuka pengaturan All Files Access Android 11+
  static Future<void> mintaIzinAksesSemuaBerkas() async {
    await _saluranMetode.invokeMethod('mintaIzinAksesSemuaBerkas');
  }

  // [FITUR: Permintaan Izin Notifikasi] - Meminta izin notifikasi foreground Android 13+
  static Future<void> mintaIzinNotifikasi() async {
    await _saluranMetode.invokeMethod('mintaIzinNotifikasi');
  }

  // [FITUR: Permintaan Izin Overlay Window] - Membuka layar izin tampilkan di atas aplikasi lain
  static Future<void> mintaIzinOverlay() async {
    await _saluranMetode.invokeMethod('mintaIzinOverlay');
  }

  // [FITUR: Permintaan Pengabaian Optimasi Baterai] - Membuka pengaturan pencegahan penutupan latar belakang
  static Future<void> mintaAbaikanPenghematBaterai() async {
    await _saluranMetode.invokeMethod('mintaAbaikanPenghematBaterai');
  }

  // [FITUR: Menjalankan Layanan Latar Depan] - Memulai LayananTangkapanLayar dengan durasi timer tertentu
  static Future<bool> mulaiLayanan(int durasiDetik) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('mulaiLayanan', {'durasiDetik': durasiDetik});
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Menghentikan Layanan Latar Depan] - Menghentikan pemantauan dan menutup gelembung melayang
  static Future<bool> hentikanLayanan() async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('hentikanLayanan');
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Memeriksa Status Berjalannya Layanan] - Mengetahui apakah service aktif di latar belakang
  static Future<bool> apakahLayananBerjalan() async {
    try {
      final aktif = await _saluranMetode.invokeMethod<bool>('statusLayanan');
      return aktif ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Pengaturan Durasi Timer] - Mengubah batas waktu kadaluarsa brankas
  static Future<void> aturDurasiTimer(int durasiDetik) async {
    await _saluranMetode.invokeMethod('aturDurasiTimer', {'durasiDetik': durasiDetik});
  }

  // [FITUR: Mengambil Sisa Waktu Hitung Mundur] - Mendapatkan detik tersisa dari layanan native
  static Future<int> dapatkanSisaWaktuDetik() async {
    try {
      final sisa = await _saluranMetode.invokeMethod<int>('dapatkanSisaWaktu');
      return sisa ?? 0;
    } catch (_) {
      return 0;
    }
  }

  // [FITUR: Mengambil Daftar Antrean Brankas] - Membaca hingga 10 screenshot aktif di brankas privat
  static Future<List<ModelBerkasGambar>> dapatkanDaftarAntrean() async {
    try {
      final hasil = await _saluranMetode.invokeMethod<List<dynamic>>('dapatkanDaftarAntrean');
      if (hasil == null) return [];
      return hasil
          .whereType<Map<dynamic, dynamic>>()
          .map((peta) => ModelBerkasGambar.dariPeta(peta))
          .toList();
    } catch (_) {
      return [];
    }
  }

  // [FITUR: Salin Gambar ke Papan Klip] - Menyalin gambar tertentu ke clipboard dengan bypass window focus
  static Future<bool> salinGambarKePapanKlip(String jalurBerkas) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>(
        'salinGambarKePapanKlip',
        {'jalurBerkas': jalurBerkas},
      );
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Bagikan Seluruh Antrean Sekaligus] - Mengirim hingga 10 gambar antrean ke WhatsApp
  static Future<bool> bagikanSemuaAntrean() async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('bagikanSemuaAntrean');
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Simpan Hasil Edit ke Galeri] - Mengekspor gambar hasil crop/coret ke folder Pictures/AutoTempelEdit MediaStore
  static Future<String?> simpanHasilEditKeGaleri(Uint8List dataByte, {String? namaBerkas}) async {
    try {
      final uri = await _saluranMetode.invokeMethod<String>(
        'simpanHasilEditKeGaleri',
        {
          'dataByte': dataByte,
          'namaBerkas': namaBerkas,
        },
      );
      return uri;
    } catch (_) {
      return null;
    }
  }

  // [FITUR: Hapus Satu Gambar Antrean] - Menghapus gambar tertentu dari disk dan memori
  static Future<bool> hapusGambarDariAntrean(String jalurBerkas) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>(
        'hapusGambarDariAntrean',
        {'jalurBerkas': jalurBerkas},
      );
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Kosongkan Seluruh Antrean Brankas] - Menghapus semua screenshot aktif seketika
  static Future<bool> kosongkanSemuaAntrean() async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('kosongkanSemuaAntrean');
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Aliran Peristiwa Realtime] - Menerima broadcast detik berkurang, antrean berubah, dan timer habis
  static Stream<dynamic> dengarkanPeristiwa() {
    return _saluranPeristiwa.receiveBroadcastStream();
  }
}
