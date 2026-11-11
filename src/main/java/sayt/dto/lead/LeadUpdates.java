package sayt.dto.lead;

import java.util.List;
import java.util.UUID;
public record LeadUpdates(long unreadCount, long latestId, List<Item> recent) {
    public record Item(long id, UUID number, String productName) {}
}
