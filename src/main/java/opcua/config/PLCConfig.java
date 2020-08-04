package opcua.config;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

public class PLCConfig {
    private String plcName;
    private String endpointUrlConfig;
    private SecurityPolicy securityPolicyConfig;
    private String[] identityProviderConfig;
    private String ip;
    //None Or SignAndEncrypt
    private Integer messageMode;

    public PLCConfig(String plcName,String endpointUrlConfig, SecurityPolicy securityPolicyConfig, String[] identityProviderConfig,String ip,Integer messageMode) {
        this.plcName = plcName;
        this.endpointUrlConfig = endpointUrlConfig;
        this.securityPolicyConfig = securityPolicyConfig;
        this.identityProviderConfig = identityProviderConfig;
        this.ip = ip;
        this.messageMode = messageMode;
    }

    public Integer getMessageMode() {
        return messageMode;
    }

    public void setMessageMode(Integer messageMode) {
        this.messageMode = messageMode;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getEndpointUrlConfig() {
        return endpointUrlConfig;
    }

    public String getPlcName() {
        return plcName;
    }

    public void setPlcName(String plcName) {
        this.plcName = plcName;
    }

    public void setEndpointUrlConfig(String endpointUrlConfig) {
        this.endpointUrlConfig = endpointUrlConfig;
    }

    public SecurityPolicy getSecurityPolicyConfig() {
        return securityPolicyConfig;
    }

    public void setSecurityPolicyConfig(SecurityPolicy securityPolicyConfig) {
        this.securityPolicyConfig = securityPolicyConfig;
    }

    public String[] getIdentityProviderConfig() {
        return identityProviderConfig;
    }

    public void setIdentityProviderConfig(String[] identityProviderConfig) {
        this.identityProviderConfig = identityProviderConfig;
    }
}
