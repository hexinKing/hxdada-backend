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
import java.util.List;
import java.util.Optional;

/**
 * 得分类应用评分策略
 */
@ScoringStrategyConfig(appType = 0 , scoringStrategy = 0)
public class CustomScoreScoringStrategy implements ScoringStrategy {

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
                        .orderByDesc(ScoringResult::getResultScoreRange)
        );
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

        // 2.根据自定义算法计算得分，统计用户总得分
        // 初始化一个 count，用于存储每个选项的所得分之和
        int totalScore = 0;
        // 遍历题目列表
        for (QuestionContentDTO questionContentDTO : questionContentDTOList) {
            // 遍历用户答案列表
            for (String answer : choices) {
                // 遍历题目中的选项
                for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                    // 如果答案和选项的key匹配
                    if (option.getKey().equals(answer)) {
                        // Optional容器类，来减少程序中出现null的情况，避免空指针异常的发生
                        // of()创建一个包含非空值的Optional，orElse(0)如果Optional为空则返回默认值
                        int score = Optional.of(option.getScore()).orElse(0);
                        totalScore += score;
                     }
                }
            }
        }

        // 3. 遍历结果得分范围 resultScoreRange，找到第一个用户分数totalScore大于该值的得分范围，作为最终的结果
        ScoringResult maxScoringResult = scoringResultList.get(0);
        for (ScoringResult scoringResult : scoringResultList) {
            if (totalScore >= scoringResult.getResultScoreRange()){
                maxScoringResult = scoringResult;
                break;
            }
        }

        // 4. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultScore(totalScore);
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        return userAnswer;
    }
}
