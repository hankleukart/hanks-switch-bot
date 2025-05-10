/**
* Hank's Switch Bot v05-09-2025
* Copyright 2025 Hank Leukart
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at:
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Hubitat app for automated control of lights/scenes from switches based on device names.
* Handles 'All'/Master conventions, zone tags, and roomName property.
* Local switches ("Local Switch" or "(S)") have LEDs updated on mode change only.
*/

definition(
	name: "Hank's Switch Bot",
	namespace: "hankle",
	author: "Hank Leukart",
	description: "Simple, zero-config control of lights and scenes from switches. Manages local switches for LED changes.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
)

preferences {
	section("<h1>Hank's Switch Bot</h1>") {
		paragraph "Switch Bot automatically creates zero-configuration control of light and scenes with switches based on device names. For example, \"Kitchen Ceiling Switch\" will automatically control lights named \"Kitchen Ceiling 1 & 2\" and a scene named \"Kitchen Scene: Cooking.\" A switch named \"Kitchen Switch\" will control all \"Kitchen\" lights not controlled by another switch. Switches named with \"Local Switch\" or ending in \"(S)\" will only have their LEDs updated based on mode. Simply select all switches, lights, and scenes you want Switch Bot to handle for you.<hr />"

		input "controlledSwitches", "capability.pushableButton",
			title: "Switches controlled by Switch Bot:",
			multiple: true, required: true, width: 6

		input "controlledLightsAndScenes", "capability.actuator,capability.switchLevel,capability.pushableButton",
			title: "Lights & Scenes controlled by Switch Bot: (select-all recommended)",
			multiple: true, required: true, width: 6
	}

	section("Switch Control Mappings Summary", hideable: true, hidden: true) {
		paragraph "${state.switchControlSummary ?: 'Mappings will be displayed after saving settings.'}"
	}

	section("Mode Lighting Defaults", hideable: true, hidden: true) {
		input name: "globalDefaultLevel", type: "number", title: "Default Brightness (%)",
			 description: "Used if a mode has no specific level set.",
			 range: "1..100", defaultValue: 100, required: true, width: 3
		input name: "globalDefaultColorTemperature", type: "number", title: "Default Color Temperature (K)",
			 description: "Used if a mode is enabled for CT but has no specific CT set.",
			 range: "2000..9000", defaultValue: 2700, required: true, width: 3
		input name: "globalDefaultLedOnBrightness", type: "number", title: "Default LED On Brightness (%)",
			 description: "Used if a mode has no specific LED ON brightness.",
			 range: "0..100", defaultValue: 30, required: true, width: 3
		input name: "globalDefaultLedOffBrightness", type: "number", title: "Default LED Off Brightness (%)",
			 description: "Used if a mode has no specific LED OFF brightness.",
			 range: "0..100", defaultValue: 7, required: true, width: 3
		paragraph ""

		if (location.modes) {
			location.modes.sort { it.name }.each { mode ->
				String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()
				String currentModeNameLower = mode.name.toLowerCase()

				Integer conditionalDefaultLevel = null
				Integer conditionalDefaultCt = null
				Boolean conditionalEnableCt = true

				// Default values for common modes
				if (currentModeNameLower == "evening") {
					conditionalDefaultLevel = 100
					conditionalDefaultCt = 2700
				} else if (currentModeNameLower == "day") {
					conditionalDefaultLevel = 100
					conditionalDefaultCt = 5200
				} else if (currentModeNameLower == "sleep") {
					conditionalDefaultLevel = 5
					conditionalDefaultCt = 2700
				}

				input(name: "level_${safeModeName}", type: "number", title: "'${mode.name}' Brightness (%)", range: "1..100", required: false, width: 3, defaultValue: conditionalDefaultLevel)
				input(name: "ct_${safeModeName}", type: "number", title: "'${mode.name}' Color Temp (K)", range: "2000..9000", required: false, width: 3, defaultValue: conditionalDefaultCt)
				input(name: "enableCt_${safeModeName}", type: "bool", title: "Set CT?", defaultValue: conditionalEnableCt, required: false, width: 2)
				
				input(name: "ledOnBrightness_${safeModeName}", type: "number", title: "LED On Brightness (%)", range: "0..100", required: false, width: 2, defaultValue: 30)
				input(name: "ledOffBrightness_${safeModeName}", type: "number", title: "LED Off Brightness (%)", range: "0..100", required: false, width: 2, defaultValue: 7)
				
				// New input field for Sleep mode to specify a zone for turning off LEDs
				if (currentModeNameLower == "sleep") {
					input(name: "ledOffZone_${safeModeName}", type: "text", 
						  title: "Turn off LEDs in Sleep mode for zone:", 
						  description: "If a zone name is entered, LEDs on switches within that zone will be turned completely off (both ON and OFF LEDs set to 0) when in Sleep mode. Other switches will use the Sleep mode's LED brightness settings above.", 
						  required: false, width: 6)
				}
				paragraph ""
			}
		} else {
			paragraph "Save settings once to see per-mode configuration options."
		}
	}

	section("Advanced Button Mappings (Optional)", hideable: true, hidden: true) {
		 input "singleTapUpButtonNumber", "number", title: "Single Tap Up (Area ON / Next Scene) Button Number", defaultValue: 1, required: false, width: 2
		 input "singleTapUpButtonEvent", "enum", title: "Single Tap Up (Area ON / Next Scene) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12
		 input "singleTapDownButtonNumber", "number", title: "Single Tap Down (Area/Room/Zone OFF) Button Number", defaultValue: 1, required: false, width: 2
		 input "singleTapDownButtonEvent", "enum", title: "Single Tap Down (Area/Room/Zone OFF) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 2
		 paragraph "Note: In Scene Mode, Button 1 Pushed/Held are used for cycling scenes.", width: 12
		 input "configButtonNumber", "number", title: "Scene Mode Toggle Button Number", defaultValue: 8, required: false, width: 2
		 input "configButtonEvent", "enum", title: "Scene Mode Toggle Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12
		 input "doubleTapUpButtonNumber", "number", title: "Zone/Room On Button Number", defaultValue: 2, required: false, width: 2
		 input "doubleTapUpButtonEvent", "enum", title: "Zone/Room On Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12
		 input "doubleTapDownButtonNumber", "number", title: "Zone/Room Off Button Number", defaultValue: 2, required: false, width: 2
		 input "doubleTapDownButtonEvent", "enum", title: "Zone/Room Off Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12
		 input "holdUpButtonNumber", "number", title: "Hold Up (Start Dim Up) Button Number", defaultValue: 6, required: false, width: 2
		 input "holdUpButtonEvent", "enum", title: "Hold Up (Start Dim Up) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12
		 input "releaseUpButtonNumber", "number", title: "Release Up (Stop Dim Up) Button Number", defaultValue: 7, required: false, width: 2
		 input "releaseUpButtonEvent", "enum", title: "Release Up (Stop Dim Up) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12
		 input "holdDownButtonNumber", "number", title: "Hold Down (Start Dim Down) Button Number", defaultValue: 6, required: false, width: 2
		 input "holdDownButtonEvent", "enum", title: "Hold Down (Start Dim Down) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12
		 input "releaseDownButtonNumber", "number", title: "Release Down (Stop Dim Down) Button Number", defaultValue: 7, required: false, width: 2
		 input "releaseDownButtonEvent", "enum", title: "Release Down (Stop Dim Down) Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12
		 input "sceneModeTimeout", "number", title: "Scene Mode Timeout (seconds)", defaultValue: 7, required: false
	}
}

def installed() {
	log.info "Installed Hank's Switch Bot"
	initialize()
}

def updated() {
	log.info "Updated Hank's Switch Bot"
	unsubscribeAndUnschedule()
	initialize()
}

def uninstalled() {
	log.info "Uninstalling Hank's Switch Bot"
	unsubscribeAndUnschedule()
}

private void unsubscribeAndUnschedule() {
	log.info "Clearing all subscriptions and schedules for Hank's Switch Bot"
	try {
		unsubscribe()
		unschedule()
	} catch (e) {
		log.error "Error during unsubscribe/unschedule: ${e.message}"
	}
}

