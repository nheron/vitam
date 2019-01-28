/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.client;

import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.configuration.ClientConfiguration;
import fr.gouv.vitam.common.client.configuration.ClientConfigurationImpl;
import fr.gouv.vitam.common.client.configuration.SSLConfiguration;
import fr.gouv.vitam.common.client.configuration.SecureClientConfiguration;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutorProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.interceptors.encoding.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.encoding.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.encoding.GZIPEncodingInterceptor;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * General VitamClientFactory for non SSL client
 *
 * @param <T> MockOrRestClient class
 */
public abstract class VitamClientFactory<T extends MockOrRestClient> implements VitamClientFactoryInterface<T> {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(VitamClientFactory.class);

    static final AtomicBoolean INIT_STATIC_CONFIG = new AtomicBoolean(false);

    /**
     * Global configuration for Apache: Pooling connection
     */
    static final PoolingHttpClientConnectionManager POOLING_CONNECTION_MANAGER =
        new PoolingHttpClientConnectionManager(VitamConfiguration.getMaxDelayUnusedConnection(), TimeUnit.MILLISECONDS);
    /**
     * Global configuration for Apache: Pooling connection
     */
    static final PoolingHttpClientConnectionManager POOLING_CONNECTION_MANAGER_NOT_CHUNKED =
        new PoolingHttpClientConnectionManager(VitamConfiguration.getMaxDelayUnusedConnection(), TimeUnit.MILLISECONDS);

    /**
     * Global PoolingHttpClientConnectionManager active list
     */
    static final Queue<PoolingHttpClientConnectionManager> allManagers = new ConcurrentLinkedQueue<>();
    /**
     * Global configuration for Apache: Idle Monitor
     */
    static final AtomicBoolean STATIC_IDLE_MONITOR = new AtomicBoolean(false);

    /**
     * Specific Socket Configuration
     */
    static final SocketConfig SOCKETCONFIG = SocketConfig.custom()
        .setRcvBufSize(VitamConfiguration.getRecvBufferSize()).setSndBufSize(VitamConfiguration.getChunkSize())
        .setSoKeepAlive(true).setSoReuseAddress(true).setTcpNoDelay(true)
        .setSoTimeout(VitamConfiguration.getReadTimeout()).build();

    /**
     * Specific Socket Configuration not chunked
     */
    static final SocketConfig SOCKETCONFIG_NONCHUNKED = SocketConfig.custom()
        .setRcvBufSize(VitamConfiguration.getRecvBufferSize()).setSndBufSize(0)
        .setSoKeepAlive(true).setSoReuseAddress(true).setTcpNoDelay(true)
        .setSoTimeout(VitamConfiguration.getReadTimeout()).build();

    static {
        if (INIT_STATIC_CONFIG.compareAndSet(false, true)) {
            setupApachePool(POOLING_CONNECTION_MANAGER);
            POOLING_CONNECTION_MANAGER.setDefaultSocketConfig(SOCKETCONFIG);
            setupApachePool(POOLING_CONNECTION_MANAGER_NOT_CHUNKED);
            POOLING_CONNECTION_MANAGER_NOT_CHUNKED.setDefaultSocketConfig(SOCKETCONFIG_NONCHUNKED);
            allManagers.add(POOLING_CONNECTION_MANAGER);
            allManagers.add(POOLING_CONNECTION_MANAGER_NOT_CHUNKED);
        }
    }


    /**
     * Global configuration for Apache: Idle Monitor
     */
    final ExpiredConnectionMonitorThread idleMonitor;

    private String serviceUrl;

    private String resourcePath;
    private boolean cacheable;
    protected ClientConfiguration clientConfiguration;
    private boolean chunkedMode;
    private Client givenClient;
    private Client givenClientNotChunked;
    private VitamClientType vitamClientType = VitamClientType.MOCK;
    private final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(VitamConfiguration.getDelayGetClient()).build();
    PoolingHttpClientConnectionManager chunkedPoolingManager;
    PoolingHttpClientConnectionManager notChunkedPoolingManager;
    VitamThreadPoolExecutor vitamThreadPoolExecutor = VitamThreadPoolExecutor.getDefaultExecutor();
    SSLConfiguration sslConfiguration = null;
    private boolean useAuthorizationFilter = true;
    private boolean allowGzipEncoded = VitamConfiguration.isAllowGzipEncoding();
    private boolean allowGzipDecoded = VitamConfiguration.isAllowGzipDecoding();

