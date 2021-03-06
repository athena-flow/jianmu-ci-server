package dev.jianmu.workflow.aggregate.definition;

import dev.jianmu.workflow.el.EvaluationContext;
import dev.jianmu.workflow.el.ExpressionLanguage;

import java.util.List;

/**
 * @author Ethan Liu
 * @class Gateway
 * @description 流程网关类型
 * @create 2021-03-16 10:17
 */
public interface Gateway {
    Branch calculateTarget(ExpressionLanguage expressionLanguage, EvaluationContext context);

    List<String> findNonLoopBranch();

    List<String> findLoopBranch();

    boolean hasNonLoopBranch();
}
