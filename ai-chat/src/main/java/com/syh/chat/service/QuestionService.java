package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.syh.chat.dto.QuestionResponse;
import com.syh.chat.entity.GeneratedQuestion;
import com.syh.chat.entity.QuestionAttempt;
import com.syh.chat.entity.WrongQuestionAssignment;
import com.syh.chat.entity.WrongQuestionGroup;
import com.syh.chat.repository.GeneratedQuestionRepository;
import com.syh.chat.repository.QuestionAttemptRepository;
import com.syh.chat.repository.WrongQuestionAssignmentRepository;
import com.syh.chat.repository.WrongQuestionGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuestionService {

    private final GeneratedQuestionRepository questionRepository;
    private final QuestionAttemptRepository attemptRepository;
    private final WrongQuestionGroupRepository wrongQuestionGroupRepository;
    private final WrongQuestionAssignmentRepository wrongQuestionAssignmentRepository;
    private final BigModelService bigModelService;
    private final SiliconFlowService siliconFlowService;
    private final RagRetrieveService ragRetrieveService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionService(
            GeneratedQuestionRepository questionRepository,
            QuestionAttemptRepository attemptRepository,
            WrongQuestionGroupRepository wrongQuestionGroupRepository,
            WrongQuestionAssignmentRepository wrongQuestionAssignmentRepository,
            BigModelService bigModelService,
            SiliconFlowService siliconFlowService,
            RagRetrieveService ragRetrieveService
    ) {
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.wrongQuestionGroupRepository = wrongQuestionGroupRepository;
        this.wrongQuestionAssignmentRepository = wrongQuestionAssignmentRepository;
        this.bigModelService = bigModelService;
        this.siliconFlowService = siliconFlowService;
        this.ragRetrieveService = ragRetrieveService;
    }

    @Transactional
    public List<QuestionResponse> generate(Long userId, Long documentId, String chapterHint, int count, String model, List<String> types) {
        int n = Math.max(1, Math.min(count, 10));
        List<String> normalizedTypes = normalizeTypes(types);
        Set<String> allowedTypes = new HashSet<>(normalizedTypes);

        String query = (chapterHint == null || chapterHint.isBlank())
                ? "请围绕该文档的重点知识点出题"
                : ("请围绕 " + chapterHint.trim() + " 的知识点出题");

        var ctx = ragRetrieveService.retrieveContext(userId, documentId, query, 6);
        StringBuilder ctxText = new StringBuilder();
        int maxCtxChars = 7000;
        for (var s : ctx.getSnippets()) {
            if (ctxText.length() >= maxCtxChars) {
                break;
            }
            String content = s == null ? "" : String.valueOf(s.getContent());
            content = sanitizePromptText(content).trim();
            if (content.isEmpty()) continue;
            int remaining = maxCtxChars - ctxText.length();
            if (remaining <= 0) break;
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            ctxText.append("- ").append(content).append("\n");
        }
        if (ctxText.length() >= maxCtxChars) {
            ctxText.append("\n（材料过长，已截断）\n");
        }

        String typeHint = String.join(", ", normalizedTypes);
        List<QuestionResponse> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int attempts = 0;
        while (out.size() < n && attempts < 5) {
            int remaining = n - out.size();
            int requestCount = Math.min(10, Math.max(remaining, remaining * 2));

            String prompt = ""
                    + "你是备考题目生成助手。请基于提供的“学习材料摘录”，生成" + requestCount + "道题目，并给出答案与解析。\n"
                    + "要求：\n"
                    + "1) 题目要贴合材料内容，避免常识性泛泛出题\n"
                    + "2) 解析要指出材料依据或推理点\n"
                    + "3) 输出必须是严格 JSON 数组，不要 Markdown，不要多余文字\n"
                    + "4) 题型仅允许以下之一：" + typeHint + "；不得输出其它题型\n"
                    + "5) JSON 每个元素必须包含字段：type, topic, stem, answer, explanation\n"
                    + "6) 若 type 为 single 或 multiple：必须提供 options（A/B/C/D 四个选项），answer 为字母或字母数组/逗号分隔\n"
                    + "7) 若 type 为 judgment：不得提供 A/B/C/D；answer 只能是 T 或 F\n"
                    + "8) 若 type 为 short：不得提供 options；answer 为简短文本（<= 80字）\n"
                    + "9) JSON 示例：\n"
                    + "[{\"type\":\"single\",\"topic\":\"...\",\"stem\":\"...\",\"options\":[{\"key\":\"A\",\"text\":\"...\"},{\"key\":\"B\",\"text\":\"...\"},{\"key\":\"C\",\"text\":\"...\"},{\"key\":\"D\",\"text\":\"...\"}],\"answer\":\"A\",\"explanation\":\"...\"}]\n"
                    + "学习材料摘录：\n"
                    + ctxText;

            prompt = sanitizePromptText(prompt);
            BigModelService.BigModelReply reply = chatOnceWithFallback(List.of(new com.syh.chat.model.Message("user", prompt)), model);
            String raw = reply == null ? "" : reply.getContent();
            ArrayNode arr = parseArray(raw);

            for (JsonNode node : arr) {
                if (out.size() >= n) break;
                String type = normalizeType(node.path("type").asText(""));
                if (!allowedTypes.contains(type)) {
                    continue;
                }
                String topic = node.path("topic").asText("");
                String stem = node.path("stem").asText("");
                String answer = readAnswer(node.path("answer"));
                String explanation = node.path("explanation").asText("");
                JsonNode options = node.path("options");

                if (stem.isBlank() || answer.isBlank()) {
                    continue;
                }

                String dedupeKey = type + "|" + stem.trim();
                if (dedupe.contains(dedupeKey)) {
                    continue;
                }

                String optionsJson = "[]";
                if (requiresOptions(type)) {
                    if (!options.isArray()) {
                        continue;
                    }
                    optionsJson = options.toString();
                }

                GeneratedQuestion q = new GeneratedQuestion();
                q.setUserId(userId);
                q.setDocumentId(documentId);
                q.setTopic(truncate(topic.isBlank() ? (chapterHint == null ? "未命名" : chapterHint.trim()) : topic, 120));
                q.setQuestionType(type);
                q.setStem(truncate(stem, 2000));
                q.setOptionsJson(truncate(optionsJson, 4000));
                q.setAnswer(normalizeAnswerByType(type, answer));
                q.setExplanation(truncate(explanation.isBlank() ? "无" : explanation, 2000));
                q.setCreatedAt(LocalDateTime.now());
                q = questionRepository.save(q);
                dedupe.add(dedupeKey);
                out.add(toResponse(q));
            }
            attempts++;
        }

        if (out.isEmpty()) {
            throw new IllegalArgumentException("题目生成失败：模型输出不可解析");
        }
        return out;
    }

    private boolean isOverloadedError(Throwable e) {
        if (e == null) return false;
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException w) {
            int code = w.getStatusCode().value();
            return code == 429 || code == 503;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("HTTP 429") || msg.contains("429") || msg.toLowerCase().contains("too many requests");
    }

    private boolean isDnsResolveError(Throwable e) {
        if (e == null) return false;
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (cur instanceof java.net.UnknownHostException) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("failed to resolve") || msg.contains("unknownhost") || msg.contains("unknown host") || msg.contains("name or service not known");
    }

    private boolean isWebClientRequestError(Throwable e) {
        return e instanceof org.springframework.web.reactive.function.client.WebClientRequestException;
    }

    private String networkFriendlyMessage(Throwable e) {
        String raw = e == null || e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("open.bigmodel.cn")) {
            return "网络异常：无法连接 open.bigmodel.cn。请检查服务器网络/代理/防火墙或 Docker DNS 配置。";
        }
        if (raw.contains("api.siliconflow.cn")) {
            return "网络异常：无法连接 api.siliconflow.cn。请检查服务器网络/代理/防火墙或 Docker DNS 配置。";
        }
        return "网络异常：无法连接大模型服务。请检查服务器网络/代理/防火墙或 Docker DNS 配置。";
    }

    private boolean isHttp400(Throwable e) {
        if (e == null) return false;
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException w) {
            return w.getStatusCode().value() == 400;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("HTTP 400") || msg.contains(" 400 ") || msg.startsWith("400 ");
    }

    private String sanitizePromptText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(c);
                continue;
            }
            if (c < 0x20 || c == 0x7F) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String extractHttpBody(Throwable e) {
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException w) {
            String body = w.getResponseBodyAsString();
            if (body == null) return "";
            String s = body.replaceAll("\\s+", " ").trim();
            if (s.length() > 400) {
                s = s.substring(0, 400) + "...";
            }
            return s;
        }
        return "";
    }

    private BigModelService.BigModelReply chatOnceWithFallback(List<com.syh.chat.model.Message> messages, String rawModel) {
        List<ModelPolicy.ModelCandidate> candidates = ModelPolicy.candidatesForNonVisionOnce(rawModel);
        RuntimeException last = null;
        for (int i = 0; i < candidates.size(); i++) {
            ModelPolicy.ModelCandidate c = candidates.get(i);
            try {
                if (c.provider() == ModelPolicy.Provider.SILICONFLOW) {
                    String s = siliconFlowService.chatOnce(messages, c.modelName()).block();
                    return new BigModelService.BigModelReply(s == null ? "" : s, "");
                }
                return bigModelService.chatOnce(messages, c.modelName()).block();
            } catch (RuntimeException e) {
                last = e;
                boolean canFallbackAuto = (ModelPolicy.isAuto(rawModel) || rawModel == null || rawModel.isBlank()) && i + 1 < candidates.size();
                boolean canFallbackAdvanced = ModelPolicy.isAdvanced(rawModel) && i + 1 < candidates.size() && (isHttp400(e) || isWebClientRequestError(e) || isDnsResolveError(e));
                boolean canFallback = canFallbackAuto || canFallbackAdvanced;
                if (canFallback) {
                    continue;
                }
                if (ModelPolicy.isAdvanced(rawModel) && isOverloadedError(e)) {
                    throw new IllegalArgumentException("当前模型使用人数过多");
                }
                if (isDnsResolveError(e)) {
                    throw new IllegalArgumentException("网络异常：域名解析失败（DNS）。如果通过 Docker 启动，请为 backend 容器配置 dns，或检查宿主机网络/DNS。");
                }
                if (isWebClientRequestError(e)) {
                    throw new IllegalArgumentException(networkFriendlyMessage(e));
                }
                if (isHttp400(e)) {
                    String body = extractHttpBody(e);
                    String suffix = body.isBlank() ? "" : (" 详情: " + body);
                    throw new IllegalArgumentException("题目生成失败：请求参数错误（HTTP 400）。可能是输入过长或模型不可用，请缩小章节范围/减少题量或切换 Auto/Advanced。" + suffix);
                }
                throw e;
            }
        }
        if (last != null) {
            throw last;
        }
        return null;
    }

    public List<QuestionResponse> listRecent(Long userId, Long documentId) {
        List<GeneratedQuestion> list = (documentId == null)
                ? questionRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : questionRepository.findTop50ByUserIdAndDocumentIdOrderByCreatedAtDesc(userId, documentId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public QuestionAttempt attempt(Long userId, Long questionId, String chosen) {
        GeneratedQuestion q = questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
        String type = normalizeType(q.getQuestionType());
        String c = chosen == null ? "" : chosen.trim();
        String normalizedChosen = normalizeAnswerByType(type, c);
        boolean correct = Objects.equals(q.getAnswer(), normalizedChosen);
        QuestionAttempt a = new QuestionAttempt();
        a.setUserId(userId);
        a.setQuestionId(questionId);
        a.setChosen(normalizedChosen);
        a.setCorrect(correct);
        a.setCreatedAt(LocalDateTime.now());
        return attemptRepository.save(a);
    }

    public Map<String, Object> statsOverview(Long userId) {
        List<QuestionAttempt> attempts = attemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long total = attempts.size();
        long correct = attempts.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count();
        long wrong = total - correct;
        double acc = total == 0 ? 0.0 : (double) correct / (double) total;
        Map<String, Object> out = new HashMap<>();
        out.put("totalAttempts", total);
        out.put("correctAttempts", correct);
        out.put("wrongAttempts", wrong);
        out.put("accuracy", acc);
        return out;
    }

    public List<Map<String, Object>> wrongBook(Long userId) {
        return wrongBook(userId, null, false);
    }

    public List<Map<String, Object>> wrongBook(Long userId, Long groupId, boolean ungrouped) {
        List<QuestionAttempt> attempts = attemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, QuestionAttempt> latestAttemptByQuestion = new LinkedHashMap<>();
        for (QuestionAttempt a : attempts) {
            if (!latestAttemptByQuestion.containsKey(a.getQuestionId())) {
                latestAttemptByQuestion.put(a.getQuestionId(), a);
            }
        }
        Set<Long> allowedQuestionIds = buildWrongBookFilterQuestionIds(userId, groupId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var entry : latestAttemptByQuestion.entrySet()) {
            QuestionAttempt a = entry.getValue();
            if (Boolean.TRUE.equals(a.getCorrect())) continue;
            if (allowedQuestionIds != null && !allowedQuestionIds.contains(a.getQuestionId())) continue;
            GeneratedQuestion q = questionRepository.findByIdAndUserId(a.getQuestionId(), userId).orElse(null);
            if (q == null) continue;
            var ctx = ragRetrieveService.retrieveContext(userId, q.getDocumentId(), q.getStem(), 4);
            Long assignedGroupId = wrongQuestionAssignmentRepository.findByUserIdAndQuestionId(userId, q.getId())
                    .map(WrongQuestionAssignment::getGroupId)
                    .orElse(null);
            if (ungrouped && assignedGroupId != null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("question", toResponse(q));
            item.put("chosen", a.getChosen());
            item.put("createdAt", a.getCreatedAt());
            item.put("snippets", ctx.getSnippets());
            item.put("groupId", assignedGroupId);
            out.add(item);
        }
        return out;
    }

    public List<WrongQuestionGroup> listWrongQuestionGroups(Long userId) {
        return wrongQuestionGroupRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public WrongQuestionGroup createWrongQuestionGroup(Long userId, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (n.length() > 60) {
            n = n.substring(0, 60);
        }
        if (wrongQuestionGroupRepository.existsByUserIdAndName(userId, n)) {
            throw new IllegalArgumentException("分组已存在");
        }
        WrongQuestionGroup g = new WrongQuestionGroup();
        g.setUserId(userId);
        g.setName(n);
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        return wrongQuestionGroupRepository.save(g);
    }

    @Transactional
    public WrongQuestionGroup renameWrongQuestionGroup(Long userId, Long groupId, String name) {
        WrongQuestionGroup g = wrongQuestionGroupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
        String n = name == null ? "" : name.trim();
        if (n.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (n.length() > 60) {
            n = n.substring(0, 60);
        }
        g.setName(n);
        g.setUpdatedAt(LocalDateTime.now());
        return wrongQuestionGroupRepository.save(g);
    }

    @Transactional
    public void deleteWrongQuestionGroup(Long userId, Long groupId) {
        WrongQuestionGroup g = wrongQuestionGroupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
        List<WrongQuestionAssignment> items = wrongQuestionAssignmentRepository.findByUserIdAndGroupId(userId, groupId);
        if (!items.isEmpty()) {
            for (WrongQuestionAssignment a : items) {
                a.setGroupId(null);
                a.setUpdatedAt(LocalDateTime.now());
            }
            wrongQuestionAssignmentRepository.saveAll(items);
        }
        wrongQuestionGroupRepository.delete(Objects.requireNonNull(g));
    }

    @Transactional
    public void assignWrongQuestionGroup(Long userId, Long questionId, Long groupId) {
        questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
        Long normalizedGroupId = groupId;
        if (normalizedGroupId != null) {
            wrongQuestionGroupRepository.findByIdAndUserId(normalizedGroupId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
        }
        WrongQuestionAssignment a = wrongQuestionAssignmentRepository.findByUserIdAndQuestionId(userId, questionId)
                .orElseGet(() -> {
                    WrongQuestionAssignment x = new WrongQuestionAssignment();
                    x.setUserId(userId);
                    x.setQuestionId(questionId);
                    return x;
                });
        a.setGroupId(normalizedGroupId);
        a.setUpdatedAt(LocalDateTime.now());
        wrongQuestionAssignmentRepository.save(a);
    }

    private Set<Long> buildWrongBookFilterQuestionIds(Long userId, Long groupId) {
        if (groupId != null) {
            List<WrongQuestionAssignment> list = wrongQuestionAssignmentRepository.findByUserIdAndGroupId(userId, groupId);
            Set<Long> ids = new HashSet<>();
            for (WrongQuestionAssignment a : list) {
                ids.add(a.getQuestionId());
            }
            return ids;
        }
        return null;
    }

    private ArrayNode parseArray(String raw) {
        if (raw == null) {
            return objectMapper.createArrayNode();
        }
        String s = raw.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(s);
            if (node.isArray()) {
                return (ArrayNode) node;
            }
        } catch (Exception ignore) {
        }
        return objectMapper.createArrayNode();
    }

    private QuestionResponse toResponse(GeneratedQuestion q) {
        QuestionResponse resp = new QuestionResponse();
        resp.setId(q.getId());
        resp.setDocumentId(q.getDocumentId());
        resp.setTopic(q.getTopic());
        resp.setType(normalizeType(q.getQuestionType()));
        resp.setStem(q.getStem());
        resp.setAnswer(q.getAnswer());
        resp.setExplanation(q.getExplanation());
        List<QuestionResponse.Option> ops = parseOptions(q.getOptionsJson());
        if (ops.isEmpty() && "judgment".equalsIgnoreCase(q.getQuestionType())) {
            ops = List.of(new QuestionResponse.Option("T", "正确"), new QuestionResponse.Option("F", "错误"));
        }
        resp.setOptions(ops);
        return resp;
    }

    private List<QuestionResponse.Option> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            if (!node.isArray()) return List.of();
            List<QuestionResponse.Option> out = new ArrayList<>();
            for (JsonNode o : node) {
                String key = o.path("key").asText("");
                String text = o.path("text").asText("");
                if (key.isBlank() || text.isBlank()) continue;
                out.add(new QuestionResponse.Option(key, text));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= maxLen) return t;
        return t.substring(0, maxLen);
    }

    private List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of("single");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : types) {
            String nt = normalizeType(t);
            if (!nt.isBlank()) {
                out.add(nt);
            }
        }
        if (out.isEmpty()) {
            return List.of("single");
        }
        return new ArrayList<>(out);
    }

    private String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toLowerCase();
        return switch (t) {
            case "single", "单选", "单选题" -> "single";
            case "multiple", "多选", "多选题" -> "multiple";
            case "judgment", "judge", "判断", "判断题" -> "judgment";
            case "short", "qa", "解答", "解答题", "简答", "简答题" -> "short";
            default -> t.isBlank() ? "single" : t;
        };
    }

    private boolean requiresOptions(String type) {
        return "single".equalsIgnoreCase(type) || "multiple".equalsIgnoreCase(type);
    }

    private String readAnswer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode n : node) {
                String s = n.asText("");
                if (!s.isBlank()) {
                    parts.add(s);
                }
            }
            return String.join(",", parts);
        }
        return node.asText("");
    }

    private String normalizeAnswerByType(String type, String answer) {
        String t = normalizeType(type);
        String a = answer == null ? "" : answer.trim();
        if (a.isBlank()) {
            return "";
        }
        if ("multiple".equals(t)) {
            List<String> parts = new ArrayList<>();
            for (String p : a.toUpperCase().split("[^A-Z]")) {
                if (!p.isBlank()) {
                    parts.add(p);
                }
            }
            Collections.sort(parts);
            return String.join(",", parts);
        }
        if ("single".equals(t)) {
            String up = a.toUpperCase();
            for (char c : up.toCharArray()) {
                if (c >= 'A' && c <= 'D') {
                    return String.valueOf(c);
                }
            }
            return up;
        }
        if ("judgment".equals(t)) {
            String low = a.toLowerCase();
            if (low.equals("t") || low.equals("true") || low.equals("对") || low.equals("正确") || low.equals("是") || low.equals("yes")) {
                return "T";
            }
            if (low.equals("f") || low.equals("false") || low.equals("错") || low.equals("错误") || low.equals("否") || low.equals("no")) {
                return "F";
            }
            if (low.contains("对") || low.contains("正确")) return "T";
            if (low.contains("错") || low.contains("错误")) return "F";
            return a.toUpperCase();
        }
        return a;
    }
}
