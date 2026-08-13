# Issue #169 App HA / Blue-Green Evidence

## 결론

Issue #169의 보장 범위는 **HTTP API App 계층 HA**다.

이번 검증으로 ALB 뒤 App EC2 2대 구성, Target Group health 기반 장애 우회, Blue/Green Target Group weight 전환, GitHub Actions 기반 Blue-Green 배포 자동화가 실제 AWS 환경에서 동작함을 확인했다.

다만 전체 시스템 HA로 표현하지 않는다. 현재 RDS는 Single-AZ이고, Kafka는 단일 EC2의 단일 KRaft broker 구성이다. 따라서 DB 계층과 메시징 계층 장애까지 포함한 전체 서비스 고가용성은 이번 범위가 아니다.

## 1. 실제 AWS 구성

### Blue 환경

| 항목 | 값 |
|---|---|
| Target Group | `bobfull-app-tg` |
| App EC2 | `bobfull-ec2`, `bobfull-ec2-2` |
| App Port | `8080` |
| Health Check | `/actuator/health/readiness` |

### Green 환경

| 항목 | 값 |
|---|---|
| Target Group | `bobfull-app-green-tg` |
| App EC2 | `bobfull-ec2-green-1`, `bobfull-ec2-green-2` |
| App Port | `8080` |
| Health Check | `/actuator/health/readiness` |

### 공용 인프라

| 항목 | 현재 구성 |
|---|---|
| Redis | ElastiCache Valkey, In-transit encryption 사용 |
| Kafka | 전용 EC2, 단일 KRaft broker |
| RDS | MySQL Single-AZ |
| 배포 | GitHub Actions, ECR SHA image, SSM Run Command, ALB Listener weight |

## 2. Blue-Green 배포 자동화

### 변경 파일

| 파일 | 역할 |
|---|---|
| `.github/workflows/deploy-backend-v1.yml` | 단일 EC2 변수 제거, Blue-Green orchestrator 호출, 신규 ALB/TG/public 검증 변수 검증 |
| `scripts/aws/deploy-backend-blue-green-v1.sh` | Listener weight 판정, inactive TG EC2 조회, SSM 배포, TG health 대기, traffic switch, public 검증, rollback |
| `scripts/aws/run-ssm-backend-deploy-v1.sh` | 단일 instance ID 대신 다중 EC2 instance ID에 같은 SSM command 실행, 모든 결과 확인 |
| `docs/deployment/aws-v1-backend.md` | Blue-Green 배포 기준, GitHub Variables, IAM 권한, 성공 조건 문서화 |
| `docs/evidence/v3/169-app-ha/README.md` | Issue #169 실제 검증 결과와 한계 기록 |

기존 `scripts/aws/deploy-backend-v1.sh`는 per-instance deploy worker 역할을 유지한다. 이번 diff에 해당 파일 변경은 없다.

### 배포 흐름

1. CI/Test 수행
2. ECR SHA image 존재 여부 확인 후 기존 image 재사용 또는 새 image push
3. ALB Listener DefaultActions 조회
4. Blue/Green Target Group weight가 정확히 `100/0` 또는 `0/100`인지 확인
5. weight `100` Target Group을 Active, weight `0` Target Group을 Inactive로 판정
6. Inactive Target Group에 등록된 EC2 target이 정확히 2대인지 확인
7. 두 EC2가 SSM managed `Online`인지 확인
8. 두 EC2에 동일 ECR SHA image를 SSM Run Command로 배포
9. 각 EC2의 local readiness 확인
10. Inactive Target Group의 두 target이 모두 `healthy`가 될 때까지 대기
11. Traffic switch 직전 기존 Listener DefaultActions JSON 저장
12. Listener weight를 Active `0`, Inactive `100`으로 전환
13. Public readiness와 public API 검증
14. 전환 후 검증 실패 시 저장한 DefaultActions로 rollback
15. Rollback 후 Listener weight 복구 확인

### GitHub Actions Variables

필수:

```text
AWS_REGION
ECR_REPOSITORY
BACKEND_PARAMETER_PREFIX
BACKEND_ALB_LISTENER_ARN
BACKEND_BLUE_TARGET_GROUP_ARN
BACKEND_GREEN_TARGET_GROUP_ARN
BACKEND_PUBLIC_READINESS_URL
BACKEND_PUBLIC_API_VERIFY_URL
```

선택:

```text
BACKEND_TARGET_PORT
BACKEND_TG_HEALTH_TIMEOUT_SECONDS
BACKEND_TG_HEALTH_POLL_INTERVAL_SECONDS
BACKEND_PUBLIC_VERIFY_ATTEMPTS
BACKEND_PUBLIC_VERIFY_DELAY_SECONDS
BACKEND_PUBLIC_VERIFY_TIMEOUT_SECONDS
BACKEND_LISTENER_WEIGHT_TIMEOUT_SECONDS
BACKEND_LISTENER_WEIGHT_POLL_INTERVAL_SECONDS
```

