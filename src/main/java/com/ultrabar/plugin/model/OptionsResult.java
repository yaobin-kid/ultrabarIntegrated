package com.ultrabar.plugin.model;

import java.util.List;
import java.util.Map;

public class OptionsResult {
    public Boolean success;
    public Map<String, Object> details;
    public ErrorInfo error;



    public List<Item> items; //返回的参数
    public boolean hashMore; //是否有分页
    public String nextCursor; //下一页
}
