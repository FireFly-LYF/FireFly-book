package contentservice.dto;

import java.util.List;

/** Feed 读扩散：一次拉多个作者的最新笔记 */
public class BatchLatestByUsersRequest {

    private List<Long> userIds;
    /** 每位作者最多条数，默认 5，上限 20 */
    private Integer perUser;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }

    public Integer getPerUser() {
        return perUser;
    }

    public void setPerUser(Integer perUser) {
        this.perUser = perUser;
    }
}
