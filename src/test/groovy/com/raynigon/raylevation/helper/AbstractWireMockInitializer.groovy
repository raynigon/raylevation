package com.raynigon.raylevation.helper

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.http.RequestMethod.fromString
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern
import static org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment

@Component
abstract class AbstractWireMockInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>,
        ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = LoggerFactory.getLogger("com.raynigon. 🎭 [WireMock]")

    private static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())

    private static final Map<String, AbstractWireMockInitializer> instances = new HashMap<>()

    protected static final Map<String, String> appProperties = new HashMap<>()

    static <T extends AbstractWireMockInitializer> T getInstance(String id) {
        if (!instances.containsKey(id))
            throw new RuntimeException("Instance with id: $id was not found")
        return (T) instances[id]
    }

    static {
        wireMockServer.addMockServiceRequestListener { request, response ->
            logger.debug("${request.method} ${request.absoluteUrl} HTTP/1.1")
            request.headers.all().forEach {
                logger.trace("${it.key()}: ${it.values().join("; ")}")
            }
            logger.trace(request.bodyAsString)
            logger.debug("HTTP/1.1 ${response.status} ${response.statusMessage}")
            request.headers.all().forEach {
                logger.trace("${it.key()}: ${it.values().join("; ")}")
            }
            logger.trace(response.bodyAsString)
        }
    }

    /*
     * Internal Properties
     */

    private String instanceId = UUID.randomUUID().toString()

    private WireMock wireMock

    private ConfigurableApplicationContext applicationContext

    protected static int getPort() {
        return wireMockServer.port()
    }

    /*
     * Implementation Specific Method
     */

    protected abstract String getName()

    protected abstract void registerMocks(WireMock wireMock)

    protected void setup() {}

    /*
     * Generic Code
     */

    protected AbstractWireMockInitializer() {
        instances[instanceId] = this
    }

    void initialize(ConfigurableApplicationContext applicationContext) {
        // Init Object
        this.applicationContext = applicationContext
        wireMockServer.start()
        appProperties["wiremock.${name.toLowerCase()}.id".toString()] = instanceId

        // Setup Implementation
        setup()

        // Register Mocks
        wireMock = WireMock.create().port(port).build()
        registerMocks(wireMock)

        // Print Initialization Options
        logger.info("WireMock '$name' configured for port $port")
        applicationContext.registerShutdownHook()
        applicationContext.addApplicationListener(this)

        setProperties()
    }

    protected final void setProperties() {
        String[] properties = new String[appProperties.size()]
        int i = 0
        for (def entry : appProperties) {
            properties[i++] = "${entry.key}=${entry.value}"
        }
        addInlinedPropertiesToEnvironment(applicationContext, properties)
    }

    void onApplicationEvent(ContextClosedEvent event) {
        wireMock.resetMappings()
        wireMock.resetRequests()
        wireMock.resetScenarios()
        wireMock.resetToDefaultMappings()
    }

    def reset() {
        wireMock.resetRequests()
    }

    boolean verifyRequestCount(int count, HttpMethod method, String path) {
        wireMock.verifyThat(count, newRequestPattern(fromString(method.name()), urlPathEqualTo(path)))
        return true
    }
}
