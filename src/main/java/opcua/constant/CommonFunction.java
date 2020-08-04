package opcua.constant;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.*;

import java.io.File;
import java.util.Arrays;

/**
 * 1.在从OPC Server 中 读到变量，要把变量包装一下 传给前端
 * 2.如果有些 opc-server 变量类型并不是 ns-iden  而仅仅是 iden 那么需要调用initialNodeId 初始化节点
 *
 * */
public class CommonFunction {
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static String extractError(String as){
        String[] errMsgs = as.split(",");
            /*for(String i:errMsgs){
                System.err.println(i);
            }*/

        String[] s = errMsgs[errMsgs.length-2].split(":");
        String errStatus = s[s.length-1];
        return errStatus.split("=")[1];
    }

    //注意：下面类型 必须和 PLCType枚举类里面的 数据类型按顺序一一对应
    //含义 是什么数据类型 就返回什么数据类型，如果是数组的话也会返回数组里面的数据类型如 "QArray|Boolen"
    public static String judgeVarType(Object value){
        if(value instanceof UByte){
            return PLCTypeConstant.QUByte;
        }else if(value instanceof UInteger){
            return PLCTypeConstant.QUInteger;
        }else if(value instanceof UShort){
            return PLCTypeConstant.QUShort;
        }else if(value instanceof ULong){
            return PLCTypeConstant.QULong;
        }else if(value instanceof Boolean){
            return PLCTypeConstant.QBoolean;
        }else if(value instanceof String){
            return PLCTypeConstant.QString;
        }else if(value instanceof Double){
            return PLCTypeConstant.QDouble;
        }else if(value instanceof Float){
            return PLCTypeConstant.QFloat;
        }else if(value instanceof Long){
            return PLCTypeConstant.QLong;
        }else if(value instanceof Integer){
            return PLCTypeConstant.QInteger;
        }else if(value instanceof Short){
            return PLCTypeConstant.QShort;
        }else if(value.getClass().isArray()){
            Object[] newArray = (Object[]) value;
            if(newArray.length==0){
                return PLCTypeConstant.QArray+"|0";
            }
            Object o = newArray[0];
            Object o1 = judgeVarType(o);
            return PLCTypeConstant.QArray+"|"+o1;
        }else if(value instanceof Byte){
            return PLCTypeConstant.QByte;
        }else if(value instanceof ByteString){
            return PLCTypeConstant.QByteString;
        }else {
            //如果是20 那么是数组类型了，数组的话只要string返回回去就行了
            //还有 如 二维数组、日期变量  那么如果需要可以补充 ，，暂时没有
            return null;
        }
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

    /**
     * 用处：在从OPC Server 中 读到变量，要把变量包装一下 传给前端
     * 含义: 包装变量
     * 如果传入的是数组，，那么把这个Object 包装成List，如果不是数组，就直接原路返回
     * //注意 传入的Objec，必须dataValue.getValue().getValue()
     * */
    public static Object var(Object object){
        if(object.getClass().isArray()){
            return Arrays.asList(object);
        }
        return object.toString();
    }


    public static Object var2String(Object object){
        if(object.getClass().isArray()){
            Object[] o = (Object[])object;
            String res = "";
            if(o.length>0){
                for(int i=0;i<o.length;i++){
                    if(i==o.length-1){
                        res += o[i].toString();
                    }else {
                        res += o[i].toString() +",";
                    }
                }
            }
            return res;
        }
        return object.toString();
    }

    public static NodeId initialNodeId(Integer value){
        return new NodeId(Unsigned.ushort(0),Unsigned.uint(value));
    }


}
