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
- PATCH `/api/events/{id}` (supports `startAt`/`endAt` ISO-8601 timestamps, `tags`, and editable event details including `meetingPoint`, `destination`, `outOfTown`, and meeting/destination coordinates)
- DELETE `/api/events/{id}`

## Buddy Search
- GET `/api/buddies` (filters: interests, near=lat,lng, radiusKm)

## Destinations
- GET  `/api/destinations`
- POST `/api/destinations` (admin)
- PATCH `/api/destinations/{id}` (admin)
- DELETE `/api/destinations/{id}` (admin)

## Messages (WebSocket)

### WebSocket Endpoint
- **Connect:** `ws://localhost:8080/ws` (SockJS fallback `/ws/**`)
- **STOMP prefix:** `/app` for sending, `/topic` for subscribing

### 🔐 Authentication (REQUIRED)

The WebSocket connection **MUST** include a valid JWT token during CONNECT. Without it, messages will be rejected and appear to "vanish".

**Send the token in connectHeaders:**

```javascript
const client = new Stomp.Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: {
        Authorization: 'Bearer ' + token  // ← REQUIRED
    },
    onConnect: () => {
        // Subscribe AFTER successful connection
        client.subscribe('/topic/chat/' + eventId, (msg) => {
            // Handle incoming messages
        });
    },
    onStompError: (frame) => {
        // Handle auth failures (401) or other errors
        console.error('WebSocket error:', frame);
    }
});
client.activate();
```

### Chat Endpoints

| Operation | Destination | Description |
|-----------|-------------|-------------|
| Send Message | `/app/chat/{eventId}` | STOMP SEND - body: `{"text": "message"}` |
| Subscribe | `/topic/chat/{eventId}` | STOMP SUBSCRIBE - receive messages |

### Example: Send a Message

```javascript
client.publish({
    destination: '/app/chat/' + eventId,
    body: JSON.stringify({ text: 'Hello!' })
});
```

### Example: Subscribe to Messages

```javascript
client.subscribe('/topic/chat/' + eventId, (message) => {
    const data = JSON.parse(message.body);
    console.log('New message:', data);
    // data contains: senderId, senderName, text, sentAt, chatRoomId
});
```

### Common Issues & Fixes

**Problem:** Messages send but disappear from UI after 1 second.

**Cause:** JWT token not sent during WebSocket CONNECT. The backend receives `Authentication = null` and skips processing.

**Solution:** Include `Authorization: Bearer <token>` in `connectHeaders` when creating the STOMP client.

**Problem:** "401 Unauthorized" on WebSocket connection.

**Cause:** Token is expired or invalid.

**Solution:** Refresh the token or re-login before connecting.

### Test Without Frontend

Open `test-websocket.html` in a browser:
1. Enter your JWT token (get from browser DevTools → Application → Local Storage)
2. Enter the Event ID
3. Click "Connect"
4. Send messages and verify they appear

---

*See `WebSocketChatGuide.md` for complete implementation examples.*
