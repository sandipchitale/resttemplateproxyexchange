package sandipchitale.resttemplateproxyexchange;

import jakarta.servlet.Servlet;
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

		@RequestMapping()
		ResponseEntity<StreamingResponseBody> proxy(HttpServletRequest httpServletRequest,
													@RequestHeader HttpHeaders httpHeaders,
													HttpServletResponse httpServletResponse) {

			StreamingResponseBody responseBody = (OutputStream outputStream) -> {
				RequestCallback requestCallback = (ClientHttpRequest clientHttpRequest) -> {
					HttpHeaders headers = clientHttpRequest.getHeaders();
					headers.addAll(httpHeaders);
					StreamUtils.copy(httpServletRequest.getInputStream(), clientHttpRequest.getBody());
				};

				// Create a custom ResponseExtractor
				ResponseExtractor<Void> responseExtractor = (ClientHttpResponse clientHttpResponse) -> {
					HttpHeaders headers = clientHttpResponse.getHeaders();
					headers.forEach((String n, List<String> vl) -> {
						vl.forEach((v) -> {
							httpServletResponse.addHeader(n, v);
						});
					});
					StreamUtils.copy(clientHttpResponse.getBody(), outputStream);
					return null;
				};

				UriComponents uriComponents = ServletUriComponentsBuilder
						.fromRequest(httpServletRequest)
						.build(true).encode();

				String query = uriComponents.getQuery();
				if (query == null) {
					query = "";
				} else {
					query = "?" + query;
				}

				getRestTemplate(httpServletRequest).execute("https://postman-echo.com/" + httpServletRequest.getMethod().toLowerCase() + query,
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
