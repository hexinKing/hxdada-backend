package com.hexin.hxdada.scoring;

import com.hexin.hxdada.common.ErrorCode;
import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.UserAnswer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 评分策略执行器
 */
@Component
public class ScoringStrategyExecutor {

    // 策略列表，获取所有的策略
    @Resource
    private List<ScoringStrategy> scoringStrategyList;


    /**
     * 评分
     *
     * @param choiceList
     * @param app
     * @return
     * @throws Exception
     */
    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        // 校验
        Integer appType = app.getAppType();
        Integer appScoringStrategy = app.getScoringStrategy();
        if (appType == null || appScoringStrategy == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
        }
        // 根据注解获取策略
        for (ScoringStrategy strategy : scoringStrategyList) {
            // 检查一个类或方法是否具有特定的注解
            if (strategy.getClass().isAnnotationPresent(ScoringStrategyConfig.class)) {
                // 通过反射获取注解
                ScoringStrategyConfig scoringStrategyConfig = strategy.getClass().getAnnotation(ScoringStrategyConfig.class);
                // 比较注解中的值和传入的值是否相等,相等则执行相应的策略
                if (scoringStrategyConfig.appType() == appType && scoringStrategyConfig.scoringStrategy() == appScoringStrategy) {
                    return strategy.doScore(choiceList, app);
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }
}
