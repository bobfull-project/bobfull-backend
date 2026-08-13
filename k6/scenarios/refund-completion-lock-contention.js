// Issue #146 시나리오 B — 동일 Reservation 락 경쟁.
//
// 한 Reservation에 속한 여러 참여자(A/B/C...)가 각자의 환불 완료를 동시에 요청할 때, 모두
// `ReservationCancellationCompletionService.complete()`에서 같은 Reservation 행 비관적 락으로
// 직렬화된다(`findWithLockById`). 이 시나리오는 그 직렬화 비용(참여자 수가 늘수록 마지막
// 참여자가 얼마나 더 기다리는지)을 측정한다.
//
// k6 `http.batch()`로 한 그룹의 모든 참여자 취소 요청을 진짜로 동시에 쏴야 직렬화가 실제로
// 재현된다 — 서로 다른 VU/iteration에 나눠 보내면 도착 시각이 우연에 좌우돼 락 경쟁이 보장되지
// 않는다(Issue #63 "동시성 재현" 원칙과 동일한 이유).
//
// 정합성(모든 참여자가 정확히 한 번만 CANCELLED되는지, 마지막 참여자만 Reservation을 확정하는지)
// 은 이미 `ReservationCancellationCompletionServiceTest` 등 기존 유닛 테스트가 보장한다 — 이
// 스크립트는 그 위에서 실제 동시 부하일 때의 지연·처리량만 측정한다.
//
// 실행 예:
//   k6 run -e STAGE=smoke k6/scenarios/refund-completion-lock-contention.js

import exec from 'k6/execution';
import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { check } from 'k6';
import { BASE_URL, STAGE, scenarioTags } from '../common/config.js';
import { buildCreateTargetPool } from '../common/fixture.js';
import { createMemberPool, prepareGroupReservation } from '../common/refundFixture.js';

// fixture.js의 SharedTable capacity 기본값(8)을 넘으면 JOIN이 정원 초과로 실패한다.
const PARTICIPANTS_PER_GROUP = Number(__ENV.PARTICIPANTS_PER_GROUP || 3);

const STAGE_OPTIONS = {
    smoke: {
        executor: 'per-vu-iterations',
        vus: 1,
        iterations: 3,
        maxDuration: '30s',
    },
    load: {
        executor: 'constant-arrival-rate',
        rate: Number(__ENV.LOAD_RATE || 10),
        timeUnit: '1s',
        duration: __ENV.LOAD_DURATION || '5m',
        preAllocatedVUs: Number(__ENV.LOAD_PRE_ALLOCATED_VUS || 40),
        maxVUs: Number(__ENV.LOAD_MAX_VUS || 100),
    },
    stress: {
        executor: 'ramping-arrival-rate',
        startRate: Number(__ENV.STRESS_START_RATE || 10),
        timeUnit: '1s',
        preAllocatedVUs: Number(__ENV.STRESS_PRE_ALLOCATED_VUS || 200),
        maxVUs: Number(__ENV.STRESS_MAX_VUS || 400),
        stages: [
            { target: 20, duration: '3m' },
            { target: 40, duration: '3m' },
            { target: 80, duration: '3m' },
            { target: 160, duration: '3m' },
        ],
    },
};

export const options = {
    scenarios: { refund_lock_contention: STAGE_OPTIONS[STAGE] },
    setupTimeout: __ENV.SETUP_TIMEOUT || '180s',
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// 그룹 전체(참여자 전원 동시 취소)가 끝나는 데 걸린 전체 시간과, 참여자별 개별 응답 시간을
// 나눠 기록한다 — 직렬화 비용이 있다면 늦게 락을 잡는 참여자일수록 개별 응답 시간이 늘어난다.
export const groupCancelBatchDuration = new Trend('refund_group_cancel_batch_duration');
export const participantCancelDuration = new Trend('refund_group_cancel_participant_duration');

const GROUP_POOL_SIZE = Number(__ENV.GROUP_POOL_SIZE || { smoke: 3, load: 1500, stress: 3000 }[STAGE]);
const BASE_DATE = __ENV.BASE_DATE || new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

export function setup() {
    const { sessionIds } = buildCreateTargetPool('refund-b', GROUP_POOL_SIZE, BASE_DATE);
    const memberTokens = createMemberPool('refund-b', GROUP_POOL_SIZE * PARTICIPANTS_PER_GROUP);

    const groups = sessionIds.map((sessionId, i) => {
        const groupTokens = memberTokens.slice(
            i * PARTICIPANTS_PER_GROUP, (i + 1) * PARTICIPANTS_PER_GROUP);
        return prepareGroupReservation('refund_lock_contention', groupTokens, sessionId);
    });

    return { groups };
}

export default function (data) {
    const globalIteration = exec.scenario.iterationInTest;
    if (globalIteration >= data.groups.length) {
        throw new Error(
            `그룹 Fixture 풀 소진: iteration=${globalIteration} poolSize=${data.groups.length} — GROUP_POOL_SIZE를 늘려 재실행하라.`
        );
    }
    const group = data.groups[globalIteration];

    const requests = group.participants.map((participant) => ({
        method: 'POST',
        url: `${BASE_URL}/api/reservations/${group.reservationId}/participations/me/cancel`,
        body: JSON.stringify({ reason: '성능 측정(시나리오 B, 동일 Reservation 락 경쟁)' }),
        params: {
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${participant.memberToken}` },
            tags: scenarioTags('refund_lock_contention', { name: 'refund_cancel_group_participant' }),
        },
    }));

    const batchStart = Date.now();
    const responses = http.batch(requests);
    groupCancelBatchDuration.add(Date.now() - batchStart);

    responses.forEach((res, i) => {
        participantCancelDuration.add(res.timings.duration);
        check(res, {
            'refund_cancel_group_participant status is 200': (r) => r.status === 200,
            'refund_cancel_group_participant body.success is true': (r) => {
                try {
                    return JSON.parse(r.body).success === true;
                } catch (e) {
                    return false;
                }
            },
        }, { participantIndex: String(i) });
    });
}
