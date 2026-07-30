# 校园考勤 — 数据驱动的 AI Agent 设计方案

> 本文档聚焦两个核心设计：**数据分析流程**（从数据采集到数据报表的完整链路）和 **Agent 架构**（记忆、工具、规划、行动的工程落地方式）。不写详细代码，写清楚怎么做、为什么这么做。

---

## 一、数据分析流程

### 架构总览

```
采集层                              处理层                             应用层
所有业务表                           实时事件驱动                        出口
┌──────────────┐     ┌─────────────┐     ┌────────────┐     ┌──────────────┐
│ class_checkin│     │             │     │ 规则引擎    │     │ 教师看板     │
│ dorm_checkin │     │ 业务代码里   │     │ (alert_rule │     │ (今日概览)    │
│ intern_*     │────→│ 同步触发     │────→│  配置表)    │────→│              │
│ leave_app    │     │             │     │            │     │ 预警推送      │
│ announcement│     │ FeatureUpdate│     │ 统计模型    │     │ (alert_event) │
│ feedback     │     │ Manager     │     │ (凌晨计算)  │     │              │
│ dynamic_code │     └─────────────┘     └────────────┘     │ 趋势图       │
│ course       │                          │                │ (ECharts)    │
│ user         │                          ▼                │              │
│ user_face    │                  ┌──────────────┐         │ CSV 报表导出  │
└──────────────┘                  │ 特征宽表      │         │              │
                                  │ feature_daily│         │ Agent 查询    │
                                  │ feature_course│         │ (NL2SQL)     │
                                  │ feature_leave │         └──────────────┘
                                  │ feature_building│
                                  └──────────────┘
```

### 1.1 全表字段分析

> complete.md 补全接口后，系统共有约 15 张业务表。以下一一列出每张表的分析价值。

| # | 表名 | 核心字段 | 分析价值 | 新增/已有 |
|---|------|---------|---------|-----------|
| 1 | user | college, major, grade, role, status, created_at | 按学院/专业/年级分层统计；新生活跃度 | 已有 |
| 2 | course | name, weekDay, startTime, endTime, weekStart, weekEnd, status | 课表覆盖率；同一时段课程冲突检测 | 已有 |
| 3 | class_checkin | userId, courseId, date, checkinTime, status(present/absent/late), method, dynamicCode, locationLat/Lng | 出勤率、迟到率、签到方式偏好、动态码使用率 | 已有 |
| 4 | dorm_checkin | userId, date, checkinTime, building, room, inDormArea, faceVerified, status(normal/late) | 归寝率、晚归率、宿舍楼排行、人脸验证通过率 | 已有 |
| 5 | user_face | userId, version, registered, createdAt | 人脸录入率、版本分布、录入后是否持续使用 | 已有 |
| 6 | knowledge_base | category, question, answer, keywords, sortOrder | 热门搜索分类、关键词分布 | 已有 |
| 7 | unanswered_log | question, category, answered(0/1) | 知识库盲区识别、最常见未答问题 TOP 排行 | 已有 |
| **8** | **leave_application** | **userId, type(sick/personal/official), startDate, endDate, reason, status(pending/approved/rejected), rejectReason, createdAt** | **请假类型分布、周一/周五病假异常、审批耗时、驳回率** | **新增** |
| **9** | **announcement** | **title, type(notice/activity/academic/policy), isTop(0/1), department, createdAt** | **各类通知发布频率、置顶效率、各部门发布量** | **新增** |
| **10** | **feedback** | **userId, type(suggestion/bug/complaint/other), content, status(0/1), createdAt** | **学生关注热点、按学院聚合负反馈率、问题解决率** | **新增** |
| **11** | **internship** | **userId, company, role, totalDays, status** | **实习类型分布、各公司实习人数** | **新增** |
| **12** | **intern_checkin** | **internshipId, checkinTime, locationAddr, logContent, status** | **实习打卡完成率、日志提交习惯、地理位置集中度** | **新增** |
| **13** | **intern_photo** | **checkinId, imageUrl** | **图片上传量（可忽略）** | **新增** |
| **14** | **dynamic_code** | **courseId, teacherId, code, sessionId, duration, expiredAt, createdAt** | **动态码生成频率、有效期分布、作废率** | **新增** |
| **15** | **checkin_stats** | **userId, dormTotal, classTotal, internTotal, streakDays, totalPoints** | **已有但硬编码，后续可替换为实时统计** | 已有 |