## 3. 실제 AWS 검증

### App EC2 장애 검증

| 검증 항목 | 결과 |
|---|---|
| App EC2 2대 Target Healthy | 확인 |
| 한 인스턴스의 backend container 중지 | 수행 |
| 중지한 Target Unhealthy 전환 | 확인 |
| 다른 Target Healthy 유지 | 확인 |
| 외부 API 반복 요청 | 10/10 HTTP 200 |

결론: App EC2 1대 장애 시 ALB가 정상 Target으로 요청을 전달해 HTTP API App 계층 요청 처리가 지속됨을 확인했다.

### GitHub Actions Blue-Green 배포

| 검증 항목 | 결과 |
|---|---|
| Inactive Target Group EC2 2대 조회 | 확인 |
| 두 EC2 SSM Online 확인 | 확인 |
| 동일 ECR SHA image 배포 | 확인 |
| 두 EC2 local readiness 성공 | 확인 |
| Inactive Target Group 두 Target Healthy | 확인 |
| ALB Listener weight 전환 | 확인 |
| Public readiness/API 검증 | 확인 |
| 실제 GitHub Actions Blue-Green 배포 실행 | 성공 |

## 4. DB Connection Budget

### 설정 근거

- `src/main/resources/application-prod.yml`
  - `spring.datasource.hikari.maximum-pool-size: ${DB_POOL_MAX_SIZE:10}`
- `scripts/aws/deploy-backend-v1.sh`
  - 현재 Parameter Store env-file 매핑에 `DB_POOL_MAX_SIZE`는 없다.
  - 따라서 운영 컨테이너에 `DB_POOL_MAX_SIZE`가 별도 주입되지 않으면 prod 기본값은 `10`이다.

### 운영 실측값

| 항목 | 값 |
|---|---:|
| Hikari maximum-pool-size | 10 |
| RDS `max_connections` | 60 |
| RDS `Threads_connected` | 45 |
| Blue/Green 배포 중 App EC2 수 | 4 |
| 최대 App pool 계산 | 4 x 10 = 40 |
| 현재 RDS 연결 여유 | 60 - 45 = 15 |

### 해석

현재 즉시 connection exhaustion이 확인된 상태는 아니다. 다만 Blue/Green 배포 중 App EC2가 최대 4대까지 동시에 떠 있고, App pool 최대치만 계산해도 40개 connection budget을 차지할 수 있다.

Auto Scaling으로 인스턴스 수를 늘리는 경우에는 RDS `max_connections`, App pool size, 비 App connection을 포함해 connection budget을 다시 계산해야 한다. Auto Scaling은 Issue #191 범위다.

## 5. Scheduler / Outbox 다중 EC2 검증

분산 락(ShedLock, Redis lock 등)은 현재 코드에 없다. 모든 작업에 분산 락을 추가하는 대신, 코드상 이미 존재하는 DB 상태 조건, pessimistic lock, conditional claim, processing token, unique constraint, optimistic lock을 기준으로 검토했다.

