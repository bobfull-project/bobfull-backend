# Issue #191 Auto Scaling 필요성 검증 Evidence

## 검증 대상

#191은 App EC2 Auto Scaling을 바로 적용하는 작업이 아니라, 현재 활성 App EC2 2대의 실제 병목을 먼저 확인한 뒤 Auto Scaling 필요 여부를 판단하는 작업이다.

이번 PR은 Stress Test에서 첫 병목이 App CPU가 아니라 Hikari Connection Pool 대기로 관측된 상태를 기록하고, 평상시 inactive Blue/Green EC2가 RDS Connection을 점유하지 않도록 Blue-Green 운영 구조를 개선한다.

## 측정 계약

- Primary KPI: 평상시 inactive EC2 STOP으로 inactive Hikari Connection 제거, 동일 Stress Test 재측정 시 Hikari Pending과 Connection Acquire Latency 변화 확인
- Secondary KPI: `Threads_connected`, Hikari Active, App CPU, RDS CPU, RDS ReadLatency, avg, p95, p99, max, HTTP 실패율, dropped iterations
- Guardrail: Blue-Green 배포의 SSM deploy, Target Group health, ALB traffic switch, public readiness/API 검증, Prometheus Active target 갱신/UP 확인, rollback fail-safe 유지

## 기준 코드

- Before SHA: `cdc7f5b1008cb8ac5987d9c8dbebf900f2efdb07`
- After SHA: 코드 변경 및 배포 검증 후 갱신 예정

## 환경·데이터·실행 조건

### Load Test

```text
20 iterations/s
5분
```

### Stress Test

```text
최대 부하 320 iterations/s
```

### RDS / App 구조

| 항목 | 값 |
|---|---:|
| RDS `max_connections` | 60 |
| RDS `Threads_connected` | 약 45 |
| Hikari `maximumPoolSize` | 10 |
| Active App EC2 | 2대 |
| Inactive App EC2 | 2대 |
| App 최대 Connection 계산 | 4대 x 10 = 약 40 |

## Before 결과

### Load Test 결과

| 지표 | 결과 |
|---|---:|
| iterations | 6,001 |
| HTTP requests | 6,006 |
| failure rate | 0% |
| avg | 18.83ms |
| p95 | 23.24ms |
| p99 | 64.44ms |
| max | 448.51ms |

일반 부하에서는 Hikari Active Connection이 대부분 0~1 수준으로 유지되어 병목이 확인되지 않았다.

### Stress Test 결과

| 지표 | 결과 |
|---|---:|
| iterations | 82,092 |
| HTTP requests | 82,097 |
| http_req_failed | 0% |
| avg | 32.35ms |
| p95 | 77.82ms |
| p99 | 310.05ms |
| max | 1.22s |
| dropped_iterations | 107 |

### 병목 지표

| 지표 | 결과 |
|---|---:|
| App CPU | 약 38~42% |
| RDS CPU | 피크 약 20.6% |
| RDS ReadLatency | 피크 약 2.5ms |
| Hikari Active | 최대 10/10 |
| Hikari Pending | 약 40~50+ |
| Connection Acquire Latency | 순간 최대 약 180ms |
| Hikari Connection Timeout | 0 |

현재까지 App CPU 포화, RDS CPU 포화, ALB 요청 편중보다 Hikari Pool 최대치 도달과 Pending 증가가 먼저 확인됐다.

### PROCESSLIST 해석

PROCESSLIST에서 Active Green 2대와 Inactive Blue 2대가 각각 약 10 Connection을 유지했다.

```text
Active Green 2대 x Hikari 10 = 최대 20
Inactive Blue 2대 x Hikari 10 = 최대 20
App 최대 Connection = 약 40
```

Inactive Blue 환경은 사용자 트래픽을 받지 않지만 Spring Boot/Hikari가 실행 중이면 DB Connection을 유지한다. 따라서 RDS `max_connections=60` 상태에서 Auto Scaling으로 App을 추가하기 전에 inactive 환경의 불필요한 Connection 점유를 먼저 제거한다.

## 변경 내용

