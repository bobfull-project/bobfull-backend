# AWS V1 백엔드 배포 문서

이 문서는 V1 배포 중 백엔드 배포에 필요한 구성과 실행 순서를 정리한다.

프론트엔드 S3 정적 호스팅은 프론트엔드 저장소에서 별도로 진행한다. 식당과 메뉴 이미지 파일은 S3를 사용하지만, 현재 백엔드 저장소에는 Presigned URL API나 이미지 업로드 API 계약이 없다. 따라서 Issue #41에서는 S3를 AWS 리소스, IAM 권한 대상, 환경변수, 검증 항목으로만 준비한다.

## 목표 구조

```text
GitHub Actions 또는 로컬 PC
  -> Docker 이미지 빌드
  -> Amazon ECR에 이미지 push

EC2
  -> ECR에서 이미지 pull
  -> Spring Boot 컨테이너 실행
  -> RDS MySQL 연결
  -> Parameter Store에서 환경변수와 비밀값 조회
  -> S3 이미지 버킷 접근
  -> CloudWatch Logs로 로그 전송
```

## 스크립트가 하는 일

여기서 스크립트는 사람이 터미널에 여러 명령어를 하나씩 입력해야 하는 작업을 파일 하나로 묶어둔 실행 파일이다. 실제 AWS 값이나 비밀값을 파일에 저장하지 않고, 실행할 때 환경변수나 Parameter Store에서 받아서 사용한다.

| 파일 | 실행 위치 | 역할 |
|---|---|---|
| `scripts/aws/bootstrap-ec2-v1.sh` | EC2 | EC2에 Docker와 AWS CLI를 설치하고 Docker 서비스를 켠다. |
| `scripts/aws/push-image-to-ecr-v1.sh` | 로컬 PC 또는 배포 환경 | Spring Boot Docker 이미지를 빌드하고 ECR에 push한다. |
| `scripts/aws/deploy-backend-v1.sh` | EC2 | Parameter Store 값을 읽고, ECR 이미지를 받아 Spring Boot 컨테이너를 실행한다. |
| `scripts/aws/verify-backend-v1.sh` | 로컬 PC 또는 EC2 | 배포된 API, 컨테이너, Parameter Store, S3, CloudWatch 접근을 확인한다. |

스크립트는 자동으로 실행되지 않는다. 직접 `bash scripts/aws/...` 명령을 실행하거나, GitHub Actions workflow가 해당 스크립트를 호출할 때만 동작한다.

## 필요한 AWS 리소스

- Spring Boot Docker 컨테이너를 실행할 EC2 인스턴스
- 백엔드 Docker 이미지를 저장할 ECR repository
- 운영 데이터베이스로 사용할 RDS MySQL 인스턴스
- 식당과 메뉴 이미지를 저장할 S3 버킷
- 백엔드 환경변수와 비밀값을 저장할 Systems Manager Parameter Store 경로
- Spring Boot 컨테이너 로그를 저장할 CloudWatch Logs log group
- ECR pull, Parameter Store read, S3 access, CloudWatch Logs write 권한을 가진 EC2 IAM Role
- ECR push 권한을 가진 GitHub Actions OIDC IAM Role

## Security Group 규칙

EC2 inbound:

- SSH `22`는 관리자 IP에서만 허용한다.
- 애플리케이션 포트 `8080`은 V1에서 허용할 client 범위에서만 허용한다.

RDS inbound:

- MySQL `3306`은 EC2 Security Group에서만 허용한다.

EC2 outbound:

- ECR, SSM, S3, CloudWatch Logs 접근을 위해 HTTPS `443`을 허용한다.
- RDS 연결을 위해 MySQL `3306`을 RDS Security Group으로 허용한다.

RDS Security Group을 `0.0.0.0/0`에 열지 않는다.

## Parameter Store

Parameter Store 경로 prefix는 다음처럼 둔다.

```text
/bobfull/prod
```

필수 Parameter:

```text
/bobfull/prod/DB_URL
/bobfull/prod/DB_USERNAME
/bobfull/prod/DB_PASSWORD
/bobfull/prod/JWT_SECRET
/bobfull/prod/PORTONE_API_SECRET
/bobfull/prod/PORTONE_STORE_ID
```

선택 Parameter:

```text
/bobfull/prod/JWT_ACCESS_TOKEN_EXPIRATION_SECONDS
/bobfull/prod/JPA_DDL_AUTO
/bobfull/prod/PORTONE_CHANNEL_KEY
/bobfull/prod/PORTONE_WEBHOOK_SECRET
/bobfull/prod/S3_IMAGE_BUCKET
```

V1 권장값:

```text
SPRING_PROFILES_ACTIVE=prod
JPA_DDL_AUTO=update
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=3600
```

