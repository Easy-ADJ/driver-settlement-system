package com.example.driversettlementsystem.auth.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 DB에서 기사 정보를 읽는 유일한 클래스.
 * <p>
 * <b>여기서만 결합의 성격이 다르다.</b> 다른 경계는 API 계약이 결합점이지만 로그인 DB는
 * <b>테이블 스키마가 직접 결합점</b>이다. {@code DRIVER_ACCOUNTS} 컬럼이 바뀌면 이
 * 클래스의 쿼리가 즉시 깨진다 — 그래서 쿼리를 한곳에 모은다.
 * <p>
 * ⚠️ 읽기 전용이다. 이 클래스에 INSERT/UPDATE가 등장하면 안 된다. 접속도 읽기 전용
 * 계정({@code settlement_ro})으로 한다.
 * <p>
 * ⚠️ <b>배치 집계는 이 클래스에 의존하지 않는다.</b> 금액 계산에는 {@code driverId}만
 * 있으면 되므로, 로그인 DB가 죽어도 정산 자체는 돌아야 한다. 이름·계좌는 조회 시점에 붙인다.
 */
@Component
public class DriverAccountReader
{

    private static final String SELECT_BY_IDS = """
            select driver_id, name, account
            from DRIVER_ACCOUNTS
            where driver_id in (:driverIds)
            """;

    private final NamedParameterJdbcTemplate authJdbcTemplate;

    public DriverAccountReader(NamedParameterJdbcTemplate authJdbcTemplate)
    {
        this.authJdbcTemplate = authJdbcTemplate;
    }

    /**
     * 기사 여러 명의 정보를 한 번에 읽는다.
     * <p>
     * {@code IN} 절로 한 번에 읽는다. 기사마다 한 번씩 부르면 N+1이 된다.
     *
     * @param driverIds 조회할 기사 ID 목록
     * @return {@code driverId → 기사 정보} 맵. 로그인 DB에 없는 ID는 맵에 담기지 않는다
     */
    public Map<Long, DriverAccount> findByIds(Collection<Long> driverIds)
    {
        if (driverIds.isEmpty())
        {
            // in () 은 문법 오류다. 호출자가 빈 배치를 넘길 수 있으므로 여기서 끊는다.
            return Map.of();
        }

        Map<Long, DriverAccount> accounts = new HashMap<>();
        authJdbcTemplate.query(SELECT_BY_IDS,
                new MapSqlParameterSource("driverIds", driverIds),
                resultSet ->
                {
                    DriverAccount account = new DriverAccount(
                            resultSet.getLong("driver_id"),
                            resultSet.getString("name"),
                            resultSet.getString("account"));
                    accounts.put(account.driverId(), account);
                });

        return accounts;
    }

}
