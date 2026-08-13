# Clutch

Java 21과 Spring Boot 기반 프로젝트입니다. 팀원 모두가 같은 MySQL, Redis, Kafka 환경을 사용할 수 있도록 Docker Compose로 로컬 개발환경을 관리합니다.

## 개발 문서

- AI 공통 개발 규칙: [`AGENTS.md`](AGENTS.md)
- 확정된 기술 문서: [`docs/README.md`](docs/README.md)
- Claude Code 연결 파일: [`CLAUDE.md`](CLAUDE.md)

## Docker 구성

이 프로젝트에서 사용하는 컨테이너는 다음과 같습니다.

| Compose 서비스 | Docker 이미지 | 용도 | 호스트 포트 | 기본 실행 여부 |
|---|---|---|---:|---|
| `mysql` | `mysql:8.4.7` | 애플리케이션 데이터베이스 | `3306` | 실행 |
| `redis` | `redis:8.4.4-alpine` | 캐시 및 Spring Session 저장소 | `6379` | 실행 |
| `kafka` | `apache/kafka:4.2.0` | 이벤트 메시지 브로커 | `9092` | 실행 |
| `app` | 프로젝트 `Dockerfile`로 빌드 | Spring Boot 애플리케이션 | `8080` | `app` 프로필에서만 실행 |
| `k6` | `grafana/k6:2.0.0` | 부하 및 스모크 테스트 | 없음 | 테스트할 때만 실행 |

Kafka는 ZooKeeper 없이 단일 노드 KRaft 모드로 실행됩니다. MySQL, Redis, Kafka 데이터는 Docker 볼륨에 저장되므로 컨테이너를 종료해도 유지됩니다.

## 사전 준비

다음을 먼저 설치합니다.

- Docker Desktop 또는 Docker Engine
- Docker Compose v2
- 애플리케이션을 IDE나 Gradle로 실행하려면 JDK 21

프로젝트 디렉터리에서 Docker가 정상적으로 설치됐는지 확인합니다.

```bash
docker --version
docker compose version
```

## 권장 실행 방법: 인프라는 Docker, 애플리케이션은 로컬

일반적인 개발에서는 MySQL, Redis, Kafka만 Docker로 실행하고 Spring Boot 애플리케이션은 IDE 또는 Gradle로 실행합니다. 코드를 수정할 때마다 Docker 이미지를 다시 만들 필요가 없어 이 방식을 권장합니다.

### 1. 프로젝트 디렉터리로 이동

```bash
cd clutch
```

이후 모든 명령은 `compose.yaml`이 있는 프로젝트 최상위 디렉터리에서 실행합니다.

### 2. 애플리케이션 설정 파일 생성

최초 한 번 예시 설정을 개인 설정 파일로 복사합니다.

macOS 또는 Linux:

```bash
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
```

Windows PowerShell:

```powershell
Copy-Item src/main/resources/application.example.yaml src/main/resources/application.yaml
```

`application.yaml`은 개인 비밀번호나 로컬 설정을 담을 수 있어 Git에 커밋되지 않습니다. 공통 설정을 추가하거나 변경했다면 `application.example.yaml`도 함께 수정해야 합니다.

### 3. MySQL, Redis, Kafka 실행

```bash
docker compose up -d --wait
```

- `-d`: 컨테이너를 백그라운드에서 실행합니다.
- `--wait`: health check가 성공할 때까지 기다립니다.

처음 실행하면 Docker 이미지를 내려받기 때문에 시간이 걸릴 수 있습니다. 이후에는 저장된 이미지를 재사용합니다.

### 4. 컨테이너 상태 확인

```bash
docker compose ps
```

다음 세 서비스가 모두 `healthy`여야 합니다.

```text
mysql    Up ... (healthy)
redis    Up ... (healthy)
kafka    Up ... (healthy)
```

정상적으로 올라오지 않은 서비스는 로그를 확인합니다.

```bash
docker compose logs mysql
docker compose logs redis
docker compose logs kafka
```

실시간으로 전체 로그를 보려면 다음 명령을 사용합니다.

```bash
docker compose logs -f
```

### 5. Spring Boot 애플리케이션 실행

Gradle로 실행하는 경우:

```bash
./gradlew bootRun
```

Windows에서는 다음 명령을 사용합니다.

```powershell
.\gradlew.bat bootRun
```

IntelliJ에서는 `ClutchApplication`의 `main` 메서드를 실행해도 됩니다.

### 6. 애플리케이션 상태 확인

애플리케이션 실행 로그가 완료된 후 다음 주소를 확인합니다.

```bash
curl http://localhost:8080/actuator/health
```

정상 응답:

```json
{"status":"UP"}
```

이제 로컬 개발을 시작할 수 있습니다.

## 선택 실행 방법: 애플리케이션까지 모두 Docker로 실행

JDK를 별도로 사용하지 않고 Spring Boot 애플리케이션까지 Docker에서 실행하려면 `app` 프로필을 사용합니다.

### 1. 설정 파일 확인

