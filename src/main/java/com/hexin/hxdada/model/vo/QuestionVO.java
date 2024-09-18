package com.hexin.hxdada.model.vo;

import cn.hutool.json.JSONUtil;
import com.hexin.hxdada.model.dto.question.QuestionContentDTO;
import com.hexin.hxdada.model.entity.Question;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目视图
 *
 *
 */
@Data
public class QuestionVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 题目内容（json格式）
     */
    private List<QuestionContentDTO> questionContent;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;


    /**
     * 封装类转对象
     *
     * @param QuestionVO
     * @return
     */
    public static Question voToObj(QuestionVO QuestionVO) {
        if (QuestionVO == null) {
            return null;
        }
        Question Question = new Question();
        BeanUtils.copyProperties(QuestionVO, Question);
        List<QuestionContentDTO> questionContentDTOS = QuestionVO.getQuestionContent();
        Question.setQuestionContent(JSONUtil.toJsonStr(questionContentDTOS));
        return Question;
    }

    /**
     * 对象转封装类
     *
     * @param Question
     * @return
     */
    public static QuestionVO objToVo(Question Question) {
        if (Question == null) {
            return null;
        }
        QuestionVO QuestionVO = new QuestionVO();
        BeanUtils.copyProperties(Question, QuestionVO);
        QuestionVO.setQuestionContent(JSONUtil.toList(Question.getQuestionContent(), QuestionContentDTO.class));
        return QuestionVO;
    }
}
