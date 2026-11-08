package sayt.leads;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sayt.catalog.Catalog;
import sayt.common.*;

@Service
class Leads {
    record Input(@NotBlank @Size(min=2,max=80) String name, @NotBlank @Size(max=45) String phone,
        @NotNull @Positive Long variantId, @NotNull @Positive @Digits(integer=14,fraction=2) BigDecimal expectedPrice,
        @NotBlank @Size(max=80) String privacyVersion, @AssertTrue boolean informed) {}
    record Receipt(UUID number) {}
    record Update(@NotBlank @Pattern(regexp="NEW|CONTACTED|COMPLETED|CANCELLED") String status,
        @Size(max=2000) String comment, @Size(max=2000) String reason, @NotNull Long version) {}
    record View(long id, UUID number, String name, String phone, long productId, long variantId, String productName,
        String sku, String variantLabel, BigDecimal price, BigDecimal originalPrice, String currency,
        boolean installment, String installmentTerms, String status, String comment, String privacyVersion,
        OffsetDateTime createdAt, OffsetDateTime closedAt, OffsetDateTime anonymizedAt, boolean retentionReview,
        long version) {}
    record ListView(List<View> items, boolean hasNext) {}
    record Detail(View lead, List<Map<String, Object>> history) {}
    private record Previous(UUID number, String hash) {}
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED");
    private final JdbcClient db;
    private final Catalog catalog;
    private final RateLimits limits;
    private final Audit audit;
    private final Clock clock;
    private final String privacyVersion;
    private final int retentionDays;
    private final List<String> phoneRegions;
    private final int sessionLimit;
    private final int sourceLimit;
    private final int phoneLimit;
    private final Telegram telegram;
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    Leads(JdbcClient db, Catalog catalog, RateLimits limits, Audit audit, Clock clock,
        @Value("${shop.privacy-version}") String privacyVersion, @Value("${shop.retention-days}") int retentionDays,
        @Value("${shop.phone-regions}") List<String> phoneRegions, @Value("${shop.limits.lead-session}") int sessionLimit,
        @Value("${shop.limits.lead-source}") int sourceLimit, @Value("${shop.limits.lead-phone}") int phoneLimit,
        Telegram telegram) {
        this.db = db;
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
        this.telegram = telegram;
    }

