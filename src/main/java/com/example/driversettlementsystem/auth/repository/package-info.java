/**
 * 로그인 DB 조회 — 기사 이름·계좌번호.
 * <p>
 * 정산 DB와 별도 인스턴스라 DataSource가 다르다. 읽는 테이블이 {@code DRIVER_ACCOUNTS}
 * 하나뿐이라 JPA를 붙이지 않고 로그인 DB에 묶인 JDBC 템플릿만 쓴다. 정산 DB에 접근하는
 * 코드와 섞이지 않도록 이 패키지에만 둔다.
 */
package com.example.driversettlementsystem.auth.repository;
