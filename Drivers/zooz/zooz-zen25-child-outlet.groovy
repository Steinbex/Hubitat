/**
 *  Zooz Double Plug Outlet v1.0 (CHILD DEVICE)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *  Changelog:
 *
 *    1.0 (01/21/2019)
 *      - Initial Release
 *
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
	definition (
		name: "Zooz ZEN25 Double Plug (Outlet)", 
		namespace: "jtp10181",
		author: "Jeff Page / Kevin LaFramboise (@krlaframboise)",
		importUrl: ""
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"		
		capability "Outlet"
		capability "PowerMeter"
		capability "VoltageMeasurement"
		capability "EnergyMeter"
		capability "Refresh"
		
		// attribute "secondaryStatus", "string"
		attribute "energyTime", "number"
		attribute "energyDuration", "string"
		attribute "current", "number"
				
		["power", "voltage", "current"].each {
			attribute "${it}Low", "number"
			attribute "${it}High", "number"
		}
				
		command "reset"
	}
	
	preferences { }
}


def installed() { }


def updated() {	
	parent.childUpdated(device.deviceNetworkId)
}


def on() {
	parent.childOn(device.deviceNetworkId)	
}

def off() {
	parent.childOff(device.deviceNetworkId)	
}

def refresh() {
	parent.childRefresh(device.deviceNetworkId)
}

def reset() {
	parent.childReset(device.deviceNetworkId)	
}