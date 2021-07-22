/**
 *  Zooz Double Plug
 *  (Model: ZEN25)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *	Documentation:
 *
 *  Changelog:
 *
 *    1.3.2 (09/27/2020)
 *      - Added support for Refresh command of USB port.
 *      - Increase default reporting intervals to improve reliability of on/off states
 *
 *    1.3.1 (09/26/2020)
 *      - Previous version replaced the child component device with a regular child, but it didn't use the new child usb port handler so this version corrects that.
 *
 *    1.3 (09/21/2020)
 *      - Create child device for USB Port using the USB Port Child DTH.
 *
 *    1.2.5 (08/16/2020)
 *      - Removed componentLabel and componentName from child outlet devices which fixes the timeout issue in the new mobile app.
 *
 *    1.2.4 (08/10/2020)
 *      - Added ST workaround for S2 Supervision bug with MultiChannel Devices.
 *
 *    1.2.3 (03/14/2020)
 *      - Fixed bug with enum settings that was caused by a change ST made in the new mobile app.
 *
 *    1.2.2 (02/03/2019)
 *      - Fixed unit on Power Threshold setting.
 *
 *    1.2 (01/31/2019)
 *      - Changed USB tile to standardTile
 *
 *    1.1 (01/26/2019)
 *      - Fixed typo
 *      - Stopped forcing isStateChange to true so that only events that duplicate events aren't shown.
 *      - Fixed config parameter options.
 *      - Fixed other misc issues.
 *
 *    1.0 (01/21/2019)
 *      - Initial Release
 *
 *
 *  Copyright 2020-2021 Jeff Page
 *  Copyright 2020 Kevin LaFramboise
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/

import groovy.transform.Field

@Field static final usbEndPoint = 3
@Field static final lowHigh = ["power", "voltage", "current"]

metadata {
	definition (
		name: "Zooz ZEN25 Double Plug", 
		namespace: "jtp10181",
		author: "Jeff Page / Kevin LaFramboise (@krlaframboise)",
		importUrl: ""
	) {
		capability "Actuator"
		// capability "Switch"
		capability "Outlet"
		capability "PowerMeter"
		capability "VoltageMeasurement"
		capability "EnergyMeter"
		capability "Configuration"
		capability "Refresh"
		// capability "HealthCheck"

		// command "leftOff"
		// command "leftOn"
		// command "rightOff"
		// command "rightOn"		
		command "resetStats"
		command "childDevices", [[name:"Select One*", type: "ENUM", constraints: ["Create","Remove"] ]]

		//attribute "assocDNI2", "string"
		//attribute "assocDNI3", "string"
		attribute "syncStatus", "string"
		
		//attribute "secondaryStatus", "string"
		attribute "energyTime", "number"
		attribute "current", "number"
		attribute "energyDuration", "string"
		
		lowHigh.each {
			attribute "${it}Low", "number"
			attribute "${it}High", "number"
		}
		
		// attribute "powerLeft", "number"
		// attribute "switchLeft", "string"
		// attribute "powerRight", "number"
		// attribute "switchRight", "string"
		// attribute "switchUsb", "string"

		fingerprint mfr: "027A", prod: "A000", deviceId: "A003", deviceJoinName: "Zooz Double Plug"
	}
	
	preferences {
		configParams.each { param ->
			if (!(param in [leftAutoOffEnabledParam, rightAutoOffEnabledParam, leftAutoOnEnabledParam, rightAutoOnEnabledParam])) {
				getOptionsInput(param)
			}
		}
	
		getBoolInput("txtEnable", "Enable Description Text Logging", false)
		getBoolInput("debugEnable", "Enable Debug Logging", true)
	}
}


private getOptionsInput(param) {
	input "configParam${param.num}", "enum",
		title: "${param.name} (#${param.num}):",
		required: false,
		defaultValue: "${param.value}",
		displayDuringSetup: true,
		options: param.options
}

private getBoolInput(name, title, defaultVal) {
	input "${name}", "bool", 
		title: "${title}?", 
		defaultValue: defaultVal, 
		required: false
}


def installed () { 
	sendEvent(name:"energyTime", value:new Date().time, displayed: false)
	runIn(10, createChildDevices) 
}

def updated() {
	if (!isDuplicateCommand(state.lastUpdated, 5000)) {
		state.lastUpdated = new Date().time

		log.info "updated..."
		log.warn "Debug logging is: ${debugEnable == true}"
		log.warn "Description logging is: ${txtEnable == true}"
	
		//runEvery3Hours(ping)
		
		if (childDevices?.size() != 3) {
			runIn(2, createChildDevices)
		}

		if (debugEnable) runIn(1800, debugLogsOff)
		
		List<String> cmds = getConfigureCmds()
		return cmds ? delayBetween(cmds, 500) : []
	}	
}

void childDevices(str) {
	switch (str) {
		case "Create":
			createChildDevices()
			break
		case "Remove":
			removeChildDevices()
			break
		default:
			log.warn "childDevices invalid input: ${str}"
	}
}

def createChildDevices() {
	(1..2).each { endPoint ->
		if (!findChildByEndPoint(endPoint)) {			
			addChildOutlet(endPoint)
		}
	}

	if (!findChildByEndPoint(usbEndPoint)) {			
		addChildUSB(usbEndPoint)
	}	
}

void addChildOutlet(endPoint) {
	String deviceType = "Zooz ZEN25 Double Plug (Outlet)"
	String deviceTypeBak = "Generic Component Metering Switch"
	String dni = getChildDeviceNetworkId(endPoint)
	String name = getEndPointName(endPoint)?.toUpperCase()
	Map properties = [isComponent: false, name: "${device.name} ${name} Outlet"]
	
	logDebug "Creating ${name} Outlet Child Device"

	try {
		addChildDevice(deviceType, dni, properties)
	}
	catch (e) {
		log.warn "The '${deviceType}' driver failed, using '${deviceTypeBak}' instead"
		addChildDevice("hubitat", deviceTypeBak, dni, properties)
	}

	childReset(dni)
}

void addChildUSB(endPoint) {
	String deviceType = "Child USB Port"
	String deviceTypeBak = "Generic Component Switch"
	String dni = getChildDeviceNetworkId(endPoint)
	String name = getEndPointName(endPoint)?.toUpperCase()
	Map properties = [isComponent: false, name: "${device.name} ${name}"]
	
	logDebug "Creating ${name} Child Device"

	try {
		addChildDevice(deviceType, dni, properties)
	}
	catch (e) {
		log.warn "The '${deviceType}' driver failed, using '${deviceTypeBak}' instead"
		addChildDevice("hubitat", deviceTypeBak, dni, properties)
	}

	sendCommands(getRefreshCmds(dni))
}

def removeChildDevices() {
	logDebug "removeChildDevices..."
	childDevices.each { child ->	
		deleteChildDevice(child.deviceNetworkId)
	}
}

def configure() {
	log.warn "configure..."
	if (debugEnable) runIn(1800, debugLogsOff)
	
	if (!pendingChanges || state.resyncAll == null) {
		logDebug "Enabling Full Re-Sync"
		state.resyncAll = true
	}

	List<String> cmds = []

	if (device.currentValue("energy") == null) {
		cmds += getResetCmds()
	}

	cmds += getRefreshCmds()
	cmds += getConfigureCmds()
	
	state.resyncAll = false

	updateSyncingStatus()
	runIn(6, refreshSyncStatus)

	return delayBetween(cmds, 500)
}

private getConfigureCmds() {
	List<String> cmds = []
	
	if (state.resyncAll || !device.getDataValue("firmwareVersion")) {
		cmds << versionGetCmd()
	}

	configParams.each { param ->
		Integer paramVal = getAdjustedParamValue(param)
		Integer storedVal = getParamStoredValue(param.num)

		if ((paramVal != null) && (state.resyncAll || (storedVal != paramVal))) {
			logDebug "Changing ${param.name} (#${param.num}) from ${storedVal} to ${paramVal}"
			cmds += configSetGetCmd(param, paramVal)
		}
	}
	
	if (state.resyncAll) clearVariables()

	return cmds
}

void clearVariables() {
	log.warn "Clearing state variables and data..."

	//Clears State Variables
	state.clear()

	//Clear Data from other Drivers
	device.removeDataValue("configVals")
	device.removeDataValue("firmwareVersion")
	device.removeDataValue("protocolVersion")
	device.removeDataValue("hardwareVersion")
	device.removeDataValue("serialNumber")
}

void debugLogsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugEnable",[value:"false",type:"bool"])
}


def ping() {
	logDebug "ping..."
	return sendCommands(basicGetCmd())
}


def on() {
	logDebug "on..."
	return getChildSwitchCmds(0xFF, null)
}

def off() {
	logDebug "off..."
	return getChildSwitchCmds(0x00, null)
}


// def childUpdated(dni) {
// 	logDebug "childUpdated(${dni})"
// 	def child = findChildByDeviceNetworkId(dni)
// 	def endPoint = getEndPoint(dni)
// 	def endPointName = getEndPointName(endPoint)	
// 	//def nameAttr = "${endPointName}Name"
	
// 	if (child && "${child.displayName}" != "${device.currentValue(nameAttr)}") {
// 		sendEvent(name: nameAttr, value: child.displayName, displayed: false)
// 	}
// }


def leftOn() { childOn(getChildDeviceNetworkId(1)) }
def rightOn() { childOn(getChildDeviceNetworkId(2)) }

def childOn(dni) {
	logDebug "childOn(${dni})..."
	sendCommands(getChildSwitchCmds(0xFF, dni))
}


def leftOff() { childOff(getChildDeviceNetworkId(1)) }
def rightOff() { childOff(getChildDeviceNetworkId(2)) }

def childOff(dni) {
	logDebug "childOff(${dni})..."
	sendCommands(getChildSwitchCmds(0x00, dni))
}

private getChildSwitchCmds(value, dni) {
	def endPoint = getEndPoint(dni)	
	return switchBinarySetCmd(value, endPoint)
}


def refresh() {
	logDebug "refresh..."
	def cmds = getRefreshCmds()
	
	childDevices.each {
		def dni = it.deviceNetworkId
		cmds += getRefreshCmds(dni)
	}
	sendCommands(cmds, 500)
	return []
}

def componentRefresh(dni) { childRefresh(dni) }
def childRefresh(dni) {
	logDebug "childRefresh($dni)..."
	sendCommands(getRefreshCmds(dni))
}

private getRefreshCmds(dni=null) {
	Integer endPoint = getEndPoint(dni) ?: 0
	List<String> cmds = [switchBinaryGetCmd(endPoint)]
	
	if (endPoint != usbEndPoint) {
		cmds += [
			meterGetCmd(meterEnergy, endPoint),
			meterGetCmd(meterPower, endPoint),
			meterGetCmd(meterVoltage, endPoint),
			meterGetCmd(meterCurrent, endPoint)
		]
	}	
	
	return cmds
}

def resetStats() {
	logDebug "reset()..."
	
	runIn(5, refresh)
	
	def cmds = getResetCmds()	
	childDevices.each { child ->	
		if (!"${child.deviceNetworkId}".endsWith("USB")) {
			cmds << "delay 500"
			cmds += getResetCmds(child.deviceNetworkId)
		}
	}
	return cmds
}

def childReset(dni) {
	logDebug "childReset($dni)"
	
	List<String> cmds = []
	cmds += getResetCmds(dni)
	cmds += getRefreshCmds(dni)
	sendCommands(cmds)
}

private getResetCmds(dni=null) {
	def endPoint = getEndPoint(dni)
	def child = findChildByDeviceNetworkId(dni)
		
	lowHigh.each {
		executeSendEvent(child, createEventMap("${it}Low", getAttrVal(it), false))
		executeSendEvent(child, createEventMap("${it}High", getAttrVal(it), false))
	}
	executeSendEvent(child, createEventMap("energyTime", new Date().time, false))
	sendEnergyEvents(child, 0)
	
	return [meterResetCmd(endPoint)]
}


//These send commands to the device either a list or a single command
void sendCommands(List<String> cmds, Long delay=400) {
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendCommands(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}


//Consolidated zwave command functions so other code is easier to read
String associationSetCmd(Integer group, List<Integer> nodes) {
	return supervisionEncap(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

String associationRemoveCmd(Integer group, List<Integer> nodes) {
	return supervisionEncap(zwave.associationV2.associationRemove(groupingIdentifier: group, nodeId: nodes))
}

String associationGetCmd(Integer group) {
	return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: group))
}

String versionGetCmd() {
	return secureCmd(zwave.versionV3.versionGet())
}

String basicGetCmd() {
	return secureCmd(zwave.basicV1.basicGet())
}

String meterGetCmd(meter, endPoint) {
	return secureCmd(zwave.meterV3.meterGet(scale: meter.scale), endPoint)
}

String meterResetCmd(endPoint) {
	return secureCmd(zwave.meterV3.meterReset(), endPoint)
}

String switchBinarySetCmd(Integer value, ep) {
	return supervisionEncap(zwave.switchBinaryV1.switchBinarySet(switchValue: value), ep)
}

String switchBinaryGetCmd(ep) {
	return secureCmd(zwave.switchBinaryV1.switchBinaryGet(), ep)
}

String configSetCmd(Map param, Integer value) {
	return supervisionEncap(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value))
}

String configGetCmd(Map param) {
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

List configSetGetCmd(Map param, Integer value) {
	List<String> cmds = []
	cmds << configSetCmd(param, value)
	cmds << configGetCmd(param)
	return cmds
}

//Secure and MultiChannel Encapsulate
String secureCmd(String cmd) {
	return zwaveSecureEncap(cmd)
}
String secureCmd(hubitat.zwave.Command cmd, ep=0) {
	return zwaveSecureEncap(multiChannelEncap(cmd, ep))
}

//MultiChannel Encapsulate if needed
//This is called from secureCmd or supervisionEncap, do not call directly
String multiChannelEncap(hubitat.zwave.Command cmd, ep) {
	//logTrace "multiChannelEncap: ${cmd} (ep ${ep})"
	if (ep > 0) {
		cmd = zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint:ep).encapsulate(cmd)
	}
	return cmd.format()
}

String supervisionEncap(hubitat.zwave.Command cmd, ep=0) {
	//logTrace "supervisionEncap: ${cmd} (ep ${ep})"

		//If supervision disabled just multichannel and secure
		return secureCmd(cmd, ep)
}

/*
CommandClassReport- class:0x25, version:1
CommandClassReport- class:0x32, version:4
CommandClassReport- class:0x55, version:2
CommandClassReport- class:0x59, version:1
CommandClassReport- class:0x5A, version:1
CommandClassReport- class:0x5E, version:2
CommandClassReport- class:0x60, version:4
CommandClassReport- class:0x6C, version:1
CommandClassReport- class:0x70, version:1
CommandClassReport- class:0x71, version:8
CommandClassReport- class:0x72, version:2
CommandClassReport- class:0x73, version:1
CommandClassReport- class:0x7A, version:4
CommandClassReport- class:0x85, version:2
CommandClassReport- class:0x86, version:3
CommandClassReport- class:0x8E, version:3
CommandClassReport- class:0x9F, version:1
*/

