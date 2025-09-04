package cn.nolaurene.cms.intercepter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlrpc.util.ThreadPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Configuration
public class BizWebMvcConfigurer implements WebMvcConfigurer {

    @Value("${image.path-prefix}")
    private String imageStoragePath;

    @Value("${maintenance.env}")
    private String env;

    @Resource
    private LoginInterceptor loginInterceptor;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Case Management System")
                        .description("This is a simple case management system.")
                        .version("1.8.0"));
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        if ("backend".equals(env)) {
            registry.addInterceptor(loginInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/",
                            "/agents/**",  // ignore manus related APIs
                            "/index.html",
                            "/user/login",
                            "/user/register",
                            "/maintenance/**",
                            "/static/**",
                            "/favicon.ico",
                            "/resources/**",
                            "/webjars/**",
                            "/**/*.js",
                            "/**/*.css",
                            "/**/*.svg",
                            "/**/*.html",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/error/**",
                            "/v2/**"
                    );
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加对静态资源的处理
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/static/images/**")
                .addResourceLocations("file:" + imageStoragePath + "/");
        // swagger and springdoc
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.initialize();
        configurer.setTaskExecutor(executor);
    }

    /**
     * Mcp server related
     */
    @Bean
    public HttpServletSseServerTransportProvider servletSseServerTransportProvider() {
        return new HttpServletSseServerTransportProvider(new ObjectMapper(), "/mcp/message", "/sse");
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> customServletBean(HttpServletSseServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp/message", "/mcp/message/*");
    }

//    @Bean
//    public FilterRegistrationBean<Filter> logFilter() {
//        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
//        registrationBean.setFilter(new Filter() {
//            @Override
//            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
//                HttpServletRequest req = (HttpServletRequest) request;
//                // 包装 request，避免 body 只能读一次
//                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(req);
//
//                log.info("[Filter] Request URI: " + wrappedRequest.getRequestURI());
//                log.info("[Filter] Method: " + wrappedRequest.getMethod());
//
//                log.info("[Filter] Headers:");
//                Enumeration<String> headerNames = wrappedRequest.getHeaderNames();
//                while (headerNames.hasMoreElements()) {
//                    String headerName = headerNames.nextElement();
//                    log.info(headerName + ": " + wrappedRequest.getHeader(headerName));
//                }
//
//                chain.doFilter(wrappedRequest, response);
//
//                // 打印请求体（POST/PUT）
//                if ("POST".equalsIgnoreCase(wrappedRequest.getMethod()) || "PUT".equalsIgnoreCase(wrappedRequest.getMethod())) {
//                    byte[] buf = wrappedRequest.getContentAsByteArray();
//                    String body = new String(buf, wrappedRequest.getCharacterEncoding());
//                    log.info("[Filter] Request Body: " + body);
//                }
//
//                // 打印响应基本信息
//                log.info("[Filter] Response class: " + response.getClass().getName());
//                log.info("[Filter] Response encoding: " + response.getCharacterEncoding());
//                log.info("[Filter] Response content type: " + response.getContentType());
//            }
//        });
//        registrationBean.addUrlPatterns("/mcp/message/*");
//        return registrationBean;
//    }
}