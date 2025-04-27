/**
* Hank's Switch Bot v04-27-2025
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
* Hubitat app providing simple, zero-config control of lights and scenes from switches.
* Users simply select devices; the app automatically builds control mappings based on device names.
* It links switches to lights and scenes by analyzing name stems (e.g., 'Kitchen Ceiling Switch' controls 'Kitchen Ceiling Lights'),
* 'All'/Master conventions for room-level control, zone tags ([ZoneName]), and the roomName property.
*/

definition(
	name: "Hank's Switch Bot",
	namespace: "hankle",
	author: "Hank Leukart",
	description: "Simple, zero-config control of lights and scenes from switches.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: ""
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
		paragraph "Configure default brightness and color temperature for each mode. "+
				 "Lights supporting color temperature will use these settings when turned ON or set by this app. " +
				 "If a specific setting for a mode is left blank or cleared, the Fallback Default will be used for level, " +
				 "and color temperature will only be set if explicitly enabled for that mode (using its Fallback Default CT if no specific CT is entered for the mode)."

		input name: "globalDefaultLevel", type: "number", title: "Fallback Default Level (%)",
			 description: "Used if a mode has no specific level set.",
			 range: "1..100", defaultValue: 100, required: true, width: 6
		input name: "globalDefaultColorTemperature", type: "number", title: "Fallback Default Color Temperature (K)",
			 description: "Used if a mode is enabled for CT but has no specific CT set.",
			 range: "2000..9000", defaultValue: 2700, required: true, width: 6
		paragraph ""

		if (location.modes) {
			location.modes.sort { it.name }.each { mode ->
				String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()
				String currentModeNameLower = mode.name.toLowerCase()

				Integer conditionalDefaultLevel = null
				Integer conditionalDefaultCt = null
				Boolean conditionalEnableCt = true

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

				input(
					name: "level_${safeModeName}",
					type: "number",
					title: "Level for '${mode.name}' (%)",
					range: "1..100",
					required: false,
					width: 4,
					defaultValue: conditionalDefaultLevel
				)
				input(
					name: "ct_${safeModeName}",
					type: "number",
					title: "Color Temp for '${mode.name}' (K)",
					range: "2000..9000",
					required: false,
					width: 4,
					defaultValue: conditionalDefaultCt
				)
				input(
					name: "enableCt_${safeModeName}",
					type: "bool",
					title: "Set CT for '${mode.name}'?",
					defaultValue: conditionalEnableCt,
					required: false,
					width: 4
				)
			}
		} else {
			paragraph "Save settings once to see per-mode configuration options."
		}
	}

	section("Advanced Button Mappings (Optional)", hideable: true, hidden: true) {
		 input "singleTapUpButtonNumber", "number",
			title: "Single Tap Up (Area ON / Next Scene) Button Number",
			defaultValue: 1, required: false, width: 2
		 input "singleTapUpButtonEvent", "enum",
			title: "Single Tap Up (Area ON / Next Scene) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12

		 input "singleTapDownButtonNumber", "number",
			title: "Single Tap Down (Area/Room/Zone OFF) Button Number",
			defaultValue: 1, required: false, width: 2
		 input "singleTapDownButtonEvent", "enum",
			title: "Single Tap Down (Area/Room/Zone OFF) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "held", required: false, width: 2
		 paragraph "Note: In Scene Mode, Button 1 Pushed/Held are used for cycling scenes.", width: 12

		 input "configButtonNumber", "number",
			title: "Scene Mode Toggle Button Number",
			defaultValue: 8, required: false, width: 2
		 input "configButtonEvent", "enum",
			title: "Scene Mode Toggle Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12

		 input "doubleTapUpButtonNumber", "number",
			title: "Zone/Room On Button Number",
			defaultValue: 2, required: false, width: 2
		 input "doubleTapUpButtonEvent", "enum",
			title: "Zone/Room On Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12

		 input "doubleTapDownButtonNumber", "number",
			title: "Zone/Room Off Button Number",
			defaultValue: 2, required: false, width: 2
		 input "doubleTapDownButtonEvent", "enum",
			title: "Zone/Room Off Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12

		 input "holdUpButtonNumber", "number",
			title: "Hold Up (Start Dim Up) Button Number",
			defaultValue: 6, required: false, width: 2
		 input "holdUpButtonEvent", "enum",
			title: "Hold Up (Start Dim Up) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12

		 input "releaseUpButtonNumber", "number",
			title: "Release Up (Stop Dim Up) Button Number",
			defaultValue: 7, required: false, width: 2
		 input "releaseUpButtonEvent", "enum",
			title: "Release Up (Stop Dim Up) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "pushed", required: false, width: 2
		 paragraph "", width: 12

		 input "holdDownButtonNumber", "number",
			title: "Hold Down (Start Dim Down) Button Number",
			defaultValue: 6, required: false, width: 2
		 input "holdDownButtonEvent", "enum",
			title: "Hold Down (Start Dim Down) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12

		 input "releaseDownButtonNumber", "number",
			title: "Release Down (Stop Dim Down) Button Number",
			defaultValue: 7, required: false, width: 2
		 input "releaseDownButtonEvent", "enum",
			title: "Release Down (Stop Dim Down) Button Event",
			options: ["pushed", "held", "released", "doubleTapped"],
			defaultValue: "held", required: false, width: 2
		 paragraph "", width: 12

		 input "sceneModeTimeout", "number",
			title: "Scene Mode Timeout (seconds)",
			defaultValue: 7, required: false
	}
}

def installed() {
	log.info "Installed Hank's Switch Machine"
	initialize()
}

def updated() {
	log.info "Updated Hank's Switch Machine"
	unsubscribe()
	initialize()
}