def initialize() {
	state.sceneIndex = [:]
	state.sceneMode = [:]
	state.sceneTimeoutJob = [:]
	state.modeSettingsMap = [:]
	state.currentLocationMode = null
	state.switchControlSummary = "Initializing or no switches configured..."
	state.siblingSwitchGroupsBySwitchId = [:]
	state.switchIdToLocationMap = [:]
	state.switchInfoMap = [:] 

	state.switchRoomLights = [:]
	state.switchAreaLights = [:]
	state.switchZoneLights = [:]
	state.switchScenes = [:]
	state.firstAreaLightToSwitch = [:] 
	state.sortedSwitchSceneIds = [:]  
	state.switchDimmableAreaLightIds = [:] 

	// Build device ID to index maps for faster lookups
	state.deviceToIndexMap = [switches: [:], lightsAndScenes: [:]]
	settings.controlledSwitches?.eachWithIndex { sw, index ->
		if (sw?.id) state.deviceToIndexMap.switches[sw.id.toString()] = index
	}
	settings.controlledLightsAndScenes?.eachWithIndex { dev, index ->
		if (dev?.id) state.deviceToIndexMap.lightsAndScenes[dev.id.toString()] = index
	}

	if (settings.controlledSwitches) {
		settings.controlledSwitches.each { sw ->
			if (sw?.id) {
				state.switchIdToLocationMap[sw.id.toString()] = parseDeviceLocation(sw)
			}
		}
	}
	
	normalizeSwitchRoomNames()
	groupSiblingSwitches()
	buildDeviceMaps() 
	buildModeSettingsMap()

	def buttonEventsToSubscribe = [
		settings.singleTapUpButtonEvent, settings.singleTapDownButtonEvent,
		settings.configButtonEvent, settings.doubleTapUpButtonEvent,
		settings.doubleTapDownButtonEvent, settings.holdUpButtonEvent,
		settings.releaseUpButtonEvent, settings.holdDownButtonEvent,
		settings.releaseDownButtonEvent
	].findAll { it }.unique()

	if (!controlledSwitches) {
		log.warn "No switches selected. Cannot subscribe to button events."
	} else {
		controlledSwitches.each { sw ->
			def switchIdStr = sw.id.toString()
			def sInfo = state.switchInfoMap[switchIdStr]

			if (sInfo?.type == "local") {
				log.info "Switch ${sw.displayName} is a Local Switch. Skipping button event subscriptions."
			} else if (sw.hasCapability("PushableButton")) {
				 buttonEventsToSubscribe.each { eventName ->
					subscribe(sw, eventName, buttonHandler)
				 }
			} else {
				log.warn "Switch ${sw.displayName} does not support PushableButton capability."
			}
			state.sceneIndex[switchIdStr] = state.sceneIndex[switchIdStr] ?: -1
			state.sceneMode[switchIdStr] = state.sceneMode[switchIdStr] ?: false
		}
	}

	// Subscribe to state changes of the first light in each area for non-local switches.
	if (state.firstAreaLightToSwitch) {
		state.firstAreaLightToSwitch.each { lightId, switchId ->
			def lightDevice = getDevicesById(lightId.toString(), settings.controlledLightsAndScenes)
			def sInfo = state.switchInfoMap[switchId.toString()] 

			if (lightDevice && sInfo?.type != "local") {
				if (lightDevice.hasCapability("SwitchLevel")) subscribe(lightDevice, "level", firstLightStateHandler)
				if (lightDevice.hasCapability("Switch")) subscribe(lightDevice, "switch", firstLightStateHandler)
				if (lightDevice.hasCapability("ColorTemperature")) subscribe(lightDevice, "colorTemperature", firstLightStateHandler)
			} else if (sInfo?.type == "local") {
				log.debug "Skipping firstLightStateHandler subscription for light ${lightDevice?.displayName} because its primary switch ${sInfo?.displayName} is local."
			} else if (!lightDevice) {
				log.warn "Could not find first area light device with ID ${lightId} to subscribe for state sync."
			}
		}
	}
	
	state.currentLocationMode = location.currentMode?.name?.toString()?.trim()
	log.info "Initial location mode tracked as: ${state.currentLocationMode ?: 'UNKNOWN'}"

	try {
		subscribe(location, "mode", modeChangeHandler)
	} catch (e) {
		log.error "Error subscribing to location mode changes: ${e.message}"
	}

	// Set initial LED brightness for all switches
	if (state.currentLocationMode) {
		Map initialModeLedSettings = getModeLedSettings(state.currentLocationMode) // Returns {on, off}
		settings.controlledSwitches?.each { sw ->
			if (sw) {
				Map effectiveBrightness = calcLEDLevel(sw, state.currentLocationMode, initialModeLedSettings.on, initialModeLedSettings.off)
				updateLEDs(sw, effectiveBrightness.on, effectiveBrightness.off)
			}
			pause(250)
		}
	} else {
		log.warn "Initial location mode not set. Setting LEDs to global defaults."
		settings.controlledSwitches?.each { sw ->
			if (sw) updateLEDs(sw, state.globalDefaultLedOnBrightness, state.globalDefaultLedOffBrightness)
			pause(250)
		}
	}

	updateSwitchControlSummary()
	log.info "Initialization complete."
}

private String normalizeDeviceName(String deviceName) {
	if (deviceName == null) return null
	return deviceName.replaceAll("[' ]", "'") // Standardize apostrophes
}

private String getParsingName(device) {
	if (!device) return ""
	String originalDisplayName = device.displayName?.trim() ?: ""
	String deviceRoomProp = null
	try {
		deviceRoomProp = device.roomName?.trim()
	} catch (MissingPropertyException e) { /* ignore */ }

	// Prepend roomName from property if not already part of displayName (for consistent parsing)
	if (deviceRoomProp && !deviceRoomProp.isEmpty() && !originalDisplayName.toLowerCase().startsWith(deviceRoomProp.toLowerCase())) {
		return "${deviceRoomProp} ${originalDisplayName}"
	}
	return originalDisplayName
}

/**
 * Parses device location: room, area, zone.
 * Zone: from [ZoneName] in device.name.
 * Room: from device.roomName or first word(s) of displayName.
 * Area: word after room, excluding "Switch", "All Switch", "Local Switch".
 */
def parseDeviceLocation(device) {
	String roomName = null
	String areaName = null
	String zoneName = null

	def zoneMatcher = device?.name =~ /\s*\[\s*(.*?)\s*\]\s*/ // Zone from device.name
	if (zoneMatcher?.find()) {
		zoneName = zoneMatcher[0][1]?.trim()
	}

	if (!device?.displayName) {
		 return [roomName: null, areaName: null, zoneName: zoneName, parsingName: ""]
	}

	String baseDisplayNameForParsing = getParsingName(device) 
	String deviceRoomProp = null
	try { deviceRoomProp = device.roomName?.trim() } catch (MissingPropertyException e) { /*ignore*/ }

	// Room from device.roomName property
	if (deviceRoomProp && !deviceRoomProp.isEmpty() && baseDisplayNameForParsing.toLowerCase().startsWith(deviceRoomProp.toLowerCase())) {
		roomName = deviceRoomProp
		String remainingAfterRoomProp = baseDisplayNameForParsing.substring(deviceRoomProp.length()).trim()
		if (remainingAfterRoomProp) {
			def remainingParts = remainingAfterRoomProp.tokenize()
			if (remainingParts) {
				String potentialArea = remainingParts.first()
				boolean isSwitchKeyword = potentialArea.equalsIgnoreCase("Switch") ||
										  (potentialArea.equalsIgnoreCase("All") && remainingParts.size() > 1 && remainingParts.getAt(1).equalsIgnoreCase("Switch")) ||
										  (potentialArea.equalsIgnoreCase("Local") && remainingParts.size() > 1 && remainingParts.getAt(1).equalsIgnoreCase("Switch"))
				
				if (!isSwitchKeyword && !potentialArea.equalsIgnoreCase("All")) { 
					areaName = potentialArea
				}
			}
		}
	}

	// Room from parsing baseDisplayNameForParsing
	if (!roomName && baseDisplayNameForParsing) {
		def parts = baseDisplayNameForParsing.tokenize()
		if (parts) {
			roomName = parts[0]
			int roomWords = 1
			// Check for common two-word room names
			if (parts.size() > 1 && (parts[1].equalsIgnoreCase("room") || parts[1].equalsIgnoreCase("rm") || parts[1].equalsIgnoreCase("bath") || parts[1].equalsIgnoreCase("bedroom"))) {
				String combinedRoom = "${parts[0]} ${parts[1]}"
				if (baseDisplayNameForParsing.toLowerCase().startsWith(combinedRoom.toLowerCase())) {
					roomName = combinedRoom
					roomWords = 2
				}
			}

			if (parts.size() > roomWords) {
				String potentialAreaWord = parts[roomWords]
				boolean isSwitchKeyword = potentialAreaWord.equalsIgnoreCase("Switch") ||
										  (potentialAreaWord.equalsIgnoreCase("All") && parts.size() > roomWords + 1 && parts.getAt(roomWords + 1).equalsIgnoreCase("Switch")) ||
										  (potentialAreaWord.equalsIgnoreCase("Local") && parts.size() > roomWords + 1 && parts.getAt(roomWords + 1).equalsIgnoreCase("Switch"))

				if (!isSwitchKeyword && !potentialAreaWord.equalsIgnoreCase("All")) { 
					areaName = potentialAreaWord
				}
			}
		}
	}
	return [roomName: roomName?.trim(), areaName: areaName?.trim(), zoneName: zoneName, parsingName: baseDisplayNameForParsing]
}


/**
 * Normalizes room names for switches with same base display name but different roomName properties.
 */
