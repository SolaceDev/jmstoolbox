/*
 * Copyright (C) 2015-2016 Denis Forveille titou10.titou10@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.titou10.jtb.qm.activemq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.titou10.jtb.config.gen.SessionDef;
import org.titou10.jtb.jms.qm.ConnectionData;
import org.titou10.jtb.jms.qm.JMSPropertyKind;
import org.titou10.jtb.jms.qm.QManager;
import org.titou10.jtb.jms.qm.QManagerProperty;

/**
 * 
 * Implements Apache ActiveMQ (embedded in TomEE and Geronimo) Q Provider
 * 
 * @author Denis Forveille
 *
 */
public class ActiveMQQManager extends QManager {

   private static final Logger                       log                    = LoggerFactory.getLogger(ActiveMQQManager.class);

   private static final String                       JMX_URL_TEMPLATE       = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";

   private static final String                       JMX_QUEUES             = "org.apache.activemq:type=Broker,destinationType=Queue,*";
   private static final String                       JMX_TOPICS             = "org.apache.activemq:type=Broker,destinationType=Topic,*";

   private static final String                       JMX_QUEUE              = "org.apache.activemq:type=Broker,destinationType=Queue,destinationName=%s,*";
   private static final String                       JMX_TOPIC              = "org.apache.activemq:type=Broker,destinationType=Topic,destinationName=%s,*";

   private static final String                       SYSTEM_PREFIX          = "ActiveMQ.";

   private static final String                       CR                     = "\n";

   private static final String                       P_BROKER_URL           = "brokerURL";
   private static final String                       P_KEY_STORE            = "javax.net.ssl.keyStore";
   private static final String                       P_KEY_STORE_PASSWORD   = "javax.net.ssl.keyStorePassword";
   private static final String                       P_TRUST_STORE          = "javax.net.ssl.trustStore";
   private static final String                       P_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
   private static final String                       P_TRUST_ALL_PACKAGES   = "trustAllPackages";

   private List<QManagerProperty>                    parameters             = new ArrayList<QManagerProperty>();

   private static final String                       HELP_TEXT;

   private final Map<Integer, JMXConnector>          jmxcs                  = new HashMap<>();
   private final Map<Integer, MBeanServerConnection> mbscs                  = new HashMap<>();

   // ------------------------
   // Constructor
   // ------------------------

   public ActiveMQQManager() {
      log.debug("Apache Active MQ");

      parameters.add(new QManagerProperty(P_BROKER_URL,
                                          true,
                                          JMSPropertyKind.STRING,
                                          false,
                                          "broker url (eg 'tcp://localhost:61616','ssl://localhost:61616' ...)",
                                          "tcp://localhost:61616"));
      parameters.add(new QManagerProperty(P_KEY_STORE, false, JMSPropertyKind.STRING));
      parameters.add(new QManagerProperty(P_KEY_STORE_PASSWORD, false, JMSPropertyKind.STRING, true));
      parameters.add(new QManagerProperty(P_TRUST_STORE, false, JMSPropertyKind.STRING));
      parameters.add(new QManagerProperty(P_TRUST_STORE_PASSWORD, false, JMSPropertyKind.STRING, true));
      parameters.add(new QManagerProperty(P_TRUST_ALL_PACKAGES, false, JMSPropertyKind.BOOLEAN));
   }