def initialize() {
	state.sceneIndex = [:]
	state.sceneMode = [:]
	state.sceneTimeoutJob = [:]
	state.modeSettingsMap = [:]
	state.switchControlSummary = "Initializing or no switches configured..."
	state.siblingSwitchGroupsBySwitchId = [:]
	state.switchIdToLocationMap = [:]
	state.switchRoomLights = [:]
	state.switchAreaLights = [:]
	state.switchZoneLights = [:]
	state.switchScenes = [:]
	state.firstAreaLightToSwitch = [:]
	state.sortedSwitchSceneIds = [:]  
	state.switchDimmableAreaLightIds = [:]

	state.deviceToIndexMap = [switches: [:], lightsAndScenes: [:]]
	settings.controlledSwitches?.eachWithIndex { sw, index ->
		if (sw?.id) state.deviceToIndexMap.switches[sw.id.toString()] = index
	}
	settings.controlledLightsAndScenes?.eachWithIndex { dev, index ->
		if (dev?.id) state.deviceToIndexMap.lightsAndScenes[dev.id.toString()] = index
	}
	log.info "Built device index maps. Switches: ${state.deviceToIndexMap?.switches?.size() ?: 0}, Lights/Scenes: ${state.deviceToIndexMap?.lightsAndScenes?.size() ?: 0}"

	if (settings.controlledSwitches) {
		settings.controlledSwitches.each { sw ->
			if (sw?.id) {
				def currentSwitchIdString = sw.id.toString()
				state.switchIdToLocationMap[currentSwitchIdString] = parseDeviceLocation(sw)
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
			if (sw.hasCapability("PushableButton")) {
				 buttonEventsToSubscribe.each { eventName ->
					subscribe(sw, eventName, buttonHandler)
				 }
			} else {
				log.warn "Switch ${sw.displayName} does not support PushableButton capability."
			}
			state.sceneIndex[sw.id.toString()] = state.sceneIndex[sw.id.toString()] ?: -1
			state.sceneMode[sw.id.toString()] = state.sceneMode[sw.id.toString()] ?: false
		}
	}

	if (state.firstAreaLightToSwitch) {
		state.firstAreaLightToSwitch.each { lightId, switchId ->
			def lightDevice = getDevicesById(lightId.toString(), settings.controlledLightsAndScenes)
			if (lightDevice) {
				if (lightDevice.hasCapability("SwitchLevel")) {
					subscribe(lightDevice, "level", firstLightStateHandler)
				}
				if (lightDevice.hasCapability("Switch")) {
					 subscribe(lightDevice, "switch", firstLightStateHandler)
				}
			} else {
				log.warn "Could not find first area light device with ID ${lightId} to subscribe for state sync."
			}
		}
	}

	updateSwitchControlSummary()
	log.info "Initialization complete."
}

private String getEffectiveDeviceNameForMatching(device) {
	if (!device) return ""
	String originalDisplayName = device.displayName?.trim() ?: ""
	String deviceRoomProp = null
	try {
		deviceRoomProp = device.roomName?.trim()
	} catch (MissingPropertyException e) { /* ignore */ }

	if (deviceRoomProp && !deviceRoomProp.isEmpty() && !originalDisplayName.toLowerCase().startsWith(deviceRoomProp.toLowerCase())) {
		return "${deviceRoomProp} ${originalDisplayName}"
	}
	return originalDisplayName
}

def parseDeviceLocation(device) {
	String roomName = null
	String areaName = null
	String zoneName = null

	def zoneMatcher = device?.name =~ /\s*\[\s*(.*?)\s*\]\s*/
	if (zoneMatcher?.find()) {
		zoneName = zoneMatcher[0][1]?.trim()
	}

	if (!device?.displayName) {
		 return [roomName: null, areaName: null, zoneName: zoneName, effectiveNameUsedForParsing: ""]
	}

	String baseDisplayNameForParsing = getEffectiveDeviceNameForMatching(device)
	String deviceRoomProp = null
	try { deviceRoomProp = device.roomName?.trim() } catch (MissingPropertyException e) { /*ignore*/ }

	if (deviceRoomProp && !deviceRoomProp.isEmpty() && baseDisplayNameForParsing.toLowerCase().startsWith(deviceRoomProp.toLowerCase())) {
		roomName = deviceRoomProp
		String remainingAfterRoomProp = baseDisplayNameForParsing.substring(deviceRoomProp.length()).trim()
		if (remainingAfterRoomProp) {
			def remainingParts = remainingAfterRoomProp.tokenize()
			if (remainingParts) {
				String potentialArea = remainingParts.first()
				if (potentialArea && !(potentialArea.equalsIgnoreCase("Switch") || (remainingParts.size() > 1 && potentialArea.equalsIgnoreCase("All") && remainingParts.getAt(1).equalsIgnoreCase("Switch")) )) {
					areaName = potentialArea
				}
			}
		}
	}

	if (!roomName && baseDisplayNameForParsing) {
		def parts = baseDisplayNameForParsing.tokenize()
		if (parts) {
			roomName = parts[0]
			int roomWords = 1
			if (parts.size() > 1 && (parts[1].equalsIgnoreCase("room") || parts[1].equalsIgnoreCase("rm"))) {
				roomName = "$roomName ${parts[1]}"
				roomWords = 2
			}
			if (parts.size() > roomWords) {
				String potentialAreaWord = parts[roomWords]
				if (potentialAreaWord && !(potentialAreaWord.equalsIgnoreCase("Switch") || (parts.size() > roomWords + 1 && potentialAreaWord.equalsIgnoreCase("All") && parts.getAt(roomWords + 1).equalsIgnoreCase("Switch")) )) {
					if (potentialAreaWord.equalsIgnoreCase("All") && parts.size() > roomWords + 1 && parts.getAt(roomWords+1).equalsIgnoreCase("Switch")) {
						// areaName = potentialAreaWord // Original bug: "All" before "Switch" is not an area.
					} else if (!potentialAreaWord.equalsIgnoreCase("All")) {
						areaName = potentialAreaWord
					}
				}
			}
		}
	}
	// log.trace "parseDeviceLocation for '${device?.displayName}' (Name: '${device?.name}'): Room='${roomName}', Area='${areaName}', Zone='${zoneName}', EffectiveName='${baseDisplayNameForParsing}'"
	return [roomName: roomName?.trim(), areaName: areaName?.trim(), zoneName: zoneName, effectiveNameUsedForParsing: baseDisplayNameForParsing]
}

private void normalizeSwitchRoomNames() {
	if (settings.controlledSwitches == null || settings.controlledSwitches.isEmpty() || state.switchIdToLocationMap == null || state.switchIdToLocationMap.isEmpty()) {
		return
	}

	Map<String, List<String>> displayNameGroups = [:].withDefault { [] }
	settings.controlledSwitches.each { swDevice ->
		if (!swDevice?.displayName) return
		String baseDisplayName = swDevice.displayName.replaceAll(/\s*\(.*\)\s*$/, "").trim()
		displayNameGroups[baseDisplayName] << swDevice.id.toString()
	}

	displayNameGroups.each { baseDisplayNameKey, switchIdsInGroup ->
		if (switchIdsInGroup.size() <= 1) return

		List<Map> switchInfoForGroup = []
		switchIdsInGroup.each { id ->
			def loc = state.switchIdToLocationMap[id]
			def device = getDevicesById(id, settings.controlledSwitches)
			if (loc && device?.displayName) {
				 switchInfoForGroup << [id: id, displayName: device.displayName, parsedRoomName: loc.roomName, effectiveName: loc.effectiveNameUsedForParsing]
			}
		}
		
		if (switchInfoForGroup.isEmpty()) return
		String firstRoomName = switchInfoForGroup.first().parsedRoomName
		if (switchInfoForGroup.every { it.parsedRoomName == firstRoomName }) return
		
		String authoritativeRoomName = null
		String authoritativeSwitchId = null

		for (Map info in switchInfoForGroup) {
			if (info.parsedRoomName && info.effectiveName?.toLowerCase()?.startsWith(info.parsedRoomName.toLowerCase())) {
				 authoritativeRoomName = info.parsedRoomName
				 authoritativeSwitchId = info.id
				 break
			}
		}
		if (!authoritativeRoomName) {
			for (Map info in switchInfoForGroup) {
				if (info.parsedRoomName && info.displayName?.toLowerCase()?.startsWith(info.parsedRoomName.toLowerCase())) {
					 authoritativeRoomName = info.parsedRoomName
					 authoritativeSwitchId = info.id
					 break
				}
			}
		}

		if (authoritativeRoomName) {
			def authSwitchDisplayName = getDevicesById(authoritativeSwitchId, settings.controlledSwitches)?.displayName ?: "ID ${authoritativeSwitchId}"
			log.info "Normalizing room names for switches matching base displayName '${baseDisplayNameKey}' to '${authoritativeRoomName}' (from switch '${authSwitchDisplayName}')."
			switchIdsInGroup.each { idToUpdate ->
				if (idToUpdate != authoritativeSwitchId) {
					def currentLocMap = state.switchIdToLocationMap[idToUpdate]
					if (currentLocMap && currentLocMap.roomName != authoritativeRoomName) {
						Map newLoc = new HashMap(currentLocMap)
						newLoc.roomName = authoritativeRoomName
						state.switchIdToLocationMap[idToUpdate] = newLoc
					}
				}
			}
		} else {
			log.warn "For switches matching base name '${baseDisplayNameKey}', multiple roomNames exist but no authoritative switch found. Room names not normalized for this group."
		}
	}
}

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

		String expectedBaseSwitchName = "${loc.roomName} Switch"
		if (swDevice.displayName == expectedBaseSwitchName || swDevice.displayName.startsWith(expectedBaseSwitchName + " (")) {
			String groupKey = "${loc.roomName}::${expectedBaseSwitchName}"
			potentialGroups[groupKey] << switchId
		}
	}

	potentialGroups.each { groupKey, memberIds ->
		if (memberIds.size() > 1) {
			def memberNames = memberIds.collect { id ->
				getDevicesById(id, settings.controlledSwitches)?.displayName ?: id
			}.join(', ')
			log.info "Identified sibling switch group for key '${groupKey}': ${memberNames}"
			memberIds.each { id ->
				state.siblingSwitchGroupsBySwitchId[id] = memberIds
			}
		}
	}
}