private void normalizeSwitchRoomNames() {
	if (settings.controlledSwitches == null || settings.controlledSwitches.isEmpty() || state.switchIdToLocationMap == null || state.switchIdToLocationMap.isEmpty()) {
		return
	}

	Map<String, List<String>> displayNameGroups = [:].withDefault { [] }
	settings.controlledSwitches.each { swDevice ->
		if (!swDevice?.displayName) return
		String baseDisplayName = swDevice.displayName.replaceAll(/\s*\(.*\)\s*$/, "").trim() // Ignore suffixes like (1)
		displayNameGroups[baseDisplayName] << swDevice.id.toString()
	}

	displayNameGroups.each { baseDisplayNameKey, switchIdsInGroup ->
		if (switchIdsInGroup.size() <= 1) return 

		List<Map> switchInfoForGroup = []
		switchIdsInGroup.each { id ->
			def loc = state.switchIdToLocationMap[id]
			def device = getDevicesById(id, settings.controlledSwitches)
			if (loc && device?.displayName) {
				 switchInfoForGroup << [id: id, displayName: device.displayName, parsedRoomName: loc.roomName, effectiveName: loc.parsingName]
			}
		}
		
		if (switchInfoForGroup.isEmpty()) return
		String firstRoomName = switchInfoForGroup.first().parsedRoomName
		if (switchInfoForGroup.every { it.parsedRoomName == firstRoomName }) return // All same, no normalization needed
		
		String authoritativeRoomName = null
		String authoritativeSwitchId = null

		// Prefer switch whose effectiveName (parsingName) starts with its parsedRoomName
		authoritativeSwitchId = switchInfoForGroup.find { info -> info.parsedRoomName && info.effectiveName?.toLowerCase()?.startsWith(info.parsedRoomName.toLowerCase()) }?.id
		if (authoritativeSwitchId) {
			authoritativeRoomName = switchInfoForGroup.find { it.id == authoritativeSwitchId }.parsedRoomName
		} else {
			// Fallback: prefer switch whose displayName starts with its parsedRoomName
			authoritativeSwitchId = switchInfoForGroup.find { info -> info.parsedRoomName && info.displayName?.toLowerCase()?.startsWith(info.parsedRoomName.toLowerCase()) }?.id
			if (authoritativeSwitchId) {
				authoritativeRoomName = switchInfoForGroup.find { it.id == authoritativeSwitchId }.parsedRoomName
			}
		}
		

		if (authoritativeRoomName) {
			def authSwitchDisplayName = getDevicesById(authoritativeSwitchId, settings.controlledSwitches)?.displayName ?: "ID ${authoritativeSwitchId}"
			log.info "Normalizing room names for switches like '${baseDisplayNameKey}' to '${authoritativeRoomName}' (from '${authSwitchDisplayName}')."
			switchIdsInGroup.each { idToUpdate ->
				if (idToUpdate != authoritativeSwitchId) {
					def currentLocMap = state.switchIdToLocationMap[idToUpdate]
					if (currentLocMap && currentLocMap.roomName != authoritativeRoomName) {
						Map newLoc = new HashMap(currentLocMap)
						newLoc.roomName = authoritativeRoomName 
						state.switchIdToLocationMap[idToUpdate] = newLoc 
						log.debug "Updated roomName for switch ID ${idToUpdate} to '${authoritativeRoomName}'"
					}
				}
			}
		} else {
			log.warn "For switches like '${baseDisplayNameKey}', multiple roomNames exist ('${switchInfoForGroup.collect{it.parsedRoomName}.unique().join(', ')}') but no authoritative one found. Room names not normalized for this group."
		}
	}
}

/**
 * Groups "sibling" master switches for the same room (e.g., "Kitchen Switch", "Kitchen Switch (Pantry)").
 */
private void groupSiblingSwitches() {
	state.siblingSwitchGroupsBySwitchId = [:]
	if (settings.controlledSwitches == null || settings.controlledSwitches.isEmpty() || state.switchIdToLocationMap == null || state.switchIdToLocationMap.isEmpty()) {
		return
	}

	Map<String, List<String>> potentialGroups = [:].withDefault { [] }

	settings.controlledSwitches.each { swDevice ->
		def switchId = swDevice.id.toString()
		def loc = state.switchIdToLocationMap[switchId]
		if (!loc?.roomName || !swDevice?.displayName) return

		String expectedBaseMasterSwitchName = "${loc.roomName} Switch"
		// Group if displayName is "RoomName Switch" or "RoomName Switch (suffix)"
		if (swDevice.displayName.equalsIgnoreCase(expectedBaseMasterSwitchName) || 
			swDevice.displayName.toLowerCase().startsWith(expectedBaseMasterSwitchName.toLowerCase() + " (")) {
			String groupKey = "${loc.roomName}::${expectedBaseMasterSwitchName}" // Group by room and base name
			potentialGroups[groupKey] << switchId
		}
	}

	potentialGroups.each { groupKey, memberIds ->
		if (memberIds.size() > 1) { 
			def memberNames = memberIds.collect { id -> getDevicesById(id, settings.controlledSwitches)?.displayName ?: id }.join(', ')
			log.info "Identified sibling switch group for key '${groupKey}': ${memberNames}"
			memberIds.each { id -> state.siblingSwitchGroupsBySwitchId[id] = memberIds }
		}
	}
}

/**
 * Builds maps of switches to controlled lights/scenes; determines switch type and stem.
 */
