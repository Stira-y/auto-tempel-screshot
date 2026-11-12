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
import android.widget.TextView
import android.widget.Toast
import java.io.File

// [FITUR: Panel Melayang Mini & Bypass Clipboard Android 16] - Menyediakan bubble mengambang di tepi layar untuk akses cepat antrean gambar, serta overlay 1x1 pixel untuk mematuhi aturan window focus Android 16 saat menulis ke clipboard
object JendelaGelembungMelayang {

    private const val TAG = "JendelaGelembung"
    private var pengelolaJendela: WindowManager? = null
    private var tampilanGelembung: View? = null
    private var tampilanLaciAntrean: View? = null
    private var parameterGelembung: WindowManager.LayoutParams? = null
    private var parameterLaci: WindowManager.LayoutParams? = null
    private var laciSedangTerbuka = false
    private val penanganUtama = Handler(Looper.getMainLooper())
    private var konteksAplikasi: Context? = null

    // [FITUR: Bypass Window Focus Android 16] - Memunculkan overlay transparan 1x1 pixel sesaat agar clipboard sistem diizinkan menerima data di latar belakang
    fun tampilkanOverlayFokusSesaat(konteks: Context, aksiSetelahFokus: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(konteks)) {
            // Jika izin overlay belum diizinkan, jalankan aksi langsung
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

                // Eksekusi penyalinan clipboard setelah window terpasang
                penanganUtama.postDelayed({
                    aksiSetelahFokus.invoke()
                    // Hapus overlay 1x1 pixel setelah selesai
                    penanganUtama.postDelayed({
                        try {
                            wm.removeView(tampilanFokus)
                        } catch (e: Exception) {
                            Log.w(TAG, "Overlay 1x1 sudah dihapus: ${e.message}")
                        }
                    }, 200)
                }, 50)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membuat overlay 1x1: ${e.message}")
                aksiSetelahFokus.invoke()
            }
        }
    }

    // [FITUR: Tampilkan Gelembung Melayang] - Memunculkan tombol gelembung melayang di tepi layar
    fun tampilkanGelembung(konteks: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(konteks)) {
            Log.w(TAG, "Izin overlay belum diberikan, gelembung tidak dapat ditampilkan")
            return
        }

        konteksAplikasi = konteks.applicationContext
        penanganUtama.post {
            if (tampilanGelembung != null) return@post

            pengelolaJendela = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val dpKePx = { dp: Int ->
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp.toFloat(),
                    konteks.resources.displayMetrics
                ).toInt()
            }

            parameterGelembung = WindowManager.LayoutParams(
                dpKePx(56), dpKePx(56),
                tipeWindow,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpKePx(16)
                y = dpKePx(200)
            }

            // Desain Visual Bubble
            val wadahGelembung = FrameLayout(konteks).apply {
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E88E5"))
                    setStroke(dpKePx(2), Color.WHITE)
                }
                background = latar
                elevation = 10f
            }

            val ikonKamera = ImageView(konteks).apply {
                setImageResource(android.R.drawable.ic_menu_camera)
                setColorFilter(Color.WHITE)
                val padding = dpKePx(12)
                setPadding(padding, padding, padding, padding)
            }
            wadahGelembung.addView(ikonKamera)

            // Lencana Angka Jumlah Gambar
            val teksJumlah = TextView(konteks).apply {
                id = View.generateViewId()
                textSize = 10f
                setTextColor(Color.WHITE)
                val latarLencana = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#E53935"))
                }
                background = latarLencana
                gravity = Gravity.CENTER
                val ukuran = dpKePx(20)
                layoutParams = FrameLayout.LayoutParams(ukuran, ukuran).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
                text = "0"
            }
            wadahGelembung.addView(teksJumlah)

            // [FITUR: Gesture Geser & Ketuk Gelembung]
            wadahGelembung.setOnTouchListener(object : View.OnTouchListener {
                private var koordinatAwalX = 0
                private var koordinatAwalY = 0
                private var sentuhanAwalX = 0f
                private var sentuhanAwalY = 0f
                private var waktuSentuhMulai = 0L

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val param = parameterGelembung ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            koordinatAwalX = param.x
                            koordinatAwalY = param.y
                            sentuhanAwalX = event.rawX
                            sentuhanAwalY = event.rawY
                            waktuSentuhMulai = System.currentTimeMillis()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            param.x = koordinatAwalX + (event.rawX - sentuhanAwalX).toInt()
                            param.y = koordinatAwalY + (event.rawY - sentuhanAwalY).toInt()
                            try {
                                pengelolaJendela?.updateViewLayout(wadahGelembung, param)
                            } catch (e: Exception) {
                                Log.w(TAG, "Gagal memperbarui posisi gelembung: ${e.message}")
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val durasiSentuh = System.currentTimeMillis() - waktuSentuhMulai
                            val perpindahan = Math.hypot(
                                (event.rawX - sentuhanAwalX).toDouble(),
                                (event.rawY - sentuhanAwalY).toDouble()
                            )
                            // Jika perpindahan kecil dan durasi singkat, anggap sebagai klik
                            if (perpindahan < 15 && durasiSentuh < 300) {
                                sakelarTampilanLaci(konteks)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            tampilanGelembung = wadahGelembung
            try {
                pengelolaJendela?.addView(wadahGelembung, parameterGelembung)
                perbaruiTampilanAntrean(PengelolaBrankas.dapatkanDaftarAntreanSaatIni())
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menambahkan gelembung ke layar: ${e.message}")
            }
        }
    }

    // [FITUR: Sakelar Buka/Tutup Laci Antrean] - Membuka atau menutup panel daftar thumbnail screenshot
    private fun sakelarTampilanLaci(konteks: Context) {
        if (laciSedangTerbuka) {
            tutupLaciAntrean()
        } else {
            bukaLaciAntrean(konteks)
        }
    }

    // [FITUR: Buka Laci Antrean] - Membuka panel drawer mini dengan thumbnail hingga 10 gambar
    private fun bukaLaciAntrean(konteks: Context) {
        if (laciSedangTerbuka) return
        val wm = pengelolaJendela ?: return

        val dpKePx = { dp: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                konteks.resources.displayMetrics
            ).toInt()
        }

        val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        parameterLaci = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dpKePx(170),
            tipeWindow,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dpKePx(16)
        }

        // Wadah Utama Laci
        val tataLetakLaci = LinearLayout(konteks).apply {
            orientation = LinearLayout.VERTICAL
            val latar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(16).toFloat()
                setColor(Color.parseColor("#EE1F2937"))
                setStroke(dpKePx(1), Color.parseColor("#374151"))
            }
            background = latar
            val p = dpKePx(12)
            setPadding(p, p, p, p)
        }

        // Header Laci (Judul, Tombol Kirim Semua, Tombol Tutup)
        val tataLetakHeader = LinearLayout(konteks).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val teksJudul = TextView(konteks).apply {
            text = "Antrean Brankas (${PengelolaBrankas.dapatkanDaftarAntreanSaatIni().size}/10)"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tataLetakHeader.addView(teksJudul)

        val tombolKirimSemua = TextView(konteks).apply {
            text = "Kirim Semua"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 13f
            val pX = dpKePx(8)
            val pY = dpKePx(4)
            setPadding(pX, pY, pX, pY)
            val latarTombol = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpKePx(6).toFloat()
                setColor(Color.parseColor("#1F3D2B"))
            }
            background = latarTombol
            setOnClickListener {
                PengelolaBrankas.bagikanSemuaAntrean(konteks)
            }
        }
        tataLetakHeader.addView(tombolKirimSemua)

        val pemisahKecil = View(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1)
        }
        tataLetakHeader.addView(pemisahKecil)

        val tombolTutup = TextView(konteks).apply {
            text = "✕"
            setTextColor(Color.GRAY)
            textSize = 16f
            val p = dpKePx(6)
            setPadding(p, p, p, p)
            setOnClickListener {
                tutupLaciAntrean()
            }
        }
        tataLetakHeader.addView(tombolTutup)
        tataLetakLaci.addView(tataLetakHeader)

        // Daftar Gulir Horizontal Thumbnail
        val penggulirHorizontal = HorizontalScrollView(konteks).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpKePx(8)
            }
        }

        val wadahThumbnail = LinearLayout(konteks).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val antrean = PengelolaBrankas.dapatkanDaftarAntreanSaatIni()
        if (antrean.isEmpty()) {
            val teksKosong = TextView(konteks).apply {
                text = "Belum ada screenshot baru. Tangkap layar untuk menguji."
                setTextColor(Color.LTGRAY)
                textSize = 12f
                val p = dpKePx(16)
                setPadding(p, p, p, p)
            }
            wadahThumbnail.addView(teksKosong)
        } else {
            for (butir in antrean) {
                val wadahKartu = LinearLayout(konteks).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val marginKanan = dpKePx(8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        rightMargin = marginKanan
                    }
                }

                val gambarThumbnail = ImageView(konteks).apply {
                    val lebar = dpKePx(70)
                    val tinggi = dpKePx(70)
                    layoutParams = LinearLayout.LayoutParams(lebar, tinggi)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val sudut = GradientDrawable().apply {
                        cornerRadius = dpKePx(8).toFloat()
                    }
                    clipToOutline = true
                    background = sudut

                    // Muat bitmap secara aman
                    try {
                        val opsi = BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        val bitmap = BitmapFactory.decodeFile(butir.jalurBerkas, opsi)
                        setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        setImageResource(android.R.drawable.ic_menu_gallery)
                    }

                    // [FITUR: Salin Cepat saat Thumbnail Diketuk]
                    setOnClickListener {
                        PengelolaBrankas.salinGambarKePapanKlip(konteks, butir.jalurBerkas, tampilkanNotifikasi = true)
                    }
                }
                wadahKartu.addView(gambarThumbnail)

                val labelSalin = TextView(konteks).apply {
                    text = "Ketuk: Salin"
                    setTextColor(Color.parseColor("#93C5FD"))
                    textSize = 10f
                    val topM = dpKePx(2)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = topM
                    }
                }
                wadahKartu.addView(labelSalin)

                wadahThumbnail.addView(wadahKartu)
            }
        }

        penggulirHorizontal.addView(wadahThumbnail)
        tataLetakLaci.addView(penggulirHorizontal)

        tampilanLaciAntrean = tataLetakLaci
        try {
            wm.addView(tataLetakLaci, parameterLaci)
            laciSedangTerbuka = true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memunculkan laci antrean: ${e.message}")
        }
    }

    // [FITUR: Tutup Laci Antrean] - Menghilangkan tampilan drawer thumbnail
    private fun tutupLaciAntrean() {
        if (!laciSedangTerbuka) return
        val wm = pengelolaJendela ?: return
        val laci = tampilanLaciAntrean ?: return

        try {
            wm.removeView(laci)
        } catch (e: Exception) {
            Log.w(TAG, "Laci sudah tertutup: ${e.message}")
        } finally {
            tampilanLaciAntrean = null
            laciSedangTerbuka = false
        }
    }

    // [FITUR: Perbarui Jumlah Antrean pada Tampilan Melayang] - Memperbarui lencana angka dan konten drawer
    fun perbaruiTampilanAntrean(antrean: List<ButirScreenshot>) {
        penanganUtama.post {
            val gelembung = tampilanGelembung as? FrameLayout ?: return@post
            // Cari TextView lencana
            for (i in 0 until gelembung.childCount) {
                val anak = gelembung.getChildAt(i)
                if (anak is TextView) {
                    anak.text = antrean.size.toString()
                    break
                }
            }

            // Jika laci sedang aktif terbuka, segarkan tampilannya
            if (laciSedangTerbuka) {
                val k = konteksAplikasi
                if (k != null) {
                    tutupLaciAntrean()
                    bukaLaciAntrean(k)
                }
            }
        }
    }

    // [FITUR: Sembunyikan dan Hapus Gelembung Melayang] - Menutup semua window saat layanan berhenti
    fun sembunyikanGelembung() {
        penanganUtama.post {
            tutupLaciAntrean()
            val wm = pengelolaJendela
            val gelembung = tampilanGelembung
            if (wm != null && gelembung != null) {
                try {
                    wm.removeView(gelembung)
                } catch (e: Exception) {
                    Log.w(TAG, "Gelembung sudah dihapus: ${e.message}")
                }
            }
            tampilanGelembung = null
            pengelolaJendela = null
        }
    }
}

