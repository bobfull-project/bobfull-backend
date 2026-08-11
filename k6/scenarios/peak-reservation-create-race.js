// #142 시나리오 B — 예약 버튼 동시 클릭(CREATE 경쟁 범위). CONCURRENT_USERS명이 정확히 같은
// 회차(TimeSlot)에 동시에 최초 예약(CREATE)을 시도한다. 서버는 TimeSlot 단위로 CREATE를
// 배타적으로 막는다(ReservationPreparationService#validateNoActiveCreate) — 정확히 1명만
// 200으로 성공하고 나머지는 409 ACTIVE_RESERVATION_ALREADY_EXISTS를 받아야 한다.
//
// 범위 한계(대화창 논의, Human 결정): 이 시나리오는 CREATE 경쟁만 다룬다. Issue #142 시나리오
// B가 원래 언급하는 "여러 명이 이미 만들어진 예약에 JOIN으로 몰려 좌석초과가 막히는지"는
// 이번 범위에 없다 — `ReservationPreparationService` 클래스 Javadoc에 "결제 성공 전에는
// Reservation·ReservationParticipant를 생성하지 않는다"고 명시돼 있어, JOIN 대상이 되려면
// 최초 참여자의 결제가 실제로 완료돼야 한다. 이 저장소엔 PortOne을 대신할 Fake 결제 확인
// 어댑터가 없고(`PortOneSdkPaymentReader` 구현체 하나뿐), 실제 결제 완료는 PortOne 결제창을
// 통한 카드 결제라 k6로 자동화할 수 없다. Issue #142 "제외 범위"의 "PortOne 실서비스 반복
// 결제 요청"과도 충돌한다. JOIN 기반 좌석초과 테스트는 Fake 결제 확인 어댑터가 별도로
// 추가되면(별도 Issue, Spring 코드 변경) 이어서 다룬다.
//
// CREATE 경쟁은 결제 완료 없이도 완전히 검증 가능하고, "인기 회차가 열리는 순간 몰리는" 상황을
// 오히려 더 정확히 모사한다 — Issue #142가 원래 그리는 시점은 아직 아무도 예약하지 않은
// 회차가 막 공개된 순간이기 때문이다.
//
// 실행 예(Issue #142 "초기 후보 단계": 2 → 5 → 10 → 20 → 50):
//   k6 run -e CONCURRENT_USERS=10 k6/scenarios/peak-reservation-create-race.js
//
// 검증 항목(Issue #142 시나리오 B): 성공 인원이 1명을 초과하지 않는지, 중복 예약이 없는지,
// Reservation·TimeSlot 락 대기시간(서버 로그/Grafana에서 별도 확인), 처리량·오류율.

import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { post } from '../common/helpers.js';
import { check } from 'k6';
import { authHeaders, login, signupMember, uniquePhoneNumber } from '../common/auth.js';
import { createOwnerRestaurantTable, createSessionsBulk, listAvailableSessionIds } from '../common/fixture.js';
import { RUN_ID } from '../common/config.js';

const CONCURRENT_USERS = Number(__ENV.CONCURRENT_USERS || 10);
const BASE_DATE = __ENV.BASE_DATE || new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

