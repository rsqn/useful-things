package tech.rsqn.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.servlet.ServletException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EmbeddedJetty {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedJetty.class);
    private List<String> appBaseSearchPaths;
    private int port;
    private String contextPath;

    @Required
    public void setAppBaseSearchPaths(List<String> appBaseSearchPaths) {
        this.appBaseSearchPaths = appBaseSearchPaths;
    }

    @Required
    public void setPort(int port) {
        this.port = port;
    }

    @Required
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void start() throws Exception {
        Server server = null;
        try {
            log.info("Starting EmbeddedJetty on port {}", port);
            log.info("Current Working Directory is ", new File(".").getAbsolutePath() + " search paths will be relative to this directory");
            server = new Server(port);
            WebAppContext context = new WebAppContext();
            File webXml = null;


            List<String> webXmlPaths = new ArrayList();
            appBaseSearchPaths.forEach(s -> webXmlPaths.add(s + "/WEB-INF/web.xml"));

            for (String webXmlPath : webXmlPaths) {
                webXml = new File(webXmlPath);

                log.info("Looking for webXml at ({} : {})", webXmlPath, webXml.getAbsolutePath());

                if (webXml.exists()) {
                    log.info("Found webXML at ({})", webXml.getAbsolutePath());
                    break;
                } else {
                    log.info("No webXML at ({})", webXmlPath);
                    webXml = null;
                }
            }

            if (webXml == null) {
                log.error("No webXML Found - exiting");
                return;
            }

            File webDirectory = webXml.getParentFile().getParentFile();
            log.info("Webdir is at ({})", webDirectory);

            if (webDirectory == null) {
                log.error("No webDir Found - exiting");
                return;
            }

            context.setDescriptor(webXml.getPath());
            context.setResourceBase(webDirectory.getPath());
            context.setContextPath(contextPath);
            context.setParentLoaderPriority(true);
            context.setThrowUnavailableOnStartupException(true);
            server.setHandler(context);
            WebSocketServerContainerInitializer.configureContext(context);
            server.start();
            server.join();
        } catch (Exception e) {
            if (server != null) {
                server.stop();
            }
            log.error(e.getMessage(), e);
        }
    }
}