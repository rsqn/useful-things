package tech.rsqn.useful.things.web.filters;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;

public class LogHttpHeadersFilter implements Filter {

    private static Logger LOG = LoggerFactory.getLogger(LogHttpHeadersFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;

        LOG.debug("LogHttpHeadersFilter - RequstedURI is: " + req.getRequestURL());

        Enumeration<String> en = req.getHeaderNames();

        while (en.hasMoreElements()) {
            String n = en.nextElement();

            String v = req.getHeader(n);

            LOG.info(n + " = " + v);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
