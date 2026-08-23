package com.fintrack.apiservice.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
public class ClientIpAddressResolver {

    private static final String CLOUDFRONT_VIEWER_ADDRESS = "CloudFront-Viewer-Address";

    private final boolean trustCloudFrontViewerAddress;

    public ClientIpAddressResolver(@Value("${fintrack.http.trust-cloudfront-viewer-address:false}") boolean trustCloudFrontViewerAddress) {
        this.trustCloudFrontViewerAddress = trustCloudFrontViewerAddress;
    }

    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (!trustCloudFrontViewerAddress) {
            return request.getRemoteAddr();
        }

        String viewerAddress = request.getHeader(CLOUDFRONT_VIEWER_ADDRESS);

        if (!StringUtils.hasText(viewerAddress)) {
            return request.getRemoteAddr();
        }

        String clientIpAddress = extractIpAddress(viewerAddress.trim());

        return clientIpAddress != null ? clientIpAddress : request.getRemoteAddr();
    }

    private String extractIpAddress(String viewerAddress) {
        if (viewerAddress.startsWith("[")) {
            int closingBracket = viewerAddress.indexOf(']');

            if (closingBracket <= 1 || closingBracket + 1 >= viewerAddress.length()
                    || viewerAddress.charAt(closingBracket + 1) != ':') {
                return null;
            }

            return isValidPort(viewerAddress.substring(closingBracket + 2))
                    ? viewerAddress.substring(1, closingBracket)
                    : null;
        }

        int portSeparator = viewerAddress.lastIndexOf(':');

        if (portSeparator <= 0 || viewerAddress.indexOf(':') != portSeparator) {
            return null;
        }

        return isValidPort(viewerAddress.substring(portSeparator + 1))
                ? viewerAddress.substring(0, portSeparator)
                : null;
    }

    private boolean isValidPort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}