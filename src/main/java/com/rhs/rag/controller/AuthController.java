package com.rhs.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rhs.rag.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证授权接口
 */
@Tag(name = "认证授权", description = "用户登录、登出接口，基于Sa-Token的认证体系")
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    // 演示用用户数据（生产中从数据库查）
    private static final Map<String, UserProfile> DEMO_USERS = Map.of(
            "hr001",   new UserProfile(1L, "HR",   "MEMBER"),
            "tech001", new UserProfile(2L, "TECH", "MEMBER"),
            "admin",   new UserProfile(3L, "ALL",  "ADMIN")
    );

    @Operation(
        summary = "用户登录",
        description = "用户登录接口，返回Sa-Token令牌。当前为演示版本，用户名密码固定。\n\n" +
                     "**演示账号**:\n" +
                     "- hr001 / demo123 (HR部门成员)\n" +
                     "- tech001 / demo123 (技术部门成员)\n" +
                     "- admin / demo123 (管理员)"
    )
    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest req) {
        UserProfile profile = DEMO_USERS.get(req.getUsername());
        if (profile == null || !"demo123".equals(req.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        StpUtil.login(profile.userId());    //生成 Token、绑定 userId、创建 Account-Session、把 Token 写入 Cookie/Header 下发给客户端
        // 把用户附加信息存入 Session，Interceptor 通过 StpUtil.getSession().get() 读取
        StpUtil.getSession().set("departmentId", profile.departmentId());
        StpUtil.getSession().set("role", profile.role());

        String token = StpUtil.getTokenValue();
        log.info("[Auth] 登录成功：username={}，userId={}，dept={}",
                req.getUsername(), profile.userId(), profile.departmentId());

        return ApiResponse.ok(token);
    }

    @Operation(
        summary = "用户登出",
        description = "用户登出接口，销毁当前会话和Token"
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();   // 会删除 Token 与 userId 的绑定、销毁 Account-Session、从 Cookie 中删除 Token，当前请求立刻变为未登录状态；后续请求若携带旧 Token 会被判定为无效，返回 401
        return ApiResponse.ok(null);
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "登录请求参数")
    static class LoginRequest {
        
        @io.swagger.v3.oas.annotations.media.Schema(
            description = "用户名", 
            example = "admin",
            allowableValues = {"hr001", "tech001", "admin"},
            required = true
        )
        private String username;
        
        @io.swagger.v3.oas.annotations.media.Schema(
            description = "密码", 
            example = "demo123",
            required = true
        )
        private String password;
    }

    record UserProfile(Long userId, String departmentId, String role) {}
}