비밀번호, JWT Secret, PortOne Secret 같은 비밀값은 `SecureString`으로 저장한다. 실제 값을 Git에 커밋하지 않는다.

## EC2 초기 준비

EC2에 접속한 뒤 bootstrap 스크립트를 실행한다.

```bash
bash scripts/aws/bootstrap-ec2-v1.sh
```

이 스크립트는 EC2에 Docker와 AWS CLI를 설치하고 Docker 서비스를 시작한다. 스크립트 실행이 끝나면 Docker group 권한 적용을 위해 EC2에서 로그아웃한 뒤 다시 접속한다.

## 수동 ECR Push

AWS 인증이 되어 있고 Docker를 사용할 수 있는 환경에서 실행한다.

```bash
export AWS_REGION=ap-northeast-2
export ECR_REPOSITORY=bobfull-backend
export IMAGE_TAG=$(git rev-parse --short HEAD)

bash scripts/aws/push-image-to-ecr-v1.sh
```

이 스크립트는 ECR repository가 없으면 만들고, Docker 이미지를 빌드한 뒤 ECR에 push한다. 실행이 끝나면 EC2 배포에 사용할 ECR image URI를 출력한다.

## 수동 EC2 배포

이미지를 ECR에 push한 뒤 EC2에서 실행한다.

```bash
export AWS_REGION=ap-northeast-2
export ECR_IMAGE_URI=<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/bobfull-backend:<tag>
export PARAMETER_PREFIX=/bobfull/prod
export S3_IMAGE_BUCKET=<image-bucket-name>
export CONTAINER_NAME=bobfull-backend
export HOST_PORT=8080
export CLOUDWATCH_LOG_GROUP=/bobfull/backend

bash scripts/aws/deploy-backend-v1.sh
```

deploy 스크립트는 다음 작업을 한다.

- Parameter Store에서 실행에 필요한 값을 읽는다.
- 비밀값을 터미널에 출력하지 않는다.
- EC2 IAM Role 권한으로 ECR에 로그인한다.
- 지정한 ECR 이미지를 pull한다.
- 기존 백엔드 컨테이너가 있으면 중지하고 제거한다.
- `SPRING_PROFILES_ACTIVE=prod`로 새 컨테이너를 실행한다.
- Docker stdout, stderr 로그를 `awslogs` driver로 CloudWatch Logs에 보낸다.
- 컨테이너가 실행 상태가 되었는지 확인한다.

## GitHub Actions 배포

workflow 파일:

```text
.github/workflows/deploy-backend-v1.yml
```

필수 GitHub Secrets:

```text
AWS_ROLE_TO_ASSUME
EC2_SSH_PRIVATE_KEY
```

필수 GitHub Variables:

```text
AWS_REGION
ECR_REPOSITORY
BACKEND_EC2_HOST
BACKEND_EC2_USER
BACKEND_PARAMETER_PREFIX
BACKEND_PUBLIC_BASE_URL
S3_IMAGE_BUCKET
```

선택 GitHub Variables:

```text
BACKEND_HOST_PORT
BACKEND_CONTAINER_NAME
BACKEND_CLOUDWATCH_LOG_GROUP
```

이 PR이 Draft 상태인 동안에는 GitHub Actions에서 workflow를 수동으로 실행한다. workflow는 Gradle 테스트, Docker 이미지 빌드, ECR push, EC2 deploy script 복사, EC2 배포, 외부에서 `GET /api/restaurants` 확인을 순서대로 수행한다.

## 배포 검증

로컬 PC 또는 EC2에서 실행한다.

```bash
export BASE_URL=http://<ec2-public-host>:8080
export AWS_REGION=ap-northeast-2
export PARAMETER_PREFIX=/bobfull/prod
export S3_IMAGE_BUCKET=<image-bucket-name>
export CLOUDWATCH_LOG_GROUP=/bobfull/backend

bash scripts/aws/verify-backend-v1.sh
```

verify 스크립트는 다음 항목을 확인한다.

- `GET /api/restaurants` 외부 API 응답
- EC2에서 실행할 경우 Docker 컨테이너 실행 상태
- AWS caller identity
- 실제 값을 출력하지 않는 Parameter Store parameter 이름 접근
- S3 image bucket 접근
- CloudWatch log group 접근

## Issue 범위

Issue #41은 백엔드 AWS 배포 환경 구성만 포함한다.

다음 작업은 이번 백엔드 Issue 범위가 아니다.

- 프론트엔드 S3 정적 호스팅
- 프론트엔드 운영 API URL 설정
- 배포된 프론트엔드 origin에 대한 백엔드 CORS 정책 설정
- S3 Presigned URL API 또는 이미지 업로드 API 구현
- Issue #42 Smoke Test 결과 기록
