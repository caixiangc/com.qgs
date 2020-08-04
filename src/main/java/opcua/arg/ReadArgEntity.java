package opcua.arg;


/**
 * @Desc: ""
 * @Author: caixiang
 * @DATE: 2020/7/30 10:22
 */
public class ReadArgEntity {
    private Integer nameSpace;
    private String identifier;
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

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }
}