| 작업 | 다중 인스턴스 동시 실행 가능성 | 현재 방어 방식 | 실제 AWS 검증 결과 | 위험도 |
|---|---:|---|---|---|
| READY Payment 만료 | 있음 | 후보 조건 `READY`, `expires_at <= now`, `findWithLockById()`의 `PESSIMISTIC_WRITE`, `expireIfNeeded()` 상태 가드 | `payment_id=5`를 `EXPIRED`에서 `READY`와 과거 `expires_at`으로 테스트 후보화. Scheduler 실행 후 `READY -> EXPIRED`. 한 App EC2에서만 `READY_PAYMENT_EXPIRED` 로그 확인. 중복 상태 변경 없음 | 낮음 |
| Refund Reconciliation | 있음 | 후보 조건 `REQUESTED/PROCESSING`, `lastPgCheckedAt` 재조회 간격, `findWithLockById()` `PESSIMISTIC_WRITE`, terminal 상태 가드 | `refund_id=1`을 후보로 설정. 한 App EC2에서만 `REFUND_RECONCILIATION_REQUIRED` 확인. 다른 인스턴스 동일 처리 로그 미확인. PortOne response `cancellations=null`에서 `NullPointerException`/`REFUND_LOOKUP_FAILED` 발견. `refund_status=REQUESTED` 유지, `last_pg_checked_at` 갱신 확인 | 중간 |
| ChatRoom Outbox | 있음 | 공통 outbox conditional claim/token, stale recovery, `outbox_event` unique, `chat_room.reservation_id` unique, `createIfAbsent()` | `outbox_event_id=1` 재처리. 최종 `COMPLETED` 확인. `reservation_id=2`의 ChatRoom 개수 1개 유지. 중복 ChatRoom 생성 없음 | 낮음 |
| Email Outbox | 있음 | 공통 outbox conditional claim/token, `email_outbox_delivery` unique, `markSent(status=PENDING)` 조건부 update | `outbox_event_id=9`/Email Delivery 재처리. 한 Active EC2에서만 처리 로그 확인. SMTP 호출 단계까지 확인. `MailAuthenticationException`으로 재시도 발생. 다른 Active EC2에서 동일 Outbox 처리 로그 미확인 | 중간 |
| ChatMessage Outbox | 있음 | 공통 outbox conditional claim/token, stale recovery, `outbox_event` unique, Kafka producer idempotence, payload `eventId` | 이번 #169 AWS 수동 검증 대상은 아님. 코드 근거로 중복 publish 가능성과 consumer 멱등 처리를 검토 | 중간 |
| Recruitment Deadline | 있음 | 후보 조건 `recruitmentStatus=OPEN`, active reservation status, `findWithLockById()` `PESSIMISTIC_WRITE`, `acceptRecruitmentDeadline()` 가드 | 이번 #169 AWS 수동 검증 대상은 아님. 코드 근거로 동일 예약 중복 마감 방어 확인 | 낮음 |
| Reservation Closing | 있음 | 후보 조건 `ReservationStatus.CONFIRMED`, `findWithLockById()` `PESSIMISTIC_WRITE`, `Reservation.close()` 상태 가드 | 이번 #169 AWS 수동 검증 대상은 아님. 코드 근거로 중복 close 방어 확인 | 낮음 |
| Chat Moderation Kafka Retry | 있음 | 같은 consumer group의 partition 단위 분산, `chat_moderation.chat_message_id` unique, `@Version` optimistic lock, 완료 상태 skip, retry/DLT | 이번 #169 AWS 수동 검증 대상은 아님. Kafka 단일 broker 장애 HA는 이번 범위가 아님 | 중간 |

## 6. WSS / STOMP 검증

브라우저 DevTools에서 실제 운영 endpoint를 확인했다. JWT 원문은 기록하지 않는다.

| 항목 | 결과 |
|---|---|
| WebSocket endpoint | `wss://api.bobfull.click/ws` |
| WebSocket OPEN | 성공 |
| STOMP CONNECT | 성공 |
| CONNECT Frame Authorization header | `Authorization: Bearer <JWT>` 형태 확인 |
| 서버 응답 | `CONNECTED version:1.2` |
| SUBSCRIBE | 정상 수행 |

### 코드/설정 근거

| 항목 | 현재 상태 | 근거 |
|---|---|---|
| STOMP endpoint | `/ws` | `WebSocketConfig.registerStompEndpoints()` |
| Broker | In-memory Simple Broker, `/sub` | `WebSocketConfig.configureMessageBroker()` |
| Application prefix | `/pub` | `WebSocketConfig.configureMessageBroker()` |
| allowedOrigins | `cors.allowed-origins`, prod 기본 `${CORS_ALLOWED_ORIGINS:http://localhost:5173}` | `WebSocketConfig`, `application-prod.yml` |
| HTTP security | `/ws` handshake 경로 `permitAll` | `SecurityConfig.securityFilterChain()` |
| JWT CONNECT 인증 | STOMP CONNECT native header `Authorization: Bearer <token>` 검증 | `ChatStompInterceptor.authenticate()` |
| role 제한 | MEMBER만 STOMP CONNECT 허용 | `ChatStompInterceptor.authenticate()` |
| SUBSCRIBE 인가 | `/sub/chat/rooms/{chatRoomId}`에서 참여 상태 조회 | `ChatStompInterceptor.authorizeSubscribe()` |
| SEND 인가 | `/pub/chat/rooms/{chatRoomId}/messages` destination 형식 및 Principal 확인 | `ChatStompInterceptor.authorizeSend()` |
| outbound 재검증 | 메시지 전달 직전 현재 참여 상태 재조회 후 무효 세션 전달 차단 | `ChatOutboundAuthorizationInterceptor.preSend()` |

### JWT 로그 노출 검토

- `ChatStompInterceptor`는 CONNECT 실패 시 token 원문을 로그로 남기지 않고 reason만 사용한다.
- `JwtAuthenticationFilter`의 invalid token 로그도 request path 수준이며 Authorization header나 token 원문을 남기지 않는다.
- Redis Pub/Sub 실시간 경로의 실패 로그는 `messageId`, `chatRoomId`, 예외 class명 수준이다.
- 현재 코드 기준 STOMP `Authorization` 또는 JWT 원문을 직접 로그에 남기는 경로는 확인되지 않았다.

