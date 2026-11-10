package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 热键上报：插件 → 服务端。
 * <p>
 * {@link #action} 只有 {@link #ACTION_DOWN} / {@link #ACTION_UP} 两种，
 * 每次热键调用至少产生一对 down + up 回调；
 * {@link #pressType} 区分短按与长按：
 * <ul>
 *   <li>{@link #PRESS_TYPE_SHORT}：短按。SDK 自动补齐 down+up，组合键 down 按按下顺序、up 按逆序</li>
 *   <li>{@link #PRESS_TYPE_LONG}：长按。插件显式调用 sendHotkeyDown / sendHotkeyUp；
 *       按住期间可重复发送同一键的 down 模拟键盘自动重复（如 PS 中 Ctrl+] 持续放大画笔）；
 *       down 后长时间未 up，SDK 会自动补发一次 up</li>
 * </ul>
 * keys 为<b>原始按键码</b>列表（与宿主平台一致——Windows 为 VK 虚拟键码，
 * 例如 CTRL=0x11，SHIFT=0x10，ALT=0x12，C=0x43，](VK_OEM_6)=0xDD）。
 * 组合键（如 Ctrl+C）作为一个整体放在同一条消息的 keys 中一次性发送；
 * 短按由 SDK 自动补齐一条 down + 一条 up（up 时 keys 逆序），长按由插件显式发送。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HotkeyPayload implements Payload {

    public static final String ACTION_DOWN = "down";
    public static final String ACTION_UP = "up";

    public static final String PRESS_TYPE_SHORT = "short";
    public static final String PRESS_TYPE_LONG = "long";

    /** 原始按键码列表（如 Windows VK 码），组合键放在同一列表中一次性发送 */
    public List<Integer> keys;

    /** down / up */
    public String action;

    /** short（短按）/ long（长按） */
    public String pressType;

    public Long timestamp;

    public HotkeyPayload() {
    }

    public HotkeyPayload(String action, String pressType, List<Integer> keys) {
        this.action = action;
        this.pressType = pressType;
        this.keys = keys;
        this.timestamp = System.currentTimeMillis();
    }
}
