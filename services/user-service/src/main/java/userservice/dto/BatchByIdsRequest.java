package userservice.dto;

import java.util.List;

/** 按用户 id 批量查询摘要 */
public class BatchByIdsRequest {

    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
