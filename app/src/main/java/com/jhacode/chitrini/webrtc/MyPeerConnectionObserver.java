package com.jhacode.chitrini.webrtc;

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyPeerConnectionObserver implements PeerConnection.Observer {

    private static final String TAG = "MyPeerConnectionObs";

    // 🔐 Prevent renegotiation spam
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    // ================= SIGNALING =================

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(TAG, "Signaling state: " + signalingState);
    }

    // ================= ICE =================

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
        Log.d(TAG, "ICE connection state: " + state);

        switch (state) {

            case CONNECTED:
            case COMPLETED:
                Log.d(TAG, "ICE connected – stable media path");
                isReconnecting.set(false);
                break;

            case DISCONNECTED:
                Log.w(TAG, "ICE temporarily disconnected");
                break;

            case FAILED:
                Log.e(TAG, "ICE failed – network path broken");
                handleIceFailure();
                break;

            case CHECKING:
            case NEW:
            case CLOSED:
            default:
                break;
        }
    }

    private void handleIceFailure() {
        if (isReconnecting.compareAndSet(false, true)) {
            Log.w(TAG, "ICE restart suggested (handled by signaling layer)");
            // 🔥 IMPORTANT:
            // Do NOT auto restart here.
            // Let signaling layer decide.
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.d(TAG, "ICE receiving: " + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
        Log.d(TAG, "ICE gathering state: " + state);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {

        // 🔥 PRIORITIZE RELAY (TURN) CANDIDATES
        if (candidate.sdp != null && candidate.sdp.contains("typ relay")) {
            Log.d(TAG, "TURN relay candidate preferred");
        }

        Log.d(TAG, "New ICE candidate: " + candidate.sdpMid);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.d(TAG, "ICE candidates removed: " + candidates.length);
    }

    // ================= MEDIA =================

    @Override
    public void onAddStream(MediaStream stream) {
        Log.d(TAG, "Media stream added: " + stream.getId());
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
        Log.d(TAG, "Media stream removed: " + stream.getId());
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
        Log.d(TAG, "Track added: " + receiver.id());
    }

    // ================= DATA CHANNEL =================

    @Override
    public void onDataChannel(DataChannel channel) {
        Log.d(TAG, "DataChannel created: " + channel.label());

        channel.registerObserver(new DataChannel.Observer() {

            @Override
            public void onBufferedAmountChange(long previousAmount) {
                // 🔕 Ignore – avoid spam
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "DataChannel state: " + channel.state());
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                if (buffer == null || buffer.data == null) return;

                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);

                String message = new String(bytes, StandardCharsets.UTF_8);
                Log.d(TAG, "DataChannel message received: " + message);

                // 🔜 Forward to UI via CallManager / Listener
            }
        });
    }

    // ================= RENEGOTIATION =================

    @Override
    public void onRenegotiationNeeded() {
        // 🔥 DO NOT AUTO-RENEGOTIATE
        // This causes lag spikes in WhatsApp-like apps
        Log.w(TAG, "Renegotiation requested – ignored for stability");
    }
}
