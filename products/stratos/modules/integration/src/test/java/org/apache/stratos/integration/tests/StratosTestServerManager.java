/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.integration.tests;

import org.apache.activemq.broker.BrokerService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.stratos.common.test.TestLogAppender;
import org.apache.stratos.integration.tests.application.SampleApplicationsTest;
import org.apache.stratos.integration.tests.rest.IntegrationMockClient;
import org.apache.stratos.integration.tests.rest.RestClient;
import org.apache.stratos.mock.iaas.client.MockIaasApiClient;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.wso2.carbon.integration.framework.TestServerManager;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.integration.framework.utils.ServerUtils;
import org.wso2.carbon.integration.framework.utils.TestUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.testng.Assert.assertNotNull;

/**
 * Prepare activemq, Stratos server for tests, enables mock iaas, starts servers and stop them after the tests.
 */
public class StratosTestServerManager extends TestServerManager {

    private static final Log log = LogFactory.getLog(StratosTestServerManager.class);

    private final static String CARBON_ZIP = SampleApplicationsTest.class.getResource("/").getPath() +
            "/../../../distribution/target/apache-stratos-4.1.2-SNAPSHOT.zip";
    private final static int PORT_OFFSET = 0;
    private static final String ACTIVEMQ_BIND_ADDRESS = "tcp://localhost:61617";
    private static final String MOCK_IAAS_XML_FILE = "mock-iaas.xml";
    private static final String JNDI_PROPERTIES_FILE = "jndi.properties";
    private static final String JMS_OUTPUT_ADAPTER_FILE = "JMSOutputAdaptor.xml";
    protected RestClient restClient;
    private String endpoint = "http://localhost:9763";

    private BrokerService broker = new BrokerService();
    private TestLogAppender testLogAppender = new TestLogAppender();
    private ServerUtils serverUtils;
    private String carbonHome;
    protected IntegrationMockClient mockIaasApiClient;

    public StratosTestServerManager() {
        super(CARBON_ZIP, PORT_OFFSET);
        serverUtils = new ServerUtils();
        restClient = new RestClient(endpoint, "admin", "admin");
        mockIaasApiClient = new IntegrationMockClient(endpoint + "/mock-iaas/api");
    }

    @Override
    @BeforeSuite(timeOut = 600000)
    public String startServer() throws IOException {
        Logger.getRootLogger().addAppender(testLogAppender);
        Logger.getRootLogger().setLevel(Level.INFO);

        try {
            // Start ActiveMQ
            long time1 = System.currentTimeMillis();
            log.info("Starting ActiveMQ...");
            broker.setDataDirectory(StratosTestServerManager.class.getResource("/").getPath() +
                    File.separator + ".." + File.separator + "activemq-data");
            broker.setBrokerName("testBroker");
            broker.addConnector(ACTIVEMQ_BIND_ADDRESS);
            broker.start();
            long time2 = System.currentTimeMillis();
            log.info(String.format("ActiveMQ started in %d sec", (time2 - time1) / 1000));
        }
        catch (Exception e) {
            throw new RuntimeException("Could not start ActiveMQ", e);
        }

        try {
            log.info("Setting up Stratos server...");
            long time3 = System.currentTimeMillis();
            String carbonZip = getCarbonZip();
            if (carbonZip == null) {
                carbonZip = System.getProperty("carbon.zip");
            }

            if (carbonZip == null) {
                throw new IllegalArgumentException("carbon zip file is null");
            } else {
                carbonHome = this.serverUtils.setUpCarbonHome(carbonZip);
                TestUtil.copySecurityVerificationService(carbonHome);
                this.copyArtifacts(carbonHome);
                log.info("Stratos server setup completed");

                log.info("Starting Stratos server...");
                this.serverUtils.startServerUsingCarbonHome(carbonHome, carbonHome, "stratos", PORT_OFFSET, null);
                FrameworkSettings.init();

                while (!serverStarted()) {
                    log.info("Waiting for topology to be initialized...");
                    Thread.sleep(5000);
                }

                while (!mockServiceStarted()) {
                    log.info("Waiting for mock service to be initialized...");
                    Thread.sleep(1000);
                }

                long time4 = System.currentTimeMillis();
                log.info(String.format("Stratos server started in %d sec", (time4 - time3) / 1000));
                return carbonHome;
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Could not start Stratos server", e);
        }
    }

    private boolean mockServiceStarted() {
        for (String message : testLogAppender.getMessages()) {
            if (message.contains("Mock IaaS service component activated")) {
                return true;
            }
        }
        return false;
    }

    @Override
    @AfterSuite(timeOut = 600000)
    public void stopServer() throws Exception {
        super.stopServer();
       /*
       while (!serverStopped()) {
            log.info("Waiting for server to be shutdown...");
            Thread.sleep(1000);
        }
        log.info("Stopped Apache Stratos server.");
        */
        broker.stop();
        log.info("Stopped ActiveMQ server.");
    }

    protected void copyArtifacts(String carbonHome) throws IOException {
        copyConfigFile(carbonHome, MOCK_IAAS_XML_FILE);
        copyConfigFile(carbonHome, JNDI_PROPERTIES_FILE);
        copyConfigFile(carbonHome, JMS_OUTPUT_ADAPTER_FILE, "repository/deployment/server/outputeventadaptors");
    }

    private void copyConfigFile(String carbonHome, String sourceFilePath) throws IOException {
        copyConfigFile(carbonHome, sourceFilePath, "repository/conf");
    }

    private void copyConfigFile(String carbonHome, String sourceFilePath, String destinationFolder) throws IOException {
        log.info("Copying file: " + sourceFilePath);
        URL fileURL = getClass().getResource("/" + sourceFilePath);
        assertNotNull(fileURL);
        File srcFile = new File(fileURL.getFile());
        File destFile = new File(carbonHome + "/" + destinationFolder + "/" + sourceFilePath);
        FileUtils.copyFile(srcFile, destFile);
        log.info(sourceFilePath + " file copied");
    }

    private boolean serverStopped() {
        for (String message : testLogAppender.getMessages()) {
            if (message.contains("Halting JVM")) {
                return true;
            }
        }
        return false;
    }


    private boolean serverStarted() {
        for (String message : testLogAppender.getMessages()) {
            if (message.contains("Topology initialized")) {
                return true;
            }
        }
        return false;
    }
}
