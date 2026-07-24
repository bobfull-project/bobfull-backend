# ADR 운영 기준

## 1. 목적

ADR(Architecture Decision Record)은 여러 대안을 비교한 뒤 프로젝트에 영향을 주는 기술·구조 결정을 기록하는 문서다. 이미 확정된 정책을 반복하거나 아직 결정하지 않은 후보 구조를 미리 고정하는 문서가 아니다.

## 2. 작성 대상과 비대상

다음처럼 기준 문서의 변경 또는 별도 합의가 필요한 선택은 ADR로 기록한다.

- 외부 시스템·저장소·메시징·캐시 도입 또는 교체
- 데이터 정합성에 영향을 주는 트랜잭션·동시성 방식 선택
- 운영·배포·관찰 방식의 중요한 변경
- 도메인 간 책임이나 장기 유지보수 비용에 큰 영향을 주는 구조 선택

다음은 ADR 대상이 아니다.

- API 명세·ERD·프로젝트 컨텍스트에 이미 확정된 내용을 다시 설명하는 경우
- 단일 Issue 안의 국소적인 구현·명명·포맷 변경
- 근거와 결정이 없는 기술 후보 나열
- 실제 선택 전의 빈 ADR 파일 사전 생성

## 3. 파일명과 상태

- 파일명은 `NNNN-간결한-kebab-case-제목.md` 형식을 사용한다. 예: `0001-payment-webhook-idempotency.md`
- 번호는 `docs/adr/` 안에서 순차 증가한다.
- 상태값은 `Proposed`, `Accepted`, `Superseded`, `Rejected`를 사용한다.
- `Superseded` 문서는 삭제하지 않고, 대체한 ADR 번호·링크를 남긴다.

## 4. 작성·검토·변경 절차

1. 관련 Issue에서 해결할 문제, 제약, 대안을 확인한다.
2. 기준 문서와 충돌 여부를 검토한다. 정책·API·데이터 모델 변경이 필요하면 해당 문서를 먼저 또는 함께 갱신할 범위를 합의한다.
3. ADR 초안을 작성하고 Human이 결정 내용을 검토한다.
4. 결정된 ADR을 `Accepted`로 기록하고, 구현·검증·PR에서 ADR 링크와 실제 적용 범위를 연결한다.
5. 이후 결정이 바뀌면 기존 문서를 수정해 이력을 지우지 말고 새 ADR을 작성해 이전 문서를 `Superseded`로 변경한다.

## 5. 최소 템플릿

```md
# ADR NNNN: 제목

- 상태: `Proposed | Accepted | Superseded | Rejected`
- 작성일: `YYYY-MM-DD`
- 관련 Issue·PR: #번호, PR #번호

## 배경

## 문제

## 고려한 대안

## 결정

## 선택 이유

## 장점

## 단점과 위험

## 검증 방법

## 후속 작업
```

## 6. 현재 범위

이 디렉터리는 운영 기준과 템플릿만 제공한다. 현재 확정되지 않은 배포·AWS·Redis·Kafka·구체적인 락·트랜잭션 방식에 대한 개별 ADR은 생성하지 않는다.

## 7. 현재 ADR

- [ADR 0001: 예약 좌석 정합성과 임시 선점 전략](./0001-reservation-seat-consistency.md)
- [ADR 0002: 결제 완료 API와 PortOne 웹훅의 멱등성 경계](./0002-payment-completion-idempotency.md)
- [ADR 0003: UTC Instant 저장과 Clock 주입 시간 전략](./0003-utc-instant-and-clock.md)
- [ADR 0004: Java 17 프로젝트 기준](./0004-use-java-17.md)
