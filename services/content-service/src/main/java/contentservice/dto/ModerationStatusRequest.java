package contentservice.dto;

/** AI 审核服务回写笔记状态（内网 HMAC，不经 Gateway）。 */
public class ModerationStatusRequest {

    /** 1=通过发布，3=审核拒绝（与 Note.STATUS_* 对齐） */
    private Integer status;
    private String reason;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