def buildDeviceMaps() {
	log.info "Starting buildDeviceMaps..."
	state.switchRoomLights = [:]
	state.switchAreaLights = [:]
	state.switchZoneLights = [:]
	state.switchScenes = [:]
	state.firstAreaLightToSwitch = [:]
	state.sortedSwitchSceneIds = [:]
	state.switchDimmableAreaLightIds = [:]

	Map lightSceneLocations = [:]
	settings.controlledLightsAndScenes?.each { dev ->
		lightSceneLocations[dev.id.toString()] = parseDeviceLocation(dev)
	}

	Map switchInfoMap = [:]
	settings.controlledSwitches?.each { sw ->
		def switchId = sw.id.toString()
		def switchLoc = state.switchIdToLocationMap[switchId]
		if (!switchLoc) {
			log.warn "buildDeviceMaps: No location info for switch ID ${switchId} (${sw.displayName}). Skipping stem calculation for this switch."
			state.switchAreaLights[switchId] = []
			state.switchScenes[switchId] = []
			state.sortedSwitchSceneIds[switchId] = []
			state.switchDimmableAreaLightIds[switchId] = []
			return
		}
		
		String actualDisplayName = sw.displayName?.trim() ?: ""
		String parsedRoom = switchLoc.roomName
		String parsedArea = switchLoc.areaName
		String type = "unknown"
		String stem = null
		String stemSourceDisplay = getEffectiveDeviceNameForMatching(sw)

		if (actualDisplayName.toLowerCase().endsWith(" all switch")) {
			type = "all"
			stem = parsedRoom
		} else if (actualDisplayName.toLowerCase().endsWith(" switch") && (parsedArea == null || parsedArea.isEmpty() || parsedArea.equalsIgnoreCase("all"))) {
			type = "master"
			stem = parsedRoom
		} else if (actualDisplayName.toLowerCase().endsWith(" switch")) {
			type = "regular"
			stem = stemSourceDisplay.substring(0, stemSourceDisplay.toLowerCase().lastIndexOf(" switch")).trim()
		}
		
		if (stem) {
			switchInfoMap[switchId] = [type: type, stem: stem, loc: switchLoc, displayName: actualDisplayName]
		} else {
			log.info "buildDeviceMaps: Switch '${actualDisplayName}' (ID: ${switchId}) did not generate a stem. It will rely on zone scenes if applicable."
			state.switchAreaLights[switchId] = []
			state.switchScenes[switchId] = []
			state.sortedSwitchSceneIds[switchId] = []
			state.switchDimmableAreaLightIds[switchId] = []
		}
	}

	settings.controlledSwitches?.each { sw ->
		def switchId = sw.id.toString()
		def sInfo = switchInfoMap[switchId]
		def sLoc = state.switchIdToLocationMap[switchId]

		if (!sLoc) return

		state.switchRoomLights[switchId] = settings.controlledLightsAndScenes?.findAll { light ->
			if (isScene(light)) return false
			def targetLoc = lightSceneLocations[light.id.toString()]
			return targetLoc?.roomName && sLoc?.roomName && targetLoc.roomName.equalsIgnoreCase(sLoc.roomName)
		}?.collect { it.id.toString() } ?: []

		if (sLoc?.zoneName) {
			log.info "buildDeviceMaps: Switch '${sw.displayName}' (ID: ${switchId}) has zoneName: '${sLoc.zoneName}'. Looking for matching zone lights."
			List<String> currentZoneLightIds = []
			settings.controlledLightsAndScenes?.each { light ->
				if (isScene(light)) return
				def targetLoc = lightSceneLocations[light.id.toString()]
				if (targetLoc?.zoneName && targetLoc.zoneName.equalsIgnoreCase(sLoc.zoneName)) {
					currentZoneLightIds << light.id.toString()
				}
			}
			state.switchZoneLights[switchId] = currentZoneLightIds.unique()
			if (currentZoneLightIds.isEmpty()) {
				log.info "buildDeviceMaps: No lights found for zone '${sLoc.zoneName}' for switch '${sw.displayName}'."
			}
		} else {
			state.switchZoneLights[switchId] = []
		}
	}

	Set<String> allRegularlyMappedLightIds = new HashSet<>()

	switchInfoMap.each { switchId, sInfo ->
		state.switchDimmableAreaLightIds[switchId] = []

		if (sInfo.type == "regular" || sInfo.type == "all") {
			String stem = sInfo.stem
			List<String> currentAreaLightIds = []
			settings.controlledLightsAndScenes?.each { targetDev ->
				if (!isScene(targetDev)) {
					String targetEffectiveName = getEffectiveDeviceNameForMatching(targetDev)
					if (targetEffectiveName.toLowerCase().startsWith(stem.toLowerCase())) {
						currentAreaLightIds << targetDev.id.toString()
					}
				}
			}
			currentAreaLightIds = currentAreaLightIds.unique()
			state.switchAreaLights[switchId] = currentAreaLightIds
			allRegularlyMappedLightIds.addAll(currentAreaLightIds)

			if (!currentAreaLightIds.isEmpty()) {
				def areaLightObjects = getDevicesById(currentAreaLightIds, settings.controlledLightsAndScenes)
				state.switchDimmableAreaLightIds[switchId] = areaLightObjects?.findAll {
					it.hasCapability("SwitchLevel")
				}?.collect { it.id.toString() } ?: []
				
				def sortedAreaLightObjects = areaLightObjects?.sort { it.displayName }
				if (sortedAreaLightObjects && !sortedAreaLightObjects.isEmpty()) {
					 state.firstAreaLightToSwitch[sortedAreaLightObjects.first().id.toString()] = switchId
				}
			}
		} else if (sInfo.type == "master") {
			String masterSwitchRoom = sInfo.stem
			List<String> masterLightIds = []
			settings.controlledLightsAndScenes?.each { targetDev ->
				if (!isScene(targetDev) && !allRegularlyMappedLightIds.contains(targetDev.id.toString())) {
					def targetLoc = lightSceneLocations[targetDev.id.toString()]
					if (targetLoc?.roomName && targetLoc.roomName.equalsIgnoreCase(masterSwitchRoom)) {
						masterLightIds << targetDev.id.toString()
					}
				}
			}
			masterLightIds = masterLightIds.unique()
			state.switchAreaLights[switchId] = masterLightIds
			
			if (!masterLightIds.isEmpty()) {
				 def masterLightObjects = getDevicesById(masterLightIds, settings.controlledLightsAndScenes)
				 state.switchDimmableAreaLightIds[switchId] = masterLightObjects?.findAll {
					 it.hasCapability("SwitchLevel")
				 }?.collect { it.id.toString() } ?: []

				 def sortedMasterLightObjects = masterLightObjects?.sort { it.displayName }
				 if (sortedMasterLightObjects && !sortedMasterLightObjects.isEmpty()) {
					def firstMasterLightId = sortedMasterLightObjects.first().id.toString()
					if (!state.firstAreaLightToSwitch.containsKey(firstMasterLightId)) {
						state.firstAreaLightToSwitch[firstMasterLightId] = switchId
					}
				 }
			} else {
				 state.switchDimmableAreaLightIds[switchId] = []
			}
		}
	}
	
	switchInfoMap.each { switchId, sInfo ->
		String stem = sInfo.stem
		List<String> currentSceneIds = []
		settings.controlledLightsAndScenes?.each { targetDev ->
			if (isScene(targetDev)) {
				String targetEffectiveName = getEffectiveDeviceNameForMatching(targetDev)
				if (stem && targetEffectiveName.toLowerCase().startsWith(stem.toLowerCase())) {
					currentSceneIds << targetDev.id.toString()
				}
			}
		}
		state.switchScenes[switchId] = currentSceneIds.unique()

		if (!currentSceneIds.isEmpty()) {
			def sceneDeviceObjects = getDevicesById(currentSceneIds, settings.controlledLightsAndScenes)
			state.sortedSwitchSceneIds[switchId] = sceneDeviceObjects?.sort { it.displayName }?.collect { it.id.toString() } ?: []
		} else {
			state.sortedSwitchSceneIds[switchId] = []
		}
	}

	log.info "Starting check for zone-specific scenes for switches without primary (stem-based) scenes..."
	settings.controlledSwitches?.each { sw ->
		def switchId = sw.id.toString()
		def switchLoc = state.switchIdToLocationMap[switchId]
		def switchDisplayName = sw.displayName ?: "Switch ID ${switchId}"

		if (state.sortedSwitchSceneIds[switchId] == null) {
			state.sortedSwitchSceneIds[switchId] = []
		}
		if (state.switchScenes[switchId] == null) {
			 state.switchScenes[switchId] = []
		}

		boolean hasPrimaryScenes = state.sortedSwitchSceneIds[switchId] && !state.sortedSwitchSceneIds[switchId].isEmpty()

		if (switchLoc?.zoneName && !hasPrimaryScenes) {
			log.info "Switch '${switchDisplayName}' (ID: ${switchId}) has zone '${switchLoc.zoneName}' and no primary scenes. Checking for zone-specific scenes."
			List<String> zoneSceneIds = []
			String zoneNamePrefix = switchLoc.zoneName

			settings.controlledLightsAndScenes?.each { targetDev ->
				if (isScene(targetDev)) {
					String targetEffectiveName = getEffectiveDeviceNameForMatching(targetDev)
					if (targetEffectiveName.toLowerCase().startsWith(zoneNamePrefix.toLowerCase())) {
						zoneSceneIds << targetDev.id.toString()
					}
				}
			}

			if (!zoneSceneIds.isEmpty()) {
				List<String> uniqueZoneSceneIds = zoneSceneIds.unique()
				state.switchScenes[switchId] = uniqueZoneSceneIds
				
				def zoneSceneDeviceObjects = getDevicesById(uniqueZoneSceneIds, settings.controlledLightsAndScenes)
				state.sortedSwitchSceneIds[switchId] = zoneSceneDeviceObjects?.sort { it.displayName }?.collect { it.id.toString() } ?: []
				
				log.info "Associated ${state.sortedSwitchSceneIds[switchId].size()} zone scenes with switch '${switchDisplayName}' (Zone: ${switchLoc.zoneName})."
			} else {
				log.info "No zone-specific scenes found for switch '${switchDisplayName}' (Zone: ${switchLoc.zoneName})."
			}
		}
	}

	log.info "buildDeviceMaps finished. Pre-sorted scenes and pre-filtered dimmable lights. Zone scene fallback applied."
}


