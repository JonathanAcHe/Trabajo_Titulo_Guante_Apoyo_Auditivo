// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.mysoundclassification

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate


class MainActivity : AppCompatActivity() {

    var TAG = "MainActivity"

    //-----------------------------------
    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var device: BluetoothDevice? = null
    private val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var address: String? = null
    public var MyConexionBT: ConnectedThread? = null
    var mmInStream: InputStream? = null
    var mmOutStream: OutputStream? = null
    var tmpIn: InputStream? = null
    var tmpOut: OutputStream? = null
    var textDetec: String? = null
    var cuentaletra : Int= 0;
    val control = arrayOf("X", "Y", "Z")

    //-----------------------------------

    // TODO 2.1: Se crea la variable con el nombre del modelo

    var modelPath = "lite-model_yamnet_classification_tflite_1.tflite"

    // TODO 2.2: umbral mínimo para aceptar una predicción de parte del modelo.
    var probabilityThreshold: Float = 0.3f

    lateinit var textView: TextView
    //lateinit var imagView: ImageView

    @RequiresApi(Build.VERSION_CODES.O)
    fun vibratePhone(tipo: String): String {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        //val pattern = longArrayOf(0, 100, 1000, 200, 2000)
        val midiendotiempo =  DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        println("Ingreso a la funcion vibracion en :" + midiendotiempo)
        if (tipo == "Dog")
            //vibrator.vibrate(pattern,-1)
            vibrator.vibrate(100) // 500 es mucho, 150 es como un pulso de medio segundo

        if (tipo == "Crying")
            vibrator.vibrate(300) // 500 es mucho, 150 es como un pulso de medio segundo
        return tipo
    }

    //Esta funciona tambien
    //fun vibratePhone() {
    //    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    //    vibrator.vibrate(300) // 500 es mucho, 150 es como un pulso de medio segundo
    //}


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //-----------------------------------
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        VerificarEstadoBT()
        //-----------------------------------


        // ----------------------------------- esto era el onResume en la otra app
        var intent : Intent = getIntent();
        address = intent.getStringExtra(DispositivosVinculados.EXTRA_DEVICE_ADDRESS)



        println("onResume.    address: ")
        println(address)

        println("*¨*****SCAASDCASDCA*ASDAS*DAS*ADAS*DA*")
        //Setea la direccion MAC
        device = btAdapter?.getRemoteDevice(address) // 00:18:E4:34:EB:22
        println("device: $device")

        try {
            println("holaesto es btSocket aNTES: $btSocket")
            btSocket = createBluetoothSocket(device)
            println("holaesto es btSocket DESPUES: $btSocket")

        }catch (e: IOException) {
            Toast.makeText(baseContext, "La creacción del Socket fallo", Toast.LENGTH_LONG).show()
        }

        // Establece la conexión con el socket Bluetooth.
        try {
            if(ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED ){
                println("BUENA CTM")

                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                println("BUENA CTM2 $btSocket") //BT
                btSocket?.connect()
                //println(btSocket?.connect()) //BT connect calling pid/uid = 23988/10373


                //println(btSocket?.connect()) // imprime BT connect calling pid/uid = 27414/10373

            }

        } catch (e: IOException){
            try {
                println("ESTA WEA SE METE ACA ASDASDASD ???? ")
                //btSocket?.close()
            } catch (e2: IOException){
                println("EEEEERRRRROOOOOORRRR")
            }

        }

        //MyConexionBT?.ConnectedThread(btSocket!!) // OPC1

        MyConexionBT =  ConnectedThread(btSocket)
        MyConexionBT?.start()

        //----------------------------------- onResume FIN


