import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'layar_utama.dart';

// [FITUR: Titik Masuk Utama Aplikasi] - Menginisialisasi aplikasi Auto-Tempel Screenshot + Editor untuk Android 16
void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Atur warna status bar transparan dengan ikon terang
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF0F172A),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const AplikasiAutoTempel());
}

// [FITUR: Aplikasi Auto Tempel] - Pengaturan tema gelap modern Material 3
class AplikasiAutoTempel extends StatelessWidget {
  const AplikasiAutoTempel({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Auto Tempel Screenshot',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: ColorScheme.dark(
          primary: const Color(0xFF38BDF8),
          surface: const Color(0xFF1E293B),
        ),
      ),
      home: const LayarUtama(),
    );
  }
}