def buildDeviceMaps() {
	log.info "Starting buildDeviceMaps..."
	state.switchRoomLights = [:]  
	state.switchAreaLights = [:]  
	state.switchZoneLights = [:]  
	state.switchScenes = [:]      
	state.firstAreaLightToSwitch = [:] 
	state.sortedSwitchSceneIds = [:]  
	state.switchDimmableAreaLightIds = [:] 
	state.switchInfoMap = [:]      

	Map lightSceneLocations = [:] // Pre-parse light/scene locations
	settings.controlledLightsAndScenes?.each { dev ->
		if (dev?.id) lightSceneLocations[dev.id.toString()] = parseDeviceLocation(dev)
	}

	// Step 1: Populate switchInfoMap (type, stem, loc, etc.)
	settings.controlledSwitches?.each { sw ->
		if (!sw?.id) return

		def switchId = sw.id.toString()
		def switchLoc = state.switchIdToLocationMap[switchId]
		String originalDisplayName = sw.displayName?.trim() ?: ""
		String type, stem
		boolean isLocal = false

		if (originalDisplayName.toLowerCase().contains("local switch") || originalDisplayName.toLowerCase().endsWith("(s)")) {
			type = "local"; isLocal = true
			stem = originalDisplayName.toLowerCase().contains("local switch") ?
				originalDisplayName.substring(0, originalDisplayName.toLowerCase().indexOf("local switch")).trim() :
				originalDisplayName.replaceFirst(/(?i)\s*\([Ss]\)\s*$/, "").trim()
			log.debug "Switch '${originalDisplayName}' (ID: ${switchId}) -> LOCAL. Stem: '${stem}'."
		} else {
			isLocal = false
			int switchKeywordIdx = originalDisplayName.toLowerCase().indexOf(" switch")
			stem = (switchKeywordIdx != -1) ? originalDisplayName.substring(0, switchKeywordIdx).trim() : originalDisplayName
			
			if (originalDisplayName.toLowerCase().contains("all switch")) {
				type = "all"
				String lowerStem = stem.toLowerCase()
				if (lowerStem.endsWith(" all")) {
					stem = stem.substring(0, stem.length() - " all".length()).trim()
				}
				log.debug "Switch '${originalDisplayName}' (ID: ${switchId}) -> ALL. Stem: '${stem}'."
			} else if (switchLoc?.roomName && !switchLoc.roomName.isEmpty() && stem.equalsIgnoreCase(switchLoc.roomName.trim())) {
				type = "master"
				log.debug "Switch '${originalDisplayName}' (ID: ${switchId}) -> MASTER. Stem: '${stem}' (Room: '${switchLoc.roomName}')."
			} else {
				type = "regular"
				log.debug "Switch '${originalDisplayName}' (ID: ${switchId}) -> REGULAR. Stem: '${stem}'."
			}
		}
		state.switchInfoMap[switchId] = [type: type, stem: stem, loc: switchLoc, displayName: originalDisplayName, isLocal: isLocal]
		state.switchAreaLights[switchId] = []; state.switchDimmableAreaLightIds[switchId] = []
		state.switchScenes[switchId] = []; state.sortedSwitchSceneIds[switchId] = []
	}

	// Step 2: Populate room and zone lights for all switches
	settings.controlledSwitches?.each { sw ->
		if (!sw?.id) return
		def switchId = sw.id.toString(); def sInfo = state.switchInfoMap[switchId]
		if (!sInfo?.loc) {
			log.warn "buildDeviceMaps: No location for switch ID ${switchId} ('${sw.displayName}') for room/zone lights."
			state.switchRoomLights[switchId] = []; state.switchZoneLights[switchId] = []
			return
		}
		state.switchRoomLights[switchId] = settings.controlledLightsAndScenes?.findAll { light ->
			!isScene(light) && light?.id && lightSceneLocations[light.id.toString()]?.roomName && sInfo.loc?.roomName && 
			normalizeDeviceName(lightSceneLocations[light.id.toString()].roomName).equalsIgnoreCase(normalizeDeviceName(sInfo.loc.roomName))
		}?.collect { it.id.toString() } ?: []

		state.switchZoneLights[switchId] = sInfo.loc?.zoneName ? 
			settings.controlledLightsAndScenes?.findAll { light ->
				!isScene(light) && light?.id && lightSceneLocations[light.id.toString()]?.zoneName &&
				normalizeDeviceName(lightSceneLocations[light.id.toString()].zoneName).equalsIgnoreCase(normalizeDeviceName(sInfo.loc.zoneName))
			}?.collect { it.id.toString() }?.unique() ?: [] : []
	}

	// Step 3: Assign Area Lights to Regular and All switches
	Map<String, String> regularlyClaimedLightToSwitchMap = [:] 
	state.switchInfoMap.each { switchId, sInfo ->
		if (sInfo.type == "local" || !(sInfo.type == "regular" || sInfo.type == "all")) return
		String stem = sInfo.stem
		if (!stem || stem.isEmpty()) {
			log.debug "Switch ${sInfo.displayName} (${sInfo.type}) has no stem for area lights."
			return
		}
		List<String> currentAreaLightIds = settings.controlledLightsAndScenes?.findAll { targetDev ->
			!isScene(targetDev) && targetDev?.id && 
			normalizeDeviceName(lightSceneLocations[targetDev.id.toString()]?.parsingName)?.toLowerCase()?.startsWith(normalizeDeviceName(stem).toLowerCase())
		}?.collect { it.id.toString() }?.unique() ?: []
		
		state.switchAreaLights[switchId] = currentAreaLightIds
		currentAreaLightIds.each { lightId -> regularlyClaimedLightToSwitchMap[lightId] = switchId }

		if (!currentAreaLightIds.isEmpty()) {
			def areaLightObjects = getDevicesById(currentAreaLightIds, settings.controlledLightsAndScenes)
			state.switchDimmableAreaLightIds[switchId] = areaLightObjects?.findAll { it.hasCapability("SwitchLevel") }?.collect { it.id.toString() } ?: []
			def sortedAreaLightObjects = areaLightObjects?.sort { it.displayName }
			if (sortedAreaLightObjects?.first()) state.firstAreaLightToSwitch[sortedAreaLightObjects.first().id.toString()] = switchId
		}
	}

	// Step 4: Assign Area Lights to Master switches
	state.switchInfoMap.each { switchId, sInfo ->
		if (sInfo.type != "master") return 
		String masterSwitchRoom = sInfo.stem 
		if (!masterSwitchRoom || masterSwitchRoom.isEmpty()) {
			log.warn "Master switch ${sInfo.displayName} has no room stem for master lights."
			return
		}
		List<String> siblingsOfThisMaster = state.siblingSwitchGroupsBySwitchId[switchId] ?: [switchId]
		List<String> masterLightIds = settings.controlledLightsAndScenes?.findAll { targetDev ->
			!isScene(targetDev) && targetDev?.id &&
			normalizeDeviceName(lightSceneLocations[targetDev.id.toString()]?.roomName)?.equalsIgnoreCase(normalizeDeviceName(masterSwitchRoom)) &&
			(regularlyClaimedLightToSwitchMap[targetDev.id.toString()] == null || siblingsOfThisMaster.contains(regularlyClaimedLightToSwitchMap[targetDev.id.toString()]))
		}?.collect { it.id.toString() }?.unique() ?: []
		
		state.switchAreaLights[switchId] = masterLightIds
		if (!masterLightIds.isEmpty()) {
			 def masterLightObjects = getDevicesById(masterLightIds, settings.controlledLightsAndScenes)
			 state.switchDimmableAreaLightIds[switchId] = masterLightObjects?.findAll { it.hasCapability("SwitchLevel") }?.collect { it.id.toString() } ?: []
			 def sortedMasterLightObjects = masterLightObjects?.sort { it.displayName }
			 if (sortedMasterLightObjects?.first() && !state.firstAreaLightToSwitch.containsKey(sortedMasterLightObjects.first().id.toString())) {
				state.firstAreaLightToSwitch[sortedMasterLightObjects.first().id.toString()] = switchId
			 }
		}
	}

	// Step 5: Assign Scenes by stem (non-local switches)
	state.switchInfoMap.each { switchId, sInfo ->
		if (sInfo.type == "local") return
		String stem = sInfo.stem
		if ((!stem || stem.isEmpty()) && sInfo.type != "master") {
			log.debug "Switch ${sInfo.displayName} (${sInfo.type}) no stem for scene matching."
			state.switchScenes[switchId] = []; state.sortedSwitchSceneIds[switchId] = []
			return
		}
		List<String> currentSceneIds = settings.controlledLightsAndScenes?.findAll { targetDev ->
			isScene(targetDev) && targetDev?.id && stem && // Ensure stem is not null for matching
			normalizeDeviceName(lightSceneLocations[targetDev.id.toString()]?.parsingName)?.toLowerCase()?.startsWith(normalizeDeviceName(stem).toLowerCase())
		}?.collect { it.id.toString() }?.unique() ?: []
		
		state.switchScenes[switchId] = currentSceneIds
		state.sortedSwitchSceneIds[switchId] = currentSceneIds.isEmpty() ? [] : 
			getDevicesById(currentSceneIds, settings.controlledLightsAndScenes)?.sort { it.displayName }?.collect { it.id.toString() } ?: []
	}

	// Step 6: Assign Zone Scenes if no primary scenes (non-local switches)
	state.switchInfoMap.each { switchId, sInfo ->
		if (sInfo.type == "local" || !(sInfo.loc?.zoneName && (state.sortedSwitchSceneIds[switchId]?.isEmpty() ?: true)) ) return

		log.info "Switch '${sInfo.displayName}' zone '${sInfo.loc.zoneName}', no primary scenes. Checking zone scenes."
		String zoneNamePrefix = sInfo.loc.zoneName
		List<String> zoneSceneIds = settings.controlledLightsAndScenes?.findAll { targetDev ->
			isScene(targetDev) && targetDev?.id &&
			normalizeDeviceName(lightSceneLocations[targetDev.id.toString()]?.parsingName)?.toLowerCase()?.startsWith(normalizeDeviceName(zoneNamePrefix).toLowerCase())
		}?.collect { it.id.toString() }?.unique() ?: []

		if (!zoneSceneIds.isEmpty()) {
			state.switchScenes[switchId].addAll(zoneSceneIds)
			state.switchScenes[switchId] = state.switchScenes[switchId].unique()
			state.sortedSwitchSceneIds[switchId] = getDevicesById(state.switchScenes[switchId], settings.controlledLightsAndScenes)
				?.sort { it.displayName }?.collect { it.id.toString() } ?: []
			log.info "Associated ${state.sortedSwitchSceneIds[switchId].size()} zone scenes with '${sInfo.displayName}'."
		}
	}
	log.info "buildDeviceMaps finished."
}


def buildModeSettingsMap() {
	def newModeSettings = [:]
	// Ensure global defaults are properly initialized from settings or fallback values
	state.globalDefaultLevel = (settings.globalDefaultLevel instanceof Number) ? settings.globalDefaultLevel : 100
	state.globalDefaultColorTemperature = (settings.globalDefaultColorTemperature instanceof Number) ? settings.globalDefaultColorTemperature : 2700
	state.globalDefaultLedOnBrightness = (settings.globalDefaultLedOnBrightness instanceof Number) ? settings.globalDefaultLedOnBrightness : 30
	state.globalDefaultLedOffBrightness = (settings.globalDefaultLedOffBrightness instanceof Number) ? settings.globalDefaultLedOffBrightness : 7

	location.modes?.each { mode ->
		String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()

		def rawLevel = settings."level_${safeModeName}"
		def validatedLevel = (rawLevel instanceof Number && rawLevel >= 1 && rawLevel <= 100) ? rawLevel : null
		def rawCt = settings."ct_${safeModeName}"
		def validatedCt = (rawCt instanceof Number && rawCt >= 2000 && rawCt <= 9000) ? rawCt : null
		boolean enableCtSetting = (settings."enableCt_${safeModeName}" != null) ? settings."enableCt_${safeModeName}" : true
		def rawLedOn = settings."ledOnBrightness_${safeModeName}"
		def validatedLedOn = (rawLedOn instanceof Number && rawLedOn >= 0 && rawLedOn <= 100) ? rawLedOn : null
		def rawLedOff = settings."ledOffBrightness_${safeModeName}"
		def validatedLedOff = (rawLedOff instanceof Number && rawLedOff >= 0 && rawLedOff <= 100) ? rawLedOff : null
		// Note: The new setting "ledOffZone_${safeModeName}" is read directly in calcLEDLevel, not stored in state.modeSettingsMap

		newModeSettings[mode.name] = [
			level: validatedLevel,
			ct: validatedCt,
			enableCt: enableCtSetting,
			ledOn: validatedLedOn,
			ledOff: validatedLedOff
		]
	}
	state.modeSettingsMap = newModeSettings
}

