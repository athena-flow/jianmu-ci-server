package dev.jianmu.application.service;

import com.github.pagehelper.PageInfo;
import dev.jianmu.application.dsl.DslParser;
import dev.jianmu.application.event.CronEvent;
import dev.jianmu.application.event.ManualEvent;
import dev.jianmu.application.event.WebhookEvent;
import dev.jianmu.application.exception.DataNotFoundException;
import dev.jianmu.application.query.NodeDefApi;
import dev.jianmu.infrastructure.GlobalProperties;
import dev.jianmu.infrastructure.jgit.JgitService;
import dev.jianmu.infrastructure.mybatis.project.ProjectRepositoryImpl;
import dev.jianmu.project.aggregate.GitRepo;
import dev.jianmu.project.aggregate.Project;
import dev.jianmu.project.aggregate.ProjectGroup;
import dev.jianmu.project.aggregate.ProjectLinkGroup;
import dev.jianmu.project.event.CreatedEvent;
import dev.jianmu.project.event.DeletedEvent;
import dev.jianmu.project.event.MovedEvent;
import dev.jianmu.project.event.TriggerEvent;
import dev.jianmu.project.query.ProjectVo;
import dev.jianmu.project.repository.GitRepoRepository;
import dev.jianmu.project.repository.ProjectGroupRepository;
import dev.jianmu.project.repository.ProjectLinkGroupRepository;
import dev.jianmu.task.repository.TaskInstanceRepository;
import dev.jianmu.trigger.aggregate.Trigger;
import dev.jianmu.trigger.aggregate.Webhook;
import dev.jianmu.trigger.repository.TriggerEventRepository;
import dev.jianmu.workflow.aggregate.definition.Workflow;
import dev.jianmu.workflow.aggregate.process.ProcessStatus;
import dev.jianmu.workflow.aggregate.process.WorkflowInstance;
import dev.jianmu.workflow.repository.AsyncTaskInstanceRepository;
import dev.jianmu.workflow.repository.WorkflowInstanceRepository;
import dev.jianmu.workflow.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.jianmu.application.service.ProjectGroupApplication.DEFAULT_PROJECT_GROUP_NAME;

/**
 * @author Ethan Liu
 * @class ProjectApplication
 * @description ProjectApplication
 * @create 2021-05-15 22:13
 */
@Service
public class ProjectApplication {
    private static final Logger logger = LoggerFactory.getLogger(ProjectApplication.class);

    private final ProjectRepositoryImpl projectRepository;
    private final GitRepoRepository gitRepoRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final AsyncTaskInstanceRepository asyncTaskInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final NodeDefApi nodeDefApi;
    private final ApplicationEventPublisher publisher;
    private final JgitService jgitService;
    private final ProjectLinkGroupRepository projectLinkGroupRepository;
    private final ProjectGroupRepository projectGroupRepository;
    private final GlobalProperties globalProperties;
    private final TriggerEventRepository triggerEventRepository;
    public ProjectApplication(
            ProjectRepositoryImpl projectRepository,
            GitRepoRepository gitRepoRepository,
            WorkflowRepository workflowRepository,
            WorkflowInstanceRepository workflowInstanceRepository,
            AsyncTaskInstanceRepository asyncTaskInstanceRepository,
            TaskInstanceRepository taskInstanceRepository,
            NodeDefApi nodeDefApi,
            ApplicationEventPublisher publisher,
            JgitService jgitService,
            ProjectLinkGroupRepository projectLinkGroupRepository,
            ProjectGroupRepository projectGroupRepository,
            GlobalProperties globalProperties,
            TriggerEventRepository triggerEventRepository
    ) {
        this.projectRepository = projectRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.workflowRepository = workflowRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.asyncTaskInstanceRepository = asyncTaskInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.nodeDefApi = nodeDefApi;
        this.publisher = publisher;
        this.jgitService = jgitService;
        this.projectLinkGroupRepository = projectLinkGroupRepository;
        this.projectGroupRepository = projectGroupRepository;
        this.globalProperties = globalProperties;
        this.triggerEventRepository = triggerEventRepository;
    }

