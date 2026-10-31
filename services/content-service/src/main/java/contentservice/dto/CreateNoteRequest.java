package contentservice.dto;

import java.util.List;

public class CreateNoteRequest {
    private String title;
    private String content;
    private String coverUrl;
    private List<String> mediaUrls;
    /** image | video */
    private String mediaKind;
    private Double lat;
    private Double lng;
    private String placeName;

    public String getTitle() {return title;}
    public void setTitle(String title) {this.title = title;}
    public String getContent() {return content;}
    public void setContent(String content) {this.content = content;}
    public String getCoverUrl() {return coverUrl;}
    public void setCoverUrl(String coverUrl) {this.coverUrl = coverUrl;}
    public List<String> getMediaUrls() {return mediaUrls;}
    public void setMediaUrls(List<String> mediaUrls) {this.mediaUrls = mediaUrls;}
    public String getMediaKind() {return mediaKind;}
    public void setMediaKind(String mediaKind) {this.mediaKind = mediaKind;}
    public Double getLat() {return lat;}
    public void setLat(Double lat) {this.lat = lat;}
    public Double getLng() {return lng;}
    public void setLng(Double lng) {this.lng = lng;}
    public String getPlaceName() {return placeName;}
    public void setPlaceName(String placeName) {this.placeName = placeName;}
}