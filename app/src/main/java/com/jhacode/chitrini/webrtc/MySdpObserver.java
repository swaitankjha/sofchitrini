package com.jhacode.chitrini.webrtc;

import android.util.Log;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class MySdpObserver implements SdpObserver {

    private static final String TAG = "MySdpObserver";

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        // 🔥 SDP MANGLE START
        String sdp = sessionDescription.description;

        // ==============================
        // 1️⃣ FORCE HIGH VIDEO BITRATE
        // ==============================
        // b=AS:4000 → ~4 Mbps
        if (!sdp.contains("b=AS")) {
            sdp = sdp.replace(
                    "a=mid:video",
                    "a=mid:video\r\nb=AS:4000"
            );
        }

        // ==============================
        // 2️⃣ DISABLE SIMULCAST (1-to-1 CALL)
        // ==============================
        sdp = sdp.replaceAll(
                "a=ssrc-group:FID.*\r\n",
                ""
        );

        // ==============================
        // 3️⃣ PREFER VP9 (FALLBACK VP8)
        // ==============================
        if (sdp.contains("VP8/90000") && !sdp.contains("VP9/90000")) {
            sdp = sdp.replace(
                    "VP8/90000",
                    "VP9/90000\r\nVP8/90000"
            );
        }

        // ==============================
        // 4️⃣ REMOVE BANDWIDTH LIMITS (IF ANY)
        // ==============================
        sdp = sdp.replaceAll(
                "b=TIAS:.*\r\n",
                ""
        );

        SessionDescription newSdp =
                new SessionDescription(
                        sessionDescription.type,
                        sdp
                );

        Log.d(TAG, "SDP modified & ready for setLocalDescription");

        // IMPORTANT: Forward modified SDP
        onCreateSuccessInternal(newSdp);
    }

    /**
     * 🔐 HOOK METHOD
     * WebRTCClient uses this observer and sets SDP itself.
     * This method keeps backward compatibility.
     */
    protected void onCreateSuccessInternal(SessionDescription sessionDescription) {
        // Default no-op
        // Actual setLocalDescription is done in WebRTCClient
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "SDP set successfully.");
    }

    @Override
    public void onCreateFailure(String error) {
        Log.e(TAG, "SDP creation failed: " + error);
    }

    @Override
    public void onSetFailure(String error) {
        Log.e(TAG, "SDP set failed: " + error);
    }
}
