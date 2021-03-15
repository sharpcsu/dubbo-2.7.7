package org.apache.dubbo.metadata.definition.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 描述一个服务接口的定义
 */
public class ServiceDefinition implements Serializable {

    /**
     * 接口的完全限定名称
     */
    private String canonicalName;

    /**
     * 服务接口所在的完整路径
     */
    private String codeSource;

    /**
     * 接口中定义的全部方法描述信息。在 MethodDefinition 中记录了方法的名称、参数类型、返回值类型以及方法参数涉及的所有 TypeDefinition。
     */
    private List<MethodDefinition> methods;

    /**
     * 接口定义中涉及的全部类型描述信息，包括方法的参数和字段，如果遇到复杂类型，TypeDefinition 会递归获取复杂类型内部的字段。在 dubbo-metadata-api 模块中，
     * 提供了多种类型对应的 TypeBuilder 用于创建对应的 TypeDefinition，对于没有特定 TypeBuilder 实现的类型，会使用 DefaultTypeBuilder。
     */
    private List<TypeDefinition> types;

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getCodeSource() {
        return codeSource;
    }

    public List<MethodDefinition> getMethods() {
        if (methods == null) {
            methods = new ArrayList<>();
        }
        return methods;
    }

    public List<TypeDefinition> getTypes() {
        if (types == null) {
            types = new ArrayList<>();
        }
        return types;
    }

    public String getUniqueId() {
        return canonicalName + "@" + codeSource;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public void setCodeSource(String codeSource) {
        this.codeSource = codeSource;
    }

    public void setMethods(List<MethodDefinition> methods) {
        this.methods = methods;
    }

    public void setTypes(List<TypeDefinition> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        return "ServiceDefinition [canonicalName=" + canonicalName + ", codeSource=" + codeSource + ", methods="
                + methods + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceDefinition)) {
            return false;
        }
        ServiceDefinition that = (ServiceDefinition) o;
        return Objects.equals(getCanonicalName(), that.getCanonicalName()) &&
                Objects.equals(getCodeSource(), that.getCodeSource()) &&
                Objects.equals(getMethods(), that.getMethods()) &&
                Objects.equals(getTypes(), that.getTypes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCanonicalName(), getCodeSource(), getMethods(), getTypes());
    }
}
