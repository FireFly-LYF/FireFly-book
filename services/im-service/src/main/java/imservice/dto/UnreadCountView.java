package imservice.dto;

public class UnreadCountView {
    private int count;

    public UnreadCountView() {}

    public UnreadCountView(int count) {
        this.count = count;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