private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x32: 3,	// Meter (4)
		0x55: 1,	// Transport Service (2)
		0x59: 1,	// AssociationGrpInfo
		0x5A: 1,	// DeviceResetLocally
		0x5E: 2,	// ZwaveplusInfo
		0x60: 3,	// Multi Channel
		0x6C: 1,	// Supervision
		0x70: 2,	// Configuration
		0x71: 8,	// Notification
		0x72: 2,	// ManufacturerSpecific
		0x73: 1,	// Powerlevel
		0x7A: 4,	// Firmware Update Md
		0x85: 2,	// Association
		0x86: 3,	// Version
		0x8E: 3,	// Multi Channel Association
		0x98: 1,	// Security 0
		0x9F: 1		// Security 2
	]
}


def parse(String description) {
	def cmd = zwave.parse(description, commandClassVersions)
	logTrace "parse: ${description} --PARSED-- ${cmd}"

	if (cmd) {
		zwaveEvent(cmd)
	} else {
		log.warn "Unable to parse: $description"
	}

	updateLastCheckIn()
}

void updateLastCheckIn() {
	if (!isDuplicateCommand(state.lastCheckInTime, 60000)) {
		state.lastCheckInTime = new Date().time
		state.lastCheckInDate = convertToLocalTimeString(new Date())
	}
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
	logTrace "${cmd} --ENCAP-- ${encapsulatedCmd}"
	
	if (encapsulatedCmd) {
		zwaveEvent(encapsulatedCmd)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
	logTrace "${cmd} --ENCAP-- ${encapsulatedCmd}"
	
	if (encapsulatedCmd) {
		zwaveEvent(encapsulatedCmd, cmd.sourceEndPoint as Integer)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, ep=0) {
	def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
	logTrace "${cmd} --ENCAP-- ${encapsulatedCmd}"
	
	if (encapsulatedCmd) {
		zwaveEvent(encapsulatedCmd, ep)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}

	sendCommands(secureCmd(zwave.supervisionV1.supervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0), ep))
}


void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
	String fullVersion = String.format("%d.%02d",cmd.firmware0Version,cmd.firmware0SubVersion)
	device.updateDataValue("firmwareVersion", fullVersion)
}


