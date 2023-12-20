package sandipchitale.resttemplateproxyexchange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@SpringBootApplication
public class ResttemplateproxyexchangeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResttemplateproxyexchangeApplication.class, args);
	}

	@RestController
	public static class RestTemplateProxyExchange {
		private static final String X_CONNECT_TIMEOUT_MILLIS = "X-CONNECT-TIMEOUT-MILLIS";
		private static final String X_READ_TIMEOUT_MILLIS = "X-READ-TIMEOUT-MILLIS";

		private static final String X_METHOD = "X-METHOD";

		private final RestTemplate restTemplate;
		private final RestTemplateBuilder restTemplateBuilder;
		private final Set<HttpMethod> httpMethods;

		RestTemplateProxyExchange(RestTemplateBuilder restTemplateBuilder) {
			restTemplate = restTemplateBuilder.build();
			this.restTemplateBuilder = restTemplateBuilder;
			httpMethods = Set.of(HttpMethod.GET,
					HttpMethod.HEAD,
					HttpMethod.POST,
					HttpMethod.PUT,
					HttpMethod.PATCH,
					HttpMethod.DELETE,
					HttpMethod.OPTIONS);
		}

		@RequestMapping("/**")
		ResponseEntity<StreamingResponseBody> proxy(HttpServletRequest httpServletRequest,
													@RequestHeader(value = X_METHOD, required = false) String method,
													@RequestHeader HttpHeaders httpHeaders,
													HttpServletResponse httpServletResponse) {

			if (method == null) {
				method = httpServletRequest.getMethod();
			}

			final String finalMethodString = method;

			HttpMethod nonFinalHttpMethod = HttpMethod.valueOf(finalMethodString.toUpperCase());
			if (!httpMethods.contains(nonFinalHttpMethod)) {
				throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid method value: " + finalMethodString + " in " + X_METHOD + " header.");
			}

			final HttpMethod httpMethod = nonFinalHttpMethod;

			StreamingResponseBody responseBody = (OutputStream outputStream) -> {
				String contextPath = httpServletRequest.getContextPath();

				HttpHeaders httpHeadersToSend = new HttpHeaders();
				httpHeadersToSend.addAll(httpHeaders);
				httpHeadersToSend.remove(X_CONNECT_TIMEOUT_MILLIS);
				httpHeadersToSend.remove(X_READ_TIMEOUT_MILLIS);
				httpHeadersToSend.remove(X_METHOD);
				if (!contextPath.isEmpty()) {
					httpHeadersToSend.add("X-Forwarded-Prefix", contextPath);
				}

				RequestCallback requestCallback = (ClientHttpRequest clientHttpRequest) -> {
					HttpHeaders headers = clientHttpRequest.getHeaders();
					headers.addAll(httpHeadersToSend);
					StreamUtils.copy(httpServletRequest.getInputStream(), clientHttpRequest.getBody());
				};

				// Create a custom ResponseExtractor
				ResponseExtractor<Void> responseExtractor = (ClientHttpResponse clientHttpResponse) -> {
					// Copy status code
					httpServletResponse.setStatus(clientHttpResponse.getStatusCode().value());
					// Copy headers
					HttpHeaders headers = clientHttpResponse.getHeaders();
					headers.forEach((String name, List<String> valueList) -> {
						valueList.forEach((String value) -> {
							httpServletResponse.addHeader(name, value);
						});
					});
					// Copy body by streaming.
					StreamUtils.copy(clientHttpResponse.getBody(), outputStream);
					return null;
				};


				String requestURI = httpServletRequest.getRequestURI();
				if (!contextPath.isEmpty()) {
					requestURI = requestURI.substring(contextPath.length());
				}

				if (requestURI.startsWith("/")) {
					requestURI = requestURI.substring(1);
				}

				requestURI = requestURI.replaceAll(Pattern.quote("%7Bmethod%7D"), finalMethodString.toLowerCase());
				requestURI = requestURI.replaceAll(Pattern.quote("%7BMETHOD%7D"), finalMethodString.toUpperCase());

				String query = httpServletRequest.getQueryString();
				if (query == null) {
					query = "";
				} else {
					query = "?" + query;
				}

				String url = requestURI + query;

				getRestTemplate(httpServletRequest)
						.execute(url,
								httpMethod,
								requestCallback,
								responseExtractor);
			};

			return ResponseEntity.ok(responseBody);
		}

		private RestTemplate getRestTemplate(HttpServletRequest httpServletRequest) {
			String connectTimeoutMillisString = httpServletRequest.getHeader(X_CONNECT_TIMEOUT_MILLIS);
			String readTimeoutMillisString = httpServletRequest.getHeader(X_READ_TIMEOUT_MILLIS);
			if (connectTimeoutMillisString == null && readTimeoutMillisString == null) {
				// Return default one
				return restTemplate;
			} else {
				Duration connectionTimeout = connectTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(connectTimeoutMillisString));
				Duration readTimeout = readTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(readTimeoutMillisString));

				RestTemplateBuilder localRestTemplateBuilder = this.restTemplateBuilder;
				if (connectionTimeout != null) {
					if (connectionTimeout.equals(Duration.ZERO)) {
						// 0 indicates infinite connect	 timeout
						localRestTemplateBuilder = localRestTemplateBuilder.setConnectTimeout(Duration.ofMillis(Long.MAX_VALUE));
					} else {
						localRestTemplateBuilder = localRestTemplateBuilder.setConnectTimeout(connectionTimeout);
					}
				}
				if (readTimeout != null) {
					if (readTimeout.equals(Duration.ZERO)) {
						// 0 indicates infinite read timeout
						localRestTemplateBuilder = localRestTemplateBuilder.setReadTimeout(Duration.ofMillis(Long.MAX_VALUE));
					} else {
						localRestTemplateBuilder = localRestTemplateBuilder.setReadTimeout(readTimeout);
					}
				}

				return localRestTemplateBuilder.build();
			}
		}

		@ExceptionHandler
		ResponseEntity<String> handleException(SocketTimeoutException socketTimeoutException) {
			return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase() + ": " + socketTimeoutException.getMessage());
		}

		@ExceptionHandler
		ResponseEntity<String> handleException(HttpClientErrorException httpClientErrorException) {
			return ResponseEntity.status(httpClientErrorException.getStatusCode()).body(httpClientErrorException.getMessage());
		}
	}
}
