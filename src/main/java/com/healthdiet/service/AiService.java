package com.healthdiet.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.entity.*;
import com.healthdiet.mapper.*;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {

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

    /**
     * 1. 获取或生成今日评估 (精简版)
     */
    public String getOrGenerateDailyReport(Long userId) {
        LocalDate today = LocalDate.now();
        AiReport report = aiReportMapper.selectOne(new QueryWrapper<AiReport>().eq("user_id", userId).eq("report_date", today));
        if (report != null) return report.getContent();

        User user = userMapper.selectById(userId);
        String history = getYesterdaySummary(userId);

        // 极其精简的指令
        String sysMsg = "你是一位毒舌但专业的营养师。请根据数据生成昨日评估。" +
                "要求：严禁寒暄，字数<130字，严格按此格式：\n" +
                "【昨日总结】：总热量与结构评价\n" +
                "【核心问题】：指出1-2个痛点\n" +
                "【今日建议】：3条极简对策";

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

        String systemPrompt = String.format(
                "你是营养师助手。用户信息：身高%.0f, 体重%.1f, 目标:%s。\n" +
                        "昨日评估报告：\n%s\n" +
                        "请结合此报告简短回答用户提问。如果用户问的与健康无关，请礼貌拒绝。",
                user.getHeight(), user.getWeight(), getTargetText(user.getTarget()), dailyReport
        );

        sendStreamRequest(systemPrompt, userMessage, emitter);
        return emitter;
    }

    // --- 工具方法 ---

    private String syncCallAi(String sys, String user) {
        String json = buildJson(sys, user, false);
        Request request = new Request.Builder().url(apiUrl).addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(json, MediaType.parse("application/json"))).build();
        try (Response response = client.newCall(request).execute()) {
            Map map = new ObjectMapper().readValue(response.body().string(), Map.class);
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
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) { emitter.complete(); break; }
                            String content = extractContent(data);
                            if (content != null) emitter.send(content);
                        }
                    }
                } catch (Exception e) { emitter.complete(); }
            }
        });
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
            Map map = new ObjectMapper().readValue(json, Map.class);
            return (String) ((Map)((Map)((List)map.get("choices")).get(0)).get("delta")).get("content");
        } catch (Exception e) { return null; }
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private String getTargetText(Integer t) { return t == null ? "维持" : (t == -1 ? "减脂" : (t == 1 ? "增肌" : "维持")); }
}