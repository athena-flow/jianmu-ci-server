package dev.jianmu.api.query;

import dev.jianmu.application.query.NodeDef;
import dev.jianmu.application.query.NodeDefApi;
import dev.jianmu.application.service.HubApplication;
import dev.jianmu.node.definition.aggregate.ShellNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @class NodeDefApiImpl
 * @description 节点定义查询API实现类
 * @author Ethan Liu
 * @create 2021-09-04 18:34
*/
@Component
public class NodeDefApiImpl implements NodeDefApi {
    private final HubApplication hubApplication;

    public NodeDefApiImpl(HubApplication hubApplication) {
        this.hubApplication = hubApplication;
    }

    @Override
    public List<NodeDef> findByTypes(Set<String> types) {
        return this.hubApplication.findByTypes(types);
    }

    @Override
    public List<NodeDef> getByTypes(Set<String> types) {
        return this.hubApplication.getByTypes(types);
    }

    @Override
    public NodeDef findByType(String type) {
        return this.hubApplication.findByType(type);
    }

    @Override
    public NodeDef getByType(String type) {
        return this.hubApplication.getByType(type);
    }

    @Override
    public void addShellNodes(List<ShellNode> shellNodes) {
        this.hubApplication.addShellNodes(shellNodes);
    }
}