def modeChangeHandler(evt) {
	String previousModeName = state.currentLocationMode
	String newModeName = evt.value?.toString()?.trim()

	log.info "Mode change: from '${previousModeName ?: 'UNINITIALIZED'}' to '${newModeName ?: 'INVALID'}'"

	if (!newModeName || newModeName == previousModeName) {
		log.info newModeName ? "New mode same as current. No action." : "New mode invalid. No action."
		return
	}
	state.currentLocationMode = newModeName 
	log.info "Tracked mode updated to '${state.currentLocationMode}'."

	Map newModeLedSettings = getModeLedSettings(newModeName) // For LEDs (base values)
	settings.controlledSwitches?.each { sw -> 
		if (sw) {
			Map effectiveBrightness = calcLEDLevel(sw, newModeName, newModeLedSettings.on, newModeLedSettings.off)
			updateLEDs(sw, effectiveBrightness.on, effectiveBrightness.off)
		}
	}

	if (previousModeName == null) {
		log.info "No previous mode (first change). Skipping light adjustments."
		return
	}

	log.info "Processing light adjustments for mode change from '${previousModeName}' to '${newModeName}'."
	Map prevModeLightSettings = getModeSettings(previousModeName) 
	Map newModeLightSettings = getModeSettings(newModeName)		 

	settings.controlledLightsAndScenes.each { lightDevice ->
		if (isScene(lightDevice) || !lightDevice.hasCapability("Switch") || lightDevice.currentValue('switch') != 'on') return

		boolean levelMatchedPrev = lightDevice.hasCapability("SwitchLevel") ? 
								 (lightDevice.currentValue('level') as Integer == prevModeLightSettings.level) :
								 (prevModeLightSettings.level > 0)
		
		boolean ctMatchedPrev = !prevModeLightSettings.enableCt || !lightDevice.hasCapability("ColorTemperature") ? true :
								(lightDevice.currentValue('colorTemperature') != null && 
								 Math.abs((lightDevice.currentValue('colorTemperature') as Integer) - (prevModeLightSettings.ct as Integer)) <= 50)

		if (levelMatchedPrev && ctMatchedPrev) {
			log.info "Light ${lightDevice.displayName} (ON) matched prev mode '${previousModeName}'. Adjusting to new mode '${newModeName}'."
			try {
				if (lightDevice.hasCapability("SwitchLevel")) {
					lightDevice.setLevel(newModeLightSettings.level)
				} else if (newModeLightSettings.level > 0 && lightDevice.currentValue('switch') != 'on') {
					lightDevice.on() // Should already be on, but defensive.
				}
				if (newModeLightSettings.enableCt && lightDevice.hasCapability("ColorTemperature") && newModeLightSettings.ct != null) {
					lightDevice.setColorTemperature(newModeLightSettings.ct)
				}
			} catch (e) {
				log.error "Error adjusting light ${lightDevice.displayName} to new mode: ${e.message}"
			}
		}
	}
	log.info "Mode change light adjustments processed."
}


/**
 * Updates LED indicator brightness on a switch device using setParameter.
 */
private void updateLEDs(switchDevice, Integer onBrightness, Integer offBrightness) {
	if (!switchDevice || !switchDevice.hasCommand("setParameter")) return

	try {
		// Parameters 97 (ON LED) & 98 (OFF LED), Size 1 (byte) are common (e.g. Inovelli)
		log.debug "Setting ${switchDevice.displayName} LED ON to ${onBrightness} (P97), OFF to ${offBrightness} (P98)"
		switchDevice.setParameter(97, onBrightness, 8) 
		switchDevice.setParameter(98, offBrightness, 8)
	} catch (e) {
		log.error "Error updating LEDs for ${switchDevice.displayName}: ${e.message}"
	}
}

/**
 * Retrieves LED brightness settings for a mode, falling back to global defaults.
 * This provides the base settings before any zone-specific overrides.
 */
private Map getModeLedSettings(String targetModeName) {
	String effectiveModeName = targetModeName ?: state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	Map modeConfig = state.modeSettingsMap[effectiveModeName]

	Integer resolvedLedOn = modeConfig?.ledOn != null ? modeConfig.ledOn : state.globalDefaultLedOnBrightness
	Integer resolvedLedOff = modeConfig?.ledOff != null ? modeConfig.ledOff : state.globalDefaultLedOffBrightness
	
	return [on: resolvedLedOn, off: resolvedLedOff]
}

/**
 * NEW HELPER FUNCTION
 * Determines the effective LED brightness for a switch, considering Sleep mode zone overrides.
 */
private Map calcLEDLevel(switchDevice, String currentModeName, Integer baseModeLedOn, Integer baseModeLedOff) {
	// Construct the setting name for the sleep mode's LED-off zone.
	// Assumes "sleep" mode name is consistent. If mode names can vary wildly for "sleep", this might need adjustment.
	String sleepSafeModeName = "sleep".replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase() // Should resolve to "sleep"
	String zoneToTurnOffLedsInSleep = settings."ledOffZone_${sleepSafeModeName}"

	if (currentModeName?.toLowerCase() == "sleep" && zoneToTurnOffLedsInSleep && !zoneToTurnOffLedsInSleep.trim().isEmpty()) {
		def switchId = switchDevice.id.toString()
		// Ensure state.switchIdToLocationMap is populated and accessible
		if (state.switchIdToLocationMap) {
			def switchLoc = state.switchIdToLocationMap[switchId]
			String switchZone = switchLoc?.zoneName?.trim()

			if (switchZone && switchZone.equalsIgnoreCase(zoneToTurnOffLedsInSleep.trim())) {
				log.debug "Sleep mode: Turning off LEDs for switch ${switchDevice.displayName} in zone '${switchZone}' as per setting for zone '${zoneToTurnOffLedsInSleep.trim()}'."
				return [on: 0, off: 0] // Override to turn LEDs off
			}
		} else {
			log.warn "calcLEDLevel: state.switchIdToLocationMap not available. Cannot apply Sleep mode zone LED override."
		}
	}
	
	// Default case: return the base mode-specific or global default LED brightness
	return [on: baseModeLedOn, off: baseModeLedOff]
}


def firstLightStateHandler(evt) {
	def triggeringLight = evt.device; def lightId = triggeringLight.id.toString()
	def eventName = evt.name; def eventValue = evt.value

	def primarySwitchId = state.firstAreaLightToSwitch[lightId]
	if (!primarySwitchId) return

	def primarySwitchInfo = state.switchInfoMap[primarySwitchId.toString()]
	if (primarySwitchInfo?.type == "local") { // No sync for local switches
		log.debug "Primary switch ${primarySwitchInfo.displayName} for light ${triggeringLight.displayName} is local. Skipping state sync."
		return
	}
	
	List<String> switchIdsToUpdate = state.siblingSwitchGroupsBySwitchId[primarySwitchId] ?: [primarySwitchId]
	
	if (eventName == "switch" || eventName == "level") { 
		log.info "Syncing switch(es) for ${triggeringLight.displayName} (${eventName}=${eventValue}). To update: ${switchIdsToUpdate}"
	}

	switchIdsToUpdate.each { switchIdToSync ->
		def targetSwitch = getDevicesById(switchIdToSync, settings.controlledSwitches)
		if (!targetSwitch) {
			log.warn "Could not find switch ID ${switchIdToSync} for light state sync."
			return // continue to next switchIdToSync
		}

		if (eventName == "level") {
			def newLevel = eventValue as Integer
			if (targetSwitch.hasCommand("setLevel") && targetSwitch.hasAttribute("level") && (targetSwitch.currentValue('level') as Integer) != newLevel) {
				try { targetSwitch.setLevel(newLevel) } catch (e) { log.error "Error setting level on ${targetSwitch.displayName}: ${e.message}" }
			}
		} else if (eventName == "switch") {
			def newState = eventValue 
			if (newState == "on" && targetSwitch.hasCommand("on") && targetSwitch.hasAttribute("switch") && targetSwitch.currentValue('switch') != "on") {
				 try { targetSwitch.on() } catch (e) { log.error "Error turning ON ${targetSwitch.displayName}: ${e.message}" }
			} else if (newState == "off" && targetSwitch.hasCommand("off") && targetSwitch.hasAttribute("switch") && targetSwitch.currentValue('switch') != "off") {
				 try { targetSwitch.off() } catch (e) { log.error "Error turning OFF ${targetSwitch.displayName}: ${e.message}" }
			}
		}
	}
}

def buttonHandler(evt) {
	def triggeringSwitch = evt.device; def switchId = triggeringSwitch.id.toString()
	def buttonNumber = evt.value.toInteger(); def buttonEvent = evt.name 
	
	def switchInfo = state.switchInfoMap[switchId]
	if (switchInfo?.type == "local") { // Local switches ignored for button actions
		log.info "Button ${buttonNumber} (${buttonEvent}) on Local Switch ${triggeringSwitch.displayName}. Ignored."
		return
	}

	log.info "Button ${buttonNumber} (${buttonEvent}) on ${triggeringSwitch.displayName} (Type: ${switchInfo?.type ?: 'Unknown'})"
	cancelSceneModeTimeout(triggeringSwitch)

	// Scene Mode Toggle
	if (buttonNumber == (settings.configButtonNumber as Integer) && buttonEvent == settings.configButtonEvent) {
		if (state.sceneMode[switchId]) {
			exitSceneMode([switchId: switchId]) 
		} else if (state.sortedSwitchSceneIds[switchId]?.any()) { // Check if scenes exist
			enterSceneMode(triggeringSwitch)
		} else {
			log.info "Config button on ${triggeringSwitch.displayName}, but no scenes. Scene Mode not activated."
		}
		return 
	}

	if (state.sceneMode[switchId]) {
		handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	} else {
		handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	}
}


