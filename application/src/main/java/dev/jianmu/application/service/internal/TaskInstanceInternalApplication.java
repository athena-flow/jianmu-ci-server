package dev.jianmu.application.service.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jianmu.application.command.TaskActivatingCmd;
import dev.jianmu.application.exception.DataNotFoundException;
import dev.jianmu.application.query.NodeDef;
import dev.jianmu.application.query.NodeDefApi;
import dev.jianmu.el.ElContext;
import dev.jianmu.infrastructure.storage.MonitoringFileService;
import dev.jianmu.node.definition.aggregate.NodeParameter;
import dev.jianmu.task.aggregate.InstanceParameter;
import dev.jianmu.task.aggregate.InstanceStatus;
import dev.jianmu.task.aggregate.NodeInfo;
import dev.jianmu.task.aggregate.TaskInstance;
import dev.jianmu.task.repository.InstanceParameterRepository;
import dev.jianmu.task.repository.TaskInstanceRepository;
import dev.jianmu.task.service.InstanceDomainService;
import dev.jianmu.trigger.event.TriggerEvent;
import dev.jianmu.trigger.repository.TriggerEventRepository;
import dev.jianmu.workflow.aggregate.parameter.Parameter;
import dev.jianmu.workflow.el.ExpressionLanguage;
import dev.jianmu.workflow.repository.ParameterRepository;
import dev.jianmu.workflow.repository.WorkflowInstanceRepository;
import dev.jianmu.workflow.repository.WorkflowRepository;
import dev.jianmu.workflow.service.ParameterDomainService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Ethan Liu
 * @class TaskInstanceInternalApplication
 * @description TaskInstanceInternalApplication
 * @create 2021-10-21 15:25
 */
@Service
@Slf4j
public class TaskInstanceInternalApplication {
    private final TaskInstanceRepository taskInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final InstanceDomainService instanceDomainService;
    private final ParameterRepository parameterRepository;
    private final ParameterDomainService parameterDomainService;
    private final TriggerEventRepository triggerEventRepository;
    private final InstanceParameterRepository instanceParameterRepository;
    private final WorkerInternalApplication workerInternalApplication;
    private final NodeDefApi nodeDefApi;
    private final ExpressionLanguage expressionLanguage;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final MonitoringFileService monitoringFileService;

