package com.hexin.hxdada.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hexin.hxdada.annotation.AuthCheck;
import com.hexin.hxdada.common.BaseResponse;
import com.hexin.hxdada.common.DeleteRequest;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.common.ResultUtils;
import com.hexin.hxdada.constant.UserConstant;
import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.manager.AIManager;
import com.hexin.hxdada.model.dto.question.*;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.Question;
import com.hexin.hxdada.model.entity.User;
import com.hexin.hxdada.model.enums.AppTypeEnum;
import com.hexin.hxdada.model.enums.ReviewStatusEnum;
import com.hexin.hxdada.model.vo.QuestionVO;
import com.hexin.hxdada.service.AppService;
import com.hexin.hxdada.service.QuestionService;
import com.hexin.hxdada.service.UserService;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private AIManager aiManager;

    @Resource
    private Scheduler vipScheduler;


    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        question.setQuestionContent(JSONUtil.toJsonStr(questionAddRequest.getQuestionContentDTO()));
        // 数据校验
        questionService.validQuestion(question, true);
        // 校验审核状态
        App app = appService.getById(question.getAppId());
        Integer reviewStatus = app.getReviewStatus();
        ThrowUtils.throwIf(reviewStatus != ReviewStatusEnum.PASS.getValue(), ErrorCode.NO_AUTH_ERROR);
        // 校验接受的appId对应的app应用是否已存在题目，一个应用只能创建一个题目
        Question questionOne = questionService.getOne(
                Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, question.getAppId())
        );
        if (questionOne != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "抱歉！一个应用只能创建一个题目");
        }
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        question.setQuestionContent(JSONUtil.toJsonStr(questionUpdateRequest.getQuestionContentDTO()));
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        question.setQuestionContent(JSONUtil.toJsonStr(questionEditRequest.getQuestionContentDTO()));
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion

    // region AI创建题目

    public static final String SYSTEM_MESSAGE = "# Role: 题目生成专家\n" +
            "\n" +
            "## Goals\n" +
            "- 你是一位严谨的出题专家，我会给你一些信息让你去生成题目\n" +
            "\n" +
            "## message\n" +
            "-应用名称，\n" +
            "-【【【应用描述】】】，\n" +
            "-应用类别，\n" +
            "-要生成的题目数，\n" +
            "-每道题目的选项数\n" +
            "\n" +
            "## Constrains\n" +
            "- 必须按照我给你的信息要求去生成题目,生成的题目数量和每道题目的选项数量必须和用户给的参数保持一致，不能超出限制。\n" +
            "- 生成的题目内容要尽可能的合情合理且有逻辑。\n" +
            "- 题目类型包含测评类和得分类，如果用户输入的是测评类则去除字段\"score\"且不生成相关内容，同理如果是得分类则去除字段\"value\"且不生成相关内容\n" +
            "- 当生成得分类题目时，生成的题目选项为单选类型，只能有一个正确答案，且正确答案得1分，错误答案得0分\n" +
            "\n" +
            "## Skills\n" +
            "- 专业的题目出题能力\n" +
            "- 理解并解析题目内容\n" +
            "- 提供准确的字段和内容\n" +
            "\n" +
            "## Example\n" +
            "[\n" +
            "    {\n" +
            "        \"options\": [\n" +
            "            {\n" +
            "                \"result\": \"I\",\n" +
            "                \"value\": \"独自工作\",\n" +
            "                \"score\": 1,\n" +
            "                \"key\": \"A\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"result\": \"E\",\n" +
            "                \"value\": \"与他人合作\",\n" +
            "                \"score\": 0,\n" +
            "                \"key\": \"B\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"title\": \"你通常更喜欢\"\n" +
            "    }\n" +
            "]\n" +
            "\n" +
            "\n" +
            "## Workflow\n" +
            "1. 要求：题目和选项尽可能地短，题目不要包含序号，每题的选项数以我提供的为主，题目不能重复\n" +
            "2. 严格按照我给你的 json 格式输出题目和选项 title 是题目，options 是选项，每个选项的 key 按照英文字母序（比如 A、B、C、D）以此类推，value 是选项内容，score是选项对应的得分\n" +
            "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
            "4. 返回的题目列表格式必须为 JSON 数组";

    /**
     * 生成题目的用户消息
     *
     * @param app
     * @param questionNumber
     * @param optionNumber
     * @return
     */
    private String getGenerateQuestionUserMessage(App app, int questionNumber, int optionNumber) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        userMessage.append(AppTypeEnum.getEnumByValue(app.getAppType()).getText()).append("\n");
        userMessage.append(questionNumber).append("\n");
        userMessage.append(optionNumber);
        return userMessage.toString();
    }

    /**
     * AI生成题目
     *
     * @param aiGenerationQuestionRequest
     * @return
     */
    @PostMapping("/ai_generate")
    public BaseResponse<List<QuestionContentDTO>> aiGenerateQuestion(@RequestBody AIGenerationQuestionRequest aiGenerationQuestionRequest) {
        // 获取数据、校验
        ThrowUtils.throwIf(aiGenerationQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = aiGenerationQuestionRequest.getAppId();
        Integer questionNumber = aiGenerationQuestionRequest.getQuestionNumber();
        ThrowUtils.throwIf(questionNumber > 20, ErrorCode.NOT_FOUND_ERROR, "抱歉！不可以一次性生成超过20道题目");
        Integer optionNumber = aiGenerationQuestionRequest.getOptionNumber();
        ThrowUtils.throwIf(optionNumber > 4, ErrorCode.NOT_FOUND_ERROR, "抱歉！不可以一次性生成超过4道选项");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取UserMessage
        String UserMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // AI生成题目
        String doSyncStableRequest = aiManager.doSyncStableRequest(SYSTEM_MESSAGE, UserMessage);
        // 截取需要的 JSON 信息
        int start = doSyncStableRequest.indexOf("[");
        int end = doSyncStableRequest.lastIndexOf("]");
        String json = doSyncStableRequest.substring(start, end + 1);
        // 封装数据返回结果
        List<QuestionContentDTO> questionContentDTOS = JSONUtil.toList(json, QuestionContentDTO.class);
        return ResultUtils.success(questionContentDTOS);
    }


    /**
     * AI生成题目（SSE 效率更高，用户体验更好）
     *
     * @param aiGenerationQuestionRequest
     * @param request
     * @return
     */
    @GetMapping("/ai_generate/sse")
    public SseEmitter aiGenerateQuestionSSE(AIGenerationQuestionRequest aiGenerationQuestionRequest, HttpServletRequest request) {
        // 获取数据、校验
        ThrowUtils.throwIf(aiGenerationQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = aiGenerationQuestionRequest.getAppId();
        Integer questionNumber = aiGenerationQuestionRequest.getQuestionNumber();
        ThrowUtils.throwIf(questionNumber > 20, ErrorCode.NOT_FOUND_ERROR, "抱歉！不可以一次性生成超过20道题目");
        Integer optionNumber = aiGenerationQuestionRequest.getOptionNumber();
        ThrowUtils.throwIf(optionNumber > 4, ErrorCode.NOT_FOUND_ERROR, "抱歉！不可以一次性生成超过4道选项");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取UserMessage
        String UserMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 创建SSE连接对象,0表示不设置超时时间
        SseEmitter sseEmitter = new SseEmitter(0L);
        // AI生成题目（流式请求）
        Flowable<ModelData> modelDataFlowable = aiManager.doStreamSyncStableRequest(SYSTEM_MESSAGE, UserMessage);
        // AtomicInteger它提供了一种线程安全的方式来操作整数，其内部实现使用了原子操作，因此可以保证线程安全。用来统计当前已处理的字符数。
        AtomicInteger atomicInteger = new AtomicInteger(0);
        // 创建一个 StringBuilder 对象，用来保存截取到的 JSON 信息
        StringBuilder stringBuilder = new StringBuilder();

        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 普通用户使用默认全局线程池,VIP用户或者管理员使用VIP自定义线程池
        Scheduler scheduler = Schedulers.io();
        if ("admin".equals(loginUser.getUserRole())) {
            scheduler = vipScheduler;
        }

        // 使用RxJava订阅数据流，将数据流进行处理
        modelDataFlowable
                .subscribeOn(Schedulers.io()) // 指定创建流的线程池
                // 先将数据流进行过滤,去除无用的字符
                .map(chunk -> chunk.getChoices().get(0).getDelta().getContent())
                .map(message -> message.replaceAll("\\s", ""))
                .filter(StrUtil::isNotBlank)
                // 讲数据转化为一个一个的字符流
                .flatMap(message -> {
                    List<Character> characterList = new ArrayList<>();
                    for (char c : message.toCharArray()) {
                        characterList.add(c);
                    }
                    // fromIterable用于将一个可迭代的对象（如List、Set等）转换为一个Flowable对象
                    return Flowable.fromIterable(characterList);
                })
                // 拿取每一个字符流，截取需要的 JSON 信息，并封装数据返回结果给前段,当atomicInteger=0时表示已经截取到了需要的数据
                .doOnNext(c -> {
                    {
                        if (c == '{') {
                            // 遇到 [ 则加一
                            atomicInteger.addAndGet(1);
                        }
                        if (atomicInteger.get() > 0) {
                            //添加到 StringBuilder 中
                            stringBuilder.append(c);
                        }
                        if (c == '}') {
                            // 遇到 ] 则减一
                            atomicInteger.addAndGet(-1);
                            // 累计的数据满足要求时，sse需要压缩为JSON格式，并推送至前端，并清空 StringBuilder
                            if (atomicInteger.get() == 0) {
                                sseEmitter.send(JSONUtil.toJsonStr(stringBuilder.toString()));
                                // 清空 StringBuilder
                                stringBuilder.setLength(0);
                            }
                        }
                    }
                })
                .doOnError(error -> log.error("AI生成题目失败", error))
                // sseEmitter::complete 表示数据流处理完成
                .doOnComplete(sseEmitter::complete).subscribe();
        return sseEmitter;
    }

    // endregion


    // region 功能扩展（将历史生成的题目标题关联到上下文中,让AI排除掉重复题目的生成） 未实现
//    /**
//     * 将历史生成的题目标题关联到上下文中,让AI排除掉重复题目的生成
//     * @param number
//     * @return
//     */
//    private String AICreateTitle(int number) {
//        String appAiSysMessageConfig = "系统提示词";
//        String appAiUserMessageConfig = "用户提示词";
//
//        //经过测试，需要每 20 道题一组，ChatGLM 拒绝一次性输出大量题目
//        List<ChatMessage> assistantChatMessageList = new ArrayList<>();
//        while (number >= 10) {
//            invokeAiLoop(number, appAiSysMessageConfig, appAiUserMessageConfig, assistantChatMessageList);
//            number = number - 10;
//        }
//        if (number > 0) {
//            invokeAiLoop(number, appAiSysMessageConfig, appAiUserMessageConfig, assistantChatMessageList);
//        }
//        //将结果合并成一个 List 返回
//        JSONArray result = new JSONArray();
//        for (ChatMessage message : assistantChatMessageList) {
//            //将每个消息的内容解析为JSONArray，将JSON字符串解析成JSON数组：。
//            JSONArray jsonArray = JSONArray.parseArray(String.valueOf(message.getContent()));
//            //将解析后的JSONArray添加到结果列表result中
//            result.addAll(jsonArray);
//        }
//        return JSON.toJSONString(result);
//    }

//    /**
//     * 将历史生成的题目标题关联到上下文中
//     * @param number
//     * @param appAiSysMessageConfig
//     * @param appAiUserMessageConfig
//     * @param assistantChatMessages
//     */
//    private void invokeAiLoop(int number, String appAiSysMessageConfig, String appAiUserMessageConfig, List<ChatMessage> assistantChatMessages) {
//        // 每次需要重建request，因为一次调用后sokcet通道已经被关闭，复用老的 request 会复用老链接会报错
//        //创建一个ChatCompletionRequest对象，使用ChatGLMUtil的buildSyncUnStableRequest方法构建请求。这里使用Math.min确保不会超过10个题目
//        ChatCompletionRequest chatCompletionRequest = ChatGLMUtil.buildSyncUnStableRequest(appAiSysMessageConfig, String.format(appAiUserMessageConfig, Math.min(number, 10), 2));
//        //将assistantChatMessages列表中的所有消息添加到请求的消息列表中。
//        chatCompletionRequest.getMessages().addAll(assistantChatMessages);
//        //调用client的invokeModelApi方法，发送请求并接收响应
//        ModelApiResponse invokeModelApiResp = client.invokeModelApi(chatCompletionRequest);
//        String s = invokeModelApiResp.getData().getChoices().get(0).getMessage().getContent().toString();
//
//        int start = s.indexOf("[");
//        int end = s.lastIndexOf("]");
//        String content = s.substring(start, end + 1);
//
//        //保存上下文
//        // 创建一个新的ChatMessage对象，设置角色为助手（ASSISTANT），内容为提取的字符串，并将其添加到消息列表中。
//        assistantChatMessages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), content));
//    }

    // endregion

}
