/**
* Hank's Switch Bot v09-10-2026
* Copyright 2026 Hank Leukart
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
*
* Each switch's LED bar (its on/off state and level) is kept in sync with the lights it
* mirrors: event-driven with debouncing, plus a periodic reconciliation sweep so missed
* device events self-correct. LED indicator brightness adapts to each room's ambient
* light: lux-sensor driven where a sensor exists, otherwise sun position with a
* room-lights-on check at night. Local switches ("Local Switch" or "(S)") ignore button
* presses and LED bar sync, but their LED brightness is still managed.
*/

import groovy.transform.Field

// +/- window when comparing a light's reported level to a mode's level, because lights
// do not always settle exactly on the requested level.
@Field static final Integer LEVEL_MATCH_TOLERANCE = 2

definition(
	name: "Hank's Switch Bot",
	namespace: "hankle",
	author: "Hank Leukart",
	description: "Simple, zero-config control of lights and scenes from switches. Manages local switches for LED changes.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	singleThreaded: true
)

preferences {
	section("<h1>Hank's Switch Bot</h1>") {
		paragraph "Switch Bot automatically creates zero-configuration control of light and scenes with switches based on device names. For example, \"Kitchen Ceiling Switch\" will automatically control lights named \"Kitchen Ceiling 1 & 2\" and a scene named \"Kitchen Scene: Cooking.\" A switch named \"Kitchen Switch\" will control all \"Kitchen\" lights not controlled by another switch. Simply select all switches, lights, and scenes you want Switch Bot to handle for you.<hr />"

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
		paragraph ""

		if (location.modes) {
			def defaults = [
				morning: [level: 100, ct: 3000],
				day:     [level: 100, ct: 4000],
				evening: [level: 100, ct: 2700],
				sleep:   [level: 40, ct: 2200]
			]
			def sortedModes = location.modes.collect().sort { mode ->
				def name = mode.name?.toLowerCase() ?: ""
				def idx = defaults.keySet().toList().indexOf(name)
				[idx != -1 ? idx : 999, name]
			}
			sortedModes.each { mode ->
				String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()
				String currentModeNameLower = mode.name.toLowerCase()

				Boolean conditionalEnableCt = true

				def modeDefault = defaults[currentModeNameLower]
				Integer conditionalDefaultLevel = modeDefault?.level
				Integer conditionalDefaultCt = modeDefault?.ct

				input(name: "level_${safeModeName}", type: "number", title: "\"${mode.name}\" Brightness (%)", range: "1..100", required: false, width: 3, defaultValue: conditionalDefaultLevel)
				input(name: "ct_${safeModeName}", type: "number", title: "\"${mode.name}\" Color Temp (K)", range: "2000..9000", required: false, width: 3, defaultValue: conditionalDefaultCt)

				if (currentModeNameLower.contains("sleep") || currentModeNameLower.contains("night") || currentModeNameLower.contains("bed")) {
					input(name: "ledOffZone_${safeModeName}", type: "text",
						  title: "While in \"${mode.name},\" LEDs and lights auto-on disabled for this zone:",
						  description: "If a zone name is entered, LEDs on switches within that zone will be turned completely off (both ON and OFF LEDs set to 0) and motion auto-on triggers will be disabled for switches in this zone when in this mode. Other switches use the Dynamic Switch LED Brightness settings.",
						  required: false, width: 6)
				}
				paragraph ""
			}
		} else {
			paragraph "Save settings once to see per-mode configuration options."
		}
	}

	section("Auto On/Off Lights (Motion)", hideable: true, hidden: true) {
		paragraph "Configure which switches can automatically turn lights on/off using their built-in motion sensors, and define the ambient light (Lux) threshold for auto-on triggers."
		input "motionEnabledSwitches", "enum",
			title: "Switches that can auto-on/off lights with motion:",
			options: (settings?.controlledSwitches ?: controlledSwitches)?.findAll { it.hasCapability("MotionSensor") || it.hasCapability("Motion Sensor") }?.collectEntries { [it.id.toString(), it.displayName] } ?: [:],
			multiple: true, required: false, width: 6
		input name: "motionIlluminanceThreshold", type: "number", title: "Auto-On Lux Threshold", 
			 description: "Only trigger auto-on if light level is below this threshold. Leave blank to ignore Lux.", 
			 defaultValue: 50, required: false, width: 6
	}

	section("Dynamic Switch LED Brightness", hideable: true, hidden: true) {
		paragraph "Switch Bot automatically matches each switch's LED brightness to its room's ambient light — bright LEDs in a bright room, dim LEDs in a dark one — so LEDs are always visible but never glaring."
		input name: "enableDynamicLedBrightness", type: "bool", title: "<b>Enable Dynamic LED Brightness</b>", defaultValue: true, width: 12

		paragraph "<hr /><b>LED Brightness Levels</b><br /><i>LED brightness in a fully bright room vs. a fully dark room. \"Lights On\" and \"Lights Off\" refer to the room's lights; rooms with a light sensor fade smoothly between Bright and Dark levels.</i>"
		input name: "dynamicLedMaxOn", type: "number", title: "Bright Room, Lights On (%)",
			 range: "0..100", defaultValue: 30, required: false, width: 3
		input name: "dynamicLedMaxOff", type: "number", title: "Bright Room, Lights Off (%)",
			 range: "0..100", defaultValue: 7, required: false, width: 3
		input name: "dynamicLedMinOn", type: "number", title: "Dark Room, Lights On (%)",
			 range: "0..100", defaultValue: 3, required: false, width: 3
		input name: "dynamicLedMinOff", type: "number", title: "Dark Room, Lights Off (%)",
			 range: "0..100", defaultValue: 2, required: false, width: 3

		paragraph "<hr /><b>Rooms With a Light Sensor</b><br /><i>The room's Lux reading positions LED brightness between the Dark and Bright levels above. If multiple switches in a room have light sensors, one is selected automatically. These settings tune sensitivity.</i>"
		input name: "dynamicLedMaxLux", type: "number", title: "Bright Room Lux Threshold",
			 description: "Lux considered fully bright",
			 range: "10..2000", defaultValue: 100, required: false, width: 3
		input name: "dynamicLedCooldownMinutes", type: "number", title: "Adjustment Cooldown (min)",
			 description: "Min minutes between adjustments per room",
			 range: "1..60", defaultValue: 1, required: false, width: 3
		input name: "dynamicLedPercentChange", type: "number", title: "Minimum Lux Change (%)",
			 description: "Relative change required to adjust",
			 range: "1..100", defaultValue: 20, required: false, width: 3
		input name: "dynamicLedMinLuxDelta", type: "number", title: "Minimum Lux Delta",
			 description: "Absolute change required (noise floor)",
			 range: "1..100", defaultValue: 5, required: false, width: 3

		paragraph "<hr /><b>Rooms Without a Light Sensor</b><br /><i>Sun position decides instead: Bright Room levels during daylight (sunrise+30m to sunset&minus;30m). At night, Bright Room levels while any room light is on, and Dark Room levels once every light in the room is off. No settings needed.</i>"
	}

	section("Advanced Button Mappings (Optional)", hideable: true, hidden: true) {
		 input "singleTapUpButtonNumber", "number", title: "On Button Number", defaultValue: 1, required: false, width: 2
		 input "singleTapUpButtonEvent", "enum", title: "On Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 input "singleTapDownButtonNumber", "number", title: "Off Button Number", defaultValue: 1, required: false, width: 2
		 input "singleTapDownButtonEvent", "enum", title: "Off Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 2
		 input "configButtonNumber", "number", title: "Scene Mode Button Number", defaultValue: 8, required: false, width: 2
		 input "configButtonEvent", "enum", title: "Scene Mode Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 input "musicModeButtonNumber", "number", title: "Music Mode Button Number", defaultValue: 9, required: false, width: 2
		 input "musicModeButtonEvent", "enum", title: "Music Mode Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 2
		 paragraph "Note: In Scene Mode, the on and off buttons are used for navigating scenes.", width: 12
		 input "doubleTapUpButtonNumber", "number", title: "Room/Zone On Button Number", defaultValue: 2, required: false, width: 3
		 input "doubleTapUpButtonEvent", "enum", title: "Room/Zone On Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 3
		 input "doubleTapDownButtonNumber", "number", title: "Room/Zone Off Button Number", defaultValue: 2, required: false, width: 3
		 input "doubleTapDownButtonEvent", "enum", title: "Room/Zone Off Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 3
		 input "holdUpButtonNumber", "number", title: "Brighten Start Button Number", defaultValue: 6, required: false, width: 3
		 input "holdUpButtonEvent", "enum", title: "Brighten Start Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 3
		 input "releaseUpButtonNumber", "number", title: "Brighten Stop Button Number", defaultValue: 7, required: false, width: 3
		 input "releaseUpButtonEvent", "enum", title: "Brighten Stop Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "pushed", required: false, width: 3
		 input "holdDownButtonNumber", "number", title: "Dim Start Button Number", defaultValue: 6, required: false, width: 3
		 input "holdDownButtonEvent", "enum", title: "Dim Start Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 3
		 input "releaseDownButtonNumber", "number", title: "Dim Stop Button Number", defaultValue: 7, required: false, width: 3
		 input "releaseDownButtonEvent", "enum", title: "Dim Stop Button Event", options: ["pushed", "held", "released", "doubleTapped"], defaultValue: "held", required: false, width: 3
		 input "sceneModeTimeout", "number", title: "Scene Mode Timeout (seconds)", defaultValue: 7, required: false, width: 2
	}

	section("Music Mode", hideable: true, hidden: true) {
		input "musicDevice", "capability.musicPlayer", title: "Music Device controlled by Music Mode", required: false, multiple: false
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
	state.activeSwitchMode = [:]
	state.ledUpdateQueue = []
	state.modeSettingsMap = [:]
	state.currentLocationMode = null
	state.switchControlSummary = "Initializing or no switches configured..."
	state.siblingSwitchGroupsBySwitchId = [:]
	state.switchIdToLocationMap = [:]
	state.switchInfoMap = [:] 
	state.motionBypass = [:]
	state.lastMotionActiveTime = [:]
	state.lastModeActivity = [:]
	state.activeVolumeLoops = [:]
	state.roomToSensorSwitchMap = [:]
	state.sensorSwitchToRoomMap = [:]
	state.roomLedStatus = state.roomLedStatus ?: [:]

	state.switchRoomLights = [:]
	state.switchAreaLights = [:]
	state.switchZoneLights = [:]
	state.switchScenes = [:]
	state.sortedSwitchSceneIds = [:]
	state.switchDimmableAreaLightIds = [:]
	state.switchSyncSourceIds = [:]
	state.lightToSyncSwitchIds = [:]
	state.pendingLedBarSync = [:]
	state.lastSwitchInteraction = [:]
	state.pendingRampUp = [:]
	state.lastAppliedLedParams = [:] // Reset so initialization re-sends brightness to every switch (self-heal)

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
		settings.configButtonEvent, settings.musicModeButtonEvent ?: "pushed",
		settings.doubleTapUpButtonEvent, settings.doubleTapDownButtonEvent,
		settings.holdUpButtonEvent, settings.releaseUpButtonEvent,
		settings.holdDownButtonEvent, settings.releaseDownButtonEvent
	].findAll { it }.unique()

	if (!controlledSwitches) {
		log.warn "No switches selected. Cannot subscribe to button events."
	} else {
		controlledSwitches.each { sw ->
			def switchIdStr = sw.id.toString()
			def sInfo = state.switchInfoMap[switchIdStr]

			if (sInfo?.type == "local") {
				log.info "Switch ${sw.displayName} is a Local Switch. Skipping button and motion event subscriptions."
			} else {
				if (sw.hasCapability("PushableButton")) {
					 buttonEventsToSubscribe.each { eventName ->
						subscribe(sw, eventName, buttonHandler)
					 }
				} else {
					log.warn "Switch ${sw.displayName} does not support PushableButton capability."
				}

				boolean isMotionEnabled = settings.motionEnabledSwitches?.any { val ->
					(val instanceof String) ? (val == switchIdStr) : (val?.id?.toString() == switchIdStr)
				}
				if (isMotionEnabled && (sw.hasCapability("MotionSensor") || sw.hasCapability("Motion Sensor"))) {
					subscribe(sw, "motion", motionHandler)
					log.info "Subscribed to motion events on ${sw.displayName}"
				}
			}
			state.sceneIndex[switchIdStr] = state.sceneIndex[switchIdStr] ?: -1
			state.activeSwitchMode = state.activeSwitchMode ?: [:]
			state.activeSwitchMode[switchIdStr] = state.activeSwitchMode[switchIdStr] ?: "normal"
		}
	}

	// LED bar sync: subscribe to level/switch changes on every light each non-local
	// switch mirrors, so a change from any source updates the switch's LED bar.
	subscribeToSyncSourceLights()

	state.currentLocationMode = location.currentMode?.name?.toString()?.trim()
	log.info "Initial location mode tracked as: ${state.currentLocationMode ?: 'UNKNOWN'}"

	try {
		subscribe(location, "mode", modeChangeHandler)
	} catch (e) {
		log.error "Error subscribing to location mode changes: ${e.message}"
	}

	setupRoomLightSensors()

	// Set initial LED brightness for all switches (staggered via queue to avoid blocking)
	if (!state.currentLocationMode) {
		log.warn "Initial location mode not set. Setting LEDs to global defaults."
	}
	scheduleLedUpdates(state.currentLocationMode)

	// LED bar sync backstop: reconcile all switches shortly after (re)initialization,
	// then periodically, so any missed device events self-correct.
	runIn(15, "reconcileAllLedBars")
	runEvery5Minutes("reconcileAllLedBars")

	// LED brightness sweep: covers sunrise/sunset boundary crossings for sensor-less
	// rooms and local switches, which the bar sync engine does not touch.
	runEvery5Minutes("refreshAllLedBrightness")

	updateSwitchControlSummary()
	log.info "Initialization complete."
}

