package opcua.constant;

public class PLCConstant {
    //配置多PLC 的 配置文件  （ 注意如果要在 新服务器上部署 没有文件结构要配置文件结构）
    public static final String localURLForWindows = "D:\\mulPLC\\mulPLCConfig4.json";
    public static final String localURLDirForWindows = "D:\\mulPLC";
    public static final String localURLForLinux = "/usr/local/security/mulPLCConfig4.json";
    public static final String localURLDirForLinux = "/usr/local/security";


    //连接OPC 的授权文件。
    public static final String securityURLForWindows = "D:\\mulPLC\\security";
    public static final String securityURLForLinux = "/usr/local/security";


    //常用线程
    public static Thread displayThread = new Thread();


    //错误常量
    
    //opc server  未授权证书
    public static final String SECURITY_CHECKS_FAILED = "Bad_SecurityChecksFailed";
    //Bad_Timeout 是指断网了（或者是网络波荡太久） 然后造成 连接超时
    public static final String TIMEOUT = "Bad_Timeout";
    //Bad_ConnectionRejected 是指opc server 宕机了
    public static final String CONNECTION_REJECTED = "Bad_ConnectionRejected";
    // 无session  原因：是你部署 程序的的服务器上没有配置 opc-server 的host   要加192.168.0.228       WIN-92SDA5G5VE8
    public static final String SESSION_CLOSED = "Bad_SessionClosed";





}
