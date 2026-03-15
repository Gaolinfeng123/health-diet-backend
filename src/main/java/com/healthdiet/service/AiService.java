package com.healthdiet.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.entity.*;
import com.healthdiet.mapper.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final long QUICK_QUESTION_CACHE_TTL_MILLIS = 30 * 60 * 1000L;
    private static final long QUICK_QUESTION_TIMEOUT_MILLIS = 1800L;
    private static final int MAX_DAILY_CHAT_TURNS = 12;

    @Value("${ai.siliconflow.api-key}") private String apiKey;
    @Value("${ai.siliconflow.base-url}") private String apiUrl;
    @Value("${ai.siliconflow.model}") private String modelName;

    @Autowired private UserMapper userMapper;
    @Autowired private DietRecordMapper dietRecordMapper;
    @Autowired private FoodMapper foodMapper;
    @Autowired private AiReportMapper aiReportMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedQuickQuestions> quickQuestionCache = new ConcurrentHashMap<>();
    private final Map<Long, DailyConversationMemory> dailyConversationCache = new ConcurrentHashMap<>();

    /**
     * 1. 获取或生成今日评估 (精简版)
     */
    public String getOrGenerateDailyReport(Long userId) {
        LocalDate today = LocalDate.now();
        AiReport report = aiReportMapper.selectOne(new QueryWrapper<AiReport>().eq("user_id", userId).eq("report_date", today));
        if (report != null) return report.getContent();

        User user = userMapper.selectById(userId);
        String history = getYesterdaySummary(userId);

        // 根据是否是慢性病用户，切换不同的系统指令
        boolean isChronicUser = user.getTarget() != null && user.getTarget() >= 2;
        String sysMsg;
        if (isChronicUser) {
            sysMsg = "你是一位温柔且专业的慢性病营养师。请根据数据生成昨日评估。" +
                    "要求：严禁寒暄，字数<150字，必须结合用户的慢性病目标给出针对性建议，严格按此格式：\n" +
                    "【昨日总结】：总热量与结构评价\n" +
                    "【病情关注】：结合慢性病目标指出饮食风险点\n" +
                    "【今日建议】：3条针对慢性病的极简饮食对策";
        } else {
            sysMsg = "你是一位温柔且专业的营养师。请根据数据生成昨日评估。" +
                    "要求：严禁寒暄，字数<130字，严格按此格式：\n" +
                    "【昨日总结】：总热量与结构评价\n" +
                    "【核心问题】：指出1-2个痛点\n" +
                    "【今日建议】：3条极简对策";
        }

        String userMsg = String.format("身高%.0f, 体重%.1f, 目标:%s。昨日记录：%s",
                user.getHeight(), user.getWeight(), getTargetText(user.getTarget()), history);

        String aiReply = syncCallAi(sysMsg, userMsg);

        AiReport newReport = new AiReport();
        newReport.setUserId(userId);
        newReport.setReportDate(today);
        newReport.setContent(aiReply);
        aiReportMapper.insert(newReport);

        return aiReply;
    }

    /**
     * 2. 基于报告的流式对话
     */
    public SseEmitter streamChat(Long userId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);
        String dailyReport = getOrGenerateDailyReport(userId);
        User user = userMapper.selectById(userId);
        String conversationContext = "";

        // 慢性病用户加入额外的限制提示，防止 AI 给出不适合的建议
        boolean isChronicUser = user.getTarget() != null && user.getTarget() >= 2;
        String chronicReminder = isChronicUser
                ? "\n注意：该用户为慢性病患者（" + getTargetText(user.getTarget()) + "），回答时必须严格遵循对应的饮食禁忌，不得给出与病情相悖的建议。"
                : "";

        String systemPrompt = String.format(
                "你是营养师助手。用户信息：身高%.0f, 体重%.1f, 目标:%s。\n" +
                        "昨日评估报告：\n%s\n" +
                        "请结合此报告简短回答用户提问。如果用户问的与健康无关，请礼貌拒绝。%s",
                user.getHeight(), user.getWeight(), getTargetText(user.getTarget()), dailyReport, chronicReminder
        );

        String promptWithContext = conversationContext.isBlank()
                ? "当前问题：\n" + userMessage
                : "以下是用户今天稍早的对话记录，仅限今天：\n"
                + conversationContext
                + "\n\n当前问题：\n"
                + userMessage;
        sendStreamRequest(systemPrompt, userMessage, emitter);
        return emitter;
    }

    // --- 工具方法 ---

    public List<String> generateQuickQuestions(User user, AnalysisReport report) {
        List<String> fallback = buildFallbackQuickQuestions(user, report);
        if (user == null || report == null) {
            return fallback;
        }
        String cacheKey = buildQuickQuestionCacheKey(user, report);
        List<String> cachedQuestions = getCachedQuickQuestions(cacheKey);
        if (cachedQuestions != null) {
            return cachedQuestions;
        }

        String systemPrompt = """
                你是健康饮食产品里的问题推荐助手。
                请基于用户当天的饮食分析报告，生成 5 条适合直接点击的中文快捷问题。
                输出要求：
                1. 只返回 JSON 数组。
                2. 数组里必须正好有 5 个字符串。
                3. 每条都使用用户口吻，长度简短。
                4. 问题要围绕热量、三餐、宏量营养、当前目标、下一步调整。
                5. 不要重复，不要解释。
                """;

        String userPrompt = String.format(
                Locale.ROOT,
                """
                用户目标：%s
                BMI：%.1f
                今日总热量：%.1f kcal
                目标热量：%.1f kcal
                热量差值：%.1f kcal
                蛋白质：%.1fg / %.1fg
                脂肪：%.1fg / %.1fg
                碳水：%.1fg / %.1fg
                报告总览：%s
                重点发现：%s
                调整建议：%s
                """,
                getTargetText(user.getTarget()),
                safe(report.getBmi()),
                safe(report.getTotalCalories()),
                safe(report.getRecommendCalories()),
                safe(report.getDiff()),
                safe(report.getTotalProtein()),
                safe(report.getTargetProtein()),
                safe(report.getTotalFat()),
                safe(report.getTargetFat()),
                safe(report.getTotalCarb()),
                safe(report.getTargetCarb()),
                defaultText(report.getOverview(), "今日分析已生成。"),
                String.join("；", safeList(report.getHighlights())),
                String.join("；", safeList(report.getSuggestions()))
        );

        try {
            List<String> parsed = normalizeQuickQuestions(parseQuickQuestions(syncCallAi(systemPrompt, userPrompt, QUICK_QUESTION_TIMEOUT_MILLIS)));
            if (parsed.size() == 5) {
                cacheQuickQuestions(cacheKey, parsed);
                return parsed;
            }
        } catch (Exception e) {
            log.warn("quick question generation failed for userId={} date={}: {}", user.getId(), report.getAnalysisDate(), e.getMessage());
        }
        return fallback;
    }

    private String syncCallAi(String sys, String user) {
        return syncCallAi(sys, user, 0L);
    }

    private String syncCallAi(String sys, String user, long timeoutMillis) {
        String json = buildJson(sys, user, false);
        Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
        OkHttpClient requestClient = timeoutMillis > 0
                ? client.newBuilder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
                : client;
        try (Response response = requestClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("AI response is empty or unsuccessful: " + response.code());
            }
            Map map = objectMapper.readValue(response.body().string(), Map.class);
            return (String) ((Map)((Map)((List)map.get("choices")).get(0)).get("message")).get("content");
        } catch (Exception e) { return "今日暂无评估数据。"; }
    }

    private void sendStreamRequest(String sys, String user, SseEmitter emitter) {
        Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(buildJson(sys, user, true), MediaType.parse("application/json"))).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { emitter.complete(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    while (!body.source().exhausted()) {
                        String line = body.source().readUtf8Line();
                        if (line != null && line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data.trim())) { emitter.complete(); break; }
                            String content = extractContent(data);
                            if (content != null) {
                                sendStreamChunk(emitter, content);
                            }
                        }
                    }
                } catch (Exception e) { emitter.complete(); }
            }
        });
    }

    private void sendStreamRequest(String sys, String user, SseEmitter emitter, Long userId, LocalDate date) {
        Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(buildJson(sys, user, true), MediaType.parse("application/json"))).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { emitter.complete(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                StringBuilder assistantReply = new StringBuilder();
                boolean appended = false;
                try (ResponseBody body = response.body()) {
                    while (!body.source().exhausted()) {
                        String line = body.source().readUtf8Line();
                        if (line != null && line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data.trim())) {
                                appendConversationTurn(userId, date, "assistant", assistantReply.toString());
                                appended = true;
                                emitter.complete();
                                break;
                            }
                            String content = extractContent(data);
                            if (content != null) {
                                assistantReply.append(content);
                                sendStreamChunk(emitter, content);
                            }
                        }
                    }
                    if (!appended) {
                        appendConversationTurn(userId, date, "assistant", assistantReply.toString());
                    }
                } catch (Exception e) { emitter.complete(); }
            }
        });
    }

    private void sendStreamChunk(SseEmitter emitter, String content) throws IOException {
        String normalized = content.replace("\r\n", "\n");
        emitter.send(
                SseEmitter.event()
                        .name("message")
                        .data(normalized, org.springframework.http.MediaType.TEXT_PLAIN)
        );
    }

    private String getYesterdaySummary(Long userId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<DietRecord> records = dietRecordMapper.selectList(new QueryWrapper<DietRecord>().eq("user_id", userId).eq("date", yesterday));
        if (records.isEmpty()) return "无记录";
        StringBuilder sb = new StringBuilder();
        for (DietRecord r : records) {
            Food f = foodMapper.selectById(r.getFoodId());
            if (f != null) sb.append(f.getName()).append(";");
        }
        return sb.toString();
    }

    private String buildJson(String sys, String user, boolean stream) {
        return String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":%b}",
                modelName, escape(sys), escape(user), stream);
    }

    private String extractContent(String json) {
        try {
            Map map = objectMapper.readValue(json, Map.class);
            return (String) ((Map)((Map)((List)map.get("choices")).get(0)).get("delta")).get("content");
        } catch (Exception e) { return null; }
    }

    private List<String> parseQuickQuestions(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        try {
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            }
            if (trimmed.startsWith("{")) {
                Map<String, Object> payload = objectMapper.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
                Object questions = payload.get("questions");
                if (questions == null) {
                    questions = payload.get("quickQuestions");
                }
                if (questions instanceof List<?> list) {
                    List<String> values = new ArrayList<>();
                    for (Object item : list) {
                        values.add(String.valueOf(item));
                    }
                    return values;
                }
            }
        } catch (Exception ignored) {
        }

        List<String> lines = new ArrayList<>();
        for (String line : trimmed.split("\\r?\\n")) {
            String cleaned = line.replaceFirst("^\\s*(?:[-*•]|\\d+[.)、．])\\s*", "").trim();
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private List<String> normalizeQuickQuestions(List<String> questions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String question : questions) {
            if (question == null) {
                continue;
            }
            String cleaned = question.trim()
                    .replace("“", "")
                    .replace("”", "")
                    .replace("\"", "");
            if (!cleaned.isBlank()) {
                normalized.add(cleaned);
            }
            if (normalized.size() == 5) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> buildFallbackQuickQuestions(User user, AnalysisReport report) {
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        String goalText = getTargetText(user == null ? null : user.getTarget());

        questions.add("今天总热量和目标差了多少，我下一餐该怎么调？");

        AnalysisReport.NutrientAssessment nutrientIssue = report == null || report.getNutrientAssessments() == null
                ? null
                : report.getNutrientAssessments().stream()
                .filter(item -> item != null && item.getStatus() != null && !"达标".equals(item.getStatus()))
                .findFirst()
                .orElse(null);
        if (nutrientIssue != null) {
            questions.add("为什么今天更需要关注" + nutrientIssue.getNutrient() + "，我该怎么调整？");
        } else {
            questions.add("按我今天的情况，蛋白质、脂肪和碳水应该先优先调哪一个？");
        }

        AnalysisReport.MealAssessment mealIssue = report == null || report.getMealAssessments() == null
                ? null
                : report.getMealAssessments().stream()
                .filter(item -> item != null && item.getStatus() != null && !Set.of("合理", "已安排", "未安排").contains(item.getStatus()))
                .findFirst()
                .orElse(null);
        if (mealIssue != null) {
            questions.add(mealIssue.getTitle() + "最需要改哪里，换成什么更合适？");
        } else {
            questions.add("如果我想把三餐吃得更稳一点，应该先从哪一餐开始改？");
        }

        questions.add("按我现在的目标“" + goalText + "”，明天主食怎么选更合适？");
        questions.add("如果今天还想加餐，什么类型的食物更适合我？");

        while (questions.size() < 5) {
            questions.add("结合我今天的报告，下一步最值得优先调整的饮食点是什么？");
        }
        return new ArrayList<>(questions);
    }

    private List<String> safeList(List<String> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private String buildQuickQuestionCacheKey(User user, AnalysisReport report) {
        return String.format(
                Locale.ROOT,
                "%d|%s|%.1f|%.1f|%.1f|%.1f|%.1f|%.1f|%.1f",
                user.getId(),
                defaultText(report.getAnalysisDate(), "unknown"),
                safe(report.getTotalCalories()),
                safe(report.getRecommendCalories()),
                safe(report.getDiff()),
                safe(report.getTotalProtein()),
                safe(report.getTotalFat()),
                safe(report.getTotalCarb()),
                safe(report.getBmi())
        );
    }

    private List<String> getCachedQuickQuestions(String cacheKey) {
        CachedQuickQuestions cached = quickQuestionCache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt() < System.currentTimeMillis()) {
            quickQuestionCache.remove(cacheKey);
            return null;
        }
        return cached.questions();
    }

    private void cacheQuickQuestions(String cacheKey, List<String> questions) {
        quickQuestionCache.put(
                cacheKey,
                new CachedQuickQuestions(
                        List.copyOf(questions),
                        System.currentTimeMillis() + QUICK_QUESTION_CACHE_TTL_MILLIS
                )
        );
    }

    private String buildConversationContext(Long userId, LocalDate date) {
        DailyConversationMemory memory = dailyConversationCache.compute(userId, (key, existing) -> {
            if (existing == null || !date.equals(existing.date())) {
                return new DailyConversationMemory(date, new ArrayList<>());
            }
            return existing;
        });

        synchronized (memory) {
            if (memory.turns().isEmpty()) {
                return "";
            }
            return memory.turns().stream()
                    .map(turn -> ("user".equals(turn.role()) ? "用户：" : "云膳AI：") + turn.content())
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private void appendConversationTurn(Long userId, LocalDate date, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        DailyConversationMemory memory = dailyConversationCache.compute(userId, (key, existing) -> {
            if (existing == null || !date.equals(existing.date())) {
                return new DailyConversationMemory(date, new ArrayList<>());
            }
            return existing;
        });

        synchronized (memory) {
            memory.turns().add(new ChatTurn(role, content));
            while (memory.turns().size() > MAX_DAILY_CHAT_TURNS) {
                memory.turns().remove(0);
            }
        }
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }

    private record CachedQuickQuestions(List<String> questions, long expiresAt) {
    }

    private record ChatTurn(String role, String content) {
    }

    private record DailyConversationMemory(LocalDate date, List<ChatTurn> turns) {
    }

    private String getTargetText(Integer t) {
        if (t == null) return "维持";
        return switch (t) {
            case -1 -> "减脂";
            case  1 -> "增肌";
            case  2 -> "糖尿病控糖（低升糖、控制精制碳水）";
            case  3 -> "高血压低盐（每日钠摄入<2000mg，少加工食品）";
            case  4 -> "高血脂低脂（低饱和脂肪、多膳食纤维）";
            default -> "维持";
        };
    }
}