def buildModeSettingsMap() {
	def newModeSettings = [:]
	state.globalDefaultLevel = settings.globalDefaultLevel ?: 80
	state.globalDefaultColorTemperature = settings.globalDefaultColorTemperature ?: 3000

	if (location.modes) {
		location.modes.each { mode ->
			String safeModeName = mode.name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase()
			Integer modeLevelSetting = settings."level_${safeModeName}"
			Integer modeCtSetting = settings."ct_${safeModeName}"
			Boolean modeEnableCtSetting = settings."enableCt_${safeModeName}" != null ? settings."enableCt_${safeModeName}" : true

			Integer validLevel = (modeLevelSetting != null && modeLevelSetting >= 1 && modeLevelSetting <= 100) ? modeLevelSetting : null
			Integer validCt = (modeCtSetting != null && modeCtSetting >= 2000 && modeCtSetting <= 9000) ? modeCtSetting : null

			newModeSettings[mode.name] = [level: validLevel, ct: validCt, enableCt: modeEnableCtSetting]
		}
	}
	state.modeSettingsMap = newModeSettings
}

def firstLightStateHandler(evt) {
	def triggeringLight = evt.device
	def lightId = triggeringLight.id.toString()
	def eventName = evt.name
	def eventValue = evt.value

	def primarySwitchId = state.firstAreaLightToSwitch[lightId]
	if (!primarySwitchId) return

	log.info "firstLightStateHandler for ${triggeringLight.displayName} (ID: ${lightId}): Event ${eventName}=${eventValue}."
	
	Map siblingGroupsMap = state.siblingSwitchGroupsBySwitchId ?: [:]
	List<String> switchIdsToUpdate = siblingGroupsMap[primarySwitchId] ?: [primarySwitchId]
	
	if (switchIdsToUpdate.size() > 1 || (switchIdsToUpdate.size() == 1 && switchIdsToUpdate.first() != primarySwitchId)) {
		def names = switchIdsToUpdate.collect { getDevicesById(it, settings.controlledSwitches)?.displayName ?: it }.join(', ')
		log.info "Syncing switch(es): ${names} due to change on primary logical switch ${getDevicesById(primarySwitchId, settings.controlledSwitches)?.displayName ?: primarySwitchId}."
	} else {
		log.info "Syncing switch ${getDevicesById(primarySwitchId, settings.controlledSwitches)?.displayName ?: primarySwitchId}."
	}

	switchIdsToUpdate.each { switchIdToSync ->
		def targetSwitch = getDevicesById(switchIdToSync, settings.controlledSwitches)
		if (!targetSwitch) return

		if (eventName == "level") {
			def newLevel = eventValue as Integer
			if (targetSwitch.hasCommand("setLevel") && (targetSwitch.currentValue('level') as Integer) != newLevel) {
				try { 
					targetSwitch.setLevel(newLevel)
					log.info "Synced ${targetSwitch.displayName} to level ${newLevel}."
				} catch (e) { log.error "Error setting level on switch ${targetSwitch.displayName}: ${e.message}" }
			}
		} else if (eventName == "switch") {
			def newState = eventValue
			if (newState == "on" && targetSwitch.hasCommand("on") && targetSwitch.currentValue('switch') != "on") {
				 try { 
					 targetSwitch.on()
					 log.info "Synced ${targetSwitch.displayName} to ON."
				} catch (e) { log.error "Error turning ON switch ${targetSwitch.displayName}: ${e.message}" }
			} else if (newState == "off" && targetSwitch.hasCommand("off") && targetSwitch.currentValue('switch') != "off") {
				 try { 
					 targetSwitch.off()
					 log.info "Synced ${targetSwitch.displayName} to OFF."
				 } catch (e) { log.error "Error turning OFF switch ${targetSwitch.displayName}: ${e.message}" }
			}
		}
	}
}

