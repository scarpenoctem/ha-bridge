package com.bwssystems.mqtt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.BridgeSettingsDescriptor;
import com.bwssystems.HABridge.Home;
import com.bwssystems.HABridge.NamedIP;
import com.bwssystems.HABridge.api.CallItem;
import com.bwssystems.HABridge.api.hue.DeviceState;
import com.bwssystems.HABridge.api.hue.StateChangeBody;
import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.HABridge.hue.BrightnessDecode;
import com.bwssystems.HABridge.hue.MultiCommandUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MQTTHome implements Home {
    private static final Logger log = LoggerFactory.getLogger(MQTTHome.class);
	private Map<String, MQTTHandler> handlers;
	private Boolean validMqtt;
	private Gson aGsonHandler;

	public MQTTHome(BridgeSettingsDescriptor bridgeSettings) {
		super();
		createHome(bridgeSettings);
	}

	@Override
	public void closeHome() {
		if(!validMqtt)
			return;
		log.debug("Shutting down MQTT handlers.");
		if(handlers != null && !handlers.isEmpty()) {
			Iterator<String> keys = handlers.keySet().iterator();
			while(keys.hasNext()) {
				String key = keys.next();
				handlers.get(key).shutdown();
			}
		}
	}

	public MQTTHandler getMQTTHandler(String aName) {
		if(!validMqtt)
			return null;
		MQTTHandler aHandler;
		if(aName == null || aName.equals("")) {
			aHandler = null;
			log.debug("Cannot get MQTT handler for name as it is empty.");
		}
		else {
			aHandler = handlers.get(aName);
			log.debug("Retrieved a MQTT hanlder for name: " + aName);
		}
		return aHandler;
	}
	
	@Override
	public Object getItems(String type) {
		if(!validMqtt)
			return null;
		Iterator<String> keys = handlers.keySet().iterator();
		ArrayList<MQTTBroker> deviceList = new ArrayList<MQTTBroker>();
		while(keys.hasNext()) {
			String key = keys.next();
			MQTTHandler aHandler = handlers.get(key);
			MQTTBroker aDevice = new MQTTBroker(aHandler.getMyConfig());
			deviceList.add(aDevice);
		}
		return deviceList;
	}

	@Override
	public String deviceHandler(CallItem anItem, MultiCommandUtil aMultiUtil, String lightId, int iterationCount,
			DeviceState state, StateChangeBody theStateChanges, boolean stateHasBri, boolean stateHasBriInc, DeviceDescriptor device, String body) {
		String responseString = null;
		log.debug("executing HUE api request to send message to MQTT broker: " + anItem.getItem().toString());
		if (validMqtt) {
			MQTTMessage[] mqttMessages = aGsonHandler.fromJson(BrightnessDecode.replaceIntensityValue(anItem.getItem().toString(),
					BrightnessDecode.calculateIntensity(state, theStateChanges, stateHasBri, stateHasBriInc), false), MQTTMessage[].class);
        	Integer theCount = 1;
       		for(int z = 0; z < mqttMessages.length; z++) {
        		if(mqttMessages[z].getCount() != null && mqttMessages[z].getCount() > 0)
        			theCount = mqttMessages[z].getCount();
        		else
        			theCount = aMultiUtil.getSetCount();
        		for(int y = 0; y < theCount; y++) {
        			if( y > 0 || z > 0) {
						log.debug("publishing message: " + mqttMessages[y].getClientId() + " - "
								+ mqttMessages[y].getTopic() + " - " + mqttMessages[y].getMessage()
								+ " - iteration: " + String.valueOf(iterationCount) + " - count: " + String.valueOf(z));
						
						MQTTHandler mqttHandler = getMQTTHandler(mqttMessages[y].getClientId());
						if (mqttHandler == null) {
							log.warn("Should not get here, no mqtt hanlder available");
						} else {
							mqttHandler.publishMessage(mqttMessages[y].getTopic(), mqttMessages[y].getMessage());
						}
        			}
        		}
 			}
		} else {
			log.warn("Should not get here, no mqtt brokers configured");
			responseString = "[{\"error\":{\"type\": 6, \"address\": \"/lights/" + lightId
					+ "\",\"description\": \"Should not get here, no mqtt brokers configured\", \"parameter\": \"/lights/"
					+ lightId + "state\"}}]";

		}
		return responseString;
	}

	@Override
	public Home createHome(BridgeSettingsDescriptor bridgeSettings) {
		validMqtt = bridgeSettings.isValidMQTT();
		if(!validMqtt) {
			log.debug("No MQTT configuration");
		} else {
			aGsonHandler =
					new GsonBuilder()
					.create();
			handlers = new HashMap<String, MQTTHandler>();
			Iterator<NamedIP> theList = bridgeSettings.getMqttaddress().getDevices().iterator();
			while(theList.hasNext()) {
				NamedIP aClientConfig = theList.next();
				MQTTHandler aHandler = new MQTTHandler(aClientConfig);
				if(aHandler != null)
					handlers.put(aClientConfig.getName(), aHandler);
			}
		}
		return this;
	}
}
