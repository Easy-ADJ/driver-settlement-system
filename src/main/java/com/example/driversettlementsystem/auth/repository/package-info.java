/**
 * 로그인 DB 조회 — 기사 이름·계좌번호.
 * <p>
 * 정산 DB와 별도 인스턴스라 DataSource가 다르다. {@code @EnableJpaRepositories}의
 * {@code basePackages}가 정산쪽과 겹치지 않도록 이 패키지에만 둔다.
 */
package com.example.driversettlementsystem.auth.repository;
