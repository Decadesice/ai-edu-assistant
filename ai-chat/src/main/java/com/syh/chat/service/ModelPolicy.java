package com.syh.chat.service;

import java.util.List;
import java.util.Locale;

final class ModelPolicy {

    enum Provider {
        BIGMODEL,
        SILICONFLOW
    }

    record ModelCandidate(String modelName, Provider provider, boolean vision, boolean thinking, int concurrency) {
    }

    static final String MODE_AUTO = "Auto";
    static final String MODE_ADVANCED = "Advanced";

    static boolean isAuto(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("auto") || s.equals("自动");
    }

    static boolean isAdvanced(String raw) {
        if (raw == null) return false;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("advanced") || s.equals("高级");
    }

    static String normalizeForStorage(String raw, boolean hasImage) {
        if (isAuto(raw)) return MODE_AUTO;
        if (isAdvanced(raw)) return MODE_ADVANCED;
        if (raw == null || raw.isBlank()) return MODE_AUTO;
        return normalizeExplicitModel(raw).modelName();
    }

    static ModelCandidate normalizeExplicitModel(String raw) {
        String m = raw == null ? "" : raw.trim();
        if (m.isEmpty()) {
            return new ModelCandidate(MODE_AUTO, Provider.BIGMODEL, false, true, 0);
        }
        if ("glm-4.6v-flash".equalsIgnoreCase(m) || "glm-4.6v-Flash".equalsIgnoreCase(m)) {
            return new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0);
        }
        if ("glm-4.7-flash".equalsIgnoreCase(m)) {
            return new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1);
        }
        if ("glm-4.1v-thinking-flash".equalsIgnoreCase(m)) {
            return new ModelCandidate("glm-4.1v-thinking-flash", Provider.BIGMODEL, true, true, 5);
        }
        if ("glm-4-flashx-250414".equalsIgnoreCase(m)) {
            return new ModelCandidate("glm-4-flashx-250414", Provider.BIGMODEL, false, false, 10);
        }
        if ("glm-4v-flash".equalsIgnoreCase(m)) {
            return new ModelCandidate("glm-4v-flash", Provider.BIGMODEL, true, false, 10);
        }
        if ("glm-4.1v-9b-thinking".equalsIgnoreCase(m)
                || "glm-4.1v-9b-thinking".equalsIgnoreCase(m)
                || "GLM-4.1V-9B-Thinking".equalsIgnoreCase(m)
                || "THUDM/GLM-4.1V-9B-Thinking".equalsIgnoreCase(m)) {
            return new ModelCandidate("THUDM/GLM-4.1V-9B-Thinking", Provider.SILICONFLOW, true, true, 0);
        }
        return new ModelCandidate(m, Provider.BIGMODEL, false, true, 0);
    }

    static List<ModelCandidate> candidatesForChat(String raw, boolean hasImage) {
        if (isAdvanced(raw)) {
            if (!hasImage) {
                return List.of(
                        new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1),
                        new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0)
                );
            }
            return List.of(advancedDefault(true));
        }
        if (isAuto(raw) || raw == null || raw.isBlank()) {
            return hasImage ? autoVisionChatCandidates() : autoNonVisionChatCandidates();
        }
        return List.of(normalizeExplicitModel(raw));
    }

    static List<ModelCandidate> candidatesForNonVisionOnce(String raw) {
        if (isAdvanced(raw)) {
            return List.of(
                    new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1),
                    new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0)
            );
        }
        if (isAuto(raw) || raw == null || raw.isBlank()) {
            return autoNonVisionOnceCandidates();
        }
        ModelCandidate c = normalizeExplicitModel(raw);
        return List.of(c);
    }

    static ModelCandidate advancedDefault(boolean hasImage) {
        if (hasImage) {
            return new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0);
        }
        return new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1);
    }

    static List<ModelCandidate> autoVisionChatCandidates() {
        return List.of(
                new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0),
                new ModelCandidate("THUDM/GLM-4.1V-9B-Thinking", Provider.SILICONFLOW, true, true, 0),
                new ModelCandidate("glm-4.1v-thinking-flash", Provider.BIGMODEL, true, true, 5),
                new ModelCandidate("glm-4v-flash", Provider.BIGMODEL, true, false, 10)
        );
    }

    static List<ModelCandidate> autoNonVisionChatCandidates() {
        return List.of(
                new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1),
                new ModelCandidate("glm-4-flashx-250414", Provider.BIGMODEL, false, false, 10),
                new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0),
                new ModelCandidate("THUDM/GLM-4.1V-9B-Thinking", Provider.SILICONFLOW, true, true, 0)
        );
    }

    static List<ModelCandidate> autoNonVisionOnceCandidates() {
        return List.of(
                new ModelCandidate("glm-4.7-flash", Provider.BIGMODEL, false, true, 1),
                new ModelCandidate("glm-4-flashx-250414", Provider.BIGMODEL, false, false, 10),
                new ModelCandidate("glm-4.6v-Flash", Provider.BIGMODEL, true, true, 0),
                new ModelCandidate("THUDM/GLM-4.1V-9B-Thinking", Provider.SILICONFLOW, true, true, 0)
        );
    }

    static ModelCandidate summaryLowTier(boolean hasImage) {
        if (hasImage) {
            return new ModelCandidate("glm-4v-flash", Provider.BIGMODEL, true, false, 10);
        }
        return new ModelCandidate("glm-4-flashx-250414", Provider.BIGMODEL, false, false, 10);
    }

    private ModelPolicy() {
    }
}

