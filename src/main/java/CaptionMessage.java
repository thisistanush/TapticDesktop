/**
 * Simple data class to hold a caption message for speech-to-text display.
 * Contains who spoke and what they said.
 */
public class CaptionMessage {

    private final String speaker; // "You" or "Them"
    private final String text; // What was said

    /**
     * Create a caption message.
     * 
     * @param speaker Who spoke ("You" or "Them")
     * @param text    What was said
     */
    public CaptionMessage(String speaker, String text) {
        this.speaker = speaker;
        this.text = text;
    }

    /**
     * Get who spoke.
     * 
     * @return "You" or "Them"
     */
    public String getSpeaker() {
        return speaker;
    }

    /**
     * Get what was said.
     * 
     * @return The spoken text
     */
    public String getText() {
        return text;
    }
}