### 1.2 采集层：事件触发点

所有业务变更都在现有代码里加一行调用，不改造架构：

```
ClassCheckinController.checkin()      末尾 → featureService.onClassCheckin(...)
DormCheckinController.checkin()       末尾 → featureService.onDormCheckin(...)
internCheckinController.checkin()     末尾 → featureService.onInternCheckin(...)
LeaveController.submit()              末尾 → featureService.onLeaveApply(...)
LeaveController.approve()             末尾 → featureService.onLeaveApprove(...)
FeedbackController.submit()           末尾 → featureService.onFeedback(...)
AnnouncementController.create()       末尾 → featureService.onAnnouncement(...)
DynamicCodeController.generate()      末尾 → featureService.onDynamicCode(...)
FaceController.register()             末尾 → featureService.onFaceRegister(...)
```

**关键决策：** 不搞 CDC 或消息队列。数据量太小（每天不到 10 万行），同步调用就够了。

### 1.3 处理层：特征设计

#### 核心特征宽表：feature_daily

每学生每天一条。从全部 15 张表提取有用特征，拉到一行里：

```sql
CREATE TABLE feature_daily (
    id               bigint AUTO_INCREMENT PRIMARY KEY,
    user_id          bigint NOT NULL,
    date             date   NOT NULL,

    -- ============ 考勤打卡 ============
    class_required    int DEFAULT 0,        -- 当天课程数 (从 course 计算)
    class_present     int DEFAULT 0,        -- 已签到
    class_late        int DEFAULT 0,        -- 迟到
    class_absent      int DEFAULT 0,        -- 旷课
    first_checkin_time time NULL,           -- 当天首次签到时间
    last_checkin_time  time NULL,           -- 当天末次签到时间

    -- ============ 查寝 ============
    dorm_required     tinyint DEFAULT 0,    -- 应查寝
    dorm_done         tinyint DEFAULT 0,    -- 已查寝
    dorm_time         time NULL,            -- 归寝时间
    dorm_is_late      tinyint DEFAULT 0,    -- 是否晚归 (time > 23:00)
    dorm_building     varchar(20) NULL,     -- 宿舍楼

    -- ============ 实习 ============
    intern_done       tinyint DEFAULT 0,    -- 当天是否实习打卡

    -- ============ 请假 ============
    leave_today       tinyint DEFAULT 0,    -- 今天是否请假
    leave_type        varchar(10) NULL,     -- sick/personal/official

    -- ============ 反馈 ============
    feedback_today    tinyint DEFAULT 0,    -- 今天是否提交反馈

    -- ============ 交叉特征 (关键) ============
    prev_dorm_late    tinyint DEFAULT 0,    -- 前一天是否晚归
    is_monday         tinyint DEFAULT 0,    -- 是否周一
    is_friday         tinyint DEFAULT 0,    -- 是否周五
    is_morning_class  tinyint DEFAULT 0,    -- 今天有早课 (startTime <= 10:00)

    -- ============ 滚动窗口特征 ============
    absent_last_7d    int DEFAULT 0,        -- 近7天旷课总数
    late_last_7d      int DEFAULT 0,        -- 近7天迟到总数
    dorm_late_last_7d int DEFAULT 0,        -- 近7天晚归总数
    intern_last_7d    int DEFAULT 0,        -- 近7天实习打卡数
    streak_days       int DEFAULT 0,        -- 连续打卡天数

    -- ============ 行为特征 ============
    leave_count_30d   int DEFAULT 0,        -- 近30天请假次数
    monday_sick_30d   int DEFAULT 0,        -- 近30天周一病假次数
    leave_approved_30d int DEFAULT 0,       -- 近30天已批准的请假数

    UNIQUE KEY uk_user_date (user_id, date),
    KEY idx_date (date),
    KEY idx_absent (absent_last_7d),
    KEY idx_dorm_late (dorm_is_late),
    KEY idx_building (dorm_building)
);
```

**为什么用宽表：** 分析时不需要 JOIN，where 条件在一行上过滤。规则引擎查一个字段就是 `row.getXxx()`，不用跨表。

#### 课程出勤表：feature_course