   @Override
   public ConnectionData connect(SessionDef sessionDef, boolean showSystemObjects) throws Exception {

      /* <managementContext> <managementContext createConnector="true"/> </managementContext> */

      // Save System properties
      saveSystemProperties();
      try {

         // Extract properties
         Map<String, String> mapProperties = extractProperties(sessionDef);

         String brokerURL = mapProperties.get(P_BROKER_URL);
         // String icf = mapProperties.get(P_ICF);
         String keyStore = mapProperties.get(P_KEY_STORE);
         String keyStorePassword = mapProperties.get(P_KEY_STORE_PASSWORD);
         String trustStore = mapProperties.get(P_TRUST_STORE);
         String trustStorePassword = mapProperties.get(P_TRUST_STORE_PASSWORD);
         String trustAllPackages = mapProperties.get(P_TRUST_ALL_PACKAGES);

         if (keyStore == null) {
            System.clearProperty(P_KEY_STORE);
         } else {
            System.setProperty(P_KEY_STORE, keyStore);
         }
         if (keyStorePassword == null) {
            System.clearProperty(P_KEY_STORE_PASSWORD);
         } else {
            System.setProperty(P_KEY_STORE_PASSWORD, keyStorePassword);
         }
         if (trustStore == null) {
            System.clearProperty(P_TRUST_STORE);
         } else {
            System.setProperty(P_TRUST_STORE, trustStore);
         }
         if (trustStorePassword == null) {
            System.clearProperty(P_TRUST_STORE_PASSWORD);
         } else {
            System.setProperty(P_TRUST_STORE_PASSWORD, trustStorePassword);
         }

         JMXServiceURL url = new JMXServiceURL(String.format(JMX_URL_TEMPLATE, sessionDef.getHost(), sessionDef.getPort()));
         log.debug("JMX URL : {}", url);

         Map<String, String[]> jmxEnv = Collections.singletonMap(JMXConnector.CREDENTIALS,
                                                                 new String[] { sessionDef.getUserid(), sessionDef.getPassword() });

         JMXConnector jmxc = JMXConnectorFactory.connect(url, jmxEnv);
         MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
         log.debug(mbsc.toString());

         // Discover queues and topics

         SortedSet<String> queueNames = new TreeSet<>();
         SortedSet<String> topicNames = new TreeSet<>();

         // ObjectName activeMQ1 = new ObjectName(String.format(JMX_QUEUES, brokerName));
         ObjectName activeMQ1 = new ObjectName(JMX_QUEUES);
         Set<ObjectName> a = mbsc.queryNames(activeMQ1, null);
         for (ObjectName objectName : a) {
            String dName = objectName.getKeyProperty("destinationName");
            log.debug("queue={}", dName);
            if (showSystemObjects) {
               queueNames.add(dName);
            } else {
               if (!dName.startsWith(SYSTEM_PREFIX)) {
                  queueNames.add(dName);
               }
            }
         }
         // ObjectName activeMQ2 = new ObjectName(String.format(JMX_TOPICS, brokerName));
         ObjectName activeMQ2 = new ObjectName(JMX_TOPICS);
         Set<ObjectName> b = mbsc.queryNames(activeMQ2, null);
         for (ObjectName objectName : b) {
            String dName = objectName.getKeyProperty("destinationName");
            log.debug("topic={}", dName);
            if (showSystemObjects) {
               topicNames.add(dName);
            } else {
               if (!dName.startsWith(SYSTEM_PREFIX)) {
                  topicNames.add(dName);
               }
            }
         }

         // -------------------

         // tcp://localhost:61616"
         // "org.apache.activemq.jndi.ActiveMQInitialContextFactory"

         log.debug("connecting to {}", brokerURL);

         ActiveMQConnectionFactory cf2 = new ActiveMQConnectionFactory(sessionDef.getUserid(), sessionDef.getPassword(), brokerURL);
         cf2.setTransactedIndividualAck(true); // Without this, browsing messages spends 15s+ on the last element
         if (trustAllPackages != null) {
            if (Boolean.valueOf(trustAllPackages)) {
               cf2.setTrustAllPackages(true);
            }
         }

         // Create JMS Connection
         Connection jmsConnection = cf2.createConnection(sessionDef.getUserid(), sessionDef.getPassword());
         log.info("connected to {}", sessionDef.getName());

         log.debug("Discovered {} queues and {} topics", queueNames.size(), topicNames.size());

         // Store per connection related data
         Integer hash = jmsConnection.hashCode();
         jmxcs.put(hash, jmxc);
         mbscs.put(hash, mbsc);

         return new ConnectionData(jmsConnection, queueNames, topicNames);
      } finally {
         restoreSystemProperties();
      }
   }

