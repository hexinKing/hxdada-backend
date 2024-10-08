package com.hexin.hxdada.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hexin.hxdada.annotation.AuthCheck;
import com.hexin.hxdada.common.BaseResponse;
import com.hexin.hxdada.common.DeleteRequest;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.common.ResultUtils;
import com.hexin.hxdada.constant.UserConstant;
import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.model.dto.useranswer.UserAnswerAddRequest;
import com.hexin.hxdada.model.dto.useranswer.UserAnswerEditRequest;
import com.hexin.hxdada.model.dto.useranswer.UserAnswerQueryRequest;
import com.hexin.hxdada.model.dto.useranswer.UserAnswerUpdateRequest;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.User;
import com.hexin.hxdada.model.entity.UserAnswer;
import com.hexin.hxdada.model.vo.UserAnswerVO;
import com.hexin.hxdada.scoring.ScoringStrategyExecutor;
import com.hexin.hxdada.service.AppService;
import com.hexin.hxdada.service.UserAnswerService;
import com.hexin.hxdada.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户答题记录接口
 *
 *
 */
@RestController
@RequestMapping("/userAnswer")
@Slf4j
public class UserAnswerController {

    @Resource
    private UserAnswerService userAnswerService;
    @Resource
    private UserService userService;
    @Resource
    private ScoringStrategyExecutor scoringStrategyExecutor;
    @Resource
    private AppService appService;


    /**
     * 生成用户答题记录 唯一id、实现幂等设计
     * @return
     */
    @GetMapping("/generate/id")
    public BaseResponse<Long> generateUserAnswerId() {
        return ResultUtils.success(IdUtil.getSnowflakeNextId());
    }

    // region 增删改查

    /**
     * 创建用户答题记录
     *
     * @param userAnswerAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addUserAnswer(@RequestBody UserAnswerAddRequest userAnswerAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userAnswerAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 在此处将实体类和 DTO 进行转换
        UserAnswer userAnswer = new UserAnswer();
        BeanUtils.copyProperties(userAnswerAddRequest, userAnswer);
        userAnswer.setChoices(JSONUtil.toJsonStr(userAnswerAddRequest.getChoices()));
        // 数据校验
        userAnswerService.validUserAnswer(userAnswer, true);
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        userAnswer.setUserId(loginUser.getId());
        // 利用数据库唯一索引的特性，防止重复提交，保证幂等性
        userAnswer.setId(userAnswerAddRequest.getId());
        // 写入数据库
        try {
            boolean result = userAnswerService.save(userAnswer);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        }catch (DuplicateKeyException e){
            // 重复提交，返回成功
        }

        // 调用评分策略接口,计算得分更新数据库
        Long appId = userAnswer.getAppId();
        App app = appService.getById(appId);
        try {
            // 调用评分策略执行器
            UserAnswer userAnswerRequest = scoringStrategyExecutor.doScore(userAnswerAddRequest.getChoices(), app);
            // 更新数据库
            userAnswerRequest.setId(userAnswer.getId());
            // user_answer分表后不能再更新分表字段
            userAnswerRequest.setAppId(null);
            userAnswerService.updateById(userAnswerRequest);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "评分失败");
        }
        // 返回新写入的数据 id
        long newUserAnswerId = userAnswer.getId();
        return ResultUtils.success(newUserAnswerId);
    }

    /**
     * 删除用户答题记录
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUserAnswer(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        UserAnswer oldUserAnswer = userAnswerService.getById(id);
        ThrowUtils.throwIf(oldUserAnswer == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldUserAnswer.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = userAnswerService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新用户答题记录（仅管理员可用）
     *
     * @param userAnswerUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUserAnswer(@RequestBody UserAnswerUpdateRequest userAnswerUpdateRequest) {
        if (userAnswerUpdateRequest == null || userAnswerUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        UserAnswer userAnswer = new UserAnswer();
        BeanUtils.copyProperties(userAnswerUpdateRequest, userAnswer);
        userAnswer.setChoices(JSONUtil.toJsonStr(userAnswerUpdateRequest.getChoices()));
        // 数据校验
        userAnswerService.validUserAnswer(userAnswer, false);
        // 判断是否存在
        long id = userAnswerUpdateRequest.getId();
        UserAnswer oldUserAnswer = userAnswerService.getById(id);
        ThrowUtils.throwIf(oldUserAnswer == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = userAnswerService.updateById(userAnswer);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户答题记录（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserAnswerVO> getUserAnswerVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        UserAnswer userAnswer = userAnswerService.getById(id);
        ThrowUtils.throwIf(userAnswer == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(userAnswerService.getUserAnswerVO(userAnswer, request));
    }

    /**
     * 分页获取用户答题记录列表（仅管理员可用）
     *
     * @param userAnswerQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserAnswer>> listUserAnswerByPage(@RequestBody UserAnswerQueryRequest userAnswerQueryRequest) {
        long current = userAnswerQueryRequest.getCurrent();
        long size = userAnswerQueryRequest.getPageSize();
        // 查询数据库
        Page<UserAnswer> userAnswerPage = userAnswerService.page(new Page<>(current, size),
                userAnswerService.getQueryWrapper(userAnswerQueryRequest));
        return ResultUtils.success(userAnswerPage);
    }

    /**
     * 分页获取用户答题记录列表（封装类）
     *
     * @param userAnswerQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserAnswerVO>> listUserAnswerVOByPage(@RequestBody UserAnswerQueryRequest userAnswerQueryRequest,
                                                               HttpServletRequest request) {
        long current = userAnswerQueryRequest.getCurrent();
        long size = userAnswerQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<UserAnswer> userAnswerPage = userAnswerService.page(new Page<>(current, size),
                userAnswerService.getQueryWrapper(userAnswerQueryRequest));
        // 获取封装类
        return ResultUtils.success(userAnswerService.getUserAnswerVOPage(userAnswerPage, request));
    }

    /**
     * 分页获取当前登录用户创建的用户答题记录列表
     *
     * @param userAnswerQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<UserAnswerVO>> listMyUserAnswerVOByPage(@RequestBody UserAnswerQueryRequest userAnswerQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(userAnswerQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        userAnswerQueryRequest.setUserId(loginUser.getId());
        long current = userAnswerQueryRequest.getCurrent();
        long size = userAnswerQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<UserAnswer> userAnswerPage = userAnswerService.page(new Page<>(current, size),
                userAnswerService.getQueryWrapper(userAnswerQueryRequest));
        // 获取封装类
        return ResultUtils.success(userAnswerService.getUserAnswerVOPage(userAnswerPage, request));
    }

    /**
     * 编辑用户答题记录（给用户使用）
     *
     * @param userAnswerEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editUserAnswer(@RequestBody UserAnswerEditRequest userAnswerEditRequest, HttpServletRequest request) {
        if (userAnswerEditRequest == null || userAnswerEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        UserAnswer userAnswer = new UserAnswer();
        BeanUtils.copyProperties(userAnswerEditRequest, userAnswer);
        userAnswer.setChoices(JSONUtil.toJsonStr(userAnswerEditRequest.getChoices()));
        // 数据校验
        userAnswerService.validUserAnswer(userAnswer, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = userAnswerEditRequest.getId();
        UserAnswer oldUserAnswer = userAnswerService.getById(id);
        ThrowUtils.throwIf(oldUserAnswer == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldUserAnswer.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = userAnswerService.updateById(userAnswer);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion



}
