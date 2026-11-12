import 'package:flutter/material.dart';

// [FITUR: Data Model Garis Coretan] - Menyimpan jejak titik koordinat, warna, dan ketebalan setiap goresan kuas
class GarisCoretan {
  final List<Offset> titikKoordinat;
  final Color warna;
  final double ketebalan;

  GarisCoretan({
    required this.titikKoordinat,
    required this.warna,
    required this.ketebalan,
  });
}

// [FITUR: Pelukis Coretan Kanvas] - Merender goresan tangan bebas di atas gambar menggunakan CustomPainter
class PelukisCoretan extends CustomPainter {
  final List<GarisCoretan> daftarCoretan;
  final GarisCoretan? coretanAktif;

  PelukisCoretan({
    required this.daftarCoretan,
    this.coretanAktif,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Gambar seluruh garis yang sudah tersimpan
    for (final garis in daftarCoretan) {
      _lukisSatuGaris(canvas, garis);
    }

    // Gambar garis yang saat ini sedang ditarik jari
    if (coretanAktif != null) {
      _lukisSatuGaris(canvas, coretanAktif!);
    }
  }

  // [FITUR: Render Garis Halus] - Menggambar sambungan titik menjadi kurva goresan yang mulus
  void _lukisSatuGaris(Canvas canvas, GarisCoretan garis) {
    if (garis.titikKoordinat.isEmpty) return;

    final kuas = Paint()
      ..color = garis.warna
      ..strokeWidth = garis.ketebalan
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke
      ..isAntiAlias = true;

    if (garis.titikKoordinat.length == 1) {
      canvas.drawCircle(garis.titikKoordinat.first, garis.ketebalan / 2, kuas..style = PaintingStyle.fill);
      return;
    }

    final jalur = Path();
    jalur.moveTo(garis.titikKoordinat.first.dx, garis.titikKoordinat.first.dy);

    for (int i = 1; i < garis.titikKoordinat.length; i++) {
      jalur.lineTo(garis.titikKoordinat[i].dx, garis.titikKoordinat[i].dy);
    }

    canvas.drawPath(jalur, kuas);
  }

  @override
  bool shouldRepaint(covariant PelukisCoretan oldDelegate) => true;
}

