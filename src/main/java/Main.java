import javafx.application.Application;

/**
 * Application entry point for Taptic Desktop.
 * 
 * This is the main class that Java runs when you start the application.
 * All it does is launch the JavaFX application (TapticFxApp).
 * 
 * The actual UI setup and initialization happens in TapticFxApp.java.
 */
public class Main {

    /**
     * Main entry point called by the Java runtime.
     * 
     * @param args Command line arguments (not currently used)
     */
    public static void main(String[] args) {
        // Launch the JavaFX application
        // This will create an instance of TapticFxApp and call its start() method
        Application.launch(TapticFxApp.class, args);
    }
}
