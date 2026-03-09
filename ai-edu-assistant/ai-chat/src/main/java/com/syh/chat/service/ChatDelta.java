package com.syh.chat.service;

public class ChatDelta {
    private final String contentDelta;
    private final String reasoningDelta;
    private final boolean done;

    public ChatDelta(String contentDelta, String reasoningDelta, boolean done) {
        this.contentDelta = contentDelta == null ? "" : contentDelta;
        this.reasoningDelta = reasoningDelta == null ? "" : reasoningDelta;
        this.done = done;
    }

    public static ChatDelta done() {
        return new ChatDelta("", "", true);
    }

    public String getContentDelta() {
        return contentDelta;
    }

    public String getReasoningDelta() {
        return reasoningDelta;
    }

    public boolean isDone() {
        return done;
    }
}
