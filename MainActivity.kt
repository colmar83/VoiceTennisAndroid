package com.voicetennis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var a: EditText
    private lateinit var b: EditText
    private lateinit var scoreA: TextView
    private lateinit var scoreB: TextView
    private lateinit var status: TextView
    private lateinit var listen: Button
    private var ga=0; private var gb=0; private var setsA=0; private var setsB=0
    private var active=false
    private var recognizer: SpeechRecognizer?=null
    private var tts: TextToSpeech?=null
    private val handler=Handler(Looper.getMainLooper())
    private var pending=false
    private var previous:IntArray?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=0xFF102D25.toInt()
        window.navigationBarColor=0xFF102D25.toInt()
        tts=TextToSpeech(this) { tts?.language=Locale.ITALIAN }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(18,24,18,16); setBackgroundColor(0xFF102D25.toInt()) }
        fun label(s:String,size:Float,color:Int)=TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER; setPadding(4,8,4,8) }
        root.addView(label("🎾 VoiceTennis",28f,0xFFD7FF68.toInt()))
        root.addView(label("Pronuncia: nome del giocatore + game",15f,0xFFB9D3C7.toInt()))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        a=EditText(this).apply { setText("Marco"); singleLine=true; setTextColor(-1); hint="Giocatore 1" }
        b=EditText(this).apply { setText("Luca"); singleLine=true; setTextColor(-1); hint="Giocatore 2" }
        row.addView(a,LinearLayout.LayoutParams(0,-2,1f)); row.addView(b,LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        val scores=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        scoreA=label("",24f,0xFFD7FF68.toInt()); scoreB=label("",24f,0xFFD7FF68.toInt())
        scores.addView(scoreA,LinearLayout.LayoutParams(0,180,1f)); scores.addView(scoreB,LinearLayout.LayoutParams(0,180,1f)); root.addView(scores)
        val manual=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        manual.addView(Button(this).apply { text="+ Game Marco"; setOnClickListener { add(0) } },LinearLayout.LayoutParams(0,-2,1f))
        manual.addView(Button(this).apply { text="+ Game Luca"; setOnClickListener { add(1) } },LinearLayout.LayoutParams(0,-2,1f)); root.addView(manual)
        listen=Button(this).apply { text="🎙 Avvia ascolto"; setOnClickListener { if(active) stop() else start() } }
        root.addView(listen,LinearLayout.LayoutParams(-1,-2))
        status=label("Microfono non attivo",14f,0xFFB9D3C7.toInt()); root.addView(status)
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text="Annulla"; setOnClickListener { undo() } },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply { text="Azzera"; setOnClickListener { ga=0;gb=0;setsA=0;setsB=0;previous=null;render() } },LinearLayout.LayoutParams(0,-2,1f)); root.addView(actions)
        root.addView(label("Tieni lo schermo acceso durante la partita.",13f,0xFFB9D3C7.toInt()))
        setContentView(root)
        a.addTextChangedListener(SimpleWatcher { render() }); b.addTextChangedListener(SimpleWatcher { render() }); render()
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7)
    }
    private fun name(i:Int):String=(if(i==0)a.text else b.text).toString().trim().ifBlank { if(i==0) "Giocatore 1" else "Giocatore 2" }
    private fun render() {
        if(!::scoreA.isInitialized)return
        scoreA.text="${name(0)}\n$ga\nGAME\nSet: $setsA"; scoreB.text="${name(1)}\n$gb\nGAME\nSet: $setsB"
        listen.text=if(active) "■ Interrompi ascolto" else "🎙 Avvia ascolto"
        status.text=if(active) "Microfono in ascolto…" else "Microfono non attivo"
    }
    private fun add(i:Int) {
        previous=intArrayOf(ga,gb,setsA,setsB)
        if(i==0)ga++ else gb++
        val n=if(i==0)ga else gb; val other=if(i==0)gb else ga
        if(n>=6&&n-other>=2) { if(i==0)setsA++ else setsB++; ga=0;gb=0 }
        render(); val msg="Game ${name(i)}"; tts?.speak(msg,TextToSpeech.QUEUE_FLUSH,null,"game"); Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()
    }
    private fun undo() { val p=previous?:run { Toast.makeText(this,"Nessuna azione da annullare",Toast.LENGTH_SHORT).show(); return }; ga=p[0];gb=p[1];setsA=p[2];setsB=p[3];previous=null;render() }
    private fun start() {
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { Toast.makeText(this,"Riconoscimento vocale non disponibile",Toast.LENGTH_LONG).show();return }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7);return }
        active=true;render();listenCycle()
    }
    private fun listenCycle() {
        if(!active||pending)return
        try {
            if(recognizer==null)recognizer=SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(p:Bundle?) { status.text="Microfono in ascolto…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(r:Float) {}
                override fun onBufferReceived(b:ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(e:Int) { restart() }
                override fun onPartialResults(b:Bundle?) {}
                override fun onEvent(e:Int,b:Bundle?) {}
                override fun onResults(b:Bundle?) {
                    val said=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().lowercase(Locale.ITALIAN)
                    if(said.contains("game")||said.contains("gam")||said.contains("ha vinto")) when {
                        said.contains(name(0).lowercase(Locale.ITALIAN))->add(0)
                        said.contains(name(1).lowercase(Locale.ITALIAN))->add(1)
                    }
                    restart()
                }
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"it-IT");putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3) }
            recognizer?.startListening(intent)
        } catch(e:Exception) { restart() }
    }
    private fun restart() {
        if(!active||pending)return
        pending=true;handler.postDelayed({pending=false;if(active){try{recognizer?.cancel()}catch(e:Exception){};listenCycle()}},700)
    }
    private fun stop() { active=false;try{recognizer?.cancel()}catch(e:Exception){};render() }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) { super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Microfono autorizzato",Toast.LENGTH_SHORT).show() }
    override fun onDestroy() { active=false;handler.removeCallbacksAndMessages(null);try{recognizer?.destroy()}catch(e:Exception){};tts?.shutdown();super.onDestroy() }
}
