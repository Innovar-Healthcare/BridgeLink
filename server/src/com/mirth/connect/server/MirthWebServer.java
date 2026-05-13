/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import javax.servlet.DispatcherType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ext.Provider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HTTP;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.LowResourceMonitor;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

// Jetty 12 EE8 imports (for javax.servlet compatibility)
import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.HandlerList;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.ResourceHandler;
import org.eclipse.jetty.ee8.nested.SessionHandler;
import org.eclipse.jetty.ee8.servlet.DefaultServlet;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.ee8.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee8.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee8.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.ee8.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee8.webapp.WebInfConfiguration;
import org.eclipse.jetty.ee8.webapp.WebXmlConfiguration;

// Jetty 12 Session imports
import org.eclipse.jetty.session.DatabaseAdaptor;
import org.eclipse.jetty.session.DefaultSessionCacheFactory;
import org.eclipse.jetty.session.JDBCSessionDataStore.SessionTableSchema;
import org.eclipse.jetty.session.JDBCSessionDataStoreFactory;
import org.eclipse.jetty.session.NullSessionCacheFactory;
import org.eclipse.jetty.session.NullSessionDataStore;
import org.eclipse.jetty.session.SessionCache;
import org.eclipse.jetty.session.SessionDataStore;
import org.eclipse.jetty.session.SessionDataStoreFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.wadl.internal.generators.WadlGeneratorJAXBGrammarGenerator;
import org.glassfish.jersey.servlet.ServletContainer;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.Replaces;
import com.mirth.connect.model.ApiProvider;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.api.providers.ApiOriginFilter;
import com.mirth.connect.server.api.providers.ClickjackingFilter;
import com.mirth.connect.server.api.providers.RequestedWithFilter;
import com.mirth.connect.server.api.providers.StrictTransportSecurityFilter;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.servlets.SwaggerExamplesServlet;
import com.mirth.connect.server.servlets.SwaggerServlet;
import com.mirth.connect.server.servlets.WebStartServlet;
import com.mirth.connect.server.tools.ClassPathResource;
import com.mirth.connect.server.util.PackagePredicate;
import com.mirth.connect.server.util.SqlConfig;
import com.mirth.connect.util.MirthSSLUtil;

import io.swagger.v3.jaxrs2.integration.resources.AcceptHeaderOpenApiResource;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;

public class MirthWebServer extends Server {

    private static final String CONNECTOR = "connector";
    private static final String CONNECTOR_SSL = "sslconnector";

    private Logger logger = LogManager.getLogger(getClass());
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();
    private List<WebAppContext> webapps;
    private HandlerList handlers;
    private ServerConnector connector;
    private ServerConnector sslConnector;

    public MirthWebServer(PropertiesConfiguration mirthProperties) throws Exception {
        // this disables a "form too large" error for occurring by setting
        // form size to infinite
        System.setProperty("org.eclipse.jetty.server.Request.maxFormContentSize", "-1");

        // Suppress logging from the WADL generator for OPTIONS requests 
        Logger logger2 = LogManager.getLogger(WadlGeneratorJAXBGrammarGenerator.class);
        Configurator.setLevel(logger2.getName(), Level.OFF);

        String baseAPI = "/api";

        boolean usingHttp = mirthProperties.containsKey("http.port") && mirthProperties.getInt("http.port") > 0;

        boolean apiAllowHTTP = usingHttp && Boolean.parseBoolean(mirthProperties.getString("server.api.allowhttp", "false"));

        if (usingHttp) {
            // add HTTP listener
            HttpConfiguration config = new HttpConfiguration();
            config.addCustomizer(new ForwardedRequestCustomizer());
            config.setSendServerVersion(false);
            config.setSendXPoweredBy(false);
            connector = new ServerConnector(this, new HttpConnectionFactory(config));
            connector.setName(CONNECTOR);
            connector.setHost(mirthProperties.getString("http.host", "0.0.0.0"));
            connector.setPort(mirthProperties.getInt("http.port"));
        }

        // add HTTPS listener
        sslConnector = createSSLConnector(CONNECTOR_SSL, mirthProperties);

        /*
         * Allows users to decide whether to store session data in the database.
         */
        boolean sessionStore = Boolean.parseBoolean(mirthProperties.getString("server.api.sessionstore", "false"));

        if (sessionStore) {
            // The name of the table to create in the database
            String sessionStoreTable = mirthProperties.getString("server.api.sessionstoretable", "sessiondata");
            addBean(createSessionDataStoreFactory(sessionStoreTable));
        }

        /*
         * Allows users to decide whether to use an L1 cache of session data at the JVM level. The
         * null session cache will only be used if session storage is enabled.
         * 
         * "none": NullSessionCache
         * 
         * "default" / anything else: DefaultSessionCache
         */
        String sessionCacheProperty = mirthProperties.getString("server.api.sessioncache", "default");

        if (StringUtils.equalsIgnoreCase(sessionCacheProperty, "none") && sessionStore) {
            addBean(new NullSessionCacheFactory());
        } else {
            // Session caching
            DefaultSessionCacheFactory sessionCacheFactory = new DefaultSessionCacheFactory();
            // Evict from the cache after the inactive period has elapsed, default value is 72 hours (3 days)
            sessionCacheFactory.setEvictionPolicy(configurationController.getMaxInactiveSessionInterval());
            addBean(sessionCacheFactory);
        }

        handlers = new HandlerList();
        String contextPath = mirthProperties.getString("http.contextpath", "");

        // Add a starting slash if one does not exist
        if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }

