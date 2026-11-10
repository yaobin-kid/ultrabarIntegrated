package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个卡片参数（args 中一项）的描述对象：声明该参数用什么控件、默认值、取值范围等。
 * <p>
 * 宿主按 {@link #type} 自动渲染配置表单，共 6 种类型，每种都有<b>专属 Builder</b>，
 * 只能设置该类型自己的字段（编译期隔离，无法交叉设置）：
 * <pre>
 * A2UIArgSpec.intArg().label("数量").defaultValue(3).min(1).max(99).build();
 * A2UIArgSpec.floatArg().label("比例").defaultValue(0.5).min(0.0).max(1.0).build();
 * A2UIArgSpec.boolArg().label("启用").defaultValue(true).build();
 * A2UIArgSpec.stringArg().label("标题").defaultValue("参数演示").build();
 * A2UIArgSpec.selectArg().label("等级").defaultValue("normal")
 *         .option("low", "低").option("normal", "中").build();
 * A2UIArgSpec.objectArg().label("嵌套参数组")
 *         .property("retries", A2UIArgSpec.intArg().defaultValue(3).min(0).max(10).build())
 *         .build();
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIArgSpec {

    /** 整数，宿主用 spinbox 渲染 */
    public static final String TYPE_INT = "int";
    /** 浮点数（宿主固定 3 位小数） */
    public static final String TYPE_FLOAT = "float";
    /** 布尔，宿主用复选框渲染 */
    public static final String TYPE_BOOL = "bool";
    /** 字符串，宿主用单行输入框渲染 */
    public static final String TYPE_STRING = "string";
    /** 下拉选择，插件收到选中项的 value */
    public static final String TYPE_SELECT = "select";
    /** 嵌套参数组，properties 为递归子 schema */
    public static final String TYPE_OBJECT = "object";

    /** 参数类型，取值见 TYPE_* 常量 */
    public String type;

    /** 表单字段标签；缺省用参数名。{@code @string/<key>} 前缀走宿主 i18n 资源 */
    public String label;

    /** 仅 UI 显示必填标记（红星），宿主不做强制校验 */
    public Boolean required;

    /** 表单预填值，插件侧也应以此作为缺省值。JSON 字段名为 {@code default} */
    @JsonProperty("default")
    public Object defaultValue;

    /** int / float 的最小值（spinbox 下限） */
    public Number min;

    /** int / float 的最大值（spinbox 上限） */
    public Number max;

    /**
     * select 的候选项。元素为 value 字符串（简写，值即标签），
     * 或 {@link Option} 对象（完整写法，value 与 label 分离）
     */
    public List<Object> options;

    /** object 类型的递归子参数（参数名 → 参数描述） */
    public Map<String, A2UIArgSpec> properties;

    /** 仅用于反序列化，请使用 6 种类型的 Builder 创建实例 */
    public A2UIArgSpec() {
    }

    private A2UIArgSpec(String type) {
        this.type = type;
    }

    /** 整数参数 → {@link IntArgBuilder} */
    public static IntArgBuilder intArg() {
        return new IntArgBuilder();
    }

    /** 浮点参数 → {@link FloatArgBuilder} */
    public static FloatArgBuilder floatArg() {
        return new FloatArgBuilder();
    }

    /** 布尔参数 → {@link BoolArgBuilder} */
    public static BoolArgBuilder boolArg() {
        return new BoolArgBuilder();
    }

    /** 字符串参数 → {@link StringArgBuilder} */
    public static StringArgBuilder stringArg() {
        return new StringArgBuilder();
    }

    /** 下拉选择参数 → {@link SelectArgBuilder} */
    public static SelectArgBuilder selectArg() {
        return new SelectArgBuilder();
    }

    /** 嵌套参数组 → {@link ObjectArgBuilder} */
    public static ObjectArgBuilder objectArg() {
        return new ObjectArgBuilder();
    }

    /**
     * select 的完整写法候选项：{@code {"value": "...", "label": "..."}}。
     * label 为空时序列化为仅含 value 的对象。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Option {
        public String value;
        public String label;

        public Option() {
        }

        Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** 参数名非空校验 */
    private static String requireNonEmpty(String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        return value;
    }

    /**
     * {@link #intArg()} 专属 Builder：只能设置 label / required / defaultValue / min / max
     */
    public static final class IntArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_INT);

        private IntArgBuilder() {
        }

        public IntArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public IntArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 默认值 */
        public IntArgBuilder defaultValue(int value) {
            spec.defaultValue = value;
            return this;
        }

        /** 最小值（不设置则下限为 Integer.MIN_VALUE） */
        public IntArgBuilder min(int min) {
            spec.min = min;
            return this;
        }

        /** 最大值（不设置则上限为 Integer.MAX_VALUE） */
        public IntArgBuilder max(int max) {
            spec.max = max;
            return this;
        }

        public A2UIArgSpec build() {
            if (spec.min != null && spec.max != null && spec.min.longValue() > spec.max.longValue()) {
                throw new IllegalArgumentException("min must not be greater than max");
            }
            return spec;
        }
    }

    /**
     * {@link #floatArg()} 专属 Builder：只能设置 label / required / defaultValue / min / max
     */
    public static final class FloatArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_FLOAT);

        private FloatArgBuilder() {
        }

        public FloatArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public FloatArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 默认值 */
        public FloatArgBuilder defaultValue(double value) {
            spec.defaultValue = value;
            return this;
        }

        /** 最小值 */
        public FloatArgBuilder min(double min) {
            spec.min = min;
            return this;
        }

        /** 最大值 */
        public FloatArgBuilder max(double max) {
            spec.max = max;
            return this;
        }

        public A2UIArgSpec build() {
            if (spec.min != null && spec.max != null && spec.min.doubleValue() > spec.max.doubleValue()) {
                throw new IllegalArgumentException("min must not be greater than max");
            }
            return spec;
        }
    }

    /**
     * {@link #boolArg()} 专属 Builder：只能设置 label / required / defaultValue
     */
    public static final class BoolArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_BOOL);

        private BoolArgBuilder() {
        }

        public BoolArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public BoolArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 默认值 */
        public BoolArgBuilder defaultValue(boolean value) {
            spec.defaultValue = value;
            return this;
        }

        public A2UIArgSpec build() {
            return spec;
        }
    }

    /**
     * {@link #stringArg()} 专属 Builder：只能设置 label / required / defaultValue
     */
    public static final class StringArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_STRING);

        private StringArgBuilder() {
        }

        public StringArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public StringArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 默认值 */
        public StringArgBuilder defaultValue(String value) {
            spec.defaultValue = value;
            return this;
        }

        public A2UIArgSpec build() {
            return spec;
        }
    }

    /**
     * {@link #selectArg()} 专属 Builder：只能设置 label / required / defaultValue / options
     */
    public static final class SelectArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_SELECT);

        private SelectArgBuilder() {
        }

        public SelectArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public SelectArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 默认选中的 option value */
        public SelectArgBuilder defaultValue(String value) {
            spec.defaultValue = value;
            return this;
        }

        /** 追加候选项（简写：值即标签） */
        public SelectArgBuilder option(String value) {
            return option(new Option(requireNonEmpty("option value", value), null));
        }

        /** 追加候选项（完整写法：value 与可翻译的 label） */
        public SelectArgBuilder option(String value, String label) {
            return option(new Option(requireNonEmpty("option value", value), label));
        }

        private SelectArgBuilder option(Option option) {
            if (spec.options == null) {
                spec.options = new ArrayList<Object>();
            }
            spec.options.add(option.label == null ? option.value : option);
            return this;
        }

        public A2UIArgSpec build() {
            if (spec.options == null || spec.options.isEmpty()) {
                throw new IllegalArgumentException("select arg requires at least one option");
            }
            return spec;
        }
    }

    /**
     * {@link #objectArg()} 专属 Builder：只能设置 label / required / properties（递归子参数）
     */
    public static final class ObjectArgBuilder {

        private final A2UIArgSpec spec = new A2UIArgSpec(TYPE_OBJECT);

        private ObjectArgBuilder() {
        }

        public ObjectArgBuilder label(String label) {
            spec.label = label;
            return this;
        }

        public ObjectArgBuilder required(boolean required) {
            spec.required = required;
            return this;
        }

        /** 追加一个递归子参数（子参数同样支持全部 6 种类型） */
        public ObjectArgBuilder property(String name, A2UIArgSpec child) {
            if (child == null) {
                throw new IllegalArgumentException("child arg spec must not be null");
            }
            if (spec.properties == null) {
                spec.properties = new LinkedHashMap<String, A2UIArgSpec>();
            }
            spec.properties.put(requireNonEmpty("property name", name), child);
            return this;
        }

        public A2UIArgSpec build() {
            if (spec.properties == null || spec.properties.isEmpty()) {
                throw new IllegalArgumentException("object arg requires at least one property");
            }
            return spec;
        }
    }
}
