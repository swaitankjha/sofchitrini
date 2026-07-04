package com.jhacode.chitrini.webrtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallManager {

    private static volatile CallManager instance;

    private final List<CallModel> callLogs =
            Collections.synchronizedList(new ArrayList<>());

    private OnCallUpdateListener listener;

    private CallManager() {}

    public static CallManager getInstance() {
        if (instance == null) {
            synchronized (CallManager.class) {
                if (instance == null) {
                    instance = new CallManager();
                }
            }
        }
        return instance;
    }

    // ================= CALL LOGS =================

    public void addCall(CallModel call) {
        if (call == null) return;

        callLogs.add(0, call);
        notifyUpdate();
    }

    public List<CallModel> getCallLogs() {
        synchronized (callLogs) {
            return new ArrayList<>(callLogs);
        }
    }

    public void clear() {
        callLogs.clear();
        notifyUpdate();
    }

    // ================= LISTENER =================

    public void setOnCallUpdateListener(OnCallUpdateListener listener) {
        this.listener = listener;
    }

    private void notifyUpdate() {
        if (listener != null) {
            listener.onUpdate();
        }
    }

    public interface OnCallUpdateListener {
        void onUpdate();
    }
}