        // Remove a trailing slash if one exists
        if (contextPath.endsWith("/")) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }

        // find the client-lib path
        String clientLibPath = null;

        if (ClassPathResource.getResourceURI("client-lib") != null) {
            clientLibPath = Paths.get(ClassPathResource.getResourceURI("client-lib")).toString() + File.separator;
        } else {
            clientLibPath = ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "client-lib" + File.separator;
        }

        // Create the lib context
        ContextHandler libContextHandler = new ContextHandler();
        libContextHandler.setContextPath(contextPath + "/webstart/client-lib");
        ResourceHandler libResourceHandler = new ResourceHandler();
        libResourceHandler.setResourceBase(clientLibPath);
        libContextHandler.setHandler(libResourceHandler);
        handlers.addHandler(libContextHandler);

        // Create the extensions context
        ContextHandler extensionsContextHandler = new ContextHandler();
        extensionsContextHandler.setContextPath(contextPath + "/webstart/extensions/libs");
        String extensionsPath = new File(ExtensionController.getExtensionsPath()).getPath();
        ResourceHandler extensionsResourceHandler = new ResourceHandler();
        extensionsResourceHandler.setResourceBase(extensionsPath);
        extensionsContextHandler.setHandler(extensionsResourceHandler);
        handlers.addHandler(extensionsContextHandler);

        // Create a combined public_html and webstart context
        // In Jetty 12, we need a single context handler for the root path that handles both
        // static files and webstart servlets
        ServletContextHandler rootContextHandler = new ServletContextHandler();
        rootContextHandler.setContextPath(contextPath);
        
        // Add method filter for webstart
        rootContextHandler.addFilter(new FilterHolder(new MethodFilter()), "/webstart.jnlp", EnumSet.of(DispatcherType.REQUEST));
        rootContextHandler.addFilter(new FilterHolder(new MethodFilter()), "/webstart", EnumSet.of(DispatcherType.REQUEST));
        rootContextHandler.addFilter(new FilterHolder(new MethodFilter()), "/webstart/*", EnumSet.of(DispatcherType.REQUEST));
        
        // Add webstart servlets
        rootContextHandler.addServlet(new ServletHolder(new WebStartServlet()), "/webstart.jnlp");
        rootContextHandler.addServlet(new ServletHolder(new WebStartServlet()), "/webstart");
        rootContextHandler.addServlet(new ServletHolder(new WebStartServlet()), "/webstart/extensions/*");
        
        // Add default servlet for static files from public_html
        String publicPath = ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "public_html";
        ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
        defaultServlet.setInitParameter("resourceBase", publicPath);
        defaultServlet.setInitParameter("dirAllowed", "false");
        rootContextHandler.addServlet(defaultServlet, "/");
        handlers.addHandler(rootContextHandler);

        // Create Administrator Launcher installer contexts
        addLauncherInstallerContextHandlers(contextPath);

        // Create the javadocs context
        ContextHandler javadocsContextHandler = new ContextHandler();
        javadocsContextHandler.setContextPath(contextPath + "/javadocs");
        String javadocsPath = ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "docs" + File.separator + "javadocs";
        ResourceHandler javadocsResourceHandler = new ResourceHandler();
        javadocsResourceHandler.setResourceBase(javadocsPath);
        javadocsResourceHandler.setDirectoriesListed(true);
        javadocsContextHandler.setHandler(javadocsResourceHandler);
        handlers.addHandler(javadocsContextHandler);

        // Load all web apps dynamically
        webapps = new ArrayList<WebAppContext>();

        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".war");
            }
        };

        /*
         * If in an IDE, webapps will be on the classpath as a resource. If that's the case, use
         * that directory. Otherwise, use the mirth home directory and append webapps.
         */
        String webappsDir = null;
        if (ClassPathResource.getResourceURI("webapps") != null) {
            webappsDir = ClassPathResource.getResourceURI("webapps").getPath() + File.separator;
        } else {
            webappsDir = ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "webapps" + File.separator;
        }

        File[] listOfFiles = new File(webappsDir).listFiles(filter);

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                logger.debug("webApp File Path: " + file.getAbsolutePath());

                WebAppContext webapp = new WebAppContext();

                // Always use the default session cache for the webadmin context, since it stores the Client in memory
                SessionHandler sessionHandler = new SessionHandler();

                DefaultSessionCacheFactory sessionCacheFactory = new DefaultSessionCacheFactory();
                // Evict from the cache after the inactive period has elapsed, default value is 72 hours (3 days)
                sessionCacheFactory.setEvictionPolicy(configurationController.getMaxInactiveSessionInterval());
                SessionCache sessionCache = sessionCacheFactory.getSessionCache(sessionHandler.getSessionManager());

                // Uses the same method as SessionHandler to determine the data store
                SessionDataStore sessionDataStore = null;
                SessionDataStoreFactory sessionDataStoreFactory = getBean(SessionDataStoreFactory.class);
                if (sessionDataStoreFactory != null) {
                    sessionDataStore = sessionDataStoreFactory.getSessionDataStore(sessionHandler.getSessionManager());
                } else {
                    sessionDataStore = new NullSessionDataStore();
                }
                sessionCache.setSessionDataStore(sessionDataStore);

                // Set the session cache directly on the handler so it doesn't use the server bean
                sessionHandler.getSessionManager().setSessionCache(sessionCache);
                webapp.setSessionHandler(sessionHandler);
                
                webapp.setContextPath(contextPath + "/" + file.getName().substring(0, file.getName().length() - 4));
                webapp.addFilter(new FilterHolder(new ClickjackingFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));
                webapp.addFilter(new FilterHolder(new StrictTransportSecurityFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));

                /*
                 * Set the ContainerIncludeJarPattern so that Jetty examines these JARs for TLDs,
                 * web fragments, etc. If you omit the jar that contains the JSTL TLDs, the JSP
                 * engine will scan for them instead.
                 */
                webapp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*/[^/]*(javax\\.servlet-api|taglibs)[^/]*\\.jar$");

                logger.debug("webApp Context Path: " + webapp.getContextPath());

                webapp.setWar(file.getAbsolutePath());
                handlers.addHandler(webapp);
                webapps.add(webapp);
            }
        }

        // TODO: Fully support backward compatibility for models before exposing earlier servlets
        ServletContextHandler apiServletContextHandler = createApiServletContextHandler(contextPath, baseAPI, apiAllowHTTP, Version.getLatest(), mirthProperties);
        
        addApiServlets(handlers, apiServletContextHandler, contextPath, baseAPI, apiAllowHTTP, Version.getLatest(), mirthProperties);
        // Add Jersey API / swagger servlets for each specific version