```sql
CREATE TABLE feature_course (
    id              bigint AUTO_INCREMENT PRIMARY KEY,
    course_id       bigint NOT NULL,
    week_start      date   NOT NULL,          -- 周一起始日

    enrolled_count  int DEFAULT 0,            -- 选课人数
    present_count   int DEFAULT 0,            -- 出勤
    late_count      int DEFAULT 0,            -- 迟到
    absent_count    int DEFAULT 0,            -- 旷课

    attendance_rate decimal(5,2),             -- 出勤率 %
    prev_week_rate  decimal(5,2),             -- 上周出勤率
    rate_change     decimal(5,2),             -- 环比变化量
    anomaly_flag    tinyint DEFAULT 0,        -- 异常标记 1=显著下降 2=显著上升

    UNIQUE KEY uk_course_week (course_id, week_start)
);
```

#### 宿舍特征表：feature_building

按宿舍楼 + 房间聚合，分析宿舍群体行为：

```sql
CREATE TABLE feature_building (
    id              bigint AUTO_INCREMENT PRIMARY KEY,
    building        varchar(20) NOT NULL,
    room            varchar(20) NOT NULL,
    week_start      date        NOT NULL,     -- 周一起始日

    resident_count  int DEFAULT 0,            -- 住宿人数
    late_count      int DEFAULT 0,            -- 晚归人次
    late_rate       decimal(5,2),             -- 晚归率 %
    avg_return_time time,                     -- 平均归寝时间
    prev_week_late  decimal(5,2),             -- 上周晚归率

    UNIQUE KEY uk_building_week (building, room, week_start)
);
```

#### 请假特征表：feature_leave

```sql
CREATE TABLE feature_leave (
    id              bigint AUTO_INCREMENT PRIMARY KEY,
    user_id         bigint NOT NULL,
    month           varchar(7) NOT NULL,      -- 月份 yyyy-MM

    total_count     int DEFAULT 0,            -- 当月请假总数
    sick_count      int DEFAULT 0,            -- 病假数
    personal_count  int DEFAULT 0,            -- 事假数
    monday_sick     int DEFAULT 0,            -- 周一病假数
    friday_sick     int DEFAULT 0,            -- 周五病假数
    approved_count  int DEFAULT 0,            -- 已批准数
    rejected_count  int DEFAULT 0,            -- 驳回数
    avg_process_hours decimal(10,2),          -- 平均审批耗时

    UNIQUE KEY uk_user_month (user_id, month)
);
```

#### 更新方式

| 表 | 更新时机 | 方式 |
|------|---------|------|
| feature_daily | 每次事件触发时 | UPSERT 实时 + 子查询算滚动窗口 |
| feature_course | 每天凌晨 3:00 | 全量计算上周数据 |
| feature_building | 每天凌晨 3:00 | 全量计算上周数据 |
| feature_leave | 每天凌晨 3:30 | 全量计算上月数据 |

feature_daily 的更新示例：

```java
public void onClassCheckin(Long userId, LocalDate date, String status) {
    String column = switch(status) {
        case "present" -> "class_present";
        case "late" -> "class_late";
        case "absent" -> "class_absent";
    };
    jdbc.update("""
        INSERT INTO feature_daily (user_id, date, """ + column + """, absent_last_7d)
        VALUES (?, ?, 1,
            (SELECT COUNT(*) FROM class_checkin
             WHERE user_id = ? AND status='absent'
             AND date BETWEEN DATE_SUB(?, INTERVAL 7 DAY) AND ?))
        ON DUPLICATE KEY UPDATE
            """ + column + " = " + column + """ + 1,
            absent_last_7d = VALUES(absent_last_7d)
        """, userId, date, userId, date, date);
}
```

### 1.4 统计模型（每天凌晨离线）

真正需要全量计算的跨学生分析：

```java
@Scheduled(cron = "0 0 3 * * *")
public void computeStats() {
    // 1. 晚归→旷课关联系数
    //    P(旷课 | 前晚晚归) = 昨天晚归且今天旷课的人数 / 昨天晚归总人数
    //    结果写入统计参数表，规则引擎读取

    // 2. 课程出勤率 写入 feature_course
    //    INSERT INTO feature_course (course_id, week_start, ...)
    //    SELECT ... FROM class_checkin c JOIN course co ON ...
    //    WHERE c.date BETWEEN ? AND ?

    // 3. 宿舍晚归 写入 feature_building
    //    INSERT INTO feature_building (building, room, week_start, ...)
    //    SELECT building, room, ... FROM dorm_checkin

    // 4. 请假模式 写入 feature_leave
    //    INSERT INTO feature_leave (user_id, month, ...)
    //    SELECT user_id, DATE_FORMAT(created_at, '%Y-%m'), ...
    //    FROM leave_application

    // 5. 热门未答问题排行
    //    SELECT question, COUNT(*) as cnt FROM unanswered_log
    //    WHERE answered=0 GROUP BY question ORDER BY cnt DESC

    // 6. 反馈类型分布
    //    SELECT type, COUNT(*) FROM feedback GROUP BY type
}
```

