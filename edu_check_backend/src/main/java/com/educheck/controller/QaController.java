package com.educheck.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.common.WordTokenizer;
import com.educheck.entity.KnowledgeBase;
import com.educheck.entity.UnansweredLog;
import com.educheck.service.DeepSeekService;
import com.educheck.service.KnowledgeBaseService;
import com.educheck.service.UnansweredLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

//接口返回的数据格式为json
@RestController
//定义当前控制的处理的接口
@RequestMapping("/api/knowledge")

//创建service对象
//给所有的final修饰的成员变量 创建对应的对象
@RequiredArgsConstructor

//
@Tag(name="智数问答" , description = "智能数据问答后端接口")
public class QaController {

    //    所有的上下文接口与服务
    private final TokenContextHolder tokenContextHolder;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UnansweredLogService unansweredLogsService;
    private final DeepSeekService deepSeekService;

    //    关键字列表
    private static final List<String> CAMPUS_KEYWORDS = List.of(
            "打卡", "考勤", "签到", "请假", "公告"
            , "课程", "实习",
            "查寝", "宿舍", "人脸", "权限", "账号", "密码",
            "教师", "学生", "数据库", "接口", "API", "功能", "页面",
            "重置", "修改", "添加", "删除", "查看", "切换",
            "token", "JWT", "角色", "成绩", "课表", "教室",
            "校园", "教务", "前端", "后端",
            "小程序", "通信", "请求", "网络",
            "手机", "微信", "审批", "反馈",
            "动态码", "意见", "录入",
            "上课", "课表", "明天", "今天"
    );


//    处理热门问题接口

