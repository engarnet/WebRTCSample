package com.engarnet.webrtcsample

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.engarnet.webrtcsample.ui.theme.WebRTCSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.ContinualGatheringPolicy
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.PeerConnectionState
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SdpSemantics
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class CallerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val myIpAddress = manager.connectionInfo.ipAddress
        setContent {
            WebRTCSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Content(
                        modifier = Modifier.padding(innerPadding),
                        myIpAddress = arrayOf<Int>(
                            myIpAddress,
                            myIpAddress shr 8,
                            myIpAddress shr 16,
                            myIpAddress shr 24
                        ).map { it and 0xff }.joinToString("."),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnection?.close()
    }
}

private val eglBase = EglBase.create()
private lateinit var videoSink: VideoSink
private lateinit var videoScreenRenderer: SurfaceViewRenderer

@Composable
private fun Content(
    modifier: Modifier,
    myIpAddress: String,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var ipAddressText by remember {
        mutableStateOf(myIpAddress)
    }
    Column(
        modifier = modifier,
    ) {
        Text(
            text = "Caller Screen",
            fontSize = 24.sp,
        )
        Text(
            text = "my ip: " + myIpAddress,
            color = Color.Red,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "dest ip: ",
            )
            TextField(
                modifier = Modifier.weight(1f, false),
                value = ipAddressText,
                placeholder = { Text("Enter Receiver IP Address") },
                keyboardActions = KeyboardActions(onDone = {}),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    autoCorrect = false,
                ),
                singleLine = true,
                onValueChange = {
                    ipAddressText = it
                },
            )
            Button(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusTarget(),
                onClick = {
                    focusRequester.requestFocus()
                    call(context, ipAddressText)
                }
            ) {
                Text(
                    text = "start calling",
                )
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val renderer = SurfaceViewRenderer(it)
                videoSink = renderer
                videoScreenRenderer = renderer
                renderer.init(eglBase.eglBaseContext, null)
                renderer
            }
        )
    }
}

private var peerConnection: PeerConnection? = null
private var videoCapturer: CameraVideoCapturer? = null

private fun call(
    context: Context,
    ipAddress: String,
) {
    val factory = CommonLogic.createPeerConnectionFactory(context, eglBase)

    peerConnection = factory.createPeerConnection(
        RTCConfiguration(emptyList()).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE
        }, object : PeerConnection.Observer {
            override fun onIceGatheringChange(p0: IceGatheringState?) {
                Log.d("WebRTCSample", "onIceGatheringChange: $p0")
                if (p0 == IceGatheringState.COMPLETE) {
                    val offer = peerConnection?.localDescription?.description ?: return
                    GlobalScope.launch {
                        withContext(Dispatchers.IO) {
                            val answer = doSignaling(offer, ipAddress)
                            withContext(Dispatchers.Main) {
                                peerConnection!!.setRemoteDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        Log.d("WebRTCSample", "onSetSuccess()")
                                        CommonLogic.initPlatformDeviceAudioManager(context)
                                        videoCapturer?.startCapture(1280, 720, 25)
                                    }

                                    override fun onSetFailure(s: String) {
                                        Log.e("WebRTCSample", "onSetFailure() $s")
                                    }

                                    override fun onCreateSuccess(p0: SessionDescription?) = Unit
                                    override fun onCreateFailure(p0: String?) = Unit
                                }, SessionDescription(SessionDescription.Type.ANSWER, answer))
                            }
                        }
                    }
                }
            }

            override fun onIceConnectionChange(p0: IceConnectionState?) {
                Log.d("WebRTCSample", "onIceConnectionChange: $p0")
                when (p0) {
                    IceConnectionState.FAILED,
                    IceConnectionState.DISCONNECTED,
                    IceConnectionState.CLOSED -> {
                        // TODO: connection lost
                    }

                    else -> Unit
                }
            }

            override fun onAddStream(p0: MediaStream?) {
                Log.d("WebRTCSample", "onAddStream: $p0")
                p0?.videoTracks?.get(0)?.addSink(videoSink)
            }

            override fun onConnectionChange(p0: PeerConnectionState?) {
                Log.d("WebRTCSample", "onConnectionChange: $p0")
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
            override fun onIceCandidate(p0: IceCandidate?) = Unit
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
            override fun onRemoveStream(p0: MediaStream?) = Unit
            override fun onDataChannel(p0: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
        })

    videoCapturer = CommonLogic.initWebRTCVideoAndAudio(context, eglBase, factory, peerConnection)

    peerConnection?.createOffer(object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {
            Log.d("WebRTCSample", "onCreateSuccess: $p0")
            peerConnection!!.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(p0: String?) = Unit
                override fun onSetFailure(p0: String?) {
                    val a = 0
                }
            }, p0)
        }

        override fun onSetSuccess() = Unit
        override fun onCreateFailure(p0: String?) = Unit
        override fun onSetFailure(p0: String?) = Unit
    }, MediaConstraints().apply {
        optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    })
}

private fun doSignaling(
    offer: String,
    ipAddress: String,
): String {
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress(ipAddress, CommonLogic.signalingSocketPort), 5000)
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        writer.write(offer)
        writer.flush()
        writer.close()

        val serverSocket = ServerSocket(CommonLogic.signalingSocketPort2)
        val socket2 = serverSocket.accept()
        val reader = BufferedReader(InputStreamReader(socket2.getInputStream()))
        var answerText = ""
        while (true) {
            val data = reader.readLine()
            if (data == null) {
                break
            }
            answerText = "$answerText$data\n"
        }
        socket2.close()
        return answerText
    } catch (throwable: Throwable) {
        Log.e("WebRTCSample", "doSignaling error", throwable)
        return ""
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewCaller() {
    WebRTCSampleTheme {
        Content(
            modifier = Modifier,
            myIpAddress = "192.168.1.1",
        )
    }
}
