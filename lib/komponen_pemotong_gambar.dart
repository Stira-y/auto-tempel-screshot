import 'package:flutter/material.dart';

// [FITUR: Pilihan Rasio Pemotongan] - Mendukung pemotongan bebas maupun rasio umum
enum RasioPotong {
  bebas,
  persegi1Banding1,
  standar4Banding3,
  lebar16Banding9,
  vertikal9Banding16,
}

// [FITUR: Komponen Pemotong Gambar Interaktif] - Menampilkan bingkai kotak pemotong dengan panduan garis sepertiga (Rule of Thirds) yang dapat digeser dan diubah ukurannya
class KomponenPemotongGambar extends StatefulWidget {
  final Rect batasGambar;
  final RasioPotong rasio;
  final ValueChanged<Rect> padaAreaPotongBerubah;

  const KomponenPemotongGambar({
    super.key,
    required this.batasGambar,
    required this.rasio,
    required this.padaAreaPotongBerubah,
  });

  @override
  State<KomponenPemotongGambar> createState() => _KomponenPemotongGambarState();
}

class _KomponenPemotongGambarState extends State<KomponenPemotongGambar> {
  late Rect _kotakPotong;
  _JenisSentuhan _sentuhanAktif = _JenisSentuhan.tidakAda;
  Offset _posisiSentuhSebelumnya = Offset.zero;

  @override
  void initState() {
    super.initState();
    _inisialisasiKotakPotong();
  }

