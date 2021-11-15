package com.example.formatting;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.SocketUtils;

public class WireMockFactory implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(WireMockFactory.class);

	private static final int RETRY_ATTEMPTS = 4;
	private WireMockServer server;
	private final Integer initialPort;

	public WireMockFactory() {
		initialPort = null;
		server = createWireMockServer(findAvailablePort());
	}

	public WireMockFactory(Integer port) {
		initialPort = port;
		server = createWireMockServer(port);
	}

	public WireMockServer getServer() {
		return server;
	}

	private WireMockServer createWireMockServer(Integer port) {
		WireMockConfiguration options = WireMockConfiguration.options()
															 .port(port)
															 .extensions(new ResponseTemplateTransformer(false));
		return new WireMockServer(options);
	}

	public WireMockFactory start() {
		startOrRetry(initialPort, RETRY_ATTEMPTS);

		LOG.info("Starting WireMockServer on port: " + server.port());
		return this;
	}

	private void startOrRetry(Integer port, int attempts) {
		if (attempts > 0) {
			try {
				server.start();
			}
			catch (Exception e) {
				int newPort = port == null ? findAvailablePort() : port;
				server = createWireMockServer(newPort);
				startOrRetry(newPort, --attempts);
			}
		}
		else {
			throw new FatalStartupException(new RuntimeException("Could not start WireMock instance after " + RETRY_ATTEMPTS + " attempts"));
		}
	}

	private int findAvailablePort() {
		return SocketUtils.findAvailableTcpPort();
	}

	@Override
	public void close() {
		LOG.info("Stopping WireMockServer on port: " + server.port());
		server.stop();
	}

}

