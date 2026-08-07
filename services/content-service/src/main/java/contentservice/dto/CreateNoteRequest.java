package contentservice.dto;

import java.util.List;

public class CreateNoteRequest {
    private String title;
    private String content;
    private String coverUrl;
    private List<String> mediaUrls;
    // getter/setter
    public String getTitle() {return title;}
    public void setTitle(String title) {this.title = title;}
    public String getContent() {return content;}
    public void setContent(String content) {this.content = content;}
    public String getCoverUrl() {return coverUrl;}
    public void setCoverUrl(String coverUrl) {this.coverUrl = coverUrl;}
    public List<String> getMediaUrls() {return mediaUrls;}
    public void setMediaUrls(List<String> mediaUrls) {this.mediaUrls = mediaUrls;}
}