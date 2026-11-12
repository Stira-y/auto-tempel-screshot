package com.example.apk_auto_tempel_srcht

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File

// [FITUR: Jendela Editor Melayang 100% Offline Tanpa Buka Aplikasi] - Editor popup melayang dengan crop, coret-coret, rotasi derajat, dan mirror cermin langsung di atas aplikasi yang sedang aktif
object JendelaEditorMelayang {

    private const val TAG = "JendelaEditorMelayang"
    private var pengelolaJendela: WindowManager? = null
    private var tampilanEditorAktif: View? = null
    private val penanganUtama = Handler(Looper.getMainLooper())

    enum class ModeAlat {
        POTONG,
        PUTAR_MIRROR,
        CORET
    }

    data class GarisCoretanNative(
        val jalur: Path,
        val warna: Int,
        val ketebalan: Float
    )

    // [FITUR: Buka Editor Melayang] - Memunculkan jendela editor fullscreen overlay
    fun bukaEditor(konteks: Context, jalurBerkas: String) {
        penanganUtama.post {
            val wm = konteks.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            pengelolaJendela = wm

            // Tutup editor lama jika ada
            tutupEditor()

            val berkas = File(jalurBerkas)
            if (!berkas.exists()) {
                Toast.makeText(konteks, "Berkas gambar tidak ditemukan!", Toast.LENGTH_SHORT).show()
                return@post
            }

            val dpKePx = { dp: Int ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), konteks.resources.displayMetrics).toInt()
            }

            val tipeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val parameterEditor = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                tipeWindow,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            // Muat Bitmap Asli
            val opsi = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmapAsli = BitmapFactory.decodeFile(jalurBerkas, opsi)
            if (bitmapAsli == null) {
                Toast.makeText(konteks, "Gagal memproses gambar!", Toast.LENGTH_SHORT).show()
                return@post
            }

            // State Editor
            var modeAktif = ModeAlat.PUTAR_MIRROR
            var rotasi90 = 0 // 0, 1, 2, 3
            var sudutDerajatHalus = 0f // -45f s/d +45f
            var cerminH = false
            var cerminV = false

            // State Coret
            val daftarCoretan = mutableListOf<GarisCoretanNative>()
            var coretanSaatIni: Path? = null
            var warnaKuas = Color.parseColor("#EF4444") // Merah
            var ketebalanKuas = dpKePx(4).toFloat()

            // State Crop (Rasio & Kotak Potong)
            var rasioPotong = "Bebas"
            var kotakCrop = RectF()

