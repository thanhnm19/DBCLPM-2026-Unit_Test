package com.example.notification_service.config;

import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
@Slf4j
public class SocketIOConfig {

    @Value("${socketio.host:0.0.0.0}")
    private String host;

    @Value("${socketio.port:9099}")
    private Integer port;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setAllowCustomRequests(true);
        config.setUpgradeTimeout(10000);
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        config.setMaxHttpContentLength(1048576);

        config.setOrigin("*");

        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam("token");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            log.info("Socket.IO authorization request from {}, token={}",
                    data.getAddress(),
                    maskToken(token));

            return token != null && !token.isBlank()
                    ? AuthorizationResult.SUCCESSFUL_AUTHORIZATION
                    : AuthorizationResult.FAILED_AUTHORIZATION;
        });

        SocketIOServer server = new SocketIOServer(config);
        log.info("Socket.IO server configured at {}:{}", host, port);
        return server;
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketServer) {
        return new SpringAnnotationScanner(socketServer);
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "null";
        }
        return token.substring(0, Math.min(20, token.length())) + "...";
    }
}