def buttonHandler(evt) {
	def triggeringSwitch = evt.device
	def switchId = triggeringSwitch.id.toString()
	def buttonNumber = evt.value.toInteger()
	def buttonEvent = evt.name
	log.info "Button ${buttonNumber} (${buttonEvent}) on ${triggeringSwitch.displayName} (ID: ${switchId})"

	cancelSceneModeTimeout(triggeringSwitch)

	if (buttonNumber == (settings.configButtonNumber as Integer) && buttonEvent == settings.configButtonEvent) {
		if (state.sceneMode[switchId]) {
			exitSceneMode([switchId: switchId])
		} else {
			def sceneIds = state.sortedSwitchSceneIds[switchId]
			if (sceneIds && !sceneIds.isEmpty()) enterSceneMode(triggeringSwitch)
			else log.info "Config button on ${triggeringSwitch.displayName}, but no scenes (primary or zone). Scene Mode not activated."
		}
		return
	}

	if (state.sceneMode[switchId]) {
		handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	} else {
		handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent)
	}
}

private void handleSceneModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	def switchId = triggeringSwitch.id.toString()
	def sortedSceneIds = state.sortedSwitchSceneIds[switchId]
	if (!sortedSceneIds || sortedSceneIds.isEmpty()) {
		log.warn "handleSceneModeAction: No sorted scenes for ${triggeringSwitch.displayName}. Exiting scene mode."
		exitSceneMode([switchId: switchId]); return
	}

	def roomScenes = getDevicesById(sortedSceneIds, settings.controlledLightsAndScenes)
	if (!roomScenes || roomScenes.empty) {
		log.warn "handleSceneModeAction: Could not retrieve scene devices for ${triggeringSwitch.displayName} using sorted IDs. Exiting scene mode."
		exitSceneMode([switchId: switchId]); return
	}

	def sceneCount = roomScenes.size()
    def currentSceneIndex = (state.sceneIndex[switchId] != null) ? state.sceneIndex[switchId] : -1
	def sceneActivated = false

	if (buttonNumber == 1 && buttonEvent == "pushed") {
		currentSceneIndex = (currentSceneIndex + 1) % sceneCount
		activateScene(roomScenes[currentSceneIndex])
		state.sceneIndex[switchId] = currentSceneIndex
		sceneActivated = true
	} else if (buttonNumber == 1 && buttonEvent == "held") {
		currentSceneIndex = (currentSceneIndex - 1 + sceneCount) % sceneCount
		activateScene(roomScenes[currentSceneIndex])
		state.sceneIndex[switchId] = currentSceneIndex
		sceneActivated = true
	} else if (!(buttonNumber == 1 && buttonEvent == "released")) { // Any other button event exits scene mode
		 exitSceneMode([switchId: switchId])
	}

	if (sceneActivated) {
		log.info "Scene Mode: Activated scene '${roomScenes[currentSceneIndex]?.displayName}' (index ${currentSceneIndex}) for ${triggeringSwitch.displayName}"
		scheduleSceneModeTimeout(triggeringSwitch)
	}
}

private void handleNormalModeAction(triggeringSwitch, buttonNumber, buttonEvent) {
	def switchId = triggeringSwitch.id.toString()

	if (isSceneOnlySwitch(switchId)) {
		log.info "${triggeringSwitch.displayName} is a scene-only switch."
		if (buttonNumber == (settings.singleTapUpButtonNumber as Integer) && buttonEvent == settings.singleTapUpButtonEvent) {
			handleSceneOnlyCycle(triggeringSwitch)
		} else if (buttonNumber == (settings.singleTapDownButtonNumber as Integer) && buttonEvent == settings.singleTapDownButtonEvent) {
			 // For scene-only switch, "down" typically means turn associated zone/room off.
			 handleZoneOff(triggeringSwitch) // Or a specific "scene off" if defined. For now, using ZoneOff.
		}
		return
	}

	def areaLightIds = state.switchAreaLights[switchId]
	def areaLights = areaLightIds ? getDevicesById(areaLightIds, settings.controlledLightsAndScenes) : []

	def dimmableAreaLightIds = state.switchDimmableAreaLightIds[switchId]
	def dimmableAreaLights = dimmableAreaLightIds ? getDevicesById(dimmableAreaLightIds, settings.controlledLightsAndScenes) : []

	if (buttonNumber == (settings.singleTapUpButtonNumber as Integer) && buttonEvent == settings.singleTapUpButtonEvent) {
		handleAreaOn(triggeringSwitch, areaLights)
	} else if (buttonNumber == (settings.singleTapDownButtonNumber as Integer) && buttonEvent == settings.singleTapDownButtonEvent) {
		 handleAreaOff(triggeringSwitch, areaLights)
	} else if (buttonNumber == (settings.holdUpButtonNumber as Integer) && buttonEvent == settings.holdUpButtonEvent) {
		handleDimStart(triggeringSwitch, dimmableAreaLights, "up")
	} else if (buttonNumber == (settings.releaseUpButtonNumber as Integer) && buttonEvent == settings.releaseUpButtonEvent) {
		handleDimStop(triggeringSwitch, dimmableAreaLights)
	} else if (buttonNumber == (settings.holdDownButtonNumber as Integer) && buttonEvent == settings.holdDownButtonEvent) {
		handleDimStart(triggeringSwitch, dimmableAreaLights, "down")
	} else if (buttonNumber == (settings.releaseDownButtonNumber as Integer) && buttonEvent == settings.releaseDownButtonEvent) {
		handleDimStop(triggeringSwitch, dimmableAreaLights)
	} else if (buttonNumber == (settings.doubleTapUpButtonNumber as Integer) && buttonEvent == settings.doubleTapUpButtonEvent) {
		handleZoneOn(triggeringSwitch)
	} else if (buttonNumber == (settings.doubleTapDownButtonNumber as Integer) && buttonEvent == settings.doubleTapDownButtonEvent) {
		handleZoneOff(triggeringSwitch)
	}
}

private void handleAreaOn(triggeringSwitch, areaLights) {
	if (areaLights && !areaLights.isEmpty()) {
		Map targetSettings = getTargetSettingsForMode()
		def targetLevel = targetSettings.level
		def targetCt = targetSettings.ct
		def shouldSetCt = targetSettings.enableCt
		log.info "handleAreaOn for ${triggeringSwitch.displayName}: Turning ON ${areaLights.size()} area light(s). Level: ${targetLevel}%, CT: ${targetCt}K (Set CT: ${shouldSetCt})"


		areaLights.each { light ->
			try {
				boolean powerCommandSent = false
				if (light.hasCapability("SwitchLevel")) {
					light.setLevel(targetLevel); powerCommandSent = true
				} else if (light.hasCommand("on")) {
					light.on(); powerCommandSent = true
				}
				if (powerCommandSent && shouldSetCt && light.hasCapability("ColorTemperature")) {
					light.setColorTemperature(targetCt)
				}
			} catch (e) { log.error "Error controlling light ${light.displayName} in handleAreaOn: ${e.message}"}
		}
	} else {
		log.warn "No area lights for ${triggeringSwitch.displayName}. Area On skipped."
	}
}

