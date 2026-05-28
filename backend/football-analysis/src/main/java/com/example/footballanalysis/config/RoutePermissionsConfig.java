package com.example.footballanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("idp")
public record RoutePermissionsConfig(List<RoutePermission> routePermissions) {
    public record RoutePermission(String path, String method, List<String> roles) {
        public boolean isPublic() {
            return roles == null || roles.isEmpty();
        }
    }
}
