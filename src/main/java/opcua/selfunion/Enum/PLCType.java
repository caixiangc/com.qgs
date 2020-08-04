package opcua.selfunion.Enum;

import opcua.constant.PLCTypeConstant;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;


//展示支持数据
//如果要添加新的变量 ：
//      1.①先在PLCTypeConstant中添加 变量常量；；
//      2.②在枚举类PLCType.convertType() 方法中扩展 新的变量，，如果是数组的话PLCType.convertArray()也要扩展；；③枚举类里面也要扩展QUByte(1),
//      3.④CommonFunction.judgeVarType() 里面也要扩展
public enum PLCType implements PLCTypeEnum{
    //注意：下面类型 必须和 CommonFunction.judgeVarType() 方法里面的数据类型 按顺序一一对应

    /**
     * 无符号 Byte
     * */
    QUByte(1),
    /**
     * 无符号 Integer
     * */
    QUInteger(2),
    /**
     * 无符号 Short
     * */
    QUShort(3),
    QULong(4),
    QBoolean(5),
    QString(6),
    QDouble(7),
    QFloat(8),
    QLong(9),
    QInteger(10),
    QShort(11),
    QArray(12),
    QByte(13),
    QByteString(14)
    ;
    private Integer plcVarType;
    PLCType(Integer type){
        this.plcVarType = type;
    }

    @Override
    public Integer getVarType() {
        return this.plcVarType;
    }

    /**
     * 用处：在写变量到 OPC-Server的时候，需要先把变量 类型转换一下 ( 转换的是非数组变量 )
     * 注意：1. 如果是ByteString  类型 那么传进来newValue 就是 “1,2,3,4,5,1,1,1”  type = ByteString；
     * 参数：传入旧的数据类型
     * 返回：返回新的数据类型
     * */
    @Override
    public Object convertType(Object oldType) {
        if(plcVarType == 1){
            return UByte.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 2){
            return UInteger.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 3){
            return UShort.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 4){
            return ULong.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 5){
            return Boolean.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 6){
            return String.valueOf(oldType);
        }else if(plcVarType == 7){
            return Double.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 8){
            return Float.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 9){
            return Long.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 10){
            return Integer.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 11){
            return Short.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 12){
            return convertArray(oldType);
        }else if(plcVarType == 13){
            return Byte.valueOf(String.valueOf(oldType));
        }else if(plcVarType == 14){
            // 如果知道是 ByteString 数据类型。那么传进来 是String 类型 用逗号隔开
            String s = (String)oldType;
            String[] split = s.split(",");
            byte[] u = new byte[split.length];
            for(int i=0;i<split.length;i++){
                u[i] = Byte.parseByte(split[i]);
            }

            ByteString b = ByteString.of(u);
            return b;
        }
        return null;
    }

    /**
     * 用处：在写变量到 OPC-Server的时候，需要先把变量 类型转换一下 ( 转换的是数组变量 )
     * 参数：传入旧的数据类型  例如 "7.1,7.1,7.1,7.1,7.1#QDouble"
     * 返回：返回新的数据类型  例如 [....] 合适数据类型
     * */
    @Override
    public Object[] convertArray(Object oldType) {

        //如果是数组 就先返回string。 注意：如果是数组 传入的是String 类型并且格式 是 “1,2,3|QUByte” 这样的。 注意后面QUByte 跟着的是数组里面变量的类型
        String s = (String)oldType;
        String[] all = s.split("#");
        String[] content = all[0].split(",");

        String type = all[1];
        Integer length = content.length;

        if(PLCTypeConstant.QUByte.equals(type)){
            UByte[] res = new UByte[length];
            for(int i=0;i<content.length;i++){
                res[i] = UByte.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QUInteger.equals(type)){
            UInteger[] res = new UInteger[length];
            for(int i=0;i<content.length;i++){
                res[i] = UInteger.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QUShort.equals(type)){
            UShort[] res = new UShort[length];
            for(int i=0;i<content.length;i++){
                res[i] = UShort.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QULong.equals(type)){
            ULong[] res = new ULong[length];
            for(int i=0;i<content.length;i++){
                res[i] = ULong.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QBoolean.equals(type)){
            Boolean[] res = new Boolean[length];
            for(int i=0;i<content.length;i++){
                res[i] = Boolean.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QString.equals(type)){
            String[] res = new String[length];
            for(int i=0;i<content.length;i++){
                res[i] = String.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QDouble.equals(type)){
            Double[] res = new Double[length];
            for(int i=0;i<content.length;i++){
                res[i] = Double.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QFloat.equals(type)){
            Float[] res = new Float[length];
            for(int i=0;i<content.length;i++){
                res[i] = Float.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QLong.equals(type)){
            Long[] res = new Long[length];
            for(int i=0;i<content.length;i++){
                res[i] = Long.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QInteger.equals(type)){
            Integer[] res = new Integer[length];
            for(int i=0;i<content.length;i++){
                res[i] = Integer.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QShort.equals(type)){
            Short[] res = new Short[length];
            for(int i=0;i<content.length;i++){
                res[i] = Short.valueOf(content[i]);
            }
            return res;
        }else if(PLCTypeConstant.QByte.equals(type)) {
            Byte[] res = new Byte[length];
            for(int i=0;i<content.length;i++){
                res[i] = Byte.valueOf(content[i]);
            }
            return res;
        }else {
            return null;
        }

        //return content;
    }
}
