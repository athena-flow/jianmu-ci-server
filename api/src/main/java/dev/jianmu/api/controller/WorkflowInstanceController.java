package dev.jianmu.api.controller;

import dev.jianmu.application.service.WorkflowInstanceApplication;
import dev.jianmu.workflow.aggregate.process.WorkflowInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;

/**
 * @class: WorkflowInstanceController
 * @description: 流程实例接口类
 * @author: Ethan Liu
 * @create: 2021-03-24 16:02
 **/
@RestController
@RequestMapping("workflow_instances")
@Tag(name = "流程实例接口", description = "提供流程实例启动停止等API")
public class WorkflowInstanceController {
    private final WorkflowInstanceApplication instanceApplication;

    @Inject
    public WorkflowInstanceController(WorkflowInstanceApplication instanceApplication) {
        this.instanceApplication = instanceApplication;
    }

    @PutMapping("/{instanceId}/{nodeRef}")
    @Operation(summary = "流程启动", description = "流程启动")
    public WorkflowInstance start(
            @Parameter(description = "流程实例ID") @PathVariable String instanceId,
            @Parameter(description = "启动节点定义名") @PathVariable String nodeRef
    ) {
        return this.instanceApplication.start(instanceId, nodeRef);
    }

    @PutMapping("/stop")
    // TODO 暂时未实现
    @Operation(summary = "流程停止", description = "流程停止", hidden = true)
    public void stop(
            @Parameter(description = "流程实例ID") String instanceId,
            @Parameter(description = "停止节点定义名") String nodeRef
    ) {
        this.instanceApplication.terminateNode(instanceId, nodeRef);
    }
}
