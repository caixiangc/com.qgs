package opcua.selfunion;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import opcua.config.LocalMulPLCConfig;
import opcua.config.PLCConfig;
import opcua.constant.CommonFunction;
import opcua.constant.PLCConstant;
import opcua.selfunion.Enum.PLCVar;
import opcua.selfunion.entity.SubscribeEntity;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.nodes.Node;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.google.common.collect.Lists.newArrayList;
import static opcua.config.LocalMulPLCConfig.readJsonFile;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

/**
 * @Author: 蔡翔
 * @Date: 2019/10/12 15:15
 * @Version 1.0
 *
 *      注意：
 *      1.订阅模式一般是用来监听某些 传感器 报警 变量 需要全天候 24小时不中断 监听 。。并且 如果有断网 或者 OPC Server异常 那么是订阅 是不会重连的，但是 读变量取变量可以重新连接（如果opcuaclient已经connect()成功了）
 *      2.如果你需要监听某些变量 当它为特定值后 你就退出监听了 那么就while(true){ getValue ...  break} 这种方式
 *      3.订阅某些传感器 变量发生改变 ，然后用websocket 传给前端。
 *      4.在做客户端的时候 ，，订阅也可以实时的吧某些变量 通过websocket传给前端。
 *      5.在修改变量值的时候要注意 变量的类型 然后用Unsigned静态类进行数据类型转换,,Unsigned.ubyte(int1) 把int1 转成byte
 *      6.补充第5条，读数据不需要传入数据类型，直接getValue().getValue() 就行了。
 *      7.所有变量取到以后都要包装一下 CommonFunction.var(b) 才能传给前台。
 *      断线重连：
 *      1. UaException: status=Bad_ConnectionRejected  （OPC-SERVER 直接关闭了..或者 OPC-SERVER直接宕机了）
 *      2. UaException: status=Bad_Timeout  是网络断开了 并且 网络断开的时间超过 5s
 *      （上诉两种情况  只要 恢复网络 或者重新开启 OPC-SERVER 就能重新 恢复 连接）
 *      3.断线重连后 订阅是不会恢复连接的 要重新发起订阅，如果 subscribe()、subscribes()
 *
 *      //实例代码
 *      Boolean aBoolean = null;
 *         try {
 *             aBoolean = uaService.setValue(PLCVar.INT32_3, a,"plc1");
 *         }catch (Exception e){
 *             // 可以用errStatus 去判断是那种异常，并传给前端。。。extractError是提取报警信息的。
 *             String errStatus = uaService.extractError(e.getMessage());
 *             System.err.println("err msg: "+errStatus);
 *         }
 *
 *
 *       动态配置plc
 *          新增plc
 *              dynamicAddPlc(...)
 *          移除plc
 *              dynamicRemovePlc(...)
 *
 */