    private final Map<VitamRestEasyConfiguration, Object> config = new EnumMap<>(VitamRestEasyConfiguration.class);
    private final Map<VitamRestEasyConfiguration, Object> configNotChunked =
        new EnumMap<>(VitamRestEasyConfiguration.class);

    /**
     * Constructor with standard configuration
     *
     * @param configuration The client configuration
     * @param resourcePath the resource path of the server for the client calls
     * @throws UnsupportedOperationException HTTPS not implemented yet
     */
    protected VitamClientFactory(ClientConfiguration configuration, String resourcePath) {
        this(configuration, resourcePath, true, false);
    }

    /**
     * Constructor with standard configuration
     *
     * @param configuration The client configuration
     * @param resourcePath the resource path of the server for the client calls
     * @throws UnsupportedOperationException HTTPS not implemented yet
     */
    protected VitamClientFactory(ClientConfiguration configuration, String resourcePath, boolean cacheable) {
        this(configuration, resourcePath, true, cacheable);
    }

    /**
     * Constructor to allow to enable Multipart support or Chunked mode.
     *
     * @param configuration The client configuration
     * @param resourcePath the resource path of the server for the client calls
     * @param chunkedMode one can managed here if the client is in default chunkedMode or not
     * @throws UnsupportedOperationException HTTPS not implemented yet
     */
    protected VitamClientFactory(ClientConfiguration configuration, String resourcePath,
        boolean chunkedMode, boolean cacheable) {

        initialisation(configuration, resourcePath);
        this.chunkedMode = chunkedMode;
        this.cacheable = cacheable;
        givenClient = null;
        givenClientNotChunked = null;
        if (STATIC_IDLE_MONITOR.compareAndSet(false, true)) {
            idleMonitor = new ExpiredConnectionMonitorThread();
            startupMonitor();
        } else {
            idleMonitor = null;
        }
        if (configuration != null) {
            useAuthorizationFilter = !configuration.isSecure();
        }
    }

    /**
     * ONLY use this constructor in unit test. Remove this when JerseyTest will be fully compatible with Jetty
     *
     * @param configuration the client configuration
     * @param resourcePath the resource path of the server for the client calls
     * @param client the HTTP client to use
     * @throws UnsupportedOperationException HTTPS not implemented yet
     */
    protected VitamClientFactory(ClientConfiguration configuration, String resourcePath, Client client) {
        ParametersChecker.checkParameter("Client cannot be null", client);
        initialisation(configuration, resourcePath);
        this.givenClient = client;
        givenClientNotChunked = client;
        idleMonitor = new ExpiredConnectionMonitorThread();
    }

    protected void disableUseAuthorizationFilter() {
        useAuthorizationFilter = false;
    }

    protected void enableUseAuthorizationFilter() {
        useAuthorizationFilter = true;
    }

    boolean useAuthorizationFilter() {
        return useAuthorizationFilter;
    }

    /**
     * Allow or not the GzipEncoded output from client
     *
     * @param allowGzipEncoded
     */
    public void setGzipEncoded(boolean allowGzipEncoded) {
        this.allowGzipEncoded = allowGzipEncoded;
        config.put(VitamRestEasyConfiguration.CONTENTCOMPRESSIONENABLED, allowGzipEncoded);
        configNotChunked.put(VitamRestEasyConfiguration.CONTENTCOMPRESSIONENABLED, allowGzipEncoded);
    }

    /**
     * @return true if client is allowed to gzip encoded
     */
    protected boolean isAllowGzipEncoded() {
        return this.allowGzipEncoded;
    }

    /**
     * Allow or not the GzipDecoded input from server
     *
     * @param allowGzipDecoded
     */
    public void setGzipdecoded(boolean allowGzipDecoded) {
        this.allowGzipDecoded = allowGzipDecoded;
    }

    /**
     * @return true if client is allowed to gzip decoded
     */
    protected boolean isAllowGzipDecoded() {
        return this.allowGzipDecoded;
    }

