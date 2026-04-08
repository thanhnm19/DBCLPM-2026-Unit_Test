package com.example.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRouteConfig {

        private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfig.class);

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                // User Service Routes (without /api/v1 prefix for frontend)
                                .route("user-service-no-prefix", r -> r.path("/user-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/user-service/(?<segment>.*)", "/api/v1/user-service/${segment}"))
                                                .uri("http://user-service:8082"))
                                
                                // User Service Routes (with /api/v1 prefix)
                                .route("user-service", r -> r.path("/api/v1/user-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://user-service:8082"))

                                // Job Service Routes (without /api/v1 prefix)
                                .route("job-service-no-prefix", r -> r.path("/job-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/job-service/(?<segment>.*)", "/api/v1/job-service/${segment}"))
                                                .uri("http://job-service:8083"))
                                
                                // Job Service Routes (with /api/v1 prefix)
                                .route("job-service", r -> r.path("/api/v1/job-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://job-service:8083"))

                                // Candidate Service Routes (without /api/v1 prefix)
                                .route("candidate-service-no-prefix", r -> r.path("/candidate-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/candidate-service/(?<segment>.*)", "/api/v1/candidate-service/${segment}"))
                                                .uri("http://candidate-service:8084"))
                                
                                // Candidate Service Routes (with /api/v1 prefix)
                                .route("candidate-service", r -> r.path("/api/v1/candidate-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://candidate-service:8084"))
                                
                                // Schedule Service Routes
                                .route("schedule-service-no-prefix", r -> r.path("/schedule-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/schedule-service/(?<segment>.*)", "/api/v1/schedule-service/${segment}"))
                                                .uri("http://schedule-service:8085"))
                                
                                .route("schedule-service", r -> r.path("/api/v1/schedule-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://schedule-service:8085"))
                                
                                // Workflow Service Routes
                                .route("workflow-service-no-prefix", r -> r.path("/workflow-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/workflow-service/(?<segment>.*)", "/api/v1/workflow-service/${segment}"))
                                                .uri("http://workflow-service:8086"))
                                
                                .route("workflow-service", r -> r.path("/api/v1/workflow-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://workflow-service:8086"))
                                
                                // Upload Service Routes
                                .route("upload-service-no-prefix", r -> r.path("/upload/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://upload-service:8087"))
                                
                                .route("upload-service", r -> r.path("/api/v1/upload/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://upload-service:8087"))
                                
                                // Notification Service Routes
                                .route("notification-service-no-prefix", r -> r.path("/notification-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/notification-service/(?<segment>.*)", "/api/v1/notification-service/${segment}"))
                                                .uri("http://notification-service:8088"))
                                
                                .route("notification-service", r -> r.path("/api/v1/notification-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://notification-service:8088"))
                                
                                // --- Notification Service WebSocket ---
                                .route("notification-socketio", r -> r.path("/socket.io/**")
                                                .uri("http://localhost:9099"))
                                
                                // Statistics Service Routes
                                .route("statistics-service-no-prefix", r -> r.path("/statistics-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/statistics-service/(?<segment>.*)", "/api/v1/statistics-service/${segment}"))
                                                .uri("http://statistics-service:8089"))
                                
                                .route("statistics-service", r -> r.path("/api/v1/statistics-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://statistics-service:8089"))
                                
                                // Email Service Routes
                                .route("email-service-no-prefix", r -> r.path("/email-service/**")
                                                .filters(f -> f
                                                                .preserveHostHeader()
                                                                .rewritePath("/email-service/(?<segment>.*)", "/api/v1/email-service/${segment}"))
                                                .uri("http://email-service:8090"))
                                
                                .route("email-service", r -> r.path("/api/v1/email-service/**")
                                                .filters(f -> f.preserveHostHeader())
                                                .uri("http://email-service:8090"))
                                .build();
        }

        /**
         * Global Filter để xử lý response từ services trước khi trả về client
         * Đảm bảo gateway là điểm trung gian xử lý tất cả responses
         */
        @Bean
        public GlobalFilter responseProcessingFilter() {
                return new GlobalFilter() {
                        @Override
                        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                                return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                                        ServerHttpResponse response = exchange.getResponse();
                                        
                                        // Log response để tracking
                                        log.debug("Gateway processing response for: {} - Status: {}", 
                                                exchange.getRequest().getURI().getPath(),
                                                response.getStatusCode());
                                        
                                        // Có thể thêm các xử lý khác ở đây:
                                        // - Thêm custom headers
                                        // - Modify response body
                                        // - Logging, monitoring
                                        // - Error handling
                                        
                                        // Đảm bảo response headers được set đúng
                                        response.getHeaders().add("X-Gateway-Processed", "true");
                                }));
                        }
                };
        }
}
