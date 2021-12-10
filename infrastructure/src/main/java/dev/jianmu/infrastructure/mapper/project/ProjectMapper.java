package dev.jianmu.infrastructure.mapper.project;

import dev.jianmu.project.aggregate.Project;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * @class ProjectMapper
 * @description DSL流程定义关联Mapper
 * @author Ethan Liu
 * @create 2021-04-23 11:39
*/
public interface ProjectMapper {
    @Insert("insert into jianmu_project(id, dsl_source, dsl_type, event_bridge_id, trigger_type, git_repo_id, workflow_name, workflow_description, workflow_ref, workflow_version, steps, dsl_text, created_time, last_modified_by, last_modified_time) " +
            "values(#{id}, #{dslSource}, #{dslType}, #{eventBridgeId}, #{triggerType}, #{gitRepoId}, #{workflowName}, #{workflowDescription}, #{workflowRef}, #{workflowVersion}, #{steps}, #{dslText}, #{createdTime}, #{lastModifiedBy}, #{lastModifiedTime})")
    void add(Project project);

    @Delete("delete from jianmu_project where workflow_ref = #{workflowRef}")
    void deleteByWorkflowRef(String workflowRef);

    @Update("update jianmu_project set dsl_type = #{dslType}, event_bridge_id = #{eventBridgeId}, trigger_type = #{triggerType}, workflow_name = #{workflowName}, workflow_description = #{workflowDescription}, workflow_version = #{workflowVersion}, steps = #{steps}, dsl_text = #{dslText} , last_modified_by = #{lastModifiedBy}, last_modified_time = #{lastModifiedTime} " +
            "where workflow_ref = #{workflowRef}")
    void updateByWorkflowRef(Project project);

    @Select("select * from jianmu_project where id = #{id}")
    @Result(column = "workflow_name", property = "workflowName")
    @Result(column = "workflow_description", property = "workflowDescription")
    @Result(column = "dsl_source", property = "dslSource")
    @Result(column = "dsl_type", property = "dslType")
    @Result(column = "event_bridge_id", property = "eventBridgeId")
    @Result(column = "trigger_type", property = "triggerType")
    @Result(column = "git_repo_id", property = "gitRepoId")
    @Result(column = "workflow_ref", property = "workflowRef")
    @Result(column = "workflow_version", property = "workflowVersion")
    @Result(column = "dsl_text", property = "dslText")
    @Result(column = "created_time", property = "createdTime")
    @Result(column = "last_modified_by", property = "lastModifiedBy")
    @Result(column = "last_modified_time", property = "lastModifiedTime")
    Optional<Project> findById(String id);

    @Select("select * from jianmu_project where workflow_name = #{name}")
    @Result(column = "workflow_name", property = "workflowName")
    @Result(column = "workflow_description", property = "workflowDescription")
    @Result(column = "dsl_source", property = "dslSource")
    @Result(column = "dsl_type", property = "dslType")
    @Result(column = "event_bridge_id", property = "eventBridgeId")
    @Result(column = "trigger_type", property = "triggerType")
    @Result(column = "git_repo_id", property = "gitRepoId")
    @Result(column = "workflow_ref", property = "workflowRef")
    @Result(column = "workflow_version", property = "workflowVersion")
    @Result(column = "dsl_text", property = "dslText")
    @Result(column = "created_time", property = "createdTime")
    @Result(column = "last_modified_by", property = "lastModifiedBy")
    @Result(column = "last_modified_time", property = "lastModifiedTime")
    Optional<Project> findByName(String name);

    @Select("<script>" +
            "SELECT * FROM `jianmu_project` " +
            "<if test='name != null'> WHERE `workflow_name` like concat('%', #{workflowName}, '%')</if>" +
            " order by last_modified_time desc" +
            "</script>")
    @Result(column = "workflow_name", property = "workflowName")
    @Result(column = "workflow_description", property = "workflowDescription")
    @Result(column = "dsl_source", property = "dslSource")
    @Result(column = "dsl_type", property = "dslType")
    @Result(column = "event_bridge_id", property = "eventBridgeId")
    @Result(column = "trigger_type", property = "triggerType")
    @Result(column = "git_repo_id", property = "gitRepoId")
    @Result(column = "workflow_ref", property = "workflowRef")
    @Result(column = "workflow_version", property = "workflowVersion")
    @Result(column = "dsl_text", property = "dslText")
    @Result(column = "created_time", property = "createdTime")
    @Result(column = "last_modified_by", property = "lastModifiedBy")
    @Result(column = "last_modified_time", property = "lastModifiedTime")
    List<Project> findAllPage(String workflowName);

    @Select("select * from jianmu_project order by created_time desc")
    @Result(column = "workflow_name", property = "workflowName")
    @Result(column = "workflow_description", property = "workflowDescription")
    @Result(column = "dsl_source", property = "dslSource")
    @Result(column = "dsl_type", property = "dslType")
    @Result(column = "event_bridge_id", property = "eventBridgeId")
    @Result(column = "trigger_type", property = "triggerType")
    @Result(column = "git_repo_id", property = "gitRepoId")
    @Result(column = "workflow_ref", property = "workflowRef")
    @Result(column = "workflow_version", property = "workflowVersion")
    @Result(column = "dsl_text", property = "dslText")
    @Result(column = "created_time", property = "createdTime")
    @Result(column = "last_modified_by", property = "lastModifiedBy")
    @Result(column = "last_modified_time", property = "lastModifiedTime")
    List<Project> findAll();

    @Select("<script>" +
            "SELECT jp.* FROM `jianmu_project` `jp` INNER JOIN `project_link_group` `plp`  ON `plp`.`project_id` = `jp`.`id` " +
            "<where>" +
            "   <if test='projectGroupId != null'> AND `plp`.`project_group_id` = #{projectGroupId} </if>" +
            "   <if test='workflowName != null'> AND `jp`.`workflow_name` like concat('%', #{workflowName}, '%')</if>" +
            "</where>" +
            "ORDER BY `plp`.`sort` asc" +
            "</script>")
    @Result(column = "workflow_name", property = "workflowName")
    @Result(column = "workflow_description", property = "workflowDescription")
    @Result(column = "dsl_source", property = "dslSource")
    @Result(column = "dsl_type", property = "dslType")
    @Result(column = "event_bridge_id", property = "eventBridgeId")
    @Result(column = "trigger_type", property = "triggerType")
    @Result(column = "git_repo_id", property = "gitRepoId")
    @Result(column = "workflow_ref", property = "workflowRef")
    @Result(column = "workflow_version", property = "workflowVersion")
    @Result(column = "dsl_text", property = "dslText")
    @Result(column = "created_time", property = "createdTime")
    @Result(column = "last_modified_by", property = "lastModifiedBy")
    @Result(column = "last_modified_time", property = "lastModifiedTime")
    List<Project> findAllByGroupId(@Param("projectGroupId") String projectGroupId, @Param("workflowName") String workflowName);
}
