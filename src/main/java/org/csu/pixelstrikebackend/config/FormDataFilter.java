package org.csu.pixelstrikebackend.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 一个全局过滤器，用于解决 WebFlux 中 @RequestParam 无法自动
 * 绑定 POST 请求中 x-www-form-urlencoded 类型数据的问题。
 * 它通过提前消费（预读）form data，使其对后续的参数解析器可见。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 确保该过滤器在最前面执行
public class FormDataFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // --- 调试日志 ---
        final String path = exchange.getRequest().getURI().getPath();
        final HttpMethod method = exchange.getRequest().getMethod();
        final MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        System.out.println(">>> FormDataFilter: Processing request for path: " + path);

        // 检查是否为POST请求，并且内容类型是 form-urlencoded
        if (method == HttpMethod.POST &&
                MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {

            System.out.println(">>> FormDataFilter: Matched POST with form-urlencoded for path: " + path + ". Pre-fetching form data...");

            // 提前异步地获取表单数据，并继续执行过滤器链
            return exchange.getFormData()
                    .doOnNext(formData -> {
                        // --- 调试日志 ---
                        System.out.println(">>> FormDataFilter: Successfully pre-fetched form data for " + path + ". Data: " + formData);
                    })
                    .then(chain.filter(exchange));
        }

        System.out.println(">>> FormDataFilter: No match, passing request down the chain for path: " + path);
        return chain.filter(exchange);
    }
}