package com.example.apk_auto_tempel_srcht

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// [FITUR: Pengenal Teks OCR & Terjemahan Google Lens On-Device] - Deteksi teks & terjemahan offline gratis menggunakan Google ML Kit
object PengenalTeksOcr {

    private const val TAG = "PengenalTeksOcr"
    private val penanganUtama = Handler(Looper.getMainLooper())
    private val pengenalTeks = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val pengenalBahasa = LanguageIdentification.getClient()

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

    // [FITUR: Terjemahan Teks On-Device Google Lens] - Mendeteksi bahasa sumber secara otomatis lalu menerjemahkan ke bahasa target (misal Indonesia/Inggris) secara offline Rp 0
    fun terjemahkanTeks(
        teksSumber: String,
        kodeBahasaTarget: String = TranslateLanguage.INDONESIAN,
        padaHasil: (sukses: Boolean, hasilAtauPesan: String, bahasaTerdeteksi: String) -> Unit
    ) {
        val teksBersih = teksSumber.trim()
        if (teksBersih.isEmpty()) {
            penanganUtama.post {
                padaHasil.invoke(false, "Tidak ada teks untuk diterjemahkan", "")
            }
            return
        }

        pengenalBahasa.identifyLanguage(teksBersih)
            .addOnSuccessListener { kodeBahasaAsal ->
                val bahasaSumber = if (kodeBahasaAsal == "und" || TranslateLanguage.fromLanguageTag(kodeBahasaAsal) == null) {
                    TranslateLanguage.ENGLISH
                } else {
                    TranslateLanguage.fromLanguageTag(kodeBahasaAsal)!!
                }

                val targetValid = TranslateLanguage.fromLanguageTag(kodeBahasaTarget) ?: TranslateLanguage.INDONESIAN
                if (bahasaSumber == targetValid) {
                    penanganUtama.post {
                        padaHasil.invoke(true, teksBersih, bahasaSumber)
                    }
                    return@addOnSuccessListener
                }

                val opsiPenerjemah = TranslatorOptions.Builder()
                    .setSourceLanguage(bahasaSumber)
                    .setTargetLanguage(targetValid)
                    .build()
                val penerjemah = Translation.getClient(opsiPenerjemah)
                val kondisiUnduh = DownloadConditions.Builder().build()

                penerjemah.downloadModelIfNeeded(kondisiUnduh)
                    .addOnSuccessListener {
                        penerjemah.translate(teksBersih)
                            .addOnSuccessListener { teksTerjemahan ->
                                penerjemah.close()
                                penanganUtama.post {
                                    padaHasil.invoke(true, teksTerjemahan, bahasaSumber)
                                }
                            }
                            .addOnFailureListener { e ->
                                penerjemah.close()
                                Log.e(TAG, "Gagal menerjemahkan teks: ${e.message}")
                                penanganUtama.post {
                                    padaHasil.invoke(false, "Gagal menerjemahkan: ${e.localizedMessage}", bahasaSumber)
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        penerjemah.close()
                        Log.e(TAG, "Gagal mengunduh model bahasa terjemahan: ${e.message}")
                        penanganUtama.post {
                            padaHasil.invoke(
                                false,
                                "Gagal menyiapkan model bahasa offline ($bahasaSumber -> $targetValid). Pastikan internet aktif saat unduh pertama kali.",
                                bahasaSumber
                            )
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal mengidentifikasi bahasa: ${e.message}")
                penanganUtama.post {
                    padaHasil.invoke(false, "Gagal mengenali bahasa: ${e.localizedMessage}", "")
                }
            }
    }
}