`src/main/resources/application.yaml`이 없다면 먼저 예시 파일을 복사합니다.

```bash
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
```

### 2. 전체 환경 빌드 및 실행

```bash
docker compose --profile app up -d --build --wait
```

이 명령은 다음 순서로 동작합니다.

1. 프로젝트 `Dockerfile`로 Spring Boot 이미지를 빌드합니다.
2. MySQL, Redis, Kafka를 실행하고 health check를 기다립니다.
3. 인프라가 준비되면 Spring Boot 컨테이너를 실행합니다.
4. `/actuator/health`가 성공할 때까지 기다립니다.

### 3. 전체 상태 확인

```bash
docker compose --profile app ps
curl http://localhost:8080/actuator/health
```

코드를 수정한 후 Docker 앱에 반영하려면 이미지를 다시 빌드합니다.

```bash
docker compose --profile app up -d --build --wait app
```

## k6 테스트 실행

k6는 계속 실행되는 서버가 아닙니다. 테스트 명령을 수행할 때만 임시 컨테이너가 생성되고, 테스트가 끝나면 `--rm` 옵션으로 자동 삭제됩니다.

### 로컬에서 실행한 Spring Boot 테스트

먼저 `./gradlew bootRun` 또는 IDE로 애플리케이션이 실행 중인지 확인한 후 실행합니다.

```bash
docker compose run --rm k6
```

### Docker에서 실행한 Spring Boot 테스트

먼저 `app` 서비스를 실행한 후 k6가 Docker 내부 주소로 접근하게 합니다.

```bash
docker compose --profile app up -d --wait app
K6_BASE_URL=http://app:8080 docker compose --profile app run --rm k6
```

기본 테스트는 가상 사용자 5명이 10초 동안 `/actuator/health`를 호출합니다. 사용자 수와 실행 시간은 다음처럼 변경할 수 있습니다.

```bash
K6_VUS=20 K6_DURATION=30s docker compose run --rm k6
```

테스트 스크립트는 `k6/smoke.js`에 있습니다. HTTP 성공 여부, 애플리케이션의 `UP` 상태, 실패율, 95 백분위 응답 시간을 검사합니다.

## 기본 접속 정보

| 서비스 | 접속 정보 | 사용자 | 비밀번호 |
|---|---|---|---|
| MySQL | `jdbc:mysql://localhost:3306/clutch` | `clutch` | `clutch_local_password` |
| MySQL root | `localhost:3306` | `root` | `clutch_root_local_password` |
| Redis | `localhost:6379` | 없음 | `clutch_local_password` |
| Kafka | `localhost:9092` | 없음 | 없음 |
| Spring Boot | `http://localhost:8080` | - | - |

위 계정과 비밀번호는 로컬 개발 전용입니다. 운영 환경에서 사용하면 안 됩니다.

## 종료와 재실행

### 애플리케이션을 로컬에서 실행한 경우

먼저 Gradle 또는 IDE 애플리케이션을 종료한 후 인프라를 종료합니다.

```bash
docker compose down
```

### 애플리케이션까지 Docker에서 실행한 경우

```bash
docker compose --profile app down
```

`down`은 컨테이너와 네트워크만 제거합니다. MySQL, Redis, Kafka 데이터는 볼륨에 유지됩니다. 다시 실행하려면 다음 명령을 사용합니다.

```bash
docker compose up -d --wait
```

## 로컬 데이터 완전 초기화

DB와 메시지 데이터를 모두 지우고 처음부터 다시 시작해야 할 때만 실행합니다.

```bash
docker compose --profile app down -v
docker compose up -d --wait
```

`-v`는 MySQL, Redis, Kafka의 Docker 볼륨을 삭제합니다. 삭제된 로컬 데이터는 복구할 수 없으므로 주의하세요.

## 자주 사용하는 명령어

```bash
# 실행 상태 확인
docker compose ps

# 전체 로그 실시간 확인
docker compose logs -f

# 특정 서비스 재시작
docker compose restart mysql
docker compose restart redis
docker compose restart kafka

# 인프라 이미지 내려받기
docker compose pull

# 인프라 시작
docker compose up -d --wait

# 인프라 종료 (데이터 유지)
docker compose down
```

## 애플리케이션 환경변수

`application.example.yaml`은 다음 환경변수를 지원합니다. 환경변수를 지정하지 않으면 로컬 개발용 기본값을 사용합니다.

| 환경변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/clutch?...` |
| `DB_USERNAME` | `clutch` |
| `DB_PASSWORD` | `clutch_local_password` |
| `DB_POOL_SIZE` | `10` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | `clutch_local_password` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_CONSUMER_GROUP` | `clutch-local` |

MySQL root 비밀번호나 호스트 포트처럼 Docker Compose에서만 사용하는 값은 Spring Boot 설정이 아닙니다. 필요한 경우 셸 환경변수 또는 Git에 커밋되지 않는 `.env` 파일로 `compose.yaml`의 기본값을 변경할 수 있습니다.
