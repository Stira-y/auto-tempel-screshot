package com.example.apk_auto_tempel_srcht

import android.content.Context
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
import java.io.File

// [FITUR: Panel Melayang Mini ala Game Turbo Redmi & Bypass Clipboard Android 16] - Bilah samping abu-abu auto-hide 1 menit, gelembung bundar melayang, laci antrean berkas yang bisa ditarik ke atas (expandable scroll), dan bypass window overlay
object JendelaGelembungMelayang {

    private const val TAG = "JendelaGelembung"
    private const val DURASI_AUTO_HIDE_MS = 60_000L // 1 Menit auto-hide

    private var pengelolaJendela: WindowManager? = null
    private var tampilanAktif: View? = null
    private var tampilanLaciAntrean: View? = null
    private var konteksAplikasi: Context? = null
    private val penanganUtama = Handler(Looper.getMainLooper())

    private var laciSedangTerbuka = false
    private var laciModePenuh = false
    private var sedangDalamModeBilahSamping = false

    private var posisiGelembungX = 0
    private var posisiGelembungY = 300

    // [FITUR: Timer Otomatis Sembunyi 1 Menit]
    private val tugasOtomatisSembunyi = Runnable {
        if (!laciSedangTerbuka && !sedangDalamModeBilahSamping && tampilanAktif != null) {
            konteksAplikasi?.let { k ->
                ubahKeModeBilahSamping(k)
            }
        }
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
        val wm = pengelolaJendela ?: return
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

        // [FITUR: Pegangan Geser ke Atas (Drag Handle)]
        val peganganGeser = LinearLayout(konteks).apply {
            gravity = Gravity.CENTER
            val pBawah = dpKePx(6)
            setPadding(0, 0, 0, pBawah)
        }
        val barGrip = View(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(dpKePx(48), dpKePx(4))
            val latarGrip = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(2).toFloat()
                setColor(Color.parseColor("#9CA3AF"))
            }
            background = latarGrip
        }
        peganganGeser.addView(barGrip)

        // Sentuh pegangan untuk toggle perbesar / perkecil laci
        peganganGeser.setOnClickListener {
            bukaLaciAntrean(konteks, perbesarPenuh = !laciModePenuh)
        }
        tataLetakLaci.addView(peganganGeser)

        // Header Laci (Judul, Tombol Mode Ukuran, Tombol Kirim Semua, Tombol Tutup)
        val tataLetakHeader = LinearLayout(konteks).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val antrean = PengelolaBrankas.dapatkanDaftarAntreanSaatIni()

        val teksJudul = TextView(konteks).apply {
            text = "Brankas (${antrean.size} Foto)"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tataLetakHeader.addView(teksJudul)

        // Tombol Tarik ke Atas / Perkecil
        val tombolTarikAtas = TextView(konteks).apply {
            text = if (laciModePenuh) "Perkecil ▼" else "Tarik Penuh ▲"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            val pX = dpKePx(8)
            val pY = dpKePx(4)
            setPadding(pX, pY, pX, pY)
            setOnClickListener {
                bukaLaciAntrean(konteks, perbesarPenuh = !laciModePenuh)
            }
        }
        tataLetakHeader.addView(tombolTarikAtas)

        val pemisah1 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(6), 1) }
        tataLetakHeader.addView(pemisah1)

        val tombolKirimSemua = TextView(konteks).apply {
            text = "Kirim Semua"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 12f
            val pX = dpKePx(8)
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

        val pemisah2 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(6), 1) }
        tataLetakHeader.addView(pemisah2)

        val tombolTutup = TextView(konteks).apply {
            text = "✕"
            setTextColor(Color.GRAY)
            textSize = 16f
            val p = dpKePx(6)
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

    // [FITUR: Pembuat Kartu Thumbnail] - Komponen item gambar interaktif
    private fun buatKartuThumbnail(
        konteks: Context,
        butir: ButirScreenshot,
        lebarPx: Int,
        tinggiPx: Int
    ): View {
        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
        }

        val wadah = LinearLayout(konteks).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val m = dpKePx(4)
            setPadding(m, m, m, m)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val gambar = ImageView(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(lebarPx, tinggiPx)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val sudut = GradientDrawable().apply {
                cornerRadius = dpKePx(8).toFloat()
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

            setOnClickListener {
                PengelolaBrankas.salinGambarKePapanKlip(konteks, butir.jalurBerkas, tampilkanNotifikasi = true)
                aturUlangTimerInaktivitas()
            }
        }
        wadah.addView(gambar)

        val teksSalin = TextView(konteks).apply {
            text = "Salin"
            setTextColor(Color.parseColor("#93C5FD"))
            textSize = 10f
            val topM = dpKePx(2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = topM }
        }
        wadah.addView(teksSalin)

        return wadah
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
