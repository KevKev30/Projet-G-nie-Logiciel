package exceptions;

/**
 * Thrown when a City object reaches an epidemiologically inconsistent state.
 *
 * Examples:
 *   - safe population becomes negative after an injection
 *   - trying to inject cases into a city with zero safe inhabitants
 *   - SEIR totals don't add up after a simulation step
 *
 * The simulation engine catches this exception per-city so one bad city
 * never stops the entire simulation loop.
 */
public class CityStateException extends RuntimeException {

    /** Name of the city that triggered the error. */
    private final String cityName;

    /**
     * @param cityName name of the city in the bad state
     * @param message  description of what went wrong
     */
    public CityStateException(String cityName, String message) {
        super("[" + cityName + "] " + message);
        this.cityName = cityName;
    }

    /**
     * @param cityName name of the city in the bad state
     * @param message  description of what went wrong
     * @param cause    underlying exception (e.g. an IllegalArgumentException)
     */
    public CityStateException(String cityName, String message, Throwable cause) {
        super("[" + cityName + "] " + message, cause);
        this.cityName = cityName;
    }

    /** @return name of the city that triggered this exception */
    public String getCityName() { return cityName; }
}
