package dev.jianmu.application.service.internal;

import dev.jianmu.application.command.WorkflowStartCmd;
import dev.jianmu.application.exception.DataNotFoundException;
import dev.jianmu.infrastructure.GlobalProperties;
import dev.jianmu.project.repository.ProjectRepository;
import dev.jianmu.trigger.event.TriggerFailedEvent;
import dev.jianmu.workflow.aggregate.definition.Workflow;
import dev.jianmu.workflow.aggregate.process.ProcessStatus;
import dev.jianmu.workflow.aggregate.process.WorkflowInstance;
import dev.jianmu.workflow.repository.AsyncTaskInstanceRepository;
import dev.jianmu.workflow.repository.WorkflowInstanceRepository;
import dev.jianmu.workflow.repository.WorkflowRepository;
import dev.jianmu.workflow.service.WorkflowInstanceDomainService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Ethan Liu
 * @class WorkflowInstanceInternalApplication
 * @description WorkflowInstanceInternalApplication
 * @create 2021-10-21 15:35
 */
@Service
@Slf4j
public class WorkflowInstanceInternalApplication {
    private final WorkflowRepository workflowRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final AsyncTaskInstanceRepository asyncTaskInstanceRepository;
    private final WorkflowInstanceDomainService workflowInstanceDomainService;
    private final ApplicationEventPublisher publisher;
    private final ProjectRepository projectRepository;
    private final GlobalProperties globalProperties;

    public WorkflowInstanceInternalApplication(
            WorkflowRepository workflowRepository,
            WorkflowInstanceRepository workflowInstanceRepository,
            AsyncTaskInstanceRepository asyncTaskInstanceRepository,
            WorkflowInstanceDomainService workflowInstanceDomainService,
            ApplicationEventPublisher publisher,
            ProjectRepository projectRepository,
            GlobalProperties globalProperties) {
        this.workflowRepository = workflowRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.asyncTaskInstanceRepository = asyncTaskInstanceRepository;
        this.workflowInstanceDomainService = workflowInstanceDomainService;
        this.publisher = publisher;
        this.projectRepository = projectRepository;
        this.globalProperties = globalProperties;
    }

    // ????????????
    @Transactional
    public void create(WorkflowStartCmd cmd, String projectId) {
        Workflow workflow = this.workflowRepository
                .findByRefAndVersion(cmd.getWorkflowRef(), cmd.getWorkflowVersion())
                .orElseThrow(() -> new DataNotFoundException("?????????????????????"));
        var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> new DataNotFoundException("???????????????ID:" + projectId));
        if (!project.isConcurrent()) {
            // ???????????????????????????
            int i = this.workflowInstanceRepository
                    .findByRefAndStatuses(workflow.getRef(), List.of(ProcessStatus.INIT))
                    .size();
            if (i >= this.globalProperties.getTriggerQueue().getMax()) {
                var triggerFailedEvent = TriggerFailedEvent.Builder.aTriggerFailedEvent()
                        .triggerId(cmd.getTriggerId())
                        .triggerType(cmd.getTriggerType())
                        .build();
                this.publisher.publishEvent(triggerFailedEvent);
                throw new RuntimeException("???????????????????????????????????????" + this.globalProperties.getTriggerQueue().getMax());
            }
        }
        // ??????serialNo
        AtomicInteger serialNo = new AtomicInteger(1);
        this.workflowInstanceRepository.findByRefAndSerialNoMax(workflow.getRef())
                .ifPresent(workflowInstance -> serialNo.set(workflowInstance.getSerialNo() + 1));
        // ????????????????????????
        WorkflowInstance workflowInstance = workflowInstanceDomainService.create(cmd.getTriggerId(), cmd.getTriggerType(), serialNo.get(), workflow);
        workflowInstance.init();
        this.workflowInstanceRepository.add(workflowInstance);
    }

    // ????????????
    @Transactional
    public void start(String workflowRef) {
        var project = this.projectRepository.findByWorkflowRef(workflowRef)
                .orElseThrow(() -> new DataNotFoundException("???????????????, ref::" + workflowRef));
        if (project.isConcurrent()) {
            this.workflowInstanceRepository.findByRefAndStatuses(workflowRef, List.of(ProcessStatus.INIT))
                    .forEach(workflowInstance -> {
                        workflowInstance.createVolume();
                        this.workflowInstanceRepository.save(workflowInstance);
                    });
            return;
        }
        // ????????????????????????????????????
        int i = this.workflowInstanceRepository
                .findByRefAndStatuses(workflowRef, List.of(ProcessStatus.RUNNING, ProcessStatus.SUSPENDED))
                .size();
        if (i > 0) {
            return;
        }
        this.workflowInstanceRepository.findByRefAndStatusAndSerialNoMin(workflowRef, ProcessStatus.INIT)
                .ifPresent(workflowInstance -> {
                    workflowInstance.createVolume();
                    this.workflowInstanceRepository.save(workflowInstance);
                });
    }

    // ??????????????????
    @Transactional
    public void end(String triggerId) {
        var workflowInstance = this.workflowInstanceRepository.findByTriggerId(triggerId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        workflowInstance.end();
        this.workflowInstanceRepository.save(workflowInstance);
    }

    // ????????????
    @Async
    @Transactional
    public void suspend(String instanceId) {
        var workflowInstance = this.workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        // ????????????
        MDC.put("triggerId", workflowInstance.getTriggerId());
        workflowInstance.suspend();
        this.workflowInstanceRepository.save(workflowInstance);
    }

    @Transactional
    public void resume(String instanceId, String taskRef) {
        var workflowInstance = this.workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        // ????????????
        MDC.put("triggerId", workflowInstance.getTriggerId());
        var asyncTaskInstances = this.asyncTaskInstanceRepository.findByInstanceId(instanceId);
        if (this.workflowInstanceDomainService.canResume(asyncTaskInstances, taskRef)) {
            workflowInstance.resume();
            this.workflowInstanceRepository.save(workflowInstance);
        }
    }

    // ????????????
    @Async
    @Transactional
    public void terminate(String instanceId) {
        var workflowInstance = this.workflowInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        // ????????????
        MDC.put("triggerId", workflowInstance.getTriggerId());
        workflowInstance.terminate();
        this.workflowInstanceRepository.save(workflowInstance);
    }

    // ????????????
    @Async
    @Transactional
    public void terminateByTriggerId(String triggerId) {
        var workflowInstance = this.workflowInstanceRepository.findByTriggerId(triggerId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        // ????????????
        MDC.put("triggerId", workflowInstance.getTriggerId());
        workflowInstance.terminate();
        this.workflowInstanceRepository.save(workflowInstance);
    }

    @Transactional
    public void statusCheck(String triggerId) {
        var workflowInstance = this.workflowInstanceRepository.findByTriggerId(triggerId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        workflowInstance.statusCheck();
        this.workflowInstanceRepository.commitEvents(workflowInstance);
    }
}
