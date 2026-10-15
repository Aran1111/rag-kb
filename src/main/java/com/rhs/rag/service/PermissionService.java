package com.rhs.rag.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.entity.KbPermission;
import com.rhs.rag.entity.KnowledgeBase;
import com.rhs.rag.exception.BizException;
import com.rhs.rag.mapper.KbPermissionMapper;
import com.rhs.rag.mapper.KnowledgeBaseMapper;
import com.rhs.rag.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final KbPermissionMapper permissionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 检查当前用户对知识库是否有读权限 */
    public void requireRead(Long kbId) {
        if (UserContext.isAdmin()) return;  // 管理员直接放行

        // 知识库是公开的——注意不能用 ifPresent + return，lambda 里的 return 只退出 lambda 本身
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb.getIsPublic()) return ;

        // 检查用户或部门权限
        String userId = String.valueOf(UserContext.getUserId());
        String deptId = UserContext.getDepartmentId();

        boolean e1 = permissionMapper.exists(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, "USER")
                .eq(KbPermission::getSubjectId, userId));
        boolean e2 = permissionMapper.exists(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, "DEPARTMENT")
                .eq(KbPermission::getSubjectId, deptId));

        boolean hasPermission = e1 || e2;
//        boolean hasPermission = permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(
//                kbId, "USER", userId)
//                || permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(
//                kbId, "DEPARTMENT", deptId);

        if (!hasPermission) {
            throw BizException.forbidden("无权访问该知识库");
        }
    }

    /** 检查当前用户对知识库是否有写权限 */
    public void requireWrite(Long kbId) {
        if (UserContext.isAdmin()) return;

        String userId = String.valueOf(UserContext.getUserId());
        String deptId = UserContext.getDepartmentId();

        boolean e1 = permissionMapper.exists(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, "USER")
                .eq(KbPermission::getSubjectId, userId)
                .in(KbPermission::getPermission, List.of("WRITE", "ADMIN")));
        boolean e2 = permissionMapper.exists(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, "DEPARTMENT")
                .eq(KbPermission::getSubjectId, deptId)
                .in(KbPermission::getPermission, List.of("WRITE", "ADMIN")));

        boolean hasWritePermission = e1 || e2;
//        boolean hasWritePermission = permissionRepository
//                .existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
//                        kbId, "USER", userId, java.util.List.of("WRITE", "ADMIN"))
//                || permissionRepository
//                .existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
//                        kbId, "DEPARTMENT", deptId, java.util.List.of("WRITE", "ADMIN"));

        if (!hasWritePermission) {
            throw BizException.forbidden("无文档管理权限");
        }
    }
}