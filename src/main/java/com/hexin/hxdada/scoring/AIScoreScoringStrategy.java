package com.hexin.hxdada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.manager.AIManager;
import com.hexin.hxdada.model.dto.question.QuestionContentDTO;
import com.hexin.hxdada.model.dto.question.QusetionAnswerDTO;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.Question;
import com.hexin.hxdada.model.entity.ScoringResult;
import com.hexin.hxdada.model.entity.UserAnswer;
import com.hexin.hxdada.model.vo.QuestionVO;
import com.hexin.hxdada.service.QuestionService;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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

    @Value("${aiPicture}")
    private String aiPicture;


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
            "- 能够识别用户回答对应的题目是否正确、在用户答案中只要有一个错误的地方则为答案错误\n" +
            "- 严格按照我给你的评分标准去做评分结论，假设正确答案得1分，错误答案得0分并统计正常答案的占比，只有占比>=95%才表示优秀、占比>=80%才表示良好、占比>=60%才表示及格、占比<60%才表示不及格，必须严格按照以上的我给出的占比范围去做评价描述\n" +
            "\n" +
            "## Skills\n" +
            "- 专业的判题分析能力\n" +
            "- 理解并解析题目内容\n" +
            "- 提供准确的字段和内容\n" +
            "\n" +
            "## Example\n" +
            "{\n" +
            "  \"resultName\": \"良好\",\n" +
            "  \"resultDesc\": \"恭喜您！您的测试结果显示正确答案占比达到或超过80%。这表明您在该分类知识领域表现良好，掌握了大部分相关内容。继续保持这种优秀的学习态度，相信您在未来的学习中会取得更大的进步！\"\n" +
            "}\n" +
            "\n" +
            "## Workflow\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，为错误答案做改正并解析，在 400 字以内）\n" +
            "2. 严格按照我给你的 json 格式输出评价名称（resultName）和评价描述（resultDesc）\n" +
            "3. 返回格式必须为 JSON 对象";

    /**
     * 生成题目的用户消息
     *
     * @param app
     * @param questionContentDTOList
     * @param choices
     * @return
     */
    private String getGenerateQuestionUserMessage(App app , List<QuestionContentDTO> questionContentDTOList , List<String> choices  ) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        // 获取题目标题和用户答案
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


    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        // 1.根据 id 查询到题目和题目结果信息
        Long appId = app.getId();
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

        // 3. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = JSONUtil.toBean(json, UserAnswer.class);
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultPicture(aiPicture);
        return userAnswer;
    }
}
