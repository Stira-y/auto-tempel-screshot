package com.example.apk_auto_tempel_srcht

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

// [FITUR: Panel Melayang Mini ala Game Turbo Redmi & Bypass Clipboard Android 16] - Bilah samping abu-abu auto-hide 25 detik, gelembung bundar melayang, laci antrean berkas yang bisa ditarik ke atas (expandable scroll), dan bypass window overlay
object JendelaGelembungMelayang {

    private const val TAG = "JendelaGelembung"
    private const val DURASI_AUTO_HIDE_MS = 25_000L // 25 Detik auto-hide

    private var pengelolaJendela: WindowManager? = null
    private var tampilanAktif: View? = null
    private var tampilanLaciAntrean: View? = null
    private var konteksAplikasi: Context? = null
    private val penanganUtama = Handler(Looper.getMainLooper())

    private var laciSedangTerbuka = false
    private var laciModePenuh = false
    private var sedangDalamModeBilahSamping = false

    // [FITUR: Status Pemilihan Banyak Gambar]
    private var modePilihBeberapa = false
    private val kumpulanJalurTerpilih = mutableSetOf<String>()

    private var posisiGelembungX = 0
    private var posisiGelembungY = 300

    // [FITUR: Timer Otomatis Sembunyi 25 Detik] - Menutup laci dan mengubah gelembung ke bilah samping abu-abu saat 25 detik inaktivitas
    private val tugasOtomatisSembunyi = Runnable {
        val konteks = konteksAplikasi ?: return@Runnable
        if (sedangDalamModeBilahSamping) {
            return@Runnable
        }
        Log.i(TAG, "Inaktivitas 25 detik: menyembunyikan tampilan ke bilah samping Game Turbo")
        ubahKeModeBilahSamping(konteks)
    }

    // [FITUR: Reset Timer Inaktivitas] - Memperpanjang durasi aktif setiap ada sentuhan atau screenshot baru
    private fun aturUlangTimerInaktivitas() {
        penanganUtama.removeCallbacks(tugasOtomatisSembunyi)
        penanganUtama.postDelayed(tugasOtomatisSembunyi, DURASI_AUTO_HIDE_MS)
    }

    // [FITUR: Bypass Window Focus Android 16] - Memunculkan overlay transparan 1x1 pixel sesaat agar clipboard diizinkan menerima data di latar belakang
    fun tampilkanOverlayFokusSesaat(konteks: Context, aksiSetelahFokus: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(konteks)) {
            aksiSetelahFokus.invoke()
            return
        }

