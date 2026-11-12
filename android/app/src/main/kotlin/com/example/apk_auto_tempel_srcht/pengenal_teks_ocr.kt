package com.example.apk_auto_tempel_srcht

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// [FITUR: Pengenal Teks OCR Google Lens On-Device] - Deteksi teks dari screenshot 100% offline menggunakan Google ML Kit Text Recognition
object PengenalTeksOcr {

    private const val TAG = "PengenalTeksOcr"
    private val penanganUtama = Handler(Looper.getMainLooper())
    private val pengenalTeks = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // [FITUR: Deteksi Teks dari Gambar] - Membaca berkas gambar dan mengenali seluruh huruf/kata secara instan
    fun deteksiTeksDariBerkas(
        konteks: Context,
        jalurBerkas: String,
        padaHasil: (sukses: Boolean, teksLengkap: String, daftarBaris: List<String>) -> Unit
    ) {
        val berkas = File(jalurBerkas)
        if (!berkas.exists()) {
            penanganUtama.post {
                padaHasil.invoke(false, "Berkas gambar tidak ditemukan", emptyList())
            }
            return
        }

        Thread {
            try {
                // Decode bitmap dengan resolusi optimal
                val opsi = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(jalurBerkas, opsi)
                if (bitmap == null) {
                    penanganUtama.post {
                        padaHasil.invoke(false, "Gagal memuat bitmap gambar", emptyList())
                    }
                    return@Thread
                }

                val gambarInput = InputImage.fromBitmap(bitmap, 0)
                pengenalTeks.process(gambarInput)
                    .addOnSuccessListener { hasilTeks ->
                        val teksLengkap = hasilTeks.text
                        val daftarBaris = mutableListOf<String>()

                        for (blok in hasilTeks.textBlocks) {
                            for (baris in blok.lines) {
                                daftarBaris.add(baris.text)
                            }
                        }

                        penanganUtama.post {
                            padaHasil.invoke(true, teksLengkap, daftarBaris)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Gagal mengenali teks: ${e.message}")
                        penanganUtama.post {
                            padaHasil.invoke(false, "Gagal mengenali teks: ${e.localizedMessage}", emptyList())
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Kesalahan sistem OCR: ${e.message}")
                penanganUtama.post {
                    padaHasil.invoke(false, "Kesalahan sistem: ${e.localizedMessage}", emptyList())
                }
            }
        }.start()
    }
}

