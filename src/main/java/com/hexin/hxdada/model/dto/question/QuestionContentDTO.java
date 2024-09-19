package com.hexin.hxdada.model.dto.question;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionContentDTO {
    /**
     * 标题
     */
    private String title;
    /**
     * 选项
     */
    private List<Option> options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Option {
        /**
         * 结果（测评类应用）
         */
        private String result;
        /**
         * 分数（得分类应用）
         */
        private int score;
        /**
         * 值（例如：选择题A对应的内容）
         */
        private String value;
        /**
         * 键（例如：选择题A）
         */
        private String key;
    }
}
