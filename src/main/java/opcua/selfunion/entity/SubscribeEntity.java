package opcua.selfunion.entity;

import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;

public class SubscribeEntity {
    private UaSubscription uaSubscription;
    // 1 代表已经订阅成功  ；；； 2 代表 这个订阅以被取消
    private Integer status;

    public SubscribeEntity(UaSubscription uaSubscription, Integer status) {
        this.uaSubscription = uaSubscription;
        this.status = status;
    }

    public UaSubscription getUaSubscription() {
        return uaSubscription;
    }

    public void setUaSubscription(UaSubscription uaSubscription) {
        this.uaSubscription = uaSubscription;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
