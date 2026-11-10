package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡片参数 schema（对应宿主 manifest 中的 {@code args} 字段）。
 * <p>
 * 线上格式是一个扁平的对象：{@code 参数名 → A2UIArgSpec}，
 * 并可选地混入两个保留字段 {@code dynamic} / {@code schemaGetter}（动态 schema）。
 * <pre>
 * // 静态 schema
 * A2UIArgs.create()
 *         .arg("title", A2UIArgSpec.stringArg().label("标题").defaultValue("参数演示").build())
 *         .arg("count", A2UIArgSpec.intArg().label("数量").defaultValue(3).min(1).max(99).build());
 *
 * // 动态 schema：渲染表单前先调插件的 schemaGetter 动作拿实时 schema
 * A2UIArgs.dynamic("schema.argsdemo");
 *
 * // 动静态共存：固定字段先渲染，动态字段由 getter 返回的 schema 合并追加
 * A2UIArgs.dynamic("schema.argsdemo")
 *         .arg("title", A2UIArgSpec.stringArg().defaultValue("标题").build());
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIArgs extends LinkedHashMap<String, Object> {

    /** 保留字段：true 表示 schema 不固定，渲染前先调 schemaGetter 取 */
    public static final String KEY_DYNAMIC = "dynamic";

    /** 保留字段：指向本插件内另一个 action 的 id（通常在 actions 中声明为 internal） */
    public static final String KEY_SCHEMA_GETTER = "schemaGetter";

    public A2UIArgs() {
    }

    /** 创建空的静态参数 schema */
    public static A2UIArgs create() {
        return new A2UIArgs();
    }

    /** 创建动态参数 schema，{@code schemaGetter} 为插件内提供 schema 的 action id */
    public static A2UIArgs dynamic(String schemaGetter) {
        return new A2UIArgs().dynamic(true).schemaGetter(schemaGetter);
    }

    /** 追加/覆盖一个参数描述 */
    public A2UIArgs arg(String name, A2UIArgSpec spec) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("arg name must not be empty");
        }
        if (spec == null) {
            throw new IllegalArgumentException("arg spec must not be null");
        }
        if (KEY_DYNAMIC.equals(name) || KEY_SCHEMA_GETTER.equals(name)) {
            throw new IllegalArgumentException(name + " is a reserved key");
        }
        put(name, spec);
        return this;
    }

    /** 标记为动态 schema */
    public A2UIArgs dynamic(boolean dynamic) {
        put(KEY_DYNAMIC, dynamic);
        return this;
    }

    /** 指定提供动态 schema 的 action id */
    public A2UIArgs schemaGetter(String schemaGetter) {
        if (schemaGetter == null || schemaGetter.isEmpty()) {
            throw new IllegalArgumentException("schemaGetter must not be empty");
        }
        put(KEY_SCHEMA_GETTER, schemaGetter);
        return this;
    }

    /** 是否为动态 schema */
    public boolean isDynamic() {
        return Boolean.TRUE.equals(get(KEY_DYNAMIC));
    }

    /** 动态 schema 对应的 action id；非动态时返回 null */
    public String schemaGetter() {
        Object value = get(KEY_SCHEMA_GETTER);
        return value == null ? null : String.valueOf(value);
    }

    /** 仅取静态参数描述（剔除保留字段） */
    public Map<String, A2UIArgSpec> specs() {
        Map<String, A2UIArgSpec> specs = new LinkedHashMap<String, A2UIArgSpec>();
        for (Map.Entry<String, Object> entry : entrySet()) {
            if (KEY_DYNAMIC.equals(entry.getKey()) || KEY_SCHEMA_GETTER.equals(entry.getKey())) {
                continue;
            }
            A2UIArgSpec spec = asSpec(entry.getValue());
            if (spec != null) {
                specs.put(entry.getKey(), spec);
            }
        }
        return specs;
    }

    /** 静态参数名列表（剔除保留字段） */
    public List<String> argNames() {
        return new ArrayList<String>(specs().keySet());
    }

    /**
     * 取一个参数描述；反序列化后普通 Map 会自动转换为 {@link A2UIArgSpec}。
     */
    private static A2UIArgSpec asSpec(Object value) {
        if (value instanceof A2UIArgSpec) {
            return (A2UIArgSpec) value;
        }
        if (value instanceof Map) {
            return Json.mapper().convertValue(value, A2UIArgSpec.class);
        }
        return null;
    }
}
