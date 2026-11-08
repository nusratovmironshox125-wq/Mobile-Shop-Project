package sayt.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class RateLimits {
    private record Window(int count, Instant expires) {}

    private final Cache<String, Window> windows = Caffeine.newBuilder().maximumSize(100_000)
        .expireAfterWrite(Duration.ofHours(1)).build();

    public void check(String key, int maximum) {
        Window current = windows.getIfPresent(key);
        if (current != null && current.count() >= maximum && current.expires().isAfter(Instant.now())) {
            throw Problem.limited((int) Duration.between(Instant.now(), current.expires()).toSeconds() + 1);
        }
    }

    public void record(String key, Duration duration) {
        var now = Instant.now();
        windows.asMap().compute(key, (ignored, current) ->
            current == null || !current.expires().isAfter(now)
                ? new Window(1, now.plus(duration)) : new Window(current.count() + 1, current.expires()));
    }

    public void acquire(String key, int maximum, Duration duration) {
        var now = Instant.now();
        windows.asMap().compute(key, (ignored, current) -> {
            if (current == null || !current.expires().isAfter(now)) return new Window(1, now.plus(duration));
            if (current.count() >= maximum) {
                throw Problem.limited((int) Duration.between(now, current.expires()).toSeconds() + 1);
            }
            return new Window(current.count() + 1, current.expires());
        });
    }

    public void clear(String key) {
        windows.invalidate(key);
    }
}