### 1.5 分析层：规则引擎

规则从代码中剥离，存在 `alert_rule` 配置表里：

```sql
CREATE TABLE alert_rule (
    id             bigint AUTO_INCREMENT PRIMARY KEY,
    rule_name      varchar(50)  NOT NULL,        -- 规则名称
    trigger_event  varchar(30)  NOT NULL,        -- 触发事件
    condition_sql  text         NOT NULL,        -- 条件 SQL（查询 feature_daily）
    action         text         NOT NULL,        -- 动作描述
    enabled        tinyint DEFAULT 1,
    created_at     datetime DEFAULT CURRENT_TIMESTAMP
);
```

预置规则清单（覆盖全表分析维度）：

| 规则 | 触发事件 | 条件 | 动作 | 涉及表 |
|------|---------|------|------|--------|
| 连续旷课预警 | class_absent | absent_last_7d >= 3 | 创建 alert_event → 推送辅导员 | class_checkin, feature_daily |
| 晚归→早课预警 | dorm_late | 晚于 23:30 且明天有早课 | 次日 7:00 推送提醒 | dorm_checkin, course, feature_daily |
| 宿舍晚归超标 | dorm_late | 本周晚归率 > 50% | 推送辅导员 | dorm_checkin, feature_building |
| 请假异常检测 | leave_apply | 本周二病假累计 monday_sick_30d >= 3 | 标记需人工审批 | leave_application, feature_daily, feature_leave |
| 低出勤率课程 | 定时任务 | 本周出勤率 < 60% | 推送教学管理部门 | class_checkin, feature_course |
| 反馈负热点 | feedback | 同类反馈近 7 天 > 5 条 | 推送管理员 | feedback |
| 人脸录入引导 | user_face | 注册超过 7 天未录入人脸 | 推送提醒 | user, user_face |
| 实习断签预警 | intern_checkin | 连续 3 天未打卡 | 推送学生 + 指导老师 | intern_checkin, feature_daily |
| 动态码有效率 | 定时任务 | 生成后未被使用的动态码 > 30% | 推送教师 | dynamic_code |

### 1.6 应用层：数据出口

| 出口 | 数据源 | 用途 |
|------|--------|------|
| 教师看板 | feature_daily 当日聚合 + 各 alert_rule 统计 | 今日出勤概览 |
| 预警推送 | alert_event | 辅导员通知 |
| 趋势图 | feature_course (课程趋势), feature_daily (学生个人趋势) | ECharts 折线图 |
| CSV 导出 | feature_daily + 各业务表聚合 | 辅导员导出报表 |
| Agent 查询 (NL2SQL) | 所有分析表 | "我上周旷了几节课" "我们班出勤率多少" |

教师看板示例：

```
今日出勤概览 (2026-07-29)
├─ 应到 120 人，实到 108 人，出勤率 90%
├─ 旷课: 8 人 (6.7%)   迟到: 4 人 (3.3%)
├─ 今日查寝: 应查 120 人 已查 112 人 (93.3%)  晚归: 5 人
├─ 实习打卡: 应打卡 45 人 已打卡 38 人 (84.4%)
│
├─ ⚠ 预警事项
│  ├─ 连续旷课 ≥ 3 天: 张三、李四 (共 2 人)
│  ├─ 今日早课未签 + 前日晚归: 赵六
│  └─ 实习断签 ≥ 3 天: 王五
│
├─ 📊 今日数据排行
│  ├─ 晚归宿舍 TOP3: 5-318(3次)  3-205(2次)  2-101(2次)
│  ├─ 请假集中: 周一病假 4 例 (占本周请假 57%)
│  └─ 反馈热点: 查寝打卡页面加载慢 (3条)
│
└─ 📋 待审批: 请假 3 条 | 未答问题 2 条
```

