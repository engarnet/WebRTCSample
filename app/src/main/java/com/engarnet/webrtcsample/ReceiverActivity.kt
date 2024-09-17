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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.engarnet.webrtcsample.ui.theme.WebRTCSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
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
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress

class ReceiverActivity : ComponentActivity() {
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
    Column(
        modifier = modifier,
    ) {
        Text(
            text = "Receiver Screen",
            fontSize = 24.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "my ip: " + myIpAddress,
                color = Color.Red,
            )
            Button(onClick = {
                GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        waitSignaling(context)
                    }
                }
            }) {
                Text(
                    text = "start receiving",
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

private fun waitSignaling(
    context: Context,
) {
    val serverSocket = ServerSocket(CommonLogic.signalingSocketPort)
    val socket = serverSocket.accept()
    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    var offerText = ""
    while (true) {
        val data = reader.readLine()
        if (data == null) {
            break
        }
        offerText = "$offerText$data\n"
    }
    reader.close()
    receive(context, offerText, socket.remoteSocketAddress)
}

private fun sendAnswer(
    context: Context,
    answer: String,
    remoteAddress: SocketAddress,
) {
    try {
        val socket = Socket()
        val inetSocketAddress = remoteAddress as InetSocketAddress
        val address = InetSocketAddress(inetSocketAddress.address, CommonLogic.signalingSocketPort2)
        socket.connect(address, 5000)
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        writer.write(answer)
        writer.flush()
        writer.close()
    } catch (throwable: Throwable) {
        Log.e("WebRTCSample", "sendAnswer", throwable)
    }
}

private var videoCapturer: CameraVideoCapturer? = null
private fun receive(
    context: Context,
    offer: String,
    remoteAddress: SocketAddress,
) {
    val factory = CommonLogic.createPeerConnectionFactory(context, eglBase)

    val peerConnection = factory.createPeerConnection(
        RTCConfiguration(emptyList()).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE
        }, object : PeerConnection.Observer {
            override fun onIceGatheringChange(p0: IceGatheringState?) {
                Log.d("WebRTCSample", "onIceGatheringChange: $p0")
                if (p0 == IceGatheringState.COMPLETE) {
                    // TODO: connection established
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

    peerConnection!!.setRemoteDescription(object : SdpObserver {
        override fun onSetSuccess() {
            peerConnection!!.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection.setLocalDescription(
                        object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) = Unit
                            override fun onSetSuccess() = Unit
                            override fun onCreateFailure(p0: String?) = Unit
                            override fun onSetFailure(p0: String?) = Unit
                        },
                        sessionDescription,
                    )
                    GlobalScope.launch {
                        withContext(Dispatchers.IO) {
                            sendAnswer(context, sessionDescription.description, remoteAddress)
                            CommonLogic.initPlatformDeviceAudioManager(context)
                            videoCapturer?.startCapture(1280, 720, 25)
                        }
                    }
                }

                override fun onSetSuccess() = Unit
                override fun onCreateFailure(s: String) = Unit
                override fun onSetFailure(p0: String?) = Unit
            }, MediaConstraints().apply {
                optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
                optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
                optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
            })
        }

        override fun onCreateSuccess(p0: SessionDescription?) = Unit
        override fun onCreateFailure(p0: String?) = Unit
        override fun onSetFailure(p0: String?) = Unit
    }, SessionDescription(SessionDescription.Type.OFFER, offer))
}