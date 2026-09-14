package com.wueasy.waymark.model;

import java.util.List;
import java.util.Map;

/**
 * 用户资料、角色与命名空间权限。
 */
public class UserProfile {

    public long id;

    public String username;

    public String nickname;

    public int status;

    public boolean isAdmin;

    public List<RoleBrief> roles;

    public Map<String, String> permissions;
}