private boolean cycleScene(triggeringSwitch, String direction = "next") {
	def switchId = triggeringSwitch.id.toString()
	def sortedSceneIds = state.sortedSwitchSceneIds[switchId]
	if (!sortedSceneIds?.any()) { // More Groovy way to check if list is not empty
		log.warn "Cannot cycle scenes for ${triggeringSwitch.displayName}: No scenes."
		return false
	}

	def roomScenes = getDevicesById(sortedSceneIds, settings.controlledLightsAndScenes)
	if (!roomScenes?.any()) {
		log.warn "Cannot cycle scenes for ${triggeringSwitch.displayName}: Scene devices not found."
		return false
	}

	def sceneCount = roomScenes.size()
	def currentSceneIndex = (state.sceneIndex[switchId] instanceof Integer) ? state.sceneIndex[switchId] : -1
	def newIndex = (direction == "previous") ? (currentSceneIndex - 1 + sceneCount) % sceneCount : (currentSceneIndex + 1) % sceneCount

	activateScene(roomScenes[newIndex])
	state.sceneIndex[switchId] = newIndex 
	log.info "Activated scene '${roomScenes[newIndex]?.displayName}' (${newIndex + 1}/${sceneCount}) for ${triggeringSwitch.displayName}"
	return true
}

private void handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	boolean sceneActionTaken = false
	if (buttonNumber == 1 && buttonEvent == "pushed") sceneActionTaken = cycleScene(triggeringSwitch, "next")
	else if (buttonNumber == 1 && buttonEvent == "held") sceneActionTaken = cycleScene(triggeringSwitch, "previous")
	else if (!(buttonNumber == 1 && buttonEvent == "released")) { // Any other button (not release of B1) exits scene mode
		log.info "Exiting scene mode for ${triggeringSwitch.displayName} due to button ${buttonNumber} ${buttonEvent}."
		exitSceneMode([switchId: triggeringSwitch.id.toString()])
	}
	if (sceneActionTaken) scheduleSceneModeTimeout(triggeringSwitch)
}

private void handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	def switchId = triggeringSwitch.id.toString()

	def switchInfo = state.switchInfoMap[switchId]
	boolean isThisSwitchSceneOnly = isSceneOnlySwitch(switchId)

	List<String> roomOrZoneLightIds = []
	if (switchInfo?.loc?.roomName) {
		roomOrZoneLightIds = state.switchRoomLights[switchId] ?: []
	} else if (switchInfo?.loc?.zoneName) {
		roomOrZoneLightIds = state.switchZoneLights[switchId] ?: []
	}
	def allRoomOrZoneLights = getDevicesById(roomOrZoneLightIds, settings.controlledLightsAndScenes) ?: []

	def dimmableRoomOrZoneLights = allRoomOrZoneLights?.findAll { it.hasCapability("SwitchLevel") } ?: []

	if (isThisSwitchSceneOnly) {
		log.info "${triggeringSwitch.displayName} is scene-only. Handling specific actions including dimming."

		if (buttonNumber == (settings.singleTapUpButtonNumber as Integer) && buttonEvent == settings.singleTapUpButtonEvent) cycleScene(triggeringSwitch, "next")
		else if (buttonNumber == (settings.singleTapDownButtonNumber as Integer) && buttonEvent == settings.singleTapDownButtonEvent) handleSceneOnlyOff(triggeringSwitch)
		else if (buttonNumber == (settings.holdUpButtonNumber as Integer) && buttonEvent == settings.holdUpButtonEvent) handleDimStart(triggeringSwitch, dimmableRoomOrZoneLights, "up")
		else if (buttonNumber == (settings.releaseUpButtonNumber as Integer) && buttonEvent == settings.releaseUpButtonEvent) handleDimStop(triggeringSwitch, dimmableRoomOrZoneLights)
		else if (buttonNumber == (settings.holdDownButtonNumber as Integer) && buttonEvent == settings.holdDownButtonEvent) handleDimStart(triggeringSwitch, dimmableRoomOrZoneLights, "down")
		else if (buttonNumber == (settings.releaseDownButtonNumber as Integer) && buttonEvent == settings.releaseDownButtonEvent) handleDimStop(triggeringSwitch, dimmableRoomOrZoneLights)
		return // Exit the function after handling scene-only switch actions
	}

	def areaLights = getDevicesById(state.switchAreaLights[switchId], settings.controlledLightsAndScenes) ?: []
	def dimmableAreaLights = getDevicesById(state.switchDimmableAreaLightIds[switchId], settings.controlledLightsAndScenes) ?: []

	if (buttonNumber == (settings.singleTapUpButtonNumber as Integer) && buttonEvent == settings.singleTapUpButtonEvent) handleAreaOn(triggeringSwitch, areaLights)
	else if (buttonNumber == (settings.singleTapDownButtonNumber as Integer) && buttonEvent == settings.singleTapDownButtonEvent) handleAreaOff(triggeringSwitch, areaLights)
	else if (buttonNumber == (settings.holdUpButtonNumber as Integer) && buttonEvent == settings.holdUpButtonEvent) handleDimStart(triggeringSwitch, dimmableAreaLights, "up")
	else if (buttonNumber == (settings.releaseUpButtonNumber as Integer) && buttonEvent == settings.releaseUpButtonEvent) handleDimStop(triggeringSwitch, dimmableAreaLights)
	else if (buttonNumber == (settings.holdDownButtonNumber as Integer) && buttonEvent == settings.holdDownButtonEvent) handleDimStart(triggeringSwitch, dimmableAreaLights, "down")
	else if (buttonNumber == (settings.releaseDownButtonNumber as Integer) && buttonEvent == settings.releaseDownButtonEvent) handleDimStop(triggeringSwitch, dimmableAreaLights)
	else if (buttonNumber == (settings.doubleTapUpButtonNumber as Integer) && buttonEvent == settings.doubleTapUpButtonEvent) handleZoneOn(triggeringSwitch)
	else if (buttonNumber == (settings.doubleTapDownButtonNumber as Integer) && buttonEvent == settings.doubleTapDownButtonEvent) handleZoneOff(triggeringSwitch)
}


private void handleAreaOn(triggeringSwitch, areaLights) {
	if (!areaLights?.any()) {
		log.warn "No area lights for ${triggeringSwitch.displayName}. Area On skipped."
		return
	}

	Map effectiveTargetSettings
	String modeUsedForSettings = state.currentLocationMode ?: "current (unknown)"
	
	def firstLight = areaLights.first() 
	if (firstLight?.hasAttribute('switch') && firstLight.currentValue('switch') == 'on') {
		Map currentModeSettings = getModeSettings() 
		Integer currentLightLevel = firstLight.hasAttribute('level') ? (firstLight.currentValue('level') as Integer) : null
		boolean levelMatchesCurrentMode = (currentLightLevel == currentModeSettings.level)

		if (levelMatchesCurrentMode) { // Light on AND matches current mode level -> set to global default level
			log.info "First light ${firstLight.displayName} ON & matches mode. Setting area to global default brightness; CT unchanged."
			effectiveTargetSettings = [level: state.globalDefaultLevel, ct: null, enableCt: false]
			modeUsedForSettings = "Global Default Level (override)"
		} else { // Light on but differs from mode -> set to current mode settings
			effectiveTargetSettings = currentModeSettings
			log.info "First light ${firstLight.displayName} ON but differs. Setting area to current mode settings."
		}
	} else { // Light off -> set to current mode settings
		effectiveTargetSettings = getModeSettings()
		log.info "First light ${firstLight?.displayName ?: 'N/A'} OFF. Setting area to current mode settings."
	}

	def targetLevel = effectiveTargetSettings.level
	def targetCt = effectiveTargetSettings.ct
	def shouldSetCt = effectiveTargetSettings.enableCt 

	log.info "handleAreaOn for ${triggeringSwitch.displayName} (${modeUsedForSettings}): ON ${areaLights.size()} lights. Lvl:${targetLevel}%, CT:${targetCt}K (SetCT:${shouldSetCt})"

	areaLights.each { light ->
		try {
			boolean powerCmdSent = false
			if (light.hasCapability("SwitchLevel")) { light.setLevel(targetLevel); powerCmdSent = true } 
			else if (light.hasCommand("on")) { light.on(); powerCmdSent = true }
			
			if (powerCmdSent && shouldSetCt && light.hasCapability("ColorTemperature") && targetCt != null) {
				light.setColorTemperature(targetCt)
			}
		} catch (e) { log.error "Error controlling light ${light.displayName} in handleAreaOn: ${e.message}"}
	}
}

private void handleZoneOn(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	def sInfo = state.switchInfoMap[switchId]
	if (!sInfo?.loc) {
		log.warn "No switch/location info for ${triggeringSwitch.displayName} in handleZoneOn."
		return
	}

	Map targetSettings = getModeSettings()
	List<String> lightsToControlIds = sInfo.loc.zoneName ? (state.switchZoneLights[switchId] ?: []) : 
									  sInfo.loc.roomName ? (state.switchRoomLights[switchId] ?: []) : []
	String controlScope = sInfo.loc.zoneName ? "Zone '${sInfo.loc.zoneName}'" : 
						  sInfo.loc.roomName ? "Room '${sInfo.loc.roomName}'" : "Unknown Scope"

	if (!lightsToControlIds?.any()) {
		log.info "No lights for ${controlScope} for ${triggeringSwitch.displayName}. Zone/Room On skipped."
		return
	}
	def lightsToControl = getDevicesById(lightsToControlIds, settings.controlledLightsAndScenes)

	if (lightsToControl?.any()) {
		log.info "Zone/Room On by ${triggeringSwitch.displayName}: ${lightsToControl.size()} lights for ${controlScope} to Lvl:${targetSettings.level}%, CT:${targetSettings.ct}K (SetCT:${targetSettings.enableCt})"
		lightsToControl.each { light ->
			try {
				boolean powerCmdSent = false
				if (light.hasCapability("SwitchLevel")) { light.setLevel(targetSettings.level); powerCmdSent = true } 
				else if (light.hasCommand("on")) { light.on(); powerCmdSent = true }
				if (powerCmdSent && targetSettings.enableCt && light.hasCapability("ColorTemperature") && targetSettings.ct != null) {
					light.setColorTemperature(targetSettings.ct)
				}
			} catch (e) { log.error "Error in Zone/Room On for ${light.displayName}: ${e.message}" }
		}
	}
}


