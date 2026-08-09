package notifyservice.dto;

import java.util.List;

public class ReadNotifyRequest {
    /** true = 当前用户全部已读 */
    private Boolean all;
    /** 指定通知 id 列表 */
    private List<Long> ids;

    public Boolean getAll() { return all; }
    public void setAll(Boolean all) { this.all = all; }
    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
