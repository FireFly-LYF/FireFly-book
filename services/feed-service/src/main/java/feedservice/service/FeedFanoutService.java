package feedservice.service;

import feedservice.client.ContentClient;
import feedservice.client.UserClient;
import feedservice.dto.FeedItem;
import feedservice.entity.FeedInbox;
import feedservice.mapper.FeedInboxMapper;
import feedservice.mq.NoteIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 写扩散：MySQL feed_inbox 为底，Redis 为热缓存。
 */
@Service
public class FeedFanoutService {

    private static final Logger log = LoggerFactory.getLogger(FeedFanoutService.class);
    private static final int INSERT_CHUNK = 200;

    private final FeedInboxMapper feedInboxMapper;
    private final TimelineCacheService timelineCache;
    private final UserClient userClient;
    private final ContentClient contentClient;
    private final int maxFanout;
    private final int backfillPerAuthor;

    public FeedFanoutService(
            FeedInboxMapper feedInboxMapper,
            TimelineCacheService timelineCache,
            UserClient userClient,
            ContentClient contentClient,
            @Value("${firefly.feed.max-fanout:2000}") int maxFanout,
            @Value("${firefly.feed.backfill-per-author:20}") int backfillPerAuthor) {
        this.feedInboxMapper = feedInboxMapper;
        this.timelineCache = timelineCache;
        this.userClient = userClient;
        this.contentClient = contentClient;
        this.maxFanout = Math.max(100, maxFanout);
        this.backfillPerAuthor = Math.max(1, Math.min(backfillPerAuthor, 50));
    }

    /** 发帖：推给粉丝 */
    @Transactional
    public void onNoteCreated(NoteIndexEvent event) {
        if (event == null || event.getId() == null || event.getUserId() == null) {
            return;
        }
        Long noteId = event.getId();
        Long authorId = event.getUserId();
        LocalDateTime createdAt = LocalDateTime.now();

        List<Long> fans = userClient.listFollowerIds(authorId);
        if (fans.isEmpty()) {
            log.debug("note.created 无粉丝 noteId={} authorId={}", noteId, authorId);
            return;
        }
        if (fans.size() > maxFanout) {
            log.warn("粉丝数 {} 超过 max-fanout={}，截断推送 noteId={}", fans.size(), maxFanout, noteId);
            fans = fans.subList(0, maxFanout);
        }

        List<FeedInbox> rows = new ArrayList<>(fans.size());
        for (Long fanId : fans) {
            FeedInbox row = new FeedInbox();
            row.setUserId(fanId);
            row.setNoteId(noteId);
            row.setAuthorId(authorId);
            row.setCreatedAt(createdAt);
            rows.add(row);
        }
        insertInChunks(rows);
        for (FeedInbox row : rows) {
            timelineCache.push(row.getUserId(), row.getNoteId(), row.getCreatedAt());
        }
        log.info("写扩散完成 noteId={} fans={}", noteId, fans.size());
    }

    /** 删帖：从所有 inbox + Redis 移除 */
    @Transactional
    public void onNoteDeleted(Long noteId) {
        if (noteId == null) {
            return;
        }
        List<Long> userIds = feedInboxMapper.listUserIdsByNoteId(noteId);
        feedInboxMapper.deleteByNoteId(noteId);
        if (userIds != null && !userIds.isEmpty()) {
            timelineCache.removeNoteForUsers(userIds, noteId);
        }
        log.info("已从 inbox/Redis 删除 noteId={} users={}", noteId, userIds != null ? userIds.size() : 0);
    }

    /** 新关注：回填作者最近笔记到粉丝时间线 */
    @Transactional
    public void onFollowCreated(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return;
        }
        List<FeedItem> latest = contentClient.listByUser(followeeId, 1, backfillPerAuthor);
        if (latest.isEmpty()) {
            return;
        }
        List<FeedInbox> rows = new ArrayList<>(latest.size());
        for (FeedItem item : latest) {
            if (item.getNoteId() == null) {
                continue;
            }
            FeedInbox row = new FeedInbox();
            row.setUserId(followerId);
            row.setNoteId(item.getNoteId());
            row.setAuthorId(followeeId);
            row.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return;
        }
        insertInChunks(rows);
        timelineCache.pushBatch(followerId, rows);
        log.info("关注回填完成 follower={} followee={} notes={}", followerId, followeeId, rows.size());
    }

    /** 取消关注：剔除该作者在粉丝时间线中的条目 */
    @Transactional
    public void onFollowDeleted(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return;
        }
        List<Long> noteIds = feedInboxMapper.listNoteIdsByUserAndAuthor(followerId, followeeId);
        feedInboxMapper.deleteByUserAndAuthor(followerId, followeeId);
        if (noteIds != null && !noteIds.isEmpty()) {
            timelineCache.removeNotes(followerId, noteIds);
        } else {
            timelineCache.invalidate(followerId);
        }
        log.info("取关清理完成 follower={} author={}", followerId, followeeId);
    }

    private void insertInChunks(List<FeedInbox> rows) {
        for (int i = 0; i < rows.size(); i += INSERT_CHUNK) {
            int end = Math.min(i + INSERT_CHUNK, rows.size());
            feedInboxMapper.insertBatch(rows.subList(i, end));
        }
    }
}
