package com.rhs.rag.service.loader;

import com.rhs.rag.security.UserContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class IndexTaskLauncher {

    /**
     * 为了不在 IndexService 里自调用导致AOP代理失效，导致异步注解失效，所以IndexTaskLauncher单独抽出来
     */
    private final IndexService indexService;

    public IndexTaskLauncher(@Lazy IndexService indexService) {
        this.indexService = indexService;
    }

    @Async("indexTaskExecutor")
    public void launchFromMinio(Long taskId, Long docId, Long userId, String departmentId, String role) {
        UserContext.set(userId,departmentId,role);
        try {
            indexService.executeFromMinio(taskId, docId);
        }finally {
            UserContext.clear();
        }
    }

    @Async("indexTaskExecutor")
    public void launchWithText(Long taskId, Long docId, String textContent, Long userId, String departmentId, String role) {
        UserContext.set(userId,departmentId,role);
        try {
            indexService.executeWithText(taskId, docId, textContent);
        }finally {
            UserContext.clear();
        }
    }
}