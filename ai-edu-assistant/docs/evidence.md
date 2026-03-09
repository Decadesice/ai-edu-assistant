# 可靠性测试证据

以下截图展示了系统在异常情况下的行为（重试、死信队列、状态流转等）。

## 1. 集成测试通过 (IngestRedisStreamReliabilityIT)
![IngestRedisStreamReliabilityIT](evidence/IngestRedisStreamReliabilityIT.png)

## 2. 死信队列与重试次数 (DEAD + attemptCount=2)
![DEAD + attemptCount=2](evidence/DEAD%20%2B%20attemptCount%3D2.png)

## 3. 状态流转与 DLQ 长度 (XLEN ingesttasksdlq)
![XLEN ingesttasksdlq](evidence/XLEN%20ingesttasksdlq%20%3D%204%20%2B%20ingest_task_transition.png)

## 4. Outbox 发布总量指标
![outbox_publish_total](evidence/outbox_publish_total.png)