    @Transactional
    Receipt create(Input input, UUID key, String session, String source) {
        String name = input.name().strip().replaceAll("[\\p{Zs} ]+", " ");
        if (name.length() < 2 || !name.matches("[\\p{L}\\p{M} ‘’ʻʼ'-]+")
            || name.codePoints().noneMatch(Character::isLetter)) throw Problem.bad("NAME_INVALID");
        String phone = phone(input.phone());
        String hash = digest(key + "\n" + input.name() + "\n" + input.phone() + "\n" + input.variantId() + "\n"
            + input.expectedPrice().stripTrailingZeros().toPlainString() + "\n" + input.privacyVersion());

        db.sql("select 1 from pg_advisory_xact_lock(hashtextextended(cast(? as text), 0))")
            .param(key).query(Integer.class).single();
        var existing = db.sql("select public_number,request_hash from lead where idempotency_key=?").param(key)
            .query((rs, index) -> new Previous(rs.getObject(1, UUID.class), rs.getString(2))).optional();
        if (existing.isPresent()) {
            var replay = existing.get();
            if (!hash.equals(replay.hash())) throw Problem.conflict("IDEMPOTENCY_CONFLICT");
            return new Receipt(replay.number());
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
        long id = db.sql("""
            insert into lead (public_number,idempotency_key,request_hash,name,phone,product_id,variant_id,
                product_name,sku,variant_label,price,original_price,installment,installment_terms,status,privacy_version)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NEW',?) returning id
            """).params(number, key, hash, name, phone, selected.productId(), selected.variantId(),
                selected.productName(), selected.sku(), selected.label(), selected.price(), selected.originalPrice(),
                selected.installment(), selected.installmentTerms(), privacyVersion).query(Long.class).single();
        if (telegram.enabled()) db.sql("insert into notification_outbox(lead_id,status) values (?,'PENDING')").param(id).update();
        return new Receipt(number);
    }

    ListView list(String status, String query, LocalDate from, LocalDate until, int page) {
        var zone = ZoneId.of("Asia/Tashkent");
        if (from != null && until != null && from.isAfter(until)) throw Problem.bad("DATE_RANGE");
        String search = query == null ? "" : query.strip();
        var sql = """
            select * from lead where true %s %s %s %s order by created_at desc,id desc limit 25 offset :skip
            """.formatted(status == null ? "" : "and status=:status",
            search.isEmpty() ? "" : "and (cast(public_number as text) ilike :q escape '!' or sku ilike :q escape '!' or phone=:phone)",
            from == null ? "" : "and created_at >= :from", until == null ? "" : "and created_at < :until");
        var stmt = db.sql(sql).param("skip", page * 24);
        if (status != null) stmt.param("status", status);
        if (!search.isEmpty()) {
            stmt.param("q", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .param("phone", search.replaceAll("[ ()-]", ""));
        }
        if (from != null) stmt.param("from", from.atStartOfDay(zone).toOffsetDateTime());
        if (until != null) stmt.param("until", until.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        var items = stmt.query(this::view).list();
        return new ListView(items.subList(0, Math.min(24, items.size())), items.size() > 24);
    }

    Detail detail(long id) {
        return new Detail(get(id), db.sql("""
            select actor_id, previous_status, new_status, reason, created_at
            from lead_status_history where lead_id=? order by id desc
            """).param(id).query().listOfRows());
    }

    @Transactional
    Detail update(long id, Update input, long actor, boolean owner) {
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
        int updated = db.sql("""
            update lead set status=?, comment=?, closed_at=?, version=version+1 where id=? and version=?
            """).params(input.status(), input.comment(), closed, id, previous.version()).update();
        if (updated != 1) throw Problem.conflict("VERSION_CONFLICT");
        db.sql("insert into lead_status_history(lead_id,actor_id,previous_status,new_status,reason) values (?,?,?,?,?)")
            .params(id, actor, previous.status(), input.status(), input.reason()).update();
        audit.record(actor, changesStatus ? "LEAD_STATUS_" + input.status() : "LEAD_COMMENT_CHANGED", "lead:" + id);
        return detail(id);
    }

    @Transactional
    void anonymize(long id, Long actor) {
        get(id);
        var active = db.sql("select coalesce(lease_until > now(), false) from notification_outbox where lead_id=? for update")
            .param(id).query(Boolean.class).optional();
        if (active.orElse(false)) throw Problem.conflict("NOTIFICATION_IN_PROGRESS");
        db.sql("""
            update notification_outbox set status='CANCELLED',last_error=null,lease_until=null,lease_token=null
            where lead_id=? and status in ('PENDING','FAILED')
            """).param(id).update();
        int changed = db.sql("""
            update lead set name=null,phone=null,comment=null,anonymized_at=now(),version=version+1
            where id=? and anonymized_at is null
            """).param(id).update();
        if (changed > 0) {
            db.sql("update lead_status_history set reason=null where lead_id=?").param(id).update();
            audit.record(actor, "LEAD_ANONYMIZED", "lead:" + id);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void retention() {
        var ids = db.sql("""
            select id from lead where closed_at < ? and anonymized_at is null
            order by closed_at limit 100
            """).param(OffsetDateTime.now(clock).minusDays(retentionDays)).query(Long.class).list();
        for (long id : ids) {
            try {
                // Run each record through the transactional service proxy.
                self.getObject().anonymize(id, null);
            } catch (Problem error) {
                if (!"NOTIFICATION_IN_PROGRESS".equals(error.code)) throw error;
            }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<Leads> self;

    private View get(long id) {
        return db.sql("select * from lead where id=?").param(id).query(this::view).optional().orElseThrow(Problem::missing);
    }

    private View view(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        var created = row.getObject("created_at", OffsetDateTime.class);
        var closed = row.getObject("closed_at", OffsetDateTime.class);
        var anonymized = row.getObject("anonymized_at", OffsetDateTime.class);
        return new View(row.getLong("id"), row.getObject("public_number", UUID.class), row.getString("name"),
            row.getString("phone"), row.getLong("product_id"), row.getLong("variant_id"), row.getString("product_name"),
            row.getString("sku"), row.getString("variant_label"), row.getBigDecimal("price"),
            row.getBigDecimal("original_price"), row.getString("currency"), row.getBoolean("installment"),
            row.getString("installment_terms"), row.getString("status"), row.getString("comment"),
            row.getString("privacy_version"), created, closed, anonymized,
            closed == null && anonymized == null && created.isBefore(OffsetDateTime.now(clock).minusDays(retentionDays)),
            row.getLong("version"));
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
