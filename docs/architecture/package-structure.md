# 패키지와 계층 책임

## 공통 원칙

- `api`는 HTTP 요청 검증, 서비스 호출과 DTO 응답 생성을 담당한다.
- `service`는 유스케이스 흐름과 transaction 경계를 담당한다.
- `domain`은 Entity, 상태와 판정 모델을 담당한다.
- `repository`는 JPA 및 JDBC 데이터 접근을 담당한다.
- Entity를 API 응답으로 직접 반환하지 않는다.

## 쿠폰 정합성 검증

`com.clutch.coupon.integrity`는 운영 쿠폰 데이터를 수정하지 않는 관리자 검증 기능이다.

```text
integrity/
├─ api/          관리자 실행·목록·상세 API, 예외 변환과 응답 DTO
├─ config/       단일 스레드 전용 Executor
├─ domain/       실행 이력, 항목 결과, 실행 상태와 판정
├─ repository/   JPA 이력 저장, JDBC 전체 집계와 MySQL named lock
└─ service/      실행 접수, 비동기 오케스트레이션, 별도 결과 저장과 장애 복구
```

대량 집계는 JPQL로 옮기지 않고 `JdbcTemplate` 기반 Query Repository에서 실행한다.
실행·항목 결과 영속화에는 JPA를 사용하며, 읽기 검증 transaction과 쓰기 이력
transaction을 분리한다.