        penanganUtama.post {
            try {
                val wm = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val tampilanFokus = View(konteks).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                }

                val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val parameterFokus = WindowManager.LayoutParams(
                    1, 1,
                    tipeWindow,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.START or Gravity.TOP
                    x = 0
                    y = 0
                }

                wm.addView(tampilanFokus, parameterFokus)

                penanganUtama.postDelayed({
                    aksiSetelahFokus.invoke()
                    penanganUtama.postDelayed({
                        try {
                            wm.removeView(tampilanFokus)
                        } catch (e: Exception) {
                            Log.w(TAG, "Overlay 1x1 sudah dilepas: ${e.message}")
                        }
                    }, 200)
                }, 50)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal overlay 1x1: ${e.message}")
                aksiSetelahFokus.invoke()
            }
        }
    }

    // [FITUR: Tampilkan Gelembung Utama] - Memunculkan floating window pertama kali
    fun tampilkanGelembung(konteks: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(konteks)) {
            Log.w(TAG, "Izin overlay belum diberikan")
            return
        }

        konteksAplikasi = konteks.applicationContext
        penanganUtama.post {
            if (tampilanAktif != null) return@post
            pengelolaJendela = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            ubahKeModeGelembungBulat(konteks)
        }
    }

    // [FITUR: Bangunkan dari Bilah Samping] - Dipicu otomatis setiap kali sistem mendeteksi screenshot baru
    fun bangunkanDariBilahSamping(konteks: Context) {
        penanganUtama.post {
            konteksAplikasi = konteks.applicationContext
            if (pengelolaJendela == null) {
                pengelolaJendela = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }

            // Jika sedang dalam mode garis abu-abu tersembunyi, munculkan gelembung bulat
            if (sedangDalamModeBilahSamping || tampilanAktif == null) {
                ubahKeModeGelembungBulat(konteks)
            } else {
                aturUlangTimerInaktivitas()
                perbaruiTampilanAntrean(PengelolaBrankas.dapatkanDaftarAntreanSaatIni())
            }
        }
    }

    // [FITUR: Mode Bilah Samping Game Turbo Redmi] - Garis abu-abu persegi panjang kecil di tepi layar
    private fun ubahKeModeBilahSamping(konteks: Context) {
        konteksAplikasi = konteks.applicationContext
        if (pengelolaJendela == null) {
            pengelolaJendela = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        val wm = pengelolaJendela ?: return

        // 1. Pastikan laci antrean tertutup dan terlepas dari window manager jika sedang terbuka
        tampilanLaciAntrean?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        tampilanLaciAntrean = null
        laciSedangTerbuka = false

        // 2. Lepas tampilan gelembung bulat aktif sebelumnya
        tampilanAktif?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            tampilanAktif = null
        }

        sedangDalamModeBilahSamping = true
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Persegi panjang kecil abu-abu tipis (lebar 7dp, tinggi 75dp)
        val parameterBilah = WindowManager.LayoutParams(
            dpKePx(7), dpKePx(75),
            tipeWindow,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0 // Menempel tepat di tepi kiri layar
            y = posisiGelembungY
        }

        // Tampilan Indikator Garis Abu-abu Game Turbo
        val bilahSamping = View(konteks).apply {
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Sudut melengkung ke arah dalam layar
                cornerRadii = floatArrayOf(0f, 0f, dpKePx(4).toFloat(), dpKePx(4).toFloat(), dpKePx(4).toFloat(), dpKePx(4).toFloat(), 0f, 0f)
                setColor(Color.parseColor("#9964748B")) // Abu-abu semi transparan
                setStroke(dpKePx(1), Color.parseColor("#475569"))
            }
            background = latar
        }

        // Gesture: Geser ke dalam layar (swipe right) atau ketuk untuk memunculkan gelembung
        bilahSamping.setOnTouchListener(object : View.OnTouchListener {
            private var awalSentuhX = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        awalSentuhX = event.rawX
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val geser = event.rawX - awalSentuhX
                        if (geser > dpKePx(10)) {
                            // Pengguna menggeser bilah abu-abu keluar dari tepi layar
                            ubahKeModeGelembungBulat(konteks)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // Ketukan langsung juga dapat membuka
                        ubahKeModeGelembungBulat(konteks)
                        return true
                    }
                }
                return false
            }
        })

        tampilanAktif = bilahSamping
        try {
            wm.addView(bilahSamping, parameterBilah)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memasang bilah samping Game Turbo: ${e.message}")
        }
    }

    // [FITUR: Mode Gelembung Bulat] - Menampilkan ikon gunting melayang bundar yang dapat digeser
    private fun ubahKeModeGelembungBulat(konteks: Context) {
        val wm = pengelolaJendela ?: return
        tampilanAktif?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            tampilanAktif = null
        }

        sedangDalamModeBilahSamping = false
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val parameterGelembung = WindowManager.LayoutParams(
            dpKePx(56), dpKePx(56),
            tipeWindow,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (posisiGelembungX < dpKePx(16)) dpKePx(16) else posisiGelembungX
            y = posisiGelembungY
        }

        val wadahGelembung = FrameLayout(konteks).apply {
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00A651")) // Hijau CorelDraw
                setStroke(dpKePx(2), Color.WHITE)
            }
            background = latar
            elevation = 10f
        }

        // Ikon Gunting / Kamera
        val ikonGunting = ImageView(konteks).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.BLACK)
            val p = dpKePx(12)
            setPadding(p, p, p, p)
        }
        wadahGelembung.addView(ikonGunting)

        // Lencana Jumlah Gambar
        val teksJumlah = TextView(konteks).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            val latarLencana = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E11D48"))
            }
            background = latarLencana
            gravity = Gravity.CENTER
            val ukuran = dpKePx(20)
            layoutParams = FrameLayout.LayoutParams(ukuran, ukuran).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            text = PengelolaBrankas.dapatkanDaftarAntreanSaatIni().size.toString()
        }
        wadahGelembung.addView(teksJumlah)

        // Touch Listener untuk Drag & Tap
        wadahGelembung.setOnTouchListener(object : View.OnTouchListener {
            private var awalX = 0
            private var awalY = 0
            private var sentuhX = 0f
            private var sentuhY = 0f
            private var waktuMulai = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                aturUlangTimerInaktivitas()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        awalX = parameterGelembung.x
                        awalY = parameterGelembung.y
                        sentuhX = event.rawX
                        sentuhY = event.rawY
                        waktuMulai = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        parameterGelembung.x = awalX + (event.rawX - sentuhX).toInt()
                        parameterGelembung.y = awalY + (event.rawY - sentuhY).toInt()
                        posisiGelembungX = parameterGelembung.x
                        posisiGelembungY = parameterGelembung.y
                        try {
                            wm.updateViewLayout(wadahGelembung, parameterGelembung)
                        } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val durasi = System.currentTimeMillis() - waktuMulai
                        val jarak = Math.hypot((event.rawX - sentuhX).toDouble(), (event.rawY - sentuhY).toDouble())
                        if (jarak < 15 && durasi < 300) {
                            sakelarTampilanLaci(konteks)
                        } else if (parameterGelembung.x < dpKePx(8)) {
                            // Jika digeser sangat dekat ke tepi kiri, langsung sembunyikan ke bilah abu-abu
                            ubahKeModeBilahSamping(konteks)
                        }
                        return true
                    }
                }
                return false
            }
        })

        tampilanAktif = wadahGelembung
        try {
            wm.addView(wadahGelembung, parameterGelembung)
            aturUlangTimerInaktivitas()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memasang gelembung: ${e.message}")
        }
    }

    // [FITUR: Sakelar Laci Antrean] - Buka atau tutup laci
    private fun sakelarTampilanLaci(konteks: Context) {
        aturUlangTimerInaktivitas()
        if (laciSedangTerbuka) {
            tutupLaciAntrean()
        } else {
            bukaLaciAntrean(konteks, perbesarPenuh = false)
        }
    }

    // [FITUR: Buka Laci Antrean Expandable] - Mendukung mode compact atau mode penuh ditarik ke atas dengan scroll
    private fun bukaLaciAntrean(konteks: Context, perbesarPenuh: Boolean) {
        val wm = pengelolaJendela ?: return
        if (laciSedangTerbuka && tampilanLaciAntrean != null) {
            tutupLaciAntrean()
        }

        laciModePenuh = perbesarPenuh
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val tinggiLaci = if (laciModePenuh) dpKePx(440) else dpKePx(210)

        val parameterLaci = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            tinggiLaci,
            tipeWindow,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dpKePx(12)
        }

        // Wadah Utama Laci
        val tataLetakLaci = LinearLayout(konteks).apply {
            orientation = LinearLayout.VERTICAL
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(18).toFloat()
                setColor(Color.parseColor("#FA111827"))
                setStroke(dpKePx(2), Color.parseColor("#374151"))
            }
            background = latar
            val p = dpKePx(12)
            setPadding(p, p, p, p)
        }

        // [FITUR: Pegangan Geser / Garis Persegi Panjang Kecil Interaktif] - Menarik ke atas untuk mode scroll horizontal, ke bawah untuk mode scroll vertikal
        val peganganGeser = LinearLayout(konteks).apply {
            gravity = Gravity.CENTER
            val pBawah = dpKePx(6)
            setPadding(0, 0, 0, pBawah)
        }
        val barGrip = View(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(dpKePx(56), dpKePx(5))
            val latarGrip = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(3).toFloat()
                setColor(Color.parseColor("#9CA3AF"))
            }
            background = latarGrip
        }
        peganganGeser.addView(barGrip)

        // Interaksi gesture garis penarik (Tarik ke atas = scroll horizontal, tarik ke bawah = scroll vertikal)
        peganganGeser.setOnTouchListener(object : View.OnTouchListener {
            private var awalY = 0f
            private var sudahGanti = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                aturUlangTimerInaktivitas()
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        awalY = event.rawY
                        sudahGanti = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - awalY
                        if (!sudahGanti) {
                            if (deltaY < -dpKePx(16)) {
                                // Menarik ke atas -> Scroll secara horizontal
                                sudahGanti = true
                                if (laciModePenuh) {
                                    bukaLaciAntrean(konteks, perbesarPenuh = false)
                                    Toast.makeText(konteks, "Mode Scroll Horizontal Aktif", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            } else if (deltaY > dpKePx(16)) {
                                // Menarik ke bawah -> Scroll secara vertikal
                                sudahGanti = true
                                if (!laciModePenuh) {
                                    bukaLaciAntrean(konteks, perbesarPenuh = true)
                                    Toast.makeText(konteks, "Mode Scroll Vertikal Aktif", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!sudahGanti) {
                            bukaLaciAntrean(konteks, perbesarPenuh = !laciModePenuh)
                        }
                        return true
                    }
                }
                return false
            }
        })
        tataLetakLaci.addView(peganganGeser)

        // Header Laci (Judul, Mode, Pilih Beberapa / Batal Pilih, Salin Terpilih, Kirim Semua, Tutup)
        val tataLetakHeader = LinearLayout(konteks).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val antrean = PengelolaBrankas.dapatkanDaftarAntreanSaatIni()

        val teksJudul = TextView(konteks).apply {
            text = "Brankas (${antrean.size} Foto)"
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tataLetakHeader.addView(teksJudul)

        // Tombol Pilih Beberapa / Batal Pilih
        val tombolPilihBeberapa = TextView(konteks).apply {
            text = if (modePilihBeberapa) "Batal Pilih" else "Pilih Beberapa"
            setTextColor(if (modePilihBeberapa) Color.parseColor("#FCA5A5") else Color.parseColor("#93C5FD"))
            textSize = 11f
            val pX = dpKePx(7)
            val pY = dpKePx(4)
            setPadding(pX, pY, pX, pY)
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(6).toFloat()
                setColor(if (modePilihBeberapa) Color.parseColor("#7F1D1D") else Color.parseColor("#1E3A8A"))
            }
            background = latar
            setOnClickListener {
                aturUlangTimerInaktivitas()
                modePilihBeberapa = !modePilihBeberapa
                kumpulanJalurTerpilih.clear()
                bukaLaciAntrean(konteks, perbesarPenuh = laciModePenuh)
            }
        }
        tataLetakHeader.addView(tombolPilihBeberapa)

        val pemisahPilih = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(5), 1) }
        tataLetakHeader.addView(pemisahPilih)

        // Tombol Salin Terpilih (jika modePilihBeberapa aktif)
        if (modePilihBeberapa) {
            val tombolSalinTerpilih = TextView(konteks).apply {
                val jumlah = kumpulanJalurTerpilih.size
                text = if (jumlah > 0) "Salin ($jumlah)" else "Salin"
                setTextColor(Color.WHITE)
                textSize = 11f
                val pX = dpKePx(7)
                val pY = dpKePx(4)
                setPadding(pX, pY, pX, pY)
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpKePx(6).toFloat()
                    setColor(if (jumlah > 0) Color.parseColor("#2563EB") else Color.parseColor("#4B5563"))
                }
                background = latar
                setOnClickListener {
                    aturUlangTimerInaktivitas()
                    if (kumpulanJalurTerpilih.isEmpty()) {
                        Toast.makeText(konteks, "Pilih minimal 1 gambar terlebih dahulu", Toast.LENGTH_SHORT).show()
                    } else {
                        PengelolaBrankas.salinBanyakGambarKePapanKlip(konteks, kumpulanJalurTerpilih.toList(), tampilkanNotifikasi = true)
                        modePilihBeberapa = false
                        kumpulanJalurTerpilih.clear()
                        bukaLaciAntrean(konteks, perbesarPenuh = laciModePenuh)
                    }
                }
            }
            tataLetakHeader.addView(tombolSalinTerpilih)

            val pemisahSalin = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(5), 1) }
            tataLetakHeader.addView(pemisahSalin)
        }

        // Tombol Kirim Semua
        val tombolKirimSemua = TextView(konteks).apply {
            text = "Kirim Semua"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 11f
            val pX = dpKePx(7)
            val pY = dpKePx(4)
            setPadding(pX, pY, pX, pY)
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(6).toFloat()
                setColor(Color.parseColor("#1F3D2B"))
            }
            background = latar
            setOnClickListener {
                PengelolaBrankas.bagikanSemuaAntrean(konteks)
            }
        }
        tataLetakHeader.addView(tombolKirimSemua)

        val pemisah2 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(5), 1) }
        tataLetakHeader.addView(pemisah2)

        val tombolTutup = TextView(konteks).apply {
            text = "✕"
            setTextColor(Color.GRAY)
            textSize = 16f
            val p = dpKePx(4)
            setPadding(p, p, p, p)
            setOnClickListener { tutupLaciAntrean() }
        }
        tataLetakHeader.addView(tombolTutup)
        tataLetakLaci.addView(tataLetakHeader)

        // [FITUR: Konten Gambar (Scrollable)] - Jika mode penuh, tampilkan scroll vertikal multi-baris; jika compact, horizontal
        if (antrean.isEmpty()) {
            val teksKosong = TextView(konteks).apply {
                text = "Belum ada screenshot. Ambil tangkapan layar untuk menguji."
                setTextColor(Color.LTGRAY)
                textSize = 12f
                val p = dpKePx(24)
                setPadding(p, p, p, p)
                gravity = Gravity.CENTER
            }
            tataLetakLaci.addView(teksKosong)
        } else if (laciModePenuh) {
            // MODE PENUH (Tarik ke Atas): ScrollView vertikal dengan banyak baris foto
            val penggulirVertikal = ScrollView(konteks).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { topMargin = dpKePx(8) }
            }

            val wadahGrid = LinearLayout(konteks).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Bagi antrean menjadi baris-baris berisi 4 thumbnail per baris
            val ukuranPerBaris = 4
            for (i in antrean.indices step ukuranPerBaris) {
                val baris = LinearLayout(konteks).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dpKePx(8) }
                }

                for (j in 0 until ukuranPerBaris) {
                    val indeks = i + j
                    if (indeks < antrean.size) {
                        val butir = antrean[indeks]
                        val kartu = buatKartuThumbnail(konteks, butir, dpKePx(74), dpKePx(74))
                        baris.addView(kartu)
                    } else {
                        // Spacer pengisi sisa baris
                        val spacer = View(konteks).apply {
                            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        }
                        baris.addView(spacer)
                    }
                }
                wadahGrid.addView(baris)
            }

            penggulirVertikal.addView(wadahGrid)
            tataLetakLaci.addView(penggulirVertikal)
        } else {
            // MODE COMPACT: HorizontalScrollView
            val penggulirHorizontal = HorizontalScrollView(konteks).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpKePx(8) }
            }

            val wadahThumbnail = LinearLayout(konteks).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            for (butir in antrean) {
                val kartu = buatKartuThumbnail(konteks, butir, dpKePx(80), dpKePx(80))
                wadahThumbnail.addView(kartu)
            }

            penggulirHorizontal.addView(wadahThumbnail)
            tataLetakLaci.addView(penggulirHorizontal)
        }

        tampilanLaciAntrean = tataLetakLaci
        try {
            wm.addView(tataLetakLaci, parameterLaci)
            laciSedangTerbuka = true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memunculkan laci antrean: ${e.message}")
        }
    }

    // [FITUR: Pembuat Kartu Thumbnail Interaktif] - Checkbox pojok kiri atas (mode pilih), klik 2 kali salin, tekan lama menu pop up simpan & edit
    private fun buatKartuThumbnail(
        konteks: Context,
        butir: ButirScreenshot,
        lebarPx: Int,
        tinggiPx: Int
    ): View {
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val wadahUtama = LinearLayout(konteks).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val m = dpKePx(4)
            setPadding(m, m, m, m)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Wadah Gambar + Checkbox Bertumpuk (FrameLayout)
        val wadahGambarRelatif = FrameLayout(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(lebarPx, tinggiPx)
        }

        val terpilihSaatIni = kumpulanJalurTerpilih.contains(butir.jalurBerkas)

        val gambar = ImageView(konteks).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val sudut = GradientDrawable().apply {
                cornerRadius = dpKePx(8).toFloat()
                if (terpilihSaatIni) {
                    setStroke(dpKePx(2), Color.parseColor("#38BDF8"))
                }
            }
            clipToOutline = true
            background = sudut

            try {
                val opsi = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bitmap = BitmapFactory.decodeFile(butir.jalurBerkas, opsi)
                setImageBitmap(bitmap)
            } catch (_: Exception) {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }
        wadahGambarRelatif.addView(gambar)

        // [FITUR: Checkbox di Pojok Kiri Atas Gambar] - Hanya tampil saat modePilihBeberapa aktif, default non-aktif
        if (modePilihBeberapa) {
            val tampilanCheckbox = TextView(konteks).apply {
                val ukuran = dpKePx(22)
                layoutParams = FrameLayout.LayoutParams(ukuran, ukuran).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val margin = dpKePx(4)
                    setMargins(margin, margin, 0, 0)
                }
                gravity = Gravity.CENTER
                textSize = 12f
                val latarCentang = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (terpilihSaatIni) {
                        setColor(Color.parseColor("#10B981"))
                        setStroke(dpKePx(1), Color.WHITE)
                    } else {
                        setColor(Color.parseColor("#80000000"))
                        setStroke(dpKePx(2), Color.WHITE)
                    }
                }
                background = latarCentang
                text = if (terpilihSaatIni) "✓" else ""
                setTextColor(Color.WHITE)
            }
            wadahGambarRelatif.addView(tampilanCheckbox)
        }

        // [FITUR: Deteksi Double Tap (Pratinjau Jelas + Pilih Teks Google Lens) & Long Press (Pop Up Simpan/Edit)]
        val detektorSentuh = GestureDetector(konteks, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            // [FITUR: Klik 2 Kali (Double Tap) -> Perlihatkan Foto dengan Jelas & Pilih Teks]
            override fun onDoubleTap(e: MotionEvent): Boolean {
                aturUlangTimerInaktivitas()
                tampilkanPratinjauFotoJelas(konteks, butir.jalurBerkas)
                return true
            }

            // [FITUR: Tekan Lama (Long Press) -> Pop Up Simpan ke Galeri & Edit]
            override fun onLongPress(e: MotionEvent) {
                aturUlangTimerInaktivitas()
                tampilkanDialogOpsiFoto(konteks, butir.jalurBerkas)
            }

            // [FITUR: Klik 1 Kali]
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                aturUlangTimerInaktivitas()
                if (modePilihBeberapa) {
                    if (kumpulanJalurTerpilih.contains(butir.jalurBerkas)) {
                        kumpulanJalurTerpilih.remove(butir.jalurBerkas)
                    } else {
                        kumpulanJalurTerpilih.add(butir.jalurBerkas)
                    }
                    bukaLaciAntrean(konteks, perbesarPenuh = laciModePenuh)
                }
                return true
            }
        })

        wadahGambarRelatif.setOnTouchListener { _, event ->
            detektorSentuh.onTouchEvent(event)
        }

        wadahUtama.addView(wadahGambarRelatif)

        // Label teks "Salin" di bawah gambar
        val teksSalin = TextView(konteks).apply {
            text = if (modePilihBeberapa) {
                if (terpilihSaatIni) "Terpilih ✓" else "Pilih"
            } else {
                "Salin"
            }
            setTextColor(if (terpilihSaatIni) Color.parseColor("#34D399") else Color.parseColor("#93C5FD"))
            textSize = 10f
            val topM = dpKePx(2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = topM }
            setOnClickListener {
                aturUlangTimerInaktivitas()
                if (modePilihBeberapa) {
                    if (kumpulanJalurTerpilih.contains(butir.jalurBerkas)) {
                        kumpulanJalurTerpilih.remove(butir.jalurBerkas)
                    } else {
                        kumpulanJalurTerpilih.add(butir.jalurBerkas)
                    }
                    bukaLaciAntrean(konteks, perbesarPenuh = laciModePenuh)
                } else {
                    PengelolaBrankas.salinGambarKePapanKlip(konteks, butir.jalurBerkas, tampilkanNotifikasi = false)
                    Toast.makeText(konteks, "Foto berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        wadahUtama.addView(teksSalin)

        return wadahUtama
    }

    private var tampilanPratinjauJelas: View? = null

    // [FITUR: Dialog Opsi Foto saat Tekan Lama] - Menampilkan dialog Simpan ke Galeri dan Edit Gambar tanpa arahkan ke aplikasi
    fun tampilkanDialogOpsiFoto(konteks: Context, jalurBerkas: String) {
        penanganUtama.post {
            val builder = android.app.AlertDialog.Builder(konteks, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            builder.setTitle("Opsi Foto")
            builder.setItems(arrayOf("Simpan ke Galeri", "Edit (Melayang Tanpa Buka Aplikasi)")) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> {
                        PengelolaBrankas.simpanGambarKeGaleriPublik(konteks, jalurBerkas)
                    }
                    1 -> {
                        // Buka editor melayang langsung di atas layar tanpa diarahkan ke aplikasi
                        JendelaEditorMelayang.bukaEditor(konteks, jalurBerkas)
                    }
                }
            }
            val dialog = builder.create()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            try {
                dialog.show()
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memunculkan dialog opsi: ${e.message}")
            }
        }
    }

    // [FITUR: Pratinjau Foto Jelas & Pilih Teks Google Lens] - Tekan 2x memunculkan foto resolusi penuh + tombol Pilih Teks OCR
    fun tampilkanPratinjauFotoJelas(konteks: Context, jalurBerkas: String) {
        penanganUtama.post {
            val wm = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            tutupPratinjauFotoJelas()

            val dpKePx = { dp: Int ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
            }

            val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val parameterPratinjau = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                tipeWindow,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            val wadahPratinjau = LinearLayout(konteks).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F5000000")) // Latar hitam pekat transparan
            }

            // Bilah Atas Pratinjau
            val bilahAtas = LinearLayout(konteks).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dpKePx(12)
                setPadding(p, p, p, p)
                setBackgroundColor(Color.parseColor("#E6111827"))
            }

            val tombolTutup = TextView(konteks).apply {
                text = "✕"
                textSize = 20f
                setTextColor(Color.WHITE)
                val p = dpKePx(6)
                setPadding(p, p, p, p)
                setOnClickListener { tutupPratinjauFotoJelas() }
            }
            bilahAtas.addView(tombolTutup)

            val teksJudul = TextView(konteks).apply {
                text = "Pratinjau Jelas"
                textSize = 14f
                setTextColor(Color.WHITE)
                val mLeft = dpKePx(10)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = mLeft
                }
            }
            bilahAtas.addView(teksJudul)

            // Tombol "🔍 Pilih Teks" (Google Lens style OCR)
            val tombolPilihTeks = TextView(konteks).apply {
                text = "🔍 Pilih Teks"
                textSize = 12f
                setTextColor(Color.WHITE)
                val pX = dpKePx(12)
                val pY = dpKePx(6)
                setPadding(pX, pY, pX, pY)
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpKePx(16).toFloat()
                    setColor(Color.parseColor("#2563EB"))
                }
                background = latar
                setOnClickListener {
                    Toast.makeText(konteks, "Mendeteksi teks dengan Google Lens...", Toast.LENGTH_SHORT).show()
                    PengenalTeksOcr.deteksiTeksDariBerkas(konteks, jalurBerkas) { sukses, teksLengkap, _ ->
                        if (sukses && teksLengkap.isNotBlank()) {
                            tampilkanDialogTeksTerdeteksi(konteks, teksLengkap)
                        } else {
                            Toast.makeText(konteks, "Tidak ada teks terdeteksi pada gambar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            bilahAtas.addView(tombolPilihTeks)

            val pBilah = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
            bilahAtas.addView(pBilah)

            // Tombol Salin Gambar
            val tombolSalin = TextView(konteks).apply {
                text = "📋 Salin"
                textSize = 12f
                setTextColor(Color.WHITE)
                val pX = dpKePx(10)
                val pY = dpKePx(6)
                setPadding(pX, pY, pX, pY)
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpKePx(16).toFloat()
                    setColor(Color.parseColor("#374151"))
                }
                background = latar
                setOnClickListener {
                    PengelolaBrankas.salinGambarKePapanKlip(konteks, jalurBerkas, tampilkanNotifikasi = true)
                }
            }
            bilahAtas.addView(tombolSalin)
            wadahPratinjau.addView(bilahAtas)

            // Tampilan Gambar Resolusi Penuh
            val wadahGambar = FrameLayout(konteks).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            val tampilanGambarJelas = ImageView(konteks).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                try {
                    val opsi = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val bitmap = BitmapFactory.decodeFile(jalurBerkas, opsi)
                    setImageBitmap(bitmap)
                } catch (_: Exception) {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
            wadahGambar.addView(tampilanGambarJelas)
            wadahPratinjau.addView(wadahGambar)

            tampilanPratinjauJelas = wadahPratinjau
            try {
                wm.addView(wadahPratinjau, parameterPratinjau)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memunculkan pratinjau jelas: ${e.message}")
            }
        }
    }

    // [FITUR: Dialog Teks Terdeteksi ala Google Lens] - Menampilkan teks yang bisa diseleksi per huruf/kata dan disalin
    private fun tampilkanDialogTeksTerdeteksi(konteks: Context, teksLengkap: String) {
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dialogLayout = LinearLayout(konteks).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpKePx(16)
            setPadding(p, p, p, p)
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(16).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dpKePx(1), Color.parseColor("#38BDF8"))
            }
            background = latar
        }

        val header = LinearLayout(konteks).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val judulTeks = TextView(konteks).apply {
            text = "🔍 Teks Terdeteksi (Pilih & Salin)"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(judulTeks)

        val tombolTutupDialog = TextView(konteks).apply {
            text = "✕"
            setTextColor(Color.GRAY)
            textSize = 18f
            val p = dpKePx(4)
            setPadding(p, p, p, p)
        }
        header.addView(tombolTutupDialog)
        dialogLayout.addView(header)

        val scrollTeks = ScrollView(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpKePx(200)
            ).apply {
                topMargin = dpKePx(12)
                bottomMargin = dpKePx(12)
            }
        }

        // TextView dengan setTextIsSelectable(true) sehingga pengguna bisa bebas memilih huruf/kata!
        val teksHasilOcr = TextView(konteks).apply {
            text = teksLengkap
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13f
            setTextIsSelectable(true)
            val p = dpKePx(8)
            setPadding(p, p, p, p)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(8).toFloat()
                setColor(Color.parseColor("#0F172A"))
            }
            background = bg
        }
        scrollTeks.addView(teksHasilOcr)
        dialogLayout.addView(scrollTeks)

        // Tombol Salin Semua Teks
        val tombolSalinSemua = TextView(konteks).apply {
            text = "📋 Salin Semua Teks"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            val pY = dpKePx(10)
            setPadding(0, pY, 0, pY)
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(8).toFloat()
                setColor(Color.parseColor("#10B981"))
            }
            background = latar
            setOnClickListener {
                val manajerPapanKlip = konteks.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val klip = android.content.ClipData.newPlainText("Teks Terdeteksi", teksLengkap)
                manajerPapanKlip.setPrimaryClip(klip)
                Toast.makeText(konteks, "Seluruh teks berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
            }
        }
        dialogLayout.addView(tombolSalinSemua)

        val wm = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            dpKePx(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipeWindow,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        tombolTutupDialog.setOnClickListener {
            try { wm.removeView(dialogLayout) } catch (_: Exception) {}
        }

        try {
            wm.addView(dialogLayout, params)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memunculkan dialog OCR: ${e.message}")
        }
    }

    fun tutupPratinjauFotoJelas() {
        penanganUtama.post {
            val wm = pengelolaJendela ?: return@post
            val tampilan = tampilanPratinjauJelas ?: return@post
            try {
                wm.removeView(tampilan)
            } catch (_: Exception) {}
            tampilanPratinjauJelas = null
        }
    }

    // [FITUR: Buka Layar Editor dari Overlay] - Cadangan untuk membuka editor
    fun bukaLayarEditorDariOverlay(konteks: Context, jalurBerkas: String) {
        JendelaEditorMelayang.bukaEditor(konteks, jalurBerkas)
    }

    // [FITUR: Tutup Laci Antrean] - Menutup panel bottom drawer
    private fun tutupLaciAntrean() {
        if (!laciSedangTerbuka) return
        val wm = pengelolaJendela ?: return
        val laci = tampilanLaciAntrean ?: return

        try {
            wm.removeView(laci)
        } catch (_: Exception) {
        } finally {
            tampilanLaciAntrean = null
            laciSedangTerbuka = false
            aturUlangTimerInaktivitas()
        }
    }

    // [FITUR: Perbarui Jumlah Antrean] - Mengupdate lencana angka
    fun perbaruiTampilanAntrean(antrean: List<ButirScreenshot>) {
        penanganUtama.post {
            val gelembung = tampilanAktif as? FrameLayout ?: return@post
            for (i in 0 until gelembung.childCount) {
                val anak = gelembung.getChildAt(i)
                if (anak is TextView) {
                    anak.text = antrean.size.toString()
                    break
                }
            }

            if (laciSedangTerbuka) {
                konteksAplikasi?.let { k ->
                    bukaLaciAntrean(k, perbesarPenuh = laciModePenuh)
                }
            }
        }
    }

    // [FITUR: Bersihkan dan Hapus Seluruh Tampilan] - Melepas semua window saat layanan dihentikan
    fun sembunyikanGelembung() {
        penanganUtama.post {
            penanganUtama.removeCallbacks(tugasOtomatisSembunyi)
            tutupLaciAntrean()
            val wm = pengelolaJendela
            val tampilan = tampilanAktif
            if (wm != null && tampilan != null) {
                try { wm.removeView(tampilan) } catch (_: Exception) {}
            }
            tampilanAktif = null
            pengelolaJendela = null
            sedangDalamModeBilahSamping = false
        }
    }
}
