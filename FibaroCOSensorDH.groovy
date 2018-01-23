/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Fibaro CO Sensor", namespace: "gregoiredore", author: "SmartThings") {
		capability "Sensor"
		capability "Battery"
		
		attribute "carbonMonoxide", "enum"

		fingerprint deviceId: "0xA1"
		fingerprint deviceId: "0x21"
		fingerprint deviceId: "0x20"
		fingerprint deviceId: "0x07"
	}


	tiles {
		standardTile("sensor", "device.sensor", width: 2, height: 2) {
			state("inactive", label:'clear', icon:"st.carbonmonoxidedetector", backgroundColor:"#13d61d")
			state("active", label:'detected', icon:"st.carbonmonoxidedetector", backgroundColor:"#dc0000")
			state("tested", label:'tested' , icon:"st.carbonmonoxidedetector", backgroundColor:"#47beed") 
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}

		main "sensor"
		details(["sensor", "battery"])
	}
}

private getCommandClassVersions() {
	[0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1]
}

def parse(String description) {
	def result = []
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	log.debug "Parsed '$description' to $result"
	return result
}

def sensorValueEvent(value) {
	if (value == 0) {
		createEvent([ name: "sensor", value: "inactive" ])
	} else if (value == 255) {
		createEvent([ name: "sensor", value: "active" ])
	} else {
		[ createEvent([ name: "sensor", value: "active" ]),
			createEvent([ name: "level", value: value ]) ]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{
	sensorValueEvent(cmd.alarmLevel)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	sensorValueEvent(cmd.sensorState)
}

def notificationEvent(String description, String value = "active") {
	createEvent([ name: "sensor", value: value, descriptionText: description, isStateChange: true ])
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd)
{
	def result = []
	{  // carbonMonoxyde Alarm
		setDeviceType("Z-Wave Smoke Alarm")
		switch (cmd.event) {
			case 0x00:
			case 0xFE:
				result << notificationEvent("Carbon Monoxyde is clear", "inactive")
				result << createEvent(name: "carbonMonoxyde", value: "clear")
				break
			case 0x01:
			case 0x02:
				result << notificationEvent("Carbon Monoxyde detected")
				result << createEvent(name: "carbonMonoxyde", value: "detected")
				break
			case 0x03:
				result << notificationEvent("Carbon Monoxyde tested")
				result << createEvent(name: "carbonMonoxyde", value: "tested")
				break
		}
	} 
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
	} else {
		map.value = cmd.batteryLevel
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	if (encapsulatedCommand) {
		state.sec = 1
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	// def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	def version = commandClassVersions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def result = null
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	log.debug "Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}"
	if (encapsulatedCommand) {
		result = zwaveEvent(encapsulatedCommand)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
	log.debug "MultiCmd with $numberOfCommands inner commands"
	cmd.encapsulatedCommands(commandClassVersions).collect { encapsulatedCommand ->
		zwaveEvent(encapsulatedCommand)
	}.flatten()
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}


def configure() {
	if (zwaveInfo.zw && zwaveInfo.zw.cc?.contains("84")) {
		zwave.wakeUpV1.wakeUpNoMoreInformation().format()
	}
}
