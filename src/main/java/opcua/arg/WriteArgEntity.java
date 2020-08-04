package opcua.arg;

/**
 * @Desc: ""
 * @Author: caixiang
 * @DATE: 2020/7/30 10:43
 */
public class WriteArgEntity {
    private Integer nameSpace;
    private String identifier;
    private String newValue;
    private String type;
    private String plcName;

    public Integer getNameSpace() {
        return nameSpace;
    }

    public void setNameSpace(Integer nameSpace) {
        this.nameSpace = nameSpace;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }
}
