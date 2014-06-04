/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.client.impl;

import org.apache.commons.io.*;
import org.gridgain.client.*;
import org.gridgain.client.balancer.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.junits.common.*;
import org.springframework.context.support.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static org.gridgain.client.GridClientConfiguration.*;

/**
 * Properties-based configuration self test.
 */
public class GridClientPropertiesConfigurationSelfTest extends GridCommonAbstractTest {
    /**
     * Grid client spring configuration.
     */
    private static final URL GRID_CLIENT_SPRING_CONFIG;

    /**
     * Grid client properties-based configuration.
     */
    private static final URL GRID_CLIENT_CONFIG;

    /**
     *
     */
    static {
        GRID_CLIENT_SPRING_CONFIG =
            U.resolveGridGainUrl("/modules/clients/src/main/java/config/grid-client-spring-config.xml");

        GRID_CLIENT_CONFIG = U.resolveGridGainUrl("/modules/clients/src/main/java/config/grid-client-config.properties");
    }

    /**
     * Test client configuration loaded from the properties.
     *
     * @throws Exception In case of exception.
     */
    public void testCreation() throws Exception {
        // Validate default configuration.
        GridClientConfiguration cfg = new GridClientConfiguration();

        cfg.setServers(Arrays.asList("localhost:11211"));

        validateConfig(0, cfg);

        // Validate default properties-based configuration.
        cfg = new GridClientConfiguration();

        cfg.setServers(Arrays.asList("localhost:11211"));

        validateConfig(0, cfg);

        // Validate loaded configuration.
        Properties props = loadProperties(1, GRID_CLIENT_CONFIG);
        validateConfig(0, new GridClientConfiguration(props));

        // Validate loaded configuration with changed key prefixes.
        Properties props2 = new Properties();

        for (Map.Entry<Object, Object> e : props.entrySet())
            props2.put("new." + e.getKey(), e.getValue());

        validateConfig(0, new GridClientConfiguration("new.gg.client", props2));
        validateConfig(0, new GridClientConfiguration("new.gg.client.", props2));

        // Validate loaded test configuration.
        File tmp = uncommentProperties(GRID_CLIENT_CONFIG);

        props = loadProperties(25, tmp.toURI().toURL());
        validateConfig(2, new GridClientConfiguration(props));

        // Validate loaded test configuration with changed key prefixes.
        props2 = new Properties();

        for (Map.Entry<Object, Object> e : props.entrySet())
            props2.put("new." + e.getKey(), e.getValue());

        validateConfig(2, new GridClientConfiguration("new.gg.client", props2));
        validateConfig(2, new GridClientConfiguration("new.gg.client.", props2));

        // Validate loaded test configuration with empty key prefixes.
        props2 = new Properties();

        for (Map.Entry<Object, Object> e : props.entrySet())
            props2.put(e.getKey().toString().replace("gg.client.", ""), e.getValue());

        validateConfig(2, new GridClientConfiguration("", props2));
        validateConfig(2, new GridClientConfiguration(".", props2));
    }

    /**
     * Validate spring client configuration.
     *
     * @throws Exception In case of any exception.
     */
    public void testSpringConfig() throws Exception {
        GridClientConfiguration cfg = new FileSystemXmlApplicationContext(
            GRID_CLIENT_SPRING_CONFIG.toString()).getBean(GridClientConfiguration.class);

        assertEquals(Arrays.asList("127.0.0.1:11211"), new ArrayList<>(cfg.getServers()));
        assertNull(cfg.getSecurityCredentialsProvider());

        Collection<GridClientDataConfiguration> dataCfgs = cfg.getDataConfigurations();

        assertEquals(1, dataCfgs.size());

        GridClientDataConfiguration dataCfg = dataCfgs.iterator().next();

        assertEquals("partitioned", dataCfg.getName());

        assertNotNull(dataCfg.getPinnedBalancer());
        assertEquals(GridClientRandomBalancer.class, dataCfg.getPinnedBalancer().getClass());

        assertNotNull(dataCfg.getAffinity());
        assertEquals(GridClientPartitionAffinity.class, dataCfg.getAffinity().getClass());
    }

    /**
     * Uncomment properties.
     *
     * @param url Source to uncomment client properties for.
     * @return Temporary file with uncommented client properties.
     * @throws IOException In case of IO exception.
     */
    private File uncommentProperties(URL url) throws IOException {
        InputStream in = url.openStream();

        assertNotNull(in);

        LineIterator it = IOUtils.lineIterator(in, "UTF-8");
        Collection<String> lines = new ArrayList<>();

        while (it.hasNext())
            lines.add(it.nextLine().replace("#gg.client.", "gg.client."));

        GridUtils.closeQuiet(in);

        File tmp = File.createTempFile(UUID.randomUUID().toString(), "properties");

        tmp.deleteOnExit();

        FileUtils.writeLines(tmp, lines);

        return tmp;
    }

    /**
     * Load properties from the url.
     *
     * @param expLoaded Expected number of loaded properties.
     * @param url URL to load properties from.
     * @return Loaded properties.
     * @throws IOException In case of IO exception.
     */
    private Properties loadProperties(int expLoaded, URL url) throws IOException {
        InputStream in = url.openStream();

        Properties props = new Properties();

        assertEquals(0, props.size());

        props.load(in);

        assertEquals(expLoaded, props.size());

        GridUtils.closeQuiet(in);

        return props;
    }

    /**
     * Validate loaded configuration.
     *
     * @param expDataCfgs Expected data configurations count.
     * @param cfg Client configuration to validate.
     */
    private void validateConfig(int expDataCfgs, GridClientConfiguration cfg) {
        assertEquals(GridClientRandomBalancer.class, cfg.getBalancer().getClass());
        assertEquals(10000, cfg.getConnectTimeout());
        assertEquals(null, cfg.getSecurityCredentialsProvider());

        assertEquals(expDataCfgs, cfg.getDataConfigurations().size());

        if (expDataCfgs == 2) {
            GridClientDataConfiguration nullCfg = cfg.getDataConfiguration(null);

            assertEquals(null, nullCfg.getName());
            assertEquals(null, nullCfg.getAffinity());
            assertEquals(GridClientRandomBalancer.class, nullCfg.getPinnedBalancer().getClass());

            GridClientDataConfiguration partCfg = cfg.getDataConfiguration("partitioned");

            assertEquals("partitioned", partCfg.getName());
            assertEquals(GridClientPartitionAffinity.class, partCfg.getAffinity().getClass());
            assertEquals(GridClientRoundRobinBalancer.class, partCfg.getPinnedBalancer().getClass());
        }

        assertEquals(DFLT_MAX_CONN_IDLE_TIME, cfg.getMaxConnectionIdleTime());
        assertEquals(GridClientProtocol.TCP, cfg.getProtocol());
        assertEquals(Arrays.asList("localhost:11211"), new ArrayList<>(cfg.getServers()));
        assertEquals(true, cfg.isEnableAttributesCache());
        assertEquals(true, cfg.isEnableMetricsCache());
        assertEquals(true, cfg.isTcpNoDelay());
        assertEquals(null, cfg.getSslContextFactory(), null);
        assertEquals(DFLT_TOP_REFRESH_FREQ, cfg.getTopologyRefreshFrequency());
    }
}