private String normalizeDeviceName(String deviceName) {
	if (deviceName == null) return null
	return deviceName.replaceAll("[‘’]", "'") // Standardize curly apostrophes to straight
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

	// Parse zone from displayName, label, or name (whichever contains brackets)
	def findZone = { String text ->
		if (!text) return null
		def matcher = text =~ /\s*\[\s*(.*?)\s*\]\s*/
		return matcher.find() ? matcher[0][1]?.trim() : null
	}
	zoneName = findZone(device?.displayName) ?: findZone(device?.label) ?: findZone(device?.name)

	if (!device?.displayName) {
		 return [roomName: null, areaName: null, zoneName: zoneName, parsingName: ""]
	}

	String baseDisplayNameForParsing = getParsingName(device) 
	// Strip out the zone tag from the parsing string to prevent interference with room/area parsing
	if (baseDisplayNameForParsing) {
		baseDisplayNameForParsing = baseDisplayNameForParsing.replaceAll(/\s*\[\s*(.*?)\s*\]\s*/, "").trim()
	} 
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

		if (originalDisplayName.toLowerCase().contains("local switch") || originalDisplayName.toLowerCase().contains("(s)")) {
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

	// Step 7: Build LED bar sync maps. Each non-local switch mirrors its area lights,
	// falling back to room then zone lights (covers master, sibling, and scene-only
	// switches). The reverse map fans a light event out to every switch mirroring it.
	state.switchSyncSourceIds = [:]
	state.lightToSyncSwitchIds = [:]
	state.switchInfoMap.each { switchId, sInfo ->
		if (sInfo.type == "local") return
		List sourceIds = (state.switchAreaLights[switchId] ?: state.switchRoomLights[switchId] ?: state.switchZoneLights[switchId] ?: []) as List
		sourceIds = sourceIds.findAll { lightId ->
			def light = getDevicesById(lightId.toString(), settings.controlledLightsAndScenes)
			light && !isAllLightsGroup(light) && light.hasAttribute("switch")
		}
		if (!sourceIds) {
			log.debug "LED bar sync: no lights to mirror for switch '${sInfo.displayName}'."
			return
		}
		state.switchSyncSourceIds[switchId] = sourceIds
		sourceIds.each { lightId ->
			List swIds = state.lightToSyncSwitchIds[lightId] ?: []
			swIds << switchId
			state.lightToSyncSwitchIds[lightId] = swIds
		}
	}
	log.info "LED bar sync: mapped ${state.switchSyncSourceIds.size()} switch(es) to ${state.lightToSyncSwitchIds.size()} light(s)."
	log.info "buildDeviceMaps finished."
}


def buildModeSettingsMap() {
	def newModeSettings = [:]
	// Ensure global defaults are properly initialized from settings or fallback values
	state.globalDefaultLevel = (settings.globalDefaultLevel instanceof Number) ? settings.globalDefaultLevel : 100
	state.globalDefaultColorTemperature = (settings.globalDefaultColorTemperature instanceof Number) ? settings.globalDefaultColorTemperature : 2700

	location.modes?.each { mode ->
		String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()

		def rawLevel = settings."level_${safeModeName}"
		def validatedLevel = (rawLevel instanceof Number && rawLevel >= 1 && rawLevel <= 100) ? rawLevel : null
		def rawCt = settings."ct_${safeModeName}"
		def validatedCt = (rawCt instanceof Number && rawCt >= 2000 && rawCt <= 9000) ? rawCt : null
		boolean enableCtSetting = (validatedCt != null)
		// Note: The setting "ledOffZone_${safeModeName}" is read directly in isZoneBypassed, not stored in state.modeSettingsMap

		newModeSettings[mode.name] = [
			level: validatedLevel,
			ct: validatedCt,
			enableCt: enableCtSetting
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
	state.motionBypass = [:]
	log.info "All temporary motion bypasses cleared due to mode change."

	scheduleLedUpdates(newModeName)

	if (previousModeName == null) {
		log.info "No previous mode (first change). Skipping light adjustments."
		return
	}

	log.info "Processing light adjustments for mode change from '${previousModeName}' to '${newModeName}'."
	Map prevModeLightSettings = getModeSettings(previousModeName) 
	Map newModeLightSettings = getModeSettings(newModeName)		 
	log.info "[ModeChange-Log] Mode Settings - Prev Mode '${previousModeName}': level=${prevModeLightSettings?.level}, ct=${prevModeLightSettings?.ct}, enableCt=${prevModeLightSettings?.enableCt}. New Mode '${newModeName}': level=${newModeLightSettings?.level}, ct=${newModeLightSettings?.ct}, enableCt=${newModeLightSettings?.enableCt}."

	settings.controlledLightsAndScenes?.each { lightDevice ->
		if (!lightDevice) return

		String dn = lightDevice.displayName ?: "Unknown Device"
		String dni = lightDevice.deviceNetworkId ?: "N/A"
		String typeName = lightDevice.typeName ?: "N/A"
		boolean isSc = isScene(lightDevice)
		boolean isAll = isAllLightsGroup(lightDevice)
		boolean hasSw = lightDevice.hasCapability("Switch")
		String swVal = hasSw ? lightDevice.currentValue('switch')?.toString() : "N/A"

		log.info "[ModeChange-Log] Evaluating device '${dn}' (Type: ${typeName}, DNI: ${dni}): isScene=${isSc}, isAllLightsGroup=${isAll}, hasSwitchCapability=${hasSw}, switchStatus='${swVal}'"

		if (isSc) {
			log.info "[ModeChange-Log]   -> Skipped '${dn}' because it is recognized as a Scene."
			return
		}
		if (isAll) {
			log.info "[ModeChange-Log]   -> Skipped '${dn}' because it is recognized as the All Lights Group."
			return
		}
		if (!hasSw) {
			log.info "[ModeChange-Log]   -> Skipped '${dn}' because it does not have the 'Switch' capability."
			return
		}
		if (swVal != 'on') {
			log.info "[ModeChange-Log]   -> Skipped '${dn}' because its current switch status is '${swVal}' (not 'on')."
			return
		}

		Integer prevLightLevel = lightDevice.hasCapability("SwitchLevel") ? (lightDevice.currentValue('level') as Integer) : null
		boolean levelMatchedPrev = lightDevice.hasCapability("SwitchLevel") ?
								 (prevLightLevel != null && Math.abs(prevLightLevel - (prevModeLightSettings.level as Integer)) <= LEVEL_MATCH_TOLERANCE) :
								 (prevModeLightSettings.level > 0)
		
		boolean ctMatchedPrev = !prevModeLightSettings.enableCt || !lightDevice.hasCapability("ColorTemperature") ? true :
								(lightDevice.currentValue('colorTemperature') != null && 
								 Math.abs((lightDevice.currentValue('colorTemperature') as Integer) - (prevModeLightSettings.ct as Integer)) <= 50)

		log.info "[ModeChange-Log] Device '${dn}' is ON. Evaluating matching criteria: " +
		         "prevLightLevel=${prevLightLevel}, targetPrevLevel=${prevModeLightSettings.level}, levelMatchedPrev=${levelMatchedPrev}; " +
		         "hasColorTemperature=${lightDevice.hasCapability("ColorTemperature")}, enableCt=${prevModeLightSettings.enableCt}, " +
		         "currentCt=${lightDevice.currentValue('colorTemperature')}, targetPrevCt=${prevModeLightSettings.ct}, ctMatchedPrev=${ctMatchedPrev}."

		if (levelMatchedPrev && ctMatchedPrev) {
			log.info "[ModeChange-Log]   -> SUCCESS: '${dn}' matched prev mode '${previousModeName}'. Adjusting to new mode '${newModeName}'."
			try {
				boolean ctChanged = (newModeLightSettings.enableCt && lightDevice.hasCapability("ColorTemperature") && newModeLightSettings.ct != null)
				if (ctChanged) {
					log.info "[ModeChange-Log]   -> Color temperature changing. Setting CT to ${newModeLightSettings.ct}K and level to ${newModeLightSettings.level}% over a 30-second duration."
					try {
						if (lightDevice.hasCapability("SwitchLevel")) {
							lightDevice.setColorTemperature(newModeLightSettings.ct, newModeLightSettings.level, 30)
						} else {
							lightDevice.setColorTemperature(newModeLightSettings.ct, null, 30)
						}
					} catch (IllegalArgumentException | MissingMethodException | GroovyRuntimeException ex) {
						log.warn "[ModeChange-Log]   -> Device '${dn}' does not support 3-argument setColorTemperature. Falling back to individual commands."
						if (lightDevice.hasCapability("SwitchLevel")) {
							lightDevice.setLevel(newModeLightSettings.level, 30)
						}
						lightDevice.setColorTemperature(newModeLightSettings.ct)
					}
				} else {
					if (lightDevice.hasCapability("SwitchLevel")) {
						log.info "[ModeChange-Log]   -> Setting level to ${newModeLightSettings.level}% for '${dn}'."
						lightDevice.setLevel(newModeLightSettings.level)
					} else if (newModeLightSettings.level > 0 && swVal != 'on') {
						log.info "[ModeChange-Log]   -> Turning ON '${dn}' defensively (new mode level > 0)."
						lightDevice.on()
					} else {
						log.info "[ModeChange-Log]   -> No level adjustment needed for '${dn}' (no SwitchLevel capability, and device already ON)."
					}
				}
			} catch (e) {
				log.error "[ModeChange-Log]   -> ERROR: Failed to adjust light '${dn}' to new mode: ${e.message}"
			}
		} else {
			log.info "[ModeChange-Log]   -> Skipped '${dn}' because it did not match previous mode settings (levelMatchedPrev=${levelMatchedPrev}, ctMatchedPrev=${ctMatchedPrev})."
		}
	}
	log.info "Mode change light adjustments processed."
	// Mode changes fade lights over up to 30 seconds; reconcile LED bars once settled.
	runIn(45, "reconcileAllLedBars")
}


/**
 * Queues staggered LED brightness updates for all controlled switches instead of blocking
 * the calling handler with pauseExecution. A new call replaces any in-flight queue, so the
 * most recent trigger (e.g. a mode change) wins.
 */
private void scheduleLedUpdates(String modeName) {
	List<Map> queue = []
	settings.controlledSwitches?.each { sw ->
		if (!sw) return
		Map effectiveBrightness = calcLEDLevel(sw, modeName)
		if (effectiveBrightness == null) return
		queue << [id: sw.id.toString(), on: effectiveBrightness.on, off: effectiveBrightness.off]
	}
	state.ledUpdateQueue = queue
	if (queue) runInMillis(250, "processLedUpdateQueue")
}

def processLedUpdateQueue() {
	List queue = state.ledUpdateQueue ?: []
	if (!queue) return
	Map item = queue.remove(0)
	state.ledUpdateQueue = queue
	String swId = item.id?.toString()
	Integer targetOn = item.on as Integer
	Integer targetOff = item.off as Integer
	// Single choke point for LED parameter writes: skip the radio command when the
	// target matches the last brightness this app applied to the switch.
	Map last = state.lastAppliedLedParams ? state.lastAppliedLedParams[swId] : null
	if (last == null || (last.on as Integer) != targetOn || (last.off as Integer) != targetOff) {
		def sw = getDevicesById(swId, settings.controlledSwitches)
		if (sw) {
			updateLEDs(sw, targetOn, targetOff)
			state.lastAppliedLedParams = state.lastAppliedLedParams ?: [:]
			state.lastAppliedLedParams[swId] = [on: targetOn, off: targetOff]
		}
	}
	if (queue) runInMillis(250, "processLedUpdateQueue")
}

/**
 * Updates LED indicator brightness on a switch device using setParameter.
 */
private void updateLEDs(switchDevice, Integer onBrightness, Integer offBrightness) {
	if (!switchDevice || !switchDevice.hasCommand("setParameter")) return

	// Determine the correct size argument if a 3-argument call is required:
	// - Zigbee / Blue Series drivers represent a 1-byte parameter with size 8 (bits).
	// - Z-Wave / Red Series drivers represent a 1-byte parameter with size 1 (byte).
	String typeNameLower = switchDevice.typeName?.toLowerCase() ?: ""
	boolean isZwave = typeNameLower.contains("red series") || typeNameLower.contains("z-wave") || typeNameLower.contains("zwave")
	Integer defaultSize = isZwave ? 1 : 8

	try {
		log.debug "Setting ${switchDevice.displayName} LED ON to ${onBrightness} (P97), OFF to ${offBrightness} (P98) [isZwave: ${isZwave}, size: ${defaultSize}]"
		try {
			// Try 2-argument call first so Zigbee Blue drivers can auto-detect the correct 8-bit size
			switchDevice.setParameter(97, onBrightness)
		} catch (MissingMethodException | IllegalArgumentException e) {
			switchDevice.setParameter(97, onBrightness, defaultSize)
		}

		try {
			switchDevice.setParameter(98, offBrightness)
		} catch (MissingMethodException | IllegalArgumentException e) {
			switchDevice.setParameter(98, offBrightness, defaultSize)
		}
	} catch (e) {
		log.error "Error updating LEDs for ${switchDevice.displayName}: ${e.message}"
	}
}

private String getSwitchRoomName(String switchId) {
	if (!switchId) return null
	def sInfo = state.switchInfoMap ? state.switchInfoMap[switchId] : null
	if (sInfo?.loc?.roomName) return sInfo.loc.roomName
	def loc = state.switchIdToLocationMap ? state.switchIdToLocationMap[switchId] : null
	return loc?.roomName
}

/**
 * Determines the effective LED brightness for a switch:
 *   1. Sleep-mode zone override -> LEDs fully off.
 *   2. Room with a lux sensor -> sensor-driven levels (interpolated Dark<->Bright Room).
 *   3. Room without a sensor -> sun-based fallback (see computeSensorlessLedLevels).
 * Returns null when the app should not manage this switch's LED brightness.
 */
private Map calcLEDLevel(switchDevice, String currentModeName) {
	String swId = switchDevice.id.toString()
	if (isZoneBypassed(swId, currentModeName)) {
		log.debug "Mode '${currentModeName}': Turning off LEDs for switch ${switchDevice.displayName} as per zone settings."
		return [on: 0, off: 0]
	}
	if (settings.enableDynamicLedBrightness == false) return null

	String swRoom = getSwitchRoomName(swId)
	if (swRoom && state.roomToSensorSwitchMap && state.roomToSensorSwitchMap[swRoom]) {
		def roomStatus = state.roomLedStatus ? state.roomLedStatus[swRoom] : null
		if (roomStatus?.currentOn != null && roomStatus?.currentOff != null) {
			return [on: roomStatus.currentOn as Integer, off: roomStatus.currentOff as Integer]
		}
	}
	return computeSensorlessLedLevels(swId)
}

/**
 * Sun-based LED brightness for rooms without a lux sensor:
 *   - Daylight (sunrise+30m to sunset-30m): Bright Room levels.
 *   - Night with any room light on (room is lit): Bright Room levels.
 *   - Night with all room lights off (room is dark): Dark Room levels.
 */
private Map computeSensorlessLedLevels(String switchId) {
	Integer minOn = (settings.dynamicLedMinOn != null) ? (settings.dynamicLedMinOn as Integer) : 2
	Integer maxOn = (settings.dynamicLedMaxOn != null) ? (settings.dynamicLedMaxOn as Integer) : 30
	Integer minOff = (settings.dynamicLedMinOff != null) ? (settings.dynamicLedMinOff as Integer) : 1
	Integer maxOff = (settings.dynamicLedMaxOff != null) ? (settings.dynamicLedMaxOff as Integer) : 7

	if (isDaylightWindow() || isAnyRoomLightOn(switchId)) {
		return [on: maxOn, off: maxOff]
	}
	return [on: minOn, off: minOff]
}

/** True between sunrise+30m and sunset-30m. Fails open (daylight) if sun times are unavailable. */
private boolean isDaylightWindow() {
	try {
		def sun = getSunriseAndSunset()
		if (!sun?.sunrise || !sun?.sunset) return true
		long nowMs = now()
		return nowMs > (sun.sunrise.time + 1800000L) && nowMs < (sun.sunset.time - 1800000L)
	} catch (e) {
		log.warn "Could not determine sunrise/sunset (${e.message}). Treating as daylight."
		return true
	}
}

/** True if any light in the switch's room (falling back to its mirrored lights) is on. */
private boolean isAnyRoomLightOn(String switchId) {
	List lightIds = (state.switchRoomLights ? state.switchRoomLights[switchId] : null) ?:
					(state.switchSyncSourceIds ? state.switchSyncSourceIds[switchId] : null) ?: []
	if (!lightIds) return false
	def lights = getDevicesById(lightIds, settings.controlledLightsAndScenes)
	return lights?.any { !isAllLightsGroup(it) && it.currentValue("switch", true) == "on" } ?: false
}

/** Recomputes one switch's LED brightness and queues an update only if it changed. */
private void refreshLedBrightness(String switchId) {
	def sw = getDevicesById(switchId, settings.controlledSwitches)
	if (!sw) return
	String modeName = state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	Map target = calcLEDLevel(sw, modeName)
	if (target == null) return
	Map last = state.lastAppliedLedParams ? state.lastAppliedLedParams[switchId] : null
	if (last == null || (last.on as Integer) != (target.on as Integer) || (last.off as Integer) != (target.off as Integer)) {
		enqueueLedUpdates([[id: switchId, on: target.on, off: target.off]])
	}
}

/**
 * Periodic LED brightness sweep over all switches (including local switches, which the
 * bar sync engine skips). Catches sunrise/sunset boundary crossings and external drift;
 * queues updates only where the target differs from the last applied values.
 */
def refreshAllLedBrightness() {
	if (settings.enableDynamicLedBrightness == false) return
	String modeName = state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	List<Map> items = []
	settings.controlledSwitches?.each { sw ->
		if (!sw) return
		Map target = calcLEDLevel(sw, modeName)
		if (target == null) return
		String swId = sw.id.toString()
		Map last = state.lastAppliedLedParams ? state.lastAppliedLedParams[swId] : null
		if (last == null || (last.on as Integer) != (target.on as Integer) || (last.off as Integer) != (target.off as Integer)) {
			items << [id: swId, on: target.on, off: target.off]
		}
	}
	if (items) {
		log.info "LED brightness sweep: adjusting ${items.size()} switch(es)."
		enqueueLedUpdates(items)
	}
}

private boolean isZoneBypassed(String switchId, String modeName) {
	if (!modeName) return false
	String safeModeName = modeName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()
	String disabledZone = settings."ledOffZone_${safeModeName}"
	if (disabledZone && !disabledZone.trim().isEmpty() && state.switchIdToLocationMap) {
		String switchZone = state.switchIdToLocationMap[switchId]?.zoneName?.trim()
		return switchZone && switchZone.equalsIgnoreCase(disabledZone.trim())
	}
	return false
}

/**
 * Calculates target LED On and Off brightness from ambient lux using linear interpolation:
 * Lux 0 -> [minOn, minOff] (default 2%, 1%)
 * Lux >= maxLux -> [maxOn, maxOff] (default 30%, 7%)
 */
private Map calculateTargetLedLevels(Double lux) {
	Integer minOn = (settings.dynamicLedMinOn != null) ? (settings.dynamicLedMinOn as Integer) : 2
	Integer maxOn = (settings.dynamicLedMaxOn != null) ? (settings.dynamicLedMaxOn as Integer) : 30
	Integer minOff = (settings.dynamicLedMinOff != null) ? (settings.dynamicLedMinOff as Integer) : 1
	Integer maxOff = (settings.dynamicLedMaxOff != null) ? (settings.dynamicLedMaxOff as Integer) : 7
	Double maxLuxThreshold = (settings.dynamicLedMaxLux != null) ? (settings.dynamicLedMaxLux as Double) : 100.0
	if (maxLuxThreshold <= 0.0) maxLuxThreshold = 100.0

	Double clampedLux = Math.max(0.0, Math.min(lux ?: 0.0, maxLuxThreshold))
	Double ratio = clampedLux / maxLuxThreshold

	Integer targetOn = Math.round(minOn + (maxOn - minOn) * ratio) as Integer
	Integer targetOff = Math.round(minOff + (maxOff - minOff) * ratio) as Integer

	return [on: targetOn, off: targetOff]
}

/**
 * Enqueues LED parameter updates for switches, deduplicating in-flight queue entries,
 * and starts queue processing if it is not already running.
 */
private void enqueueLedUpdates(List<Map> items) {
	if (!items) return
	List queue = state.ledUpdateQueue ?: []
	items.each { newItem ->
		queue.removeAll { it.id == newItem.id }
		queue << newItem
	}
	state.ledUpdateQueue = queue
	// Always (re)arm the drain timer: if a previous drain chain died (lost timer,
	// exception), a stale non-empty queue would otherwise never process again.
	if (queue) runInMillis(250, "processLedUpdateQueue")
}

/**
 * Discovers and maps switches with IlluminanceMeasurement capability for each room.
 * If multiple switches in a room have light sensors, exactly one is selected
 * (prioritizing the room's master switch, then first alphabetically).
 */
private void setupRoomLightSensors() {
	state.roomToSensorSwitchMap = [:]
	state.sensorSwitchToRoomMap = [:]
	state.roomLedStatus = state.roomLedStatus ?: [:]

	if (settings.enableDynamicLedBrightness == false) {
		log.info "Dynamic switch LED brightness based on room light sensors is disabled."
		return
	}

	if (!settings.controlledSwitches) return

	// Group switches with IlluminanceMeasurement by roomName
	Map<String, List> roomToCandidateSensors = [:]
	settings.controlledSwitches.each { sw ->
		if (!sw) return
		String swId = sw.id.toString()
		String roomName = getSwitchRoomName(swId)
		if (!roomName) return

		boolean hasIlluminance = sw.hasCapability("IlluminanceMeasurement") || sw.hasCapability("Illuminance Measurement")
		if (hasIlluminance) {
			if (!roomToCandidateSensors.containsKey(roomName)) {
				roomToCandidateSensors[roomName] = []
			}
			roomToCandidateSensors[roomName] << sw
		}
	}

	long nowMs = now()
	roomToCandidateSensors.each { roomName, candidateList ->
		if (!candidateList) return

		// Select single designated sensor switch for the room:
		// Prefer master switch if candidate, otherwise sort alphabetically by displayName
		def selectedSensor = candidateList.find { sw ->
			def sInfo = state.switchInfoMap ? state.switchInfoMap[sw.id.toString()] : null
			sInfo?.type == "master"
		}
		if (!selectedSensor) {
			selectedSensor = candidateList.sort { (it.displayName ?: "").toLowerCase() }.first()
		}

		String sensorId = selectedSensor.id.toString()
		state.roomToSensorSwitchMap[roomName] = sensorId
		state.sensorSwitchToRoomMap[sensorId] = roomName

		subscribe(selectedSensor, "illuminance", illuminanceHandler)

		if (candidateList.size() > 1) {
			log.info "Dynamic LEDs: Room '${roomName}' has ${candidateList.size()} switches with light sensors. Selected '${selectedSensor.displayName}' (ID: ${sensorId}) as designated room sensor."
		} else {
			log.info "Dynamic LEDs: Room '${roomName}' using light sensor on '${selectedSensor.displayName}' (ID: ${sensorId})."
		}

		// Seed initial reading if available on the sensor device
		def currentIlluminance = selectedSensor.currentValue("illuminance")
		if (currentIlluminance != null) {
			try {
				Double initLux = currentIlluminance as Double
				Map targetLevels = calculateTargetLedLevels(initLux)
				state.roomLedStatus[roomName] = [
					lastUpdated: nowMs,
					lastLux: initLux,
					currentOn: targetLevels.on,
					currentOff: targetLevels.off
				]
				log.debug "Dynamic LEDs: Seeded room '${roomName}' with ${initLux} Lux -> ON: ${targetLevels.on}%, OFF: ${targetLevels.off}%"
			} catch (e) {
				log.warn "Dynamic LEDs: Error reading initial illuminance from ${selectedSensor.displayName}: ${e.message}"
			}
		}
	}
}

/**
 * Handles illuminance events from a room's designated sensor switch.
 * Enforces a cooldown (default 1 minute), minimum lux change thresholds, and an
 * integer deadband before queueing parameter updates for all switches in the room.
 */
def illuminanceHandler(evt) {
	if (settings.enableDynamicLedBrightness == false) return

	def sensorSwitch = evt.device
	if (!sensorSwitch) return
	String sensorId = sensorSwitch.id.toString()
	String roomName = state.sensorSwitchToRoomMap ? state.sensorSwitchToRoomMap[sensorId] : null
	if (!roomName) {
		roomName = getSwitchRoomName(sensorId)
	}
	if (!roomName) {
		log.debug "Dynamic LEDs: Illuminance event from ${sensorSwitch.displayName}, but no associated room found. Ignoring."
		return
	}

	if (evt.value == null) return
	Double currentLux
	try {
		currentLux = evt.value as Double
	} catch (e) {
		log.warn "Dynamic LEDs: Could not parse illuminance value '${evt.value}' from ${sensorSwitch.displayName}: ${e.message}"
		return
	}

	long nowMs = now()
	Map roomStatus = (state.roomLedStatus && state.roomLedStatus[roomName]) ? (state.roomLedStatus[roomName] as Map) : [:]
	long lastUpdatedTime = roomStatus.lastUpdated ?: 0L
	Integer cooldownMinutes = (settings.dynamicLedCooldownMinutes != null) ? (settings.dynamicLedCooldownMinutes as Integer) : 1
	long cooldownMs = cooldownMinutes * 60 * 1000L

	// 1. Cooldown check (default 1 minute)
	if ((nowMs - lastUpdatedTime) < cooldownMs) {
		long secondsRemaining = ((cooldownMs - (nowMs - lastUpdatedTime)) / 1000).toLong()
		log.debug "Dynamic LEDs: Cooldown active for room '${roomName}' (${secondsRemaining}s remaining). Skipping update for ${currentLux} Lux."
		return
	}

	// 2. 20% change and 5 Lux floor check
	Double lastRecordedLux = roomStatus.lastLux != null ? (roomStatus.lastLux as Double) : null
	Double percentThreshold = (settings.dynamicLedPercentChange != null) ? (settings.dynamicLedPercentChange as Double) : 20.0
	Double minLuxDelta = (settings.dynamicLedMinLuxDelta != null) ? (settings.dynamicLedMinLuxDelta as Double) : 5.0

	if (lastRecordedLux != null) {
		Double deltaLux = Math.abs(currentLux - lastRecordedLux)
		Double baseLux = Math.max(lastRecordedLux, 5.0)
		Double percentChange = (deltaLux / baseLux) * 100.0

		if (deltaLux < minLuxDelta || percentChange < percentThreshold) {
			log.debug "Dynamic LEDs: Lux change in room '${roomName}' (current: ${currentLux}, last: ${lastRecordedLux}, delta: ${deltaLux.round(1)}, change: ${percentChange.round(1)}%) did not meet thresholds (min delta: ${minLuxDelta}, min %: ${percentThreshold}%). Skipping."
			return
		}
	}

	// 3. Compute target ON and OFF brightness
	Map targetLevels = calculateTargetLedLevels(currentLux)
	Integer targetOn = targetLevels.on
	Integer targetOff = targetLevels.off

	// 4. Integer output deadband check against current applied values
	Integer currentAppliedOn = roomStatus.currentOn != null ? (roomStatus.currentOn as Integer) : null
	Integer currentAppliedOff = roomStatus.currentOff != null ? (roomStatus.currentOff as Integer) : null

	if (currentAppliedOn != null && currentAppliedOff != null && targetOn == currentAppliedOn && targetOff == currentAppliedOff) {
		log.debug "Dynamic LEDs: Target LED levels for room '${roomName}' (ON: ${targetOn}%, OFF: ${targetOff}%) match current levels. Skipping commands."
		state.roomLedStatus[roomName] = [
			lastUpdated: nowMs,
			lastLux: currentLux,
			currentOn: targetOn,
			currentOff: targetOff
		]
		return
	}

	// 5. Build queue items for all switches in the room
	String currentMode = state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	List<Map> itemsToQueue = []

	settings.controlledSwitches?.each { sw ->
		if (!sw) return
		String swId = sw.id.toString()
		String swRoom = getSwitchRoomName(swId)
		if (swRoom && swRoom.equalsIgnoreCase(roomName)) {
			if (isZoneBypassed(swId, currentMode)) {
				log.debug "Dynamic LEDs: Switch '${sw.displayName}' in room '${roomName}' is zone-bypassed in mode '${currentMode}'. Keeping LEDs at 0."
				itemsToQueue << [id: swId, on: 0, off: 0]
			} else {
				itemsToQueue << [id: swId, on: targetOn, off: targetOff]
			}
		}
	}

	if (itemsToQueue) {
		log.info "Dynamic LEDs: Adjusting LEDs in room '${roomName}' based on ${currentLux} Lux (Delta: ${lastRecordedLux != null ? Math.round(Math.abs(currentLux - lastRecordedLux)) : 'initial'} Lux). Setting ON: ${targetOn}%, OFF: ${targetOff}% across ${itemsToQueue.size()} switch(es)."
		state.roomLedStatus[roomName] = [
			lastUpdated: nowMs,
			lastLux: currentLux,
			currentOn: targetOn,
			currentOff: targetOff
		]
		enqueueLedUpdates(itemsToQueue)
	}
}

private void setMotionBypass(String switchId) {
	state.motionBypass = state.motionBypass ?: [:]
	state.motionBypass[switchId] = true
	log.info "Motion bypass set (ignored) for switch ID ${switchId}."
}

private void clearMotionBypass(String switchId) {
	if (state.motionBypass) {
		state.motionBypass.remove(switchId)
		log.info "Motion bypass cleared for switch ID ${switchId}."
	}
}


/**
 * ============================== LED BAR SYNC ==============================
 * Keeps each switch's on/off state and level (which drive its LED bar) matched
 * to the lights it mirrors. Three triggers feed one debounced, staggered queue:
 *   1. Events: any switch/level event on any mirrored light.
 *   2. Post-action nudges: scheduled after app-initiated light changes.
 *   3. Reconciliation: a periodic sweep recomputing every switch from live
 *      device values, so missed or deduped events self-correct.
 * Syncs are idempotent: commands are sent only when live values differ.
 */

def lightStateSyncHandler(evt) {
	List switchIds = state.lightToSyncSwitchIds ? state.lightToSyncSwitchIds[evt.device.id.toString()] : null
	if (switchIds) requestLedBarSync(switchIds)
}

private void subscribeToSyncSourceLights() {
	if (!state.lightToSyncSwitchIds) return
	state.lightToSyncSwitchIds.keySet().each { lightId ->
		def light = getDevicesById(lightId.toString(), settings.controlledLightsAndScenes)
		if (!light) {
			log.warn "LED bar sync: could not find light ID ${lightId} to subscribe."
			return
		}
		subscribe(light, "switch", lightStateSyncHandler)
		if (light.hasAttribute("level")) subscribe(light, "level", lightStateSyncHandler)
	}
	log.info "LED bar sync: subscribed to ${state.lightToSyncSwitchIds.size()} light(s)."
}

/**
 * Queues switches for an LED bar sync. Debounced: bursts of light events (a scene
 * hitting several bulbs, a dim ramp) collapse into one evaluation per switch after
 * the burst settles.
 */
private void requestLedBarSync(Collection switchIds, Long delayMs = 750L) {
	if (!switchIds) return
	Map pending = state.pendingLedBarSync ?: [:]
	switchIds.each { id -> if (id) pending[id.toString()] = true }
	state.pendingLedBarSync = pending
	if (pending) runInMillis(delayMs, "processLedBarSyncQueue")
}

/** Maps changed lights to the switches mirroring them, then queues those switches. */
private void requestLedBarSyncForLights(Collection lightIds, Long delayMs = 2500L) {
	if (!lightIds || !state.lightToSyncSwitchIds) return
	Set switchIds = [] as Set
	lightIds.each { lightId ->
		List ids = state.lightToSyncSwitchIds[lightId?.toString()]
		if (ids) switchIds.addAll(ids)
	}
	if (switchIds) requestLedBarSync(switchIds, delayMs)
}

def processLedBarSyncQueue() {
	Map pending = state.pendingLedBarSync ?: [:]
	if (!pending) return
	String switchId = pending.keySet().first()
	pending.remove(switchId)
	state.pendingLedBarSync = pending
	syncLedBar(switchId)
	// Stagger remaining switches so a full reconcile cannot flood the mesh
	if (pending) runInMillis(250, "processLedBarSyncQueue")
}

/**
 * Computes what the LED bar should show from live (uncached) values of the mirrored
 * lights: ON if any light is on; level = the brightest on light.
 */
private Map computeLedBarReference(String switchId) {
	List sourceIds = state.switchSyncSourceIds ? state.switchSyncSourceIds[switchId] : null
	if (!sourceIds) return null
	def lights = getDevicesById(sourceIds, settings.controlledLightsAndScenes)
	if (!lights) return null
	def onLights = lights.findAll { it.currentValue("switch", true) == "on" }
	if (!onLights) return [on: false, level: null]
	List levels = onLights.findAll { it.hasAttribute("level") }
		.collect { it.currentValue("level", true) }
		.findAll { it != null }
		.collect { Math.round((it as Double).doubleValue()) as Integer }
	return [on: true, level: levels ? levels.max() : null]
}

/**
 * Applies the computed reference to one switch, sending only commands whose target
 * differs from the switch's live (uncached) state.
 */
private void syncLedBar(String switchId) {
	def sw = getDevicesById(switchId, settings.controlledSwitches)
	if (!sw) return
	if (state.switchInfoMap && state.switchInfoMap[switchId]?.type == "local") return
	// LED brightness shares the bar sync triggers (light events, nudges, reconcile):
	// recompute it here so sensor-less rooms react when their lights turn on/off.
	refreshLedBrightness(switchId)
	Map ref = computeLedBarReference(switchId)
	if (ref == null) return

	try {
		String currentSwitchState = sw.hasAttribute("switch") ? sw.currentValue("switch", true) : null

		if (!ref.on) {
			if (currentSwitchState != "off" && sw.hasCommand("off")) {
				log.info "LED bar sync: ${sw.displayName} -> OFF (all mirrored lights off)."
				sw.off()
			}
			return
		}

		boolean levelSent = false
		if (ref.level != null && sw.hasCommand("setLevel") && sw.hasAttribute("level")) {
			def rawLevel = sw.currentValue("level", true)
			Integer currentLevel = (rawLevel != null) ? (Math.round((rawLevel as Double).doubleValue()) as Integer) : null
			if (currentLevel == null || Math.abs(currentLevel - (ref.level as Integer)) > LEVEL_MATCH_TOLERANCE) {
				log.info "LED bar sync: ${sw.displayName} -> level ${ref.level}% (was ${currentLevel != null ? "${currentLevel}%" : 'unknown'})."
				sw.setLevel(ref.level)
				levelSent = true
			}
		}
		// setLevel implies ON on dimmers; send an explicit on() only when no level was sent
		if (currentSwitchState != "on" && !levelSent && sw.hasCommand("on")) {
			log.info "LED bar sync: ${sw.displayName} -> ON."
			sw.on()
		}
	} catch (e) {
		log.error "LED bar sync: error syncing ${sw.displayName}: ${e.message}"
	}
}

/**
 * Periodic reconciliation backstop. Recomputes every switch from live device state so
 * any missed events self-correct within one cycle. Switches with a button press in the
 * last 60 seconds are skipped so the sweep never fights someone at the switch.
 */
def reconcileAllLedBars() {
	if (!state.switchSyncSourceIds) return
	long nowMs = now()
	List idsToSync = state.switchSyncSourceIds.keySet().findAll { switchId ->
		long lastTouch = ((state.lastSwitchInteraction ? state.lastSwitchInteraction[switchId] : null) ?: 0L) as long
		(nowMs - lastTouch) > 60000L
	}.collect { it.toString() }
	if (idsToSync) requestLedBarSync(idsToSync)
}

def buttonHandler(evt) {
	def triggeringSwitch = evt.device; def switchId = triggeringSwitch.id.toString()
	if (!evt.value?.toString()?.isInteger()) {
		log.warn "Ignoring non-numeric button value '${evt.value}' from ${triggeringSwitch.displayName}."
		return
	}
	def buttonNumber = evt.value.toInteger(); def buttonEvent = evt.name
	
	def switchInfo = state.switchInfoMap[switchId]
	if (switchInfo?.type == "local") { // Local switches ignored for button actions
		log.info "Button ${buttonNumber} (${buttonEvent}) on Local Switch ${triggeringSwitch.displayName}. Ignored."
		return
	}

	log.info "Button ${buttonNumber} (${buttonEvent}) on ${triggeringSwitch.displayName} (Type: ${switchInfo?.type ?: 'Unknown'})"
	cancelModeTimeout(triggeringSwitch)

	// Track interaction so the periodic LED bar reconcile never fights someone at the switch
	state.lastSwitchInteraction = state.lastSwitchInteraction ?: [:]
	state.lastSwitchInteraction[switchId] = now()

	state.activeSwitchMode = state.activeSwitchMode ?: [:]
	def activeMode = state.activeSwitchMode[switchId] ?: "normal"

	// Music Mode Toggle
	def musicModeBtnNum = settings.musicModeButtonNumber != null ? (settings.musicModeButtonNumber as Integer) : 9
	def musicModeBtnEvt = settings.musicModeButtonEvent ?: "pushed"
	if (buttonNumber == musicModeBtnNum && buttonEvent == musicModeBtnEvt) {
		if (activeMode == "music") {
			exitMode(switchId, "music")
		} else {
			enterMode(triggeringSwitch, "music")
		}
		return
	}

	// Scene Mode Toggle / Leave Any Mode
	if (buttonNumber == (settings.configButtonNumber as Integer) && buttonEvent == settings.configButtonEvent) {
		if (activeMode != "normal") {
			exitMode(switchId, activeMode) 
		} else if (!isSceneOnlySwitch(switchId) && state.sortedSwitchSceneIds[switchId]?.any()) { // Check if scenes exist
			enterMode(triggeringSwitch, "scene")
		} else {
			log.info "Config button on ${triggeringSwitch.displayName}, but no scenes. Scene Mode not activated."
		}
		return 
	}

	if (activeMode == "music") {
		handleMusicModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	} else if (activeMode == "scene") {
		handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	} else {
		handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	}
}

def motionHandler(evt) {
	def triggeringSwitch = evt.device
	def switchId = triggeringSwitch.id.toString()
	def motionState = evt.value?.toString()

	boolean isMotionEnabled = settings.motionEnabledSwitches?.any { val ->
		(val instanceof String) ? (val == switchId) : (val?.id?.toString() == switchId)
	}
	if (!isMotionEnabled) {
		log.warn "Motion event received from ${triggeringSwitch.displayName} but it is not enabled in settings. Ignored."
		return
	}

	def switchInfo = state.switchInfoMap[switchId]
	if (switchInfo?.type == "local") {
		log.info "Motion ${motionState} on Local Switch ${triggeringSwitch.displayName}. Ignored."
		return
	}

	log.info "Motion ${motionState} on ${triggeringSwitch.displayName}"
	cancelModeTimeout(triggeringSwitch)

	if (motionState == "active") {
		state.lastMotionActiveTime = state.lastMotionActiveTime ?: [:]
		state.lastMotionActiveTime[switchId] = now()
	}

	if (state.motionBypass && state.motionBypass[switchId]) {
		if (motionState == "active") {
			log.info "Motion active on ${triggeringSwitch.displayName} ignored because motion bypass is active."
		} else if (motionState == "inactive") {
			log.info "Motion inactive on ${triggeringSwitch.displayName} ignored because motion bypass is active. Scheduling bypass clear in 10 minutes."
			runIn(600, "checkClearMotionBypass", [data: [switchId: switchId, scheduledAt: now()]])
		}
		return
	}

	def buttonNumber
	def buttonEvent

	if (motionState == "active") {
		if (isZoneBypassed(switchId, state.currentLocationMode)) {
			log.info "Motion active on ${triggeringSwitch.displayName} ignored because auto-on is disabled for this zone in '${state.currentLocationMode}' mode."
			return
		}

		boolean hasIlluminance = triggeringSwitch.hasCapability("IlluminanceMeasurement") || triggeringSwitch.hasCapability("Illuminance Measurement")
		if (hasIlluminance && settings.motionIlluminanceThreshold != null) {
			def currentIlluminance = triggeringSwitch.currentValue("illuminance")
			if (currentIlluminance != null) {
				def currentLux = currentIlluminance as Double
				def thresholdLux = settings.motionIlluminanceThreshold as Double
				if (currentLux >= thresholdLux) {
					log.info "Motion active on ${triggeringSwitch.displayName} ignored because ambient light level (${currentLux} Lux) is at or above the threshold (${thresholdLux} Lux)."
					return
				}
			} else {
				log.warn "Illuminance measurement not available on ${triggeringSwitch.displayName} (null value). Proceeding."
			}
		}

		buttonNumber = (settings.singleTapUpButtonNumber != null) ? (settings.singleTapUpButtonNumber as Integer) : 1
		buttonEvent = settings.singleTapUpButtonEvent ?: "pushed"
	} else if (motionState == "inactive") {
		buttonNumber = (settings.singleTapDownButtonNumber != null) ? (settings.singleTapDownButtonNumber as Integer) : 1
		buttonEvent = settings.singleTapDownButtonEvent ?: "held"
	} else {
		log.warn "Ignoring unknown motion state '${motionState}' from ${triggeringSwitch.displayName}."
		return
	}

	log.info "Motion ${motionState} on ${triggeringSwitch.displayName} mapped to Button ${buttonNumber} (${buttonEvent})"
	state.activeSwitchMode = state.activeSwitchMode ?: [:]
	def activeMode = state.activeSwitchMode[switchId] ?: "normal"
	if (activeMode == "scene") {
		handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	} else if (activeMode == "music") {
		handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent)
		scheduleModeTimeout(triggeringSwitch, "music")
	} else {
		handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	}
}

def checkClearMotionBypass(data) {
	def switchId = data?.switchId?.toString()
	def scheduledAt = data?.scheduledAt
	if (!switchId || !scheduledAt) return

	state.lastMotionActiveTime = state.lastMotionActiveTime ?: [:]
	long lastActive = state.lastMotionActiveTime[switchId] ?: 0L
	long elapsedMs = now() - lastActive

	// 10 minutes = 600,000 milliseconds
	if (elapsedMs >= 600000) {
		log.info "No motion detected for 10 minutes on switch ID ${switchId}. Clearing motion bypass."
		clearMotionBypass(switchId)
	} else {
		log.debug "Motion was detected ${elapsedMs / 1000}s ago (less than 10 minutes). Keeping motion bypass active."
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
	setMotionBypass(switchId)
	// Scenes change bulbs outside this app's direct commands; nudge the LED bar sync
	// for everything this switch could be mirroring once the scene has settled.
	requestLedBarSyncForLights(((state.switchAreaLights[switchId] ?: []) + (state.switchRoomLights[switchId] ?: []) + (state.switchZoneLights[switchId] ?: [])).unique(), 3000L)
	return true
}

private void handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	boolean sceneActionTaken = false
	if (buttonNumber == 1 && buttonEvent == "pushed") sceneActionTaken = cycleScene(triggeringSwitch, "next")
	else if (buttonNumber == 1 && buttonEvent == "held") sceneActionTaken = cycleScene(triggeringSwitch, "previous")
	else if (!(buttonNumber == 1 && buttonEvent == "released")) { // Any other button (not release of B1) exits scene mode
		log.info "Exiting scene mode for ${triggeringSwitch.displayName} due to button ${buttonNumber} ${buttonEvent}."
		exitMode(triggeringSwitch.id.toString(), "scene")
	}
	if (sceneActionTaken) scheduleModeTimeout(triggeringSwitch, "scene")
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


/**
 * Applies level/CT settings to a list of lights. Lights without SwitchLevel are simply turned on.
 */
private void applyToLights(lights, Integer level, Integer ct, Boolean enableCt) {
	lights?.each { light ->
		try {
			boolean powerCmdSent = false
			if (light.hasCapability("SwitchLevel")) { light.setLevel(level); powerCmdSent = true }
			else if (light.hasCommand("on")) { light.on(); powerCmdSent = true }

			if (powerCmdSent && enableCt && light.hasCapability("ColorTemperature") && ct != null) {
				light.setColorTemperature(ct)
			}
		} catch (e) { log.error "Error applying settings to light ${light?.displayName}: ${e.message}" }
	}
}

private void turnOffLights(lights) {
	lights?.each { light ->
		try { if (light.hasCommand("off")) light.off() }
		catch (e) { log.error "Error turning off light ${light?.displayName}: ${e.message}" }
	}
}

private void handleAreaOn(triggeringSwitch, areaLights) {
	if (!areaLights?.any()) {
		log.warn "No area lights for ${triggeringSwitch.displayName}. Area On skipped."
		return
	}

	Map effectiveTargetSettings
	String modeUsedForSettings = state.currentLocationMode ?: "current (unknown)"

	// Judge the area by the same live group reference the LED bar sync uses:
	// "on" if any light is on, at the level of the brightest lit light.
	Map areaRef = computeLedBarReference(triggeringSwitch.id.toString())
	if (areaRef?.on) {
		Map currentModeSettings = getModeSettings()
		boolean levelMatchesCurrentMode = (areaRef.level != null && Math.abs((areaRef.level as Integer) - (currentModeSettings.level as Integer)) <= LEVEL_MATCH_TOLERANCE)

		if (levelMatchesCurrentMode) { // Area on AND at mode level -> boost to global default level
			log.info "Area lights ON at mode level. Setting area to global default brightness; CT unchanged."
			effectiveTargetSettings = [level: state.globalDefaultLevel, ct: null, enableCt: false]
			modeUsedForSettings = "Global Default Level (override)"
		} else { // Area on but differs from mode -> set to current mode settings
			effectiveTargetSettings = currentModeSettings
			log.info "Area lights ON at ${areaRef.level}%, differing from mode level. Setting area to current mode settings."
		}
	} else { // Area off -> set to current mode settings
		effectiveTargetSettings = getModeSettings()
		log.info "Area lights OFF. Setting area to current mode settings."
	}

	def targetLevel = effectiveTargetSettings.level
	def targetCt = effectiveTargetSettings.ct
	def shouldSetCt = effectiveTargetSettings.enableCt 

	log.info "handleAreaOn for ${triggeringSwitch.displayName} (${modeUsedForSettings}): ON ${areaLights.size()} lights. Lvl:${targetLevel}%, CT:${targetCt}K (SetCT:${shouldSetCt})"

	applyToLights(areaLights, targetLevel, targetCt, shouldSetCt)
	clearMotionBypass(triggeringSwitch.id.toString())
	requestLedBarSyncForLights(areaLights.collect { it.id.toString() })
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
		applyToLights(lightsToControl, targetSettings.level, targetSettings.ct, targetSettings.enableCt)
		clearMotionBypass(switchId)
		requestLedBarSyncForLights(lightsToControlIds)
	}
}


private void handleAreaOff(triggeringSwitch, areaLights) {
	if (areaLights?.any()) {
		log.info "handleAreaOff for ${triggeringSwitch.displayName}: Turning OFF ${areaLights.size()} area light(s)."
		turnOffLights(areaLights)
		setMotionBypass(triggeringSwitch.id.toString())
		requestLedBarSyncForLights(areaLights.collect { it.id.toString() })
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
			def lightNames = lightsToTurnOff.collect { it.displayName }.join(', ')
			log.info "Turning off ${lightsToTurnOff.size()} lights (${lightNames}) in ${scope} for scene-only switch ${triggeringSwitch.displayName}."
			turnOffLights(lightsToTurnOff)
			setMotionBypass(switchId)
			requestLedBarSyncForLights(lightsToTurnOffIds)
		} else {
			log.warn "No light devices for ${scope} for scene-only switch ${triggeringSwitch.displayName}."
		}
	} else {
		log.warn "No room/zone lights for scene-only switch ${triggeringSwitch.displayName}."
	}

	state.sceneIndex[switchId] = -1
}

/**
 * Hold-to-dim start. Lights already on begin ramping immediately. Lights that are off
 * first turn on at 2%; if the user is still holding the paddle after half a second they
 * join the ramp, and if the paddle was released first they stay at 2% — so a quick
 * hold-and-release doubles as a "turn on dimly" gesture.
 */
private void handleDimStart(triggeringSwitch, dimmableAreaLights, String direction) {
	if (!dimmableAreaLights?.any()) {
		log.info "No dimmable area lights for ${triggeringSwitch.displayName}. Dimming skipped."
		return
	}
	log.info "handleDimStart for ${triggeringSwitch.displayName}: Start level change '${direction}' for ${dimmableAreaLights.size()} light(s)."

	if (direction != "up") {
		startRamp(dimmableAreaLights, direction)
		return
	}

	List offLights = dimmableAreaLights.findAll { it.hasCommand('setLevel') && it.currentValue('switch', true) == 'off' }
	List onLights = dimmableAreaLights.findAll { light -> !offLights.any { it.id == light.id } }

	offLights.each { light ->
		try { light.setLevel(2) }
		catch (e) { log.error "Error setting min level on ${light.displayName}: ${e.message}" }
	}
	startRamp(onLights, "up")

	if (offLights) {
		String switchId = triggeringSwitch.id.toString()
		long token = now()
		state.pendingRampUp = state.pendingRampUp ?: [:]
		state.pendingRampUp[switchId] = [lightIds: offLights.collect { it.id.toString() }, token: token]
		runInMillis(500, "startDelayedRampUp", [data: [switchId: switchId, token: token], overwrite: false])
	}
}

private void startRamp(lights, String direction) {
	lights?.each { light ->
		try { if (light.hasCommand('startLevelChange')) light.startLevelChange(direction) }
		catch (e) { log.error "Error startLevelChange(${direction}) on ${light.displayName}: ${e.message}" }
	}
}

/** Fires half a second after hold-up turned off lights on at 2%. A release in the
 *  meantime removed the pending entry (or replaced its token), making this a no-op. */
def startDelayedRampUp(data) {
	String switchId = data?.switchId?.toString()
	if (!switchId) return
	Map pending = state.pendingRampUp ? state.pendingRampUp[switchId] : null
	if (pending == null || (pending.token as Long) != (data.token as Long)) return
	state.pendingRampUp.remove(switchId)
	def lights = getDevicesById(pending.lightIds, settings.controlledLightsAndScenes)
	if (lights) {
		log.info "Paddle still held after 2% turn-on: ramping up ${lights.size()} light(s)."
		startRamp(lights, "up")
	}
}

private void handleDimStop(triggeringSwitch, dimmableAreaLights) {
	// Released within the half-second window: cancel the pending ramp so lights that
	// just turned on stay at 2% (the "turn on dimly" gesture).
	if (state.pendingRampUp?.remove(triggeringSwitch.id.toString()) != null) {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: released before ramp delay. Lights stay at 2%."
	}

	if (dimmableAreaLights?.any()) {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: Stop level change for ${dimmableAreaLights.size()} light(s)."
		dimmableAreaLights.each { light ->
			try { if(light.hasCommand('stopLevelChange')) light.stopLevelChange() }
			catch (e) { log.error "Error stopLevelChange() on ${light.displayName}: ${e.message}"}
		}

		// Schedule a refresh on the first dimmable light to query its final settled level
		def firstLightId = dimmableAreaLights.first().id.toString()
		runInMillis(500, "refreshDimmableLight", [data: [lightId: firstLightId]])
		requestLedBarSyncForLights(dimmableAreaLights.collect { it.id.toString() })
	} else {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: No dimmable lights to stop."
	}
}

def refreshDimmableLight(data) {
	def lightId = data?.lightId?.toString()
	if (!lightId) return
	def light = getDevicesById(lightId, settings.controlledLightsAndScenes)
	if (light && light.hasCommand('refresh')) {
		log.debug "Refreshing ${light.displayName} so the LED bar sync sees its settled level."
		try { light.refresh() } catch (e) { log.error "Error refreshing light ${light.displayName}: ${e.message}" }
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
		turnOffLights(lightsToControl)
		setMotionBypass(switchId)
		requestLedBarSyncForLights(lightsToControlIds)
	}
}


def enterMode(triggeringSwitch, String mode) {
	def switchId = triggeringSwitch.id.toString()
	state.activeSwitchMode = state.activeSwitchMode ?: [:]
	
	// Automatic Mutual Exclusivity: exit whatever mode is currently active
	def prevMode = state.activeSwitchMode[switchId]
	if (prevMode && prevMode != mode) {
		exitMode(switchId, prevMode)
	}

	state.activeSwitchMode[switchId] = mode

	// Map of mode names to their respective LED effects and custom hues (null defaults to device setting)
	def modeLEDConfigs = [
		scene: [effect: "chase", hue: null],
		music: [effect: "chase", hue: 21] // 21 is Orange hue
	]

	def ledConfig = modeLEDConfigs[mode.toLowerCase()]
	if (ledConfig) {
		setLedEffect(triggeringSwitch, ledConfig.effect, ledConfig.hue)
	}
	
	scheduleModeTimeout(triggeringSwitch, mode)
	log.info "Entered ${mode} mode for ${triggeringSwitch.displayName}"
}

def exitMode(data, String modeOverride = null) {
	def switchId = null
	def targetMode = modeOverride
	
	if (data instanceof Map) {
		switchId = data.switchId?.toString()
		targetMode = data.mode ?: modeOverride
	} else if (data != null) {
		switchId = data.toString()
	}

	if (!switchId) {
		log.warn "exitMode called without switchId. Clearing all active modes."
		state.activeSwitchMode = state.activeSwitchMode ?: [:]
		state.lastModeActivity = [:]
		state.activeVolumeLoops = [:]
		if (settings.musicDevice?.hasCommand("stopLevelChange")) {
			try { settings.musicDevice.stopLevelChange() } catch(e) {}
		}
		
		// Collect keys first to avoid ConcurrentModificationException while modifying map entries inside the loop
		(state.activeSwitchMode.keySet() ?: []).collect().each { id ->
			def activeMode = state.activeSwitchMode[id]
			if (activeMode) {
				state.activeSwitchMode[id] = null
				def sw = getDevicesById(id, settings.controlledSwitches)
				if (sw) setLedEffect(sw, "solid")
				log.info "Exited ${activeMode} mode for ${sw?.displayName ?: id} (general clear)."
			}
		}
		return
	}

	state.activeSwitchMode = state.activeSwitchMode ?: [:]
	def activeMode = state.activeSwitchMode[switchId]
	if (activeMode && (targetMode == null || activeMode == targetMode)) {
		state.activeSwitchMode[switchId] = null
		def triggeringSwitch = getDevicesById(switchId, settings.controlledSwitches)
		if (triggeringSwitch) {
			setLedEffect(triggeringSwitch, "solid")
			log.info "Exited ${activeMode} mode for ${triggeringSwitch.displayName}"
		} else {
			log.warn "exitMode: Switch ID ${switchId} not found. Clearing state."
		}
		
		if (state.lastModeActivity) {
			state.lastModeActivity.remove(switchId)
		}
		stopVolumeLoop(switchId)
		if (activeMode == "music" && settings.musicDevice?.hasCommand("stopLevelChange")) {
			try { settings.musicDevice.stopLevelChange() } catch(e) {}
		}
	}
}

private void nextSong() {
	if (!settings.musicDevice) return
	log.info "Music Mode: Next track on ${settings.musicDevice.displayName}"
	try {
		if (settings.musicDevice.hasCommand("nextTrack")) {
			settings.musicDevice.nextTrack()
		} else {
			log.warn "${settings.musicDevice.displayName} does not support nextTrack."
		}
	} catch (e) {
		log.error "Failed to skip to next track: ${e.message}"
	}
}

private void prevSong() {
	if (!settings.musicDevice) return
	log.info "Music Mode: Previous track on ${settings.musicDevice.displayName}"
	try {
		if (settings.musicDevice.hasCommand("previousTrack")) {
			settings.musicDevice.previousTrack()
		} else {
			log.warn "${settings.musicDevice.displayName} does not support previousTrack."
		}
	} catch (e) {
		log.error "Failed to skip to previous track: ${e.message}"
	}
}

private void volumeUp() {
	if (!settings.musicDevice) return
	log.info "Music Mode: Volume up on ${settings.musicDevice.displayName}"
	try {
		if (settings.musicDevice.hasCommand("volumeUp")) {
			settings.musicDevice.volumeUp()
		} else {
			def currentVol = settings.musicDevice.currentValue("volume")
			int currentVolInt = (currentVol instanceof Number) ? currentVol.toInteger() : 0
			int newVol = Math.min(currentVolInt + 5, 100)
			if (settings.musicDevice.hasCommand("setVolume")) {
				settings.musicDevice.setVolume(newVol)
			} else {
				log.warn "${settings.musicDevice.displayName} supports neither volumeUp nor setVolume."
			}
		}
	} catch (e) {
		log.error "Failed to increase volume: ${e.message}"
	}
}

private void volumeDown() {
	if (!settings.musicDevice) return
	log.info "Music Mode: Volume down on ${settings.musicDevice.displayName}"
	try {
		if (settings.musicDevice.hasCommand("volumeDown")) {
			settings.musicDevice.volumeDown()
		} else {
			def currentVol = settings.musicDevice.currentValue("volume")
			int currentVolInt = (currentVol instanceof Number) ? currentVol.toInteger() : 0
			int newVol = Math.max(currentVolInt - 5, 0)
			if (settings.musicDevice.hasCommand("setVolume")) {
				settings.musicDevice.setVolume(newVol)
			} else {
				log.warn "${settings.musicDevice.displayName} supports neither volumeDown nor setVolume."
			}
		}
	} catch (e) {
		log.error "Failed to decrease volume: ${e.message}"
	}
}

private void stopMusic() {
	if (!settings.musicDevice) return
	log.info "Music Mode: Stop on ${settings.musicDevice.displayName}"
	try {
		if (settings.musicDevice.hasCommand("stop")) {
			settings.musicDevice.stop()
		} else if (settings.musicDevice.hasCommand("pause")) {
			settings.musicDevice.pause()
		} else {
			log.warn "${settings.musicDevice.displayName} supports neither stop nor pause."
		}
	} catch (e) {
		log.error "Failed to stop music: ${e.message}"
	}
}

private void playFirstPlaylist() {
	if (!settings.musicDevice) return
	def musicDevice = settings.musicDevice
	def playlistName = null
	try {
		def playlistsAttr = musicDevice.currentValue("supportedPlaylists") ?: musicDevice.currentValue("playlists")
		if (playlistsAttr) {
			if (playlistsAttr instanceof String) {
				try {
					def list = new groovy.json.JsonSlurper().parseText(playlistsAttr)
					if (list && list.size() > 0) {
						playlistName = list[0]
					}
				} catch (e) {
					log.warn "Failed to parse playlists JSON: ${e.message}"
				}
			} else if (playlistsAttr instanceof List && playlistsAttr.size() > 0) {
				playlistName = playlistsAttr[0]
			}
		}
	} catch (e) {
		log.warn "Error reading playlists from ${musicDevice.displayName}: ${e.message}"
	}

	if (playlistName && musicDevice.hasCommand("playPlaylist")) {
		log.info "Music Mode: Playing first playlist '${playlistName}' on ${musicDevice.displayName}"
		try {
			musicDevice.playPlaylist(playlistName)
			return
		} catch (e) {
			log.error "Failed to play playlist '${playlistName}' via playPlaylist: ${e.message}"
		}
	}

	log.info "Music Mode: Falling back to standard play() on ${musicDevice.displayName}"
	try {
		if (musicDevice.hasCommand("play")) {
			musicDevice.play()
		} else {
			log.warn "${musicDevice.displayName} does not support play command."
		}
	} catch (e) {
		log.error "Failed to play: ${e.message}"
	}
}

private void handleMusicModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	if (!settings.musicDevice) {
		log.warn "Music Mode action triggered, but no music device is selected."
		return
	}
	def switchId = triggeringSwitch.id.toString()

	// Tap up goes to next song. Tap up while nothing is playing plays first playlist.
	if (buttonNumber == (settings.singleTapUpButtonNumber as Integer) && buttonEvent == settings.singleTapUpButtonEvent) {
		def status = settings.musicDevice.currentValue("status")
		if (status?.toString()?.toLowerCase() != "playing") {
			playFirstPlaylist()
		} else {
			nextSong()
		}
	}
	// Tap down goes to prev song
	else if (buttonNumber == (settings.singleTapDownButtonNumber as Integer) && buttonEvent == settings.singleTapDownButtonEvent) {
		prevSong()
	}
	// Hold up starts raising volume
	else if (buttonNumber == (settings.holdUpButtonNumber as Integer) && buttonEvent == settings.holdUpButtonEvent) {
		if (settings.musicDevice.hasCommand("startLevelChange")) {
			try { settings.musicDevice.startLevelChange("up") }
			catch (e) { log.error "Error calling startLevelChange(up) on ${settings.musicDevice.displayName}: ${e.message}" }
		} else {
			startVolumeLoop(switchId, "up")
		}
	}
	// Release up stops raising volume
	else if (buttonNumber == (settings.releaseUpButtonNumber as Integer) && buttonEvent == settings.releaseUpButtonEvent) {
		if (settings.musicDevice.hasCommand("stopLevelChange")) {
			try { settings.musicDevice.stopLevelChange() }
			catch (e) { log.error "Error calling stopLevelChange on ${settings.musicDevice.displayName}: ${e.message}" }
		} else {
			stopVolumeLoop(switchId)
		}
	}
	// Hold down starts lowering volume
	else if (buttonNumber == (settings.holdDownButtonNumber as Integer) && buttonEvent == settings.holdDownButtonEvent) {
		if (settings.musicDevice.hasCommand("startLevelChange")) {
			try { settings.musicDevice.startLevelChange("down") }
			catch (e) { log.error "Error calling startLevelChange(down) on ${settings.musicDevice.displayName}: ${e.message}" }
		} else {
			startVolumeLoop(switchId, "down")
		}
	}
	// Release down stops lowering volume
	else if (buttonNumber == (settings.releaseDownButtonNumber as Integer) && buttonEvent == settings.releaseDownButtonEvent) {
		if (settings.musicDevice.hasCommand("stopLevelChange")) {
			try { settings.musicDevice.stopLevelChange() }
			catch (e) { log.error "Error calling stopLevelChange on ${settings.musicDevice.displayName}: ${e.message}" }
		} else {
			stopVolumeLoop(switchId)
		}
	}
	// Tap down 2x stops playing
	else if (buttonNumber == (settings.doubleTapDownButtonNumber as Integer) && buttonEvent == settings.doubleTapDownButtonEvent) {
		stopMusic()
	}

	scheduleModeTimeout(triggeringSwitch, "music")
}

