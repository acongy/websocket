package com.acong.websocket.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MyWebSocketHandler extends TextWebSocketHandler {

    // userId -> Session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // userId -> username
    private final Map<String, String> userNames = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close();
            return;
        }
        // 获取 userId 和 username
        String query = uri.getQuery();
        Map<String, String> paramMap = parseQuery(query);
        String userId = paramMap.get("userId");
        String username = paramMap.get("username");

        if (userId == null || username == null) {
            session.close();
            return;
        }

        // 保存连接和用户名
        sessions.put(userId, session);
        userNames.put(userId, username);

        // 发送欢迎消息
        session.sendMessage(new TextMessage("系统：\uD83E\uDD73欢迎 " + username + " 进入聊天室！"));

        // 广播在线用户列表
        broadcastUserList();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 移除对应用户
        String userId = getUserId(session);
        if (userId != null) {
            sessions.remove(userId);
            userNames.remove(userId);
            // 广播在线用户列表
            broadcastUserList();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> msgMap;
        try {
            msgMap = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            session.sendMessage(new TextMessage("系统：消息格式错误！"));
            return;
        }

        String type = (String) msgMap.get("type");
        String fromUserId = (String) msgMap.get("fromUserId");
        String fromUsername = (String) msgMap.get("fromUsername");
        String content = (String) msgMap.get("content");
        String toUserId = (String) msgMap.get("toUserId");

        if ("group".equals(type)) {
            // 群聊广播
            Map<String, Object> sendMap = new HashMap<>();
            sendMap.put("type", "message");
            sendMap.put("fromUserId", fromUserId);
            sendMap.put("fromUsername", fromUsername);
            sendMap.put("content", content);
            String sendMsg = mapper.writeValueAsString(sendMap);
            broadcast(sendMsg);
        } else if ("private".equals(type)) {
            // 私聊，转发给指定用户
            if (toUserId == null) {
                session.sendMessage(new TextMessage("系统：私聊必须指定接收人"));
                return;
            }
            WebSocketSession toSession = sessions.get(toUserId);
            if (toSession != null && toSession.isOpen()) {
                Map<String, Object> sendMap = new HashMap<>();
                sendMap.put("type", "message");
                sendMap.put("fromUserId", fromUserId);
                sendMap.put("fromUsername", fromUsername);
                sendMap.put("content", content);
                sendMap.put("toUserId", toUserId);
                String sendMsg = mapper.writeValueAsString(sendMap);
                toSession.sendMessage(new TextMessage(sendMsg));
            } else {
                session.sendMessage(new TextMessage("系统：对方不在线"));
            }
        } else {
            session.sendMessage(new TextMessage("系统：不支持的消息类型"));
        }
    }

    private void broadcastUserList() throws Exception {
        List<Map<String, String>> userList = new ArrayList<>();
        for (String uid : userNames.keySet()) {
            Map<String, String> user = new HashMap<>();
            user.put("userId", uid);
            user.put("username", userNames.get(uid));
            userList.add(user);
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "userList");
        msg.put("list", userList);

        String userListMsg = mapper.writeValueAsString(msg);
        broadcast(userListMsg);
    }

    private void broadcast(String msg) throws Exception {
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(msg));
            }
        }
    }

    private String getUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        Map<String, String> params = parseQuery(query);
        return params.get("userId");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }
}