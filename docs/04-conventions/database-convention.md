# 데이터베이스 규칙

## 기본 구성

- 데이터베이스는 MySQL 8을 사용한다.
- 애플리케이션 데이터 접근에는 Spring Data JPA를 사용한다.
- 스키마 변경 이력은 Flyway로 관리한다.
- MySQL 문자 집합은 `utf8mb4`, collation은 `utf8mb4_0900_ai_ci`를 사용한다.

## 스키마 관리

- migration 파일은 `src/main/resources/db/migration/`에 둔다.
- 애플리케이션은 `spring.jpa.hibernate.ddl-auto=validate`로 Entity와 스키마의 일치 여부를 검증한다.
- 스키마 변경 시 관련 Entity, Repository와 테스트의 영향을 함께 확인한다.
- 중복 방지와 값 범위처럼 데이터 정합성에 필요한 제약 조건은 현재 스키마의 unique key, foreign key와 check constraint를 함께 확인한다.

## 시간

- 애플리케이션 JVM, JDBC와 데이터베이스의 날짜·시각 기준은 UTC이다.
- 로컬 운영체제의 시간대와 관계없이 애플리케이션 시작 시 JVM 기본 시간대를 UTC로 설정한다.
- 외부 시각을 저장하거나 비교할 때 시간대 변환 여부를 명확히 한다.

## 설정 파일

- 공통 설정 예시는 `src/main/resources/application.example.yaml`에서 관리한다.
- 공통 설정을 추가하거나 변경하면 예시 설정 파일도 함께 확인한다.
- 개인용 `src/main/resources/application.yaml`과 `.env`는 Git에 커밋하지 않는다.
