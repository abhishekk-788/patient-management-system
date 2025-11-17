# ✅ **Architecture Flow Diagram**

```
┌──────────────────────┐
│     Frontend (UI)    │
│  React / Mobile / UI │
└───────────┬──────────┘
            │  HTTP Request (JSON)
            ▼
┌──────────────────────┐
│      Controller       │
│  - Accepts request    │
│  - Validates DTO      │
│  - Calls Service      │
└───────────┬──────────┘
            │  DTO (input)
            ▼
┌──────────────────────────────┐
│            Service            │
│  - Business logic             │
│  - Convert DTO ↔ Entity       │
│  - Check rules (email, etc.)  │
│  - Calls Repository           │
└───────────┬──────────────────┘
            │  Entity (Model)
            ▼
┌──────────────────────────────┐
│          Repository           │
│  - Talks to Database          │
│  - JPA/Hibernate queries      │
│  - Save / Update / Delete     │
└───────────┬──────────────────┘
            │  SQL Queries
            ▼
┌──────────────────────────────┐
│           Database            │
│  - Stores actual data         │
│  - Returns entities           │
└───────────┬──────────────────┘
            │  Entity (Model)
            ▼
┌──────────────────────────────┐
│            Service            │
│  - Converts Entity → DTO      │
│  - Prepares final response    │
└───────────┬──────────────────┘
            │  DTO (response)
            ▼
┌──────────────────────┐
│       Controller      │
│  - Returns JSON       │
└───────────┬──────────┘
            │ HTTP Response (JSON)
            ▼
┌──────────────────────┐
│       Frontend       │
│   Shows result       │
└──────────────────────┘
```

---

```
Frontend ──HTTP Request──> Controller ──DTO──> Service ──Entity──> Repository ──SQL──> Database
Frontend <─HTTP Response── Controller <─DTO── Service <─Entity── Repository <─Rows── Database
```
