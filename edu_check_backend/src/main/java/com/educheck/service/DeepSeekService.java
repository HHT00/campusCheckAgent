package com.educheck.service;

import com.educheck.entity.KnowledgeBase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

//    创建一个 http 请求对象 调用大模型
    private final RestTemplate restTemplate;

//加载配置文件 ds 密钥
    @Value(("${deepseek.api-key}"))
    private String apikey;

//    加载ds 的调用网址
    @Value("${deepseek.base-url}")
    private String baseUrl;

//    加载使用的 ds 模型
    @Value("${deepseek.model}")
    private String model;
//    模型是温度越低 回答越严谨
    private double temperature = 0.1;

//    读取单次回答 输出的最大token 长度
    @Value("${deepseek.max-tokens}")
    private int maxTokens;
//    记录key 是否有效
    private boolean keyValid;

    @PostConstruct
    public void init(){
        if(apikey == null || apikey.isEmpty()){
            log.warn("deepseek密钥没有配置，后续回答将会降级回答");
            keyValid = false;
            return;
        }
        try{
//            构建 向 deepseek请求的 http 协议头 检测密钥是否正常
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setBearerAuth(apikey);
            HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<Map> resp = restTemplate.exchange(baseUrl+"/v1/models", HttpMethod.GET,entity,Map.class);

//            请求状态码是2 开头表示 密钥正常
            keyValid = resp.getStatusCode().is2xxSuccessful();
            log.info("deepseek api 链接成功 model:{}",model);

        }catch (Exception e){
            log.warn("deepseek 密钥测试失败{}， 将降级为匹配回答",e.getMessage());
            keyValid = false;
        }
    }

//    返回密钥状态


    public boolean isKeyValid() {
        return keyValid;
    }

//拼接知识库每个问题与回答 +系统系统提示词
//    构建系统提示词 给deepseek  加设定（分角色）
    private String  buildSystemPrompt(List<KnowledgeBase> knowledgeBases){
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个校园考勤助手的智能助手。" +
                "你只回答关于校园考勤系统使用的问题。\n\n");
        sb.append("## 核心规则\n");
        sb.append("1. 必须且仅基于以下知识库内容回答" +
                "，不得使用你自身的知识\n");
        sb.append("2. 用你自己的话重新组织语言，不要逐字复制\n");
        sb.append("3. 如果问题与校园考勤完全无关" +
                "（如天气、新闻、美食、娱乐等），" +
                "请只回复：\"请咨询与校园考勤相关的问题。\"\n");
        sb.append("4. 如果知识库中没有相关信息，请只回复" +
                "：\"知识库中没有找到相关信息，请尝试换一种问法。\"\n");
        sb.append("5. 不要编造答案，不要猜测，不要使用你的内部知识\n");
        sb.append("6. 知识库中 [1] 是最相关的条目" +
                "，请以 [1] 为准回答问题，其他条目仅作参考\n");
        sb.append("7. 回答控制在200字以内\n\n");
        sb.append("## 拒绝回答的例子\n");
        sb.append("- \"今天天气怎么样\" → 请咨询与校园考勤相关的问题。\n");
        sb.append("- \"写一首诗\" → 请咨询与校园考勤相关的问题。\n");
        sb.append("- \"世界上最高的山\" → 请咨询与校园考勤相关的问题。\n");
        sb.append("- \"讲个笑话\" → 请咨询与校园考勤相关的问题。\n\n");

        if(knowledgeBases != null && knowledgeBases.isEmpty()){
//            拼接标题
            sb.append("### 知识库内容\n");
//            循环遍历每一条检索到的知识库信息
            for(int i =0 ; i < knowledgeBases.size(); i++){
                KnowledgeBase kb = knowledgeBases.get(i);
//                增加分割线让提示词清晰
                sb.append("-------\n");
//                拼接结果为 [1] + 对应问题  ...
                sb.append("[").append(i+1).append("]")
                        .append(kb.getQuestion()).append("]n");
                sb.append("答：").append(kb.getAnswer()).append("\n");
            }
        }else{
            sb.append("暂时没有相关内容\n");
        }
        return sb.toString();
    }

//    单次回答
    public String ask(String question ,List<KnowledgeBase> knowledge){
        return ask(question,knowledge,List.of());
    }

// 多次对话 有历史记录
    public String ask(String question ,List<KnowledgeBase> knowledge,List<Map<String,String>> history){
//    判断密钥是否正常
        if(!keyValid){
            return null;
        }
        String systemPrompt = buildSystemPrompt(knowledge);
        try{
//            构建请求体
            Map<String,Object> body = new HashMap<>();

//            指定需要调用的模型版本
            body.put("model",model);

//            设置回答的token 控制回答内容长度
            body.put("max-tokens",maxTokens);

//            设置温度参数
            body.put("temperature",temperature);

//             构建消息列表
            List<Map<String,String>> messages = new ArrayList<>();

//            用构建好的提示词 设置系统角色
            messages.add(Map.of("role","system","content",systemPrompt));

//            添加之前对话的历史记录 控制添加历史对话条数
            int start = Math.max(0,history.size()-8);

//          将最近几轮的对话 添加到新的对话里
            for(int i= start; i<history.size();i++ ){
                messages.add(history.get(i));
            }

//          把用户正在进行的提问 添加到对话里面
            messages.add(Map.of("role","user","content",question));
            body.put("messages",messages);

//          构建请求
            HttpHeaders httpHeaders = new HttpHeaders();

//            设置请求提交参数格式
            httpHeaders.setContentType(MediaType.APPLICATION_CBOR);
            httpHeaders.setBearerAuth(apikey);
//            封装完整请求
            HttpEntity<Map<String,Object>> entity  = new HttpEntity<>(body,httpHeaders);
            String url = baseUrl+"/v1/chat/completions";
            ResponseEntity<Map> response = restTemplate.exchange(url,HttpMethod.POST,entity,Map.class);

            if(response.getBody()!=null){

//                获取 问答候选列表
                List<Map> choice = (List<Map>)response.getBody().get("choice");
//                返回值 choice 部分不为空
                if(choice != null && choice.isEmpty()){
//                    从choice 里面获取我们想要的信息
                    Map<String,Object> message = (Map<String,Object>)choice.get(0).get("message");
                    String content = (String)message.get("content");
                    return content != null ? content.trim():null;
                }
            }
            return null;


        }catch(Exception e){
           log.error("deepseek api 调用失败{}",e.getMessage());
           return null;
        }
    }

}
