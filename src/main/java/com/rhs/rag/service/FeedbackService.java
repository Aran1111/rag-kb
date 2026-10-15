package com.rhs.rag.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jichi.ragkb.entity.ChatMessage;
import com.rhs.rag.entity.AnswerFeedback;
import com.rhs.rag.exception.BizException;
import com.rhs.rag.mapper.AnswerFeedbackMapper;
import com.rhs.rag.mapper.ChatMessageMapper;
import com.rhs.rag.mapper.EvalDatasetMapper;
import com.rhs.rag.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final AnswerFeedbackMapper answerFeedbackMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final EvalDatasetMapper evalDatasetMapper;

    /**
     * 提交用户反馈（点赞/点踩）。
     * 对于差评，自动提取问题加入候选评估数据集（人工审核后正式纳入）。
     */
    @Transactional
    public void submitFeedback(Long messageId, int feedback, String comment) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null){
            throw new BizException(404,"消息不存在");
        }

        // 同一用户对同一消息只保留一条反馈，重复提交则覆盖
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<AnswerFeedback> wrapper = new LambdaQueryWrapper<AnswerFeedback>()
                .eq(AnswerFeedback::getMessageId, messageId)
                .eq(AnswerFeedback::getUserId, userId);

        AnswerFeedback fb = answerFeedbackMapper.selectOne(wrapper);
        if (fb == null) {
            fb = new AnswerFeedback();
            fb.setMessageId(messageId);
            fb.setUserId(userId);
        }
        fb.setFeedback((short) feedback);
        fb.setComment(comment);
        answerFeedbackMapper.updateById(fb);

        // 更新消息的 feedback 字段
        message.setFeedback((short) feedback);
        chatMessageMapper.updateById(message);

        // 差评：把这个问题加入评估候选（人工审核后加入正式评估集）
        if (feedback == -1) {
            log.info("[Feedback] 差评记录，候选加入评估集：messageId={}", messageId);
            // 这里只记录日志，实际需要一个管理后台人工审核后才加入 eval_dataset
        }

        log.info("[Feedback] 反馈已记录：messageId={}，feedback={}，userId={}",
                messageId, feedback, UserContext.getUserId());
    }
}