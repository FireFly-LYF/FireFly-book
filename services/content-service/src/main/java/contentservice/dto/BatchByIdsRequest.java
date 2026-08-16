package contentservice.dto;

import java.util.List;

/** 按笔记 id 批量查询（Feed 时间线水合） */
public class BatchByIdsRequest {

    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
