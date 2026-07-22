package com.jhacode.chitrini.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.gson.Gson;
import com.jhacode.chitrini.utils.*;

public class FirebaseClient {

    private final Gson gson = new Gson();
    private final DatabaseReference dbRef =
            FirebaseDatabase.getInstance("https://chitrini-695ed-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String currentUsername;

    private static final String TAG = "FIREBASE";
    private static final String EVENTS_NODE_NAME = "events";
    private static final String PUBLIC_KEY_FIELD_NAME = "public_key";
    private static final String OWNER_UID_FIELD_NAME = "owner_uid";
    private static final String STATUS_FIELD_NAME = "status";
    private static final String PROFILE_PIC_FIELD_NAME = "profile_pic";
    private static final String TYPING_NODE_NAME = "typing"; // 🔥 Changed to 'typing' to match rules

    // ================= LOGIN =================

    public void login(String username, SuccessCallBack callBack) {
        if (username == null || username.trim().isEmpty()) {
            Log.e(TAG, "❌ Username is NULL or empty");
            return;
        }

        final String trimmedUsername = username.trim();

        // 1. Sign in Anonymously
        auth.signInAnonymously().addOnCompleteListener(authTask -> {
            if (authTask.isSuccessful()) {
                String uid = auth.getUid();
                Log.d(TAG, "✅ Authenticated with UID: " + uid);

                // 2. Check/Claim Username
                dbRef.child(trimmedUsername).child(OWNER_UID_FIELD_NAME).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Object val = task.getResult().getValue();
                        String existingUid = val != null ? val.toString() : null;

                        if (existingUid == null || existingUid.equals(uid)) {
                            // 3. Claim it
                            dbRef.child(trimmedUsername).child(OWNER_UID_FIELD_NAME).setValue(uid);
                            dbRef.child(trimmedUsername).child("username").setValue(trimmedUsername)
                                .addOnCompleteListener(saveTask -> {
                                    if (saveTask.isSuccessful()) {
                                        currentUsername = trimmedUsername;
                                        Log.d(TAG, "✅ Logged in as: " + trimmedUsername);
                                        
                                        String publicKey = com.jhacode.chitrini.utils.EncryptionManager.INSTANCE.getPublicKeyBase64();
                                        storePublicKey(publicKey);
                                        
                                        // Set Online Status
                                        setUserStatus("online");
                                        dbRef.child(currentUsername).child(STATUS_FIELD_NAME).onDisconnect().setValue("offline");

                                        callBack.onSuccess();
                                    }
                                });
                        } else {
                            Log.e(TAG, "❌ Username taken by: " + existingUid);
                        }
                    } else {
                        Log.e(TAG, "❌ DB Check Error", task.getException());
                    }
                });
            } else {
                Log.e(TAG, "❌ Auth failed", authTask.getException());
            }
        });
    }

    public void setUserStatus(String status) {
        if (currentUsername == null) return;
        
        // 🔥 Respect Privacy Setting
        android.content.Context ctx = com.jhacode.chitrini.repository.MainRepository.getInstance().getContext();
        if (ctx != null) {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("chitrini_prefs", android.content.Context.MODE_PRIVATE);
            if (!prefs.getBoolean("show_online_status", true) && status.equals("online")) {
                status = "offline"; // Show as offline if privacy enabled
            }
        }

        dbRef.child(currentUsername).child(STATUS_FIELD_NAME).setValue(status);
        
        // 🔥 Clear typing on disconnect
        if (status.equals("offline")) {
            dbRef.child(currentUsername).child(TYPING_NODE_NAME).removeValue();
        }
    }

    public void setTypingStatus(String targetUsername, boolean isTyping) {
        if (currentUsername == null || targetUsername == null) return;
        
        DatabaseReference ref = dbRef.child(currentUsername).child(TYPING_NODE_NAME).child(targetUsername);
        
        if (isTyping) {
            ref.setValue(true);
            ref.onDisconnect().setValue(false);
        } else {
            ref.setValue(false);
        }
    }

    public void observeUserTyping(String otherUsername, TypingCallBack callBack) {
        if (currentUsername == null) return;
        
        // Listen to: users/$otherUsername/typing/$myUsername
        dbRef.child(otherUsername)
             .child(TYPING_NODE_NAME)
             .child(currentUsername)
             .addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isTyping = snapshot.getValue(Boolean.class);
                callBack.onTypingChanged(isTyping != null && isTyping);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public interface TypingCallBack {
        void onTypingChanged(boolean isTyping);
    }

    public void observeUserStatus(String username, StatusCallBack callBack) {
        dbRef.child(username).child(STATUS_FIELD_NAME).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                callBack.onStatusChanged(status != null ? status : "offline");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Status listen denied for " + username);
            }
        });
    }

    public interface StatusCallBack {
        void onStatusChanged(String status);
    }

    public void storePublicKey(String publicKey) {
        if (currentUsername == null) return;
        dbRef.child(currentUsername).child(PUBLIC_KEY_FIELD_NAME).setValue(publicKey);
    }

    public void getPublicKey(String username, SuccessCallBackWithData callBack) {
        if (username == null) return;
        dbRef.child(username.trim()).child(PUBLIC_KEY_FIELD_NAME).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                callBack.onSuccess(snapshot.getValue(String.class));
            } else {
                callBack.onSuccess(null);
            }
        });
    }

    public interface SuccessCallBackWithData {
        void onSuccess(String data);
    }

    // ================= SEND =================

    public void sendMessageToOtherUser(DataModel dataModel, ErrorCallBack errorCallBack){
        if (dataModel == null || dataModel.getTarget() == null) return;

        String target = dataModel.getTarget().trim();
        dbRef.child(target).child(EVENTS_NODE_NAME).push().setValue(gson.toJson(dataModel))
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Event sent to " + target))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Send failed: " + e.getMessage());
                    errorCallBack.onError();
                });
    }

    // ================= LISTEN =================

    public void observeIncomingLatestEvent(NewEventCallBack callBack){
        if (currentUsername == null) {
            Log.e(TAG, "❌ observeIncomingLatestEvent failed: currentUsername is null");
            return;
        }

        Log.d(TAG, "🚀 Attaching ChildEventListener to: " + currentUsername + "/events");
        dbRef.child(currentUsername).child(EVENTS_NODE_NAME).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                try {
                    Object rawData = snapshot.getValue();
                    if (rawData == null) return;

                    String json = (rawData instanceof String) ? (String) rawData : gson.toJson(rawData);
                    DataModel model = gson.fromJson(json, DataModel.class);
                    
                    Log.d(TAG, "📩 Signal Received: " + model.getType() + " from " + model.getSender());

                    callBack.onNewEventReceived(model);
                    snapshot.getRef().removeValue()
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "🗑️ Cleaned processed signal from server"))
                        .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to cleanup signal", e));

                } catch (Exception e){
                    Log.e(TAG, "❌ Signal Parse error", e);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Log.e(TAG, "Signal listen denied: " + e.getMessage());
            }
        });
    }

    // ================= DELETE ACCOUNT =================

    public void deleteAccount(SuccessCallBack callBack) {
        if (currentUsername == null) return;

        dbRef.child(currentUsername).removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (auth.getCurrentUser() != null) {
                    auth.getCurrentUser().delete().addOnCompleteListener(t -> callBack.onSuccess());
                } else {
                    callBack.onSuccess();
                }
            } else {
                Log.e(TAG, "❌ Delete failed");
            }
        });
    }

    public void storeProfilePic(String json) {
        if (currentUsername == null) return;
        dbRef.child(currentUsername).child(PROFILE_PIC_FIELD_NAME).setValue(json);
    }

    public void getProfilePic(String username, SuccessCallBackWithData callBack) {
        if (username == null) return;
        dbRef.child(username.trim()).child(PROFILE_PIC_FIELD_NAME).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                callBack.onSuccess(snapshot.getValue(String.class));
            } else {
                callBack.onSuccess(null);
            }
        });
    }

    public void checkAppUpdate(UpdateCallBack callBack) {
        dbRef.child("app_config").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Long latestVersion = snapshot.child("latest_version").getValue(Long.class);
                String apkUrl = snapshot.child("apk_url").getValue(String.class);
                if (latestVersion != null && apkUrl != null) {
                    callBack.onUpdateInfoReceived(latestVersion.intValue(), apkUrl);
                }
            }
        });
    }

    public interface UpdateCallBack {
        void onUpdateInfoReceived(int latestVersion, String apkUrl);
    }
}
