package com.hexin.hxdada.controller;

import com.hexin.hxdada.common.BaseResponse;
import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.common.ResultUtils;
import com.hexin.hxdada.exception.ThrowUtils;
import com.hexin.hxdada.mapper.UserAnswerMapper;
import com.hexin.hxdada.model.dto.statistic.AppAnswerCountDTO;
import com.hexin.hxdada.model.dto.statistic.AppAnswerResultCountDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/app/statistic")
@Slf4j
public class AppStatisticController {

    @Resource
    private UserAnswerMapper userAnswerMapper;

    /**
     * 获取用户应用答题统计数据
     * @return
     */
    @GetMapping("/answer_count")
    public BaseResponse<List<AppAnswerCountDTO>> getAppAnswerCount() {
        return ResultUtils.success(userAnswerMapper.doAppAnswerCount());
    }


    /**
     * 获取App 答案结果统计
     * @return
     */
    @GetMapping("/answer_result_count")
    public BaseResponse<List<AppAnswerResultCountDTO>> getAppAnswerResultCount(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"appId非法");
        return ResultUtils.success(userAnswerMapper.doAppAnswerResultCount(appId));
    }
}