private void handleAreaOff(triggeringSwitch, areaLights) {
	if (areaLights?.any()) {
		log.info "handleAreaOff for ${triggeringSwitch.displayName}: Turning OFF ${areaLights.size()} area light(s)."
		 areaLights.each { light ->
			try { if (light.hasCommand("off")) light.off() }
			catch (e) { log.error "Error turning light OFF for ${light.displayName}: ${e.message}"}
		 }
	} else {
		log.info "handleAreaOff for ${triggeringSwitch.displayName}: No area lights to turn off."
	}
}

private void handleSceneOnlyOff(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	log.info "Scene-only switch ${triggeringSwitch.displayName} tap down: turning off room/zone lights."

	def sInfo = state.switchInfoMap[switchId]; def switchLocation = sInfo?.loc
	List<String> lightsToTurnOffIds = []
	String scope = ""

	if (switchLocation?.roomName) { 
		lightsToTurnOffIds = state.switchRoomLights[switchId] ?: []
		scope = "room '${switchLocation.roomName}'"
	} else if (switchLocation?.zoneName) { 
		lightsToTurnOffIds = state.switchZoneLights[switchId] ?: []
		scope = "zone '${switchLocation.zoneName}'"
	}

	if (lightsToTurnOffIds?.any()) {
		def lightsToTurnOff = getDevicesById(lightsToTurnOffIds, settings.controlledLightsAndScenes)
		if (lightsToTurnOff?.any()) {
			log.info "Turning off ${lightsToTurnOff.size()} lights in ${scope} for scene-only switch ${triggeringSwitch.displayName}."
			lightsToTurnOff.each { light ->
				try { if (light.hasCommand("off")) light.off() }
				catch (e) { log.error "Error turning off light ${light.displayName} for scene-only switch: ${e.message}"}
			}
		} else {
			log.warn "No light devices for ${scope} for scene-only switch ${triggeringSwitch.displayName}."
		}
	} else {
		log.warn "No room/zone lights for scene-only switch ${triggeringSwitch.displayName}."
	}

	state.sceneIndex[switchId] = -1
}

private void handleDimStart(triggeringSwitch, dimmableAreaLights, String direction) {
	if (!dimmableAreaLights?.any()) {
		log.info "No dimmable area lights for ${triggeringSwitch.displayName}. Dimming skipped."
		return
	}
	log.info "handleDimStart for ${triggeringSwitch.displayName}: Start level change '${direction}' for ${dimmableAreaLights.size()} light(s)."

	if (direction == "up" && dimmableAreaLights.first()?.currentValue('switch') == 'off') {
		log.info "Dimming UP, lights off. Setting to low level first."
		dimmableAreaLights.each { light ->
			try { if (light.hasCommand('setLevel')) light.setLevel(2) } // Low level to turn on
			catch (e) { log.error "Error setting min level on ${light.displayName}: ${e.message}" }
		}
		pause(250) // Pause for lights to turn on
	}

	dimmableAreaLights.each { light ->
		try { if (light.hasCommand('startLevelChange')) light.startLevelChange(direction) } 
		catch (e) { log.error "Error startLevelChange(${direction}) on ${light.displayName}: ${e.message}" }
	}
}

private void handleDimStop(triggeringSwitch, dimmableAreaLights) {
	if (dimmableAreaLights?.any()) {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: Stop level change for ${dimmableAreaLights.size()} light(s)."
		dimmableAreaLights.each { light ->
			try { if(light.hasCommand('stopLevelChange')) light.stopLevelChange() } 
			catch (e) { log.error "Error stopLevelChange() on ${light.displayName}: ${e.message}"}
		}
	} else {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: No dimmable lights to stop."
	}
}

private void handleZoneOff(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	def sInfo = state.switchInfoMap[switchId]
	if (!sInfo?.loc) {
		log.warn "No switch/location info for ${triggeringSwitch.displayName} in handleZoneOff."
		return
	}

	List<String> lightsToControlIds = sInfo.loc.zoneName ? (state.switchZoneLights[switchId] ?: []) :
									  sInfo.loc.roomName ? (state.switchRoomLights[switchId] ?: []) : []
	String controlScope = sInfo.loc.zoneName ? "Zone '${sInfo.loc.zoneName}'" :
						  sInfo.loc.roomName ? "Room '${sInfo.loc.roomName}'" : "Unknown Scope"
	
	if (!lightsToControlIds?.any()) {
		log.info "No lights for ${controlScope} for ${triggeringSwitch.displayName}. Zone/Room Off skipped."
		return
	}
	def lightsToControl = getDevicesById(lightsToControlIds, settings.controlledLightsAndScenes)

	if (lightsToControl?.any()) {
		log.info "Zone/Room Off by ${triggeringSwitch.displayName}: Turning off ${lightsToControl.size()} lights for ${controlScope}"
		lightsToControl.each { light ->
			try { if (light.hasCommand("off")) light.off() } 
			catch (eDevice) { log.error "Error turning off ${light?.displayName} in Zone/Room Off: ${eDevice.message}" }
		}
	}
}


def enterSceneMode(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	state.sceneMode[switchId] = true
	setLedEffect(triggeringSwitch, "chase") 
	scheduleSceneModeTimeout(triggeringSwitch)
	log.info "Entered scene mode for ${triggeringSwitch.displayName}"
}

def activateScene(sceneDevice) {
	if (!sceneDevice) { log.warn "activateScene called with null sceneDevice."; return }
	log.info "Activating scene '${sceneDevice.displayName}'."
	try {
		if (sceneDevice.hasCommand("on")) sceneDevice.on()
		else if (sceneDevice.hasCommand("push")) sceneDevice.push(1) 
		else log.warn "Scene ${sceneDevice.displayName} supports neither 'on()' nor 'push()'."
	} catch (e) {
		log.error "Failed to activate scene ${sceneDevice.displayName}: ${e.message}."
	}
}

def scheduleSceneModeTimeout(triggeringSwitch) {
	def timeoutSetting = settings.sceneModeTimeout
	def timeoutSeconds = (timeoutSetting instanceof Number && timeoutSetting > 0) ? timeoutSetting : 7
	def switchId = triggeringSwitch.id.toString()

	cancelSceneModeTimeout(triggeringSwitch) 
	state.sceneTimeoutJob[switchId] = runIn(timeoutSeconds, "exitSceneMode", [data: [switchId: switchId], overwrite: true])
	log.debug "Scheduled scene mode timeout for ${triggeringSwitch.displayName} in ${timeoutSeconds}s."
}

def cancelSceneModeTimeout(triggeringSwitch) {
	if (!triggeringSwitch) return
	def switchId = triggeringSwitch.id.toString()
	if (state.sceneTimeoutJob[switchId]) {
		try { 
			unschedule(state.sceneTimeoutJob[switchId]) 
			log.debug "Unscheduled scene mode timeout for ${triggeringSwitch.displayName}."
		}
		catch (e) { log.warn "Error unscheduling scene mode timeout for ${switchId}: ${e.message}" }
		state.sceneTimeoutJob.remove(switchId) 
	}
}

def exitSceneMode(data) {
	def switchId = data?.switchId?.toString() 
	
	if (!switchId) { 
		log.warn "exitSceneMode called without switchId. Clearing all scene modes."
		(state.sceneMode?.keySet() ?: []).each { id ->
			if (state.sceneMode[id]) { 
				state.sceneMode[id] = false
				def sw = getDevicesById(id, settings.controlledSwitches)
				if (sw) setLedEffect(sw, "solid") 
				log.info "Exited scene mode for ${sw?.displayName ?: id} (general clear)."
				if (state.sceneTimeoutJob[id]) {
					try { unschedule(state.sceneTimeoutJob[id]) } catch(e){}
					state.sceneTimeoutJob.remove(id)
				}
			}
		}
		return
	}

	def triggeringSwitch = getDevicesById(switchId, settings.controlledSwitches)
	if (triggeringSwitch && state.sceneMode[switchId]) {
		state.sceneMode[switchId] = false
		setLedEffect(triggeringSwitch, "solid") 
		log.info "Exited scene mode for ${triggeringSwitch.displayName}"
	} else if (!triggeringSwitch) {
		log.warn "exitSceneMode: Switch ID ${switchId} not found. Clearing state."
		state.sceneMode?.remove(switchId) 
		state.sceneIndex?.remove(switchId) 
	}
	
	if (state.sceneTimeoutJob[switchId]) {
		try { unschedule(state.sceneTimeoutJob[switchId]) } catch(e){ /* ignore */ }
		state.sceneTimeoutJob.remove(switchId)
	}
}


