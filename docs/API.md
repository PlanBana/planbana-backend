# API (v1)

Base path: `/api`

## Auth
- POST `/api/auth/register`
- POST `/api/auth/login`
- POST `/api/auth/refresh`
- POST `/api/auth/logout`
- POST `/api/auth/request-password-reset`
- POST `/api/auth/reset-password`
- GET  `/api/auth/verify-email?token=...`

## Users & Profiles
- GET  `/api/users/me`
- PATCH `/api/users/me`

## Events
- POST `/api/events`
- GET  `/api/events` (filters: q, startDate, endDate, near=lat,lng, radiusKm)
- GET  `/api/events/{id}`
- PATCH `/api/events/{id}`
- DELETE `/api/events/{id}`

## Buddy Search
- GET `/api/buddies` (filters: interests, near=lat,lng, radiusKm)

## Destinations
- GET  `/api/destinations`
- POST `/api/destinations` (admin)
- PATCH `/api/destinations/{id}` (admin)
- DELETE `/api/destinations/{id}` (admin)

## Messages (WebSocket)
- WS endpoint: `/ws` (SockJS fallback `/ws/**`)
- STOMP app prefix: `/app`
- Subscribe: `/topic/conversations.{conversationId}`
- Send: `/app/conversations/{conversationId}/send`

