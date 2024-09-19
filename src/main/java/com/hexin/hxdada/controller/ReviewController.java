package com.hexin.hxdada.controller;

import com.hexin.hxdada.annotation.AuthCheck;
import com.hexin.hxdada.common.BaseResponse;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.common.ResultUtils;
import com.hexin.hxdada.common.ReviewRequest;
import com.hexin.hxdada.constant.UserConstant;
import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.User;
import com.hexin.hxdada.model.enums.ReviewStatusEnum;
import com.hexin.hxdada.service.AppService;
import com.hexin.hxdada.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 审核接口
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private AppService appService;
    @Resource
    private UserService userService;

    /**
     * 应用审核
     * @param reviewRequest
     * @param request
     * @return
     */
    @PostMapping("/")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doAppReview(@RequestBody ReviewRequest reviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(reviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = reviewRequest.getAppid();
        Integer reviewStatus = reviewRequest.getReviewStatus();
        // 校验
        ReviewStatusEnum reviewStatusEnum = ReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldApp.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        User loginUser = userService.getLoginUser(request);
        App app = new App();
        app.setId(id);
        app.setReviewStatus(reviewStatus);
        app.setReviewerId(loginUser.getId());
        app.setReviewMessage(reviewRequest.getReviewMessage());
        app.setReviewTime(new Date());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }



}
