# Idempotency Gateway

`Idempotency Gateway` is a Spring Boot REST API that protects payment processing from duplicate requests. When clients send the same `Idempotency-Key` with the same request body, the payment is processed once and the saved result is replayed for later calls.

## Project Structure

- `controller`: exposes the REST endpoint
- `service`: contains the idempotency and concurrency logic
- `model`: defines request objects and stored request state

## Endpoint

- `POST /process-payment`
- Header: `Idempotency-Key: <unique-key>`
- Body:

```json
{
  "amount": 100,
  "currency": "USD"
}
```

## How It Works

1. If the key is new, the gateway stores the request as `PROCESSING`, waits 2 seconds to simulate a downstream payment call, then saves the response as `COMPLETED`.
2. If the same key arrives again with the same body, the gateway returns the saved response immediately and adds `X-Cache-Hit: true`.
3. If the same key arrives with a different body, the gateway returns `409 Conflict`.
4. If the first request is still running, concurrent duplicates wait for it to finish and then receive the same saved result instead of processing again.

## Sequence Diagram

```mermaid
sequenceDiagram
    participant ClientA as Client A
    participant ClientB as Client B
    participant API as Idempotency Gateway
    participant Store as In-Memory Store

    ClientA->>API: POST /process-payment + Idempotency-Key
    API->>Store: Save key as PROCESSING
    API->>API: Simulate payment processing
    ClientB->>API: Duplicate POST with same key/body
    API->>Store: Find existing key in PROCESSING
    API->>API: Wait for first request to finish
    API->>Store: Save response as COMPLETED
    API-->>ClientA: 201 Created + Charged 100 USD
    API-->>ClientB: 201 Created + X-Cache-Hit: true
```

## Example Requests

### 1. First request

```bash
curl -i -X POST http://localhost:8080/process-payment ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: payment-123" ^
  -d "{\"amount\":100,\"currency\":\"USD\"}"
```

Example response:

```http
HTTP/1.1 201 Created
Location: /process-payment/payment-123

Charged 100 USD
```

### 2. Same key and same body

```bash
curl -i -X POST http://localhost:8080/process-payment ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: payment-123" ^
  -d "{\"amount\":100,\"currency\":\"USD\"}"
```

Example response:

```http
HTTP/1.1 201 Created
X-Cache-Hit: true

Charged 100 USD
```

### 3. Same key and different body

```bash
curl -i -X POST http://localhost:8080/process-payment ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: payment-123" ^
  -d "{\"amount\":250,\"currency\":\"EUR\"}"
```

Example response:

```http
HTTP/1.1 409 Conflict

Idempotency key already used for a different request body
```

## Design Decisions

- `ConcurrentHashMap<String, StoredRequest>` is used for a thread-safe in-memory idempotency store.
- Each `StoredRequest` keeps the original request body, current processing status, and the saved response.
- A `CountDownLatch` lets concurrent duplicate requests wait for the first in-flight request instead of reprocessing.
- The service replays the original `201 Created` status for the completed request, which keeps duplicate responses consistent.

## Extra Feature

Completed idempotency keys expire automatically after a configurable TTL. This prevents the in-memory store from growing forever in a long-running service.

Properties:

- `payment.processing.delay.ms` default: `2000`
- `idempotency.entry.ttl.ms` default: `600000`

## Running the Project

```bash
mvn spring-boot:run
```

## Running Tests

```bash
mvn test
```
