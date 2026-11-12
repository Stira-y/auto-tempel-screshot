// [FITUR: Model Berkas Gambar] - Representasi data berkas gambar tangkapan layar di brankas privat

class ModelBerkasGambar {
  final String idBerkas;
  final String namaBerkas;
  final String jalurBerkas;
  final int waktuDibuatMs;
  final int ukuranBerkasByte;

  ModelBerkasGambar({
    required this.idBerkas,
    required this.namaBerkas,
    required this.jalurBerkas,
    required this.waktuDibuatMs,
    required this.ukuranBerkasByte,
  });

  // [FITUR: Serialisasi dari Map Native] - Mengubah struktur peta dari saluran platform Android ke objek model Dart
  factory ModelBerkasGambar.dariPeta(Map<dynamic, dynamic> peta) {
    return ModelBerkasGambar(
      idBerkas: peta['idBerkas']?.toString() ?? '',
      namaBerkas: peta['namaBerkas']?.toString() ?? '',
      jalurBerkas: peta['jalurBerkas']?.toString() ?? '',
      waktuDibuatMs: (peta['waktuDibuatMs'] as num?)?.toInt() ?? 0,
      ukuranBerkasByte: (peta['ukuranBerkasByte'] as num?)?.toInt() ?? 0,
    );
  }

  // [FITUR: Format Ukuran Berkas] - Mengonversi bita menjadi representasi teks KB atau MB yang mudah dibaca
  String dapatkanUkuranTerbaca() {
    if (ukuranBerkasByte < 1024) return '$ukuranBerkasByte B';
    if (ukuranBerkasByte < 1024 * 1024) {
      return '${(ukuranBerkasByte / 1024).toStringAsFixed(1)} KB';
    }
    return '${(ukuranBerkasByte / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}

