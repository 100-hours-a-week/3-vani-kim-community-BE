# 🚀 100만 MAU 트래픽을 감당하는 고가용성·비용 효율적 AWS 아키텍처 설계

> **프로젝트 요약** > 100만 MAU의 음식 사진을 주로 서빙하는 커뮤니티 서비스를 가정하여, 안정적인 서비스 운영(고가용성)과 스타트업 환경에 맞는 비용 최적화를 동시에 달성하기 위한 AWS 클라우드 마이그레이션 및 아키텍처 설계 프로젝트입니다.

---

## 1. Architecture Overview

전체 시스템은 **가용성(Availability)**, **확장성(Scalability)**, **비용 효율성(Cost Efficiency)** 을 최우선으로 고려하여 설계되었습니다.

<img src="images/AWS_structure.png" alt="AWS Architecture Overview">

*▲ [그림 1] 전체 AWS 아키텍처 구성도 (VPC, Public/Private Subnet 구조 및 각 서비스 연결)*

### 🛠 Tech Stack & Infrastructure

| Category | Service | Description |
|:---:|:---:|:---|
| **Compute** | **EC2 (ASG)** | T4g(Graviton) 인스턴스와 Spot Instance 혼합 전략 |
| **Serverless** | **Lambda** | 이미지 업로드 권한(Presigned URL) 발급 및 인증 처리 |
| **Network** | **VPC, ALB** | Private Subnet 격리 및 VPC Endpoint를 통한 보안 통신 |
| **Database** | **RDS (MySQL)** | 비용 효율적인 MySQL 사용 및 Read Replica 확장 고려 |
| **Cache** | **ElastiCache (Redis)** | '좋아요' 기능 등 빈번한 I/O 부하 분산 |
| **Storage** | **S3** | 정적 리소스 및 이미지 파일 저장 (Lifecycle 정책 적용) |

---

## 2. Capacity Planning (규모 산정)

막연한 추측이 아닌, 실제 비즈니스 시나리오와 경쟁사 데이터를 기반으로 정량적인 목표 수치를 설정했습니다.

### 🎯 트래픽 목표 설정
* **가정:** 100만 MAU (월간 활성 사용자), 10만 DAU, 피크타임 동시 접속자(CCU) 약 1만 명 예상
* **QPS 산출 (읽기:쓰기 = 9:1 가정):**
    * **Read QPS:** 약 1,210 (Peak 시)
    * **Write QPS:** 약 333 (Peak 시)
    * **Total Target QPS:** 약 1,543 → **안전 마진(1.5배) 적용 시 약 2,315 QPS**

<image>
![Traffic Calculation Logic]([여기에_트래픽_산정_근거_이미지_경로])
*▲ [그림 2] 유저 시나리오 기반 피크타임 QPS 산출 근거*

---

## 3. Key Architecture Decisions (핵심 의사결정)

인프라 구축 과정에서 직면한 비용 및 성능 문제를 해결하기 위해 내린 기술적 의사결정(Trade-off) 내역입니다.

### 💡 Decision 1: 비용 최적화를 위한 EC2 인스턴스 전략 (Spot + ARM)
> **Challenge:** 100만 MAU 트래픽을 온디맨드 인스턴스로만 처리할 경우 인프라 비용이 과다하게 발생함.
* **Solution:**
    1.  **Spot Instance 활용:** 상시 트래픽(Base)은 온디맨드 2대로 처리하고, 예측 불가능한 피크 트래픽은 **Spot Instance(할인율 약 70%)** 를 통해 처리하도록 Auto Scaling Group(ASG) 구성.
    2.  **Graviton 프로세서 도입:** 동급 x86 인스턴스 대비 가성비가 뛰어난 **AWS Graviton (t4g.medium)** 인스턴스 채택.
* **Result:** 동급 x86 온디맨드 대비 **약 60% 이상의 컴퓨팅 비용 절감** 효과 기대.

### 💡 Decision 2: NAT Gateway 제거와 VPC Endpoint 활용
> **Challenge:** Private Subnet의 인스턴스가 S3, CloudWatch 등 AWS 서비스와 통신해야 함. NAT Gateway는 시간당 비용과 데이터 처리 비용이 매우 높음.
* **Solution:** 외부 인터넷 접속이 불필요하고 AWS 내부 서비스 통신만 필요한 구간에는 **VPC Endpoint(Interface/Gateway)**, **NAT Instance**, **EICE** 를 적용.
* **Result:** 고가의 NAT Gateway 비용(Data Transfer In/Out)을 제거하고, AWS 사설 네트워크를 이용해 보안성 강화.