    public TaskInstanceInternalApplication(
            TaskInstanceRepository taskInstanceRepository,
            WorkflowRepository workflowRepository,
            InstanceDomainService instanceDomainService,
            ParameterRepository parameterRepository,
            ParameterDomainService parameterDomainService,
            TriggerEventRepository triggerEventRepository,
            InstanceParameterRepository instanceParameterRepository,
            WorkerInternalApplication workerInternalApplication,
            NodeDefApi nodeDefApi,
            ExpressionLanguage expressionLanguage,
            WorkflowInstanceRepository workflowInstanceRepository,
            MonitoringFileService monitoringFileService
    ) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.workflowRepository = workflowRepository;
        this.instanceDomainService = instanceDomainService;
        this.parameterRepository = parameterRepository;
        this.parameterDomainService = parameterDomainService;
        this.triggerEventRepository = triggerEventRepository;
        this.instanceParameterRepository = instanceParameterRepository;
        this.workerInternalApplication = workerInternalApplication;
        this.nodeDefApi = nodeDefApi;
        this.expressionLanguage = expressionLanguage;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.monitoringFileService = monitoringFileService;
    }

    public List<TaskInstance> findRunningTask() {
        return this.taskInstanceRepository.findRunningTask();
    }

    @Transactional
    public void create(TaskActivatingCmd cmd) {
        var workflow = this.workflowRepository.findByRefAndVersion(cmd.getWorkflowRef(), cmd.getWorkflowVersion())
                .orElseThrow(() -> new DataNotFoundException("?????????????????????: " + cmd.getWorkflowRef()));
        var asyncTask = workflow.findNode(cmd.getNodeRef());
        var nodeDef = this.nodeDefApi.getByType(asyncTask.getType());
        // ??????????????????
        List<TaskInstance> taskInstances = this.taskInstanceRepository.findByBusinessId(cmd.getAsyncTaskInstanceId());
        // ?????????????????????
        this.instanceDomainService.runningCheck(taskInstances);
        var nodeInfo = NodeInfo.Builder.aNodeDef()
                .name(nodeDef.getName())
                .icon(nodeDef.getIcon())
                .description(nodeDef.getDescription())
                .creatorName(nodeDef.getCreatorName())
                .creatorRef(nodeDef.getCreatorRef())
                .ownerName(nodeDef.getOwnerName())
                .ownerRef(nodeDef.getOwnerRef())
                .ownerType(nodeDef.getOwnerType())
                .workerType(nodeDef.getWorkerType())
                .type(nodeDef.getType())
                .documentLink(nodeDef.getDocumentLink())
                .sourceLink(nodeDef.getSourceLink())
                .build();
        var taskInstance = TaskInstance.Builder.anInstance()
                .serialNo(taskInstances.size() + 1)
                .defKey(asyncTask.getType())
                .nodeInfo(nodeInfo)
                .asyncTaskRef(asyncTask.getRef())
                .workflowRef(workflow.getRef())
                .workflowVersion(workflow.getVersion())
                .businessId(cmd.getAsyncTaskInstanceId())
                .triggerId(cmd.getTriggerId())
                .build();
        // ???????????????
        var eventParameters = this.triggerEventRepository.findById(cmd.getTriggerId())
                .map(TriggerEvent::getParameters)
                .orElseGet(List::of);
        var instanceParameters = this.instanceParameterRepository
                .findLastOutputParamByTriggerId(cmd.getTriggerId());
        // ????????????????????????
        var context = new ElContext();
        // ???????????????????????????
        workflow.getGlobalParameters()
                .forEach(globalParameter -> context.add(
                        "global",
                        globalParameter.getName(),
                        Parameter.Type.getTypeByName(globalParameter.getType()).newParameter(globalParameter.getValue()))
                );
        // ???????????????????????????
        var eventParams = eventParameters.stream()
                .map(eventParameter -> Map.entry(eventParameter.getName(), eventParameter.getParameterId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var eventParamValues = this.parameterRepository.findByIds(new HashSet<>(eventParams.values()));
        var eventMap = this.parameterDomainService.matchParameters(eventParams, eventParamValues);
        // ????????????scope???event
        eventMap.forEach((key, val) -> context.add("trigger", key, val));
        // ?????????????????????????????????
        Map<String, String> outParams = new HashMap<>();
        instanceParameters.forEach(instanceParameter -> {
            // ????????????scope???workflowType.asyncTaskRef
            outParams.put(instanceParameter.getWorkflowType() + "." + instanceParameter.getAsyncTaskRef() + "." + instanceParameter.getRef(), instanceParameter.getParameterId());
            // ????????????scope???asyncTaskRef
            outParams.put(instanceParameter.getAsyncTaskRef() + "." + instanceParameter.getRef(), instanceParameter.getParameterId());
        });
        var outParamValues = this.parameterRepository.findByIds(new HashSet<>(outParams.values()));
        var outMap = this.parameterDomainService.matchParameters(outParams, outParamValues);
        outMap.forEach(context::add);

        workflow.setExpressionLanguage(this.expressionLanguage);
        workflow.setContext(context);
        Map<String, Parameter<?>> params = Map.of();
        try {
            params = workflow.calculateTaskParams(asyncTask.getRef());
        } catch (RuntimeException e) {
            log.warn("???????????????????????????{}", e.getMessage());
            taskInstance.executeFailed();
            // ??????????????????
            this.taskInstanceRepository.add(taskInstance);
            return;
        }

        // ??????????????????????????????
        var instanceInputParameters = this.createInstanceParameters(params, taskInstance, workflow.getType().name(), nodeDef.getInputParameters());

        // ????????????
        this.parameterRepository.addAll(new ArrayList<>(params.values()));
        // ??????????????????????????????
        this.instanceParameterRepository.addAll(instanceInputParameters);
        // ??????????????????
        this.taskInstanceRepository.add(taskInstance);
    }

    public void terminate(String asyncTaskInstanceId) {
        var taskInstance = this.taskInstanceRepository.findByBusinessIdAndMaxSerialNo(asyncTaskInstanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        this.workerInternalApplication.terminateTask(taskInstance.getWorkerId(), taskInstance.getId());
    }

    @Transactional
    public void executeSucceeded(String taskInstanceId, String resultFile) {
        TaskInstance taskInstance = this.taskInstanceRepository.findById(taskInstanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        MDC.put("triggerId", taskInstance.getTriggerId());
        // start???end??????
        if (taskInstance.isCreationVolume()) {
            var workflowInstance = this.workflowInstanceRepository.findByTriggerId(taskInstance.getTriggerId())
                    .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
            workflowInstance.start();
            this.workflowInstanceRepository.commitEvents(workflowInstance);
        }
        if (taskInstance.isDeletionVolume()) {
            this.monitoringFileService.clearCallbackByLogId(taskInstance.getTriggerId());
        }
        if (taskInstance.isVolume()) {
            taskInstance.executeSucceeded();
            this.taskInstanceRepository.saveSucceeded(taskInstance);
            return;
        }
        var workflow = this.workflowRepository.findByRefAndVersion(taskInstance.getWorkflowRef(), taskInstance.getWorkflowVersion())
                .orElseThrow(() -> new DataNotFoundException("?????????????????????: " + taskInstance.getWorkflowRef()));
        // ????????????
        var nodeVersion = this.nodeDefApi.findByType(taskInstance.getDefKey());
        Map<InstanceParameter, Parameter<?>> outputParameters = new HashMap<>();
        if (nodeVersion.getOutputParameters() != null &&
                !nodeVersion.getOutputParameters().isEmpty()) {
            if (nodeVersion.getResultFile() != null) {
                outputParameters = this.handleOutputParameter(
                        resultFile, nodeVersion, workflow.getType().name(), taskInstance
                );
                if (outputParameters.isEmpty()) {
                    this.executeFailed(taskInstanceId);
                    return;
                }
            } else {
                log.warn("????????????????????????????????????ResultFile");
                this.executeFailed(taskInstanceId);
                return;
            }
        }
        taskInstance.executeSucceeded();
        outputParameters.putAll(this.createInnerOutputParameters(taskInstance, workflow.getType().name()));
        // ??????????????????????????????
        this.instanceParameterRepository.addAll(outputParameters.keySet());
        // ????????????
        this.parameterRepository.addAll(new ArrayList<>(outputParameters.values()));
        this.taskInstanceRepository.saveSucceeded(taskInstance);
    }

    @Transactional
    public void executeFailed(String taskInstanceId) {
        TaskInstance taskInstance = this.taskInstanceRepository.findById(taskInstanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        MDC.put("triggerId", taskInstance.getTriggerId());
        var workflow = this.workflowRepository.findByRefAndVersion(taskInstance.getWorkflowRef(), taskInstance.getWorkflowVersion())
                .orElseThrow(() -> new DataNotFoundException("?????????????????????: " + taskInstance.getWorkflowRef()));
        taskInstance.executeFailed();
        // ??????????????????
        if (taskInstance.isVolume()) {
            if (taskInstance.isCreationVolume()) {
                log.error("??????Volume??????");
                var workflowInstance = this.workflowInstanceRepository.findByTriggerId(taskInstance.getTriggerId())
                        .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
                workflowInstance.terminateInStart();
                this.workflowInstanceRepository.save(workflowInstance);
            }else {
                log.error("??????Volume??????");
                this.monitoringFileService.clearCallbackByLogId(taskInstance.getTriggerId());
            }
            this.taskInstanceRepository.updateStatus(taskInstance);
            return;
        }
        // ????????????
        var outputParameters =
                this.createInnerOutputParameters(taskInstance, workflow.getType().name());
        this.parameterRepository.addAll(new ArrayList<>(outputParameters.values()));
        this.instanceParameterRepository.addAll(outputParameters.keySet());
        this.taskInstanceRepository.updateStatus(taskInstance);
    }

    @Transactional
    public void running(String taskInstanceId) {
        TaskInstance taskInstance = this.taskInstanceRepository.findById(taskInstanceId)
                .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        taskInstance.running();
        this.taskInstanceRepository.updateStatus(taskInstance);
    }

    private Map<String, Object> parseJson(String resultFile) {
        if (resultFile == null || resultFile.isBlank()) {
            throw new IllegalArgumentException("????????????????????????");
        }
        var objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(resultFile, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Json??????", e);
            throw new IllegalArgumentException("??????????????????????????????");
        }
    }

    private Set<InstanceParameter> createInstanceParameters(
            Map<String, Parameter<?>> parameterMap,
            TaskInstance taskInstance,
            String workflowType,
            Set<NodeParameter> nodeParameters
    ) {
        var instanceParameters = parameterMap.entrySet().stream()
                .filter(entry ->
                        nodeParameters.stream()
                                .anyMatch(nodeParameter -> nodeParameter.getRef().equals(entry.getKey()))
                )
                .map(entry ->
                        InstanceParameter.Builder.anInstanceParameter()
                                .instanceId(taskInstance.getId())
                                .triggerId(taskInstance.getTriggerId())
                                .defKey(taskInstance.getDefKey())
                                .asyncTaskRef(taskInstance.getAsyncTaskRef())
                                .businessId(taskInstance.getBusinessId())
                                .ref(entry.getKey())
                                .serialNo(taskInstance.getSerialNo())
                                .parameterId(entry.getValue().getId())
                                .required(nodeParameters.stream()
                                        .filter(nodeParameter -> nodeParameter.getRef().equals(entry.getKey()))
                                        .findFirst()
                                        .map(NodeParameter::getRequired)
                                        .orElse(false)
                                )
                                .type(InstanceParameter.Type.INPUT)
                                .workflowType(workflowType)
                                .build()
                ).collect(Collectors.toSet());
        // ????????????????????????????????????DSL??????????????????
        nodeParameters.forEach(nodeParameter -> {
            // ????????????????????????????????????
            if (!parameterMap.containsKey(nodeParameter.getRef())) {
                var p = InstanceParameter.Builder.anInstanceParameter()
                        .instanceId(taskInstance.getId())
                        .triggerId(taskInstance.getTriggerId())
                        .defKey(taskInstance.getDefKey())
                        .asyncTaskRef(taskInstance.getAsyncTaskRef())
                        .businessId(taskInstance.getBusinessId())
                        .ref(nodeParameter.getRef())
                        .serialNo(taskInstance.getSerialNo())
                        .parameterId(nodeParameter.getParameterId())
                        .type(InstanceParameter.Type.INPUT)
                        .required(nodeParameter.getRequired())
                        .workflowType(workflowType)
                        .build();
                instanceParameters.add(p);
            }
        });
        return instanceParameters;
    }

    private Map<InstanceParameter, Parameter<?>> createInnerOutputParameters(TaskInstance taskInstance, String workflowType) {
        Map<InstanceParameter, Parameter<?>> innerOutputParameters = new HashMap<>();
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // inner.execution_status
        var executionStatusValue = Parameter.Type.STRING.newParameter(taskInstance.getStatus().name());
        var executionStatusKey = InstanceParameter.Builder.anInstanceParameter()
                .instanceId(taskInstance.getId())
                .triggerId(taskInstance.getTriggerId())
                .defKey(taskInstance.getDefKey())
                .asyncTaskRef(taskInstance.getAsyncTaskRef())
                .businessId(taskInstance.getBusinessId())
                .ref("inner.execution_status")
                .serialNo(taskInstance.getSerialNo())
                .parameterId(executionStatusValue.getId())
                .required(true)
                .type(InstanceParameter.Type.OUTPUT)
                .workflowType(workflowType)
                .build();
        innerOutputParameters.put(executionStatusKey, executionStatusValue);
        // inner.start_time
        var startTimeValue = Parameter.Type.STRING
                .newParameter(formatter.format(taskInstance.getStartTime()));
        var startTimeKey = InstanceParameter.Builder.anInstanceParameter()
                .instanceId(taskInstance.getId())
                .triggerId(taskInstance.getTriggerId())
                .defKey(taskInstance.getDefKey())
                .asyncTaskRef(taskInstance.getAsyncTaskRef())
                .businessId(taskInstance.getBusinessId())
                .ref("inner.start_time")
                .serialNo(taskInstance.getSerialNo())
                .parameterId(startTimeValue.getId())
                .required(true)
                .type(InstanceParameter.Type.OUTPUT)
                .workflowType(workflowType)
                .build();
        innerOutputParameters.put(startTimeKey, startTimeValue);
        // inner.end_time
        var endTimeValue = Parameter.Type.STRING
                .newParameter(formatter.format(taskInstance.getEndTime()));
        var endTimeKey = InstanceParameter.Builder.anInstanceParameter()
                .instanceId(taskInstance.getId())
                .triggerId(taskInstance.getTriggerId())
                .defKey(taskInstance.getDefKey())
                .asyncTaskRef(taskInstance.getAsyncTaskRef())
                .businessId(taskInstance.getBusinessId())
                .ref("inner.end_time")
                .serialNo(taskInstance.getSerialNo())
                .parameterId(endTimeValue.getId())
                .required(true)
                .type(InstanceParameter.Type.OUTPUT)
                .workflowType(workflowType)
                .build();
        innerOutputParameters.put(endTimeKey, endTimeValue);
        return innerOutputParameters;
    }

    private Map<InstanceParameter, Parameter<?>> handleOutputParameter(
            String resultFile,
            NodeDef nodeDef,
            String workflowType,
            TaskInstance taskInstance
    ) {
        try {
            // ??????Json???Map
            var parameterMap = this.parseJson(resultFile);
            // ???????????????????????????????????????????????????
            // ??????????????????????????????
            var outputParameters = nodeDef.matchedOutputParameters(parameterMap);
            return outputParameters.stream()
                    .map(nodeParameter -> {
                        var value = parameterMap.get(nodeParameter.getRef());
                        // ????????????
                        var parameter = Parameter.Type.getTypeByName(nodeParameter.getType()).newParameter(value);
                        // ??????????????????????????????
                        var instanceParameter = InstanceParameter.Builder.anInstanceParameter()
                                .instanceId(taskInstance.getId())
                                .serialNo(taskInstance.getSerialNo())
                                .asyncTaskRef(taskInstance.getAsyncTaskRef())
                                .defKey(taskInstance.getDefKey())
                                .businessId(taskInstance.getBusinessId())
                                .triggerId(taskInstance.getTriggerId())
                                .ref(nodeParameter.getRef())
                                .serialNo(taskInstance.getSerialNo())
                                .type(InstanceParameter.Type.OUTPUT)
                                .required(nodeParameter.getRequired())
                                .workflowType(workflowType)
                                .parameterId(parameter.getId())
                                .build();
                        return Map.entry(instanceParameter, parameter);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (RuntimeException e) {
            log.warn("e: ", e);
        }
        return Map.of();
    }

    // ??????????????????
    @Transactional
    public void terminateByTriggerId(String triggerId) {
        var taskInstances = this.taskInstanceRepository.findByTriggerId(triggerId);
        if (taskInstances.stream().noneMatch(taskInstance -> taskInstance.isDeletionVolume() || taskInstance.getStatus() == InstanceStatus.RUNNING)) {
            this.workflowInstanceRepository.findByTriggerId(triggerId)
                    .ifPresent(workflow -> this.taskInstanceRepository.add(TaskInstance.Builder.anInstance()
                            .serialNo(1)
                            .defKey("end")
                            .nodeInfo(NodeInfo.Builder.aNodeDef().name("end").build())
                            .asyncTaskRef("end")
                            .workflowRef(workflow.getWorkflowRef())
                            .workflowVersion(workflow.getWorkflowVersion())
                            .businessId(UUID.randomUUID().toString().replace("-", ""))
                            .triggerId(triggerId)
                            .build()));
        }
        taskInstances.stream().filter(taskInstance -> !taskInstance.isDeletionVolume() && taskInstance.getStatus() == InstanceStatus.WAITING)
                .forEach(taskInstance -> {
                    taskInstance.executeFailed();
                    this.taskInstanceRepository.terminate(taskInstance);
                });
    }
}
