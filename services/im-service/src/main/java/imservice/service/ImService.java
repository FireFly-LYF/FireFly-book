package imservice.service;

import imservice.dto.ConversationView;
import imservice.dto.MessageView;
import imservice.dto.UnreadCountView;
import imservice.entity.Conversation;
import imservice.entity.ConversationMember;
import imservice.entity.ImMessage;
import imservice.mapper.ConversationMapper;
import imservice.mapper.ConversationMemberMapper;
import imservice.mapper.ImMessageMapper;
import imservice.mq.MqConstants;
import imservice.mq.NotifyPayload;
import imservice.mq.OutboxRelay;
import imservice.mq.OutboxService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final OutboxService outboxService;
    private final OutboxRelay outboxRelay;

    public ImService(
            ConversationMapper conversationMapper,
            ConversationMemberMapper memberMapper,
            ImMessageMapper messageMapper,
            OutboxService outboxService,
            OutboxRelay outboxRelay) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.outboxService = outboxService;
        this.outboxRelay = outboxRelay;
    }

    @Transactional
    public ConversationView openDm(Long userId, Long peerUserId) {
        requireLogin(userId);
        if (peerUserId == null || peerUserId <= 0) {
            throw new IllegalArgumentException("peerUserId 无效");
        }
        if (userId.equals(peerUserId)) {
            throw new IllegalArgumentException("不能给自己发私信");
        }
        Conversation c = ensureDm(userId, peerUserId);
        return toView(c, userId);
    }

    public List<ConversationView> listConversations(Long userId, int limit) {
        requireLogin(userId);
        int lim = Math.min(Math.max(limit, 1), 100);
        List<Conversation> rows = conversationMapper.listByUser(userId, lim);
        List<ConversationView> out = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            out.add(toView(c, userId));
        }
        return out;
    }

    public List<MessageView> listMessages(Long userId, Long conversationId, Long afterId, int limit) {
        requireLogin(userId);
        requireMember(conversationId, userId);
        int lim = Math.min(Math.max(limit, 1), 100);
        List<ImMessage> rows;
        if (afterId != null && afterId > 0) {
            rows = messageMapper.listAfter(conversationId, afterId, lim);
        } else {
            rows = messageMapper.listLatest(conversationId, lim);
        }
        List<MessageView> out = new ArrayList<>(rows.size());
        for (ImMessage m : rows) {
            out.add(toMessageView(m));
        }
        return out;
    }

    @Transactional
    public MessageView send(Long userId, Long conversationId, String content, String idemKey) {
        requireLogin(userId);
        requireMember(conversationId, userId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        String text = content.trim();
        if (text.length() > 1000) {
            throw new IllegalArgumentException("消息不能超过 1000 字");
        }
        String key = normalizeIdem(idemKey);
        if (key != null) {
            ImMessage existing = messageMapper.findByIdem(conversationId, key);
            if (existing != null) {
                return toMessageView(existing);
            }
        }

        ImMessage msg = new ImMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(userId);
        msg.setContent(text);
        msg.setIdemKey(key);
        try {
            messageMapper.insert(msg);
        } catch (DuplicateKeyException e) {
            ImMessage again = key == null ? null : messageMapper.findByIdem(conversationId, key);
            if (again != null) {
                return toMessageView(again);
            }
            throw e;
        }

        String preview = text.length() <= 120 ? text : text.substring(0, 120);
        conversationMapper.updateLastMessage(conversationId, msg.getId(), preview, userId);

        Long peerId = memberMapper.findPeerUserId(conversationId, userId);
        if (peerId != null) {
            ConversationMember peer = memberMapper.find(conversationId, peerId);
            int prevUnread = peer == null || peer.getUnreadCount() == null ? 0 : peer.getUnreadCount();
            memberMapper.incrUnread(conversationId, peerId);
            // 仅从「已读清零」到首条未读时写一条通知，避免每条私信刷屏通知列表
            if (prevUnread <= 0) {
                NotifyPayload payload = new NotifyPayload();
                payload.setUserId(peerId);
                payload.setFromUserId(userId);
                payload.setType("IM");
                payload.setRefId(conversationId);
                payload.setContent(preview.length() <= 256 ? preview : preview.substring(0, 256));
                outboxService.enqueue(MqConstants.RK_MESSAGE_CREATED, msg.getId(), payload);
                kickOutboxAfterCommit();
            }
        }

        return toMessageView(msg);
    }

    @Transactional
    public void markRead(Long userId, Long conversationId, Long messageId) {
        requireLogin(userId);
        requireMember(conversationId, userId);
        long mid = messageId == null ? 0L : messageId;
        if (mid <= 0) {
            Conversation c = conversationMapper.findById(conversationId);
            if (c != null && c.getLastMessageId() != null) {
                mid = c.getLastMessageId();
            }
        }
        memberMapper.markRead(conversationId, userId, mid);
    }

    public UnreadCountView unreadCount(Long userId) {
        requireLogin(userId);
        return new UnreadCountView(memberMapper.sumUnread(userId));
    }

    private Conversation ensureDm(Long a, Long b) {
        String dmKey = dmKey(a, b);
        Conversation existing = conversationMapper.findByDmKey(dmKey);
        if (existing != null) {
            return existing;
        }
        Conversation c = new Conversation();
        c.setType("DM");
        c.setDmKey(dmKey);
        try {
            conversationMapper.insert(c);
        } catch (DuplicateKeyException e) {
            Conversation again = conversationMapper.findByDmKey(dmKey);
            if (again != null) {
                return again;
            }
            throw e;
        }
        ConversationMember m1 = new ConversationMember();
        m1.setConversationId(c.getId());
        m1.setUserId(a);
        memberMapper.insert(m1);
        ConversationMember m2 = new ConversationMember();
        m2.setConversationId(c.getId());
        m2.setUserId(b);
        memberMapper.insert(m2);
        return c;
    }

    private ConversationView toView(Conversation c, Long userId) {
        ConversationView v = new ConversationView();
        v.setId(c.getId());
        v.setPeerUserId(memberMapper.findPeerUserId(c.getId(), userId));
        v.setLastPreview(c.getLastPreview());
        v.setLastSenderId(c.getLastSenderId());
        v.setLastMessageAt(c.getLastMessageAt());
        ConversationMember me = memberMapper.find(c.getId(), userId);
        v.setUnreadCount(me == null || me.getUnreadCount() == null ? 0 : me.getUnreadCount());
        return v;
    }

    private static MessageView toMessageView(ImMessage m) {
        MessageView v = new MessageView();
        v.setId(m.getId());
        v.setConversationId(m.getConversationId());
        v.setSenderId(m.getSenderId());
        v.setContent(m.getContent());
        v.setCreatedAt(m.getCreatedAt());
        return v;
    }

    private void requireMember(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        ConversationMember m = memberMapper.find(conversationId, userId);
        if (m == null) {
            throw new IllegalArgumentException("无权访问该会话");
        }
    }

    private void kickOutboxAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            outboxRelay.relayBatch();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxRelay.relayBatch();
            }
        });
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
    }

    private static String dmKey(Long a, Long b) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return min + ":" + max;
    }

    private static String normalizeIdem(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String t = key.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