다중 EC2 환경의 Redis Pub/Sub 기반 채팅 전파는 기존 검증이 완료된 상태다. 이번 PR은 Redis Pub/Sub 자체 구현 PR이 아니라 HTTP App HA와 Blue-Green 배포 자동화 PR이므로, Redis/Valkey 장애 HA나 broker relay 전환은 이번 완료 범위로 표현하지 않는다.

## 7. 범위 및 한계

### 이번 PR에서 말할 수 있는 것

- HTTP Application Layer HA
- ALB 기반 다중 App EC2
- Target Health 기반 장애 우회
- ALB Listener weight 기반 Blue-Green 무중단 배포
- Inactive Target Group 선배포 후 Traffic switch
- Traffic switch 후 public 검증 실패 시 rollback
- 다중 EC2 Scheduler/Outbox 정합성 검토 및 일부 실제 AWS 검증
- WSS/STOMP 운영 연결 성공 확인

### 이번 PR에서 말하지 않는 것

- 전체 시스템 완전 고가용성
- RDS HA 완료
- Kafka HA 완료
- Auto Scaling 동작 검증
- DB Migration 자동화
- Redis/Valkey 장애 HA
- Kafka broker 장애 HA

후속 범위:

- Auto Scaling: Issue #191
- DB Migration: Issue #198
- Kafka/RDS HA 고도화: 별도 인프라 이슈 필요
- Refund Reconciliation의 PortOne `cancellations=null` 처리 보강: 별도 버그/운영 안정화 이슈 필요

## 8. Evidence 링크

- [배포 #13 - 단일 EC2 한계 분석과 다중 EC2 전환 결정](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EB%B0%B0%ED%8F%AC-13-%EB%8B%A8%EC%9D%BC-EC2-%ED%95%9C%EA%B3%84-%EB%B6%84%EC%84%9D%EA%B3%BC-%EB%8B%A4%EC%A4%91-EC2-%EC%A0%84%ED%99%98-%EA%B2%B0%EC%A0%95)
- [배포 #14 - ElastiCache Valkey 기반 공용 Redis 구성](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EB%B0%B0%ED%8F%AC-14-ElastiCache-Valkey-%EA%B8%B0%EB%B0%98-%EA%B3%B5%EC%9A%A9-Redis-%EA%B5%AC%EC%84%B1)
- [배포 #15 - Kafka 전용 EC2 구성 및 애플리케이션 연결](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EB%B0%B0%ED%8F%AC-15-Kafka-%EC%A0%84%EC%9A%A9-EC2-%EA%B5%AC%EC%84%B1-%EB%B0%8F-%EC%95%A0%ED%94%8C%EB%A6%AC%EC%BC%80%EC%9D%B4%EC%85%98-%EC%97%B0%EA%B2%B0)
- [배포 #16 - ALB 기반 다중 EC2 고가용성 구성](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EB%B0%B0%ED%8F%AC-16-ALB-%EA%B8%B0%EB%B0%98-%EB%8B%A4%EC%A4%91-EC2-%EA%B3%A0%EA%B0%80%EC%9A%A9%EC%84%B1-%EA%B5%AC%EC%84%B1)
- [배포 #17 - ALB 기반 Blue-Green 무중단 배포 환경 구성](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-%EB%B0%B0%ED%8F%AC-17-ALB-%EA%B8%B0%EB%B0%98-Blue-Green-%EB%AC%B4%EC%A4%91%EB%8B%A8-%EB%B0%B0%ED%8F%AC-%ED%99%98%EA%B2%BD-%EA%B5%AC%EC%84%B1)
- [트러블슈팅 - 다중 EC2 전환 후 DB Connection Budget 검증](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85-%EB%8B%A4%EC%A4%91-EC2-%EC%A0%84%ED%99%98-%ED%9B%84-DB-Connection-Budget-%EA%B2%80%EC%A6%9D)
- [트러블슈팅 - 다중 EC2 환경 Scheduler / Outbox 중복 실행 검증](https://velog.io/@gpekd5/%EC%B5%9C%EC%A2%85-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85-%EB%8B%A4%EC%A4%91-EC2-%ED%99%98%EA%B2%BD%EC%97%90%EC%84%9C-Scheduler-Outbox-%EC%A4%91%EB%B3%B5-%EC%8B%A4%ED%96%89-%EA%B2%80%EC%A6%9D)

## 9. 이번 문서에서 하지 않은 것

- 애플리케이션 코드 수정 없음
- Scheduler/Outbox 분산 락 추가 없음
- Auto Scaling 구현 없음
- DB Migration 구현 없음
- Secret, JWT, Parameter Store 실제 값 기록 없음
- 전체 시스템 HA 보장 표현 없음
