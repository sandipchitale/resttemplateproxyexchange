package sandipchitale.resttemplateproxyexchange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.io.OutputStream;
import java.net.URI;
import java.util.List;

@SpringBootApplication
public class ResttemplateproxyexchangeApplication {

	@RestController
	public static class RestTemplateProxyExchange {
		private static final String X_TIMEOUT_MILLIS = "X-TIMEOUT-MILLIS";

		private final RestTemplate restTemplate;
		private final RestTemplateBuilder restTemplateBuilder;

		RestTemplateProxyExchange(RestTemplateBuilder restTemplateBuilder) {
			restTemplate = restTemplateBuilder.build();
			this.restTemplateBuilder = restTemplateBuilder;
		}

		@RequestMapping("/**")
		ResponseEntity<StreamingResponseBody> proxy(HttpServletRequest httpServletRequest,
													@RequestHeader HttpHeaders httpHeaders,
													HttpServletResponse httpServletResponse) {

			String contextPath = httpServletRequest.getContextPath();

			HttpHeaders httpHeadersToSend = new HttpHeaders();
			httpHeadersToSend.addAll(httpHeaders);
			httpHeadersToSend.remove(X_TIMEOUT_MILLIS);
			if (!contextPath.isEmpty()) {
				httpHeadersToSend.add("X-Forwarded-Prefix", contextPath);
			}

			StreamingResponseBody responseBody = (OutputStream outputStream) -> {
				RequestCallback requestCallback = (ClientHttpRequest clientHttpRequest) -> {
					HttpHeaders headers = clientHttpRequest.getHeaders();
					headers.addAll(httpHeadersToSend);
					StreamUtils.copy(httpServletRequest.getInputStream(), clientHttpRequest.getBody());
				};

				// Create a custom ResponseExtractor
				ResponseExtractor<Void> responseExtractor = (ClientHttpResponse clientHttpResponse) -> {
					HttpHeaders headers = clientHttpResponse.getHeaders();
					headers.forEach((String name, List<String> valueList) -> {
						valueList.forEach((String value) -> {
							httpServletResponse.addHeader(name, value);
						});
					});
					StreamUtils.copy(clientHttpResponse.getBody(), outputStream);
					return null;
				};

				ServletUriComponentsBuilder servletUriComponentsBuilder = ServletUriComponentsBuilder.fromRequest(httpServletRequest);

				String requestURI = httpServletRequest.getRequestURI();

				if (!contextPath.isEmpty()) {
					requestURI = requestURI.substring(contextPath.length());
				}
				if (requestURI.startsWith("/postman-echo")) {
					requestURI = requestURI.substring("/postman-echo".length());
				}
				requestURI += "/" + httpServletRequest.getMethod().toLowerCase();
				if (requestURI.startsWith("//")) {
					requestURI = requestURI.substring(1);
				}
				servletUriComponentsBuilder.replacePath(requestURI);

				UriComponents uriComponents = servletUriComponentsBuilder
						.build(true)
						.encode();

				URI uri = uriComponents.toUri();

				String query = uriComponents.getQuery();
				if (query == null) {
					query = "";
				} else {
					query = "?" + query;
				}

				String url = "https://postman-echo.com" + uri.getPath() + query;
						getRestTemplate(httpServletRequest).execute(url,
						HttpMethod.valueOf(httpServletRequest.getMethod()),
						requestCallback,
						responseExtractor);
			};

			return ResponseEntity.ok(responseBody);
		}

		private RestTemplate getRestTemplate(HttpServletRequest httpServletRequest) {
			String xTimeoutMillis = httpServletRequest.getHeader(X_TIMEOUT_MILLIS);
			if (xTimeoutMillis != null) {
				// Cache and return
				return restTemplateBuilder.build();
			}
			return restTemplate;
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(ResttemplateproxyexchangeApplication.class, args);
	}
}