private void handleZoneOn(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	def switchLocation = state.switchIdToLocationMap[switchId]

	if (!switchLocation) {
		log.warn "handleZoneOn: No location for ${triggeringSwitch.displayName}. Skipping."
		return
	}

	Map targetSettings = getTargetSettingsForMode()
	def targetLevel = targetSettings.level
	def targetCt = targetSettings.ct
	def shouldSetCt = targetSettings.enableCt

	List lightsToControlIds = []
	String controlScope = ""

	if (switchLocation.zoneName) {
		lightsToControlIds = state.switchZoneLights[switchId] ?: []
		controlScope = "Zone '${switchLocation.zoneName}'"
	} else if (switchLocation.roomName) {
		lightsToControlIds = state.switchRoomLights[switchId] ?: []
		controlScope = "Room '${switchLocation.roomName}'"
	} else {
		log.warn "handleZoneOn: ${triggeringSwitch.displayName} has no Zone or Room defined. Zone/Room On skipped."
		return
	}

	if (!lightsToControlIds || lightsToControlIds.isEmpty()){
		log.warn "handleZoneOn: No light IDs found for ${controlScope} (triggered by ${triggeringSwitch.displayName}). Zone/Room On skipped."
		return
	}
	def lightsToControl = getDevicesById(lightsToControlIds, settings.controlledLightsAndScenes)

	if (lightsToControl && !lightsToControl.isEmpty()) {
		log.info "Zone/Room On by ${triggeringSwitch.displayName}: Setting ${lightsToControl.size()} lights for ${controlScope} to Level: ${targetLevel}%, CT: ${targetCt}K (Set CT: ${shouldSetCt})"
		lightsToControl.each { light ->
			try {
				boolean powerCommandSent = false
				if (light.hasCapability("SwitchLevel")) {
					 light.setLevel(targetLevel); powerCommandSent = true
				} else if (light.hasCommand("on")) {
					 light.on(); powerCommandSent = true
				}
				if (powerCommandSent && shouldSetCt && light.hasCapability("ColorTemperature")) {
					light.setColorTemperature(targetCt)
				}
			} catch (e) { log.error "Error in Zone/Room On for light ${light.displayName}: ${e.message}" }
		}
	} else {
		 log.warn "handleZoneOn: No controllable light devices found for ${controlScope} for ${triggeringSwitch.displayName} (IDs: ${lightsToControlIds}). Zone/Room On skipped."
	}
}

private void handleAreaOff(triggeringSwitch, areaLights) {
  if (areaLights && !areaLights.isEmpty()) {
		log.info "handleAreaOff for ${triggeringSwitch.displayName}: Turning OFF ${areaLights.size()} area light(s)."
		 areaLights.each { light ->
			try { if (light.hasCommand("off")) light.off() }
			catch (e) { log.error "Error turning light OFF for ${light.displayName}: ${e.message}"}
		 }
  } else {
		log.warn "No area lights for ${triggeringSwitch.displayName}. Area Off skipped."
  }
}

private void handleSceneOnlyCycle(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	def sortedSceneIds = state.sortedSwitchSceneIds[switchId]
	if (!sortedSceneIds || sortedSceneIds.isEmpty()) {
		log.warn "handleSceneOnlyCycle: No sorted scenes for ${triggeringSwitch.displayName}."
		return
	}

	def roomScenes = getDevicesById(sortedSceneIds, settings.controlledLightsAndScenes)
	if (!roomScenes || roomScenes.empty) {
		log.warn "handleSceneOnlyCycle: Could not retrieve scene devices for ${triggeringSwitch.displayName} using sorted IDs."
		return
	}

	def sceneCount = roomScenes.size()
	def currentSceneIndex = state.sceneIndex[switchId] ?: -1
	def nextIndex = (currentSceneIndex + 1) % sceneCount

	activateScene(roomScenes[nextIndex])
	state.sceneIndex[switchId] = nextIndex
	log.info "SceneOnlyCycle: Activated scene '${roomScenes[nextIndex]?.displayName}' (index ${nextIndex}) for scene-only switch ${triggeringSwitch.displayName}"
}

private void handleDimStart(triggeringSwitch, dimmableAreaLights, String direction) {
	if (!dimmableAreaLights || dimmableAreaLights.isEmpty()) {
		// log.debug "handleDimStart: No dimmable area lights for ${triggeringSwitch.displayName}. Skipping." // Not a major event for info log
		return
	}
	log.info "handleDimStart for ${triggeringSwitch.displayName}: Start level change '${direction}' for ${dimmableAreaLights.size()} dimmable light(s)."

	if (direction == "up" && dimmableAreaLights.first()?.currentValue('switch') == 'off') {
		dimmableAreaLights.each { light ->
			try { if (light.hasCommand('setLevel')) light.setLevel(2) }
			catch (e) { log.error "Error setting min level on ${light.displayName}: ${e.message}" }
		}
		pause(250)
	}

	dimmableAreaLights.each { light ->
		try { if (light.hasCommand('startLevelChange')) light.startLevelChange(direction) }
		catch (e) { log.error "Error calling startLevelChange(${direction}) on ${light.displayName}: ${e.message}" }
	}
}

private void handleDimStop(triggeringSwitch, dimmableAreaLights) {
  if (dimmableAreaLights && !dimmableAreaLights.isEmpty()) {
		log.info "handleDimStop for ${triggeringSwitch.displayName}: Stop level change for ${dimmableAreaLights.size()} dimmable light(s)."
		dimmableAreaLights.each { light ->
			try { if(light.hasCommand('stopLevelChange')) light.stopLevelChange() }
			catch (e) { log.error "Error calling stopLevelChange() on ${light.displayName}: ${e.message}"}
		}
  } // else { log.debug "handleDimStop: No dimmable area lights for ${triggeringSwitch.displayName}. Skipping." } // Not a major event
}

