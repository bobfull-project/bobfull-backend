# AWS V1 백엔드 배포 설정 기준

이 문서는 저장소에 남기는 백엔드 운영 설정과 재실행 가능한 수동 배포 기준을 정리한다.

실제 AWS 콘솔 작업, EC2 터미널 명령, 캡처 중심 진행 기록은 외부 배포 기록에서 관리한다. 저장소에는 값이 없는 스크립트와 설정 기준만 남긴다.

## 이번 PR에 남기는 범위

- Spring Boot prod Profile 설정
- Docker 이미지 빌드 기준
- 로컬 Docker app 검증용 Compose 설정
- ECR push, EC2 bootstrap, EC2 deploy, 배포 verify 스크립트
- GitHub Actions 수동 실행 workflow 파일
- 운영 환경변수 이름과 Parameter Store 이름 기준
- 이미지 저장용 S3 버킷 이름 환경변수 기준
- CloudWatch Logs log group 이름 기준

## 운영 Profile 환경변수

`application-prod.yml`은 실제 값을 직접 저장하지 않고 환경변수만 참조한다.

| 환경변수 | 용도 | 필수 여부 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod Profile 활성화 | 필수 |
| `DB_URL` | RDS MySQL JDBC URL | 필수 |
| `DB_USERNAME` | RDS DB 사용자 이름 | 필수 |
| `DB_PASSWORD` | RDS DB 비밀번호 | 필수 |
| `JWT_SECRET` | JWT 서명 Secret | 필수 |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | Access Token 만료 초 | 선택 |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin 목록 | 선택 |
| `PORTONE_API_SECRET` | PortOne API Secret | 필수 |
| `PORTONE_CHANNEL_KEY` | PortOne Channel Key | 선택 |
| `PORTONE_STORE_ID` | PortOne Store ID | 필수 |
| `PORTONE_WEBHOOK_SECRET` | PortOne Webhook Secret | 선택 |
| `PAYMENT_EXPIRATION_ENABLED` | 결제 만료 스케줄러 활성화 | 선택 |
| `PAYMENT_EXPIRATION_FIXED_DELAY` | 결제 만료 스케줄러 주기 | 선택 |
| `PAYMENT_EXPIRATION_BATCH_SIZE` | 결제 만료 배치 크기 | 선택 |
| `AWS_REGION` | AWS Region | 선택 |
| `S3_IMAGE_BUCKET` | 식당·메뉴 이미지 S3 버킷 이름 | 선택 |

기본값이 있는 선택 환경변수는 운영에서 명시하지 않아도 애플리케이션 기본값으로 동작한다.

## Parameter Store 이름 기준

운영 값은 `/bobfull/prod` 아래에 저장한다.

필수 Parameter:

```text
/bobfull/prod/db-url
/bobfull/prod/db-username
/bobfull/prod/db-password
/bobfull/prod/jwt-secret
/bobfull/prod/portone-api-secret
/bobfull/prod/portone-store-id
```

선택 Parameter:

```text
/bobfull/prod/jwt-access-token-expiration-seconds
/bobfull/prod/jpa-ddl-auto
/bobfull/prod/cors-allowed-origins
/bobfull/prod/portone-channel-key
/bobfull/prod/portone-webhook-secret
/bobfull/prod/payment-expiration-enabled
/bobfull/prod/payment-expiration-fixed-delay
/bobfull/prod/payment-expiration-batch-size
/bobfull/prod/s3-image-bucket
```

Parameter Store 이름은 kebab-case로 저장하고, `scripts/aws/deploy-backend-v1.sh`가 컨테이너 실행 시 `DB_URL`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `S3_IMAGE_BUCKET` 같은 대문자 환경변수 이름으로 변환한다.

비밀번호, JWT Secret, PortOne Secret처럼 노출되면 안 되는 값은 `SecureString`으로 저장한다.

## CORS와 S3 프론트엔드 Origin

프론트엔드가 S3 정적 웹사이트 호스팅으로 배포되면 브라우저의 Origin은 S3 웹사이트 endpoint가 된다. 백엔드는 이 Origin을 `CORS_ALLOWED_ORIGINS`로 받아 Spring Security CORS 설정에 적용한다.

S3 정적 웹사이트 endpoint 예시:

```text
http://<frontend-bucket>.s3-website.ap-northeast-2.amazonaws.com
```

CloudFront를 붙인 뒤에는 CloudFront 배포 도메인 또는 실제 서비스 도메인을 Origin으로 사용한다.

```text
https://<cloudfront-distribution-domain>
https://<service-domain>
```

Parameter Store 등록 예시:

```bash
aws ssm put-parameter \
  --region ap-northeast-2 \
  --name /bobfull/prod/cors-allowed-origins \
  --type String \
  --value "http://<frontend-bucket>.s3-website.ap-northeast-2.amazonaws.com" \
  --overwrite
```

로컬 프론트엔드와 S3 프론트엔드를 함께 허용해야 하면 쉼표로 구분한다.

```text
http://localhost:5173,http://<frontend-bucket>.s3-website.ap-northeast-2.amazonaws.com
```

Origin에는 path를 넣지 않고 scheme, host, port까지만 기록한다. 값을 바꾼 뒤에는 EC2에서 `scripts/aws/deploy-backend-v1.sh`를 다시 실행해 env-file에 `CORS_ALLOWED_ORIGINS`가 기록된 컨테이너로 교체한다.

## AWS 리소스 이름 기준

| 항목 | 기준 |
|---|---|
| Parameter Store prefix | `/bobfull/prod` |
| CloudWatch Log Group | `/bobfull/backend` |
| S3 이미지 버킷 | `S3_IMAGE_BUCKET` 환경변수로 주입 |
| 컨테이너 이름 | `bobfull-backend` |
| 애플리케이션 포트 | `8080` |

## 제외 범위

다음 항목은 이번 PR에 포함하지 않는다.

- 프론트엔드 S3 정적 호스팅
- 프론트엔드 운영 API URL 설정
- S3 Presigned URL API 또는 이미지 업로드 API 구현
- GitHub Actions workflow 실제 실행 결과
- Presigned URL 구현 이후의 S3 이미지 업로드 연동
