package com.example.footballanalysis;

import com.example.footballanalysis.model.db.MedicalAdminAccess;
import com.example.footballanalysis.repository.AuditEventRepository;
import com.example.footballanalysis.repository.MedicalAdminAccessRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3 to V5 on a real PostgreSQL: the partial unique index allows one current certificate per profile,
 * clubs start OFF, deletes cascade or set null as the plan says, and an admin access row outlives the profile.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
// Each statement commits on its own: in PostgreSQL a failed insert would abort the rest of a shared transaction.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlywayMedicalCertificatesMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MedicalAdminAccessRepository adminAccessRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createsTheTableIndexesAndColumns() {
        assertThat(indexDefinition("ux_medical_certificates_current"))
                .contains("UNIQUE")
                .contains("WHERE");
        assertThat(indexDefinition("ix_medical_certificates_profile_created")).isNotNull();
        assertThat(columnExists("player_profiles", "medical_valid_until")).isFalse();
        assertThat(columnDefault("player_profiles", "can_upload_medical")).isEqualTo("false");
        assertThat(columnDefault("clubs", "medical_mode")).contains("OFF");
        assertThat(columnDefault("teams", "medical_mode")).contains("INHERIT");
        assertThat(columnDefault("clubs", "medical_history_retention_years")).isEqualTo("3");
        assertThat(columnExists("player_profiles", "medical_not_required_since")).isTrue();
    }

    @Test
    void allowsOneCurrentCertificatePerProfileButAnyNumberOfPendingAndHistoryRows() {
        UUID profileId = insertProfile();
        insertCertificate(profileId, "PARENT", true, false);
        insertCertificate(profileId, "PARENT", false, false);
        insertCertificate(profileId, "PARENT", false, false);
        insertCertificate(profileId, "PARENT", true, true);

        assertThatThrownBy(() -> insertCertificate(profileId, "PARENT", true, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownRolesAndModes() {
        UUID profileId = insertProfile();
        assertThatThrownBy(() -> insertCertificate(profileId, "OPERATOR", false, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into clubs (id, name, created_at, medical_mode) values (?, 'X', now(), 'INHERIT')",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into clubs (id, name, created_at, medical_history_retention_years) values (?, 'Y', now(), 2)",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheProfileRemovesItsCertificatesAndDeletingTheSubmitterKeepsThem() {
        UUID profileId = insertProfile();
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at, role, user_type) values (?, ?, 'P', 'Q', now(), 'PARENT', 'PARENT')",
                userId, userId + "@test.com");
        UUID certificateId = insertCertificate(profileId, "PARENT", true, false);
        jdbc.update("update medical_certificates set submitted_by_user_id = ? where id = ?", userId, certificateId);

        jdbc.update("delete from users where id = ?", userId);
        assertThat(jdbc.queryForObject("select submitted_by_user_id from medical_certificates where id = ?", UUID.class, certificateId))
                .isNull();

        jdbc.update("delete from player_profiles where id = ?", profileId);
        assertThat(jdbc.queryForObject("select count(*) from medical_certificates where player_profile_id = ?", Integer.class, profileId))
                .isZero();
    }

    @Test
    void v5AddsTheAdminAccessTableAndTheLastRequiringClubs() {
        assertThat(indexDefinition("ix_medical_admin_accesses_club_ids")).contains("gin");
        assertThat(indexDefinition("ix_medical_admin_accesses_created")).isNotNull();
        assertThat(columnExists("player_profiles", "medical_last_required_club_ids")).isTrue();
        UUID profileId = insertProfile();
        assertThat(jdbc.queryForObject("select cardinality(medical_last_required_club_ids) from player_profiles where id = ?",
                Integer.class, profileId)).isZero();

        for (String action : new String[]{"READ", "WITHDRAWN"}) {
            insertAdminAccess(profileId, action);
        }
        assertThatThrownBy(() -> insertAdminAccess(profileId, "DELETED")).isInstanceOf(DataIntegrityViolationException.class);
        // V11: the platform admin no longer opens files.
        assertThatThrownBy(() -> insertAdminAccess(profileId, "FILE_OPENED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAdminAccessRowOutlivesTheProfileAndItsCertificate() {
        UUID profileId = insertProfile();
        UUID certificateId = insertCertificate(profileId, "PARENT", true, false);
        UUID accessId = insertAdminAccess(profileId, "READ");
        jdbc.update("update medical_admin_accesses set certificate_id = ? where id = ?", certificateId, accessId);

        jdbc.update("delete from player_profiles where id = ?", profileId);

        assertThat(jdbc.queryForObject("select player_name from medical_admin_accesses where id = ?", String.class, accessId))
                .isEqualTo("B A");
        assertThat(jdbc.queryForObject("select certificate_id from medical_admin_accesses where id = ?", UUID.class, accessId))
                .isNull();
    }

    @Test
    void theClubListFindsOnlyItsOwnRowsAndTheParentGetsTheRowsWithoutAClub() {
        UUID profileId = insertProfile();
        UUID mine = insertAdminAccess(profileId, "READ");
        insertAdminAccess(profileId, "WITHDRAWN");
        UUID clubId = jdbc.queryForObject("select club_ids[1] from medical_admin_accesses where id = ?", UUID.class, mine);
        UUID orphan = insertAdminAccess(profileId, "READ");
        jdbc.update("update medical_admin_accesses set club_ids = '{}' where id = ?", orphan);

        var page = adminAccessRepository.findByClubId(clubId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(mine);
        assertThat(page.getContent().get(0).getClubIds()).containsExactly(clubId);
        assertThat(adminAccessRepository.findWithoutClubByProfileId(profileId))
                .extracting(MedicalAdminAccess::getId)
                .containsExactly(orphan);
    }

    @Test
    void v6AddsTheClubConditionsAndAllowsEitherALinkOrAPdf() {
        for (String column : new String[]{"medical_legal_basis", "medical_notice_url", "medical_notice_object_key",
                "medical_notice_updated_at", "medical_dpa_version", "medical_dpa_accepted_at", "medical_dpa_accepted_by_user_id"}) {
            assertThat(columnExists("clubs", column)).as(column).isTrue();
        }
        assertThat(columnDefault("clubs", "medical_notice_version")).isEqualTo("0");
        UUID clubId = UUID.randomUUID();
        jdbc.update("insert into clubs (id, name, created_at) values (?, 'Notice FC', now())", clubId);
        jdbc.update("update clubs set medical_notice_url = 'https://club.example/notice' where id = ?", clubId);
        jdbc.update("update clubs set medical_notice_url = null, medical_notice_object_key = 'notices/x.pdf' where id = ?", clubId);

        assertThatThrownBy(() -> jdbc.update(
                "update clubs set medical_notice_url = 'https://club.example/notice' where id = ?", clubId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v6DeletingTheUserWhoAcceptedTheAgreementKeepsTheAcceptance() {
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at, role, user_type) values (?, ?, 'C', 'A', now(), 'CLUB_ADMIN', 'CLUB_ADMIN')",
                userId, userId + "@test.com");
        UUID clubId = UUID.randomUUID();
        jdbc.update("""
                insert into clubs (id, name, created_at, medical_dpa_version, medical_dpa_accepted_at, medical_dpa_accepted_by_user_id)
                values (?, 'Dpa FC', now(), 'v1', now(), ?)
                """, clubId, userId);

        jdbc.update("delete from users where id = ?", userId);

        assertThat(jdbc.queryForObject("select medical_dpa_version from clubs where id = ?", String.class, clubId)).isEqualTo("v1");
        assertThat(jdbc.queryForObject("select medical_dpa_accepted_by_user_id from clubs where id = ?", UUID.class, clubId)).isNull();
    }

    @Test
    void v7MovesTheClubsThatAlreadyCollectToTheFileAndKeepsTheOldRowsAsFileRowsWithoutAResult() {
        // A schema of its own, migrated up to V6, filled, then migrated to the end.
        String schema = "pre_v7";
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).target("6").load().migrate();
        UUID on = UUID.randomUUID();
        UUID off = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID certificateId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".clubs (id, name, created_at, medical_mode) values (?, 'On FC', now(), 'ON'), (?, 'Off FC', now(), 'OFF')", on, off);
        jdbc.update("insert into " + schema + ".player_profiles (id, first_name, last_name, created_at) values (?, 'A', 'B', now())", profileId);
        jdbc.update("""
                insert into %s.medical_certificates (id, player_profile_id, valid_until, content_type, size_bytes,
                    submitted_by_role, acknowledged_at, acknowledgement_version, created_at, completed_at)
                values (?, ?, date '2027-05-03', 'application/pdf', 100, 'PARENT', now(), 'sport-medical-v1', now(), now())
                """.formatted(schema), certificateId, profileId);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).load().migrate();

        assertThat(jdbc.queryForObject("select medical_evidence from " + schema + ".clubs where id = ?", String.class, on)).isEqualTo("REDACTED_FILE");
        assertThat(jdbc.queryForObject("select medical_evidence from " + schema + ".clubs where id = ?", String.class, off)).isEqualTo("DATE_ONLY");
        assertThat(jdbc.queryForObject("select evidence from " + schema + ".medical_certificates where id = ?", String.class, certificateId)).isEqualTo("FILE");
        assertThat(jdbc.queryForObject("select result from " + schema + ".medical_certificates where id = ?", String.class, certificateId)).isNull();
    }

    @Test
    void v7DateOnlyRowsNeedNoFileButNewRowsNeedAResultWithTheRightDate() {
        assertThat(columnDefault("teams", "medical_evidence")).contains("INHERIT");
        assertThat(columnDefault("clubs", "medical_evidence")).contains("DATE_ONLY");
        assertThat(columnExists("player_profiles", "medical_file_not_required_since")).isTrue();
        UUID profileId = insertProfile();

        insertV2(profileId, "DATE_ONLY", "VERSENYEZHET", "date '2027-05-03'", null, false);
        insertV2(profileId, "DATE_ONLY", "NEM_ALKALMAS", "null", null, true);
        // A file row needs its content type and size.
        assertThatThrownBy(() -> insertV2(profileId, "FILE", "VERSENYEZHET", "date '2027-05-03'", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A v2 row without a result, a dateless result with a date, and a dated result without one.
        assertThatThrownBy(() -> insertV2(profileId, "DATE_ONLY", null, "date '2027-05-03'", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertV2(profileId, "DATE_ONLY", "NEM_ALKALMAS", "date '2027-05-03'", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertV2(profileId, "DATE_ONLY", "VERSENYEZHET", "null", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertV2(profileId, "DATE_ONLY", "FIT", "date '2027-05-03'", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v8VerificationFieldsComeTogetherAndTheCoachMaySubmit() {
        assertThat(columnDefault("clubs", "medical_coach_file_access")).isEqualTo("false");
        UUID profileId = insertProfile();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into medical_certificates (id, player_profile_id, valid_until, submitted_by_role, acknowledged_at,
                    acknowledgement_version, created_at, evidence, result, completed_at,
                    verified_at, verified_by_role, verification_method)
                values (?, ?, date '2027-05-03', 'COACH', now(), 'sport-medical-v2-in-person', now(), 'DATE_ONLY', 'ALKALMAS', now(),
                    now(), 'COACH', 'ORIGINAL')
                """, id, profileId);
        assertThatThrownBy(() -> jdbc.update("update medical_certificates set verified_at = null where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update medical_certificates set verified_by_role = 'PARENT' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update medical_certificates set verification_method = 'PHOTO' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update medical_certificates set verified_at = null, verified_by_role = null, verification_method = null where id = ?", id);
        assertThatThrownBy(() -> jdbc.update("update medical_certificates set verification_method = 'FILE' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v9TheSeenRowBelongsToOneParentAndOneChildAndGoesWithEitherOrTheCertificate() {
        assertThat(columnDefault("player_profiles", "medical_parent_access_allowed")).isEqualTo("false");
        UUID profileId = insertProfile();
        UUID certificateId = insertCertificate(profileId, "PARENT", true, false);
        UUID parentId = UUID.randomUUID();
        UUID otherParentId = UUID.randomUUID();
        for (UUID id : List.of(parentId, otherParentId)) {
            jdbc.update("insert into users (id, email, first_name, last_name, created_at, role, user_type) values (?, ?, 'P', 'Q', now(), 'PARENT', 'PARENT')",
                    id, id + "@test.com");
        }
        String insert = "insert into medical_parent_seen (player_profile_id, parent_user_id, certificate_id, seen_at) values (?, ?, ?, now())";
        jdbc.update(insert, profileId, parentId, certificateId);
        jdbc.update(insert, profileId, otherParentId, certificateId);
        assertThatThrownBy(() -> jdbc.update(insert, profileId, parentId, certificateId)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("delete from users where id = ?", otherParentId);
        assertThat(jdbc.queryForObject("select count(*) from medical_parent_seen where player_profile_id = ?", Integer.class, profileId)).isEqualTo(1);
        jdbc.update("delete from medical_certificates where id = ?", certificateId);
        assertThat(jdbc.queryForObject("select count(*) from medical_parent_seen where player_profile_id = ?", Integer.class, profileId)).isZero();

        UUID again = insertCertificate(profileId, "PARENT", true, false);
        jdbc.update(insert, profileId, parentId, again);
        jdbc.update("delete from player_profiles where id = ?", profileId);
        assertThat(jdbc.queryForObject("select count(*) from medical_parent_seen where parent_user_id = ?", Integer.class, parentId)).isZero();
    }

    @Test
    void theAuditCleanupDeletesOnlyOldSportMedicalRowsInOneBatch() {
        jdbc.update("delete from audit_events");
        LocalDateTime now = LocalDateTime.now();
        UUID oldMedical = insertAudit("MEDICAL_CERTIFICATE_OPENED", now.minusYears(4));
        UUID newMedical = insertAudit("MEDICAL_CERTIFICATE_OPENED", now.minusYears(2));
        UUID oldOther = insertAudit("PLAYER_RIDE_RIGHT_SET", now.minusYears(4));
        // The underscore is escaped: MEDICALX does not match MEDICAL_.
        UUID oldLookalike = insertAudit("MEDICALXSETTINGS", now.minusYears(4));
        UUID profileId = insertProfile();
        UUID oldAccess = insertAdminAccess(profileId, "READ");
        jdbc.update("update medical_admin_accesses set created_at = now() - interval '4 years' where id = ?", oldAccess);
        UUID newAccess = insertAdminAccess(profileId, "READ");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int audit = transaction.execute(status -> auditEventRepository.deleteMedicalBefore(now.minusYears(3)));
        int accesses = transaction.execute(status -> adminAccessRepository.deleteCreatedBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(3 * 365))));

        assertThat(audit).isEqualTo(1);
        assertThat(accesses).isEqualTo(1);
        assertThat(jdbc.queryForList("select id from audit_events", UUID.class)).containsExactlyInAnyOrder(newMedical, oldOther, oldLookalike);
        assertThat(jdbc.queryForList("select id from medical_admin_accesses where player_profile_id = ?", UUID.class, profileId))
                .containsExactly(newAccess);
        assertThat(oldMedical).isNotNull();
    }

    private UUID insertAudit(String eventType, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into audit_events (id, event_type, target_type, created_at) values (?, ?, 'X', ?)", id, eventType, createdAt);
        return id;
    }

    private void insertV2(UUID profileId, String evidence, String result, String validUntil, String contentType, boolean superseded) {
        jdbc.update("""
                insert into medical_certificates (id, player_profile_id, valid_until, content_type, submitted_by_role,
                    acknowledged_at, acknowledgement_version, created_at, evidence, result, superseded_at, completed_at)
                values (?, ?, %s, ?, 'PARENT', now(), 'sport-medical-v2-date', now(), ?, ?, case when ? then now() end, now())
                """.formatted(validUntil), UUID.randomUUID(), profileId, contentType, evidence, result, superseded);
    }

    private UUID insertAdminAccess(UUID profileId, String action) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into medical_admin_accesses (id, player_profile_id, player_name, club_ids, action, reason, created_at)
                values (?, ?, 'B A', array[?]::uuid[], ?, 'The club asked for a check.', now())
                """, id, profileId, UUID.randomUUID(), action);
        return id;
    }

    private UUID insertProfile() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into player_profiles (id, first_name, last_name, created_at) values (?, 'A', 'B', now())", id);
        return id;
    }

    private UUID insertCertificate(UUID profileId, String role, boolean completed, boolean superseded) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into medical_certificates (id, player_profile_id, valid_until, content_type, size_bytes,
                    submitted_by_role, acknowledged_at, acknowledgement_version, created_at, completed_at, superseded_at, evidence)
                values (?, ?, date '2027-05-03', 'application/pdf', 100, ?, now(), 'sport-medical-v1', now(),
                    case when ? then now() end, case when ? then now() end, 'FILE')
                """, id, profileId, role, completed, superseded);
        return id;
    }

    private String indexDefinition(String name) {
        return jdbc.query("select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                rs -> rs.next() ? rs.getString(1) : null, name);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private String columnDefault(String table, String column) {
        return jdbc.queryForObject(
                "select column_default from information_schema.columns where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, table, column);
    }
}
