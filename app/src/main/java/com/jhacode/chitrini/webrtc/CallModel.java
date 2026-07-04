package com.jhacode.chitrini.webrtc;

/**
 * CallModel
 * Represents a single call entry
 * Future-ready for call type, duration, status
 */
public class CallModel {

    private final String userId;      // other user
    private final boolean isDialed;   // true = outgoing, false = incoming
    private final long timestamp;     // call start time (epoch millis)

    // 🔜 future use (not breaking)
    private long duration = 0L;       // call duration in ms
    private boolean missed = false;   // missed call flag

    public CallModel(String userId, boolean isDialed, long timestamp) {
        this.userId = userId;
        this.isDialed = isDialed;
        this.timestamp = timestamp;
    }

    // ================= GETTERS =================

    public String getUserId() {
        return userId;
    }

    public boolean isDialed() {
        return isDialed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isMissed() {
        return missed;
    }

    // ================= SETTERS (OPTIONAL) =================

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setMissed(boolean missed) {
        this.missed = missed;
    }

    // ================= HELPERS =================

    public boolean isIncoming() {
        return !isDialed;
    }

    public boolean isOutgoing() {
        return isDialed;
    }
}
