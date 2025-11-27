import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Lightweight in-app notification banner so we can control color + icon.
 */
public final class NotificationPopup {

    private NotificationPopup() {}

    public static void show(Stage owner,
                            String title,
                            String message,
                            String color,
                            Image icon) {
        if (message == null || message.isEmpty()) {
            return;
        }

        HBox container = new HBox(10);
        container.setPadding(new Insets(12));
        container.setAlignment(Pos.CENTER_LEFT);

        String safeColor = (color == null || color.isEmpty()) ? "#3B82F6" : color;
        container.setStyle("-fx-background-color: " + safeColor + ";" +
                "-fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 18, 0.3, 0, 4);");

        if (icon != null) {
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(32);
            iv.setFitHeight(32);
            iv.setPreserveRatio(true);
            container.getChildren().add(iv);
        }

        VBox textBox = new VBox(4);
        Label titleLabel = new Label(title == null ? "Notification" : title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13;");

        Label bodyLabel = new Label(message);
        bodyLabel.setTextFill(Color.WHITE);
        bodyLabel.setStyle("-fx-font-size: 12; -fx-wrap-text: true;");
        bodyLabel.setMaxWidth(260);

        textBox.getChildren().addAll(titleLabel, bodyLabel);
        container.getChildren().add(textBox);

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setAutoHide(true);
        popup.getContent().add(container);

        double x = 40;
        double y = 40;
        if (owner != null) {
            x = owner.getX() + Math.max(20, owner.getWidth() - 320);
            y = owner.getY() + 60;
        }
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), container);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), container);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> popup.hide());
            fadeOut.play();
        });
        delay.play();
    }
}
