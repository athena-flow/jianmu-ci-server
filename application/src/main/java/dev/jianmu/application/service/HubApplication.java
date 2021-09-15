package dev.jianmu.application.service;

import dev.jianmu.application.exception.DataNotFoundException;
import dev.jianmu.application.query.NodeDef;
import dev.jianmu.hub.intergration.aggregate.NodeDefinition;
import dev.jianmu.hub.intergration.aggregate.NodeDefinitionVersion;
import dev.jianmu.hub.intergration.aggregate.NodeLibrary;
import dev.jianmu.hub.intergration.aggregate.NodeParameter;
import dev.jianmu.hub.intergration.repository.NodeDefinitionRepository;
import dev.jianmu.hub.intergration.repository.NodeDefinitionVersionRepository;
import dev.jianmu.hub.intergration.repository.NodeLibraryRepository;
import dev.jianmu.infrastructure.client.RegistryClient;
import dev.jianmu.workflow.aggregate.parameter.Parameter;
import dev.jianmu.workflow.repository.ParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @class: HubApplication
 * @description: HubApplication
 * @author: Ethan Liu
 * @create: 2021-09-04 10:03
 **/
@Service
public class HubApplication {
    private final NodeLibraryRepository nodeLibraryRepository;
    private final NodeDefinitionRepository nodeDefinitionRepository;
    private final NodeDefinitionVersionRepository nodeDefinitionVersionRepository;
    private final ParameterRepository parameterRepository;
    private final RegistryClient registryClient;

    public HubApplication(
            NodeLibraryRepository nodeLibraryRepository,
            NodeDefinitionRepository nodeDefinitionRepository,
            NodeDefinitionVersionRepository nodeDefinitionVersionRepository,
            ParameterRepository parameterRepository,
            RegistryClient registryClient
    ) {
        this.nodeLibraryRepository = nodeLibraryRepository;
        this.nodeDefinitionRepository = nodeDefinitionRepository;
        this.nodeDefinitionVersionRepository = nodeDefinitionVersionRepository;
        this.parameterRepository = parameterRepository;
        this.registryClient = registryClient;
    }

    public List<NodeLibrary> findLibAll() {
        return this.nodeLibraryRepository.findAll();
    }

    public List<NodeDefinition> findNodeAll(int pageNum, int pageSize) {
        return this.nodeDefinitionRepository.findAll(pageNum, pageSize);
    }

    public List<NodeDefinitionVersion> findByRef(String ref) {
        return this.nodeDefinitionVersionRepository.findByRef(ref);
    }

    private String getRef(String type) {
        var ref = type.split(":")[0];
        var strings = ref.split("/");
        if (strings.length == 1) {
            return "_/" + ref;
        }
        return ref;
    }

    private String getVersion(String type) {
        return type.split(":")[1];
    }

    private NodeDefinition downloadNodeDef(String type) {
        var defDto = this.registryClient.findByRef(getRef(type))
                .orElseThrow(() -> new DataNotFoundException("未找到节点定义"));
        return NodeDefinition.Builder.aNodeDefinition()
                .id(defDto.getOwnerRef() + "/" + defDto.getRef())
                .icon(defDto.getIcon())
                .name(defDto.getName())
                .ownerName(defDto.getOwnerName())
                .ownerType(defDto.getOwnerType())
                .ownerRef(defDto.getOwnerRef())
                .creatorName(defDto.getCreatorName())
                .creatorRef(defDto.getCreatorRef())
                .type(defDto.getType())
                .description(defDto.getDescription())
                .ref(defDto.getRef())
                .sourceLink(defDto.getSourceLink())
                .documentLink(defDto.getDocumentLink())
                .build();
    }

    private NodeDefinitionVersion downloadNodeDefVersion(String ref, String version) {
        var dto = this.registryClient.findByRefAndVersion(ref, version)
                .orElseThrow(() -> new DataNotFoundException("未找到节点定义版本"));
        List<Parameter> parameters = new ArrayList<>();
        var inputParameters = dto.getInputParameters().stream().map(parameter -> {
            var p = Parameter.Type.valueOf(parameter.getType()).newParameter(parameter.getValue());
            parameters.add(p);
            return NodeParameter.Builder.aNodeParameter()
                    .name(parameter.getName())
                    .description(parameter.getDescription())
                    .ref(parameter.getRef())
                    .type(parameter.getType())
                    .parameterId(p.getId())
                    .value(parameter.getValue())
                    .build();
        }).collect(Collectors.toSet());

        var outputParameters = dto.getOutputParameters().stream().map(parameter -> {
            var p = Parameter.Type.valueOf(parameter.getType()).newParameter(parameter.getValue());
            parameters.add(p);
            return NodeParameter.Builder.aNodeParameter()
                    .name(parameter.getName())
                    .description(parameter.getDescription())
                    .ref(parameter.getRef())
                    .type(parameter.getType())
                    .parameterId(p.getId())
                    .value(parameter.getValue())
                    .build();
        }).collect(Collectors.toSet());

        this.parameterRepository.addAll(parameters);

        return NodeDefinitionVersion.Builder.aNodeDefinitionVersion()
                .id(dto.getRef() + ":" + dto.getVersion())
                .ownerRef(dto.getOwnerRef())
                .ref(dto.getRef())
                .creatorName(dto.getCreatorName())
                .creatorRef(dto.getCreatorRef())
                .version(dto.getVersion())
                .resultFile(dto.getResultFile())
                .inputParameters(inputParameters)
                .outputParameters(outputParameters)
                .spec(dto.getSpec())
                .build();
    }

    @Transactional
    public NodeDef findByType(String type) {
        var node = this.nodeDefinitionRepository.findById(getRef(type))
                .orElseGet(() -> this.downloadNodeDef(type));
        var version =
                this.nodeDefinitionVersionRepository.findByRefAndVersion(getRef(type), getVersion(type))
                        .orElseGet(() -> this.downloadNodeDefVersion(getRef(type), getVersion(type)));

        var nodeDef = NodeDef.builder()
                .name(node.getName())
                .description(node.getDescription())
                .type(type)
                .workerType(node.getType().name())
                .resultFile(version.getResultFile())
                .inputParameters(version.getInputParameters())
                .outputParameters(version.getOutputParameters())
                .spec(version.getSpec())
                .build();

        this.nodeDefinitionRepository.saveOrUpdate(node);
        this.nodeDefinitionVersionRepository.saveOrUpdate(version);
        return nodeDef;
    }

    public List<NodeDef> findByTypes(Set<String> types) {
        return types.stream().map(this::findByType)
                .collect(Collectors.toList());
    }
}
