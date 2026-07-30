package com.educheck.controller;

import com.educheck.agent.AgentAssistant;
import com.educheck.agent.AgentTools;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.User;
import com.educheck.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "AI 智能体", description = "Agent对话，支持@MemoryId记忆")
public class AgentController {

    private final AgentAssistant agentAssistant;
    private final TokenContextHolder tokenContextHolder;
    private final UserService userService;

    @PostMapping("/chat")
    @Operation(summary = "Agent 对话（自动记录会话记忆）")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Long userId = tokenContextHolder.requireCurrentUserId();
        String message = (String) body.get("message");

        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }

        User user = userService.getById(userId);
        String role = user != null ? user.getRole() : "unknown";
        String name = user != null ? user.getName() : "未知用户";

        AgentTools.currentUserId.set(userId);
        AgentTools.currentUserRole.set(role);
        AgentTools.currentUserName.set(name);

        String enrichedMessage = String.format(
            "【当前日期：%s】\n【当前用户：%s（%s），身份：%s】\n%s",
            LocalDate.now(), name, userId, "teacher".equals(role) ? "教师" : "学生", message);

        long start = System.currentTimeMillis();
        try {
            String reply = agentAssistant.chat(userId, enrichedMessage);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reply", reply);
            result.put("elapsed", System.currentTimeMillis() - start);
            return Result.success(result);
        } finally {
            AgentTools.clearUserId();
        }
    }
}
