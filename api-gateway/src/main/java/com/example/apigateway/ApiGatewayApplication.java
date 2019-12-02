package com.example.apigateway;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import feign.RequestInterceptor;
import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.hateoas.CollectionModel;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;

import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@EnableFeignClients
@EnableCircuitBreaker
@EnableDiscoveryClient
@EnableZuulProxy
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}



	@Configuration
	static class OktaOAuth2WebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
					.authorizeRequests().anyRequest().authenticated()
					.and()
					.oauth2Login()
					.and()
					.oauth2ResourceServer().jwt();
			// @formatter:on
		}
	}


	@Bean
	public FilterRegistrationBean<CorsFilter> simpleCorsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.setAllowedOrigins(Collections.singletonList("*"));
		config.setAllowedMethods(Collections.singletonList("*"));
		config.setAllowedHeaders(Collections.singletonList("*"));
		source.registerCorsConfiguration("/**", config);
		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return bean;
	}


	@Bean
	public RequestInterceptor getUserFeignClientInterceptor(OAuth2AuthorizedClientService clientService) {
		return new UserFeignClientInterceptor(clientService);
	}


	@Bean
	public AuthorizationHeaderFilter authHeaderFilter(OAuth2AuthorizedClientService clientService) {
		return new AuthorizationHeaderFilter(clientService);
	}
}


@Data
class Course {
	private String name;
}

@FeignClient("course-service")
interface CourseClient {

	@GetMapping("/courses")
	@CrossOrigin
	CollectionModel<Course> readCourses();
}

@RestController
class CoolCourseController {

	private final CourseClient courseClient;

	public CoolCourseController(CourseClient courseClient) {
		this.courseClient = courseClient;
	}

	private Collection<Course> fallback() {
		return new ArrayList<>();
	}

	@GetMapping("/cool-courses")
	@CrossOrigin
	@HystrixCommand(fallbackMethod = "fallback")
	public Collection<Course> goodCourses() {
		return courseClient.readCourses()
				.getContent()
				.stream()
				.filter(this::isCool)
				.collect(Collectors.toList());
	}

	private boolean isCool(Course course) {
		return !course.getName().equals("Docker y Kubernetes") &&
				!course.getName().equals("OCA Java 11") &&
				!course.getName().equals("Azure Devops");
	}
}