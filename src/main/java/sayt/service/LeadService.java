package sayt.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.dto.lead.*;
import sayt.exception.Problem;
import sayt.repository.LeadRepository;

@Service
public class LeadService {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED");
    private final LeadRepository repository;
    private final CatalogService catalog;
    private final RateLimits limits;
    private final AuditService audit;
    private final Clock clock;
    private final String privacyVersion;
    private final int retentionDays;
    private final List<String> phoneRegions;
    private final int sessionLimit;
    private final int sourceLimit;
    private final int phoneLimit;
    private final NotificationService notifications;
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    LeadService(LeadRepository repository, CatalogService catalog, RateLimits limits, AuditService audit, Clock clock,
        @Value("${shop.privacy-version}") String privacyVersion, @Value("${shop.retention-days}") int retentionDays,
        @Value("${shop.phone-regions}") List<String> phoneRegions, @Value("${shop.limits.lead-session}") int sessionLimit,
        @Value("${shop.limits.lead-source}") int sourceLimit, @Value("${shop.limits.lead-phone}") int phoneLimit,
        NotificationService notifications) {
        this.repository = repository;
        this.catalog = catalog;
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
        this.privacyVersion = privacyVersion;
        this.retentionDays = Math.max(1, retentionDays);
        this.phoneRegions = phoneRegions;
        this.sessionLimit = sessionLimit;
        this.sourceLimit = sourceLimit;
        this.phoneLimit = phoneLimit;
        this.notifications = notifications;
    }

    @Transactional
    public LeadReceipt create(CreateLeadRequest input, UUID key, String session, String source) {
        String name = input.name().strip().replaceAll("[\\p{Zs} ]+", " ");
        if (name.length() < 2 || !name.matches("[\\p{L}\\p{M} ‘’ʻʼ'-]+")
            || name.codePoints().noneMatch(Character::isLetter)) throw Problem.bad("NAME_INVALID");
        String phone = phone(input.phone());
        String hash = digest(key + "\n" + input.name() + "\n" + input.phone() + "\n" + input.variantId() + "\n"
            + input.expectedPrice().stripTrailingZeros().toPlainString() + "\n" + input.privacyVersion());

        var existing = repository.lockAndFindReplay(key);
        if (existing.isPresent()) {
            var replay = existing.get();
            if (!hash.equals(replay.hash())) throw Problem.conflict("IDEMPOTENCY_CONFLICT");
            return new LeadReceipt(replay.number());
        }
        if (!privacyVersion.equals(input.privacyVersion())) throw Problem.conflict("PRIVACY_CHANGED");

        var selected = catalog.lockSelection(input.variantId());
        if (input.expectedPrice().compareTo(selected.price()) != 0) {
            var quote = new LinkedHashMap<String, Object>();
            quote.put("currentPrice", selected.price());
            if (selected.originalPrice() != null) quote.put("originalPrice", selected.originalPrice());
            throw new Problem(HttpStatus.CONFLICT, "PRICE_CHANGED", quote);
        }
        limits.acquire("lead-ip:" + source, sourceLimit, Duration.ofMinutes(10));
        limits.acquire("lead-session:" + session, sessionLimit, Duration.ofMinutes(10));
        limits.acquire("lead-phone:" + digest(phone), phoneLimit, Duration.ofHours(1));

        UUID number = UUID.randomUUID();
        long id = repository.insert(number, key, hash, name, phone, selected, privacyVersion);
        notifications.enqueue(id);
        return new LeadReceipt(number);
    }

    public LeadListResponse list(String status, String query, LocalDate from, LocalDate until, int page) {
        var zone = ZoneId.of("Asia/Tashkent");
        if (from != null && until != null && from.isAfter(until)) throw Problem.bad("DATE_RANGE");
        return repository.list(status, query,
            from == null ? null : from.atStartOfDay(zone).toOffsetDateTime(),
            until == null ? null : until.plusDays(1).atStartOfDay(zone).toOffsetDateTime(), page, cutoff());
    }

    public LeadDetailResponse detail(long id) { return new LeadDetailResponse(get(id), repository.history(id)); }

    private LeadResponse get(long id) { return repository.find(id, cutoff()).orElseThrow(Problem::missing); }

    private OffsetDateTime cutoff() { return OffsetDateTime.now(clock).minusDays(retentionDays); }

    @Transactional
    public LeadDetailResponse update(long id, UpdateLeadRequest input, long actor, boolean owner) {
        var previous = get(id);
        Problem.version(input.version(), previous.version());
        boolean changesStatus = !previous.status().equals(input.status());
        if (previous.anonymizedAt() != null) throw Problem.conflict("LEAD_ANONYMIZED");
        if (changesStatus) {
            boolean allowed = switch (previous.status()) {
                case "NEW" -> Set.of("CONTACTED", "CANCELLED").contains(input.status());
                case "CONTACTED" -> TERMINAL.contains(input.status());
                case "COMPLETED", "CANCELLED" -> owner && "CONTACTED".equals(input.status())
                    && input.reason() != null && !input.reason().isBlank();
                default -> false;
            };
            if (!allowed) throw Problem.conflict("INVALID_LEAD_STATUS");
        }
        OffsetDateTime closed = changesStatus ? TERMINAL.contains(input.status()) ? OffsetDateTime.now(clock) : null
            : previous.closedAt();
        int updated = repository.update(id, previous.version(), input.status(), input.comment(), closed);
        if (updated != 1) throw Problem.conflict("VERSION_CONFLICT");
        repository.addHistory(id, actor, previous.status(), input.status(), input.reason());
        audit.record(actor, changesStatus ? "LEAD_STATUS_" + input.status() : "LEAD_COMMENT_CHANGED", "lead:" + id);
        return detail(id);
    }

    private String phone(String raw) {
        if (!raw.matches("[+0-9 ()-]+")) throw Problem.bad("PHONE_INVALID");
        try {
            var parsed = phoneUtil.parse(raw, "UZ");
            if (phoneUtil.isValidNumber(parsed) && phoneRegions.contains(phoneUtil.getRegionCodeForNumber(parsed))) {
                return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        } catch (com.google.i18n.phonenumbers.NumberParseException ignored) {
            // Invalid public input is reported without returning the submitted number.
        }
        throw Problem.bad("PHONE_INVALID");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