def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	logTrace "${cmd}"	
	updateSyncingStatus()
	
	Map param = configParams.find { it.num == cmd.parameterNumber }

	if (param) {	
		Integer val = cmd.scaledConfigurationValue
		logDebug "${param.name} (#${param.num}) = ${val}"
		setParamStoredValue(param.num, val)				
	}
	else {
		logDebug "Unknown Parameter #${cmd.parameterNumber} = ${cmd.scaledConfigurationValue}"
	}		
	state.resyncAll = false	
}

//DEPRECIATED
// void updateSyncStatus(String status=null) {	
// 	if (status == null) {	
// 		Integer changes = pendingChanges
// 		status = changes ? "${changes} Pending Changes" : "Synced"
// 	}

// 	if (getSyncStatus() != status) {
// 		executeSendEvent(null, createEventMap("syncStatus", status, false))		
// 	}
// }

void updateSyncingStatus() {
	runIn(4, refreshSyncStatus)
	executeSendEvent(null, createEventMap("syncStatus", "Syncing...", false))
}

void refreshSyncStatus() {
	Integer changes = pendingChanges
	executeSendEvent(null, createEventMap("syncStatus", (changes ?  "${changes} Pending Changes" : "Synced"), false))
}


String getSyncStatus() {
	return device.currentValue("syncStatus")
}

