package com.engarnet.webrtcsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.engarnet.webrtcsample.ui.theme.WebRTCSampleTheme


class MainActivity : ComponentActivity() {
    private val enabledCameraForResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
            } else {
                Toast.makeText(this, "needs permission of camera ", Toast.LENGTH_LONG).show()
                // no finish() in case no camera access wanted but contact data pasted
            }
        }

    private val enabledMicrophoneForResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
            } else {
                // do not turn on microphone
                Toast.makeText(this, "needs permission of microphone ", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED).not()
        ) {
            enabledCameraForResult.launch(Manifest.permission.CAMERA)
        }

        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED).not()
        ) {
            enabledMicrophoneForResult.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            WebRTCSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        Button(onClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    CallerActivity::class.java
                                )
                            )
                        }) {
                            Text("Caller")
                        }
                        Button(onClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    ReceiverActivity::class.java
                                )
                            )
                        }) {
                            Text("Receiver")
                        }
                    }
                }
            }
        }
    }
}
