package com.fintrack.apiservice.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpAddressResolverTest {

    @Test
    void resolveUsesRemoteAddressWhenCloudFrontHeaderIsNotTrusted() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver(false);
        MockHttpServletRequest request = request("10.20.10.15");
        request.addHeader("CloudFront-Viewer-Address", "203.0.113.10:41235");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.10.15");
    }

    @Test
    void resolveExtractsTrustedCloudFrontIpv4Address() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver(true);
        MockHttpServletRequest request = request("10.20.10.15");
        request.addHeader("CloudFront-Viewer-Address", "203.0.113.10:41235");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void resolveExtractsTrustedCloudFrontIpv6Address() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver(true);
        MockHttpServletRequest request = request("10.20.10.15");
        request.addHeader("CloudFront-Viewer-Address", "[2001:db8::10]:41235");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::10");
    }

    @Test
    void resolveUsesRemoteAddressWhenCloudFrontHeaderIsMissing() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver(true);
        MockHttpServletRequest request = request("10.20.10.15");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.10.15");
    }

    @Test
    void resolveUsesRemoteAddressWhenCloudFrontHeaderIsMalformed() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver(true);
        MockHttpServletRequest request = request("10.20.10.15");
        request.addHeader("CloudFront-Viewer-Address", "invalid-value");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.10.15");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}