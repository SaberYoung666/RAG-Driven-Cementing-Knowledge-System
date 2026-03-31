package org.swpu.backend.modules.logging.support;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.swpu.backend.common.api.ApiResponse;

@RestControllerAdvice
public class ApiResponseLogAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse && request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(RequestLogInterceptor.ATTR_LOGICAL_CODE, apiResponse.getCode());
            servletRequest.getServletRequest().setAttribute(RequestLogInterceptor.ATTR_LOGICAL_SUCCESS, apiResponse.getCode() == 0);
            servletRequest.getServletRequest().setAttribute(RequestLogInterceptor.ATTR_LOGICAL_MESSAGE, apiResponse.getMessage());
        }
        return body;
    }
}