---

## 二、Agent 架构

### 总体设计

```
┌──────────────────────────────────────────────────┐
│                  Agent                            │
│                                                  │
│  ┌────────────────────────────────────────┐      │
│  │  LangChain4j AiServices               │      │
│  │                                        │      │
│  │  短期记忆 (ChatMemory)                  │      │
│  │  └─ MessageWindowChatMemory(20条)       │      │
│  │                                        │      │
│  │  @Tool 注解 → 自动生成 Function         │      │
│  │  Declaration → DeepSeek function        │      │
│  │  calling → 工具结果自动注入回消息         │      │
│  │                                        │      │
│  │  ReAct 循环 (内置，不必手写)              │      │
│  │  └─ 思考 → 调工具 → 观察 → 再思考        │      │
│  └────────────────────────────────────────┘      │
│                         │                         │
│  你自定义的部分             │                         │
│  ┌────────────────────────────────────────┐      │
│  │  MemoryService (长期记忆)               │      │
│  │  └─ user_memory 表读写                  │      │
│  │  @Tool 方法内部权限校验                  │      │
│  │  AgentController (对话入口)              │      │
│  └────────────────────────────────────────┘      │
└──────────────────────────────────────────────────┘
```

**LangChain4j 替你做了：** 工具注册、Function Declaration 生成、ReAct 循环、短期记忆管理、tool call 解析、结果注入消息、循环终止判断。

**你需要自己做的：** 长期记忆（user_memory 表）、工具方法里的业务逻辑 + 权限校验、AgentController、`@Tool` 注解的方法定义。

### 2.1 集成方式

**第一步：加依赖**

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>0.35.0</version>
</dependency>
```

**第二步：配置 DeepSeek（兼容 OpenAI API）**

```yaml
langchain4j:
  chat-model:
    provider: open-ai
    base-url: https://api.deepseek.com
    api-key: ${DEEPSEEK_API_KEY}
    model-name: deepseek-chat
    temperature: 0.1
    max-tokens: 1024
```

**第三步：用 @Tool 定义工具，不用自己写 AgentTool 接口**

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class AttendanceTools {

    private final CourseService courseService;
    private final LeaveService leaveService;
    private final UserService userService;
    private final MemoryService memoryService;

    // 注入当前操作用户（LangChain4j 支持从 context 取）
    private final TokenContextHolder tokenContextHolder;

    @Tool("查询指定日期的课程表，返回课程名、时间、地点、教师。参数 date 格式 yyyy-MM-dd")
    public String querySchedule(@P("date") String date) {
        List<Course> courses = courseService.getScheduleByDate(date);
        // 格式化为文本返回
        return formatSchedule(courses);
    }

    @Tool("查询学生考勤数据，返回出勤率、旷课次数、迟到次数。参数 userId 学号，dateRange 如 '7d' 或 '30d'")
    public String queryAttendance(@P("userId") Long targetUserId,
                                   @P("dateRange") String dateRange) {
        // 权限校验：学生只能查自己
        Long callerId = tokenContextHolder.requireCurrentUserId();
        String role = userService.getById(callerId).getRole();
        if ("student".equals(role) && !targetUserId.equals(callerId)) {
            return "无权查询其他学生的数据";
        }
        // 查 feature_daily 表聚合
        return featureService.query(targetUserId, dateRange);
    }

    @Tool("提交请假申请。参数 type: sick/personal/official, startDate/endDate: yyyy-MM-dd, reason: 原因")
    public String submitLeave(@P("type") String type,
                               @P("startDate") String startDate,
                               @P("endDate") String endDate,
                               @P("reason") String reason) {
        Long userId = tokenContextHolder.requireCurrentUserId();

        // 检查是否有异常请假模式（从长期记忆读取）
        String pattern = memoryService.get(userId, "leave_pattern");
        boolean suspicious = pattern != null && pattern.contains("monday_sick_ratio\":0.8");

        // 提交请假
        LeaveApplication leave = leaveService.submit(userId, type, startDate, endDate, reason);
        if (suspicious) {
            leave.setStatus("manual_review");
            leaveService.update(leave);
            return "请假已提交，因检测到异常请假模式，已转人工审批，请等待辅导员处理。";
        }
        return "请假已提交，等待审批。";
    }
}
```

