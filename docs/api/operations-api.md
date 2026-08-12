# 운영 Endpoint / Webhook

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## PortOne Webhook

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `POST` | `/api/webhooks/portone` | `PUBLIC` | Raw `String` body; headers `webhook-id?`, `webhook-signature?`, `webhook-timestamp?` | `ResponseEntity<Void>` | 200 / 400 |

Controller 파라미터의 세 webhook header는 `required=false`지만 Controller가 직접 누락 여부를 검사한다. 누락 또는 서명 검증 실패 시 `400`을 반환한다. 이 Endpoint는 `ApiResponse<T>`를 사용하지 않는다.

## Actuator

| Method | Path | Auth | Response |
|---|---|---|---|
| `GET` | `/actuator/health` | `PUBLIC` | Spring Boot Actuator Health JSON |
| `GET` | `/actuator/prometheus` | `PUBLIC` | Prometheus text exposition |

## 문서 경계

- 위 Endpoint는 외부/운영 HTTP 계약만 문서화한다.
- Prometheus Metric 이름, Grafana Dashboard, Slack Alert, ALB/Auto Scaling 검증 결과는 Evidence/운영 문서에서 관리한다.
- PortOne 결제 완료·환불 비즈니스 API는 [`payment-api.md`](payment-api.md)를 참조한다.