    /**
     * Initialize default resource path, service Url, pool manager, ssl configuration and the VitamApacheHttpClient for
     * RestEasy
     *
     * @param configuration
     * @param resourcePath
     */
    protected final void initialisation(ClientConfiguration configuration, String resourcePath) {
        if (configuration == null) {
            setVitamClientType(VitamClientType.MOCK);
            clientConfiguration = new ClientConfigurationImpl();
        } else {
            setVitamClientType(VitamClientType.PRODUCTION);
            clientConfiguration = configuration;
            ParametersChecker.checkParameter("Host cannot be null", clientConfiguration.getServerHost());
            ParametersChecker.checkValue("Port has invalid value", clientConfiguration.getServerPort(), 1);
        }
        ParametersChecker.checkParameter("resourcePath cannot be null", resourcePath);
        this.resourcePath = Optional.ofNullable(resourcePath).orElse("/");
        if (this.resourcePath.codePointAt(0) != '/') {
            this.resourcePath = "/" + this.resourcePath;
        }
        serviceUrl = (clientConfiguration.isSecure() ? "https://"
            : "http://") + clientConfiguration.getServerHost() + ":" + clientConfiguration.getServerPort() +
            this.resourcePath;
        if (chunkedPoolingManager != null && chunkedPoolingManager != POOLING_CONNECTION_MANAGER) {
            allManagers.remove(chunkedPoolingManager);
            chunkedPoolingManager.close();
        }
        if (notChunkedPoolingManager != null && notChunkedPoolingManager != POOLING_CONNECTION_MANAGER_NOT_CHUNKED) {
            allManagers.remove(notChunkedPoolingManager);
            notChunkedPoolingManager.close();
        }
        configure(config, true);
        configure(configNotChunked, false);
        if (clientConfiguration.isSecure()) {
            final SecureClientConfiguration sclientConfiguration = (SecureClientConfiguration) clientConfiguration;
            sslConfiguration = sclientConfiguration.getSslConfiguration();
            ParametersChecker.checkParameter("sslConfiguration is a mandatory parameter", sslConfiguration);
            Registry<ConnectionSocketFactory> registry;
            try {
                SSLContext context = sslConfiguration.createSSLContext();
                config.put(VitamRestEasyConfiguration.SSL_CONTEXT, context);
                configNotChunked.put(VitamRestEasyConfiguration.SSL_CONTEXT, context);
                registry = sslConfiguration.getRegistry(context);
            } catch (FileNotFoundException | VitamException e) {
                LOGGER.error(e);
                throw new IllegalArgumentException("SSLConfiguration issue while reading KeyStore or TrustStore", e);
            }
            PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager(registry, null, null, null,
                VitamConfiguration.getMaxDelayUnusedConnection(), TimeUnit.MILLISECONDS);
            setupApachePool(pool);
            pool.setDefaultSocketConfig(SOCKETCONFIG);
            chunkedPoolingManager = pool;
            allManagers.add(chunkedPoolingManager);
            pool = new PoolingHttpClientConnectionManager(registry, null, null, null,
                VitamConfiguration.getMaxDelayUnusedConnection(), TimeUnit.MILLISECONDS);
            setupApachePool(pool);
            pool.setDefaultSocketConfig(SOCKETCONFIG_NONCHUNKED);
            notChunkedPoolingManager = pool;
            allManagers.add(notChunkedPoolingManager);
        } else {
            chunkedPoolingManager = POOLING_CONNECTION_MANAGER;
            notChunkedPoolingManager = POOLING_CONNECTION_MANAGER_NOT_CHUNKED;
        }
        config.put(VitamRestEasyConfiguration.CONNECTION_MANAGER, chunkedPoolingManager);
        configNotChunked.put(VitamRestEasyConfiguration.CONNECTION_MANAGER, notChunkedPoolingManager);
    }

    @Override
    public void changeResourcePath(String resourcePath) {
        initialisation(clientConfiguration, resourcePath);
    }

    @Override
    public void changeServerPort(int port) {
        try {
            ParametersChecker.checkParameter("Host cannot be null", clientConfiguration.getServerHost());
        } catch (final IllegalArgumentException e) {
            LOGGER.debug(e);
            clientConfiguration.setServerHost(TestVitamClientFactory.LOCALHOST);
        }
        this.initialisation(clientConfiguration.setServerPort(port), getResourcePath());
    }


    @Override
    public VitamClientType getVitamClientType() {
        return vitamClientType;
    }

    @Override
    public VitamClientFactory<T> setVitamClientType(VitamClientType vitamClientType) {
        this.vitamClientType = vitamClientType;
        return this;
    }

    /**
     * Specific to handle Junit tests with given Client
     *
     * @param config
     * @return the associated client
     */
    private Client buildClient(Map<VitamRestEasyConfiguration, Object> config) {
        if (givenClient != null) {
            return givenClient;
        }
        VitamApacheHttpClientEngine engine = new VitamApacheHttpClientEngine(config);
        ResteasyClientBuilder builder = configureRestEasy(config, engine);
        return builder.build();
    }

