package com.example.driversettlementsystem.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 두 엔티티의 매핑이 확정 ERD와 같은지 검증한다.
 * <p>
 * DB에 붙지 않고 Hibernate 매핑만 만들어 확인한다. 로컬에 PostgreSQL이 없어
 * {@code ddl-auto=validate}를 돌릴 수 없는 동안, 컬럼 이름이 어긋난 것을 잡을 수 있는
 * 유일한 수단이다. 여기서 통과해도 <b>실제 테이블과의 일치는 보장하지 않는다</b> —
 * 그것은 Flyway 마이그레이션이 붙은 뒤 부팅에서 확인된다.
 */
class SettlementMappingTest
{

    private static Metadata metadata;

    @BeforeAll
    static void buildMetadata()
    {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();

        metadata = new MetadataSources(registry)
                .addAnnotatedClass(SettlementBatch.class)
                .addAnnotatedClass(Settlement.class)
                .buildMetadata();
    }

    @DisplayName("SessionFactory가 만들어진다 — 매핑 자체에 오류가 없다")
    @Test
    void buildsSessionFactory()
    {
        assertThatCode(() -> metadata.buildSessionFactory().close()).doesNotThrowAnyException();
    }

    @DisplayName("BATCHES가 ERD의 컬럼을 모두 갖는다")
    @Test
    void batchesHasAllColumns()
    {
        assertThat(columnNamesOf(SettlementBatch.class)).containsExactlyInAnyOrder(
                "batch_id", "target_date", "status", "executed_at",
                "total_payout_amount", "reconciliation_status", "confirmed_at");
    }

    @DisplayName("SETTLEMENTS가 ERD의 컬럼을 모두 갖는다")
    @Test
    void settlementsHasAllColumns()
    {
        assertThat(columnNamesOf(Settlement.class)).containsExactlyInAnyOrder(
                "batch_id", "driver_id", "ledger_id",
                "fare_total", "fee_amount", "amount", "payout_status");
    }

    @DisplayName("SETTLEMENTS의 PK는 (batch_id, driver_id) 복합키다")
    @Test
    void settlementsHasCompositePrimaryKey()
    {
        List<String> pk = metadata.getEntityBinding(Settlement.class.getName())
                .getTable()
                .getPrimaryKey()
                .getColumns()
                .stream()
                .map(Column::getName)
                .toList();

        assertThat(pk).containsExactly("batch_id", "driver_id");
    }

    /**
     * 지급 분개를 남기기 전에는 값이 없으므로 nullable이어야 한다. 여기가 NOT NULL이면
     * 정산 항목을 저장하는 순간 실패한다.
     */
    @DisplayName("ledger_id는 nullable이다")
    @Test
    void ledgerIdIsNullable()
    {
        Column ledgerId = metadata.getEntityBinding(Settlement.class.getName())
                .getTable()
                .getColumn(new Column("ledger_id"));

        assertThat(ledgerId.isNullable()).isTrue();
    }

    private static List<String> columnNamesOf(Class<?> entityType)
    {
        PersistentClass binding = metadata.getEntityBinding(entityType.getName());
        return binding.getTable().getColumns().stream().map(Column::getName).toList();
    }

}