Integer getPendingChanges() {
	Integer configChanges = configParams.count { param ->
		Integer paramVal = getAdjustedParamValue(param)
		((paramVal != null) && (paramVal != getParamStoredValue(param.num)))
	}
	return (configChanges)
}

Integer getParamStoredValue(Integer paramNum) {
	//Using Data (Map) instead of State Variables
	TreeMap configsMap = getParamStoredMap()
	return safeToInt(configsMap[paramNum], null)
}

void setParamStoredValue(Integer paramNum, Integer value) {
	//Using Data (Map) instead of State Variables
	TreeMap configsMap = getParamStoredMap()
	configsMap[paramNum] = value
	device.updateDataValue("configVals", configsMap.inspect())
}

Map getParamStoredMap() {
	Map configsMap = [:]
	String configsStr = device?.getDataValue("configVals")

	if (configsStr) {
		try {
			configsMap = evaluate(configsStr)
		}
		catch(Exception e) {
			log.warn("Clearing Invalid configVals: ${e}")
			device.removeDataValue("configVals")
		}
	}
	return configsMap
}


def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, endPoint=0) {
	logTrace "SwitchBinaryReport: ${cmd} (${getEndPointName(endPoint)})"
	
	def value = (cmd.value == 0xFF) ? "on" : "off"
	
	executeSendEvent(findChildByEndPoint(endPoint), createEventMap("switch", value))
	
	//if (endPoint) sendEvent(name: "switch${getEndPointName(endPoint)}", value:value, displayed: false)
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, endPoint=0) {
	logTrace "BasicReport: ${cmd} (${getEndPointName(endPoint)})"
}