   @Override
   public void close(Connection jmsConnection) throws JMSException {
      log.debug("close connection");

      Integer hash = jmsConnection.hashCode();
      JMXConnector jmxc = jmxcs.get(hash);

      try {
         jmsConnection.close();
      } catch (Exception e) {
         log.warn("Exception occured while closing session. Ignore it. Msg={}", e.getMessage());
      }
      if (jmxc != null) {
         try {
            jmxc.close();
         } catch (IOException e) {
            log.warn("Exception occured while closing JMXConnector. Ignore it. Msg={}", e.getMessage());
         }
         jmxcs.remove(hash);
         mbscs.remove(hash);
      }
   }

   @Override
   public Integer getQueueDepth(Connection jmsConnection, String queueName) {

      Integer hash = jmsConnection.hashCode();
      MBeanServerConnection mbsc = mbscs.get(hash);

      Integer depth = null;
      try {
         ObjectName on = new ObjectName(String.format(JMX_QUEUE, queueName));
         Set<ObjectName> attributesSet = mbsc.queryNames(on, null);
         if ((attributesSet != null) && (!attributesSet.isEmpty())) {
            // TODO Long -> Integer !
            depth = ((Long) mbsc.getAttribute(attributesSet.iterator().next(), "QueueSize")).intValue();
         }
      } catch (Exception e) {
         log.error("Exception when reading queue depth. Ignoring", e);
      }
      return depth;
   }

