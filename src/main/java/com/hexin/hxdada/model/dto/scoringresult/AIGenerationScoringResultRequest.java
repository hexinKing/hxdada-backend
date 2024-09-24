package com.hexin.hxdada.model.dto.scoringresult;

import lombok.Data;

/**
 * AI生成评分结果
 */
@Data
public class AIGenerationScoringResultRequest {

    /**
     * 评价名称
     */
    private String resultName;

    /**
     * 评价描述
     */
    private String resultDesc;

}
