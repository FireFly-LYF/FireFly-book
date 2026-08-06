package contentservice.dto;

public class CreateNoteRequest {
    private String title;
    private String content;
    private String coverUrl;
    // getter/setter
    public String getTitle() {return title;}
    public void setTitle(String title) {this.title = title;}
    public String getContent() {return content;}
    public void setContent(String content) {this.content = content;}
    public String getCoverUrl() {return coverUrl;}
    public void setCoverUrl(String coverUrl) {this.coverUrl = coverUrl;}
}