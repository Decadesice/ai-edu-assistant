package com.syh.chat.service;

import java.util.List;
import java.util.Locale;

final class ModelPolicy {

    enum Provider {
        OLLAMA
    }

    record ModelCandidate(String modelName, Provider provider, boolean vision, boolean thinking, int concurrency) {
    }

    static final String MODEL_SMALL = "qwen3.5:0.8b";
    static final String MODEL_MEDIUM = "qwen3.5:2b";
    static final String MODEL_LARGE = "qwen3.5:4b";

    static boolean isSmall(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("qwen3.5:0.8b") || s.equals("small") || s.equals("小");
    }

    static boolean isMedium(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("qwen3.5:2b") || s.equals("medium") || s.equals("中");
    }

    static boolean isLarge(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("qwen3.5:4b") || s.equals("large") || s.equals("大");
    }

    static String normalizeForStorage(String raw, boolean hasImage) {
        if (isSmall(raw)) return MODEL_SMALL;
        if (isMedium(raw)) return MODEL_MEDIUM;
        if (isLarge(raw)) return MODEL_LARGE;
        if (raw == null || raw.isBlank()) return MODEL_SMALL;
        return normalizeExplicitModel(raw).modelName();
    }

    static ModelCandidate normalizeExplicitModel(String raw) {
        String m = raw == null ? "" : raw.trim();
        if (m.isEmpty()) {
            return new ModelCandidate(MODEL_SMALL, Provider.OLLAMA, false, true, 0);
        }
        if ("qwen3.5:0.8b".equalsIgnoreCase(m) || "small".equalsIgnoreCase(m)) {
            return new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10);
        }
        if ("qwen3.5:2b".equalsIgnoreCase(m) || "medium".equalsIgnoreCase(m)) {
            return new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5);
        }
        if ("qwen3.5:4b".equalsIgnoreCase(m) || "large".equalsIgnoreCase(m)) {
            return new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1);
        }
        return new ModelCandidate(m, Provider.OLLAMA, false, true, 0);
    }

    static List<ModelCandidate> candidatesForChat(String raw, boolean hasImage) {
        if (isSmall(raw)) {
            return List.of(new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10));
        }
        if (isMedium(raw)) {
            return List.of(new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5));
        }
        if (isLarge(raw)) {
            return List.of(new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1));
        }
        return List.of(normalizeExplicitModel(raw));
    }

    static List<ModelCandidate> candidatesForNonVisionOnce(String raw) {
        if (isSmall(raw)) {
            return List.of(new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10));
        }
        if (isMedium(raw)) {
            return List.of(new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5));
        }
        if (isLarge(raw)) {
            return List.of(new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1));
        }
        ModelCandidate c = normalizeExplicitModel(raw);
        return List.of(c);
    }

    static ModelCandidate advancedDefault(boolean hasImage) {
        return new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1);
    }

    static List<ModelCandidate> autoVisionChatCandidates() {
        return List.of(
                new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1),
                new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5),
                new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10)
        );
    }

    static List<ModelCandidate> autoNonVisionChatCandidates() {
        return List.of(
                new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1),
                new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5),
                new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10)
        );
    }

    static List<ModelCandidate> autoNonVisionOnceCandidates() {
        return List.of(
                new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10),
                new ModelCandidate("qwen3.5:2b", Provider.OLLAMA, false, true, 5),
                new ModelCandidate("qwen3.5:4b", Provider.OLLAMA, false, true, 1)
        );
    }

    static ModelCandidate summaryLowTier(boolean hasImage) {
        return new ModelCandidate("qwen3.5:0.8b", Provider.OLLAMA, false, true, 10);
    }

    private ModelPolicy() {
    }
}
