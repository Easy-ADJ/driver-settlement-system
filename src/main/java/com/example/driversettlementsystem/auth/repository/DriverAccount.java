package com.example.driversettlementsystem.auth.repository;

/**
 * 로그인 DB {@code DRIVER_ACCOUNTS}에서 정산이 쓰는 필드만.
 * <p>
 * 계정·비밀번호가 든 테이블이라 필요한 세 컬럼만 읽는다. 쓰지도 않을 값을 가져오면
 * 로그·덤프를 통해 새어 나갈 표면만 넓어진다.
 *
 * @param driverId 기사 ID
 * @param name     기사명
 * @param account  계좌번호
 */
public record DriverAccount(Long driverId, String name, String account)
{
}
