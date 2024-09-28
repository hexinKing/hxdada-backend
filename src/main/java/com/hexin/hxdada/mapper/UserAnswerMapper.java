package com.hexin.hxdada.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hexin.hxdada.model.dto.statistic.AppAnswerCountDTO;
import com.hexin.hxdada.model.dto.statistic.AppAnswerResultCountDTO;
import com.hexin.hxdada.model.entity.UserAnswer;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户答题记录数据库操作
 *
 * @author 矮蕉大王
 * @description 针对表【user_answer(用户答题记录)】的数据库操作Mapper
 * @createDate 2024-09-14 11:02:43
 * @Entity com.hexin.hxdada.model.entity.UserAnswer
 */
public interface UserAnswerMapper extends BaseMapper<UserAnswer> {

    @Select("select appId, count(userId) as answerCount from user_answer " +
            "group by appId order by answerCount desc")
    List<AppAnswerCountDTO> doAppAnswerCount();


    @Select("select resultName, count(resultName) as resultCount from user_answer " +
            "where appId = #{appId} group by resultName order by resultCount desc")
    List<AppAnswerResultCountDTO> doAppAnswerResultCount(Long appId);


}




