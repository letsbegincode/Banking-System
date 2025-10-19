# System Design Overview

## Caching Strategy
- **Cache Provider Abstraction:** The `CacheProvider` interface in `src/BankingApplication.java` defines a contract for caching account profiles and balance lookups. The production default is `InMemoryCacheProvider`, while `RedisCacheProvider` illustrates how a Redis-backed implementation can be wired in.
- **Cache Population:** The `Bank` aggregate owns cache lifecycle. Account mutations (create, deposit, withdraw, transfer, close, and interest accrual) refresh cache entries to keep balances and account snapshots in sync.
- **Read Path:** Lookups (`Bank.getAccount` and `Bank.getAccountBalance`) consult the cache before touching the persistent map, reducing latency and enabling external cache deployments such as Redis.

## Asynchronous Transaction Processing
- **Message Broker Abstraction:** `MessageBroker` decouples the command queue from execution. The default `InMemoryMessageBroker` runs operations on a worker pool, while the interface enables future adapters for Kafka or RabbitMQ.
- **Transaction Messages:** Each `AccountOperation` is wrapped in a `TransactionMessage` so success/failure callbacks can update caches and observers without blocking the caller.
- **Extensibility:** Replace the in-memory broker with Kafka/RabbitMQ producers and consumers to distribute load across multiple banking service instances.

## Load Balancing & Horizontal Scaling
- **Stateless Frontends:** Console/UI nodes interact with the shared broker and cache, keeping state in Redis or the persistent store so multiple replicas can serve traffic.
- **Sticky Sessions Not Required:** Because account state lives in the cache/database, requests can be distributed by a Layer-7 load balancer (e.g., NGINX, AWS ALB) without session affinity.
- **Idempotent Operations:** Transaction commands should include unique identifiers so downstream consumers can safely retry when scaling message handlers.
- **Observability:** Centralized logging and metrics (e.g., Prometheus/Grafana) are recommended to monitor queue depth, cache hit rates, and account throughput as replicas scale.

## Scaling Workflow
1. Deploy Redis (or compatible cache) and point the `CacheProvider` to it.
2. Provision Kafka/RabbitMQ clusters and implement concrete `MessageBroker` adapters.
3. Run multiple BankingApplication instances behind a load balancer; each instance connects to the shared cache, broker, and persistence tier.
4. Use Infrastructure-as-Code manifests in `infra/` to provision and scale infrastructure consistently across environments.
