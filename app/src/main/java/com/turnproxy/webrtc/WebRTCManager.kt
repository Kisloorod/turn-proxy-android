package com.turnproxy.webrtc

import org.webrtc.*
import java.nio.ByteBuffer

class WebRTCManager(
    private val listener: WebRTCListener,
    private val iceServers: List<PeerConnection.IceServer>
) {
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val eglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory

    interface WebRTCListener {
        fun onDataChannelReady()
        fun onDataReceived(data: ByteArray)
        fun onIceCandidate(candidate: IceCandidate)
        fun onConnectionChange(state: PeerConnection.IceConnectionState)
    }

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(null)
            .setEnableInternalTracer(true)
            .setEnableVideoHwAcceleration(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase, true, true))
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 10
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                listener.onConnectionChange(newState)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                listener.onIceCandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onSelectedCandidatePairChanged(localCandidate: IceCandidate, remoteCandidate: IceCandidate) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {
                this@WebRTCManager.dataChannel = channel
                setupDataChannel(channel)
                listener.onDataChannelReady()
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, tracks: Array<MediaStreamTrack>) {}
            override fun onTrack(receiver: RtpReceiver) {}
        })
    }

    private fun setupDataChannel(channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) {
                    listener.onDataChannelReady()
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                listener.onDataReceived(data)
            }
        })
    }

    fun createDataChannel(label: String = "vpn"): Boolean {
        val dcInit = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 3
        }
        
        dataChannel = peerConnection?.createDataChannel(label, dcInit)
        dataChannel?.let { setupDataChannel(it) }
        
        return dataChannel != null
    }

    fun setRemoteDescription(sdp: String, type: String) {
        val sdpType = when (type) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> return
        }

        val sessionDescription = SessionDescription(sdpType, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {}
        }, sessionDescription)
    }

    fun createAnswer(): String? {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String) {}
                    override fun onSetFailure(error: String) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {}
        }, MediaConstraints())

        return peerConnection?.localDescription?.description
    }

    fun addIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun sendData(data: ByteArray): Boolean {
        val buffer = ByteBuffer.wrap(data)
        val dc = dataChannel ?: return false
        
        if (dc.state() != DataChannel.State.OPEN) {
            return false
        }

        val dataChannelBuffer = DataChannel.Buffer(buffer, data.size.toLong())
        return dc.send(dataChannelBuffer)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        eglBase.release()
    }
}
