package feedservice.service;

import feedservice.client.ContentClient;
import feedservice.client.UserClient;
import feedservice.dto.FeedItem;
import feedservice.entity.FeedInbox;
import feedservice.mapper.FeedInboxMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 读路径：Redis 热缓存 → MySQL inbox → 批量水合笔记；
 * inbox 为空时回退读扩散（兼容尚未写扩散的历史数据）。
 */
@Service
public class FeedService {

    private static final int PER_AUTHOR = 5;
    private static final int MAX_FOLLOWING_SCAN = 50;
    private static final int INBOX_WARM_SIZE = 200;

    private final UserClient userClient;
    private final ContentClient contentClient;
    private final FeedInboxMapper feedInboxMapper;
    private final TimelineCacheService timelineCache;

    public FeedService(
            UserClient userClient,
            ContentClient contentClient,
            FeedInboxMapper feedInboxMapper,
            TimelineCacheService timelineCache) {
        this.userClient = userClient;
        this.contentClient = contentClient;
        this.feedInboxMapper = feedInboxMapper;
        this.timelineCache = timelineCache;
    }

    public List<FeedItem> followingFeed(Long userId, int size) {
        int limit = size <= 0 ? 20 : Math.min(size, 50);

        List<Long> noteIds = timelineCache.latestNoteIds(userId, limit);
        if (noteIds.isEmpty()) {
            List<FeedInbox> fromDb = feedInboxMapper.listByUser(userId, Math.max(limit, INBOX_WARM_SIZE));
            if (fromDb != null && !fromDb.isEmpty()) {
                timelineCache.pushBatch(userId, fromDb);
                noteIds = fromDb.stream()
                        .map(inbox -> inbox.getNoteId())
                        .filter(id -> id != null)
                        .limit(limit)
                        .toList();
            }
        }

        if (!noteIds.isEmpty()) {
            List<FeedItem> items = contentClient.listByIds(noteIds);
            if (!items.isEmpty()) {
                return items.stream().limit(limit).toList();
            }
        }

        // 回退：读扩散（冷启动 / 尚无写扩散数据）
        return pullFallback(userId, limit);
    }

    private List<FeedItem> pullFallback(Long userId, int limit) {
        List<Long> followingIds = userClient.listFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return List.of();
        }
        if (followingIds.size() > MAX_FOLLOWING_SCAN) {
            followingIds = followingIds.subList(0, MAX_FOLLOWING_SCAN);
        }
        List<FeedItem> merged = contentClient.listLatestByUsers(followingIds, PER_AUTHOR);
        return merged.stream()
                .sorted(Comparator.comparing(
                        (FeedItem item) -> item.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}