            // Wadah Utama
            val tataLetakUtama = LinearLayout(konteks).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F00F172A")) // Gelap pekat elegan
            }

            // Top Bar: Tombol Tutup, Judul, Tombol Undo, Tombol Simpan ke Galeri
            val bilahAtas = LinearLayout(konteks).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dpKePx(12)
                setPadding(p, p, p, p)
                setBackgroundColor(Color.parseColor("#1E293B"))
            }

            val tombolTutup = TextView(konteks).apply {
                text = "✕"
                textSize = 20f
                setTextColor(Color.WHITE)
                val p = dpKePx(6)
                setPadding(p, p, p, p)
                setOnClickListener { tutupEditor() }
            }
            bilahAtas.addView(tombolTutup)

            val teksJudul = TextView(konteks).apply {
                text = "Edit Foto (Melayang)"
                textSize = 15f
                setTextColor(Color.WHITE)
                val mLeft = dpKePx(12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = mLeft
                }
            }
            bilahAtas.addView(teksJudul)

            // Tombol Undo
            val tombolUndo = TextView(konteks).apply {
                text = "↶ Undo"
                textSize = 12f
                setTextColor(Color.parseColor("#93C5FD"))
                val pX = dpKePx(10)
                val pY = dpKePx(6)
                setPadding(pX, pY, pX, pY)
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpKePx(6).toFloat()
                    setColor(Color.parseColor("#1E3A8A"))
                }
                background = latar
            }
            bilahAtas.addView(tombolUndo)

            val pemisahSimpan = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
            bilahAtas.addView(pemisahSimpan)

            // Tombol Simpan ke Galeri
            val tombolSimpan = TextView(konteks).apply {
                text = "💾 Simpan ke Galeri"
                textSize = 12f
                setTextColor(Color.WHITE)
                val pX = dpKePx(12)
                val pY = dpKePx(6)
                setPadding(pX, pY, pX, pY)
                val latar = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpKePx(6).toFloat()
                    setColor(Color.parseColor("#10B981"))
                }
                background = latar
            }
            bilahAtas.addView(tombolSimpan)
            tataLetakUtama.addView(bilahAtas)

            // Kanvas Area Tengah
            val wadahKanvas = FrameLayout(konteks).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            // Tampilan Kanvas Interaktif Custom
            lateinit var tampilanKanvas: View
            tampilanKanvas = object : View(konteks) {
                private val kuasGambar = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                private val kuasCrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = dpKePx(2).toFloat()
                }
                private val kuasCropGelap = Paint().apply {
                    color = Color.parseColor("#99000000")
                    style = Paint.Style.FILL
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val lebarLayar = width.toFloat()
                    val tinggiLayar = height.toFloat()
                    if (lebarLayar <= 0 || tinggiLayar <= 0) return

                    val totalSudut = (rotasi90 * 90) + sudutDerajatHalus

                    canvas.save()
                    canvas.translate(lebarLayar / 2f, tinggiLayar / 2f)
                    canvas.rotate(totalSudut)
                    canvas.scale(if (cerminH) -1f else 1f, if (cerminV) -1f else 1f)

                    // Hitung skala fit
                    val skala = Math.min(
                        (lebarLayar * 0.85f) / bitmapAsli.width,
                        (tinggiLayar * 0.85f) / bitmapAsli.height
                    )
                    canvas.scale(skala, skala)
                    canvas.translate(-bitmapAsli.width / 2f, -bitmapAsli.height / 2f)

                    // Gambar Foto
                    canvas.drawBitmap(bitmapAsli, 0f, 0f, kuasGambar)

                    // Gambar Coretan
                    for (coretan in daftarCoretan) {
                        val kuas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = coretan.warna
                            strokeWidth = coretan.ketebalan / skala
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        canvas.drawPath(coretan.jalur, kuas)
                    }

                    coretanSaatIni?.let { path ->
                        val kuas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = warnaKuas
                            strokeWidth = ketebalanKuas / skala
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        canvas.drawPath(path, kuas)
                    }

                    canvas.restore()

                    // Gambar Garis Crop jika mode potong aktif
                    if (modeAktif == ModeAlat.POTONG) {
                        if (kotakCrop.isEmpty) {
                            val marginW = lebarLayar * 0.08f
                            val marginH = tinggiLayar * 0.08f
                            kotakCrop.set(marginW, marginH, lebarLayar - marginW, tinggiLayar - marginH)
                        }
                        // Shading luar crop
                        canvas.drawRect(0f, 0f, lebarLayar, kotakCrop.top, kuasCropGelap)
                        canvas.drawRect(0f, kotakCrop.bottom, lebarLayar, tinggiLayar, kuasCropGelap)
                        canvas.drawRect(0f, kotakCrop.top, kotakCrop.left, kotakCrop.bottom, kuasCropGelap)
                        canvas.drawRect(kotakCrop.right, kotakCrop.top, lebarLayar, kotakCrop.bottom, kuasCropGelap)
                        // Border kotak crop
                        canvas.drawRect(kotakCrop, kuasCrop)
                    }
                }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (modeAktif == ModeAlat.CORET) {
                        val lebarLayar = width.toFloat()
                        val tinggiLayar = height.toFloat()
                        val skala = Math.min(
                            (lebarLayar * 0.85f) / bitmapAsli.width,
                            (tinggiLayar * 0.85f) / bitmapAsli.height
                        )

                        // Konversi koordinat sentuh layar ke koordinat bitmap lokal
                        val matriksInvers = Matrix()
                        val matriksMaju = Matrix().apply {
                            postTranslate(lebarLayar / 2f, tinggiLayar / 2f)
                            postRotate((rotasi90 * 90) + sudutDerajatHalus)
                            postScale(if (cerminH) -1f else 1f, if (cerminV) -1f else 1f)
                            postScale(skala, skala)
                            postTranslate(-bitmapAsli.width / 2f, -bitmapAsli.height / 2f)
                        }
                        matriksMaju.invert(matriksInvers)

                        val titik = floatArrayOf(event.x, event.y)
                        matriksInvers.mapPoints(titik)
                        val titikX = titik[0]
                        val titikY = titik[1]

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                coretanSaatIni = Path().apply { moveTo(titikX, titikY) }
                                invalidate()
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                coretanSaatIni?.lineTo(titikX, titikY)
                                invalidate()
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                coretanSaatIni?.let {
                                    daftarCoretan.add(GarisCoretanNative(it, warnaKuas, ketebalanKuas))
                                }
                                coretanSaatIni = null
                                invalidate()
                                return true
                            }
                        }
                    } else if (modeAktif == ModeAlat.POTONG) {
                        // Drag kotak crop
                        when (event.action) {
                            MotionEvent.ACTION_MOVE -> {
                                kotakCrop.right = Math.max(kotakCrop.left + dpKePx(60), event.x)
                                kotakCrop.bottom = Math.max(kotakCrop.top + dpKePx(60), event.y)
                                invalidate()
                                return true
                            }
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }

            wadahKanvas.addView(tampilanKanvas)
            tataLetakUtama.addView(wadahKanvas)

            // Undo Handler
            tombolUndo.setOnClickListener {
                if (daftarCoretan.isNotEmpty()) {
                    daftarCoretan.removeAt(daftarCoretan.size - 1)
                    tampilanKanvas.invalidate()
                }
            }

            // Panel Pengaturan Sub-Alat
            val panelSubAlat = LinearLayout(konteks).apply {
                orientation = LinearLayout.VERTICAL
                val p = dpKePx(10)
                setPadding(p, p, p, p)
                setBackgroundColor(Color.parseColor("#1E293B"))
            }

            // Fungsi render ulang sub-panel bawah sesuai mode aktif
            fun perbaruiSubPanel() {
                panelSubAlat.removeAllViews()

                when (modeAktif) {
                    ModeAlat.PUTAR_MIRROR -> {
                        // Baris Aksi Cepat: +90°, Cermin H, Cermin V, Reset
                        val barisAksi = LinearLayout(konteks).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                        }

                        val tombolPutar90 = TextView(konteks).apply {
                            text = "+90° (${(rotasi90 * 90)}°)"
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            val pX = dpKePx(10)
                            val pY = dpKePx(6)
                            setPadding(pX, pY, pX, pY)
                            val latar = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dpKePx(6).toFloat()
                                setColor(Color.parseColor("#374151"))
                            }
                            background = latar
                            setOnClickListener {
                                rotasi90 = (rotasi90 + 1) % 4
                                text = "+90° (${(rotasi90 * 90)}°)"
                                tampilanKanvas.invalidate()
                            }
                        }
                        barisAksi.addView(tombolPutar90)

                        val p1 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
                        barisAksi.addView(p1)

                        val tombolCerminH = TextView(konteks).apply {
                            text = if (cerminH) "Cermin H [Aktif]" else "Cermin H"
                            setTextColor(if (cerminH) Color.parseColor("#38BDF8") else Color.WHITE)
                            textSize = 12f
                            val pX = dpKePx(10)
                            val pY = dpKePx(6)
                            setPadding(pX, pY, pX, pY)
                            val latar = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dpKePx(6).toFloat()
                                setColor(if (cerminH) Color.parseColor("#1E3A8A") else Color.parseColor("#374151"))
                            }
                            background = latar
                            setOnClickListener {
                                cerminH = !cerminH
                                perbaruiSubPanel()
                                tampilanKanvas.invalidate()
                            }
                        }
                        barisAksi.addView(tombolCerminH)

                        val p2 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
                        barisAksi.addView(p2)

                        val tombolCerminV = TextView(konteks).apply {
                            text = if (cerminV) "Cermin V [Aktif]" else "Cermin V"
                            setTextColor(if (cerminV) Color.parseColor("#38BDF8") else Color.WHITE)
                            textSize = 12f
                            val pX = dpKePx(10)
                            val pY = dpKePx(6)
                            setPadding(pX, pY, pX, pY)
                            val latar = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dpKePx(6).toFloat()
                                setColor(if (cerminV) Color.parseColor("#1E3A8A") else Color.parseColor("#374151"))
                            }
                            background = latar
                            setOnClickListener {
                                cerminV = !cerminV
                                perbaruiSubPanel()
                                tampilanKanvas.invalidate()
                            }
                        }
                        barisAksi.addView(tombolCerminV)

                        val p3 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
                        barisAksi.addView(p3)

                        val tombolReset = TextView(konteks).apply {
                            text = "Setel Ulang"
                            setTextColor(Color.parseColor("#FBBF24"))
                            textSize = 12f
                            val pX = dpKePx(10)
                            val pY = dpKePx(6)
                            setPadding(pX, pY, pX, pY)
                            setOnClickListener {
                                rotasi90 = 0
                                sudutDerajatHalus = 0f
                                cerminH = false
                                cerminV = false
                                perbaruiSubPanel()
                                tampilanKanvas.invalidate()
                            }
                        }
                        barisAksi.addView(tombolReset)
                        panelSubAlat.addView(barisAksi)

                        // Slider Derajat Halus (-45° s/d +45°) ala Penggaris Edit Foto
                        val barisSlider = LinearLayout(konteks).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            val topM = dpKePx(8)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = topM }
                        }

                        val teksSudut = TextView(konteks).apply {
                            text = "${sudutDerajatHalus.toInt()}°"
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            val w = dpKePx(40)
                            layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
                            gravity = Gravity.CENTER
                        }

                        val slider = SeekBar(konteks).apply {
                            max = 90
                            progress = (sudutDerajatHalus + 45).toInt()
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(seekBar: SeekBar?, nilai: Int, fromUser: Boolean) {
                                    sudutDerajatHalus = (nilai - 45).toFloat()
                                    teksSudut.text = "${sudutDerajatHalus.toInt()}°"
                                    tampilanKanvas.invalidate()
                                }
                                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                            })
                        }

                        barisSlider.addView(slider)
                        barisSlider.addView(teksSudut)
                        panelSubAlat.addView(barisSlider)
                    }

                    ModeAlat.POTONG -> {
                        val barisRasio = LinearLayout(konteks).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                        }

                        val daftarRasio = listOf("Bebas", "1:1", "4:3", "16:9", "9:16")
                        for (rasio in daftarRasio) {
                            val tombolRasio = TextView(konteks).apply {
                                text = rasio
                                val terpilih = rasioPotong == rasio
                                setTextColor(if (terpilih) Color.WHITE else Color.LTGRAY)
                                textSize = 12f
                                val pX = dpKePx(10)
                                val pY = dpKePx(6)
                                setPadding(pX, pY, pX, pY)
                                val latar = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dpKePx(6).toFloat()
                                    setColor(if (terpilih) Color.parseColor("#2563EB") else Color.parseColor("#374151"))
                                }
                                background = latar
                                setOnClickListener {
                                    rasioPotong = rasio
                                    perbaruiSubPanel()
                                    tampilanKanvas.invalidate()
                                }
                            }
                            barisRasio.addView(tombolRasio)
                            val sp = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(6), 1) }
                            barisRasio.addView(sp)
                        }
                        panelSubAlat.addView(barisRasio)
                    }

                    ModeAlat.CORET -> {
                        val barisWarna = LinearLayout(konteks).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                        }

                        val warnaTersedia = listOf(
                            Color.parseColor("#EF4444"), // Merah
                            Color.parseColor("#EAB308"), // Kuning
                            Color.parseColor("#22C55E"), // Hijau
                            Color.parseColor("#000000"), // Hitam
                            Color.parseColor("#FFFFFF")  // Putih
                        )

                        for (w in warnaTersedia) {
                            val lingkaran = View(konteks).apply {
                                val ukuran = dpKePx(26)
                                layoutParams = LinearLayout.LayoutParams(ukuran, ukuran).apply {
                                    val m = dpKePx(6)
                                    setMargins(m, 0, m, 0)
                                }
                                val bg = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(w)
                                    if (warnaKuas == w) {
                                        setStroke(dpKePx(3), Color.parseColor("#38BDF8"))
                                    } else {
                                        setStroke(dpKePx(1), Color.GRAY)
                                    }
                                }
                                background = bg
                                setOnClickListener {
                                    warnaKuas = w
                                    perbaruiSubPanel()
                                }
                            }
                            barisWarna.addView(lingkaran)
                        }
                        panelSubAlat.addView(barisWarna)
                    }
                }
            }

            perbaruiSubPanel()
            tataLetakUtama.addView(panelSubAlat)

            // Bottom Navigation Tabs (Potong, Putar & Mirror, Coret)
            val bilahNavigasiBawah = LinearLayout(konteks).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val p = dpKePx(10)
                setPadding(p, p, p, p)
                setBackgroundColor(Color.parseColor("#0F172A"))
            }

            fun buatTombolTab(label: String, mode: ModeAlat): TextView {
                return TextView(konteks).apply {
                    text = label
                    val terpilih = modeAktif == mode
                    setTextColor(if (terpilih) Color.parseColor("#38BDF8") else Color.GRAY)
                    textSize = 13f
                    val pX = dpKePx(14)
                    val pY = dpKePx(8)
                    setPadding(pX, pY, pX, pY)
                    val latar = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpKePx(8).toFloat()
                        setColor(if (terpilih) Color.parseColor("#1E293B") else Color.TRANSPARENT)
                        if (terpilih) {
                            setStroke(dpKePx(1), Color.parseColor("#38BDF8"))
                        }
                    }
                    background = latar
                    setOnClickListener {
                        modeAktif = mode
                        bilahNavigasiBawah.removeAllViews()
                        bilahNavigasiBawah.addView(buatTombolTab("📐 Potong", ModeAlat.POTONG))
                        val s1 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
                        bilahNavigasiBawah.addView(s1)
                        bilahNavigasiBawah.addView(buatTombolTab("🔄 Putar & Mirror", ModeAlat.PUTAR_MIRROR))
                        val s2 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
                        bilahNavigasiBawah.addView(s2)
                        bilahNavigasiBawah.addView(buatTombolTab("✏️ Coret", ModeAlat.CORET))
                        perbaruiSubPanel()
                        tampilanKanvas.invalidate()
                    }
                }
            }

            bilahNavigasiBawah.addView(buatTombolTab("📐 Potong", ModeAlat.POTONG))
            val s1 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
            bilahNavigasiBawah.addView(s1)
            bilahNavigasiBawah.addView(buatTombolTab("🔄 Putar & Mirror", ModeAlat.PUTAR_MIRROR))
            val s2 = View(konteks).apply { layoutParams = LinearLayout.LayoutParams(dpKePx(8), 1) }
            bilahNavigasiBawah.addView(s2)
            bilahNavigasiBawah.addView(buatTombolTab("✏️ Coret", ModeAlat.CORET))

            tataLetakUtama.addView(bilahNavigasiBawah)

            // Eksekusi Simpan Hasil Edit ke Galeri
            tombolSimpan.setOnClickListener {
                tombolSimpan.isEnabled = false
                tombolSimpan.text = "Menyimpan..."

                Thread {
                    try {
                        val totalSudut = (rotasi90 * 90) + sudutDerajatHalus
                        val totalRadian = Math.toRadians(totalSudut.toDouble())
                        val cosSudut = Math.abs(Math.cos(totalRadian))
                        val sinSudut = Math.abs(Math.sin(totalRadian))

                        val wRotasi = (bitmapAsli.width * cosSudut + bitmapAsli.height * sinSudut).toInt().coerceAtLeast(1)
                        val hRotasi = (bitmapAsli.width * sinSudut + bitmapAsli.height * cosSudut).toInt().coerceAtLeast(1)

                        val bitmapHasil = Bitmap.createBitmap(wRotasi, hRotasi, Bitmap.Config.ARGB_8888)
                        val kanvasHasil = Canvas(bitmapHasil)

                        kanvasHasil.translate(wRotasi / 2f, hRotasi / 2f)
                        kanvasHasil.rotate(totalSudut)
                        kanvasHasil.scale(if (cerminH) -1f else 1f, if (cerminV) -1f else 1f)
                        kanvasHasil.translate(-bitmapAsli.width / 2f, -bitmapAsli.height / 2f)

                        // Gambar Bitmap Asli
                        kanvasHasil.drawBitmap(bitmapAsli, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                        // Gambar Seluruh Coretan
                        for (coretan in daftarCoretan) {
                            val kuas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = coretan.warna
                                strokeWidth = coretan.ketebalan
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            kanvasHasil.drawPath(coretan.jalur, kuas)
                        }

                        // Kompres ke PNG byte array
                        val aliranByte = ByteArrayOutputStream()
                        bitmapHasil.compress(Bitmap.CompressFormat.PNG, 100, aliranByte)
                        val dataByte = aliranByte.toByteArray()

                        val namaSimpan = "AutoTempel_Edit_${System.currentTimeMillis()}.png"
                        val uriSimpan = PengelolaBrankas.simpanHasilEditKeGaleri(konteks, dataByte, namaSimpan)

                        penanganUtama.post {
                            if (uriSimpan != null) {
                                Toast.makeText(konteks, "Hasil edit tersimpan di Galeri & disalin ke Clipboard!", Toast.LENGTH_LONG).show()
                                tutupEditor()
                            } else {
                                Toast.makeText(konteks, "Gagal menyimpan hasil edit!", Toast.LENGTH_SHORT).show()
                                tombolSimpan.isEnabled = true
                                tombolSimpan.text = "💾 Simpan ke Galeri"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal merender hasil edit: ${e.message}")
                        penanganUtama.post {
                            Toast.makeText(konteks, "Terjadi kesalahan saat menyimpan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            tombolSimpan.isEnabled = true
                            tombolSimpan.text = "💾 Simpan ke Galeri"
                        }
                    }
                }.start()
            }

            tampilanEditorAktif = tataLetakUtama
            try {
                wm.addView(tataLetakUtama, parameterEditor)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menampilkan editor melayang: ${e.message}")
            }
        }
    }

    // [FITUR: Tutup Jendela Editor Melayang]
    fun tutupEditor() {
        penanganUtama.post {
            val wm = pengelolaJendela ?: return@post
            val tampilan = tampilanEditorAktif ?: return@post
            try {
                wm.removeView(tampilan)
            } catch (_: Exception) {}
            tampilanEditorAktif = null
        }
    }
}