  @override
  void didUpdateWidget(covariant KomponenPemotongGambar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.batasGambar != widget.batasGambar || oldWidget.rasio != widget.rasio) {
      _inisialisasiKotakPotong();
    }
  }

  // [FITUR: Inisialisasi Ukuran Bingkai Potong] - Menyesuaikan ukuran awal kotak sesuai batas gambar dan rasio yang dipilih
  void _inisialisasiKotakPotong() {
    final batas = widget.batasGambar;
    if (batas.width <= 0 || batas.height <= 0) {
      _kotakPotong = Rect.zero;
      return;
    }

    double lebar = batas.width * 0.85;
    double tinggi = batas.height * 0.85;

    switch (widget.rasio) {
      case RasioPotong.persegi1Banding1:
        final sisi = lebar < tinggi ? lebar : tinggi;
        lebar = sisi;
        tinggi = sisi;
        break;
      case RasioPotong.standar4Banding3:
        if (lebar / tinggi > 4 / 3) {
          lebar = tinggi * (4 / 3);
        } else {
          tinggi = lebar * (3 / 4);
        }
        break;
      case RasioPotong.lebar16Banding9:
        if (lebar / tinggi > 16 / 9) {
          lebar = tinggi * (16 / 9);
        } else {
          tinggi = lebar * (9 / 16);
        }
        break;
      case RasioPotong.vertikal9Banding16:
        if (lebar / tinggi > 9 / 16) {
          lebar = tinggi * (9 / 16);
        } else {
          tinggi = lebar * (16 / 9);
        }
        break;
      case RasioPotong.bebas:
        break;
    }

    final kiri = batas.left + (batas.width - lebar) / 2;
    final atas = batas.top + (batas.height - tinggi) / 2;

    _kotakPotong = Rect.fromLTWH(kiri, atas, lebar, tinggi);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      widget.padaAreaPotongBerubah(_kotakPotong);
    });
  }

  @override
  Widget build(BuildContext context) {
    if (widget.batasGambar.width <= 0 || widget.batasGambar.height <= 0) {
      return const SizedBox.shrink();
    }

    return GestureDetector(
      onPanDown: _tanganiSentuhMulai,
      onPanUpdate: _tanganiSentuhGeser,
      onPanEnd: (_) => _sentuhanAktif = _JenisSentuhan.tidakAda,
      child: CustomPaint(
        size: Size.infinite,
        painter: _PelukisBingkaiPotong(
          kotakPotong: _kotakPotong,
          batasGambar: widget.batasGambar,
        ),
      ),
    );
  }

  // [FITUR: Deteksi Titik Pegangan Sentuh] - Mengetahui apakah pengguna menyentuh sudut, tepi, atau tengah kotak pemotong
  void _tanganiSentuhMulai(DragDownDetails rincian) {
    final posisi = rincian.localPosition;
    _posisiSentuhSebelumnya = posisi;
    const toleransi = 28.0;

    final pojokKiriAtas = _kotakPotong.topLeft;
    final pojokKananAtas = _kotakPotong.topRight;
    final pojokKiriBawah = _kotakPotong.bottomLeft;
    final pojokKananBawah = _kotakPotong.bottomRight;

    if ((posisi - pojokKiriAtas).distance <= toleransi) {
      _sentuhanAktif = _JenisSentuhan.sudutKiriAtas;
    } else if ((posisi - pojokKananAtas).distance <= toleransi) {
      _sentuhanAktif = _JenisSentuhan.sudutKananAtas;
    } else if ((posisi - pojokKiriBawah).distance <= toleransi) {
      _sentuhanAktif = _JenisSentuhan.sudutKiriBawah;
    } else if ((posisi - pojokKananBawah).distance <= toleransi) {
      _sentuhanAktif = _JenisSentuhan.sudutKananBawah;
    } else if (_kotakPotong.contains(posisi)) {
      _sentuhanAktif = _JenisSentuhan.geserPusat;
    } else {
      _sentuhanAktif = _JenisSentuhan.tidakAda;
    }
  }

  // [FITUR: Perubahan Ukuran Dinamis] - Menghitung dimensi baru kotak potong dengan batas area gambar
  void _tanganiSentuhGeser(DragUpdateDetails rincian) {
    if (_sentuhanAktif == _JenisSentuhan.tidakAda) return;

    final selisih = rincian.localPosition - _posisiSentuhSebelumnya;
    _posisiSentuhSebelumnya = rincian.localPosition;

    final batas = widget.batasGambar;
    double kiri = _kotakPotong.left;
    double atas = _kotakPotong.top;
    double kanan = _kotakPotong.right;
    double bawah = _kotakPotong.bottom;

    const ukuranMinimal = 50.0;

    switch (_sentuhanAktif) {
      case _JenisSentuhan.geserPusat:
        double deltaX = selisih.dx;
        double deltaY = selisih.dy;

        if (kiri + deltaX < batas.left) deltaX = batas.left - kiri;
        if (kanan + deltaX > batas.right) deltaX = batas.right - kanan;
        if (atas + deltaY < batas.top) deltaY = batas.top - atas;
        if (bawah + deltaY > batas.bottom) deltaY = batas.bottom - bawah;

        kiri += deltaX;
        kanan += deltaX;
        atas += deltaY;
        bawah += deltaY;
        break;

      case _JenisSentuhan.sudutKiriAtas:
        kiri = (kiri + selisih.dx).clamp(batas.left, kanan - ukuranMinimal);
        atas = (atas + selisih.dy).clamp(batas.top, bawah - ukuranMinimal);
        break;

      case _JenisSentuhan.sudutKananAtas:
        kanan = (kanan + selisih.dx).clamp(kiri + ukuranMinimal, batas.right);
        atas = (atas + selisih.dy).clamp(batas.top, bawah - ukuranMinimal);
        break;

      case _JenisSentuhan.sudutKiriBawah:
        kiri = (kiri + selisih.dx).clamp(batas.left, kanan - ukuranMinimal);
        bawah = (bawah + selisih.dy).clamp(atas + ukuranMinimal, batas.bottom);
        break;

      case _JenisSentuhan.sudutKananBawah:
        kanan = (kanan + selisih.dx).clamp(kiri + ukuranMinimal, batas.right);
        bawah = (bawah + selisih.dy).clamp(atas + ukuranMinimal, batas.bottom);
        break;

      case _JenisSentuhan.tidakAda:
        break;
    }

    setState(() {
      _kotakPotong = Rect.fromLTRB(kiri, atas, kanan, bawah);
    });
    widget.padaAreaPotongBerubah(_kotakPotong);
  }
}

enum _JenisSentuhan {
  tidakAda,
  geserPusat,
  sudutKiriAtas,
  sudutKananAtas,
  sudutKiriBawah,
  sudutKananBawah,
}

