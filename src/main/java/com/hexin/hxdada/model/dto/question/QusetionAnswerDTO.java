package com.hexin.hxdada.model.dto.question;

import lombok.Data;

/**
 * 问题回答，输出的参数（JSON数组）（AI智能评分）
 */
@Data
public class QusetionAnswerDTO {

    /**
     * 题目标题
     */
    private String title;

    /**
     * 用户答案
     */
    private String userAnswer;

}
