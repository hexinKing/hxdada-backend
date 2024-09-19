package com.hexin.hxdada.common;

import com.hexin.hxdada.exception.BusinessException;
import com.hexin.hxdada.model.entity.App;
import com.hexin.hxdada.model.entity.UserAnswer;
import com.hexin.hxdada.model.enums.AppTypeEnum;
import com.hexin.hxdada.model.enums.ScoringStrategyEnum;
import com.hexin.hxdada.scoring.CustomScoreScoringStrategy;
import com.hexin.hxdada.scoring.CustomTestScoringStrategy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 评分策略的全局执行器
 * 优点：简单，容易理解
 * 缺点：扩展性差，每次新增策略需要修改代码
 */
@Service
@Deprecated
public class ScoringStrategyContext {

    @Resource
    private CustomScoreScoringStrategy customScoreScoringStrategy;

    @Resource
    private CustomTestScoringStrategy customTestScoringStrategy;

    /**
     * 评分
     *
     * @param choiceList
     * @param app
     * @return
     * @throws Exception
     */
    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        AppTypeEnum appTypeEnum = AppTypeEnum.getEnumByValue(app.getAppType());
        ScoringStrategyEnum appScoringStrategyEnum = ScoringStrategyEnum.getEnumByValue(app.getScoringStrategy());
        if (appTypeEnum == null || appScoringStrategyEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
        }
        // 根据不同的应用类别和评分策略，选择对应的策略执行
        switch (appTypeEnum) {
            case SCORE:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        return customScoreScoringStrategy.doScore(choiceList, app);
                    case AI:
                        break;
                }
                break;
            case TEST:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        return customTestScoringStrategy.doScore(choiceList, app);
                    case AI:
                        break;
                }
                break;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }
}
