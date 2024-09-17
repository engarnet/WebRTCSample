package com.engarnet.webrtcsample

import android.content.Context
import android.media.AudioManager
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback

object CommonLogic {
    const val signalingSocketPort = 10001
    const val signalingSocketPort2 = 10002

    fun createPeerConnectionFactory(context: Context, eglBase: EglBase): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val audioDeviceModule = initPlatformAudioDevice(context)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun initWebRTCVideoAndAudio(
        context: Context,
        eglBase: EglBase,
        factory: PeerConnectionFactory,
        peerConnection: PeerConnection?,
    ): CameraVideoCapturer {
        val audioSource = factory.createAudioSource(MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        })

        val localAudioTrack = factory.createAudioTrack("audio1", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("stream1"))

        val videoCapturer = initPlatformCameraDevice()
        val surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)

        val videoTrack = factory.createVideoTrack("video1", videoSource).apply {
            setEnabled(true)
        }

        val rtpSender = peerConnection?.addTrack(videoTrack, listOf("stream1"))
        // needed to have a high resolution/framerate
        for (encoding in rtpSender!!.parameters.encodings) {
            encoding.maxBitrateBps = 10000 * 1000 * 8 // 10 MB/s - just a high value
            encoding.scaleResolutionDownBy = 2.0
        }
        return videoCapturer
    }

    private fun initPlatformAudioDevice(context: Context): JavaAudioDeviceModule {
        val audioRecordErrorCallback = object : AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) = Unit
            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                errorMessage: String
            ) = Unit

            override fun onWebRtcAudioRecordError(errorMessage: String) = Unit
        }
        val audioTrackErrorCallback: AudioTrackErrorCallback = object : AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) = Unit
            override fun onWebRtcAudioTrackStartError(
                errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                errorMessage: String
            ) = Unit

            override fun onWebRtcAudioTrackError(errorMessage: String) = Unit
        }

        val audioRecordStateCallback = object : AudioRecordStateCallback {
            override fun onWebRtcAudioRecordStart() = Unit
            override fun onWebRtcAudioRecordStop() = Unit
        }

        val audioTrackStateCallback = object : AudioTrackStateCallback {
            override fun onWebRtcAudioTrackStart() = Unit
            override fun onWebRtcAudioTrackStop() = Unit
        }

        return JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioRecordStateCallback(audioRecordStateCallback)
            .setAudioTrackStateCallback(audioTrackStateCallback)
            .createAudioDeviceModule()
    }

    private fun initPlatformCameraDevice(): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator()
        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) }
        return if (deviceName != null) {
            enumerator.createCapturer(deviceName, null)
        } else {
            null
        }
    }

    fun initPlatformDeviceAudioManager(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val audioFocusChangeListener = object : AudioManager.OnAudioFocusChangeListener {
            override fun onAudioFocusChange(focusChange: Int) {
                val typeOfChange = when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                    AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
                    else -> "AUDIOFOCUS_INVALID"
                }
            }
        }

        // Request audio playout focus (without ducking) and install listener for changes in focus.
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = true
        }
    }
}