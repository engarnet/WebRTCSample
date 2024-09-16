package com.engarnet.webrtcsample

import android.content.Context
import android.media.AudioManager
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback

object CommonLogic {
    const val signalingSocketPort = 10001
    const val signalingSocketPort2 = 10002
    fun initAudioDevice(context: Context): JavaAudioDeviceModule {
        val audioRecordErrorCallback: AudioRecordErrorCallback = object : AudioRecordErrorCallback {
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

        // Set audio record state callbacks
        val audioRecordStateCallback: AudioRecordStateCallback = object : AudioRecordStateCallback {
            override fun onWebRtcAudioRecordStart() = Unit
            override fun onWebRtcAudioRecordStop() = Unit
        }

        // Set audio track state callbacks
        val audioTrackStateCallback: AudioTrackStateCallback = object : AudioTrackStateCallback {
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

    fun initCameraVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator()
        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) }
        return if (deviceName != null) {
            enumerator.createCapturer(deviceName, null)
        } else {
            null
        }
    }

    fun setupDeviceAudioManager(context: Context) {
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