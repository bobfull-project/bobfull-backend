# GitHub Rules

## 1. 브랜치 구조

| 브랜치 | 역할 | 병합 대상 |
|---|---|---|
| `main` | 배포 가능한 안정 버전 관리 | - |
| `develop` | 개발 완료 기능 통합 및 검증 | `main` |
| `feature/*` | 새로운 기능 개발 | `develop` |

### 작업 흐름

1. `develop` 브랜치를 기준으로 `feature/*` 브랜치를 생성한다.
2. `feature/*` 브랜치에서 기능을 개발한다.
3. 개발 완료 후 `develop` 브랜치로 PR을 올린다.
4. 팀원이 코드 리뷰를 진행한다.
5. 최소 2명 이상의 Approve를 받은 후 PR 작성자가 Merge한다.
6. `develop`에서 기능 통합 및 테스트를 진행한다.
7. 배포 가능한 상태가 되면 `develop`에서 `main`으로 PR을 올린다.
8. `main` 병합 후 배포를 진행한다.

```text
develop에서 feature 브랜치 생성
        ↓
feature 브랜치에서 기능 개발
        ↓
develop으로 PR 생성
        ↓
2명 이상 Approve
        ↓
PR 작성자가 Merge
        ↓
develop에서 통합 테스트
        ↓
develop → main PR
        ↓
main 병합 후 배포
```

### 초기 저장소 설정 예외

`develop` 브랜치가 아직 생성되지 않은 최초 설정 단계에서는 팀 규칙·AI 워크플로우·Issue/PR 템플릿 등 저장소 기반 문서를 `feature/*` 브랜치에서 작성하고 `main`으로 PR을 올릴 수 있다.

초기 설정 PR을 `main`에 Merge한 뒤 최신 `main`에서 `develop`을 생성한다. 이후 기능 개발부터는 일반 브랜치 흐름을 따른다.

```text
초기 설정 feature 브랜치
→ main PR
→ 리뷰·승인·Merge
→ 최신 main에서 develop 생성
→ 이후 feature/* → develop
```

### 브랜치 이름

```text
main
  develop
    ├── feature/member-signup
    ├── feature/auth-login
    ├── feature/restaurant-register
    ├── feature/table-manage
    ├── feature/timeslot-manage
    ├── feature/reservation-create
    ├── feature/payment-deposit
    ├── feature/refund-cancel
    ├── feature/checkin-manage
    ├── feature/noshow-manage
    ├── feature/chat-reservation
    └── feature/deploy-aws
```

### 브랜치 네이밍 컨벤션

- 영어 소문자로 작성한다.
- 슬래시(`/`)와 하이픈(`-`)을 사용한다.
- `feature/도메인-기능명` 형식으로 작성한다.
- 너무 길지 않게 핵심 단어만 사용한다.
- 브랜치명에는 담당자 이름을 사용하지 않는다.

## 2. 커밋 컨벤션

```text
feat: 제목
```

| 타입 | 설명 | 예시 |
|---|---|---|
| `feat` | 새로운 기능 추가 | `feat: 로그인 페이지 구현` |
| `fix` | 버그 수정 | `fix: 비밀번호 유효성 검사 오류 수정` |
| `docs` | 문서 수정 | `docs: API 명세서 업데이트` |
| `style` | 기능 변경 없는 코드 포맷팅 | `style: 들여쓰기 정리` |
| `refactor` | 기능 변경 없는 코드 리팩터링 | `refactor: 유저 서비스 함수 분리` |
| `test` | 테스트 코드 추가 또는 수정 | `test: 로그인 유닛 테스트 추가` |
| `chore` | 빌드 설정·패키지 관리 등 기타 작업 | `chore: eslint 설정 추가` |
| `remove` | 파일 또는 코드 삭제 | `remove: 사용하지 않는 컴포넌트 삭제` |
| `build` | Gradle 의존성 및 빌드 설정 변경 | - |
| `rename` | 파일·폴더 이동 또는 이름 변경 | - |

### 제목 규칙

- 커밋 타입은 영어로 작성한다.
- 작업 내용은 한국어로 작성한다.
- 50자 이내로 작성한다.
- 마침표를 붙이지 않는다.
- 명령문보다 현재형의 간결한 표현을 사용한다.
- 커밋 바디는 기본적으로 생략하며, 제목만으로 설명하기 어려운 경우에만 작성한다.

좋은 예시:

```text
feat: 회원가입 이메일 인증 기능 추가
```

나쁜 예시:

```text
수정함
ㅇㅇ
asdfasdf
고침
작업중
```

## 3. 코드 리뷰 규칙

- 리뷰 코멘트는 건설적으로 작성한다.
- 최소 2명 이상의 Approve를 받은 후 PR 작성자가 직접 Merge한다.
- 리뷰 의견이 있으면 반영하거나 답변한 후 Merge한다.
- 하나의 PR은 하나의 기능 또는 하나의 버그 수정에 집중한다.
- 서로 관련 없는 여러 도메인의 변경을 하나의 PR에 포함하지 않는다.
- PR 범위가 지나치게 커지면 기능을 분리하여 별도의 PR로 작성한다.
- 리뷰 코멘트에는 수정 요청 이유나 대안을 함께 작성한다.
