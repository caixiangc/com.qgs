package opcua.selfunion.Enum;

public enum  PLCVar implements PLCVarEnum {
    /**
     * 命名空间为3  identify：Int32 的变量
     * */
    INT32_3(3,"Int32"),
    BYTE_3(3,"Byte"),
    Boolean_3(3,"Boolean"),
    BooleanArray_3(3,"BooleanArray"),
    ByteArray_3(3,"ByteArray"),
    StringArray_3(3,"StringArray"),
    Counter1_5(5,"Counter1"),
    Double_5(3,"Double"),
    Float_5(3,"Float"),
    String_5(3,"String"),
    UInteger_5(3,"UInteger"),
    UInt16_5(3,"UInt16"),
    Int64_5(3,"Int64"),
    Int32_5(3,"Int32"),
    Int16_5(3,"Int16"),

    RealPLC(3,"@LOCALSERVER.db1.0,b"),

    ;
    private Integer namespace;
    private String identifier;
    PLCVar(Integer namespace,String identifier){
        this.namespace = namespace;
        this.identifier = identifier;
    }

    @Override
    public Integer getNameSpace() {
        return this.namespace;
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }
}