`@Tool` 注解的效果：LangChain4j 自动读取注解内容 + 方法参数，生成 DeepSeek 需要的 Function Declaration JSON。不需要手写 `FunctionDeclaration.builder()`。

**工具列表：**

| @Tool 方法 | 调什么 | 权限控制方式 |
|-----------|--------|------------|
| searchKnowledge | KnowledgeBaseService | 无限制，只读 |
| querySchedule | CourseService | 无限制 |
| queryAttendance | FeatureService | 方法内硬校验角色 |
| queryRisk | AlertEventService | 方法内硬校验角色 |
| submitLeave | LeaveService | 方法内写操作确认 |
| notifyTeacher | NotificationService | 方法内硬校验角色 |
| askUser | — | 纯对话，不需要 @Tool |

### 2.2 长期记忆（自定义）

LangChain4j 不提供跨会话的长期记忆。需要自己实现。

```sql
CREATE TABLE user_memory (
    id            bigint AUTO_INCREMENT PRIMARY KEY,
    user_id       bigint       NOT NULL,
    memory_key    varchar(50)  NOT NULL,    -- preference / habit / fact
    memory_value  text         NOT NULL,    -- 记忆内容（JSON）
    confidence    decimal(3,2) DEFAULT 1.0,
    updated_at    datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_key (user_id, memory_key)
);
```

**存什么：**

| memory_key | 举例 | 怎么来的 |
|-----------|------|---------|
| `leave_pattern` | `{"monday_sick_ratio": 0.8}` | 每日统计任务自动写入 |
| `frequent_leave_type` | `{"type": "sick", "count": 5}` | 同上 |
| `recent_questions` | `["怎么打卡", "请假流程"]` | 每次对话后追加 |
| `known_issues` | `{"face_not_registered": true}` | Agent 发现后写入 |

```java
@Service
public class MemoryService {

    /** 读取用户长期记忆，格式化为文本 */
    public String buildContext(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT memory_key, memory_value FROM user_memory WHERE user_id = ?", userId);
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("关于该用户的历史信息：\n");
        for (Map<String, Object> row : rows) {
            sb.append("- ").append(row.get("memory_key"))
              .append(": ").append(row.get("memory_value")).append("\n");
        }
        return sb.toString();
    }

    /** 对话结束后保存新记忆 */
    public void save(Long userId, String key, String value) {
        jdbc.update("INSERT INTO user_memory (user_id, memory_key, memory_value) " +
                     "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE memory_value = ?");
    }
}
```

长期记忆通过 **系统提示词注入** 的方式给到 DeepSeek，而不是存在 LangChain4j 的 ChatMemory 里。ChatMemory 只管当前对话上下文，`user_memory` 表管跨会话知识。

### 2.3 短期记忆（LangChain4j 内置）

LangChain4j 提供 `ChatMemory`：

```java
// 自动管理消息列表，超出窗口自动丢弃最早的
ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
```

但你的场景里，用户可能关闭小程序再打开，所以短期记忆应该由**前端维护**，每次请求时带回来：

```java
// AgentController 里，从请求拿到 history，直接传给 LangChain4j
List<ChatMessage> history = parseHistory(request.getHistory());

ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
memory.addAll(history);
memory.add(userMessage(message));

// 调 LangChain4j 的 AiServices
// 不需要手动控制 ReAct 循环
```

### 2.4 规划与决策（LangChain4j 内置）

LangChain4j 的 ReAct 循环是内置的，不需要手写。

**你的代码：**

```java
// 定义一个接口，LangChain4j 自动实现
public interface Assistant {
    String chat(@MemoryId Long userId, @UserMessage String message);
}

// 配置
@Bean
public Assistant assistant(ChatLanguageModel model,
                            AttendanceTools tools,
                            MemoryService memoryService) {

    return AiServices.builder(Assistant.class)
        .chatLanguageModel(model)
        .tools(tools)
        // 这里不用 .chatMemory()，因为我们用前端传的 history
        // 但通过 SystemMessageProvider 注入长期记忆
        .systemMessageProvider(chatMemory -> {
            Long userId = currentUserId();  // 从上下文取
            String memoryContext = memoryService.buildContext(userId);
            return SystemMessage.from(buildSystemPrompt(memoryContext));
        })
        .build();
}

// 调用时
String reply = assistant.chat(userId, message);
// 这一行背后：LangChain4j 自动做 ReAct 循环、调 DeepSeek、
// 解析 tool_call、执行 @Tool 方法、循环直到 LLM 返回最终回答
```