export const options = {
    scenarios: {
        peak_create_race: {
            executor: 'per-vu-iterations',
            vus: CONCURRENT_USERS,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

export const createSuccessCount = new Counter('peak_create_race_success');
export const createConflictCount = new Counter('peak_create_race_conflict');
export const createUnexpectedCount = new Counter('peak_create_race_unexpected');

export function setup() {
    const { ownerHeaders, restaurantId, tableId } = createOwnerRestaurantTable('peak-race', 4);
    createSessionsBulk(ownerHeaders, tableId, [BASE_DATE], '00:00', '00:30', 15);
    const sessionIds = listAvailableSessionIds(restaurantId, BASE_DATE);
    if (sessionIds.length === 0) {
        throw new Error('Fixture 회차 생성 실패: 경쟁 대상 sessionId가 없다.');
    }
    const targetSessionId = sessionIds[0];

    const memberTokens = [];
    for (let i = 0; i < CONCURRENT_USERS; i += 1) {
        const email = `peak-race-member-${i}-${RUN_ID}@bobfull.test`;
        const password = 'Perf1234!aA';
        const signupRes = signupMember(email, password, `동시예약회원${i}`, uniquePhoneNumber(email));
        if (signupRes.status !== 201) {
            throw new Error(`Fixture 회원 가입 실패(i=${i}): status=${signupRes.status} body=${signupRes.body}`);
        }
        memberTokens.push(login(email, password));
    }

    const verifierEmail = `peak-race-verifier-${RUN_ID}@bobfull.test`;
    const verifierPassword = 'Perf1234!aA';
    const verifierSignupRes = signupMember(verifierEmail, verifierPassword, '검증용회원', uniquePhoneNumber(verifierEmail));
    if (verifierSignupRes.status !== 201) {
        throw new Error(`Fixture 검증용 회원 가입 실패: status=${verifierSignupRes.status} body=${verifierSignupRes.body}`);
    }
    const verifierToken = login(verifierEmail, verifierPassword);

    return { restaurantId, date: BASE_DATE, targetSessionId, memberTokens, verifierToken };
}

export default function (data) {
    // per-vu-iterations executor에서 각 VU는 정확히 1회만 실행되므로, VU 순번(1-base)을
    // 그대로 회원 풀 인덱스로 쓴다 — 회원마다 각자 한 번씩만 CREATE를 시도한다.
    const memberIndex = exec.vu.idInTest - 1;
    const token = data.memberTokens[memberIndex];

    const res = post('peak_create_race', '/api/reservations/prepare', {
        type: 'CREATE',
        targetId: data.targetSessionId,
        partySize: 1,
    }, authHeaders(token));

    if (res.status === 200) {
        createSuccessCount.add(1);
        check(res, { 'peak_create_race: 성공 응답 body.success': (r) => JSON.parse(r.body).success === true });
    } else if (res.status === 409 && bodyCode(res) === 'ACTIVE_RESERVATION_ALREADY_EXISTS') {
        createConflictCount.add(1);
    } else {
        createUnexpectedCount.add(1);
        check(res, { [`peak_create_race: 예상 밖 응답(status=${res.status})`]: () => false });
    }
}

export function teardown(data) {
    // 정합성 검증(Issue #142 "정합성 검증"): CREATE는 결제가 실제로 완료돼야 Reservation이
    // 생성되므로(ReservationPreparationService 클래스 Javadoc), prepare 성공(200) 자체는
    // Reservation 생성을 의미하지 않는다 — 이 스크립트는 결제 완료를 다루지 않는다(범위 한계,
    // 파일 상단 주석 참고). 대신 "이 TimeSlot에 대한 CREATE 배타 선점이 경쟁 종료 후에도
    // 여전히 유효한지"를 새 회원의 CREATE 재시도로 독립 검증한다 — 승자가 있었다면 이 마지막
    // 시도는 반드시 409 ACTIVE_RESERVATION_ALREADY_EXISTS를 받아야 한다.
    const res = post('peak_create_race', '/api/reservations/prepare', {
        type: 'CREATE',
        targetId: data.targetSessionId,
        partySize: 1,
    }, authHeaders(data.verifierToken), 'peak_create_race_verify');

    if (res.status === 409 && bodyCode(res) === 'ACTIVE_RESERVATION_ALREADY_EXISTS') {
        console.log(`teardown 검증 성공: 회차 ${data.targetSessionId}는 경쟁 종료 후에도 CREATE 배타 선점이 유지된다(정합성 위반 없음).`);
    } else {
        console.error(
            `teardown 검증 실패: 회차 ${data.targetSessionId}에 대한 검증용 CREATE 재시도가 409가 아니었다 ` +
            `(status=${res.status}, body=${res.body}) — 승자가 없었거나(전원 실패) 배타 선점이 깨졌을 수 있다.`
        );
    }
}

function bodyCode(res) {
    try {
        return JSON.parse(res.body).code;
    } catch (e) {
        return null;
    }
}
