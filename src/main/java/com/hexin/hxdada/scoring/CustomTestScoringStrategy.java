package com.hexin.hxdada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.model.dto.question.QuestionContentDTO;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.Question;
import com.hexin.hxdada.model.entity.ScoringResult;
import com.hexin.hxdada.model.entity.UserAnswer;
import com.hexin.hxdada.model.vo.QuestionVO;
import com.hexin.hxdada.service.QuestionService;
import com.hexin.hxdada.service.ScoringResultService;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测评类应用评分策略
 */
@ScoringStrategyConfig(appType = 1 , scoringStrategy = 0)
public class CustomTestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;
    @Resource
    private ScoringResultService scoringResultService;


    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        // 1.根据 id 查询到题目和题目结果信息
        Long appId = app.getId();
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR, "appId非法");
        Question question = questionService.getOne(
                Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
        );
        List<ScoringResult> scoringResultList = scoringResultService.list(
                Wrappers.lambdaQuery(ScoringResult.class).eq(ScoringResult::getAppId, appId)
        );
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

        // 2.根据自定义算法计算得分,统计用户每个选择对应的属性个数，如 I = 10 个，E = 5 个
        // 初始化一个 Map，用于存储每个选项的计数
        Map<String, Integer> optionCount = new HashMap<>();
        // 遍历题目列表
        for (QuestionContentDTO questionContentDTO : questionContentDTOList) {
            // 遍历用户答案列表
            for (String answer : choices) {
                // 遍历题目中的选项
                for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                    // 如果答案和选项的key匹配
                    if (option.getKey().equals(answer)) {
                        // 获取选项的result属性
                        String result = option.getResult();
                        // 如果result属性不在optionCount中，初始化为0
                        if (!optionCount.containsKey(result)) {
                            optionCount.put(result, 0);
                        }
                        // 在optionCount中增加计数
                        optionCount.put(result, optionCount.get(result) + 1);
                    }
                }
            }
        }

        // 3. 遍历每种评分结果，计算哪个结果的得分更高
        // 初始化最高分数和最高分数对应的评分结果
        int maxScore = 0;
        ScoringResult scoringResult = scoringResultList.get(0);
        // 遍历评分结果列表
        for (ScoringResult result : scoringResultList) {
            List<String> ResultProplist = JSONUtil.toList(result.getResultProp(), String.class);
            // 计算当前评分结果的分数
            int score = ResultProplist.stream()
                    .mapToInt(prop -> optionCount.getOrDefault(prop, 0))
                    .sum();

            // 如果分数高于当前最高分数，更新最高分数和最高分数对应的评分结果
            if (score > maxScore) {
                maxScore = score;
                scoringResult = result;
            }
        }

        // 4. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(scoringResult.getId());
        userAnswer.setResultName(scoringResult.getResultName());
        userAnswer.setResultDesc(scoringResult.getResultDesc());
        userAnswer.setResultPicture(scoringResult.getResultPicture());
        return userAnswer;
    }
}