**系统提示词生成：**

```java
private String buildSystemPrompt(String memoryContext) {
    return """
        你是校园考勤助手的 AI 助手。你有以下工具可用，根据用户问题调用合适的工具。

        使用规范：
        1. 信息不足时先追问，不要猜测
        2. 涉及写操作（请假、通知）前先让用户确认
        3. 一次只说一个工具，等结果再决定下一步

        """
        + (memoryContext.isEmpty() ? "" : memoryContext + "\n")
        + """
        安全规则（必须在工具方法内执行，不要只在提示词里要求）：
        - 学生只能查看自己的数据
        - 删除操作需要教师权限
        """;
}
```

**你不需要自己写的代码（LangChain4j 代劳）：**

| 代码 | 手写行数 | LangChain4j |
|------|---------|-------------|
| FunctionDeclaration 构建 | ~100 行 | 从 @Tool 注解自动生成 |
| ReAct for 循环 | ~80 行 | AiServices 内置 |
| tool call 解析 | ~60 行 | 内置 |
| 工具结果注入 messages | ~30 行 | 内置 |
| 短期记忆截断 | ~30 行 | ChatMemory 内置 |
| 合计 | ~300 行 | 0 行 |

**你需要自己写的：**

| 代码 | 说明 |
|------|------|
| `@Tool` 注解的方法 | 实际业务逻辑 + 权限校验 |
| `MemoryService` | 长期记忆读写 |
| `AgentController` | 对话入口 |
| `buildSystemPrompt()` | 系统提示词拼装 |

### 2.5 Agent 接入方式

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final Assistant assistant;
    private final MemoryService memoryService;
    private final TokenContextHolder tokenContextHolder;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Long userId = tokenContextHolder.requireCurrentUserId();
        String message = (String) body.get("message");

        // 调 LangChain4j Agent，内部自动走 ReAct 循环
        String reply = assistant.chat(userId, message);

        // 异步保存长期记忆（Agent 在对话中发现了什么）
        memoryService.saveAsync(userId, "recent_questions",
            "{\"last\": \"" + message + "\"}");

        return Result.success(Map.of("reply", reply));
    }
}
```

**消息出口（通知）：**

Agent 可以通过 `@Tool("通知教师...")` 写入 `notification` 表，教师端轮询拉取：

```sql
CREATE TABLE notification (
    id           bigint AUTO_INCREMENT PRIMARY KEY,
    user_id      bigint       NOT NULL,
    title        varchar(200) NOT NULL,
    content      text,
    is_read      tinyint DEFAULT 0,
    source       varchar(30)  DEFAULT 'agent',
    created_at   datetime DEFAULT CURRENT_TIMESTAMP
);
```

### 2.6 请假场景完整流程

```
1. 用户："帮我请个假"
   → LangChain4j 调 DeepSeek
   → DeepSeek 发现没给类型和时间 → 调用 @Tool("追问用户")
   → Agent 回复："好的，请问请假类型（病假/事假/公假）、时间、原因？"

2. 用户："明天发烧了，请病假一天"
   → LangChain4j 继续 ReAct 循环
   → DeepSeek 调用 @Tool("querySchedule")
      → 调 CourseService → 返回"明天有 08:00 软件工程、10:00 数据结构"
   → DeepSeek 调用 @Tool("queryAttendance") 查近期记录
      → 调 FeatureService → 查 feature_daily 发现 3 次周一病假
   → 结果回到消息 → DeepSeek 判断需转人工审批
   → DeepSeek 调用 @Tool("submitLeave")
      → 调 LeaveService → 写入 leave_application(status=manual_review)
      → 调 MemoryService → 写入 user_memory(leave_pattern)
   → DeepSeek 调用 @Tool("notifyTeacher")
      → 写入 notification 表 → 教师端拉取
   → DeepSeek 返回最终回答

3. Agent："已为您提交明天病假申请，涉及 2 节课。
        检测到本学期多次周一病假记录，已转人工审批。"
