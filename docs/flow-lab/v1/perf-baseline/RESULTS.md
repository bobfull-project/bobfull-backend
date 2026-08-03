# V1 성능 기준값

기준 Commit: `feebb8c24aac4f6043c89578e5dfa55fa05e8036` (`develop`), 측정일: 2026-08-03 KST.

환경은 Docker MySQL 8.4(local, 3307)와 local Spring Boot(Java 17, 8081)다. HTTP 측정은 비운영 전용 DB `bobfull_perf_121`에 OWNER 1명, 식당 1개, 테이블 1개, 회차 10개, 예약 1개, PAID Payment 1개를 준비해 각 API를 워밍업 5회 뒤 10회 호출했다. 원본 HTTP 시간은 [`http-raw-results.txt`](./http-raw-results.txt)에 보존한다.

| Fixture 데이터 | HTTP 시간 측정 | Statistics 쿼리 수 측정 |
|---|---:|---:|
| 회원 | 1 (OWNER) | 0 (Service 계약에 전달하는 ownerId=1, Member 행은 생성하지 않음) |
| 식당 | 1 | 1 |
| 합석 테이블 | 1 | 1 |
| 식사 회차 | 10 | 2 |
| 예약 | 1 | 1 |
| 참여자 | 0 | 0 |
| PAID Payment | 1 | 1 |
| READY Payment | 0 | 0 |
| Refund | 0 | 0 |
| 정산 대상 예약 | 1 | 1 |

| 시나리오 | HTTP 평균 | p50 | 최소 | 최대 | DB 쿼리 수 |
|---|---:|---:|---:|---:|---|
| 예약 가능 회차 조회 | 19.289ms | 15.367ms | 13.856ms | 32.960ms | 11건 |
| 지급 예정 정산 총액 | 5.763ms | 5.285ms | 4.968ms | 6.980ms | 2건 |
| 예약별 지급 예정 목록 | 13.047ms | 10.806ms | 9.576ms | 21.270ms | 5건 |

HTTP 응답시간은 Statistics를 비활성화한 local 서버에서 기록했고, DB 쿼리 수는 `PerformanceQueryCountIntegrationTest`의 Docker MySQL `performance` 프로필에서 별도로 기록했다. 쿼리 수 Fixture는 격리된 `create-drop` DB에서 위 표의 Statistics 열과 동일한 데이터를 만든다. 이 프로필만 `hibernate.generate_statistics=true`를 적용한다. 각 테스트는 Fixture 생성 후 `Statistics.clear()`를 호출하고 대상 Service 요청을 1회 실행해 `prepareStatementCount`를 읽으며, 응답 데이터도 검증한다. 원본 쿼리 수는 [`query-count-raw.txt`](./query-count-raw.txt)에 보존한다. prod와 일반 local 실행에는 적용하지 않는다.

재현 명령은 다음과 같다. 일반 전체 테스트는 performance 테스트를 환경변수 없이 건너뛴다.

```bash
./gradlew clean test
BOBFULL_PERF_DB_URL=jdbc:mysql://127.0.0.1:3307/bobfull_perf_121 \
BOBFULL_PERF_DB_USERNAME=<local-db-user> \
BOBFULL_PERF_DB_PASSWORD=<local-db-password> \
./gradlew test --tests com.bobfull.performance.PerformanceQueryCountIntegrationTest
```

쿼리 수만으로 N+1 또는 성능 문제를 확정하지 않는다. 이 결과는 현재 V1 코드·Fixture의 스냅샷이며, 개선 후 응답시간·DB 쿼리 수·K6 결과·개선율은 모두 측정 전이다. Local 측정값은 운영 성능이나 최대 처리량을 대표하지 않는다. PR Merge 뒤 Human은 최신 `develop`의 Merge Commit을 확인한 후 `git tag -a v1-performance-baseline <merge-commit> -m "V1 performance baseline"` 및 `git push origin v1-performance-baseline`으로 태그를 생성한다.
