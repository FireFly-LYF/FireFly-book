package contentservice.dto;

import contentservice.entity.Note;

import java.util.List;

public class NoteDetailResponse {
    private Note note;
    private List<String> mediaUrls;

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
}