    @GetMapping("/hot")
    @Operation(summary = "获得热门问题前六个")
    public Result<List<KnowledgeBase>> hot(
    ) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .orderByDesc(KnowledgeBase::getSortOrder).last("limit 6");
        List<KnowledgeBase> hotlist = knowledgeBaseService.list(wrapper);
        return Result.success(hotlist);
    }


    @PostMapping("/search")
    @Operation(summary = "智能问答回答用户问题")
    public Result<Map<String, Object>> search(
//            使用map接收前端所用参数
            @RequestBody Map<String, Object> request
    ) {
//        在请求参数里面获取用户问题
        String question = ((String) request.getOrDefault("question", "")).trim();

//      提取用户问题类型
        String category = (String) request.getOrDefault("category", "all");

//       取之前聊天记录
        List<Map<String, String>> history = (List<Map<String, String>>) request.getOrDefault("history", List.of());

//        前端 用户问题为空 返回一个空结果
        if (question.isEmpty()) {
            return Result.success(Map.of("answer", "", "sources", List.of()));

        }
//        敏感内容过滤
//        判断用户提问 里面是否有 包含定义的关键字
        boolean isCampusRelated = CAMPUS_KEYWORDS.stream().anyMatch(k -> question.contains(k));
        if (!isCampusRelated) {
            return Result.success(Map.of("answer", "请咨询校园助手相关问题", "sources", List.of()));

        }


//          如果用户提问是正常问题 我们查询知识库 提取相关问题信息
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
//        用户问题不是所有
        if (!"all".equals(category)) {
//          按照具体分类 在知识库中查询
            wrapper.eq(KnowledgeBase::getCategory, category);

        }
        wrapper.orderByAsc(KnowledgeBase::getSortOrder);
        List<KnowledgeBase> all = knowledgeBaseService.list(wrapper);

//        用户提进行处理
        String q = question.toLowerCase();

//        分词
        List<String> questionWords = WordTokenizer.segment(q);
        List<Map<String, Object>> scored = new ArrayList<>();

        for (KnowledgeBase kb : all) {

//            初始话分数 0
            int score = 0;
//            获取关键字
            String kw = (kb.getKeywords() != null ? kb.getKeywords() : "").toLowerCase();
//            获取同义字
            String syn = (kb.getSynonyms() != null ? kb.getSynonyms() : "").toLowerCase();
//            获取对应的知识库问题
            String kq = (kb.getQuestion() != null ? kb.getQuestion() : "").toLowerCase();
//            获取对应的知识库问答
            String ka = (kb.getAnswer() != null ? kb.getAnswer() : "").toLowerCase();


//            开始计算 每条回答评分
//            关键字直接匹配 提问包括的知识库关键字 每条关键字+10
//            每个关键字的链接符号位 , 以 , 切割关键字
            for (String keyword : kw.split(",")) {
//                去掉前后空格
                keyword = keyword.strip();
//                关键字不为空 用户问题包括关键字
                if (!keyword.isEmpty() && q.contains(keyword)) {
                    score += 10;
                }
            }
            // 判断同义词命中 进行对应加分
            // 同义词不为空 进行判断
            if (!syn.isEmpty()) {
                // 同义词格式 查表=查房,宿舍打卡|打卡=签到|怎么=如何,怎样|宿舍=寝室,公寓
                // 按竖线切割
                // 上面的结果会切成下面部分
                // 查表=查房,宿舍打卡 打卡=签到 怎么=如何,怎样 宿舍=寝室,公寓
                for (String group : syn.split("\\|")) {
                    // 去掉前后空格
                    group = group.trim(); // 查表=查房,宿舍打卡
                    // 如果 group 里面没有 等于号 跳过
                    if (!group.contains("=")) continue;
                    // 将group 按 = 切割 最多切两段
                    String[] parts = group.split("=", 2);
                    // 遍历当前组所有的同义词
                    // parts[1] 查房,宿舍打卡 将 parts[1] 用 逗号切割
                    for (String s : parts[1].split(",")) {
                        // 同义词去掉空格
                        s = s.trim();
                        // 同义词不为空 并且 问题包括同义词 +8 分
                        if (!s.isEmpty() && q.contains(s)) {
                            score += 8;
                        }
                    }
                }
            }
            // 问题和 答案 匹配 问题命中 +3 分 答案命中+1
            // 遍历分词 考勤打卡怎么操作 分词后
            // questionWords = 考勤 打卡 怎么 操作
            for (String w : questionWords) {
                if (kq.contains(w)) score += 3;
                if (ka.contains(w)) score += 1;
            }
            // 排序需要加权 序号越小 基础分越高 最大额外+100 分
            score += Math.max(0, 100 - kb.getSortOrder());
            // 如果知识库和用户问题有相关性
            if (score > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", kb.getId());
                item.put("category", kb.getCategory());
                item.put("question", kb.getQuestion());
                item.put("answer", kb.getAnswer());
                item.put("score", score);
                scored.add(item);
            }
        }
        // 按匹配分数倒序排列 高分在前 筛选分数最高的两条记录
        scored.sort((Map<String, Object> a, Map<String, Object> b)
                -> (int) b.get("score") - (int) a.get("score"));

        // 存储 筛选后高相关知识库记录
        List<Map<String, Object>> top = new ArrayList<>();
        // scored 不为空 进行对应操作
        if (!scored.isEmpty()) {
            // 获取最高分
            int topScore = (int) scored.get(0).get("score");

            // 只保留 得分 >= 最高分 60% 的记录 最多两条
            for (Map<String, Object> item : scored) {
                // 记录最多两条
                if (top.size() >= 2) break;
                if ((int) item.get("score") >= topScore * 0.6) {
                    top.add(item);
                }
            }

        }
        //如果用户问题 没有对应的知识库匹配 未命中应该存入 unanswered_log 库
        if(top.isEmpty()){
            UnansweredLog unansweredLog = new UnansweredLog();
            unansweredLog.setQuestion(question);
            unansweredLog.setCategory("all".equals(category)? null : category);
            unansweredLog.setAnswered(0);
            unansweredLogsService.save(unansweredLog);
        }
        String llaAnswer = null;
        //已经匹配知识库  调用  大模型
        if(!top.isEmpty()){
//                将Map格式数据转化为  数据库 实体 对象列表
            List<KnowledgeBase> matchedkb = top.stream().map(
                    m->{
                        KnowledgeBase kb = new KnowledgeBase();
                        kb.setId(((Number)m.get("id")).longValue());
                        kb.setCategory((String)m.get("category"));
                        kb.setQuestion((String) m.get("question"));
                        kb.setAnswer((String) m.get("answer"));
                        return kb;
                    }).collect(Collectors.toList());

            llaAnswer = deepSeekService.ask(question,matchedkb,history);

        }
        // 给前端返回值  删除评分(不需要给前端的展示)
        top.forEach(item->item.remove("score"));

//            返回给前端的结果
        Map<String,Object> result = new HashMap<>();
        if(llaAnswer != null){

            //将大模型的返回值 打包 返回给前端
            result.put("answer",llaAnswer);

        }else if(!top.isEmpty()){

            //没有调用大模型 但是匹配知识库  返回知识库最高评分
            result.put("answer",top.get(0).get("answer"));

        }else{
            result.put("answer","未找到相关答案");

        }
        result.put("sources",top);
        return Result.success(result);

    }

    @GetMapping("/unanswered")
    @Operation(summary = "获取未回答的问题列表（教师端）")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<UnansweredLog>> unanswered(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UnansweredLog> p =
                unansweredLogsService.page(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                        new LambdaQueryWrapper<UnansweredLog>()
                                .eq(UnansweredLog::getAnswered, 0)
                                .orderByDesc(UnansweredLog::getCreateAt));
        return Result.success(p);
    }

}
