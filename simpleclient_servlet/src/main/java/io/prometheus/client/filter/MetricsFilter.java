package io.prometheus.client.filter;

import io.prometheus.client.Histogram;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * The MetricsFilter class exists to provide a high-level filter that enables tunable collection of metrics for Servlet
 * performance.
 *
 * The Histogram name itself is required, and configured with a {@code metric-name} init parameter.
 *
 * By default, this filter will provide metrics that distinguish 3 levels deep for the request path
 * (including servlet context path), but can be configured with the {@code path-components} init parameter.
 *
 * The Histogram buckets can be configured with a {@code buckets} init parameter whose value is a comma-separated list of valid {@code double} values.
 *
 *
 * @author Andrew Stuart &lt;andrew.stuart2@gmail.com&gt;
 */
public class MetricsFilter implements Filter {
    public static final String PATH_COMPONENT_PARAM = "path-components";
    public static final String METRIC_NAME_PARAM = "metric-name";
    public static final String BUCKET_CONFIG_PARAM = "buckets";
    public static final int DEFAULT_PATH_COMPONENTS = 3;

    private Histogram servletLatency = null;

    // Package-level for testing purposes
    int pathComponents = DEFAULT_PATH_COMPONENTS;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Histogram.Builder builder = Histogram.build()
                .help("The time taken fulfilling servlet requests")
                .labelNames("path", "method");

        if (filterConfig == null) {
            throw new ServletException("Please provide a configuration object, even for testing.");
        }

        if (StringUtils.isEmpty(filterConfig.getInitParameter(METRIC_NAME_PARAM))) {
            throw new ServletException("Init parameter \"" + METRIC_NAME_PARAM + "\" is required. Please supply a value");
        }

        // "metric-name" is required
        builder.name(filterConfig.getInitParameter(METRIC_NAME_PARAM));

        // Allow overriding of the path "depth" to track
        if (!StringUtils.isEmpty(filterConfig.getInitParameter(PATH_COMPONENT_PARAM))) {
            pathComponents = Integer.valueOf(filterConfig.getInitParameter(PATH_COMPONENT_PARAM));
        }

        // Allow users to override the default bucket configuration
        if (!StringUtils.isEmpty(filterConfig.getInitParameter(BUCKET_CONFIG_PARAM))) {
            String[] bucketParams = filterConfig.getInitParameter(BUCKET_CONFIG_PARAM).split(",");
            double[] buckets = new double[bucketParams.length];

            for (int i = 0; i < bucketParams.length; i++) {
                buckets[i] = Double.parseDouble(bucketParams[i]);
            }

            builder.buckets(buckets);
        }

        servletLatency = builder.register();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        String path = request.getRequestURI();
        int lastSlash = StringUtils.ordinalIndexOf(path, "/", pathComponents+1);

        Histogram.Timer timer = servletLatency
            .labels(lastSlash == -1 ? path : path.substring(0, lastSlash), request.getMethod())
            .startTimer();

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            timer.observeDuration();
        }
    }

    @Override
    public void destroy() {
    }
}
