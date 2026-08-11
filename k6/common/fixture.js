// #63 공통 K6 Harness — 시나리오가 필요한 Restaurant/SharedTable/DiningSession Fixture를
// 실제 API 호출로 만든다(docs/BOBFULL_API_SPEC_COMPLETE.md 3-x, 4-x, 5-4절).
//
// 상태를 소비하는 예약 준비(CREATE) 시나리오는 반복 호출마다 "아직 아무도 선점하지 않은"
// DiningSession이 필요하다(같은 세션에 두 번째 CREATE는 409 ACTIVE_RESERVATION_ALREADY_EXISTS,
// ReservationPreparationService#validateNoActiveCreate). 이번 Fixture는 그 풀(pool)을
// 충분히 만들어 "같은 데이터 반복 호출로 멱등 응답/409만 측정하는" 위험(Issue #63 "테스트 데이터
// 계약")을 피한다.

import { post, get, parseData } from './helpers.js';
import { signupOwner, login, authHeaders, uniquePhoneNumber } from './auth.js';
import { RUN_ID } from './config.js';

/**
 * Owner 1명 + Restaurant 1개 + SharedTable 1개를 새로 만든다.
 * capacity는 partySize 부하를 고려해 넉넉하게 잡는다(기본 8명).
 */
export function createOwnerRestaurantTable(prefix, capacity) {
    const email = `${prefix}-owner-${RUN_ID}@bobfull.test`;
    const password = 'Perf1234!aA';
    const signupRes = signupOwner(email, password, `${prefix} 사장님`, uniquePhoneNumber(email), `biz-${RUN_ID}-${prefix}`);
    if (signupRes.status !== 201) {
        throw new Error(`Fixture Owner 가입 실패: status=${signupRes.status} body=${signupRes.body}`);
    }
    const ownerToken = login(email, password);
    const ownerHeaders = authHeaders(ownerToken);

    const restaurantRes = post('fixture', '/api/owner/restaurants', {
        name: `${prefix} K6 성능테스트 식당 ${RUN_ID}`,
        address: '서울시 성능테스트로 1',
        category: '한식',
        description: 'K6 성능 테스트용 Fixture 식당(#63)',
        keyword: prefix,
        depositPerPerson: 10000,
    }, ownerHeaders, 'fixture_create_restaurant');
    if (restaurantRes.status !== 201) {
        throw new Error(`Fixture 식당 생성 실패: status=${restaurantRes.status} body=${restaurantRes.body}`);
    }
    const restaurantId = parseData(restaurantRes).restaurantId;

    const tableRes = post('fixture', `/api/owner/restaurants/${restaurantId}/tables`, {
        capacity: capacity || 8,
    }, ownerHeaders, 'fixture_create_table');
    if (tableRes.status !== 201) {
        throw new Error(`Fixture 테이블 생성 실패: status=${tableRes.status} body=${tableRes.body}`);
    }
    const tableId = parseData(tableRes).tableId;

    return { ownerHeaders, restaurantId, tableId };
}

/**
 * dates 각각에 startTime~endTime 구간을 intervalMinutes 간격으로 나눠 DiningSession을 만든다.
 * 벌크 응답은 개수만 반환하므로(DiningSessionBulkResponse), 실제 sessionId 목록은
 * 이어서 listAvailableSessions로 다시 조회해야 한다.
 */
export function createSessionsBulk(ownerHeaders, tableId, dates, startTime, endTime, intervalMinutes) {
    const res = post('fixture', `/api/owner/tables/${tableId}/dining-sessions/bulk`, {
        dates, startTime, endTime, intervalMinutes,
    }, ownerHeaders, 'fixture_create_sessions_bulk');
    if (res.status !== 201) {
        throw new Error(`Fixture 회차 생성 실패: status=${res.status} body=${res.body}`);
    }
    return parseData(res).createdSessionCount;
}

/** 아직 예약이 없는(reservationId === null) 회차만 CREATE Fixture 풀로 사용한다. */
export function listAvailableSessionIds(restaurantId, date) {
    const res = get('fixture', `/api/restaurants/${restaurantId}/dining-sessions?date=${date}`, {}, 'fixture_list_sessions');
    if (res.status !== 200) {
        throw new Error(`Fixture 회차 조회 실패: status=${res.status} body=${res.body}`);
    }
    return parseData(res).content
        .filter((session) => session.reservationId === null)
        .map((session) => session.sessionId);
}

/**
 * poolSize 이상의 미사용 DiningSession을 만들어 sessionId 배열로 돌려준다.
 * 하루 24시간을 intervalMinutes 간격으로 나눠도 부족하면 dates를 늘려 재시도한다.
 */
export function buildCreateTargetPool(prefix, poolSize, baseDate) {
    const { ownerHeaders, restaurantId, tableId } = createOwnerRestaurantTable(prefix, 8);

    const intervalMinutes = 30;
    let dayOffset = 0;
    let sessionIds = [];
    while (sessionIds.length < poolSize && dayOffset < 30) {
        const date = addDays(baseDate, dayOffset);
        createSessionsBulk(ownerHeaders, tableId, [date], '00:00', '23:30', intervalMinutes);
        sessionIds = sessionIds.concat(listAvailableSessionIds(restaurantId, date));
        dayOffset += 1;
    }
    if (sessionIds.length < poolSize) {
        throw new Error(`Fixture 회차 풀이 부족하다: 필요=${poolSize} 생성=${sessionIds.length} (dayOffset 상한 30일 도달)`);
    }
    return { restaurantId, sessionIds };
}

function addDays(dateStr, days) {
    const d = new Date(`${dateStr}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
}
