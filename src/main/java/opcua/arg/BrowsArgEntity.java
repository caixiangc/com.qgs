package opcua.arg;

/**
 * @Desc: ""
 * @Author: caixiang
 * @DATE: 2020/7/30 11:02
 */
public class BrowsArgEntity {
    private String plcName;
    private Integer rootNameSpace;
    private String idenrifier;

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }

    public Integer getRootNameSpace() {
        return rootNameSpace;
    }

    public void setRootNameSpace(Integer rootNameSpace) {
        this.rootNameSpace = rootNameSpace;
    }

    public String getIdenrifier() {
        return idenrifier;
    }

    public void setIdenrifier(String idenrifier) {
        this.idenrifier = idenrifier;
    }
}
