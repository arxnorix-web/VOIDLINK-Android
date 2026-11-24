package com.voidaxiom.voidlink

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MainActivity : Activity() {
    private val cyan = Color.rgb(55, 224, 220)
    private val bg = Color.rgb(8, 13, 24)
    private val panel = Color.rgb(17, 27, 43)
    private var player: MediaPlayer? = null
    private var lastFile: File? = null
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var decoded: TextView
    private val pickAudio = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(page)
        val brand = TextView(this).apply {
            text = "VOIDLINK  /  ACOUSTIC DATA"
            textSize = 12f; setTextColor(cyan); letterSpacing = .18f
        }
        page.addView(brand)
        page.addView(TextView(this).apply {
            text = "Transmit beyond\nthe interface."
            textSize = 30f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(8))
        })
        page.addView(TextView(this).apply {
            text = "Experimental FSK modem • Offline • v1.0"
            textSize = 13f; setTextColor(Color.LTGRAY)
        })
        page.addView(sectionView("01  /  ENCODER"))
        input = EditText(this).apply {
            hint = "Escribe el mensaje que quieres transmitir…"
            setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 16f; minLines = 4; gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 14)
        }
        page.addView(input, matchWrap().apply { topMargin = dp(12) })
        val encode = actionButton("GENERAR AUDIO  ↗")
        page.addView(encode, matchWrap().apply { topMargin = dp(12) })
        encode.setOnClickListener { encodeMessage() }
        val play = actionButton("▶  ESCUCHAR ÚLTIMO AUDIO")
        page.addView(play, matchWrap().apply { topMargin = dp(8) })
        play.setOnClickListener { lastFile?.let { playFile(it) } ?: setStatus("Primero genera un audio.") }
        val saveInfo = TextView(this).apply {
            text = "El audio se guarda como WAV PCM, sin pérdida y compatible con el decodificador."
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(2, dp(8), 2, 0)
        }
        page.addView(saveInfo)
        page.addView(sectionView("02  /  DECODER"))
        val choose = actionButton("SELECCIONAR ARCHIVO DE AUDIO")
        page.addView(choose, matchWrap().apply { topMargin = dp(12) })
        choose.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
            }
            startActivityForResult(i, pickAudio)
        }
        decoded = TextView(this).apply {
            text = "El texto recuperado aparecerá aquí."
            textSize = 15f; setTextColor(Color.WHITE); minHeight = dp(100)
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(panel, 14)
        }
        page.addView(decoded, matchWrap().apply { topMargin = dp(12) })
        status = TextView(this).apply {
            text = "● SISTEMA LISTO"; textSize = 12f; setTextColor(cyan)
            setPadding(2, dp(18), 2, 0)
        }
        page.addView(status)
        page.addView(TextView(this).apply {
            text = "PROTOCOLO: AA55AA55 · VLNK · UTF-8 · CRC32\nFSK: 1500 / 2200 Hz · Manchester · 12 ms/símbolo\nNota: formato experimental VOIDLINK; no es GGWave."
            textSize = 11f; setTextColor(Color.GRAY); setPadding(2, dp(18), 2, 0)
        })
        setContentView(scroll)
    }

    private fun sectionView(s: String) = TextView(this).apply {
        text=s; textSize=13f; setTextColor(cyan); typeface=android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(28), 0, dp(4))
    }

    private fun actionButton(label: String) = Button(this).apply {
        text = label; setTextColor(Color.rgb(5, 18, 28)); textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = rounded(cyan, 12)
        isAllCaps = false
    }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun setStatus(s: String) { status.text = "● $s" }

    private fun encodeMessage() {
        val msg = input.text.toString()
        if (msg.isBlank()) { setStatus("Escribe un mensaje primero."); return }
        try {
            val wav = makeWav(msg)
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            dir.mkdirs()
            val f = File(dir, "VOIDLINK_${System.currentTimeMillis()}.wav")
            FileOutputStream(f).use { it.write(wav) }
            lastFile = f
            setStatus("AUDIO GENERADO · ${f.length()/1024} KB · ${f.name}")
        } catch (e: Exception) { setStatus("Error: ${e.message}") }
    }

    private fun makeWav(message: String): ByteArray {
        val payload = message.toByteArray(Charsets.UTF_8)
        require(payload.size <= 65535) { "Mensaje demasiado largo (máximo 65535 bytes)." }
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val frame = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xAA.toByte(),0x55,0xAA.toByte(),0x55))
            write("VLNK".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf((payload.size shr 8).toByte(), payload.size.toByte()))
            write(payload)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array())
        }.toByteArray()
        val bits = ArrayList<Int>()
        for (b in frame) for (i in 7 downTo 0) {
            val bit = (b.toInt() shr i) and 1
            if (bit == 0) { bits.add(0); bits.add(1) } else { bits.add(1); bits.add(0) }
        }
        val sr = 44100
        val samplesPerSymbol = (sr * 0.012).toInt()
        val lead = sr / 10
        val tail = sr / 10
        val pcm = ByteArrayOutputStream()
        repeat(lead) { writePcm(pcm, 0) }
        var phase = 0.0
        for (bit in bits) {
            val freq = if (bit == 0) 1500.0 else 2200.0
            for (n in 0 until samplesPerSymbol) {
                val sample = (sin(phase) * 0.72 * 32767).toInt()
                writePcm(pcm, sample)
                phase += 2.0 * PI * freq / sr
                if (phase > 2*PI) phase -= 2*PI
            }
        }
        repeat(tail) { writePcm(pcm, 0) }
        // End-of-frame marker: 620 Hz for 280 ms
        repeat((sr*0.28).toInt()) { n ->
            val sample = (sin(2*PI*620.0*n/sr)*0.72*32767).toInt()
            writePcm(pcm, sample)
        }
        val data = pcm.toByteArray()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36+data.size)
        header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(sr); header.putInt(sr*2); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(data.size)
        return header.array() + data
    }
    private fun writePcm(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 255); out.write((v shr 8) and 255)
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath); prepare(); start()
                setOnCompletionListener { it.release(); player = null }
            }
            setStatus("REPRODUCIENDO · ${file.name}")
        } catch (e: Exception) { setStatus("No se pudo reproducir: ${e.message}") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickAudio && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            setStatus("ANALIZANDO AUDIO…")
            Thread {
                try {
                    val (samples, sr) = decodeUriToPcm(uri)
                    val result = decodeFrame(samples, sr)
                    runOnUiThread {
                        decoded.text = result.first
                        setStatus(if (result.second) "CRC32 CORRECTO · MENSAJE RECUPERADO" else "ARCHIVO LEÍDO, PERO CRC32 NO COINCIDE")
                    }
                } catch (e: Exception) {
                    runOnUiThread { decoded.text = "No se pudo decodificar: ${e.message}"; setStatus("ERROR DE DECODIFICACIÓN") }
                }
            }.start()
        }
    }

    private fun decodeUriToPcm(uri: Uri): Pair<ShortArray, Int> {
        val inputStream = contentResolver.openInputStream(uri) ?: error("No se puede abrir el archivo")
        val bytes = inputStream.use { it.readBytes() }
        if (bytes.size > 44 && String(bytes,0,4) == "RIFF") {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val sr = bb.getInt(24)
            var pos = 12
            var dataPos = -1; var dataSize = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4); val size = ByteBuffer.wrap(bytes,pos+4,4).order(ByteOrder.LITTLE_ENDIAN).int
                if (id == "data") { dataPos=pos+8; dataSize=minOf(size, bytes.size-dataPos); break }
                pos += 8 + size + (size and 1)
            }
            require(dataPos >= 0) { "WAV sin bloque data" }
            val count = dataSize/2
            val out = ShortArray(count)
            val data = ByteBuffer.wrap(bytes,dataPos,dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) out[i] = data.short
            return Pair(out,sr)
        }
        // MP3 and other common audio formats: decode through Android MediaCodec.
        val extractor = MediaExtractor()
        extractor.setDataSource(this, uri, null)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track=i; break }
        }
        require(track >= 0) { "No se encontró una pista de audio" }
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        extractor.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format,null,null,0); codec.start()
        val output = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone=false; var outputDone=false
        while (!outputDone) {
            if (!inputDone) {
                val inIndex=codec.dequeueInputBuffer(10000)
                if (inIndex>=0) {
                    val buf=codec.getInputBuffer(inIndex)!!
                    val size=extractor.readSampleData(buf,0)
                    if (size<0) { codec.queueInputBuffer(inIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                    else { codec.queueInputBuffer(inIndex,0,size,extractor.sampleTime,0); extractor.advance() }
                }
            }
            val outIndex=codec.dequeueOutputBuffer(info,10000)
            if (outIndex>=0) {
                val buf=codec.getOutputBuffer(outIndex)!!
                val chunk=ByteArray(info.size); buf.position(info.offset); buf.get(chunk)
                output.write(chunk); codec.releaseOutputBuffer(outIndex,false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) outputDone=true
            } else if (outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { /* PCM output format is normally unchanged */ }
        }
        codec.stop(); codec.release(); extractor.release()
        val raw=output.toByteArray()
        val bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val total=raw.size/(2*channels)
        val mono=ShortArray(total)
        for (i in 0 until total) {
            var sum=0
            for (c in 0 until channels) sum += bb.short.toInt()
            mono[i]=(sum/channels).toShort()
        }
        return Pair(mono,sampleRate)
    }

    private fun decodeFrame(samples: ShortArray, sr: Int): Pair<String,Boolean> {
        val sps=max(8,(sr*0.012).toInt())
        val maxOffset=minOf(sps, samples.size/4)
        for (offset in 0 until maxOffset) {
            val symbols=ArrayList<Int>()
            var p=offset
            while (p+sps <= samples.size) {
                symbols.add(classify(samples,p,sps,sr))
                p+=sps
            }
            val raw=ArrayList<Int>()
            var i=0
            while (i+1<symbols.size) {
                val a=symbols[i]; val b=symbols[i+1]
                if (a==0 && b==1) raw.add(0) else if (a==1 && b==0) raw.add(1) else raw.add(-1)
                i+=2
            }
            // The recording may contain a few leading silence symbols. Try every
            // bit alignment so the frame header can still be found.
            for (bitOffset in 0..7) {
                val bytes=ArrayList<Byte>()
                var j=bitOffset
                while (j+8<=raw.size) {
                    var v=0; var valid=true
                    for (k in 0..7) { val bit=raw[j+k]; if(bit<0) valid=false else v=(v shl 1) or bit }
                    bytes.add(if(valid) v.toByte() else 0)
                    j+=8
                }
                val arr=bytes.toByteArray()
                for (start in 0..maxOf(0,arr.size-10)) {
                    if (start+10>arr.size) break
                    if (arr[start]==0xAA.toByte() && arr[start+1]==0x55.toByte() &&
                        arr[start+2]==0xAA.toByte() && arr[start+3]==0x55.toByte() &&
                        String(arr,start+4,4,Charsets.US_ASCII)=="VLNK") {
                        val len=((arr[start+8].toInt() and 255) shl 8) or (arr[start+9].toInt() and 255)
                        val end=start+10+len+4
                        if (len>=0 && end<=arr.size) {
                            val payload=arr.copyOfRange(start+10,start+10+len)
                            val expected=ByteBuffer.wrap(arr,start+10+len,4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
                            val actual=CRC32().apply { update(payload) }.value
                            return Pair(String(payload,Charsets.UTF_8), expected==actual)
                        }
                    }
                }
            }
        }
        error("No se encontró una trama VOIDLINK válida. Comprueba que el archivo contiene audio generado por este protocolo.")
    }
    private fun classify(a: ShortArray, start: Int, count: Int, sr: Int): Int {
        var p0=0.0; var p1=0.0
        for (f in doubleArrayOf(1500.0,2200.0)) {
            val coeff=2*cos(2*PI*f/sr)
            var q1=0.0; var q2=0.0
            for (i in start until start+count) {
                val q0=a[i].toDouble()+coeff*q1-q2; q2=q1; q1=q0
            }
            val power=q1*q1+q2*q2-coeff*q1*q2
            if (f==1500.0) p0=power else p1=power
        }
        return if(p1>p0) 1 else 0
    }

    override fun onDestroy() { player?.release(); super.onDestroy() }
}