def startVolumeLoop(String switchId, String direction) {
	state.activeVolumeLoops = state.activeVolumeLoops ?: [:]
	
	// Generate a unique transaction timestamp for this loop start
	long runId = now()
	state.activeVolumeLoops[switchId] = [direction: direction, runId: runId]
	
	runVolumeLoop([switchId: switchId, direction: direction, runId: runId])
}

def stopVolumeLoop(String switchId) {
	state.activeVolumeLoops = state.activeVolumeLoops ?: [:]
	state.activeVolumeLoops.remove(switchId)
}

def runVolumeLoop(Map data) {
	def switchId = data.switchId
	def direction = data.direction
	def runId = data.runId
	
	state.activeVolumeLoops = state.activeVolumeLoops ?: [:]
	def activeLoop = state.activeVolumeLoops[switchId]
	
	if (!activeLoop || activeLoop.direction != direction || activeLoop.runId != runId) {
		log.debug "Volume loop for switch ${switchId} in direction ${direction} (runId: ${runId}) is no longer active. Stopping."
		return
	}

	if (!settings.musicDevice) {
		log.warn "Volume loop: no music device is selected."
		stopVolumeLoop(switchId)
		return
	}

	// Change volume
	if (direction == "up") {
		volumeUp()
	} else {
		volumeDown()
	}

	// Schedule the next step in 500ms
	runInMillis(500, "runVolumeLoop", [data: [switchId: switchId, direction: direction, runId: runId], overwrite: false])
}