def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, endPoint=0) {
	def val = roundTwoPlaces(cmd.scaledMeterValue)
	def child = findChildByEndPoint(endPoint)	
	
	switch (cmd.scale) {
		case meterEnergy.scale:			
			sendEnergyEvents(child, val)
			break
		case meterPower.scale:
			sendMeterEvents(child, meterPower, val)
			//if (endPoint) sendEvent(name: "power${getEndPointName(endPoint)}", value: val, unit:"W", displayed: false)
			break
		case meterVoltage.scale:
			sendMeterEvents(child, meterVoltage, val)
			break
		case meterCurrent.scale:
			sendMeterEvents(child, meterCurrent, val)
			break
		default:
			logDebug "Unknown Meter Scale: $cmd"
	}
	
	// runIn(2, updateSecondaryStatus)
}

private sendMeterEvents(child, meter, value) {
	def highLowNames = [] 
	
	executeSendEvent(child, createEventMap(meter.name, value, meter.displayed, meter.unit))
	
	def highName = "${meter.name}High"
	if (getAttrVal(highName, child) == null || value > getAttrVal(highName, child)) {
		highLowNames << highName
	}

	def lowName = "${meter.name}Low"
	if (getAttrVal(lowName, child) == null || value < getAttrVal(lowName, child)) {
		highLowNames << lowName
	}
	
	highLowNames.each {
		executeSendEvent(child, createEventMap("$it", value, false, meterPower.unit))
	}	
}



