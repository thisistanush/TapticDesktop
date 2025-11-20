public class CaptionMessage {

    private final String speaker; // "You" or "Them"
    private final String text;

    public CaptionMessage(String speaker, String text) {
        this.speaker = speaker;
        this.text = text;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }
}
