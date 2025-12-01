import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * In-app notification popup that appears in the corner of the application
 * window.
 * Shows a colored banner with an icon, title, and message.
 * 
 * This is NOT a macOS system notification - those are created separately
 * and use the system's styling (colors cannot be customized due to OS
 * limitations).
 */
public final class NotificationPopup {

    private static Popup activePopup;
    private static PauseTransition activeDelay;
    private static FadeTransition activeFadeOut;

    // Private constructor - this class only has static methods
    private NotificationPopup() {
    }

    /**
     * Display a notification popup in the application window.
     * The popup appears near the owner window, fades in, stays for 5 seconds, then
     * fades out.
     * 
     * @param owner   The stage that owns this popup (can be null, but popup won't
     *                show)
     * @param title   The notification title
     * @param message The notification message text
     * @param color   The background color (CSS color string like "#FF5252")
     * @param icon    Optional icon image (can be null)
     */
    public static void show(Stage owner, String title, String message, String color, Image icon) {
        // Don't show empty notifications
        if (message == null || message.isEmpty()) {
            return;
        }

        // Can't show popup without an owner window
        if (owner == null) {
            System.err.println("NotificationPopup: Owner window is null, cannot show popup.");
            return;
        }

        // Make sure any previous popup is cleared so notifications never overlap
        hideActivePopup();

        // Create the main container
        HBox container = new HBox(10);
        container.setPadding(new Insets(12));
        container.setAlignment(Pos.CENTER_LEFT);

        // Use provided color, or default to blue if not specified
        String backgroundColor = color;
        if (backgroundColor == null || backgroundColor.isEmpty()) {
            backgroundColor = "#3B82F6"; // Default blue
        }

        // Style the container with rounded corners and shadow
        container.setStyle("-fx-background-color: " + backgroundColor + ";" +
                "-fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 18, 0.3, 0, 4);");

        // Add icon if provided
        if (icon != null) {
            ImageView iconView = new ImageView(icon);
            iconView.setFitWidth(32);
            iconView.setFitHeight(32);
            iconView.setPreserveRatio(true);
            container.getChildren().add(iconView);
        }

        // Create text container
        VBox textBox = new VBox(4);

        // Create title label
        String titleText = title;
        if (titleText == null) {
            titleText = "Notification";
        }
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");

        // Create message label with white text
        Label bodyLabel = new Label(message);
        bodyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12; -fx-wrap-text: true;");
        bodyLabel.setMaxWidth(260);

        // Add labels to text container
        textBox.getChildren().add(titleLabel);
        textBox.getChildren().add(bodyLabel);
        container.getChildren().add(textBox);

        // Create the popup window
        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setAutoHide(true);
        popup.getContent().add(container);

        activePopup = popup;

        // Position popup in top-right corner of owner window
        double x = owner.getX() + Math.max(20, owner.getWidth() - 320);
        double y = owner.getY() + 60;
        popup.show(owner, x, y);

        popup.setOnHidden(event -> {
            if (activePopup == popup) {
                activePopup = null;
                activeDelay = null;
                activeFadeOut = null;
            }
        });

        // Fade in animation
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), container);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Auto-hide after 5 seconds with fade out
        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        activeDelay = delay;
        delay.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), container);
            activeFadeOut = fadeOut;
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(fadeEvent -> {
                popup.hide();
                if (activePopup == popup) {
                    activePopup = null;
                    activeDelay = null;
                    activeFadeOut = null;
                }
            });
            fadeOut.play();
        });
        delay.play();
    }

    private static void hideActivePopup() {
        if (activeDelay != null) {
            activeDelay.stop();
            activeDelay = null;
        }
        if (activeFadeOut != null) {
            activeFadeOut.stop();
            activeFadeOut = null;
        }
        if (activePopup != null) {
            activePopup.hide();
            activePopup = null;
        }
    }
}