private sendEnergyEvents(child, value) {
	executeSendEvent(child, createEventMap("energy", value, meterEnergy.displayed, meterEnergy.unit))
	executeSendEvent(child, createEventMap("energyDuration", calculateEnergyDuration(child), false))
}

private calculateEnergyDuration(child) {
	def energyTimeMS = getAttrVal("energyTime", child)
	if (!energyTimeMS) {
		return "Unknown"
	}
	else {
		def duration = roundTwoPlaces((new Date().time - energyTimeMS) / 60000)
		
		if (duration >= (24 * 60)) {
			return getFormattedDuration(duration, (24 * 60), "Day")
		}
		else if (duration >= 60) {
			return getFormattedDuration(duration, 60, "Hour")
		}
		else {
			return getFormattedDuration(duration, 0, "Minute")
		}
	}
}

private getFormattedDuration(duration, divisor, name) {
	if (divisor) {
		duration = roundTwoPlaces(duration / divisor)
	}	
	return "${duration} ${name}${duration == 1 ? '' : 's'}"
}


// def updateSecondaryStatus() {
	// (0..5).each { endPoint ->	
		// def child = findChildByEndPoint(endPoint)
		// def power = getAttrVal("power", child) ?: 0
		// def energy = getAttrVal("energy", child) ?: 0
		// def duration = getAttrVal("energyDuration", child) ?: ""
		// // def active = getAttrVal("acceleration", child) ?: "inactive"
		
		// if (duration) {
			// duration = " - ${duration}"
		// }
		
		// def status = ""
		
		// // status = settings?.displayAcceleration ? "${active.toUpperCase()} / " : ""
		
		// status =  "${status}${power} ${meterPower.unit} / ${energy} ${meterEnergy.unit}${duration}"
		
		// if (getAttrVal("secondaryStatus", child) != "${status}") {
			// executeSendEvent(child, createEventMap("secondaryStatus", status, false))
		// }
	// }
// }


def zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Unhandled zwaveEvent: $cmd"
}


// Meters
private getMeterEnergy() { 
	return getMeterMap("energy", 0, "kWh", null, settings?.displayEnergy != false) 
}

private getMeterPower() { 
	return getMeterMap("power", 2, "W", 2000, settings?.displayPower != false)
}

private getMeterVoltage() { 
	return getMeterMap("voltage", 4, "V", 150, settings?.displayVoltage != false) 
}

private getMeterCurrent() { 
	return getMeterMap("current", 5, "A", 18, settings?.displayCurrent != false)
}

private getMeterMap(name, scale, unit, limit, displayed) {
	return [name:name, scale:scale, unit:unit, limit: limit, displayed:displayed]
}



// Configuration Parameters
private getConfigParams() {
	return [
		powerFailureRecoveryParam,
		overloadProtectionParam,
		manualControlParam,
		ledIndicatorModeParam,
		powerReportingThresholdParam,
		powerReportingFrequencyParam,
		energyReportingFrequencyParam,
		voltageReportingFrequencyParam,
		ampsReportingFrequencyParam,		
		leftAutoOffEnabledParam,
		leftAutoOffIntervalParam,
		rightAutoOffEnabledParam,
		rightAutoOffIntervalParam,
		leftAutoOnEnabledParam,
		leftAutoOnIntervalParam,
		rightAutoOnEnabledParam,
		rightAutoOnIntervalParam
	]
}


private getAdjustedParamValue(Map param) {
	Integer paramVal
	switch(param.num) {
		case leftAutoOffEnabledParam.num:
			paramVal = leftAutoOffIntervalParam.value == 0 ? 0 : 1
			break
		case leftAutoOffIntervalParam.num:
			paramVal = leftAutoOffIntervalParam.value ?: 60
			break
		case rightAutoOffEnabledParam.num:
			paramVal = rightAutoOffIntervalParam.value == 0 ? 0 : 1
			break
		case rightAutoOffIntervalParam.num:
			paramVal = rightAutoOffIntervalParam.value ?: 60
			break
		case leftAutoOnEnabledParam.num:
			paramVal = leftAutoOnIntervalParam.value == 0 ? 0 : 1
			break
		case leftAutoOnIntervalParam.num:
			paramVal = leftAutoOnIntervalParam.value ?: 60
			break
		case rightAutoOnEnabledParam.num:
			paramVal = rightAutoOnIntervalParam.value == 0 ? 0 : 1
			break
		case rightAutoOnIntervalParam.num:
			paramVal = rightAutoOnIntervalParam.value ?: 60
			break
		default:
			paramVal = param.value
	}
	return paramVal
}