### 💡 Decision 3: 서버 부하 분산을 위한 Serverless (Lambda) 도입
> **Challenge:** 고해상도 음식 이미지 업로드 트래픽이 몰릴 경우 웹 서버(EC2)의 CPU/메모리 리소스가 급증하여 서비스 장애 위험.
* **Solution:**
    * **Presigned URL:** 클라이언트가 웹 서버를 거치지 않고 S3로 이미지를 직접 업로드하도록 변경.
    * **Lambda:** 업로드 권한 발급 로직만 가벼운 Lambda(Node.js)로 분리하여 구현.
* **Result:** EC2는 비즈니스 로직 처리에만 집중할 수 있게 되어 필요한 인스턴스 수 감소.

<image>
![Image Upload Sequence]([여기에_이미지_업로드_시퀀스_이미지_경로])
*▲ [그림 3] EC2 부하를 줄이는 S3 Direct Upload 시퀀스 (Client -> Lambda -> S3)*

### 💡 Decision 4: DB 병목 해소를 위한 Redis 캐싱 전략
> **Challenge:** 커뮤니티 특성상 특정 인기 게시글에 '좋아요' 요청이 몰릴 경우(Hot Key), DB Row Lock 경합으로 인한 성능 저하 발생.
* **Solution:** **ElastiCache(Redis)**를 도입하여 '좋아요' 카운트와 사용자 세션 정보를 메모리 기반으로 처리.
* **Result:** RDS 스펙을 무리하게 올리는 비용(Vertical Scaling)보다 저렴하게 DB 부하를 해소하고 응답 속도(Latency) 개선.

---

## 4. Monitoring & Observability

"측정할 수 없으면 관리할 수 없다"는 원칙 하에, 서비스 안정성을 위한 핵심 지표 모니터링 대시보드를 구성했습니다.

* **EC2:** CPU Credit Balance (T타입 인스턴스 성능 제한 감시), Memory Utilization (JVM 힙 메모리 관리)
* **RDS:** Connection Count, Replica Lag (읽기 전용 복제본 지연 확인)
* **S3:** Bucket Size (스토리지 비용 관리)

<image>
![Monitoring Dashboard]([여기에_클라우드워치_대시보드_이미지_경로])
*▲ [그림 4] 주요 리소스 상태를 한눈에 파악하는 CloudWatch 대시보드*

---

## 5. Cost Analysis (예상 비용 산정)

서울 리전(ap-northeast-2) 기준 월간 예상 비용을 산출하여 예산 내 운영 가능성을 검증했습니다.

| 구분 | 세부 내역 |     월별 예상 비용 | 비고 |
|:---:|:---|-------------:|:---|
| **EC2** | T4g.medium (On-Demand 2대 + Spot 10대 가정) |   **$72.09** | Spot 인스턴스로 비용 절감 |
| **RDS** | db.m5.large (Master 1 + Replica 1) + Storage 200GB | **$NEED TO** | (실제 계산값 기입) |
| **Cache** | ElastiCache (cache.t4g.small) | **$NEED TO** | |
| **ELB** | ALB 시간당 요금 + LCU 처리 비용 | **$NEED TO** | |
| **Total** | **월 합계 (Estimated)** | **$NEED TO** | |

*(※ 비용은 AWS Pricing Calculator 기준이며 트래픽 변동에 따라 달라질 수 있음)*

---

## 6. Feedback & Retrospective (회고)

### 🔧 피드백 및 개선 (Refactoring) ~ing
**Q. "Lambda 안에서 JWT 인증을 직접 구현하는 것이 비용 효율적인가?"**
* **As-Is:** Lambda 함수 코드 내부에서 JWT 토큰 검증 로직을 수행.
* **Problem:** 람다는 실행 시간만큼 과금되는데, 인증 로직 수행 시간까지 비용에 포함됨. 또한 유효하지 않은 요청도 람다를 실행시키게 됨.
* **To-Be:** 인증 책임을 **API Gateway Authorizer**로 이관. 유효하지 않은 요청은 람다 실행 전에 네트워크 단에서 차단하여 비용 절감 및 보안 강화 달성.

### 📝 Lessons Learned
* **비용과 가용성의 균형:** 무조건적인 고가용성(Multi-AZ) 추구가 정답이 아니며, 초기 스타트업 단계에서는 Single AZ로 시작하되 장애 복구 전략(Failover)을 갖추는 것이 더 효율적일 수 있음을 배웠습니다.
* **근거 기반 설계(Evidence-based Design):** "남들이 쓰니까 쓴다"가 아니라, "예상 QPS가 XX이고, 메모리 요구량이 YY이므로 이 인스턴스를 쓴다"라는 정량적 근거의 중요성을 체득했습니다.