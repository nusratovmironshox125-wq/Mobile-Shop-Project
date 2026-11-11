package sayt.dto.notification;

import java.util.List;
import java.util.Map;

public record NotificationQueueResponse(boolean enabled, List<Map<String, Object>> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {}