/*
ZOOZ: We'd probably do the following [default] settings for energy reporting:
Parameter 3: 600 
Parameter 4: 3600 
Parameter 5: 7200
Parameter 6: 3600 
*/

private getPowerFailureRecoveryParam() {
	return getParam(1, "Behavior After Power Failure", 1, 0, 
		[0:"Restores Last Status", 1:"Turn Outlets On", 2:"Turn Outlets Off"])
}

private getPowerReportingThresholdParam() {
	return getParam(2, "Power Wattage Reporting Threshold", 4, 5, powerReportingThresholdOptions) 
}

private getPowerReportingFrequencyParam() {
	return getParam(3, "Power Wattage Reporting Frequency", 4, 600, frequencyOptions) //Zooz default is 30
}

private getEnergyReportingFrequencyParam() {
	return getParam(4, "Energy (kWh) Reporting Frequency", 4, 3600, frequencyOptions) 
}

private getVoltageReportingFrequencyParam() {
	return getParam(5, "Voltage (V) Reporting Frequency", 4, 7200, frequencyOptions) 
}

private getAmpsReportingFrequencyParam() {
	return getParam(6, "Electrical Current (A) Reporting Frequency", 4, 3600, frequencyOptions) 
}

private getOverloadProtectionParam() {
	return getParam(7, "Overload Protection Amps", 1, 10, overloadOptions) 
}

private getLeftAutoOffEnabledParam() {
	return getParam(8, "Left Outlet Auto Turn-Off", 1, 0, enabledOptions)
}

private getLeftAutoOffIntervalParam() {
	return getParam(9, "Left Outlet Auto Turn-Off Timer", 4, 0, autoOnOffIntervalOptions)
}

private getRightAutoOffEnabledParam() {
	return getParam(12, "Right Outlet Auto Turn-Off", 1, 0, enabledOptions)
}

private getRightAutoOffIntervalParam() {
	return getParam(13, "Right Outlet Auto Turn-Off Timer", 4, 0, autoOnOffIntervalOptions)
}

private getLeftAutoOnEnabledParam() {
	return getParam(10, "Left Outlet Auto Turn-On", 1, 0, enabledOptions)
}

private getLeftAutoOnIntervalParam() {
	return getParam(11, "Left Outlet Auto Turn-On Timer", 4, 0, autoOnOffIntervalOptions)
}

private getRightAutoOnEnabledParam() {
	return getParam(14, "Right Outlet Auto Turn-On", 1, 0, enabledOptions)
}

private getRightAutoOnIntervalParam() {
	return getParam(15, "Right Outlet Auto Turn-On Timer", 4, 0, autoOnOffIntervalOptions)
}

private getManualControlParam() {
	return getParam(16, "Manual Control Button", 1, 1, enabledOptions)
}

private getLedIndicatorModeParam() {
	return getParam(17, "LED Indicator Mode", 1, 1, [0:"Always On", 1:"On When Switch On", 2:"LED On for 5 Seconds", 3:"LED Always Off"])
}

Map getParam(Integer num, String name, Integer size, Integer defaultVal, Map options) {
	Integer val = safeToInt(settings?."configParam${num}", defaultVal)
	Map retMap = [num: num, name: name, size: size, value: val, options: options]

	if (options) {
		retMap.valueName = options?.find { k, v -> "${k}" == "${val}" }?.value
		retMap.options = setDefaultOption(options, defaultVal)
	}

	return retMap
}

Map setDefaultOption(Map options, Integer defaultVal) {
	return options?.collectEntries { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"
		}
		["$k": "$v"]
	}
}


private getOverloadOptions() {
	def options = [:]
	(1..10).each {
		options["${it}"] = "${it} A"
	}	
	return options
}

