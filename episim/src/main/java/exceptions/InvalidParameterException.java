package exceptions;

/**
 * Thrown when a simulation parameter (rate, count, speed…) is outside
 * its valid range.
 *
 * Examples:
 *   - transmission rate set to 1.5  (must be in [0.0, 1.0])
 *   - injection count set to -10    (must be > 0)
 *   - simulation speed set to 0     (must be ≥ 0.1)
 */
public class InvalidParameterException extends RuntimeException {

    /** The name of the parameter that failed validation. */
    private final String parameterName;

    /** The offending value (as String for any type). */
    private final String offendingValue;

    /**
     * @param parameterName  name of the invalid parameter (e.g. "transmissionRate")
     * @param offendingValue the bad value (e.g. "1.5")
     * @param message        human-readable explanation
     */
    public InvalidParameterException(String parameterName,
                                     String offendingValue,
                                     String message) {
        super(message);
        this.parameterName  = parameterName;
        this.offendingValue = offendingValue;
    }

    /** @return name of the parameter that caused this exception */
    public String getParameterName()  { return parameterName; }

    /** @return string representation of the offending value */
    public String getOffendingValue() { return offendingValue; }
}
