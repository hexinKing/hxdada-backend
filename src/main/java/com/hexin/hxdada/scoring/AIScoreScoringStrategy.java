package com.hexin.hxdada.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.manager.AIManager;
import com.hexin.hxdada.model.dto.question.QuestionContentDTO;
import com.hexin.hxdada.model.dto.question.QusetionAnswerDTO;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.Question;
import com.hexin.hxdada.model.entity.UserAnswer;
import com.hexin.hxdada.model.vo.QuestionVO;
import com.hexin.hxdada.service.QuestionService;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * AI
 * 得分类应用评分策略
 */
@ScoringStrategyConfig(appType = 0 , scoringStrategy = 1)
public class AIScoreScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;
    @Resource
    private AIManager aiManager;
    @Resource
    private RedissonClient redissonClient;

    @Value("${aiPicture}")
    private String aiPicture;

    // redis缓存key,业务前缀防止冲突
    public static final String AI_TEST_SCORING_STRATEGY_KEY = "AI_SCORE_SCORING_STRATEGY_KEY";


    /**
     * 初始化Caffeine本地缓存
     */
    private final Cache<String, String> answerCacheMap =
            Caffeine.newBuilder()
                    // 用于设置缓存的初始容量
                    .initialCapacity(1024)
                    // 缓存5分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();


    /**
     * 构建缓存key
     *
     * @param appId
     * @param choicesStr
     * @return
     */
    private String buildCacheKey(Long appId, String choicesStr) {
        return  DigestUtil.md5Hex(choicesStr)+":"+appId ;
    }


    // 系统消息
    public static final String SYSTEM_MESSAGE = "# Role: 判题专家\n" +
            "\n" +
            "## Goals\n" +
            "- 你是一位严谨的判题专家，我会给你一些信息去分析题目\n" +
            "\n" +
            "## message\n" +
            "-应用名称，\n" +
            "-【【【应用描述】】】，\n" +
            "-题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "\n" +
            "## Constrains\n" +
            "- 必须按照我给你的信息要求去分析题目。\n" +
            "- 分析的内容要尽可能的合情合理且有逻辑。\n" +
            "- 能够识别用户回答对应的题目是否正确,且用户答案中只要有一个错误的地方则视为答案错误。\n" +
            "- 严格按照我给你的评分标准范围去做评价名称（resultName），需要统计正常答案的占比，只有占比>=95%才显示优秀、占比>=80%才显示良好、占比>=60%才显示及格、占比<60%才显示不及格。\n" +
            "- 将用户输入的正确答案占比转化为对应的百分制整数得分。\n" +
            "\n" +
            "## Skills\n" +
            "- 专业的判题分析能力\n" +
            "- 理解并解析题目内容\n" +
            "- 提供准确的字段和内容\n" +
            "\n" +
            "## Example\n" +
            "{\n" +
            "  \"resultScore\": 88,\n" +
            "  \"resultName\": \"良好\",\n" +
            "  \"resultDesc\": \"恭喜您，通过了测试！这分数表明您在该分类知识领域表现良好，掌握了大部分相关内容。继续保持这种优秀的学习态度，相信您在未来的学习中会取得更大的进步！\"\n" +
            "}\n" +
            "\n" +
            "## Workflow\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，不要显示用户提交答案的正确百分比，不要给评论描述加换行符和序号，并且为错误答案做改正并解析，在 400 字以内）、题目得分（使用整数表示且尽量准确）\n" +
            "2. 严格按照我给你的 json 格式输出评价名称（resultName）和评价描述（resultDesc）、题目得分（resultScore）\n" +
            "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
            "4. 返回格式必须为 JSON 对象\n";

    /**
     * 生成题目的用户消息
     *
     * @param app
     * @param questionContentDTOList
     * @param choices
     * @return
     */
    private String getGenerateQuestionUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        // 代码优化  获取题目标题和用户答案
        List<QusetionAnswerDTO> qusetionAnswerDTOList = new ArrayList<>();
        IntStream.range(0, questionContentDTOList.size())
                .forEach(i -> {
                    QusetionAnswerDTO qusetionAnswerDTO = new QusetionAnswerDTO();
                    qusetionAnswerDTO.setUserAnswer(choices.get(i));
                    qusetionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
                    qusetionAnswerDTOList.add(qusetionAnswerDTO);
                });

        // 需要将用户的答案（A、B、B）转化为选项对应的内容
        IntStream.range(0, questionContentDTOList.size())
                .parallel()
                .forEach(i -> {
                    QuestionContentDTO questionContentDTO = questionContentDTOList.get(i);
                    for (QusetionAnswerDTO qusetionAnswerDTO : qusetionAnswerDTOList) {
                        if (qusetionAnswerDTO.getTitle().equals(questionContentDTO.getTitle())) {
                            for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                                if (option.getKey().equals(qusetionAnswerDTO.getUserAnswer())) {
                                    qusetionAnswerDTO.setUserAnswer(option.getValue());
                                    break;
                                }
                            }
                        }
                    }
                });

        userMessage.append(JSONUtil.toJsonStr(qusetionAnswerDTOList));
        return userMessage.toString();
    }


    /**
     * AI测评类应用评分策略实现
     * @param choices
     * @param app
     * @return
     * @throws Exception
     */
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        // 1.根据 id 查询到题目和题目结果信息
        Long appId = app.getId();
        // 调用本地缓存,如果命中则直接返回结果
        String cacheKey = buildCacheKey(appId, JSONUtil.toJsonStr(choices));
        String answerJson = answerCacheMap.getIfPresent(cacheKey);
        // 命中缓存则直接返回结果
        if (StrUtil.isNotBlank(answerJson)) {
            return getUserAnswer(choices, app, appId, answerJson);
        }
        // 使用redis分布式锁，解决缓存击穿的问题
        // 加锁
        RLock lock = redissonClient.getLock(AI_TEST_SCORING_STRATEGY_KEY + cacheKey);
        Boolean locked;
        try {
            // 竞争分布式锁，等待3秒，15秒后自动释放锁
            // 获取到锁则locked为true，超过三秒没获取到锁则locked为false
            locked = lock.tryLock(3, 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取锁失败");
        }
        if (!locked) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "正在处理中，请稍后重试");
        }
        try {
            // 处理逻辑
            ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR, "appId非法");
            Question question = questionService.getOne(
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
            );
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

            // 2. 调用封装方法
            String userMessage = getGenerateQuestionUserMessage(app, questionContentDTOList, choices);
            // 调用AI接口，返回相应数据
            String doSyncStableRequest = aiManager.doSyncStableRequest(SYSTEM_MESSAGE, userMessage);
            // 截取需要的 JSON 信息
            int start = doSyncStableRequest.indexOf("{");
            int end = doSyncStableRequest.lastIndexOf("}");
            String json = doSyncStableRequest.substring(start, end + 1);
            // 未命中缓存，重新生成题目和题目结果信息,并存入缓存
            answerCacheMap.put(cacheKey, json);
            // 3. 构造返回值，填充答案对象的属性
            return getUserAnswer(choices, app, appId, json);
        } finally {
            if (lock.isLocked()){
                if (lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }
        }

    }

    @NotNull
    private UserAnswer getUserAnswer(List<String> choices, App app, Long appId, String answerJson) {
        UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultPicture(aiPicture);
        return userAnswer;
    }

}