        val REQUEST_RECORD_AUDIO = 1337
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)

        textView = findViewById<TextView>(R.id.output)
        //imagView = findViewById<ImageView>(R.id.imageView3)

        val recorderSpecsTextView = findViewById<TextView>(R.id.textViewAudioRecorderSpecs)

        // TODO 2.3: Se cargara el modelo desde la carpeta de elementos.
        val classifier = AudioClassifier.createFromFile(this, modelPath)

        // TODO 3.1: Crear un tensor de Audio
        val tensor = classifier.createInputTensorAudio()

        // TODO 3.2: Mostrar especificaciones de la grabacion de audio
        val format = classifier.requiredTensorAudioFormat
        val recorderSpecs = "Numero de canales: ${format.channels}\n" +
                "Frecuencia de Muestreo: ${format.sampleRate}\n" +
                "Periodo de Muestreo: 2000"
        recorderSpecsTextView.text = recorderSpecs

        // TODO 3.3: Crea el grabador de audio y comienza a grabar.
        val record = classifier.createAudioRecord()
        record.startRecording()


        Timer().scheduleAtFixedRate(1, 2000) { //ORIGINALMENTE EL PERIODO ESTABA EN 500 haciendo pruebas con 4500

            println("*******************************************************************************************************************")
            // TODO 4.1: Classifing audio data
            val numberOfSamples = tensor.load(record)
            val output = classifier.classify(tensor)

            // TODO 4.2: Filtering out classifications with low probability
            val filteredModelOutput = output[0].categories.filter {
                it.score > probabilityThreshold
            }

            // TODO 4.3: Creating a multiline string with the filtered results
            val outputStr = filteredModelOutput.sortedBy { -it.score }
                    .joinToString(separator = "\n") { "${it.label} -> ${it.score} " }

            //ACA TOMAMOS EL PARAMETRO A DETECTAR Y MANDAMOS LA SEÑAL A ARDUINO

            if(outputStr.contains("Baby cry") || outputStr.contains("infant")){
                println("DETECTO Baby cry")
                arduinoSignal("C", cuentaletra)
                cuentaletra = cuentaletra + 1;
                textDetec = "BEBE LLORANDO"
                //imagView.setImageResource(R.drawable.bbcry)
            }
            else if(outputStr.contains("Siren") || outputStr.contains("Smoke") || outputStr.contains("Fire") || outputStr.contains("Beep, bleep")){
                println("DETECTO Siren")
                arduinoSignal("S", cuentaletra)
                cuentaletra = cuentaletra + 1;
                textDetec = "PELIGRO"
            }
            else if(outputStr.contains("Doorbell") || outputStr.contains("Ding-dong") ){
                println("DETECTO Doorbell - Ding-dong")
                arduinoSignal("D", cuentaletra)
                cuentaletra = cuentaletra + 1;
                textDetec = "TIMBRE CASA"
            } else {
                textDetec = outputStr
            }


            // TODO 4.4: Updating the UI // Aca se puede actualizar lo que muestra en pantalla

            if (outputStr.isNotEmpty()) {
                textView.text = textDetec

            }


        }

    }

    fun arduinoSignal (letra: String?, cuenta: Int) {
        println("arduinoSignal")
        println("Arduino Letra $letra - btSocket: $btSocket - Cuenta: $cuenta")

        if (cuenta==0 || cuenta==1 || cuenta==2)
        {
            control[cuenta] = letra.toString()
            println( control[0]+ control[1]+ control[2])
            runOnUiThread {

                if (letra != null) {
                    MyConexionBT?.write(letra, btSocket)
                }

            }
        }

        if (cuenta==3 || cuenta==4)
        {
            if(control[0]==control[1] && control[0]==control[2] && control[1]==control[2]){
                println("No enviar bluetooth")
                control[0] = letra.toString()
            }
            else{
                runOnUiThread {

                    if (letra != null) {
                        MyConexionBT?.write(letra, btSocket)
                    }

                }

            }


        }

        if (cuenta==5)
        {
            cuentaletra = 0

        }

    }

    public fun createBluetoothSocket (devicesocket: BluetoothDevice?) : BluetoothSocket {
        println("***************  createBluetoothSocket *************")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED)
        {}
        return devicesocket?.createRfcommSocketToServiceRecord(BTMODULEUUID)!!
    }


    @Override
    override fun onPause() {
        super.onPause()
        try { // Cuando se sale de la aplicación esta parte permite que no se deje abierto el socket
            //btSocket!!.close()
        } catch (e2: IOException) {
        }
    }

    //Comprueba que el dispositivo Bluetooth
    //está disponible y solicita que se actie si está desactivado
    private fun VerificarEstadoBT() {
        println("QUE WEA ES ESTO ?1")
        if(btAdapter == null){
            Toast.makeText(baseContext, "El dispositivo no soporta bluetooth", Toast.LENGTH_LONG).show()
        }else{
            if (btAdapter!!.isEnabled()){
                println("QUE WEA ES ESTO ?btAdapter $btAdapter")
            }else {
                var enableBtIntent : Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_PRIVILEGED
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    println("QUE WEA ES ESTO ?")
                    startActivityForResult(enableBtIntent, 1)
                    //return;
                }

            }
        }

    }


    //Crea la clase que permite crear el evento de conexion
    //public class ConnectedThread(btSocket: BluetoothSocket) : Thread() {
    //public class ConnectedThread : Thread() { // OPC1
    //public class ConnectedThread(btSocket: BluetoothSocket?) : Thread() { // OPC2

    public class ConnectedThread(btSocket: BluetoothSocket?) : Thread() {

        private var mmInStream: InputStream? = null
        private var mmOutStream: OutputStream? = null

        public fun ConnectedThread(socket: BluetoothSocket) {

            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null


            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream

            } catch (e: IOException) {}
            mmInStream = tmpIn
            mmOutStream = tmpOut

        }

        override fun run() {
            val byte_in = ByteArray(1)
            // Se mantiene en modo escucha para determinar el ingreso de datos
            while (true) {
                try {
                    mmInStream?.read(byte_in)
                    val ch = byte_in[0].toChar()
                    //bluetoothIn.obtainMessage(handlerState, ch).sendToTarget()
                } catch (e: IOException) {
                    break
                }
            }
        }

        //Envio de trama
        fun write(input: String, socket: BluetoothSocket?) {
            var letra: Int
            try {


                println("WENA ACA ESTAMOS DENTRO del nuevo WRITE")
                var tmpIn: InputStream? = null
                var tmpOut: OutputStream? = null


                try {
                    tmpIn = socket?.inputStream
                    tmpOut = socket?.outputStream

                } catch (e: IOException) {}
                mmInStream = tmpIn
                mmOutStream = tmpOut

                println("********************* ESTAMOS ESCRIBIENDO O NO ? **************** Q WEA **************")
                println(" ESTE es EL INPUT $input")
                println(mmOutStream)

                mmOutStream?.write(input.toByteArray(StandardCharsets.UTF_8))


            } catch (e: IOException) {
                //si no es posible enviar datos se cierra la conexión
                //Toast.makeText(baseContext, "La creacción del Socket fallo", Toast.LENGTH_LONG).show()
                //Toast.makeText(contextClassLoader,"La Conexión fallo", Toast.LENGTH_LONG).show()
                //finish()
            }
        }


    }


}