- `scripts/aws/deploy-backend-blue-green-v1.sh`
  - ALB Listener weight 기준 active/inactive Target Group 판별 유지
  - 배포 시작 시점의 active instance id를 별도로 저장
  - inactive Target Group EC2가 `stopped`이면 START
  - inactive Target Group EC2가 `pending`이면 running까지 대기
  - inactive Target Group EC2가 `stopping`이면 stopped까지 대기 후 START
  - 기타 상태는 오류로 실패
  - EC2 running 이후 SSM `PingStatus=Online`까지 polling
  - public 검증 성공 후 rollback window 동안 기존 active EC2 유지
  - 새 Active Target Group의 EC2 private IP 2개 조회
  - Monitoring EC2에 SSM 명령으로 Prometheus `bobfull-backend` scrape target 갱신
  - Prometheus `/-/reload` 호출
  - 새 Active target 2대가 모두 `up=1`일 때만 기존 active STOP 단계 진행
  - Prometheus target 갱신 또는 UP 확인 실패 시 기존 active EC2 STOP 금지
  - rollback window 종료 후 배포 시작 시점에 저장한 active instance id만 STOP
  - rollback 발생 또는 rollback 시도 시 기존 active EC2 STOP 금지
- `.github/workflows/deploy-backend-v1.yml`
  - EC2 상태 대기, SSM Online 대기, 이전 active 유지 시간, Monitoring EC2/Prometheus 갱신 변수를 전달
- `docs/deployment/aws-v1-backend.md`
  - 운영 흐름, GitHub Variables, IAM 권한, 성공 조건 업데이트
- `docs/operations/monitoring-runbook.md`
  - 수동 target 재기동 기준을 Blue-Green 자동 target 갱신과 reload 기준으로 업데이트

## After 결과

코드 변경 후 실제 AWS 배포와 동일 Stress Test 재측정이 필요하다. 이 문서에서는 아직 After 성능 개선을 PASS로 기록하지 않는다.

확인할 항목:

```text
Threads_connected
Hikari Active
Hikari Pending
Connection Acquire Latency
App CPU
RDS CPU
RDS ReadLatency
avg
p95
p99
max
HTTP 실패율
Dropped Iterations
```

## 정합성 회귀 검증

이번 PR에서 확인할 Guardrail:

- inactive EC2 START 실패 시 Listener traffic 전환 없음
- EC2 running 대기 실패 시 Listener traffic 전환 없음
- SSM Online 실패 시 Listener traffic 전환 없음
- SSM deploy 실패 시 Listener traffic 전환 없음
- Target Group health 실패 시 Listener traffic 전환 없음
- ALB 전환 확인 실패 시 rollback 시도
- public readiness/API 검증 실패 시 rollback 시도
- public 검증 실패 rollback 시 Prometheus target 변경 전이므로 이전 Active target 유지
- Prometheus target 갱신 실패 시 기존 active EC2 STOP 금지
- Prometheus 신규 Active target 2대 중 하나라도 UP 확인 실패 시 기존 active EC2 STOP 금지
- rollback 발생 또는 rollback 시도 시 기존 active EC2 STOP 금지
- 정상 검증 후 stop 대상은 배포 시작 시점의 active instance id로 고정

## 구조화 로그·메트릭

이번 스크립트는 GitHub Actions 로그에 다음을 남긴다.

- Blue/Green Target Group weight
- 배포 시작 시점 active/inactive Target Group
- 배포 시작 시점 active instance ids
- inactive instance ids
- inactive EC2 state summary
- SSM Online status summary
- Target Group health summary
- Listener weight 확인 summary
- 새 Active EC2 private IP
- Prometheus target 갱신 대상과 file_sd target preview
- Prometheus target별 `UP`/`DOWN` 확인 결과
- 이전 active EC2 유지 시간
- 이전 active EC2 stop 대상과 stopped 확인 summary

## 결과 해석

현재 수치만으로는 App EC2 Auto Scaling을 바로 적용할 근거가 부족하다. Stress Test에서 첫 병목은 App CPU가 아니라 Hikari Connection Pool 대기로 관측됐다.

따라서 평상시 inactive EC2를 STOP해 RDS Connection Budget을 확보한 뒤 동일 조건 Stress Test를 재측정한다. 재측정 후 판단 기준은 다음과 같다.

```text
Hikari 병목 지속
→ Connection Pool / DB 구조 추가 검토

Hikari 안정
+
App 자체 병목 발생
→ Auto Scaling 적용 검토

Hikari 안정
+
App CPU/처리량에도 여유
→ 현재 단계에서는 Auto Scaling 미적용
```

## 검증 한계

- 현재 문서는 Before 측정값과 개선 계획을 기록한다.
- After 배포 검증과 동일 Stress Test는 아직 수행하지 않았다.
- Hikari `maximumPoolSize=10` 자체는 이번 PR에서 변경하지 않는다.
- Auto Scaling 정책, ASG, Launch Template, Scheduled Scaling은 이번 PR 범위가 아니다.
- 실제 Secret, DB 비밀번호, 토큰, 계정 Key는 기록하지 않는다.

## 관련

- Issue: #191
- PR: #276
- 선행 Evidence: `docs/evidence/v3/169-app-ha/README.md`