    @Override
    public void resume(Client client, boolean chunk) {
        if (!VitamConfiguration.isUseNewJaxrClient()) {
            return;
        }
        client.close();
    }

    /**
     * @return the client chunked mode default configuration
     */
    boolean getChunkedMode() {
        return chunkedMode;
    }

    @Override
    public Client getHttpClient() {
        return getHttpClient(chunkedMode);
    }

    @Override
    public Client getHttpClient(boolean useChunkedMode) {
        if (useChunkedMode && givenClient != null) {
            return givenClient;
        } else if (!useChunkedMode && givenClientNotChunked != null) {
            return givenClientNotChunked;

        }
        if (useChunkedMode) {
            Client client = buildClient(config);
            if (!VitamConfiguration.isUseNewJaxrClient()) {
                givenClient = client;
            }
            return client;
        } else {
            Client client = buildClient(configNotChunked);
            if (!VitamConfiguration.isUseNewJaxrClient()) {
                givenClientNotChunked = client;

            }
            return client;
        }
    }

    @Override
    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public String getServiceUrl() {
        return serviceUrl;
    }

    @Override
    public String toString() {
        return new StringBuilder("VitamFactory: { ")
            .append("ServiceUrl: ").append(serviceUrl)
            .append(", ResourcePath: ").append(resourcePath)
            .append(", ChunkedMode: ").append(chunkedMode)
            .append(", Configuration: { Properties: \"").append(config)
            .append("\" }")
            .append(" }").toString();
    }

    @Override
    public final ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    @Override
    public final Map<VitamRestEasyConfiguration, Object> getDefaultConfigCient() {
        return Collections.unmodifiableMap(config);
    }

    @Override
    public final Map<VitamRestEasyConfiguration, Object> getDefaultConfigCient(boolean chunkedMode) {
        if (chunkedMode) {
            return Collections.unmodifiableMap(config);
        } else {
            return Collections.unmodifiableMap(configNotChunked);
        }
    }

    /**
     * Shutdown the global Connection Manager (cannot be restarted yet)
     */
    @Override
    public synchronized void shutdown() {
        if (idleMonitor != null && !STATIC_IDLE_MONITOR.get()) {
            idleMonitor.shutdown();
            idleMonitor.interrupt();
        }
        if (chunkedPoolingManager != null && chunkedPoolingManager != POOLING_CONNECTION_MANAGER) {
            allManagers.remove(chunkedPoolingManager);
            chunkedPoolingManager.close();
        }
        if (notChunkedPoolingManager != null && notChunkedPoolingManager != POOLING_CONNECTION_MANAGER_NOT_CHUNKED) {
            allManagers.remove(notChunkedPoolingManager);
            notChunkedPoolingManager.close();
        }
    }

    /**
     * Closes any pending connection.
     */
    @VisibleForTesting
    public static void resetConnections() {
        for (PoolingHttpClientConnectionManager manager : allManagers) {
            manager.closeExpiredConnections();
            manager.closeIdleConnections(0, TimeUnit.MICROSECONDS);
        }
    }

    private static void setupApachePool(PoolingHttpClientConnectionManager manager) {
        manager.setMaxTotal(VitamConfiguration.getMaxTotalClient());
        manager.setDefaultMaxPerRoute(VitamConfiguration.getMaxClientPerHost());
        manager.setValidateAfterInactivity(VitamConfiguration.getDelayValidationAfterInactivity());
    }

    private void startupMonitor() {
        // Apache configuration
        if (idleMonitor != null) {
            idleMonitor.setDaemon(true);
            idleMonitor.start();
        }
    }

