package ${packagePrefix}.${pluginName}.dto;

public class EgroupDTO {
    private Integer groupId;
    private String groupName;
    private Integer parentGroupId;
    private Integer childrenGroupId;
    private Integer gatewayId;

    public Integer getGroupId() {
        return this.groupId;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public Integer getParentGroupId() {
        return this.parentGroupId;
    }

    public Integer getChildrenGroupId() {
        return this.childrenGroupId;
    }

    public Integer getGatewayId() {
        return this.gatewayId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setParentGroupId(Integer parentGroupId) {
        this.parentGroupId = parentGroupId;
    }

    public void setChildrenGroupId(Integer childrenGroupId) {
        this.childrenGroupId = childrenGroupId;
    }

    public void setGatewayId(Integer gatewayId) {
        this.gatewayId = gatewayId;
    }
}