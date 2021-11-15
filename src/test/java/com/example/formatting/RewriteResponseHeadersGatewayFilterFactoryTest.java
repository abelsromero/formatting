package com.example.formatting;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(RewriteResponseHeadersGatewayFilterFactoryTest.TestConfig.class)
public class RewriteResponseHeadersGatewayFilterFactoryTest {

	static final String HEADER_NAME = "X-Rewrite-Me";
	static final String REGEXP_TO_REPLACE = "/thingy/";
	static final String REPLACEMENT = "/thingy/gateway/";

	static final String RESPONSE_VALUE = "https://domain.org/ctx/thingy/my-app/my-resource";
	static final String MODIFIED_VALUE = "https://domain.org/ctx/thingy/gateway/my-app/my-resource";

	static final String NOT_MATCHING_VALUE_1 = "some-value";
	static final String NOT_MATCHING_VALUE_2 = "some-other-value";

	@Autowired
	WebTestClient webTestClient;

	@Autowired
	WireMockServer wireMockServer;

	@TestConfiguration
	static class TestConfig {

		@Autowired
		RewriteResponseHeadersGatewayFilterFactory filterFactory;

		@Bean(destroyMethod = "stop")
		WireMockServer wireMockServer() {
			return new WireMockFactory()
					.start()
					.getServer();
		}

		@Bean
		RouteLocator testRoutes(RouteLocatorBuilder builder, WireMockServer wireMockServer) {
			var config = new RewriteResponseHeaderGatewayFilterFactory.Config();
			config.setRegexp(REGEXP_TO_REPLACE);
			config.setReplacement(REPLACEMENT);
			return builder
					.routes()
					.route(predicateSpec -> predicateSpec.path("/single-header")
														 .filters(spec -> spec.filter(filterFactory.apply(config)))
														 .uri(wireMockServer.baseUrl()))
					.route(predicateSpec -> predicateSpec.path("/multiple-headers")
														 .filters(spec -> spec.filter(filterFactory.apply(config)))
														 .uri(wireMockServer.baseUrl()))
					.route(predicateSpec -> predicateSpec.path("/multivalued-header")
														 .filters(spec -> spec.filter(filterFactory.apply(config)))
														 .uri(wireMockServer.baseUrl()))
					.build();
		}
	}

	@AfterEach
	void afterEach() {
		wireMockServer.resetAll();
	}

	@Test
	void shouldNotRewriteHeadersWhenHeaderValueDoesNotMatch() {
		wireMockServer.stubFor(WireMock.get("/single-header")
									   .willReturn(WireMock.ok()
														   .withHeader("Unmodified-header",
																	   NOT_MATCHING_VALUE_1)
														   .withHeader("Another-unmodified-header",
																	   NOT_MATCHING_VALUE_2)));

		webTestClient.get().uri("/single-header")
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 .valueEquals("Unmodified-header", NOT_MATCHING_VALUE_1)
					 .expectHeader()
					 .valueEquals("Another-unmodified-header", NOT_MATCHING_VALUE_2);
	}

	@Test
	void shouldRewriteSingleHeaderWhenHeaderValueMatches() {
		wireMockServer.stubFor(WireMock.get("/single-header")
									   .willReturn(WireMock.ok()
														   .withHeader(HEADER_NAME,
																	   RESPONSE_VALUE)));

		webTestClient.get().uri("/single-header")
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 .valueEquals(HEADER_NAME, MODIFIED_VALUE);
	}

	@Test
	void shouldRewriteSingleHeaderWhenHeaderValueMatchesAndIgnoreThoseThadDoNotMatch() {
		wireMockServer.stubFor(WireMock.get("/single-header")
									   .willReturn(WireMock.ok()
														   .withHeader(HEADER_NAME,
																	   RESPONSE_VALUE)
														   .withHeader("Unmodified-header",
																	   NOT_MATCHING_VALUE_1)
														   .withHeader("Another-unmodified-header",
																	   NOT_MATCHING_VALUE_2)));

		webTestClient.get().uri("/single-header")
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 .valueEquals(HEADER_NAME, MODIFIED_VALUE)
					 .expectHeader()
					 .valueEquals("Unmodified-header", NOT_MATCHING_VALUE_1)
					 .expectHeader()
					 .valueEquals("Another-unmodified-header", NOT_MATCHING_VALUE_2);
	}

	@Test
	void shouldRewriteMultipleHeadersWhenHeaderValueMatches() {
		wireMockServer.stubFor(WireMock.get("/multiple-headers")
									   .willReturn(WireMock.ok()
														   .withHeader(HEADER_NAME + "-1",
																	   RESPONSE_VALUE)
														   .withHeader(HEADER_NAME + "-2",
																	   RESPONSE_VALUE)));

		webTestClient.get().uri("/multiple-headers")
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 .valueEquals(HEADER_NAME + "-1", MODIFIED_VALUE)
					 .expectHeader()
					 .valueEquals(HEADER_NAME + "-2", MODIFIED_VALUE);
	}

	@Test
	void shouldRewriteMultivaluedHeaderWhenHeaderValueMatches() {
		wireMockServer.stubFor(WireMock.get("/multivalued-header")
									   .willReturn(WireMock.ok()
														   .withHeader(HEADER_NAME,
																	   RESPONSE_VALUE,
																	   "something-else/THINGY",
																	   "url/thingy/")));

		webTestClient.get().uri("/multivalued-header")
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 // first & third are replaced, the other not because is case-sensitive
					 .valueEquals(HEADER_NAME, MODIFIED_VALUE,
								  "something-else/THINGY",
								  "url/thingy/gateway/");
	}

	@Test
	void shouldOnlyRewriteMatchingValuesInMultivaluedHeader() {
		String routePath = "/multivalued-header";
		wireMockServer.stubFor(WireMock.get(routePath)
									   .willReturn(WireMock.ok()
														   .withHeader(HEADER_NAME,
																	   RESPONSE_VALUE,
																	   "something-else/THINGY",
																	   "url/thingy/")));

		webTestClient.get().uri(routePath)
					 .exchange()
					 .expectStatus()
					 .isEqualTo(HttpStatus.OK)
					 .expectHeader()
					 // first & third match and are replaced, the other des not
					 .valueEquals(HEADER_NAME,
								  MODIFIED_VALUE,
								  "something-else/THINGY",
								  "url/thingy/gateway/");
	}
}
