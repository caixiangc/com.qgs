package opcua.selfunion.entity;

import java.util.List;

/**
 * @Desc: ""
 * @Author: caixiang
 * @DATE: 2020/7/23 14:51
 */
public class SubscribeEventArgEntity {
    List<Integer> listNameSpace;
    List<Object> listIdentifier;
    String plcName;

    public List<Integer> getListNameSpace() {
        return listNameSpace;
    }

    public void setListNameSpace(List<Integer> listNameSpace) {
        this.listNameSpace = listNameSpace;
    }

    public List<Object> getListIdentifier() {
        return listIdentifier;
    }

    public void setListIdentifier(List<Object> listIdentifier) {
        this.listIdentifier = listIdentifier;
    }

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }
}
