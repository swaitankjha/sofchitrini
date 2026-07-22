package com.jhacode.chitrini.repository;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.jhacode.chitrini.remote.FirebaseClient;
import com.jhacode.chitrini.utils.DataModel;
import com.jhacode.chitrini.utils.DataModelType;
import com.jhacode.chitrini.utils.EncryptionManager;
import com.jhacode.chitrini.utils.ErrorCallBack;
import com.jhacode.chitrini.utils.NewEventCallBack;
import com.jhacode.chitrini.utils.SuccessCallBack;
import com.jhacode.chitrini.webrtc.MyPeerConnectionObserver;
import com.jhacode.chitrini.webrtc.WebRTCClient;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainRepository {

    private static final String TAG = "MainRepository";
    private Context context;
    private WebRTCClient webRTCClient;
    private String currentUsername;
    private SurfaceViewRenderer remoteView;
    private SurfaceViewRenderer localView;
    private String target;
    private String targetPublicKey;
    private VideoTrack remoteVideoTrack;
    private List<PeerConnection.IceServer> dynamicIceServers = new ArrayList<>();
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private final Gson gson = new Gson();
    private final FirebaseClient firebaseClient;

    public WebRTCRepositoryListener listener;

    private static MainRepository instance;
    private final List<NewEventCallBack> eventCallbacks = new CopyOnWriteArrayList<>();
    private boolean isSubscribed = false;

    private MainRepository() {
        firebaseClient = new FirebaseClient();
    }

    public static MainRepository getInstance() {
        if (instance == null) instance = new MainRepository();
        return instance;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    // ================= LOGIN =================

    public void login(String username, Context context, SuccessCallBack callBack) {
        setContext(context);
        firebaseClient.login(username, () -> {
            this.currentUsername = username;
            firebaseClient.storePublicKey(EncryptionManager.INSTANCE.getPublicKeyBase64());

            // 🔥 Fetch TURN credentials dynamically
            fetchIceServers();

            // 🔥 Start signaling listener if we have pending callbacks
            if (!eventCallbacks.isEmpty() && !isSubscribed) {
                startFirebaseListener();
            }

            // Start service
            try {
                android.content.Intent s = new android.content.Intent(context, com.jhacode.chitrini.service.ChitriniService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(s);
                else context.startService(s);
            } catch (Exception e) { Log.e(TAG, "Service error", e); }

            callBack.onSuccess();
        });
    }

    // ================= DYNAMIC CLIENT MANAGEMENT =================

    private void ensureClientReady() {
        if (webRTCClient != null) return;
        Log.d(TAG, "🚀 Creating fresh WebRTC session...");
        webRTCClient = new WebRTCClient(context, new MyPeerConnectionObserver() {
            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                if (!mediaStream.videoTracks.isEmpty()) {
                    remoteVideoTrack = mediaStream.videoTracks.get(0);
                    if (remoteView != null) remoteVideoTrack.addSink(remoteView);
                }
            }
            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                super.onAddTrack(receiver, streams);
                if (receiver.track() instanceof VideoTrack) {
                    remoteVideoTrack = (VideoTrack) receiver.track();
                    if (remoteView != null) remoteVideoTrack.addSink(remoteView);
                }
            }
            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                Log.d(TAG, "Connection State Change: " + newState);
                if (newState == PeerConnection.PeerConnectionState.CONNECTED && listener != null) listener.webrtcConnected();
                if (newState == PeerConnection.PeerConnectionState.FAILED) {
                    Log.w(TAG, "⚠️ Connection Failed - Attempting ICE Restart...");
                    if (webRTCClient != null) webRTCClient.restartIce();
                }
                if (newState == PeerConnection.PeerConnectionState.DISCONNECTED || newState == PeerConnection.PeerConnectionState.CLOSED) {
                    if (listener != null) listener.webrtcClosed();
                }
            }
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (webRTCClient != null) webRTCClient.sendIceCandidate(iceCandidate, target);
            }
        }, currentUsername, dynamicIceServers);

        webRTCClient.setSignalingListener((data, type) -> encryptAndSend(target, data, type));
    }

    // ================= STATUS =================

    public void setUserStatus(String status) { firebaseClient.setUserStatus(status); }
    public void observeUserStatus(String username, FirebaseClient.StatusCallBack callBack) { firebaseClient.observeUserStatus(username, callBack); }

    public void setTypingStatus(String target, boolean isTyping) { firebaseClient.setTypingStatus(target, isTyping); }
    public void observeUserTyping(String username, FirebaseClient.TypingCallBack callBack) { firebaseClient.observeUserTyping(username, callBack); }

    // ================= CHAT =================

    public void sendChatMessage(String message) {
        if (target != null) {
            String uuid = java.util.UUID.randomUUID().toString();
            sendChatMessage(target, message, uuid);
        }
    }

    public void sendChatMessage(String target, String encryptedSignal, String messageId) {
        firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, encryptedSignal, DataModelType.ChatMessage), () -> {});
    }

    public void sendDeliveryReceipt(String target, String messageId) {
        firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, messageId, DataModelType.MessageDelivered), () -> {});
    }

    public void sendSeenReceipt(String target, String messageId) {
        firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, messageId, DataModelType.MessageSeen), () -> {});
    }

    public void notifyChatReceived(String from, String message) {
        if (listener != null) listener.onChatMessageReceived(from, message);
    }

    public void sendDeleteSignal(String target, String messageId) {
        firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, messageId, DataModelType.DeleteMessage), () -> {});
    }

    // ================= CALL FLOW =================

    public void sendCallRequest(String target, boolean isVideo, ErrorCallBack errorCallBack) {
        this.target = target;
        getPublicKey(target, key -> {
            if (key == null) { errorCallBack.onError(); return; }
            targetPublicKey = key;
            DataModelType type = isVideo ? DataModelType.StartCall : DataModelType.StartAudioCall;
            firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, null, type), errorCallBack);
        });
    }

    public void startCall(String target, boolean isVideo) {
        this.target = target;
        ensureClientReady();
        if (targetPublicKey == null) {
            getPublicKey(target, key -> {
                targetPublicKey = key;
                webRTCClient.call(target, isVideo);
            });
        } else webRTCClient.call(target, isVideo);
    }

    public void answerCall(String target, boolean isVideo) {
        this.target = target;
        ensureClientReady();
        if (targetPublicKey == null) {
            getPublicKey(target, key -> {
                targetPublicKey = key;
                webRTCClient.answer(target, isVideo);
            });
        } else webRTCClient.answer(target, isVideo);
    }

    public void endCall() {
        if (target != null) firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, null, DataModelType.EndCall), () -> {});
        if (webRTCClient != null) {
            webRTCClient.closeConnection();
            webRTCClient = null;
        }
        remoteVideoTrack = null;
        remoteView = null;
        localView = null;
    }

    private void encryptAndSend(String target, String data, DataModelType type) {
        if (targetPublicKey == null) {
            getPublicKey(target, key -> {
                targetPublicKey = key;
                sendActual(target, data, type);
            });
        } else sendActual(target, data, type);
    }

    private void sendActual(String target, String data, DataModelType type) {
        String enc = EncryptionManager.INSTANCE.encrypt(data, targetPublicKey);
        firebaseClient.sendMessageToOtherUser(new DataModel(target, currentUsername, enc != null ? enc : data, type), () -> {});
    }

    // ================= UI =================

    public void initLocalView(SurfaceViewRenderer view) {
        this.localView = view;
        ensureClientReady();
        webRTCClient.initLocalSurfaceView(view);
    }

    public void initRemoteView(SurfaceViewRenderer view) {
        this.remoteView = view;
        ensureClientReady();
        webRTCClient.initRemoteSurfaceView(view);
        if (remoteVideoTrack != null) remoteVideoTrack.addSink(remoteView);
    }

    public void switchCamera() { if (webRTCClient != null) webRTCClient.switchCamera(); }
    public void toggleAudio(Boolean mute) { if (webRTCClient != null) webRTCClient.toggleAudio(mute); }
    public void toggleVideo(Boolean mute) { if (webRTCClient != null) webRTCClient.toggleVideo(mute); }
    public void getPublicKey(String username, FirebaseClient.SuccessCallBackWithData cb) { firebaseClient.getPublicKey(username, cb); }

    // ================= SIGNALING =================

    public void subscribeForLatestEvent(NewEventCallBack callBack) {
        if (!eventCallbacks.contains(callBack)) eventCallbacks.add(callBack);
        if (isSubscribed) return;
        if (currentUsername == null) {
            Log.d(TAG, "Subscribe requested, but waiting for login...");
            return;
        }
        startFirebaseListener();
    }

    private void startFirebaseListener() {
        if (isSubscribed) return;
        isSubscribed = true;
        Log.d(TAG, "🚀 Starting Firebase signaling listener for: " + currentUsername);
        firebaseClient.observeIncomingLatestEvent(model -> {
            handleIncomingSignal(model);
            for (NewEventCallBack cb : eventCallbacks) cb.onNewEventReceived(model);
        });
    }

    public void unsubscribeForLatestEvent(NewEventCallBack callBack) { eventCallbacks.remove(callBack); }

    private void handleIncomingSignal(DataModel model) {
        String decryptedData = model.getData();
        if (model.getType() == DataModelType.Offer || model.getType() == DataModelType.Answer || model.getType() == DataModelType.IceCandidate) {
            decryptedData = EncryptionManager.INSTANCE.decrypt(model.getData());
            if (decryptedData == null) decryptedData = model.getData();
        }

        switch (model.getType()) {
            case StartCall:
            case StartAudioCall:
                this.target = model.getSender();
                getPublicKey(target, key -> targetPublicKey = key);
                break;
            case EndCall:
                if (webRTCClient != null) { webRTCClient.closeConnection(); webRTCClient = null; }
                if (listener != null) listener.webrtcClosed();
                break;
            case Offer:
                ensureClientReady();
                webRTCClient.onRemoteSessionReceived(new SessionDescription(SessionDescription.Type.OFFER, decryptedData));
                break;
            case Answer:
                if (webRTCClient != null) webRTCClient.onRemoteSessionReceived(new SessionDescription(SessionDescription.Type.ANSWER, decryptedData));
                break;
            case IceCandidate:
                if (webRTCClient != null) {
                    IceCandidate candidate = gson.fromJson(decryptedData, IceCandidate.class);
                    webRTCClient.addIceCandidate(candidate);
                }
                break;
        }
    }

    public void deleteAccount(SuccessCallBack callBack) { firebaseClient.deleteAccount(callBack); }

    public void storeProfilePic(String json) {
        if (currentUsername == null) return;
        firebaseClient.storeProfilePic(json);
    }

    public void getProfilePic(String username, FirebaseClient.SuccessCallBackWithData callBack) {
        if (username == null) return;
        firebaseClient.getProfilePic(username, callBack);
    }

    public void checkAppUpdate(FirebaseClient.UpdateCallBack callBack) {
        firebaseClient.checkAppUpdate(callBack);
    }

    // ================= DYNAMIC TURN SERVER FETCHING =================

    private void fetchIceServers() {
        // 🔥 REPLACE THIS WITH YOUR ACTUAL RENDER URL
        String url = "https://chitrini-server.onrender.com/turn-credentials";

        Request request = new Request.Builder()
                .url(url)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch ICE servers: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Unexpected response code: " + response);
                    return;
                }

                String body = response.body().string();
                try {
                    TurnixResponse iceResponse = gson.fromJson(body, TurnixResponse.class);
                    if (iceResponse != null && iceResponse.iceServers != null) {
                        List<PeerConnection.IceServer> newServers = new ArrayList<>();
                        for (TurnixServer s : iceResponse.iceServers) {
                            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(s.urls);
                            if (s.username != null) builder.setUsername(s.username);
                            if (s.credential != null) builder.setPassword(s.credential);
                            newServers.add(builder.createIceServer());
                        }
                        dynamicIceServers = newServers;
                        Log.d(TAG, "✅ Successfully fetched " + dynamicIceServers.size() + " ICE servers from API");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing ICE response: " + e.getMessage());
                }
            }
        });
    }

    private static class TurnixResponse {
        List<TurnixServer> iceServers;
    }

    private static class TurnixServer {
        List<String> urls;
        String username;
        String credential;
    }

    public interface WebRTCRepositoryListener {
        void webrtcConnected();
        void webrtcClosed();
        void onChatMessageReceived(String from, String message);
    }
}
