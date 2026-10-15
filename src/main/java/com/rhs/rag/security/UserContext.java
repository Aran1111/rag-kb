package com.rhs.rag.security;

import lombok.Data;

public class UserContext {

    public static final ThreadLocal<Long> USER_ID = ThreadLocal.withInitial(() -> 1L);
    public static final ThreadLocal<String> DEPARTMENT_ID = ThreadLocal.withInitial(() -> "default");
    public static final ThreadLocal<String> ROLE = ThreadLocal.withInitial(() -> "admin");

    public static void set(Long userId, String departmentId, String role){
        USER_ID.set(userId);
        DEPARTMENT_ID.set(departmentId);
        ROLE.set(role);
    }

    public static Long getUserId(){
        return USER_ID.get();
    }

    public static String getDepartmentId(){
        return DEPARTMENT_ID.get();
    }
    public static String getRole(){
        return ROLE.get();
    }

    public static boolean isAdmin(){
        return "ADMIN".equals(ROLE.get());
    }

    public static void clear(){
        USER_ID.remove();
        DEPARTMENT_ID.remove();
        ROLE.remove();
    }



    @Data
    public static class Userinfo{
        private Long userId;
        private String username;
        private String role;
        private Long departmentId;


    }
}
