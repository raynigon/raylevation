package com.raynigon.raylevation.helper

import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.StreamUtils

class SRTMWireMock extends AbstractWireMockInitializer {

    String name = "srtm"
    public String url

    void setup() {
        url = "http://localhost:$port/srtm/download/{{name}}.rar".toString()
    }

    void registerMocks(WireMock wireMock) {
        byte[] ceaRar = StreamUtils.copyToByteArray(this.class.getResourceAsStream("/archive/cea.rar"))
        wireMock.register(
                WireMock.get(WireMock.urlPathEqualTo("/srtm/download/cea.rar"))
                        .atPriority(1)
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                        .withHeader(HttpHeaders.ETAG, "1234567890")
                                        .withBody(ceaRar)
                        )
        )
        wireMock.register(
                WireMock.get(WireMock.urlPathEqualTo("/srtm/download/cea-no-etag.rar"))
                        .atPriority(1)
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                        .withBody(ceaRar)
                        )
        )
        wireMock.register(
                WireMock.get(WireMock.urlPathEqualTo("/srtm/download/missing.rar"))
                        .atPriority(1)
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(404)
                        )
        )
    }
}
