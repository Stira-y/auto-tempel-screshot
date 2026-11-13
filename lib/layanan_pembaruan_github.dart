import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

// [FITUR: Model Informasi Pembaruan GitHub] - Menyimpan rincian rilis/komit terbaru dari repositori GitHub
class InformasiPembaruanGitHub {
  final bool apakahAdaPembaruan;
  final String shaKomit;
  final String pesanKomit;
  final String tanggalKomit;
  final String tautanWeb;

  InformasiPembaruanGitHub({
    required this.apakahAdaPembaruan,
    required this.shaKomit,
    required this.pesanKomit,
    required this.tanggalKomit,
    required this.tautanWeb,
  });
}

// [FITUR: Layanan Pemeriksa Pembaruan GitHub] - Menghubungkan aplikasi langsung ke repositori GitHub untuk mendeteksi update terbaru setelah push
class LayananPembaruanGitHub {
  // [IDENTITAS VERSI SAAT INI] - Kode commit SHA versi lokal saat ini
  static const String kodeKomitLokalSaatIni = "39677b7";
  static const String tautanRepositoriUtama = "https://github.com/Stira-y/auto-tempel-screshot";
  static const String titikAksesApiGithub = "https://api.github.com/repos/Stira-y/auto-tempel-screshot/commits/main";

  // [FITUR: Pengecekan Komit Terbaru di GitHub] - Membandingkan komit GitHub dengan versi aplikasi saat ini
  static Future<InformasiPembaruanGitHub?> periksaPembaruanDiGitHub() async {
    final klienHttp = HttpClient();
    klienHttp.connectionTimeout = const Duration(seconds: 8);

    try {
      final uri = Uri.parse(titikAksesApiGithub);
      final permintaan = await klienHttp.getUrl(uri);
      permintaan.headers.set('User-Agent', 'Take-and-Paste-App-Android');
      permintaan.headers.set('Accept', 'application/vnd.github.v3+json');

      final respon = await permintaan.close();
      if (respon.statusCode == 200) {
        final dataTeks = await respon.transform(utf8.decoder).join();
        final Map<String, dynamic> dataJson = json.decode(dataTeks);

        final shaDaring = dataJson['sha']?.toString() ?? '';
        final pesanKomit = dataJson['commit']?['message']?.toString() ?? 'Pembaruan sistem terbaru';
        final tanggalKomit = dataJson['commit']?['committer']?['date']?.toString() ?? '';
        final tautanWeb = dataJson['html_url']?.toString() ?? tautanRepositoriUtama;

        // Bandingkan kode SHA: Jika commit di GitHub berbeda dari versi lokal, maka ada update baru
        final apakahAdaUpdate = shaDaring.isNotEmpty &&
            !shaDaring.startsWith(kodeKomitLokalSaatIni) &&
            !kodeKomitLokalSaatIni.startsWith(shaDaring);

        return InformasiPembaruanGitHub(
          apakahAdaPembaruan: apakahAdaUpdate,
          shaKomit: shaDaring.length >= 7 ? shaDaring.substring(0, 7) : shaDaring,
          pesanKomit: pesanKomit.trim(),
          tanggalKomit: tanggalKomit,
          tautanWeb: tautanWeb,
        );
      }
    } catch (e) {
      debugPrint("Gagal memeriksa pembaruan GitHub: $e");
    } finally {
      klienHttp.close();
    }
    return null;
  }
}
