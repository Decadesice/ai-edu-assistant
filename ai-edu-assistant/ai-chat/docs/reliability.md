# 异步入库可靠性（Redis Streams + Kafka Outbox）

本文档描述 ai-chat 后端“异步入库任务”的可靠消费与发布策略，所有规则均可在仓库代码、迁移脚本与测试中验证。

## 1. 任务状态机（ingest_task）

### 状态集合
- QUEUED：已创建任务并入队（Redis Stream 或 Outbox）
- RUNNING：正在执行入库
- RETRYING：执行失败，等待下一次重试（由 nextRetryAt 控制）
- SUCCEEDED：入库完成
- DEAD：超过最大重试次数，进入死信终态

### 关键字段
- attemptCount：失败次数（从 0 开始，失败后 +1）
- nextRetryAt：下一次允许重试的时间点（指数退避计算结果）
- lastError：最近一次失败原因（截断）
- errorMessage：对外显示的错误信息（与 lastError 同步）

### 状态流转可追踪
每次状态变化都会写入 `ingest_task_transition`，用于审计与排障（from_status / to_status / attempt_count / message / created_at）。

## 2. Redis Streams：可靠消费 + 重试 + DLQ + 幂等

### 2.1 取舍：保留在 PEL，通过 reclaim 重试
选择“失败不 ack，保留在 Pending Entries List(PEL)，由定时 reclaim 重新投递到消费者”：
- 优点：不需要额外 retry stream；保留原始消息与 delivery 语义
- 风险与约束：需要合理设置 reclaimIdleMs，避免长任务被过早 reclaim 导致并发重复消费

对应实现：
- 正常消费：从 consumer group 读取新消息
- 失败：不 ack，消息留在 PEL
- 重试：reclaimer 按 idle 时间 claim pending 消息并重新执行
- DLQ：任务进入 DEAD 后，写入 DLQ stream，并 ack 原消息

### 2.2 幂等策略（taskId 不重复执行 ingest）
`ingest_task` 的状态切换通过“条件更新”实现：
- 只有当 status ∈ {QUEUED, RETRYING} 且 nextRetryAt <= now 才允许切换到 RUNNING
- 其它情况（例如重复投递、并发消费）会 SKIPPED/NOT_DUE，不会重复执行入库

### 2.3 重试与死信规则
失败后：
- attemptCount = attemptCount + 1
- 若 attemptCount >= maxAttempts：status=DEAD，nextRetryAt=NULL
- 否则：status=RETRYING，nextRetryAt=now + backoff(attemptCount)

退避（指数退避，上限封顶）：
- backoff(attempt) = min(baseBackoffMs * 2^(attempt-1), maxBackoffMs)

### 2.4 配置项（Redis）
| 配置 | 默认值 | 说明 |
|---|---:|---|
| app.ingest.redis.max-attempts | 10 | 最大失败次数，达到后进入 DEAD |
| app.ingest.redis.retry.base-backoff-ms | 1000 | 退避基准 |
| app.ingest.redis.retry.max-backoff-ms | 600000 | 退避上限 |
| app.ingest.redis.reclaim-enabled | true | 是否启用 reclaim 任务 |
| app.ingest.redis.reclaim-interval-ms | 5000 | reclaim 扫描周期 |
| app.ingest.redis.reclaim-idle-ms | 600000 | pending 消息最小 idle 才能 claim |
| app.ingest.redis.reclaim-batch-size | 20 | 每次 claim 的最大消息数 |
| app.ingest.redis.dlq-stream-key | ingest:tasks:dlq | DLQ stream key（可置空关闭 DLQ stream 写入，仅保留 DEAD 状态） |

## 3. Kafka Outbox：退避重试 + 死信 + 指标

### 3.1 OutboxEvent 状态集合
- NEW：新建待发布（nextRetryAt 为 NULL）
- RETRYING：发布失败，等待重试（nextRetryAt 为到期时间）
- SENT：已发布成功（sentAt 记录成功时间）
- DEAD：超过最大重试次数