def scheduleModeTimeout(triggeringSwitch, String mode, Integer customTimeout = null) {
	def timeoutSetting = customTimeout ?: settings.sceneModeTimeout
	def timeoutSeconds = (timeoutSetting instanceof Number && timeoutSetting > 0) ? timeoutSetting : 7
	def switchId = triggeringSwitch.id.toString()

	state.lastModeActivity = state.lastModeActivity ?: [:]
	long timestamp = now()
	state.lastModeActivity[switchId] = [timestamp: timestamp, mode: mode]

	// Schedule the timeout callback
	runIn(timeoutSeconds, "exitModeIfInactive", [data: [switchId: switchId, scheduledAt: timestamp, mode: mode], overwrite: false])
	log.debug "Scheduled ${mode} mode timeout for ${triggeringSwitch.displayName} in ${timeoutSeconds}s."
}

def cancelModeTimeout(triggeringSwitch) {
	if (!triggeringSwitch) return
	def switchId = triggeringSwitch.id.toString()
	state.lastModeActivity = state.lastModeActivity ?: [:]
	state.lastModeActivity.remove(switchId)
	log.debug "Cancelled mode timeout for ${triggeringSwitch.displayName}."
}

def exitModeIfInactive(data) {
	def switchId = data?.switchId?.toString()
	def scheduledAt = data?.scheduledAt
	def mode = data?.mode
	if (!switchId || !scheduledAt || !mode) return

	if (state.lastModeActivity && state.lastModeActivity[switchId]?.timestamp == scheduledAt && state.activeSwitchMode[switchId] == mode) {
		log.info "${mode} mode timeout reached for switch ID ${switchId}. Exiting ${mode} mode."
		exitMode(switchId, mode)
	} else {
		log.debug "Ignoring stale timeout for switch ID ${switchId} (mode: ${mode})."
	}
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


def setLedEffect(switchDevice, effectName, hueOverride = null) {
	if (!switchDevice || !switchDevice.hasCommand('ledEffectAll')) return
	try {
		Integer hue = 170 // Default hue
		if (hueOverride != null) {
			hue = hueOverride as Integer
		} else {
			try { // Attempt to get hue from device setting parameter 95
				if (switchDevice.metaClass.respondsTo(switchDevice, "getSetting")) {
					def settingVal = switchDevice.getSetting('parameter95') 
					if (settingVal?.toString()) hue = settingVal.toString().toInteger()
				}
			} catch (Exception e) { /* Default hue will be used */ }
		}
		
		Integer effectCode = (effectName.toLowerCase() == "chase") ? 17 : 255 // 17=chase, 255=solid (device specific)
		switchDevice.ledEffectAll(effectCode, hue, 100, 255) // Brightness 100%, Duration 255 (indefinite)
		log.debug "Set LED effect '${effectName}' (code ${effectCode}) on ${switchDevice.displayName} with hue ${hue}."
	} catch (e) {
		 log.error "Failed to send LED effect to ${switchDevice.displayName}: ${e.message}."
	}
}

/**
 * Gets light (level, CT, enableCt) settings for a mode.
 * Falls back to global defaults if mode-specific settings are missing.
 */
private Map getModeSettings(String targetModeName = null) {
	String effectiveModeName = targetModeName ?: state.currentLocationMode ?: location.currentMode?.name?.toString()?.trim()
	Map modeConfig = state.modeSettingsMap[effectiveModeName]

	Integer finalLevel = modeConfig?.level ?: state.globalDefaultLevel
	Boolean resolvedEnableCt = modeConfig ? modeConfig.enableCt : (targetModeName != null) // Enable CT if specific mode requested but not found, else false
	Integer finalCt = resolvedEnableCt ? (modeConfig?.ct ?: state.globalDefaultColorTemperature) : null

	return [level: finalLevel, ct: finalCt, enableCt: resolvedEnableCt]
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
 * Determines if a device is the "All Hue Lights" whole-home group. Hue Groups for the whole house should not be controlled due to a variety of corner case bugs that result
 */
def isAllLightsGroup(device) {
	if (!device) return false
	String dni = device.deviceNetworkId ?: ''
	if (dni.endsWith('/0')) return true
	if (device.displayName instanceof String) {
		String dnLower = device.displayName.toLowerCase().trim()
		if (dnLower.contains("all hue lights") || 
		    dnLower == "home (hue group)") {
			return true
		}
	}
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

	Map<String, Integer> indexMap = settings.controlledSwitches?.is(deviceListParameter) ? state.deviceToIndexMap?.switches :
									settings.controlledLightsAndScenes?.is(deviceListParameter) ? state.deviceToIndexMap?.lightsAndScenes : null

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
	return singleIdMode ? (foundDevices ? foundDevices.first() : null) : foundDevices // .first() on an empty list throws, so guard it
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
	def sortedSwitches = settings.controlledSwitches.collect().sort { a, b -> (a.displayName ?: '').toLowerCase() <=> (b.displayName ?: '').toLowerCase() }
	sortedSwitches.each { sw ->
		def switchId = sw.id.toString(); def sInfo = state.switchInfoMap[switchId]
		def switchName = sInfo?.displayName ?: sw.displayName ?: "Switch ID ${switchId}"
		boolean hasMotion = sInfo?.type != "local" && (sw.hasCapability("MotionSensor") || sw.hasCapability("Motion Sensor"))
		boolean isLightSensor = state.sensorSwitchToRoomMap ? (state.sensorSwitchToRoomMap[switchId] != null) : false
		summary.append("<b>${switchName.toUpperCase()}</b> (${sInfo?.type}${hasMotion ? ' + Motion' : ''}${isLightSensor ? ' + Light Sensor' : ''})\n")

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
		String tapUpHdr = isThisSwitchSceneOnly ? "On/Next Scene" : "On"
		String tapUpNames = getDevicesById(tapUpIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  <b>${tapUpHdr}</b>: ${tapUpNames ?: "None"}\n")

		// Tap Down (Single)
		String tapDownHdr = "Off"
		List<String> tapDownTargetIds = isThisSwitchSceneOnly ? 
			((sInfo.loc?.roomName ? state.switchRoomLights[switchId] : []) + (sInfo.loc?.zoneName ? state.switchZoneLights[switchId] : [])).unique() :
			(state.switchAreaLights[switchId] ?: [])
		String tapDownNames = getDevicesById(tapDownTargetIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  <b>${tapDownHdr}</b>: ${tapDownNames ?: "None"}\n")

		// Tap Up 2x (Double) - Zone/Room ON
		List<String> tapUp2xIds = (sInfo.loc?.zoneName ? state.switchZoneLights[switchId] : sInfo.loc?.roomName ? state.switchRoomLights[switchId] : []) ?: []
		String tapUp2xNames = getDevicesById(tapUp2xIds, settings.controlledLightsAndScenes)?.sort { it.displayName ?: '' }?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  <b>On 2x</b>: ${tapUp2xNames ?: "None"}\n")
		
		// Tap Down 2x (Double) - Zone/Room OFF
		summary.append("  <b>Off 2x</b>: ${tapUp2xNames ?: "None"}\n")

		// Config Button (Scenes)
		List<String> sceneIds = state.sortedSwitchSceneIds[switchId] ?: []
		String sceneNames = getDevicesById(sceneIds, settings.controlledLightsAndScenes)?.collect { transformName(it.displayName, roomNameForStripping) }?.join(", ")
		summary.append("  <b>Scene Mode</b>: ${sceneNames ?: "None"}\n\n")
	}
	state.switchControlSummary = summary.toString().trim()
	log.debug("Switch control summary updated.")
}