```

整个过程不需要你写一行循环控制代码。LangChain4j 的 AiServices 管理了从"调 DeepSeek"到"执行工具"到"再调 DeepSeek"的全部循环。

### 2.7 安全边界

LangChain4j 不处理权限。直接在 `@Tool` 方法里硬校验：

```java
@Tool("查询学生考勤数据")
public String queryAttendance(@P("userId") Long targetUserId, ...) {
    Long callerId = tokenContextHolder.requireCurrentUserId();
    String role = userService.getById(callerId).getRole();

    if ("student".equals(role) && !targetUserId.equals(callerId)) {
        return "无权查询其他学生的数据";  // 不是抛异常，是返回给 LLM
    }
    // ... 正常查询
}
```

校验不在提示词里要求 LLM 自觉遵守，在工具执行时硬拦截。即使 LLM 构造了错误的参数，到工具层也会被拦下。

---

## 三、落地路径

### 需要新建什么

| 模块 | 文件 | 说明 |
|------|------|------|
| 特征表 | feature_daily, feature_course | 两张表 |
| 特征服务 | FeatureService + FeatureUpdateManager | 实时更新 + 每日聚合 |
| 缓存 | StatsCache | 排行榜等不频繁变更的数据 |
| 规则引擎 | AlertRule + alert_rule 表 | 配置化规则，不写死在代码里 |
| LangChain4j 集成 | pom.xml + application.yml | 依赖和配置 |
| 工具类 | AttendanceTools（@Tool 注解方法） | 7 个工具方法，含权限校验 |
| Agent 配置 | Assistant Bean（AiServices 配置） | LangChain4j 入口 |
| 长期记忆 | user_memory 表 + MemoryService | 跨会话知识留存 |
| Agent 入口 | AgentController | `POST /api/agent/chat` |
| 通知表 | notification | Agent 行动的出口之一 |

### LangChain4j 覆盖了什么

| 组件 | 手写方案 | LangChain4j 方案 |
|------|---------|-----------------|
| 工具注册 | AgentTool 接口 + 7 个实现类 + FunctionDeclaration 构建 | `@Tool` 注解，自动生成 |
| ReAct 循环 | ReactAgent 约 80 行 for 循环 | AiServices 内置 |
| tool call 解析 | 手动解析 response 提取参数 | 内置 |
| 短期记忆 | MessageWindow 手动截断 | ChatMemory 内置 |
| **合计** | **约 300 行胶水代码** | **0 行** |

### 需要自己写的

| 模块 | 说明 |
|------|------|
| 特征数据 + 规则引擎 | 全部自己实现，LangChain4j 管不了 |
| 长期记忆 | MemoryService 读写 user_memory 表 |
| 权限校验 | `@Tool` 方法内硬校验 |
| 系统提示词 | buildSystemPrompt() 拼装记忆+规则 |
| AgentController 入口 | 接收前端请求 + 调 Assistant |

### 不需要引入什么

| 技术 | 原因 |
|------|------|
| Kafka/RabbitMQ | 每天不到 10 万行，同步调用就够，不需要消息队列 |
| Redis | ConcurrentHashMap 缓存足够，多实例部署时再考虑 |
| 向量库（除 pgvector） | 知识库几百条，MySQL + pgvector 够用 |
| 工作流引擎 | 单级审批用 status 字段就够了 |
| Drools 规则引擎 | alert_rule 配置表 + 简单表达式比 DSL 更实用 |

### 实施顺序

```
第 1 周：建 feature_daily 表 + FeatureUpdateManager 实时更新
第 2 周：建 feature_course + 每天凌晨的统计模型
第 3 周：alert_rule 规则引擎 + alert_event 预警
第 4 周：LangChain4j 集成 + 7 个 @Tool 方法（含权限校验）
第 5 周：MemoryService 长期记忆 + Assistant Bean 配置
第 6 周：AgentController + 前端对接
第 7 周：教师看板数据接口 + notification 通知表
第 8 周：空闲时把 RAG 的 pgvector 加上
```

### 面试时怎么说

> "Agent 基于 LangChain4j + DeepSeek function calling 实现。用 `@Tool` 注解定义了 7 个校园考勤工具，LangChain4j 自动管理 ReAct 循环和 tool call 解析，不需要手写胶水代码。长期记忆存在 MySQL 的 user_memory 表里，每次对话前加载拼到系统提示词中。权限校验在 `@Tool` 方法内部硬拦截，不依赖 LLM 自觉遵守。整个数据链路是事件驱动的——打卡事件在 10ms 内完成特征更新和规则检查。没有引入 Kafka 或 Redis，因为全校上万人的数据量 MySQL 单机就能扛住。"
