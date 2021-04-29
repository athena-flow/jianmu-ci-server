package dev.jianmu.task.repository;

import dev.jianmu.task.aggregate.InstanceParameter;

import java.util.List;
import java.util.Set;

/**
 * @class: InstanceParameterRepository
 * @description: 任务实例参数仓储
 * @author: Ethan Liu
 * @create: 2021-04-28 16:17
 **/
public interface InstanceParameterRepository {
    void addAll(Set<InstanceParameter> instanceParameters);

    List<InstanceParameter> findByInstanceIdAndType(String instanceId, InstanceParameter.Type type);

    List<InstanceParameter> findByBusinessIdAndAsyncTaskRefAndType(String businessId, String asyncTaskRef, InstanceParameter.Type type);
}
