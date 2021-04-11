package dev.jianmu.parameter.aggregate;

/**
 * @class: Reference
 * @description: 参数引用，两个ID为联合主键，必须唯一
 * @author: Ethan Liu
 * @create: 2021-04-09 18:51
 **/
public class Reference {
    // 关联参数所在上下文ID
    private String contextId;
    // 被关联参数ID
    private String linkedParameterId;
    // 参数ID
    private String parameterId;

    public String getContextId() {
        return contextId;
    }

    public String getLinkedParameterId() {
        return linkedParameterId;
    }

    public String getParameterId() {
        return parameterId;
    }

    public static final class Builder {
        // 关联参数所在上下文ID
        private String contextId;
        // 被关联参数ID
        private String linkedParameterId;
        // 参数ID
        private String parameterId;

        private Builder() {
        }

        public static Builder aReference() {
            return new Builder();
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder linkedParameterId(String linkedParameterId) {
            this.linkedParameterId = linkedParameterId;
            return this;
        }

        public Builder parameterId(String parameterId) {
            this.parameterId = parameterId;
            return this;
        }

        public Reference build() {
            Reference reference = new Reference();
            reference.linkedParameterId = this.linkedParameterId;
            reference.contextId = this.contextId;
            reference.parameterId = this.parameterId;
            return reference;
        }
    }
}