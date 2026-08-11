package feedservice.service;

import feedservice.client.ContentClient;
import feedservice.client.UserClient;
import feedservice.dto.FeedItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读扩散（pull）：打开 Feed 时查关注列表，再拉各作者最新笔记，内存合并排序。
 */
@Service
public class FeedService {

    private static final int PER_AUTHOR = 5;

    private final UserClient userClient;
    private final ContentClient contentClient;

    public FeedService(UserClient userClient, ContentClient contentClient) {
        this.userClient = userClient;
        this.contentClient = contentClient;
    }

    public List<FeedItem> followingFeed(Long userId, int size) {
        int limit = size <= 0 ? 20 : Math.min(size, 50);
        List<Long> followingIds = userClient.listFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return List.of();
        }

        List<FeedItem> merged = new ArrayList<>();
        for (Long authorId : followingIds) {
            merged.addAll(contentClient.listByUser(authorId, 1, PER_AUTHOR));
        }

        return merged.stream()
                .sorted(Comparator.comparing(
                        FeedItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}
