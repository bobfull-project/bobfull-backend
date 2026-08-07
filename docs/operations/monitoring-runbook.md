# Prometheus·Grafana 모니터링 운영 절차

## 구성 기준

- Prometheus와 Grafana는 애플리케이션 EC2와 분리된 Monitoring EC2 1대에서 Docker Compose로 실행한다.
- Prometheus는 App EC2의 `/actuator/prometheus`를 scrape한다.
- Grafana는 Prometheus Data Source, BobFull Overview Dashboard, 초기 Grafana Alert Rule, Slack Contact Point를 provisioning으로 로드한다.
- Slack으로 발송되는 운영 Alert의 기준은 Grafana Alert Rule이다. Prometheus는 Spring Boot 메트릭 scrape와 Grafana Data Source 역할만 맡는다.
- Slack Webhook URL, Grafana 관리자 비밀번호 같은 비밀값은 `monitoring/.env` 또는 운영 비밀 저장소에만 둔다.

## 네트워크

1. App EC2 Security Group은 Monitoring EC2의 private IP 또는 Security Group에서 `8080` 접근을 허용한다.
2. Monitoring EC2 Security Group은 운영자 접근용 `3000`(Grafana)과 필요 시 `9090`(Prometheus)을 제한된 IP에서만 허용한다.
3. Prometheus Target은 public IP가 아니라 App EC2 private IP 또는 private DNS를 사용한다.

## 배포

```bash
cd monitoring
cp .env.example .env
vi .env
docker compose up -d
```

필수 값:

```text
BOBFULL_BACKEND_METRICS_TARGET=<app-ec2-private-ip>:8080
GRAFANA_ADMIN_PASSWORD=<strong-password>
GRAFANA_SLACK_WEBHOOK_URL=<slack-incoming-webhook-url>
GRAFANA_SLACK_RECIPIENT=<slack-channel-name>
```

## 확인 순서

1. App EC2에서 `/actuator/health`가 `UP`인지 확인한다.
2. App EC2에서 `/actuator/prometheus`가 Prometheus text format을 반환하는지 확인한다.
3. Monitoring EC2 Prometheus UI에서 `Status → Targets → bobfull-backend`가 `UP`인지 확인한다.
4. Grafana에서 Prometheus Data Source `Save & test`가 성공하는지 확인한다.
5. Grafana `BobFull Overview` Dashboard에서 HTTP, JVM, DB, business event 패널이 표시되는지 확인한다.
6. Grafana Contact Point `slack-monitoring`의 `Test`를 실행해 Slack 모니터링 채널 수신을 확인한다.
7. 테스트용 임계치를 낮추거나 테스트 이벤트를 발생시켜 Alert Rule이 `Firing`으로 전환되고 Slack에 전달되는지 확인한다.

## 주요 PromQL

```promql
up{job="bobfull-backend"}
sum by (status) (rate(http_server_requests_seconds_count{job="bobfull-backend"}[1m]))
histogram_quantile(0.95, sum by (uri, le) (rate(http_server_requests_seconds_bucket{job="bobfull-backend"}[5m])))
sum(jvm_memory_used_bytes{job="bobfull-backend",area="heap"}) / sum(jvm_memory_max_bytes{job="bobfull-backend",area="heap"})
max(hikaricp_connections_pending{job="bobfull-backend"})
sum by (event) (increase(bobfull_business_events_total[5m]))
```

## Alert 기준

- 운영 Alert는 `monitoring/grafana/provisioning/alerting/alert-rules.yml`에서 관리하며, 각 Rule은 `slack-monitoring` Contact Point로 연결한다.
- Prometheus Alert Rule은 별도로 로드하지 않는다. 동일 조건을 Prometheus와 Grafana 양쪽에서 중복 평가하지 않기 위함이다.
- 1순위 비즈니스 사건은 5분 안에 1건 이상 발생하면 즉시 확인한다.
- `LOGIN_FAILED`, p95, 오류율, JVM Heap, DB Connection Pending은 초기 검증용 임계치로 시작하고 실제 AWS 기준 데이터 측정 후 조정한다.
- 개별 `paymentId`, `refundId`, `reservationId`, `memberId`, email, client IP는 Prometheus Label로 넣지 않는다. 상세 원인은 CloudWatch 구조화 로그에서 확인한다.

## 기준 데이터 측정

실제 AWS 단일 App EC2 환경에서 k6로 지정 API에 부하를 발생시킨 뒤 `docs/operations/monitoring-baseline-template.md`에 결과를 기록한다.

기록 대상:

- 요청량
- 평균 응답시간
- p95 응답시간
- 오류율
- JVM CPU/Memory
- DB Connection Pool active/idle/pending
- 테스트 조건과 실행 시각
