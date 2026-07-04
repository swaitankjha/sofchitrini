package com.jhacode.chitrini.webrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.webrtc.*;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebRTCClient {

    private static final String TAG = "WebRTC_PRO";

    private final Context context;
    private final String username;

    private static EglBase rootEglBase; // 🔥 Global static EglBase, NEVER released during call
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private AudioSource audioSource;

    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private boolean remoteSet = false;

    private final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
    private WebRTCSignalListener signalingListener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Timer statsTimer;

    public interface WebRTCSignalListener {
        void onSignalReady(String data, com.jhacode.chitrini.utils.DataModelType type);
    }

    public void setSignalingListener(WebRTCSignalListener listener) {
        this.signalingListener = listener;
    }

    public synchronized static EglBase getRootEglBase() {
        if (rootEglBase == null) {
            rootEglBase = EglBase.create();
        }
        return rootEglBase;
    }

    public WebRTCClient(Context context, PeerConnection.Observer observer, String username, List<PeerConnection.IceServer> customIceServers) {
        this.context = context;
        this.username = username;

        executor.execute(() -> {
            try {
                initFactory();
                setupIceServers(customIceServers);

                PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
                rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
                rtcConfig.iceCandidatePoolSize = 10;
                rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
                rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
                rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
                rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;

                peerConnection = factory.createPeerConnection(rtcConfig, observer);
                Log.d(TAG, "WebRTC Engine Started");

                startStatsTimer();
            } catch (Exception e) {
                Log.e(TAG, "Init failed", e);
            }
        });
    }

    private void setupIceServers(List<PeerConnection.IceServer> custom) {
        if (custom != null && !custom.isEmpty()) {
            iceServers.addAll(custom);
        }
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
    }

    private void initFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        );

        AudioDeviceModule adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(getRootEglBase().getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(getRootEglBase().getEglBaseContext()))
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        adm.release();
    }

    public synchronized void startLocalStreaming(boolean isVideo) {
        executor.execute(() -> {
            try {
                while (factory == null) { Thread.sleep(100); }

                // Audio - Always needed
                if (localAudioTrack == null) {
                    MediaConstraints audioConstraints = new MediaConstraints();
                    audioSource = factory.createAudioSource(audioConstraints);
                    localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
                    peerConnection.addTrack(localAudioTrack);
                }

                // Video - Only if needed and not already created
                if (isVideo && localVideoTrack == null) {
                    videoCapturer = getVideoCapturer(context);
                    videoSource = factory.createVideoSource(videoCapturer.isScreencast());
                    SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", getRootEglBase().getEglBaseContext());
                    videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());

                    videoCapturer.startCapture(1280, 720, 30);
                    localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
                    peerConnection.addTrack(localVideoTrack);
                }

                Log.d(TAG, "Local capture started (Video: " + isVideo + ")");
            } catch (Exception e) { Log.e(TAG, "Streaming error", e); }
        });
    }

    private VideoCapturer getVideoCapturer(Context context) {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) return enumerator.createCapturer(deviceName, null);
        }
        return enumerator.createCapturer(deviceNames[0], null);
    }

    public void call(String target, boolean isVideo) {
        startLocalStreaming(isVideo);
        executor.execute(() -> {
            try {
                int retries = 0;
                while (localAudioTrack == null && retries < 20) { Thread.sleep(100); retries++; }

                MediaConstraints sdpConstraints = new MediaConstraints();
                sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo ? "true" : "false"));
                sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

                peerConnection.createOffer(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        peerConnection.setLocalDescription(new MySdpObserver() {}, sdp);
                        if (signalingListener != null) signalingListener.onSignalReady(sdp.description, com.jhacode.chitrini.utils.DataModelType.Offer);
                    }
                }, sdpConstraints);
            } catch (Exception e) { Log.e(TAG, "Call failed", e); }
        });
    }

    public void answer(String target, boolean isVideo) {
        startLocalStreaming(isVideo);
        executor.execute(() -> {
            try {
                int retries = 0;
                while (localAudioTrack == null && retries < 10) { Thread.sleep(100); retries++; }

                MediaConstraints sdpConstraints = new MediaConstraints();
                sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo ? "true" : "false"));
                sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                peerConnection.createAnswer(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        peerConnection.setLocalDescription(new MySdpObserver() {}, sdp);
                        if (signalingListener != null) signalingListener.onSignalReady(sdp.description, com.jhacode.chitrini.utils.DataModelType.Answer);
                    }
                }, sdpConstraints);
            } catch (Exception e) { Log.e(TAG, "Answer failed", e); }
        });
    }

    public void onRemoteSessionReceived(SessionDescription sdp) {
        executor.execute(() -> {
            try {
                while (peerConnection == null) Thread.sleep(100);
                peerConnection.setRemoteDescription(new MySdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        remoteSet = true;
                        synchronized (pendingCandidates) {
                            for (IceCandidate c : pendingCandidates) peerConnection.addIceCandidate(c);
                            pendingCandidates.clear();
                        }
                    }
                }, sdp);
            } catch (Exception e) { Log.e(TAG, "Remote SDP error", e); }
        });
    }

    public void sendIceCandidate(IceCandidate candidate, String target) {
        if (signalingListener != null) signalingListener.onSignalReady(new com.google.gson.Gson().toJson(candidate), com.jhacode.chitrini.utils.DataModelType.IceCandidate);
    }

    public void addIceCandidate(IceCandidate candidate) {
        executor.execute(() -> {
            if (remoteSet) peerConnection.addIceCandidate(candidate);
            else { synchronized (pendingCandidates) { pendingCandidates.add(candidate); } }
        });
    }

    public void initLocalSurfaceView(SurfaceViewRenderer view) {
        if (view == null) return;
        mainHandler.post(() -> {
            try {
                view.init(getRootEglBase().getEglBaseContext(), null);
                view.setMirror(true);
                view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                checkAndAddLocalSink(view);
            } catch (Exception e) { Log.e(TAG, "Local view error", e); }
        });
    }

    private void checkAndAddLocalSink(SurfaceViewRenderer view) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (localVideoTrack != null) {
                    localVideoTrack.addSink(view);
                    Log.d(TAG, "✅ Local sink attached");
                } else {
                    mainHandler.postDelayed(this, 300);
                }
            }
        });
    }

    public void initRemoteSurfaceView(SurfaceViewRenderer view) {
        if (view == null) return;
        mainHandler.post(() -> {
            try {
                view.init(getRootEglBase().getEglBaseContext(), null);
                view.setMirror(false);
                view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            } catch (Exception e) { Log.e(TAG, "Remote view error", e); }
        });
    }

    public void switchCamera() { if (videoCapturer instanceof CameraVideoCapturer) ((CameraVideoCapturer) videoCapturer).switchCamera(null); }
    public void toggleAudio(boolean mute) { if (localAudioTrack != null) localAudioTrack.setEnabled(!mute); }
    public void toggleVideo(boolean enable) { if (localVideoTrack != null) localVideoTrack.setEnabled(enable); }

    public void restartIce() {
        executor.execute(() -> {
            if (peerConnection == null) return;
            Log.d(TAG, "🔄 ICE Restart...");
            MediaConstraints c = new MediaConstraints();
            c.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
            c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
            c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            peerConnection.createOffer(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    peerConnection.setLocalDescription(new MySdpObserver() {}, sdp);
                    if (signalingListener != null) signalingListener.onSignalReady(sdp.description, com.jhacode.chitrini.utils.DataModelType.Offer);
                }
            }, c);
        });
    }

    private void startStatsTimer() {
        statsTimer = new Timer();
        statsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (peerConnection == null) return;
                peerConnection.getStats(report -> {
                    Map<String, RTCStats> map = report.getStatsMap();
                    for (RTCStats s : map.values()) {
                        if ("candidate-pair".equals(s.getType())) {
                            if ("succeeded".equals(s.getMembers().get("state"))) {
                                Log.d(TAG, "Ping: " + s.getMembers().get("currentRoundTripTime") + "s");
                            }
                        }
                    }
                });
            }
        }, 5000, 5000);
    }

    public void closeConnection() {
        if (statsTimer != null) statsTimer.cancel();
        executor.execute(() -> {
            try {
                if (videoCapturer != null) { videoCapturer.stopCapture(); videoCapturer.dispose(); videoCapturer = null; }
                if (peerConnection != null) { peerConnection.close(); peerConnection = null; }
                localVideoTrack = null;
                localAudioTrack = null;
                remoteSet = false;
                Log.d(TAG, "Session cleaned up");
            } catch (Exception e) { Log.e(TAG, "Cleanup error", e); }
        });
    }
}