def setLedEffect(switchDevice, effectName) {
	if (!switchDevice || !switchDevice.hasCommand('ledEffectAll')) return
	try {
		Integer hue = 170 // Default hue
		try { // Attempt to get hue from device setting parameter 95
			if (switchDevice.metaClass.respondsTo(switchDevice, "getSetting")) {
				def settingVal = switchDevice.getSetting('parameter95') 
				if (settingVal?.toString()) hue = settingVal.toString().toInteger()
			}
		} catch (Exception e) { /* Default hue will be used */ }
		
		Integer effectCode = (effectName.toLowerCase() == "chase") ? 17 : 255 // 17=chase, 255=solid (device specific)
		switchDevice.ledEffectAll(effectCode, hue, 100, 255) // Brightness 100%, Duration 255 (indefinite)
		log.debug "Set LED effect '${effectName}' (code ${effectCode}) on ${switchDevice.displayName} with hue ${hue}."
	} catch (e) {
		 log.error "Failed to send LED effect to ${switchDevice.displayName}: ${e.message}."
	}
}

/**
 * Gets light (level, CT, enableCt) and LED (onLed, offLed) settings for a mode.
 * Falls back to global defaults if mode-specific settings are missing.
 */
private Map getModeSettings(String targetModeName = null) {
	String effectiveModeName = targetModeName ?: state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	Map modeConfig = state.modeSettingsMap[effectiveModeName] 

	Integer finalLevel = modeConfig?.level ?: state.globalDefaultLevel
	Boolean resolvedEnableCt = modeConfig ? modeConfig.enableCt : (targetModeName != null) // Enable CT if specific mode requested but not found, else false
	Integer finalCt = resolvedEnableCt ? (modeConfig?.ct ?: state.globalDefaultColorTemperature) : null
	
	Integer finalLedOn = modeConfig?.ledOn != null ? modeConfig.ledOn : state.globalDefaultLedOnBrightness
	Integer finalLedOff = modeConfig?.ledOff != null ? modeConfig.ledOff : state.globalDefaultLedOffBrightness
	
	return [level: finalLevel, ct: finalCt, enableCt: resolvedEnableCt, onLed: finalLedOn, offLed: finalLedOff]
}


/**
 * Checks if a switch is "scene-only": name contains " Scene Switch", or has scenes AND no area lights.
 * Local switches are not considered scene-only for control.
 */
private boolean isSceneOnlySwitch(String switchId) {
	def sInfo = state.switchInfoMap[switchId.toString()]
	if (!sInfo || sInfo.type == "local") return false

	if (sInfo.displayName?.toLowerCase()?.contains(" scene switch")) return true
	
	boolean hasScenes = state.sortedSwitchSceneIds[switchId.toString()]?.any()
	boolean hasAreaLights = state.switchAreaLights[switchId.toString()]?.any()
	return hasScenes && !hasAreaLights
}

/**
 * Determines if a device is a scene: checks displayName, typeName, or SceneActivation capability.
 */
def isScene(device) {
	if (!device) return false
	if (device.displayName instanceof String && device.displayName.matches(/(?i).*\b[Ss][Cc][Ee][Nn][Ee]\b.*/)) return true
	if (device.typeName && ["CoCoHue Scene", "Scene Activator", "hueBridgeScene", "Virtual Scene Switch", "Advanced Scene Switch"].contains(device.typeName)) return true
	try { if (device.hasCapability("SceneActivation")) return true } 
	catch (Exception e) { /* ignore, capability check might fail on some devices/drivers */ }
	return false
}

/**
 * Retrieves device(s) by ID(s) from a list, using index maps for performance if available.
 */
def getDevicesById(def deviceIdInput, Collection deviceListParameter) {
	boolean singleIdMode = deviceIdInput instanceof String || deviceIdInput instanceof Number
	if (deviceIdInput == null || (deviceIdInput instanceof Collection && deviceIdInput.isEmpty()) || 
		deviceListParameter == null || deviceListParameter.isEmpty()) {
		return singleIdMode ? null : []
	}

	List<String> idsToFetch = (singleIdMode) ? [deviceIdInput.toString()] : 
							  (deviceIdInput instanceof Collection) ? deviceIdInput.collect { it?.toString() }.findAll { it } : 
							  [deviceIdInput.toString()]
	if (idsToFetch.isEmpty()) return singleIdMode ? null : []

	Map<String, Integer> indexMap = settings.controlledSwitches.is(deviceListParameter) ? state.deviceToIndexMap?.switches :
									settings.controlledLightsAndScenes.is(deviceListParameter) ? state.deviceToIndexMap?.lightsAndScenes : null

	List foundDevices = []
	if (indexMap != null) { // Use index map
		idsToFetch.each { id ->
			Integer deviceIndex = indexMap[id]
			if (deviceIndex != null && deviceIndex >= 0 && deviceIndex < deviceListParameter.size()) {
				def device = deviceListParameter[deviceIndex] 
				if (device?.id?.toString() == id) foundDevices << device // Verify ID match
				else { // Stale index, fallback search
					def fallbackDevice = deviceListParameter.find { it.id?.toString() == id }
					if (fallbackDevice) foundDevices << fallbackDevice
				}
			} else { // Not in index or out of bounds, fallback search
				def fallbackDevice = deviceListParameter.find { it.id?.toString() == id }
				if (fallbackDevice) foundDevices << fallbackDevice
			}
		}
	} else { // No index map, iterate
		idsToFetch.each { idToFind ->
			def device = deviceListParameter.find { it.id?.toString() == idToFind }
			if (device) foundDevices << device
		}
	}
	return singleIdMode ? foundDevices.first() : foundDevices // .first() on empty list gives null
}

/**
 * Updates the switch control summary for app preferences.
 * Lists controls for non-local switches; identifies local switches.
 */
def updateSwitchControlSummary() {
	if (!settings.controlledSwitches?.any()) {
		state.switchControlSummary = "No switches are currently selected."
		return
	}
	if (!state.switchInfoMap || !state.switchIdToLocationMap || !state.switchAreaLights || 
		!state.switchScenes || !state.sortedSwitchSceneIds || !state.switchZoneLights || !state.switchRoomLights) {
		state.switchControlSummary = "Device maps not fully initialized. Please save settings again."
		log.warn "updateSwitchControlSummary: Required state maps missing."
		return
	}

	StringBuilder summary = new StringBuilder()
	def sortedSwitches = settings.controlledSwitches.sort { a, b -> (a.displayName ?: '').toLowerCase() <=> (b.displayName ?: '').toLowerCase() }

	sortedSwitches.each { sw ->
		def switchId = sw.id.toString(); def sInfo = state.switchInfoMap[switchId]
		def switchName = sInfo?.displayName ?: sw.displayName ?: "Switch ID ${switchId}"
		
		summary.append("${switchName.toUpperCase()} (${sInfo?.type})\n")

		def transformName = { String devName, String roomToStrip ->
			if (!devName) return ""
			if (roomToStrip && !roomToStrip.isEmpty() && devName.toLowerCase().startsWith((roomToStrip + " ").toLowerCase())) {
				return devName.substring(roomToStrip.length() + 1).trim() ?: devName
			}
			return devName
		}
		String roomNameForStripping = sInfo.loc?.roomName?.trim()
		boolean isThisSwitchSceneOnly = isSceneOnlySwitch(switchId)

		// Tap Up (Single)
		List<String> tapUpIds = isThisSwitchSceneOnly ? (state.sortedSwitchSceneIds[switchId] ?: []) : (state.switchAreaLights[switchId] ?: [])
		String tapUpHdr = isThisSwitchSceneOnly ? "Tap Up (Next Scene)" : "Tap Up (Area Lights ON)"
		String tapUpNames = getDevicesById(tapUpIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  ${tapUpHdr}: ${tapUpNames ?: "None"}\n")

		// Tap Down (Single)
		String tapDownHdr = isThisSwitchSceneOnly ? "Tap Down (Room/Zone Lights OFF)" : "Tap Down (Area Lights OFF)"
		List<String> tapDownTargetIds = isThisSwitchSceneOnly ? 
			((sInfo.loc?.roomName ? state.switchRoomLights[switchId] : []) + (sInfo.loc?.zoneName ? state.switchZoneLights[switchId] : [])).unique() :
			(state.switchAreaLights[switchId] ?: [])
		String tapDownNames = getDevicesById(tapDownTargetIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  ${tapDownHdr}: ${tapDownNames ?: "None"}\n")

		// Tap Up 2x (Double) - Zone/Room ON
		List<String> tapUp2xIds = (sInfo.loc?.zoneName ? state.switchZoneLights[switchId] : sInfo.loc?.roomName ? state.switchRoomLights[switchId] : []) ?: []
		String tapUp2xNames = getDevicesById(tapUp2xIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  Tap Up 2x (Zone/Room ON): ${tapUp2xNames ?: "None"}\n")
		
		// Tap Down 2x (Double) - Zone/Room OFF
		summary.append("  Tap Down 2x (Zone/Room OFF): ${tapUp2xNames ?: "None (targets same as Tap Up 2x)"}\n")

		// Config Button (Scenes)
		List<String> sceneIds = state.sortedSwitchSceneIds[switchId] ?: []
		String sceneNames = getDevicesById(sceneIds, settings.controlledLightsAndScenes)?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  Config Btn (Cycle Scenes): ${sceneNames ?: "None"}\n\n")
	}
	state.switchControlSummary = summary.toString().trim()
	log.debug("Switch control summary updated.")
}
