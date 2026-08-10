package com.fintrack.apiservice.common.cors;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "fintrack.cors")
public class CorsProperties {

    private List<String> allowedOrigins;
}