// [FITUR: Pelukis Tampilan Kotak Pemotong] - Menampilkan area redup di luar crop dan garis kisi pemotong
class _PelukisBingkaiPotong extends CustomPainter {
  final Rect kotakPotong;
  final Rect batasGambar;

  _PelukisBingkaiPotong({
    required this.kotakPotong,
    required this.batasGambar,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Gambar overlay gelap di luar kotak potong
    final kuasGelap = Paint()..color = Colors.black.withValues(alpha: 0.55);
    final jalurLuar = Path()
      ..addRect(batasGambar)
      ..addRect(kotakPotong)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(jalurLuar, kuasGelap);

    // 2. Gambar garis batas kotak putih
    final kuasBingkai = Paint()
      ..color = Colors.white
      ..strokeWidth = 2.0
      ..style = PaintingStyle.stroke;
    canvas.drawRect(kotakPotong, kuasBingkai);

    // 3. Gambar garis pemandu sepertiga (Rule of Thirds)
    final kuasKisi = Paint()
      ..color = Colors.white.withValues(alpha: 0.35)
      ..strokeWidth = 1.0
      ..style = PaintingStyle.stroke;

    final lebarSatuPertiga = kotakPotong.width / 3;
    final tinggiSatuPertiga = kotakPotong.height / 3;

    // Garis vertikal
    canvas.drawLine(
      Offset(kotakPotong.left + lebarSatuPertiga, kotakPotong.top),
      Offset(kotakPotong.left + lebarSatuPertiga, kotakPotong.bottom),
      kuasKisi,
    );
    canvas.drawLine(
      Offset(kotakPotong.left + lebarSatuPertiga * 2, kotakPotong.top),
      Offset(kotakPotong.left + lebarSatuPertiga * 2, kotakPotong.bottom),
      kuasKisi,
    );

    // Garis horizontal
    canvas.drawLine(
      Offset(kotakPotong.left, kotakPotong.top + tinggiSatuPertiga),
      Offset(kotakPotong.right, kotakPotong.top + tinggiSatuPertiga),
      kuasKisi,
    );
    canvas.drawLine(
      Offset(kotakPotong.left, kotakPotong.top + tinggiSatuPertiga * 2),
      Offset(kotakPotong.right, kotakPotong.top + tinggiSatuPertiga * 2),
      kuasKisi,
    );

    // 4. Gambar pegangan sudut tebal
    final kuasSudut = Paint()
      ..color = Colors.white
      ..strokeWidth = 4.0
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.square;

    const panjangSudut = 18.0;

    // Kiri atas
    canvas.drawLine(kotakPotong.topLeft, kotakPotong.topLeft + const Offset(panjangSudut, 0), kuasSudut);
    canvas.drawLine(kotakPotong.topLeft, kotakPotong.topLeft + const Offset(0, panjangSudut), kuasSudut);

    // Kanan atas
    canvas.drawLine(kotakPotong.topRight, kotakPotong.topRight - const Offset(panjangSudut, 0), kuasSudut);
    canvas.drawLine(kotakPotong.topRight, kotakPotong.topRight + const Offset(0, panjangSudut), kuasSudut);

    // Kiri bawah
    canvas.drawLine(kotakPotong.bottomLeft, kotakPotong.bottomLeft + const Offset(panjangSudut, 0), kuasSudut);
    canvas.drawLine(kotakPotong.bottomLeft, kotakPotong.bottomLeft - const Offset(0, panjangSudut), kuasSudut);

    // Kanan bawah
    canvas.drawLine(kotakPotong.bottomRight, kotakPotong.bottomRight - const Offset(panjangSudut, 0), kuasSudut);
    canvas.drawLine(kotakPotong.bottomRight, kotakPotong.bottomRight - const Offset(0, panjangSudut), kuasSudut);
  }

  @override
  bool shouldRepaint(covariant _PelukisBingkaiPotong oldDelegate) {
    return oldDelegate.kotakPotong != kotakPotong || oldDelegate.batasGambar != batasGambar;
  }
}
