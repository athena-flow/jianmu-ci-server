package dev.jianmu.infrastructure.mybatis.task;

import dev.jianmu.task.aggregate.ParameterRefer;
import dev.jianmu.task.repository.ParameterReferRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @class: ParameterReferRepositoryImpl
 * @description: 参数关联仓储实现
 * @author: Ethan Liu
 * @create: 2021-04-29 15:47
 **/
@Repository
public class ParameterReferRepositoryImpl implements ParameterReferRepository {
    @Override
    public void addAll(List<ParameterRefer> parameterRefers) {

    }

    @Override
    public List<ParameterRefer> findByRefAndVersionAndTargetTaskRef(String workflowRef, String workflowVersion, String targetTaskRef) {
        return null;
    }
}