   @Override
   public Map<String, Object> getQueueInformation(Connection jmsConnection, String queueName) {

      Integer hash = jmsConnection.hashCode();
      MBeanServerConnection mbsc = mbscs.get(hash);

      Map<String, Object> properties = new LinkedHashMap<>();

      try {
         ObjectName on = new ObjectName(String.format(JMX_QUEUE, queueName));
         Set<ObjectName> attributesSet = mbsc.queryNames(on, null);

         if ((attributesSet != null) && (!attributesSet.isEmpty())) {
            addInfo(mbsc, properties, attributesSet, "QueueSize");
            addInfo(mbsc, properties, attributesSet, "Paused");
            addInfo(mbsc, properties, attributesSet, "DLQ");

            addInfo(mbsc, properties, attributesSet, "CacheEnabled");
            addInfo(mbsc, properties, attributesSet, "UseCache");
            addInfo(mbsc, properties, attributesSet, "CursorMemoryUsage");
            addInfo(mbsc, properties, attributesSet, "CursorPercentUsage");
            addInfo(mbsc, properties, attributesSet, "CursorFull");
            addInfo(mbsc, properties, attributesSet, "MessageGroupType");
            addInfo(mbsc, properties, attributesSet, "MessageGroups");
            addInfo(mbsc, properties, attributesSet, "MemoryPercentUsage");
            addInfo(mbsc, properties, attributesSet, "MemoryUsagePortion");
            addInfo(mbsc, properties, attributesSet, "MemoryUsageByteCount");
            addInfo(mbsc, properties, attributesSet, "MemoryLimit");
            addInfo(mbsc, properties, attributesSet, "Options");
            addInfo(mbsc, properties, attributesSet, "SlowConsumerStrategy");
            addInfo(mbsc, properties, attributesSet, "ProducerFlowControl");
            addInfo(mbsc, properties, attributesSet, "AlwaysRetroactive");
            addInfo(mbsc, properties, attributesSet, "MaxProducersToAudit");
            addInfo(mbsc, properties, attributesSet, "PrioritizedMessages");
            addInfo(mbsc, properties, attributesSet, "MaxAuditDepth");
            addInfo(mbsc, properties, attributesSet, "AverageMessageSize");
            addInfo(mbsc, properties, attributesSet, "MaxMessageSize");
            addInfo(mbsc, properties, attributesSet, "MinMessageSize");
            addInfo(mbsc, properties, attributesSet, "MaxPageSize");
            addInfo(mbsc, properties, attributesSet, "BlockedProducerWarningInterval");
            addInfo(mbsc, properties, attributesSet, "BlockedSends");
            addInfo(mbsc, properties, attributesSet, "StoreMessageSize");
            addInfo(mbsc, properties, attributesSet, "ProducerCount");
            addInfo(mbsc, properties, attributesSet, "ConsumerCount");
            addInfo(mbsc, properties, attributesSet, "EnqueueCount");
            addInfo(mbsc, properties, attributesSet, "DequeueCount");
            addInfo(mbsc, properties, attributesSet, "ForwardCount");
            addInfo(mbsc, properties, attributesSet, "DispatchCount");
            addInfo(mbsc, properties, attributesSet, "InFlightCount");
            addInfo(mbsc, properties, attributesSet, "ExpiredCount");
            addInfo(mbsc, properties, attributesSet, "AverageEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "MaxEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "MinEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "AverageBlockedTime");
            // addInfo(properties, attributesSet, "Subscriptions");
         }
      } catch (Exception e) {
         log.error("Exception when reading Queue Information. Ignoring", e);
      }

      return properties;
   }

   @Override
   public Map<String, Object> getTopicInformation(Connection jmsConnection, String topicName) {

      Integer hash = jmsConnection.hashCode();
      MBeanServerConnection mbsc = mbscs.get(hash);

      Map<String, Object> properties = new LinkedHashMap<>();

      try {
         ObjectName on = new ObjectName(String.format(JMX_TOPIC, topicName));
         Set<ObjectName> attributesSet = mbsc.queryNames(on, null);

         // Display all attributes
         MBeanInfo info = mbsc.getMBeanInfo(attributesSet.iterator().next());
         MBeanAttributeInfo[] attrInfo = info.getAttributes();
         for (MBeanAttributeInfo attr : attrInfo) {
            System.out.println(" " + attr.getName() + "\n");
         }

         if ((attributesSet != null) && (!attributesSet.isEmpty())) {
            addInfo(mbsc, properties, attributesSet, "QueueSize");
            addInfo(mbsc, properties, attributesSet, "DLQ");
            addInfo(mbsc, properties, attributesSet, "UseCache");

            addInfo(mbsc, properties, attributesSet, "ProducerCount");
            addInfo(mbsc, properties, attributesSet, "ConsumerCount");
            addInfo(mbsc, properties, attributesSet, "EnqueueCount");
            addInfo(mbsc, properties, attributesSet, "DequeueCount");
            addInfo(mbsc, properties, attributesSet, "ForwardCount");
            addInfo(mbsc, properties, attributesSet, "MemoryPercentUsage");
            addInfo(mbsc, properties, attributesSet, "MemoryUsagePortion");
            addInfo(mbsc, properties, attributesSet, "Options");
            addInfo(mbsc, properties, attributesSet, "MemoryLimit");
            addInfo(mbsc, properties, attributesSet, "MemoryUsageByteCount");
            addInfo(mbsc, properties, attributesSet, "SlowConsumerStrategy");
            addInfo(mbsc, properties, attributesSet, "ProducerFlowControl");
            addInfo(mbsc, properties, attributesSet, "AlwaysRetroactive");
            addInfo(mbsc, properties, attributesSet, "MaxProducersToAudit");
            addInfo(mbsc, properties, attributesSet, "PrioritizedMessages");
            addInfo(mbsc, properties, attributesSet, "AverageMessageSize");
            addInfo(mbsc, properties, attributesSet, "MaxMessageSize");
            addInfo(mbsc, properties, attributesSet, "MinMessageSize");
            addInfo(mbsc, properties, attributesSet, "MaxAuditDepth");
            addInfo(mbsc, properties, attributesSet, "MaxPageSize");
            addInfo(mbsc, properties, attributesSet, "BlockedProducerWarningInterval");
            addInfo(mbsc, properties, attributesSet, "BlockedSends");
            addInfo(mbsc, properties, attributesSet, "StoreMessageSize");
            addInfo(mbsc, properties, attributesSet, "AverageEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "MaxEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "MinEnqueueTime");
            addInfo(mbsc, properties, attributesSet, "AverageBlockedTime");
            addInfo(mbsc, properties, attributesSet, "TotalBlockedTime");
            addInfo(mbsc, properties, attributesSet, "DispatchCount");
            addInfo(mbsc, properties, attributesSet, "InFlightCount");
            addInfo(mbsc, properties, attributesSet, "ExpiredCount");
            // addInfo(properties, attributesSet, "Subscriptions");
         }
      } catch (Exception e) {
         log.error("Exception when reading Queue Information. Ignoring", e);
      }

      return properties;
   }

   private void addInfo(MBeanServerConnection mbsc,
                        Map<String, Object> properties,
                        Set<ObjectName> attributesSet,
                        String propertyName) {

      try {
         properties.put(propertyName, mbsc.getAttribute(attributesSet.iterator().next(), propertyName));
      } catch (Exception e) {
         log.warn("Exception when reading " + propertyName + " Ignoring. " + e.getMessage());
      }
   }

   @Override
   public String getHelpText() {
      return HELP_TEXT;

   }

   static {
      StringBuilder sb = new StringBuilder(2048);
      sb.append("Extra JARS :").append(CR);
      sb.append("------------").append(CR);
      sb.append("No extra jar is needed as JMSToolBox is bundled with Apache ActiveMQ v5.13.2 jars").append(CR);
      sb.append(CR);
      sb.append("Requirements").append(CR);
      sb.append("------------").append(CR);
      sb.append("JMX must be activated on the broker:").append(CR);
      sb.append("--> http://activemq.apache.org/jmx.html").append(CR);
      sb.append(CR);
      sb.append("Connection:").append(CR);
      sb.append("-----------").append(CR);
      sb.append("Host          : Apache ActiveMQ broker server name for JMX Connection (eg localhost)").append(CR);
      sb.append("Port          : Apache ActiveMQ broker port for JMX Connection (eg. 1099)").append(CR);
      sb.append("User/Password : User allowed to connect to Apache ActiveMQ").append(CR);
      sb.append(CR);
      sb.append("Properties:").append(CR);
      sb.append("-----------").append(CR);
      // sb.append("- initialContextFactory : org.apache.activemq.jndi.ActiveMQInitialContextFactory").append(CR);
      sb.append("- brokerURL             : broker url. Examples:").append(CR);
      sb.append("                          tcp://localhost:61616").append(CR);
      sb.append("                          https://localhost:8443").append(CR);
      sb.append("                          ssl://localhost:61616").append(CR);
      sb.append("                          ssl://localhost:61616?socket.enabledCipherSuites=SSL_RSA_WITH_RC4_128_SHA,SSL_DH_anon_WITH_3DES_EDE_CBC_SHA");
      sb.append(CR);
      sb.append("- trustAllPackages                 : If true, allows to display ObjectMessage payload (Needs some config on the server also)");
      sb.append(CR);
      sb.append("- javax.net.ssl.trustStore         : trust store (eg D:/somewhere/trust.jks)").append(CR);
      sb.append("- javax.net.ssl.trustStorePassword : trust store password").append(CR);
      sb.append(CR);
      sb.append("If the \"transportConnector\" on the server is configured with \"transport.needClientAuth=true\":").append(CR);
      sb.append("- javax.net.ssl.keyStore           : key store (eg D:/somewhere/key.jks)").append(CR);
      sb.append("- javax.net.ssl.keyStorePassword   : key store password").append(CR);

      HELP_TEXT = sb.toString();
   }

   // ------------------------
   // Standard Getters/Setters
   // ------------------------

   @Override
   public List<QManagerProperty> getQManagerProperties() {
      return parameters;
   }

}
