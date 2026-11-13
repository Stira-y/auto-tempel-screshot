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

  // [FITUR: Menjalankan Layanan Latar Depan] - Memulai LayananTangkapanLayar untuk pemantauan terus-menerus
  static Future<bool> mulaiLayanan([int durasiDetik = 0]) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('mulaiLayanan');
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

  // [FITUR: Mengambil Daftar Antrean Brankas] - Membaca seluruh screenshot aktif di brankas privat tanpa batasan
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

  // [FITUR: Salin Banyak Gambar Sekaligus ke Papan Klip] - Menyalin beberapa gambar yang dipilih ke clipboard
  static Future<bool> salinBanyakGambarKePapanKlip(List<String> daftarJalur) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>(
        'salinBanyakGambarKePapanKlip',
        {'daftarJalur': daftarJalur},
      );
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Bagikan Gambar Terpilih] - Membagikan beberapa gambar pilihan saja ke WhatsApp atau aplikasi lain
  static Future<bool> bagikanBanyakGambar(List<String> daftarJalur) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>(
        'bagikanBanyakGambar',
        {'daftarJalur': daftarJalur},
      );
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Simpan Satu Gambar Langsung ke Galeri Publik] - Menyimpan berkas dari brankas ke Galeri (Pictures/AutoTempelEdit)
  static Future<bool> simpanGambarKeGaleriPublik(String jalurBerkas) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>(
        'simpanGambarKeGaleriPublik',
        {'jalurBerkas': jalurBerkas},
      );
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Simpan Banyak Gambar Sekaligus ke Galeri Publik] - Mengekspor sejumlah foto terpilih ke Galeri sekaligus
  static Future<int> simpanBanyakGambarKeGaleriPublik(List<String> daftarJalur) async {
    try {
      final jumlah = await _saluranMetode.invokeMethod<int>(
        'simpanBanyakGambarKeGaleriPublik',
        {'daftarJalur': daftarJalur},
      );
      return jumlah ?? 0;
    } catch (_) {
      return 0;
    }
  }

  // [FITUR: Hapus Banyak Gambar dari Brankas] - Menghapus sejumlah foto terpilih secara langsung dari brankas
  static Future<int> hapusBanyakGambarDariAntrean(List<String> daftarJalur) async {
    try {
      final jumlah = await _saluranMetode.invokeMethod<int>(
        'hapusBanyakGambarDariAntrean',
        {'daftarJalur': daftarJalur},
      );
      return jumlah ?? 0;
    } catch (_) {
      return 0;
    }
  }

  // [FITUR: Deteksi Teks OCR Google Lens] - Mengekstrak teks dari berkas gambar 100% offline
  static Future<Map<String, dynamic>?> deteksiTeksDariGambar(String jalurBerkas) async {
    try {
      final hasil = await _saluranMetode.invokeMethod<Map<dynamic, dynamic>>(
        'deteksiTeksDariGambar',
        {'jalurBerkas': jalurBerkas},
      );
      if (hasil == null) return null;
      return hasil.map((k, v) => MapEntry(k.toString(), v));
    } catch (_) {
      return null;
    }
  }

  // [FITUR: Terjemahkan Teks On-Device Google Lens] - Menerjemahkan teks hasil scan ke bahasa target (default Indonesia)
  static Future<Map<String, dynamic>?> terjemahkanTeks(
    String teksSumber, {
    String kodeBahasaTarget = 'id',
  }) async {
    try {
      final hasil = await _saluranMetode.invokeMethod<Map<dynamic, dynamic>>(
        'terjemahkanTeks',
        {
          'teksSumber': teksSumber,
          'kodeBahasaTarget': kodeBahasaTarget,
        },
      );
      if (hasil == null) return null;
      return hasil.map((k, v) => MapEntry(k.toString(), v));
    } catch (_) {
      return null;
    }
  }

  // [FITUR: Bagikan Seluruh Antrean Sekaligus] - Mengirim seluruh gambar antrean ke WhatsApp
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

  // [FITUR: Buka Tautan Eksternal] - Membuka tautan GitHub atau web di browser eksternal
  static Future<bool> bukaTautanWeb(String tautanUrl) async {
    try {
      final sukses = await _saluranMetode.invokeMethod<bool>('bukaTautanWeb', {'url': tautanUrl});
      return sukses ?? false;
    } catch (_) {
      return false;
    }
  }

  // [FITUR: Aliran Peristiwa Realtime] - Menerima broadcast pembaruan antrean dan status layanan dari native
  static Stream<dynamic> dengarkanPeristiwa() {
    return _saluranPeristiwa.receiveBroadcastStream();
  }
}
