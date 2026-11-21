# WebSocket Chat Integration Guide

This guide helps frontend developers integrate with the PlanBana WebSocket chat system.

---

## The Problem: Messages Disappearing

If messages send but disappear from the UI after ~1 second, the cause is almost always:

**The WebSocket connection is not authenticated.**

The backend logs will show:
```
? Auth: null
? No authentication, skipping.
```

This means the server received your message but rejected it because there was no JWT token in the WebSocket connection.

---

## Solution: Pass JWT in Connect Headers

### Quick Fix

```javascript
const client = new Stomp.Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: {
        Authorization: 'Bearer ' + YOUR_JWT_TOKEN  // ← THIS IS THE FIX
    },
    onConnect: () => {
        // Now subscribe - ONLY after connection is authenticated
        client.subscribe('/topic/chat/' + eventId, (msg) => {
            // Handle message
        });
    }
});
client.activate();
```

---

## Complete Implementation

### Step 1: Get the JWT Token

Where you store the token depends on your frontend app:

```javascript
// Option A: localStorage (most common)
const token = localStorage.getItem('accessToken');

// Option B: From your auth service
const token = authService.getToken();

// Option C: React state/context
const token = useAuth().token;
```

### Step 2: Create WebSocket Client with Auth

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class ChatService {
    constructor() {
        this.stompClient = null;
    }

    connect(eventId, onMessageReceived) {
        const token = localStorage.getItem('accessToken');

        if (!token) {
            console.error('No JWT token - cannot connect');
            return;
        }

        this.stompClient = new Client({
            webSocketFactory: () => new SockJS('/ws'),

            // 🔑 CRITICAL: Pass JWT in connectHeaders
            connectHeaders: {
                Authorization: 'Bearer ' + token
            },

            onConnect: () => {
                console.log('✅ WebSocket connected & authenticated');

                // Subscribe to chat topic AFTER connection
                this.subscribe(eventId, onMessageReceived);
            },

            onStompError: (frame) => {
                console.error('STOMP Error:', frame);

                if (frame.headers['websocket-status-code'] === '401') {
                    console.error('❌ Authentication failed! Token expired or invalid.');
                }
            },

            onWebSocketError: (event) => {
                console.error('WebSocket Error:', event);
            }
        });

        this.stompClient.activate();
    }

    subscribe(eventId, onMessageReceived) {
        if (!this.stompClient || !this.stompClient.connected) {
            console.error('Not connected');
            return;
        }

        const topic = '/topic/chat/' + eventId;
        this.stompClient.subscribe(topic, (message) => {
            const data = JSON.parse(message.body);
            onMessageReceived(data);
        });

        console.log('📡 Subscribed to:', topic);
    }

    sendMessage(eventId, text) {
        if (!this.stompClient || !this.stompClient.connected) {
            console.error('WebSocket not connected');
            return false;
        }

        const destination = '/app/chat/' + eventId;
        const body = JSON.stringify({ text: text });

        this.stompClient.publish({
            destination: destination,
            body: body
            // No Authorization header needed here - auth was at CONNECT
        });

        console.log('📤 Sent to:', destination);
        return true;
    }

    disconnect() {
        if (this.stompClient) {
            this.stompClient.deactivate();
            this.stompClient = null;
        }
    }
}

// Usage
const chatService = new ChatService();

// Connect
chatService.connect('6aa2c5175d835b1e956a1f21', (message) => {
    console.log('Received:', message);
    addMessageToChatUI(message);
});

// Send a message
chatService.sendMessage('6aa2c5175d835b1e956a1f21', 'Hello!');
```
