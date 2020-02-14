package com.github.onsdigital.zebedee.reader.api.filter;

import com.github.davidcarboni.restolino.framework.Filter;
import com.github.onsdigital.logging.util.RequestLogUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.github.onsdigital.zebedee.logging.ReaderLogger.info;

/**
 * Filter to add the X-Request-Id and remote address to the {@link org.slf4j.MDC} for logging.
 */
public class MDCFilter implements Filter {

    @Override
    public boolean filter(HttpServletRequest request, HttpServletResponse response) {
        RequestLogUtil.extractDiagnosticContext(request);
        info().beginHTTP(request).log("request receieved");
        return true;
    }
}
