package com.example.driversettlementsystem.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.driversettlementsystem.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@code DRIVER_ACCOUNTS} 조회 쿼리를 검증한다.
 * <p>
 * 로그인 DB는 남이 소유한 실제 인스턴스라 테스트가 붙지 않는다. 대신 컨테이너에 같은
 * 모양의 테이블을 만들어 <b>쿼리 자체</b>를 확인한다 — 컬럼 이름이 어긋나면 여기서 깨진다.
 * <p>
 * ⚠️ 이 테스트는 컬럼 구성이 ERD와 같다는 전제 위에 있다. 소유자가 컬럼을 바꾸면
 * 테스트는 통과하고 <b>운영에서만</b> 깨진다. 로그인 DB 스키마 변경이 위험한 이유다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DriverAccountReaderTest
{

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DriverAccountReader reader;

    @BeforeEach
    void createDriverAccounts()
    {
        jdbcTemplate.execute("""
                create table DRIVER_ACCOUNTS (
                    driver_id  bigint primary key,
                    manager_id bigint,
                    user_id    varchar(20),
                    password   varchar,
                    name       varchar(20),
                    account    varchar(20),
                    created_at timestamptz
                )
                """);
        jdbcTemplate.update("""
                insert into DRIVER_ACCOUNTS (driver_id, name, account)
                values (7, '김기사', '110-123-456789'), (8, '이기사', '110-987-654321')
                """);

        reader = new DriverAccountReader(new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    @DisplayName("기사 ID 목록으로 이름·계좌번호를 한 번에 읽는다")
    @Test
    void readsAccountsByIds()
    {
        Map<Long, DriverAccount> accounts = reader.findByIds(List.of(7L, 8L));

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(7L))
                .isEqualTo(new DriverAccount(7L, "김기사", "110-123-456789"));
    }

    /** 없는 기사를 null로 채워 돌려주면 호출자가 매번 null을 다뤄야 한다. */
    @DisplayName("로그인 DB에 없는 기사 ID는 맵에 담기지 않는다")
    @Test
    void omitsUnknownDriverIds()
    {
        Map<Long, DriverAccount> accounts = reader.findByIds(List.of(7L, 999L));

        assertThat(accounts).containsOnlyKeys(7L);
    }

    /** {@code in ()} 은 문법 오류다. 빈 배치에서 쿼리가 나가면 그대로 예외가 된다. */
    @DisplayName("빈 목록이면 쿼리를 보내지 않고 빈 맵을 준다")
    @Test
    void returnsEmptyMapWithoutQuerying()
    {
        assertThat(reader.findByIds(List.of())).isEmpty();
    }

}
