/*
 *  Child USB Port
 *
 *  Changelog:
 *
 *    1.0.1 (09/27/2020)
 *      - Changed ocfDeviceType to fix active/inactive icon on the dashboard
 *      - Added Refresh Capability because quick on/off states don't always get reported.
 *
 *    1.0 (09/21/2020)
 *      - Initial Release
 *
 *
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

metadata {
	definition (
		name: "Child USB Port", 
		namespace: "jtp10181",
		author: "Jeff Page / Kevin LaFramboise (@krlaframboise)",
		importUrl: ""
	) {
		capability "Switch"
		capability "Refresh"	
	}
	
	preferences { }
}


def installed() { 
	sendEvent(name:"switch", value:"off")
	sendEvent(name:"usbPort", value:"off")
}

def updated() {	

}

def on() {
	log.debug "The USB Port can't be controlled."
}

def off() {
	log.debug "The USB Port can't be controlled."
}

def refresh() {
	parent.childRefresh(device.deviceNetworkId)	
}