@Component
public class UAService {
    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }


    private final Logger logger = LoggerFactory.getLogger(getClass());
    private boolean flag = true;
    private final AtomicLong clientHandleGlobal = new AtomicLong(1L);

    private final CompletableFuture<OpcUaClient> future = new CompletableFuture<>();


    /**
     * 含义：每次生产一个订阅都会把 订阅放到allSubscription ，为的是后续可以 找到并且暂停订阅
     *
     * key的格式  ：plcName:ns,ide  plcName:ns,ide;ns,ide;ns,ide
     * 解释： plcName是指定哪个plc ;;
     *       ns,ide 是事件节点的信息（可以有多个如果订阅的是多个的话）;;
     *       status是状态这个节点的状态 ;;
     * */
    private HashMap<String, SubscribeEntity> allSubscription = new HashMap<>();

    public UAService(){
        run();
    }

    Predicate<EndpointDescription> endpointFilter(MessageSecurityMode m) {
        //只要是 就全部放进来 （不过滤）
        //        return e -> e.getSecurityMode().equals(MessageSecurityMode.SignAndEncrypt);
        return e -> e.getSecurityMode().equals(m);
    }

    //OPC UA 地址配置=================================结束=================================================

    //private List<OpcUaClient> opcUaClient = new ArrayList<>();
    private HashMap<String,OpcUaClient> opcUaClients = new HashMap<>();

    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static void createDirIfNotExit(String url){
        File file =new File(url);
        //如果文件夹不存在则创建
        if  (!file.exists()  && !file.isDirectory())
        {
            System.out.println("//不存在");
            boolean mkdir = file.mkdir();
        }
    }


    private OpcUaClient createClient(PLCConfig plcConfig) throws Exception {
        //会获取系统的临时目录 可能是linux 或者 是windows
        //Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        String securityUrl = "";
        if(isWindows()){
            System.err.println("isWindows");
            securityUrl = PLCConstant.securityURLForWindows;
            createDirIfNotExit(securityUrl);

        }
        if(isLinux()){
            System.err.println("isLinux");
            securityUrl = PLCConstant.securityURLForLinux;
            createDirIfNotExit(securityUrl);
        }

        Path securityTempDir = Paths.get(securityUrl);

        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }

        LoggerFactory.getLogger(getClass())
                .info("security temp dir: {}", securityTempDir.toAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir,plcConfig.getIp());

        SecurityPolicy securityPolicy =plcConfig.getSecurityPolicyConfig();

        List<EndpointDescription> endpoints;

        try {
            endpoints = DiscoveryClient.getEndpoints(plcConfig.getEndpointUrlConfig()).get();
        } catch (Throwable ex) {
            // try the explicit discovery endpoint as well
            String discoveryUrl = plcConfig.getEndpointUrlConfig();

            if (!discoveryUrl.endsWith("/")) {
                discoveryUrl += "/";
            }
            discoveryUrl += "discovery";

            logger.info("Trying explicit discovery URL: {}", discoveryUrl);
            endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get();
        }

        MessageSecurityMode filterMessageMode = MessageSecurityMode.from(plcConfig.getMessageMode());

        EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
                .filter(endpointFilter(filterMessageMode))
                .findFirst()
                .orElseThrow(() -> new Exception("no desired endpoints returned"));

        logger.info("Using endpoint: {} [{}/{}]",
                endpoint.getEndpointUrl(), securityPolicy, endpoint.getSecurityMode());

        String uri = "urn:"+plcConfig.getIp()+":SimulationServer:clientBaseMilo";
        //System.err.println("username:"+plcConfig.getIdentityProviderConfig()[0]+",password:"+plcConfig.getIdentityProviderConfig()[1]);
        OpcUaClientConfig config = null;
        if(plcConfig.getIdentityProviderConfig()!=null){
            config = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english("opc-ua client"))
                    //urn:192.168.0.163:SimulationServer:clientMilo
                    .setApplicationUri(uri)
                    .setCertificate(loader.getClientCertificate())
                    .setKeyPair(loader.getClientKeyPair())
                    .setEndpoint(endpoint)
                    .setIdentityProvider(new UsernameProvider(plcConfig.getIdentityProviderConfig()[0],plcConfig.getIdentityProviderConfig()[1]))
                    .setRequestTimeout(uint(5000))
                    .build();
        }else {
            config = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english("opc-ua client"))
                    //urn:192.168.0.163:SimulationServer:clientMilo
                    .setApplicationUri(uri)
                    .setCertificate(loader.getClientCertificate())
                    .setKeyPair(loader.getClientKeyPair())
                    .setEndpoint(endpoint)
                    .setRequestTimeout(uint(5000))
                    .build();
        }

        return OpcUaClient.create(config);
    }

    public String extractError(String as){
        String[] errMsgs = as.split(",");
            /*for(String i:errMsgs){
                System.err.println(i);
            }*/
        if(errMsgs.length <=1){
            return as;
        }
        String[] s = errMsgs[errMsgs.length-2].split(":");
        String errStatus = s[s.length-1];
        return errStatus.split("=")[1];
    }

    /**
     * 含义： 读取一个PLC变量(数组)
     * 注意：
     *          1.拿到变量以后要调用  CommonFunction.var() 方法包装一下才返回给前端
     *          2.如果内部用的话 要 预先 知道数据类型 然后数据类型转换的。
     * 参数：      PLCVar的枚举类，只要把这个枚举类（一个plc变量就是一个枚举类），只要把这个枚举类传进来就行了
     * 返回值：
     *      异常：    null         ==》 代表参数错误/ 或无此变量值
     *      正常：    DataValue    ==》 返回结果需要  dataValue.getValue().getValue()
     *      null     指定的plc不存在
     * 格式：PLCVar 内部是 （ns,identify）
     * */
    public DataValue getValue(PLCVar var, String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);


        if(var.getIdentifier() == null || "".equals(var.getIdentifier()) || var.getNameSpace()==null){
            return null;
        }

        NodeId nodeId = new NodeId(var.getNameSpace(),var.getIdentifier());
        VariableNode node2 = client.getAddressSpace().createVariableNode(nodeId);
        DataValue value2 = null;
        try {
            value2 = node2.readValue().get();
        }catch (Exception e){
            throw new Exception("OPCUA 读变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return value2;
    }
    public DataValue getValue(Integer nameSpace,String identifier,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);
        if(identifier == null || "".equals(identifier) || nameSpace==null || client == null){
            return null;
        }

        NodeId nodeId = new NodeId(nameSpace,identifier);
        VariableNode node2 = client.getAddressSpace().createVariableNode(nodeId);
        DataValue value2 = null;
        try {
            value2 = node2.readValue().get();
        }catch (Exception e){
            throw new Exception("OPCUA 读变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return value2;
    }

    /**
     * 含义： 读取多个个PLC变量
     * 参数： List<PLCVar> 的枚举类，只要把这个枚举类（一个plc变量就是一个枚举类），只要把这个枚举类传进来就行了
     * 返回值：
     *      异常：    null         ==》 代表参数错误/ 或无此变量值
     *      正常：    DataValue    ==》 返回结果需要  dataValue.getValue().getValue()
     *      null     代表选中的plc不存在
     * 格式：List<PLCVar> 内部是 （ns,identify）
     * */
    public List<DataValue> getValues(List<PLCVar> vars,String plcName) throws Exception {
        OpcUaClient client = opcUaClients.get(plcName);

        List<PLCVar> plcVars = vars;
        List<NodeId> nodeIds = new ArrayList<>();
        for(PLCVar var : plcVars){
            if(var.getNameSpace()==null || var.getIdentifier()==null || var.getIdentifier()==" "){
                return null;
            }
            nodeIds.add(new NodeId(var.getNameSpace(),var.getIdentifier()));
        }
        try {
            List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds).get();
            //future 结束掉，然后 把 opcclient 资源回收。
            //future.complete(opcUaClient);
            return values;
        }catch (Exception e){
            throw new Exception("OPCUA 读多个变量出现异常,,具体异常是: "+e);
        }
    }


    /**
     * 含义： 读取 NodeId ==> i=2255  这种类型的Node （如 Server节点 ... 这些）
     * 参数：
     *      value    就是NodeId 的 i （就是上面的2255）
     *      plcName  就是你要读取的哪个plc
     * 返回值：
     *      异常：    null         ==》 代表参数错误/ 或无此变量值
     *      正常：    DataValue    ==》 返回结果需要  dataValue.getValue().getValue()
     *      null     代表选中的plc 不存在
     * 格式：getInitialNode(2255,"plc3");
     * */
    public DataValue getInitialNode(int value,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);

        NodeId nodeId = CommonFunction.initialNodeId(value);
        VariableNode node2 = client.getAddressSpace().createVariableNode(nodeId);
        DataValue value2 = null;
        try {
            value2 = node2.readValue().get();
        }catch (Exception e){
            throw new Exception("OPCUA 读变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return value2;
    }

    /**
     * 含义： 修改 一个PLC变量 的值 (包括数组)
     * 注意：如果要修改数组变量的值那么需要：
     *      1. 传入参数 newValue = "6.1,6.1,6.1,6.1,6.1#QDouble" ；type = QUByte（是个String 类型）后端会传给你
     *      2. newValue前端传入的是String ，后端要包装一下 才能进行读写 PLCType.valueOf(type).convertType(newValue);
     *      3. 如果是QByteString类型 ，他不是一个数组 但是它表现出来的类型是一个数组。
     * 参数：
     *      PLCVar   这个变量的信息
     *      newValue 是新值  注意 要提前知道 这个变量的数据类型，然后调用静态类 Unsigned 进行数据类型转化
     * 返回值：
     *      true：    写成功
     *      false：   写失败
     *      null      代表选中的plc不存在或者参数异常
     * 格式：setValue(PLCVar.RealPLC, Unsigned.ubyte(110),"plc3")
     * 例子：① {
     *     "nameSpace": 6,
     *     "identifier": "S7-1200 station_1.PLC_1.TestDB80.Array[0..7] of Real1",
     *     "newValue": "1.1,2.1,3.1,4.1,5.1,6.1,7.1,8.1#QFloat",
     *     "type": "QArray",
     *     "plcName": "plc1"
     * }
     *  ② {   //非数组---常规变量
     *     "nameSpace": 6,
     *     "identifier": "S7-1200 station_1.PLC_1.TestDB80.Bool1",
     *     "newValue": "true",
     *     "type": "QBoolean",
     *     "plcName": "plc1"
     * }
     * {    //非数组---非常规变量 （ByteString）
     *     "nameSpace": 6,
     *     "identifier": "S7-1200 station_1.PLC_1.TestDB80.Array[0..7] of Byte1",
     *     "newValue": "1,2,3,4,5,6,1,1",
     *     "type": "QByteString",
     *     "plcName": "plc1"
     * }
     * */
    public Boolean setValue(PLCVar var,Object newValue,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        if(var.getIdentifier() == null || "".equals(var.getIdentifier()) || var.getNameSpace()==null || newValue == null){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);

        Variant v = new Variant(newValue);
        // don't write status or timestamps
        DataValue dv = new DataValue(v, null, null);
        //
        StatusCode statusCode = null;
        try {
            statusCode = client.writeValue(new NodeId(var.getNameSpace(), var.getIdentifier()), dv).get();
        }catch (Exception e){
            throw new Exception("OPCUA 写变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return statusCode.isGood();
    }

    //todo 去优化 当plc 不存在时候 的读写操作
    public Boolean setValue(Integer nameSpace,String identifier,Object newValue,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);
        if(identifier == null || "".equals(identifier) || nameSpace==null || newValue == null || client == null){
            return null;
        }
        Variant v = new Variant(newValue);
        // don't write status or timestamps
        DataValue dv = new DataValue(v, null, null);
        //
        StatusCode statusCode = null;
        try {
            statusCode = client.writeValue(new NodeId(nameSpace, identifier), dv).get();
        }catch (Exception e){
            throw new Exception("OPCUA 写变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return statusCode.isGood();
    }

    /**
     * 含义： 修改 多个PLC变量 的值
     * 参数：
     *      List<PLCVar>  要修改变量的集合
     *      List<Object>  新值集合  注意 要提前知道 这个变量的数据类型，然后调用静态类 Unsigned 对newValue 进行数据类型转化
     * 返回值：
     *      true：    写成功
     *      false：   写失败
     *      null      参数异常  或者  指定的plc不存在
     * 注意：
     *      1.list<var>  要 和 list<Object>   一一对应
     *      2.list<Object>  ==》  可以是 ["string","int","byte".....]
     * */
    public Boolean setValues(List<PLCVar> vars,List<Object> newValues,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);

        if(vars.isEmpty() || newValues.isEmpty() || vars.size()!=newValues.size() || client == null){
            return null;
        }

        List<NodeId> nodeIds = new ArrayList<>();
        List<DataValue> NEWValues = new ArrayList<>();
        for(int i=0;i<vars.size();i++){
            PLCVar p = vars.get(i);
            Object n = newValues.get(i);
            // don't write status or timestamps
            DataValue dv = new DataValue(new Variant(n), null, null);
            NEWValues.add(dv);
            nodeIds.add(new NodeId(p.getNameSpace(),p.getIdentifier()));
        }

        List<StatusCode> statusCode = null;
        try {
            statusCode = client.writeValues(nodeIds,NEWValues).get();
        }catch (Exception e){
            throw new Exception("OPCUA 写多个变量出现异常,,具体异常是: "+e.getMessage());
        }
        //future.complete(opcUaClient);
        return statusCode.stream().allMatch(i->i.isGood());
    }

    //返回   true 表示 合法 ；； false 表示 不合法
    private boolean isValid(String plcName){
       return opcUaClients.get(plcName) != null;
    }

    /**
     * 参数：
     *          回调函数BiConsumer<UaMonitoredItem, DataValue>，是当你订阅的这个变量当变量发生 改变的时候 你执行的方法(刚开始 会执行一次)
     *
     * 返回值：
     *          1  <===> 你要订阅的这个Node 订阅成功
     *          -2  <===> 你要订阅的这个Node 已订阅
     *          -3  <===> 你要订阅的这个Node 订阅失败
     *          有异常直接抛出
     *          null   代表选中的plc不存在
     *
     *      tip：Subscription有两种模式，一种是Reporting，另一种是Sampling。
     *      如果定义为Sampling，则这个Subscription是一个Triggered Item，即被激发的订阅，需要一个定义为Reporting的Subscription（称为Triggering Item）
     * 与它连接。这样当Triggering Item更新时，会激发Triggered Item更新。
     *
     * */
    public synchronized Integer subscribe(PLCVar var,Double listenTimeInterval,BiConsumer<UaMonitoredItem, DataValue> biConsumer,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        this.flag = true;
        OpcUaClient client = opcUaClients.get(plcName);


        // create a subscription @ 1000ms
        // create a subscription @ 1000ms  一个订阅可以包含多个监控item

        String subStr = plcName+":"+var.getNameSpace()+","+var.getIdentifier()+";";
        UaSubscription subscription = null;
        try {
            subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
        }catch (Exception e){
            allSubscription.remove(subStr);
            throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
        }

        if(subscription == null){
            return -3;
        }

        //之前订阅过
        if(allSubscription.containsKey(subStr)){
            SubscribeEntity se = allSubscription.get(subStr);
            //已经在 订阅
            if(se.getStatus() == 1){
                return -2;
            }
            //订阅被取消 后 只要开启就行了
            else if(se.getStatus() == 2){
                StatusCode statusCode = null;

                try {
                    statusCode = se.getUaSubscription().setPublishingMode(true).get();
                }catch (Exception e){
                    allSubscription.remove(subStr);
                    throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
                }
                se.setStatus(1);
                return statusCode.isGood()?1:-3;
            }
        }

        //之前 没订阅过
        SubscribeEntity subscribeEntity = new SubscribeEntity(subscription,1);
        allSubscription.put(subStr,subscribeEntity);

        // subscribe to the Value attribute of the server's CurrentTime node
        /*ReadValueId readValueId = new ReadValueId(
            Identifiers.Server_ServerStatus_CurrentTime,
            AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
        );*/
        /* *
         * 第一个参数 ： 就是你要订阅的变量
         * 第二个参数 ： 你监听变量的那个属性，，这里是我们要监听 变量的value 而不是其他变量。
         * 第三个参数 ： 保持默认就行
         * 第四个参数 ： 保持默认就行
         * */
        ReadValueId readValueId = new ReadValueId(
                new NodeId(var.getNameSpace(),var.getIdentifier()),
                AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
        );

        // IMPORTANT: client handle must be unique per item within the context of a subscription.
        // You are not required to use the UaSubscription's client handle sequence; it is provided as a convenience.
        // Your application is free to assign client handles by whatever means necessary.
        // 注意clientHandle 这个句柄 必须是独一无二 的 所以用nextClientHandle
        UInteger clientHandle = uint(clientHandleGlobal.getAndIncrement());

        MonitoringParameters parameters = new MonitoringParameters(
                clientHandle,
                listenTimeInterval,     // sampling interval
                null,       // filter, null means use default
                uint(10),   // queue size
                true        // discard oldest
        );

        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                readValueId,
                MonitoringMode.Reporting,
                parameters
        );

        // when creating items in MonitoringMode.Reporting this callback is where each item needs to have its
        // value/event consumer hooked up. The alternative is to create the item in sampling mode, hook up the
        // consumer after the creation call completes, and then change the mode for all items to reporting.
        //item.setValueConsumer 就是 当item 的value 发生改变的时候 执行 的回调函数 函数是this::onSubscriptionValue
        BiConsumer<UaMonitoredItem, Integer> onItemCreated =
                (item, id) -> item.setValueConsumer(biConsumer);

        //创建监控item, 第一个为Reporting mode
        // 加了 get() 就相当于 把 执行线程阻塞 住了，只有当建立订阅成功以后，才能往后执行。
        List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                //这里第二个参数 是List<MonitoredItemCreateRequest>,,可以同时订阅多个 变量。
                newArrayList(request),
                onItemCreated
        ).get();

        for (UaMonitoredItem item : items) {
            if (item.getStatusCode().isGood()) {
                logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
            } else {
                logger.warn(
                        "failed to create item for nodeId={} (status={})",
                        item.getReadValueId().getNodeId(), item.getStatusCode());
            }
        }

        // 外部 地方会 future.get() 取 异步处理的结果，但是如果 结果还没出，你就去 complete()了
        // 那么会直接把complete(client) 里面的client 作为结果传给 外部。外部的future.get() 获得就是传过来的 client
        // future.complete(this.opcUaClient);   future 什么时候结束 由外部回调方法决定
        /*while (flag){
            Thread.sleep(1000);
        }*/
        return 1;
    }

    /**
     * 同时订阅 或 唤醒订阅 多个变量
     * 注意：
     * 同时监听多个变量，只要有一个变量发生改变了，就把调用回调函数并且把那个改变了的变量通过websocket发给前端（如果两个同时改变也不是同时发给前端的是一个一个发的）。因为不同变量有不同的监视器，而监视器他们自己会调回调函数
     * 参数：
     *          回调函数BiConsumer<UaMonitoredItem, DataValue>，是当你订阅的这个变量当变量发生 改变的时候 你执行的方法(刚开始 会执行一次)
     * 返回值：
     *                1  <===> 你要订阅的这个Node 订阅成功
     *                -2  <===> 你要订阅的这个Node 已订阅
     *                -3  <===> 你要订阅的这个Node 订阅失败
     *                有异常直接抛出
     *                null      代表选中的plc不存在
     *
     *      tip：Subscription有两种模式，一种是Reporting，另一种是Sampling。
     *      如果定义为Sampling，则这个Subscription是一个Triggered Item，即被激发的订阅，需要一个定义为Reporting的Subscription（称为Triggering Item）
     * 与它连接。这样当Triggering Item更新时，会激发Triggered Item更新。
     *
     * */
    public synchronized Integer subscribeValues(List<PLCVar> vars,Double listenTimeInterval,BiConsumer<UaMonitoredItem, DataValue> biConsumer,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        this.flag = true;
        OpcUaClient client = opcUaClients.get(plcName);

        // create a subscription @ 1000ms
        // create a subscription @ 1000ms  一个订阅可以包含多个监控item

        String subStr = plcName+":";
        for(PLCVar index:vars){
            subStr  = subStr + index.getNameSpace()+","+index.getIdentifier()+";";
        }

        UaSubscription subscription = null;
        try {
            subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
        }catch (Exception e){
            allSubscription.remove(subStr);
            throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
        }
        if(subscription == null){
            return -3;
        }

        //之前订阅过,,如果之前订阅过了，那么唤醒就行了。
        if(allSubscription.containsKey(subStr)){
            SubscribeEntity se = allSubscription.get(subStr);
            //已经在 订阅
            if(se.getStatus() == 1){
                return -2;
            }
            //订阅被取消 后 只要开启就行了
            else if(se.getStatus() == 2){
                StatusCode statusCode = null;

                try {
                    statusCode = se.getUaSubscription().setPublishingMode(true).get();
                }catch (Exception e){
                    allSubscription.remove(subStr);
                    throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
                }
                se.setStatus(1);
                return statusCode.isGood()?1:-3;
            }
        }

        //之前 没订阅过
        SubscribeEntity subscribeEntity = new SubscribeEntity(subscription,1);
        allSubscription.put(subStr,subscribeEntity);


        // IMPORTANT: client handle must be unique per item within the context of a subscription.
        // You are not required to use the UaSubscription's client handle sequence; it is provided as a convenience.
        // Your application is free to assign client handles by whatever means necessary.
        // 注意clientHandle 这个句柄 必须是独一无二 的 所以用nextClientHandle
        UInteger clientHandle = uint(clientHandleGlobal.getAndIncrement());

        MonitoringParameters parameters = new MonitoringParameters(
                clientHandle,
                listenTimeInterval,     // sampling interval
                null,       // filter, null means use default
                uint(10),   // queue size
                true        // discard oldest
        );

        // subscribe to the Value attribute of the server's CurrentTime node
        /*ReadValueId readValueId = new ReadValueId(
            Identifiers.Server_ServerStatus_CurrentTime,
            AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
        );*/

        List<MonitoredItemCreateRequest> listMonitor = new ArrayList<>();
        for(PLCVar plcVar:vars){
            /* *
             * 第一个参数 ： 就是你要订阅的变量
             * 第二个参数 ： 你监听变量的那个属性，，这里是我们要监听 变量的value 而不是其他变量。
             * 第三个参数 ： 保持默认就行
             * 第四个参数 ： 保持默认就行
             * */
            ReadValueId readValueId = new ReadValueId(
                    new NodeId(plcVar.getNameSpace(),plcVar.getIdentifier()),
                    AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
            );
            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    readValueId,
                    MonitoringMode.Reporting,
                    parameters
            );
            listMonitor.add(request);
        }



        // when creating items in MonitoringMode.Reporting this callback is where each item needs to have its
        // value/event consumer hooked up. The alternative is to create the item in sampling mode, hook up the
        // consumer after the creation call completes, and then change the mode for all items to reporting.
        //item.setValueConsumer 就是 当item 的value 发生改变的时候 执行 的回调函数 函数是this::onSubscriptionValue
        BiConsumer<UaMonitoredItem, Integer> onItemCreated =
                (item, id) -> item.setValueConsumer(biConsumer);

        //创建监控item, 第一个为Reporting mode
        // 加了 get() 就相当于 把 执行线程阻塞 住了，只有当建立订阅成功以后，才能往后执行。
        List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                //这里第二个参数 是List<MonitoredItemCreateRequest>,,可以同时订阅多个 变量。
                listMonitor,
                onItemCreated
        ).get();

        for (UaMonitoredItem item : items) {
            if (item.getStatusCode().isGood()) {
                logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
            } else {
                logger.warn(
                        "failed to create item for nodeId={} (status={})",
                        item.getReadValueId().getNodeId(), item.getStatusCode());
            }
        }

        // 外部 地方会 future.get() 取 异步处理的结果，但是如果 结果还没出，你就去 complete()了
        // 那么会直接把complete(client) 里面的client 作为结果传给 外部。外部的future.get() 获得就是传过来的 client
        // future.complete(this.opcUaClient);   future 什么时候结束 由外部回调方法决定
        /*while (flag){
            Thread.sleep(1000);
        }*/

        return 1;
    }
    /**
     * 同时订阅多个变量
     * 注意：
     * 同时监听多个变量，只要有一个变量发生改变了，就把调用回调函数并且把那个改变了的变量通过websocket发给前端（如果两个同时改变也不是同时发给前端的是一个一个发的）。因为不同变量有不同的监视器，而监视器他们自己会调回调函数
     * 参数：
     *          回调函数BiConsumer<UaMonitoredItem, DataValue>，是当你订阅的这个变量当变量发生 改变的时候 你执行的方法(刚开始 会执行一次)
     * 返回值：
     *                1  <===> 你要订阅的这个Node 订阅成功
     *                -2  <===> 你要订阅的这个Node 已订阅
     *                -3  <===> 你要订阅的这个Node 订阅失败
     *                有异常直接抛出
     *                null      代表选中的plc不存在
     *
     *      tip：Subscription有两种模式，一种是Reporting，另一种是Sampling。
     *      如果定义为Sampling，则这个Subscription是一个Triggered Item，即被激发的订阅，需要一个定义为Reporting的Subscription（称为Triggering Item）
     * 与它连接。这样当Triggering Item更新时，会激发Triggered Item更新。
     *
     * */
    public synchronized Integer subscribeValues(List<Integer> listNameSpace,List<String> listIdentifier,Double listenTimeInterval,BiConsumer<UaMonitoredItem, DataValue> biConsumer,String plcName) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        if(listNameSpace.size()!=listIdentifier.size()){
            return -3;
        }
        this.flag = true;
        OpcUaClient client = opcUaClients.get(plcName);

        // create a subscription @ 1000ms
        // create a subscription @ 1000ms  一个订阅可以包含多个监控item

        /*String subStr = plcName+":";
        for(PLCVar index:vars){
            subStr  = subStr + index.getNameSpace()+","+index.getIdentifier()+";";
        }*/
        String subStr = plcName+":";
        int size = listIdentifier.size();
        for(int i=0;i<size;i++){
            subStr  = subStr + listNameSpace.get(i)+","+listIdentifier.get(i)+";";
        }

        UaSubscription subscription = null;
        try {
            subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
        }catch (Exception e){
            allSubscription.remove(subStr);
            throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
        }
        if(subscription == null){
            return -3;
        }

        //之前订阅过
        if(allSubscription.containsKey(subStr)){
            SubscribeEntity se = allSubscription.get(subStr);
            //已经在 订阅
            if(se.getStatus() == 1){
                return -2;
            }
            //订阅被取消 后 只要开启就行了
            else if(se.getStatus() == 2){
                StatusCode statusCode = null;

                try {
                    statusCode = se.getUaSubscription().setPublishingMode(true).get();
                }catch (Exception e){
                    allSubscription.remove(subStr);
                    throw new Exception("在 订阅变量的时候出现异常,,具体异常是: "+e.getMessage());
                }
                se.setStatus(1);
                return statusCode.isGood()?1:-3;
            }
        }

        //之前 没订阅过
        SubscribeEntity subscribeEntity = new SubscribeEntity(subscription,1);
        allSubscription.put(subStr,subscribeEntity);


        // IMPORTANT: client handle must be unique per item within the context of a subscription.
        // You are not required to use the UaSubscription's client handle sequence; it is provided as a convenience.
        // Your application is free to assign client handles by whatever means necessary.
        // 注意clientHandle 这个句柄 必须是独一无二 的 所以用nextClientHandle
        UInteger clientHandle = uint(clientHandleGlobal.getAndIncrement());

        MonitoringParameters parameters = new MonitoringParameters(
                clientHandle,
                listenTimeInterval,     // sampling interval
                null,       // filter, null means use default
                uint(10),   // queue size
                true        // discard oldest
        );

        List<MonitoredItemCreateRequest> listMonitor = new ArrayList<>();
        for(int i=0;i<size;i++){
            /* *
             * 第一个参数 ： 就是你要订阅的变量
             * 第二个参数 ： 你监听变量的那个属性，，这里是我们要监听 变量的value 而不是其他变量。
             * 第三个参数 ： 保持默认就行
             * 第四个参数 ： 保持默认就行
             * */
            ReadValueId readValueId = new ReadValueId(
                    new NodeId(listNameSpace.get(i),listIdentifier.get(i)),
                    AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE
            );
            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    readValueId,
                    MonitoringMode.Reporting,
                    parameters
            );
            listMonitor.add(request);
        }

        // when creating items in MonitoringMode.Reporting this callback is where each item needs to have its
        // value/event consumer hooked up. The alternative is to create the item in sampling mode, hook up the
        // consumer after the creation call completes, and then change the mode for all items to reporting.
        //item.setValueConsumer 就是 当item 的value 发生改变的时候 执行 的回调函数 函数是this::onSubscriptionValue
        BiConsumer<UaMonitoredItem, Integer> onItemCreated =
                (item, id) -> item.setValueConsumer(biConsumer);

        //创建监控item, 第一个为Reporting mode
        // 加了 get() 就相当于 把 执行线程阻塞 住了，只有当建立订阅成功以后，才能往后执行。
        List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                //这里第二个参数 是List<MonitoredItemCreateRequest>,,可以同时订阅多个 变量。
                listMonitor,
                onItemCreated
        ).get();

        for (UaMonitoredItem item : items) {
            if (item.getStatusCode().isGood()) {
                logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
            } else {
                logger.warn(
                        "failed to create item for nodeId={} (status={})",
                        item.getReadValueId().getNodeId(), item.getStatusCode());
            }
        }

        // 外部 地方会 future.get() 取 异步处理的结果，但是如果 结果还没出，你就去 complete()了
        // 那么会直接把complete(client) 里面的client 作为结果传给 外部。外部的future.get() 获得就是传过来的 client
        // future.complete(this.opcUaClient);   future 什么时候结束 由外部回调方法决定
        /*while (flag){
            Thread.sleep(1000);
        }*/

        return 1;
    }


    private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
        logger.info(
                "subscription value received: item={}, value={}",
                item.getReadValueId().getNodeId(), value.getValue());
    }


    /**
     * 动态添加plc
     * user_config是连接的账号密码： " CXCX,251128856 "
     * 注意
     *
     * 1  代表成功
     * 0  代表已连接
     * 异常 如果有异常 就抛出异常msg
     * //-1 代表异常
     * //-2 代表opc-server端 没有设置 新建证书为 信任
     */
    public Integer dynamicAddPlc(String plcName,String url_config,String policy_config,String user_configs,String ip,Integer messageMode) throws Exception {
        try{
            String s = "";
            String ss = "";
            if(CommonFunction.isLinux()){
                s = readJsonFile(PLCConstant.localURLForLinux);
                ss = PLCConstant.localURLForLinux;
            }else if(CommonFunction.isWindows()){
                s = readJsonFile(PLCConstant.localURLForWindows);
                ss = PLCConstant.localURLForWindows;
            }

            String[] users = null;
            //保存到 本地后然后打开 opcclient
            if("".equals(user_configs) || user_configs==null){
                users =null;
            }else {
                users = user_configs.split(",");
            }
            PLCConfig plcConfig = new PLCConfig(plcName, url_config, SecurityPolicy.valueOf(policy_config), users, ip,messageMode);
            opcUaClients.put(plcName,createClient(plcConfig));
            opcUaClients.get(plcName).connect().get();

            //只有当写入无异常的时候在录到本地json文件中去
            //写入json 开始
            JSONObject jobj = JSON.parseObject(s);

            JSONArray movies = jobj.getJSONArray("config");//构建JSONArray数组

            for(int i=0;i<movies.size();i++){
                JSONObject key = (JSONObject)movies.get(i);
                String getPlcName = (String)key.get("plc_name");
                if(getPlcName.equals(plcName)){
                    //如果 配置中已经 配置了此plc 那就什么也不做。
                    return 0;
                }
            }

            Map<String, Object> newValue = new LinkedHashMap<String, Object>();
            newValue.put("plc_name", plcName);
            newValue.put("identityProvider_config", user_configs);
            newValue.put("ip", ip);
            newValue.put("endpointUrl_config", url_config);
            newValue.put("securityPolicy_config", policy_config);
            newValue.put("messageMode_config", messageMode);
            movies.add(newValue);

            JSONObject json = jobj;


            File file = new File(ss);


            if (!file.exists()) {
                boolean b= file.createNewFile();

            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            json.writeJSONString(bw);
            bw.close();
            System.out.println("end");
            //写入json 结束

            return 1;
        }catch (Exception e){
            String err = "动态配置PLC时候 出现异常："+e.getMessage();
            logger.error(err);

            //出现异常后把 连接给中断，，然后回收资源
            opcUaClients.get(plcName).disconnect().get();
            opcUaClients.remove(plcName);
            throw e;
        }
    }

    /**
     * 动态添加plc
     * user_config是连接的账号密码： "CXCX,251128856"
     *
     * 1  代表成功
     * 0  代表已连接
     * 如果有异常 直接抛出异常 msg
     * null   代表选中的plc不存在
     *
     */
    public Integer dynamicRemovePlc(String plcName) throws IOException, ExecutionException, InterruptedException {
        if(!isValid(plcName)){
            return null;
        }
        try {
            String s = "";
            if(CommonFunction.isLinux()){
                s = readJsonFile(PLCConstant.localURLForLinux);
            }else if(CommonFunction.isWindows()){
                s = readJsonFile(PLCConstant.localURLForWindows);
            }
            JSONObject jobj = JSON.parseObject(s);
            LocalMulPLCConfig.getPLCConfig();

            JSONArray movies = jobj.getJSONArray("config");//构建JSONArray数组


            boolean flag = false;
            for(int i=0;i<movies.size();i++){
                JSONObject key = (JSONObject)movies.get(i);
                String getPlcName = (String)key.get("plc_name");
                if(getPlcName.equals(plcName)){
                    //如果 配置中已经 配置了此plc 那就什么也不做。
                    flag = true;
                    break;
                }
            }
            if(!flag){
                return 0;
            }

            //movies.
            movies.removeIf(i->{
                JSONObject is = (JSONObject)i;
                return is.get("plc_name").equals(plcName);
            });

            JSONObject json = jobj;

            String ss = "";
            if(CommonFunction.isLinux()){
                ss = PLCConstant.localURLForLinux;
            }else if(CommonFunction.isWindows()){
                ss = PLCConstant.localURLForWindows;
            }
            File file = new File(ss);

            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            json.writeJSONString(bw);
            bw.close();
            System.out.println("end");

            opcUaClients.get(plcName).disconnect().get();
            opcUaClients.remove(plcName);
            //Stack.releaseSharedResources();

            return 1;
        }catch (Exception e){
            logger.error("在去除plc的时候出现异常："+e.getMessage());
            throw e;
        }
    }

    /**
     * 注意：和上面哪个方法不同的是返回的结果 ，，这方法返回的是树形结果
     * 含义：获取指定plc 根目录（可指定目录）  下的所有 变量，，即获取变量目录
     * 参数：
     *      1.plcName
     *      2.根目录NodeId 的 namespace
     *      3.根目录NodeId 的 identifier
     *      null   代表选中的plc不存在
     * 返回：所有这个根目录NodeId 下的 所有边来那个，，方便前端自动订阅变量
     * */
    public NodeIdKey browseA(String plcName,Integer rootNameSpace,String identifier){
        if(!isValid(plcName)){
            return null;
        }
        List<NodeIdKey> res = new ArrayList<>();
        NodeId rootNodeId = new NodeId(rootNameSpace,identifier);
        String rootName = rootNameSpace+","+identifier;
        NodeIdKey rootNodeIdKey = new NodeIdKey(rootName, NodeClass.Object.getValue(), rootNameSpace, identifier,"根目录");
        rootNodeIdKey.setChild(browseNodeA(rootNodeIdKey, opcUaClients.get(plcName), rootNodeId));

        return rootNodeIdKey;
    }

    private List<NodeIdKey> browseNodeA(NodeIdKey parent, OpcUaClient client, NodeId browseRoot) {
        List<NodeIdKey> result = new ArrayList<>();
        try {
            List<Node> nodes = client.getAddressSpace().browse(browseRoot).get();

            for (Node node : nodes) {
                NodeId nodeId=node.getNodeId().get();
                //logger.info("NodeName={},ns={},id={}", node.getBrowseName().get().getName(),nodeId.getNamespaceIndex(),nodeId.getIdentifier());
                int nodeType = node.getNodeClass().get().getValue();
                String varType = "";

                //遍历节点 得到的是变量
                if(nodeType == NodeClass.Variable.getValue()){
                    UShort ns = nodeId.getNamespaceIndex();
                    String iden = (String)nodeId.getIdentifier();
                    try {
                        DataValue plc1 = getValue(Integer.valueOf(String.valueOf(ns)), iden, "plc1");
                        Object value = plc1.getValue().getValue();
                        varType = CommonFunction.judgeVarType(value);
                    }catch (Exception e){
                        logger.error("获取文件目录 --  遍历变量异常");
                    }
                //遍历节点 得到的是文件夹
                }else if(nodeType == NodeClass.Object.getValue()){
                    varType = "文件夹";
                //遍历节点 得到的是视图
                }else if(nodeType == NodeClass.View.getValue()){
                    varType = "视图";
                }
                //....后面可以补充

                NodeIdKey nodeIdKey1 = new NodeIdKey(node.getBrowseName().get().getName(), nodeType, Integer.parseInt(nodeId.getNamespaceIndex().toString()), nodeId.getIdentifier().toString(),varType);
                result.add(nodeIdKey1);
                // recursively browse to children

                //NodeIdKey nodeIdKey = new NodeIdKey(browName,66,Integer.parseInt(String.valueOf(browseRoot.getNamespaceIndex())),browseRoot.getIdentifier().toString());

                // 目录也是一个NodeId，，所以要递归的去遍历看看是否 是目录
                nodeIdKey1.setChild(browseNodeA(nodeIdKey1, client, node.getNodeId().get()));
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Browsing nodeId={} failed: {}", browseRoot, e.getMessage(), e);
        }

        return result;
    }

    /**
     * 含义：获取某个变量在 某个时间段内的 所有历史数据
     * 参数：
     *      1.plcName
     *      2.开始时间、结束时间  注意是DataTime格式 并且传入的是时间戳
     *      3.nodeId 的 ns 和 identify
     * 返回：
     *      1.如果这个时间段 内 有变量 就返回 List<DataValue>
     *      2.如果没有变量就返回new ArrayList
     *      3.null  代表训中的plc不存在
     * 注意：你要读取的这个变量 必须在OPC Server 上设置 为 historizing：true
     * */
    public List<DataValue> historyRead(String plcName,DateTime start,DateTime end,Integer nameSpace,String identifier) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        OpcUaClient client = opcUaClients.get(plcName);

        HistoryReadDetails historyReadDetails = new ReadRawModifiedDetails(
                false,
                start,
                end,
                uint(0),
                true
        );

        HistoryReadValueId historyReadValueId = new HistoryReadValueId(
                new NodeId(nameSpace, identifier),
                null,
                QualifiedName.NULL_VALUE,
                ByteString.NULL_VALUE
        );

        List<HistoryReadValueId> nodesToRead = new ArrayList<>();
        nodesToRead.add(historyReadValueId);
        HistoryReadResponse historyReadResponse = null;
        try {
            historyReadResponse = client.historyRead(
                    historyReadDetails,
                    TimestampsToReturn.Both,
                    false,
                    nodesToRead
            ).get();
        }catch (Exception e){
            throw new Exception("在读 变量历史数据的时候出现异常,,具体异常是: "+e.getMessage());
        }


        HistoryReadResult[] historyReadResults = historyReadResponse.getResults();

        List<Variant> res = new ArrayList<>();

        if (historyReadResults != null) {
            HistoryReadResult historyReadResult = historyReadResults[0];
            HistoryData historyData = (HistoryData) historyReadResult.getHistoryData().decode(
                    client.getSerializationContext()
            );
            return l(historyData.getDataValues());
        }
        return new ArrayList<>();
    }


    /**
     * 含义：订阅单个事件
     * 参数：
     *      1.plcName
     *      2.你要订阅 事件的 nameSpace 和 identifier
     *      3.可以选择你要过滤的条件（现在暂时无，，后续可以补充）
     *      4.设置回调函数（就是当有订阅 的事件发生的时候 就调用的函数 ）
     * 返回：
     *      1   <===>  订阅成功
     *      -2  <===>  订阅的事件已存在
     *      如果出现异常 直接抛出
     *      -3  <===>  订阅失败
     *      null    代表选中的plc不存在
     *          ( 1.如果是系统内部调用 那么通过回调函数 来执行当 监听变量发生改变后的结果。 2.如果是第三方系统监听变量 那么 考虑用netty长连接 还是消息中间件再说。)
     *
     * 注意 ：事件节点（ns,identifier） identifier 可能是Integer（2232） 或者是 String（myDevice） 类型的。。所以要和电控沟通好，是什么类型就传什么类型过来
     * */
    public synchronized Integer subscribeEvent(String plcName,Integer nameSpace,Object identifier,BiConsumer<UaMonitoredItem, Variant[]> biConsumer) throws Exception {
        if(!isValid(plcName)){
            return null;
        }

        OpcUaClient client = opcUaClients.get(plcName);

        // create a subscription and a monitored item
        UaSubscription subscription = client.getSubscriptionManager()
                .createSubscription(1000.0).get();


        String subStr = plcName+":"+nameSpace+","+identifier+";";
        //之前订阅过
        if(allSubscription.containsKey(subStr)){
            SubscribeEntity se = allSubscription.get(subStr);
            //已经在 订阅
            if(se.getStatus() == 1){
                return -2;
            }
            //订阅被取消 后 只要开启就行了
            else if(se.getStatus() == 2){
                StatusCode statusCode = se.getUaSubscription().setPublishingMode(true).get();
                se.setStatus(1);
                return statusCode.isGood()?1:-3;
            }
        }

        //之前 没订阅过
        SubscribeEntity subscribeEntity = new SubscribeEntity(subscription,1);
        allSubscription.put(subStr,subscribeEntity);
        // subscription.setPublishingMode();

        NodeId nodeId = null;
        if(identifier instanceof String){
            nodeId = new NodeId(Unsigned.ushort(nameSpace),(String)identifier);
        }else if(identifier instanceof Integer){
            nodeId = new NodeId(Unsigned.ushort(nameSpace), Unsigned.uint((Integer) identifier));
        }

        ReadValueId readValueId = new ReadValueId(
                //Identifiers.Server,
                nodeId,
                AttributeId.EventNotifier.uid(),
                null,
                QualifiedName.NULL_VALUE
        );

        // client handle must be unique per item
        UInteger clientHandle = uint(clientHandleGlobal.getAndIncrement());

        //eventFilter 就是过滤出 下面配置的信息（也就是下面配置的信息是需要展示 的其他不需要展示）,,其实下面的过滤项 也是 一个一个变量。
        EventFilter eventFilter = new EventFilter(
                new SimpleAttributeOperand[]{
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "EventId")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "EventType")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Severity")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Time")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Message")},
                                AttributeId.Value.uid(),
                                null)
                },
                new ContentFilter(null)
        );


        MonitoringParameters parameters = new MonitoringParameters(
                clientHandle,
                0.0,
                ExtensionObject.encode(client.getSerializationContext(), eventFilter),
                uint(10),
                true
        );

        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                readValueId,
                MonitoringMode.Reporting,
                parameters
        );

        List<UaMonitoredItem> items = new ArrayList<>();
        try {
            items = subscription
                    .createMonitoredItems(TimestampsToReturn.Both, newArrayList(request)).get();
        }catch (Exception e){
            allSubscription.remove(subStr);
            throw new Exception("在 订阅事件的时候出现异常,,具体异常是: "+e.getMessage());
        }

        // do something with the value updates
        UaMonitoredItem monitoredItem = items.get(0);

        //回调接口
        monitoredItem.setEventConsumer(biConsumer);
        return 1;
    }

    /**
     * 含义：同时订阅 多个事件
     * 参数：
     *      1.plcName
     *      2.你要订阅 事件的 nameSpace 和 identifier
     *      3.可以选择你要过滤的条件（现在暂时无，，后续可以补充）
     *      4.设置回调函数（就是当有订阅 的事件发生的时候 就调用的函数 ）
     * 返回：
     *      1   <===>  订阅成功
     *      -2  <===>  订阅的事件已存在
     *      如果出现异常 直接抛出
     *      -3  <===>  订阅失败/参数错误
     *      null       代表选中的plc不存在
     *
     *          ( 1.如果是系统内部调用 那么通过回调函数 来执行当 监听变量发生改变后的结果。 2.如果是第三方系统监听变量 那么 考虑用netty长连接 还是消息中间件再说。)
     *
     * 注意 ：事件节点（ns,identifier） identifier 可能是Integer（2232） 或者是 String（myDevice） 类型的。。所以要和电控沟通好，是什么类型就传什么类型过来
     * */
    public synchronized Integer subscribeEvents(String plcName,List<Integer> nameSpace,List<Object> identifier,List<BiConsumer<UaMonitoredItem, Variant[]>> biConsumers) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        if(nameSpace.size() != identifier.size() && nameSpace.size() != biConsumers.size()){
            return -3;
        }

        OpcUaClient client = opcUaClients.get(plcName);

        // create a subscription and a monitored item
        UaSubscription subscription = client.getSubscriptionManager()
                .createSubscription(1000.0).get();

        String subStr = plcName+":";
        for(int i=0;i<nameSpace.size();i++){
            subStr = subStr + nameSpace.get(i)+","+identifier.get(i)+";";
        }

        //之前订阅过
        if(allSubscription.containsKey(subStr)){
            SubscribeEntity se = allSubscription.get(subStr);
            //已经在 订阅
            if(se.getStatus() == 1){
                return -2;
            }
            //订阅被取消 后 只要开启就行了
            else if(se.getStatus() == 2){
                StatusCode statusCode = se.getUaSubscription().setPublishingMode(true).get();
                se.setStatus(1);
                return statusCode.isGood()?1:-3;
            }
        }

        //之前 没订阅过
        SubscribeEntity subscribeEntity = new SubscribeEntity(subscription,1);
        allSubscription.put(subStr,subscribeEntity);
        // subscription.setPublishingMode();

        EventFilter eventFilter = new EventFilter(
                new SimpleAttributeOperand[]{
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "EventId")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "EventType")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Severity")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Time")},
                                AttributeId.Value.uid(),
                                null),
                        new SimpleAttributeOperand(
                                Identifiers.BaseEventType,
                                new QualifiedName[]{new QualifiedName(0, "Message")},
                                AttributeId.Value.uid(),
                                null)
                },
                new ContentFilter(null)
        );

        List<MonitoredItemCreateRequest> listMonitorItem = new ArrayList<>();
        for(int i = 0;i<nameSpace.size();i++){
            NodeId nodeId = null;
            Integer ns = nameSpace.get(i);
            Object o =  identifier.get(i);
            if(o instanceof String){
                nodeId = new NodeId(Unsigned.ushort(ns),(String)o);
            }else if(o instanceof Integer){
                nodeId = new NodeId(Unsigned.ushort(ns), Unsigned.uint((Integer) o));
            }

            ReadValueId readValueId = new ReadValueId(
                    //Identifiers.Server,
                    nodeId,
                    AttributeId.EventNotifier.uid(),
                    null,
                    QualifiedName.NULL_VALUE
            );

            // client handle must be unique per item
            UInteger clientHandle = uint(clientHandleGlobal.getAndIncrement());



            MonitoringParameters parameters = new MonitoringParameters(
                    clientHandle,
                    0.0,
                    ExtensionObject.encode(client.getSerializationContext(), eventFilter),
                    uint(10),
                    true
            );

            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    readValueId,
                    MonitoringMode.Reporting,
                    parameters
            );
            listMonitorItem.add(request);
        }

        List<UaMonitoredItem> items = new ArrayList<>();
        try {
            items = subscription
                    .createMonitoredItems(TimestampsToReturn.Both, listMonitorItem).get();
        }catch (Exception e){
            allSubscription.remove(subStr);
            throw new Exception("在 订阅事件的时候出现异常,,具体异常是: "+e.getMessage());

        }

        // do something with the value updates

        //回调接口
        /*for(UaMonitoredItem it:items){
            it.setEventConsumer(biConsumer);
        }*/
        for(int i=0;i<nameSpace.size();i++){
            UaMonitoredItem ua = items.get(i);
            ua.setEventConsumer(biConsumers.get(i));
        }

        return 1;
    }


    /* *
     * 原因：订阅包含一个寿命计数器，保存了在没有发布请求时经历的循环次数，当达到阈值时，会删除这个订阅以及与订阅相关的监控项。在删除订阅时，会发送一条StateChangeNotification消息，并携带状态码Bad_Timeout
     * */
    /**
     * 含义：暂停订阅 某个Node 或者 List<Node>  (无论是订阅变量 或者是订阅 Event)
     * 参数：
     *      1.plcName
     *      2.你要取消订阅 事件的 nameSpace 和 identifier
     *      3.可以选择你要过滤的条件（现在暂时无，，后续可以补充）
     *      4.设置回调函数（就是当有订阅 的事件发生的时候 就调用的函数 ）
     * 返回：Integer
     *      1  <===> 代表 取消订阅的事件 成功
     *      -2 <===> 代表 你要取消的订阅事件 不存在
     *      -3 <===> 代表 取消订阅失败/参数错误
     *      -4 <===> 代表 你要暂停的订阅事件 已经暂停了
     *      如果有异常就直接抛出异常
     *      null   代表选中的plc异常
     *
     * 注意：暂停订阅的时候 ，传过来来的nodeList 顺序 必须和 你之前 订阅的nodeList 顺序一致，否者会无法取消订阅
     *
     * */
    public synchronized Integer suspendSubscribe(String plcName, List<Integer> nameSpace, List<Object> identifier) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        String key = plcName+":";
        if(nameSpace.size()!= identifier.size()){
            return -3;
        }
        for(int i=0;i<nameSpace.size();i++){
            key = key + nameSpace.get(i)+","+identifier.get(i)+";";
        }

        boolean b = allSubscription.containsKey(key);
        //如果不包含 那就不能取消
        if(!b){
            return -2;
        }
        SubscribeEntity subscribeEntity = allSubscription.get(key);


        if(subscribeEntity.getStatus() == 2){
            return -4;
        }

        UaSubscription uaSubscription = subscribeEntity.getUaSubscription();
        StatusCode statusCode = null;
        try {
            statusCode = uaSubscription.setPublishingMode(false).get();
            subscribeEntity.setStatus(2);
        }catch (Exception e){
            throw new Exception("在 取消订阅事件的时候出现异常,,具体异常是: "+e.getMessage());
        }
        return  statusCode.isGood()?1:-3;
    }

    /**
     * 含义：删除订阅 某个Node 或者 List<Node>  (无论是订阅变量 或者是订阅 Event)
     * 参数：
     *      1.plcName
     *      2.你要取消订阅 事件的 nameSpace 和 identifier
     *      3.可以选择你要过滤的条件（现在暂时无，，后续可以补充）
     *      4.设置回调函数（就是当有订阅 的事件发生的时候 就调用的函数 ）
     * 返回：Integer
     *      1  <===> 代表 删除订阅的事件 成功
     *      -2 <===> 代表 你要删除的订阅事件 不存在
     *      -3 <===> 代表 删除订阅失败/参数错误
     *      如果有异常就直接抛出异常
     *      null      代表选中的plc异常
     *
     *  注意：删除订阅的时候 ，传过来来的nodeList 顺序 必须和 你之前 订阅的nodeList 顺序一致，否者会无法取消订阅
     *
     * */
    public synchronized Integer deleteSubscribe(String plcName, List<Integer> nameSpace, List<Object> identifier) throws Exception {
        if(!isValid(plcName)){
            return null;
        }
        String key = plcName+":";
        if(nameSpace.size()!= identifier.size()){
            return -3;
        }
        for(int i=0;i<nameSpace.size();i++){
            key = key + nameSpace.get(i)+","+identifier.get(i)+";";
        }

        boolean b = allSubscription.containsKey(key);
        //如果不包含 那就不能取消
        if(!b){
            return -2;
        }
        SubscribeEntity subscribeEntity = allSubscription.get(key);

        UaSubscription uaSubscription = subscribeEntity.getUaSubscription();

        //subscriptionManager 是在你new 一个client 的时候就生成了。
        OpcUaSubscriptionManager subscriptionManager = opcUaClients.get(plcName).getSubscriptionManager();

        //deleteSubscription 返回的是删除了的订阅，，，deleteSubscription参数是订阅Id。
        UaSubscription uaSubscription1 = subscriptionManager.deleteSubscription(uaSubscription.getSubscriptionId()).get();

        //allSubscription 是我们自己维护的
        allSubscription.remove(key);

        return  uaSubscription1!=null?1:-3;
    }

    public void run() {
        try {

            //一旦建立 连接 后 尽管 session 或者 网络波动或者是 OPC Server 异常 。。都会尝试断线重新连接
            HashMap<String, PLCConfig> hashMap = LocalMulPLCConfig.getPLCConfig();
            //构建 opcclients
            for(String key:hashMap.keySet()){
                OpcUaClient op = null;
                try {
                    op =  createClient(hashMap.get(key));
                }catch (Exception e){
                    logger.error("采集程序启动的时候,尝试连接 "+key +"失败，，可能是网络问题 或者 OPC SERVER问题");
                    continue;
                }
                opcUaClients.put(key,op);
            }

            // 初始化 opcclients
            for(String key:this.opcUaClients.keySet()){
                opcUaClients.get(key).connect().get();
            }
        } catch (Throwable t) {
            logger.error("Error getting client: {}", t.getMessage(), t);
            future.completeExceptionally(t);
        }
    }

}