    /**
     * @param config
     * @param engine
     * @return the ResteasyClientBuilder
     */
    private ResteasyClientBuilder configureRestEasy(Map<VitamRestEasyConfiguration, Object> config,
        ClientHttpEngine engine) {
        ResteasyClientBuilder clientBuilder = new ResteasyClientBuilder();
        clientBuilder.httpEngine(engine);
        clientBuilder.connectionCheckoutTimeout(
            VitamRestEasyConfiguration.CONNECTIONREQUESTTIMEOUT.getInt(config, 1000),
            TimeUnit.MILLISECONDS);
        clientBuilder.establishConnectionTimeout(VitamRestEasyConfiguration.CONNECT_TIMEOUT.getInt(config, 1000),
            TimeUnit.MILLISECONDS);
        clientBuilder.socketTimeout(VitamRestEasyConfiguration.READ_TIMEOUT.getInt(config, 100000),
            TimeUnit.MILLISECONDS);
        clientBuilder.asyncExecutor(vitamThreadPoolExecutor);
        clientBuilder.register(new VitamThreadPoolExecutorProvider("Vitam"));
        if (isAllowGzipDecoded()) {
            clientBuilder.register(AcceptEncodingGZIPFilter.class);
            clientBuilder.register(GZIPDecodingInterceptor.class);
        }
        if (isAllowGzipEncoded()) {
            clientBuilder.register(GZIPEncodingInterceptor.class);
        }
        // Jackson automatically included
        if (useAuthorizationFilter()) {
            clientBuilder.register(HeaderIdClientFilter.class);
        }
        return clientBuilder;
    }

    /**
     * Configure the configuration MAP according to chunked mode
     *
     * @param config
     * @param chunkedMode
     */
    void configure(Map<VitamRestEasyConfiguration, Object> config, boolean chunkedMode) {
        // Prevent Warning on misusage of non standard Calls
        config.put(VitamRestEasyConfiguration.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        config.put(VitamRestEasyConfiguration.CONNECT_TIMEOUT, VitamConfiguration.getConnectTimeout());
        config.put(VitamRestEasyConfiguration.CONNECTTIMEOUT, VitamConfiguration.getConnectTimeout());
        config.put(VitamRestEasyConfiguration.CONNECTIONREQUESTTIMEOUT, VitamConfiguration.getDelayGetClient());
        config.put(VitamRestEasyConfiguration.READ_TIMEOUT, VitamConfiguration.getReadTimeout());
        config.put(VitamRestEasyConfiguration.SOCKETTIMEOUT, VitamConfiguration.getReadTimeout());
        config.put(VitamRestEasyConfiguration.CONTENTCOMPRESSIONENABLED, allowGzipEncoded);
        config.put(VitamRestEasyConfiguration.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        if (chunkedMode) {
            config.put(VitamRestEasyConfiguration.CHUNKED_ENCODING_SIZE, VitamConfiguration.getChunkSize());
            config.put(VitamRestEasyConfiguration.REQUEST_ENTITY_PROCESSING, VitamRestEasyConfiguration.CHUNKED);
        } else {
            config.put(VitamRestEasyConfiguration.CHUNKED_ENCODING_SIZE, 0);
            config.put(VitamRestEasyConfiguration.REQUEST_ENTITY_PROCESSING, VitamRestEasyConfiguration.BUFFERED);
        }
        config.put(VitamRestEasyConfiguration.CACHE_ENABLED, cacheable);
        config.put(VitamRestEasyConfiguration.RECV_BUFFER_SIZE, VitamConfiguration.getRecvBufferSize());
        config.put(VitamRestEasyConfiguration.CONNECTION_MANAGER_SHARED, true);
        config.put(VitamRestEasyConfiguration.DISABLE_AUTOMATIC_RETRIES, true);
        config.put(VitamRestEasyConfiguration.REQUEST_CONFIG, requestConfig);
    }

    /**
     * @return the VitamThreadPoolExecutor used by the server
     */
    public VitamThreadPoolExecutor getVitamThreadPoolExecutor() {
        return vitamThreadPoolExecutor;
    }

    /**
     * Monitor to check Expired Connection (staled). Idle connections are managed directly in the Pool
     */
    private static class ExpiredConnectionMonitorThread extends Thread {
        volatile boolean shutdown;

        /**
         * Constructor
         */
        ExpiredConnectionMonitorThread() {
            //Empty
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(VitamConfiguration.getIntervalDelayCheckIdle());
                        // Close expired connections
                        for (PoolingHttpClientConnectionManager poolingHttpClientConnectionManager : allManagers) {
                            poolingHttpClientConnectionManager.closeExpiredConnections();
                            /*poolingHttpClientConnectionManager.closeIdleConnections(
                                VitamConfiguration.getDelayValidationAfterInactivity(), TimeUnit.MILLISECONDS);*/
                        }
                    }
                }
            } catch (final InterruptedException ex) {
                SysErrLogger.FAKE_LOGGER.ignoreLog(ex);
                // terminate
            }
        }

        /**
         * Shutdown this Daemon
         */
        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
