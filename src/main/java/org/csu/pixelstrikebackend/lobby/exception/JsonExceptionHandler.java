package org.csu.pixelstrikebackend.lobby.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2) // 设置高优先级，确保在默认的异常处理器之前执行
public class JsonExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 如果响应已经提交，则不做任何事情
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        CommonResponse<?> errorResponse;
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // 默认为500

        // 针对特定的异常类型进行处理
        if (ex instanceof ResponseStatusException) {
            // 处理由Spring Security或过滤器抛出的带有状态码的异常
            status = (HttpStatus) ((ResponseStatusException) ex).getStatusCode();
            if (status == HttpStatus.UNAUTHORIZED) {
                errorResponse = CommonResponse.createForError(401, "未授权的访问，请检查您的Token");
            } else {
                errorResponse = CommonResponse.createForError(status.value(), ex.getMessage());
            }
        } else {
            // 处理所有其他服务器内部错误
            // 在生产环境中，为了安全，不应暴露详细的异常信息
            // ex.printStackTrace(); // 仅在开发时打印详细错误
            System.err.println("Global Exception Handler Caught: " + ex.getMessage());
            errorResponse = CommonResponse.createForError(500, "服务器内部错误，请联系管理员");
        }

        // 设置响应头
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 将 CommonResponse 对象序列化为JSON并写入响应体
        try {
            byte[] responseBytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer dataBuffer = bufferFactory.wrap(responseBytes);
            return exchange.getResponse().writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            // 如果序列化失败，返回一个更简单的错误
            e.printStackTrace();
            return exchange.getResponse().setComplete();
        }
    }
}
