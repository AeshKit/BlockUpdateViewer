package aesh.kai.client.log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ThisTick {
    private static Long thisTick = null;

    public static void update(long tick) {
        thisTick = tick;
    }

    public static Optional<Long> get() {
        return Optional.ofNullable(thisTick);
    }
}