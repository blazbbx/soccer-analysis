package com.example.footballanalysis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Szűrő (Filter), amely minden bejövő HTTP kérésnél beállít egy egyedi 'traceId'-t az MDC-ben.
 * Ha a fejlécben érkezett 'X-Request-Id', azt használja, különben generál egy újat.
 * Az MDC-ben (Mapped Diagnostic Context) lévő érték minden olyan logban (Info, Debug, Error) 
 * automatikusan szerepelni fog, amely ehhez a szálhoz (thread) tartozik. 
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_PROPERTY = "traceId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String traceId = request.getHeader(REQUEST_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = traceId.trim();
            if (traceId.length() > MAX_REQUEST_ID_LENGTH || !REQUEST_ID_PATTERN.matcher(traceId).matches()) {
                log.debug("Rejected invalid {} header value; generated new traceId", REQUEST_ID_HEADER);
                traceId = UUID.randomUUID().toString();
            }
        }
        
        MDC.put(TRACE_ID_PROPERTY, traceId);
        
        // Let we attach it to the response header as well
        response.addHeader(REQUEST_ID_HEADER, traceId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_PROPERTY);
        }
    }
}
