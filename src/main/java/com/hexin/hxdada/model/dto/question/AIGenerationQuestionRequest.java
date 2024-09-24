package com.hexin.hxdada.model.dto.question;

import lombok.Data;

/**
 * AI生成题目
 *
 */
@Data
public class AIGenerationQuestionRequest {

    /**
     * 应用Id
     */
    private Long appId;

    /**
     * 题目数量
     */
    private Integer questionNumber = 10;

    /**
     * 题目选项数量
     */
    private Integer optionNumber = 2;

}
