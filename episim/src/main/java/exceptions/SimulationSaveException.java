package exceptions;

/**
 * Thrown when the simulation cannot be saved or loaded.
 *
 * Examples:
 *   - the save directory does not exist and cannot be created
 *   - the JSON file is malformed or missing expected fields
 *   - insufficient write permissions
 *
 * Wraps the underlying IOException so callers don't need to import java.io.
 */
public class SimulationSaveException extends Exception {

    /** Path that was being written to or read from. */
    private final String filePath;

    /**
     * @param filePath the path involved in the failed operation
     * @param message  human-readable explanation
     * @param cause    the underlying IOException or other cause
     */
    public SimulationSaveException(String filePath, String message, Throwable cause) {
        super(message + " (path: " + filePath + ")", cause);
        this.filePath = filePath;
    }

    /**
     * @param filePath the path involved in the failed operation
     * @param message  human-readable explanation
     */
    public SimulationSaveException(String filePath, String message) {
        super(message + " (path: " + filePath + ")");
        this.filePath = filePath;
    }

    /** @return the file path that caused this exception */
    public String getFilePath() { return filePath; }
}
