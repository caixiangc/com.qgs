package opcua.arg;

/**
 * @Desc: ""
 * @Author: caixiang
 * @DATE: 2020/7/30 11:05
 */
public class AddPLCArgEntity {
    private String plcName;
    private String urlConfig;
    private String policyConfig;
    private String userConfigs;
    private String ip;
    private Integer messageMode;

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }

    public String getUrlConfig() {
        return urlConfig;
    }

    public void setUrlConfig(String urlConfig) {
        this.urlConfig = urlConfig;
    }

    public String getPolicyConfig() {
        return policyConfig;
    }

    public void setPolicyConfig(String policyConfig) {
        this.policyConfig = policyConfig;
    }

    public String getUserConfigs() {
        return userConfigs;
    }

    public void setUserConfigs(String userConfigs) {
        this.userConfigs = userConfigs;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getMessageMode() {
        return messageMode;
    }

    public void setMessageMode(Integer messageMode) {
        this.messageMode = messageMode;
    }
}
