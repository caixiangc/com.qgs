package opcua.selfunion.Enum;

public interface PLCTypeEnum {
    Integer getVarType();
    Object convertType(Object oldType);
    Object[] convertArray(Object oldType);
}
