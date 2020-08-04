package opcua.selfunion;

import java.util.ArrayList;
import java.util.List;

public class NodeIdKey {
    private String nodeName;
    private Integer namespace;
    private String identifier;
    private List<NodeIdKey> child;

    /**
     * type 类型 (就是Node)
     *     Unspecified(0),
     *
     *     Object(1),    //一般代表文件夹
     *
     *     Variable(2),  //一般代表变量
     *
     *     Method(4),    //一般代表方法
     *
     *     ObjectType(8),
     *
     *     VariableType(16),
     *
     *     ReferenceType(32),
     *
     *     DataType(64),
     *
     *     View(128);   //一般代表视图
     *
     * */
    private Integer nodeType;

    /**
     * 这个代表 Node节点里面存着的变量 的类型
     * */
    private String varType;

    public NodeIdKey(String nodeName,Integer nodeType,Integer namespace, String identifier,String varType) {
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.namespace = namespace;
        this.identifier = identifier;
        this.varType = varType;
        this.child = new ArrayList<>();
    }

    public Integer getNodeType() {
        return nodeType;
    }

    public void setNodeType(Integer nodeType) {
        this.nodeType = nodeType;
    }

    public String getVarType() {
        return varType;
    }

    public void setVarType(String varType) {
        this.varType = varType;
    }

    public List<NodeIdKey> getChild() {
        return child;
    }

    public void setChild(List<NodeIdKey> child) {
        this.child = child;
    }



    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getNamespace() {
        return namespace;
    }

    public void setNamespace(Integer namespace) {
        this.namespace = namespace;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