//        Version version = Version.getApiEarliest();
//        while (version != null) {
//            addApiServlets(handlers, contextPath, baseAPI, apiAllowHTTP, version, mirthProperties);
//            version = version.getNextVersion();
//        }
        // Add servlets for the main (default) API endpoint
        apiServletContextHandler = createApiServletContextHandler(contextPath, baseAPI, apiAllowHTTP, null, mirthProperties);
        addApiServlets(handlers, apiServletContextHandler, contextPath, baseAPI, apiAllowHTTP, null, mirthProperties);
        
        addSwaggerServlets(handlers, apiServletContextHandler, contextPath, baseAPI, apiAllowHTTP, null);

       // Add a redirect handler at /api/version/* to forward to /api/*
       // This allows old clients using /api/4.6.1/* to be redirected to the primary /api/* endpoint
       final String finalContextPath = contextPath;
       final String finalBaseAPI = baseAPI;
       final String finalVersion = String.valueOf(Version.getLatest());
       ServletContextHandler versionedRedirectHandler = new ServletContextHandler();
       versionedRedirectHandler.setContextPath(finalContextPath + finalBaseAPI + "/" + finalVersion);
       ServletHolder redirectServlet = new ServletHolder("redirect", new javax.servlet.http.HttpServlet() {
           @Override
           protected void service(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
               String pathInfo = request.getPathInfo();
               // Don't redirect the root path (/)
               if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
                   // For root path, just return 404 or forward to unversioned endpoint
                   String redirectPath = finalContextPath + finalBaseAPI + "/";
                   response.sendRedirect(redirectPath);
                   return;
               }
               // Redirect to unversioned endpoint
               String redirectPath = finalContextPath + finalBaseAPI + pathInfo;
               if (request.getQueryString() != null) {
                   redirectPath += "?" + request.getQueryString();
               }
               response.sendRedirect(redirectPath);
           }
       });
       versionedRedirectHandler.addServlet(redirectServlet, "/*");
       handlers.addHandler(versionedRedirectHandler);

        // In Jetty 12, we need to wrap the EE8 HandlerList in a core handler
        // The handlers' core context handlers will be collected and set on the server
        org.eclipse.jetty.server.handler.ContextHandlerCollection coreHandlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection();
        for (org.eclipse.jetty.ee8.nested.Handler handler : handlers.getHandlers()) {
            logger.debug("Processing handler: " + handler.getClass().getName());
            if (handler instanceof ContextHandler) {
                ContextHandler contextHandler = (ContextHandler) handler;
                logger.debug("Adding context handler with path: " + contextHandler.getContextPath());
                // In Jetty 12 EE8, we must set the server on the handler before getting its core handler
                contextHandler.setServer(this);
                coreHandlers.addHandler(contextHandler.getCoreContextHandler());
            } else {
                logger.debug("Skipping handler (not a ContextHandler): " + handler.getClass().getName());
            }
        }
        
        // Add a default handler for 404s
        org.eclipse.jetty.server.handler.DefaultHandler defaultHandler = new org.eclipse.jetty.server.handler.DefaultHandler();
        defaultHandler.setServeFavIcon(false); // don't serve the Jetty favicon

        // Use a Handler.Sequence to combine the context handlers with the default handler
        setHandler(new org.eclipse.jetty.server.Handler.Sequence(coreHandlers, defaultHandler));

        if (usingHttp) {
            setConnectors(new Connector[] { connector, sslConnector });
        } else {
            setConnectors(new Connector[] { sslConnector });
        }
    }

    public void startup() throws Exception {
        try {
            start();
        } catch (Throwable e) {
            logger.error("Could not load web app", e);
            try {
                stop();
            } catch (Throwable t) {
                // Ignore exception stopping
            }
            for (WebAppContext webapp : webapps) {
                handlers.removeHandler(webapp);
            }
            start();
        }
        logger.debug("started jetty web server on ports: " + (connector != null ? connector.getPort() + ", " : "") + sslConnector.getPort());
    }

    private ServerConnector createSSLConnector(String name, PropertiesConfiguration mirthProperties) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(mirthProperties.getString("keystore.type", "JCEKS"));
        FileInputStream is = new FileInputStream(new File(mirthProperties.getString("keystore.path")));
        try {
            keyStore.load(is, mirthProperties.getString("keystore.storepass").toCharArray());
        } finally {
            IOUtils.closeQuietly(is);
        }

        SslContextFactory.Server contextFactory = new SslContextFactory.Server();
        contextFactory.setKeyStore(keyStore);
        contextFactory.setCertAlias("mirthconnect");
        contextFactory.setKeyManagerPassword(mirthProperties.getString("keystore.keypass"));
        contextFactory.setEndpointIdentificationAlgorithm(null);
        // Jetty 12: Disable SNI host checking to allow connections via localhost/IP
        contextFactory.setSniRequired(false);

        HttpConfiguration config = new HttpConfiguration();
        config.setSecureScheme("https");
        config.setSecurePort(mirthProperties.getInt("https.port"));
        // Jetty 12: Configure SecureRequestCustomizer to disable SNI host check
        SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
        secureRequestCustomizer.setSniHostCheck(false);
        config.addCustomizer(secureRequestCustomizer);
        config.setSendServerVersion(false);
        config.setSendXPoweredBy(false);

        ServerConnector sslConnector = new ServerConnector(this, new SslConnectionFactory(contextFactory, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(config));

        /*
         * http://www.mirthcorp.com/community/issues/browse/MIRTH-3070 Keep SSL connections alive
         * for 24 hours unless closed by the client. When the Administrator runs on Windows, the SSL
         * handshake performed when a new connection is created takes about 4-5 seconds if
         * connecting via IP address and no reverse DNS entry can be found. By keeping the
         * connection alive longer the Administrator shouldn't have to perform the handshake unless
         * idle for this amount of time.
         */
        sslConnector.setIdleTimeout(86400000);

        LowResourceMonitor lowResourceMonitor = new LowResourceMonitor(this);
        lowResourceMonitor.setMonitoredConnectors(Collections.singleton((Connector) sslConnector));
        // If the number of connections open reaches 200, use the MaxConnectionsLowResourceCheck
        lowResourceMonitor.addLowResourceCheck(lowResourceMonitor.new MaxConnectionsLowResourceCheck(200));
        // Then close connections after 200 seconds, which is the default MaxIdleTime value. This should affect existing connections as well.
        lowResourceMonitor.setLowResourcesIdleTimeout(200000);

        sslConnector.setName(name);
        sslConnector.setHost(mirthProperties.getString("https.host", "0.0.0.0"));
        sslConnector.setPort(mirthProperties.getInt("https.port"));

        /*
         * We were previously disabling low and medium strength ciphers (MIRTH-1924). However with
         * MIRTH-3492, we're now always specifying an include list everywhere rather than an exclude
         * list. Remove excluded lists first because Jetty sets them by default.
         */
        contextFactory.setExcludeProtocols();
        contextFactory.setExcludeCipherSuites();
        contextFactory.setIncludeProtocols(MirthSSLUtil.getEnabledHttpsProtocols(configurationController.getHttpsServerProtocols()));
        contextFactory.setIncludeCipherSuites(MirthSSLUtil.getEnabledHttpsCipherSuites(configurationController.getHttpsCipherSuites()));

        return sslConnector;
    }

    private ServletContextHandler createApiServletContextHandler(String contextPath, String baseAPI, boolean apiAllowHTTP, Version version, PropertiesConfiguration mirthProperties) {
    	String apiPath = "";
        Version apiVersion = version;
        if (apiVersion != null) {
            apiPath += "/" + apiVersion.toString();
        }
    	
        // Create the servlet handler for the API
    	ServletContextHandler apiServletContextHandler = new ServletContextHandler();
        apiServletContextHandler.setMaxFormContentSize(0);
        apiServletContextHandler.setSessionHandler(new SessionHandler());
        apiServletContextHandler.setContextPath(contextPath + baseAPI + apiPath);
        apiServletContextHandler.addFilter(new FilterHolder(new ApiOriginFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));
        apiServletContextHandler.addFilter(new FilterHolder(new ClickjackingFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));
        
        // Swagger UI filter - must be BEFORE RequestedWithFilter so that static file requests
        // (index.html, css, js) don't require the X-Requested-With header
        final String swaggerUiBase = ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "public_api_html";
        apiServletContextHandler.addFilter(new FilterHolder(new javax.servlet.Filter() {
            @Override
            public void init(javax.servlet.FilterConfig filterConfig) throws javax.servlet.ServletException {}
            @Override
            public void destroy() {}
            @Override
            public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response, javax.servlet.FilterChain chain) throws java.io.IOException, javax.servlet.ServletException {
                javax.servlet.http.HttpServletRequest httpRequest = (javax.servlet.http.HttpServletRequest) request;
                javax.servlet.http.HttpServletResponse httpResponse = (javax.servlet.http.HttpServletResponse) response;
                String pathInfo = httpRequest.getPathInfo();
                
                // Serve index.html for root /api/ request
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    java.io.File indexFile = new java.io.File(swaggerUiBase, "index.html");
                    if (indexFile.exists()) {
                        httpResponse.setContentType("text/html; charset=UTF-8");
                        httpResponse.setStatus(javax.servlet.http.HttpServletResponse.SC_OK);
                        java.nio.file.Files.copy(indexFile.toPath(), httpResponse.getOutputStream());
                        return;
                    }
                }
                
                // Serve static files from public_api_html if they exist (css, js, images, etc.)
                if (pathInfo != null && !pathInfo.equals("/")) {
                    java.io.File staticFile = new java.io.File(swaggerUiBase, pathInfo);
                    if (staticFile.exists() && staticFile.isFile() && staticFile.getCanonicalPath().startsWith(new java.io.File(swaggerUiBase).getCanonicalPath())) {
                        String fileName = staticFile.getName();
                        if (fileName.endsWith(".css")) httpResponse.setContentType("text/css");
                        else if (fileName.endsWith(".js")) httpResponse.setContentType("application/javascript");
                        else if (fileName.endsWith(".html")) httpResponse.setContentType("text/html");
                        else if (fileName.endsWith(".png")) httpResponse.setContentType("image/png");
                        else if (fileName.endsWith(".json")) httpResponse.setContentType("application/json");
                        else if (fileName.endsWith(".map")) httpResponse.setContentType("application/json");
                        else httpResponse.setContentType("application/octet-stream");
                        httpResponse.setStatus(javax.servlet.http.HttpServletResponse.SC_OK);
                        java.nio.file.Files.copy(staticFile.toPath(), httpResponse.getOutputStream());
                        return;
                    }
                }
                
                // Not a static file - pass through to remaining filters and Jersey
                chain.doFilter(request, response);
            }
        }), "/*", EnumSet.of(DispatcherType.REQUEST));
        
        apiServletContextHandler.addFilter(new FilterHolder(new RequestedWithFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));
        apiServletContextHandler.addFilter(new FilterHolder(new MethodFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));
        apiServletContextHandler.addFilter(new FilterHolder(new StrictTransportSecurityFilter(mirthProperties)), "/*", EnumSet.of(DispatcherType.REQUEST));
        setConnectorNames(apiServletContextHandler, apiAllowHTTP);
    
        
        
        return apiServletContextHandler;
    }
    
    private void addApiServlets(HandlerList handlers, ServletContextHandler apiServletContextHandler, String contextPath, String baseAPI, boolean apiAllowHTTP, Version version, PropertiesConfiguration mirthProperties) {
        Version apiVersion = version;
        if (apiVersion == null) {
        	apiVersion = Version.getLatest();
        }

        ApiProviders apiProviders = getApiProviders(apiVersion);

        // Add versioned Jersey API servlet
        ServletHolder jerseyVersionedServlet = apiServletContextHandler.addServlet(ServletContainer.class, "/*");
        jerseyVersionedServlet.setInitOrder(1);
        jerseyVersionedServlet.setInitParameter(ServerProperties.PROVIDER_PACKAGES, StringUtils.join(apiProviders.providerPackages, ','));
        jerseyVersionedServlet.setInitParameter(ServerProperties.PROVIDER_CLASSNAMES, joinClasses(apiProviders.providerClasses));

        // Add API handler
        handlers.addHandler(apiServletContextHandler);
    }
    
    private void addSwaggerServlets(HandlerList handlers, ServletContextHandler apiServletContextHandler, String contextPath, String baseAPI, boolean apiAllowHTTP, Version version) {
    	String apiPath = "";
        // Use provided version, or default to latest if not provided
        Version apiVersion = (version != null) ? version : Version.getLatest();
        logger.info("apiVersion:" + apiVersion);

        ApiProviders apiProviders = getApiProviders(apiVersion);

        // Add versioned Swagger bootstrap configuration servlet
        SwaggerServlet swaggerServlet = new SwaggerServlet(contextPath + baseAPI + apiPath, null, apiVersion, apiProviders.servletInterfacePackages, apiProviders.servletInterfaces, apiAllowHTTP);
        ServletHolder swaggerVersionedServlet = new ServletHolder(swaggerServlet);
        swaggerVersionedServlet.setInitOrder(2);
        apiServletContextHandler.addServlet(swaggerVersionedServlet, contextPath + baseAPI + apiPath + "/openapi.json");
        apiServletContextHandler.addServlet(swaggerVersionedServlet, contextPath + baseAPI + apiPath + "/openapi.yaml");

        // Add Swagger examples servlet
        ServletContextHandler swaggerExamplesServletContextHandler = new ServletContextHandler();
        swaggerExamplesServletContextHandler.setContextPath("/apiexamples");
        ServletHolder swaggerExamplesServlet = new ServletHolder(new SwaggerExamplesServlet());
        swaggerExamplesServlet.setInitOrder(3);
        swaggerExamplesServletContextHandler.addServlet(swaggerExamplesServlet, "/*");
        

        handlers.addHandler(swaggerExamplesServletContextHandler);
    }



    private void setConnectorNames(ContextHandler contextHandler, boolean apiAllowHTTP) {
        List<String> connectorNames = new ArrayList<String>();
        connectorNames.add("@" + CONNECTOR_SSL);
        if (apiAllowHTTP) {
            connectorNames.add("@" + CONNECTOR);
        }
        contextHandler.setVirtualHosts(connectorNames.toArray(new String[connectorNames.size()]));
    }

    private class ApiProviders {
        public Set<String> servletInterfacePackages;
        public Set<Class<?>> servletInterfaces;
        public Set<String> providerPackages;
        public Set<Class<?>> providerClasses;

        public ApiProviders(Set<String> servletInterfacePackages, Set<Class<?>> servletInterfaces, Set<String> providerPackages, Set<Class<?>> providerClasses) {
            this.servletInterfacePackages = servletInterfacePackages;
            this.servletInterfaces = servletInterfaces;
            this.providerPackages = providerPackages;
            this.providerClasses = providerClasses;
        }
    }
    
    private ApiProviders getApiProviders(Version version) {
        // These contain only the shared servlet interfaces, and will be used to generate the Swagger models.
        Set<String> servletInterfacePackages = new LinkedHashSet<String>();
        Set<Class<?>> servletInterfaces = new LinkedHashSet<Class<?>>();
        servletInterfaces.addAll(getApiClassesForVersion("com.mirth.connect.client.core.api.servlets", version, new Class<?>[] {
                BaseServletInterface.class }, new Class<?>[0]));

        // These are JAX-RS providers that should be shared on the client and server.
        Set<String> coreProviderPackages = new LinkedHashSet<String>();
        Set<Class<?>> coreProviderClasses = new LinkedHashSet<Class<?>>();
        coreProviderClasses.addAll(getApiClassesForVersion("com.mirth.connect.client.core.api.providers", version, new Class<?>[0], new Class<?>[] {
                Provider.class }));
        coreProviderClasses.add(MultiPartFeature.class);

        /*
         * These are JAX-RS providers that are on the server side only. Servlet implementation
         * classes should be added directly to the class set, as JAX-RS does not scan for subclasses
         * of a parent class that has provider annotations.
         */
        Set<String> serverProviderPackages = new LinkedHashSet<String>();
        serverProviderPackages.add("io.swagger.jaxrs.listing");
        Set<Class<?>> serverProviderClasses = new LinkedHashSet<Class<?>>();
        serverProviderClasses.addAll(getApiClassesForVersion("com.mirth.connect.server.api.providers", version, new Class<?>[0], new Class<?>[] {
                Provider.class }));
        serverProviderClasses.addAll(getApiClassesForVersion("com.mirth.connect.server.api.servlets", version, new Class<?>[] {
                MirthServlet.class }, new Class<?>[0]));

        // Add JAX-RS providers from extensions
        for (MetaData metaData : CollectionUtils.union(extensionController.getPluginMetaData().values(), extensionController.getConnectorMetaData().values())) {
            if (extensionController.isExtensionEnabled(metaData.getName())) {
                for (ApiProvider apiProvider : metaData.getApiProviders(version)) {
                    try {
                        switch (apiProvider.getType()) {
                            case SERVLET_INTERFACE_PACKAGE:
                                servletInterfacePackages.add(apiProvider.getName());
                                break;
                            case SERVLET_INTERFACE:
                                servletInterfaces.add(Class.forName(apiProvider.getName()));
                                break;
                            case CORE_PACKAGE:
                                coreProviderPackages.add(apiProvider.getName());
                                break;
                            case SERVER_PACKAGE:
                                serverProviderPackages.add(apiProvider.getName());
                                break;
                            case CORE_CLASS:
                                coreProviderClasses.add(Class.forName(apiProvider.getName()));
                                break;
                            case SERVER_CLASS:
                                serverProviderClasses.add(Class.forName(apiProvider.getName()));
                                break;
                        }
                    } catch (Throwable t) {
                        logger.error("Error adding API provider to web server: " + apiProvider);
                    }
                }
            }
        }

        Set<String> providerPackages = new LinkedHashSet<String>();
        Set<Class<?>> providerClasses = new LinkedHashSet<Class<?>>();
        providerPackages.addAll(coreProviderPackages);
        providerPackages.addAll(serverProviderPackages);
        providerClasses.addAll(coreProviderClasses);
        providerClasses.addAll(serverProviderClasses);
        providerClasses.add(OpenApiResource.class);
        providerClasses.add(AcceptHeaderOpenApiResource.class);

        return new ApiProviders(servletInterfacePackages, servletInterfaces, providerPackages, providerClasses);
    }

    private Set<Class<?>> getApiClassesForVersion(String packageName, Version version, Class<?>[] baseClasses, Class<?>[] annotations) {
        // If it's the latest version always use the default package
        if (version == Version.getLatest()) {
            return getClassesInPackage(packageName, baseClasses, annotations);
        }

        /*
         * First, see if there are any versioned packages ahead of the given version. If so, then we
         * know we're not going to be using the default package.
         */
        Version testVersion = version.getNextVersion();
        boolean useDefaultPackage = true;
        while (testVersion != null) {
            if (testPackageVersion(packageName, testVersion, baseClasses, annotations)) {
                useDefaultPackage = false;
                break;
            }
            testVersion = testVersion.getNextVersion();
        }
        if (useDefaultPackage) {
            return getClassesInPackage(packageName, baseClasses, annotations);
        }

        /*
         * At this point we know we have to use an older version of the package. So start at the
         * beginning and work forwards, replacing classes as needed.
         */
        Set<Class<?>> classes = new HashSet<Class<?>>();
        testVersion = Version.getApiEarliest();
        while (testVersion != null && testVersion.ordinal() <= version.ordinal()) {
            for (Class<?> clazz : getClassesInPackage(getVersionedPackageName(packageName, testVersion), baseClasses, annotations)) {
                Replaces replaces = clazz.getAnnotation(Replaces.class);
                if (replaces != null) {
                    classes.remove(replaces.value());
                }
                classes.add(clazz);
            }

            testVersion = testVersion.getNextVersion();
        }
        return classes;
    }

    @SuppressWarnings("unchecked")
    private Set<Class<?>> getClassesInPackage(String packageName, Class<?>[] baseClasses, Class<?>[] annotations) {
        ConfigurationBuilder config = new ConfigurationBuilder();
        config.setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner(false));
        config.addUrls(ClasspathHelper.forPackage(packageName));
        config.setInputsFilter(new PackagePredicate(packageName));
        Reflections reflections = new Reflections(config);

        Set<Class<?>> classes = new HashSet<Class<?>>();
        if (ArrayUtils.isNotEmpty(baseClasses)) {
            for (Class<?> baseClass : baseClasses) {
                classes.addAll(reflections.getSubTypesOf(baseClass));
            }
        }
        if (ArrayUtils.isNotEmpty(annotations)) {
            for (Class<?> annotation : annotations) {
                if (annotation.isAnnotation()) {
                    classes.addAll(reflections.getTypesAnnotatedWith((Class<? extends Annotation>) annotation));
                }
            }
        }
        return classes;
    }

    private boolean testPackageVersion(String packageName, Version version, Class<?>[] baseClasses, Class<?>[] annotations) {
        packageName = getVersionedPackageName(packageName, version);
        try {
            // Look for package-info.java first
            Class.forName(packageName + ".package-info");
            return true;
        } catch (ClassNotFoundException e) {
        }
        return CollectionUtils.isNotEmpty(getClassesInPackage(packageName, baseClasses, annotations));
    }

    private String getVersionedPackageName(String packageName, Version version) {
        return packageName + "." + version.toPackageString();
    }

    private String joinClasses(Set<Class<?>> classes) {
        StringBuilder builder = new StringBuilder();

        if (CollectionUtils.isNotEmpty(classes)) {
            boolean added = false;
            for (Class<?> clazz : classes) {
                if (clazz != null) {
                    String name = clazz.getCanonicalName();
                    if (name != null) {
                        if (added) {
                            builder.append(',');
                        }
                        builder.append(name);
                        added = true;
                    }
                }
            }
        }

        return builder.toString();
    }

    private SessionDataStoreFactory createSessionDataStoreFactory(String sessionStoreTable) throws SQLException {
        JDBCSessionDataStoreFactory jdbcSDSFactory = new JDBCSessionDataStoreFactory();
        SessionTableSchema schema = new SessionTableSchema();
        schema.setTableName(sessionStoreTable);
        jdbcSDSFactory.setSessionTableSchema(schema);

        /*
         * The default Jetty implementation doesn't account for SQL Server's "image" data type so we
         * add that ourselves.
         */
        DatabaseAdaptor dbAdapter = new DatabaseAdaptor() {
            @Override
            public String getBlobType() {
                if (_blobType == null && StringUtils.containsIgnoreCase(getDBName(), "sql server")) {
                    setBlobType("image");
                }
                return super.getBlobType();
            }
        };

        SqlSessionManager sqlSessionManager = SqlConfig.getInstance().getSqlSessionManager();
        sqlSessionManager.startManagedSession();
        Connection connection = sqlSessionManager.getConnection();
        try {
            dbAdapter.adaptTo(connection.getMetaData());
            dbAdapter.setDatasource(sqlSessionManager.getConfiguration().getEnvironment().getDataSource());
        } finally {
            if (sqlSessionManager.isManagedSessionStarted()) {
                sqlSessionManager.close();
            }
        }
        jdbcSDSFactory.setDatabaseAdaptor(dbAdapter);

        return jdbcSDSFactory;
    }

    private void addLauncherInstallerContextHandlers(String contextPath) {
        File installersDirectory = new File(ControllerFactory.getFactory().createConfigurationController().getBaseDir() + File.separator + "public_html" + File.separator + "installers");

        java.util.Collection<File> installerFiles;
        if (installersDirectory.exists() && installersDirectory.isDirectory()) {
            installerFiles = FileUtils.listFiles(installersDirectory, TrueFileFilter.TRUE, FalseFileFilter.FALSE);
        } else {
            installerFiles = new ArrayList<File>();
        }

        MimeTypes.Mutable mimeTypes = new MimeTypes.Mutable();
        mimeTypes.addMimeMapping("dmg", "application/x-apple-diskimage");
        mimeTypes.addMimeMapping("sh", "text/x-sh");
        mimeTypes.addMimeMapping("exe", "application/octet-stream");

        addLauncherInstallerContextHandler(contextPath, "macos", "macos", ".dmg", installerFiles, mimeTypes);
        addLauncherInstallerContextHandler(contextPath, "linux", "unix", ".sh", installerFiles, mimeTypes);
        addLauncherInstallerContextHandler(contextPath, "windows", "windows", ".exe", installerFiles, mimeTypes);
        addLauncherInstallerContextHandler(contextPath, "windows-x64", "windows-x64", ".exe", installerFiles, mimeTypes);
    }

    private void addLauncherInstallerContextHandler(String contextPath, String os, String fileSuffix, String fileExt, java.util.Collection<File> installers, MimeTypes mimeTypes) {
        File installerFile = null;
        for (File file : installers) {
            if (StringUtils.endsWithIgnoreCase(file.getName(), fileSuffix + fileExt)) {
                installerFile = file;
                break;
            }
        }

        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath(contextPath + "/launcher/" + os + fileExt);
        contextHandler.setAllowNullPathInfo(true);
        contextHandler.setHandler(new InstallerFileHandler(installerFile, mimeTypes));
        handlers.addHandler(contextHandler);
    }

    private class InstallerFileHandler extends AbstractHandler {

        private File file;
        private String contentType;

        public InstallerFileHandler(File file, MimeTypes mimeTypes) {
            this.file = file;

            if (file != null) {
                contentType = mimeTypes.getMimeByExtension(file.getName());
            }
            if (StringUtils.isBlank(contentType)) {
                contentType = ContentType.APPLICATION_OCTET_STREAM.getMimeType();
            }
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            // Only allow GET/HEAD requests, otherwise pass to the next request handler
            if (!baseRequest.getMethod().equalsIgnoreCase(HttpMethod.GET.asString()) && !baseRequest.getMethod().equalsIgnoreCase(HttpMethod.HEAD.asString())) {
                return;
            }

            if (file != null && file.exists()) {
                response.setStatus(HttpStatus.SC_OK);

                if (baseRequest.getMethod().equalsIgnoreCase(HttpMethod.GET.asString())) {
                    FileInputStream fis = null;
                    try {
                        response.setContentType(contentType);
                        response.addHeader("Content-Disposition", "attachment; filename=" + file.getName());

                        OutputStream responseOutputStream = response.getOutputStream();

                        // If the client accepts GZIP compression, compress the content
                        boolean gzip = false;
                        for (Enumeration<String> en = request.getHeaders("Accept-Encoding"); en.hasMoreElements();) {
                            if (StringUtils.contains(en.nextElement(), "gzip")) {
                                response.setHeader(HTTP.CONTENT_ENCODING, "gzip");
                                responseOutputStream = new GZIPOutputStream(responseOutputStream);
                                gzip = true;
                                break;
                            }
                        }

                        if (!gzip) {
                            response.setContentLengthLong(file.length());
                        }

                        fis = new FileInputStream(file);
                        IOUtils.copy(fis, responseOutputStream);

                        // If we gzipped, we need to finish the stream now
                        if (responseOutputStream instanceof GZIPOutputStream) {
                            ((GZIPOutputStream) responseOutputStream).finish();
                        }
                    } catch (Throwable t) {
                        try {
                            response.reset();
                            response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                        } catch (IllegalStateException ise) {
                            logger.debug("Response already committed", ise);
                        }
                    } finally {
                        IOUtils.closeQuietly(fis);
                    }
                }
            } else {
                response.setStatus(HttpStatus.SC_NOT_FOUND);
            }

            baseRequest.setHandled(true);
        }
    }
}
