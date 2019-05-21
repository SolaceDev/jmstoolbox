/*
 * Copyright (C) 2015 Denis Forveille titou10.titou10@gmail.com
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
package org.titou10.jtb.qm.solace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.LoggerFactory;
import org.titou10.jtb.config.gen.SessionDef;
import org.titou10.jtb.jms.qm.DestinationData;
import org.titou10.jtb.jms.qm.JMSPropertyKind;
import org.titou10.jtb.jms.qm.QManager;
import org.titou10.jtb.jms.qm.QManagerProperty;
import org.titou10.jtb.jms.qm.QueueData;
import org.titou10.jtb.jms.qm.TopicData;

import com.solacesystems.jms.SupportedProperty;

/**
 * 
 * Implements Solace
 * 
 * @author Denis Forveille
 *
 */
public class SolaceQManager extends QManager {

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SolaceQManager.class);

	private static final String SOLJMS_INITIAL_CONTEXT_FACTORY = "com.solacesystems.jndi.SolJNDIInitialContextFactory";
//	private static final String BROKER_URL = "brokerURL";
	private static final String MESSAGE_VPN = "msgVpn";
	private static final String CF_JNDI_NAME = "cfJndiName";
	private static final String QUEUE_JNDI_NAMES = "queueJndiName";
	private static final String TOPIC_JNDI_NAMES = "topicJndiName";

	private final Map<Integer, SessionInfo> sessionJMSs = new HashMap<>();
	private final Map<Integer, InitialContext> jndiContexts = new HashMap<>();

	private List<QManagerProperty> parameters = new ArrayList<QManagerProperty>();

	public SolaceQManager() {
		log.debug("Instantiate Solace");
//		parameters.add(new QManagerProperty(BROKER_URL, true, JMSPropertyKind.STRING, false,
//				"broker url (eg 'tcp://localhost:55555','tcps://localhost:55443' ...)", "tcp://localhost:55555"));
		parameters.add(
				new QManagerProperty(MESSAGE_VPN, true, JMSPropertyKind.STRING, false, "message vpn name", "default"));
		parameters.add(new QManagerProperty(CF_JNDI_NAME, true, JMSPropertyKind.STRING, false,
				"connection factory JNDI name", "/jms/cf/default"));
		parameters.add(new QManagerProperty(QUEUE_JNDI_NAMES, true, JMSPropertyKind.STRING, false,
				"comma separated queue JNDI name", "/jms/queue/default"));
		parameters.add(new QManagerProperty(TOPIC_JNDI_NAMES, true, JMSPropertyKind.STRING, false,
				"comma separated topic JNDI name", "/jms/topic/default"));
	}

	@Override
	public Connection connect(SessionDef sessionDef, boolean showSystemObjects, String clientID) throws Exception {

		log.info("connecting to {} - {}", sessionDef.getName(), clientID);

		// Save System properties
		saveSystemProperties();

		try {
			// Extract properties
			Map<String, String> mapProperties = extractProperties(sessionDef);
			String brokerURL = sessionDef.getHost() + ":" + sessionDef.getPort();
			String username = sessionDef.getActiveUserid();
			if (username == null) {
				username = "default";
			}
			String password = sessionDef.getActivePassword();
			if (password == null) {
				password = "";
			}
			String msgVpn = mapProperties.get(MESSAGE_VPN);
			String cfJndiName = mapProperties.get(CF_JNDI_NAME);
			String queueJndiNames = mapProperties.get(QUEUE_JNDI_NAMES);
			String topicJndiNames = mapProperties.get(TOPIC_JNDI_NAMES);

			SessionInfo sessionInfo = new SessionInfo(cfJndiName, queueJndiNames, topicJndiNames);

			Hashtable<String, Object> env = new Hashtable<>();
			env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
			env.put(InitialContext.PROVIDER_URL, brokerURL);
			env.put(Context.SECURITY_PRINCIPAL, username);
			env.put(Context.SECURITY_CREDENTIALS, password);
			env.put(SupportedProperty.SOLACE_JMS_VPN, msgVpn);
			env.put(SupportedProperty.SOLACE_JMS_SSL_VALIDATE_CERTIFICATE, false);


			// JMS Connections
			InitialContext ctx = new InitialContext(env);
			ConnectionFactory cf = (ConnectionFactory) ctx.lookup(cfJndiName);

			Connection jmsConnection = cf.createConnection();
			jmsConnection.setClientID(clientID);

			// Store per connection related data
			Integer hash = jmsConnection.hashCode();
			jndiContexts.put(hash, ctx);
			sessionJMSs.put(hash, sessionInfo);

			jmsConnection.start();

			return jmsConnection;
		} finally {
			restoreSystemProperties();
		}
	}

	@Override
	public void close(Connection jmsConnection) throws JMSException {
		log.debug("close connection {}", jmsConnection);

		Integer hash = jmsConnection.hashCode();
		Context ctx = jndiContexts.get(hash);

		if (ctx != null) {
			try {
				ctx.close();
			} catch (Exception e) {
				log.warn("Exception occurred while closing initial context. Ignore it. Msg={}", e.getMessage());
			}
			jndiContexts.remove(hash);
		}

		try {
			jmsConnection.close();
		} catch (Exception e) {
			log.warn("Exception occurred while closing jmsConnection. Ignore it. Msg={}", e.getMessage());
		}
	}

	@Override
	public DestinationData discoverDestinations(Connection jmsConnection, boolean showSystemObjects) throws Exception {
		log.debug("discoverDestinations : {} - {}", jmsConnection, showSystemObjects);

		Integer hash = jmsConnection.hashCode();
		Context ctx = jndiContexts.get(hash);
		SessionInfo sessionInfo = sessionJMSs.get(hash);

		// Build Queues/Topics lists
		SortedSet<QueueData> listQueueData = new TreeSet<>();
		SortedSet<TopicData> listTopicData = new TreeSet<>();
		
		String queueJndiNames = sessionInfo.getQueueJndiNames();
		String topicJndiNames = sessionInfo.getTopicJndiNames();

		lookupDestinations(queueJndiNames, ctx, listQueueData, listTopicData);
		lookupDestinations(topicJndiNames, ctx, listQueueData, listTopicData);

		return new DestinationData(listQueueData, listTopicData);
	}
	
	@Override
	public boolean manulAcknoledge() {
		return false;
	}

	// -------
	// Helpers
	// -------

	private void lookupDestinations(String jndiNames, Context ctx, SortedSet<QueueData> q, SortedSet<TopicData> t)
			throws NamingException {
		if (jndiNames != null && jndiNames.length() > 0) {
			for (String jndiName : jndiNames.split(",")) {
				Object o;
				String name = null;
				try {	
					name = jndiName.trim();
					o = ctx.lookup(name);
					if (o instanceof Queue) {
						log.debug("   It's a Queue");
						Queue oq = (Queue) o;
						q.add(new QueueData(oq.getQueueName()));
					}
					if (o instanceof Topic) {
						log.debug("   It's a Topic");
						Topic ot = (Topic) o;
						t.add(new TopicData(ot.getTopicName()));
					}
				} catch (Throwable e) {
					log.debug("   !!! Exception when processing jndiName '{}' : {}", name, e.getMessage());
					continue;
				}				
			}
		}
	}

	// ------------------------
	// Standard Getters/Setters
	// ------------------------

	@Override
	public List<QManagerProperty> getQManagerProperties() {
		return parameters;
	}

	private class SessionInfo {

		private String cfJndiName;
		private String queueJndiNames;
		private String topicJndiNames;

		public SessionInfo(String cfJndiName, String queueJndiNames, String topicJndiNames) {
			this.cfJndiName = cfJndiName;
			this.queueJndiNames = queueJndiNames;
			this.topicJndiNames = topicJndiNames;
		}

		public String getCfJndiName() {
			return cfJndiName;
		}

		public String getQueueJndiNames() {
			return queueJndiNames;
		}

		public String getTopicJndiNames() {
			return topicJndiNames;
		}

	}

}
