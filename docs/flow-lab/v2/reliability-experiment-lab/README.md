# Reliability Experiment Lab

이 Lab은 실제 정상 흐름 재생기가 아니다. 4개 실험에서 `develop merged · 실제 채택`과 `비교용 가상 대안`을 구분한다.

1. 마지막 좌석 동시성·락 순서
2. 취소·환불 Transaction 경계
3. ChatRoom AFTER_COMMIT 생성
4. Scheduler와 요청 시점 시간 경계

가상 대안은 실제 BobFull Java 코드나 성능 측정 결과가 아니다. K6, 검색 Redis cache, Prometheus/Grafana 수치는 V2의 완료 결과로 표시하지 않는다.