    public void switchEnabled(String projectId, boolean enabled) {
        var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> new DataNotFoundException("??????????????????"));
        project.switchEnabled(enabled);
        this.projectRepository.updateByWorkflowRef(project);
    }

    public void trigger(String projectId, String triggerId, String triggerType) {
        MDC.put("triggerId", triggerId);
        var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> new DataNotFoundException("??????????????????"));
        var triggerEvent = TriggerEvent.Builder.aTriggerEvent()
                .projectId(project.getId())
                .triggerId(triggerId)
                .triggerType(triggerType)
                .workflowRef(project.getWorkflowRef())
                .workflowVersion(project.getWorkflowVersion())
                .build();
        this.publisher.publishEvent(triggerEvent);
    }

    public void triggerByManual(String projectId) {
        var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> new DataNotFoundException("??????????????????"));
        if (!project.isEnabled()) {
            throw new RuntimeException("?????????????????????????????????????????????");
        }

        var evt = dev.jianmu.trigger.event.TriggerEvent.Builder
                .aTriggerEvent()
                .projectId(project.getId())
                .triggerId("")
                .triggerType(Trigger.Type.MANUAL.name())
                .build();
        this.triggerEventRepository.save(evt);

        MDC.put("triggerId", evt.getId());
        var triggerEvent = TriggerEvent.Builder.aTriggerEvent()
                .projectId(project.getId())
                .triggerId(evt.getId())
                .triggerType(Trigger.Type.MANUAL.name())
                .workflowRef(project.getWorkflowRef())
                .workflowVersion(project.getWorkflowVersion())
                .build();
        this.publisher.publishEvent(triggerEvent);
    }

    private Workflow createWorkflow(DslParser parser, String dslText, String ref) {
        // ??????Shell node??????
        var shellNodes = parser.getShellNodes();
        this.nodeDefApi.addShellNodes(shellNodes);

        // ???????????????????????????
        var types = parser.getAsyncTaskTypes();
        var nodeDefs = this.nodeDefApi.getByTypes(types);

        // ?????????????????????DSL??????????????????Workflow
        var nodes = parser.createNodes(nodeDefs);
        return Workflow.Builder.aWorkflow()
                .ref(ref)
                .type(parser.getType())
                .name(parser.getName())
                .description(parser.getDescription())
                .nodes(nodes)
                .globalParameters(parser.getGlobalParameters())
                .dslText(dslText)
                .build();
    }

    @Transactional
    public void importProject(GitRepo gitRepo, String projectGroupId) {
        var dslText = this.jgitService.readDsl(gitRepo.getId(), gitRepo.getDslPath());
        // ??????DSL,????????????
        var parser = DslParser.parse(dslText);
        // ????????????Ref
        var ref = UUID.randomUUID().toString().replace("-", "");
        var workflow = this.createWorkflow(parser, dslText, ref);
        var project = Project.Builder.aReference()
                .workflowName(parser.getName())
                .workflowDescription(parser.getDescription())
                .workflowRef(workflow.getRef())
                .workflowVersion(workflow.getVersion())
                .dslText(dslText)
                .steps(parser.getSteps())
                .enabled(parser.isEnabled())
                .mutable(parser.isMutable())
                .concurrent(parser.isConcurrent())
                .gitRepoId(gitRepo.getId())
                .dslSource(Project.DslSource.GIT)
                .triggerType(parser.getTriggerType())
                .dslType(parser.getType().equals(Workflow.Type.WORKFLOW) ? Project.DslType.WORKFLOW : Project.DslType.PIPELINE)
                .lastModifiedBy("admin")
                .build();
        // ?????????????????????
        String groupId;
        if (projectGroupId != null) {
            this.projectGroupRepository.findById(projectGroupId).orElseThrow(() -> new DataNotFoundException("?????????????????????"));
            groupId = projectGroupId;
        } else {
            groupId = this.projectGroupRepository.findByName(DEFAULT_PROJECT_GROUP_NAME).map(ProjectGroup::getId)
                    .orElseThrow(() -> new DataNotFoundException("????????????????????????"));
        }
        var sort = this.projectLinkGroupRepository.findByProjectGroupIdAndSortMax(groupId)
                .map(ProjectLinkGroup::getSort)
                .orElse(-1);
        var projectLinkGroup = ProjectLinkGroup.Builder.aReference()
                .projectGroupId(groupId)
                .projectId(project.getId())
                .sort(++sort)
                .build();
        this.pubTriggerEvent(parser, project);
        this.projectRepository.add(project);
        this.projectLinkGroupRepository.add(projectLinkGroup);
        this.projectGroupRepository.addProjectCountById(groupId, 1);
        this.gitRepoRepository.add(gitRepo);
        this.jgitService.cleanUp(gitRepo.getId());
        this.workflowRepository.add(workflow);
        this.publisher.publishEvent(new CreatedEvent(project.getId()));
    }

    @Transactional
    public void syncProject(String projectId) {
        logger.info("????????????Git????????????DSL");
        var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> new DataNotFoundException("??????????????????"));
        var concurrent = project.isConcurrent();
        var gitRepo = this.gitRepoRepository.findById(project.getGitRepoId())
                .orElseThrow(() -> new DataNotFoundException("?????????Git????????????"));
        var dslText = this.jgitService.readDsl(gitRepo.getId(), gitRepo.getDslPath());
        // ??????DSL,????????????
        var parser = DslParser.parse(dslText);
        var workflow = this.createWorkflow(parser, dslText, project.getWorkflowRef());
        project.setDslText(dslText);
        project.setDslType(parser.getType().equals(Workflow.Type.WORKFLOW) ? Project.DslType.WORKFLOW : Project.DslType.PIPELINE);
        project.setTriggerType(Project.TriggerType.MANUAL);
        project.setLastModifiedBy("admin");
        project.setSteps(parser.getSteps());
        project.setEnabled(parser.isEnabled());
        project.setMutable(parser.isMutable());
        project.setConcurrent(parser.isConcurrent());
        project.setWorkflowName(parser.getName());
        project.setWorkflowDescription(parser.getDescription());
        project.setLastModifiedTime();
        project.setWorkflowVersion(workflow.getVersion());
        project.setTriggerType(parser.getTriggerType());

        this.pubTriggerEvent(parser, project);
        this.projectRepository.updateByWorkflowRef(project);
        this.workflowRepository.add(workflow);
        this.jgitService.cleanUp(gitRepo.getId());
        if (!concurrent && project.isConcurrent()) {
            this.concurrentWorkflowInstance(workflow.getRef());
        }
    }

    // ??????????????????
    private void concurrentWorkflowInstance(String workflowRef) {
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

    @Transactional
    public Project createProject(String dslText, String projectGroupId) {
        // ??????DSL,????????????
        var parser = DslParser.parse(dslText);
        // ????????????Ref
        var ref = UUID.randomUUID().toString().replace("-", "");
        var workflow = this.createWorkflow(parser, dslText, ref);
        // ????????????
        var project = Project.Builder.aReference()
                .workflowName(workflow.getName())
                .workflowDescription(workflow.getDescription())
                .workflowRef(workflow.getRef())
                .workflowVersion(workflow.getVersion())
                .dslText(dslText)
                .steps(parser.getSteps())
                .enabled(parser.isEnabled())
                .mutable(parser.isMutable())
                .concurrent(parser.isConcurrent())
                .lastModifiedBy("admin")
                .gitRepoId("")
                .dslSource(Project.DslSource.LOCAL)
                .triggerType(parser.getTriggerType())
                .dslType(parser.getType().equals(Workflow.Type.WORKFLOW) ? Project.DslType.WORKFLOW : Project.DslType.PIPELINE)
                .build();
        // ????????????
        this.projectGroupRepository.findById(projectGroupId).orElseThrow(() -> new DataNotFoundException("?????????????????????"));
        var sort = this.projectLinkGroupRepository.findByProjectGroupIdAndSortMax(projectGroupId)
                .map(ProjectLinkGroup::getSort)
                .orElse(-1);
        var projectLinkGroup = ProjectLinkGroup.Builder.aReference()
                .projectGroupId(projectGroupId)
                .projectId(project.getId())
                .sort(++sort)
                .build();

        this.pubTriggerEvent(parser, project);
        this.projectRepository.add(project);
        this.projectLinkGroupRepository.add(projectLinkGroup);
        this.projectGroupRepository.addProjectCountById(projectGroupId, 1);
        this.workflowRepository.add(workflow);
        this.publisher.publishEvent(new CreatedEvent(project.getId()));
        return project;
    }

    @Transactional
    public void updateProject(String dslId, String dslText, String projectGroupId) {
        Project project = this.projectRepository.findById(dslId)
                .orElseThrow(() -> new DataNotFoundException("????????????DSL"));
        var concurrent = project.isConcurrent();
        if (project.getDslSource() == Project.DslSource.GIT) {
            throw new IllegalArgumentException("??????????????????Git???????????????");
        }
        // ????????????????????????
        this.publisher.publishEvent(new MovedEvent(project.getId(), projectGroupId));
        // ??????DSL,????????????
        var parser = DslParser.parse(dslText);
        var workflow = this.createWorkflow(parser, dslText, project.getWorkflowRef());
        if (project.getDslText().equals(dslText)) {
            return;
        }
        project.setDslText(dslText);
        project.setDslType(parser.getType().equals(Workflow.Type.WORKFLOW) ? Project.DslType.WORKFLOW : Project.DslType.PIPELINE);
        project.setTriggerType(parser.getTriggerType());
        project.setLastModifiedBy("admin");
        project.setSteps(parser.getSteps());
        project.setEnabled(parser.isEnabled());
        project.setMutable(parser.isMutable());
        project.setConcurrent(parser.isConcurrent());
        project.setWorkflowName(parser.getName());
        project.setWorkflowDescription(parser.getDescription());
        project.setLastModifiedTime();
        project.setWorkflowVersion(workflow.getVersion());

        this.pubTriggerEvent(parser, project);
        this.projectRepository.updateByWorkflowRef(project);
        this.workflowRepository.add(workflow);
        if (!concurrent && project.isConcurrent()) {
            this.concurrentWorkflowInstance(workflow.getRef());
        }
    }

    @Transactional
    public void deleteById(String id) {
        Project project = this.projectRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("??????????????????"));
        var running = this.workflowInstanceRepository
                .findByRefAndStatuses(project.getWorkflowRef(), List.of(ProcessStatus.INIT, ProcessStatus.RUNNING, ProcessStatus.SUSPENDED))
                .size();
        if (running > 0) {
            throw new RuntimeException("????????????????????????????????????");
        }
        var projectLinkGroup = this.projectLinkGroupRepository.findByProjectId(id)
                .orElseThrow(() -> new DataNotFoundException("?????????????????????"));
        this.projectLinkGroupRepository.deleteById(projectLinkGroup.getId());
        this.projectGroupRepository.subProjectCountById(projectLinkGroup.getProjectGroupId(), 1);
        this.projectRepository.deleteByWorkflowRef(project.getWorkflowRef());
        this.workflowRepository.deleteByRef(project.getWorkflowRef());
        this.workflowInstanceRepository.deleteByWorkflowRef(project.getWorkflowRef());
        this.asyncTaskInstanceRepository.deleteByWorkflowRef(project.getWorkflowRef());
        this.taskInstanceRepository.deleteByWorkflowRef(project.getWorkflowRef());
        this.gitRepoRepository.deleteById(project.getGitRepoId());
        this.publisher.publishEvent(new DeletedEvent(project.getId()));
    }

    @Transactional
    public void autoClean() {
        // ????????????????????????
        if (!this.globalProperties.getGlobal().getRecord().getAutoClean()) {
            return;
        }
        logger.info("?????????????????????????????????????????????????????????{}??????????????????", this.globalProperties.getGlobal().getRecord().getMax());
        this.projectRepository.findAll().forEach(project -> {
            this.workflowInstanceRepository.findByRefOffset(project.getWorkflowRef(), this.globalProperties.getGlobal().getRecord().getMax())
                    .stream()
                    .filter(workflowInstance -> workflowInstance.getStatus() == ProcessStatus.FINISHED || workflowInstance.getStatus() == ProcessStatus.TERMINATED)
                    .forEach(workflowInstance -> {
                this.workflowInstanceRepository.deleteById(workflowInstance.getId());
                this.asyncTaskInstanceRepository.deleteByWorkflowInstanceId(workflowInstance.getId());
                this.taskInstanceRepository.deleteByTriggerId(workflowInstance.getTriggerId());
            });
        });

    }

    private void pubTriggerEvent(DslParser parser, Project project) {
        // ??????Cron?????????
        if (project.getTriggerType() == Project.TriggerType.CRON) {
            var cronEvent = CronEvent.builder()
                    .projectId(project.getId())
                    .schedule(parser.getCron())
                    .build();
            this.publisher.publishEvent(cronEvent);
        }
        // ??????Webhook?????????
        if (project.getTriggerType() == Project.TriggerType.WEBHOOK) {
            var webhook = Webhook.Builder.aWebhook()
                    .only(parser.getWebhook().getOnly())
                    .auth(parser.getWebhook().getAuth())
                    .param(parser.getWebhook().getParam())
                    .build();
            var webhookEvent = WebhookEvent.builder()
                    .projectId(project.getId())
                    .webhook(webhook)
                    .build();
            this.publisher.publishEvent(webhookEvent);
        }
        // ???????????????????????????
        if (project.getTriggerType() == Project.TriggerType.MANUAL) {
            var manualEvent = ManualEvent.builder()
                    .projectId(project.getId())
                    .build();
            this.publisher.publishEvent(manualEvent);
        }
    }

    public List<Project> findAll() {
        return this.projectRepository.findAll();
    }

    public Optional<Project> findById(String dslId) {
        return this.projectRepository.findById(dslId);
    }

    public Workflow findByRefAndVersion(String ref, String version) {
        return this.workflowRepository.findByRefAndVersion(ref, version)
                .orElseThrow(() -> new DataNotFoundException("????????????Workflow"));
    }

    public GitRepo findGitRepoById(String gitRepoId) {
        return this.gitRepoRepository.findById(gitRepoId).orElseThrow(() -> new DataNotFoundException("????????????Git???"));
    }

    public PageInfo<ProjectVo> findPageByGroupId(Integer pageNum, Integer pageSize, String projectGroupId, String workflowName, String sortType) {
        return this.projectRepository.findPageByGroupId(pageNum, pageSize, projectGroupId, workflowName, sortType);
    }
}