private void handleZoneOff(triggeringSwitch) {
	def switchId = triggeringSwitch.id.toString()
	def switchLocation = state.switchIdToLocationMap[switchId]

	if (!switchLocation) {
		log.warn "handleZoneOff: No location for ${triggeringSwitch.displayName}. Skipping."
		return
	}

	List lightsToControlIds = []
	String controlScope = ""

	if (switchLocation.zoneName) {
		lightsToControlIds = state.switchZoneLights[switchId] ?: []
		controlScope = "Zone '${switchLocation.zoneName}'"
	} else if (switchLocation.roomName) {
		lightsToControlIds = state.switchRoomLights[switchId] ?: []
		controlScope = "Room '${switchLocation.roomName}'"
	} else {
		log.warn "handleZoneOff: ${triggeringSwitch.displayName} has no Zone or Room defined. Zone/Room Off skipped."
		return
	}

	if (!lightsToControlIds || lightsToControlIds.isEmpty()){
		log.warn "handleZoneOff: No light IDs found for ${controlScope} (triggered by ${triggeringSwitch.displayName}). Zone/Room Off skipped."
		return
	}
	def lightsToControl = getDevicesById(lightsToControlIds, settings.controlledLightsAndScenes)

	if (lightsToControl && !lightsToControl.isEmpty()) {
		log.info "Zone/Room Off by ${triggeringSwitch.displayName}: Turning off ${lightsToControl.size()} lights for ${controlScope}"
		lightsToControl.each { light ->
			try { if (light.hasCommand("off")) light.off() }
			catch (eDevice) { log.error "Error turning off device ${light?.displayName}: ${eDevice.message}" }
		}
	} else {
		log.warn "handleZoneOff: No controllable light devices found for ${controlScope} for ${triggeringSwitch.displayName}. Zone/Room Off skipped."
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
	if (!sceneDevice) return
	log.info "activateScene: Activating scene '${sceneDevice.displayName}'."
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
	def timeoutSeconds = (timeoutSetting != null && timeoutSetting instanceof Number && timeoutSetting > 0) ? timeoutSetting : 7
	def switchId = triggeringSwitch.id.toString()

	cancelSceneModeTimeout(triggeringSwitch)
	state.sceneTimeoutJob[switchId] = runIn(timeoutSeconds, "exitSceneMode", [data: [switchId: switchId], overwrite: true])
}

def cancelSceneModeTimeout(triggeringSwitch) {
	if (!triggeringSwitch) return
	def switchId = triggeringSwitch.id.toString()
	if (state.sceneTimeoutJob[switchId]) {
		try { unschedule(state.sceneTimeoutJob[switchId]) }
		catch (e) { log.warn "Error unscheduling scene mode timeout for ${switchId}: ${e.message}" }
		state.sceneTimeoutJob.remove(switchId)
	}
}

def exitSceneMode(data) {
	def switchId = data?.switchId?.toString()
	if (!switchId) {
		log.warn "exitSceneMode called without a specific switchId. Clearing all scene mode timeouts and states."
		Set<String> keysToClear = new HashSet<>(state.sceneTimeoutJob?.keySet() ?: [])
		keysToClear.each { id ->
			try { unschedule(state.sceneTimeoutJob[id]) } catch(e){}
			state.sceneTimeoutJob.remove(id)
			if (state.sceneMode?.containsKey(id)) {
				 state.sceneMode[id] = false
				 def sw = getDevicesById(id, settings.controlledSwitches)
				 if (sw) {
					 setLedEffect(sw, "solid")
					 log.info "Exited scene mode for ${sw.displayName} (cleared due to global exit)."
				 }
			}
		}
		return
	}

	def triggeringSwitch = getDevicesById(switchId, settings.controlledSwitches)
	if (triggeringSwitch) {
		if (state.sceneMode[switchId]) {
			state.sceneMode[switchId] = false
			setLedEffect(triggeringSwitch, "solid")
			log.info "Exited scene mode for ${triggeringSwitch.displayName}"
		}
	} else {
		log.warn "exitSceneMode: Switch with ID ${switchId} not found. Clearing its scene mode state."
		state.sceneMode?.remove(switchId)
		state.sceneIndex?.remove(switchId)
	}
	if (state.sceneTimeoutJob[switchId]) {
		try { unschedule(state.sceneTimeoutJob[switchId]) } catch(e){}
		state.sceneTimeoutJob.remove(switchId)
	}
}

def setLedEffect(switchDevice, effectName) {
	if (!switchDevice || !switchDevice.hasCommand('ledEffectAll')) return
	try {
		Integer hue
		String effectiveHueString = (switchDevice.getSetting('parameter95')?.toString() ?: '170')
		try {
			hue = effectiveHueString.toInteger()
		} catch (NumberFormatException e) {
			log.warn "Could not parse effective hue string '${effectiveHueString}' (derived from device setting 'parameter95') for LED hue on ${switchDevice.displayName}. Using default hue 170."
			hue = 170
		}

		Integer effectCode = (effectName == "chase") ? 17 : 255 // 17 = Chase, 255 = Solid (or whatever the "normal" is)
		switchDevice.ledEffectAll(effectCode, hue, 100, 255) // Assuming level 100, duration 255 (forever for solid)
	} catch (e) {
		 log.error "Failed to send LED effect to ${switchDevice.displayName}: ${e.message}."
	}
}

Map getTargetSettingsForMode() {
	def currentModeName = location.currentMode?.name?.toString()?.trim()
	Map modeConfig = state.modeSettingsMap[currentModeName]

	Integer finalLevel = state.globalDefaultLevel
	Integer finalCt = state.globalDefaultColorTemperature
	Boolean applyCt = false // Default to not applying CT unless explicitly enabled for the mode

	if (modeConfig) {
		finalLevel = modeConfig.level ?: state.globalDefaultLevel // Use mode level or global default
		if (modeConfig.enableCt) { // Check if CT should be set for this mode
			applyCt = true
			finalCt = modeConfig.ct ?: state.globalDefaultColorTemperature // Use mode CT or global default CT
		}
	}
	// log.debug "getTargetSettingsForMode: Mode='${currentModeName}', Level=${finalLevel}, CT=${finalCt}, ApplyCT=${applyCt}"
	return [level: finalLevel, ct: finalCt, enableCt: applyCt]
}

private boolean isSceneOnlySwitch(String switchId) {
	boolean hasScenes = state.sortedSwitchSceneIds?.get(switchId.toString())?.size() > 0
	boolean hasAreaLights = state.switchAreaLights?.get(switchId.toString())?.size() > 0
	return hasScenes && !hasAreaLights
}

def isScene(device) {
	if (!device) return false
	// Check display name for "scene" (case-insensitive)
	if (device.displayName instanceof String && device.displayName.matches(/(?i).*\b[Ss][Cc][Ee][Nn][Ee]\b.*/)) return true
	// Check specific device type names known to be scenes
	def deviceType = device.typeName // Get the device's type name
	if (deviceType && ["CoCoHue Scene", "Scene Activator", "hueBridgeScene", "Virtual Scene Switch"].contains(deviceType)) return true
	return false
}

// Optimized getDevicesById using pre-built index maps
def getDevicesById(def deviceIdInput, Collection deviceListParameter) {
	boolean singleIdMode = deviceIdInput instanceof String
	
	// Handle empty or null input immediately
	if (!deviceIdInput || (deviceIdInput instanceof Collection && deviceIdInput.isEmpty())) {
		return singleIdMode ? null : []
	}
	if (!deviceListParameter) { // Should not happen if called with settings.controlledSwitches/Lights
		log.warn "getDevicesById: deviceListParameter is null. Cannot retrieve devices."
		return singleIdMode ? null : []
	}

	List<String> idsToFetch
	if (singleIdMode) {
		idsToFetch = [deviceIdInput.toString()] // Ensure it's a string for map lookup
	} else if (deviceIdInput instanceof Collection) {
		// Ensure all elements are strings and filter out any nulls from the input collection
		idsToFetch = deviceIdInput.collect { it?.toString() }.findAll { it != null }
	} else { // Fallback for unexpected single input type, treat as single ID
		idsToFetch = [deviceIdInput.toString()]
	}

	if (idsToFetch.isEmpty()) return singleIdMode ? null : []

	Map<String, Integer> indexMapForList
	// Determine which index map to use based on object identity of the list parameter
	if (settings.controlledSwitches.is(deviceListParameter)) {
		indexMapForList = state.deviceToIndexMap?.switches
	} else if (settings.controlledLightsAndScenes.is(deviceListParameter)) {
		indexMapForList = state.deviceToIndexMap?.lightsAndScenes
	} else {
		log.warn "getDevicesById: deviceListParameter is not an identity match for settings.controlledSwitches or settings.controlledLightsAndScenes. Falling back to slow iteration."
		List fallbackDevices = []
		idsToFetch.each { idToFind ->
			def foundDevice = deviceListParameter.find { it.id?.toString() == idToFind }
			if (foundDevice) fallbackDevices << foundDevice
		}
		return singleIdMode ? (fallbackDevices.isEmpty() ? null : fallbackDevices.first()) : fallbackDevices
	}

	if (indexMapForList == null) {
		log.error "getDevicesById: Index map is null for the provided device list type. This indicates an issue with state.deviceToIndexMap initialization."
		return singleIdMode ? null : [] // Cannot proceed without an index
	}

	List foundDevices = []
	idsToFetch.each { id ->
		Integer deviceIndex = indexMapForList[id]
		if (deviceIndex != null && deviceIndex >= 0 && deviceIndex < deviceListParameter.size()) {
			// Fast path: direct access using pre-calculated index
			def device = deviceListParameter[deviceIndex]
			// Verify the device at the index actually matches the ID (paranoid check for list mutations)
			if (device?.id?.toString() == id) { // Check ID to ensure index is still valid
				foundDevices << device
			} else {
				log.warn "getDevicesById: Stale or incorrect index for ID ${id}. Device at index ${deviceIndex} is ${device?.id} ('${device?.displayName}'). Searching list as fallback for this ID."
				def fallbackDevice = deviceListParameter.find { it.id?.toString() == id } // Fallback search
				if (fallbackDevice) foundDevices << fallbackDevice
			}
		} else if (deviceIndex != null) { // Index exists but is out of bounds
			 log.warn "getDevicesById: Index ${deviceIndex} for ID ${id} is out of bounds for list size ${deviceListParameter.size()}. Searching list as fallback for this ID."
			 def fallbackDevice = deviceListParameter.find { it.id?.toString() == id } // Fallback search
			 if (fallbackDevice) foundDevices << fallbackDevice
		} else { // ID not found in the index map, try a direct search as a last resort
			// log.debug "getDevicesById: ID ${id} not found in index map. Searching list as fallback." // This can be noisy if expected for some IDs
			def fallbackDevice = deviceListParameter.find { it.id?.toString() == id } // Fallback search
			if (fallbackDevice) {
				foundDevices << fallbackDevice
			} // else: device truly not in list
		}
	}
	return singleIdMode ? (foundDevices.isEmpty() ? null : foundDevices.first()) : foundDevices
}

def updateSwitchControlSummary() {
	if (!settings.controlledSwitches || settings.controlledSwitches.isEmpty()) {
		state.switchControlSummary = "No switches are currently selected for control."
		return
	}
	// Check for incomplete initialization which could lead to errors here
	if (state.switchAreaLights == null || state.switchIdToLocationMap == null ||
		state.switchScenes == null || state.switchZoneLights == null || state.switchRoomLights == null ||
		state.deviceToIndexMap == null || state.sortedSwitchSceneIds == null || state.switchDimmableAreaLightIds == null) { // Added checks from your original code
		state.switchControlSummary = "Device maps are not fully initialized. Please save settings again."
		// log.warn "updateSwitchControlSummary: Aborting due to uninitialized state maps." // Optional: log this state
		return
	}

	StringBuilder summary = new StringBuilder()

	// Sort switches by display name for consistent summary order
	settings.controlledSwitches.sort { it.displayName ?: '' }.each { sw ->
		def switchId = sw.id.toString()
		def switchName = sw.displayName ?: "Switch ID ${switchId}"
		def switchLocation = state.switchIdToLocationMap[switchId]
		// Safely get room name for stripping, ensure it's trimmed
		String currentSwitchRoomNameTrimmed = switchLocation?.roomName?.trim()

		// Helper to transform device names, stripping the room if present
		def transformName = { String deviceDisplayNameString, String roomNameToStrip ->
			def dn = deviceDisplayNameString?.trim()
			if (!dn) return "" // Handle null or empty display names
			if (roomNameToStrip && dn.toLowerCase().startsWith((roomNameToStrip + " ").toLowerCase())) {
				def stripped = dn.substring(roomNameToStrip.length() + 1).trim()
				return stripped ?: dn // Return stripped name, or original if stripping results in empty
			}
			return dn
		}

		summary.append("${switchName.toUpperCase()}\n")
		boolean isThisSwitchSceneOnly = isSceneOnlySwitch(switchId)

		// Tap Up: Area Lights or Cycle Scenes (for scene-only switches)
		List<String> idsForTapUp = isThisSwitchSceneOnly ? state.sortedSwitchSceneIds[switchId] : state.switchAreaLights[switchId]
		String tapUpHeader = isThisSwitchSceneOnly ? "Tap Up (Cycle Scenes)" : "Tap Up (Area Lights)"
		String tapUpDeviceNames = getDevicesById(idsForTapUp, settings.controlledLightsAndScenes)
			?.sort { it.displayName ?: '' }// Sort devices by name
			?.collect { dev -> transformName(dev.displayName, currentSwitchRoomNameTrimmed) }
			?.join(", ")
		summary.append(" ${tapUpHeader}: ${ (idsForTapUp && !idsForTapUp.isEmpty() && tapUpDeviceNames) ? tapUpDeviceNames : "None" }\n")

		// Tap Up 2x: Zone/Room Lights (consistent for all switch types for this action)
		String tapUp2xHeader = "Tap Up 2x"
		// Determine lights for 2x tap: Zone lights if zone exists, otherwise Room lights
		List<String> idsForTapUp2x = (switchLocation?.zoneName ? state.switchZoneLights[switchId] : state.switchRoomLights[switchId]) ?: []
		String tapUp2xDeviceNames = getDevicesById(idsForTapUp2x, settings.controlledLightsAndScenes)
			?.sort { it.displayName ?: '' }// Sort devices by name
			?.collect { dev -> transformName(dev.displayName, currentSwitchRoomNameTrimmed) }
			?.join(", ")
		summary.append(" ${tapUp2xHeader}: ${ (idsForTapUp2x && !idsForTapUp2x.isEmpty() && tapUp2xDeviceNames) ? tapUp2xDeviceNames : "None" }\n")

		// Config Button: Scenes (primary or zone fallback)
		List<String> idsForScenes = state.sortedSwitchSceneIds[switchId] // This now includes zone fallback if applicable
		String sceneDeviceNames = getDevicesById(idsForScenes, settings.controlledLightsAndScenes)
			// No need to sort here if sortedSwitchSceneIds is already sorted, but collecting names might reorder if not careful
			// Assuming getDevicesById preserves order or scenes are re-sorted if needed for display
			?.collect { dev -> transformName(dev.displayName, currentSwitchRoomNameTrimmed) } // Apply name transformation
			?.join(", ")
		summary.append(" Scenes: ${ (idsForScenes && !idsForScenes.isEmpty() && sceneDeviceNames) ? sceneDeviceNames : "None" }\n")
		
		summary.append("\n") // Add a blank line between switches
	}

	state.switchControlSummary = summary.toString().trim()
	log.info "Switch control summary updated."
}