### 3.2 发布器规则
发布器按批扫描“到期可重试”的记录：
- NEW 且 nextRetryAt IS NULL
- RETRYING 且 nextRetryAt <= now

发布成功：
- status=SENT，sentAt=now，nextRetryAt=NULL，lastError=NULL

发布失败：
- attemptCount = attemptCount + 1，lastError=异常信息（截断）
- 若 attemptCount >= maxAttempts：status=DEAD，nextRetryAt=NULL
- 否则：status=RETRYING，nextRetryAt=now + backoff(attemptCount)

### 3.3 配置项（Kafka Outbox）
| 配置 | 默认值 | 说明 |
|---|---:|---|
| app.ingest.kafka.max-attempts | 10 | 最大失败次数，达到后进入 DEAD |
| app.ingest.kafka.retry.base-backoff-ms | 1000 | 退避基准 |
| app.ingest.kafka.retry.max-backoff-ms | 600000 | 退避上限 |
| app.ingest.kafka.publish-batch-size | 20 | 每次扫描发布的最大条数 |
| app.ingest.kafka.publish-interval-ms | 1000 | 发布扫描周期 |

## 4. 指标（Micrometer/Prometheus）

### Redis Streams（异步入库）
- ingest_task_process_total{result=...}：处理计数（succeeded / retry / dead / skipped / not_due）
- ingest_task_process_seconds{result=...}：处理耗时（同 result 维度）
- ingest_stream_length{stream=...}：stream 长度（队列积压口径：XLen）
- ingest_stream_pending{stream=...,group=...}：pending 数（口径：consumer group pending summary）
- ingest_stream_reclaim_total{result=...}：reclaim 次数（claimed / error）
- ingest_task_dlq_total：进入 DLQ 的任务数（写入 DLQ stream 的计数）

### Kafka Outbox
- outbox_backlog：outbox backlog（NEW + RETRYING 的条数）
- outbox_publish_total{result=success|failure|dead}：发布结果计数
- outbox_publish_seconds{result=...}：发布耗时（send.get() 的等待口径）
- outbox_publish_delay_seconds：发布延迟（createdAt → sentAt）

## 5. 本地复现（失败 → 重试 → 死信）

### 5.1 Redis Streams（异步入库）
1) 启动 MySQL / Redis，并启动应用（参考 QUICKSTART）。

2) 设置一个可稳定触发入库失败的环境（示例：让 Chroma 不可达）：
```bash
set CHROMA_BASE_URL=http://127.0.0.1:65530
```

3) 为了快速观察重试与死信，建议临时调整重试参数：
```bash
set INGEST_REDIS_MAX_ATTEMPTS=2
set INGEST_REDIS_RETRY_BASE_BACKOFF_MS=0
set INGEST_REDIS_RETRY_MAX_BACKOFF_MS=0
set INGEST_REDIS_RECLAIM_IDLE_MS=0
```

4) 触发异步上传入库（拿到 taskId），并查询任务状态：
- POST `/api/knowledge/documents/upload-async`
- GET `/api/knowledge/tasks/{taskId}`

5) 观察任务状态变化：
- 首次失败：status=RETRYING，attemptCount=1
- 触发 reclaim 后再次失败：status=DEAD，attemptCount=2

6) 验证 DLQ 与 PEL：
```bash
redis-cli XLEN ingest:tasks:dlq
redis-cli XPENDING ingest:tasks ingest-workers
```

7) 验证 DB：
```sql
SELECT id,status,attempt_count,next_retry_at,last_error,updated_at FROM ingest_task WHERE id='...';
SELECT * FROM ingest_task_transition WHERE task_id='...' ORDER BY id;
```

### 5.2 Kafka Outbox（发布失败 → 退避 → 死信）
1) 将队列切换为 Kafka 模式：
```bash
set INGEST_QUEUE=kafka
set KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:65530
```

2) 触发异步上传（会写 outbox_event）。

3) 观察 outbox_event 的重试与死信：
```sql
SELECT id,status,attempt_count,next_retry_at,last_error,created_at,sent_at
FROM outbox_event
ORDER BY created_at DESC
LIMIT 20;
```