private getPowerReportingThresholdOptions() {
	def options = [0:"Disabled"]
	[1,2,3,4,5,10,25,50,75,100,150,200,250,300,400,500,750,1000,1250,1500,1750,2000,2500,3000,3500,4000,4500,5000].each {
		options["${it}"] = "${it} W"
	}
	return options
}

private getFrequencyOptions() {
	def options = [:]
	options = getTimeOptionsRange(options, "Second", 1, [30,45])
	options = getTimeOptionsRange(options, "Minute", 60, [1,2,3,4,5,10,15,30,45])
	options = getTimeOptionsRange(options, "Hour", (60 * 60), [1,2,3,6,9,12,24])
	return options
}

private getAutoOnOffIntervalOptions() {
	def options = [0:"Disabled"]
	options = getTimeOptionsRange(options, "Minute", 1, [1,2,3,4,5,6,7,8,9,10,15,20,25,30,45])
	options = getTimeOptionsRange(options, "Hour", 60, [1,2,3,4,5,6,7,8,9,10,12,18])
	options = getTimeOptionsRange(options, "Day", (60 * 24), [1,2,3,4,5,6])
	options = getTimeOptionsRange(options, "Week", (60 * 24 * 7), [1,2])
	return options
}

private getTimeOptionsRange(options, name, multiplier, range) {	
	range?.each {
		options["${(it * multiplier)}"] = "${it} ${name}${it == 1 ? '' : 's'}"
	}
	return options
}

private getEnabledOptions() {
	return [0:"Disabled", 1:"Enabled"]
}


private executeSendEvent(child, evt) {
	if (evt.displayed == null) {
		evt.displayed = (getAttrVal(evt.name, child) != evt.value)
	}

	if (evt) {
		if (child) {
			if (evt.descriptionText) {
				evt.descriptionText = evt.descriptionText.replace(device.displayName, child.displayName)
				logDebug "${evt.descriptionText}"
			}
			child.sendEvent(evt)

			if ((evt.name == "switch") && (child.deviceNetworkId.endsWith("USB"))) {
				evt.name = "switchUsb"
				child.sendEvent(evt)
			}
			
		}
		else {
			logDebug "${evt.descriptionText}"
			sendEvent(evt)
		}
	}
}

private createEventMap(String name, value, displayed=null, unit=null) {	
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		descriptionText: "${device.displayName} - ${name} is ${value}"
	]
	
	if (unit) {
		eventMap.unit = unit
		eventMap.descriptionText = "${eventMap.descriptionText} ${unit}"
	}	
	return eventMap
}

private getAttrVal(attrName, child=null) {
	try {
		if (child) {
			return child?.currentValue("${attrName}")
		}
		else {
			return device?.currentValue("${attrName}")
		}
	}
	catch (ex) {
		logTrace "$ex"
		return null
	}
}

private findChildByEndPoint(endPoint) {
	def dni = getChildDeviceNetworkId(endPoint)
	return findChildByDeviceNetworkId(dni)
}

private findChildByDeviceNetworkId(dni) {
	return childDevices?.find { it.deviceNetworkId == dni }
}

private getEndPoint(childDeviceNetworkId) {
	return safeToInt((1..3).find {
		"${childDeviceNetworkId}".endsWith("-${getEndPointName(it)?.toUpperCase()}")
	})
}

String getChildDeviceNetworkId(endPoint) {
	return "${device.deviceNetworkId}-${getEndPointName(endPoint).toUpperCase()}"
}

private getEndPointName(endPoint) {
	switch (endPoint) {
		case 1:
			return "Left"
			break
		case 2:
			return "Right"
			break
		case 3:
			return "Usb"
			break
		default:
			return ""
	}
}


private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private safeToDec(val, defaultVal=0) {
	return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

private roundTwoPlaces(val) {
	return Math.round(safeToDec(val) * 100) / 100
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}	
}

BigDecimal getFirmwareVersion() {
	String version = device?.getDataValue("firmwareVersion")
	return ((version != null) && version.isNumber()) ? version.toBigDecimal() : 0.0
}

boolean isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}


void logDebug(String msg) {
	if (debugEnable) log.debug "${device.displayName}: ${msg}"
}

void logTxt(String msg) {
	if (txtEnable) log.info "${device.displayName}: ${msg}"
}

//For Extreme Code Debugging - tracing commands
void logTrace(String msg) {
	//Uncomment to Enable
	//log.trace "${device.displayName}: ${msg}"
}
